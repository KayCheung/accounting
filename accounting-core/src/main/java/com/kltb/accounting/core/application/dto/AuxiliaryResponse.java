package com.kltb.accounting.core.application.dto;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 辅助核算项响应 DTO
 */
@Data
@Accessors(chain = true)
public class AuxiliaryResponse {

    private Long id;
    private String subjectCode;
    private String auxiliaryType;
    private Boolean required;
    private String defaultAuxCode;
}
