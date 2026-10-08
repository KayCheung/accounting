package com.kltb.accounting.core.infrastructure.persistence.repository;

import com.kltb.accounting.api.constant.ResultCode;
import com.kltb.accounting.core.infrastructure.persistence.entity.SubAccountDetailPO;
import com.kltb.accounting.core.infrastructure.persistence.mapper.SubAccountDetailMapper;
import com.kltb.accounting.core.shared.exception.AccountException;
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
        if (detail == null) {
            throw new AccountException(ResultCode.PARAM_ERROR, "待插入子账户明细对象不能为空");
        }
        int affected = subAccountDetailMapper.insert(detail);
        if (affected == 0) {
            throw new AccountException(ResultCode.SYSTEM_ERROR,
                    "写入子账户明细失败: entryId=" + detail.getEntryId());
        }
    }

    /**
     * 批量写入子账户明细
     */
    public void batchInsert(List<SubAccountDetailPO> details) {
        if (details == null || details.isEmpty()) {
            return;
        }
        for (SubAccountDetailPO detail : details) {
            insert(detail);
        }
    }
}
