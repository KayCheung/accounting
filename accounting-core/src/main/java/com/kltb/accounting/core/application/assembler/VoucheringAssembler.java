package com.kltb.accounting.core.application.assembler;

import cn.hutool.core.util.StrUtil;
import com.kltb.accounting.api.response.*;
import com.kltb.accounting.core.domain.enums.DebitCreditEnum;
import com.kltb.accounting.core.domain.enums.PostingTypeEnum;
import com.kltb.accounting.core.domain.enums.TradeTypeEnum;
import com.kltb.accounting.core.domain.enums.VoucherStatusEnum;
import com.kltb.accounting.core.domain.service.VoucherEntryData;
import com.kltb.accounting.core.infrastructure.dictionary.DictionaryComponent;
import com.kltb.accounting.core.infrastructure.dictionary.VoucherTypeMeta;
import com.kltb.accounting.core.infrastructure.persistence.entity.AccountingVoucherAttachmentPO;
import com.kltb.accounting.core.infrastructure.persistence.entity.AccountingVoucherAuxiliaryPO;
import com.kltb.accounting.core.infrastructure.persistence.entity.AccountingVoucherEntryPO;
import com.kltb.accounting.core.infrastructure.persistence.entity.AccountingVoucherPO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 凭证生成 PO ↔ DTO 转换器
 */
@Component
public class VoucheringAssembler {

    private final DictionaryComponent dictionaryComponent;

    public VoucheringAssembler() {
        this.dictionaryComponent = null;
    }

    @Autowired(required = false)
    public VoucheringAssembler(DictionaryComponent dictionaryComponent) {
        this.dictionaryComponent = dictionaryComponent;
    }

    /**
     * 将凭证生成结果组装为响应 DTO
     */
    public VoucherGenerateResponse toResponse(
            AccountingVoucherPO voucher,
            List<VoucherEntryData> entries,
            String txnNo) {

        long bufferedCount = entries.stream()
                .filter(e -> Boolean.TRUE.equals(e.getIsBuffered()))
                .count();
        long normalCount = entries.size() - bufferedCount;

        return new VoucherGenerateResponse(
                voucher.getVoucherNo(),
                voucher.getTraceNo(),
                txnNo,
                voucher.getVoucherType(),
                voucher.getAmount(),
                voucher.getAccountingDate(),
                Optional.ofNullable(voucher.getStatus()).map(VoucherStatusEnum::getCode).orElse(null),
                voucher.getTradeTime(),
                voucher.getSummary(),
                entries.stream().map(this::toEntryResponse).collect(Collectors.toList()),
                (int) bufferedCount,
                (int) normalCount
        );
    }

    public VoucherEntryResponse toEntryResponse(VoucherEntryData data) {
        return new VoucherEntryResponse(
                data.getEntryId(),
                data.getRowNum(),
                data.getSubjectCode(),
                data.getAccountNo(),
                data.getDebitCredit(),
                data.getAmount(),
                data.getCurrency(),
                data.getSummary(),
                data.getIsUnilateral(),
                data.getIsBuffered(),
                data.getAccountingDate()
        );
    }

    /**
     * 将凭证 PO + 分录 PO 列表转为分页列表项 DTO
     */
    public VoucherPageItemResponse toPageItem(
            AccountingVoucherPO po,
            List<AccountingVoucherEntryPO> entries,
            Map<String, String> subjectNameMap,
            Map<String, String> voucherTypeNameMap) {

        List<VoucherEntryResponse> entryResponses = entries != null
                ? entries.stream()
                .map(e -> toEntryResponse(e, subjectNameMap != null ? subjectNameMap.get(e.getSubjectCode()) : null))
                .collect(Collectors.toList())
                : Collections.emptyList();

        int debitCount = 0;
        int creditCount = 0;
        BigDecimal debitAmount = BigDecimal.ZERO;
        BigDecimal creditAmount = BigDecimal.ZERO;

        for (AccountingVoucherEntryPO e : (entries != null ? entries : Collections.<AccountingVoucherEntryPO>emptyList())) {
            if (e.getDebitCredit() == DebitCreditEnum.DEBIT) {
                debitCount++;
                if (e.getAmount() != null) {
                    debitAmount = debitAmount.add(e.getAmount());
                }
            } else if (e.getDebitCredit() == DebitCreditEnum.CREDIT) {
                creditCount++;
                if (e.getAmount() != null) {
                    creditAmount = creditAmount.add(e.getAmount());
                }
            }
        }

        boolean isBalanced = debitAmount.compareTo(creditAmount) == 0;
        String voucherTypeName = voucherTypeNameMap != null && po.getVoucherType() != null
                ? voucherTypeNameMap.getOrDefault(po.getVoucherType(), po.getVoucherType())
                : po.getVoucherType();

        return VoucherPageItemResponse.builder()
                .id(po.getId())
                .voucherNo(po.getVoucherNo())
                .txnNo(po.getTxnNo())
                .traceNo(po.getTraceNo())
                .traceSeq(po.getTraceSeq())
                .voucherType(po.getVoucherType())
                .voucherTypeName(voucherTypeName)
                .postingType(po.getPostingType() != null ? po.getPostingType().getCode() : null)
                .postingTypeDesc(po.getPostingType() != null ? po.getPostingType().getDesc() : null)
                .businessCode(po.getBusinessCode())
                .tradingCode(po.getTradingCode())
                .payChannel(po.getPayChannel())
                .tradeType(po.getTradeType() != null ? po.getTradeType().getCode() : null)
                .tradeTypeDesc(po.getTradeType() != null ? po.getTradeType().getDesc() : null)
                .tradeTime(po.getTradeTime())
                .amount(po.getAmount())
                .status(po.getStatus() != null ? po.getStatus().getCode() : null)
                .statusDesc(po.getStatus() != null ? po.getStatus().getDesc() : null)
                .postTime(po.getPostTime())
                .accountingDate(po.getAccountingDate())
                .summary(po.getSummary())
                .attachmentCount(po.getAttachmentCount())
                .origVoucherNo(po.getOrigVoucherNo())
                .bookkeeperName(po.getBookkeeperName())
                .reviewerName(po.getReviewerName())
                .failReason(po.getFailReason())
                .retryCount(po.getRetryCount())
                .skipFlag(po.getSkipFlag())
                .createTime(po.getCreateTime())
                .entries(entryResponses)
                .debitCount(debitCount)
                .creditCount(creditCount)
                .debitAmount(debitAmount)
                .creditAmount(creditAmount)
                .isBalanced(isBalanced)
                .build();
    }

    /**
     * 将凭证分录 PO 转换为分录行响应 DTO
     */
    public VoucherEntryResponse toEntryResponse(AccountingVoucherEntryPO po, String subjectName) {
        return VoucherEntryResponse.builder()
                .entryId(po.getEntryId())
                .rowNum(po.getRowNum())
                .subjectCode(po.getSubjectCode())
                .subjectName(subjectName)
                .accountNo(po.getAccountNo())
                .debitCredit(po.getDebitCredit() != null ? po.getDebitCredit().getCode() : null)
                .amount(po.getAmount())
                .currency(po.getCurrency())
                .summary(po.getSummary())
                .isUnilateral(Integer.valueOf(1).equals(po.getUnilateral()))
                .isBuffered(Integer.valueOf(1).equals(po.getBuffered()))
                .accountingDate(po.getAccountingDate())
                .build();
    }

    /**
     * 辅助核算 PO 转 DTO
     */
    public VoucherAuxiliaryResponse toAuxiliaryResponse(AccountingVoucherAuxiliaryPO po) {
        return VoucherAuxiliaryResponse.builder()
                .entryId(po.getEntryId())
                .subjectCode(po.getSubjectCode())
                .auxType(po.getAuxType())
                .auxCode(po.getAuxCode())
                .auxName(po.getAuxName())
                .changeDirection(po.getChangeDirection() != null ? po.getChangeDirection().getCode() : null)
                .amount(po.getAmount())
                .accountingDate(po.getAccountingDate())
                .build();
    }

    /**
     * 附件 PO 转 DTO
     */
    public VoucherAttachmentResponse toAttachmentResponse(AccountingVoucherAttachmentPO po) {
        return VoucherAttachmentResponse.builder()
                .id(po.getId())
                .voucherNo(po.getVoucherNo())
                .filePath(po.getFilePath())
                .createTime(po.getCreateTime())
                .build();
    }

    /**
     * 全景档案 DTO 组装
     */
    public VoucherFullDetailResponse toFullDetail(
            AccountingVoucherPO po,
            List<AccountingVoucherEntryPO> entries,
            List<AccountingVoucherAuxiliaryPO> auxiliaries,
            List<AccountingVoucherAttachmentPO> attachments,
            String reversalVoucherNo,
            boolean canReversal,
            Map<String, String> subjectNameMap,
            Map<String, String> voucherTypeNameMap) {
        return toFullDetail(po, entries, auxiliaries, attachments, reversalVoucherNo, canReversal, subjectNameMap, voucherTypeNameMap, null);
    }

    /**
     * 全景档案 DTO 组装（支持传入凭证类型元数据 VoucherTypeMeta）
     */
    public VoucherFullDetailResponse toFullDetail(
            AccountingVoucherPO po,
            List<AccountingVoucherEntryPO> entries,
            List<AccountingVoucherAuxiliaryPO> auxiliaries,
            List<AccountingVoucherAttachmentPO> attachments,
            String reversalVoucherNo,
            boolean canReversal,
            Map<String, String> subjectNameMap,
            Map<String, String> voucherTypeNameMap,
            VoucherTypeMeta voucherTypeMeta) {

        VoucherPageItemResponse item = toPageItem(po, entries, subjectNameMap, voucherTypeNameMap);

        List<VoucherAuxiliaryResponse> auxResponses = auxiliaries != null
                ? auxiliaries.stream().map(this::toAuxiliaryResponse).collect(Collectors.toList())
                : Collections.emptyList();

        List<VoucherAttachmentResponse> attachResponses = attachments != null
                ? attachments.stream().map(this::toAttachmentResponse).collect(Collectors.toList())
                : Collections.emptyList();

        // 动态推导凭证印签大标题与字头（优先读取字典 ext_json 元数据，彻底根除硬编码）
        VoucherTypeMeta meta = voucherTypeMeta;
        if (meta == null && dictionaryComponent != null) {
            meta = dictionaryComponent.getVoucherTypeMeta(po.getVoucherType());
        }

        String title = meta != null && StrUtil.isNotBlank(meta.getTitle())
                ? meta.getTitle()
                : (item.getVoucherTypeName() != null ? item.getVoucherTypeName() : "记账凭证");
        String prefix = meta != null && StrUtil.isNotBlank(meta.getPrefix())
                ? meta.getPrefix()
                : (title.length() > 0 ? title.substring(0, 1) : "记");

        String digits = po.getVoucherNo() != null ? po.getVoucherNo().replaceAll("[^0-9]", "") : "";
        String voucherWord = prefix + (digits.isEmpty() ? "" : " " + digits);

        BigDecimal totalAmt = item.getDebitAmount() != null && item.getDebitAmount().compareTo(BigDecimal.ZERO) > 0
                ? item.getDebitAmount()
                : (item.getAmount() != null ? item.getAmount() : BigDecimal.ZERO);
        String words;
        try {
            words = cn.hutool.core.convert.Convert.digitToChinese(totalAmt.doubleValue());
        } catch (Exception e) {
            words = totalAmt.toPlainString() + " 元整";
        }

        return VoucherFullDetailResponse.builder()
                .id(item.getId())
                .voucherNo(item.getVoucherNo())
                .txnNo(item.getTxnNo())
                .traceNo(item.getTraceNo())
                .traceSeq(item.getTraceSeq())
                .voucherType(item.getVoucherType())
                .voucherTypeName(item.getVoucherTypeName())
                .voucherWord(voucherWord)
                .voucherTitle(title)
                .totalAmountInWords(words)
                .postingType(item.getPostingType())
                .postingTypeDesc(item.getPostingTypeDesc())
                .businessCode(item.getBusinessCode())
                .tradingCode(item.getTradingCode())
                .payChannel(item.getPayChannel())
                .tradeType(item.getTradeType())
                .tradeTypeDesc(item.getTradeTypeDesc())
                .tradeTime(item.getTradeTime())
                .amount(item.getAmount())
                .status(item.getStatus())
                .statusDesc(item.getStatusDesc())
                .postTime(item.getPostTime())
                .accountingDate(item.getAccountingDate())
                .summary(item.getSummary())
                .attachmentCount(item.getAttachmentCount())
                .origVoucherNo(item.getOrigVoucherNo())
                .reversalVoucherNo(reversalVoucherNo)
                .canReversal(canReversal)
                .bookkeeperName(item.getBookkeeperName())
                .reviewerName(item.getReviewerName())
                .failReason(item.getFailReason())
                .retryCount(item.getRetryCount())
                .skipFlag(item.getSkipFlag())
                .createTime(item.getCreateTime())
                .entries(item.getEntries())
                .auxiliaries(auxResponses)
                .attachments(attachResponses)
                .debitCount(item.getDebitCount())
                .creditCount(item.getCreditCount())
                .debitAmount(item.getDebitAmount())
                .creditAmount(item.getCreditAmount())
                .isBalanced(item.getIsBalanced())
                .build();
    }
}
