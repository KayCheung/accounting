// accounting-api/src/main/java/com/kltb/accounting/api/request/LocalMessageResetRequest.java
package com.kltb.accounting.api.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 失败本地消息重置为待发送（PENDING）请求
 */
@Getter
@Setter
@ToString
@Accessors(chain = true)
@Schema(description = "失败本地消息重置为待发送请求")
public class LocalMessageResetRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "指定消息ID列表（与时间范围二选一，指定列表优先）", example = "[\"MSG-20261109-001\"]")
    private List<String> messageIds;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @Schema(description = "创建起始时间（按时间范围批量重置时使用）", example = "2026-11-09 00:00:00")
    private LocalDateTime startTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @Schema(description = "创建截止时间（按时间范围批量重置时使用）", example = "2026-11-09 23:59:59")
    private LocalDateTime endTime;

    @Schema(description = "重置原因说明", example = "RocketMQ 故障恢复后重新激活")
    private String reason;
}
