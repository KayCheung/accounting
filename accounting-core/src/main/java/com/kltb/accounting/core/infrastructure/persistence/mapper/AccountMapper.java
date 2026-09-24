// accounting-core/src/main/java/com/kltb/accounting/core/infrastructure/persistence/mapper/AccountMapper.java
package com.kltb.accounting.core.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kltb.accounting.core.infrastructure.persistence.dto.AccountWithSubAccountsDTO;
import com.kltb.accounting.core.infrastructure.persistence.entity.AccountPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 账户表 Mapper 接口
 * <p>
 * DDL: docs/sql/1-account.sql (t_account)
 * </p>
 */
@Mapper
public interface AccountMapper extends BaseMapper<AccountPO> {

    /**
     * 按账户编号加悲观锁查询
     *
     * @param accountNo 账户编号
     * @return 账户PO（已加锁）
     */
    AccountPO selectForUpdate(@Param("accountNo") String accountNo);

    /**
     * 按账户编号列表加悲观锁批量查询
     * 调用方必须保证 accountNos 已按升序排序
     */
    List<AccountPO> selectForUpdateBatch(@Param("accountNos") List<String> accountNos);

    /**
     * 按账户编号联查账户及其子账户
     *
     * @param accountNo 账户编号
     * @return 账户含子账户的DTO
     */
    AccountWithSubAccountsDTO selectWithSubAccounts(@Param("accountNo") String accountNo);

    /**
     * 按科目编码统计活跃账户数（通用统计，不限客户类型）
     *
     * @param subjectCode 科目编码
     * @return 关联该科目的未注销账户数
     */
    default long countBySubjectCode(String subjectCode) {
        return this.selectCount(new LambdaQueryWrapper<AccountPO>()
                .eq(AccountPO::getSubjectCode, subjectCode)
                .ne(AccountPO::getStatus, 3)
                .eq(AccountPO::getIsDelete, 0));
    }

    /**
     * 按科目编码和客户类型统计活跃账户数（模板停用校验用）
     *
     * @param subjectCode 科目编码
     * @param ownerType 客户类型
     * @return 关联该科目和客户类型的未注销账户数
     */
    default long countBySubjectCodeAndOwnerType(String subjectCode, Integer ownerType) {
        return this.selectCount(new LambdaQueryWrapper<AccountPO>()
                .eq(AccountPO::getSubjectCode, subjectCode)
                .eq(AccountPO::getOwnerType, ownerType)
                .ne(AccountPO::getStatus, 3)
                .eq(AccountPO::getIsDelete, 0));
    }

    /**
     * 按所有者ID + 科目编码检查账户是否存在（防重复开户）
     *
     * @param ownerId 所有者ID
     * @param subjectCode 科目编码
     * @return true 表示账户已存在
     */
    default boolean existsByOwnerIdAndSubjectCode(String ownerId, String subjectCode) {
        return this.selectCount(new LambdaQueryWrapper<AccountPO>()
                .eq(AccountPO::getOwnerId, ownerId)
                .eq(AccountPO::getSubjectCode, subjectCode)
                .eq(AccountPO::getIsDelete, 0)) > 0;
    }

    /**
     * 按所有者ID + 科目编码查询账户
     *
     * @param ownerId 所有者ID
     * @param subjectCode 科目编码
     * @return 账户PO，不存在时返回null
     */
    default AccountPO selectByOwnerIdAndSubjectCode(String ownerId, String subjectCode) {
        return this.selectOne(new LambdaQueryWrapper<AccountPO>()
                .eq(AccountPO::getOwnerId, ownerId)
                .eq(AccountPO::getSubjectCode, subjectCode)
                .eq(AccountPO::getIsDelete, 0)
                .last("LIMIT 1"));
    }

    /**
     * 按账户编号查询余额 + 状态 + 风控状态
     * <p>
     * 注意：仅查询指定字段，其余字段为 null：
     * ownerId, ownerType, version, createTime, updateTime 等均未查询。
     */
    default AccountPO selectBalanceByAccountNo(String accountNo) {
        return this.selectOne(new LambdaQueryWrapper<AccountPO>()
                .select(AccountPO::getAccountNo, AccountPO::getAccountName, AccountPO::getSubjectCode,
                        AccountPO::getBalance, AccountPO::getStatus, AccountPO::getRiskStatus,
                        AccountPO::getBalanceDirection, AccountPO::getCurrency)
                .eq(AccountPO::getAccountNo, accountNo)
                .eq(AccountPO::getIsDelete, 0)
                .last("LIMIT 1"));
    }

    /**
     * 按账户编号更新状态（乐观锁）
     */
    default int updateStatusByAccountNo(String accountNo,
                                         com.kltb.accounting.core.domain.enums.AccountStatusEnum status,
                                         Long version) {
        return this.update(null, new LambdaUpdateWrapper<AccountPO>()
                .eq(AccountPO::getAccountNo, accountNo)
                .eq(AccountPO::getVersion, version)
                .eq(AccountPO::getIsDelete, 0)
                .set(AccountPO::getStatus, status.getCode())
                .setSql("version = version + 1"));
    }

    /**
     * 按账户编号更新余额（乐观锁）
     */
    default int updateBalance(String accountNo, BigDecimal newBalance, Long version) {
        return this.update(null, new LambdaUpdateWrapper<AccountPO>()
                .eq(AccountPO::getAccountNo, accountNo)
                .eq(AccountPO::getVersion, version)
                .eq(AccountPO::getIsDelete, 0)
                .set(AccountPO::getBalance, newBalance)
                .setSql("version = version + 1"));
    }

    /**
     * 按账户编号更新风控状态（乐观锁）
     */
    default int updateRiskStatusByAccountNo(String accountNo,
                                             com.kltb.accounting.core.domain.enums.RiskStatusEnum riskStatus,
                                             Long version) {
        return this.update(null, new LambdaUpdateWrapper<AccountPO>()
                .eq(AccountPO::getAccountNo, accountNo)
                .eq(AccountPO::getVersion, version)
                .eq(AccountPO::getIsDelete, 0)
                .set(AccountPO::getRiskStatus, riskStatus.getCode())
                .setSql("version = version + 1"));
    }

    /**
     * 按科目汇总账户余额（总分核对用-分户侧）
     *
     * @return 科目汇总列表，每项包含 subject_code / total_balance / account_count
     */
    List<Map<String, Object>> sumBalancesBySubject();

    /**
     * 批量查询账户余额（余额核对用）
     *
     * @param accountNos 账户编号列表
     * @return 账户余额列表，每项包含 account_no / balance
     */
    List<Map<String, Object>> selectBalancesByAccountNos(@Param("accountNos") List<String> accountNos);
}
