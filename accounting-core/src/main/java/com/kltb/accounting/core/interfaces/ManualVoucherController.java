// accounting-core/src/main/java/com/kltb/accounting/core/interfaces/ManualVoucherController.java
package com.kltb.accounting.core.interfaces;

import com.kltb.accounting.api.constant.ResultCode;
import com.kltb.accounting.api.request.*;
import com.kltb.accounting.api.response.ApiResponse;
import com.kltb.accounting.api.response.ManualVoucherApplyDetailResponse;
import com.kltb.accounting.api.response.ManualVoucherApplyPageItemResponse;
import com.kltb.accounting.api.response.PageResponse;
import com.kltb.accounting.core.application.service.ManualVoucherApplicationService;
import com.kltb.accounting.core.infrastructure.persistence.entity.AccountSubjectPO;
import com.kltb.accounting.core.infrastructure.persistence.repository.SubjectRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 手工记账审批流与凭证印签管理 Controller
 */
@RestController
@RequestMapping("/accounting/manual-voucher")
@RequiredArgsConstructor
@Validated
@Tag(name = "手工记账审批流管理", description = "手工凭证创建、初审、复核、记账全生命周期流转管理")
public class ManualVoucherController {

    private final ManualVoucherApplicationService manualVoucherApplicationService;
    private final SubjectRepository subjectRepository;

    /**
     * POST /accounting/manual-voucher/save — 制单填制/保存草稿/提交初审
     */
    @PostMapping("/save")
    @Operation(summary = "制单填制/保存草稿/提交初审", description = "支持新制单或修改驳回单据，action=DRAFT为草稿，action=SUBMIT为直接提交初审")
    public ApiResponse<Map<String, String>> saveOrSubmit(@Valid @RequestBody ManualVoucherApplySaveRequest request) {
        String applyNo = manualVoucherApplicationService.saveOrSubmit(request);
        Map<String, String> res = new HashMap<>();
        res.put("applyNo", applyNo);
        res.put("message", "DRAFT".equalsIgnoreCase(request.getAction()) ? "草稿保存成功" : "已提交初审");
        return ApiResponse.ok(res);
    }

    /**
     * POST /accounting/manual-voucher/audit — 初审把关操作
     */
    @PostMapping("/audit")
    @Operation(summary = "初审操作", description = "初审岗处理：PASS-初审通过流转至复核岗，REJECT-初审驳回退回制单人")
    public ApiResponse<Void> audit(@Valid @RequestBody ManualVoucherApplyAuditRequest request) {
        manualVoucherApplicationService.audit(request);
        return ApiResponse.ok();
    }

    /**
     * POST /accounting/manual-voucher/review — 终审复核操作
     */
    @PostMapping("/review")
    @Operation(summary = "复核操作", description = "复核岗终审：PASS-复核通过流转至待记账，REJECT-复核驳回退回制单人")
    public ApiResponse<Void> review(@Valid @RequestBody ManualVoucherApplyAuditRequest request) {
        manualVoucherApplicationService.review(request);
        return ApiResponse.ok();
    }

    /**
     * POST /accounting/manual-voucher/post — 确认记账入账
     */
    @PostMapping("/post")
    @Operation(summary = "确认记账入账", description = "记账员对已复核通过的手工凭证执行记账：正式写入法定凭证表并联动过账引擎")
    public ApiResponse<Map<String, String>> executeBookkeeping(@Valid @RequestBody ManualVoucherApplyPostRequest request) {
        String voucherNo = manualVoucherApplicationService.executeBookkeeping(request);
        Map<String, String> res = new HashMap<>();
        res.put("voucherNo", voucherNo);
        res.put("message", "记账成功，正式凭证号: " + voucherNo);
        return ApiResponse.ok(res);
    }

    /**
     * POST /accounting/manual-voucher/cancel — 作废申请单
     */
    @PostMapping("/cancel")
    @Operation(summary = "作废申请单", description = "制单人作废草稿或被驳回的申请单")
    public ApiResponse<Void> cancel(@RequestParam String applyNo,
                                    @RequestParam String operatorName,
                                    @RequestParam(required = false) String reason) {
        manualVoucherApplicationService.cancel(applyNo, operatorName, reason);
        return ApiResponse.ok();
    }

    /**
     * GET /accounting/manual-voucher/page — 综合分页检索申请列表
     */
    @GetMapping("/page")
    @Operation(summary = "综合分页检索手工记账申请列表", description = "支持按申请单号、凭证号、状态、制单人、会计日期等多维检索")
    public ApiResponse<PageResponse<ManualVoucherApplyPageItemResponse>> queryPage(@Valid ManualVoucherApplyPageRequest request) {
        PageResponse<ManualVoucherApplyPageItemResponse> response = manualVoucherApplicationService.queryPage(request);
        return ApiResponse.ok(response);
    }

    /**
     * GET /accounting/manual-voucher/detail/{applyNo} — 查询申请全景档案与凭证印签详情
     */
    @GetMapping("/detail/{applyNo}")
    @Operation(summary = "查询申请全景档案与凭证印签详情", description = "包含凭证头、借贷分录、四方印签栏及全生命周期可追溯时间轴")
    @Parameter(name = "applyNo", description = "申请单号", required = true)
    public ApiResponse<ManualVoucherApplyDetailResponse> getDetail(@PathVariable String applyNo) {
        ManualVoucherApplyDetailResponse response = manualVoucherApplicationService.getDetail(applyNo);
        if (response == null) {
            return ApiResponse.fail(ResultCode.DATA_NOT_FOUND, "申请单不存在: " + applyNo);
        }
        return ApiResponse.ok(response);
    }

    /**
     * GET /accounting/manual-voucher/statistics — 看板流转状态统计
     */
    @GetMapping("/statistics")
    @Operation(summary = "看板流转状态统计", description = "统计全部、待初审、待复核、待记账、已记账、被驳回数量")
    public ApiResponse<Map<String, Long>> getStatistics() {
        return ApiResponse.ok(manualVoucherApplicationService.getStatistics());
    }

    /**
     * GET /accounting/manual-voucher/leaf-subjects — 获取所有允许记账的末级科目
     */
    @GetMapping("/leaf-subjects")
    @Operation(summary = "获取允许记账的末级科目列表", description = "用于制单时快速下拉或搜索科目")
    public ApiResponse<List<com.kltb.accounting.api.response.LeafSubjectResponse>> getLeafSubjects() {
        List<AccountSubjectPO> subjects = subjectRepository.selectLeafForPosting();
        List<com.kltb.accounting.api.response.LeafSubjectResponse> list = subjects.stream().map(sub ->
            com.kltb.accounting.api.response.LeafSubjectResponse.builder()
                .subjectCode(sub.getSubjectCode())
                .subjectName(sub.getSubjectName())
                .subjectLevel(sub.getSubjectLevel())
                .balanceDirection(sub.getDebitCredit() != null ? sub.getDebitCredit().getCode() : 1)
                .build()
        ).collect(Collectors.toList());
        return ApiResponse.ok(list);
    }
}
