package com.kltb.accounting.api.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * 科目下各分户账户总分核对穿透明细响应 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GlAccountDiffResponse {

    /** 科目代码 */
    private String subjectCode;

    /** 科目名称 */
    private String subjectName;

    /** 该科目下分户账户明细列表 */
    private List<AccountItem> accounts;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AccountItem {
        /** 账户编号 */
        private String accountNo;

        /** 账户名称 */
        private String accountName;

        /** 总账期末余额 (t_account_balance.end_balance) */
        private BigDecimal glEndBalance;

        /** 分户当前/截面余额 (t_account.balance) */
        private BigDecimal accountBalance;

        /** 绝对差额 */
        private BigDecimal diff;

        /** 账户状态描述 */
        private String statusDesc;

        /** 最近一次凭证发生日期 */
        private String latestVoucherDate;
    }
}
