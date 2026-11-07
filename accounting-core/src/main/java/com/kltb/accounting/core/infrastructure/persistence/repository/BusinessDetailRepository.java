// accounting-core/src/main/java/com/kltb/accounting/core/infrastructure/persistence/repository/BusinessDetailRepository.java
package com.kltb.accounting.core.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kltb.accounting.core.infrastructure.persistence.entity.BusinessDetailPO;
import com.kltb.accounting.core.infrastructure.persistence.mapper.BusinessDetailMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.List;

/**
 * 业务记账流水明细持久化仓储（S3/M1 修复：独立仓储）
 */
@Repository
@RequiredArgsConstructor
public class BusinessDetailRepository {

    private final BusinessDetailMapper businessDetailMapper;

    /**
     * 保存流水明细
     */
    public void save(BusinessDetailPO detail) {
        businessDetailMapper.insert(detail);
    }

    /**
     * 按 traceNo 查询流水明细列表
     */
    public List<BusinessDetailPO> selectByTraceNo(String traceNo) {
        List<BusinessDetailPO> result = businessDetailMapper.selectList(
                new LambdaQueryWrapper<BusinessDetailPO>()
                        .eq(BusinessDetailPO::getTraceNo, traceNo)
                        .eq(BusinessDetailPO::getIsDelete, 0));
        return result != null ? result : Collections.emptyList();
    }
}
