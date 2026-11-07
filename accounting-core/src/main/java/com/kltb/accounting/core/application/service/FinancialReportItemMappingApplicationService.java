package com.kltb.accounting.core.application.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kltb.accounting.api.request.ReportItemMappingCreateRequest;
import com.kltb.accounting.api.request.ReportItemMappingQueryRequest;
import com.kltb.accounting.api.request.ReportItemMappingUpdateRequest;
import com.kltb.accounting.api.response.PageResponse;
import com.kltb.accounting.api.response.ReportItemMappingResponse;
import com.kltb.accounting.core.domain.enums.AvailableStatusEnum;
import com.kltb.accounting.core.domain.enums.ReportBalanceDirectionEnum;
import com.kltb.accounting.core.domain.enums.ReportCalcOperatorEnum;
import com.kltb.accounting.core.domain.enums.ReportCategoryEnum;
import com.kltb.accounting.core.domain.enums.ReportDisplayLevelEnum;
import com.kltb.accounting.core.domain.enums.ReportTypeEnum;
import com.kltb.accounting.core.domain.service.FinancialReportItemMappingDomainService;
import com.kltb.accounting.core.infrastructure.persistence.entity.FinancialReportItemMappingPO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 财务报表行项目映射应用服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FinancialReportItemMappingApplicationService {

    private final FinancialReportItemMappingDomainService mappingDomainService;
    private final TransactionTemplate transactionTemplate;

    /**
     * 分页查询
     */
    public PageResponse<ReportItemMappingResponse> pageMappings(ReportItemMappingQueryRequest request) {
        int pageNo = request.getPageNo() != null && request.getPageNo() > 0 ? request.getPageNo() : 1;
        int pageSize = request.getPageSize() != null && request.getPageSize() > 0 ? request.getPageSize() : 10;

        Page<FinancialReportItemMappingPO> page = mappingDomainService.pageMappings(
                pageNo, pageSize,
                request.getReportType(),
                request.getKeyword(),
                request.getItemCategory(),
                request.getDisplayLevel(),
                request.getStatus());

        List<ReportItemMappingResponse> dtoList = page.getRecords().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());

        return PageResponse.<ReportItemMappingResponse>builder()
                .total(page.getTotal())
                .pages(page.getPages())
                .current(page.getCurrent())
                .list(dtoList)
                .build();
    }

    /**
     * 查询单条详情
     */
    public ReportItemMappingResponse getById(Long id) {
        return toResponse(mappingDomainService.getById(id));
    }

    /**
     * 新增行项配置
     */
    public Long createMapping(ReportItemMappingCreateRequest request) {
        FinancialReportItemMappingPO po = new FinancialReportItemMappingPO();
        po.setReportType(ReportTypeEnum.fromCode(request.getReportType()));
        po.setItemCode(request.getItemCode());
        po.setItemName(request.getItemName());
        po.setItemCategory(ReportCategoryEnum.fromCode(request.getItemCategory()));
        po.setLineNo(request.getLineNo());
        po.setDisplayLevel(ReportDisplayLevelEnum.fromCode(request.getDisplayLevel()));
        po.setSubjectCodes(request.getSubjectCodes());
        po.setCalcOperator(ReportCalcOperatorEnum.fromCode(request.getCalcOperator()));
        po.setBalanceDirection(ReportBalanceDirectionEnum.fromCode(request.getBalanceDirection()));
        po.setFormulaExpr(request.getFormulaExpr());
        po.setStatus(request.getStatus() != null
                ? AvailableStatusEnum.fromCode(request.getStatus())
                : AvailableStatusEnum.ENABLED);
        po.setRemark(request.getRemark());

        return transactionTemplate.execute(status -> mappingDomainService.createMapping(po));
    }

    /**
     * 更新行项配置
     */
    public void updateMapping(Long id, ReportItemMappingUpdateRequest request) {
        FinancialReportItemMappingPO po = new FinancialReportItemMappingPO();
        po.setItemName(request.getItemName());
        po.setItemCategory(ReportCategoryEnum.fromCode(request.getItemCategory()));
        po.setLineNo(request.getLineNo());
        po.setDisplayLevel(ReportDisplayLevelEnum.fromCode(request.getDisplayLevel()));
        po.setSubjectCodes(request.getSubjectCodes());
        po.setCalcOperator(ReportCalcOperatorEnum.fromCode(request.getCalcOperator()));
        po.setBalanceDirection(ReportBalanceDirectionEnum.fromCode(request.getBalanceDirection()));
        po.setFormulaExpr(request.getFormulaExpr());
        if (request.getStatus() != null) {
            po.setStatus(AvailableStatusEnum.fromCode(request.getStatus()));
        }
        po.setRemark(request.getRemark());

        transactionTemplate.executeWithoutResult(status -> mappingDomainService.updateMapping(id, po));
    }

    /**
     * 切换状态
     */
    public void updateStatus(Long id, Integer status) {
        transactionTemplate.executeWithoutResult(txStatus -> mappingDomainService.updateStatus(id, status));
    }

    /**
     * 删除行项配置
     */
    public void deleteMapping(Long id) {
        transactionTemplate.executeWithoutResult(txStatus -> mappingDomainService.deleteMapping(id));
    }

    private ReportItemMappingResponse toResponse(FinancialReportItemMappingPO po) {
        if (po == null) {
            return null;
        }
        return ReportItemMappingResponse.builder()
                .id(po.getId())
                .tenantId(po.getTenantId())
                .reportType(po.getReportType() != null ? po.getReportType().getCode() : null)
                .itemCode(po.getItemCode())
                .itemName(po.getItemName())
                .itemCategory(po.getItemCategory() != null ? po.getItemCategory().getCode() : null)
                .lineNo(po.getLineNo())
                .displayLevel(po.getDisplayLevel() != null ? po.getDisplayLevel().getCode() : null)
                .subjectCodes(po.getSubjectCodes())
                .calcOperator(po.getCalcOperator() != null ? po.getCalcOperator().getCode() : null)
                .balanceDirection(po.getBalanceDirection() != null ? po.getBalanceDirection().getCode() : null)
                .formulaExpr(po.getFormulaExpr())
                .status(po.getStatus() != null ? po.getStatus().getCode() : null)
                .remark(po.getRemark())
                .createTime(po.getCreateTime())
                .updateTime(po.getUpdateTime())
                .build();
    }
}
