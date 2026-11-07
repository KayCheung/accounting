// accounting-api/src/main/java/com/kltb/accounting/api/response/JournalUnfreezeResponse.java
package com.kltb.accounting.api.response;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 业务预冻结全额解冻（撤销）响应 DTO
 */
@Data
public class JournalUnfreezeResponse {

    /** 本次解冻撤销的系统跟踪号 */
    private String traceNo;

    /** 原预冻结业务流水跟踪号 */
    private String origTraceNo;

    /** 冻结单号（freeze_id） */
    private String freezeId;

    /** 账户编号 */
    private String accountNo;

    /** 全额解冻金额 */
    private BigDecimal unfreezeAmount;

    /** 解冻后状态：2-已解冻 */
    private Integer status;

    /** 状态描述 */
    private String statusDesc;

    /** 多账户解冻明细列表 */
    private java.util.List<UnfreezeItem> unfreezeItems;

    @Data
    public static class UnfreezeItem {
        private String freezeId;
        private String accountNo;
        private BigDecimal unfreezeAmount;
        private Integer status;
        private String statusDesc;
    }
}
