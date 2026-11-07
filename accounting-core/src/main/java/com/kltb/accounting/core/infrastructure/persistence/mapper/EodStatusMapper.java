package com.kltb.accounting.core.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kltb.accounting.core.infrastructure.persistence.entity.EodStatusPO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 日切状态表 Mapper 接口（Step 17S 新增）
 * <p>
 * DDL: docs/sql/6-infra.sql (t_eod_status)
 * </p>
 */
@Mapper
public interface EodStatusMapper extends BaseMapper<EodStatusPO> {
}
