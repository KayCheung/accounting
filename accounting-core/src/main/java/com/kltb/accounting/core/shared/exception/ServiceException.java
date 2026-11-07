// accounting-core/src/main/java/com/kltb/accounting/core/shared/exception/ServiceException.java
package com.kltb.accounting.core.shared.exception;

import com.kltb.accounting.api.constant.ResultCode;

/**
 * 业务异常
 * <p>
 * 适用于可预期的业务错误，如：余额不足、规则未找到、数据不存在等。
 * 此类异常不需要打印堆栈，GlobalExceptionHandler 会以 WARN 级别记录。
 */
public class ServiceException extends GenericException {

    public ServiceException(ResultCode resultCode) {
        super(resultCode);
    }

    public ServiceException(ResultCode resultCode, String message) {
        super(resultCode, message);
    }

    public ServiceException(ResultCode resultCode, String message, Throwable cause) {
        super(resultCode, message, cause);
    }
}
