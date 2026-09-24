// accounting-core/src/main/java/com/kltb/accounting/core/infrastructure/persistence/mapper/LocalMessageMapper.java
package com.kltb.accounting.core.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kltb.accounting.core.infrastructure.persistence.entity.LocalMessagePO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 本地消息（Outbox） Mapper
 * <p>
 * 对应表：t_local_message
 */
@Mapper
public interface LocalMessageMapper extends BaseMapper<LocalMessagePO> {
}
