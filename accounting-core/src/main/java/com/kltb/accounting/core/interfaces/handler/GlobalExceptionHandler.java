// accounting-core/src/main/java/com/kltb/accounting/core/interfaces/handler/GlobalExceptionHandler.java
package com.kltb.accounting.core.interfaces.handler;

import com.kltb.accounting.api.constant.ResultCode;
import com.kltb.accounting.api.response.ApiResponse;
import com.kltb.accounting.core.shared.exception.AccountException;
import com.kltb.accounting.core.shared.exception.GenericException;
import com.kltb.accounting.core.shared.exception.ServiceException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.stream.Collectors;

/**
 * 全局异常处理器
 * <p>
 * 统一解析 ResultCode 枚举，包装为 ApiResponse 返回。
 * traceId 由 ApiResponse.getTraceId() 自动从 SkyWalking 获取，无需手动传入。
 * 日志级别策略：
 *   - ServiceException  → WARN（可预期业务异常，无需告警）
 *   - AccountException  → ERROR（账务专项异常，需监控告警）
 *   - 其他未知异常      → ERROR（系统异常，需立即排查）
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 处理业务异常（可预期，WARN 级别）
     */
    @ExceptionHandler(ServiceException.class)
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<Void> handleServiceException(ServiceException ex) {
        log.warn("[业务异常] code={}, message={}", ex.getResultCode().getCode(), ex.getMessage());
        return ApiResponse.fail(ex.getResultCode(), ex.getMessage());
    }

    /**
     * 处理账务专项异常（ERROR 级别，需监控告警）
     */
    @ExceptionHandler(AccountException.class)
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<Void> handleAccountException(AccountException ex) {
        // 账务专项异常必须 ERROR 级别记录，携带完整堆栈便于排查
        log.error("[账务异常] code={}, message={}", ex.getResultCode().getCode(), ex.getMessage(), ex);
        return ApiResponse.fail(ex.getResultCode(), ex.getMessage());
    }

    /**
     * 处理通用业务异常基类（兜底，WARN 级别）
     */
    @ExceptionHandler(GenericException.class)
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<Void> handleGenericException(GenericException ex) {
        log.warn("[通用异常] code={}, message={}", ex.getResultCode().getCode(), ex.getMessage());
        return ApiResponse.fail(ex.getResultCode(), ex.getMessage());
    }

    /**
     * 处理 @Valid 参数校验失败（MethodArgumentNotValidException）
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<Void> handleValidationException(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        log.warn("[参数校验失败] {}", message);
        return ApiResponse.fail(ResultCode.PARAM_ERROR, message);
    }

    /**
     * 处理 @Validated 方法级参数校验失败（ConstraintViolationException）
     */
    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<Void> handleConstraintViolationException(ConstraintViolationException ex) {
        String message = ex.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.joining("; "));
        log.warn("[约束校验失败] {}", message);
        return ApiResponse.fail(ResultCode.PARAM_ERROR, message);
    }

    /**
     * 处理缺失必选请求参数异常（MissingServletRequestParameterException）
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<Void> handleMissingServletRequestParameterException(
            MissingServletRequestParameterException ex) {
        String message = String.format("缺少必填请求参数: %s", ex.getParameterName());
        log.warn("[请求参数缺失] {}", message);
        return ApiResponse.fail(ResultCode.PARAM_ERROR, message);
    }

    /**
     * 处理请求参数类型不匹配异常（MethodArgumentTypeMismatchException）
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<Void> handleMethodArgumentTypeMismatchException(
            MethodArgumentTypeMismatchException ex) {
        String requiredType = ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "未知";
        String message = String.format("参数类型不匹配: %s 应为 %s", ex.getName(), requiredType);
        log.warn("[参数类型不匹配] {}", message);
        return ApiResponse.fail(ResultCode.PARAM_ERROR, message);
    }

    /**
     * 处理所有未捕获的系统异常（ERROR 级别，需立即排查）
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResponse<Void> handleUnknownException(Exception ex) {
        // 系统异常必须打印完整堆栈，便于排查根因
        log.error("[系统异常] 未预期的异常，请立即排查", ex);
        Throwable root = ex;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String rootMsg = root.getMessage() != null ? (root.getClass().getSimpleName() + ": " + root.getMessage()) : root.getClass().getSimpleName();
        return ApiResponse.fail(ResultCode.SYSTEM_ERROR, "系统内部错误: " + rootMsg);
    }
}
