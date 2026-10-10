// accounting-api/src/main/java/com/kltb/accounting/api/response/JournalOverviewResponse.java
package com.kltb.accounting.api.response;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 记账全链路总览响应 DTO
 * <p>
 * 通过 traceNo 聚合展示业务流水、关联事务、记账凭证及借贷分录、资金冻结等全生命周期明细。
 */
@Data
public class JournalOverviewResponse {

    /** 业务流水信息 */
    private RecordInfo record;

    /** 关联事务信息（若已生成事务） */
    private TransactionInfo transaction;

    /** 记账凭证列表（若已生成凭证） */
    private List<VoucherInfo> vouchers;

    /** 关联资金冻结信息（若涉及预冻结或规则入金冻结） */
    private FreezeInfo freeze;

    /** 当前记账流程阶段编码：RECORDED, VOUCHERED, POSTING, SUCCESS, FAILED */
    private String processStage;

    /** 流程阶段描述：流水已登记、凭证已生成、过账处理中、记账完成、记账失败 */
    private String stageDesc;

    /** 记账执行进度百分比：25, 50, 75, 100 */
    private Integer progressPercent;

    /** 是否支持失败重试 */
    private Boolean canRetry;

    /** 是否支持撤销/回滚 */
    private Boolean canRollback;

    /** 并行记账、异步记账、缓冲记账综合结果统计 */
    private PostingSummaryInfo postingSummary;

    @Data
    public static class PostingSummaryInfo {
        /** 实时分录总数 */
        private Integer realtimeTotal;
        /** 实时已过账数 */
        private Integer realtimeSuccessCount;
        /** 异步分录总数 */
        private Integer asyncTotal;
        /** 异步已过账数 */
        private Integer asyncSuccessCount;
        /** 缓冲分录总数 */
        private Integer bufferTotal;
        /** 缓冲已入账数 */
        private Integer bufferSuccessCount;
        /** 异步分录本地消息状态列表 */
        private List<AsyncMessageInfo> asyncMessages;
        /** 缓冲记账明细列表 */
        private List<BufferDetailInfo> bufferDetails;
    }

    @Data
    public static class AsyncMessageInfo {
        private String messageId;
        private String businessKey; // entryId
        private String topic;
        private String tag;
        private Integer status;
        private String statusDesc;
        private Integer retryCount;
        private LocalDateTime nextRetryTime;
        private LocalDateTime createTime;
    }

    @Data
    public static class BufferDetailInfo {
        private Long id;
        private Long ruleId;
        private Integer bufferMode;
        private String bufferModeDesc;
        private String voucherNo;
        private String entryId;
        private String accountNo;
        private BigDecimal amount;
        private Integer changeDirection;
        private Integer status;
        private String statusDesc;
        private LocalDateTime triggerTime;
        private LocalDateTime postTime;
        private String failReason;
    }

    @Data
    public static class RecordInfo {
        private String traceNo;
        private Integer traceSeq;
        private String businessCode;
        private String tradingCode;
        private String payChannel;
        private Integer tradeType;
        private String tradeTypeDesc;
        private BigDecimal amount;
        private LocalDate accountingDate;
        private String summary;
        private String origFreezeNo;
        private Integer status;
        private String statusDesc;
        private LocalDateTime tradeTime;
        private LocalDateTime createTime;
    }

    @Data
    public static class TransactionInfo {
        private String txnNo;
        private Integer status;
        private String statusDesc;
        private String failReason;
        private LocalDateTime finishTime;
        private Integer relateAccountCount;
    }

    @Data
    public static class VoucherInfo {
        private String voucherNo;
        private String voucherType;
        private Integer status;
        private String statusDesc;
        private BigDecimal amount;
        private List<EntryInfo> entries;
    }

    @Data
    public static class EntryInfo {
        private String entryId;
        private Integer rowNum;
        private String subjectCode;
        private String accountNo;
        private Integer debitCredit;
        private String debitCreditDesc;
        private BigDecimal amount;
        private Integer unilateral;
        private Integer buffered;
        private Integer changeDirection;
        private Integer status;
        private String statusDesc;
    }

    @Data
    public static class FreezeInfo {
        private String freezeId;
        private String accountNo;
        private BigDecimal freezeAmount;
        private BigDecimal origFreezeAmount;
        private BigDecimal unfrozenAmount;
        private BigDecimal deductedAmount;
        private LocalDateTime expireTime;
        private Integer status;
        private String statusDesc;
    }
}
