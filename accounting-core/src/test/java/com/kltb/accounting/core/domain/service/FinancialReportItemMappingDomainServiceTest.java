package com.kltb.accounting.core.domain.service;

import com.kltb.accounting.core.domain.enums.AvailableStatusEnum;
import com.kltb.accounting.core.domain.enums.ReportCalcOperatorEnum;
import com.kltb.accounting.core.domain.enums.ReportCategoryEnum;
import com.kltb.accounting.core.domain.enums.ReportDisplayLevelEnum;
import com.kltb.accounting.core.domain.enums.ReportTypeEnum;
import com.kltb.accounting.core.infrastructure.persistence.entity.FinancialReportItemMappingPO;
import com.kltb.accounting.core.infrastructure.persistence.repository.FinancialReportItemMappingRepository;
import com.kltb.accounting.core.infrastructure.persistence.repository.SubjectRepository;
import com.kltb.accounting.core.shared.exception.ServiceException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FinancialReportItemMappingDomainServiceTest {

    @Mock
    private FinancialReportItemMappingRepository mappingRepository;

    @Mock
    private SubjectRepository subjectRepository;

    @InjectMocks
    private FinancialReportItemMappingDomainService domainService;

    @Test
    @DisplayName("新增报表行项配置正常场景")
    void testCreateMappingSuccess() {
        FinancialReportItemMappingPO po = new FinancialReportItemMappingPO();
        po.setReportType(ReportTypeEnum.BALANCE_SHEET);
        po.setItemCode("ASSET_CASH");
        po.setItemName("货币资金");
        po.setItemCategory(ReportCategoryEnum.CURRENT_ASSET);
        po.setLineNo(2);
        po.setDisplayLevel(ReportDisplayLevelEnum.DETAIL);
        po.setSubjectCodes("1001,1002,1012");
        po.setCalcOperator(ReportCalcOperatorEnum.ADD);

        when(mappingRepository.selectByReportTypeAndItemCode(ReportTypeEnum.BALANCE_SHEET, "ASSET_CASH")).thenReturn(null);
        when(mappingRepository.insert(any())).thenReturn(1);

        Long id = domainService.createMapping(po);
        verify(mappingRepository, times(1)).insert(po);
    }

    @Test
    @DisplayName("新增报表行项编码重复抛出异常场景")
    void testCreateMappingDuplicateThrowsException() {
        FinancialReportItemMappingPO po = new FinancialReportItemMappingPO();
        po.setReportType(ReportTypeEnum.BALANCE_SHEET);
        po.setItemCode("ASSET_CASH");

        when(mappingRepository.selectByReportTypeAndItemCode(ReportTypeEnum.BALANCE_SHEET, "ASSET_CASH"))
                .thenReturn(new FinancialReportItemMappingPO());

        assertThatThrownBy(() -> domainService.createMapping(po))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("已存在相同行项编码");
    }

    @Test
    @DisplayName("状态切换与逻辑删除场景")
    void testUpdateStatusAndDelete() {
        FinancialReportItemMappingPO po = new FinancialReportItemMappingPO();
        po.setId(10L);
        po.setStatus(AvailableStatusEnum.ENABLED);

        when(mappingRepository.selectById(10L)).thenReturn(po);

        domainService.updateStatus(10L, 2);
        assertThat(po.getStatus()).isEqualTo(AvailableStatusEnum.DISABLED);
        verify(mappingRepository, times(1)).updateById(po);

        domainService.deleteMapping(10L);
        verify(mappingRepository, times(1)).deleteById(10L);
    }
}
