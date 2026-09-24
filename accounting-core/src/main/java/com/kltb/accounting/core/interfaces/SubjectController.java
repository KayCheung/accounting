package com.kltb.accounting.core.interfaces;

import com.kltb.accounting.api.request.SubjectCreateRequest;
import com.kltb.accounting.api.request.SubjectQueryRequest;
import com.kltb.accounting.api.request.SubjectUpdateRequest;
import com.kltb.accounting.api.response.ApiResponse;
import com.kltb.accounting.api.response.PageResponse;
import com.kltb.accounting.core.application.SubjectApplicationService;
import com.kltb.accounting.core.application.dto.SubjectResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 科目管理 Controller
 */
@RestController
@RequestMapping("/accounting/config/subject")
@RequiredArgsConstructor
@Validated
@Tag(name = "科目管理", description = "会计科目维护接口")
public class SubjectController {

    private final SubjectApplicationService subjectApplicationService;

    @PostMapping
    @Operation(summary = "创建科目")
    public ApiResponse<Void> create(@Valid @RequestBody SubjectCreateRequest request) {
        subjectApplicationService.create(request);
        return ApiResponse.ok();
    }

    @PutMapping("/{subjectCode}")
    @Operation(summary = "更新科目")
    public ApiResponse<Void> update(
            @Parameter(name = "subjectCode", description = "科目编码") @PathVariable String subjectCode,
            @Valid @RequestBody SubjectUpdateRequest request) {
        subjectApplicationService.update(subjectCode, request);
        return ApiResponse.ok();
    }

    @DeleteMapping("/{subjectCode}")
    @Operation(summary = "停用科目")
    public ApiResponse<Void> disable(
            @Parameter(name = "subjectCode", description = "科目编码") @PathVariable String subjectCode) {
        subjectApplicationService.disable(subjectCode);
        return ApiResponse.ok();
    }

    @GetMapping("/{subjectCode}")
    @Operation(summary = "查询单个科目")
    public ApiResponse<SubjectResponse> getByCode(
            @Parameter(name = "subjectCode", description = "科目编码") @PathVariable String subjectCode) {
        SubjectResponse resp = subjectApplicationService.getByCode(subjectCode);
        return ApiResponse.ok(resp);
    }

    @GetMapping("/page")
    @Operation(summary = "分页查询科目")
    public ApiResponse<PageResponse<SubjectResponse>> page(SubjectQueryRequest request) {
        PageResponse<SubjectResponse> result = subjectApplicationService.page(request);
        return ApiResponse.ok(result);
    }
}
