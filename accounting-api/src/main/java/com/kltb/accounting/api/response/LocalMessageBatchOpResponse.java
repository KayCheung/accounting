// accounting-api/src/main/java/com/kltb/accounting/api/response/LocalMessageBatchOpResponse.java
package com.kltb.accounting.api.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 本地消息批量操作结果响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
@Schema(description = "本地消息批量操作结果响应")
public class LocalMessageBatchOpResponse implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "总处理条数", example = "10")
    private Integer totalCount;

    @Schema(description = "成功条数", example = "8")
    private Integer successCount;

    @Schema(description = "失败条数", example = "2")
    private Integer failedCount;

    @Builder.Default
    @Schema(description = "失败的消息ID列表", example = "[\"MSG-20261109-002\"]")
    private List<String> failedMessageIds = new ArrayList<>();
}
