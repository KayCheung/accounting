// accounting-api/src/main/java/com/kltb/accounting/api/response/LocalMessageResponse.java
package com.kltb.accounting.api.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 本地消息明细响应体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
@Schema(description = "本地消息明细响应")
public class LocalMessageResponse implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "主键ID", example = "1001")
    private Long id;

    @Schema(description = "消息ID", example = "MSG-20261109-001")
    private String messageId;

    @Schema(description = "消息主题/队列名", example = "TOPIC_ASYNC_POSTING")
    private String topic;

    @Schema(description = "消息标签", example = "TAG_POSTING")
    private String tag;

    @Schema(description = "业务唯一键", example = "ENTRY-20261109-001")
    private String businessKey;

    @Schema(description = "消息体载荷（JSON 字符串）")
    private String payload;

    @Schema(description = "消息状态码：1-待发送, 2-已发送, 3-发送失败, 4-已确认", example = "3")
    private Integer status;

    @Schema(description = "消息状态描述", example = "发送失败")
    private String statusDesc;

    @Schema(description = "已重试次数", example = "3")
    private Integer retryCount;

    @Schema(description = "最大重试次数", example = "3")
    private Integer maxRetry;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @Schema(description = "下次重试时间")
    private LocalDateTime nextRetryTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @Schema(description = "实际发送时间")
    private LocalDateTime sendTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}
