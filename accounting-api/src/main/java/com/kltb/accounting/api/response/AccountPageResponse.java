package com.kltb.accounting.api.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 账户分页查询响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "账户分页查询响应")
public class AccountPageResponse implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "账户主键ID", example = "1001")
    private Long id;

    @Schema(description = "账户编号", example = "0012026030100001")
    private String accountNo;

    @Schema(description = "账户名称", example = "张三-现金账户")
    private String accountName;

    @Schema(description = "所有者/客户ID", example = "CUST001")
    private String ownerId;

    @Schema(description = "所有者类型：1-个人, 2-企业, 99-其他", example = "1")
    private Integer ownerType;

    @Schema(description = "所有者类型描述", example = "个人")
    private String ownerTypeDesc;

    @Schema(description = "会计科目编码", example = "1001")
    private String subjectCode;

    @Schema(description = "会计科目名称", example = "库存现金")
    private String subjectName;

    @Schema(description = "账户类型字典CODE", example = "CASH")
    private String accountType;

    @Schema(description = "账户类型中文名称", example = "现金账户")
    private String accountTypeName;

    @Schema(description = "币种", example = "CNY")
    private String currency;

    @Schema(description = "余额借贷方向：1-借, 2-贷", example = "1")
    private Integer balanceDirection;

    @Schema(description = "余额借贷方向描述", example = "借")
    private String balanceDirectionDesc;

    @Schema(description = "期初余额", example = "0.00")
    private BigDecimal openingBalance;

    @Schema(description = "主账户总余额", example = "5000.00")
    private BigDecimal balance;

    @Schema(description = "可用子账户余额", example = "4000.00")
    private BigDecimal availableBalance;

    @Schema(description = "冻结子账户余额", example = "1000.00")
    private BigDecimal frozenBalance;

    @Schema(description = "账户状态：1-正常, 2-冻结, 3-注销", example = "1")
    private Integer status;

    @Schema(description = "账户状态描述", example = "正常")
    private String statusDesc;

    @Schema(description = "风控状态：1-正常, 2-止入, 3-止出, 4-止入止出", example = "1")
    private Integer riskStatus;

    @Schema(description = "风控状态描述", example = "正常")
    private String riskStatusDesc;

    @Schema(description = "开户请求号", example = "REQ20260301001")
    private String requestNo;

    @Schema(description = "开户日期", example = "2026-03-01")
    private LocalDate openDate;

    @Schema(description = "最后动支日期", example = "2026-03-02")
    private LocalDate inactiveDate;

    @Schema(description = "创建时间", example = "2026-03-01 10:00:00")
    private LocalDateTime createdAt;
}
