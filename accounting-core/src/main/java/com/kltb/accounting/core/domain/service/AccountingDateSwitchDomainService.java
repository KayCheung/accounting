package com.kltb.accounting.core.domain.service;

import com.kltb.accounting.api.constant.ResultCode;
import com.kltb.accounting.core.infrastructure.cache.AccountingDateCache;
import com.kltb.accounting.core.infrastructure.persistence.entity.EodStatusPO;
import com.kltb.accounting.core.infrastructure.persistence.repository.EodStatusRepository;
import com.kltb.accounting.core.shared.exception.AccountException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 瞬间切日领域服务（Step 17S Phase 2 新增）
 * <p>
 * 职责：
 * 1. 计算新会计日期（T+1）
 * 2. 幂等检查（同目标日期不重复切日）
 * 3. 更新 Redis 缓存
 * 4. 写入 t_eod_status 新记录
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountingDateSwitchDomainService {

    private final AccountingDateCache accountingDateCache;
    private final EodStatusRepository eodStatusRepository;
    private final EodStatusDomainService eodStatusDomainService;

    /**
     * 执行瞬间切日
     * <p>
     * 从当前会计日期切换到下一天，更新缓存并创建新的日切状态记录。
     *
     * @param targetDate 目标会计日期（可选，不传则自动+1天）
     * @return 切日结果（旧日期 → 新日期）
     */
    public DateSwitchResult switchDate(LocalDate targetDate) {
        LocalDate currentDate = accountingDateCache.getCurrentDate();
        LocalDate newDate = targetDate != null ? targetDate : currentDate.plusDays(1);

        // 幂等检查：若系统当前日期已经是目标日期或晚于目标日期，跳过
        if (currentDate.isEqual(newDate) || currentDate.isAfter(newDate)) {
            log.info("[DATE-SWITCH] 当前全局会计日期已达目标日期，跳过切日: current={}, target={}", currentDate, newDate);
            return new DateSwitchResult(currentDate, newDate, true);
        }

        try {
            log.info("[DATE-SWITCH] 开始切日: oldDate={}, newDate={}", currentDate, newDate);
            LocalDateTime switchTime = LocalDateTime.now();

            // 1. 更新原会计日（T日）的切日完成时间
            if (currentDate != null) {
                eodStatusDomainService.updateSwitchDateTime(currentDate, switchTime);
            }

            // 2. 初始化新会计日（T+1日）状态为 1-未开始(正常营业中)
            EodStatusPO existing = eodStatusRepository.findByDate(newDate);
            if (existing == null) {
                eodStatusDomainService.createStatus(newDate);
            } else if (existing.isCompleted()) {
                // 若历史数据曾误标记为已完成，重置为未开始/营业中
                eodStatusRepository.resetStatus(newDate);
            }

            // 3. 更新 Redis + Caffeine 缓存及通知
            accountingDateCache.setCurrentDate(newDate);

            log.info("[DATE-SWITCH] 切日完成: oldDate={}, newDate={}", currentDate, newDate);
            return new DateSwitchResult(currentDate, newDate, false, switchTime);

        } catch (Exception e) {
            log.error("[DATE-SWITCH] 切日失败: oldDate={}, newDate={}, reason={}",
                    currentDate, newDate, e.getMessage(), e);
            throw new AccountException(ResultCode.DATE_SWITCH_FAILED,
                    "全局会计日期切换失败: " + e.getMessage());
        }
    }

    /**
     * 切日结果
     */
    public static class DateSwitchResult {
        private final LocalDate previousDate;
        private final LocalDate newDate;
        private final boolean alreadySwitched;
        private final LocalDateTime switchedAt;

        public DateSwitchResult(LocalDate previousDate, LocalDate newDate, boolean alreadySwitched) {
            this(previousDate, newDate, alreadySwitched, null);
        }

        public DateSwitchResult(LocalDate previousDate, LocalDate newDate, boolean alreadySwitched,
                                LocalDateTime switchedAt) {
            this.previousDate = previousDate;
            this.newDate = newDate;
            this.alreadySwitched = alreadySwitched;
            this.switchedAt = switchedAt;
        }

        public LocalDate getPreviousDate() { return previousDate; }
        public LocalDate getNewDate() { return newDate; }
        public boolean isAlreadySwitched() { return alreadySwitched; }
        public LocalDateTime getSwitchedAt() { return switchedAt; }
    }
}
