package com.kltb.accounting.api.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 记账凭证全景档案响应 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "记账凭证全景档案响应")
public class VoucherFullDetailResponse implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "主键ID", example = "1001")
    private Long id;

    @Schema(description = "凭证号", example = "VOU20260920000001")
    private String voucherNo;

    @Schema(description = "事务编号", example = "TXN202609200001")
    private String txnNo;

    @Schema(description = "系统跟踪号", example = "TRC202609200001")
    private String traceNo;

    @Schema(description = "跟踪序号", example = "1")
    private Integer traceSeq;

    @Schema(description = "凭证类型", example = "GENERAL")
    private String voucherType;

    @Schema(description = "凭证类型名称", example = "通用凭证")
    private String voucherTypeName;

    @Schema(description = "入账类型：1-手工凭证, 2-机制凭证", example = "2")
    private Integer postingType;

    @Schema(description = "入账类型描述", example = "机制凭证")
    private String postingTypeDesc;

    @Schema(description = "业务线编码", example = "MHD")
    private String businessCode;

    @Schema(description = "交易编码", example = "LOAN_DISBURSE")
    private String tradingCode;

    @Schema(description = "支付渠道", example = "ALIPAY")
    private String payChannel;

    @Schema(description = "交易类别：1-正常, 2-调账, 3-红, 4-蓝", example = "1")
    private Integer tradeType;

    @Schema(description = "交易类别描述", example = "正常")
    private String tradeTypeDesc;

    @Schema(description = "交易时间")
    private LocalDateTime tradeTime;

    @Schema(description = "凭证金额", example = "10000.00")
    private BigDecimal amount;

    @Schema(description = "凭证状态：1-未过账, 2-过账中, 3-已过账, 4-过账失败, 5-已冲销", example = "3")
    private Integer status;

    @Schema(description = "凭证状态描述", example = "已过账")
    private String statusDesc;

    @Schema(description = "过账时间")
    private LocalDateTime postTime;

    @Schema(description = "会计日期", example = "2026-09-20")
    private LocalDate accountingDate;

    @Schema(description = "摘要", example = "放款本金入账")
    private String summary;

    @Schema(description = "附件数", example = "0")
    private Integer attachmentCount;

    @Schema(description = "原凭证号（红冲凭证关联原凭证）", example = "VOU20260920000000")
    private String origVoucherNo;

    @Schema(description = "红冲凭证号（若当前凭证已被红冲）", example = "VOU20260920000099")
    private String reversalVoucherNo;

    @Schema(description = "是否可执行红冲", example = "true")
    private Boolean canReversal;

    @Schema(description = "记账人姓名", example = "系统自动")
    private String bookkeeperName;

    @Schema(description = "复核人姓名", example = "李主管")
    private String reviewerName;

    @Schema(description = "过账失败原因", example = "")
    private String failReason;

    @Schema(description = "重试次数", example = "0")
    private Integer retryCount;

    @Schema(description = "人工跳过标记：0-未跳过, 1-已跳过", example = "0")
    private Integer skipFlag;

    @Schema(description = "制单时间/创建时间")
    private LocalDateTime createTime;

    @Schema(description = "借贷分录列表")
    private List<VoucherEntryResponse> entries;

    @Schema(description = "辅助核算项列表")
    private List<VoucherAuxiliaryResponse> auxiliaries;

    @Schema(description = "凭证附件列表")
    private List<VoucherAttachmentResponse> attachments;

    @Schema(description = "借方分录数", example = "1")
    private Integer debitCount;

    @Schema(description = "贷方分录数", example = "1")
    private Integer creditCount;

    @Schema(description = "借方合计金额", example = "10000.00")
    private BigDecimal debitAmount;

    @Schema(description = "贷方合计金额", example = "10000.00")
    private BigDecimal creditAmount;

    @Schema(description = "借贷是否平衡", example = "true")
    private Boolean isBalanced;
}
