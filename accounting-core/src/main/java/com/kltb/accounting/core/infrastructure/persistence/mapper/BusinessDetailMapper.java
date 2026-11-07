// accounting-core/src/main/java/com/kltb/accounting/core/infrastructure/persistence/mapper/BusinessDetailMapper.java
package com.kltb.accounting.core.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kltb.accounting.core.infrastructure.persistence.entity.BusinessDetailPO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 业务记账流水明细 Mapper
 * <p>
 * 对应表：t_business_detail
 */
@Mapper
public interface BusinessDetailMapper extends BaseMapper<BusinessDetailPO> {
}
