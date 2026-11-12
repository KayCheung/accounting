package com.kltb.accounting.api.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * 总账与分户账核对一键修复响应 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GlRepairResponse {

    /** 修复是否成功（总分差额已清零） */
    private boolean success;

    /** 自动补齐/回溯的历史会计日天数 */
    private int repairedDateCount;

    /** 修复/重算的账户数量 */
    private int repairedAccountCount;

    /** 补回/结转的发生额累计汇总 */
    private BigDecimal repairedAmount;

    /** 修复后剩余总分差额（成功时为 0） */
    private BigDecimal remainingDiff;

    /** 修复结果友好提示信息 */
    private String message;

    /** 详细诊断与修复执行日志 */
    private List<String> repairLogs;
}
