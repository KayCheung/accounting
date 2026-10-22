package com.kltb.accounting.core.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kltb.accounting.core.infrastructure.persistence.entity.FinancialReportItemMappingPO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 财务报表行项目映射配置 Mapper
 */
@Mapper
public interface FinancialReportItemMappingMapper extends BaseMapper<FinancialReportItemMappingPO> {
}
