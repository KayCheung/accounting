package com.kltb.accounting.core.application.assembler;

import com.kltb.accounting.api.response.PostingEntryResponse;
import com.kltb.accounting.api.response.PostingExecuteResponse;
import com.kltb.accounting.api.response.TransactionStatusResponse;
import com.kltb.accounting.core.domain.enums.DebitCreditEnum;
import com.kltb.accounting.core.domain.enums.TransactionStatusEnum;
import com.kltb.accounting.core.domain.enums.VoucherEntryStatusEnum;
import com.kltb.accounting.core.domain.enums.VoucherStatusEnum;
import com.kltb.accounting.core.infrastructure.persistence.entity.AccountingVoucherEntryPO;
import com.kltb.accounting.core.infrastructure.persistence.entity.AccountingVoucherPO;
import com.kltb.accounting.core.infrastructure.persistence.entity.TransactionPO;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 凭证过账 PO ↔ DTO 转换器
 */
@Component
public class PostingAssembler {

    /**
     * 将凭证 PO + 分录列表转换为响应 DTO
     */
    public PostingExecuteResponse toResponse(
        AccountingVoucherPO voucher,
        List<AccountingVoucherEntryPO> entries,
        TransactionPO transaction) {

        PostingExecuteResponse response = new PostingExecuteResponse();
        response.setVoucherNo(voucher.getVoucherNo());
        response.setVoucherStatus(Optional.ofNullable(voucher.getStatus()).map(VoucherStatusEnum::getCode).orElse(null));
        response.setVoucherStatusDesc(Optional.ofNullable(voucher.getStatus()).map(VoucherStatusEnum::getDesc).orElse(null));
        response.setTxnNo(voucher.getTxnNo());
        if (transaction != null) {
            response.setTransactionStatus(Optional.ofNullable(transaction.getStatus())
                    .map(TransactionStatusEnum::getCode).orElse(null));
        }
        response.setEntries(entries.stream().map(this::toEntryResponse).collect(Collectors.toList()));
        response.setHasAsyncEntries(entries.stream().anyMatch(e -> e.getUnilateral() != null
                && e.getUnilateral() == 0 && (e.getBuffered() == null || e.getBuffered() != 1)));
        response.setHasBufferEntries(entries.stream().anyMatch(e -> e.getBuffered() != null && e.getBuffered() == 1));
        return response;
    }

    /**
     * 单个分录 PO → 分录响应 DTO
     */
    public PostingEntryResponse toEntryResponse(AccountingVoucherEntryPO entry) {
        PostingEntryResponse response = new PostingEntryResponse();
        response.setEntryId(entry.getEntryId());
        response.setSubjectCode(entry.getSubjectCode());
        response.setAccountNo(entry.getAccountNo());
        response.setDebitCredit(Optional.ofNullable(entry.getDebitCredit()).map(DebitCreditEnum::getCode).orElse(null));
        response.setAmount(entry.getAmount());
        response.setStatus(Optional.ofNullable(entry.getStatus()).map(VoucherEntryStatusEnum::getCode).orElse(null));
        response.setStatusDesc(Optional.ofNullable(entry.getStatus()).map(VoucherEntryStatusEnum::getDesc).orElse(null));
        response.setUnilateral(entry.getUnilateral());
        response.setBuffered(entry.getBuffered());
        return response;
    }

    /**
     * 将事务 PO + 凭证 PO 转换为事务状态响应 DTO
     */
    public com.kltb.accounting.api.response.TransactionStatusResponse toTransactionResponse(
        TransactionPO transaction,
        AccountingVoucherPO voucher) {

        TransactionStatusResponse response = new TransactionStatusResponse();
        response.setTxnNo(transaction.getTxnNo());
        response.setStatus(Optional.ofNullable(transaction.getStatus()).map(TransactionStatusEnum::getCode).orElse(null));
        response.setStatusDesc(Optional.ofNullable(transaction.getStatus()).map(TransactionStatusEnum::getDesc).orElse(null));
        response.setFailReason(transaction.getFailReason());
        response.setFinishTime(transaction.getFinishTime());
        response.setRelateAccountCount(transaction.getRelateAccountCount());
        if (voucher != null) {
            response.setVoucherNo(voucher.getVoucherNo());
            response.setVoucherStatus(Optional.ofNullable(voucher.getStatus()).map(VoucherStatusEnum::getCode).orElse(null));
            response.setVoucherStatusDesc(Optional.ofNullable(voucher.getStatus()).map(VoucherStatusEnum::getDesc).orElse(null));
        }
        return response;
    }
}
