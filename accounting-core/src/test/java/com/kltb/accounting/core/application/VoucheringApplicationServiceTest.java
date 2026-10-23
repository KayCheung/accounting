package com.kltb.accounting.core.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kltb.accounting.api.request.VoucherPageQueryRequest;
import com.kltb.accounting.api.response.PageResponse;
import com.kltb.accounting.api.response.VoucherFullDetailResponse;
import com.kltb.accounting.api.response.VoucherPageItemResponse;
import com.kltb.accounting.core.application.assembler.VoucheringAssembler;
import com.kltb.accounting.core.application.service.VoucheringApplicationService;
import com.kltb.accounting.core.domain.enums.ChangeDirectionEnum;
import com.kltb.accounting.core.domain.enums.DebitCreditEnum;
import com.kltb.accounting.core.domain.enums.PostingTypeEnum;
import com.kltb.accounting.core.domain.enums.SubjectBalanceDirectionEnum;
import com.kltb.accounting.core.domain.enums.TradeTypeEnum;
import com.kltb.accounting.core.domain.enums.VoucherStatusEnum;
import com.kltb.accounting.core.domain.service.AuxiliaryDomainService;
import com.kltb.accounting.core.domain.service.AuxiliaryItemData;
import com.kltb.accounting.core.domain.service.BufferPostingDomainService;
import com.kltb.accounting.core.domain.service.VoucherEntryData;
import com.kltb.accounting.core.domain.service.VoucheringDomainService;
import com.kltb.accounting.core.infrastructure.persistence.entity.*;
import com.kltb.accounting.core.infrastructure.persistence.repository.AccountRepository;
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

import com.kltb.accounting.api.request.VoucherGenerateRequest;
import com.kltb.accounting.api.response.VoucherGenerateResponse;
import com.kltb.accounting.core.domain.enums.BusinessRecordStatusEnum;
import org.springframework.transaction.support.TransactionCallback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VoucheringApplicationServiceTest {

    @Mock
    private VoucheringDomainService voucheringDomainService;

    @Mock
    private AuxiliaryDomainService auxiliaryDomainService;

    @Mock
    private BufferPostingDomainService bufferPostingDomainService;

    @Mock
    private AccountingVoucherRepository accountingVoucherRepository;

    @Mock
    private AccountRepository accountRepository;

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

    @Test
    @DisplayName("generateVoucher: 携带 accountMapping 时直接命中内存字典解析账户编号，0 DB I/O")
    void generateVoucher_withAccountMapping_shouldResolveDirectly() {
        VoucherGenerateRequest req = new VoucherGenerateRequest();
        req.setTraceNo("TRC001");
        req.setAccountMapping(Map.of("CUST001:1001", "ACC-20261010-000001", "INNER:2001", "ACC-INNER-2001"));

        BusinessRecordPO journal = new BusinessRecordPO();
        journal.setTraceNo("TRC001");
        journal.setStatus(BusinessRecordStatusEnum.PROCESSING);
        journal.setBusinessCode("LOAN");
        journal.setTradingCode("DISBURSE");
        journal.setPayChannel("BANK");
        journal.setAccountingDate(LocalDate.now());

        BusinessDetailPO d1 = new BusinessDetailPO();
        d1.setFundsType("PRINCIPAL");
        d1.setCustomerId("CUST001");
        d1.setAmount(new BigDecimal("100.00"));

        VoucheringDomainService.JournalWithDetails jwd = new VoucheringDomainService.JournalWithDetails(
                journal, List.of(d1));
        when(voucheringDomainService.loadJournal("TRC001")).thenReturn(jwd);

        AccountingRulePO rule = new AccountingRulePO();
        rule.setId(1L);
        AccountingRuleDetailPO rd1 = new AccountingRuleDetailPO();
        rd1.setId(11L);
        rd1.setRowNum(1);
        rd1.setFundsType("PRINCIPAL");
        rd1.setSubjectCode("1001");
        rd1.setDebitCredit(DebitCreditEnum.DEBIT);
        rd1.setCurrency("CNY");

        VoucheringDomainService.AccountingRuleWithDetails rwd = new VoucheringDomainService.AccountingRuleWithDetails(
                rule, List.of(rd1), Collections.emptyMap());
        when(voucheringDomainService.matchRule("LOAN", "DISBURSE", "BANK")).thenReturn(rwd);

        TransactionPO txn = new TransactionPO();
        txn.setTxnNo("TXN001");
        when(transactionRepository.selectByTraceNo("TRC001")).thenReturn(txn);

        AccountSubjectPO subject = new AccountSubjectPO();
        subject.setSubjectCode("1001");
        subject.setDebitCredit(SubjectBalanceDirectionEnum.DEBIT);
        when(subjectRepository.selectByCode("1001")).thenReturn(subject);

        when(voucheringDomainService.calculateEntryAmount(eq(rd1), eq(d1), eq(journal)))
                .thenReturn(new BigDecimal("100.00"));

        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> cb = invocation.getArgument(0);
            return cb.doInTransaction(null);
        });

        when(voucheringDomainService.persistVoucher(any(), any(), any(), any())).thenReturn("VOU001");
        when(accountingVoucherRepository.updateTxnNoByVoucherNo("VOU001", "TXN001")).thenReturn(1);

        AccountingVoucherPO savedVoucher = new AccountingVoucherPO();
        savedVoucher.setVoucherNo("VOU001");
        savedVoucher.setAmount(new BigDecimal("100.00"));
        when(accountingVoucherRepository.selectByTraceNo("TRC001")).thenReturn(List.of(savedVoucher));

        VoucherGenerateResponse resp = service.generateVoucher(req);
        assertThat(resp).isNotNull();
        assertThat(resp.getVoucherNo()).isEqualTo("VOU001");
        assertThat(resp.getEntries()).hasSize(1);
        assertThat(resp.getEntries().get(0).getAccountNo()).isEqualTo("ACC-20261010-000001");

        // 验证根本无需调用 accountRepository 二次查库！
        verifyNoInteractions(accountRepository);
    }

    @Test
    @DisplayName("generateVoucher: 无 accountMapping 时回源 accountRepository 精确匹配")
    void generateVoucher_withoutAccountMapping_shouldFallbackToRepository() {
        VoucherGenerateRequest req = new VoucherGenerateRequest();
        req.setTraceNo("TRC002");

        BusinessRecordPO journal = new BusinessRecordPO();
        journal.setTraceNo("TRC002");
        journal.setStatus(BusinessRecordStatusEnum.PROCESSING);
        journal.setBusinessCode("LOAN");
        journal.setTradingCode("DISBURSE");
        journal.setPayChannel("BANK");
        journal.setAccountingDate(LocalDate.now());

        BusinessDetailPO d1 = new BusinessDetailPO();
        d1.setFundsType("PRINCIPAL");
        d1.setCustomerId("CUST002");
        d1.setAmount(new BigDecimal("200.00"));

        VoucheringDomainService.JournalWithDetails jwd = new VoucheringDomainService.JournalWithDetails(
                journal, List.of(d1));
        when(voucheringDomainService.loadJournal("TRC002")).thenReturn(jwd);

        AccountingRulePO rule = new AccountingRulePO();
        rule.setId(1L);
        AccountingRuleDetailPO rd1 = new AccountingRuleDetailPO();
        rd1.setId(11L);
        rd1.setRowNum(1);
        rd1.setFundsType("PRINCIPAL");
        rd1.setSubjectCode("1001");
        rd1.setDebitCredit(DebitCreditEnum.DEBIT);
        rd1.setCurrency("CNY");

        VoucheringDomainService.AccountingRuleWithDetails rwd = new VoucheringDomainService.AccountingRuleWithDetails(
                rule, List.of(rd1), Collections.emptyMap());
        when(voucheringDomainService.matchRule("LOAN", "DISBURSE", "BANK")).thenReturn(rwd);

        TransactionPO txn = new TransactionPO();
        txn.setTxnNo("TXN002");
        when(transactionRepository.selectByTraceNo("TRC002")).thenReturn(txn);

        AccountSubjectPO subject = new AccountSubjectPO();
        subject.setSubjectCode("1001");
        subject.setDebitCredit(SubjectBalanceDirectionEnum.DEBIT);
        when(subjectRepository.selectByCode("1001")).thenReturn(subject);

        when(voucheringDomainService.calculateEntryAmount(eq(rd1), eq(d1), eq(journal)))
                .thenReturn(new BigDecimal("200.00"));

        AccountPO mockAccount = new AccountPO();
        mockAccount.setAccountNo("ACC-FROM-DB-002");
        when(accountRepository.selectByOwnerIdAndSubjectCode("CUST002", "1001")).thenReturn(mockAccount);

        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> cb = invocation.getArgument(0);
            return cb.doInTransaction(null);
        });

        when(voucheringDomainService.persistVoucher(any(), any(), any(), any())).thenReturn("VOU002");
        when(accountingVoucherRepository.updateTxnNoByVoucherNo("VOU002", "TXN002")).thenReturn(1);

        AccountingVoucherPO savedVoucher = new AccountingVoucherPO();
        savedVoucher.setVoucherNo("VOU002");
        savedVoucher.setAmount(new BigDecimal("200.00"));
        when(accountingVoucherRepository.selectByTraceNo("TRC002")).thenReturn(List.of(savedVoucher));

        VoucherGenerateResponse resp = service.generateVoucher(req);
        assertThat(resp).isNotNull();
        assertThat(resp.getEntries().get(0).getAccountNo()).isEqualTo("ACC-FROM-DB-002");
        verify(accountRepository).selectByOwnerIdAndSubjectCode("CUST002", "1001");
        verify(transactionRepository).updateRelateAccountCount("TXN002", 1);
    }

    @Test
    @DisplayName("generateVoucher: 校验分录增减方向 changeDirection 正确推导（借借为增，借贷为减）")
    void generateVoucher_changeDirectionDerivation_shouldSetCorrectDirection() {
        VoucherGenerateRequest req = new VoucherGenerateRequest();
        req.setTraceNo("TRC_DIR_001");
        req.setAccountMapping(Map.of("CUST_A:1001", "ACC_001", "CUST_B:2001", "ACC_002"));

        BusinessRecordPO journal = new BusinessRecordPO();
        journal.setTraceNo("TRC_DIR_001");
        journal.setStatus(BusinessRecordStatusEnum.PROCESSING);
        journal.setBusinessCode("PAY");
        journal.setTradingCode("TRANSFER");
        journal.setPayChannel("WECHAT");
        journal.setAccountingDate(LocalDate.now());

        BusinessDetailPO d1 = new BusinessDetailPO();
        d1.setFundsType("FEE");
        d1.setCustomerId("CUST_A");
        d1.setAmount(new BigDecimal("100.00"));

        BusinessDetailPO d2 = new BusinessDetailPO();
        d2.setFundsType("FEE");
        d2.setCustomerId("CUST_B");
        d2.setAmount(new BigDecimal("100.00"));

        VoucheringDomainService.JournalWithDetails jwd = new VoucheringDomainService.JournalWithDetails(
                journal, List.of(d1, d2));
        when(voucheringDomainService.loadJournal("TRC_DIR_001")).thenReturn(jwd);

        AccountingRulePO rule = new AccountingRulePO();
        rule.setId(2L);
        AccountingRuleDetailPO rd1 = new AccountingRuleDetailPO();
        rd1.setId(21L);
        rd1.setRowNum(1);
        rd1.setFundsType("FEE");
        rd1.setSubjectCode("1001"); // 借方科目
        rd1.setDebitCredit(DebitCreditEnum.DEBIT); // 借方分录 -> INCREASE
        rd1.setCurrency("CNY");

        AccountingRuleDetailPO rd2 = new AccountingRuleDetailPO();
        rd2.setId(22L);
        rd2.setRowNum(2);
        rd2.setFundsType("FEE");
        rd2.setSubjectCode("2001"); // 贷方科目
        rd2.setDebitCredit(DebitCreditEnum.DEBIT); // 借方分录 -> DECREASE
        rd2.setCurrency("CNY");

        VoucheringDomainService.AccountingRuleWithDetails rwd = new VoucheringDomainService.AccountingRuleWithDetails(
                rule, List.of(rd1, rd2), Collections.emptyMap());
        when(voucheringDomainService.matchRule("PAY", "TRANSFER", "WECHAT")).thenReturn(rwd);

        TransactionPO txn = new TransactionPO();
        txn.setTxnNo("TXN_DIR_001");
        when(transactionRepository.selectByTraceNo("TRC_DIR_001")).thenReturn(txn);

        AccountSubjectPO sub1 = new AccountSubjectPO();
        sub1.setSubjectCode("1001");
        sub1.setDebitCredit(SubjectBalanceDirectionEnum.DEBIT); // 借方余额方向
        AccountSubjectPO sub2 = new AccountSubjectPO();
        sub2.setSubjectCode("2001");
        sub2.setDebitCredit(SubjectBalanceDirectionEnum.CREDIT); // 贷方余额方向
        when(subjectRepository.selectByCode("1001")).thenReturn(sub1);
        when(subjectRepository.selectByCode("2001")).thenReturn(sub2);

        when(voucheringDomainService.calculateEntryAmount(eq(rd1), any(), eq(journal)))
                .thenReturn(new BigDecimal("100.00"));
        when(voucheringDomainService.calculateEntryAmount(eq(rd2), any(), eq(journal)))
                .thenReturn(new BigDecimal("100.00"));

        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> cb = invocation.getArgument(0);
            return cb.doInTransaction(null);
        });

        org.mockito.ArgumentCaptor<List<VoucherEntryData>> entriesCaptor = org.mockito.ArgumentCaptor.forClass(List.class);
        when(voucheringDomainService.persistVoucher(any(), any(), entriesCaptor.capture(), any())).thenReturn("VOU_DIR");
        when(accountingVoucherRepository.updateTxnNoByVoucherNo("VOU_DIR", "TXN_DIR_001")).thenReturn(1);

        AccountingVoucherPO savedVoucher = new AccountingVoucherPO();
        savedVoucher.setVoucherNo("VOU_DIR");
        savedVoucher.setAmount(new BigDecimal("100.00"));
        when(accountingVoucherRepository.selectByTraceNo("TRC_DIR_001")).thenReturn(List.of(savedVoucher));

        VoucherGenerateResponse resp = service.generateVoucher(req);
        assertThat(resp).isNotNull();

        List<VoucherEntryData> captured = entriesCaptor.getValue();
        assertThat(captured).hasSize(2);
        // 借方分录(1) + 借方科目(1) -> 增 (1)
        assertThat(captured.get(0).getChangeDirection()).isEqualTo(ChangeDirectionEnum.INCREASE.getCode());
        // 借方分录(1) + 贷方科目(2) -> 减 (2)
        assertThat(captured.get(1).getChangeDirection()).isEqualTo(ChangeDirectionEnum.DECREASE.getCode());
    }

    @Test
    @DisplayName("generateVoucher: 携带辅助核算项时应正确回填真实生成的 voucherNo 与 entryId")
    void generateVoucher_withAuxiliary_shouldBackfillVoucherNoAndEntryId() {
        VoucherGenerateRequest req = new VoucherGenerateRequest();
        req.setTraceNo("TRC_AUX_001");
        req.setAccountMapping(Map.of("CUST_A:1001", "ACC_001"));

        BusinessRecordPO journal = new BusinessRecordPO();
        journal.setTraceNo("TRC_AUX_001");
        journal.setStatus(BusinessRecordStatusEnum.PROCESSING);
        journal.setBusinessCode("PAY");
        journal.setTradingCode("TRANSFER");
        journal.setPayChannel("WECHAT");
        journal.setAccountingDate(LocalDate.now());

        BusinessDetailPO d1 = new BusinessDetailPO();
        d1.setFundsType("PRINCIPAL");
        d1.setCustomerId("CUST_A");
        d1.setAmount(new BigDecimal("100.00"));

        VoucheringDomainService.JournalWithDetails jwd = new VoucheringDomainService.JournalWithDetails(
                journal, List.of(d1));
        when(voucheringDomainService.loadJournal("TRC_AUX_001")).thenReturn(jwd);

        AccountingRulePO rule = new AccountingRulePO();
        rule.setId(3L);
        AccountingRuleDetailPO rd1 = new AccountingRuleDetailPO();
        rd1.setId(31L);
        rd1.setRowNum(1);
        rd1.setFundsType("PRINCIPAL");
        rd1.setSubjectCode("1001");
        rd1.setDebitCredit(DebitCreditEnum.DEBIT);
        rd1.setCurrency("CNY");

        AccountingRuleAuxiliaryPO auxConfig = new AccountingRuleAuxiliaryPO();
        auxConfig.setId(301L);
        auxConfig.setRuleDetailId(31L);
        auxConfig.setAuxType("DEPT");
        auxConfig.setAuxCode("D001");

        VoucheringDomainService.AccountingRuleWithDetails rwd = new VoucheringDomainService.AccountingRuleWithDetails(
                rule, List.of(rd1), Map.of(31L, List.of(auxConfig)));
        when(voucheringDomainService.matchRule("PAY", "TRANSFER", "WECHAT")).thenReturn(rwd);

        TransactionPO txn = new TransactionPO();
        txn.setTxnNo("TXN_AUX_001");
        when(transactionRepository.selectByTraceNo("TRC_AUX_001")).thenReturn(txn);

        AccountSubjectPO sub1 = new AccountSubjectPO();
        sub1.setSubjectCode("1001");
        sub1.setDebitCredit(SubjectBalanceDirectionEnum.DEBIT);
        when(subjectRepository.selectByCode("1001")).thenReturn(sub1);

        when(voucheringDomainService.calculateEntryAmount(eq(rd1), any(), eq(journal)))
                .thenReturn(new BigDecimal("100.00"));

        AuxiliaryItemData mockAuxItem = new AuxiliaryItemData(
                null, null, "1001", "DEPT", "D001", "技术部", 1, new BigDecimal("100.00"), LocalDate.now());
        when(auxiliaryDomainService.calculateAuxiliaryAllocation(any(), any(), any(), any()))
                .thenReturn(List.of(mockAuxItem));

        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> cb = invocation.getArgument(0);
            return cb.doInTransaction(null);
        });

        when(voucheringDomainService.persistVoucher(any(), any(), any(), any())).thenAnswer(invocation -> {
            List<VoucherEntryData> entryList = invocation.getArgument(2);
            for (int i = 0; i < entryList.size(); i++) {
                entryList.get(i).setVoucherNo("VOU_AUX_001");
                entryList.get(i).setEntryId("ENT_AUX_00" + (i + 1));
            }
            return "VOU_AUX_001";
        });
        when(accountingVoucherRepository.updateTxnNoByVoucherNo("VOU_AUX_001", "TXN_AUX_001")).thenReturn(1);

        AccountingVoucherPO savedVoucher = new AccountingVoucherPO();
        savedVoucher.setVoucherNo("VOU_AUX_001");
        savedVoucher.setAmount(new BigDecimal("100.00"));
        when(accountingVoucherRepository.selectByTraceNo("TRC_AUX_001")).thenReturn(List.of(savedVoucher));

        VoucherGenerateResponse resp = service.generateVoucher(req);
        assertThat(resp).isNotNull();

        org.mockito.ArgumentCaptor<List<AuxiliaryItemData>> auxCaptor = org.mockito.ArgumentCaptor.forClass(List.class);
        verify(auxiliaryDomainService).persistAuxiliaryItems(auxCaptor.capture());
        List<AuxiliaryItemData> savedAuxList = auxCaptor.getValue();
        assertThat(savedAuxList).hasSize(1);
        assertThat(savedAuxList.get(0).getVoucherNo()).isEqualTo("VOU_AUX_001");
        assertThat(savedAuxList.get(0).getEntryId()).isEqualTo("ENT_AUX_001");
    }

    @Test
    @DisplayName("generateVoucher: 规则配置多款项分录但流水仅上传部分款项时，动态跳过未上传款项分录且借贷平衡生成凭证")
    void generateVoucher_withOptionalFundsType_shouldSkipMissingAndGenerateVoucher() {
        VoucherGenerateRequest req = new VoucherGenerateRequest();
        req.setTraceNo("TRC_OPT_001");
        req.setAccountMapping(Map.of("CUST_A:1001", "ACC_001", "CUST_A:2001", "ACC_002"));

        BusinessRecordPO journal = new BusinessRecordPO();
        journal.setTraceNo("TRC_OPT_001");
        journal.setStatus(BusinessRecordStatusEnum.PROCESSING);
        journal.setBusinessCode("LOAN");
        journal.setTradingCode("REPAY");
        journal.setPayChannel("BANK");
        journal.setAccountingDate(LocalDate.now());

        // 业务流水明细仅传了本金 PRINCIPAL，未传利息 INTEREST
        BusinessDetailPO dPrincipal = new BusinessDetailPO();
        dPrincipal.setFundsType("PRINCIPAL");
        dPrincipal.setCustomerId("CUST_A");
        dPrincipal.setAmount(new BigDecimal("1000.00"));

        VoucheringDomainService.JournalWithDetails jwd = new VoucheringDomainService.JournalWithDetails(
                journal, List.of(dPrincipal));
        when(voucheringDomainService.loadJournal("TRC_OPT_001")).thenReturn(jwd);

        AccountingRulePO rule = new AccountingRulePO();
        rule.setId(10L);

        // 规则行 1: 借方 银行存款（本金款项）
        AccountingRuleDetailPO rd1 = new AccountingRuleDetailPO();
        rd1.setId(101L);
        rd1.setRowNum(1);
        rd1.setFundsType("PRINCIPAL");
        rd1.setSubjectCode("1001");
        rd1.setDebitCredit(DebitCreditEnum.DEBIT);
        rd1.setCurrency("CNY");

        // 规则行 2: 贷方 贷款本金（本金款项）
        AccountingRuleDetailPO rd2 = new AccountingRuleDetailPO();
        rd2.setId(102L);
        rd2.setRowNum(2);
        rd2.setFundsType("PRINCIPAL");
        rd2.setSubjectCode("2001");
        rd2.setDebitCredit(DebitCreditEnum.CREDIT);
        rd2.setCurrency("CNY");

        // 规则行 3: 贷方 利息收入（利息款项，本次交易未发生且未上送）
        AccountingRuleDetailPO rd3 = new AccountingRuleDetailPO();
        rd3.setId(103L);
        rd3.setRowNum(3);
        rd3.setFundsType("INTEREST");
        rd3.setSubjectCode("6011");
        rd3.setDebitCredit(DebitCreditEnum.CREDIT);
        rd3.setCurrency("CNY");

        VoucheringDomainService.AccountingRuleWithDetails rwd = new VoucheringDomainService.AccountingRuleWithDetails(
                rule, List.of(rd1, rd2, rd3), Collections.emptyMap());
        when(voucheringDomainService.matchRule("LOAN", "REPAY", "BANK")).thenReturn(rwd);

        TransactionPO txn = new TransactionPO();
        txn.setTxnNo("TXN_OPT_001");
        when(transactionRepository.selectByTraceNo("TRC_OPT_001")).thenReturn(txn);

        AccountSubjectPO sub1 = new AccountSubjectPO();
        sub1.setSubjectCode("1001");
        sub1.setDebitCredit(SubjectBalanceDirectionEnum.DEBIT);
        AccountSubjectPO sub2 = new AccountSubjectPO();
        sub2.setSubjectCode("2001");
        sub2.setDebitCredit(SubjectBalanceDirectionEnum.CREDIT);
        when(subjectRepository.selectByCode("1001")).thenReturn(sub1);
        when(subjectRepository.selectByCode("2001")).thenReturn(sub2);

        when(voucheringDomainService.calculateEntryAmount(eq(rd1), any(), eq(journal)))
                .thenReturn(new BigDecimal("1000.00"));
        when(voucheringDomainService.calculateEntryAmount(eq(rd2), any(), eq(journal)))
                .thenReturn(new BigDecimal("1000.00"));

        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> cb = invocation.getArgument(0);
            return cb.doInTransaction(null);
        });

        org.mockito.ArgumentCaptor<List<VoucherEntryData>> entriesCaptor = org.mockito.ArgumentCaptor.forClass(List.class);
        when(voucheringDomainService.persistVoucher(any(), any(), entriesCaptor.capture(), any())).thenReturn("VOU_OPT_001");
        when(accountingVoucherRepository.updateTxnNoByVoucherNo("VOU_OPT_001", "TXN_OPT_001")).thenReturn(1);

        AccountingVoucherPO savedVoucher = new AccountingVoucherPO();
        savedVoucher.setVoucherNo("VOU_OPT_001");
        savedVoucher.setAmount(new BigDecimal("1000.00"));
        when(accountingVoucherRepository.selectByTraceNo("TRC_OPT_001")).thenReturn(List.of(savedVoucher));

        VoucherGenerateResponse resp = service.generateVoucher(req);
        assertThat(resp).isNotNull();

        List<VoucherEntryData> captured = entriesCaptor.getValue();
        // 验证只生成了 2 条分录（未上传的利息分录 rd3 被动态跳过，不报规则不存在）
        assertThat(captured).hasSize(2);
        assertThat(captured.get(0).getSubjectCode()).isEqualTo("1001");
        assertThat(captured.get(1).getSubjectCode()).isEqualTo("2001");
        verify(voucheringDomainService).validateDebitCreditBalance(captured);
    }

    @Test
    @DisplayName("generateVoucher: 分录计算金额为 0 时动态跳过生成 0 元分录")
    void generateVoucher_withZeroCalculatedAmount_shouldSkipZeroAmountEntry() {
        VoucherGenerateRequest req = new VoucherGenerateRequest();
        req.setTraceNo("TRC_ZERO_001");
        req.setAccountMapping(Map.of("CUST_A:1001", "ACC_001", "CUST_A:2001", "ACC_002", "CUST_A:6011", "ACC_003"));

        BusinessRecordPO journal = new BusinessRecordPO();
        journal.setTraceNo("TRC_ZERO_001");
        journal.setStatus(BusinessRecordStatusEnum.PROCESSING);
        journal.setBusinessCode("LOAN");
        journal.setTradingCode("REPAY");
        journal.setPayChannel("BANK");
        journal.setAccountingDate(LocalDate.now());

        BusinessDetailPO dPrincipal = new BusinessDetailPO();
        dPrincipal.setFundsType("PRINCIPAL");
        dPrincipal.setCustomerId("CUST_A");
        dPrincipal.setAmount(new BigDecimal("1000.00"));

        BusinessDetailPO dInterest = new BusinessDetailPO();
        dInterest.setFundsType("INTEREST");
        dInterest.setCustomerId("CUST_A");
        dInterest.setAmount(BigDecimal.ZERO);

        VoucheringDomainService.JournalWithDetails jwd = new VoucheringDomainService.JournalWithDetails(
                journal, List.of(dPrincipal, dInterest));
        when(voucheringDomainService.loadJournal("TRC_ZERO_001")).thenReturn(jwd);

        AccountingRulePO rule = new AccountingRulePO();
        rule.setId(10L);

        AccountingRuleDetailPO rd1 = new AccountingRuleDetailPO();
        rd1.setId(101L);
        rd1.setRowNum(1);
        rd1.setFundsType("PRINCIPAL");
        rd1.setSubjectCode("1001");
        rd1.setDebitCredit(DebitCreditEnum.DEBIT);
        rd1.setCurrency("CNY");

        AccountingRuleDetailPO rd2 = new AccountingRuleDetailPO();
        rd2.setId(102L);
        rd2.setRowNum(2);
        rd2.setFundsType("PRINCIPAL");
        rd2.setSubjectCode("2001");
        rd2.setDebitCredit(DebitCreditEnum.CREDIT);
        rd2.setCurrency("CNY");

        AccountingRuleDetailPO rd3 = new AccountingRuleDetailPO();
        rd3.setId(103L);
        rd3.setRowNum(3);
        rd3.setFundsType("INTEREST");
        rd3.setSubjectCode("6011");
        rd3.setDebitCredit(DebitCreditEnum.CREDIT);
        rd3.setCurrency("CNY");

        VoucheringDomainService.AccountingRuleWithDetails rwd = new VoucheringDomainService.AccountingRuleWithDetails(
                rule, List.of(rd1, rd2, rd3), Collections.emptyMap());
        when(voucheringDomainService.matchRule("LOAN", "REPAY", "BANK")).thenReturn(rwd);

        TransactionPO txn = new TransactionPO();
        txn.setTxnNo("TXN_ZERO_001");
        when(transactionRepository.selectByTraceNo("TRC_ZERO_001")).thenReturn(txn);

        AccountSubjectPO sub1 = new AccountSubjectPO();
        sub1.setSubjectCode("1001");
        sub1.setDebitCredit(SubjectBalanceDirectionEnum.DEBIT);
        AccountSubjectPO sub2 = new AccountSubjectPO();
        sub2.setSubjectCode("2001");
        sub2.setDebitCredit(SubjectBalanceDirectionEnum.CREDIT);
        when(subjectRepository.selectByCode("1001")).thenReturn(sub1);
        when(subjectRepository.selectByCode("2001")).thenReturn(sub2);

        when(voucheringDomainService.calculateEntryAmount(eq(rd1), any(), eq(journal)))
                .thenReturn(new BigDecimal("1000.00"));
        when(voucheringDomainService.calculateEntryAmount(eq(rd2), any(), eq(journal)))
                .thenReturn(new BigDecimal("1000.00"));
        // rd3 计算金额为 0
        when(voucheringDomainService.calculateEntryAmount(eq(rd3), any(), eq(journal)))
                .thenReturn(BigDecimal.ZERO);

        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> cb = invocation.getArgument(0);
            return cb.doInTransaction(null);
        });

        org.mockito.ArgumentCaptor<List<VoucherEntryData>> entriesCaptor = org.mockito.ArgumentCaptor.forClass(List.class);
        when(voucheringDomainService.persistVoucher(any(), any(), entriesCaptor.capture(), any())).thenReturn("VOU_ZERO_001");
        when(accountingVoucherRepository.updateTxnNoByVoucherNo("VOU_ZERO_001", "TXN_ZERO_001")).thenReturn(1);

        AccountingVoucherPO savedVoucher = new AccountingVoucherPO();
        savedVoucher.setVoucherNo("VOU_ZERO_001");
        savedVoucher.setAmount(new BigDecimal("1000.00"));
        when(accountingVoucherRepository.selectByTraceNo("TRC_ZERO_001")).thenReturn(List.of(savedVoucher));

        VoucherGenerateResponse resp = service.generateVoucher(req);
        assertThat(resp).isNotNull();

        List<VoucherEntryData> captured = entriesCaptor.getValue();
        // 验证金额为 0 的利息分录被动态跳过，未生成 0 元分录
        assertThat(captured).hasSize(2);
        assertThat(captured.stream().map(VoucherEntryData::getSubjectCode).toList())
                .containsExactly("1001", "2001");
    }

    @Test
    @DisplayName("用户还款（含减免券）多款项聚合分录生成：生成严格6行分录且借贷平衡")
    void generateVoucher_repaymentWithReliefCoupons_multiFundsAggregation_shouldGenerateBalancedSixEntries() {
        VoucherGenerateRequest req = new VoucherGenerateRequest();
        req.setTraceNo("RD202610220000001001");
        req.setBookkeeperName("SYSTEM");

        BusinessRecordPO journal = new BusinessRecordPO();
        journal.setTraceNo("RD202610220000001001");
        journal.setStatus(BusinessRecordStatusEnum.PROCESSING);
        journal.setBusinessCode("jiedianqian_quant");
        journal.setTradingCode("LOAN_REPAY");
        journal.setPayChannel("BANK");
        journal.setAmount(new BigDecimal("140.00"));
        journal.setAccountingDate(LocalDate.now());

        // 5 笔款项明细
        BusinessDetailPO dPrincipal = new BusinessDetailPO();
        dPrincipal.setFundsType("PRINCIPAL");
        dPrincipal.setCustomerId("CUST_LF");
        dPrincipal.setAmount(new BigDecimal("100.00"));

        BusinessDetailPO dInterest = new BusinessDetailPO();
        dInterest.setFundsType("INTEREST");
        dInterest.setCustomerId("CUST_LF");
        dInterest.setAmount(new BigDecimal("10.00"));

        BusinessDetailPO dFee = new BusinessDetailPO();
        dFee.setFundsType("GUARANTEE_FEE");
        dFee.setCustomerId("CUST_LF");
        dFee.setAmount(new BigDecimal("10.00"));

        BusinessDetailPO dInterestRelief = new BusinessDetailPO();
        dInterestRelief.setFundsType("INTEREST_RELIEF");
        dInterestRelief.setCustomerId("CUST_LF");
        dInterestRelief.setAmount(new BigDecimal("10.00"));

        BusinessDetailPO dFeeRelief = new BusinessDetailPO();
        dFeeRelief.setFundsType("GUARANTEE_FEE_RELIEF");
        dFeeRelief.setCustomerId("CUST_LF");
        dFeeRelief.setAmount(new BigDecimal("10.00"));

        List<BusinessDetailPO> details = List.of(dPrincipal, dInterest, dFee, dInterestRelief, dFeeRelief);
        when(voucheringDomainService.loadJournal("RD202610220000001001"))
                .thenReturn(new VoucheringDomainService.JournalWithDetails(journal, details));

        AccountingRulePO rule = new AccountingRulePO();
        rule.setId(88L);
        rule.setVoucherType("LOAN_REPAY");

        // 6 行规则明细配置（采用多款项配置）
        AccountingRuleDetailPO rd1 = new AccountingRuleDetailPO();
        rd1.setId(881L);
        rd1.setRowNum(1);
        rd1.setFundsType("PRINCIPAL,INTEREST,GUARANTEE_FEE"); // 借：一般户存款（聚合 100+10+10=120）
        rd1.setSubjectCode("100202");
        rd1.setDebitCredit(DebitCreditEnum.DEBIT);
        rd1.setCurrency("CNY");

        AccountingRuleDetailPO rd2 = new AccountingRuleDetailPO();
        rd2.setId(882L);
        rd2.setRowNum(2);
        rd2.setFundsType("INTEREST_RELIEF"); // 借：利息减免损失（10）
        rd2.setSubjectCode("670201");
        rd2.setDebitCredit(DebitCreditEnum.DEBIT);
        rd2.setCurrency("CNY");

        AccountingRuleDetailPO rd3 = new AccountingRuleDetailPO();
        rd3.setId(883L);
        rd3.setRowNum(3);
        rd3.setFundsType("GUARANTEE_FEE_RELIEF"); // 借：担保费减免损失（10）
        rd3.setSubjectCode("670203");
        rd3.setDebitCredit(DebitCreditEnum.DEBIT);
        rd3.setCurrency("CNY");

        AccountingRuleDetailPO rd4 = new AccountingRuleDetailPO();
        rd4.setId(884L);
        rd4.setRowNum(4);
        rd4.setFundsType("PRINCIPAL"); // 贷：个人消费贷款（100）
        rd4.setSubjectCode("12010101");
        rd4.setDebitCredit(DebitCreditEnum.CREDIT);
        rd4.setCurrency("CNY");

        AccountingRuleDetailPO rd5 = new AccountingRuleDetailPO();
        rd5.setId(885L);
        rd5.setRowNum(5);
        rd5.setFundsType("INTEREST,INTEREST_RELIEF"); // 贷：应收利息（聚合 10+10=20）
        rd5.setSubjectCode("120301");
        rd5.setDebitCredit(DebitCreditEnum.CREDIT);
        rd5.setCurrency("CNY");

        AccountingRuleDetailPO rd6 = new AccountingRuleDetailPO();
        rd6.setId(886L);
        rd6.setRowNum(6);
        rd6.setFundsType("GUARANTEE_FEE,GUARANTEE_FEE_RELIEF"); // 贷：应收担保费（聚合 10+10=20）
        rd6.setSubjectCode("120501");
        rd6.setDebitCredit(DebitCreditEnum.CREDIT);
        rd6.setCurrency("CNY");

        List<AccountingRuleDetailPO> ruleDetails = List.of(rd1, rd2, rd3, rd4, rd5, rd6);
        when(voucheringDomainService.matchRule("jiedianqian_quant", "LOAN_REPAY", "BANK"))
                .thenReturn(new VoucheringDomainService.AccountingRuleWithDetails(rule, ruleDetails, Collections.emptyMap()));

        TransactionPO txn = new TransactionPO();
        txn.setTxnNo("TXN_REPAY_001");
        when(transactionRepository.selectByTraceNo("RD202610220000001001")).thenReturn(txn);

        // 科目余额方向配置
        Map<String, AccountSubjectPO> subjects = Map.of(
                "100202", createSubject("100202", SubjectBalanceDirectionEnum.DEBIT),
                "670201", createSubject("670201", SubjectBalanceDirectionEnum.DEBIT),
                "670203", createSubject("670203", SubjectBalanceDirectionEnum.DEBIT),
                "12010101", createSubject("12010101", SubjectBalanceDirectionEnum.DEBIT),
                "120301", createSubject("120301", SubjectBalanceDirectionEnum.DEBIT),
                "120501", createSubject("120501", SubjectBalanceDirectionEnum.DEBIT)
        );
        subjects.forEach((code, subj) -> when(subjectRepository.selectByCode(code)).thenReturn(subj));

        // Mock voucheringDomainService 计算金额（验证聚合方法与单方法调用）
        when(voucheringDomainService.calculateAggregatedEntryAmount(eq(rd1), anyList(), eq(journal)))
                .thenReturn(new BigDecimal("120.00")); // 100 + 10 + 10
        when(voucheringDomainService.calculateEntryAmount(eq(rd2), any(), eq(journal)))
                .thenReturn(new BigDecimal("10.00"));
        when(voucheringDomainService.calculateEntryAmount(eq(rd3), any(), eq(journal)))
                .thenReturn(new BigDecimal("10.00"));
        when(voucheringDomainService.calculateEntryAmount(eq(rd4), any(), eq(journal)))
                .thenReturn(new BigDecimal("100.00"));
        when(voucheringDomainService.calculateAggregatedEntryAmount(eq(rd5), anyList(), eq(journal)))
                .thenReturn(new BigDecimal("20.00")); // 10 + 10
        when(voucheringDomainService.calculateAggregatedEntryAmount(eq(rd6), anyList(), eq(journal)))
                .thenReturn(new BigDecimal("20.00")); // 10 + 10

        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> cb = invocation.getArgument(0);
            return cb.doInTransaction(null);
        });

        org.mockito.ArgumentCaptor<List<VoucherEntryData>> entriesCaptor = org.mockito.ArgumentCaptor.forClass(List.class);
        when(voucheringDomainService.persistVoucher(any(), any(), entriesCaptor.capture(), any())).thenReturn("VOU_REPAY_001");
        when(accountingVoucherRepository.updateTxnNoByVoucherNo("VOU_REPAY_001", "TXN_REPAY_001")).thenReturn(1);

        AccountingVoucherPO savedVoucher = new AccountingVoucherPO();
        savedVoucher.setVoucherNo("VOU_REPAY_001");
        savedVoucher.setAmount(new BigDecimal("140.00"));
        when(accountingVoucherRepository.selectByTraceNo("RD202610220000001001")).thenReturn(List.of(savedVoucher));

        VoucherGenerateResponse resp = service.generateVoucher(req);
        assertThat(resp).isNotNull();

        List<VoucherEntryData> entries = entriesCaptor.getValue();
        // 验证：凭证分录严格为 6 行
        assertThat(entries).hasSize(6);

        // 验证借贷双方金额
        BigDecimal debitSum = entries.stream()
                .filter(e -> e.getDebitCredit() == 1)
                .map(VoucherEntryData::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal creditSum = entries.stream()
                .filter(e -> e.getDebitCredit() == 2)
                .map(VoucherEntryData::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 验证：借贷平衡且总额均为 140.00
        assertThat(debitSum).isEqualByComparingTo(new BigDecimal("140.00"));
        assertThat(creditSum).isEqualByComparingTo(new BigDecimal("140.00"));

        // 验证各明细分录金额
        assertThat(entries.get(0).getAmount()).isEqualByComparingTo("120.00"); // 100202 一般户存款
        assertThat(entries.get(1).getAmount()).isEqualByComparingTo("10.00");  // 670201 利息减免损失
        assertThat(entries.get(2).getAmount()).isEqualByComparingTo("10.00");  // 670203 担保费减免损失
        assertThat(entries.get(3).getAmount()).isEqualByComparingTo("100.00"); // 12010101 个人消费贷款
        assertThat(entries.get(4).getAmount()).isEqualByComparingTo("20.00");  // 120301 个人贷款应收利息
        assertThat(entries.get(5).getAmount()).isEqualByComparingTo("20.00");  // 120501 个人贷款应收担保费
    }

    private AccountSubjectPO createSubject(String code, SubjectBalanceDirectionEnum dir) {
        AccountSubjectPO subj = new AccountSubjectPO();
        subj.setSubjectCode(code);
        subj.setDebitCredit(dir);
        return subj;
    }
}
