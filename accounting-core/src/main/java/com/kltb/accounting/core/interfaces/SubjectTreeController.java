package com.kltb.accounting.core.interfaces;

import com.kltb.accounting.api.request.AuxiliaryCreateRequest;
import com.kltb.accounting.api.request.AuxiliaryUpdateRequest;
import com.kltb.accounting.api.response.ApiResponse;
import com.kltb.accounting.core.application.SubjectTreeApplicationService;
import com.kltb.accounting.core.application.dto.AuxiliaryResponse;
import com.kltb.accounting.core.application.dto.SubjectResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 科目树 + 辅助核算项 Controller
 */
@RestController
@RequestMapping("/accounting/config/subject")
@RequiredArgsConstructor
@Validated
@Tag(name = "科目树管理", description = "科目树形查询与辅助核算项维护接口")
public class SubjectTreeController {

    private final SubjectTreeApplicationService subjectTreeApplicationService;

    @GetMapping("/tree")
    @Operation(summary = "科目树形查询")
    public ApiResponse<List<SubjectResponse>> queryTree(
            @Parameter(name = "parentId", description = "父科目ID（不传或传0→根节点，传值→懒加载）")
            @RequestParam(value = "parentId", required = false) Long parentId,
            @Parameter(name = "status", description = "状态过滤（默认只返回启用，0表示全部）")
            @RequestParam(value = "status", required = false) Integer status,
            @Parameter(name = "subjectCategory", description = "账类过滤（可选）")
            @RequestParam(value = "subjectCategory", required = false) Integer subjectCategory,
            @Parameter(name = "keyword", description = "编码或名称关键字（跨级搜索）")
            @RequestParam(value = "keyword", required = false) String keyword) {
        List<SubjectResponse> result = subjectTreeApplicationService.queryTree(parentId, status, subjectCategory, keyword);
        return ApiResponse.ok(result);
    }

    @PostMapping("/{subjectCode}/auxiliary")
    @Operation(summary = "为科目添加辅助核算项")
    public ApiResponse<Void> createAuxiliary(
            @Parameter(name = "subjectCode", description = "科目编码") @PathVariable String subjectCode,
            @Valid @RequestBody AuxiliaryCreateRequest request) {
        subjectTreeApplicationService.createAuxiliary(subjectCode, request);
        return ApiResponse.ok();
    }

    @DeleteMapping("/{subjectCode}/auxiliary/{auxiliaryType}")
    @Operation(summary = "删除辅助核算项")
    public ApiResponse<Void> deleteAuxiliary(
            @Parameter(name = "subjectCode", description = "科目编码") @PathVariable String subjectCode,
            @Parameter(name = "auxiliaryType", description = "辅助核算项类型") @PathVariable String auxiliaryType) {
        subjectTreeApplicationService.deleteAuxiliary(subjectCode, auxiliaryType);
        return ApiResponse.ok();
    }

    @PutMapping("/{subjectCode}/auxiliary/{auxiliaryType}")
    @Operation(summary = "更新辅助核算项")
    public ApiResponse<Void> updateAuxiliary(
            @Parameter(name = "subjectCode", description = "科目编码") @PathVariable String subjectCode,
            @Parameter(name = "auxiliaryType", description = "辅助核算项类型") @PathVariable String auxiliaryType,
            @Valid @RequestBody AuxiliaryUpdateRequest request) {
        subjectTreeApplicationService.updateAuxiliary(subjectCode, auxiliaryType, request);
        return ApiResponse.ok();
    }

    @GetMapping("/{subjectCode}/auxiliary")
    @Operation(summary = "查询科目的辅助核算项列表")
    public ApiResponse<List<AuxiliaryResponse>> listAuxiliary(
            @Parameter(name = "subjectCode", description = "科目编码") @PathVariable String subjectCode) {
        List<AuxiliaryResponse> result = subjectTreeApplicationService.listAuxiliary(subjectCode);
        return ApiResponse.ok(result);
    }
}
