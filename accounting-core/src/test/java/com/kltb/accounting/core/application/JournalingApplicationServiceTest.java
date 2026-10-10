package com.kltb.accounting.core.application;

import com.kltb.accounting.api.request.*;
import com.kltb.accounting.api.response.*;
import com.kltb.accounting.core.application.assembler.JournalingAssembler;
import com.kltb.accounting.core.application.service.JournalingApplicationService;
import com.kltb.accounting.core.domain.enums.*;
import com.kltb.accounting.core.domain.service.AccountPreCheckDomainService;
import com.kltb.accounting.core.domain.service.FreezeDomainService;
import com.kltb.accounting.core.domain.service.JournalSubmitResult;
import com.kltb.accounting.core.domain.service.JournalingDomainService;
import com.kltb.accounting.core.infrastructure.config.FreezeProperties;
import com.kltb.accounting.core.infrastructure.persistence.entity.*;
import com.kltb.accounting.core.infrastructure.persistence.repository.*;
import com.kltb.accounting.core.infrastructure.redis.DistributedLockTemplate;
import com.kltb.accounting.core.shared.exception.ServiceException;
import org.junit.jupiter.api.BeforeEach;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JournalingApplicationServiceTest {

    @Mock
    private JournalingDomainService journalingDomainService;

    @Mock
    private AccountPreCheckDomainService accountPreCheckDomainService;

    @Mock
    private FreezeDomainService freezeDomainService;

    @Mock
    private BusinessRecordRepository businessRecordRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private AccountingVoucherRepository accountingVoucherRepository;

    @Mock
    private FreezeDetailRepository freezeDetailRepository;

    @Mock
    private AccountingRuleRepository accountingRuleRepository;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private SubjectRepository subjectRepository;

    @Mock
    private DistributedLockTemplate distributedLockTemplate;

    @Mock
    private TransactionTemplate transactionTemplate;

    @Mock
    private com.kltb.accounting.core.application.service.VoucheringApplicationService voucheringApplicationService;

    @Mock
    private com.kltb.accounting.core.application.service.PostingApplicationService postingApplicationService;

    @Mock
    private com.kltb.accounting.core.domain.service.RollbackDomainService rollbackDomainService;

    @Mock
    private BufferPostingDetailRepository bufferPostingDetailRepository;

    @Mock
    private com.kltb.accounting.core.infrastructure.messaging.LocalMessageService localMessageService;

    @Mock
    private ManualVoucherApplyRepository manualVoucherApplyRepository;

    @Spy
    private JournalingAssembler assembler = new JournalingAssembler();

    @Spy
    private FreezeProperties freezeProperties = new FreezeProperties();

    @InjectMocks
    private JournalingApplicationService service;

    @BeforeEach
    void setUp() {
        // 让分布式锁模板默认直接执行传入的 Supplier
        lenient().when(distributedLockTemplate.execute(anyString(), anyLong(), anyLong(), any()))
                .thenAnswer(invocation -> {
                    java.util.function.Supplier<?> action = invocation.getArgument(3);
                    return action.get();
                });
    }

    @Test
    @DisplayName("submitJournal: 提交普通记账流水成功")
    void submitJournal_shouldSucceed() {
        JournalSubmitRequest request = new JournalSubmitRequest();
        request.setTraceNo("TRACE001");
        request.setTraceSeq(0);
        request.setBusinessCode("LOAN");
        request.setTradingCode("DISBURSE");
        request.setPayChannel("BANK");
        request.setTradeType(TradeTypeEnum.NORMAL.getCode());
        request.setAmount(new BigDecimal("100.00"));
        request.setTradeTime(LocalDateTime.now());
        request.setSummary("放款记账");

        JournalDetailRequest detail = new JournalDetailRequest();
        detail.setCustomerId("CUST001");
        detail.setCustomerType(CustomerTypeEnum.PERSONAL.getCode());
        detail.setFundsType("PRINCIPAL");
        detail.setItemCode("ITEM001");
        detail.setAmount(new BigDecimal("100.00"));
        request.setDetails(List.of(detail));

        when(journalingDomainService.checkIdempotent("TRACE001", 0)).thenReturn(null);
        when(journalingDomainService.determineAccountingDate(any())).thenReturn(LocalDate.of(2026, 3, 1));
        when(journalingDomainService.persistJournal(anyString(), anyInt(), anyString(), anyString(), anyString(),
                anyInt(), any(), any(), any(), anyList(), any(), any(), any()))
                .thenReturn(new JournalSubmitResult("TRACE001", LocalDate.of(2026, 3, 1), "TXN001"));

        JournalSubmitResponse response = service.submitJournal(request);

        assertThat(response).isNotNull();
        assertThat(response.getTraceNo()).isEqualTo("TRACE001");
        assertThat(response.getTxnNo()).isEqualTo("TXN001");
        assertThat(response.getNeedVouchering()).isTrue();
    }

    @Test
    @DisplayName("freezeJournal: 业务预冻结成功（自动识别出金方并锁定资金）")
    void freezeJournal_shouldSucceed() {
        JournalFreezeRequest request = new JournalFreezeRequest();
        request.setTraceNo("FRZ_TRACE_001");
        request.setTraceSeq(0);
        request.setBusinessCode("RETAIL");
        request.setTradingCode("AUTH_PAY");
        request.setPayChannel("ALIPAY");
        request.setAmount(new BigDecimal("200.00"));
        request.setTradeTime(LocalDateTime.now());
        request.setSummary("预授权冻结");

        JournalDetailRequest detail = new JournalDetailRequest();
        detail.setCustomerId("USER100");
        detail.setCustomerType(CustomerTypeEnum.PERSONAL.getCode());
        detail.setFundsType("DEPOSIT");
        detail.setItemCode("ITEM001");
        detail.setAmount(new BigDecimal("200.00"));
        request.setDetails(List.of(detail));

        when(journalingDomainService.checkIdempotent("FRZ_TRACE_001", 0)).thenReturn(null);
        when(journalingDomainService.determineAccountingDate(any())).thenReturn(LocalDate.of(2026, 3, 1));

        // Mock 记账规则
        AccountingRulePO rule = new AccountingRulePO();
        rule.setId(10L);
        rule.setRuleName("消费支付规则");
        rule.setRequirePreFreeze(1); // 启用预冻结要求
        when(accountingRuleRepository.selectByBusinessKey("RETAIL", "AUTH_PAY", "ALIPAY")).thenReturn(rule);

        AccountingRuleDetailPO ruleDetail = new AccountingRuleDetailPO();
        ruleDetail.setId(101L);
        ruleDetail.setSubjectCode("200101");
        ruleDetail.setFundsType("DEPOSIT");
        ruleDetail.setDebitCredit(DebitCreditEnum.DEBIT); // 客户存款借方 = 出金方（余额减少）
        when(accountingRuleRepository.selectDetailsWithAuxiliary(10L)).thenReturn(List.of(ruleDetail));

        AccountSubjectPO subject = new AccountSubjectPO();
        subject.setSubjectCode("200101");
        subject.setDebitCredit(DebitCreditEnum.CREDIT); // 负债类余额方向为贷
        when(subjectRepository.selectByCode("200101")).thenReturn(subject);

        AccountPO account = new AccountPO();
        account.setAccountNo("ACCT2001010001");
        account.setBalance(new BigDecimal("500.00"));
        when(accountRepository.selectByOwnerIdAndSubjectCode("USER100", "200101")).thenReturn(account);

        BusinessRecordPO recordPO = new BusinessRecordPO();
        recordPO.setTraceNo("FRZ_TRACE_001");
        recordPO.setAccountingDate(LocalDate.of(2026, 3, 1));
        when(journalingDomainService.persistFreezeRecord(anyString(), anyInt(), anyString(), anyString(), anyString(),
                any(), any(), any(), anyList(), any(), any())).thenReturn(recordPO);

        AccountFreezeDetailPO freezeDetailPO = new AccountFreezeDetailPO();
        freezeDetailPO.setVoucherNo("FRZ_ID_888");
        freezeDetailPO.setAccountNo("ACCT2001010001");
        freezeDetailPO.setFreezeAmount(new BigDecimal("200.00"));
        freezeDetailPO.setOrigFreezeAmount(new BigDecimal("200.00"));
        freezeDetailPO.setExpireTime(LocalDateTime.now().plusSeconds(1800));
        freezeDetailPO.setStatus(FreezeStatusEnum.FROZEN);
        when(freezeDomainService.freezeFund(eq("ACCT2001010001"), eq(new BigDecimal("200.00")), eq("RETAIL"), any(), any(), any()))
                .thenReturn(freezeDetailPO);

        JournalFreezeResponse response = service.freezeJournal(request);

        assertThat(response).isNotNull();
        assertThat(response.getTraceNo()).isEqualTo("FRZ_TRACE_001");
        assertThat(response.getFreezeId()).isEqualTo("FRZ_ID_888");
        assertThat(response.getAccountNo()).isEqualTo("ACCT2001010001");
        assertThat(response.getFreezeAmount()).isEqualByComparingTo("200.00");
        assertThat(response.getStatus()).isEqualTo(FreezeStatusEnum.FROZEN.getCode());
        verify(businessRecordRepository).updateOrigFreezeNoAndStatus("FRZ_TRACE_001", "FRZ_ID_888", BusinessRecordStatusEnum.SUCCESS);
    }

    @Test
    @DisplayName("freezeJournal: 规则未启用预冻结时抛出异常")
    void freezeJournal_ruleNotRequirePreFreeze_shouldThrowException() {
        JournalFreezeRequest request = new JournalFreezeRequest();
        request.setTraceNo("FRZ_TRACE_ERR");
        request.setTraceSeq(0);
        request.setBusinessCode("RETAIL");
        request.setTradingCode("DIRECT_PAY");
        request.setPayChannel("ALIPAY");
        request.setAmount(new BigDecimal("100.00"));
        request.setTradeTime(LocalDateTime.now());
        JournalDetailRequest detail = new JournalDetailRequest();
        detail.setCustomerId("USER100");
        detail.setCustomerType(1);
        detail.setFundsType("DEPOSIT");
        detail.setItemCode("ITEM001");
        detail.setAmount(new BigDecimal("100.00"));
        request.setDetails(List.of(detail));

        AccountingRulePO rule = new AccountingRulePO();
        rule.setId(20L);
        rule.setRequirePreFreeze(0); // 未启用
        when(accountingRuleRepository.selectByBusinessKey("RETAIL", "DIRECT_PAY", "ALIPAY")).thenReturn(rule);

        assertThatThrownBy(() -> service.freezeJournal(request))
                .isInstanceOf(com.kltb.accounting.core.shared.exception.AccountException.class)
                .hasMessageContaining("未启用预冻结要求");
    }

    @Test
    @DisplayName("submitJournal: 规则要求需先预冻结但未传 origFreezeNo 时抛出异常")
    void submitJournal_requirePreFreeze_withoutOrigFreezeNo_shouldThrowException() {
        JournalSubmitRequest request = new JournalSubmitRequest();
        request.setTraceNo("TRACE_ERR_001");
        request.setTraceSeq(0);
        request.setBusinessCode("RETAIL");
        request.setTradingCode("AUTH_PAY");
        request.setPayChannel("ALIPAY");
        request.setTradeType(1);
        request.setAmount(new BigDecimal("100.00"));
        request.setTradeTime(LocalDateTime.now());
        JournalDetailRequest detail = new JournalDetailRequest();
        detail.setCustomerId("USER100");
        detail.setCustomerType(1);
        detail.setFundsType("DEPOSIT");
        detail.setItemCode("ITEM001");
        detail.setAmount(new BigDecimal("100.00"));
        request.setDetails(List.of(detail));

        AccountingRulePO rule = new AccountingRulePO();
        rule.setId(10L);
        rule.setRequirePreFreeze(1);
        when(accountingRuleRepository.selectByBusinessKey("RETAIL", "AUTH_PAY", "ALIPAY")).thenReturn(rule);

        assertThatThrownBy(() -> service.submitJournal(request))
                .isInstanceOf(com.kltb.accounting.core.shared.exception.AccountException.class)
                .hasMessageContaining("必须先完成资金预冻结");
    }

    @Test
    @DisplayName("unfreezeJournal: 业务预冻结全额撤销解冻成功")
    void unfreezeJournal_shouldSucceed() {
        JournalUnfreezeRequest request = new JournalUnfreezeRequest();
        request.setTraceNo("UNFRZ_TRACE_001");
        request.setOrigTraceNo("FRZ_TRACE_001");
        request.setReason("订单取消撤销冻结");

        when(journalingDomainService.checkIdempotent("UNFRZ_TRACE_001", 0)).thenReturn(null);

        BusinessRecordPO origRecord = new BusinessRecordPO();
        origRecord.setTraceNo("FRZ_TRACE_001");
        origRecord.setTradeType(TradeTypeEnum.PRE_FREEZE);
        origRecord.setOrigFreezeNo("FRZ_ID_888");
        origRecord.setBusinessCode("RETAIL");
        origRecord.setTradingCode("AUTH_PAY");
        origRecord.setPayChannel("ALIPAY");
        when(businessRecordRepository.selectByTraceNo("FRZ_TRACE_001")).thenReturn(origRecord);

        AccountFreezeDetailPO freezeDetail = new AccountFreezeDetailPO();
        freezeDetail.setVoucherNo("FRZ_ID_888");
        freezeDetail.setAccountNo("ACCT2001010001");
        freezeDetail.setOrigFreezeAmount(new BigDecimal("200.00"));
        freezeDetail.setFreezeAmount(new BigDecimal("200.00"));
        freezeDetail.setDeductedAmount(BigDecimal.ZERO);
        freezeDetail.setStatus(FreezeStatusEnum.FROZEN);

        AccountFreezeDetailPO unfrozenDetail = new AccountFreezeDetailPO();
        unfrozenDetail.setVoucherNo("FRZ_ID_888");
        unfrozenDetail.setAccountNo("ACCT2001010001");
        unfrozenDetail.setOrigFreezeAmount(new BigDecimal("200.00"));
        unfrozenDetail.setStatus(FreezeStatusEnum.UNFROZEN);

        when(freezeDetailRepository.selectListByTraceNo("FRZ_TRACE_001")).thenReturn(List.of(freezeDetail));
        when(freezeDetailRepository.selectByVoucherNo("FRZ_ID_888")).thenReturn(unfrozenDetail);

        when(journalingDomainService.determineAccountingDate(any())).thenReturn(LocalDate.of(2026, 3, 1));

        JournalUnfreezeResponse response = service.unfreezeJournal(request);

        assertThat(response).isNotNull();
        assertThat(response.getTraceNo()).isEqualTo("UNFRZ_TRACE_001");
        assertThat(response.getOrigTraceNo()).isEqualTo("FRZ_TRACE_001");
        assertThat(response.getFreezeId()).isEqualTo("FRZ_ID_888");
        assertThat(response.getUnfreezeAmount()).isEqualByComparingTo("200.00");
        verify(freezeDomainService).unfreezeFund("FRZ_ID_888", new BigDecimal("200.00"), "订单取消撤销冻结");
    }

    @Test
    @DisplayName("unfreezeJournal: 原预冻结已有扣款时禁止全额撤销")
    void unfreezeJournal_withDeduction_shouldThrowException() {
        JournalUnfreezeRequest request = new JournalUnfreezeRequest();
        request.setTraceNo("UNFRZ_TRACE_002");
        request.setOrigTraceNo("FRZ_TRACE_001");

        when(journalingDomainService.checkIdempotent("UNFRZ_TRACE_002", 0)).thenReturn(null);

        BusinessRecordPO origRecord = new BusinessRecordPO();
        origRecord.setTraceNo("FRZ_TRACE_001");
        origRecord.setTradeType(TradeTypeEnum.PRE_FREEZE);
        origRecord.setOrigFreezeNo("FRZ_ID_888");
        when(businessRecordRepository.selectByTraceNo("FRZ_TRACE_001")).thenReturn(origRecord);

        AccountFreezeDetailPO freezeDetail = new AccountFreezeDetailPO();
        freezeDetail.setVoucherNo("FRZ_ID_888");
        freezeDetail.setOrigFreezeAmount(new BigDecimal("200.00"));
        freezeDetail.setFreezeAmount(new BigDecimal("100.00"));
        freezeDetail.setDeductedAmount(new BigDecimal("100.00")); // 已发生扣款
        freezeDetail.setStatus(FreezeStatusEnum.FROZEN);
        when(freezeDetailRepository.selectListByTraceNo("FRZ_TRACE_001")).thenReturn(List.of(freezeDetail));

        assertThatThrownBy(() -> service.unfreezeJournal(request))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("已有扣款");
    }

    @Test
    @DisplayName("getJournalOverview: 聚合查询流水全流程总览成功")
    void getJournalOverview_shouldReturnOverview() {
        String traceNo = "TRACE_OVERVIEW_001";

        BusinessRecordPO record = new BusinessRecordPO();
        record.setTraceNo(traceNo);
        record.setBusinessCode("LOAN");
        record.setTradingCode("PAY");
        record.setAmount(new BigDecimal("500.00"));
        record.setTradeType(TradeTypeEnum.NORMAL);
        record.setStatus(BusinessRecordStatusEnum.SUCCESS);
        record.setAccountingDate(LocalDate.of(2026, 3, 1));
        when(businessRecordRepository.selectByTraceNo(traceNo)).thenReturn(record);

        TransactionPO txn = new TransactionPO();
        txn.setTxnNo("TXN_001");
        txn.setStatus(TransactionStatusEnum.SUCCESS);
        when(transactionRepository.selectByTraceNo(traceNo)).thenReturn(txn);

        AccountingVoucherPO voucher = new AccountingVoucherPO();
        voucher.setVoucherNo("VOU_001");
        voucher.setVoucherType("GEN");
        voucher.setStatus(VoucherStatusEnum.POSTED);
        voucher.setAmount(new BigDecimal("500.00"));
        when(accountingVoucherRepository.selectByTraceNo(traceNo)).thenReturn(List.of(voucher));

        AccountingVoucherEntryPO entry = new AccountingVoucherEntryPO();
        entry.setVoucherNo("VOU_001");
        entry.setEntryId("ENT_001");
        entry.setSubjectCode("1001");
        entry.setAccountNo("ACCT001");
        entry.setDebitCredit(DebitCreditEnum.DEBIT);
        entry.setAmount(new BigDecimal("500.00"));
        entry.setStatus(VoucherEntryStatusEnum.POSTED);
        when(accountingVoucherRepository.selectEntriesByVoucherNos(List.of("VOU_001"))).thenReturn(List.of(entry));

        JournalOverviewResponse overview = service.getJournalOverview(traceNo);

        assertThat(overview).isNotNull();
        assertThat(overview.getRecord().getTraceNo()).isEqualTo(traceNo);
        assertThat(overview.getTransaction().getTxnNo()).isEqualTo("TXN_001");
        assertThat(overview.getVouchers()).hasSize(1);
        assertThat(overview.getVouchers().get(0).getEntries()).hasSize(1);
        assertThat(overview.getVouchers().get(0).getEntries().get(0).getEntryId()).isEqualTo("ENT_001");
    }

    @Test
    @DisplayName("getJournalOverview: 手工记账流水不存在 BusinessRecordPO 时仍能基于事务和凭证聚合总览")
    void getJournalOverview_manualVoucherWithoutBusinessRecord_shouldSynthesizeOverview() {
        String applyNo = "MVA20261008000002";
        when(businessRecordRepository.selectByTraceNo(applyNo)).thenReturn(null);

        TransactionPO txn = new TransactionPO();
        txn.setTxnNo("TXN_MVA_001");
        txn.setTraceNo(applyNo);
        txn.setStatus(TransactionStatusEnum.SUCCESS);
        txn.setAmount(new BigDecimal("1000.00"));
        txn.setAccountingDate(LocalDate.of(2026, 10, 8));
        when(transactionRepository.selectByTraceNo(applyNo)).thenReturn(txn);

        AccountingVoucherPO voucher = new AccountingVoucherPO();
        voucher.setVoucherNo("VOU_MVA_001");
        voucher.setTxnNo("TXN_MVA_001");
        voucher.setTraceNo(applyNo);
        voucher.setBusinessCode("MANUAL");
        voucher.setTradingCode("TRANSFER");
        voucher.setPayChannel("INTERNAL");
        voucher.setStatus(VoucherStatusEnum.POSTED);
        voucher.setAmount(new BigDecimal("1000.00"));
        when(accountingVoucherRepository.selectByTraceNo(applyNo)).thenReturn(List.of(voucher));

        ManualVoucherApplyPO apply = new ManualVoucherApplyPO();
        apply.setApplyNo(applyNo);
        apply.setTotalDebitAmount(new BigDecimal("1000.00"));
        apply.setSummary("手工凭证补录");
        apply.setApplyStatus(ManualVoucherApplyStatusEnum.BOOKED);
        when(manualVoucherApplyRepository.selectByApplyNo(applyNo)).thenReturn(apply);

        JournalOverviewResponse overview = service.getJournalOverview(applyNo);

        assertThat(overview).isNotNull();
        assertThat(overview.getRecord()).isNotNull();
        assertThat(overview.getRecord().getTraceNo()).isEqualTo(applyNo);
        assertThat(overview.getRecord().getBusinessCode()).isEqualTo("MANUAL");
        assertThat(overview.getRecord().getSummary()).isEqualTo("手工凭证补录");
        assertThat(overview.getTransaction()).isNotNull();
        assertThat(overview.getTransaction().getTxnNo()).isEqualTo("TXN_MVA_001");
        assertThat(overview.getVouchers()).hasSize(1);
        assertThat(overview.getProcessStage()).isEqualTo("SUCCESS");
        assertThat(overview.getCanRetry()).isFalse(); // 合成流水不具备真实业务流水ID，禁止重试
    }

    @Test
    @DisplayName("getTransactionStatus: 查询关联事务状态成功")
    void getTransactionStatus_shouldReturnStatus() {
        String traceNo = "TRACE_TXN_001";
        TransactionPO txn = new TransactionPO();
        txn.setTxnNo("TXN_888");
        txn.setStatus(TransactionStatusEnum.SUCCESS);
        txn.setRelateAccountCount(2);
        when(transactionRepository.selectByTraceNo(traceNo)).thenReturn(txn);

        AccountingVoucherPO voucher = new AccountingVoucherPO();
        voucher.setVoucherNo("VOU_888");
        voucher.setStatus(VoucherStatusEnum.POSTED);
        when(accountingVoucherRepository.selectByTraceNo(traceNo)).thenReturn(List.of(voucher));

        TransactionStatusResponse response = service.getTransactionStatus(traceNo);

        assertThat(response).isNotNull();
        assertThat(response.getTxnNo()).isEqualTo("TXN_888");
        assertThat(response.getVoucherNo()).isEqualTo("VOU_888");
        assertThat(response.getStatus()).isEqualTo(TransactionStatusEnum.SUCCESS.getCode());
    }

    @Test
    @DisplayName("submitJournal: 全流程自动串联（流水持久化 -> 凭证生成 -> 实时过账 -> 流水标记SUCCESS）")
    void submitJournal_withVoucheringAndPosting_shouldSucceed() {
        JournalSubmitRequest request = new JournalSubmitRequest();
        request.setTraceNo("TRACE_FULL_001");
        request.setTraceSeq(0);
        request.setBusinessCode("LOAN");
        request.setTradingCode("DISBURSE");
        request.setPayChannel("BANK");
        request.setTradeType(TradeTypeEnum.NORMAL.getCode());
        request.setAmount(new BigDecimal("100.00"));
        request.setTradeTime(LocalDateTime.now());
        request.setSummary("全流程记账");

        JournalDetailRequest detail = new JournalDetailRequest();
        detail.setCustomerId("CUST001");
        detail.setCustomerType(CustomerTypeEnum.PERSONAL.getCode());
        detail.setFundsType("PRINCIPAL");
        detail.setItemCode("ITEM001");
        detail.setAmount(new BigDecimal("100.00"));
        request.setDetails(List.of(detail));

        when(journalingDomainService.checkIdempotent("TRACE_FULL_001", 0)).thenReturn(null);
        when(journalingDomainService.determineAccountingDate(any())).thenReturn(LocalDate.of(2026, 3, 1));
        when(journalingDomainService.persistJournal(anyString(), anyInt(), anyString(), anyString(), anyString(),
                anyInt(), any(), any(), any(), anyList(), any(), any(), any()))
                .thenReturn(new JournalSubmitResult("TRACE_FULL_001", LocalDate.of(2026, 3, 1), "TXN_FULL_001"));

        VoucherGenerateResponse voucherResp = new VoucherGenerateResponse();
        voucherResp.setVoucherNo("VOU_FULL_001");
        when(voucheringApplicationService.generateVoucher(any())).thenReturn(voucherResp);

        PostingExecuteResponse postResp = new PostingExecuteResponse();
        postResp.setVoucherNo("VOU_FULL_001");
        postResp.setVoucherStatus(3); // POSTED
        when(postingApplicationService.executePosting(any())).thenReturn(postResp);

        JournalSubmitResponse response = service.submitJournal(request);

        assertThat(response).isNotNull();
        assertThat(response.getTraceNo()).isEqualTo("TRACE_FULL_001");
        verify(voucheringApplicationService).generateVoucher(any());
        verify(postingApplicationService).executePosting(any());
        verify(businessRecordRepository).updateStatusByTraceNo("TRACE_FULL_001", BusinessRecordStatusEnum.SUCCESS);
    }

    @Test
    @DisplayName("retryJournal: 记账失败流水重试成功")
    void retryJournal_shouldSucceed() {
        String traceNo = "TRACE_RETRY_001";
        BusinessRecordPO record = new BusinessRecordPO();
        record.setTraceNo(traceNo);
        record.setTraceSeq(1);
        record.setStatus(BusinessRecordStatusEnum.FAILED);
        when(businessRecordRepository.selectByTraceNo(traceNo)).thenReturn(record);

        AccountingVoucherPO voucher = new AccountingVoucherPO();
        voucher.setVoucherNo("VOU_RETRY_001");
        voucher.setStatus(VoucherStatusEnum.FAILED);
        when(accountingVoucherRepository.selectByTraceNo(traceNo)).thenReturn(List.of(voucher));

        PostingExecuteResponse postResp = new PostingExecuteResponse();
        postResp.setVoucherNo("VOU_RETRY_001");
        postResp.setVoucherStatus(3); // POSTED
        when(postingApplicationService.executePosting(any())).thenReturn(postResp);

        JournalOverviewResponse overview = service.retryJournal(traceNo);

        assertThat(overview).isNotNull();
        verify(businessRecordRepository).updateStatusByTraceNo(traceNo, BusinessRecordStatusEnum.PROCESSING);
        verify(postingApplicationService).executePosting(any());
        verify(businessRecordRepository).updateStatusByTraceNo(traceNo, BusinessRecordStatusEnum.SUCCESS);
    }

    @Test
    @DisplayName("rollbackJournal: 记账流水回滚成功")
    void rollbackJournal_shouldSucceed() {
        String traceNo = "TRACE_ROLLBACK_001";
        BusinessRecordPO record = new BusinessRecordPO();
        record.setTraceNo(traceNo);
        record.setTraceSeq(1);
        record.setStatus(BusinessRecordStatusEnum.FAILED);
        when(businessRecordRepository.selectByTraceNo(traceNo)).thenReturn(record);

        AccountingVoucherPO voucher = new AccountingVoucherPO();
        voucher.setVoucherNo("VOU_ROLLBACK_001");
        voucher.setTxnNo("TXN_ROLLBACK_001");
        when(accountingVoucherRepository.selectByTraceNo(traceNo)).thenReturn(List.of(voucher));

        JournalOverviewResponse overview = service.rollbackJournal(traceNo, "用户撤单");

        assertThat(overview).isNotNull();
        verify(rollbackDomainService).executeRollbackForAsyncFailure("VOU_ROLLBACK_001", "TXN_ROLLBACK_001", "用户撤单");
        verify(businessRecordRepository).updateStatusByTraceNo(traceNo, BusinessRecordStatusEnum.FAILED);
    }

    @Test
    @DisplayName("getJournal: 查询精简流水结果成功")
    void getJournal_shouldReturnQueryResponse() {
        String traceNo = "TRACE_QUERY_001";
        BusinessRecordPO record = new BusinessRecordPO();
        record.setTraceNo(traceNo);
        record.setTraceSeq(0);
        record.setBusinessCode("LOAN");
        record.setTradingCode("DISBURSE");
        record.setPayChannel("BANK");
        record.setTradeType(TradeTypeEnum.NORMAL);
        record.setAmount(new BigDecimal("100.00"));
        record.setAccountingDate(LocalDate.of(2026, 3, 1));
        record.setStatus(BusinessRecordStatusEnum.SUCCESS);
        when(businessRecordRepository.selectByTraceNo(traceNo)).thenReturn(record);

        TransactionPO txn = new TransactionPO();
        txn.setTxnNo("TXN_QUERY_001");
        txn.setStatus(TransactionStatusEnum.SUCCESS);
        when(transactionRepository.selectByTraceNo(traceNo)).thenReturn(txn);

        JournalQueryResponse response = service.getJournal(traceNo);

        assertThat(response).isNotNull();
        assertThat(response.getTraceNo()).isEqualTo(traceNo);
        assertThat(response.getTxnNo()).isEqualTo("TXN_QUERY_001");
        assertThat(response.getStatus()).isEqualTo(BusinessRecordStatusEnum.SUCCESS.getCode());
        assertThat(response.getAmount()).isEqualByComparingTo("100.00");
    }

    @Test
    @DisplayName("getJournalPage: 分页查询业务记账流水记录成功")
    void getJournalPage_shouldReturnPageResponse() {
        JournalPageQueryRequest req = new JournalPageQueryRequest();
        req.setPageNo(1);
        req.setPageSize(20);

        BusinessRecordPO record = new BusinessRecordPO();
        record.setTraceNo("TRACE_PAGE_001");
        record.setAmount(new BigDecimal("100.00"));
        record.setStatus(BusinessRecordStatusEnum.SUCCESS);

        com.baomidou.mybatisplus.extension.plugins.pagination.Page<BusinessRecordPO> mockPage =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(1, 20);
        mockPage.setRecords(List.of(record));
        mockPage.setTotal(1);

        when(businessRecordRepository.selectPage(req)).thenReturn(mockPage);

        PageResponse<JournalRecordItemResponse> response = service.getJournalPage(req);

        assertThat(response).isNotNull();
        assertThat(response.getTotal()).isEqualTo(1L);
        assertThat(response.getList()).hasSize(1);
        assertThat(response.getList().get(0).getTraceNo()).isEqualTo("TRACE_PAGE_001");
    }

    @Test
    @DisplayName("getTransactionPage: 分页查询账务事务记录成功")
    void getTransactionPage_shouldReturnPageResponse() {
        TransactionPageQueryRequest req = new TransactionPageQueryRequest();
        req.setPageNo(1);
        req.setPageSize(20);

        TransactionPO txn = new TransactionPO();
        txn.setTxnNo("TXN_PAGE_001");
        txn.setTraceNo("TRACE_PAGE_001");
        txn.setAmount(new BigDecimal("100.00"));
        txn.setStatus(TransactionStatusEnum.SUCCESS);

        com.baomidou.mybatisplus.extension.plugins.pagination.Page<TransactionPO> mockPage =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(1, 20);
        mockPage.setRecords(List.of(txn));
        mockPage.setTotal(1);

        when(transactionRepository.selectPage(req)).thenReturn(mockPage);

        PageResponse<TransactionRecordItemResponse> response = service.getTransactionPage(req);

        assertThat(response).isNotNull();
        assertThat(response.getTotal()).isEqualTo(1L);
        assertThat(response.getList()).hasSize(1);
        assertThat(response.getList().get(0).getTxnNo()).isEqualTo("TXN_PAGE_001");
    }
}
