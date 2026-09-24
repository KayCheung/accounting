// accounting-api/src/main/java/com/kltb/accounting/api/request/JournalDetailRequest.java
package com.kltb.accounting.api.request;

import lombok.Data;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

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

    /**
     * 款项明细编码（N2 修复）
     * DDL 唯一索引 uk_trace_no 包含此字段：(trace_no, trace_seq, customer_id, item_code)
     * 用于区分同一客户在同一笔流水中的不同款项明细
     */
    @NotBlank(message = "itemCode不能为空")
    @Size(max = 32, message = "itemCode长度不能超过32")
    private String itemCode;

    @NotNull(message = "amount不能为空")
    @DecimalMin(value = "0.000001", message = "amount必须大于0")
    private BigDecimal amount;
}
