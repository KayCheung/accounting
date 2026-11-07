// accounting-core/src/main/java/com/kltb/accounting/core/infrastructure/persistence/mapper/AccountBalanceMapper.java
package com.kltb.accounting.core.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kltb.accounting.core.infrastructure.persistence.entity.AccountBalancePO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 账户日余额表 Mapper 接口（按年分区）
 * <p>
 * DDL: docs/sql/1-account.sql (t_account_balance)
 * </p>
 */
@Mapper
public interface AccountBalanceMapper extends BaseMapper<AccountBalancePO> {

    /**
     * 批量上插/更新账户日余额（INSERT ... ON DUPLICATE KEY UPDATE）
     *
     * @param list 余额记录列表
     * @return 影响行数
     */
    int batchUpsertBalance(@Param("list") List<AccountBalancePO> list);

    /**
     * 按科目汇总日余额（总分核对用）
     *
     * @param accountingDate 会计日期
     * @return 科目汇总列表，每项包含 subject_code / total_balance / total_debit / total_credit / account_count
     */
    List<Map<String, Object>> sumBalancesBySubject(@Param("accountingDate") LocalDate accountingDate);
}
