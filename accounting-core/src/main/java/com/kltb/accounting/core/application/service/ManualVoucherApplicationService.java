// accounting-core/src/main/java/com/kltb/accounting/core/application/service/ManualVoucherApplicationService.java
package com.kltb.accounting.core.application.service;

import cn.hutool.core.convert.Convert;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.kltb.accounting.api.constant.AuditDecisionEnum;
import com.kltb.accounting.api.constant.CurrencyConstant;
import com.kltb.accounting.api.constant.DictTypeEnum;
import com.kltb.accounting.api.constant.ManualVoucherSaveActionEnum;
import com.kltb.accounting.api.constant.ResultCode;
import com.kltb.accounting.api.request.*;
import com.kltb.accounting.api.response.*;
import com.kltb.accounting.core.domain.enums.*;
import com.kltb.accounting.core.infrastructure.account.RedisSequenceGenerator;
import com.kltb.accounting.core.infrastructure.persistence.entity.*;
import com.kltb.accounting.core.infrastructure.persistence.repository.*;
import com.kltb.accounting.core.shared.exception.AccountException;
import com.kltb.accounting.core.shared.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import com.kltb.accounting.core.infrastructure.dictionary.DictionaryComponent;
import com.kltb.accounting.core.infrastructure.dictionary.VoucherTypeMeta;

/**
 * 手工记账独立审批流编排应用服务
 * <p>
 * 核心职责：
 * 1. 严格解耦：未过审与驳回数据仅在申请表流转，绝不污染法定凭证表；
 * 2. 四阶段内控：创建制单(Maker) -> 初审(Auditor) -> 复核(Reviewer) -> 记账(Bookkeeper)；
 * 3. 全程可追溯：详细记录每一次审批动作、经办人、意见与时间；
 * 4. 记账落库：复核通过后方可执行记账，原子写入法定凭证并联动过账引擎。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ManualVoucherApplicationService {

    private final ManualVoucherApplyRepository applyRepository;
    private final AccountingVoucherRepository accountingVoucherRepository;
    private final SubjectRepository subjectRepository;
    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final DictionaryRepository dictionaryRepository;
    private final DictionaryComponent dictionaryComponent;
    private final PostingApplicationService postingApplicationService;
    private final TransactionTemplate transactionTemplate;
    private final RedisSequenceGenerator seqGen;

    /**
     * 1. 制单录入 / 编辑保存（支持保存草稿 DRAFT 或提交初审 SUBMIT）
     */
    public String saveOrSubmit(ManualVoucherApplySaveRequest request) {
        // a. 基础与财务平衡校验
        validateEntries(request.getEntries());

        // b. 校验末级科目
        validateSubjects(request.getEntries());

        // c. 账号自动推导与多账号拦截（若未选账号则由科目反查，唯一定位填入，多账号报错）
        resolveAndValidateAccounts(request.getEntries());

        // d. 计算借贷合计
        BigDecimal totalDebit = BigDecimal.ZERO;
        BigDecimal totalCredit = BigDecimal.ZERO;
        for (ManualVoucherApplyEntryRequest entry : request.getEntries()) {
            if (entry.getDebitCredit() == 1) {
                totalDebit = totalDebit.add(entry.getAmount());
            } else if (entry.getDebitCredit() == 2) {
                totalCredit = totalCredit.add(entry.getAmount());
            }
        }

        final BigDecimal finalDebit = totalDebit;
        final BigDecimal finalCredit = totalCredit;

        ManualVoucherSaveActionEnum saveAction = ManualVoucherSaveActionEnum.fromCode(request.getAction());
        boolean isDraft = saveAction.isDraft();
        ManualVoucherApplyStatusEnum targetStatus = isDraft
                ? ManualVoucherApplyStatusEnum.DRAFT
                : ManualVoucherApplyStatusEnum.PENDING_AUDIT;

        // 凭证类型元数据动态解析（通过公共字典组件读取 ext_json，彻底消除硬编码 "ADJUST"）
        VoucherTypeMeta vMeta = dictionaryComponent.getVoucherTypeMeta(request.getVoucherType());
        int defaultTradeType = (vMeta.getTradeType() != null)
                ? vMeta.getTradeType()
                : (vMeta.isAdjustment() ? TradeTypeEnum.ADJUSTMENT.getCode() : TradeTypeEnum.NORMAL.getCode());
        int resolvedTradeType = (request.getTradeType() != null) ? request.getTradeType() : defaultTradeType;

        return transactionTemplate.execute(status -> {
            boolean isNew = StrUtil.isBlank(request.getApplyNo());
            String applyNo = isNew ? generateApplyNo(request.getAccountingDate()) : request.getApplyNo();
            ManualVoucherApplyPO po;

            int attachmentCount = (request.getAttachments() != null && !request.getAttachments().isEmpty())
                    ? request.getAttachments().size() : 0;

            if (isNew) {
                // 全新制单申请
                po = new ManualVoucherApplyPO();
                po.setApplyNo(applyNo);
                po.setVoucherType(defaultIfBlank(request.getVoucherType(), "记账凭证"));
                po.setTradeType(resolvedTradeType);
                po.setAccountingDate(request.getAccountingDate());
                po.setSummary(request.getSummary());
                po.setAttachmentCount(attachmentCount);
                po.setTotalDebitAmount(finalDebit);
                po.setTotalCreditAmount(finalCredit);
                po.setApplyStatus(targetStatus);
                po.setMakerName(request.getMakerName());
                applyRepository.insertApply(po);

                // 写入审计日志（使用规范领域枚举，消除魔法字符）
                recordAuditLog(applyNo,
                        isDraft ? ManualVoucherAuditActionEnum.CREATE_DRAFT : ManualVoucherAuditActionEnum.SUBMIT_AUDIT,
                        request.getMakerName(), ManualVoucherOperatorRoleEnum.MAKER,
                        null, targetStatus.getCode(),
                        isDraft ? "制单人保存凭证草稿" : "制单人提交初审");
            } else {
                // 编辑已有申请（仅允许在草稿或被驳回状态下编辑）
                po = applyRepository.selectByApplyNo(applyNo);
                if (po == null) {
                    throw new ServiceException(ResultCode.DATA_NOT_FOUND, "申请单不存在: " + applyNo);
                }
                if (po.getApplyStatus() != ManualVoucherApplyStatusEnum.DRAFT
                        && po.getApplyStatus() != ManualVoucherApplyStatusEnum.AUDIT_REJECTED
                        && po.getApplyStatus() != ManualVoucherApplyStatusEnum.REVIEW_REJECTED) {
                    throw new ServiceException(ResultCode.OPERATION_NOT_ALLOWED,
                            "当前状态不允许编辑: " + (po.getApplyStatus() != null ? po.getApplyStatus().getDesc() : ""));
                }

                Integer preStatus = po.getApplyStatus() != null ? po.getApplyStatus().getCode() : null;
                po.setVoucherType(defaultIfBlank(request.getVoucherType(), po.getVoucherType()));
                po.setTradeType(request.getTradeType() != null ? request.getTradeType() : defaultTradeType);
                po.setAccountingDate(request.getAccountingDate());
                po.setSummary(request.getSummary());
                po.setAttachmentCount(attachmentCount);
                po.setTotalDebitAmount(finalDebit);
                po.setTotalCreditAmount(finalCredit);
                po.setApplyStatus(targetStatus);
                po.setMakerName(request.getMakerName());
                applyRepository.updateApply(po);

                // 清除旧分录、辅助核算与附件
                applyRepository.deleteEntriesByApplyNo(applyNo);
                applyRepository.deleteAuxiliariesByApplyNo(applyNo);
                applyRepository.deleteAttachmentsByApplyNo(applyNo);

                // 写入审计日志
                recordAuditLog(applyNo,
                        isDraft ? ManualVoucherAuditActionEnum.UPDATE_DRAFT : ManualVoucherAuditActionEnum.RESUBMIT_AUDIT,
                        request.getMakerName(), ManualVoucherOperatorRoleEnum.MAKER,
                        preStatus, targetStatus.getCode(),
                        isDraft ? "制单人修改草稿" : "制单人重新修改并提交初审");
            }

            // 写入分录明细
            List<ManualVoucherApplyEntryPO> entryPOs = new ArrayList<>();
            int row = 1;
            for (ManualVoucherApplyEntryRequest entryReq : request.getEntries()) {
                ManualVoucherApplyEntryPO entryPO = new ManualVoucherApplyEntryPO();
                entryPO.setApplyNo(applyNo);
                entryPO.setRowNum(entryReq.getRowNum() != null ? entryReq.getRowNum() : row++);
                entryPO.setDebitCredit(DebitCreditEnum.fromCode(entryReq.getDebitCredit()));
                entryPO.setSubjectCode(entryReq.getSubjectCode());
                entryPO.setAccountNo(defaultIfBlank(entryReq.getAccountNo(), ""));
                entryPO.setAmount(entryReq.getAmount());
                entryPO.setCurrency(StrUtil.isNotBlank(entryReq.getCurrency()) ? entryReq.getCurrency() : CurrencyConstant.DEFAULT_CURRENCY);
                entryPO.setSummary(defaultIfBlank(entryReq.getSummary(), request.getSummary()));
                entryPO.setUnilateral(entryReq.getUnilateral() != null ? entryReq.getUnilateral() : 1);
                entryPOs.add(entryPO);
            }
            applyRepository.insertEntries(entryPOs);

            // 写入辅助核算分摊明细
            if (request.getAuxiliaries() != null && !request.getAuxiliaries().isEmpty()) {
                List<ManualVoucherApplyAuxiliaryPO> auxPOs = new ArrayList<>();
                for (ManualVoucherApplyAuxiliaryRequest auxReq : request.getAuxiliaries()) {
                    ManualVoucherApplyAuxiliaryPO auxPO = new ManualVoucherApplyAuxiliaryPO();
                    auxPO.setApplyNo(applyNo);
                    auxPO.setEntryRowNum(auxReq.getEntryRowNum() != null ? auxReq.getEntryRowNum() : 1);
                    auxPO.setSubjectCode(defaultIfBlank(auxReq.getSubjectCode(), ""));
                    auxPO.setAuxType(auxReq.getAuxType());
                    auxPO.setAuxTypeName(defaultIfBlank(auxReq.getAuxTypeName(), auxReq.getAuxType()));
                    auxPO.setAuxCode(auxReq.getAuxCode());
                    auxPO.setAuxName(auxReq.getAuxName());
                    auxPO.setChangeDirection(auxReq.getChangeDirection() != null ? auxReq.getChangeDirection() : 1);
                    auxPO.setAmount(auxReq.getAmount() != null ? auxReq.getAmount() : BigDecimal.ZERO);
                    auxPOs.add(auxPO);
                }
                applyRepository.insertAuxiliaries(auxPOs);
            }

            // 写入凭证附件明细
            if (request.getAttachments() != null && !request.getAttachments().isEmpty()) {
                List<ManualVoucherApplyAttachmentPO> attPOs = new ArrayList<>();
                for (ManualVoucherApplyAttachmentRequest attReq : request.getAttachments()) {
                    ManualVoucherApplyAttachmentPO attPO = new ManualVoucherApplyAttachmentPO();
                    attPO.setApplyNo(applyNo);
                    attPO.setFileName(attReq.getFileName());
                    attPO.setFilePath(attReq.getFilePath());
                    attPO.setFileSize(attReq.getFileSize() != null ? attReq.getFileSize() : 0L);
                    attPOs.add(attPO);
                }
                applyRepository.insertAttachments(attPOs);
            }

            return applyNo;
        });
    }

    /**
     * 2. 初审操作（初审通过 PASS -> 待复核；初审驳回 REJECT -> 初审驳回）
     */
    public void audit(ManualVoucherApplyAuditRequest request) {
        ManualVoucherApplyPO po = applyRepository.selectByApplyNo(request.getApplyNo());
        if (po == null) {
            throw new ServiceException(ResultCode.DATA_NOT_FOUND, "申请单不存在: " + request.getApplyNo());
        }
        if (po.getApplyStatus() != ManualVoucherApplyStatusEnum.PENDING_AUDIT) {
            throw new ServiceException(ResultCode.OPERATION_NOT_ALLOWED,
                    "当前申请单不在待初审状态: " + (po.getApplyStatus() != null ? po.getApplyStatus().getDesc() : ""));
        }

        AuditDecisionEnum decision = AuditDecisionEnum.fromCode(request.getAction());
        if (decision == null) {
            throw new ServiceException(ResultCode.PARAM_ERROR, "不支持的初审操作类型: " + request.getAction());
        }
        boolean isPass = decision.isPass();
        if (!isPass && StrUtil.isBlank(request.getOpinion())) {
            throw new ServiceException(ResultCode.PARAM_ERROR, "初审驳回时必须填写驳回原因");
        }

        ManualVoucherApplyStatusEnum newStatus = isPass
                ? ManualVoucherApplyStatusEnum.PENDING_REVIEW
                : ManualVoucherApplyStatusEnum.AUDIT_REJECTED;

        transactionTemplate.executeWithoutResult(status -> {
            Integer preStatus = po.getApplyStatus() != null ? po.getApplyStatus().getCode() : null;
            po.setApplyStatus(newStatus);
            po.setAuditorName(request.getOperatorName());
            po.setAuditTime(LocalDateTime.now());
            po.setAuditOpinion(request.getOpinion());
            applyRepository.updateApply(po);

            recordAuditLog(request.getApplyNo(),
                    isPass ? ManualVoucherAuditActionEnum.AUDIT_PASS : ManualVoucherAuditActionEnum.AUDIT_REJECT,
                    request.getOperatorName(), ManualVoucherOperatorRoleEnum.AUDITOR,
                    preStatus, newStatus.getCode(),
                    request.getOpinion());
        });
    }

    /**
     * 3. 复核操作（复核通过 PASS -> 待记账；复核驳回 REJECT -> 复核驳回）
     */
    public void review(ManualVoucherApplyAuditRequest request) {
        ManualVoucherApplyPO po = applyRepository.selectByApplyNo(request.getApplyNo());
        if (po == null) {
            throw new ServiceException(ResultCode.DATA_NOT_FOUND, "申请单不存在: " + request.getApplyNo());
        }
        if (po.getApplyStatus() != ManualVoucherApplyStatusEnum.PENDING_REVIEW) {
            throw new ServiceException(ResultCode.OPERATION_NOT_ALLOWED,
                    "当前申请单不在待复核状态: " + (po.getApplyStatus() != null ? po.getApplyStatus().getDesc() : ""));
        }

        AuditDecisionEnum decision = AuditDecisionEnum.fromCode(request.getAction());
        if (decision == null) {
            throw new ServiceException(ResultCode.PARAM_ERROR, "不支持的复核操作类型: " + request.getAction());
        }
        boolean isPass = decision.isPass();
        if (!isPass && StrUtil.isBlank(request.getOpinion())) {
            throw new ServiceException(ResultCode.PARAM_ERROR, "复核驳回时必须填写驳回原因");
        }

        ManualVoucherApplyStatusEnum newStatus = isPass
                ? ManualVoucherApplyStatusEnum.PENDING_BOOKKEEPING
                : ManualVoucherApplyStatusEnum.REVIEW_REJECTED;

        transactionTemplate.executeWithoutResult(status -> {
            Integer preStatus = po.getApplyStatus() != null ? po.getApplyStatus().getCode() : null;
            po.setApplyStatus(newStatus);
            po.setReviewerName(request.getOperatorName());
            po.setReviewTime(LocalDateTime.now());
            po.setReviewOpinion(request.getOpinion());
            applyRepository.updateApply(po);

            recordAuditLog(request.getApplyNo(),
                    isPass ? ManualVoucherAuditActionEnum.REVIEW_PASS : ManualVoucherAuditActionEnum.REVIEW_REJECT,
                    request.getOperatorName(), ManualVoucherOperatorRoleEnum.REVIEWER,
                    preStatus, newStatus.getCode(),
                    request.getOpinion());
        });
    }

    /**
     * 4. 确认记账（终审复核通过后由记账员操作，正式生成法定凭证、事务记录并实时联动过账）
     */
    public String executeBookkeeping(ManualVoucherApplyPostRequest request) {
        ManualVoucherApplyPO po = applyRepository.selectByApplyNo(request.getApplyNo());
        if (po == null) {
            throw new ServiceException(ResultCode.DATA_NOT_FOUND, "申请单不存在: " + request.getApplyNo());
        }
        if (po.getApplyStatus() != ManualVoucherApplyStatusEnum.PENDING_BOOKKEEPING) {
            throw new ServiceException(ResultCode.OPERATION_NOT_ALLOWED,
                    "仅允许对【待记账】状态的手工凭证执行记账入账，当前状态=" + (po.getApplyStatus() != null ? po.getApplyStatus().getDesc() : ""));
        }

        List<ManualVoucherApplyEntryPO> applyEntries = applyRepository.selectEntriesByApplyNo(request.getApplyNo());
        if (applyEntries == null || applyEntries.isEmpty()) {
            throw new ServiceException(ResultCode.DATA_NOT_FOUND, "申请分录明细不存在: " + request.getApplyNo());
        }

        // 生成正式凭证号、事务记录与入账逻辑
        String voucherNo = transactionTemplate.execute(status -> {
            String vouNo = generateVoucherNo(po.getAccountingDate());
            String txnNo = generateTxnNo(po.getAccountingDate());

            // 0. 通过公共字典组件动态获取凭证类型元数据（彻底消除硬编码与手工解析）
            VoucherTypeMeta vMeta = dictionaryComponent.getVoucherTypeMeta(po.getVoucherType());
            String tradingCode = StrUtil.isNotBlank(vMeta.getTradingCode()) ? vMeta.getTradingCode() : "TRANSFER";
            String payChannel = StrUtil.isNotBlank(vMeta.getPayChannel()) ? vMeta.getPayChannel() : "INTERNAL";
            int resolvedTradeType = (po.getTradeType() != null)
                    ? po.getTradeType()
                    : (vMeta.getTradeType() != null ? vMeta.getTradeType() : (vMeta.isAdjustment() ? TradeTypeEnum.ADJUSTMENT.getCode() : TradeTypeEnum.NORMAL.getCode()));
            String finalVoucherType = StrUtil.isNotBlank(vMeta.getDictCode()) ? vMeta.getDictCode() : po.getVoucherType();

            // 动态解析币种（由分录继承，若空则使用系统默认币种，绝不硬编码）
            String resolvedCurrency = applyEntries.stream()
                    .map(ManualVoucherApplyEntryPO::getCurrency)
                    .filter(StrUtil::isNotBlank)
                    .findFirst()
                    .orElse(CurrencyConstant.DEFAULT_CURRENCY);

            // 1. 创建事务记录落库 t_transaction
            TransactionPO txn = new TransactionPO();
            txn.setTxnNo(txnNo);
            txn.setTraceNo(po.getApplyNo());
            txn.setAccountingDate(po.getAccountingDate());
            txn.setRelateAccountCount(applyEntries.size());
            txn.setAmount(po.getTotalDebitAmount());
            txn.setCurrency(resolvedCurrency);
            txn.setStatus(TransactionStatusEnum.PROCESSING);
            transactionRepository.save(txn);

            // 2. 正式落库法定凭证主表 t_accounting_voucher
            AccountingVoucherPO voucherPO = new AccountingVoucherPO();
            voucherPO.setVoucherNo(vouNo);
            voucherPO.setTxnNo(txnNo);
            voucherPO.setTraceNo(po.getApplyNo());
            voucherPO.setTraceSeq(1);
            voucherPO.setVoucherType(finalVoucherType);
            voucherPO.setPostingType(PostingTypeEnum.MANUAL);
            voucherPO.setBusinessCode("MANUAL");
            voucherPO.setTradingCode(tradingCode);
            voucherPO.setPayChannel(payChannel);
            voucherPO.setTradeType(TradeTypeEnum.fromCode(resolvedTradeType));
            voucherPO.setTradeTime(LocalDateTime.now());
            voucherPO.setAmount(po.getTotalDebitAmount());
            voucherPO.setStatus(VoucherStatusEnum.PENDING);
            voucherPO.setAccountingDate(po.getAccountingDate());
            voucherPO.setSummary(po.getSummary());
            voucherPO.setAttachmentCount(po.getAttachmentCount() != null ? po.getAttachmentCount() : 1);
            voucherPO.setBookkeeperName(request.getBookkeeperName());
            voucherPO.setReviewerName(po.getReviewerName());
            accountingVoucherRepository.insert(voucherPO);

            // 3. 正式落库 t_accounting_voucher_entry 与关联辅助核算 t_accounting_voucher_auxiliary
            List<ManualVoucherApplyAuxiliaryPO> applyAuxiliaries = applyRepository.selectAuxiliariesByApplyNo(po.getApplyNo());
            Map<Integer, List<ManualVoucherApplyAuxiliaryPO>> auxMap = applyAuxiliaries.stream()
                    .collect(Collectors.groupingBy(ManualVoucherApplyAuxiliaryPO::getEntryRowNum));

            for (ManualVoucherApplyEntryPO applyEntry : applyEntries) {
                // 若手工申请遗留空白账号，根据科目自动反查补全
                if (StrUtil.isBlank(applyEntry.getAccountNo())) {
                    List<AccountPO> accounts = accountRepository.selectBySubjectCode(applyEntry.getSubjectCode());
                    if (accounts.size() == 1) {
                        applyEntry.setAccountNo(accounts.get(0).getAccountNo());
                    } else if (accounts.size() > 1) {
                        throw new AccountException(ResultCode.PARAM_ERROR,
                                "分录科目 [" + applyEntry.getSubjectCode() + "] 关联多个分户账户，无法自动推导，请修改申请指定明确账号");
                    } else {
                        throw new AccountException(ResultCode.ACCOUNT_NOT_FOUND,
                                "分录科目 [" + applyEntry.getSubjectCode() + "] 未开立分户账户，无法记账");
                    }
                }

                String entryId = generateEntryId();
                AccountingVoucherEntryPO entryPO = new AccountingVoucherEntryPO();
                entryPO.setVoucherNo(vouNo);
                entryPO.setEntryId(entryId);
                entryPO.setRowNum(applyEntry.getRowNum());
                entryPO.setSubjectCode(applyEntry.getSubjectCode());
                entryPO.setAccountNo(defaultIfBlank(applyEntry.getAccountNo(), ""));
                entryPO.setDebitCredit(applyEntry.getDebitCredit());
                entryPO.setAmount(applyEntry.getAmount());
                entryPO.setCurrency(StrUtil.isNotBlank(applyEntry.getCurrency()) ? applyEntry.getCurrency() : CurrencyConstant.DEFAULT_CURRENCY);
                entryPO.setSummary(defaultIfBlank(applyEntry.getSummary(), po.getSummary()));
                entryPO.setStatus(VoucherEntryStatusEnum.PENDING);
                entryPO.setAccountingDate(po.getAccountingDate());
                entryPO.setUnilateral(applyEntry.getUnilateral() != null ? applyEntry.getUnilateral() : 1);
                entryPO.setBuffered(0);
                entryPO.setExchangeRate(BigDecimal.ONE);
                entryPO.setUnitPrice(BigDecimal.ZERO);
                entryPO.setQuantity(0);
                entryPO.setPricingUnit("");

                // 核心财务律法：推导分录增减方向 changeDirection（1-增, 2-减）
                AccountSubjectPO subject = subjectRepository.selectByCode(applyEntry.getSubjectCode());
                int subjectBalanceDir = 1; // 默认借方
                if (subject != null && subject.getDebitCredit() != null) {
                    subjectBalanceDir = subject.getDebitCredit().getCode();
                } else if (applyEntry.getSubjectCode() != null && !applyEntry.getSubjectCode().isEmpty()) {
                    char firstChar = applyEntry.getSubjectCode().charAt(0);
                    if (firstChar == '2' || firstChar == '3' || (firstChar == '6' && applyEntry.getSubjectCode().startsWith("60"))) {
                        subjectBalanceDir = 2; // 贷方
                    } else {
                        subjectBalanceDir = 1; // 借方
                    }
                }
                int entryDebitCredit = (applyEntry.getDebitCredit() != null)
                        ? applyEntry.getDebitCredit().getCode()
                        : 1;
                int changeDir = (entryDebitCredit == subjectBalanceDir) ? 1 : 2;
                entryPO.setChangeDirection(changeDir);

                accountingVoucherRepository.insertEntry(entryPO);

                // 关联当前分录的辅助核算分摊项，转入法定凭证辅助核算表
                List<ManualVoucherApplyAuxiliaryPO> matchedAuxList = auxMap.get(applyEntry.getRowNum());
                if (matchedAuxList != null && !matchedAuxList.isEmpty()) {
                    for (ManualVoucherApplyAuxiliaryPO aux : matchedAuxList) {
                        AccountingVoucherAuxiliaryPO voucherAux = new AccountingVoucherAuxiliaryPO();
                        voucherAux.setVoucherNo(vouNo);
                        voucherAux.setEntryId(entryId);
                        voucherAux.setSubjectCode(applyEntry.getSubjectCode());
                        voucherAux.setAuxType(aux.getAuxType());
                        voucherAux.setAuxCode(aux.getAuxCode());
                        voucherAux.setAuxName(aux.getAuxName());
                        voucherAux.setChangeDirection(ChangeDirectionEnum.fromCode(aux.getChangeDirection()));
                        voucherAux.setAmount(aux.getAmount());
                        voucherAux.setAccountingDate(po.getAccountingDate());
                        accountingVoucherRepository.insertAuxiliary(voucherAux);
                    }
                }
            }

            // 4. 正式落库凭证附件 t_accounting_voucher_attachment
            List<ManualVoucherApplyAttachmentPO> applyAttachments = applyRepository.selectAttachmentsByApplyNo(po.getApplyNo());
            if (applyAttachments != null && !applyAttachments.isEmpty()) {
                for (ManualVoucherApplyAttachmentPO att : applyAttachments) {
                    AccountingVoucherAttachmentPO voucherAtt = new AccountingVoucherAttachmentPO();
                    voucherAtt.setVoucherNo(vouNo);
                    voucherAtt.setFilePath(att.getFilePath());
                    accountingVoucherRepository.insertAttachment(voucherAtt);
                }
            }

            // 5. 回填申请表状态与正式凭证号
            Integer preStatus = po.getApplyStatus() != null ? po.getApplyStatus().getCode() : null;
            po.setApplyStatus(ManualVoucherApplyStatusEnum.BOOKED);
            po.setVoucherNo(vouNo);
            po.setBookkeeperName(request.getBookkeeperName());
            po.setBookkeepingTime(LocalDateTime.now());
            applyRepository.updateApply(po);

            // 6. 记录记账流转日志
            recordAuditLog(po.getApplyNo(),
                    ManualVoucherAuditActionEnum.BOOKKEEPING,
                    request.getBookkeeperName(), ManualVoucherOperatorRoleEnum.BOOKKEEPER,
                    preStatus, ManualVoucherApplyStatusEnum.BOOKED.getCode(),
                    defaultIfBlank(request.getRemark(), "确认记账，正式凭证号: " + vouNo));

            return vouNo;
        });

        // 7. 联动过账引擎实时执行过账与余额更新（记账后系统自动过账，无需人工二次操作）
        try {
            PostingExecuteRequest postReq = new PostingExecuteRequest();
            postReq.setVoucherNo(voucherNo);
            postReq.setOperatorName(request.getBookkeeperName());
            postingApplicationService.executePosting(postReq);
            log.info("[手工记账] 凭证记账落库成功并完成实时过账: applyNo={}, voucherNo={}", request.getApplyNo(), voucherNo);
        } catch (Exception e) {
            log.warn("[手工记账] 凭证保存成功，过账处理提示: applyNo={}, voucherNo={}, message={}",
                    request.getApplyNo(), voucherNo, e.getMessage());
        }

        return voucherNo;
    }

    /**
     * 5. 作废草稿或被驳回的申请
     */
    public void cancel(String applyNo, String operatorName, String reason) {
        ManualVoucherApplyPO po = applyRepository.selectByApplyNo(applyNo);
        if (po == null) {
            throw new ServiceException(ResultCode.DATA_NOT_FOUND, "申请单不存在: " + applyNo);
        }
        if (po.getApplyStatus() != ManualVoucherApplyStatusEnum.DRAFT
                && po.getApplyStatus() != ManualVoucherApplyStatusEnum.AUDIT_REJECTED
                && po.getApplyStatus() != ManualVoucherApplyStatusEnum.REVIEW_REJECTED) {
            throw new ServiceException(ResultCode.OPERATION_NOT_ALLOWED,
                    "仅允许对草稿或被驳回申请执行作废操作，当前状态=" + (po.getApplyStatus() != null ? po.getApplyStatus().getDesc() : ""));
        }

        transactionTemplate.executeWithoutResult(status -> {
            Integer preStatus = po.getApplyStatus() != null ? po.getApplyStatus().getCode() : null;
            po.setApplyStatus(ManualVoucherApplyStatusEnum.CANCELLED);
            applyRepository.updateApply(po);

            recordAuditLog(applyNo,
                    ManualVoucherAuditActionEnum.CANCEL,
                    operatorName, ManualVoucherOperatorRoleEnum.MAKER,
                    preStatus, ManualVoucherApplyStatusEnum.CANCELLED.getCode(),
                    defaultIfBlank(reason, "制单人主动作废"));
        });
    }

    /**
     * 6. 综合分页查询申请单列表
     */
    public PageResponse<ManualVoucherApplyPageItemResponse> queryPage(ManualVoucherApplyPageRequest request) {
        IPage<ManualVoucherApplyPO> page = applyRepository.selectPage(request);
        if (page.getRecords().isEmpty()) {
            return PageResponse.<ManualVoucherApplyPageItemResponse>builder()
                    .list(Collections.emptyList())
                    .total(page.getTotal())
                    .pages(page.getPages())
                    .current(page.getCurrent())
                    .build();
        }

        List<String> applyNos = page.getRecords().stream()
                .map(ManualVoucherApplyPO::getApplyNo)
                .collect(Collectors.toList());

        // 批量预取分录（防 N+1）
        List<ManualVoucherApplyEntryPO> allEntries = applyRepository.selectEntriesByApplyNos(applyNos);
        Map<String, List<ManualVoucherApplyEntryPO>> entryMap = allEntries.stream()
                .collect(Collectors.groupingBy(ManualVoucherApplyEntryPO::getApplyNo));

        // 批量预查科目名称缓存
        Set<String> subjectCodes = allEntries.stream()
                .map(ManualVoucherApplyEntryPO::getSubjectCode)
                .filter(StrUtil::isNotBlank)
                .collect(Collectors.toSet());
        Map<String, String> subjectNameMap = loadSubjectNames(subjectCodes);

        List<ManualVoucherApplyPageItemResponse> items = page.getRecords().stream().map(po -> {
            List<ManualVoucherApplyEntryPO> entries = entryMap.getOrDefault(po.getApplyNo(), Collections.emptyList());
            List<ManualVoucherApplyPageItemResponse.ApplyEntryItemDTO> entryDTOs = entries.stream().map(e ->
                    ManualVoucherApplyPageItemResponse.ApplyEntryItemDTO.builder()
                            .id(e.getId())
                            .applyNo(e.getApplyNo())
                            .rowNum(e.getRowNum())
                            .debitCredit(e.getDebitCredit() != null ? e.getDebitCredit().getCode() : null)
                            .debitCreditDesc(e.getDebitCredit() != null ? e.getDebitCredit().getDesc() : "")
                            .subjectCode(e.getSubjectCode())
                            .subjectName(subjectNameMap.getOrDefault(e.getSubjectCode(), e.getSubjectCode()))
                            .accountNo(e.getAccountNo())
                            .amount(e.getAmount())
                            .currency(e.getCurrency())
                            .summary(e.getSummary())
                            .unilateral(e.getUnilateral())
                            .build()
            ).collect(Collectors.toList());

            boolean balanced = po.getTotalDebitAmount() != null
                    && po.getTotalCreditAmount() != null
                    && po.getTotalDebitAmount().compareTo(po.getTotalCreditAmount()) == 0;

            return ManualVoucherApplyPageItemResponse.builder()
                    .id(po.getId())
                    .applyNo(po.getApplyNo())
                    .voucherType(po.getVoucherType())
                    .tradeType(po.getTradeType())
                    .tradeTypeDesc(po.getTradeType() != null && po.getTradeType() == 1 ? "正常" : "调账")
                    .accountingDate(po.getAccountingDate())
                    .summary(po.getSummary())
                    .totalDebitAmount(po.getTotalDebitAmount())
                    .totalCreditAmount(po.getTotalCreditAmount())
                    .isBalanced(balanced)
                    .applyStatus(po.getApplyStatus() != null ? po.getApplyStatus().getCode() : null)
                    .applyStatusDesc(po.getApplyStatus() != null ? po.getApplyStatus().getDesc() : "")
                    .makerName(po.getMakerName())
                    .auditorName(po.getAuditorName())
                    .auditTime(po.getAuditTime())
                    .auditOpinion(po.getAuditOpinion())
                    .reviewerName(po.getReviewerName())
                    .reviewTime(po.getReviewTime())
                    .reviewOpinion(po.getReviewOpinion())
                    .bookkeeperName(po.getBookkeeperName())
                    .bookkeepingTime(po.getBookkeepingTime())
                    .voucherNo(po.getVoucherNo())
                    .createTime(po.getCreateTime())
                    .entries(entryDTOs)
                    .build();
        }).collect(Collectors.toList());

        return PageResponse.<ManualVoucherApplyPageItemResponse>builder()
                .list(items)
                .total(page.getTotal())
                .pages(page.getPages())
                .current(page.getCurrent())
                .build();
    }

    /**
     * 7. 查询申请单全景详情与凭证印签卡片（参考《凭证详情.html》）
     */
    public ManualVoucherApplyDetailResponse getDetail(String applyNo) {
        ManualVoucherApplyPO po = applyRepository.selectByApplyNo(applyNo);
        if (po == null) {
            return null;
        }

        List<ManualVoucherApplyEntryPO> entries = applyRepository.selectEntriesByApplyNo(applyNo);
        Set<String> subjectCodes = entries.stream()
                .map(ManualVoucherApplyEntryPO::getSubjectCode)
                .filter(StrUtil::isNotBlank)
                .collect(Collectors.toSet());
        Map<String, String> subjectNameMap = loadSubjectNames(subjectCodes);

        List<ManualVoucherApplyPageItemResponse.ApplyEntryItemDTO> entryDTOs = entries.stream().map(e ->
                ManualVoucherApplyPageItemResponse.ApplyEntryItemDTO.builder()
                        .id(e.getId())
                        .applyNo(e.getApplyNo())
                        .rowNum(e.getRowNum())
                        .debitCredit(e.getDebitCredit() != null ? e.getDebitCredit().getCode() : null)
                        .debitCreditDesc(e.getDebitCredit() != null ? e.getDebitCredit().getDesc() : "")
                        .subjectCode(e.getSubjectCode())
                        .subjectName(subjectNameMap.getOrDefault(e.getSubjectCode(), e.getSubjectCode()))
                        .accountNo(e.getAccountNo())
                        .amount(e.getAmount())
                        .currency(e.getCurrency())
                        .summary(e.getSummary())
                        .unilateral(e.getUnilateral())
                        .build()
        ).collect(Collectors.toList());

        List<ManualVoucherAuditLogPO> auditLogs = applyRepository.selectAuditLogsByApplyNo(applyNo);
        List<ManualVoucherAuditLogResponse> logDTOs = auditLogs.stream().map(logPO ->
                ManualVoucherAuditLogResponse.builder()
                        .id(logPO.getId())
                        .applyNo(logPO.getApplyNo())
                        .action(logPO.getAction())
                        .actionDesc(logPO.getActionDesc())
                        .operatorName(logPO.getOperatorName())
                        .operatorRole(logPO.getOperatorRole())
                        .preStatus(logPO.getPreStatus())
                        .preStatusDesc(getStatusDesc(logPO.getPreStatus()))
                        .postStatus(logPO.getPostStatus())
                        .postStatusDesc(getStatusDesc(logPO.getPostStatus()))
                        .opinion(logPO.getOpinion())
                        .operateTime(logPO.getOperateTime())
                        .build()
        ).collect(Collectors.toList());

        boolean balanced = po.getTotalDebitAmount() != null
                && po.getTotalCreditAmount() != null
                && po.getTotalDebitAmount().compareTo(po.getTotalCreditAmount()) == 0;

        String words = formatAmountToChinese(po.getTotalDebitAmount());

        // 动态推导凭证印签大标题与字头（通过公共字典组件读取 ext_json，彻底消除硬编码）
        VoucherTypeMeta vMeta = dictionaryComponent.getVoucherTypeMeta(po.getVoucherType());
        String prefix = vMeta.getPrefix();
        String title = vMeta.getTitle();

        String digits = (po.getVoucherNo() != null ? po.getVoucherNo() : po.getApplyNo()).replaceAll("[^0-9]", "");
        String voucherWord = prefix + (digits.isEmpty() ? "" : " " + digits);

        return ManualVoucherApplyDetailResponse.builder()
                .applyNo(po.getApplyNo())
                .voucherNo(po.getVoucherNo())
                .voucherType(po.getVoucherType())
                .voucherWord(voucherWord)
                .voucherTitle(title)
                .postingType(PostingTypeEnum.MANUAL.getCode())
                .postingTypeDesc(PostingTypeEnum.MANUAL.getDesc())
                .tradeType(po.getTradeType())
                .tradeTypeDesc(po.getTradeType() != null && po.getTradeType() == 1 ? "正常" : "调账")
                .accountingDate(po.getAccountingDate())
                .summary(po.getSummary())
                .attachmentCount(po.getAttachmentCount() != null ? po.getAttachmentCount() : 1)
                .totalDebitAmount(po.getTotalDebitAmount())
                .totalCreditAmount(po.getTotalCreditAmount())
                .totalAmountInWords(words)
                .isBalanced(balanced)
                .applyStatus(po.getApplyStatus() != null ? po.getApplyStatus().getCode() : null)
                .applyStatusDesc(po.getApplyStatus() != null ? po.getApplyStatus().getDesc() : "")
                .makerName(po.getMakerName())
                .auditorName(po.getAuditorName())
                .auditTime(po.getAuditTime())
                .auditOpinion(po.getAuditOpinion())
                .reviewerName(po.getReviewerName())
                .reviewTime(po.getReviewTime())
                .reviewOpinion(po.getReviewOpinion())
                .bookkeeperName(po.getBookkeeperName())
                .bookkeepingTime(po.getBookkeepingTime())
                .createTime(po.getCreateTime())
                .entries(entryDTOs)
                .auditLogs(logDTOs)
                .auxiliaries(loadAuxiliaryResponses(applyNo, subjectNameMap))
                .attachments(loadAttachmentResponses(applyNo))
                .build();
    }

    /**
     * 8. 看板状态统计（待初审、待复核、待记账、已记账、被驳回）
     */
    public Map<String, Long> getStatistics() {
        Map<String, Long> map = new HashMap<>();
        map.put("total", applyRepository.countByStatus(null));
        map.put("pendingAudit", applyRepository.countByStatus(ManualVoucherApplyStatusEnum.PENDING_AUDIT));
        map.put("pendingReview", applyRepository.countByStatus(ManualVoucherApplyStatusEnum.PENDING_REVIEW));
        map.put("pendingBookkeeping", applyRepository.countByStatus(ManualVoucherApplyStatusEnum.PENDING_BOOKKEEPING));
        map.put("booked", applyRepository.countByStatus(ManualVoucherApplyStatusEnum.BOOKED));
        long rejected = (applyRepository.countByStatus(ManualVoucherApplyStatusEnum.AUDIT_REJECTED) != null ? applyRepository.countByStatus(ManualVoucherApplyStatusEnum.AUDIT_REJECTED) : 0L)
                + (applyRepository.countByStatus(ManualVoucherApplyStatusEnum.REVIEW_REJECTED) != null ? applyRepository.countByStatus(ManualVoucherApplyStatusEnum.REVIEW_REJECTED) : 0L);
        map.put("rejected", rejected);
        return map;
    }

    // ==================== 内部私有校验与辅助逻辑 ====================

    private void validateEntries(List<ManualVoucherApplyEntryRequest> entries) {
        if (entries == null || entries.size() < 2) {
            throw new AccountException(ResultCode.PARAM_ERROR, "记账凭证至少需要2条借贷分录");
        }

        boolean hasDebit = false;
        boolean hasCredit = false;
        BigDecimal debitSum = BigDecimal.ZERO;
        BigDecimal creditSum = BigDecimal.ZERO;

        for (ManualVoucherApplyEntryRequest entry : entries) {
            if (entry.getAmount() == null || entry.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
                throw new AccountException(ResultCode.PARAM_ERROR, "分录金额必须大于0(遵循绝对值法则)");
            }
            if (entry.getDebitCredit() == 1) {
                hasDebit = true;
                debitSum = debitSum.add(entry.getAmount());
            } else if (entry.getDebitCredit() == 2) {
                hasCredit = true;
                creditSum = creditSum.add(entry.getAmount());
            } else {
                throw new AccountException(ResultCode.PARAM_ERROR, "非法的借贷方向: " + entry.getDebitCredit());
            }
        }

        if (!hasDebit || !hasCredit) {
            throw new AccountException(ResultCode.DEBIT_CREDIT_NOT_BALANCED, "凭证必须同时包含借方与贷方分录");
        }

        if (debitSum.compareTo(creditSum) != 0) {
            throw new AccountException(ResultCode.DEBIT_CREDIT_NOT_BALANCED,
                    String.format("借贷不平衡: 借方合计=%s, 贷方合计=%s, 差额=%s",
                            debitSum, creditSum, debitSum.subtract(creditSum).abs()));
        }
    }

    private void validateSubjects(List<ManualVoucherApplyEntryRequest> entries) {
        for (ManualVoucherApplyEntryRequest entry : entries) {
            AccountSubjectPO subject = subjectRepository.selectByCode(entry.getSubjectCode());
            if (subject == null) {
                throw new AccountException(ResultCode.SUBJECT_NOT_FOUND, "会计科目不存在: " + entry.getSubjectCode());
            }
            if (subject.getLeaf() == null || !subject.getLeaf()) {
                throw new AccountException(ResultCode.SUBJECT_NOT_LEAF, "非末级科目不允许记账: " + entry.getSubjectCode());
            }
        }
    }

    /**
     * 账号自动推导与多账号校验拦截
     */
    private void resolveAndValidateAccounts(List<ManualVoucherApplyEntryRequest> entries) {
        if (entries == null || entries.isEmpty()) {
            return;
        }
        for (ManualVoucherApplyEntryRequest entry : entries) {
            if (StrUtil.isBlank(entry.getAccountNo())) {
                List<AccountPO> accounts = accountRepository.selectBySubjectCode(entry.getSubjectCode());
                if (accounts == null || accounts.isEmpty()) {
                    throw new AccountException(ResultCode.ACCOUNT_NOT_FOUND,
                            "会计科目 [" + entry.getSubjectCode() + "] 未查询到绑定的分户账户，请先开立分户账户");
                }
                if (accounts.size() > 1) {
                    throw new AccountException(ResultCode.PARAM_ERROR,
                            "会计科目 [" + entry.getSubjectCode() + "] 存在多个分户账户(" + accounts.size() + "个)，请明确选择具体分户账号");
                }
                entry.setAccountNo(accounts.get(0).getAccountNo());
            } else {
                AccountPO acc = accountRepository.selectByAccountNo(entry.getAccountNo().trim());
                if (acc == null) {
                    throw new AccountException(ResultCode.ACCOUNT_NOT_FOUND,
                            "分户账户不存在: " + entry.getAccountNo());
                }
                if (!acc.getSubjectCode().equals(entry.getSubjectCode())) {
                    throw new AccountException(ResultCode.PARAM_ERROR,
                            "分户账户 " + entry.getAccountNo() + " 所属科目(" + acc.getSubjectCode() + ")与分录科目(" + entry.getSubjectCode() + ")不一致");
                }
            }
        }
    }

    private void recordAuditLog(String applyNo, ManualVoucherAuditActionEnum action,
                                String operatorName, ManualVoucherOperatorRoleEnum operatorRole,
                                Integer preStatus, Integer postStatus, String opinion) {
        ManualVoucherAuditLogPO logPO = new ManualVoucherAuditLogPO();
        logPO.setApplyNo(applyNo);
        logPO.setAction(action != null ? action.getCode() : "ACTION");
        logPO.setActionDesc(action != null ? action.getDesc() : "");
        logPO.setOperatorName(defaultIfBlank(operatorName, "SYSTEM"));
        logPO.setOperatorRole(operatorRole != null ? operatorRole.getCode() : "OPERATOR");
        logPO.setPreStatus(preStatus);
        logPO.setPostStatus(postStatus);
        logPO.setOpinion(defaultIfBlank(opinion, ""));
        logPO.setOperateTime(LocalDateTime.now());
        applyRepository.insertAuditLog(logPO);
    }

    private Map<String, String> loadSubjectNames(Set<String> subjectCodes) {
        if (subjectCodes.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, String> map = new HashMap<>();
        for (String code : subjectCodes) {
            AccountSubjectPO sub = subjectRepository.selectByCode(code);
            if (sub != null) {
                map.put(code, sub.getSubjectName());
            }
        }
        return map;
    }

    private String getStatusDesc(Integer code) {
        ManualVoucherApplyStatusEnum status = ManualVoucherApplyStatusEnum.fromCode(code);
        return status != null ? status.getDesc() : "";
    }

    private static String defaultIfBlank(String str, String defaultVal) {
        return (str == null || str.trim().isEmpty()) ? defaultVal : str;
    }

    private String generateApplyNo(LocalDate date) {
        try {
            return seqGen.generate("MVA", date != null ? date : LocalDate.now(), 6, 25);
        } catch (Exception e) {
            return "MVA" + (date != null ? date.toString().replace("-", "") : "20260929")
                    + String.format("%06d", (int) (Math.random() * 900000 + 100000));
        }
    }

    private String generateVoucherNo(LocalDate date) {
        try {
            return seqGen.generate("VOU", date != null ? date : LocalDate.now(), 6, 25);
        } catch (Exception e) {
            return "VOU" + (date != null ? date.toString().replace("-", "") : "20260929")
                    + String.format("%06d", (int) (Math.random() * 900000 + 100000));
        }
    }

    private String generateTxnNo(LocalDate date) {
        try {
            return seqGen.generate("TXN", date != null ? date : LocalDate.now(), 6, 25);
        } catch (Exception e) {
            return "TXN" + (date != null ? date.toString().replace("-", "") : "20260930")
                    + String.format("%06d", (int) (Math.random() * 900000 + 100000));
        }
    }

    private String generateEntryId() {
        try {
            return seqGen.generate("ENT", LocalDateTime.now(), "yyyyMMddHHmmssSSS", 4, 2);
        } catch (Exception e) {
            return "ENT" + System.currentTimeMillis() + String.format("%04d", (int) (Math.random() * 9000 + 1000));
        }
    }

    private String formatAmountToChinese(BigDecimal amount) {
        if (amount == null) {
            return "零元整";
        }
        try {
            return Convert.digitToChinese(amount.doubleValue());
        } catch (Exception e) {
            return amount.toPlainString() + " 元";
        }
    }

    private List<ManualVoucherApplyAuxiliaryResponse> loadAuxiliaryResponses(String applyNo, Map<String, String> subjectNameMap) {
        List<ManualVoucherApplyAuxiliaryPO> list = applyRepository.selectAuxiliariesByApplyNo(applyNo);
        if (list == null || list.isEmpty()) {
            return Collections.emptyList();
        }
        return list.stream().map(aux -> ManualVoucherApplyAuxiliaryResponse.builder()
                .id(aux.getId())
                .applyNo(aux.getApplyNo())
                .entryRowNum(aux.getEntryRowNum())
                .subjectCode(aux.getSubjectCode())
                .subjectName(subjectNameMap.getOrDefault(aux.getSubjectCode(), aux.getSubjectCode()))
                .auxType(aux.getAuxType())
                .auxTypeName(aux.getAuxTypeName())
                .auxCode(aux.getAuxCode())
                .auxName(aux.getAuxName())
                .changeDirection(aux.getChangeDirection())
                .changeDirectionDesc(aux.getChangeDirection() != null && aux.getChangeDirection() == 2 ? "减少" : "增加")
                .amount(aux.getAmount())
                .build()
        ).collect(Collectors.toList());
    }

    private List<ManualVoucherApplyAttachmentResponse> loadAttachmentResponses(String applyNo) {
        List<ManualVoucherApplyAttachmentPO> list = applyRepository.selectAttachmentsByApplyNo(applyNo);
        if (list == null || list.isEmpty()) {
            return Collections.emptyList();
        }
        return list.stream().map(att -> ManualVoucherApplyAttachmentResponse.builder()
                .id(att.getId())
                .applyNo(att.getApplyNo())
                .fileName(att.getFileName())
                .filePath(att.getFilePath())
                .fileSize(att.getFileSize())
                .fileType(extractFileExt(att.getFileName()))
                .fileSizeFormatted(formatFileSize(att.getFileSize()))
                .createTime(att.getCreateTime())
                .build()
        ).collect(Collectors.toList());
    }

    private String extractFileExt(String fileName) {
        if (StrUtil.isBlank(fileName)) {
            return "FILE";
        }
        int idx = fileName.lastIndexOf('.');
        if (idx >= 0 && idx < fileName.length() - 1) {
            return fileName.substring(idx + 1).toUpperCase();
        }
        return "FILE";
    }

    private String formatFileSize(Long bytes) {
        if (bytes == null || bytes <= 0) {
            return "0 KB";
        }
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.1f KB", bytes / 1024.0);
        } else {
            return String.format("%.2f MB", bytes / (1024.0 * 1024.0));
        }
    }
}
