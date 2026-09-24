package com.kltb.accounting.core.application.dto;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 字典响应 DTO
 */
@Data
@Accessors(chain = true)
public class DictResponse {

    private Long id;
    private String dictType;
    private String dictCode;
    private String dictName;
    private String dictNameEn;
    private Integer sortOrder;
    private String groupKey;
    private Integer status;
    private Boolean system;
    private String extJson;
}
