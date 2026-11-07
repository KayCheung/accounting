// accounting-core/src/main/java/com/kltb/accounting/core/infrastructure/persistence/mapper/AccountSubjectMapper.java
package com.kltb.accounting.core.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kltb.accounting.core.infrastructure.persistence.entity.AccountSubjectPO;
import org.apache.ibatis.annotations.Mapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import java.util.List;

/**
 * 会计科目 Mapper
 * <p>
 * 对应表：t_account_subject
 */
@Mapper
public interface AccountSubjectMapper extends BaseMapper<AccountSubjectPO> {

    /**
     * 按父科目ID查询子科目列表
     *
     * @param parentSubjectId 父科目ID
     * @return 子科目列表
     */
    default List<AccountSubjectPO> selectTreeByParent(Long parentSubjectId) {
        return this.selectList(new LambdaQueryWrapper<AccountSubjectPO>()
                .eq(AccountSubjectPO::getParentSubjectId, parentSubjectId)
                .eq(AccountSubjectPO::getIsDelete, 0)
                .orderByAsc(AccountSubjectPO::getSubjectCode));
    }

    /**
     * 查询末级且允许记账的科目
     *
     * @return 可记账科目列表
     */
    default List<AccountSubjectPO> selectLeafForPosting() {
        return this.selectList(new LambdaQueryWrapper<AccountSubjectPO>()
                .eq(AccountSubjectPO::getLeaf, 1)
                .eq(AccountSubjectPO::getAllowPost, 1)
                .eq(AccountSubjectPO::getStatus, 1)
                .eq(AccountSubjectPO::getIsDelete, 0));
    }

    /**
     * 检查是否存在子科目（按父科目编码前缀匹配）
     *
     * @param parentSubjectCode 父科目编码
     * @return true 表示存在子科目
     */
    default boolean existsByParentCode(String parentSubjectCode) {
        return this.selectCount(new LambdaQueryWrapper<AccountSubjectPO>()
                .likeRight(AccountSubjectPO::getSubjectCode, parentSubjectCode)
                .ne(AccountSubjectPO::getSubjectCode, parentSubjectCode)
                .eq(AccountSubjectPO::getIsDelete, 0)) > 0;
    }

    /**
     * 查询所有 allow_open_account=1 且 is_leaf=1 的科目（批量扫描内部账户用）
     *
     * @return 可开户末级科目列表
     */
    default List<AccountSubjectPO> selectAllowOpenAccountLeafSubjects() {
        return this.selectList(new LambdaQueryWrapper<AccountSubjectPO>()
                .eq(AccountSubjectPO::getAllowOpenAccount, 1)
                .eq(AccountSubjectPO::getLeaf, 1)
                .eq(AccountSubjectPO::getIsDelete, 0));
    }
}
