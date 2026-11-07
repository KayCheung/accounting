package com.kltb.accounting.api.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 更新缓冲入账规则请求
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "更新缓冲入账规则请求")
public class BufferRuleUpdateRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Size(max = 32, message = "规则名称长度不能超过32")
    @Schema(description = "规则名称", example = "现金缓冲规则（新）")
    private String ruleName;

    @Schema(description = "缓冲模式：1-逐条，2-日间批量，3-日终批量", example = "2")
    private Integer bufferMode;

    @Size(max = 32, message = "科目编码长度不能超过32")
    @Schema(description = "会计科目编码", example = "101001")
    private String subjectCode;

    @Size(max = 32, message = "账户编号长度不能超过32")
    @Schema(description = "账户编号", example = "")
    private String accountNo;

    @Schema(description = "借贷方向：1-借，2-贷", example = "1")
    private Integer debitCredit;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @Schema(description = "生效时间", example = "2026-01-01 00:00:00")
    private LocalDateTime effectiveTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @Schema(description = "失效时间", example = "2099-12-31 23:59:59")
    private LocalDateTime expirationTime;
}
