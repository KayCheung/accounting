package com.kltb.accounting.core.application.service;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kltb.accounting.api.constant.Constants;
import com.kltb.accounting.api.constant.ResultCode;
import com.kltb.accounting.api.request.*;
import com.kltb.accounting.api.response.*;
import com.kltb.accounting.core.application.assembler.JournalingAssembler;
import com.kltb.accounting.core.domain.enums.*;
import com.kltb.accounting.core.domain.model.AccountPreCheckResult;
import com.kltb.accounting.core.domain.model.CustomerCheckInfo;
import com.kltb.accounting.core.domain.model.FreezeRecordCommand;
import com.kltb.accounting.core.domain.model.JournalCreateCommand;
import com.kltb.accounting.core.domain.service.*;
import com.kltb.accounting.core.infrastructure.config.FreezeProperties;
import com.kltb.accounting.core.infrastructure.messaging.LocalMessageService;
import com.kltb.accounting.core.infrastructure.persistence.entity.*;
import com.kltb.accounting.core.infrastructure.persistence.repository.*;
import com.kltb.accounting.core.infrastructure.redis.DistributedLockTemplate;
import com.kltb.accounting.core.infrastructure.redis.RedisKeyConstants;
import com.kltb.accounting.core.shared.exception.AccountException;
import com.kltb.accounting.core.shared.exception.ServiceException;
import com.kltb.accounting.core.shared.util.AccountingEntryUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 记账流水入库与业务交易应用服务
 * <p>
 * 负责：入口幂等锁控制、用例编排、业务预冻结/全额撤销解冻、流水及全链路总览查询、FAILED 状态更新
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JournalingApplicationService {

    private final JournalingDomainService journalingDomainService;
    private final AccountPreCheckDomainService accountPreCheckDomainService;
    private final FreezeDomainService freezeDomainService;
    private final BusinessRecordRepository businessRecordRepository;
    private final TransactionRepository transactionRepository;
    private final AccountingVoucherRepository accountingVoucherRepository;
    private final FreezeDetailRepository freezeDetailRepository;
    private final AccountingRuleRepository accountingRuleRepository;
    private final AccountRepository accountRepository;
    private final SubjectRepository subjectRepository;
    private final DistributedLockTemplate distributedLockTemplate;
    private final JournalingAssembler assembler;
    private final TransactionTemplate transactionTemplate;
    private final VoucheringApplicationService voucheringApplicationService;
    private final PostingApplicationService postingApplicationService;
    private final RollbackDomainService rollbackDomainService;
    private final BufferPostingDetailRepository bufferPostingDetailRepository;
    private final LocalMessageService localMessageService;
    private final ManualVoucherApplyRepository manualVoucherApplyRepository;
    private final FreezeProperties freezeProperties;
    private final BusinessDetailRepository businessDetailRepository;
    private final AsyncPostingDomainService asyncPostingDomainService;
    private final ReversalDomainService reversalDomainService;

    /**
     * 提交记账流水（含入口幂等锁控制）
     */
    public JournalSubmitResponse submitJournal(JournalSubmitRequest request) {
        // 1. 校验明细金额合计 = 总金额
        BigDecimal detailTotal = request.getDetails().stream()
                .map(JournalDetailRequest::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (request.getAmount().compareTo(detailTotal) != 0) {
            throw new ServiceException(ResultCode.PARAM_ERROR,
                    "流水明细金额合计(" + detailTotal + ")不等于总金额(" + request.getAmount() + ")");
        }

        // 2. 预校验 tradeType
        if (!TradeTypeEnum.isValid(request.getTradeType())) {
            throw new ServiceException(ResultCode.PARAM_ERROR,
                    "无效的tradeType: " + request.getTradeType());
        }

        // 3. 预校验 customerType
        validateCustomerTypes(request.getDetails());

        // 4. 幂等锁 Key
        String lockKey = RedisKeyConstants.Lock.Idempotent.trace(request.getTraceNo(), request.getTraceSeq());

        try {
            return distributedLockTemplate.execute(
                    lockKey,
                    0,     // wait 0s
                    -1,    // lease -1 (watchdog)
                    () -> doSubmit(request)
            );
        } catch (DuplicateKeyException e) {
            log.warn("[Journal] 唯一约束冲突，返回幂等结果: traceNo={}", request.getTraceNo());
            BusinessRecordPO existing = journalingDomainService.checkIdempotent(
                    request.getTraceNo(), request.getTraceSeq());
            TransactionPO txn = transactionRepository.selectByTraceNo(request.getTraceNo());
            return assembler.toIdempotentResponse(existing, txn);
        } catch (AccountException e) {
            String errorMsg = StrUtil.isNotBlank(e.getMessage()) ? e.getMessage() : e.getResultCode().getMessage();
            log.warn("[Journal] 流水记账业务异常，执行整笔流水回滚: traceNo={} error={}",
                    request.getTraceNo(), errorMsg);
            rollbackDomainService.executeFullJournalRollback(request.getTraceNo(), errorMsg);
            throw e;
        } catch (RuntimeException e) {
            log.error("[Journal] 流水记账系统异常，执行整笔流水回滚: traceNo={} error={}",
                    request.getTraceNo(), e.getMessage(), e);
            rollbackDomainService.executeFullJournalRollback(request.getTraceNo(), "系统异常: " + e.getMessage());
            throw e;
        }
    }

    /**
     * 锁内执行记账流水提交
     */
    private JournalSubmitResponse doSubmit(JournalSubmitRequest request) {
        // 幂等检查
        BusinessRecordPO existing = journalingDomainService.checkIdempotent(
                request.getTraceNo(), request.getTraceSeq());
        if (existing != null) {
            TransactionPO txn = transactionRepository.selectByTraceNo(request.getTraceNo());
            return assembler.toIdempotentResponse(existing, txn);
        }

        // 确定会计日期
        LocalDate accountingDate = journalingDomainService.determineAccountingDate(request.getTradeTime());

        // 匹配记账规则：若规则要求需先预冻结，强制校验 origFreezeNo 必传
        AccountingRulePO rule = accountingRuleRepository.selectByBusinessKey(
                request.getBusinessCode(), request.getTradingCode(), request.getPayChannel());
        if (rule != null && Integer.valueOf(1).equals(rule.getRequirePreFreeze()) && StrUtil.isBlank(request.getOrigFreezeNo())) {
            throw new AccountException(ResultCode.PARAM_ERROR,
                    "该业务记账规则要求必须先完成资金预冻结，缺失原预冻结流水号(origFreezeNo)");
        }

        // 流水持久化（采用 Command 领域命令对象，消除长参数列表坏味道）
        JournalCreateCommand createCmd = JournalCreateCommand.builder()
                .traceNo(request.getTraceNo())
                .traceSeq(request.getTraceSeq())
                .businessCode(request.getBusinessCode())
                .tradingCode(request.getTradingCode())
                .payChannel(request.getPayChannel())
                .tradeType(request.getTradeType())
                .amount(request.getAmount())
                .tradeTime(request.getTradeTime())
                .summary(request.getSummary())
                .details(request.getDetails())
                .accountingDate(accountingDate)
                .origFreezeNo(request.getOrigFreezeNo())
                .extraAttrs(request.getExtraAttrs())
                .build();
        JournalSubmitResult result = journalingDomainService.persistJournal(createCmd);

        // 预开户检查 + 自动开户（直接复用流程已匹配规则，彻底避免重复查询）
        Map<String, CustomerCheckInfo> customerMap = buildCustomerMap(request.getDetails());
        AccountPreCheckResult preCheckResult;
        if (rule != null) {
            preCheckResult = accountPreCheckDomainService.preCheckAndOpenAccounts(rule, customerMap, request.getTraceNo());
        } else {
            preCheckResult = accountPreCheckDomainService.preCheckAndOpenAccounts(
                    request.getBusinessCode(), request.getTradingCode(), request.getPayChannel(),
                    customerMap, request.getTraceNo());
        }

        // Phase 2: 凭证生成（Vouchering，透传账户内存字典，0 DB I/O）
        VoucherGenerateRequest voucherReq = new VoucherGenerateRequest();
        voucherReq.setTraceNo(request.getTraceNo());
        voucherReq.setBookkeeperName(Constants.SYSTEM_OPERATOR);
        voucherReq.setAccountMapping(preCheckResult != null ? preCheckResult.getAccountMapping() : Collections.emptyMap());
        VoucherGenerateResponse voucherResp = voucheringApplicationService.generateVoucher(voucherReq);

        // Phase 4: 过账执行（Posting）
        if (voucherResp != null && StrUtil.isNotBlank(voucherResp.getVoucherNo())) {
            PostingExecuteRequest postReq = new PostingExecuteRequest();
            postReq.setVoucherNo(voucherResp.getVoucherNo());
            postReq.setOperatorName(Constants.SYSTEM_OPERATOR);
            PostingExecuteResponse postResp = postingApplicationService.executePosting(postReq);

            // 若凭证状态已过账，联动更新流水为 SUCCESS
            if (postResp != null && VoucherStatusEnum.isPosted(postResp.getVoucherStatus())) {
                businessRecordRepository.updateStatusByTraceNo(request.getTraceNo(), BusinessRecordStatusEnum.SUCCESS);
            }
        }

        return assembler.toResponse(result);
    }

    /**
     * 业务预冻结（含入口幂等锁控制）
     * <p>
     * 业务方仅传入业务要素（businessCode, tradingCode, payChannel, customerId, amount），
     * 内部匹配记账规则，通过科目余额方向推导出金方并执行资金冻结。
     */
    public JournalFreezeResponse freezeJournal(JournalFreezeRequest request) {
        // 1. 校验明细金额合计 = 总金额
        BigDecimal detailTotal = request.getDetails().stream()
                .map(JournalDetailRequest::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (request.getAmount().compareTo(detailTotal) != 0) {
            throw new ServiceException(ResultCode.PARAM_ERROR,
                    "预冻结明细金额合计(" + detailTotal + ")不等于总金额(" + request.getAmount() + ")");
        }

        // 2. 预校验 customerType
        validateCustomerTypes(request.getDetails());

        // 3. 幂等锁
        String lockKey = RedisKeyConstants.Lock.Idempotent.trace(request.getTraceNo(), request.getTraceSeq());

        try {
            return distributedLockTemplate.execute(
                    lockKey, 0, -1, () -> doFreeze(request)
            );
        } catch (DuplicateKeyException e) {
            log.warn("[JournalFreeze] 唯一约束冲突，返回幂等结果: traceNo={}", request.getTraceNo());
            BusinessRecordPO existing = journalingDomainService.checkIdempotent(request.getTraceNo(), request.getTraceSeq());
            AccountFreezeDetailPO freezeDetail = null;
            if (existing != null && StrUtil.isNotBlank(existing.getOrigFreezeNo())) {
                freezeDetail = freezeDetailRepository.selectByVoucherNo(existing.getOrigFreezeNo());
            }
            return assembler.toFreezeResponse(existing, freezeDetail);
        } catch (AccountException e) {
            String errorMsg = StrUtil.isNotBlank(e.getMessage()) ? e.getMessage() : e.getResultCode().getMessage();
            markJournalFailed(request.getTraceNo(), errorMsg);
            throw e;
        } catch (RuntimeException e) {
            log.error("[JournalFreeze] 预冻结异常: traceNo={} error={}", request.getTraceNo(), e.getMessage(), e);
            markJournalFailed(request.getTraceNo(), "系统异常: " + e.getMessage());
            throw e;
        }
    }

    /**
     * 锁内执行业务预冻结
     */
    private JournalFreezeResponse doFreeze(JournalFreezeRequest request) {
        // 幂等检查
        BusinessRecordPO existing = journalingDomainService.checkIdempotent(request.getTraceNo(), request.getTraceSeq());
        if (existing != null) {
            AccountFreezeDetailPO freezeDetail = null;
            if (StrUtil.isNotBlank(existing.getOrigFreezeNo())) {
                freezeDetail = freezeDetailRepository.selectByVoucherNo(existing.getOrigFreezeNo());
            }
            return assembler.toFreezeResponse(existing, freezeDetail);
        }

        // 确定会计日期
        LocalDate accountingDate = journalingDomainService.determineAccountingDate(request.getTradeTime());

        // 匹配记账规则以识别出金方（优先走二级缓存）
        AccountingRulePO rule = accountingRuleRepository.selectByBusinessKey(
                request.getBusinessCode(), request.getTradingCode(), request.getPayChannel());
        if (rule == null) {
            throw new AccountException(ResultCode.RULE_NOT_FOUND,
                    "记账规则不存在: " + request.getBusinessCode() + "/" + request.getTradingCode());
        }
        if (rule.getRequirePreFreeze() == null || rule.getRequirePreFreeze() != 1) {
            throw new AccountException(ResultCode.PARAM_ERROR,
                    "当前记账规则未启用预冻结要求: " + request.getBusinessCode() + "/" + request.getTradingCode());
        }

        // 预开户检查 + 自动开户（直接复用规则，避免重复查库）
        Map<String, CustomerCheckInfo> customerMap = buildCustomerMap(request.getDetails());
        AccountPreCheckResult preCheckResult = accountPreCheckDomainService.preCheckAndOpenAccounts(rule, customerMap, request.getTraceNo());
        List<AccountingRuleDetailPO> ruleDetails = accountingRuleRepository.selectDetailsWithAuxiliary(rule.getId());
        if (ruleDetails == null || ruleDetails.isEmpty()) {
            throw new AccountException(ResultCode.RULE_NOT_FOUND, "记账规则明细为空");
        }

        // 识别所有出金方分录（科目的借贷方向与分录借贷方向推导出减少资金方）
        List<AccountingRuleDetailPO> payoutRuleDetails = new ArrayList<>();
        for (AccountingRuleDetailPO detail : ruleDetails) {
            AccountSubjectPO subject = subjectRepository.selectByCode(detail.getSubjectCode());
            SubjectBalanceDirectionEnum subjectDir = (subject != null && subject.getDebitCredit() != null)
                    ? subject.getDebitCredit() : SubjectBalanceDirectionEnum.CREDIT;
            DebitCreditEnum entryDc = detail.getDebitCredit();
            boolean isDecrease;
            if (subjectDir.isOffBalance()) {
                isDecrease = (entryDc == DebitCreditEnum.CREDIT);
            } else if (subjectDir.isBidirectional()) {
                isDecrease = false;
            } else {
                boolean isDebitSubject = (subjectDir == SubjectBalanceDirectionEnum.DEBIT);
                isDecrease = isDebitSubject ? (entryDc == DebitCreditEnum.CREDIT) : (entryDc == DebitCreditEnum.DEBIT);
            }
            if (isDecrease) {
                payoutRuleDetails.add(detail);
            }
        }
        if (payoutRuleDetails.isEmpty()) {
            payoutRuleDetails.add(ruleDetails.get(0));
        }

        // 流水持久化（采用 Command 领域命令对象，消除长参数列表坏味道）
        FreezeRecordCommand freezeCmd = FreezeRecordCommand.builder()
                .traceNo(request.getTraceNo())
                .traceSeq(request.getTraceSeq())
                .businessCode(request.getBusinessCode())
                .tradingCode(request.getTradingCode())
                .payChannel(request.getPayChannel())
                .amount(request.getAmount())
                .tradeTime(request.getTradeTime())
                .summary(request.getSummary())
                .details(request.getDetails())
                .accountingDate(accountingDate)
                .extraAttrs(request.getExtraAttrs())
                .build();
        BusinessRecordPO record = journalingDomainService.persistFreezeRecord(freezeCmd);

        // 执行多账户资金冻结（统一预冻结有效时长配置化，默认 1800 秒）
        long expireSeconds = (freezeProperties != null && freezeProperties.getDefaultExpireSeconds() > 0)
                ? freezeProperties.getDefaultExpireSeconds()
                : 1800L;
        LocalDateTime expireTime = LocalDateTime.now().plusSeconds(expireSeconds);
        List<AccountFreezeDetailPO> freezeDetails = new ArrayList<>();
        for (AccountingRuleDetailPO payoutRuleDetail : payoutRuleDetails) {
            String fundsType = payoutRuleDetail.getFundsType();
            JournalDetailRequest matchedDetail = request.getDetails().stream()
                    .filter(d -> fundsType.equals(d.getFundsType()))
                    .findFirst()
                    .orElse(null);
            if (matchedDetail == null) {
                matchedDetail = request.getDetails().get(0);
            }

            // 优先从内存字典获取真实账户号，未命中再回源 DB，彻底消除二次查库
            String accountNo = preCheckResult != null
                    ? preCheckResult.resolveAccountNo(matchedDetail.getCustomerId(), payoutRuleDetail.getSubjectCode())
                    : null;
            if (accountNo == null) {
                AccountPO account = accountRepository.selectByOwnerIdAndSubjectCode(
                        matchedDetail.getCustomerId(), payoutRuleDetail.getSubjectCode());
                if (account == null) {
                    throw new AccountException(ResultCode.ACCOUNT_NOT_FOUND,
                            "出金方账户未找到: customerId=" + matchedDetail.getCustomerId() + ", subjectCode=" + payoutRuleDetail.getSubjectCode());
                }
                accountNo = account.getAccountNo();
            }

            AccountFreezeDetailPO freezeDetail = freezeDomainService.freezeFund(
                    accountNo, matchedDetail.getAmount(), request.getBusinessCode(), request.getTraceNo(), expireTime, request.getSummary());
            freezeDetails.add(freezeDetail);
        }

        // 更新流水关联 freezeId 并标记为成功（多账户以首笔单号作为主单号）
        String primaryFreezeId = freezeDetails.get(0).getVoucherNo();
        businessRecordRepository.updateOrigFreezeNoAndStatus(
                request.getTraceNo(), primaryFreezeId, BusinessRecordStatusEnum.SUCCESS);
        record.setOrigFreezeNo(primaryFreezeId);
        record.setStatus(BusinessRecordStatusEnum.SUCCESS);

        return assembler.toFreezeResponse(record, freezeDetails);
    }

    /**
     * 业务预冻结全额解冻撤销（含入口幂等锁控制）
     * <p>
     * 约束：严格全额解冻，不允许部分解冻。
     */
    public JournalUnfreezeResponse unfreezeJournal(JournalUnfreezeRequest request) {
        String lockKey = RedisKeyConstants.Lock.Idempotent.trace(request.getTraceNo(), request.getTraceSeq());

        return distributedLockTemplate.execute(
                lockKey, 0, -1, () -> doUnfreeze(request)
        );
    }

    /**
     * 锁内执行全额解冻撤销
     */
    private JournalUnfreezeResponse doUnfreeze(JournalUnfreezeRequest request) {
        // 幂等检查：查看本次撤销流水是否已存在
        BusinessRecordPO existing = journalingDomainService.checkIdempotent(request.getTraceNo(), request.getTraceSeq());
        if (existing != null) {
            List<AccountFreezeDetailPO> freezeDetails = freezeDetailRepository.selectListByTraceNo(request.getOrigTraceNo());
            if (freezeDetails.isEmpty() && StrUtil.isNotBlank(existing.getOrigFreezeNo())) {
                AccountFreezeDetailPO single = freezeDetailRepository.selectByVoucherNo(existing.getOrigFreezeNo());
                if (single != null) freezeDetails = List.of(single);
            }
            return assembler.toUnfreezeResponse(request.getTraceNo(), request.getOrigTraceNo(), freezeDetails);
        }

        // 查询原预冻结流水
        BusinessRecordPO origRecord = businessRecordRepository.selectByTraceNo(request.getOrigTraceNo());
        if (origRecord == null) {
            throw new ServiceException(ResultCode.JOURNAL_NOT_FOUND, "原预冻结流水不存在: " + request.getOrigTraceNo());
        }
        if (origRecord.getTradeType() != TradeTypeEnum.PRE_FREEZE) {
            throw new ServiceException(ResultCode.PARAM_ERROR, "原流水非预冻结流水: " + request.getOrigTraceNo());
        }

        // 查询该笔预冻结流水下的所有冻结明细（支持多账户）
        List<AccountFreezeDetailPO> freezeDetails = freezeDetailRepository.selectListByTraceNo(request.getOrigTraceNo());
        if (freezeDetails.isEmpty() && StrUtil.isNotBlank(origRecord.getOrigFreezeNo())) {
            AccountFreezeDetailPO single = freezeDetailRepository.selectByVoucherNo(origRecord.getOrigFreezeNo());
            if (single != null) freezeDetails = List.of(single);
        }
        if (freezeDetails.isEmpty()) {
            throw new AccountException(ResultCode.FREEZE_RECORD_NOT_FOUND, "未找到关联冻结记录: origTraceNo=" + request.getOrigTraceNo());
        }

        // 强校验未发生任何扣款（全额撤销约束）
        for (AccountFreezeDetailPO freezeDetail : freezeDetails) {
            if (freezeDetail.getDeductedAmount() != null && freezeDetail.getDeductedAmount().compareTo(BigDecimal.ZERO) > 0) {
                throw new ServiceException(ResultCode.PARAM_ERROR,
                        "原预冻结已有扣款(" + freezeDetail.getDeductedAmount() + ")，禁止全额撤销解冻: " + freezeDetail.getVoucherNo());
            }
        }

        // 批量执行全额解冻
        List<AccountFreezeDetailPO> unfrozenDetails = new ArrayList<>();
        for (AccountFreezeDetailPO freezeDetail : freezeDetails) {
            if (freezeDetail.getStatus() == FreezeStatusEnum.FROZEN) {
                freezeDomainService.unfreezeFund(freezeDetail.getVoucherNo(), freezeDetail.getOrigFreezeAmount(), request.getReason());
                AccountFreezeDetailPO updated = freezeDetailRepository.selectByVoucherNo(freezeDetail.getVoucherNo());
                unfrozenDetails.add(ObjectUtil.defaultIfNull(updated, freezeDetail));
            } else {
                unfrozenDetails.add(freezeDetail);
            }
        }

        // 记录撤销流水
        LocalDate accountingDate = journalingDomainService.determineAccountingDate(LocalDateTime.now());
        BigDecimal totalUnfreezeAmount = unfrozenDetails.stream()
                .map(AccountFreezeDetailPO::getOrigFreezeAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        String primaryFreezeId = freezeDetails.get(0).getVoucherNo();
        journalingDomainService.persistUnfreezeRecord(
                request.getTraceNo(), request.getTraceSeq(), request.getOrigTraceNo(), primaryFreezeId,
                origRecord.getBusinessCode(), origRecord.getTradingCode(), origRecord.getPayChannel(),
                totalUnfreezeAmount, LocalDateTime.now(), request.getReason(), accountingDate);

        return assembler.toUnfreezeResponse(request.getTraceNo(), request.getOrigTraceNo(), unfrozenDetails);
    }

    /**
     * 查询记账全流程总览
     */
    public JournalOverviewResponse getJournalOverview(String traceNo) {
        BusinessRecordPO record = businessRecordRepository.selectByTraceNo(traceNo);
        TransactionPO txn = transactionRepository.selectByTraceNo(traceNo);
        if (txn == null) {
            txn = transactionRepository.selectByTxnNo(traceNo);
        }

        List<AccountingVoucherPO> vouchers = accountingVoucherRepository.selectByTraceNo(traceNo);
        if ((vouchers == null || vouchers.isEmpty()) && txn != null) {
            vouchers = accountingVoucherRepository.selectByTxnNo(txn.getTxnNo());
        }

        ManualVoucherApplyPO manualApply = manualVoucherApplyRepository.selectByApplyNo(traceNo);
        if (manualApply == null && txn != null && StrUtil.isNotBlank(txn.getTraceNo())) {
            manualApply = manualVoucherApplyRepository.selectByApplyNo(txn.getTraceNo());
        }
        if (manualApply == null && vouchers != null && !vouchers.isEmpty()) {
            manualApply = manualVoucherApplyRepository.selectByApplyNo(vouchers.get(0).getTraceNo());
        }

        // 如果流水、事务、凭证、手工记账申请全都不存在，才返回 null
        if (record == null && txn == null && (vouchers == null || vouchers.isEmpty()) && manualApply == null) {
            return null;
        }

        // 如果没有常规流水记录（如通过手工凭证审批入账的事务），合成 BusinessRecordPO
        if (record == null) {
            record = buildSyntheticRecord(traceNo, txn, vouchers, manualApply);
        }

        Map<String, List<AccountingVoucherEntryPO>> entryMap = new HashMap<>();
        if (vouchers != null && !vouchers.isEmpty()) {
            List<String> voucherNos = vouchers.stream().map(AccountingVoucherPO::getVoucherNo).toList();
            List<AccountingVoucherEntryPO> allEntries = accountingVoucherRepository.selectEntriesByVoucherNos(voucherNos);
            if (allEntries != null) {
                entryMap = allEntries.stream()
                        .collect(Collectors.groupingBy(AccountingVoucherEntryPO::getVoucherNo));
            }
        }

        AccountFreezeDetailPO freezeDetail = null;
        if (record != null && StrUtil.isNotBlank(record.getOrigFreezeNo())) {
            freezeDetail = freezeDetailRepository.selectByVoucherNo(record.getOrigFreezeNo());
        }
        if (freezeDetail == null) {
            freezeDetail = freezeDetailRepository.selectByTraceNo(traceNo);
        }

        List<BufferPostingDetailPO> bufferDetails = bufferPostingDetailRepository.selectByTraceNo(traceNo);

        List<LocalMessagePO> localMessages = Collections.emptyList();
        if (!entryMap.isEmpty()) {
            List<String> entryIds = entryMap.values().stream()
                    .flatMap(List::stream)
                    .map(AccountingVoucherEntryPO::getEntryId)
                    .filter(Objects::nonNull)
                    .toList();
            if (!entryIds.isEmpty()) {
                localMessages = localMessageService.selectByBusinessKeys(entryIds);
            }
        }

        // 历史数据自愈兼容：若事务中涉及分户数为 0 或空，基于已生成的凭证分录重新计算并持久化
        if (txn != null && (txn.getRelateAccountCount() == null || txn.getRelateAccountCount() == 0)) {
            int distinctCount = AccountingEntryUtil.countDistinctAccountsFromEntryMap(entryMap);
            if (distinctCount > 0) {
                txn.setRelateAccountCount(distinctCount);
                transactionRepository.updateRelateAccountCount(txn.getTxnNo(), distinctCount);
            }
        }

        return assembler.toOverviewResponse(record, txn, vouchers, entryMap, freezeDetail, bufferDetails, localMessages);
    }

    /**
     * 为手工凭证等非业务流水提交产生的事务构建合成流水信息
     */
    private BusinessRecordPO buildSyntheticRecord(String traceNo,
                                                   TransactionPO txn,
                                                   List<AccountingVoucherPO> vouchers,
                                                   ManualVoucherApplyPO manualApply) {
        BusinessRecordPO record = new BusinessRecordPO();
        record.setTraceNo(traceNo);
        record.setTraceSeq(1);

        AccountingVoucherPO voucher = (vouchers != null && !vouchers.isEmpty()) ? vouchers.get(0) : null;

        if (manualApply != null) {
            record.setBusinessCode("MANUAL");
            record.setTradingCode(voucher != null ? StrUtil.blankToDefault(voucher.getTradingCode(), "MANUAL_ENTRY") : "MANUAL_ENTRY");
            record.setPayChannel(voucher != null ? StrUtil.blankToDefault(voucher.getPayChannel(), "INTERNAL") : "INTERNAL");
            record.setTradeType(ObjectUtil.defaultIfNull(manualApply.getTradeType(), voucher != null ? voucher.getTradeType() : TradeTypeEnum.NORMAL));
            record.setAmount(ObjectUtil.defaultIfNull(manualApply.getTotalDebitAmount(), txn != null ? ObjectUtil.defaultIfNull(txn.getAmount(), BigDecimal.ZERO) : BigDecimal.ZERO));
            record.setAccountingDate(ObjectUtil.defaultIfNull(manualApply.getAccountingDate(), txn != null ? ObjectUtil.defaultIfNull(txn.getAccountingDate(), LocalDate.now()) : LocalDate.now()));
            record.setSummary(StrUtil.blankToDefault(manualApply.getSummary(), "手工记账审批入账"));
            record.setTradeTime(ObjectUtil.defaultIfNull(manualApply.getBookkeepingTime(), manualApply.getCreateTime()));
            record.setCreateTime(manualApply.getCreateTime());
        } else if (voucher != null) {
            record.setBusinessCode(StrUtil.blankToDefault(voucher.getBusinessCode(), Constants.SYSTEM_OPERATOR));
            record.setTradingCode(voucher.getTradingCode());
            record.setPayChannel(voucher.getPayChannel());
            record.setTradeType(ObjectUtil.defaultIfNull(voucher.getTradeType(), TradeTypeEnum.NORMAL));
            record.setAmount(ObjectUtil.defaultIfNull(voucher.getAmount(), txn != null ? ObjectUtil.defaultIfNull(txn.getAmount(), BigDecimal.ZERO) : BigDecimal.ZERO));
            record.setAccountingDate(ObjectUtil.defaultIfNull(voucher.getAccountingDate(), txn != null ? ObjectUtil.defaultIfNull(txn.getAccountingDate(), LocalDate.now()) : LocalDate.now()));
            record.setSummary(StrUtil.blankToDefault(voucher.getSummary(), "系统自动或手工凭证事务"));
            record.setTradeTime(ObjectUtil.defaultIfNull(voucher.getTradeTime(), txn != null ? ObjectUtil.defaultIfNull(txn.getCreateTime(), LocalDateTime.now()) : LocalDateTime.now()));
            record.setCreateTime(ObjectUtil.defaultIfNull(voucher.getCreateTime(), LocalDateTime.now()));
        } else if (txn != null) {
            record.setBusinessCode(Constants.SYSTEM_OPERATOR);
            record.setTradingCode("TRANSACTION");
            record.setPayChannel("INTERNAL");
            record.setTradeType(TradeTypeEnum.NORMAL);
            record.setAmount(ObjectUtil.defaultIfNull(txn.getAmount(), BigDecimal.ZERO));
            record.setAccountingDate(ObjectUtil.defaultIfNull(txn.getAccountingDate(), LocalDate.now()));
            record.setSummary("账务事务记录");
            record.setTradeTime(ObjectUtil.defaultIfNull(txn.getCreateTime(), LocalDateTime.now()));
            record.setCreateTime(ObjectUtil.defaultIfNull(txn.getCreateTime(), LocalDateTime.now()));
        }

        if (txn != null && txn.getStatus() == TransactionStatusEnum.SUCCESS) {
            record.setStatus(BusinessRecordStatusEnum.SUCCESS);
        } else if (txn != null && txn.getStatus() == TransactionStatusEnum.FAILED) {
            record.setStatus(BusinessRecordStatusEnum.FAILED);
        } else if (voucher != null && voucher.getStatus() == VoucherStatusEnum.POSTED) {
            record.setStatus(BusinessRecordStatusEnum.SUCCESS);
        } else if (voucher != null && voucher.getStatus() == VoucherStatusEnum.REVERSED) {
            record.setStatus(BusinessRecordStatusEnum.REVERSED);
        } else if (voucher != null && voucher.getStatus() == VoucherStatusEnum.FAILED) {
            record.setStatus(BusinessRecordStatusEnum.FAILED);
        } else {
            record.setStatus(BusinessRecordStatusEnum.PROCESSING);
        }

        return record;
    }

    /**
     * 重试记账（针对 FAILED 状态的流水重新触发生成凭证与过账）
     */
    public JournalOverviewResponse retryJournal(String traceNo) {
        BusinessRecordPO record = businessRecordRepository.selectByTraceNo(traceNo);
        if (record == null) {
            throw new ServiceException(ResultCode.JOURNAL_NOT_FOUND, "未找到流水记录: " + traceNo);
        }
        if (record.getStatus() != BusinessRecordStatusEnum.FAILED) {
            throw new ServiceException(ResultCode.PARAM_ERROR, "仅允许对失败流水发起重试，当前状态: " + record.getStatus());
        }

        String lockKey = RedisKeyConstants.Lock.Idempotent.trace(traceNo, ObjectUtil.defaultIfNull(record.getTraceSeq(), 1));
        return distributedLockTemplate.execute(lockKey, 3, -1, () -> {
            try {
                businessRecordRepository.updateStatusByTraceNo(traceNo, BusinessRecordStatusEnum.PROCESSING);

                List<AccountingVoucherPO> vouchers = accountingVoucherRepository.selectByTraceNo(traceNo);
                AccountingVoucherPO voucher = (vouchers != null && !vouchers.isEmpty()) ? vouchers.get(0) : null;
                if (voucher == null) {
                    VoucherGenerateRequest vReq = new VoucherGenerateRequest();
                    vReq.setTraceNo(traceNo);
                    vReq.setBookkeeperName(Constants.SYSTEM_OPERATOR);
                    VoucherGenerateResponse vResp = voucheringApplicationService.generateVoucher(vReq);
                    if (vResp != null) {
                        voucher = accountingVoucherRepository.selectByVoucherNoSimple(vResp.getVoucherNo());
                    }
                }

                if (voucher != null) {
                    PostingExecuteRequest postReq = new PostingExecuteRequest();
                    postReq.setVoucherNo(voucher.getVoucherNo());
                    postReq.setOperatorName(Constants.SYSTEM_OPERATOR);
                    PostingExecuteResponse postResp = postingApplicationService.executePosting(postReq);
                    if (postResp != null && VoucherStatusEnum.isPosted(postResp.getVoucherStatus())) {
                        businessRecordRepository.updateStatusByTraceNo(traceNo, BusinessRecordStatusEnum.SUCCESS);
                    }
                }

                return getJournalOverview(traceNo);
            } catch (AccountException e) {
                String errorMsg = StrUtil.isNotBlank(e.getMessage()) ? e.getMessage() : e.getResultCode().getMessage();
                markJournalFailed(traceNo, errorMsg);
                throw e;
            } catch (RuntimeException e) {
                log.error("[Journal] 重试记账异常，恢复 FAILED: traceNo={} error={}", traceNo, e.getMessage(), e);
                markJournalFailed(traceNo, "系统异常: " + e.getMessage());
                throw e;
            }
        });
    }

    /**
     * 回滚记账（对未完成、中途异常或掉单的流水执行整笔流水级数据回滚与状态清理）
     */
    public JournalOverviewResponse rollbackJournal(String traceNo, String reason) {
        BusinessRecordPO record = businessRecordRepository.selectByTraceNo(traceNo);
        if (record == null) {
            throw new ServiceException(ResultCode.JOURNAL_NOT_FOUND, "未找到流水记录: " + traceNo);
        }
        if (record.getStatus() == BusinessRecordStatusEnum.SUCCESS) {
            throw new ServiceException(ResultCode.PARAM_ERROR,
                    "记账成功的流水禁止执行技术回滚，请使用凭证红冲操作: " + traceNo);
        }

        rollbackDomainService.executeFullJournalRollback(traceNo, reason);
        return getJournalOverview(traceNo);
    }

    /**
     * 业务流水红冲（对已记账成功的流水执行标准会计红冲）
     */
    public JournalOverviewResponse reversalJournal(String traceNo, String bookkeeperName, String reason) {
        BusinessRecordPO record = businessRecordRepository.selectByTraceNo(traceNo);
        if (record == null) {
            throw new ServiceException(ResultCode.JOURNAL_NOT_FOUND, "未找到流水记录: " + traceNo);
        }
        if (record.getStatus() != BusinessRecordStatusEnum.SUCCESS) {
            throw new ServiceException(ResultCode.PARAM_ERROR,
                    "仅记账成功的流水允许执行红冲，当前状态: " + record.getStatus().getDesc());
        }

        List<AccountingVoucherPO> vouchers = accountingVoucherRepository.selectByTraceNo(traceNo);
        if (vouchers == null || vouchers.isEmpty()) {
            throw new ServiceException(ResultCode.VOUCHER_NOT_FOUND, "未找到该流水关联的记账凭证: " + traceNo);
        }

        AccountingVoucherPO postedVoucher = vouchers.stream()
                .filter(v -> v.getStatus() == VoucherStatusEnum.POSTED)
                .findFirst()
                .orElse(null);

        if (postedVoucher == null) {
            throw new ServiceException(ResultCode.REVERSAL_ORIGINAL_NOT_POSTED, "未找到已过账凭证，无法执行红冲: " + traceNo);
        }

        String effectiveBookkeeper = StrUtil.blankToDefault(bookkeeperName, Constants.SYSTEM_OPERATOR);
        String effectiveSummary = StrUtil.blankToDefault(reason, "业务流水红冲: " + traceNo);

        reversalDomainService.executeReversal(postedVoucher.getVoucherNo(), effectiveBookkeeper, effectiveSummary);
        businessRecordRepository.updateStatusByTraceNo(traceNo, BusinessRecordStatusEnum.REVERSED);
        return getJournalOverview(traceNo);
    }

    /**
     * 掉单流水自动对账与推进补偿（针对长时间处于 PROCESSING 的无异常流水）
     */
    public JournalOverviewResponse reconcileHangingJournal(String traceNo) {
        BusinessRecordPO record = businessRecordRepository.selectByTraceNo(traceNo);
        if (record == null) {
            throw new ServiceException(ResultCode.JOURNAL_NOT_FOUND, "未找到流水记录: " + traceNo);
        }
        if (record.getStatus() != BusinessRecordStatusEnum.PROCESSING) {
            log.info("[RECONCILE] 流水非处理中状态，无需对账补偿: traceNo={}, status={}", traceNo, record.getStatus());
            return getJournalOverview(traceNo);
        }

        String lockKey = RedisKeyConstants.Lock.Idempotent.trace(traceNo, ObjectUtil.defaultIfNull(record.getTraceSeq(), 1));
        return distributedLockTemplate.execute(lockKey, 3, -1, () -> {
            BusinessRecordPO curRecord = businessRecordRepository.selectByTraceNo(traceNo);
            if (curRecord.getStatus() != BusinessRecordStatusEnum.PROCESSING) {
                return getJournalOverview(traceNo);
            }

            List<AccountingVoucherPO> vouchers = accountingVoucherRepository.selectByTraceNo(traceNo);
            AccountingVoucherPO voucher = (vouchers != null && !vouchers.isEmpty()) ? vouchers.get(0) : null;

            // 阶段 1：流水已落库，但凭证未生成
            if (voucher == null) {
                log.info("[RECONCILE] 凭证未生成，尝试补生成凭证并过账: traceNo={}", traceNo);
                try {
                    List<BusinessDetailPO> details = businessDetailRepository.selectByTraceNo(traceNo);
                    Map<String, CustomerCheckInfo> customerMap = buildCustomerMapFromDetails(details);

                    AccountPreCheckResult preCheckResult = accountPreCheckDomainService.preCheckAndOpenAccounts(
                            curRecord.getBusinessCode(), curRecord.getTradingCode(), curRecord.getPayChannel(),
                            customerMap, traceNo);

                    VoucherGenerateRequest voucherReq = new VoucherGenerateRequest();
                    voucherReq.setTraceNo(traceNo);
                    voucherReq.setBookkeeperName(Constants.SYSTEM_OPERATOR);
                    voucherReq.setAccountMapping(preCheckResult != null ? preCheckResult.getAccountMapping() : Collections.emptyMap());
                    VoucherGenerateResponse voucherResp = voucheringApplicationService.generateVoucher(voucherReq);

                    if (voucherResp != null && StrUtil.isNotBlank(voucherResp.getVoucherNo())) {
                        voucher = accountingVoucherRepository.selectByVoucherNoSimple(voucherResp.getVoucherNo());
                    }
                } catch (Exception ex) {
                    log.error("[RECONCILE] 补生成凭证失败，执行流水回滚: traceNo={}", traceNo, ex);
                    rollbackDomainService.executeFullJournalRollback(traceNo, "掉单自动对账失败-凭证生成异常: " + ex.getMessage());
                    return getJournalOverview(traceNo);
                }
            }

            // 阶段 2：凭证已生成，检查过账推进
            if (voucher != null) {
                if (voucher.getStatus() == VoucherStatusEnum.PENDING || voucher.getStatus() == VoucherStatusEnum.FAILED) {
                    log.info("[RECONCILE] 凭证待过账或失败，重新触发过账: voucherNo={}", voucher.getVoucherNo());
                    try {
                        PostingExecuteRequest postReq = new PostingExecuteRequest();
                        postReq.setVoucherNo(voucher.getVoucherNo());
                        postReq.setOperatorName(Constants.SYSTEM_OPERATOR);
                        PostingExecuteResponse postResp = postingApplicationService.executePosting(postReq);

                        if (postResp != null && VoucherStatusEnum.isPosted(postResp.getVoucherStatus())) {
                            businessRecordRepository.updateStatusByTraceNo(traceNo, BusinessRecordStatusEnum.SUCCESS);
                        }
                    } catch (Exception ex) {
                        log.error("[RECONCILE] 补执行过账失败，执行流水回滚: traceNo={}", traceNo, ex);
                        rollbackDomainService.executeFullJournalRollback(traceNo, "掉单自动对账失败-过账执行异常: " + ex.getMessage());
                        return getJournalOverview(traceNo);
                    }
                } else if (voucher.getStatus() == VoucherStatusEnum.POSTING) {
                    // 检查是否所有非缓冲分录实际已全部过账
                    boolean allPosted = asyncPostingDomainService.areAllNonBufferEntriesPosted(voucher.getVoucherNo());
                    if (allPosted) {
                        log.info("[RECONCILE] 异步分录均已过账，补齐状态联动: voucherNo={}", voucher.getVoucherNo());
                        asyncPostingDomainService.updateStatusIfAllNonBufferPosted(voucher.getVoucherNo());
                    } else {
                        // 异步过账未完成，检查是否超过超时阈值（如30分钟）
                        if (curRecord.getCreateTime() != null && curRecord.getCreateTime().isBefore(LocalDateTime.now().minusMinutes(30))) {
                            log.warn("[RECONCILE] 异步过账超过30分钟未完成，触发超时强制回滚: traceNo={}", traceNo);
                            rollbackDomainService.executeFullJournalRollback(traceNo, "掉单超时超过30分钟未完成");
                        }
                    }
                } else if (voucher.getStatus() == VoucherStatusEnum.POSTED) {
                    // 凭证已是 POSTED，流水补更 SUCCESS
                    businessRecordRepository.updateStatusByTraceNo(traceNo, BusinessRecordStatusEnum.SUCCESS);
                }
            }

            return getJournalOverview(traceNo);
        });
    }

    /**
     * 从 BusinessDetailPO 列表构建 CustomerCheckInfo 映射
     */
    private Map<String, CustomerCheckInfo> buildCustomerMapFromDetails(List<BusinessDetailPO> details) {
        Map<String, CustomerCheckInfo> map = new HashMap<>();
        if (details != null) {
            for (BusinessDetailPO detail : details) {
                if (detail != null && StrUtil.isNotBlank(detail.getCustomerId())) {
                    map.putIfAbsent(detail.getCustomerId(),
                            new CustomerCheckInfo(detail.getCustomerType(), null));
                }
            }
        }
        return map;
    }

    /**
     * 查询记账流水精简信息（面向业务调用方）
     */
    public JournalQueryResponse getJournal(String traceNo) {
        BusinessRecordPO record = businessRecordRepository.selectByTraceNo(traceNo);
        if (record == null) {
            return null;
        }
        TransactionPO txn = transactionRepository.selectByTraceNo(traceNo);
        return assembler.toQueryResponse(record, txn);
    }

    /**
     * 查询关联事务状态
     */
    public TransactionStatusResponse getTransactionStatus(String traceNo) {
        TransactionPO txn = transactionRepository.selectByTraceNo(traceNo);
        if (txn == null) {
            txn = transactionRepository.selectByTxnNo(traceNo);
        }
        if (txn == null) {
            return null;
        }
        List<AccountingVoucherPO> vouchers = accountingVoucherRepository.selectByTraceNo(traceNo);
        if ((vouchers == null || vouchers.isEmpty()) && StrUtil.isNotBlank(txn.getTxnNo())) {
            vouchers = accountingVoucherRepository.selectByTxnNo(txn.getTxnNo());
        }
        AccountingVoucherPO voucher = (vouchers != null && !vouchers.isEmpty()) ? vouchers.get(0) : null;
        return assembler.toTransactionStatusResponse(txn, voucher);
    }

    /**
     * 在独立事务中标记流水为 FAILED
     */
    private void markJournalFailed(String traceNo, String reason) {
        try {
            transactionTemplate.execute(status -> {
                businessRecordRepository.updateStatusByTraceNo(traceNo, BusinessRecordStatusEnum.FAILED);
                TransactionPO txn = transactionRepository.selectByTraceNo(traceNo);
                List<AccountingVoucherPO> vouchers = accountingVoucherRepository.selectByTraceNo(traceNo);
                String voucherNo = (vouchers != null && !vouchers.isEmpty()) ? vouchers.get(0).getVoucherNo() : null;
                if (txn != null) {
                    rollbackDomainService.markTransactionFailed(txn.getTxnNo(), voucherNo, traceNo, reason);
                }
                return null;
            });
        } catch (Exception ex) {
            log.error("[Journal] 标记 FAILED 异常: traceNo={}, reason={}", traceNo, reason, ex);
        }
    }

    /**
     * 构建 customerId → CustomerCheckInfo 映射（含 customerType 与 customerName）
     */
    private Map<String, CustomerCheckInfo> buildCustomerMap(List<JournalDetailRequest> details) {
        Map<String, CustomerCheckInfo> map = new HashMap<>();
        if (details != null) {
            for (JournalDetailRequest detail : details) {
                if (detail == null || StrUtil.isBlank(detail.getCustomerId())) {
                    continue;
                }
                CustomerTypeEnum customerType = CustomerTypeEnum.fromValue(detail.getCustomerType());
                String customerName = detail.getCustomerName();
                CustomerCheckInfo existing = map.get(detail.getCustomerId());
                if (existing == null) {
                    map.put(detail.getCustomerId(), new CustomerCheckInfo(customerType, customerName));
                } else {
                    if (StrUtil.isBlank(existing.getCustomerName()) && StrUtil.isNotBlank(customerName)) {
                        existing.setCustomerName(customerName);
                    }
                    if (existing.getCustomerType() == null && customerType != null) {
                        existing.setCustomerType(customerType);
                    }
                }
            }
        }
        return map;
    }

    /**
     * 预校验所有 customerType 值
     */
    private void validateCustomerTypes(List<JournalDetailRequest> details) {
        for (JournalDetailRequest detail : details) {
            if (!CustomerTypeEnum.isValid(detail.getCustomerType())) {
                throw new ServiceException(ResultCode.PARAM_ERROR,
                        "无效的customerType: " + detail.getCustomerType());
            }
        }
    }

    /**
     * 分页查询业务记账流水记录
     */
    public PageResponse<JournalRecordItemResponse> getJournalPage(JournalPageQueryRequest request) {
        Page<BusinessRecordPO> pageResult = businessRecordRepository.selectPage(request);
        List<BusinessRecordPO> records = pageResult.getRecords();
        if (records == null || records.isEmpty()) {
            return PageResponse.<JournalRecordItemResponse>builder()
                    .current(pageResult.getCurrent())
                    .pages(pageResult.getPages())
                    .total(pageResult.getTotal())
                    .list(Collections.emptyList())
                    .build();
        }

        List<String> traceNos = records.stream()
                .map(BusinessRecordPO::getTraceNo)
                .filter(StrUtil::isNotBlank)
                .distinct()
                .collect(Collectors.toList());

        Map<String, List<AccountingVoucherPO>> voucherMap = traceNos.isEmpty()
                ? Collections.emptyMap()
                : accountingVoucherRepository.selectByTraceNos(traceNos).stream()
                        .filter(v -> v.getTraceNo() != null)
                        .collect(Collectors.groupingBy(AccountingVoucherPO::getTraceNo));

        List<JournalRecordItemResponse> items = records.stream()
                .map(record -> {
                    List<AccountingVoucherPO> vouchers = voucherMap.getOrDefault(record.getTraceNo(), Collections.emptyList());
                    return assembler.toJournalRecordItemResponse(record, vouchers);
                })
                .collect(Collectors.toList());

        return PageResponse.<JournalRecordItemResponse>builder()
                .current(pageResult.getCurrent())
                .pages(pageResult.getPages())
                .total(pageResult.getTotal())
                .list(items)
                .build();
    }

    /**
     * 分页查询账务事务记录
     */
    public PageResponse<TransactionRecordItemResponse> getTransactionPage(TransactionPageQueryRequest request) {
        Page<TransactionPO> pageResult = transactionRepository.selectPage(request);
        List<TransactionRecordItemResponse> items = pageResult.getRecords().stream()
                .map(assembler::toTransactionRecordItemResponse)
                .collect(Collectors.toList());
        return PageResponse.<TransactionRecordItemResponse>builder()
                .current(pageResult.getCurrent())
                .pages(pageResult.getPages())
                .total(pageResult.getTotal())
                .list(items)
                .build();
    }
}
