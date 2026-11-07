package com.kltb.accounting.core.infrastructure.account;

import com.kltb.accounting.core.domain.enums.BalanceDirectionEnum;
import com.kltb.accounting.core.domain.enums.CustomerTypeEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 账户编号与名称规则解析上下文
 * <p>
 * 封装规则生成所需的所有元数据变量，支持任意变量指定长度截取（如 {seq3}、{subjectCode4}）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountRuleContext {

    /** 业务线编码（用于 Redis 计数器命名空间隔离） */
    private String businessCode;

    /** 账户类型编码（如 CASH、SETTLE、贷款本金） */
    private String accountType;

    /** 账户类型名称（如 现金账户、结算账户） */
    private String accountTypeName;

    /** 币种编码（如 CNY、USD） */
    private String currency;

    /** 币种名称（如 人民币、美元） */
    private String currencyName;

    /** 余额/借贷方向枚举 */
    private BalanceDirectionEnum balanceDirection;

    /** 余额/借贷方向编码（1-借, 2-贷） */
    private Integer balanceDirectionCode;

    /** 借贷方向名称（如 借、贷） */
    private String directionName;

    /** 客户类型枚举 */
    private CustomerTypeEnum customerType;

    /** 客户/所有者类型编码（1-个人, 2-企业, 99-其他） */
    private Integer ownerTypeCode;

    /** 客户/所有者类型名称（如 个人、企业、其他） */
    private String ownerTypeName;

    /** 会计科目编码 */
    private String subjectCode;

    /** 会计科目名称 */
    private String subjectName;

    /** 客户/所有者ID */
    private String ownerId;

    /** 客户/所有者姓名或企业名称 */
    private String ownerName;
}
