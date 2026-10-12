package com.kltb.accounting.api.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

@Data
public class EodExecuteRequest {

    @NotNull(message = "会计日期不能为空")
    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    private LocalDate accountingDate;

    private boolean skipPreCheck = false;

    private boolean executeTransfer = true;
}
