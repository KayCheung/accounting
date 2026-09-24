package com.kltb.accounting.api.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 手动切日响应（Step 17S Phase 2 新增）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DateSwitchResponse {

    /** 原会计日期 */
    private LocalDate previousDate;

    /** 新会计日期 */
    private LocalDate newDate;

    /** 是否已经切换过（幂等） */
    private boolean alreadySwitched;

    /** 切日时间 */
    private LocalDateTime switchedAt;
}
