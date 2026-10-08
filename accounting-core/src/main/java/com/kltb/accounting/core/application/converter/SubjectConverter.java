package com.kltb.accounting.core.application.converter;

import com.kltb.accounting.api.constant.Constants;
import com.kltb.accounting.api.request.SubjectCreateRequest;
import com.kltb.accounting.api.request.SubjectUpdateRequest;
import com.kltb.accounting.core.application.dto.SubjectResponse;
import com.kltb.accounting.core.domain.enums.AvailableStatusEnum;
import com.kltb.accounting.core.domain.enums.DebitCreditEnum;
import com.kltb.accounting.core.domain.enums.SubjectCategoryEnum;
import com.kltb.accounting.core.domain.enums.SubjectNatureEnum;
import com.kltb.accounting.core.infrastructure.persistence.entity.AccountSubjectPO;
import com.kltb.accounting.core.shared.context.TenantContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 科目转换器
 *
 * 是否记账：否
 */
public class SubjectConverter {

    /**
     * 创建请求 → PO
     */
    public static AccountSubjectPO toPO(SubjectCreateRequest request) {
        AccountSubjectPO po = new AccountSubjectPO();
        po.setSubjectCode(request.getSubjectCode());
        po.setSubjectName(request.getSubjectName());
        po.setSubjectLevel(request.getSubjectLevel());
        po.setParentSubjectId(request.getParentSubjectId());
        po.setSubjectCategory(Optional.ofNullable(SubjectCategoryEnum.fromCode(request.getSubjectCategory()))
                .orElse(SubjectCategoryEnum.OFF_BALANCE));
        po.setNature(Optional.ofNullable(SubjectNatureEnum.fromCode(request.getNature()))
                .orElse(SubjectNatureEnum.NORMAL));
        po.setDebitCredit(Optional.ofNullable(DebitCreditEnum.fromCode(request.getDebitCredit()))
                .orElse(DebitCreditEnum.DEBIT));
        po.setLeaf(request.getLeaf() != null && request.getLeaf());
        po.setAllowPost(request.getAllowPost() != null && request.getAllowPost());
        po.setAllowOpenAccount(request.getAllowOpenAccount() != null && request.getAllowOpenAccount());
        po.setStatus(Optional.ofNullable(AvailableStatusEnum.fromCode(request.getStatus()))
                .orElse(AvailableStatusEnum.ENABLED));
        po.setTenantId(TenantContext.get());
        po.setCreateId(Constants.SYSTEM_OPERATOR);
        po.setCreateName(Constants.SYSTEM_OPERATOR);
        po.setUpdateId(Constants.SYSTEM_OPERATOR);
        po.setUpdateName(Constants.SYSTEM_OPERATOR);
        return po;
    }

    /**
     * 更新请求 → PO 字段
     */
    public static void updatePO(SubjectUpdateRequest request, AccountSubjectPO po) {
        if (request.getSubjectName() != null) {
            po.setSubjectName(request.getSubjectName());
        }
        if (request.getSubjectCategory() != null) {
            Optional.ofNullable(SubjectCategoryEnum.fromCode(request.getSubjectCategory()))
                    .ifPresent(po::setSubjectCategory);
        }
        if (request.getNature() != null) {
            Optional.ofNullable(SubjectNatureEnum.fromCode(request.getNature()))
                    .ifPresent(po::setNature);
        }
        if (request.getDebitCredit() != null) {
            Optional.ofNullable(DebitCreditEnum.fromCode(request.getDebitCredit()))
                    .ifPresent(po::setDebitCredit);
        }
        if (request.getLeaf() != null) {
            po.setLeaf(request.getLeaf());
        }
        if (request.getAllowPost() != null) {
            po.setAllowPost(request.getAllowPost());
        }
        if (request.getAllowOpenAccount() != null) {
            po.setAllowOpenAccount(request.getAllowOpenAccount());
        }
        if (request.getStatus() != null) {
            Optional.ofNullable(AvailableStatusEnum.fromCode(request.getStatus()))
                    .ifPresent(po::setStatus);
        }
        po.setUpdateId(Constants.SYSTEM_OPERATOR);
        po.setUpdateName(Constants.SYSTEM_OPERATOR);
    }

    /**
     * PO → 响应 DTO
     */
    public static SubjectResponse toResponse(AccountSubjectPO po) {
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
     * PO 列表 → 响应 DTO 列表
     */
    public static List<SubjectResponse> toResponseList(List<AccountSubjectPO> poList) {
        if (poList == null || poList.isEmpty()) {
            return List.of();
        }
        List<SubjectResponse> result = new ArrayList<>(poList.size());
        for (AccountSubjectPO po : poList) {
            result.add(toResponse(po));
        }
        return result;
    }
}
