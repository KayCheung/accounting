package com.kltb.accounting.core.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.kltb.accounting.api.constant.ResultCode;
import com.kltb.accounting.core.infrastructure.persistence.entity.EodStatusPO;
import com.kltb.accounting.core.infrastructure.persistence.mapper.EodStatusMapper;
import com.kltb.accounting.core.shared.exception.AccountException;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DuplicateKeyException;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EodStatusRepositoryTest {

    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), EodStatusPO.class);
    }

    @Mock
    private EodStatusMapper eodStatusMapper;

    @InjectMocks
    private EodStatusRepository eodStatusRepository;

    @Test
    @DisplayName("首次发起日切时正常插入状态记录")
    void createStatus_firstTime_insertsNewRecord() {
        LocalDate date = LocalDate.of(2026, 10, 8);
        when(eodStatusMapper.selectOne(any(), eq(false))).thenReturn(null);
        when(eodStatusMapper.insert(any(EodStatusPO.class))).thenAnswer(invocation -> {
            EodStatusPO po = invocation.getArgument(0);
            po.setId(100L);
            return 1;
        });

        EodStatusPO result = eodStatusRepository.createStatus(date);

        assertThat(result).isNotNull();
        assertThat(result.getAccountingDate()).isEqualTo(date);
        assertThat(result.getEodStatus()).isEqualTo(1);
        assertThat(result.getTotalDurationMs()).isEqualTo(0L);
        verify(eodStatusMapper).insert(any(EodStatusPO.class));
    }

    @Test
    @DisplayName("日切失败后再次发起时幂等重置状态，清空失败原因且不抛主键冲突")
    void createStatus_failedRetry_resetsStatusAndClearsFailReason() {
        LocalDate date = LocalDate.of(2026, 10, 8);
        EodStatusPO failedPo = new EodStatusPO();
        failedPo.setId(100L);
        failedPo.setAccountingDate(date);
        failedPo.setEodStatus(9); // 9-失败
        failedPo.setFailedStage("CLEANUP");
        failedPo.setFailReason("Column 'tenant_id' cannot be null");
        failedPo.setTotalDurationMs(1234L);
        failedPo.setArchiveDateTime(LocalDateTime.now());

        when(eodStatusMapper.selectOne(any(), eq(false))).thenReturn(failedPo);
        when(eodStatusMapper.update(isNull(), any())).thenReturn(1);

        EodStatusPO result = eodStatusRepository.createStatus(date);

        assertThat(result).isNotNull();
        assertThat(result.getAccountingDate()).isEqualTo(date);
        assertThat(result.getEodStatus()).isEqualTo(1); // 已重置为1 未开始
        assertThat(result.getFailedStage()).isEqualTo(""); // 已清空
        assertThat(result.getFailReason()).isEqualTo(""); // 已清空
        assertThat(result.getTotalDurationMs()).isEqualTo(0L); // 已重置
        assertThat(result.getArchiveDateTime()).isNull();
        // 验证没有调用 insert，避免 uk_eod_date 冲突
        verify(eodStatusMapper, never()).insert(any(EodStatusPO.class));
        verify(eodStatusMapper).update(isNull(), any());
    }

    @Test
    @DisplayName("日切已完成（状态8）后再次发起时抛出不可重复执行业务异常")
    void createStatus_alreadyCompleted_throwsEodAlreadyExecuted() {
        LocalDate date = LocalDate.of(2026, 10, 8);
        EodStatusPO completedPo = new EodStatusPO();
        completedPo.setId(100L);
        completedPo.setAccountingDate(date);
        completedPo.setEodStatus(8); // 8-完成

        when(eodStatusMapper.selectOne(any(), eq(false))).thenReturn(completedPo);

        assertThatThrownBy(() -> eodStatusRepository.createStatus(date))
                .isInstanceOf(AccountException.class)
                .hasMessageContaining("日切已完成，不可重复执行")
                .satisfies(ex -> assertThat(((AccountException) ex).getResultCode()).isEqualTo(ResultCode.EOD_ALREADY_EXECUTED));

        verify(eodStatusMapper, never()).insert(any(EodStatusPO.class));
        verify(eodStatusMapper, never()).update(any(), any());
    }

    @Test
    @DisplayName("并发插入冲突时兜底重新查询并执行重置")
    void createStatus_duplicateKeyConcurrent_retriesAndResets() {
        LocalDate date = LocalDate.of(2026, 10, 8);
        EodStatusPO failedPo = new EodStatusPO();
        failedPo.setId(100L);
        failedPo.setAccountingDate(date);
        failedPo.setEodStatus(9);
        failedPo.setFailedStage("CLEANUP");
        failedPo.setFailReason("并发失败");

        // 第一次查为 null，后续查返回已有记录
        when(eodStatusMapper.selectOne(any(), eq(false)))
                .thenReturn(null)
                .thenReturn(failedPo);
        when(eodStatusMapper.insert(any(EodStatusPO.class)))
                .thenThrow(new DuplicateKeyException("Duplicate entry '2026-10-08-0' for key 'uk_eod_date'"));
        when(eodStatusMapper.update(isNull(), any())).thenReturn(1);

        EodStatusPO result = eodStatusRepository.createStatus(date);

        assertThat(result).isNotNull();
        assertThat(result.getEodStatus()).isEqualTo(1);
        assertThat(result.getFailReason()).isEqualTo("");
        verify(eodStatusMapper).update(isNull(), any());
    }
}
