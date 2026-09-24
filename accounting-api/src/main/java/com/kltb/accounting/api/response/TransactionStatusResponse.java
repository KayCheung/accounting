package com.kltb.accounting.api.response;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 事务状态查询响应 DTO
 */
@Data
public class TransactionStatusResponse {

    /** 事务编号 */
    private String txnNo;

    /** 事务状态：1-处理中,2-成功,3-失败 */
    private Integer status;

    /** 事务状态描述 */
    private String statusDesc;

    /** 失败原因 */
    private String failReason;

    /** 事务完成时间 */
    private LocalDateTime finishTime;

    /** 关联账户数 */
    private Integer relateAccountCount;

    /** 关联凭证号 */
    private String voucherNo;

    /** 凭证状态 */
    private Integer voucherStatus;

    /** 凭证状态描述 */
    private String voucherStatusDesc;
}
