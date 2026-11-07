package com.kltb.accounting.api.response;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 过账执行结果响应 DTO
 */
@Data
public class PostingExecuteResponse {

    /** 凭证号 */
    private String voucherNo;

    /** 凭证状态：1-未过账,2-过账中,3-已过账,4-过账失败 */
    private Integer voucherStatus;

    /** 凭证状态描述 */
    private String voucherStatusDesc;

    /** 事务编号 */
    private String txnNo;

    /** 事务状态：1-处理中,2-成功,3-失败 */
    private Integer transactionStatus;

    /** 过账分录列表 */
    private List<PostingEntryResponse> entries;

    /** 是否包含异步分录 */
    private Boolean hasAsyncEntries;

    /** 是否包含缓冲分录 */
    private Boolean hasBufferEntries;
}
