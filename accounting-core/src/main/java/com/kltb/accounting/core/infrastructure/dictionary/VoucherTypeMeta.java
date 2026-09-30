package com.kltb.accounting.core.infrastructure.dictionary;

import com.kltb.accounting.core.domain.enums.TradeTypeEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 凭证类型字典元数据 DTO
 * <p>
 * 封装自 t_dictionary 中 voucher_type 的基础属性与 ext_json 扩展属性，
 * 避免在业务层、装配层重复解析或硬编码。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VoucherTypeMeta implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 字典项编码，如 RECEIPT, PAYMENT, TRANSFER, ADJUST, REVERSAL, PERIOD_END, GENERAL
     */
    private String dictCode;

    /**
     * 字典项名称，如 收款凭证, 付款凭证, 转账凭证, 调账凭证, 冲账凭证, 期末结转凭证, 记账凭证
     */
    private String dictName;

    /**
     * 凭证印签大标题，如 "收款凭证"、"转账凭证"、"调账凭证"
     */
    private String title;

    /**
     * 财务凭证字头，如 "收"、"付"、"转"、"调"、"冲"、"结"、"记"
     */
    private String prefix;

    /**
     * 默认交易类别：1-正常, 2-调账, 3-红字, 4-蓝字
     */
    private Integer tradeType;

    /**
     * 默认交易编码，如 CASH_PAY, TRANSFER, ADJUST
     */
    private String tradingCode;

    /**
     * 默认支付渠道，如 BANK, INTERNAL, CASH
     */
    private String payChannel;

    /**
     * 是否为调账类型凭证
     */
    public boolean isAdjustment() {
        return (tradeType != null && tradeType == TradeTypeEnum.ADJUSTMENT.getCode())
                || "ADJUST".equalsIgnoreCase(dictCode)
                || (dictName != null && dictName.contains("调账"));
    }
}
