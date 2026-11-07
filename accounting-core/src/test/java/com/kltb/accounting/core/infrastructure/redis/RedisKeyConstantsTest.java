package com.kltb.accounting.core.infrastructure.redis;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * RedisKeyConstants 单元测试
 */
class RedisKeyConstantsTest {

    @Test
    @DisplayName("Lock.Account: 生成统一账户互斥锁")
    void testAccountLock() {
        String key = RedisKeyConstants.Lock.Account.accountMutex("ACC_1001");
        assertThat(key).isEqualTo("account:ACC_1001");

        assertThatThrownBy(() -> RedisKeyConstants.Lock.Account.accountMutex(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RedisKeyConstants.Lock.Account.accountMutex("  "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Lock.Voucher: 生成统一凭证互斥锁")
    void testVoucherLock() {
        String key = RedisKeyConstants.Lock.Voucher.voucherMutex("VOU_20260611_0001");
        assertThat(key).isEqualTo("voucher:VOU_20260611_0001");

        assertThatThrownBy(() -> RedisKeyConstants.Lock.Voucher.voucherMutex(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RedisKeyConstants.Lock.Voucher.voucherMutex(" "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Lock.AccountOpen: 生成外部与内部开户防重锁")
    void testAccountOpenLock() {
        String extKey = RedisKeyConstants.Lock.AccountOpen.of("USER_88", "100101");
        assertThat(extKey).isEqualTo("account:open:USER_88:100101");

        String innerKey = RedisKeyConstants.Lock.AccountOpen.ofInner("100201");
        assertThat(innerKey).isEqualTo("account:open:INNER:100201");
    }

    @Test
    @DisplayName("Lock.Idempotent: 生成交易流水幂等锁")
    void testIdempotentLock() {
        String key = RedisKeyConstants.Lock.Idempotent.trace("TRX20260512", "1");
        assertThat(key).isEqualTo("idempotent:trace:TRX20260512-1");

        String keyWithNullSeq = RedisKeyConstants.Lock.Idempotent.trace("TRX20260512", null);
        assertThat(keyWithNullSeq).isEqualTo("idempotent:trace:TRX20260512-");
    }

    @Test
    @DisplayName("Cache: 生成字典缓存 Key")
    void testCacheKeys() {
        String dictKey = RedisKeyConstants.Cache.dictKey(1001, "PAY_CHANNEL");
        assertThat(dictKey).isEqualTo("accounting:1001:dict:PAY_CHANNEL");

        assertThat(RedisKeyConstants.Cache.ACCOUNTING_DATE_CURRENT)
                .isEqualTo("accounting:date:current");
    }

    @Test
    @DisplayName("Sequence: 生成各发号器 Key")
    void testSequenceKeys() {
        assertThat(RedisKeyConstants.Sequence.extAccount("TPL01", "20260611"))
                .isEqualTo("account:seq:ext:TPL01:20260611");

        assertThat(RedisKeyConstants.Sequence.innerAccount("100101"))
                .isEqualTo("account:seq:inner:100101");

        assertThat(RedisKeyConstants.Sequence.freeze("20260611"))
                .isEqualTo("frz:seq:20260611");

        assertThat(RedisKeyConstants.Sequence.transaction("20260611"))
                .isEqualTo("txn:seq:20260611");

        assertThat(RedisKeyConstants.Sequence.common("VOU", "20260611"))
                .isEqualTo("vou:seq:20260611");
    }

    @Test
    @DisplayName("PubSub: 广播通知频道常量")
    void testPubSubKeys() {
        assertThat(RedisKeyConstants.PubSub.ACCOUNTING_DATE_NOTIFY)
                .isEqualTo("accounting:date:notify");
    }
}
