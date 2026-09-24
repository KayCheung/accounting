package com.kltb.accounting.core.application.assembler;

import com.kltb.accounting.api.response.VoucherEntryResponse;
import com.kltb.accounting.api.response.VoucherGenerateResponse;
import com.kltb.accounting.core.domain.enums.VoucherStatusEnum;
import com.kltb.accounting.core.domain.service.VoucherEntryData;
import com.kltb.accounting.core.infrastructure.persistence.entity.AccountingVoucherPO;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 凭证生成 PO ↔ DTO 转换器
 * P2-1 修复：status 通过 voucher.getStatus().getCode() 转换为 Integer
 */
@Component
public class VoucheringAssembler {

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

    private VoucherEntryResponse toEntryResponse(VoucherEntryData data) {
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
}
