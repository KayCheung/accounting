package com.kltb.accounting.core.infrastructure.redis;

import cn.hutool.core.util.StrUtil;

/**
 * 全局 Redis Key 与分布式锁统一常量管理
 *
 * <p>设计核心：
 * <ol>
 *   <li><b>实体互斥优先原则 (Entity Mutex Domain)</b>：
 *       分布式锁的核心职责是保护共享数据实体的并发一致性。
 *       凡是针对同一实体的并发写操作（如账户的资金变动、状态变更、缓冲入账；凭证的过账与红冲），
 *       必须统一收敛使用同一把实体互斥锁，严禁按业务动作分散命名导致锁互斥失效！</li>
 *   <li><b>命名空间结构化</b>：
 *       清晰划分 Lock（分布式锁）、Cache（二级缓存）、Sequence（发号器）、PubSub（广播通知）。</li>
 *   <li><b>禁止硬编码</b>：
 *       所有业务 Service 与基础设施组件必须统一通过本类方法构建 Key，杜绝代码中散落字符串拼接。</li>
 * </ol>
 */
public final class RedisKeyConstants {

    private RedisKeyConstants() {
        // 工具常量类禁止实例化
    }

    /**
     * =========================================================================
     * 一、分布式锁 Key (Lock)
     * 说明：所有锁 Key 传给 DistributedLockTemplate，
     * 框架层会自动补齐 "accounting:{tenantId}:lock:" 前缀实现租户隔离。
     * =========================================================================
     */
    public static final class Lock {

        private Lock() {}

        /**
         * 【账户实体互斥域 (Account Mutex Domain)】
         * <p>互斥实体：{@code accountNo}
         * <p>保护场景：
         * <ul>
         *   <li>资金变动：可用资金冻结、解冻、冻结扣款 (FreezeDomainService)</li>
         *   <li>状态变更：账户冻结、解冻、销户注销 (AccountStatusChangeDomainService)</li>
         *   <li>缓冲入账：缓冲记账引擎按账户汇总与明细过账 (BufferPostingEngineDomainService)</li>
         * </ul>
         *
         * <p>⚠️ 财务并发核心约束：
         * 过去系统中拆分为 account:status:、account:fund:、account:buffer:，导致注销账户与资金变动、缓冲入账无法互斥，
         * 极易产生"已注销账户仍被入账"或"校验零余额通过后并发扣款"的严重财务事故。
         * 此处统一收敛为 {@code account:{accountNo}} 互斥锁，彻底堵死并发穿透漏洞。
         */
        public static final class Account {

            private Account() {}

            /** 统一账户级互斥锁模式 */
            private static final String ACCOUNT_MUTEX_PATTERN = "account:%s";

            /**
             * 获取账户级统一互斥锁 Key
             *
             * @param accountNo 账户编号
             * @return 业务锁 Key（如 account:ACC1001）
             */
            public static String accountMutex(String accountNo) {
                if (StrUtil.isBlank(accountNo)) {
                    throw new IllegalArgumentException("accountNo 不能为空");
                }
                return String.format(ACCOUNT_MUTEX_PATTERN, accountNo.trim());
            }
        }

        /**
         * 【凭证/交易实体互斥域 (Voucher Mutex Domain)】
         * <p>互斥实体：{@code voucherNo}
         * <p>保护场景：
         * <ul>
         *   <li>凭证实时/异步过账 (PostingApplicationService)</li>
         *   <li>凭证业务红冲冲销 (ReversalDomainService)</li>
         * </ul>
         *
         * <p>⚠️ 财务并发核心约束：
         * 过去过账使用 posting:trx:{voucherNo}，红冲使用 reversal:{voucherNo}，导致同一张凭证在过账中途
         * 可能被并发红冲，引发状态混乱与重复记账。此处统一收敛为 {@code voucher:{voucherNo}} 互斥锁。
         */
        public static final class Voucher {

            private Voucher() {}

            /** 统一凭证生命周期互斥锁模式 */
            private static final String VOUCHER_MUTEX_PATTERN = "voucher:%s";

            /**
             * 获取凭证级统一互斥锁 Key
             *
             * @param voucherNo 凭证编号
             * @return 业务锁 Key（如 voucher:VOU20260512000001）
             */
            public static String voucherMutex(String voucherNo) {
                if (StrUtil.isBlank(voucherNo)) {
                    throw new IllegalArgumentException("voucherNo 不能为空");
                }
                return String.format(VOUCHER_MUTEX_PATTERN, voucherNo.trim());
            }
        }

        /**
         * 【开户防重互斥域 (Account Opening Mutex Domain)】
         * <p>互斥实体：{@code ownerId} + {@code subjectCode}
         * <p>保护场景：
         * <ul>
         *   <li>外部客户基于模板开户防重复并发提交</li>
         *   <li>内部机构账户初始化防重复开户</li>
         * </ul>
         */
        public static final class AccountOpen {

            private AccountOpen() {}

            private static final String OPEN_LOCK_PATTERN = "account:open:%s:%s";
            public static final String INNER_OWNER = "INNER";

            /**
             * 外部客户开户锁 Key
             *
             * @param ownerId 账户归属方ID
             * @param subjectCode 科目代码
             * @return 业务锁 Key（如 account:open:USER_01:100101）
             */
            public static String of(String ownerId, String subjectCode) {
                return String.format(OPEN_LOCK_PATTERN,
                        StrUtil.nullToEmpty(ownerId).trim(),
                        StrUtil.nullToEmpty(subjectCode).trim());
            }

            /**
             * 内部账户开户锁 Key
             *
             * @param subjectCode 科目代码
             * @return 业务锁 Key（如 account:open:INNER:100101）
             */
            public static String ofInner(String subjectCode) {
                return of(INNER_OWNER, subjectCode);
            }
        }

        /**
         * 【流水请求幂等互斥域 (Idempotent Mutex Domain)】
         * <p>互斥实体：{@code traceNo} + {@code traceSeq}
         * <p>保护场景：上游交易流水接口并发重复提交防护 (JournalingApplicationService)
         */
        public static final class Idempotent {

            private Idempotent() {}

            private static final String TRACE_LOCK_PATTERN = "idempotent:trace:%s-%s";

            /**
             * 流水幂等锁 Key
             *
             * @param traceNo 请求流水号
             * @param traceSeq 流水序号
             * @return 业务锁 Key（如 idempotent:trace:TRX123-1）
             */
            public static String trace(String traceNo, Object traceSeq) {
                return String.format(TRACE_LOCK_PATTERN,
                        StrUtil.nullToEmpty(traceNo).trim(),
                        traceSeq == null ? "" : traceSeq.toString().trim());
            }
        }
    }

    /**
     * =========================================================================
     * 二、业务缓存 Key (Cache)
     * =========================================================================
     */
    public static final class Cache {

        private Cache() {}

        /** 字典二级缓存 Key 格式：accounting:{tenantId}:dict:{dictType} */
        public static final String DICT_CACHE_FORMAT = "accounting:%s:dict:%s";

        /** 全局会计日期缓存 Key（L2 分布式缓存） */
        public static final String ACCOUNTING_DATE_CURRENT = "accounting:date:current";

        /**
         * 获取租户级字典缓存 Key
         *
         * @param tenantId 租户ID
         * @param dictType 字典类型代码
         * @return 完整 Redis Key（如 accounting:1001:dict:PAY_CHANNEL）
         */
        public static String dictKey(Object tenantId, String dictType) {
            return String.format(DICT_CACHE_FORMAT, tenantId, dictType);
        }
    }

    /**
     * =========================================================================
     * 三、发号器序列 Key (Sequence)
     * =========================================================================
     */
    public static final class Sequence {

        private Sequence() {}

        public static final String EXT_ACCOUNT_SEQ = "account:seq:ext:%s:%s";
        public static final String INNER_ACCOUNT_SEQ = "account:seq:inner:%s";
        public static final String FREEZE_SEQ = "frz:seq:%s";
        public static final String TXN_SEQ = "txn:seq:%s";
        public static final String COMMON_SEQ = "%s:seq:%s";

        public static String extAccount(String templateId, String date) {
            return String.format(EXT_ACCOUNT_SEQ, templateId, date);
        }

        public static String innerAccount(String subjectCode) {
            return String.format(INNER_ACCOUNT_SEQ, subjectCode);
        }

        public static String freeze(String date) {
            return String.format(FREEZE_SEQ, date);
        }

        public static String transaction(String date) {
            return String.format(TXN_SEQ, date);
        }

        public static String common(String prefix, String dateOrTime) {
            return String.format(COMMON_SEQ, prefix.toLowerCase(), dateOrTime);
        }
    }

    /**
     * =========================================================================
     * 四、Pub/Sub 广播通知频道 (PubSub)
     * =========================================================================
     */
    public static final class PubSub {

        private PubSub() {}

        /** 会计日期切日广播通知频道 */
        public static final String ACCOUNTING_DATE_NOTIFY = "accounting:date:notify";
    }
}
