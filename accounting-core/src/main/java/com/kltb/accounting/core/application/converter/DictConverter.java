package com.kltb.accounting.core.application.converter;

import com.kltb.accounting.api.request.DictCreateRequest;
import com.kltb.accounting.api.request.DictUpdateRequest;
import com.kltb.accounting.core.application.dto.DictResponse;
import com.kltb.accounting.core.domain.enums.AvailableStatusEnum;
import com.kltb.accounting.core.infrastructure.persistence.entity.DictionaryPO;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 字典转换器
 *
 * 是否记账：否
 *
 * 负责 DictionaryPO 与请求/响应 DTO 之间的转换。
 */
public class DictConverter {

    /**
     * 创建请求 → PO
     *
     * @param request 创建请求
     * @return PO 对象
     */
    public static DictionaryPO toPO(DictCreateRequest request) {
        DictionaryPO po = new DictionaryPO();
        po.setDictType(request.getDictType());
        po.setDictCode(request.getDictCode());
        po.setDictName(request.getDictName());
        po.setDictNameEn(request.getDictNameEn());
        po.setSortOrder(request.getSortOrder() != null ? request.getSortOrder() : 0);
        po.setGroupKey(request.getGroupKey());
        po.setStatus(request.getStatus() != null && request.getStatus() == 1
                ? AvailableStatusEnum.ENABLED : AvailableStatusEnum.DISABLED);
        po.setSystem(false);
        po.setExtJson(request.getExtJson());
        po.setCreateId("system");
        po.setCreateName("system");
        po.setUpdateId("system");
        po.setUpdateName("system");
        return po;
    }

    /**
     * 更新请求 → PO 字段（仅填充可修改字段）
     *
     * @param request 更新请求
     * @param po      已存在的 PO 对象
     */
    public static void updatePO(DictUpdateRequest request, DictionaryPO po) {
        if (request.getDictName() != null) {
            po.setDictName(request.getDictName());
        }
        if (request.getDictNameEn() != null) {
            po.setDictNameEn(request.getDictNameEn());
        }
        if (request.getSortOrder() != null) {
            po.setSortOrder(request.getSortOrder());
        }
        if (request.getGroupKey() != null) {
            po.setGroupKey(request.getGroupKey());
        }
        if (request.getStatus() != null) {
            po.setStatus(request.getStatus() == 1 ? AvailableStatusEnum.ENABLED : AvailableStatusEnum.DISABLED);
        }
        if (request.getExtJson() != null) {
            po.setExtJson(request.getExtJson());
        }
        po.setUpdateId("system");
        po.setUpdateName("system");
    }

    /**
     * PO → 响应 DTO
     *
     * @param po PO 对象
     * @return 响应 DTO
     */
    public static DictResponse toResponse(DictionaryPO po) {
        DictResponse resp = new DictResponse();
        resp.setId(po.getId());
        resp.setDictType(po.getDictType());
        resp.setDictCode(po.getDictCode());
        resp.setDictName(po.getDictName());
        resp.setDictNameEn(po.getDictNameEn());
        resp.setSortOrder(po.getSortOrder());
        resp.setGroupKey(po.getGroupKey());
        resp.setStatus(Optional.ofNullable(po.getStatus()).map(AvailableStatusEnum::getCode).orElse(null));
        resp.setSystem(po.getSystem());
        resp.setExtJson(po.getExtJson());
        return resp;
    }

    /**
     * PO 列表 → 响应 DTO 列表
     */
    public static List<DictResponse> toResponseList(List<DictionaryPO> poList) {
        if (poList == null || poList.isEmpty()) {
            return List.of();
        }
        List<DictResponse> result = new ArrayList<>(poList.size());
        for (DictionaryPO po : poList) {
            result.add(toResponse(po));
        }
        return result;
    }
}
