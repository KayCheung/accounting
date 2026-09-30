// accounting-core/src/test/java/com/kltb/accounting/core/application/ManualVoucherApplicationServiceTest.java
package com.kltb.accounting.core.application;

import com.kltb.accounting.api.constant.AuditDecisionEnum;
import com.kltb.accounting.api.request.*;
import com.kltb.accounting.api.response.ManualVoucherApplyDetailResponse;
import com.kltb.accounting.api.response.PostingExecuteResponse;
import com.kltb.accounting.core.application.service.ManualVoucherApplicationService;
import com.kltb.accounting.core.application.service.PostingApplicationService;
import com.kltb.accounting.core.domain.enums.DebitCreditEnum;
import com.kltb.accounting.core.domain.enums.ManualVoucherApplyStatusEnum;
import com.kltb.accounting.core.infrastructure.account.RedisSequenceGenerator;
import com.kltb.accounting.core.infrastructure.dictionary.DictionaryComponent;
import com.kltb.accounting.core.infrastructure.persistence.entity.AccountPO;
import com.kltb.accounting.core.infrastructure.persistence.entity.AccountSubjectPO;
import com.kltb.accounting.core.infrastructure.persistence.entity.ManualVoucherApplyEntryPO;
import com.kltb.accounting.core.infrastructure.persistence.entity.ManualVoucherApplyPO;
import com.kltb.accounting.core.infrastructure.persistence.repository.*;
import com.kltb.accounting.core.shared.exception.AccountException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 手工记账独立审批流服务单元测试
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ManualVoucherApplicationServiceTest {

    @Mock
    private ManualVoucherApplyRepository applyRepository;
    @Mock
    private AccountingVoucherRepository accountingVoucherRepository;
    @Mock
    private SubjectRepository subjectRepository;
    @Mock
    private AccountRepository accountRepository;
    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private DictionaryRepository dictionaryRepository;
    @Mock
    private PostingApplicationService postingApplicationService;
    @Mock
    private TransactionTemplate transactionTemplate;
    @Mock
    private RedisSequenceGenerator seqGen;

    private ManualVoucherApplicationService service;

    @BeforeEach
    void setUp() {
        // mock TransactionTemplate 同步执行 callback
        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(mock(TransactionStatus.class));
        });
        doAnswer(invocation -> {
            Consumer<TransactionStatus> action = invocation.getArgument(0);
            action.accept(mock(TransactionStatus.class));
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());

        DictionaryComponent dictionaryComponent = new DictionaryComponent(dictionaryRepository);
        service = new ManualVoucherApplicationService(
                applyRepository,
                accountingVoucherRepository,
                subjectRepository,
                accountRepository,
                transactionRepository,
                dictionaryRepository,
                dictionaryComponent,
                postingApplicationService,
                transactionTemplate,
                seqGen
        );

        // mock 末级科目
        AccountSubjectPO sub1 = new AccountSubjectPO();
        sub1.setSubjectCode("100201");
        sub1.setSubjectName("银行存款-工行");
        sub1.setLeaf(true);
        sub1.setAllowPost(true);
        sub1.setDebitCredit(DebitCreditEnum.DEBIT);
        when(subjectRepository.selectByCode("100201")).thenReturn(sub1);

        AccountSubjectPO sub2 = new AccountSubjectPO();
        sub2.setSubjectCode("600101");
        sub2.setSubjectName("主营业务收入");
        sub2.setLeaf(true);
        sub2.setAllowPost(true);
        sub2.setDebitCredit(DebitCreditEnum.CREDIT);
        when(subjectRepository.selectByCode("600101")).thenReturn(sub2);

        // mock 账户推导
        AccountPO acc1 = new AccountPO();
        acc1.setAccountNo("ACC001");
        acc1.setSubjectCode("100201");
        when(accountRepository.selectBySubjectCode("100201")).thenReturn(Collections.singletonList(acc1));
        when(accountRepository.selectByAccountNo("ACC001")).thenReturn(acc1);

        AccountPO acc2 = new AccountPO();
        acc2.setAccountNo("ACC002");
        acc2.setSubjectCode("600101");
        when(accountRepository.selectBySubjectCode("600101")).thenReturn(Collections.singletonList(acc2));
        when(accountRepository.selectByAccountNo("ACC002")).thenReturn(acc2);

        when(seqGen.generate(eq("TXN"), any(LocalDate.class), eq(6), eq(25))).thenReturn("TXN20260929000088");
    }

    @Test
    @DisplayName("测试保存草稿：借贷平衡，状态成功置为 DRAFT 并记录审计日志")
    void testSaveDraft_Success() {
        when(seqGen.generate(eq("MVA"), any(LocalDate.class), eq(6), eq(25)))
                .thenReturn("MVA20260929000001");

        ManualVoucherApplySaveRequest request = ManualVoucherApplySaveRequest.builder()
                .accountingDate(LocalDate.of(2026, 9, 29))
                .summary("9月末调账草稿")
                .makerName("张会计")
                .action("DRAFT")
                .entries(Arrays.asList(
                        ManualVoucherApplyEntryRequest.builder()
                                .debitCredit(1)
                                .subjectCode("100201")
                                .amount(new BigDecimal("1000.00"))
                                .build(),
                        ManualVoucherApplyEntryRequest.builder()
                                .debitCredit(2)
                                .subjectCode("600101")
                                .amount(new BigDecimal("1000.00"))
                                .build()
                ))
                .build();

        String applyNo = service.saveOrSubmit(request);
        assertEquals("MVA20260929000001", applyNo);
        verify(applyRepository).insertApply(argThat(po ->
                po.getApplyStatus() == ManualVoucherApplyStatusEnum.DRAFT
                        && po.getTotalDebitAmount().compareTo(new BigDecimal("1000.00")) == 0
        ));
        verify(applyRepository).insertEntries(anyList());
        verify(applyRepository).insertAuditLog(argThat(log ->
                "CREATE_DRAFT".equals(log.getAction()) && "张会计".equals(log.getOperatorName())
        ));
    }

    @Test
    @DisplayName("测试借贷不平衡拦截：借方1000 vs 贷方800，抛出 AccountException")
    void testDebitCreditNotBalanced_ThrowsException() {
        ManualVoucherApplySaveRequest request = ManualVoucherApplySaveRequest.builder()
                .accountingDate(LocalDate.of(2026, 9, 29))
                .summary("不平衡分录")
                .makerName("张会计")
                .action("SUBMIT")
                .entries(Arrays.asList(
                        ManualVoucherApplyEntryRequest.builder()
                                .debitCredit(1)
                                .subjectCode("100201")
                                .amount(new BigDecimal("1000.00"))
                                .build(),
                        ManualVoucherApplyEntryRequest.builder()
                                .debitCredit(2)
                                .subjectCode("600101")
                                .amount(new BigDecimal("800.00"))
                                .build()
                ))
                .build();

        AccountException ex = assertThrows(AccountException.class, () -> service.saveOrSubmit(request));
        assertTrue(ex.getMessage().contains("借贷不平衡"));
    }

    @Test
    @DisplayName("测试初审流转：初审通过进入 PENDING_REVIEW，初审驳回进入 AUDIT_REJECTED")
    void testAudit_PassAndReject() {
        ManualVoucherApplyPO po = new ManualVoucherApplyPO();
        po.setApplyNo("MVA001");
        po.setApplyStatus(ManualVoucherApplyStatusEnum.PENDING_AUDIT);
        when(applyRepository.selectByApplyNo("MVA001")).thenReturn(po);

        // 初审通过
        service.audit(ManualVoucherApplyAuditRequest.builder()
                .applyNo("MVA001")
                .action(AuditDecisionEnum.PASS.getCode())
                .operatorName("李主管")
                .opinion("核对无误，初审通过")
                .build());

        assertEquals(ManualVoucherApplyStatusEnum.PENDING_REVIEW, po.getApplyStatus());
        assertEquals("李主管", po.getAuditorName());

        // 初审驳回
        po.setApplyStatus(ManualVoucherApplyStatusEnum.PENDING_AUDIT);
        service.audit(ManualVoucherApplyAuditRequest.builder()
                .applyNo("MVA001")
                .action(AuditDecisionEnum.REJECT.getCode())
                .operatorName("李主管")
                .opinion("科目选择有误，请修改")
                .build());

        assertEquals(ManualVoucherApplyStatusEnum.AUDIT_REJECTED, po.getApplyStatus());
        assertEquals("科目选择有误，请修改", po.getAuditOpinion());
    }

    @Test
    @DisplayName("测试复核流转：复核通过进入 PENDING_BOOKKEEPING（待记账）")
    void testReview_Pass() {
        ManualVoucherApplyPO po = new ManualVoucherApplyPO();
        po.setApplyNo("MVA002");
        po.setApplyStatus(ManualVoucherApplyStatusEnum.PENDING_REVIEW);
        when(applyRepository.selectByApplyNo("MVA002")).thenReturn(po);

        service.review(ManualVoucherApplyAuditRequest.builder()
                .applyNo("MVA002")
                .action(AuditDecisionEnum.PASS.getCode())
                .operatorName("赵经理")
                .opinion("终审合规，同意入账")
                .build());

        assertEquals(ManualVoucherApplyStatusEnum.PENDING_BOOKKEEPING, po.getApplyStatus());
        assertEquals("赵经理", po.getReviewerName());
    }

    @Test
    @DisplayName("测试正式记账落库：生成正式凭证落库，回填 reviewer_name 与 bookkeeper_name，申请表置为 BOOKED")
    void testExecuteBookkeeping_Success() {
        ManualVoucherApplyPO po = new ManualVoucherApplyPO();
        po.setApplyNo("MVA003");
        po.setVoucherType("记账凭证");
        po.setTradeType(2);
        po.setAccountingDate(LocalDate.of(2026, 9, 29));
        po.setSummary("正式调账");
        po.setTotalDebitAmount(new BigDecimal("2500.00"));
        po.setTotalCreditAmount(new BigDecimal("2500.00"));
        po.setApplyStatus(ManualVoucherApplyStatusEnum.PENDING_BOOKKEEPING);
        po.setReviewerName("赵经理");
        when(applyRepository.selectByApplyNo("MVA003")).thenReturn(po);

        List<ManualVoucherApplyEntryPO> entries = new ArrayList<>();
        ManualVoucherApplyEntryPO e1 = new ManualVoucherApplyEntryPO();
        e1.setApplyNo("MVA003");
        e1.setRowNum(1);
        e1.setDebitCredit(DebitCreditEnum.DEBIT);
        e1.setSubjectCode("100201");
        e1.setAccountNo("ACC001");
        e1.setAmount(new BigDecimal("2500.00"));
        e1.setCurrency("CNY");
        e1.setSummary("分录1");
        e1.setUnilateral(1);
        entries.add(e1);

        ManualVoucherApplyEntryPO e2 = new ManualVoucherApplyEntryPO();
        e2.setApplyNo("MVA003");
        e2.setRowNum(2);
        e2.setDebitCredit(DebitCreditEnum.CREDIT);
        e2.setSubjectCode("600101");
        e2.setAccountNo("ACC002");
        e2.setAmount(new BigDecimal("2500.00"));
        e2.setCurrency("CNY");
        e2.setSummary("分录2");
        e2.setUnilateral(1);
        entries.add(e2);

        when(applyRepository.selectEntriesByApplyNo("MVA003")).thenReturn(entries);
        when(seqGen.generate(eq("VOU"), any(LocalDate.class), eq(6), eq(25))).thenReturn("VOU20260929000088");
        when(seqGen.generate(eq("ENT"), any(LocalDateTime.class), anyString(), eq(4), eq(2))).thenReturn("ENT202609290001");
        when(postingApplicationService.executePosting(any())).thenReturn(mock(PostingExecuteResponse.class));

        ManualVoucherApplyPostRequest postReq = ManualVoucherApplyPostRequest.builder()
                .applyNo("MVA003")
                .bookkeeperName("王出纳")
                .remark("确认过账完成")
                .build();

        String voucherNo = service.executeBookkeeping(postReq);

        assertEquals("VOU20260929000088", voucherNo);
        // 验证正式落库 t_accounting_voucher 回填了复核人与记账人
        verify(accountingVoucherRepository).insert(argThat(v ->
                "VOU20260929000088".equals(v.getVoucherNo())
                        && "赵经理".equals(v.getReviewerName())
                        && "王出纳".equals(v.getBookkeeperName())
                        && "MVA003".equals(v.getTraceNo())
        ));
        // 验证分录落库且已正确推导并设置 changeDirection 增减方向
        verify(accountingVoucherRepository, times(2)).insertEntry(argThat(entry ->
                entry.getChangeDirection() != null && (entry.getChangeDirection() == 1 || entry.getChangeDirection() == 2)
        ));
        // 验证申请表状态回填
        assertEquals(ManualVoucherApplyStatusEnum.BOOKED, po.getApplyStatus());
        assertEquals("VOU20260929000088", po.getVoucherNo());
        assertEquals("王出纳", po.getBookkeeperName());
    }

    @Test
    @DisplayName("测试作废草稿成功")
    void testCancel_Success() {
        ManualVoucherApplyPO po = new ManualVoucherApplyPO();
        po.setApplyNo("MVA004");
        po.setApplyStatus(ManualVoucherApplyStatusEnum.DRAFT);
        when(applyRepository.selectByApplyNo("MVA004")).thenReturn(po);

        service.cancel("MVA004", "张会计", "录错作废");
        assertEquals(ManualVoucherApplyStatusEnum.CANCELLED, po.getApplyStatus());
        verify(applyRepository).insertAuditLog(argThat(log ->
                "CANCEL".equals(log.getAction()) && "录错作废".equals(log.getOpinion())
        ));
    }

    @Test
    @DisplayName("测试辅助核算与附件支持：保存落库、审核流转与正式记账落库法定表")
    void testSaveAndBookkeepingWithAuxiliaryAndAttachment() {
        when(seqGen.generate(eq("MVA"), any(LocalDate.class), eq(6), eq(25)))
                .thenReturn("MVA202609299999");
        when(seqGen.generate(eq("VOU"), any(LocalDate.class), eq(6), eq(25)))
                .thenReturn("VOU202609299999");
        when(seqGen.generate(eq("ENT"), any(LocalDateTime.class), anyString(), eq(4), eq(2)))
                .thenReturn("ENT202609299999");

        ManualVoucherApplySaveRequest saveReq = ManualVoucherApplySaveRequest.builder()
                .accountingDate(LocalDate.of(2026, 9, 29))
                .summary("带辅助核算与附件的调账单")
                .makerName("张会计")
                .action("SUBMIT")
                .entries(Arrays.asList(
                        ManualVoucherApplyEntryRequest.builder()
                                .rowNum(1)
                                .debitCredit(1)
                                .subjectCode("100201")
                                .amount(new BigDecimal("150.00"))
                                .build(),
                        ManualVoucherApplyEntryRequest.builder()
                                .rowNum(2)
                                .debitCredit(2)
                                .subjectCode("600101")
                                .amount(new BigDecimal("150.00"))
                                .build()
                ))
                .auxiliaries(Arrays.asList(
                        ManualVoucherApplyAuxiliaryRequest.builder()
                                .entryRowNum(1)
                                .subjectCode("100201")
                                .auxType("DEPT")
                                .auxTypeName("部门")
                                .auxCode("DEPT001")
                                .auxName("产品运营部")
                                .changeDirection(1)
                                .amount(new BigDecimal("150.00"))
                                .build()
                ))
                .attachments(Arrays.asList(
                        ManualVoucherApplyAttachmentRequest.builder()
                                .fileName("京东代扣证明.pdf")
                                .filePath("/attachments/mva_proof.pdf")
                                .fileSize(1024L * 1024L)
                                .build()
                ))
                .build();

        String applyNo = service.saveOrSubmit(saveReq);
        assertEquals("MVA202609299999", applyNo);

        // 验证辅助核算与附件落库
        verify(applyRepository).insertAuxiliaries(argThat(list ->
                list.size() == 1 && "DEPT001".equals(list.get(0).getAuxCode())
        ));
        verify(applyRepository).insertAttachments(argThat(list ->
                list.size() == 1 && "京东代扣证明.pdf".equals(list.get(0).getFileName())
        ));

        // 模拟执行记账
        ManualVoucherApplyPO po = new ManualVoucherApplyPO();
        po.setApplyNo("MVA202609299999");
        po.setAccountingDate(LocalDate.of(2026, 9, 29));
        po.setTotalDebitAmount(new BigDecimal("150.00"));
        po.setTotalCreditAmount(new BigDecimal("150.00"));
        po.setApplyStatus(ManualVoucherApplyStatusEnum.PENDING_BOOKKEEPING);
        po.setReviewerName("赵经理");
        po.setAttachmentCount(1);
        when(applyRepository.selectByApplyNo("MVA202609299999")).thenReturn(po);

        ManualVoucherApplyEntryPO entry1 = new ManualVoucherApplyEntryPO();
        entry1.setApplyNo("MVA202609299999");
        entry1.setRowNum(1);
        entry1.setDebitCredit(DebitCreditEnum.DEBIT);
        entry1.setSubjectCode("100201");
        entry1.setAmount(new BigDecimal("150.00"));
        when(applyRepository.selectEntriesByApplyNo("MVA202609299999")).thenReturn(List.of(entry1));

        var auxPO = new com.kltb.accounting.core.infrastructure.persistence.entity.ManualVoucherApplyAuxiliaryPO();
        auxPO.setApplyNo("MVA202609299999");
        auxPO.setEntryRowNum(1);
        auxPO.setAuxType("DEPT");
        auxPO.setAuxCode("DEPT001");
        auxPO.setAuxName("产品运营部");
        auxPO.setChangeDirection(1);
        auxPO.setAmount(new BigDecimal("150.00"));
        when(applyRepository.selectAuxiliariesByApplyNo("MVA202609299999")).thenReturn(List.of(auxPO));

        var attPO = new com.kltb.accounting.core.infrastructure.persistence.entity.ManualVoucherApplyAttachmentPO();
        attPO.setApplyNo("MVA202609299999");
        attPO.setFileName("京东代扣证明.pdf");
        attPO.setFilePath("/attachments/mva_proof.pdf");
        attPO.setFileSize(1024L * 1024L);
        when(applyRepository.selectAttachmentsByApplyNo("MVA202609299999")).thenReturn(List.of(attPO));

        String vouNo = service.executeBookkeeping(ManualVoucherApplyPostRequest.builder()
                .applyNo("MVA202609299999")
                .bookkeeperName("王出纳")
                .build());
        assertEquals("VOU202609299999", vouNo);

        // 验证正式凭证辅助核算表与附件表落库
        verify(accountingVoucherRepository).insertAuxiliary(argThat(a ->
                "VOU202609299999".equals(a.getVoucherNo()) && "DEPT001".equals(a.getAuxCode())
        ));
        verify(accountingVoucherRepository).insertAttachment(argThat(att ->
                "VOU202609299999".equals(att.getVoucherNo()) && "/attachments/mva_proof.pdf".equals(att.getFilePath())
        ));

        // 验证详情查询
        ManualVoucherApplyDetailResponse detail = service.getDetail("MVA202609299999");
        assertNotNull(detail);
        assertEquals(1, detail.getAuxiliaries().size());
        assertEquals("产品运营部", detail.getAuxiliaries().get(0).getAuxName());
        assertEquals(1, detail.getAttachments().size());
        assertEquals("PDF", detail.getAttachments().get(0).getFileType());
        assertEquals("1.00 MB", detail.getAttachments().get(0).getFileSizeFormatted());
    }
}
