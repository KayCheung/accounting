// accounting-core/src/main/java/com/kltb/accounting/core/infrastructure/persistence/mapper/AccountFreezeDetailMapper.java
package com.kltb.accounting.core.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kltb.accounting.core.infrastructure.persistence.entity.AccountFreezeDetailPO;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 账户资金冻结明细表 Mapper 接口
 * <p>
 * DDL: docs/sql/1-account.sql (t_account_freeze_detail)
 * </p>
 */
@Mapper
public interface AccountFreezeDetailMapper extends BaseMapper<AccountFreezeDetailPO> {

    /**
     * 查询已过期的冻结记录
     */
    default List<AccountFreezeDetailPO> selectExpired(LocalDateTime now) {
        return this.selectList(new LambdaQueryWrapper<AccountFreezeDetailPO>()
                .eq(AccountFreezeDetailPO::getStatus, 1)
                .le(AccountFreezeDetailPO::getExpireTime, now)
                .eq(AccountFreezeDetailPO::getIsDelete, 0)
                .orderByAsc(AccountFreezeDetailPO::getCreateTime));
    }

    /**
     * 分页查询冻结记录（按 accountNo）
     * <p>
     * 功能描述：按账户编号和状态分页查询冻结记录，使用 MyBatis-Plus Page 对象分页。
     * <p>
     * 是否记账：否，纯查询操作。
     * <p>
     * 异常处理：无特殊异常处理。
     */
    default IPage<AccountFreezeDetailPO> selectPageByAccountNo(String accountNo, Integer status,
                                                                long offset, int limit) {
        LambdaQueryWrapper<AccountFreezeDetailPO> wrapper = new LambdaQueryWrapper<AccountFreezeDetailPO>()
                .eq(AccountFreezeDetailPO::getAccountNo, accountNo)
                .eq(status != null, AccountFreezeDetailPO::getStatus, status)
                .eq(AccountFreezeDetailPO::getIsDelete, 0)
                .orderByDesc(AccountFreezeDetailPO::getCreateTime);
        return this.selectPage(new Page<>(offset / limit + 1, limit), wrapper);
    }

    /**
     * 统计已过期但未解冻的冻结记录数量（Step 17 P0-8）
     */
    default int countExpiredUnfrozen() {
        return this.selectCount(new LambdaQueryWrapper<AccountFreezeDetailPO>()
                .eq(AccountFreezeDetailPO::getStatus, 1)
                .le(AccountFreezeDetailPO::getExpireTime, java.time.LocalDateTime.now())
                .eq(AccountFreezeDetailPO::getIsDelete, 0)).intValue();
    }

    /**
     * 更新冻结记录状态（乐观锁）
     */
    default int updateStatus(String voucherNo, Integer status, Long version) {
        return this.update(null, new LambdaUpdateWrapper<AccountFreezeDetailPO>()
                .eq(AccountFreezeDetailPO::getVoucherNo, voucherNo)
                .eq(AccountFreezeDetailPO::getVersion, version)
                .eq(AccountFreezeDetailPO::getIsDelete, 0)
                .set(AccountFreezeDetailPO::getStatus, status)
                .setSql("version = version + 1"));
    }
}
