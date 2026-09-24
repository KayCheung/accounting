package com.kltb.accounting.core.application.assembler;

import com.kltb.accounting.api.response.ReversalRecordResponse;
import com.kltb.accounting.api.response.ReversalResponse;
import com.kltb.accounting.core.domain.enums.VoucherStatusEnum;
import com.kltb.accounting.core.domain.service.ReversalDomainService.ReversalResult;
import com.kltb.accounting.core.infrastructure.persistence.entity.AccountingVoucherPO;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 红冲 PO ↔ DTO 转换器
 */
@Component
public class ReversalAssembler {

    /**
     * 红冲响应组装（从 ReversalResult 转换，无需额外查询凭证）
     */
    public ReversalResponse toResponse(ReversalResult result) {
        return ReversalResponse.builder()
                .origVoucherNo(result.getOrigVoucherNo())
                .reversalVoucherNo(result.getReversalVoucherNo())
                .tradeType(result.getTradeType())
                .amount(result.getAmount())
                .accountingDate(result.getAccountingDate())
                .entryCount(result.getEntryCount())
                .reversaledAt(result.getReversaledAt())
                .build();
    }

    /**
     * 红冲记录列表组装
     */
    public List<ReversalRecordResponse> toRecordResponses(
            List<AccountingVoucherPO> reversalRecords) {

        return reversalRecords.stream()
                .map(this::toRecordResponse)
                .collect(Collectors.toList());
    }

    private ReversalRecordResponse toRecordResponse(AccountingVoucherPO voucher) {
        return ReversalRecordResponse.builder()
                .reversalVoucherNo(voucher.getVoucherNo())
                .origVoucherNo(voucher.getOrigVoucherNo())
                .amount(voucher.getAmount())
                .accountingDate(voucher.getAccountingDate())
                .summary(voucher.getSummary())
                .status(Optional.ofNullable(voucher.getStatus()).map(VoucherStatusEnum::getCode).orElse(null))
                .postTime(voucher.getPostTime())
                .bookkeeperName(voucher.getBookkeeperName())
                .build();
    }
}
