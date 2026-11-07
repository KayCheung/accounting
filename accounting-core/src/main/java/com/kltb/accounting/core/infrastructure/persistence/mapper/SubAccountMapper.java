// accounting-core/src/main/java/com/kltb/accounting/core/infrastructure/persistence/mapper/SubAccountMapper.java
package com.kltb.accounting.core.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kltb.accounting.core.infrastructure.persistence.entity.SubAccountPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;

/**
 * 子账户表 Mapper 接口
 * <p>
 * DDL: docs/sql/1-account.sql (t_sub_account)
 * </p>
 */
@Mapper
public interface SubAccountMapper extends BaseMapper<SubAccountPO> {

    /**
     * 按账户编号加悲观锁查询子账户
     *
     * @param accountNo 账户编号
     * @return 子账户列表（已加锁）
     */
    List<SubAccountPO> selectForUpdate(@Param("accountNo") String accountNo);

    /**
     * 按账户编号查询所有子账户余额
     */
    default List<SubAccountPO> selectBalanceByAccountNo(String accountNo) {
        return this.selectList(new LambdaQueryWrapper<SubAccountPO>()
                .select(SubAccountPO::getAccountNo, SubAccountPO::getBalanceType,
                        SubAccountPO::getBalanceDirection, SubAccountPO::getBalance, SubAccountPO::getVersion)
                .eq(SubAccountPO::getAccountNo, accountNo)
                .eq(SubAccountPO::getIsDelete, 0));
    }

    /**
     * 按账户编号 + 余额类型更新余额（乐观锁）
     */
    default int updateBalance(String accountNo, Integer balanceType, BigDecimal newBalance, Long version) {
        return this.update(null, new LambdaUpdateWrapper<SubAccountPO>()
                .eq(SubAccountPO::getAccountNo, accountNo)
                .eq(SubAccountPO::getBalanceType, balanceType)
                .eq(SubAccountPO::getVersion, version)
                .eq(SubAccountPO::getIsDelete, 0)
                .set(SubAccountPO::getBalance, newBalance)
                .setSql("version = version + 1"));
    }
}
