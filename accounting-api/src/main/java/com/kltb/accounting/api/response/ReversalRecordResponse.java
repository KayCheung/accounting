package com.kltb.accounting.api.response;

import com.fasterxml.jackson.annotation.JsonFormat;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 红冲记录响应 DTO
 */
@Data
@Builder
public class ReversalRecordResponse {

    /** 红冲凭证号 */
    private String reversalVoucherNo;

    /** 原凭证号 */
    private String origVoucherNo;

    /** 红冲金额 */
    private BigDecimal amount;

    /** 红冲会计日期 */
    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    private LocalDate accountingDate;

    /** 摘要 */
    private String summary;

    /** 凭证状态编码（3=已过账, 5=已冲销） */
    private Integer status;

    /** 过账时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime postTime;

    /** 记账人姓名 */
    private String bookkeeperName;
}
