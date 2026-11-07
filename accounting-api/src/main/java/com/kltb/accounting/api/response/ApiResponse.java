// accounting-api/src/main/java/com/kltb/accounting/api/response/ApiResponse.java
package com.kltb.accounting.api.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.kltb.accounting.api.constant.ResultCode;
import lombok.Getter;
import org.apache.skywalking.apm.toolkit.trace.TraceContext;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;

/**
 * 统一 API 响应体
 * <p>
 * traceId 通过 SkyWalking TraceContext 自动获取，无需手动传入。
 *
 * @param <T> 业务数据类型
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 结果码 */
    @Getter
    private final String code;

    /** 结果描述 */
    @Getter
    private final String message;

    /** 业务数据 */
    @Getter
    private final T data;

    /** 响应时间戳（毫秒） */
    @Getter
    private final long timestamp;

    private ApiResponse(String code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
        this.timestamp = Instant.now().toEpochMilli();
    }

    /**
     * 全链路追踪 ID，由 SkyWalking TraceContext 自动注入。
     * 未挂载 Agent 时返回 "N/A"。
     */
    public String getTraceId() {
        return TraceContext.traceId();
    }

    /** 成功（无数据） */
    public static ApiResponse<Void> ok() {
        return new ApiResponse<>(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMessage(), null);
    }

    /** 成功（有数据） */
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMessage(), data);
    }

    /** 失败（ResultCode 枚举） */
    public static <T> ApiResponse<T> fail(ResultCode resultCode) {
        return new ApiResponse<>(resultCode.getCode(), resultCode.getMessage(), null);
    }

    /** 失败（ResultCode 枚举 + 自定义消息） */
    public static <T> ApiResponse<T> fail(ResultCode resultCode, String message) {
        return new ApiResponse<>(resultCode.getCode(), message, null);
    }

    /** 判断是否成功 */
    public boolean isSuccess() {
        return ResultCode.SUCCESS.getCode().equals(this.code);
    }
}
