package com.kltb.accounting.core.infrastructure.persistence.repository;

import com.kltb.accounting.core.infrastructure.persistence.entity.SubAccountDetailPO;
import com.kltb.accounting.core.infrastructure.persistence.mapper.SubAccountDetailMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 子账户明细持久化仓储
 */
@Repository
@RequiredArgsConstructor
public class SubAccountDetailRepository {

    private final SubAccountDetailMapper subAccountDetailMapper;

    /**
     * 写入子账户明细
     */
    public void insert(SubAccountDetailPO detail) {
        subAccountDetailMapper.insert(detail);
    }

    /**
     * 批量写入子账户明细
     */
    public void batchInsert(List<SubAccountDetailPO> details) {
        for (SubAccountDetailPO detail : details) {
            subAccountDetailMapper.insert(detail);
        }
    }
}
