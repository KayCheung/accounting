package com.kltb.accounting.core.application.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kltb.accounting.api.constant.ResultCode;
import com.kltb.accounting.api.request.VoucherGenerateRequest;
import com.kltb.accounting.api.request.VoucherPageQueryRequest;
import com.kltb.accounting.api.response.PageResponse;
import com.kltb.accounting.api.response.VoucherFullDetailResponse;
import com.kltb.accounting.api.response.VoucherGenerateResponse;
import com.kltb.accounting.api.response.VoucherPageItemResponse;
import com.kltb.accounting.core.application.assembler.VoucheringAssembler;
import com.kltb.accounting.core.domain.enums.BusinessRecordStatusEnum;
import com.kltb.accounting.core.domain.enums.TradeTypeEnum;
import com.kltb.accounting.core.domain.enums.VoucherStatusEnum;
import com.kltb.accounting.core.domain.service.*;
import com.kltb.accounting.core.domain.service.BufferPostingDomainService;
import com.kltb.accounting.core.domain.service.VoucheringDomainService.AccountingRuleWithDetails;
import com.kltb.accounting.core.domain.service.VoucheringDomainService.JournalWithDetails;
import com.kltb.accounting.core.infrastructure.persistence.entity.*;
import com.kltb.accounting.core.infrastructure.persistence.repository.AccountingVoucherRepository;
import com.kltb.accounting.core.infrastructure.persistence.repository.DictionaryRepository;
import com.kltb.accounting.core.infrastructure.persistence.repository.SubjectRepository;
import com.kltb.accounting.core.infrastructure.persistence.repository.TransactionRepository;
import com.kltb.accounting.core.shared.exception.AccountException;
import com.kltb.accounting.core.shared.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 凭证生成应用服务（P1-1 修复：统一事务边界在此处控制）
 */
@Service
@RequiredArgsConstructor
public class VoucheringApplicationService {

    private final VoucheringDomainService voucheringDomainService;
    private final BufferPostingDomainService bufferPostingDomainService;
    private final AccountingVoucherRepository accountingVoucherRepository;
    private final TransactionRepository transactionRepository;
    private final TransactionTemplate transactionTemplate;
    private final VoucheringAssembler assembler;
    private final SubjectRepository subjectRepository;
    private final DictionaryRepository dictionaryRepository;

    /**
     * 凭证生成用例入口
     */
    public VoucherGenerateResponse generateVoucher(VoucherGenerateRequest request) {
        // 1. 加载流水 + 状态校验
        JournalWithDetails journalWithDetails = voucheringDomainService.loadJournal(request.getTraceNo());
        BusinessRecordPO journal = journalWithDetails.getRecord();
        List<BusinessDetailPO> businessDetails = journalWithDetails.getDetails();

        if (journal.getStatus() != BusinessRecordStatusEnum.PROCESSING) {
            throw new ServiceException(ResultCode.JOURNAL_STATUS_INVALID,
                    "流水状态非法: traceNo=" + request.getTraceNo() + ", status=" + journal.getStatus());
        }

        // 2. 匹配记账规则
        AccountingRuleWithDetails ruleWithDetails = voucheringDomainService.matchRule(
                journal.getBusinessCode(), journal.getTradingCode(), journal.getPayChannel());

        AccountingRulePO rule = ruleWithDetails.getRule();
        List<AccountingRuleDetailPO> ruleDetails = ruleWithDetails.getDetails();
        Map<Long, List<AccountingRuleAuxiliaryPO>> auxMap = ruleWithDetails.getAuxMap();

        // 查询 txnNo（从 t_transaction 表获取）
        TransactionPO transaction = transactionRepository.selectByTraceNo(journal.getTraceNo());
        if (transaction == null) {
            throw new ServiceException(ResultCode.SYSTEM_ERROR,
                    "事务记录不存在: traceNo=" + journal.getTraceNo());
        }
        String txnNo = transaction.getTxnNo();

        // 3. 逐规则明细行计算分录
        List<VoucherEntryData> entries = new ArrayList<>();
        List<AuxiliaryItemData> allAuxItems = new ArrayList<>();
        List<BufferPostingDetailData> allBufferData = new ArrayList<>();

        for (AccountingRuleDetailPO ruleDetail : ruleDetails) {
            // 找到匹配的款项类型业务明细
            BusinessDetailPO matchedDetail = findMatchingDetail(
                    businessDetails, ruleDetail.getFundsType());
            if (matchedDetail == null) {
                throw new AccountException(ResultCode.RULE_NOT_FOUND,
                        "未找到匹配的款项类型: fundsType=" + ruleDetail.getFundsType());
            }

            // 计算分录金额
            BigDecimal amount = voucheringDomainService.calculateEntryAmount(
                    ruleDetail, matchedDetail);

            // 确定账户编号（P1-2 修复：使用 customerId 作为 fallback）
            String accountNo = resolveAccountNo(ruleDetail, matchedDetail);

            // 构建分录数据
            VoucherEntryData entry = new VoucherEntryData(
                    null,  // entryId 由 persistVoucher 内部生成
                    null,  // voucherNo 由 persistVoucher 内部生成
                    ruleDetail.getRowNum(),
                    ruleDetail.getSubjectCode(),
                    accountNo,
                    ruleDetail.getDebitCredit() != null ? ruleDetail.getDebitCredit().getCode() : null,
                    amount,
                    ruleDetail.getCurrency(),
                    ruleDetail.getSummary(),
                    journal.getAccountingDate(),
                    ruleDetail.getUnilateral() != null && ruleDetail.getUnilateral(),
                    false  // isBuffered 默认为 false，后续缓冲匹配后更新
            );
            entries.add(entry);

            // P1-3 修复：从 auxMap 获取该分录行的辅助核算配置
            List<AccountingRuleAuxiliaryPO> auxConfigs = auxMap.get(ruleDetail.getId());
            if (auxConfigs != null && !auxConfigs.isEmpty()) {
                List<AuxiliaryItemData> auxItems = bufferPostingDomainService
                        .calculateAuxiliaryAllocation(entry, auxConfigs);
                allAuxItems.addAll(auxItems);
            }
        }

        // 4. 借贷平衡校验
        voucheringDomainService.validateDebitCreditBalance(entries);

        // 5. 在统一事务中完成所有持久化操作（P1-1 修复）
        String voucherNo = transactionTemplate.execute(status -> {
            // 5a. 写入凭证 + 分录
            String vouNo = voucheringDomainService.persistVoucher(
                    journal, rule, entries, request.getBookkeeperName());

            // 5b. 写入辅助核算项
            bufferPostingDomainService.persistAuxiliaryItems(allAuxItems);

            // 5c. 缓冲规则匹配
            for (VoucherEntryData entry : entries) {
                entry.setVoucherNo(vouNo);
                BufferPostingRulePO bufferRule = bufferPostingDomainService.matchBufferRule(
                        entry, journal.getBusinessCode(), journal.getTradingCode(), journal.getPayChannel());

                if (bufferRule != null) {
                    entry.setIsBuffered(true);
                    Long sharding = bufferPostingDomainService.calculateSharding(entry.getAccountNo());
                    allBufferData.add(buildBufferPostingData(
                            bufferRule, entry, journal, txnNo, sharding));
                }
            }

            // 5d. 写入缓冲记账明细
            bufferPostingDomainService.persistBufferPostingDetails(allBufferData);

            // 5e. 回填 txnNo
            int affected = accountingVoucherRepository.updateTxnNoByVoucherNo(vouNo, txnNo);
            if (affected != 1) {
                throw new ServiceException(ResultCode.SYSTEM_ERROR,
                        "txnNo 回填失败: voucherNo=" + vouNo);
            }

            return vouNo;
        });

        // 6. 查询持久化后的凭证，返回响应
        List<AccountingVoucherPO> vouchers = accountingVoucherRepository.selectByTraceNo(request.getTraceNo());
        AccountingVoucherPO voucher = vouchers.isEmpty() ? null : vouchers.get(0);
        return assembler.toResponse(voucher, entries, txnNo);
    }

    /**
     * 按凭证号查询凭证详情
     */
    public VoucherGenerateResponse getVoucherByNo(String voucherNo) {
        AccountingVoucherPO voucher = accountingVoucherRepository.selectByVoucherNo(voucherNo);
        if (voucher == null) {
            return null;
        }
        List<VoucherEntryData> entries = accountingVoucherRepository.selectEntriesByVoucherNo(voucherNo)
                .stream()
                .map(po -> new VoucherEntryData(
                        po.getEntryId(),
                        po.getVoucherNo(),
                        po.getRowNum(),
                        po.getSubjectCode(),
                        po.getAccountNo(),
                        po.getDebitCredit() != null ? po.getDebitCredit().getCode() : null,
                        po.getAmount(),
                        po.getCurrency(),
                        po.getSummary(),
                        po.getAccountingDate(),
                        null, null
                ))
                .toList();
        return assembler.toResponse(voucher, entries, voucher.getTxnNo());
    }

    /**
     * 按流水号查询凭证列表
     */
    public List<VoucherGenerateResponse> getVouchersByTraceNo(String traceNo) {
        return accountingVoucherRepository.selectByTraceNo(traceNo)
                .stream()
                .map(voucher -> {
                    List<VoucherEntryData> entries = accountingVoucherRepository.selectEntriesByVoucherNo(voucher.getVoucherNo())
                            .stream()
                            .map(po -> new VoucherEntryData(
                                    po.getEntryId(),
                                    po.getVoucherNo(),
                                    po.getRowNum(),
                                    po.getSubjectCode(),
                                    po.getAccountNo(),
                                    po.getDebitCredit() != null ? po.getDebitCredit().getCode() : null,
                                    po.getAmount(),
                                    po.getCurrency(),
                                    po.getSummary(),
                                    po.getAccountingDate(),
                                    null, null
                            ))
                            .toList();
                    return assembler.toResponse(voucher, entries, voucher.getTxnNo());
                })
                .toList();
    }

    /**
     * 找到匹配的款项类型业务明细
     */
    private BusinessDetailPO findMatchingDetail(List<BusinessDetailPO> details, String fundsType) {
        return details.stream()
                .filter(d -> fundsType.equals(d.getFundsType()))
                .findFirst()
                .orElse(null);
    }

    /**
     * 确定账户编号
     * <p>
     * 当前使用 customerId 作为临时占位（凭证先行模式，账户尚未建立时）。
     * Step 11/12 重构：需在此处引入 AccountPreCheckDomainService 的预开户结果，
     * 将 customerId 替换为真实的 accountNo。
     */
    private String resolveAccountNo(AccountingRuleDetailPO ruleDetail, BusinessDetailPO detail) {
        return detail.getCustomerId();
    }

    /**
     * 构建缓冲记账明细数据
     */
    private BufferPostingDetailData buildBufferPostingData(
            BufferPostingRulePO bufferRule,
            VoucherEntryData entry,
            BusinessRecordPO journal,
            String txnNo,
            Long sharding) {

        return new BufferPostingDetailData(
                bufferRule.getId(),
                bufferRule.getBufferMode() != null ? bufferRule.getBufferMode().getCode() : null,
                entry.getVoucherNo(),
                entry.getEntryId(),
                txnNo,
                journal.getTraceNo(),
                journal.getTraceSeq(),
                journal.getBusinessCode(),
                journal.getTradingCode(),
                journal.getPayChannel(),
                journal.getTradeType() != null ? journal.getTradeType().getCode() : null,
                journal.getTradeTime(),
                entry.getAccountNo(),
                entry.getDebitCredit(),
                entry.getCurrency(),
                entry.getAmount(),
                entry.getAccountingDate(),
                entry.getSummary(),
                sharding
        );
    }

    /**
     * 记账凭证综合多维分页查询
     */
    public PageResponse<VoucherPageItemResponse> queryVoucherPage(VoucherPageQueryRequest request) {
        Page<AccountingVoucherPO> pageParam = new Page<>(
                request.getPageNo() != null && request.getPageNo() > 0 ? request.getPageNo() : 1,
                request.getPageSize() != null && request.getPageSize() > 0 ? request.getPageSize() : 10
        );

        LambdaQueryWrapper<AccountingVoucherPO> wrapper = new LambdaQueryWrapper<AccountingVoucherPO>()
                .like(StrUtil.isNotBlank(request.getVoucherNo()), AccountingVoucherPO::getVoucherNo, StrUtil.trim(request.getVoucherNo()))
                .eq(StrUtil.isNotBlank(request.getTraceNo()), AccountingVoucherPO::getTraceNo, StrUtil.trim(request.getTraceNo()))
                .eq(StrUtil.isNotBlank(request.getTxnNo()), AccountingVoucherPO::getTxnNo, StrUtil.trim(request.getTxnNo()))
                .eq(request.getStatus() != null, AccountingVoucherPO::getStatus, request.getStatus())
                .eq(StrUtil.isNotBlank(request.getVoucherType()), AccountingVoucherPO::getVoucherType, StrUtil.trim(request.getVoucherType()))
                .eq(request.getPostingType() != null, AccountingVoucherPO::getPostingType, request.getPostingType())
                .eq(StrUtil.isNotBlank(request.getBusinessCode()), AccountingVoucherPO::getBusinessCode, StrUtil.trim(request.getBusinessCode()))
                .eq(StrUtil.isNotBlank(request.getTradingCode()), AccountingVoucherPO::getTradingCode, StrUtil.trim(request.getTradingCode()))
                .eq(StrUtil.isNotBlank(request.getPayChannel()), AccountingVoucherPO::getPayChannel, StrUtil.trim(request.getPayChannel()))
                .eq(request.getTradeType() != null, AccountingVoucherPO::getTradeType, request.getTradeType())
                .ge(request.getStartDate() != null, AccountingVoucherPO::getAccountingDate, request.getStartDate())
                .le(request.getEndDate() != null, AccountingVoucherPO::getAccountingDate, request.getEndDate())
                .ge(request.getCreateStartTime() != null, AccountingVoucherPO::getCreateTime, request.getCreateStartTime())
                .le(request.getCreateEndTime() != null, AccountingVoucherPO::getCreateTime, request.getCreateEndTime())
                .orderByDesc(AccountingVoucherPO::getId);

        IPage<AccountingVoucherPO> resultPage = accountingVoucherRepository.selectPage(pageParam, wrapper);
        List<AccountingVoucherPO> records = resultPage.getRecords();

        if (records == null || records.isEmpty()) {
            return PageResponse.<VoucherPageItemResponse>builder()
                    .current(resultPage.getCurrent())
                    .pages(resultPage.getPages())
                    .total(resultPage.getTotal())
                    .list(Collections.emptyList())
                    .build();
        }

        // 1. 批量预取分录（单次 SQL 查询，防 N+1）
        List<String> voucherNos = records.stream()
                .map(AccountingVoucherPO::getVoucherNo)
                .filter(StrUtil::isNotBlank)
                .collect(Collectors.toList());
        List<AccountingVoucherEntryPO> allEntries = accountingVoucherRepository.selectEntriesByVoucherNos(voucherNos);
        Map<String, List<AccountingVoucherEntryPO>> entryMap = allEntries.stream()
                .collect(Collectors.groupingBy(AccountingVoucherEntryPO::getVoucherNo));

        // 2. 批量查询分录涉及的会计科目名称
        Set<String> subjectCodes = allEntries.stream()
                .map(AccountingVoucherEntryPO::getSubjectCode)
                .filter(StrUtil::isNotBlank)
                .collect(Collectors.toSet());
        Map<String, String> subjectNameMap = subjectRepository.selectSubjectNameMap(subjectCodes);

        // 3. 字典名称映射（凭证类型）
        Map<String, String> voucherTypeNameMap = getVoucherTypeNameMap();

        // 4. 组装响应列表
        List<VoucherPageItemResponse> list = records.stream()
                .map(v -> assembler.toPageItem(v, entryMap.get(v.getVoucherNo()), subjectNameMap, voucherTypeNameMap))
                .collect(Collectors.toList());

        return PageResponse.<VoucherPageItemResponse>builder()
                .current(resultPage.getCurrent())
                .pages(resultPage.getPages())
                .total(resultPage.getTotal())
                .list(list)
                .build();
    }

    /**
     * 查询凭证全景档案详情（凭证头 + 借贷分录 + 辅助核算分摊项 + 附件列表 + 红冲溯源）
     */
    public VoucherFullDetailResponse getVoucherDetail(String voucherNo) {
        AccountingVoucherPO voucher = accountingVoucherRepository.selectByVoucherNo(voucherNo);
        if (voucher == null) {
            return null;
        }

        List<AccountingVoucherEntryPO> entries = accountingVoucherRepository.selectEntriesByVoucherNo(voucherNo);
        List<AccountingVoucherAuxiliaryPO> auxiliaries = accountingVoucherRepository.selectAuxiliaryByVoucherNo(voucherNo);
        List<AccountingVoucherAttachmentPO> attachments = accountingVoucherRepository.selectAttachmentsByVoucherNo(voucherNo);

        // 检查红冲记录
        List<AccountingVoucherPO> reversals = accountingVoucherRepository.selectReversalByOrig(voucherNo);
        String reversalVoucherNo = reversals.isEmpty() ? null : reversals.get(0).getVoucherNo();
        boolean canReversal = voucher.getStatus() == VoucherStatusEnum.POSTED
                && voucher.getTradeType() != TradeTypeEnum.RED
                && reversals.isEmpty();

        // 查科目名称
        Set<String> subjectCodes = entries.stream()
                .map(AccountingVoucherEntryPO::getSubjectCode)
                .filter(StrUtil::isNotBlank)
                .collect(Collectors.toSet());
        Map<String, String> subjectNameMap = subjectRepository.selectSubjectNameMap(subjectCodes);

        Map<String, String> voucherTypeNameMap = getVoucherTypeNameMap();

        return assembler.toFullDetail(
                voucher, entries, auxiliaries, attachments,
                reversalVoucherNo, canReversal, subjectNameMap, voucherTypeNameMap
        );
    }

    /**
     * 获取凭证类型字典编码与名称映射
     */
    private Map<String, String> getVoucherTypeNameMap() {
        try {
            List<DictionaryPO> dicts = dictionaryRepository.selectByType("VOUCHER_TYPE");
            return dicts.stream().collect(Collectors.toMap(
                    DictionaryPO::getDictCode,
                    DictionaryPO::getDictName,
                    (k1, k2) -> k1
            ));
        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }
}
