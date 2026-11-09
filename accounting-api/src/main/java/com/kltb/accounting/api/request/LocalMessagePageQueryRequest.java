// accounting-api/src/main/java/com/kltb/accounting/api/request/LocalMessagePageQueryRequest.java
package com.kltb.accounting.api.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.time.LocalDateTime;

/**
 * 本地消息分页查询请求
 */
@Getter
@Setter
@ToString(callSuper = true)
@Accessors(chain = true)
@Schema(description = "本地消息分页查询请求")
public class LocalMessagePageQueryRequest extends PageRequest {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "消息状态：1-待发送, 2-已发送, 3-发送失败, 4-已确认", example = "3")
    private Integer status;

    @Schema(description = "消息主题/队列名", example = "TOPIC_ASYNC_POSTING")
    private String topic;

    @Schema(description = "消息ID", example = "MSG-20261109-001")
    private String messageId;

    @Schema(description = "业务唯一键", example = "ENTRY-20261109-001")
    private String businessKey;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @Schema(description = "创建起始时间", example = "2026-11-09 00:00:00")
    private LocalDateTime startTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @Schema(description = "创建截止时间", example = "2026-11-09 23:59:59")
    private LocalDateTime endTime;
}
