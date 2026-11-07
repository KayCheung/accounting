// accounting-core/src/main/java/com/kltb/accounting/core/domain/enums/EodStatusEnum.java
package com.kltb.accounting.core.domain.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 日切状态枚举：
 * 1-未开始, 2-切日中, 3-清理中, 4-快照中, 5-试算中, 6-结转中, 7-归档中, 8-已完成, 9-失败
 */
@Getter
@AllArgsConstructor
public enum EodStatusEnum {

    NOT_STARTED(1, "未开始"),
    SWITCHING(2, "切日中"),
    CLEANING(3, "清理中"),
    SNAPSHOT(4, "快照中"),
    TRIAL_BALANCE(5, "试算中"),
    TRANSFER(6, "结转中"),
    ARCHIVING(7, "归档中"),
    COMPLETED(8, "已完成"),
    FAILED(9, "失败");

    @EnumValue
    @JsonValue
    private final Integer code;

    private final String desc;

    private static final Map<Integer, EodStatusEnum> CODE_MAP = Arrays.stream(values())
            .collect(Collectors.toMap(EodStatusEnum::getCode, Function.identity()));

    /**
     * 根据状态码查找枚举实例（空安全，未匹配时返回 null）
     */
    public static EodStatusEnum fromCode(Integer code) {
        return code == null ? null : CODE_MAP.get(code);
    }

    /**
     * 根据状态码获取中文描述（空安全，未匹配时返回空字符串或状态码本身）
     */
    public static String getDescByCode(Integer code) {
        if (code == null) {
            return "";
        }
        EodStatusEnum status = fromCode(code);
        return status != null ? status.getDesc() : String.valueOf(code);
    }

    /**
     * 判断状态码是否与当前枚举匹配（安全避免包装类型引用比较与 NPE 陷阱）
     */
    public boolean matches(Integer statusCode) {
        return statusCode != null && statusCode.equals(this.code);
    }

    /**
     * 判断状态码是否表示日切归档已完成
     */
    public static boolean isCompleted(Integer statusCode) {
        return COMPLETED.matches(statusCode);
    }

    /**
     * 判断枚举实例是否表示日切归档已完成
     */
    public static boolean isCompleted(EodStatusEnum status) {
        return COMPLETED.equals(status);
    }
}
