// accounting-core/src/main/java/com/kltb/accounting/core/domain/enums/BalanceDirectionEnum.java
package com.kltb.accounting.core.domain.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 余额方向枚举
 */
@Getter
public enum BalanceDirectionEnum {

    /**
     * 平（借贷相抵平衡，余额为0）
     */
    FLAT(0, "平"),

    /**
     * 借
     */
    DEBIT(1, "借"),

    /**
     * 贷
     */
    CREDIT(2, "贷");

    @EnumValue
    private final Integer code;

    @JsonValue
    private final String desc;

    BalanceDirectionEnum(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    private static final Map<Integer, BalanceDirectionEnum> CODE_MAP = Arrays.stream(values())
            .collect(Collectors.toMap(BalanceDirectionEnum::getCode, Function.identity()));

    public static BalanceDirectionEnum fromCode(Integer code) {
        return code == null ? null : CODE_MAP.get(code);
    }

    public static String getDescByCode(Integer code) {
        if (code == null) {
            return "";
        }
        BalanceDirectionEnum val = fromCode(code);
        return val != null ? val.getDesc() : String.valueOf(code);
    }

    /**
     * 根据借贷发生额原生大小对比推导余额/净额方向（严格遵守绝对值法则，借贷相等为平）
     *
     * @param debitAmount  借方金额
     * @param creditAmount 贷方金额
     * @return 余额方向枚举
     */
    public static BalanceDirectionEnum determineDirection(BigDecimal debitAmount, BigDecimal creditAmount) {
        BigDecimal debit = debitAmount != null ? debitAmount : BigDecimal.ZERO;
        BigDecimal credit = creditAmount != null ? creditAmount : BigDecimal.ZERO;
        int cmp = debit.compareTo(credit);
        if (cmp == 0) {
            return FLAT;
        }
        return cmp > 0 ? DEBIT : CREDIT;
    }
}
