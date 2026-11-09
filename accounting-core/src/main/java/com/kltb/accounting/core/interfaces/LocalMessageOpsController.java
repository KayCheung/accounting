package com.kltb.accounting.core.interfaces;

import com.kltb.accounting.api.request.LocalMessagePageQueryRequest;
import com.kltb.accounting.api.request.LocalMessageReplayRequest;
import com.kltb.accounting.api.request.LocalMessageResetRequest;
import com.kltb.accounting.api.response.ApiResponse;
import com.kltb.accounting.api.response.LocalMessageBatchOpResponse;
import com.kltb.accounting.api.response.LocalMessageResponse;
import com.kltb.accounting.api.response.PageResponse;
import com.kltb.accounting.core.application.service.LocalMessageApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 本地消息发件箱运维 Controller
 * <p>
 * 路径前缀：/accounting/ops/local-messages
 */
@RestController
@RequestMapping("/accounting/ops/local-messages")
@RequiredArgsConstructor
@Validated
@Tag(name = "本地消息运维", description = "本地消息发件箱死信与补偿运维接口")
public class LocalMessageOpsController {

    private final LocalMessageApplicationService localMessageApplicationService;

    /**
     * POST /accounting/ops/local-messages/page — 分页查询本地消息
     */
    @PostMapping("/page")
    @Operation(summary = "分页查询本地消息", description = "支持按消息状态（如发送失败 status=3）、主题、业务唯一键及创建时间区间分页检索")
    public ApiResponse<PageResponse<LocalMessageResponse>> pageQuery(
            @Valid @RequestBody LocalMessagePageQueryRequest request) {
        return ApiResponse.ok(localMessageApplicationService.pageQuery(request));
    }

    /**
     * POST /accounting/ops/local-messages/replay — 手动重放本地消息投递
     */
    @PostMapping("/replay")
    @Operation(summary = "手动重放消息投递", description = "指定消息ID列表立即尝试向 RocketMQ 重新投递，发送成功标记为已发送")
    public ApiResponse<LocalMessageBatchOpResponse> replay(
            @Valid @RequestBody LocalMessageReplayRequest request) {
        return ApiResponse.ok(localMessageApplicationService.replay(request));
    }

    /**
     * POST /accounting/ops/local-messages/reset — 批量重置失败消息为待发送
     */
    @PostMapping("/reset")
    @Operation(summary = "批量重置失败消息", description = "支持按指定消息ID列表或按故障时间范围，将状态为 FAILED 的消息重置为 PENDING 并归零重试计数，交由定时任务重新推进")
    public ApiResponse<LocalMessageBatchOpResponse> reset(
            @Valid @RequestBody LocalMessageResetRequest request) {
        return ApiResponse.ok(localMessageApplicationService.reset(request));
    }
}
