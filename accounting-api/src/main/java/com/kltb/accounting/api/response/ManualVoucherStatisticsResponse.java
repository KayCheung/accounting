package com.kltb.accounting.api.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 手工记账看板状态统计响应 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ManualVoucherStatisticsResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 全部申请单总数
     */
    private Long total;

    /**
     * 待初审数量
     */
    private Long pendingAudit;

    /**
     * 待复核数量
     */
    private Long pendingReview;

    /**
     * 待记账数量
     */
    private Long pendingBookkeeping;

    /**
     * 已记账数量
     */
    private Long booked;

    /**
     * 已驳回数量（含初审驳回与复核驳回）
     */
    private Long rejected;
}
