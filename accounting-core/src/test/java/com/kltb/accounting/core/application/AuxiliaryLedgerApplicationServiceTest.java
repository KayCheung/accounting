package com.kltb.accounting.core.application;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kltb.accounting.api.request.AuxiliaryLedgerQueryRequest;
import com.kltb.accounting.api.response.*;
import com.kltb.accounting.core.application.dto.DictResponse;
import com.kltb.accounting.core.application.service.AuxiliaryLedgerApplicationService;
import com.kltb.accounting.core.domain.enums.AvailableStatusEnum;
import com.kltb.accounting.core.domain.enums.BalanceDirectionEnum;
import com.kltb.accounting.core.domain.enums.SubjectCategoryEnum;
import com.kltb.accounting.core.infrastructure.persistence.bo.*;
import com.kltb.accounting.core.infrastructure.persistence.entity.AccountSubjectPO;
import com.kltb.accounting.core.infrastructure.persistence.entity.DictionaryPO;
import com.kltb.accounting.core.infrastructure.dictionary.DictionaryComponent;
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

    @Mock
    private DictionaryComponent dictionaryComponent;

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
        s1.setDebitAmount(new BigDecimal("186000.00"));
        s1.setCreditAmount(BigDecimal.ZERO);

        AuxiliaryItemStatBO s2 = new AuxiliaryItemStatBO();
        s2.setAuxCode("DEPT_SALES");
        s2.setAuxName("销售中心");
        s2.setRecordCount(25L);
        s2.setTotalAmount(new BigDecimal("280000.00"));
        s2.setDebitAmount(new BigDecimal("200000.00"));
        s2.setCreditAmount(new BigDecimal("80000.00"));

        when(auxiliaryRepository.selectDistinctItems(eq("DEPARTMENT"), any(), any(), any()))
                .thenReturn(List.of(s2, s1));

        List<AuxiliaryItemResponse> items = auxiliaryService.getAuxiliaryItems("DEPARTMENT", null, null);
        assertThat(items).hasSize(2);
        assertThat(items.get(0).getAuxCode()).isEqualTo("DEPT_SALES");
        assertThat(items.get(0).getTotalAmount()).isEqualByComparingTo("280000.00");
        assertThat(items.get(0).getNetAmount()).isEqualByComparingTo("120000.00");
        assertThat(items.get(0).getNetDirectionDesc()).isEqualTo(BalanceDirectionEnum.DEBIT.getDesc());

        assertThat(items.get(1).getAuxCode()).isEqualTo("DEPT_TECH");
        assertThat(items.get(1).getNetAmount()).isEqualByComparingTo("186000.00");
    }

    @Test
    @DisplayName("查询核算项目项列表测试-存在重复auxCode时合并去重并累加金额")
    void testGetAuxiliaryItems_withDuplicates_shouldMergeByAuxCode() {
        AuxiliaryItemStatBO s1 = new AuxiliaryItemStatBO();
        s1.setAuxCode("PRJ_001");
        s1.setAuxName("研发项目A");
        s1.setRecordCount(10L);
        s1.setTotalAmount(new BigDecimal("1000.00"));
        s1.setDebitAmount(new BigDecimal("1000.00"));
        s1.setCreditAmount(BigDecimal.ZERO);

        AuxiliaryItemStatBO s2 = new AuxiliaryItemStatBO();
        s2.setAuxCode("PRJ_001");
        s2.setAuxName("研发项目A-别名");
        s2.setRecordCount(5L);
        s2.setTotalAmount(new BigDecimal("500.00"));
        s2.setDebitAmount(BigDecimal.ZERO);
        s2.setCreditAmount(new BigDecimal("500.00"));

        when(auxiliaryRepository.selectDistinctItems(eq("PROJECT"), any(), any(), any()))
                .thenReturn(List.of(s1, s2));

        List<AuxiliaryItemResponse> items = auxiliaryService.getAuxiliaryItems("PROJECT", null, null);
        assertThat(items).hasSize(1);
        assertThat(items.get(0).getAuxCode()).isEqualTo("PRJ_001");
        assertThat(items.get(0).getRecordCount()).isEqualTo(15L);
        assertThat(items.get(0).getTotalAmount()).isEqualByComparingTo("1500.00");
        assertThat(items.get(0).getDebitAmount()).isEqualByComparingTo("1000.00");
        assertThat(items.get(0).getCreditAmount()).isEqualByComparingTo("500.00");
        assertThat(items.get(0).getNetAmount()).isEqualByComparingTo("500.00");
    }

    @Test
    @DisplayName("查询账簿统计概览与Top占比测试")
    void testGetAuxiliarySummary() {
        AuxiliaryLedgerQueryRequest req = new AuxiliaryLedgerQueryRequest();
        req.setAuxType("DEPARTMENT");

        AuxiliarySummaryBO summaryBO = new AuxiliarySummaryBO();
        summaryBO.setTotalAmount(new BigDecimal("466000.00"));
        summaryBO.setDebitAmount(new BigDecimal("400000.00"));
        summaryBO.setCreditAmount(new BigDecimal("66000.00"));
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
        when(auxiliaryRepository.selectDistinctItems(eq("DEPARTMENT"), any(), any(), any()))
                .thenReturn(List.of(s1, s2));

        AuxiliarySummaryResponse resp = auxiliaryService.getAuxiliarySummary(req);
        assertThat(resp.getTotalAmount()).isEqualByComparingTo("466000.00");
        assertThat(resp.getNetAmount()).isEqualByComparingTo("334000.00");
        assertThat(resp.getNetDirectionDesc()).isEqualTo(BalanceDirectionEnum.DEBIT.getDesc());
        assertThat(resp.getItemCount()).isEqualTo(2);
        assertThat(resp.getTopItems()).hasSize(2);
        // 280000 / 466000 * 100 = 60.09%
        assertThat(resp.getTopItems().get(0).getPercentage()).isEqualByComparingTo("60.09");
    }

    @Test
    @DisplayName("查询交叉对比矩阵双口径与红冲归零测试")
    void testGetAuxiliaryMatrix() {
        AuxiliaryLedgerQueryRequest req = new AuxiliaryLedgerQueryRequest();
        req.setAuxType("DEPARTMENT");
        req.setAmountMode("NET");

        AuxiliaryItemStatBO s1 = new AuxiliaryItemStatBO();
        s1.setAuxCode("DEPT_SALES");
        s1.setAuxName("销售中心");
        s1.setTotalAmount(new BigDecimal("2000.00"));
        s1.setDebitAmount(new BigDecimal("2000.00"));
        s1.setCreditAmount(BigDecimal.ZERO);

        AuxiliaryItemStatBO s2 = new AuxiliaryItemStatBO();
        s2.setAuxCode("DEPT_TECH");
        s2.setAuxName("技术中心");
        s2.setTotalAmount(new BigDecimal("1000.00"));
        s2.setDebitAmount(new BigDecimal("500.00"));
        s2.setCreditAmount(new BigDecimal("500.00")); // 红冲对冲净额为0

        when(auxiliaryRepository.selectDistinctItems(eq("DEPARTMENT"), any(), any(), any()))
                .thenReturn(List.of(s1, s2));

        AuxiliaryMatrixCellBO c1 = new AuxiliaryMatrixCellBO();
        c1.setSubjectCode("6601");
        c1.setAuxCode("DEPT_SALES");
        c1.setAmount(new BigDecimal("2000.00"));
        c1.setDebitAmount(new BigDecimal("2000.00"));
        c1.setCreditAmount(BigDecimal.ZERO);

        AuxiliaryMatrixCellBO c2 = new AuxiliaryMatrixCellBO();
        c2.setSubjectCode("6602");
        c2.setAuxCode("DEPT_TECH");
        c2.setAmount(new BigDecimal("1000.00"));
        c2.setDebitAmount(new BigDecimal("500.00"));
        c2.setCreditAmount(new BigDecimal("500.00")); // 发生额1000，净额0

        when(auxiliaryRepository.selectMatrixCellAmounts(req)).thenReturn(List.of(c1, c2));

        // 1. 验证默认 NET 净额口径
        AuxiliaryMatrixResponse netMatrix = auxiliaryService.getAuxiliaryMatrix(req);
        assertThat(netMatrix.getColumns()).hasSize(2);
        assertThat(netMatrix.getRows()).hasSize(2);
        assertThat(netMatrix.getGrandTotal()).isEqualByComparingTo("2000.00"); // 2000 + 0 = 2000
        assertThat(netMatrix.getGrandNetTotal()).isEqualByComparingTo("2000.00");
        assertThat(netMatrix.getColumnTotals().get("DEPT_SALES")).isEqualByComparingTo("2000.00");
        assertThat(netMatrix.getColumnTotals().get("DEPT_TECH")).isEqualByComparingTo("0.00"); // 净额归零！

        // 2. 验证 GROSS 发生额口径
        req.setAmountMode("GROSS");
        when(auxiliaryRepository.selectMatrixCellAmounts(req)).thenReturn(List.of(c1, c2));
        AuxiliaryMatrixResponse grossMatrix = auxiliaryService.getAuxiliaryMatrix(req);
        assertThat(grossMatrix.getGrandTotal()).isEqualByComparingTo("3000.00"); // 2000 + 1000 = 3000
        assertThat(grossMatrix.getColumnTotals().get("DEPT_SALES")).isEqualByComparingTo("2000.00");
        assertThat(grossMatrix.getColumnTotals().get("DEPT_TECH")).isEqualByComparingTo("1000.00"); // 发生额保留
    }

    @Test
    @DisplayName("查询辅助核算科目分布明细测试（含红冲平与贷方绝对值）")
    void testGetAuxiliarySubjectBreakdown() {
        AuxiliaryLedgerQueryRequest req = new AuxiliaryLedgerQueryRequest();
        req.setAuxType("DEPARTMENT");

        AuxiliarySubjectBreakdownBO b1 = new AuxiliarySubjectBreakdownBO();
        b1.setSubjectCode("6601");
        b1.setDebitAmount(new BigDecimal("500.00"));
        b1.setCreditAmount(new BigDecimal("500.00")); // 红冲归零
        b1.setTotalAmount(new BigDecimal("1000.00"));
        b1.setEntryCount(2);

        // 补充贷方大于借方的负债/贷方科目场景：借200，贷500，净额应为绝对值正数300，方向为贷
        AuxiliarySubjectBreakdownBO b2 = new AuxiliarySubjectBreakdownBO();
        b2.setSubjectCode("6602");
        b2.setDebitAmount(new BigDecimal("200.00"));
        b2.setCreditAmount(new BigDecimal("500.00"));
        b2.setTotalAmount(new BigDecimal("700.00"));
        b2.setEntryCount(2);

        when(auxiliaryRepository.selectSubjectBreakdown(req)).thenReturn(List.of(b1, b2));

        List<AuxiliarySubjectDetailResponse> list = auxiliaryService.getAuxiliarySubjectBreakdown(req);
        assertThat(list).hasSize(2);
        AuxiliarySubjectDetailResponse detail1 = list.get(0);
        assertThat(detail1.getSubjectCode()).isEqualTo("6601");
        assertThat(detail1.getSubjectName()).isEqualTo("销售费用");
        assertThat(detail1.getDebitAmount()).isEqualByComparingTo("500.00");
        assertThat(detail1.getCreditAmount()).isEqualByComparingTo("500.00");
        assertThat(detail1.getNetAmount()).isEqualByComparingTo("0.00");
        assertThat(detail1.getNetDirectionDesc()).isEqualTo(BalanceDirectionEnum.FLAT.getDesc()); // "平"
        assertThat(detail1.getTotalAmount()).isEqualByComparingTo("1000.00");

        AuxiliarySubjectDetailResponse detail2 = list.get(1);
        assertThat(detail2.getSubjectCode()).isEqualTo("6602");
        assertThat(detail2.getDebitAmount()).isEqualByComparingTo("200.00");
        assertThat(detail2.getCreditAmount()).isEqualByComparingTo("500.00");
        assertThat(detail2.getNetAmount()).isEqualByComparingTo("300.00"); // 绝对值法则，正数！
        assertThat(detail2.getNetDirectionDesc()).isEqualTo(BalanceDirectionEnum.CREDIT.getDesc()); // "贷"
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
        bo.setVoucherStatus(5);
        bo.setTradeType(3);

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
        assertThat(item.getVoucherStatus()).isEqualTo(5);
        assertThat(item.getVoucherStatusDesc()).isEqualTo("已冲销");
        assertThat(item.getTradeType()).isEqualTo(3);
        assertThat(item.getTradeTypeDesc()).isEqualTo("红");
    }

    @Test
    @DisplayName("resolveAuxName: 原始名称为code时通过字典反查到中文名称")
    void testResolveAuxNameWithDictionary() {
        DictionaryPO deptDict = new DictionaryPO();
        deptDict.setDictType("DEPARTMENT");
        deptDict.setDictCode("DEPT_TECH");
        deptDict.setDictName("技术中心");

        when(dictionaryComponent.findByTypeAndCodeOrName("DEPARTMENT", "DEPT_TECH")).thenReturn(deptDict);

        // 1. 原始名称与编码相同，成功命中字典返回字典名称
        String resolved = auxiliaryService.resolveAuxName("DEPARTMENT", "DEPT_TECH", "DEPT_TECH");
        assertThat(resolved).isEqualTo("技术中心");

        // 2. 原始名称为空，成功命中字典返回字典名称
        String resolvedEmpty = auxiliaryService.resolveAuxName("DEPARTMENT", "DEPT_TECH", "");
        assertThat(resolvedEmpty).isEqualTo("技术中心");

        // 3. 原始名称已有业务名称且不同于code，即使无字典也返回原名称
        String resolvedCustom = auxiliaryService.resolveAuxName("DEPARTMENT", "DEPT_CUSTOM", "自定义运营组");
        assertThat(resolvedCustom).isEqualTo("自定义运营组");

        // 4. 无字典且无特别名称，兜底返回code
        String resolvedFallback = auxiliaryService.resolveAuxName("UNKNOWN", "UNK_01", "UNK_01");
        assertThat(resolvedFallback).isEqualTo("UNK_01");
    }
}
