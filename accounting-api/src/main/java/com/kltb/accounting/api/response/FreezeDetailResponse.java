package com.kltb.accounting.api.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 冻结记录响应 DTO
 */
@Data
public class FreezeDetailResponse {

    private String freezeId;

    private String accountNo;

    /**
     * 当前有效冻结金额（剩余冻结金额）
     */
    private BigDecimal freezeAmount;

    /**
     * 初始冻结金额
     */
    private BigDecimal origFreezeAmount;

    /**
     * 累计已解冻金额
     */
    private BigDecimal unfrozenAmount;

    /**
     * 累计已扣款金额
     */
    private BigDecimal deductedAmount;

    private Integer status;

    private String statusDesc;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime expireTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime tradeTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    private String summary;
}
