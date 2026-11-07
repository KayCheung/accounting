package com.kltb.accounting.core.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kltb.accounting.core.domain.enums.AvailableStatusEnum;
import com.kltb.accounting.core.domain.enums.ReportCategoryEnum;
import com.kltb.accounting.core.domain.enums.ReportDisplayLevelEnum;
import com.kltb.accounting.core.domain.enums.ReportTypeEnum;
import com.kltb.accounting.core.infrastructure.persistence.entity.FinancialReportItemMappingPO;
import com.kltb.accounting.core.infrastructure.persistence.mapper.FinancialReportItemMappingMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.List;

/**
 * 财务报表行项目映射持久化仓储
 */
@Repository
@RequiredArgsConstructor
public class FinancialReportItemMappingRepository {

    private final FinancialReportItemMappingMapper mappingMapper;

    /**
     * 按报表类型查询全部启用的行项映射配置（按行号 line_no 升序）
     *
     * @param reportType 报表类型 (BALANCE_SHEET, INCOME_STATEMENT 等)
     * @return 行项映射列表
     */
    public List<FinancialReportItemMappingPO> selectEnabledByReportType(String reportType) {
        ReportTypeEnum typeEnum = ReportTypeEnum.fromCode(reportType);
        if (typeEnum == null) {
            return Collections.emptyList();
        }
        return selectEnabledByReportType(typeEnum);
    }

    /**
     * 按强类型报表枚举查询启用的配置
     */
    public List<FinancialReportItemMappingPO> selectEnabledByReportType(ReportTypeEnum reportType) {
        if (reportType == null) {
            return Collections.emptyList();
        }
        LambdaQueryWrapper<FinancialReportItemMappingPO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FinancialReportItemMappingPO::getReportType, reportType)
                .eq(FinancialReportItemMappingPO::getStatus, AvailableStatusEnum.ENABLED)
                .eq(FinancialReportItemMappingPO::getIsDelete, 0L)
                .orderByAsc(FinancialReportItemMappingPO::getLineNo);
        List<FinancialReportItemMappingPO> list = mappingMapper.selectList(wrapper);
        return list != null ? list : Collections.emptyList();
    }

    /**
     * 按报表类型查询全部行项映射配置（包含已停用，按行号 line_no 升序）
     */
    public List<FinancialReportItemMappingPO> selectAllByReportType(String reportType) {
        ReportTypeEnum typeEnum = ReportTypeEnum.fromCode(reportType);
        if (typeEnum == null) {
            return Collections.emptyList();
        }
        LambdaQueryWrapper<FinancialReportItemMappingPO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FinancialReportItemMappingPO::getReportType, typeEnum)
                .eq(FinancialReportItemMappingPO::getIsDelete, 0L)
                .orderByAsc(FinancialReportItemMappingPO::getLineNo);
        List<FinancialReportItemMappingPO> list = mappingMapper.selectList(wrapper);
        return list != null ? list : Collections.emptyList();
    }

    /**
     * 分页查询报表映射配置
     */
    public Page<FinancialReportItemMappingPO> selectPage(int pageNo, int pageSize,
                                                         String reportType, String keyword,
                                                         String itemCategory, Integer displayLevel,
                                                         Integer status) {
        Page<FinancialReportItemMappingPO> page = new Page<>(pageNo, pageSize);
        LambdaQueryWrapper<FinancialReportItemMappingPO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FinancialReportItemMappingPO::getIsDelete, 0L);

        ReportTypeEnum typeEnum = ReportTypeEnum.fromCode(reportType);
        if (typeEnum != null) {
            wrapper.eq(FinancialReportItemMappingPO::getReportType, typeEnum);
        }
        ReportCategoryEnum categoryEnum = ReportCategoryEnum.fromCode(itemCategory);
        if (categoryEnum != null) {
            wrapper.eq(FinancialReportItemMappingPO::getItemCategory, categoryEnum);
        }
        ReportDisplayLevelEnum levelEnum = ReportDisplayLevelEnum.fromCode(displayLevel);
        if (levelEnum != null) {
            wrapper.eq(FinancialReportItemMappingPO::getDisplayLevel, levelEnum);
        }
        AvailableStatusEnum statusEnum = AvailableStatusEnum.fromCode(status);
        if (statusEnum != null) {
            wrapper.eq(FinancialReportItemMappingPO::getStatus, statusEnum);
        }
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(FinancialReportItemMappingPO::getItemCode, keyword.trim())
                    .or().like(FinancialReportItemMappingPO::getItemName, keyword.trim())
                    .or().like(FinancialReportItemMappingPO::getSubjectCodes, keyword.trim()));
        }

        wrapper.orderByAsc(FinancialReportItemMappingPO::getReportType)
                .orderByAsc(FinancialReportItemMappingPO::getLineNo);

        return mappingMapper.selectPage(page, wrapper);
    }

    /**
     * 按 ID 查询详情
     */
    public FinancialReportItemMappingPO selectById(Long id) {
        if (id == null) {
            return null;
        }
        LambdaQueryWrapper<FinancialReportItemMappingPO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FinancialReportItemMappingPO::getId, id)
                .eq(FinancialReportItemMappingPO::getIsDelete, 0L);
        return mappingMapper.selectOne(wrapper);
    }

    /**
     * 按报表类型和行项编码查询单条（用于唯一性校验）
     */
    public FinancialReportItemMappingPO selectByReportTypeAndItemCode(ReportTypeEnum reportType, String itemCode) {
        if (reportType == null || !StringUtils.hasText(itemCode)) {
            return null;
        }
        LambdaQueryWrapper<FinancialReportItemMappingPO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FinancialReportItemMappingPO::getReportType, reportType)
                .eq(FinancialReportItemMappingPO::getItemCode, itemCode.trim())
                .eq(FinancialReportItemMappingPO::getIsDelete, 0L);
        return mappingMapper.selectOne(wrapper);
    }

    /**
     * 插入新配置
     */
    public int insert(FinancialReportItemMappingPO po) {
        if (po.getTenantId() == null) {
            po.setTenantId(-1);
        }
        if (po.getIsDelete() == null) {
            po.setIsDelete(0L);
        }
        return mappingMapper.insert(po);
    }

    /**
     * 更新配置
     */
    public int updateById(FinancialReportItemMappingPO po) {
        return mappingMapper.updateById(po);
    }

    /**
     * 逻辑删除：设置 is_delete 为当前时间戳
     */
    public int deleteById(Long id) {
        FinancialReportItemMappingPO po = selectById(id);
        if (po == null) {
            return 0;
        }
        po.setIsDelete(System.currentTimeMillis());
        return mappingMapper.updateById(po);
    }
}
