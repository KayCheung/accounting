package com.kltb.accounting.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;

/**
 * 辅助核算账簿多维查询请求 DTO
 */
@Data
@Schema(description = "辅助核算账簿查询请求")
public class AuxiliaryLedgerQueryRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotBlank(message = "辅助核算类别不能为空")
    @Schema(description = "辅助核算类型（字典CODE，如 DEPARTMENT/PROJECT/CUSTOMER/SUPPLIER/EMPLOYEE）", example = "DEPARTMENT", requiredMode = Schema.RequiredMode.REQUIRED)
    private String auxType;

    @Schema(description = "辅助核算项目编码（空或 ALL 表示全部核算项汇总）", example = "DEPT001")
    private String auxCode;

    @Schema(description = "会计期间起始日期", example = "2026-03-01")
    private LocalDate startDate;

    @Schema(description = "会计期间截止日期", example = "2026-03-31")
    private LocalDate endDate;

    @Schema(description = "会计科目编码", example = "6601")
    private String subjectCode;

    @Schema(description = "会计科目账类：1-资产, 2-负债, 3-权益, 4-共同, 5-成本, 6-损益", example = "6")
    private Integer subjectCategory;

    @Schema(description = "搜索关键字（核算项名称/编码、凭证号、科目或摘要模糊匹配）", example = "研发")
    private String keyword;

    @Schema(description = "页码（分页查询凭证流水时生效，默认 1）", example = "1")
    private Integer pageNo = 1;

    @Schema(description = "每页大小（默认 20）", example = "20")
    private Integer pageSize = 20;
}
