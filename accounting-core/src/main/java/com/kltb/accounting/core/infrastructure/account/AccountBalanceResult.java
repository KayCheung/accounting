package com.kltb.accounting.core.infrastructure.account;

import com.kltb.accounting.core.domain.enums.BalanceDirectionEnum;
import com.kltb.accounting.core.domain.enums.ChangeDirectionEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 账户余额计算结果载体
 * <p>
 * 封装单向、双向及表外账户入账试算后的权威三元组：
 * 1. newBalance: 新余额绝对值（始终 >= 0，遵守绝对值法则）
 * 2. newDirection: 新余额方向（0-平/无, 1-借, 2-贷）
 * 3. changeDirection: 本次入账产生的增减效果（1-增, 2-减，用于流水明细和风控校验）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountBalanceResult {

    /**
     * 变更后新余额（始终 >= 0）
     */
    private BigDecimal newBalance;

    /**
     * 变更后新余额方向
     */
    private BalanceDirectionEnum newDirection;

    /**
     * 实际生效的增减方向
     */
    private ChangeDirectionEnum changeDirection;
}
