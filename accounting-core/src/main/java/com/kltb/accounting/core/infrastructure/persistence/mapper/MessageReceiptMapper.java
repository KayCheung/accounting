// accounting-core/src/main/java/com/kltb/accounting/core/infrastructure/persistence/mapper/MessageReceiptMapper.java
package com.kltb.accounting.core.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kltb.accounting.core.infrastructure.persistence.entity.MessageReceiptPO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 消息回执 Mapper
 * <p>
 * 对应表：t_message_receipt
 * ⚠️ 无 tenant_id 字段，忽略租户拦截器
 */
@Mapper
@InterceptorIgnore(tenantLine = "true")
public interface MessageReceiptMapper extends BaseMapper<MessageReceiptPO> {
}
