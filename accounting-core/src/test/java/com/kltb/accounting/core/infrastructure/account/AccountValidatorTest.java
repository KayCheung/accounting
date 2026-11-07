package com.kltb.accounting.core.infrastructure.account;

import com.kltb.accounting.api.constant.ResultCode;
import com.kltb.accounting.core.domain.enums.AccountStatusEnum;
import com.kltb.accounting.core.domain.enums.RiskStatusEnum;
import com.kltb.accounting.core.infrastructure.persistence.entity.AccountPO;
import com.kltb.accounting.core.shared.exception.AccountException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccountValidatorTest {

    @Test
    @DisplayName("账户为空校验")
    void validate_nullAccount_shouldThrow() {
        assertThatThrownBy(() -> AccountValidator.validatePostable(null))
                .isInstanceOf(AccountException.class)
                .satisfies(ex -> assertThat(((AccountException) ex).getResultCode()).isEqualTo(ResultCode.ACCOUNT_NOT_FOUND));

        assertThatThrownBy(() -> AccountValidator.validateNormal(null))
                .isInstanceOf(AccountException.class)
                .satisfies(ex -> assertThat(((AccountException) ex).getResultCode()).isEqualTo(ResultCode.ACCOUNT_NOT_FOUND));

        assertThatThrownBy(() -> AccountValidator.validateExists(null, "A001"))
                .isInstanceOf(AccountException.class)
                .satisfies(ex -> assertThat(((AccountException) ex).getResultCode()).isEqualTo(ResultCode.ACCOUNT_NOT_FOUND));

        assertThat(AccountValidator.isPostable(null)).isFalse();
    }

    @Test
    @DisplayName("正常账户校验: 通过")
    void validatePostable_normal_shouldPass() {
        AccountPO account = new AccountPO().setAccountNo("A001").setStatus(AccountStatusEnum.NORMAL).setRiskStatus(RiskStatusEnum.NORMAL);
        assertThatCode(() -> AccountValidator.validatePostable(account)).doesNotThrowAnyException();
        assertThatCode(() -> AccountValidator.validatePostable(account, 1)).doesNotThrowAnyException();
        assertThatCode(() -> AccountValidator.validatePostable(account, 2)).doesNotThrowAnyException();
        assertThatCode(() -> AccountValidator.validateNormal(account)).doesNotThrowAnyException();
        assertThat(AccountValidator.isPostable(account)).isTrue();
    }

    @Test
    @DisplayName("冻结账户校验: 抛出 ACCOUNT_FROZEN")
    void validatePostable_frozen_shouldThrow() {
        AccountPO account = new AccountPO().setAccountNo("A001").setStatus(AccountStatusEnum.FROZEN);
        assertThatThrownBy(() -> AccountValidator.validatePostable(account))
                .isInstanceOf(AccountException.class)
                .satisfies(ex -> assertThat(((AccountException) ex).getResultCode()).isEqualTo(ResultCode.ACCOUNT_FROZEN));

        assertThatThrownBy(() -> AccountValidator.validateNormal(account))
                .isInstanceOf(AccountException.class)
                .satisfies(ex -> assertThat(((AccountException) ex).getResultCode()).isEqualTo(ResultCode.ACCOUNT_FROZEN));

        assertThat(AccountValidator.isPostable(account)).isFalse();
    }

    @Test
    @DisplayName("注销账户校验: 抛出 ACCOUNT_CANCELLED")
    void validatePostable_cancelled_shouldThrow() {
        AccountPO account = new AccountPO().setAccountNo("A001").setStatus(AccountStatusEnum.CANCELLED);
        assertThatThrownBy(() -> AccountValidator.validatePostable(account))
                .isInstanceOf(AccountException.class)
                .satisfies(ex -> assertThat(((AccountException) ex).getResultCode()).isEqualTo(ResultCode.ACCOUNT_CANCELLED));

        assertThatThrownBy(() -> AccountValidator.validateNormal(account))
                .isInstanceOf(AccountException.class)
                .satisfies(ex -> assertThat(((AccountException) ex).getResultCode()).isEqualTo(ResultCode.ACCOUNT_CANCELLED));

        assertThat(AccountValidator.isPostable(account)).isFalse();
    }

    @Test
    @DisplayName("风控止入校验: 增加方向拦截，减少方向放行")
    void validatePostable_riskNoIn_shouldBlockIncrease() {
        AccountPO account = new AccountPO().setAccountNo("A001").setStatus(AccountStatusEnum.NORMAL).setRiskStatus(RiskStatusEnum.NO_IN);

        // 增 (入账) -> 拦截
        assertThatThrownBy(() -> AccountValidator.validatePostable(account, 1))
                .isInstanceOf(AccountException.class)
                .satisfies(ex -> assertThat(((AccountException) ex).getResultCode()).isEqualTo(ResultCode.ACCOUNT_RISK_BLOCKED));

        // 减 (出账) -> 放行
        assertThatCode(() -> AccountValidator.validatePostable(account, 2)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("风控止出校验: 减少方向拦截，增加方向放行")
    void validatePostable_riskNoOut_shouldBlockDecrease() {
        AccountPO account = new AccountPO().setAccountNo("A001").setStatus(AccountStatusEnum.NORMAL).setRiskStatus(RiskStatusEnum.NO_OUT);

        // 减 (出账) -> 拦截
        assertThatThrownBy(() -> AccountValidator.validatePostable(account, 2))
                .isInstanceOf(AccountException.class)
                .satisfies(ex -> assertThat(((AccountException) ex).getResultCode()).isEqualTo(ResultCode.ACCOUNT_RISK_BLOCKED));

        // 增 (入账) -> 放行
        assertThatCode(() -> AccountValidator.validatePostable(account, 1)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("风控止入止出校验: 增减双向均拦截")
    void validatePostable_riskNoInOut_shouldBlockBoth() {
        AccountPO account = new AccountPO().setAccountNo("A001").setStatus(AccountStatusEnum.NORMAL).setRiskStatus(RiskStatusEnum.NO_IN_OUT);

        assertThatThrownBy(() -> AccountValidator.validatePostable(account, 1))
                .isInstanceOf(AccountException.class)
                .satisfies(ex -> assertThat(((AccountException) ex).getResultCode()).isEqualTo(ResultCode.ACCOUNT_RISK_BLOCKED));

        assertThatThrownBy(() -> AccountValidator.validatePostable(account, 2))
                .isInstanceOf(AccountException.class)
                .satisfies(ex -> assertThat(((AccountException) ex).getResultCode()).isEqualTo(ResultCode.ACCOUNT_RISK_BLOCKED));
    }

    @Test
    @DisplayName("资金冻结可执行校验")
    void validateFreezable_shouldCheckStatus() {
        AccountPO normalAccount = new AccountPO().setAccountNo("A001").setStatus(AccountStatusEnum.NORMAL);
        assertThatCode(() -> AccountValidator.validateFreezable(normalAccount)).doesNotThrowAnyException();

        AccountPO frozenAccount = new AccountPO().setAccountNo("A001").setStatus(AccountStatusEnum.FROZEN);
        assertThatThrownBy(() -> AccountValidator.validateFreezable(frozenAccount))
                .isInstanceOf(AccountException.class)
                .satisfies(ex -> assertThat(((AccountException) ex).getResultCode()).isEqualTo(ResultCode.ACCOUNT_FROZEN_CANNOT_FREEZE));
    }
}
