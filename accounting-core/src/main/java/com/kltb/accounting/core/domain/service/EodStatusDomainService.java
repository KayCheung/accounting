package com.kltb.accounting.core.domain.service;

import com.kltb.accounting.core.infrastructure.persistence.entity.EodStatusPO;
import com.kltb.accounting.core.infrastructure.persistence.repository.EodStatusRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 日切状态追踪领域服务（Step 17S 新增）
 * <p>
 * 职责：
 * 1. 创建日切状态记录
 * 2. 更新各阶段状态
 * 3. 标记完成/失败
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EodStatusDomainService {

    private final EodStatusRepository eodStatusRepository;

    /**
     * 创建日切状态记录（初始状态=1 未开始）
     *
     * @param accountingDate 会计日期
     * @return 新创建的PO
     */
    public EodStatusPO createStatus(LocalDate accountingDate) {
        EodStatusPO po = eodStatusRepository.createStatus(accountingDate);
        log.info("[EOD-STATUS] 创建日切状态: date={}, id={}", accountingDate, po.getId());
        return po;
    }

    /**
     * 更新日切阶段状态
     *
     * @param status 状态码（2~7）
     * @param accountingDate 会计日期
     */
    public void updateStage(int status, LocalDate accountingDate) {
        eodStatusRepository.updateStatus(accountingDate, status);
        log.debug("[EOD-STATUS] 阶段更新: date={}, status={}", accountingDate, status);
    }

    /**
     * 标记日切完成
     *
     * @param accountingDate 会计日期
     * @param durationMs 总耗时（毫秒）
     */
    public void markCompleted(LocalDate accountingDate, long durationMs) {
        eodStatusRepository.markCompleted(accountingDate, durationMs);
        log.info("[EOD-STATUS] 日切完成: date={}, duration={}ms", accountingDate, durationMs);
    }

    /**
     * 标记日切失败
     *
     * @param accountingDate 会计日期
     * @param stage 失败阶段标识（如 CLEANUP, TRIAL_BALANCE, GL_RECONCILIATION 等）
     * @param reason 失败原因
     */
    public void markFailed(LocalDate accountingDate, String stage, String reason) {
        eodStatusRepository.markFailed(accountingDate, stage, truncate(reason, 255));
        log.error("[EOD-STATUS] 日切失败: date={}, stage={}, reason={}", accountingDate, stage, reason);
    }

    /**
     * 按会计日期查询日切状态
     */
    public EodStatusPO findByDate(LocalDate accountingDate) {
        return eodStatusRepository.findByDate(accountingDate);
    }

    /**
     * 查询最近一条已完成的日切记录
     */
    public EodStatusPO findLatestCompleted() {
        return eodStatusRepository.findLatestCompleted();
    }

    /**
     * 更新切日完成时间
     */
    public void updateSwitchDateTime(LocalDate accountingDate, LocalDateTime switchDateTime) {
        eodStatusRepository.updateSwitchDateTime(accountingDate, switchDateTime);
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen);
    }
}
