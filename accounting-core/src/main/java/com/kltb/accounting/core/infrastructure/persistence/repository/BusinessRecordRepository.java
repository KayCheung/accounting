// accounting-core/src/main/java/com/kltb/accounting/core/infrastructure/persistence/repository/BusinessRecordRepository.java
package com.kltb.accounting.core.infrastructure.persistence.repository;

import com.kltb.accounting.core.domain.enums.BusinessRecordStatusEnum;
import com.kltb.accounting.core.infrastructure.persistence.entity.BusinessRecordPO;
import com.kltb.accounting.core.infrastructure.persistence.mapper.BusinessRecordMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;

/**
 * 业务记账流水持久化仓储
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class BusinessRecordRepository {

    private final BusinessRecordMapper businessRecordMapper;

    /**
     * 按 traceNo + traceSeq 查询流水（幂等检查用）
     */
    public BusinessRecordPO selectByTraceNo(String traceNo, Integer traceSeq) {
        return businessRecordMapper.selectByTraceNo(traceNo, traceSeq);
    }

    /**
     * 按 traceNo 查询流水（凭证生成用，取最新一条）
     */
    public BusinessRecordPO selectByTraceNo(String traceNo) {
        return businessRecordMapper.selectByTraceNoOnly(traceNo);
    }

    /**
     * 保存流水记录
     */
    public void save(BusinessRecordPO record) {
        businessRecordMapper.insert(record);
    }

    /**
     * 按 traceNo 更新流水状态（开户失败时使用）
     *
     * @return 受影响的行数（P1-5 修复：返回 affectedRows 供调用方判断更新是否成功）
     */
    public int updateStatusByTraceNo(String traceNo, BusinessRecordStatusEnum status) {
        int affectedRows = businessRecordMapper.updateStatusByTraceNo(traceNo, status.getCode());
        if (affectedRows == 0) {
            log.warn("[Journal] 更新流水状态失败，未找到匹配记录: traceNo={}, status={}", traceNo, status);
        }
        return affectedRows;
    }

    /**
     * 按会计日期和状态统计业务流水数量（Step 17 P0-4）
     */
    public int countByAccountingDateAndStatus(LocalDate accountingDate, Integer status) {
        return businessRecordMapper.countByAccountingDateAndStatus(accountingDate, status);
    }
}
