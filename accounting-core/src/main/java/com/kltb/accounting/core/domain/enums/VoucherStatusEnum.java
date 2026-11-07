// accounting-core/src/main/java/com/kltb/accounting/core/domain/enums/VoucherStatusEnum.java
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
 * 凭证状态枚举：1-未过账，2-过账中，3-已过账，4-过账失败，5-已冲销。
 */
@Getter
@AllArgsConstructor
public enum VoucherStatusEnum {

    PENDING(1, "未过账"),
    POSTING(2, "过账中"),
    POSTED(3, "已过账"),
    FAILED(4, "过账失败"),
    REVERSED(5, "已冲销");

    @EnumValue
    @JsonValue
    private final Integer code;

    private final String desc;

    private static final Map<Integer, VoucherStatusEnum> CODE_MAP = Arrays.stream(values())
            .collect(Collectors.toMap(VoucherStatusEnum::getCode, Function.identity()));

    /**
     * 根据状态码查找枚举实例（空安全，未匹配时返回 null）
     */
    public static VoucherStatusEnum fromCode(Integer code) {
        return code == null ? null : CODE_MAP.get(code);
    }

    /**
     * 根据状态码获取中文描述（空安全，未匹配时返回状态码本身或空字符串）
     */
    public static String getDescByCode(Integer code) {
        if (code == null) {
            return "";
        }
        VoucherStatusEnum status = fromCode(code);
        return status != null ? status.getDesc() : String.valueOf(code);
    }

    /**
     * 判断状态码是否与当前枚举匹配（安全避免包装类型引用比较与NPE陷阱）
     */
    public boolean matches(Integer statusCode) {
        return statusCode != null && statusCode.equals(this.code);
    }

    /**
     * 判断状态码是否表示已过账
     */
    public static boolean isPosted(Integer statusCode) {
        return POSTED.matches(statusCode);
    }
}