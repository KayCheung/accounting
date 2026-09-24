// accounting-core/src/main/java/com/kltb/accounting/core/infrastructure/persistence/mapper/AccountingVoucherAuxiliaryMapper.java
package com.kltb.accounting.core.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kltb.accounting.core.infrastructure.persistence.entity.AccountingVoucherAuxiliaryPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 记账凭证辅助核算项目数据访问层。
 */
@Mapper
public interface AccountingVoucherAuxiliaryMapper extends BaseMapper<AccountingVoucherAuxiliaryPO> {

    /**
     * 批量插入辅助核算项（红冲用）
     */
    int batchInsert(@Param("list") List<AccountingVoucherAuxiliaryPO> list);
}