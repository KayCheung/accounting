// accounting-core/src/main/java/com/kltb/accounting/core/infrastructure/persistence/mapper/ManualVoucherApplyAttachmentMapper.java
package com.kltb.accounting.core.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kltb.accounting.core.infrastructure.persistence.entity.ManualVoucherApplyAttachmentPO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 手工记账申请附件 Mapper
 */
@Mapper
public interface ManualVoucherApplyAttachmentMapper extends BaseMapper<ManualVoucherApplyAttachmentPO> {
}
