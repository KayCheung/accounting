package com.kltb.accounting.api.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 批量开户结果响应 DTO
 */
@Getter
@Setter
@Accessors(chain = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BatchOpenResultResponse implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "扫描总数")
    private Integer totalCount;

    @Schema(description = "已存在")
    private Integer alreadyExists;

    @Schema(description = "新创建")
    private Integer newlyCreated;

    @Schema(description = "失败数")
    private Integer failed;

    @Schema(description = "失败原因列表")
    private List<String> failedReasons;
}
