// accounting-core/src/main/java/com/kltb/accounting/core/interfaces/JournalingController.java
package com.kltb.accounting.core.interfaces;

import com.kltb.accounting.api.request.JournalSubmitRequest;
import com.kltb.accounting.api.response.ApiResponse;
import com.kltb.accounting.api.response.JournalSubmitResponse;
import com.kltb.accounting.core.application.service.JournalingApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 记账流水入库 Controller
 */
@RestController
@RequestMapping("/accounting/journal")
@RequiredArgsConstructor
@Validated
@Tag(name = "记账流水入库", description = "业务流水入库接口")
public class JournalingController {

    private final JournalingApplicationService journalingApplicationService;

    @PostMapping("/submit")
    @Operation(summary = "提交记账流水")
    public ApiResponse<JournalSubmitResponse> submit(
            @Valid @RequestBody JournalSubmitRequest request) {
        JournalSubmitResponse response = journalingApplicationService.submitJournal(request);
        return ApiResponse.ok(response);
    }

    @GetMapping("/{traceNo}")
    @Operation(summary = "查询流水状态")
    @Parameter(name = "traceNo", description = "系统跟踪号")
    public ApiResponse<?> getJournal(@PathVariable String traceNo) {
        // 当前阶段返回空数据，Step 10 后补充实现
        return ApiResponse.ok(null);
    }

    @GetMapping("/trace/{traceNo}/transaction")
    @Operation(summary = "查询关联事务")
    @Parameter(name = "traceNo", description = "系统跟踪号")
    public ApiResponse<?> getTransaction(@PathVariable String traceNo) {
        // 当前阶段返回空数据，Step 10 后补充实现
        return ApiResponse.ok(null);
    }
}
