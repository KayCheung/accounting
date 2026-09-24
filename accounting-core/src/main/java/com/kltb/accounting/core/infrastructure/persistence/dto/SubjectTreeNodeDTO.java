package com.kltb.accounting.core.infrastructure.persistence.dto;

import com.kltb.accounting.core.infrastructure.persistence.entity.AccountSubjectPO;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 科目树节点DTO，支持递归树形结构
 */
@Data
public class SubjectTreeNodeDTO {

    /** 科目基础信息 */
    private AccountSubjectPO subject;

    /** 子科目列表 */
    private List<SubjectTreeNodeDTO> children = new ArrayList<>();
}
