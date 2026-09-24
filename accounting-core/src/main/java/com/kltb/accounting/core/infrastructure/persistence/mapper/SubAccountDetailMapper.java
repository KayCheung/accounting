// accounting-core/src/main/java/com/kltb/accounting/core/infrastructure/persistence/mapper/SubAccountDetailMapper.java
package com.kltb.accounting.core.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kltb.accounting.core.infrastructure.persistence.entity.SubAccountDetailPO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 子账户明细表 Mapper 接口
 * <p>
 * DDL: docs/sql/1-account.sql (t_sub_account_detail)
 * </p>
 */
@Mapper
public interface SubAccountDetailMapper extends BaseMapper<SubAccountDetailPO> {
}
