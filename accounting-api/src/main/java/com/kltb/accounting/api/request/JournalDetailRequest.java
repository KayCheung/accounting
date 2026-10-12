// accounting-api/src/main/java/com/kltb/accounting/api/request/JournalDetailRequest.java
package com.kltb.accounting.api.request;

import lombok.Data;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.Map;

/**
 * 记账流水明细请求 DTO（N2 修复：含 itemCode 字段）
 */
@Data
public class JournalDetailRequest {

    @NotBlank(message = "customerId不能为空")
    @Size(max = 64, message = "customerId长度不能超过64")
    private String customerId;

    @NotNull(message = "customerType不能为空")
    private Integer customerType;

    @NotBlank(message = "fundsType不能为空")
    @Size(max = 32, message = "fundsType长度不能超过32")
    private String fundsType;

    @NotNull(message = "amount不能为空")
    @DecimalMin(value = "0.000001", message = "amount必须大于0")
    private BigDecimal amount;

    /**
     * 款项明细扩展参数（键值对，如 subProduct, rate 等）
     */
    private Map<String, Object> extraAttrs;
}
