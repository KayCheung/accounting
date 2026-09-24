// accounting-core/src/main/java/com/kltb/accounting/core/application/assembler/JournalingAssembler.java
package com.kltb.accounting.core.application.assembler;

import com.kltb.accounting.api.response.JournalSubmitResponse;
import com.kltb.accounting.core.domain.service.JournalSubmitResult;
import com.kltb.accounting.core.infrastructure.persistence.entity.BusinessRecordPO;
import com.kltb.accounting.core.infrastructure.persistence.entity.TransactionPO;
import org.springframework.stereotype.Component;

/**
 * 流水入库 DTO 转换器
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
}
