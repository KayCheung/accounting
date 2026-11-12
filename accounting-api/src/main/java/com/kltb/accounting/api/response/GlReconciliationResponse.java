package com.kltb.accounting.api.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 总账与分户账核对（总分核对）响应 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GlReconciliationResponse {

    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    private LocalDate accountingDate;

    /** 是否全部平衡（无差额） */
    private boolean passed;

    /** 总账总发生额汇总 */
    private BigDecimal totalGlBalance;

    /** 分户实时/截面余额汇总 */
    private BigDecimal totalAccountBalance;

    /** 绝对差额总和 */
    private BigDecimal totalDiff;

    /** 差异科目明细列表 */
    private List<GlDiffDetail> diffDetails;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GlDiffDetail {
        /** 科目代码 */
        private String subjectCode;

        /** 科目名称 */
        private String subjectName;

        /** 总账侧余额 (t_account_balance.end_balance 汇总) */
        private BigDecimal glTotal;

        /** 分户侧余额 (t_account.balance 汇总) */
        private BigDecimal accountTotal;

        /** 绝对差额 */
        private BigDecimal diff;

        /** 分户账户数 */
        private Integer accountCount;

        /** 异常诊断原因（如检测到历史断档凭证等） */
        private String reasonDesc;

        /** 是否支持一键自动修复 */
        private Boolean repairable;
    }
}
