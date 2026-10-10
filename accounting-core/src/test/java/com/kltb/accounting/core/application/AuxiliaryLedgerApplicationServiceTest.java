package com.kltb.accounting.core.application;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kltb.accounting.api.request.AuxiliaryLedgerQueryRequest;
import com.kltb.accounting.api.response.*;
import com.kltb.accounting.core.application.dto.DictResponse;
import com.kltb.accounting.core.application.service.AuxiliaryLedgerApplicationService;
import com.kltb.accounting.core.domain.enums.AvailableStatusEnum;
import com.kltb.accounting.core.domain.enums.SubjectCategoryEnum;
import com.kltb.accounting.core.infrastructure.persistence.bo.*;
import com.kltb.accounting.core.infrastructure.persistence.entity.AccountSubjectPO;
import com.kltb.accounting.core.infrastructure.persistence.entity.DictionaryPO;
import com.kltb.accounting.core.infrastructure.persistence.repository.AccountingVoucherAuxiliaryRepository;
import com.kltb.accounting.core.infrastructure.persistence.repository.DictionaryRepository;
import com.kltb.accounting.core.infrastructure.persistence.repository.SubjectRepository;
import com.kltb.accounting.core.infrastructure.redis.DictionaryCacheService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuxiliaryLedgerApplicationServiceTest {

    @Mock
    private AccountingVoucherAuxiliaryRepository auxiliaryRepository;

    @Mock
    private SubjectRepository subjectRepository;

    @Mock
    private DictionaryCacheService dictionaryCacheService;

    @Mock
    private DictionaryRepository dictionaryRepository;

    @InjectMocks
    private AuxiliaryLedgerApplicationService auxiliaryService;

    private AccountSubjectPO subject6601;
    private AccountSubjectPO subject6602;

    @BeforeEach
    void setUp() {
        subject6601 = new AccountSubjectPO();
        subject6601.setSubjectCode("6601");
        subject6601.setSubjectName("销售费用");
        subject6601.setSubjectCategory(SubjectCategoryEnum.PROFIT_LOSS);

        subject6602 = new AccountSubjectPO();
        subject6602.setSubjectCode("6602");
        subject6602.setSubjectName("管理费用");
        subject6602.setSubjectCategory(SubjectCategoryEnum.PROFIT_LOSS);

        when(subjectRepository.selectAllSubjects()).thenReturn(List.of(subject6601, subject6602));
    }

    @Test
    @DisplayName("查询辅助核算类型列表测试")
    void testGetAuxiliaryTypes() {
        DictionaryPO d1 = new DictionaryPO()
                .setDictType("auxiliary_type")
                .setDictCode("DEPARTMENT")
                .setDictName("部门")
                .setStatus(AvailableStatusEnum.ENABLED);
        DictionaryPO d2 = new DictionaryPO()
                .setDictType("auxiliary_type")
                .setDictCode("PROJECT")
                .setDictName("项目")
                .setStatus(AvailableStatusEnum.ENABLED);

        when(dictionaryCacheService.getByType("auxiliary_type")).thenReturn(List.of(d1, d2));

        List<DictResponse> types = auxiliaryService.getAuxiliaryTypes();
        assertThat(types).hasSize(2);
        assertThat(types.get(0).getDictCode()).isEqualTo("DEPARTMENT");
        assertThat(types.get(1).getDictCode()).isEqualTo("PROJECT");
    }

    @Test
    @DisplayName("查询核算项目项列表测试")
    void testGetAuxiliaryItems() {
        AuxiliaryItemStatBO s1 = new AuxiliaryItemStatBO();
        s1.setAuxCode("DEPT_TECH");
        s1.setAuxName("技术中心");
        s1.setRecordCount(15L);
        s1.setTotalAmount(new BigDecimal("186000.00"));

        AuxiliaryItemStatBO s2 = new AuxiliaryItemStatBO();
        s2.setAuxCode("DEPT_SALES");
        s2.setAuxName("销售中心");
        s2.setRecordCount(25L);
        s2.setTotalAmount(new BigDecimal("280000.00"));

        when(auxiliaryRepository.selectDistinctItems(eq("DEPARTMENT"), any(), any()))
                .thenReturn(List.of(s2, s1));

        List<AuxiliaryItemResponse> items = auxiliaryService.getAuxiliaryItems("DEPARTMENT", null, null);
        assertThat(items).hasSize(2);
        assertThat(items.get(0).getAuxCode()).isEqualTo("DEPT_SALES");
        assertThat(items.get(0).getTotalAmount()).isEqualByComparingTo("280000.00");
    }

    @Test
    @DisplayName("查询账簿统计概览与Top占比测试")
    void testGetAuxiliarySummary() {
        AuxiliaryLedgerQueryRequest req = new AuxiliaryLedgerQueryRequest();
        req.setAuxType("DEPARTMENT");

        AuxiliarySummaryBO summaryBO = new AuxiliarySummaryBO();
        summaryBO.setTotalAmount(new BigDecimal("466000.00"));
        summaryBO.setDebitAmount(new BigDecimal("466000.00"));
        summaryBO.setCreditAmount(BigDecimal.ZERO);
        summaryBO.setItemCount(2);
        summaryBO.setSubjectCount(2);
        summaryBO.setVoucherCount(40);

        AuxiliaryItemStatBO s1 = new AuxiliaryItemStatBO();
        s1.setAuxCode("DEPT_SALES");
        s1.setAuxName("销售中心");
        s1.setTotalAmount(new BigDecimal("280000.00"));

        AuxiliaryItemStatBO s2 = new AuxiliaryItemStatBO();
        s2.setAuxCode("DEPT_TECH");
        s2.setAuxName("技术中心");
        s2.setTotalAmount(new BigDecimal("186000.00"));

        when(auxiliaryRepository.selectSummary(req)).thenReturn(summaryBO);
        when(auxiliaryRepository.selectDistinctItems(eq("DEPARTMENT"), any(), any()))
                .thenReturn(List.of(s1, s2));

        AuxiliarySummaryResponse resp = auxiliaryService.getAuxiliarySummary(req);
        assertThat(resp.getTotalAmount()).isEqualByComparingTo("466000.00");
        assertThat(resp.getItemCount()).isEqualTo(2);
        assertThat(resp.getTopItems()).hasSize(2);
        // 280000 / 466000 * 100 = 60.09%
        assertThat(resp.getTopItems().get(0).getPercentage()).isEqualByComparingTo("60.09");
    }

    @Test
    @DisplayName("查询交叉对比矩阵测试")
    void testGetAuxiliaryMatrix() {
        AuxiliaryLedgerQueryRequest req = new AuxiliaryLedgerQueryRequest();
        req.setAuxType("DEPARTMENT");

        AuxiliaryItemStatBO s1 = new AuxiliaryItemStatBO();
        s1.setAuxCode("DEPT_SALES");
        s1.setAuxName("销售中心");
        s1.setTotalAmount(new BigDecimal("2000.00"));

        AuxiliaryItemStatBO s2 = new AuxiliaryItemStatBO();
        s2.setAuxCode("DEPT_TECH");
        s2.setAuxName("技术中心");
        s2.setTotalAmount(new BigDecimal("1000.00"));

        when(auxiliaryRepository.selectDistinctItems(eq("DEPARTMENT"), any(), any()))
                .thenReturn(List.of(s1, s2));

        AuxiliaryMatrixCellBO c1 = new AuxiliaryMatrixCellBO();
        c1.setSubjectCode("6601");
        c1.setAuxCode("DEPT_SALES");
        c1.setAmount(new BigDecimal("2000.00"));

        AuxiliaryMatrixCellBO c2 = new AuxiliaryMatrixCellBO();
        c2.setSubjectCode("6602");
        c2.setAuxCode("DEPT_TECH");
        c2.setAmount(new BigDecimal("1000.00"));

        when(auxiliaryRepository.selectMatrixCellAmounts(req)).thenReturn(List.of(c1, c2));

        AuxiliaryMatrixResponse matrix = auxiliaryService.getAuxiliaryMatrix(req);
        assertThat(matrix.getColumns()).hasSize(2);
        assertThat(matrix.getRows()).hasSize(2);
        assertThat(matrix.getGrandTotal()).isEqualByComparingTo("3000.00");
        assertThat(matrix.getColumnTotals().get("DEPT_SALES")).isEqualByComparingTo("2000.00");
        assertThat(matrix.getColumnTotals().get("DEPT_TECH")).isEqualByComparingTo("1000.00");
    }

    @Test
    @DisplayName("分页查询凭证分录记录测试")
    void testGetAuxiliaryEntries() {
        AuxiliaryLedgerQueryRequest req = new AuxiliaryLedgerQueryRequest();
        req.setAuxType("DEPARTMENT");
        req.setPageNo(1);
        req.setPageSize(10);

        AuxiliaryEntryRecordBO bo = new AuxiliaryEntryRecordBO();
        bo.setId(101L);
        bo.setAccountingDate(LocalDate.of(2026, 3, 1));
        bo.setVoucherNo("VOU20260301000001");
        bo.setEntryId("ENT202603010001");
        bo.setSummary("发放员工工资");
        bo.setAuxType("DEPARTMENT");
        bo.setAuxCode("DEPT_TECH");
        bo.setAuxName("技术中心");
        bo.setSubjectCode("6602");
        bo.setSubjectName("管理费用");
        bo.setChangeDirection(1);
        bo.setDebitCredit(1);
        bo.setAmount(new BigDecimal("50000.00"));

        IPage<AuxiliaryEntryRecordBO> page = new Page<>(1, 10);
        page.setRecords(List.of(bo));
        page.setTotal(1);

        when(auxiliaryRepository.selectEntryPage(any(), eq(req))).thenReturn(page);

        PageResponse<AuxiliaryEntryRecordResponse> resp = auxiliaryService.getAuxiliaryEntries(req);
        assertThat(resp.getTotal()).isEqualTo(1L);
        assertThat(resp.getList()).hasSize(1);
        AuxiliaryEntryRecordResponse item = resp.getList().get(0);
        assertThat(item.getVoucherWord()).contains("20260301000001");
        assertThat(item.getDebitAmount()).isEqualByComparingTo("50000.00");
        assertThat(item.getCreditAmount()).isEqualByComparingTo(BigDecimal.ZERO);
    }
}
