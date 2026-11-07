// accounting-core/src/main/java/com/kltb/accounting/core/infrastructure/persistence/mapper/ManualVoucherAuditLogMapper.java
package com.kltb.accounting.core.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kltb.accounting.core.infrastructure.persistence.entity.ManualVoucherAuditLogPO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 手工记账流转可追溯审计日志 Mapper
 */
@Mapper
public interface ManualVoucherAuditLogMapper extends BaseMapper<ManualVoucherAuditLogPO> {
}
