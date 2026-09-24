package com.kltb.accounting.core.application.converter;

import com.kltb.accounting.api.request.AuxiliaryCreateRequest;
import com.kltb.accounting.api.request.AuxiliaryUpdateRequest;
import com.kltb.accounting.core.application.dto.AuxiliaryResponse;
import com.kltb.accounting.core.application.dto.SubjectResponse;
import com.kltb.accounting.core.domain.enums.AvailableStatusEnum;
import com.kltb.accounting.core.domain.enums.DebitCreditEnum;
import com.kltb.accounting.core.domain.enums.SubjectCategoryEnum;
import com.kltb.accounting.core.domain.enums.SubjectNatureEnum;
import com.kltb.accounting.core.infrastructure.persistence.entity.AccountSubjectAuxiliaryPO;
import com.kltb.accounting.core.infrastructure.persistence.entity.AccountSubjectPO;
import com.kltb.accounting.core.shared.context.TenantContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 科目树 + 辅助核算项转换器
 *
 * 是否记账：否
 */
public class SubjectTreeConverter {

    /**
     * PO → 科目树响应 DTO
     */
    public static SubjectResponse toSubjectResponse(AccountSubjectPO po) {
        SubjectResponse resp = new SubjectResponse();
        resp.setId(po.getId());
        resp.setSubjectCode(po.getSubjectCode());
        resp.setSubjectName(po.getSubjectName());
        resp.setSubjectLevel(po.getSubjectLevel());
        resp.setParentSubjectId(po.getParentSubjectId());
        resp.setSubjectCategory(Optional.ofNullable(po.getSubjectCategory()).map(SubjectCategoryEnum::getCode).orElse(null));
        resp.setNature(Optional.ofNullable(po.getNature()).map(SubjectNatureEnum::getCode).orElse(null));
        resp.setDebitCredit(Optional.ofNullable(po.getDebitCredit()).map(DebitCreditEnum::getCode).orElse(null));
        resp.setLeaf(po.getLeaf());
        resp.setAllowPost(po.getAllowPost());
        resp.setAllowOpenAccount(po.getAllowOpenAccount());
        resp.setStatus(Optional.ofNullable(po.getStatus()).map(AvailableStatusEnum::getCode).orElse(null));
        resp.setHasChildren(!Boolean.TRUE.equals(po.getLeaf()));
        return resp;
    }

    /**
     * PO 列表 → 科目树响应 DTO 列表
     */
    public static List<SubjectResponse> toSubjectResponseList(List<AccountSubjectPO> poList) {
        if (poList == null || poList.isEmpty()) {
            return List.of();
        }
        List<SubjectResponse> result = new ArrayList<>(poList.size());
        for (AccountSubjectPO po : poList) {
            result.add(toSubjectResponse(po));
        }
        return result;
    }

    /**
     * 创建请求 → PO
     */
    public static AccountSubjectAuxiliaryPO toAuxiliaryPO(AuxiliaryCreateRequest request, String subjectCode) {
        AccountSubjectAuxiliaryPO po = new AccountSubjectAuxiliaryPO();
        po.setSubjectCode(subjectCode);
        po.setAuxiliaryType(request.getAuxiliaryType());
        po.setRequired(request.getRequired());
        po.setDefaultAuxCode(request.getDefaultAuxCode());
        po.setTenantId(TenantContext.get());
        po.setCreateId("system");
        po.setCreateName("system");
        po.setUpdateId("system");
        po.setUpdateName("system");
        return po;
    }

    /**
     * 更新请求 → PO 字段
     */
    public static void updateAuxiliaryPO(AuxiliaryUpdateRequest request, AccountSubjectAuxiliaryPO po) {
        if (request.getRequired() != null) {
            po.setRequired(request.getRequired());
        }
        if (request.getDefaultAuxCode() != null) {
            po.setDefaultAuxCode(request.getDefaultAuxCode());
        }
        po.setUpdateId("system");
        po.setUpdateName("system");
    }

    /**
     * PO → 辅助核算项响应 DTO
     */
    public static AuxiliaryResponse toAuxiliaryResponse(AccountSubjectAuxiliaryPO po) {
        AuxiliaryResponse resp = new AuxiliaryResponse();
        resp.setId(po.getId());
        resp.setSubjectCode(po.getSubjectCode());
        resp.setAuxiliaryType(po.getAuxiliaryType());
        resp.setRequired(po.getRequired());
        resp.setDefaultAuxCode(po.getDefaultAuxCode());
        return resp;
    }

    /**
     * PO 列表 → 辅助核算项响应 DTO 列表
     */
    public static List<AuxiliaryResponse> toAuxiliaryResponseList(List<AccountSubjectAuxiliaryPO> poList) {
        if (poList == null || poList.isEmpty()) {
            return List.of();
        }
        List<AuxiliaryResponse> result = new ArrayList<>(poList.size());
        for (AccountSubjectAuxiliaryPO po : poList) {
            result.add(toAuxiliaryResponse(po));
        }
        return result;
    }
}
