package com.kltb.accounting.api.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

/**
 * 批量过账请求 DTO
 */
@Data
public class BatchPostingRequest {

    @NotNull(message = "起始日期不能为空")
    private LocalDate startDate;

    private LocalDate endDate;  // 默认等于 startDate

    @Size(max = 32, message = "业务线编码长度不能超过32")
    private String businessCode;  // null 表示全部

    @Min(value = 1, message = "批次大小最小为1")
    @Max(value = 200, message = "批次大小最大为200")
    private Integer maxBatchSize = 50;  // 默认 50
}
