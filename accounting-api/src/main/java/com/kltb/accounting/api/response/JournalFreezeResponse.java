// accounting-api/src/main/java/com/kltb/accounting/api/response/JournalFreezeResponse.java
package com.kltb.accounting.api.response;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 业务预冻结响应 DTO
 */
@Data
public class JournalFreezeResponse {

    /** 系统跟踪号 */
    private String traceNo;

    /** 冻结单号（freeze_id） */
    private String freezeId;

    /** 冻结账户编号 */
    private String accountNo;

    /** 冻结金额 */
    private BigDecimal freezeAmount;

    /** 冻结过期时间 */
    private LocalDateTime expireTime;

    /** 会计日期 */
    private LocalDate accountingDate;

    /** 冻结状态：1-冻结, 2-已解冻 */
    private Integer status;

    /** 状态描述 */
    private String statusDesc;

    /** 多账户冻结明细列表（支持多借多贷多账户场景） */
    private java.util.List<FreezeItem> freezeItems;

    @Data
    public static class FreezeItem {
        private String freezeId;
        private String accountNo;
        private String customerId;
        private String fundsType;
        private BigDecimal freezeAmount;
        private LocalDateTime expireTime;
        private Integer status;
        private String statusDesc;
    }
}
