// accounting-core/src/main/java/com/kltb/accounting/core/shared/exception/GenericException.java
package com.kltb.accounting.core.shared.exception;

import com.kltb.accounting.api.constant.ResultCode;
import lombok.Getter;

/**
 * 异常基类
 * <p>
 * 所有业务异常必须继承此类，并绑定 ResultCode 枚举。
 * 严禁传入魔法数字或硬编码字符串。
 */
@Getter
public class GenericException extends RuntimeException {

    /** 绑定的结果码枚举 */
    private final ResultCode resultCode;

    public GenericException(ResultCode resultCode) {
        super(resultCode.getMessage());
        this.resultCode = resultCode;
    }

    public GenericException(ResultCode resultCode, String message) {
        super(message);
        this.resultCode = resultCode;
    }

    public GenericException(ResultCode resultCode, String message, Throwable cause) {
        super(message, cause);
        this.resultCode = resultCode;
    }

    public GenericException(ResultCode resultCode, Throwable cause) {
        super(resultCode.getMessage(), cause);
        this.resultCode = resultCode;
    }
}
