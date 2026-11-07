package com.kltb.accounting.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.io.Serializable;

/**
 * 冻结记录查询请求
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "冻结记录查询请求")
public class FreezeRecordQueryRequest extends PageRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotBlank(message = "账户编号不能为空")
    @Schema(description = "账户编号", example = "00120260301000001")
    private String accountNo;

    @Schema(description = "状态：1-冻结,2-已解冻", example = "1")
    private Integer status;
}
