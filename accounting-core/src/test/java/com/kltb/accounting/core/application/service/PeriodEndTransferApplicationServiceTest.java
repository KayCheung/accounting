package com.kltb.accounting.core.application.service;

import com.kltb.accounting.api.constant.ResultCode;
import com.kltb.accounting.api.request.TransferRuleCreateRequest;
import com.kltb.accounting.api.request.TransferRuleUpdateRequest;
import com.kltb.accounting.api.response.TransferRuleResponse;
import com.kltb.accounting.core.domain.enums.AvailableStatusEnum;
import com.kltb.accounting.core.domain.enums.TransferDirectionEnum;
import com.kltb.accounting.core.domain.enums.TransferTypeEnum;
import com.kltb.accounting.core.domain.service.PeriodEndTransferDomainService;
import com.kltb.accounting.core.infrastructure.persistence.entity.AccountSubjectPO;
import com.kltb.accounting.core.infrastructure.persistence.entity.PeriodEndTransferRulePO;
import com.kltb.accounting.core.infrastructure.persistence.repository.PeriodEndTransferRecordRepository;
import com.kltb.accounting.core.infrastructure.persistence.repository.PeriodEndTransferRuleRepository;
import com.kltb.accounting.core.infrastructure.persistence.repository.SubjectRepository;
import com.kltb.accounting.core.shared.exception.AccountException;
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
class PeriodEndTransferApplicationServiceTest {

    @Mock
    private PeriodEndTransferRuleRepository ruleRepository;

    @Mock
    private PeriodEndTransferRecordRepository recordRepository;

    @Mock
    private PeriodEndTransferDomainService transferDomainService;

    @Mock
    private SubjectRepository subjectRepository;

    @InjectMocks
    private PeriodEndTransferApplicationService applicationService;

    @Test
    @DisplayName("创建结转规则：编码不重复且目标科目为末级科目时创建成功")
    void createRule_success() {
        TransferRuleCreateRequest req = new TransferRuleCreateRequest();
        req.setRuleCode("TR_REV_PROFIT");
        req.setRuleName("主营业务收入结转");
        req.setTransferType(1);
        req.setSourceSubjectCode("60*");
        req.setTargetSubjectCode("410301");
        req.setTransferDirection(2);
        req.setSummaryTemplate("{year}年{month}月损益结转");
        req.setExecuteOrder(10);

        when(ruleRepository.existsByRuleCode("TR_REV_PROFIT", null)).thenReturn(false);

        AccountSubjectPO leafSubject = new AccountSubjectPO();
        leafSubject.setSubjectCode("410301");
        leafSubject.setSubjectName("本年利润");
        leafSubject.setLeaf(true); // 末级科目
        when(subjectRepository.selectByCode("410301")).thenReturn(leafSubject);

        doAnswer(inv -> {
            PeriodEndTransferRulePO po = inv.getArgument(0);
            po.setId(99L);
            return null;
        }).when(ruleRepository).insert(any());

        Long ruleId = applicationService.createRule(req);

        assertThat(ruleId).isEqualTo(99L);
        verify(ruleRepository).insert(any());
    }

    @Test
    @DisplayName("创建结转规则：规则编码已存在时抛出异常")
    void createRule_duplicateCode_throwsException() {
        TransferRuleCreateRequest req = new TransferRuleCreateRequest();
        req.setRuleCode("TR_REV_PROFIT");

        when(ruleRepository.existsByRuleCode("TR_REV_PROFIT", null)).thenReturn(true);

        assertThatThrownBy(() -> applicationService.createRule(req))
                .isInstanceOf(AccountException.class)
                .satisfies(ex -> assertThat(((AccountException) ex).getResultCode()).isEqualTo(ResultCode.TRANSFER_RULE_CODE_EXISTS));

        verify(ruleRepository, never()).insert(any());
    }

    @Test
    @DisplayName("创建结转规则：目标科目非末级科目时抛出异常")
    void createRule_targetSubjectNotLeaf_throwsException() {
        TransferRuleCreateRequest req = new TransferRuleCreateRequest();
        req.setRuleCode("TR_REV_PROFIT");
        req.setRuleName("测试");
        req.setTransferType(1);
        req.setSourceSubjectCode("60*");
        req.setTargetSubjectCode("4103"); // 一级或二级非末级科目
        req.setTransferDirection(2);

        when(ruleRepository.existsByRuleCode("TR_REV_PROFIT", null)).thenReturn(false);

        AccountSubjectPO nonLeafSubject = new AccountSubjectPO();
        nonLeafSubject.setSubjectCode("4103");
        nonLeafSubject.setLeaf(false); // 非末级科目
        when(subjectRepository.selectByCode("4103")).thenReturn(nonLeafSubject);

        assertThatThrownBy(() -> applicationService.createRule(req))
                .isInstanceOf(AccountException.class)
                .satisfies(ex -> assertThat(((AccountException) ex).getResultCode()).isEqualTo(ResultCode.SUBJECT_NOT_LEAF));

        verify(ruleRepository, never()).insert(any());
    }

    @Test
    @DisplayName("切换规则状态成功")
    void updateRuleStatus_success() {
        PeriodEndTransferRulePO po = new PeriodEndTransferRulePO();
        po.setId(10L);
        po.setRuleCode("TR_01");
        po.setStatus(AvailableStatusEnum.ENABLED);

        when(ruleRepository.findById(10L)).thenReturn(po);

        applicationService.updateRuleStatus(10L, 2); // 切换为停用

        verify(ruleRepository).updateStatus(10L, AvailableStatusEnum.DISABLED);
    }
}
