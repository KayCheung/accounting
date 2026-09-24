package com.kltb.accounting.api.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

@Data
public class EodExecuteRequest {

    @NotNull(message = "会计日期不能为空")
    private LocalDate accountingDate;

    private boolean skipPreCheck = false;

    private boolean executeTransfer = true;
}
