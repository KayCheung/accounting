package com.kltb.accounting.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.io.Serializable;

/**
 * 内部账户开户请求 DTO
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "内部账户开户请求")
public class InternalAccountOpenRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotBlank(message = "科目编码不能为空")
    @Size(max = 32, message = "科目编码长度不能超过32")
    @Schema(description = "科目编码", example = "1001")
    private String subjectCode;
}
