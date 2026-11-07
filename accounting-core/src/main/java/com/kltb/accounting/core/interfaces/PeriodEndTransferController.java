package com.kltb.accounting.core.interfaces;

import com.kltb.accounting.api.request.TransferExecuteRequest;
import com.kltb.accounting.api.request.TransferRecordQueryRequest;
import com.kltb.accounting.api.response.ApiResponse;
import com.kltb.accounting.api.response.PageResponse;
import com.kltb.accounting.api.response.TransferExecuteBatchResponse;
import com.kltb.accounting.api.response.TransferPreviewResponse;
import com.kltb.accounting.api.response.TransferRecordResponse;
import com.kltb.accounting.core.application.service.PeriodEndTransferApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 期末结转执行与记录 Controller
 */
@RestController
@RequestMapping("/accounting/transfer")
@RequiredArgsConstructor
@Validated
@Tag(name = "期末结转管理", description = "期末结转执行、分录试算预览、历史记录查询接口")
public class PeriodEndTransferController {

    private final PeriodEndTransferApplicationService transferApplicationService;

    @PostMapping("/preview")
    @Operation(summary = "期末结转试算预览", description = "基于指定会计日的日余额模拟匹配结转规则，计算预估生成的借贷分录与平衡性（只读不落库）")
    public ApiResponse<TransferPreviewResponse> previewTransfer(@Valid @RequestBody TransferExecuteRequest request) {
        return ApiResponse.ok(transferApplicationService.previewTransfer(request));
    }

    @PostMapping("/execute")
    @Operation(summary = "手动执行期末结转", description = "按启用规则或指定规则执行期末结转，生成结转凭证并冲销源科目余额")
    public ApiResponse<TransferExecuteBatchResponse> executeTransfer(@Valid @RequestBody TransferExecuteRequest request) {
        return ApiResponse.ok(transferApplicationService.executeTransfer(request));
    }

    @GetMapping("/record/page")
    @Operation(summary = "分页查询结转历史记录", description = "支持按会计日期范围、结转流水号、规则编码、凭证号、执行状态筛选")
    public ApiResponse<PageResponse<TransferRecordResponse>> pageRecords(@Valid TransferRecordQueryRequest request) {
        return ApiResponse.ok(transferApplicationService.pageRecords(request));
    }

    @GetMapping("/record/{transferNo}")
    @Operation(summary = "查询结转记录详情", description = "根据结转流水号查询详情")
    public ApiResponse<TransferRecordResponse> getRecordByTransferNo(
            @Parameter(description = "结转流水号") @PathVariable String transferNo) {
        return ApiResponse.ok(transferApplicationService.getRecordByTransferNo(transferNo));
    }
}
