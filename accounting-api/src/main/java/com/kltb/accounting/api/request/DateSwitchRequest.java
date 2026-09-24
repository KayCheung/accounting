package com.kltb.accounting.api.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

/**
 * 手动切日请求（Step 17S Phase 2 新增）
 */
@Data
public class DateSwitchRequest {

    /** 目标会计日期（不传则自动+1天） */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate targetDate;
}
