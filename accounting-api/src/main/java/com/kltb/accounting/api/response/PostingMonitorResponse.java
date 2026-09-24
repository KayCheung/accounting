package com.kltb.accounting.api.response;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 过账监控响应 DTO
 */
@Data
public class PostingMonitorResponse {

    private String no;
    private Integer status;
    private String statusDesc;
    private LocalDate accountingDate;
    private int totalCount;
    private int postedCount;
    private int processingCount;
    private int failedCount;
    private BigDecimal progressPercent;
    private List<PostingDetailInfo> detailList;

    @Data
    public static class PostingDetailInfo {
        private String entryId;
        private String subjectCode;
        private String accountNo;
        private Integer debitCredit;
        private BigDecimal amount;
        private Integer entryStatus;
        private String entryStatusDesc;
        private Integer unilateral;
        private Integer buffered;
    }
}
