package com.kltb.accounting.api.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 系统概览大盘与待办数据响应 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "系统概览大盘与待办数据响应")
public class DashboardOverviewResponse implements Serializable {

    @Schema(description = "全局会计日期（yyyy-MM-dd）", example = "2026-10-20")
    private String accountingDate;

    @Schema(description = "日切营业状态码：1-未开始(正常营业), 8-已完成(正常营业), 9-失败, 其他-日切中", example = "1")
    private Integer eodStatus;

    @Schema(description = "日切营业状态描述", example = "正常营业")
    private String eodStatusDesc;

    @Schema(description = "今日试算是否平衡", example = "true")
    private Boolean trialBalanced;

    @Schema(description = "试算平衡借贷差额（绝对值）", example = "0.000000")
    private BigDecimal trialBalanceDiff;

    @Schema(description = "试算借方发生额合计", example = "0.000000")
    private BigDecimal trialDebitTotal;

    @Schema(description = "试算贷方发生额合计", example = "0.000000")
    private BigDecimal trialCreditTotal;

    // ==================== 待办事项统计（面向 MAKER / AUDITOR / REVIEWER / BOOKKEEPER） ====================

    @Schema(description = "手工凭证-草稿箱笔数", example = "2")
    private Long makerDraftCount;

    @Schema(description = "手工凭证-被驳回待修改笔数", example = "1")
    private Long makerRejectedCount;

    @Schema(description = "手工凭证-待初审把关笔数", example = "5")
    private Long pendingAuditCount;

    @Schema(description = "手工凭证-待终审复核笔数", example = "3")
    private Long pendingReviewCount;

    @Schema(description = "手工凭证-待确认记账笔数", example = "4")
    private Long pendingBookkeepingCount;

    // ==================== 异常治理待办（面向 OPERATOR / ADMIN） ====================

    @Schema(description = "过账失败异常凭证笔数", example = "0")
    private Long failedPostingCount;

    @Schema(description = "挂起/掉单待补偿流水笔数（PROCESSING）", example = "0")
    private Long hangingJournalCount;

    @Schema(description = "缓冲记账待入账存量明细笔数", example = "0")
    private Long pendingBufferCount;

    @Schema(description = "资金冻结中工单数", example = "12")
    private Long activeFreezeCount;

    // ==================== 资产与业务规模指标（面向 ADMIN / OPERATOR） ====================

    @Schema(description = "今日生成凭证总笔数", example = "128")
    private Long todayVoucherCount;

    @Schema(description = "今日已成功过账凭证笔数", example = "128")
    private Long todayPostedVoucherCount;

    @Schema(description = "今日记账业务流水总笔数", example = "86")
    private Long todayJournalCount;

    @Schema(description = "正常有效分户账户总数", example = "542")
    private Long activeAccountCount;
}
