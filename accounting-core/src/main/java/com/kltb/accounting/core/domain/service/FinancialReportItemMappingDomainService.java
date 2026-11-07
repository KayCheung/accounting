package com.kltb.accounting.core.domain.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kltb.accounting.api.constant.ResultCode;
import com.kltb.accounting.core.domain.enums.AvailableStatusEnum;
import com.kltb.accounting.core.domain.enums.ReportBalanceDirectionEnum;
import com.kltb.accounting.core.domain.enums.ReportCalcOperatorEnum;
import com.kltb.accounting.core.domain.enums.ReportDisplayLevelEnum;
import com.kltb.accounting.core.domain.enums.ReportTypeEnum;
import com.kltb.accounting.core.infrastructure.persistence.entity.FinancialReportItemMappingPO;
import com.kltb.accounting.core.infrastructure.persistence.repository.FinancialReportItemMappingRepository;
import com.kltb.accounting.core.infrastructure.persistence.repository.SubjectRepository;
import com.kltb.accounting.core.shared.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 财务报表行项目映射领域服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FinancialReportItemMappingDomainService {

    private final FinancialReportItemMappingRepository mappingRepository;
    private final SubjectRepository subjectRepository;

    /**
     * 按报表类型获取全部启用的映射行项（供报表引擎动态聚合使用）
     */
    public List<FinancialReportItemMappingPO> getEnabledMappings(String reportType) {
        return mappingRepository.selectEnabledByReportType(reportType);
    }

    /**
     * 分页查询报表映射配置
     */
    public Page<FinancialReportItemMappingPO> pageMappings(int pageNo, int pageSize,
                                                          String reportType, String keyword,
                                                          String itemCategory, Integer displayLevel,
                                                          Integer status) {
        return mappingRepository.selectPage(pageNo, pageSize, reportType, keyword, itemCategory, displayLevel, status);
    }

    /**
     * 按 ID 获取详情
     */
    public FinancialReportItemMappingPO getById(Long id) {
        FinancialReportItemMappingPO po = mappingRepository.selectById(id);
        if (po == null) {
            throw new ServiceException(ResultCode.DATA_NOT_FOUND, "报表行项配置不存在，ID: " + id);
        }
        return po;
    }

    /**
     * 新增报表行项映射
     */
    public Long createMapping(FinancialReportItemMappingPO po) {
        if (po.getReportType() == null) {
            throw new ServiceException(ResultCode.PARAM_ERROR, "报表类型不能为空");
        }
        if (!StringUtils.hasText(po.getItemCode())) {
            throw new ServiceException(ResultCode.PARAM_ERROR, "行项目编码不能为空");
        }

        // 1. 唯一性校验：同一报表类型下 itemCode 不可重复
        FinancialReportItemMappingPO exist = mappingRepository.selectByReportTypeAndItemCode(
                po.getReportType(), po.getItemCode());
        if (exist != null) {
            throw new ServiceException(ResultCode.PARAM_ERROR,
                    String.format("报表[%s]已存在相同行项编码[%s]", po.getReportType().getCode(), po.getItemCode()));
        }

        // 2. 校验科目代码格式及有效性（若配置了科目）
        validateSubjectCodes(po.getSubjectCodes());

        // 3. 默认值处理
        if (po.getStatus() == null) {
            po.setStatus(AvailableStatusEnum.ENABLED);
        }
        if (po.getDisplayLevel() == null) {
            po.setDisplayLevel(ReportDisplayLevelEnum.DETAIL);
        }
        if (po.getCalcOperator() == null) {
            po.setCalcOperator(ReportCalcOperatorEnum.ADD);
        }
        if (po.getBalanceDirection() == null) {
            po.setBalanceDirection(ReportBalanceDirectionEnum.AUTO);
        }

        mappingRepository.insert(po);
        log.info("新增报表行项映射成功, id={}, reportType={}, itemCode={}", po.getId(), po.getReportType().getCode(), po.getItemCode());
        return po.getId();
    }

    /**
     * 修改报表行项映射
     */
    public void updateMapping(Long id, FinancialReportItemMappingPO updateParam) {
        FinancialReportItemMappingPO po = getById(id);

        validateSubjectCodes(updateParam.getSubjectCodes());

        po.setItemName(updateParam.getItemName());
        po.setItemCategory(updateParam.getItemCategory());
        po.setLineNo(updateParam.getLineNo());
        po.setDisplayLevel(updateParam.getDisplayLevel());
        po.setSubjectCodes(updateParam.getSubjectCodes());
        po.setCalcOperator(updateParam.getCalcOperator());
        po.setBalanceDirection(updateParam.getBalanceDirection());
        po.setFormulaExpr(updateParam.getFormulaExpr());
        if (updateParam.getStatus() != null) {
            po.setStatus(updateParam.getStatus());
        }
        po.setRemark(updateParam.getRemark());

        mappingRepository.updateById(po);
        log.info("更新报表行项映射成功, id={}, itemCode={}", id, po.getItemCode());
    }

    /**
     * 切换启用/停用状态
     */
    public void updateStatus(Long id, Integer status) {
        AvailableStatusEnum statusEnum = AvailableStatusEnum.fromCode(status);
        if (statusEnum == null) {
            throw new ServiceException(ResultCode.PARAM_ERROR, "状态值非法: " + status);
        }
        FinancialReportItemMappingPO po = getById(id);
        po.setStatus(statusEnum);
        mappingRepository.updateById(po);
        log.info("修改报表行项状态成功, id={}, status={}", id, statusEnum.getDesc());
    }

    /**
     * 删除报表行项映射
     */
    public void deleteMapping(Long id) {
        FinancialReportItemMappingPO po = getById(id);
        mappingRepository.deleteById(po.getId());
        log.info("逻辑删除报表行项成功, id={}, itemCode={}", id, po.getItemCode());
    }

    private void validateSubjectCodes(String subjectCodes) {
        if (!StringUtils.hasText(subjectCodes)) {
            return;
        }
        String[] codes = subjectCodes.split(",");
        for (String code : codes) {
            String trimmed = code.trim();
            if (!StringUtils.hasText(trimmed)) {
                continue;
            }
            if (!trimmed.matches("^[0-9]+$")) {
                throw new ServiceException(ResultCode.PARAM_ERROR, "科目代码格式非法: " + trimmed);
            }
        }
    }
}
