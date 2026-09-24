package com.kltb.accounting.api.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

/**
 * 缓冲记账执行请求 DTO
 */
@Data
public class BufferExecuteRequest {

    @NotNull(message = "会计日期不能为空")
    private LocalDate accountingDate;

    @NotNull(message = "缓冲模式不能为空")
    private Integer bufferMode;

    private String accountNo;

    private Integer maxBatchSize = 50;
}
