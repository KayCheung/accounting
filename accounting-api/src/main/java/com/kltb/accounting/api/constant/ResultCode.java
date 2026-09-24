// accounting-api/src/main/java/com/kltb/accounting/api/constant/ResultCode.java
package com.kltb.accounting.api.constant;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 统一结果码枚举
 * <p>
 * 规范：严禁在业务代码中使用魔法数字，所有异常必须绑定此枚举
 * 编码规则：
 *   0        - 成功
 *   1xxx     - 通用系统错误
 *   2xxx     - 账务业务错误
 *   3xxx     - 账户专项错误
 *   4xxx     - 幂等/并发错误
 */
public enum ResultCode {

    // ==================== 成功 ====================
    SUCCESS("0", "成功"),

    // ==================== 通用系统错误 1xxx ====================
    SYSTEM_ERROR("1000", "系统内部错误"),
    PARAM_ERROR("1001", "请求参数错误"),
    DATA_NOT_FOUND("1002", "数据不存在"),
    OPERATION_NOT_ALLOWED("1003", "操作不允许"),
    TENANT_ID_IS_NULL("1004", "租户ID为空"),

    // ==================== 账务业务错误 2xxx ====================
    INSUFFICIENT_BALANCE("2001", "余额不足"),
    RULE_NOT_FOUND("2002", "记账规则不存在"),
    RULE_DISABLED("2003", "记账规则已停用"),
    DEBIT_CREDIT_NOT_BALANCED("2004", "借贷不平衡"),
    VOUCHER_NOT_FOUND("2005", "凭证不存在"),
    VOUCHER_STATUS_ILLEGAL("2006", "凭证状态非法"),
    SUBJECT_NOT_FOUND("2007", "会计科目不存在"),
    SUBJECT_NOT_LEAF("2008", "非末级科目，不允许记账"),
    ACCOUNTING_DATE_CLOSED("2009", "会计日期已关闭"),
    BUFFER_POSTING_FAILED("2010", "缓冲入账失败"),
    EOD_BLOCKED("2011", "日终核算阻断，存在未处理存量"),
    TRIAL_BALANCE_FAILED("2012", "试算平衡失败"),
    JOURNAL_NOT_FOUND("2013", "业务流水不存在"),
    JOURNAL_STATUS_INVALID("2014", "业务流水状态非法"),
    VOUCHER_NOT_BALANCED("2015", "借贷不平衡，凭证生成失败"),
    SPEL_CALC_ERROR("2016", "SpEL 脚本执行错误"),
    AUXILIARY_CONFIG_MISSING("2017", "辅助核算配置缺失"),
    AUXILIARY_AMOUNT_MISMATCH("2018", "辅助核算分摊金额不匹配"),
    BUFFER_RULE_ERROR("2019", "缓冲规则匹配异常"),
    POSTING_STATUS_INVALID("2020", "凭证状态非法，无法执行过账"),
    POSTING_FAILED("2021", "过账失败"),
    POSTING_RETRY_EXHAUSTED("2022", "过账重试次数已达上限"),
    BUFFER_POSTING_BALANCE_MISMATCH("2023", "缓冲记账余额校验失败（Running Balance 不一致）"),
    BUFFER_POSTING_LOCK_UPGRADE_FAILED("2024", "缓冲记账锁升级后仍失败，需人工介入"),
    BUFFER_POSTING_RETRY_EXHAUSTED("2025", "缓冲记账重试次数已达上限"),
    EOD_PRECHECK_FAILED("2026", "日切前置检查失败（存在未处理项）"),
    EOD_TRANSFER_FAILED("2027", "期末结转执行失败"),
    DAILY_BALANCE_NEGATIVE("2028", "日余额计算出现负值"),
    EOD_ALREADY_EXECUTED("2029", "日切已执行，不可重复执行"),

    // ==================== 日切与核对错误 203x ====================
    DATE_SWITCH_FAILED("2030", "全局会计日期切换失败"),
    EOD_ARCHIVE_FAILED("2031", "日切归档失败"),
    GL_SUB_ACCOUNT_MISMATCH("2032", "总分核对失败（总账与分户余额不一致）"),
    ACCOUNT_DETAIL_MISMATCH("2033", "余额核对失败（账户余额与明细不一致）"),
    EOD_STATUS_NOT_FOUND("2034", "日切状态记录不存在"),

    // ==================== 红冲错误 2035~2039 ====================
    REVERSAL_ORIGINAL_NOT_POSTED("2035", "原凭证未过账，不可红冲"),
    REVERSAL_ENTRIES_NOT_ALL_POSTED("2036", "原凭证分录未全部过账，不可红冲"),
    REVERSAL_ALREADY_EXISTS("2037", "红冲记录已存在，不可重复红冲"),
    REVERSAL_ORIGINAL_NOT_FOUND("2038", "原凭证不存在"),
    REVERSAL_POSTING_FAILED("2039", "红冲过账失败"),
    REVERSAL_ENTRY_DIRECTION_INVALID("2040", "红冲原分录借贷方向非法"),

    // ==================== 账户专项错误 3xxx ====================
    ACCOUNT_NOT_FOUND("3001", "账户不存在"),
    ACCOUNT_FROZEN("3002", "账户已冻结"),
    ACCOUNT_CANCELLED("3003", "账户已注销"),
    ACCOUNT_RISK_BLOCKED("3004", "账户风控拦截"),
    ACCOUNT_STATUS_ILLEGAL("3005", "账户状态非法"),
    ACCOUNT_ALREADY_EXISTS("3006", "账户已存在"),
    ACCOUNT_TEMPLATE_NOT_FOUND("3007", "开户模板不存在"),
    ACCOUNT_TEMPLATE_NOT_ENABLED("3010", "开户模板未启用"),
    ACCOUNT_TEMPLATE_NOT_AUTO_OPEN("3011", "开户模板不支持自动开户"),
    SUBJECT_NOT_ALLOW_OPEN_ACCOUNT("3012", "科目不允许开户"),
    SUB_ACCOUNT_CREATE_FAILED("3013", "子账户创建失败"),
    FREEZE_NOT_FOUND("3008", "冻结记录不存在"),
    FREEZE_ALREADY_UNFROZEN("3009", "资金已解冻"),
    ACCOUNT_STATUS_TRANSITION_INVALID("3014", "账户状态转换非法"),
    ACCOUNT_BALANCE_NOT_ZERO("3015", "账户余额不为零，无法注销"),
    FREEZE_AMOUNT_INVALID("3016", "冻结金额无效（<=0 或超过可用余额）"),
    FREEZE_RECORD_NOT_FOUND("3017", "冻结记录不存在"),
    FREEZE_STATUS_INVALID("3018", "冻结记录状态非法（非冻结中）"),
    FREEZE_AMOUNT_EXCEEDED("3019", "解冻/扣款金额超过冻结金额"),
    ACCOUNT_FROZEN_CANNOT_FREEZE("3020", "账户已冻结，无法执行资金冻结"),
    ACCOUNT_NO_REQUIRED("3021", "账户编号不能为空"),

    // ==================== 幂等/并发错误 4xxx ====================
    IDEMPOTENT_CONFLICT("4001", "幂等冲突，请求已处理"),
    LOCK_ACQUIRE_FAILED("4002", "获取分布式锁失败"),
    OPTIMISTIC_LOCK_FAILED("4003", "乐观锁冲突，请重试"),
    CONCURRENT_OPERATION("4004", "并发操作冲突");

    /** 错误码 */
    private final String code;

    /** 错误描述 */
    private final String message;

    ResultCode(String code, String message) {
        this.code = code;
        this.message = message;
    }

    @JsonValue
    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    @Override
    public String toString() {
        return code + ":" + message;
    }
}
