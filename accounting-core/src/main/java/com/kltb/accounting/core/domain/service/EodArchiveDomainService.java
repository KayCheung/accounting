package com.kltb.accounting.core.domain.service;

import com.kltb.accounting.api.constant.ResultCode;
import com.kltb.accounting.core.infrastructure.persistence.repository.EodStatusRepository;
import com.kltb.accounting.core.shared.exception.AccountException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

/**
 * 日切归档领域服务（Step 17S 新增）
 * <p>
 * 职责：
 * 1. 标记 T 日账务关闭
 * 2. 记录归档完成时间和总耗时
 * 3. 失败时标记失败状态
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EodArchiveDomainService {

    private final EodStatusDomainService eodStatusDomainService;

    /**
     * 执行归档操作
     * <p>
     * 更新 t_eod_status 为状态 8（完成），记录归档时间和总耗时。
     *
     * @param accountingDate 会计日期
     * @param durationMs     总耗时（毫秒）
     * @throws AccountException 当归档失败时抛出
     */
    public void archive(LocalDate accountingDate, long durationMs) {
        try {
            log.info("[EOD-ARCHIVE] 开始归档: date={}, duration={}ms", accountingDate, durationMs);

            eodStatusDomainService.markCompleted(accountingDate, durationMs);

            log.info("[EOD-ARCHIVE] date={}, status=COMPLETED, duration={}ms", accountingDate, durationMs);
        } catch (Exception e) {
            log.error("[EOD-ARCHIVE] 归档失败: date={}, reason={}", accountingDate, e.getMessage(), e);
            eodStatusDomainService.markFailed(accountingDate, "ARCHIVE", e.getMessage());
            throw new AccountException(ResultCode.EOD_ARCHIVE_FAILED,
                    "日切归档失败: date=" + accountingDate + ", reason=" + e.getMessage());
        }
    }
}
