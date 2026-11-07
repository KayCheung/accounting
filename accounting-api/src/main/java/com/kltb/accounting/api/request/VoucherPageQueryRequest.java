package com.kltb.accounting.api.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 记账凭证综合分页查询请求
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "记账凭证综合分页查询请求")
public class VoucherPageQueryRequest extends PageRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "凭证编号（支持模糊）", example = "VOU20260920000001")
    private String voucherNo;

    @Schema(description = "系统跟踪号", example = "TRC202609200001")
    private String traceNo;

    @Schema(description = "全局事务号", example = "TXN202609200001")
    private String txnNo;

    @Schema(description = "凭证状态：1-未过账, 2-过账中, 3-已过账, 4-过账失败, 5-已冲销", example = "3")
    private Integer status;

    @Schema(description = "凭证类型", example = "GENERAL")
    private String voucherType;

    @Schema(description = "入账类型：1-手工凭证, 2-机制凭证", example = "2")
    private Integer postingType;

    @Schema(description = "业务线编码", example = "MHD")
    private String businessCode;

    @Schema(description = "交易编码", example = "LOAN_DISBURSE")
    private String tradingCode;

    @Schema(description = "支付渠道", example = "ALIPAY")
    private String payChannel;

    @Schema(description = "交易类别：1-正常, 2-调账, 3-红, 4-蓝", example = "1")
    private Integer tradeType;

    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    @Schema(description = "会计日期起始", example = "2026-09-01")
    private LocalDate startDate;

    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    @Schema(description = "会计日期截止", example = "2026-09-30")
    private LocalDate endDate;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @Schema(description = "制单时间起始", example = "2026-09-01 00:00:00")
    private LocalDateTime createStartTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @Schema(description = "制单时间截止", example = "2026-09-30 23:59:59")
    private LocalDateTime createEndTime;
}
