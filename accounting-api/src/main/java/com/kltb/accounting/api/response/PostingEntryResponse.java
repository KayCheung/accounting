package com.kltb.accounting.api.response;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 过账分录行响应 DTO
 */
@Data
public class PostingEntryResponse {

    /** 分录流水号 */
    private String entryId;

    /** 会计科目编码 */
    private String subjectCode;

    /** 账户编号 */
    private String accountNo;

    /** 借贷方向：1-借,2-贷 */
    private Integer debitCredit;

    /** 金额 */
    private BigDecimal amount;

    /** 状态：1-未过账,2-已过账,3-过账失败 */
    private Integer status;

    /** 状态描述 */
    private String statusDesc;

    /** 是否实时更新 */
    private Integer unilateral;

    /** 是否缓冲入账 */
    private Integer buffered;
}
