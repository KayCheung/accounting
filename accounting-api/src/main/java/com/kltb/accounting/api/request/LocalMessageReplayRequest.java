// accounting-api/src/main/java/com/kltb/accounting/api/request/LocalMessageReplayRequest.java
package com.kltb.accounting.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 本地消息手动重放请求
 */
@Getter
@Setter
@ToString
@Accessors(chain = true)
@Schema(description = "本地消息手动重放请求")
public class LocalMessageReplayRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotEmpty(message = "重放的消息ID列表不能为空")
    @Schema(description = "待重放的消息ID列表", requiredMode = Schema.RequiredMode.REQUIRED, example = "[\"MSG-20261109-001\"]")
    private List<String> messageIds;
}
