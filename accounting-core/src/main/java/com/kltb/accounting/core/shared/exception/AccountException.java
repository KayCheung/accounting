// accounting-core/src/main/java/com/kltb/accounting/core/shared/exception/AccountException.java
package com.kltb.accounting.core.shared.exception;

import com.kltb.accounting.api.constant.ResultCode;

/**
 * 账务专项异常
 * <p>
 * 适用于账务核心场景的专项错误，如：
 * - 账户冻结/注销状态下的操作拦截
 * - 借贷不平衡（阻断凭证写库）
 * - 非法账户状态流转
 * <p>
 * 此类异常会在 GlobalExceptionHandler 中以 ERROR 级别记录，并触发监控告警。
 */
public class AccountException extends GenericException {

    public AccountException(ResultCode resultCode) {
        super(resultCode);
    }

    public AccountException(ResultCode resultCode, String message) {
        super(resultCode, message);
    }

    public AccountException(ResultCode resultCode, String message, Throwable cause) {
        super(resultCode, message, cause);
    }

    public AccountException(String message) {
        super(ResultCode.SYSTEM_ERROR, message);
    }
}
