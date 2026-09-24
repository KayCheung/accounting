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

        // 幂等检查：目标日期是否已存在日切记录
        EodStatusPO existing = eodStatusRepository.findByDate(newDate);
        if (existing != null && existing.getEodStatus() >= 2) {
            log.info("[DATE-SWITCH] 目标日期已有日切记录，跳过: date={}", newDate);
            return new DateSwitchResult(currentDate, newDate, true);
        }

        try {
            log.info("[DATE-SWITCH] 开始切日: oldDate={}, newDate={}", currentDate, newDate);

            // 1. 写入 t_eod_status 新记录（状态=2 切日中）
            eodStatusDomainService.createStatus(newDate);
            eodStatusDomainService.updateStage(2, newDate);

            // 2. 更新切日完成时间（持久化到 DB）
            LocalDateTime switchTime = LocalDateTime.now();
            eodStatusDomainService.updateSwitchDateTime(newDate, switchTime);

            // 3. 更新 Redis + Caffeine 缓存
            accountingDateCache.setCurrentDate(newDate);

            // 4. 标记切日完成
            eodStatusDomainService.updateStage(8, newDate);

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
