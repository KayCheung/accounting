// accounting-core/src/main/java/com/kltb/accounting/core/infrastructure/persistence/mapper/ManualVoucherApplyAuxiliaryMapper.java
package com.kltb.accounting.core.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kltb.accounting.core.infrastructure.persistence.entity.ManualVoucherApplyAuxiliaryPO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 手工记账申请辅助核算分摊明细 Mapper
 */
@Mapper
public interface ManualVoucherApplyAuxiliaryMapper extends BaseMapper<ManualVoucherApplyAuxiliaryPO> {
}
