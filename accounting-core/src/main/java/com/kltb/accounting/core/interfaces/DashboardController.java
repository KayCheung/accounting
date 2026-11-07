package com.kltb.accounting.core.interfaces;

import com.kltb.accounting.api.response.ApiResponse;
import com.kltb.accounting.api.response.DashboardOverviewResponse;
import com.kltb.accounting.core.application.service.DashboardApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 系统概览与运营看板 Controller
 */
@RestController
@RequestMapping("/accounting/dashboard")
@RequiredArgsConstructor
@Validated
@Tag(name = "系统概览", description = "系统概览大盘与多角色待办中心接口")
public class DashboardController {

    private final DashboardApplicationService dashboardApplicationService;

    @GetMapping("/overview")
    @Operation(summary = "查询系统概览大盘与待办数据", description = "聚合返回当前会计日、日切状态、试算平衡指示灯、各角色待办数量、异常治理指标及今日业务量")
    public ApiResponse<DashboardOverviewResponse> getOverview() {
        DashboardOverviewResponse response = dashboardApplicationService.getDashboardOverview();
        return ApiResponse.ok(response);
    }
}
