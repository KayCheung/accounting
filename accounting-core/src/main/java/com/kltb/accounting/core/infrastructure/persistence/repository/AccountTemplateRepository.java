package com.kltb.accounting.core.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kltb.accounting.core.infrastructure.persistence.entity.AccountTemplatePO;
import com.kltb.accounting.core.infrastructure.persistence.mapper.AccountTemplateMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.List;

/**
 * 外部客户账户开户模板持久化仓储
 * <p>
 * 负责开户模板的增删改查、多会计科目账户配置组检索与状态维护。
 */
@Repository
@RequiredArgsConstructor
public class AccountTemplateRepository {

    private final AccountTemplateMapper templateMapper;

    /**
     * 按ID查询开户模板
     */
    public AccountTemplatePO selectById(Long id) {
        if (id == null) {
            return null;
        }
        return templateMapper.selectOne(new LambdaQueryWrapper<AccountTemplatePO>()
                .eq(AccountTemplatePO::getId, id));
    }

    /**
     * 按业务键查询开户模板
     *
     * @param businessCode 业务线编码
     * @param customerType 客户类型
     * @param subjectCode  科目编码
     * @return 开户模板PO，不存在时返回null
     */
    public AccountTemplatePO selectByBusinessKey(String businessCode, Integer customerType, String subjectCode) {
        return templateMapper.selectByBusinessKey(businessCode, customerType, subjectCode);
    }

    /**
     * 按业务线、客户类型与模板名称查询模板列表（组维度）
     */
    public List<AccountTemplatePO> selectByGroup(String businessCode, Integer customerType, String templateName) {
        List<AccountTemplatePO> result = templateMapper.selectList(new LambdaQueryWrapper<AccountTemplatePO>()
                .eq(AccountTemplatePO::getBusinessCode, businessCode)
                .eq(AccountTemplatePO::getCustomerType, customerType)
                .eq(AccountTemplatePO::getTemplateName, templateName)
                .eq(AccountTemplatePO::getIsDelete, 0));
        return result != null ? result : Collections.emptyList();
    }

    /**
     * 按业务线编码+客户类型查询首个启用的开户模板（subjectCode 为空时的兜底匹配）
     */
    public AccountTemplatePO selectFirstEnabled(String businessCode, Integer customerType) {
        return templateMapper.selectFirstEnabledByBusinessAndCustomer(businessCode, customerType, 2);
    }

    /**
     * 按业务线编码+客户类型查询所有启用的开户模板列表（支持单模板多科目账户）
     */
    public List<AccountTemplatePO> selectEnabledTemplates(String businessCode, Integer customerType) {
        List<AccountTemplatePO> result = templateMapper.selectEnabledByBusinessAndCustomer(businessCode, customerType, 2);
        return result != null ? result : Collections.emptyList();
    }

    /**
     * 插入开户模板
     */
    public void insert(AccountTemplatePO template) {
        templateMapper.insert(template);
    }

    /**
     * 更新开户模板
     */
    public boolean updateById(AccountTemplatePO template) {
        return templateMapper.updateById(template) > 0;
    }

    /**
     * 逻辑删除开户模板（is_delete = id，彻底避免固定值唯一索引冲突）
     *
     * @param id 模板ID
     * @return 是否删除成功
     */
    public boolean logicDeleteById(Long id) {
        if (id == null) {
            return false;
        }
        return templateMapper.logicDeleteById(id) > 0;
    }

    /**
     * 分页查询开户模板
     */
    public Page<AccountTemplatePO> page(Page<AccountTemplatePO> pageParam,
                                         LambdaQueryWrapper<AccountTemplatePO> wrapper) {
        return templateMapper.selectPage(pageParam, wrapper);
    }

    /**
     * 统计关联该科目的开户模板数（停用科目校验用）
     */
    public long countBySubjectCode(String subjectCode) {
        return templateMapper.countBySubjectCode(subjectCode);
    }
}
