package com.kltb.accounting.core.application.service;

import com.kltb.accounting.api.constant.ResultCode;
import com.kltb.accounting.api.request.PostingExecuteRequest;
import com.kltb.accounting.api.response.PostingExecuteResponse;
import com.kltb.accounting.core.application.assembler.PostingAssembler;
import com.kltb.accounting.core.domain.enums.AccountStatusEnum;
import com.kltb.accounting.core.domain.enums.VoucherStatusEnum;
import com.kltb.accounting.core.domain.service.AsyncPostingDomainService;
import com.kltb.accounting.core.domain.service.PostingDomainService;
import com.kltb.accounting.core.domain.service.RollbackDomainService;
import com.kltb.accounting.core.infrastructure.persistence.entity.AccountPO;
import com.kltb.accounting.core.infrastructure.persistence.entity.AccountingVoucherEntryPO;
import com.kltb.accounting.core.infrastructure.persistence.entity.AccountingVoucherPO;
import com.kltb.accounting.core.infrastructure.persistence.entity.TransactionPO;
import com.kltb.accounting.core.infrastructure.persistence.repository.AccountRepository;
import com.kltb.accounting.core.infrastructure.persistence.repository.AccountingVoucherRepository;
import com.kltb.accounting.core.infrastructure.persistence.repository.TransactionRepository;
import com.kltb.accounting.core.infrastructure.redis.DistributedLockTemplate;
import com.kltb.accounting.core.shared.exception.AccountException;
import com.kltb.accounting.core.shared.exception.ServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PostingApplicationServiceTest {

    @Mock
    private PostingDomainService postingDomainService;

    @Mock
    private AsyncPostingDomainService asyncPostingDomainService;

    @Mock
    private RollbackDomainService rollbackDomainService;

    @Mock
    private AccountingVoucherRepository accountingVoucherRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private TransactionTemplate transactionTemplate;

    @Mock
    private DistributedLockTemplate distributedLockTemplate;

    @Spy
    private PostingAssembler assembler = new PostingAssembler();

    @InjectMocks
    private PostingApplicationService postingApplicationService;

    @BeforeEach
    void setUp() {
        lenient().when(distributedLockTemplate.execute(anyString(), anyLong(), anyLong(), any()))
                .thenAnswer(invocation -> {
                    java.util.function.Supplier<?> action = invocation.getArgument(3);
                    return action.get();
                });

        lenient().when(transactionTemplate.execute(any()))
                .thenAnswer(invocation -> {
                    TransactionCallback<?> callback = invocation.getArgument(0);
                    return callback.doInTransaction(mock(TransactionStatus.class));
                });

        lenient().when(rollbackDomainService.isRetryable(any())).thenReturn(false);
    }

    @Test
    @DisplayName("executePosting: 批量一次性查询账户成功并执行过账")
    void executePosting_batchQueryAccounts_success() {
        String voucherNo = "VOUCHER001";
        AccountingVoucherPO voucher = new AccountingVoucherPO();
        voucher.setVoucherNo(voucherNo);
        voucher.setStatus(VoucherStatusEnum.PENDING);
        voucher.setTxnNo("TXN001");

        AccountingVoucherEntryPO entry1 = new AccountingVoucherEntryPO();
        entry1.setEntryId("ENTRY001");
        entry1.setVoucherNo(voucherNo);
        entry1.setAccountNo("ACC001");
        entry1.setUnilateral(1); // 实时
        entry1.setBuffered(0);

        AccountingVoucherEntryPO entry2 = new AccountingVoucherEntryPO();
        entry2.setEntryId("ENTRY002");
        entry2.setVoucherNo(voucherNo);
        entry2.setAccountNo("ACC002");
        entry2.setUnilateral(1); // 实时
        entry2.setBuffered(0);

        when(accountingVoucherRepository.selectByVoucherNoSimple(voucherNo)).thenReturn(voucher);
        when(accountingVoucherRepository.selectEntriesByVoucherNo(voucherNo)).thenReturn(List.of(entry1, entry2));

        AccountPO acc1 = new AccountPO();
        acc1.setAccountNo("ACC001");
        acc1.setStatus(AccountStatusEnum.NORMAL);

        AccountPO acc2 = new AccountPO();
        acc2.setAccountNo("ACC002");
        acc2.setStatus(AccountStatusEnum.NORMAL);

        when(accountRepository.selectByAccountNos(List.of("ACC001", "ACC002"))).thenReturn(List.of(acc1, acc2));

        TransactionPO txn = new TransactionPO();
        txn.setTxnNo("TXN001");
        when(transactionRepository.selectByTxnNo("TXN001")).thenReturn(txn);

        PostingExecuteRequest req = new PostingExecuteRequest();
        req.setVoucherNo(voucherNo);
        req.setOperatorName("admin");

        PostingExecuteResponse response = postingApplicationService.executePosting(req);

        assertThat(response).isNotNull();
        // 关键验证：批量查询方法被调用 1 次，入参为 [ACC001, ACC002]
        verify(accountRepository, times(1)).selectByAccountNos(List.of("ACC001", "ACC002"));
        // 验证不再循环逐个调用 selectByAccountNo
        verify(accountRepository, never()).selectByAccountNo(anyString());
        verify(postingDomainService, times(1)).executeRealTimePosting(eq(voucher), anyList());
    }

    @Test
    @DisplayName("executePosting: 账户不存在时抛出 ACCOUNT_NOT_FOUND 异常")
    void executePosting_accountNotFound_throwsException() {
        String voucherNo = "VOUCHER001";
        AccountingVoucherPO voucher = new AccountingVoucherPO();
        voucher.setVoucherNo(voucherNo);
        voucher.setStatus(VoucherStatusEnum.PENDING);

        AccountingVoucherEntryPO entry1 = new AccountingVoucherEntryPO();
        entry1.setEntryId("ENTRY001");
        entry1.setVoucherNo(voucherNo);
        entry1.setAccountNo("ACC001");

        when(accountingVoucherRepository.selectByVoucherNoSimple(voucherNo)).thenReturn(voucher);
        when(accountingVoucherRepository.selectEntriesByVoucherNo(voucherNo)).thenReturn(List.of(entry1));

        // 批量查询返回空列表，表示 ACC001 并不存在
        when(accountRepository.selectByAccountNos(List.of("ACC001"))).thenReturn(Collections.emptyList());

        PostingExecuteRequest req = new PostingExecuteRequest();
        req.setVoucherNo(voucherNo);

        assertThatThrownBy(() -> postingApplicationService.executePosting(req))
                .isInstanceOf(AccountException.class)
                .hasMessageContaining("账户不存在");
    }

    @Test
    @DisplayName("executePosting: 账户状态冻结时抛出 ACCOUNT_FROZEN 异常")
    void executePosting_accountFrozen_throwsException() {
        String voucherNo = "VOUCHER001";
        AccountingVoucherPO voucher = new AccountingVoucherPO();
        voucher.setVoucherNo(voucherNo);
        voucher.setStatus(VoucherStatusEnum.PENDING);

        AccountingVoucherEntryPO entry1 = new AccountingVoucherEntryPO();
        entry1.setEntryId("ENTRY001");
        entry1.setVoucherNo(voucherNo);
        entry1.setAccountNo("ACC001");

        when(accountingVoucherRepository.selectByVoucherNoSimple(voucherNo)).thenReturn(voucher);
        when(accountingVoucherRepository.selectEntriesByVoucherNo(voucherNo)).thenReturn(List.of(entry1));

        AccountPO acc1 = new AccountPO();
        acc1.setAccountNo("ACC001");
        acc1.setStatus(AccountStatusEnum.FROZEN);

        when(accountRepository.selectByAccountNos(List.of("ACC001"))).thenReturn(List.of(acc1));

        PostingExecuteRequest req = new PostingExecuteRequest();
        req.setVoucherNo(voucherNo);

        assertThatThrownBy(() -> postingApplicationService.executePosting(req))
                .isInstanceOf(AccountException.class)
                .hasMessageContaining("冻结");
    }

    @Test
    @DisplayName("executePosting: 分录缺少 accountNo 时抛出 PARAM_ERROR 异常")
    void executePosting_entryMissingAccountNo_throwsException() {
        String voucherNo = "VOUCHER001";
        AccountingVoucherPO voucher = new AccountingVoucherPO();
        voucher.setVoucherNo(voucherNo);
        voucher.setStatus(VoucherStatusEnum.PENDING);

        AccountingVoucherEntryPO entry1 = new AccountingVoucherEntryPO();
        entry1.setEntryId("ENTRY001");
        entry1.setVoucherNo(voucherNo);
        entry1.setAccountNo(null);

        when(accountingVoucherRepository.selectByVoucherNoSimple(voucherNo)).thenReturn(voucher);
        when(accountingVoucherRepository.selectEntriesByVoucherNo(voucherNo)).thenReturn(List.of(entry1));

        PostingExecuteRequest req = new PostingExecuteRequest();
        req.setVoucherNo(voucherNo);

        assertThatThrownBy(() -> postingApplicationService.executePosting(req))
                .isInstanceOf(AccountException.class)
                .hasMessageContaining("未关联有效分户账号");
    }

    @Test
    @DisplayName("executePosting: 凭证不存在时抛出 VOUCHER_NOT_FOUND 异常")
    void executePosting_voucherNotFound_throwsException() {
        when(accountingVoucherRepository.selectByVoucherNoSimple("UNKNOWN")).thenReturn(null);

        PostingExecuteRequest req = new PostingExecuteRequest();
        req.setVoucherNo("UNKNOWN");

        assertThatThrownBy(() -> postingApplicationService.executePosting(req))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("凭证不存在");
    }

    @Test
    @DisplayName("executePosting: 凭证状态非法时抛出 VOUCHER_STATUS_ILLEGAL 异常")
    void executePosting_voucherStatusIllegal_throwsException() {
        AccountingVoucherPO voucher = new AccountingVoucherPO();
        voucher.setVoucherNo("VOUCHER001");
        voucher.setStatus(VoucherStatusEnum.POSTED);

        when(accountingVoucherRepository.selectByVoucherNoSimple("VOUCHER001")).thenReturn(voucher);

        PostingExecuteRequest req = new PostingExecuteRequest();
        req.setVoucherNo("VOUCHER001");

        assertThatThrownBy(() -> postingApplicationService.executePosting(req))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("凭证状态非法");
    }
}
