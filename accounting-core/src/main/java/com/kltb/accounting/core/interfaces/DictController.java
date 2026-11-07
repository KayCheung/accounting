package com.kltb.accounting.core.interfaces;

import com.kltb.accounting.api.request.DictCreateRequest;
import com.kltb.accounting.api.request.DictQueryRequest;
import com.kltb.accounting.api.request.DictUpdateRequest;
import com.kltb.accounting.api.response.ApiResponse;
import com.kltb.accounting.api.response.PageResponse;
import com.kltb.accounting.core.application.DictApplicationService;
import com.kltb.accounting.core.application.dto.DictResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 字典管理 Controller
 */
@RestController
@RequestMapping("/accounting/config/dict")
@RequiredArgsConstructor
@Validated
@Tag(name = "字典管理", description = "系统字典维护接口")
public class DictController {

    private final DictApplicationService dictApplicationService;

    @PostMapping
    @Operation(summary = "创建字典项")
    public ApiResponse<Void> create(@Valid @RequestBody DictCreateRequest request) {
        dictApplicationService.create(request);
        return ApiResponse.ok();
    }

    @PutMapping("/{dictType}/{dictCode}")
    @Operation(summary = "更新字典项")
    public ApiResponse<Void> update(
            @PathVariable String dictType,
            @PathVariable String dictCode,
            @Valid @RequestBody DictUpdateRequest request) {
        dictApplicationService.update(dictType, dictCode, request);
        return ApiResponse.ok();
    }

    @DeleteMapping("/{dictType}/{dictCode}")
    @Operation(summary = "删除字典项")
    public ApiResponse<Void> delete(
            @PathVariable String dictType,
            @PathVariable String dictCode) {
        dictApplicationService.delete(dictType, dictCode);
        return ApiResponse.ok();
    }

    @GetMapping("/{dictType}")
    @Operation(summary = "按类型查询字典列表")
    public ApiResponse<List<DictResponse>> listByType(
            @Parameter(name = "dictType", description = "字典类型编码") @PathVariable String dictType) {
        List<DictResponse> list = dictApplicationService.listByType(dictType);
        return ApiResponse.ok(list);
    }

    @GetMapping("/page")
    @Operation(summary = "分页查询字典")
    public ApiResponse<PageResponse<DictResponse>> page(DictQueryRequest request) {
        PageResponse<DictResponse> result = dictApplicationService.page(request);
        return ApiResponse.ok(result);
    }

    @PostMapping("/cache/refresh")
    @Operation(summary = "手动刷新字典缓存")
    public ApiResponse<Void> refreshCache(
            @RequestParam(value = "dictType", required = false) String dictType) {
        dictApplicationService.refreshCache(dictType);
        return ApiResponse.ok();
    }
}
