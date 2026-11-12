package com.kltb.accounting.api.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * 总账与分户账核对一键修复请求 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "总分核对一键修复请求")
public class GlRepairRequest {

    @NotNull(message = "会计日期不能为空")
    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    @Schema(description = "会计日期", example = "2026-11-09", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDate accountingDate;

    @Schema(description = "指定待修复的科目代码（可选，不传则对当日所有不平科目进行自动诊断修复）", example = "12010101")
    private String subjectCode;
}
