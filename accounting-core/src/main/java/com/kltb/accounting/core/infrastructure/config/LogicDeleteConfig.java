// accounting-core/src/main/java/com/kltb/accounting/core/infrastructure/config/LogicDeleteConfig.java
package com.kltb.accounting.core.infrastructure.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 逻辑删除动态时间戳说明
 * <p>
 * MyBatis-Plus 全局 logic-delete-value 不支持动态值（如 System.currentTimeMillis()），
 * 因此逻辑删除的实际赋值策略如下：
 * <p>
 * 1. application.yml 中 logic-delete-value=1 仅作为占位，不实际使用
 * 2. 所有 PO 类的 isDelete 字段标注 @TableLogic
 * 3. 执行逻辑删除时，必须通过 Mapper 的 update 方法手动设置：
 *    isDelete = System.currentTimeMillis()
 *    而非依赖 MyBatis-Plus 的 removeById 自动填充
 * <p>
 * 示例（在 Repository 实现中）：
 * <pre>
 *   LambdaUpdateWrapper<XxxPO> wrapper = new LambdaUpdateWrapper<>();
 *   wrapper.eq(XxxPO::getId, id)
 *          .set(XxxPO::getIsDelete, System.currentTimeMillis());
 *   mapper.update(null, wrapper);
 * </pre>
 * <p>
 * 带唯一索引的表，唯一索引字段必须包含 is_delete，解决逻辑删除后的唯一冲突。
 */
@Component
@Primary
public class LogicDeleteConfig implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        LocalDateTime now = LocalDateTime.now();
        this.strictInsertFill(metaObject, "createTime", LocalDateTime.class, now);
        this.strictInsertFill(metaObject, "updateTime", LocalDateTime.class, now);
        // 插入时 isDelete 默认为 0（未删除），由数据库 DEFAULT 保证，无需此处填充
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        this.strictUpdateFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
    }
}
