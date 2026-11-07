// accounting-core/src/main/java/com/kltb/accounting/core/infrastructure/persistence/mapper/AccountBalanceSnapshotMapper.java
package com.kltb.accounting.core.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kltb.accounting.core.infrastructure.persistence.entity.AccountBalanceSnapshotPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 账户余额快照表 Mapper 接口（按年分区）
 * <p>
 * DDL: docs/sql/1-account.sql (t_account_balance_snapshot)
 * </p>
 */
@Mapper
public interface AccountBalanceSnapshotMapper extends BaseMapper<AccountBalanceSnapshotPO> {

    /**
     * 批量插入余额快照记录
     *
     * @param list 快照记录列表
     * @return 影响行数
     */
    int batchInsertSnapshot(@Param("list") List<AccountBalanceSnapshotPO> list);
}
