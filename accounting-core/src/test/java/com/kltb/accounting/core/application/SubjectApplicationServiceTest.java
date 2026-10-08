package com.kltb.accounting.core.application;

import com.kltb.accounting.api.constant.ResultCode;
import com.kltb.accounting.api.request.SubjectUpdateRequest;
import com.kltb.accounting.core.domain.enums.AvailableStatusEnum;
import com.kltb.accounting.core.domain.enums.DebitCreditEnum;
import com.kltb.accounting.core.domain.enums.SubjectCategoryEnum;
import com.kltb.accounting.core.domain.enums.SubjectNatureEnum;
import com.kltb.accounting.core.infrastructure.persistence.entity.AccountSubjectPO;
import com.kltb.accounting.core.infrastructure.persistence.mapper.AccountMapper;
import com.kltb.accounting.core.infrastructure.persistence.repository.SubjectRepository;
import com.kltb.accounting.core.shared.exception.ServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * SubjectApplicationService 单元测试
 */
@ExtendWith(MockitoExtension.class)
class SubjectApplicationServiceTest {

    @Mock
    private SubjectRepository subjectRepository;

    @Mock
    private AccountMapper accountMapper;

    @Mock
    private TransactionTemplate transactionTemplate;

    @InjectMocks
    private SubjectApplicationService subjectApplicationService;

    @BeforeEach
    void setUp() {
        lenient().doAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(null);
        }).when(transactionTemplate).execute(any());
    }

    @Test
    @DisplayName("更新科目: 更新借贷方向成功")
    void update_debitCredit_success() {
        AccountSubjectPO po = new AccountSubjectPO();
        po.setId(100L);
        po.setSubjectCode("1001");
        po.setSubjectName("库存现金");
        po.setSubjectCategory(SubjectCategoryEnum.ASSET);
        po.setNature(SubjectNatureEnum.CASH);
        po.setDebitCredit(DebitCreditEnum.DEBIT);
        po.setLeaf(true);
        po.setStatus(AvailableStatusEnum.ENABLED);

        when(subjectRepository.selectByCode("1001")).thenReturn(po);

        SubjectUpdateRequest request = new SubjectUpdateRequest();
        request.setDebitCredit(2); // 改为贷方

        subjectApplicationService.update("1001", request);

        ArgumentCaptor<AccountSubjectPO> captor = ArgumentCaptor.forClass(AccountSubjectPO.class);
        verify(subjectRepository).updateSubjectById(captor.capture());

        AccountSubjectPO updated = captor.getValue();
        assertThat(updated.getDebitCredit()).isEqualTo(DebitCreditEnum.CREDIT);
    }

    @Test
    @DisplayName("更新科目: 科目不存在抛出异常")
    void update_subjectNotFound_throwsException() {
        when(subjectRepository.selectByCode("9999")).thenReturn(null);

        SubjectUpdateRequest request = new SubjectUpdateRequest();
        request.setSubjectName("不存在");

        assertThatThrownBy(() -> subjectApplicationService.update("9999", request))
                .isInstanceOf(ServiceException.class)
                .extracting("resultCode")
                .isEqualTo(ResultCode.SUBJECT_NOT_FOUND);
    }
}
