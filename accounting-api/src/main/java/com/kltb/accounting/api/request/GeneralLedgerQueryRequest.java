package com.kltb.accounting.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;

@Data
@Schema(description = "科目总账查询请求")
public class GeneralLedgerQueryRequest implements Serializable {

    @NotNull(message = "开始日期不能为空")
    @Schema(description = "会计期间起始日期", example = "2026-01-01")
    private LocalDate startDate;

    @NotNull(message = "结束日期不能为空")
    @Schema(description = "会计期间截止日期", example = "2026-03-31")
    private LocalDate endDate;

    @Schema(description = "起始科目编码", example = "1001")
    private String startSubjectCode;

    @Schema(description = "终止科目编码", example = "9999")
    private String endSubjectCode;

    @Schema(description = "科目级次（1-一级科目, 2-二级科目, null-全部）", example = "1")
    private Integer subjectLevel;

    @Schema(description = "是否显示无发生额且无余额科目（默认 false）", example = "false")
    private Boolean showZeroBalance = false;

    @Schema(description = "科目编码或名称关键字模糊检索")
    private String keyword;
}
