package com.kltb.accounting.core.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kltb.accounting.core.infrastructure.persistence.entity.FinancialReportSnapshotPO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 财务报表归档快照表 Mapper 接口
 */
@Mapper
public interface FinancialReportSnapshotMapper extends BaseMapper<FinancialReportSnapshotPO> {
}
