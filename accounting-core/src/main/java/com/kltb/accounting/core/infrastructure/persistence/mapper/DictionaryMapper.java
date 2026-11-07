// accounting-core/src/main/java/com/kltb/accounting/core/infrastructure/persistence/mapper/DictionaryMapper.java
package com.kltb.accounting.core.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kltb.accounting.core.infrastructure.persistence.entity.DictionaryPO;
import org.apache.ibatis.annotations.Mapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kltb.accounting.core.domain.enums.AvailableStatusEnum;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 字典 Mapper
 * <p>
 * 对应表：t_dictionary
 */
@Mapper
public interface DictionaryMapper extends BaseMapper<DictionaryPO> {

    /**
     * 按字典类型查询字典项列表
     *
     * @param dictType 字典类型编码
     * @return 字典项列表
     */
    default List<DictionaryPO> selectByType(String dictType) {
        return this.selectList(new LambdaQueryWrapper<DictionaryPO>()
                .eq(DictionaryPO::getDictType, dictType)
                .eq(DictionaryPO::getStatus, AvailableStatusEnum.ENABLED)
                .eq(DictionaryPO::getIsDelete, 0)
                .orderByAsc(DictionaryPO::getSortOrder));
    }

    /**
     * 按分组键查询字典项
     *
     * @param groupKey 分组键
     * @return 字典项列表
     */
    default List<DictionaryPO> selectByGroupKey(String groupKey) {
        return this.selectList(new LambdaQueryWrapper<DictionaryPO>()
                .eq(DictionaryPO::getGroupKey, groupKey)
                .eq(DictionaryPO::getStatus, AvailableStatusEnum.ENABLED)
                .eq(DictionaryPO::getIsDelete, 0)
                .orderByAsc(DictionaryPO::getSortOrder));
    }

    /**
     * 逻辑删除字典项（直接更新 is_delete 时间戳，绕过 @TableLogic 在 updateById 中忽略该字段的问题）
     *
     * @param id       字典ID
     * @param isDelete 删除时间戳
     * @return 影响行数
     */
    @Update("UPDATE t_dictionary SET is_delete = #{isDelete}, update_time = NOW() WHERE id = #{id} AND is_delete = 0")
    int logicDeleteById(@org.apache.ibatis.annotations.Param("id") Long id, @org.apache.ibatis.annotations.Param("isDelete") Long isDelete);
}
