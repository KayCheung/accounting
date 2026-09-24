package com.kltb.accounting.job.job;

import com.kltb.accounting.core.domain.enums.FreezeStatusEnum;
import com.kltb.accounting.core.domain.service.FreezeDomainService;
import com.kltb.accounting.core.infrastructure.persistence.entity.AccountFreezeDetailPO;
import com.kltb.accounting.core.infrastructure.persistence.repository.FreezeDetailRepository;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 超时自动解冻定时任务（XXL-JOB）
 * <p>
 * 调度配置：每 5 分钟执行一次
 * 路由策略：FIRST（单实例执行）
 * <p>
 * 执行逻辑：
 * 1. 查询 expire_time &lt;= NOW() 且 status=1 的冻结记录
 * 2. 逐笔执行自动解冻（独立事务，单笔失败不中断）
 * 3. 记录成功/失败统计
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AutoUnfreezeJobHandler extends AbstractXxlJobHandler {

    private final FreezeDetailRepository freezeDetailRepository;
    private final FreezeDomainService freezeDomainService;

    @Override
    protected String jobName() {
        return "AUTO-UNFREEZE-JOB";
    }

    @XxlJob("autoUnfreezeJob")
    public void execute() {
        initContext();

        logStart("");

        LocalDateTime now = LocalDateTime.now();
        List<AccountFreezeDetailPO> expiredRecords = freezeDetailRepository.selectExpiredRecords(now);

        if (expiredRecords.isEmpty()) {
            log.info("[{}] 无过期冻结记录", jobName());
            return;
        }

        ctx().totalCount(expiredRecords.size());
        log.info("[{}] 发现过期记录: count={}", jobName(), expiredRecords.size());

        for (AccountFreezeDetailPO record : expiredRecords) {
            try {
                if (record.getStatus() != FreezeStatusEnum.FROZEN) {
                    log.warn("[{}] 跳过非冻结状态记录: freezeId={}, status={}",
                            jobName(), record.getVoucherNo(), record.getStatus());
                    continue;
                }

                freezeDomainService.autoUnfreezeOne(record);
                ctx().success();
            } catch (Exception e) {
                ctx().fail(record.getVoucherNo() + "(" + e.getMessage() + ")");
                log.error("[{}] 自动解冻失败: freezeId={} error={}",
                        jobName(), record.getVoucherNo(), e.getMessage(), e);
            }
        }

        logComplete("total=" + ctx().totalCount + ", success=" + ctx().successCount
                + ", failed=" + ctx().failedCount);
        logFailedDetails();
    }
}
