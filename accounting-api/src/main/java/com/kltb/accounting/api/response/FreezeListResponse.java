package com.kltb.accounting.api.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 冻结记录列表响应
 */
@Data
@Schema(description = "冻结记录列表响应")
public class FreezeListResponse implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "冻结编号")
    private String freezeId;

    @Schema(description = "账户编号")
    private String accountNo;

    @Schema(description = "冻结金额")
    private BigDecimal freezeAmount;

    @Schema(description = "状态：1-冻结,2-已解冻")
    private Integer status;

    @Schema(description = "状态描述")
    private String statusDesc;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @Schema(description = "过期时间")
    private LocalDateTime expireTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @Schema(description = "交易时间")
    private LocalDateTime tradeTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "摘要")
    private String summary;
}
