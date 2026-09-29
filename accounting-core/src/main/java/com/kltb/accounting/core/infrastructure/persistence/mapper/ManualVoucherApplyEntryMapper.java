// accounting-core/src/main/java/com/kltb/accounting/core/infrastructure/persistence/mapper/ManualVoucherApplyEntryMapper.java
package com.kltb.accounting.core.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kltb.accounting.core.infrastructure.persistence.entity.ManualVoucherApplyEntryPO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 手工记账申请分录明细 Mapper
 */
@Mapper
public interface ManualVoucherApplyEntryMapper extends BaseMapper<ManualVoucherApplyEntryPO> {
}
