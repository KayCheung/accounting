package com.kltb.accounting.core.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kltb.accounting.core.infrastructure.persistence.dto.SubjectTreeNodeDTO;
import com.kltb.accounting.core.infrastructure.persistence.entity.AccountSubjectAuxiliaryPO;
import com.kltb.accounting.core.infrastructure.persistence.entity.AccountSubjectPO;
import com.kltb.accounting.core.infrastructure.persistence.entity.AccountTemplatePO;
import com.kltb.accounting.core.infrastructure.persistence.mapper.AccountSubjectAuxiliaryMapper;
import com.kltb.accounting.core.infrastructure.persistence.mapper.AccountSubjectMapper;
import com.kltb.accounting.core.infrastructure.persistence.mapper.AccountTemplateMapper;
import com.kltb.accounting.core.infrastructure.persistence.mapper.AccountingRuleDetailMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 科目与模板持久化仓储
 */
@Repository
@RequiredArgsConstructor
public class SubjectRepository {

    private final AccountSubjectMapper subjectMapper;
    private final AccountSubjectAuxiliaryMapper subjectAuxiliaryMapper;
    private final AccountTemplateMapper templateMapper;
    private final AccountingRuleDetailMapper ruleDetailMapper;

    /**
     * 按父科目ID查询子科目列表
     *
     * @param parentSubjectId 父科目ID
     * @return 子科目列表，无数据时返回空列表
     */
    public List<AccountSubjectPO> selectTreeByParent(Long parentSubjectId) {
        List<AccountSubjectPO> result = subjectMapper.selectTreeByParent(parentSubjectId);
        return result != null ? result : Collections.emptyList();
    }

    /**
     * 查询完整科目树（从指定节点递归加载所有子科目）
     * <p>
     * MySQL 5.7 不支持 CTE 递归，故在内存中构建树。
     * 一次性查询所有科目，按 parent_subject_id 分组后递归挂载。
     *
     * @param rootSubjectId 根节点科目ID（传0表示从顶级科目开始）
     * @return 树形结构的科目节点列表
     */
    public List<SubjectTreeNodeDTO> selectSubjectTree(Long rootSubjectId) {
        List<AccountSubjectPO> allSubjects = subjectMapper.selectList(
                new LambdaQueryWrapper<AccountSubjectPO>()
                        .orderByAsc(AccountSubjectPO::getSubjectCode));
        if (allSubjects == null || allSubjects.isEmpty()) {
            return Collections.emptyList();
        }

        Map<Long, List<AccountSubjectPO>> childrenMap = allSubjects.stream()
                .collect(Collectors.groupingBy(AccountSubjectPO::getParentSubjectId, LinkedHashMap::new, Collectors.toList()));

        long targetId = rootSubjectId != null && rootSubjectId > 0 ? rootSubjectId : 0L;
        return buildSubjectTree(targetId, childrenMap);
    }

    private List<SubjectTreeNodeDTO> buildSubjectTree(Long parentId, Map<Long, List<AccountSubjectPO>> childrenMap) {
        List<AccountSubjectPO> children = childrenMap.get(parentId);
        if (children == null || children.isEmpty()) {
            return Collections.emptyList();
        }

        List<SubjectTreeNodeDTO> tree = new ArrayList<>(children.size());
        for (AccountSubjectPO child : children) {
            SubjectTreeNodeDTO node = new SubjectTreeNodeDTO();
            node.setSubject(child);
            node.setChildren(buildSubjectTree(child.getId(), childrenMap));
            tree.add(node);
        }
        return tree;
    }

    /**
     * 查询末级且允许记账的科目
     *
     * @return 可记账科目列表，无数据时返回空列表
     */
    public List<AccountSubjectPO> selectLeafForPosting() {
        List<AccountSubjectPO> result = subjectMapper.selectLeafForPosting();
        return result != null ? result : Collections.emptyList();
    }

    /**
     * 按科目编码查询科目
     *
     * @param subjectCode 科目编码
     * @return 科目PO，不存在时返回null
     */
    public AccountSubjectPO selectByCode(String subjectCode) {
        return subjectMapper.selectOne(new LambdaQueryWrapper<AccountSubjectPO>()
                .eq(AccountSubjectPO::getSubjectCode, subjectCode));
    }

    /**
     * 按ID查询科目
     */
    public AccountSubjectPO selectById(Long id) {
        return subjectMapper.selectOne(new LambdaQueryWrapper<AccountSubjectPO>()
                .eq(AccountSubjectPO::getId, id));
    }

    /**
     * 批量按ID查询科目
     */
    public List<AccountSubjectPO> selectBatchIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        return subjectMapper.selectList(new LambdaQueryWrapper<AccountSubjectPO>()
                .in(AccountSubjectPO::getId, ids));
    }

    /**
     * 条件查询科目列表
     */
    public List<AccountSubjectPO> selectList(LambdaQueryWrapper<AccountSubjectPO> wrapper) {
        return subjectMapper.selectList(wrapper);
    }

    /**
     * 插入科目
     */
    public void insertSubject(AccountSubjectPO subject) {
        subjectMapper.insert(subject);
    }

    /**
     * 更新科目
     */
    public boolean updateSubjectById(AccountSubjectPO subject) {
        return subjectMapper.updateById(subject) > 0;
    }

    /**
     * 按科目编码查询辅助核算项
     *
     * @param subjectCode 科目编码
     * @return 辅助核算项列表，无数据时返回空列表
     */
    public List<AccountSubjectAuxiliaryPO> selectAuxiliaryBySubjectCode(String subjectCode) {
        return subjectAuxiliaryMapper.selectList(new LambdaQueryWrapper<AccountSubjectAuxiliaryPO>()
                .eq(AccountSubjectAuxiliaryPO::getSubjectCode, subjectCode));
    }

    /**
     * 按业务键查询开户模板
     *
     * @param businessCode 业务线编码
     * @param customerType 客户类型
     * @param subjectCode 科目编码
     * @return 模板PO，不存在时返回null
     */
    public AccountTemplatePO selectTemplateByBusinessKey(String businessCode,
                                                          Integer customerType,
                                                          String subjectCode) {
        return templateMapper.selectByBusinessKey(businessCode, customerType, subjectCode);
    }

    /**
     * 按业务线编码+客户类型查询首个启用的开户模板（subjectCode 为空时的兜底匹配）
     */
    public AccountTemplatePO selectFirstEnabledTemplate(String businessCode, Integer customerType) {
        return templateMapper.selectFirstEnabledByBusinessAndCustomer(businessCode, customerType, 2);
    }

    /**
     * 按业务线编码+客户类型查询所有启用的开户模板列表（支持单模板多科目账户）
     */
    public List<AccountTemplatePO> selectEnabledTemplates(String businessCode, Integer customerType) {
        return templateMapper.selectEnabledByBusinessAndCustomer(businessCode, customerType, 2);
    }

    /**
     * 插入开户模板
     */
    public void insertTemplate(AccountTemplatePO template) {
        templateMapper.insert(template);
    }

    /**
     * 更新开户模板
     */
    public boolean updateTemplateById(AccountTemplatePO template) {
        return templateMapper.updateById(template) > 0;
    }

    /**
     * 分页查询科目
     */
    public Page<AccountSubjectPO> pageSubject(Page<AccountSubjectPO> pageParam,
                                               LambdaQueryWrapper<AccountSubjectPO> wrapper) {
        return subjectMapper.selectPage(pageParam, wrapper);
    }

    /**
     * 检查是否存在子科目（按父科目编码前缀匹配）
     *
     * @param parentSubjectCode 父科目编码
     * @return true 表示存在子科目
     */
    public boolean existsSubjectByParentCode(String parentSubjectCode) {
        return subjectMapper.existsByParentCode(parentSubjectCode);
    }

    /**
     * 统计关联该科目的开户模板数（停用科目校验用）
     */
    public long countTemplateBySubjectCode(String subjectCode) {
        return templateMapper.countBySubjectCode(subjectCode);
    }

    /**
     * 统计关联该科目的记账规则明细数（停用科目校验用）
     */
    public long countRuleDetailBySubjectCode(String subjectCode) {
        return ruleDetailMapper.countBySubjectCode(subjectCode);
    }

    /**
     * 按ID查询开户模板
     */
    public AccountTemplatePO selectTemplateById(Long id) {
        return templateMapper.selectOne(new LambdaQueryWrapper<AccountTemplatePO>()
                .eq(AccountTemplatePO::getId, id));
    }

    /**
     * 分页查询开户模板
     */
    public Page<AccountTemplatePO> pageTemplate(Page<AccountTemplatePO> pageParam,
                                                 LambdaQueryWrapper<AccountTemplatePO> wrapper) {
        return templateMapper.selectPage(pageParam, wrapper);
    }

    /**
     * 查询所有 allow_open_account=1 且 is_leaf=1 的科目（批量扫描内部账户用）
     *
     * @return 可开户末级科目列表，无数据时返回空列表
     */
    public List<AccountSubjectPO> selectAllowOpenAccountLeafSubjects() {
        List<AccountSubjectPO> result = subjectMapper.selectAllowOpenAccountLeafSubjects();
        return result != null ? result : Collections.emptyList();
    }
}
