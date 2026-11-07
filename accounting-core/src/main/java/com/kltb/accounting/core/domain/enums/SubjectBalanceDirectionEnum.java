package com.kltb.accounting.core.domain.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 会计科目余额方向枚举
 * <p>
 * 对应 t_account_subject.debit_credit 字段。
 * 表达科目的全局余额属性：
 * 0 - 无 / 不适用（专用于表外科目）
 * 1 - 借（资产类、成本类等）
 * 2 - 贷（负债类、权益类、损益类等）
 * 3 - 双向（共同类科目、往来互抵科目，其余额方向由发生额净额动态决定）
 */
@Getter
public enum SubjectBalanceDirectionEnum {

    /**
     * 无 / 不适用（表外科目）
     */
    NONE(0, "无"),

    /**
     * 借
     */
    DEBIT(1, "借"),

    /**
     * 贷
     */
    CREDIT(2, "贷"),

    /**
     * 双向（共同类及双向往来科目）
     */
    BOTH(3, "双向");

    @EnumValue
    private final Integer code;

    @JsonValue
    private final String desc;

    SubjectBalanceDirectionEnum(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    private static final Map<Integer, SubjectBalanceDirectionEnum> CODE_MAP = Arrays.stream(values())
            .collect(Collectors.toMap(SubjectBalanceDirectionEnum::getCode, Function.identity()));

    /**
     * 根据数字编码查找枚举实例（空安全）
     */
    public static SubjectBalanceDirectionEnum fromCode(Integer code) {
        return code == null ? null : CODE_MAP.get(code);
    }

    /**
     * 根据数字编码获取中文描述
     */
    public static String getDescByCode(Integer code) {
        if (code == null) {
            return "";
        }
        SubjectBalanceDirectionEnum val = fromCode(code);
        return val != null ? val.getDesc() : String.valueOf(code);
    }

    /**
     * 是否为双向科目
     */
    public boolean isBidirectional() {
        return this == BOTH;
    }

    /**
     * 是否为双向科目 (别名)
     */
    public boolean isBoth() {
        return this == BOTH;
    }

    /**
     * 是否为借方余额方向
     */
    public boolean isDebit() {
        return this == DEBIT;
    }

    /**
     * 是否为贷方余额方向
     */
    public boolean isCredit() {
        return this == CREDIT;
    }

    /**
     * 是否为表外无方向科目
     */
    public boolean isOffBalance() {
        return this == NONE;
    }

    /**
     * 是否为单向科目（借或贷）
     */
    public boolean isUnidirectional() {
        return this == DEBIT || this == CREDIT;
    }
}
