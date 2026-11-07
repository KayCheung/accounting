// accounting-core/src/main/java/com/kltb/accounting/core/infrastructure/persistence/mapper/AccountSubjectAuxiliaryMapper.java
package com.kltb.accounting.core.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kltb.accounting.core.infrastructure.persistence.entity.AccountSubjectAuxiliaryPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Update;

/**
 * 会计科目辅助核算项 Mapper
 * <p>
 * 对应表：t_account_subject_auxiliary
 */
@Mapper
public interface AccountSubjectAuxiliaryMapper extends BaseMapper<AccountSubjectAuxiliaryPO> {

    /**
     * 逻辑删除辅助核算项（直接更新 is_delete 时间戳，绕过 @TableLogic 在 updateById 中忽略该字段的问题）
     *
     * @param id       核算项ID
     * @param isDelete 删除时间戳
     * @return 影响行数
     */
    @Update("UPDATE t_account_subject_auxiliary SET is_delete = #{isDelete}, update_time = NOW() WHERE id = #{id} AND is_delete = 0")
    int logicDeleteById(@org.apache.ibatis.annotations.Param("id") Long id, @org.apache.ibatis.annotations.Param("isDelete") Long isDelete);
}
