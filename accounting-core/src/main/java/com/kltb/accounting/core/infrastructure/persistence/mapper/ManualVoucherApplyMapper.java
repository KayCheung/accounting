// accounting-core/src/main/java/com/kltb/accounting/core/infrastructure/persistence/mapper/ManualVoucherApplyMapper.java
package com.kltb.accounting.core.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kltb.accounting.core.infrastructure.persistence.entity.ManualVoucherApplyPO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 手工记账申请单 Mapper
 */
@Mapper
public interface ManualVoucherApplyMapper extends BaseMapper<ManualVoucherApplyPO> {
}
