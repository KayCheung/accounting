// accounting-api/src/main/java/com/kltb/accounting/api/response/ManualVoucherApplyDetailResponse.java
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
 * 手工记账申请全景档案与凭证预览响应 DTO
 * 参考原型《凭证详情.html》的经典财务凭证布局
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "手工记账申请全景档案与凭证预览响应")
public class ManualVoucherApplyDetailResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "申请单号", example = "MVA202609290001")
    private String applyNo;

    @Schema(description = "正式入账凭证号(已记账后回填)", example = "VOU20260929000001")
    private String voucherNo;

    @Schema(description = "凭证字号/类型", example = "记账凭证")
    private String voucherType;

    @Schema(description = "经典财务凭证字号(如：转 20260930000001)", example = "转 20260930000001")
    private String voucherWord;

    @Schema(description = "凭证排版大标题(如：转账凭证/收款凭证/记账凭证)", example = "转账凭证")
    private String voucherTitle;

    @Schema(description = "入账类型：1-手工凭证, 2-机制凭证", example = "1")
    private Integer postingType;

    @Schema(description = "入账类型描述", example = "手工凭证")
    private String postingTypeDesc;

    @Schema(description = "交易类别：1-正常, 2-调账", example = "2")
    private Integer tradeType;

    @Schema(description = "交易类别描述", example = "调账")
    private String tradeTypeDesc;

    @Schema(description = "会计日期")
    private LocalDate accountingDate;

    @Schema(description = "凭证摘要", example = "9月末手工调账分录")
    private String summary;

    @Schema(description = "附件数", example = "1")
    private Integer attachmentCount;

    @Schema(description = "借方合计金额", example = "1156.75")
    private BigDecimal totalDebitAmount;

    @Schema(description = "贷方合计金额", example = "1156.75")
    private BigDecimal totalCreditAmount;

    @Schema(description = "大写合计金额", example = "壹仟壹佰伍拾陆元柒角伍分")
    private String totalAmountInWords;

    @Schema(description = "借贷平衡状态：true-平衡, false-不平衡")
    private Boolean isBalanced;

    @Schema(description = "审批状态码：1-草稿, 2-待初审, 3-初审驳回, 4-待复核, 5-复核驳回, 6-待记账, 7-已记账, 8-已作废")
    private Integer applyStatus;

    @Schema(description = "审批状态描述", example = "待初审")
    private String applyStatusDesc;

    // ==================== 经典财务凭证四方签章栏 (参考《凭证详情.html》) ====================

    @Schema(description = "制单人姓名", example = "张会计")
    private String makerName;

    @Schema(description = "审核/初审人姓名", example = "李主管")
    private String auditorName;

    @Schema(description = "初审时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime auditTime;

    @Schema(description = "初审意见/驳回原因")
    private String auditOpinion;

    @Schema(description = "会计主管/复核人姓名", example = "赵经理")
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

    @Schema(description = "创建时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;

    // ==================== 借贷分录明细 ====================

    @Schema(description = "分录明细列表")
    private List<ManualVoucherApplyPageItemResponse.ApplyEntryItemDTO> entries;

    // ==================== 全量流转可追溯审计时间轴 ====================

    @Schema(description = "全量流转可追溯审计日志时间轴")
    private List<ManualVoucherAuditLogResponse> auditLogs;

    // ==================== 辅助核算项明细 (参考《凭证详情.html》) ====================

    @Schema(description = "辅助核算项分摊明细列表")
    private List<ManualVoucherApplyAuxiliaryResponse> auxiliaries;

    // ==================== 凭证原始附件 (参考《凭证附件.html》) ====================

    @Schema(description = "凭证原始单据附件列表")
    private List<ManualVoucherApplyAttachmentResponse> attachments;
}
