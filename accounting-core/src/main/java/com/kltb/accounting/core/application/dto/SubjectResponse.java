package com.kltb.accounting.core.application.dto;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 科目响应 DTO
 */
@Data
@Accessors(chain = true)
public class SubjectResponse {

    private Long id;
    private String subjectCode;
    private String subjectName;
    private Integer subjectLevel;
    private Long parentSubjectId;
    private Integer subjectCategory;
    private Integer nature;
    private Integer debitCredit;
    private Boolean leaf;
    private Boolean allowPost;
    private Boolean allowOpenAccount;
    private Integer status;
    private String parentSubjectCode;
    private String parentSubjectName;
    private Boolean hasChildren;
}
