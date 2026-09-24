package com.kltb.accounting.core.application.converter;

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
import java.util.Map;
import java.util.Optional;

/**
 * 科目转换器
 *
 * 是否记账：否
 */
public class SubjectConverter {

    private static final Map<Integer, SubjectCategoryEnum> CATEGORY_MAP = Map.of(
            0, SubjectCategoryEnum.OFF_BALANCE,
            1, SubjectCategoryEnum.ASSET,
            2, SubjectCategoryEnum.LIABILITY,
            3, SubjectCategoryEnum.EQUITY,
            4, SubjectCategoryEnum.COMMON,
            5, SubjectCategoryEnum.COST,
            6, SubjectCategoryEnum.PROFIT_LOSS
    );

    private static final Map<Integer, SubjectNatureEnum> NATURE_MAP = Map.of(
            1, SubjectNatureEnum.NORMAL,
            2, SubjectNatureEnum.WRITE_OFF,
            3, SubjectNatureEnum.LOAN,
            4, SubjectNatureEnum.CASH
    );

    private static final Map<Integer, DebitCreditEnum> DEBIT_CREDIT_MAP = Map.of(
            1, DebitCreditEnum.DEBIT,
            2, DebitCreditEnum.CREDIT
    );

    /**
     * 创建请求 → PO
     */
    public static AccountSubjectPO toPO(SubjectCreateRequest request) {
        AccountSubjectPO po = new AccountSubjectPO();
        po.setSubjectCode(request.getSubjectCode());
        po.setSubjectName(request.getSubjectName());
        po.setSubjectLevel(request.getSubjectLevel());
        po.setParentSubjectId(request.getParentSubjectId());
        po.setSubjectCategory(CATEGORY_MAP.getOrDefault(request.getSubjectCategory(), SubjectCategoryEnum.OFF_BALANCE));
        po.setNature(NATURE_MAP.getOrDefault(request.getNature(), SubjectNatureEnum.NORMAL));
        po.setDebitCredit(DEBIT_CREDIT_MAP.getOrDefault(request.getDebitCredit(), DebitCreditEnum.DEBIT));
        po.setLeaf(request.getLeaf() != null && request.getLeaf());
        po.setAllowPost(request.getAllowPost() != null && request.getAllowPost());
        po.setAllowOpenAccount(request.getAllowOpenAccount() != null && request.getAllowOpenAccount());
        po.setStatus(request.getStatus() != null && request.getStatus() == 1
                ? AvailableStatusEnum.ENABLED : AvailableStatusEnum.DISABLED);
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
    public static void updatePO(SubjectUpdateRequest request, AccountSubjectPO po) {
        if (request.getSubjectName() != null) {
            po.setSubjectName(request.getSubjectName());
        }
        if (request.getSubjectCategory() != null) {
            po.setSubjectCategory(CATEGORY_MAP.getOrDefault(request.getSubjectCategory(), po.getSubjectCategory()));
        }
        if (request.getNature() != null) {
            po.setNature(NATURE_MAP.getOrDefault(request.getNature(), po.getNature()));
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
            po.setStatus(request.getStatus() == 1 ? AvailableStatusEnum.ENABLED : AvailableStatusEnum.DISABLED);
        }
        po.setUpdateId("system");
        po.setUpdateName("system");
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
