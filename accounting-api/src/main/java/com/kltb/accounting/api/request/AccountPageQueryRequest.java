package com.kltb.accounting.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.time.LocalDate;

/**
 * 账户分页查询请求
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@Schema(description = "账户分页查询请求")
public class AccountPageQueryRequest extends PageRequest {

    @Schema(description = "账户大类：CUSTOMER-客户账户，INTERNAL-内部账户，空表示全部", example = "CUSTOMER")
    private String accountCategory;

    @Schema(description = "账户编号（支持模糊搜索）", example = "0012026")
    private String accountNo;

    @Schema(description = "账户名称（支持模糊搜索）", example = "贷款本金")
    private String accountName;

    @Schema(description = "所有者/客户ID", example = "CUST001")
    private String ownerId;

    @Schema(description = "所有者类型：1-个人, 2-企业, 99-其他", example = "1")
    private Integer ownerType;

    @Schema(description = "会计科目编码", example = "1001")
    private String subjectCode;

    @Schema(description = "账户类型字典CODE", example = "CASH")
    private String accountType;

    @Schema(description = "币种", example = "CNY")
    private String currency;

    @Schema(description = "账户状态：1-正常, 2-冻结, 3-注销", example = "1")
    private Integer status;

    @Schema(description = "风控状态：1-正常, 2-止入, 3-止出, 4-止入止出", example = "1")
    private Integer riskStatus;

    @Schema(description = "开户起始日期", example = "2026-01-01")
    private LocalDate startDate;

    @Schema(description = "开户截止日期", example = "2026-12-31")
    private LocalDate endDate;
}
