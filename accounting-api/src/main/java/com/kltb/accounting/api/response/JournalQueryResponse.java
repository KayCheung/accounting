// accounting-api/src/main/java/com/kltb/accounting/api/response/JournalQueryResponse.java
package com.kltb.accounting.api.response;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 业务记账流水查询响应 DTO（面向外部业务系统调用）
 * <p>
 * 仅透出业务方关心的流水标识、事务编号、会计日期、记账处理状态与失败原因等核心结果，
 * 隐藏底层凭证分录、MQ重试及缓冲等系统内部细节。
 */
@Data
public class JournalQueryResponse {

    /** 系统跟踪号 */
    private String traceNo;

    /** 跟踪号序列号 */
    private Integer traceSeq;

    /** 关联事务编号 */
    private String txnNo;

    /** 业务线编码 */
    private String businessCode;

    /** 交易编码 */
    private String tradingCode;

    /** 支付渠道 */
    private String payChannel;

    /** 交易类别代码：1-正常, 2-调账, 3-红冲, 4-蓝字, 5-预冻结, 6-预冻结解冻 */
    private Integer tradeType;

    /** 交易类别描述 */
    private String tradeTypeDesc;

    /** 记账金额 */
    private BigDecimal amount;

    /** 会计日期 */
    private LocalDate accountingDate;

    /** 摘要 */
    private String summary;

    /** 关联原预冻结单号 */
    private String origFreezeNo;

    /** 流水处理状态：1-处理中, 2-成功, 3-失败 */
    private Integer status;

    /** 状态中文描述 */
    private String statusDesc;

    /** 失败原因（失败时非空） */
    private String failReason;

    /** 交易发生时间 */
    private LocalDateTime tradeTime;

    /** 记账完成时间 */
    private LocalDateTime finishTime;
}
