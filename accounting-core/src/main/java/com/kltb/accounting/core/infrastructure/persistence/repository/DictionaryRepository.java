package com.kltb.accounting.core.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kltb.accounting.core.domain.enums.AvailableStatusEnum;
import com.kltb.accounting.core.infrastructure.persistence.entity.DictionaryPO;
import com.kltb.accounting.core.infrastructure.persistence.mapper.DictionaryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 字典持久化仓储
 */
@Repository
@RequiredArgsConstructor
public class DictionaryRepository {

    private final DictionaryMapper dictionaryMapper;

    /**
     * 按字典类型查询字典项列表
     *
     * @param dictType 字典类型编码
     * @return 字典项列表，无数据时返回空列表
     */
    public List<DictionaryPO> selectByType(String dictType) {
        List<DictionaryPO> result = dictionaryMapper.selectByType(dictType);
        return result != null ? result : Collections.emptyList();
    }

    /**
     * 按分组键查询字典项
     *
     * @param groupKey 分组键
     * @return 字典项列表，无数据时返回空列表
     */
    public List<DictionaryPO> selectByGroupKey(String groupKey) {
        List<DictionaryPO> result = dictionaryMapper.selectByGroupKey(groupKey);
        return result != null ? result : Collections.emptyList();
    }

    /**
     * 按字典类型和编码查询单个字典项
     *
     * @param dictType 字典类型编码
     * @param dictCode 字典项编码
     * @return 字典项PO，不存在时返回null
     */
    public DictionaryPO selectByTypeAndCode(String dictType, String dictCode) {
        return dictionaryMapper.selectOne(new LambdaQueryWrapper<DictionaryPO>()
                .eq(DictionaryPO::getDictType, dictType)
                .eq(DictionaryPO::getDictCode, dictCode)
                .eq(DictionaryPO::getIsDelete, 0));
    }

    /**
     * 批量按类型查询字典，返回按类型分组的结果
     *
     * @param dictTypes 字典类型列表
     * @return 按 dictType 分组的 Map
     */
    public Map<String, List<DictionaryPO>> selectByTypes(List<String> dictTypes) {
        if (dictTypes == null || dictTypes.isEmpty()) {
            return Collections.emptyMap();
        }
        List<DictionaryPO> list = dictionaryMapper.selectList(new LambdaQueryWrapper<DictionaryPO>()
                .in(DictionaryPO::getDictType, dictTypes)
                .eq(DictionaryPO::getStatus, AvailableStatusEnum.ENABLED)
                .eq(DictionaryPO::getIsDelete, 0)
                .orderByAsc(DictionaryPO::getDictType)
                .orderByAsc(DictionaryPO::getSortOrder));
        return list.stream().collect(Collectors.groupingBy(DictionaryPO::getDictType));
    }

    /**
     * 插入字典项
     */
    public void insert(DictionaryPO dictionary) {
        dictionaryMapper.insert(dictionary);
    }

    /**
     * 更新字典项
     */
    public boolean updateById(DictionaryPO dictionary) {
        return dictionaryMapper.updateById(dictionary) > 0;
    }

    /**
     * 分页查询字典
     */
    public Page<DictionaryPO> selectPage(Page<DictionaryPO> page, LambdaQueryWrapper<DictionaryPO> wrapper) {
        return dictionaryMapper.selectPage(page, wrapper);
    }

    /**
     * 逻辑删除字典项
     */
    public boolean logicDeleteById(Long id, Long isDelete) {
        return dictionaryMapper.logicDeleteById(id, isDelete) > 0;
    }
}
