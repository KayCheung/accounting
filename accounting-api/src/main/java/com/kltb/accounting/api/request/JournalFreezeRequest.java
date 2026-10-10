// accounting-api/src/main/java/com/kltb/accounting/api/request/JournalFreezeRequest.java
package com.kltb.accounting.api.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 业务预冻结请求 DTO
 * <p>
 * 结构对齐 JournalSubmitRequest，业务方无需传入内部账号或科目，通过业务参数匹配规则并识别出金方执行冻结。
 */
@Data
public class JournalFreezeRequest {

    @NotBlank(message = "traceNo不能为空")
    @Size(max = 64, message = "traceNo长度不能超过64")
    private String traceNo;

    @Min(value = 0, message = "traceSeq必须>=0")
    private Integer traceSeq = 0;

    @NotBlank(message = "businessCode不能为空")
    @Size(max = 32, message = "businessCode长度不能超过32")
    private String businessCode;

    @NotBlank(message = "tradingCode不能为空")
    @Size(max = 32, message = "tradingCode长度不能超过32")
    private String tradingCode;

    @NotBlank(message = "payChannel不能为空")
    @Size(max = 32, message = "payChannel长度不能超过32")
    private String payChannel;

    @NotNull(message = "amount不能为空")
    @DecimalMin(value = "0.000001", message = "amount必须大于0")
    private BigDecimal amount;

    @NotNull(message = "tradeTime不能为空")
    private LocalDateTime tradeTime;

    @Size(max = 64, message = "summary长度不能超过64")
    private String summary;

    /**
     * 业务扩展参数（键值对，如 creditParty, partnerCode, channelSource 等）
     */
    private java.util.Map<String, Object> extraAttrs;

    @NotEmpty(message = "details不能为空")
    @Valid
    private List<JournalDetailRequest> details;
}
