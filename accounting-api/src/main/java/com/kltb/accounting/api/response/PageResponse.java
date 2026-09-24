// accounting-api/src/main/java/com/kltb/accounting/api/response/PageResponse.java
package com.kltb.accounting.api.response;

import lombok.Builder;
import lombok.Getter;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 统一分页响应体
 *
 * @param <T> 列表元素类型
 */
@Getter
@Builder
public class PageResponse<T> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 总记录数 */
    private final Long total;

    /** 总页数 */
    private final Long pages;

    /** 当前页码 */
    private final Long current;

    /** 当前页数据 */
    private final List<T> list;
}
