// accounting-api/src/main/java/com/kltb/accounting/api/response/ManualVoucherApplyPageItemResponse.java
package com.kltb.accounting.api.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 手工记账申请综合分页列表项响应 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "手工记账申请综合分页列表项响应")
public class ManualVoucherApplyPageItemResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "主键ID")
    private Long id;

    @Schema(description = "申请单号", example = "MVA202609290001")
    private String applyNo;

    @Schema(description = "凭证类型", example = "记账凭证")
    private String voucherType;

    @Schema(description = "交易类别：1-正常, 2-调账", example = "2")
    private Integer tradeType;

    @Schema(description = "交易类别描述", example = "调账")
    private String tradeTypeDesc;

    @Schema(description = "会计日期")
    private LocalDate accountingDate;

    @Schema(description = "凭证摘要", example = "9月末手工调账")
    private String summary;

    @Schema(description = "借方合计金额", example = "1000.00")
    private BigDecimal totalDebitAmount;

    @Schema(description = "贷方合计金额", example = "1000.00")
    private BigDecimal totalCreditAmount;

    @Schema(description = "借贷平衡状态：true-严格平衡, false-不平衡")
    private Boolean isBalanced;

    @Schema(description = "审批流状态码：1-草稿, 2-待初审, 3-初审驳回, 4-待复核, 5-复核驳回, 6-待记账, 7-已记账, 8-已作废")
    private Integer applyStatus;

    @Schema(description = "审批流状态描述", example = "待初审")
    private String applyStatusDesc;

    @Schema(description = "制单人姓名", example = "张会计")
    private String makerName;

    @Schema(description = "初审人姓名", example = "李主管")
    private String auditorName;

    @Schema(description = "初审时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime auditTime;

    @Schema(description = "初审意见/驳回原因")
    private String auditOpinion;

    @Schema(description = "复核人姓名", example = "赵经理")
    private String reviewerName;

    @Schema(description = "复核时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime reviewTime;

    @Schema(description = "复核意见/驳回原因")
    private String reviewOpinion;

    @Schema(description = "记账人姓名", example = "王出纳")
    private String bookkeeperName;

    @Schema(description = "记账时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime bookkeepingTime;

    @Schema(description = "正式入账凭证号(已记账后回填)", example = "VOU20260929000001")
    private String voucherNo;

    @Schema(description = "申请创建时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;

    @Schema(description = "分录明细列表(主表格展开行即时预览)")
    private List<ApplyEntryItemDTO> entries;

    /**
     * 申请分录明细 DTO
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "申请分录明细 DTO")
    public static class ApplyEntryItemDTO implements Serializable {
        private static final long serialVersionUID = 1L;

        private Long id;
        private String applyNo;
        private Integer rowNum;
        private Integer debitCredit;
        private String debitCreditDesc;
        private String subjectCode;
        private String subjectName;
        private String accountNo;
        private BigDecimal amount;
        private String currency;
        private String summary;
        private Integer unilateral;
    }
}
