// accounting-core/src/main/java/com/kltb/accounting/core/infrastructure/persistence/mapper/AccountingVoucherEntryMapper.java
package com.kltb.accounting.core.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kltb.accounting.core.infrastructure.persistence.entity.AccountingVoucherEntryPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 分录流水表数据访问层。
 */
@Mapper
public interface AccountingVoucherEntryMapper extends BaseMapper<AccountingVoucherEntryPO> {

    /**
     * 查询待过账的分录
     *
     * @param voucherNo 凭证号
     * @return 待过账分录列表
     */
    default List<AccountingVoucherEntryPO> selectPendingPosting(String voucherNo) {
        return this.selectList(new LambdaQueryWrapper<AccountingVoucherEntryPO>()
                .eq(AccountingVoucherEntryPO::getVoucherNo, voucherNo)
                .in(AccountingVoucherEntryPO::getStatus, 1, 3)
                .eq(AccountingVoucherEntryPO::getIsDelete, 0));
    }

    /**
     * 按凭证号查询分录列表
     *
     * @param voucherNo 凭证号
     * @return 分录列表
     */
    default List<AccountingVoucherEntryPO> selectByVoucherNo(String voucherNo) {
        return this.selectList(new LambdaQueryWrapper<AccountingVoucherEntryPO>()
                .eq(AccountingVoucherEntryPO::getVoucherNo, voucherNo)
                .eq(AccountingVoucherEntryPO::getIsDelete, 0)
                .orderByAsc(AccountingVoucherEntryPO::getRowNum));
    }

    /**
     * 按凭证号和状态查询分录
     */
    default List<AccountingVoucherEntryPO> selectByVoucherNoWithStatus(String voucherNo, Integer status) {
        return this.selectList(new LambdaQueryWrapper<AccountingVoucherEntryPO>()
                .eq(AccountingVoucherEntryPO::getVoucherNo, voucherNo)
                .eq(AccountingVoucherEntryPO::getStatus, status)
                .eq(AccountingVoucherEntryPO::getIsDelete, 0)
                .orderByAsc(AccountingVoucherEntryPO::getRowNum));
    }

    /**
     * 按凭证号和分录ID查询单条分录
     */
    default AccountingVoucherEntryPO selectByVoucherNoAndEntryId(String voucherNo, String entryId) {
        return this.selectOne(new LambdaQueryWrapper<AccountingVoucherEntryPO>()
                .eq(AccountingVoucherEntryPO::getVoucherNo, voucherNo)
                .eq(AccountingVoucherEntryPO::getEntryId, entryId)
                .eq(AccountingVoucherEntryPO::getIsDelete, 0));
    }

    /**
     * 按会计日期汇总各账户借贷方金额（Step 17 P0-6）
     */
    List<Map<String, Object>> sumEntriesByAccount(@Param("accountingDate") LocalDate accountingDate);

    /**
     * 按会计日期汇总各科目借贷方金额（试算平衡用）
     */
    List<Map<String, Object>> sumEntriesBySubject(@Param("accountingDate") LocalDate accountingDate);

    /**
     * 批量插入分录（红冲用）
     */
    int batchInsert(@Param("list") List<AccountingVoucherEntryPO> list);
}