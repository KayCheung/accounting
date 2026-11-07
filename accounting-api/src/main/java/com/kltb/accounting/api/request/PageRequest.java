// accounting-api/src/main/java/com/kltb/accounting/api/request/PageRequest.java
package com.kltb.accounting.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.io.Serializable;

/**
 * 分页请求基类
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "分页请求基类")
public class PageRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Min(value = 1, message = "页码最小为 1")
    @Schema(description = "页码，从 1 开始", example = "1")
    private Integer pageNo = 1;

    @Min(value = 1, message = "每页条数最小为 1")
    @Max(value = 200, message = "每页条数最大为 200")
    @Schema(description = "每页条数，最大 200", example = "20")
    private Integer pageSize = 20;
}
