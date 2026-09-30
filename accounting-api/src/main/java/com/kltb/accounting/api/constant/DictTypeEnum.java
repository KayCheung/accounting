package com.kltb.accounting.api.constant;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 系统字典类型枚举 (dict_type)
 * <p>
 * 统一管理系统级字典分类编码，杜绝全局魔法字符串。
 */
@Getter
@AllArgsConstructor
public enum DictTypeEnum {

    /**
     * 凭证类型（收款凭证、付款凭证、转账凭证、调账凭证、冲账凭证、期末结转凭证、记账凭证）
     */
    VOUCHER_TYPE("voucher_type", "凭证类型"),

    /**
     * 支付渠道（现金、支付宝、微信支付、银行卡、银联在线、内部清算）
     */
    PAY_CHANNEL("pay_channel", "支付渠道"),

    /**
     * 常用交易编码（现金付款、贷款放款、还款、资金转账、手续费扣收、提现、账务调整等）
     */
    TRADING_CODE("trading_code", "交易编码"),

    /**
     * 交易款项类型（本金款项、利息款项、罚息、手续费、税费等）
     */
    FUNDS_TYPE("funds_type", "交易款项类型"),

    /**
     * 辅助核算类型（客户、供应商、部门、项目、员工等）
     */
    AUXILIARY_TYPE("auxiliary_type", "辅助核算类型"),

    /**
     * 业务线编码（芒好贷、购车宝、手工记账等）
     */
    BUSINESS_CODE("business_code", "业务线"),

    /**
     * 账户类型（贷款本金、利息账户、结算账户、现金账户等）
     */
    ACCOUNT_TYPE("account_type", "账户类型"),

    /**
     * 币种（人民币、美元、欧元、港币等）
     */
    CURRENCY("currency", "币种");

    private final String code;
    private final String desc;

    public static DictTypeEnum fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (DictTypeEnum item : values()) {
            if (item.code.equalsIgnoreCase(code)) {
                return item;
            }
        }
        return null;
    }
}
