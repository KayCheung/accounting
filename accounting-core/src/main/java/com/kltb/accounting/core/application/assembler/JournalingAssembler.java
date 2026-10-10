// accounting-core/src/main/java/com/kltb/accounting/core/application/assembler/JournalingAssembler.java
package com.kltb.accounting.core.application.assembler;

import cn.hutool.core.util.StrUtil;
import com.kltb.accounting.api.response.*;
import com.kltb.accounting.core.domain.enums.BusinessRecordStatusEnum;
import com.kltb.accounting.core.domain.enums.DebitCreditEnum;
import com.kltb.accounting.core.domain.enums.TransactionStatusEnum;
import com.kltb.accounting.core.domain.enums.VoucherEntryStatusEnum;
import com.kltb.accounting.core.domain.service.JournalSubmitResult;
import com.kltb.accounting.core.infrastructure.persistence.entity.*;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 流水入库及全链路 DTO 转换器
 */
@Component
public class JournalingAssembler {

    /**
     * 领域结果 → API 响应 DTO
     */
    public JournalSubmitResponse toResponse(JournalSubmitResult result) {
        JournalSubmitResponse response = new JournalSubmitResponse();
        response.setTraceNo(result.getTraceNo());
        response.setAccountingDate(result.getAccountingDate());
        response.setTxnNo(result.getTxnNo());
        response.setNeedVouchering(true);
        return response;
    }

    /**
     * 幂等已存在结果 → API 响应 DTO（M5 修复）
     */
    public JournalSubmitResponse toIdempotentResponse(BusinessRecordPO record, TransactionPO transaction) {
        JournalSubmitResponse response = new JournalSubmitResponse();
        response.setTraceNo(record.getTraceNo());
        response.setAccountingDate(record.getAccountingDate());
        if (transaction != null) {
            response.setTxnNo(transaction.getTxnNo());
        }
        response.setNeedVouchering(false);
        return response;
    }

    /**
     * 业务流水精简查询结果 → API 响应 DTO（面向外部业务系统）
     */
    public JournalQueryResponse toQueryResponse(BusinessRecordPO record, TransactionPO txn) {
        if (record == null) {
            return null;
        }
        JournalQueryResponse response = new JournalQueryResponse();
        response.setTraceNo(record.getTraceNo());
        response.setTraceSeq(record.getTraceSeq());
        if (txn != null) {
            response.setTxnNo(txn.getTxnNo());
            response.setFinishTime(txn.getFinishTime());
            if (StrUtil.isNotBlank(txn.getFailReason())) {
                response.setFailReason(txn.getFailReason());
            }
        }
        response.setBusinessCode(record.getBusinessCode());
        response.setTradingCode(record.getTradingCode());
        response.setPayChannel(record.getPayChannel());
        if (record.getTradeType() != null) {
            response.setTradeType(record.getTradeType().getCode());
            response.setTradeTypeDesc(record.getTradeType().getDesc());
        }
        response.setAmount(record.getAmount());
        response.setAccountingDate(record.getAccountingDate());
        response.setSummary(record.getSummary());
        response.setOrigFreezeNo(record.getOrigFreezeNo());
        if (record.getStatus() != null) {
            response.setStatus(record.getStatus().getCode());
            response.setStatusDesc(record.getStatus().getDesc());
        }
        response.setTradeTime(record.getTradeTime());
        return response;
    }

    /**
     * 预冻结结果 → API 响应 DTO（支持多账户及单账户）
     */
    public JournalFreezeResponse toFreezeResponse(BusinessRecordPO record, List<AccountFreezeDetailPO> freezeDetails) {
        JournalFreezeResponse response = new JournalFreezeResponse();
        response.setTraceNo(record.getTraceNo());
        response.setAccountingDate(record.getAccountingDate());

        if (freezeDetails != null && !freezeDetails.isEmpty()) {
            AccountFreezeDetailPO primary = freezeDetails.get(0);
            response.setFreezeId(primary.getVoucherNo());
            response.setAccountNo(primary.getAccountNo());
            BigDecimal totalFreeze = freezeDetails.stream()
                    .map(AccountFreezeDetailPO::getFreezeAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            response.setFreezeAmount(totalFreeze);
            response.setExpireTime(primary.getExpireTime());
            response.setStatus(primary.getStatus() != null ? primary.getStatus().getCode() : null);
            response.setStatusDesc(primary.getStatus() != null ? primary.getStatus().getDesc() : null);

            List<JournalFreezeResponse.FreezeItem> items = freezeDetails.stream().map(d -> {
                JournalFreezeResponse.FreezeItem item = new JournalFreezeResponse.FreezeItem();
                item.setFreezeId(d.getVoucherNo());
                item.setAccountNo(d.getAccountNo());
                item.setFreezeAmount(d.getFreezeAmount());
                item.setExpireTime(d.getExpireTime());
                item.setStatus(d.getStatus() != null ? d.getStatus().getCode() : null);
                item.setStatusDesc(d.getStatus() != null ? d.getStatus().getDesc() : null);
                return item;
            }).collect(Collectors.toList());
            response.setFreezeItems(items);
        }
        return response;
    }

    public JournalFreezeResponse toFreezeResponse(BusinessRecordPO record, AccountFreezeDetailPO freezeDetail) {
        return toFreezeResponse(record, freezeDetail != null ? List.of(freezeDetail) : Collections.emptyList());
    }

    /**
     * 预冻结全额解冻结果 → API 响应 DTO（支持多账户及单账户）
     */
    public JournalUnfreezeResponse toUnfreezeResponse(String traceNo, String origTraceNo, List<AccountFreezeDetailPO> unfreezeDetails) {
        JournalUnfreezeResponse response = new JournalUnfreezeResponse();
        response.setTraceNo(traceNo);
        response.setOrigTraceNo(origTraceNo);

        if (unfreezeDetails != null && !unfreezeDetails.isEmpty()) {
            AccountFreezeDetailPO primary = unfreezeDetails.get(0);
            response.setFreezeId(primary.getVoucherNo());
            response.setAccountNo(primary.getAccountNo());
            BigDecimal totalUnfrozen = unfreezeDetails.stream()
                    .map(AccountFreezeDetailPO::getOrigFreezeAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            response.setUnfreezeAmount(totalUnfrozen);
            response.setStatus(primary.getStatus() != null ? primary.getStatus().getCode() : null);
            response.setStatusDesc(primary.getStatus() != null ? primary.getStatus().getDesc() : null);

            List<JournalUnfreezeResponse.UnfreezeItem> items = unfreezeDetails.stream().map(d -> {
                JournalUnfreezeResponse.UnfreezeItem item = new JournalUnfreezeResponse.UnfreezeItem();
                item.setFreezeId(d.getVoucherNo());
                item.setAccountNo(d.getAccountNo());
                item.setUnfreezeAmount(d.getOrigFreezeAmount());
                item.setStatus(d.getStatus() != null ? d.getStatus().getCode() : null);
                item.setStatusDesc(d.getStatus() != null ? d.getStatus().getDesc() : null);
                return item;
            }).collect(Collectors.toList());
            response.setUnfreezeItems(items);
        }
        return response;
    }

    public JournalUnfreezeResponse toUnfreezeResponse(String traceNo, String origTraceNo, AccountFreezeDetailPO freezeDetail) {
        return toUnfreezeResponse(traceNo, origTraceNo, freezeDetail != null ? List.of(freezeDetail) : Collections.emptyList());
    }

    /**
     * 聚合全流程总览响应 DTO
     */
    /**
     * 聚合全流程总览响应 DTO
     */
    public JournalOverviewResponse toOverviewResponse(
            BusinessRecordPO record,
            TransactionPO txn,
            List<AccountingVoucherPO> vouchers,
            Map<String, List<AccountingVoucherEntryPO>> entryMap,
            AccountFreezeDetailPO freezeDetail) {
        return toOverviewResponse(record, txn, vouchers, entryMap, freezeDetail, Collections.emptyList(), Collections.emptyList());
    }

    /**
     * 聚合全流程总览响应 DTO（包含缓冲明细与异步本地消息状态）
     */
    public JournalOverviewResponse toOverviewResponse(
            BusinessRecordPO record,
            TransactionPO txn,
            List<AccountingVoucherPO> vouchers,
            Map<String, List<AccountingVoucherEntryPO>> entryMap,
            AccountFreezeDetailPO freezeDetail,
            List<BufferPostingDetailPO> bufferDetails,
            List<LocalMessagePO> localMessages) {

        JournalOverviewResponse response = new JournalOverviewResponse();

        // 1. 流水信息
        if (record != null) {
            JournalOverviewResponse.RecordInfo recordInfo = new JournalOverviewResponse.RecordInfo();
            recordInfo.setTraceNo(record.getTraceNo());
            recordInfo.setTraceSeq(record.getTraceSeq());
            recordInfo.setBusinessCode(record.getBusinessCode());
            recordInfo.setTradingCode(record.getTradingCode());
            recordInfo.setPayChannel(record.getPayChannel());
            recordInfo.setTradeType(record.getTradeType() != null ? record.getTradeType().getCode() : null);
            recordInfo.setTradeTypeDesc(record.getTradeType() != null ? record.getTradeType().getDesc() : null);
            recordInfo.setAmount(record.getAmount());
            recordInfo.setAccountingDate(record.getAccountingDate());
            recordInfo.setSummary(record.getSummary());
            recordInfo.setOrigFreezeNo(record.getOrigFreezeNo());
            recordInfo.setStatus(record.getStatus() != null ? record.getStatus().getCode() : null);
            recordInfo.setStatusDesc(record.getStatus() != null ? record.getStatus().getDesc() : null);
            recordInfo.setTradeTime(record.getTradeTime());
            recordInfo.setCreateTime(record.getCreateTime());
            response.setRecord(recordInfo);
        }

        // 2. 事务信息
        if (txn != null) {
            JournalOverviewResponse.TransactionInfo txnInfo = new JournalOverviewResponse.TransactionInfo();
            txnInfo.setTxnNo(txn.getTxnNo());
            txnInfo.setStatus(txn.getStatus() != null ? txn.getStatus().getCode() : null);
            txnInfo.setStatusDesc(txn.getStatus() != null ? txn.getStatus().getDesc() : null);
            txnInfo.setFailReason(txn.getFailReason());
            txnInfo.setFinishTime(txn.getFinishTime());
            txnInfo.setRelateAccountCount(txn.getRelateAccountCount());
            response.setTransaction(txnInfo);
        }

        // 3. 凭证及分录信息
        int realtimeTotal = 0;
        int realtimeSuccessCount = 0;
        int asyncTotal = 0;
        int asyncSuccessCount = 0;
        int bufferTotal = 0;
        int bufferSuccessCount = 0;

        if (vouchers != null && !vouchers.isEmpty()) {
            List<JournalOverviewResponse.VoucherInfo> voucherInfos = vouchers.stream().map(v -> {
                JournalOverviewResponse.VoucherInfo vInfo = new JournalOverviewResponse.VoucherInfo();
                vInfo.setVoucherNo(v.getVoucherNo());
                vInfo.setVoucherType(v.getVoucherType());
                vInfo.setStatus(v.getStatus() != null ? v.getStatus().getCode() : null);
                vInfo.setStatusDesc(v.getStatus() != null ? v.getStatus().getDesc() : null);
                vInfo.setAmount(v.getAmount());

                List<AccountingVoucherEntryPO> entries = entryMap.getOrDefault(v.getVoucherNo(), Collections.emptyList());
                List<JournalOverviewResponse.EntryInfo> entryInfos = entries.stream().map(e -> {
                    JournalOverviewResponse.EntryInfo eInfo = new JournalOverviewResponse.EntryInfo();
                    eInfo.setEntryId(e.getEntryId());
                    eInfo.setRowNum(e.getRowNum());
                    eInfo.setSubjectCode(e.getSubjectCode());
                    eInfo.setAccountNo(e.getAccountNo());
                    eInfo.setDebitCredit(e.getDebitCredit() != null ? e.getDebitCredit().getCode() : null);
                    eInfo.setDebitCreditDesc(e.getDebitCredit() != null ? e.getDebitCredit().getDesc() : null);
                    eInfo.setAmount(e.getAmount());
                    eInfo.setUnilateral(e.getUnilateral());
                    eInfo.setBuffered(e.getBuffered());
                    eInfo.setChangeDirection(e.getChangeDirection() != null ? e.getChangeDirection().getCode() : null);
                    eInfo.setStatus(e.getStatus() != null ? e.getStatus().getCode() : null);
                    eInfo.setStatusDesc(e.getStatus() != null ? e.getStatus().getDesc() : null);
                    return eInfo;
                }).collect(Collectors.toList());

                vInfo.setEntries(entryInfos);
                return vInfo;
            }).collect(Collectors.toList());
            response.setVouchers(voucherInfos);

            // 统计分录执行情况
            for (List<AccountingVoucherEntryPO> entryList : entryMap.values()) {
                for (AccountingVoucherEntryPO entry : entryList) {
                    boolean isPosted = entry.getStatus() == VoucherEntryStatusEnum.POSTED;
                    if (Integer.valueOf(1).equals(entry.getBuffered())) {
                        bufferTotal++;
                        if (isPosted) bufferSuccessCount++;
                    } else if (Integer.valueOf(1).equals(entry.getUnilateral())) {
                        realtimeTotal++;
                        if (isPosted) realtimeSuccessCount++;
                    } else {
                        asyncTotal++;
                        if (isPosted) asyncSuccessCount++;
                    }
                }
            }
        }

        // 4. 冻结信息
        if (freezeDetail != null) {
            JournalOverviewResponse.FreezeInfo freezeInfo = new JournalOverviewResponse.FreezeInfo();
            freezeInfo.setFreezeId(freezeDetail.getVoucherNo());
            freezeInfo.setAccountNo(freezeDetail.getAccountNo());
            freezeInfo.setFreezeAmount(freezeDetail.getFreezeAmount());
            freezeInfo.setOrigFreezeAmount(freezeDetail.getOrigFreezeAmount());
            freezeInfo.setUnfrozenAmount(freezeDetail.getUnfrozenAmount());
            freezeInfo.setDeductedAmount(freezeDetail.getDeductedAmount());
            freezeInfo.setExpireTime(freezeDetail.getExpireTime());
            freezeInfo.setStatus(freezeDetail.getStatus() != null ? freezeDetail.getStatus().getCode() : null);
            freezeInfo.setStatusDesc(freezeDetail.getStatus() != null ? freezeDetail.getStatus().getDesc() : null);
            response.setFreeze(freezeInfo);
        }

        // 5. 综合入账统计与异步/缓冲明细
        JournalOverviewResponse.PostingSummaryInfo summaryInfo = new JournalOverviewResponse.PostingSummaryInfo();
        summaryInfo.setRealtimeTotal(realtimeTotal);
        summaryInfo.setRealtimeSuccessCount(realtimeSuccessCount);
        summaryInfo.setAsyncTotal(asyncTotal);
        summaryInfo.setAsyncSuccessCount(asyncSuccessCount);
        summaryInfo.setBufferTotal(bufferTotal);
        summaryInfo.setBufferSuccessCount(bufferSuccessCount);

        if (localMessages != null && !localMessages.isEmpty()) {
            List<JournalOverviewResponse.AsyncMessageInfo> msgInfos = localMessages.stream().map(m -> {
                JournalOverviewResponse.AsyncMessageInfo mInfo = new JournalOverviewResponse.AsyncMessageInfo();
                mInfo.setMessageId(m.getMessageId());
                mInfo.setBusinessKey(m.getBusinessKey());
                mInfo.setTopic(m.getTopic());
                mInfo.setTag(m.getTag());
                mInfo.setStatus(m.getStatus() != null ? m.getStatus().getCode() : null);
                mInfo.setStatusDesc(m.getStatus() != null ? m.getStatus().name() : null);
                mInfo.setRetryCount(m.getRetryCount());
                mInfo.setNextRetryTime(m.getNextRetryTime());
                mInfo.setCreateTime(m.getCreateTime());
                return mInfo;
            }).collect(Collectors.toList());
            summaryInfo.setAsyncMessages(msgInfos);
        } else {
            summaryInfo.setAsyncMessages(Collections.emptyList());
        }

        if (bufferDetails != null && !bufferDetails.isEmpty()) {
            List<JournalOverviewResponse.BufferDetailInfo> bInfos = bufferDetails.stream().map(b -> {
                JournalOverviewResponse.BufferDetailInfo bInfo = new JournalOverviewResponse.BufferDetailInfo();
                bInfo.setId(b.getId());
                bInfo.setRuleId(b.getRuleId());
                bInfo.setBufferMode(b.getBufferMode() != null ? b.getBufferMode().getCode() : null);
                bInfo.setBufferModeDesc(b.getBufferMode() != null ? b.getBufferMode().getDesc() : null);
                bInfo.setVoucherNo(b.getVoucherNo());
                bInfo.setEntryId(b.getEntryId());
                bInfo.setAccountNo(b.getAccountNo());
                bInfo.setAmount(b.getAmount());
                bInfo.setChangeDirection(b.getDebitCredit() != null ? b.getDebitCredit().getCode() : null);
                bInfo.setStatus(b.getStatus() != null ? b.getStatus().getCode() : null);
                bInfo.setStatusDesc(b.getStatus() != null ? b.getStatus().getDesc() : null);
                bInfo.setTriggerTime(b.getStartTime());
                bInfo.setPostTime(b.getCompleteTime());
                bInfo.setFailReason(b.getFailReason());
                return bInfo;
            }).collect(Collectors.toList());
            summaryInfo.setBufferDetails(bInfos);
        } else {
            summaryInfo.setBufferDetails(Collections.emptyList());
        }
        response.setPostingSummary(summaryInfo);

        // 6. 当前记账流程阶段、进度百分比及重试/回滚操作可用性判定
        boolean isFailed = (record != null && record.getStatus() == BusinessRecordStatusEnum.FAILED)
                || (txn != null && txn.getStatus() == TransactionStatusEnum.FAILED);
        boolean isSuccess = (record != null && record.getStatus() == BusinessRecordStatusEnum.SUCCESS)
                || (txn != null && txn.getStatus() == TransactionStatusEnum.SUCCESS);

        if (isFailed) {
            response.setProcessStage("FAILED");
            response.setStageDesc("记账失败");
            response.setProgressPercent(100);
            response.setCanRetry(true);
            response.setCanRollback(true);
        } else if (isSuccess) {
            response.setProcessStage("SUCCESS");
            response.setStageDesc("记账完成");
            response.setProgressPercent(100);
            response.setCanRetry(false);
            response.setCanRollback(true);
        } else if (vouchers != null && !vouchers.isEmpty()) {
            response.setProcessStage("POSTING");
            response.setStageDesc("过账处理中");
            response.setProgressPercent(75);
            response.setCanRetry(false);
            response.setCanRollback(true);
        } else if (txn != null) {
            response.setProcessStage("VOUCHERED");
            response.setStageDesc("凭证已生成");
            response.setProgressPercent(50);
            response.setCanRetry(false);
            response.setCanRollback(false);
        } else {
            response.setProcessStage("RECORDED");
            response.setStageDesc("流水已登记");
            response.setProgressPercent(25);
            response.setCanRetry(false);
            response.setCanRollback(false);
        }

        return response;
    }

    /**
     * 事务状态响应 DTO
     */
    public TransactionStatusResponse toTransactionStatusResponse(TransactionPO txn, AccountingVoucherPO voucher) {
        TransactionStatusResponse response = new TransactionStatusResponse();
        if (txn != null) {
            response.setTxnNo(txn.getTxnNo());
            response.setStatus(txn.getStatus() != null ? txn.getStatus().getCode() : null);
            response.setStatusDesc(txn.getStatus() != null ? txn.getStatus().getDesc() : null);
            response.setFailReason(txn.getFailReason());
            response.setFinishTime(txn.getFinishTime());
            response.setRelateAccountCount(txn.getRelateAccountCount());
        }
        if (voucher != null) {
            response.setVoucherNo(voucher.getVoucherNo());
            response.setVoucherStatus(voucher.getStatus() != null ? voucher.getStatus().getCode() : null);
            response.setVoucherStatusDesc(voucher.getStatus() != null ? voucher.getStatus().getDesc() : null);
        }
        return response;
    }
}
