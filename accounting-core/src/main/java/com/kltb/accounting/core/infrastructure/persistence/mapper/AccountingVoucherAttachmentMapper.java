// accounting-core/src/main/java/com/kltb/accounting/core/infrastructure/persistence/mapper/AccountingVoucherAttachmentMapper.java
package com.kltb.accounting.core.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kltb.accounting.core.infrastructure.persistence.entity.AccountingVoucherAttachmentPO;
import org.apache.ibatis.annotations.Mapper;
/**
 * 记账凭证附件表数据访问层。
 */
@Mapper
public interface AccountingVoucherAttachmentMapper extends BaseMapper<AccountingVoucherAttachmentPO> {
}