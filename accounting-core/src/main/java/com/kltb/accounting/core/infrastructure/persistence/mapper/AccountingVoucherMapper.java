// accounting-core/src/main/java/com/kltb/accounting/core/infrastructure/persistence/mapper/AccountingVoucherMapper.java
package com.kltb.accounting.core.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kltb.accounting.core.infrastructure.persistence.entity.AccountingVoucherPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 记账凭证表数据访问层。
 */
@Mapper
public interface AccountingVoucherMapper extends BaseMapper<AccountingVoucherPO> {

    /**
     * 按凭证号联查凭证及其分录
     */
    default AccountingVoucherPO selectWithEntries(String voucherNo) {
        return this.selectOne(new LambdaQueryWrapper<AccountingVoucherPO>()
                .eq(AccountingVoucherPO::getVoucherNo, voucherNo)
                .eq(AccountingVoucherPO::getIsDelete, 0));
    }

    /**
     * 按系统跟踪号查询凭证列表
     */
    default List<AccountingVoucherPO> selectByTraceNo(String traceNo) {
        return this.selectList(new LambdaQueryWrapper<AccountingVoucherPO>()
                .eq(AccountingVoucherPO::getTraceNo, traceNo)
                .eq(AccountingVoucherPO::getIsDelete, 0));
    }

    /**
     * 按原凭证号查询红冲凭证
     */
    default List<AccountingVoucherPO> selectReversalByOrig(String origVoucherNo) {
        return this.selectList(new LambdaQueryWrapper<AccountingVoucherPO>()
                .eq(AccountingVoucherPO::getOrigVoucherNo, origVoucherNo)
                .eq(AccountingVoucherPO::getIsDelete, 0));
    }

    /**
     * 按业务键统计凭证数量（规则停用校验用）
     */
    default long countByBusinessKey(String businessCode, String tradingCode, String payChannel) {
        return this.selectCount(new LambdaQueryWrapper<AccountingVoucherPO>()
                .eq(AccountingVoucherPO::getBusinessCode, businessCode)
                .eq(AccountingVoucherPO::getTradingCode, tradingCode)
                .eq(AccountingVoucherPO::getPayChannel, payChannel)
                .eq(AccountingVoucherPO::getIsDelete, 0));
    }

    /**
     * 回填事务编号到凭证
     */
    @Update("UPDATE t_accounting_voucher SET txn_no = #{txnNo}, version = version + 1 WHERE voucher_no = #{voucherNo} AND is_delete = 0")
    int updateTxnNoByVoucherNo(@Param("voucherNo") String voucherNo,
                               @Param("txnNo") String txnNo);

    /**
     * 按凭证号更新凭证状态
     */
    default void updateStatusByVoucherNo(String voucherNo, Integer status, LocalDateTime postTime) {
        LambdaUpdateWrapper<AccountingVoucherPO> wrapper = new LambdaUpdateWrapper<AccountingVoucherPO>()
                .eq(AccountingVoucherPO::getVoucherNo, voucherNo)
                .eq(AccountingVoucherPO::getIsDelete, 0)
                .set(AccountingVoucherPO::getStatus, status);
        if (postTime != null) {
            wrapper.set(AccountingVoucherPO::getPostTime, postTime);
        }
        this.update(null, wrapper);
    }

    /**
     * 按凭证号查询凭证
     */
    default AccountingVoucherPO selectByVoucherNo(String voucherNo) {
        return this.selectOne(new LambdaQueryWrapper<AccountingVoucherPO>()
                .eq(AccountingVoucherPO::getVoucherNo, voucherNo)
                .eq(AccountingVoucherPO::getIsDelete, 0));
    }

    /**
     * 更新凭证状态 + 记账人（红冲完成后标记原凭证）
     */
    @Update("UPDATE t_accounting_voucher SET status = #{status.code}, bookkeeper_name = #{bookkeeperName}, version = version + 1 " +
            "WHERE voucher_no = #{voucherNo} AND is_delete = 0")
    int updateStatusAndBookkeeper(@Param("voucherNo") String voucherNo,
                                   @Param("status") com.kltb.accounting.core.domain.enums.VoucherStatusEnum status,
                                   @Param("bookkeeperName") String bookkeeperName);

    /**
     * 按凭证状态和会计日期范围查询凭证（批量过账 + 监控查询用）
     */
    default List<AccountingVoucherPO> selectByStatusAndDateRange(
            Integer status, LocalDate startDate, LocalDate endDate,
            String businessCode, int limit) {
        LambdaQueryWrapper<AccountingVoucherPO> wrapper = new LambdaQueryWrapper<AccountingVoucherPO>()
                .eq(AccountingVoucherPO::getIsDelete, 0);
        if (status != null) {
            wrapper.eq(AccountingVoucherPO::getStatus, status);
        }
        if (startDate != null) {
            wrapper.ge(AccountingVoucherPO::getAccountingDate, startDate);
        }
        if (endDate != null) {
            wrapper.le(AccountingVoucherPO::getAccountingDate, endDate);
        }
        if (businessCode != null && !businessCode.isEmpty()) {
            wrapper.eq(AccountingVoucherPO::getBusinessCode, businessCode);
        }
        wrapper.apply("posting_type IN ('REALTIME', 'ASYNC')")
                .orderByAsc(AccountingVoucherPO::getCreateTime)
                .last("LIMIT " + Math.max(1, limit));
        return this.selectList(wrapper);
    }

    /**
     * 按凭证状态分组统计（统计报表用）
     */
    List<Map<String, Object>> countByStatusGroup(
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate,
        @Param("businessCode") String businessCode);

}