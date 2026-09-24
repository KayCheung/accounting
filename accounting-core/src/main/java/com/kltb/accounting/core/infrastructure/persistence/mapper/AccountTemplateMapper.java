// accounting-core/src/main/java/com/kltb/accounting/core/infrastructure/persistence/mapper/AccountTemplateMapper.java
package com.kltb.accounting.core.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kltb.accounting.core.infrastructure.persistence.entity.AccountTemplatePO;
import org.apache.ibatis.annotations.Mapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kltb.accounting.core.domain.enums.AvailableStatusEnum;

/**
 * 外部客户账户开户模板 Mapper
 * <p>
 * 对应表：t_account_template
 */
@Mapper
public interface AccountTemplateMapper extends BaseMapper<AccountTemplatePO> {

    /**
     * 按业务键查询开户模板
     *
     * @param businessCode 业务线编码
     * @param customerType 客户类型
     * @param subjectCode 科目编码
     * @return 开户模板PO，不存在时返回null
     */
    default AccountTemplatePO selectByBusinessKey(String businessCode,
                                                   Integer customerType,
                                                   String subjectCode) {
        return this.selectOne(new LambdaQueryWrapper<AccountTemplatePO>()
                .eq(AccountTemplatePO::getBusinessCode, businessCode)
                .eq(AccountTemplatePO::getCustomerType, customerType)
                .eq(AccountTemplatePO::getSubjectCode, subjectCode));
    }

    /**
     * 按科目编码统计开户模板数量（停用科目校验用）
     *
     * @param subjectCode 科目编码
     * @return 关联该科目的未删除模板数
     */
    default int countBySubjectCode(String subjectCode) {
        return this.selectCount(new LambdaQueryWrapper<AccountTemplatePO>()
                .eq(AccountTemplatePO::getSubjectCode, subjectCode)).intValue();
    }

    /**
     * 按业务线编码+客户类型查询首个启用的开户模板（subjectCode 为空时的兜底匹配）
     *
     * @param businessCode 业务线编码
     * @param customerType 客户类型
     * @param status 模板状态（通常传 2=启用）
     * @return 开户模板PO，不存在时返回null
     */
    default AccountTemplatePO selectFirstEnabledByBusinessAndCustomer(String businessCode,
                                                                       Integer customerType,
                                                                       Integer status) {
        return this.selectOne(new LambdaQueryWrapper<AccountTemplatePO>()
                .eq(AccountTemplatePO::getBusinessCode, businessCode)
                .eq(AccountTemplatePO::getCustomerType, customerType)
                .eq(AccountTemplatePO::getStatus, status)
                .orderByAsc(AccountTemplatePO::getId)
                .last("LIMIT 1"));
    }

    /**
     * 按业务线编码+客户类型查询所有启用的开户模板列表（支持单模板多科目账户）
     *
     * @param businessCode 业务线编码
     * @param customerType 客户类型
     * @param status 模板状态（通常传 2=启用）
     * @return 启用的开户模板列表
     */
    default java.util.List<AccountTemplatePO> selectEnabledByBusinessAndCustomer(String businessCode,
                                                                                Integer customerType,
                                                                                Integer status) {
        return this.selectList(new LambdaQueryWrapper<AccountTemplatePO>()
                .eq(AccountTemplatePO::getBusinessCode, businessCode)
                .eq(AccountTemplatePO::getCustomerType, customerType)
                .eq(AccountTemplatePO::getStatus, status)
                .orderByAsc(AccountTemplatePO::getId));
    }
}
