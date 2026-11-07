package com.kltb.accounting.core.domain.model;

import com.kltb.accounting.api.request.JournalDetailRequest;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 流水持久化领域命令对象（封装长参数列表）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JournalCreateCommand {

    /** 跟踪号 */
    private String traceNo;

    /** 跟踪序号 */
    private Integer traceSeq;

    /** 业务线编码 */
    private String businessCode;

    /** 交易编码 */
    private String tradingCode;

    /** 支付渠道 */
    private String payChannel;

    /** 交易类型 */
    private Integer tradeType;

    /** 交易总金额 */
    private BigDecimal amount;

    /** 交易发生时间 */
    private LocalDateTime tradeTime;

    /** 摘要说明 */
    private String summary;

    /** 款项明细列表 */
    private List<JournalDetailRequest> details;

    /** 会计日期 */
    private LocalDate accountingDate;

    /** 原预冻结单号（选填） */
    private String origFreezeNo;

    /** 扩展业务属性（选填） */
    private Map<String, Object> extraAttrs;
}
