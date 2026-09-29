// accounting-api/src/main/java/com/kltb/accounting/api/request/ManualVoucherApplyPageRequest.java
package com.kltb.accounting.api.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 手工记账申请综合分页查询请求
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "手工记账申请综合分页查询请求")
public class ManualVoucherApplyPageRequest extends PageRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "申请单号(支持模糊匹配)", example = "MVA2026")
    private String applyNo;

    @Schema(description = "正式入账凭证号", example = "VOU20260929")
    private String voucherNo;

    @Schema(description = "审批流状态：1-草稿, 2-待初审, 3-初审驳回, 4-待复核, 5-复核驳回, 6-待记账, 7-已记账, 8-已作废", example = "2")
    private Integer applyStatus;

    @Schema(description = "制单人姓名(支持模糊匹配)", example = "张会计")
    private String makerName;

    @Schema(description = "凭证摘要(支持模糊匹配)", example = "调账")
    private String summary;

    @JsonFormat(pattern = "yyyy-MM-dd")
    @Schema(description = "会计日期起始", example = "2026-09-01")
    private LocalDate startDate;

    @JsonFormat(pattern = "yyyy-MM-dd")
    @Schema(description = "会计日期截止", example = "2026-09-30")
    private LocalDate endDate;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "申请创建时间起始", example = "2026-09-01 00:00:00")
    private LocalDateTime createStartTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "申请创建时间截止", example = "2026-09-30 23:59:59")
    private LocalDateTime createEndTime;
}
