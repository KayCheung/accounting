package com.kltb.accounting.core.application;

import com.kltb.accounting.api.constant.ResultCode;
import com.kltb.accounting.api.request.SubjectCreateRequest;
import com.kltb.accounting.api.request.SubjectUpdateRequest;
import com.kltb.accounting.core.domain.enums.AvailableStatusEnum;
import com.kltb.accounting.core.domain.enums.SubjectBalanceDirectionEnum;
import com.kltb.accounting.core.domain.enums.SubjectCategoryEnum;
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
        po.setDebitCredit(SubjectBalanceDirectionEnum.DEBIT);
        po.setLeaf(true);
        po.setStatus(AvailableStatusEnum.ENABLED);

        when(subjectRepository.selectByCode("1001")).thenReturn(po);

        SubjectUpdateRequest request = new SubjectUpdateRequest();
        request.setDebitCredit(2); // 改为贷方

        subjectApplicationService.update("1001", request);

        ArgumentCaptor<AccountSubjectPO> captor = ArgumentCaptor.forClass(AccountSubjectPO.class);
        verify(subjectRepository).updateSubjectById(captor.capture());

        AccountSubjectPO updated = captor.getValue();
        assertThat(updated.getDebitCredit()).isEqualTo(SubjectBalanceDirectionEnum.CREDIT);
    }

    @Test
    @DisplayName("更新科目: 表内科目不允许更新为无余额方向(0)")
    void update_onBalance_cannotBeNone() {
        AccountSubjectPO po = new AccountSubjectPO();
        po.setId(100L);
        po.setSubjectCode("1001");
        po.setSubjectName("库存现金");
        po.setSubjectCategory(SubjectCategoryEnum.ASSET);
        po.setDebitCredit(SubjectBalanceDirectionEnum.DEBIT);
        po.setLeaf(true);
        po.setStatus(AvailableStatusEnum.ENABLED);

        when(subjectRepository.selectByCode("1001")).thenReturn(po);

        SubjectUpdateRequest request = new SubjectUpdateRequest();
        request.setDebitCredit(0); // 试图设为无方向

        assertThatThrownBy(() -> subjectApplicationService.update("1001", request))
                .isInstanceOf(ServiceException.class)
                .satisfies(ex -> assertThat(((ServiceException) ex).getResultCode()).isEqualTo(ResultCode.PARAM_ERROR));
    }

    @Test
    @DisplayName("创建科目: 表内科目不允许设置为无余额方向(0)")
    void create_onBalance_cannotBeNone() {
        SubjectCreateRequest request = new SubjectCreateRequest();
        request.setSubjectCode("1002");
        request.setSubjectName("银行存款");
        request.setSubjectCategory(1); // 资产类
        request.setDebitCredit(0);     // 试图设为 0
        request.setSubjectLevel(1);
        request.setParentSubjectId(0L);

        when(subjectRepository.selectByCode("1002")).thenReturn(null);

        assertThatThrownBy(() -> subjectApplicationService.create(request))
                .isInstanceOf(ServiceException.class)
                .satisfies(ex -> assertThat(((ServiceException) ex).getResultCode()).isEqualTo(ResultCode.PARAM_ERROR));
    }

    @Test
    @DisplayName("创建科目: 表外科目(0)自动强制设置为无余额方向(0)")
    void create_offBalance_forceNone() {
        SubjectCreateRequest request = new SubjectCreateRequest();
        request.setSubjectCode("0001");
        request.setSubjectName("重要空白凭证");
        request.setSubjectCategory(0); // 表外
        request.setDebitCredit(1);     // 外部传入 1，应被重置为 0
        request.setSubjectLevel(1);
        request.setParentSubjectId(0L);

        when(subjectRepository.selectByCode("0001")).thenReturn(null);

        subjectApplicationService.create(request);

        ArgumentCaptor<AccountSubjectPO> captor = ArgumentCaptor.forClass(AccountSubjectPO.class);
        verify(subjectRepository).insertSubject(captor.capture());
        AccountSubjectPO po = captor.getValue();
        assertThat(po.getDebitCredit()).isEqualTo(SubjectBalanceDirectionEnum.NONE);
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
