package com.kltb.accounting.job.job;

import com.xxl.job.core.context.XxlJobHelper;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/**
 * XXL-JOB 抽象基类，封装所有 Job 的公共逻辑
 * <p>
 * 提供能力：
 * <ul>
 *   <li>统一的生命周期管理：计时 / 开始结束日志 / 失败时 handleFail</li>
 *   <li>参数解析：任务参数 → LocalDate（子类可指定默认日期策略）</li>
 *   <li>分片上下文：自动读取并归一化 shardIndex / shardTotal</li>
 *   <li>结果追踪：success/failed 计数与失败明细汇总</li>
 *   <li>线程安全：context 存储在 ThreadLocal，单例 Bean 无并发问题</li>
 * </ul>
 * <p>
 * 子类在 execute() 开头调用 {@link #initContext(String)} 初始化上下文，
 * 后续通过 {@link #ctx()} 获取。
 */
@Slf4j
public abstract class AbstractXxlJobHandler {

    /**
     * 每个 Handler 实例一个 ThreadLocal，保证并发安全
     */
    private final ThreadLocal<JobContext> contextHolder = new ThreadLocal<>();

    /**
     * 任务执行上下文（由基类自动填充，子类只读使用）
     */
    protected static class JobContext {
        /** 任务名称 */
        public final String jobName;
        /** XXL-JOB 原始参数 */
        public final String param;
        /** 分片总数，归一化后 >= 1 */
        public final int shardTotal;
        /** 分片索引，归一化后在 [0, shardTotal) 范围内 */
        public final int shardIndex;
        /** 执行开始时间戳（毫秒） */
        public final long startTime;
        /** 会计日期（由 parseAccountingDate 解析） */
        public LocalDate accountingDate;
        /** 成功笔数 */
        public int successCount;
        /** 失败笔数 */
        public int failedCount;
        /** 总笔数（可选） */
        public int totalCount;
        /** 失败明细列表 */
        private final java.util.List<String> failedDetails = new java.util.ArrayList<>();

        protected JobContext(String jobName) {
            this.jobName = jobName;
            this.param = XxlJobHelper.getJobParam();
            this.shardTotal = Math.max(1, XxlJobHelper.getShardTotal());
            this.shardIndex = normalizeShardIndex(XxlJobHelper.getShardIndex(), this.shardTotal);
            this.startTime = System.currentTimeMillis();
        }

        private static int normalizeShardIndex(int shardIndex, int shardTotal) {
            return (shardIndex < 0 || shardIndex >= shardTotal) ? 0 : shardIndex;
        }

        /** 记录一条成功 */
        public void success() {
            successCount++;
        }

        /** 记录一条失败（附带原因） */
        public void fail(String detail) {
            failedCount++;
            if (detail != null && !detail.isEmpty()) {
                failedDetails.add(detail);
            }
        }

        /** 耗时（毫秒） */
        public long duration() {
            return System.currentTimeMillis() - startTime;
        }

        /** 失败明细摘要 */
        public String failedSummary() {
            return String.join(", ", failedDetails);
        }

        /** 设置会计日期（链式） */
        public JobContext accountingDate(LocalDate date) {
            this.accountingDate = date;
            return this;
        }

        /** 设置总数（链式） */
        public JobContext totalCount(int count) {
            this.totalCount = count;
            return this;
        }

        /** 设置成功数（链式） */
        public JobContext successCount(int count) {
            this.successCount = count;
            return this;
        }

        /** 设置失败数（链式） */
        public JobContext failedCount(int count) {
            this.failedCount = count;
            return this;
        }
    }

    /**
     * 获取当前线程的任务上下文
     */
    protected final JobContext ctx() {
        return contextHolder.get();
    }

    /**
     * 任务名称，用于日志前缀（如 "BUFFER-POSTING-ASYNC-JOB"）
     */
    protected abstract String jobName();

    /**
     * 初始化上下文并放入 ThreadLocal（子类在 execute() 开头调用）
     *
     * @return 初始化后的 JobContext
     */
    protected final JobContext initContext() {
        JobContext ctx = new JobContext(jobName());
        contextHolder.set(ctx);
        return ctx;
    }

    /**
     * 解析会计日期
     *
     * @param param        任务参数
     * @param defaultDate  默认日期（解析失败或参数为空时返回）
     * @return 会计日期
     */
    protected LocalDate parseAccountingDate(String param, LocalDate defaultDate) {
        if (param != null && !param.trim().isEmpty()) {
            try {
                return LocalDate.parse(param.trim());
            } catch (DateTimeParseException e) {
                log.error("[{}] 参数解析失败: param={}, 使用默认日期", jobName(), param);
            }
        }
        return defaultDate;
    }

    /**
     * 打印开始日志
     */
    protected void logStart(String detail) {
        log.info("[{}] 开始执行: {}", jobName(), detail);
    }

    /**
     * 打印完成日志（基类自动拼接 duration）
     */
    protected void logComplete(String detail) {
        log.info("[{}] 执行完成: {} duration={}ms", jobName(), detail, ctx().duration());
    }

    /**
     * 标记任务失败（调用 XxlJobHelper.handleFail）
     */
    protected void markFailed(String reason) {
        XxlJobHelper.handleFail(reason);
    }

    /**
     * 标记任务成功（调用 XxlJobHelper.handleSuccess）
     */
    protected void markSuccess() {
        XxlJobHelper.handleSuccess();
    }

    /**
     * 记录失败明细日志（failedCount > 0 时自动输出 WARN）
     */
    protected void logFailedDetails() {
        if (ctx().failedCount > 0) {
            log.warn("[{}] 失败明细: {}", jobName(), ctx().failedSummary());
        }
    }
}
