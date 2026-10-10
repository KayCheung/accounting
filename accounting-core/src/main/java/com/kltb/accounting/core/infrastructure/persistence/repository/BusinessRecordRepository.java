package com.kltb.accounting.core.infrastructure.persistence.repository;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kltb.accounting.api.request.JournalPageQueryRequest;
import com.kltb.accounting.core.domain.enums.BusinessRecordStatusEnum;
import com.kltb.accounting.core.domain.enums.TradeTypeEnum;
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
     * 按 traceNo 更新原冻结单号与流水状态
     */
    public int updateOrigFreezeNoAndStatus(String traceNo, String origFreezeNo, BusinessRecordStatusEnum status) {
        int affectedRows = businessRecordMapper.updateOrigFreezeNoAndStatus(traceNo, origFreezeNo, status.getCode());
        if (affectedRows == 0) {
            log.warn("[Journal] 更新流水冻结单号与状态失败，未找到匹配记录: traceNo={}, origFreezeNo={}, status={}",
                    traceNo, origFreezeNo, status);
        }
        return affectedRows;
    }

    /**
     * 按会计日期和状态统计业务流水数量（Step 17 P0-4）
     */
    public int countByAccountingDateAndStatus(LocalDate accountingDate, Integer status) {
        return businessRecordMapper.countByAccountingDateAndStatus(accountingDate, status);
    }

    /**
     * 业务流水多条件分页查询
     */
    public Page<BusinessRecordPO> selectPage(JournalPageQueryRequest request) {
        Page<BusinessRecordPO> page = new Page<>(
                request.getPageNo() != null ? request.getPageNo() : 1,
                request.getPageSize() != null ? request.getPageSize() : 20
        );
        TradeTypeEnum tradeTypeEnum = request.getTradeType() != null ? TradeTypeEnum.fromCode(request.getTradeType()) : null;
        BusinessRecordStatusEnum statusEnum = request.getStatus() != null ? BusinessRecordStatusEnum.fromCode(request.getStatus()) : null;

        LambdaQueryWrapper<BusinessRecordPO> wrapper = new LambdaQueryWrapper<BusinessRecordPO>()
                .like(StrUtil.isNotBlank(request.getTraceNo()), BusinessRecordPO::getTraceNo, request.getTraceNo() != null ? request.getTraceNo().trim() : null)
                .eq(StrUtil.isNotBlank(request.getBusinessCode()), BusinessRecordPO::getBusinessCode, request.getBusinessCode() != null ? request.getBusinessCode().trim() : null)
                .eq(tradeTypeEnum != null, BusinessRecordPO::getTradeType, tradeTypeEnum)
                .eq(statusEnum != null, BusinessRecordPO::getStatus, statusEnum)
                .like(StrUtil.isNotBlank(request.getOrigFreezeNo()), BusinessRecordPO::getOrigFreezeNo, request.getOrigFreezeNo() != null ? request.getOrigFreezeNo().trim() : null)
                .ge(request.getStartDate() != null, BusinessRecordPO::getAccountingDate, request.getStartDate())
                .le(request.getEndDate() != null, BusinessRecordPO::getAccountingDate, request.getEndDate())
                .eq(BusinessRecordPO::getIsDelete, 0)
                .orderByDesc(BusinessRecordPO::getCreateTime);
        return businessRecordMapper.selectPage(page, wrapper);
    }
}
