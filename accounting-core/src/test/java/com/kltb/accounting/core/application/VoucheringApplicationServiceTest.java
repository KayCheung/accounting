package com.kltb.accounting.core.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kltb.accounting.api.request.VoucherPageQueryRequest;
import com.kltb.accounting.api.response.PageResponse;
import com.kltb.accounting.api.response.VoucherFullDetailResponse;
import com.kltb.accounting.api.response.VoucherPageItemResponse;
import com.kltb.accounting.core.application.assembler.VoucheringAssembler;
import com.kltb.accounting.core.application.service.VoucheringApplicationService;
import com.kltb.accounting.core.domain.enums.DebitCreditEnum;
import com.kltb.accounting.core.domain.enums.PostingTypeEnum;
import com.kltb.accounting.core.domain.enums.TradeTypeEnum;
import com.kltb.accounting.core.domain.enums.VoucherStatusEnum;
import com.kltb.accounting.core.domain.service.BufferPostingDomainService;
import com.kltb.accounting.core.domain.service.VoucheringDomainService;
import com.kltb.accounting.core.infrastructure.persistence.entity.*;
import com.kltb.accounting.core.infrastructure.persistence.repository.AccountingVoucherRepository;
import com.kltb.accounting.core.infrastructure.persistence.repository.DictionaryRepository;
import com.kltb.accounting.core.infrastructure.persistence.repository.SubjectRepository;
import com.kltb.accounting.core.infrastructure.persistence.repository.TransactionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VoucheringApplicationServiceTest {

    @Mock
    private VoucheringDomainService voucheringDomainService;

    @Mock
    private BufferPostingDomainService bufferPostingDomainService;

    @Mock
    private AccountingVoucherRepository accountingVoucherRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private TransactionTemplate transactionTemplate;

    @Spy
    private VoucheringAssembler assembler = new VoucheringAssembler();

    @Mock
    private SubjectRepository subjectRepository;

    @Mock
    private DictionaryRepository dictionaryRepository;

    @InjectMocks
    private VoucheringApplicationService service;

    @Test
    @DisplayName("queryVoucherPage: 空结果返回空分页响应")
    void queryVoucherPage_empty_shouldReturnEmpty() {
        VoucherPageQueryRequest req = new VoucherPageQueryRequest();
        req.setPageNo(1);
        req.setPageSize(10);

        Page<AccountingVoucherPO> emptyPage = new Page<>(1, 10, 0);
        when(accountingVoucherRepository.selectPage(any(), any())).thenReturn(emptyPage);

        PageResponse<VoucherPageItemResponse> response = service.queryVoucherPage(req);
        assertThat(response).isNotNull();
        assertThat(response.getList()).isEmpty();
        assertThat(response.getTotal()).isZero();
    }

    @Test
    @DisplayName("queryVoucherPage: 成功查询凭证分页并批量预取分录与科目字典")
    void queryVoucherPage_success_shouldReturnPopulatedItems() {
        VoucherPageQueryRequest req = new VoucherPageQueryRequest();
        req.setPageNo(1);
        req.setPageSize(10);
        req.setVoucherNo("VOU202609200001");
        req.setStatus(3);

        AccountingVoucherPO po = new AccountingVoucherPO();
        po.setId(101L);
        po.setVoucherNo("VOU202609200001");
        po.setTxnNo("TXN202609200001");
        po.setTraceNo("TRC202609200001");
        po.setVoucherType("GENERAL");
        po.setPostingType(PostingTypeEnum.AUTOMATIC);
        po.setBusinessCode("MHD");
        po.setTradingCode("LOAN_DISBURSE");
        po.setTradeType(TradeTypeEnum.NORMAL);
        po.setTradeTime(LocalDateTime.of(2026, 9, 20, 10, 0, 0));
        po.setAmount(new BigDecimal("10000.00"));
        po.setStatus(VoucherStatusEnum.POSTED);
        po.setAccountingDate(LocalDate.of(2026, 9, 20));
        po.setSummary("芒好贷放款");
        po.setBookkeeperName("系统自动");
        po.setCreateTime(LocalDateTime.of(2026, 9, 20, 10, 0, 0));

        Page<AccountingVoucherPO> mockPage = new Page<>(1, 10, 1);
        mockPage.setRecords(List.of(po));
        when(accountingVoucherRepository.selectPage(any(), any())).thenReturn(mockPage);

        AccountingVoucherEntryPO debitEntry = new AccountingVoucherEntryPO();
        debitEntry.setEntryId("ENT001");
        debitEntry.setVoucherNo("VOU202609200001");
        debitEntry.setRowNum(1);
        debitEntry.setSubjectCode("1001");
        debitEntry.setAccountNo("ACCT001");
        debitEntry.setDebitCredit(DebitCreditEnum.DEBIT);
        debitEntry.setAmount(new BigDecimal("10000.00"));
        debitEntry.setCurrency("CNY");
        debitEntry.setSummary("借方分录");
        debitEntry.setUnilateral(0);
        debitEntry.setBuffered(0);
        debitEntry.setAccountingDate(LocalDate.of(2026, 9, 20));

        AccountingVoucherEntryPO creditEntry = new AccountingVoucherEntryPO();
        creditEntry.setEntryId("ENT002");
        creditEntry.setVoucherNo("VOU202609200001");
        creditEntry.setRowNum(2);
        creditEntry.setSubjectCode("2001");
        creditEntry.setAccountNo("ACCT002");
        creditEntry.setDebitCredit(DebitCreditEnum.CREDIT);
        creditEntry.setAmount(new BigDecimal("10000.00"));
        creditEntry.setCurrency("CNY");
        creditEntry.setSummary("贷方分录");
        creditEntry.setUnilateral(0);
        creditEntry.setBuffered(0);
        creditEntry.setAccountingDate(LocalDate.of(2026, 9, 20));

        when(accountingVoucherRepository.selectEntriesByVoucherNos(List.of("VOU202609200001")))
                .thenReturn(List.of(debitEntry, creditEntry));

        when(subjectRepository.selectSubjectNameMap(any()))
                .thenReturn(Map.of("1001", "库存现金", "2001", "短期借款"));

        DictionaryPO dict = new DictionaryPO();
        dict.setDictCode("GENERAL");
        dict.setDictName("通用记账凭证");
        when(dictionaryRepository.selectByType(com.kltb.accounting.api.constant.DictTypeEnum.VOUCHER_TYPE.getCode()))
                .thenReturn(List.of(dict));

        PageResponse<VoucherPageItemResponse> response = service.queryVoucherPage(req);
        assertThat(response).isNotNull();
        assertThat(response.getTotal()).isEqualTo(1);
        assertThat(response.getList()).hasSize(1);

        VoucherPageItemResponse item = response.getList().get(0);
        assertThat(item.getVoucherNo()).isEqualTo("VOU202609200001");
        assertThat(item.getVoucherTypeName()).isEqualTo("通用记账凭证");
        assertThat(item.getDebitCount()).isEqualTo(1);
        assertThat(item.getCreditCount()).isEqualTo(1);
        assertThat(item.getDebitAmount()).isEqualByComparingTo("10000.00");
        assertThat(item.getCreditAmount()).isEqualByComparingTo("10000.00");
        assertThat(item.getIsBalanced()).isTrue();
        assertThat(item.getEntries()).hasSize(2);
        assertThat(item.getEntries().get(0).getSubjectName()).isEqualTo("库存现金");
        assertThat(item.getEntries().get(1).getSubjectName()).isEqualTo("短期借款");
    }

    @Test
    @DisplayName("getVoucherDetail: 凭证不存在返回 null")
    void getVoucherDetail_notFound_shouldReturnNull() {
        when(accountingVoucherRepository.selectByVoucherNo("NOT_EXIST")).thenReturn(null);
        VoucherFullDetailResponse detail = service.getVoucherDetail("NOT_EXIST");
        assertThat(detail).isNull();
    }

    @Test
    @DisplayName("getVoucherDetail: 凭证存在返回全景档案且正常凭证可红冲")
    void getVoucherDetail_success_canReversal() {
        String voucherNo = "VOU202609200001";
        AccountingVoucherPO po = new AccountingVoucherPO();
        po.setId(101L);
        po.setVoucherNo(voucherNo);
        po.setAmount(new BigDecimal("500.00"));
        po.setStatus(VoucherStatusEnum.POSTED);
        po.setTradeType(TradeTypeEnum.NORMAL);
        po.setVoucherType("GENERAL");

        when(accountingVoucherRepository.selectByVoucherNo(voucherNo)).thenReturn(po);
        when(accountingVoucherRepository.selectEntriesByVoucherNo(voucherNo)).thenReturn(Collections.emptyList());
        when(accountingVoucherRepository.selectAuxiliaryByVoucherNo(voucherNo)).thenReturn(Collections.emptyList());
        when(accountingVoucherRepository.selectAttachmentsByVoucherNo(voucherNo)).thenReturn(Collections.emptyList());
        when(accountingVoucherRepository.selectReversalByOrig(voucherNo)).thenReturn(Collections.emptyList());

        VoucherFullDetailResponse detail = service.getVoucherDetail(voucherNo);
        assertThat(detail).isNotNull();
        assertThat(detail.getVoucherNo()).isEqualTo(voucherNo);
        assertThat(detail.getCanReversal()).isTrue();
        assertThat(detail.getReversalVoucherNo()).isNull();
    }

    @Test
    @DisplayName("getVoucherDetail: 已红冲凭证不可再次红冲且带有红冲凭证号")
    void getVoucherDetail_alreadyReversed_cannotReversal() {
        String voucherNo = "VOU202609200001";
        AccountingVoucherPO po = new AccountingVoucherPO();
        po.setId(101L);
        po.setVoucherNo(voucherNo);
        po.setAmount(new BigDecimal("500.00"));
        po.setStatus(VoucherStatusEnum.POSTED);
        po.setTradeType(TradeTypeEnum.NORMAL);

        AccountingVoucherPO reversalPo = new AccountingVoucherPO();
        reversalPo.setVoucherNo("VOU202609200099_REV");

        when(accountingVoucherRepository.selectByVoucherNo(voucherNo)).thenReturn(po);
        when(accountingVoucherRepository.selectEntriesByVoucherNo(voucherNo)).thenReturn(Collections.emptyList());
        when(accountingVoucherRepository.selectAuxiliaryByVoucherNo(voucherNo)).thenReturn(Collections.emptyList());
        when(accountingVoucherRepository.selectAttachmentsByVoucherNo(voucherNo)).thenReturn(Collections.emptyList());
        when(accountingVoucherRepository.selectReversalByOrig(voucherNo)).thenReturn(List.of(reversalPo));

        VoucherFullDetailResponse detail = service.getVoucherDetail(voucherNo);
        assertThat(detail).isNotNull();
        assertThat(detail.getCanReversal()).isFalse();
        assertThat(detail.getReversalVoucherNo()).isEqualTo("VOU202609200099_REV");
    }
}
