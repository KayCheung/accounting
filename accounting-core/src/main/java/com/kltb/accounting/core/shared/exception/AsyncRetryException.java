// accounting-core/src/main/java/com/kltb/accounting/core/shared/exception/AsyncRetryException.java
package com.kltb.accounting.core.shared.exception;

import com.kltb.accounting.api.constant.ResultCode;

/**
 * 异步重试异常
 * <p>
 * 用于触发 MQ 消费端的异步重试机制。
 * 抛出此异常时，消费者框架应将消息重新投递，而非直接标记为失败。
 * 重试间隔遵循指数退避策略：10s / 30s / 60s，最大重试 3 次。
 * 超限后标记 status=FAILED 并触发告警。
 */
public class AsyncRetryException extends GenericException {

    /** 当前已重试次数 */
    private final int retryCount;

    public AsyncRetryException(ResultCode resultCode) {
        super(resultCode);
        this.retryCount = 0;
    }

    public AsyncRetryException(ResultCode resultCode, String message) {
        super(resultCode, message);
        this.retryCount = 0;
    }

    public AsyncRetryException(ResultCode resultCode, String message, int retryCount) {
        super(resultCode, message);
        this.retryCount = retryCount;
    }

    public AsyncRetryException(ResultCode resultCode, String message, Throwable cause) {
        super(resultCode, message, cause);
        this.retryCount = 0;
    }

    public int getRetryCount() {
        return retryCount;
    }
}
