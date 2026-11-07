package com.kltb.accounting.core.interfaces.handler;

import com.kltb.accounting.api.constant.ResultCode;
import com.kltb.accounting.api.response.ApiResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.HttpMessageNotReadableException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("GlobalExceptionHandler: 拦截 HttpMessageNotReadableException 并返回 PARAM_ERROR")
    void testHandleHttpMessageNotReadableException() {
        HttpMessageNotReadableException ex = new HttpMessageNotReadableException("JSON parse error: Cannot deserialize value of type java.time.LocalDateTime");
        ApiResponse<Void> response = handler.handleHttpMessageNotReadableException(ex);

        assertNotNull(response);
        assertEquals(ResultCode.PARAM_ERROR.getCode(), response.getCode());
        assertEquals("请求数据格式错误，请检查 JSON 格式或日期时间字段", response.getMessage());
    }
}
