package com.kltb.accounting.core.infrastructure.account;

import cn.hutool.core.util.StrUtil;
import org.redisson.api.RScript;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.LongCodec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 账户编号与名称生成器
 * <p>
 * 外部客户账户：支持基于模板规则（acctNoRule、acctNameRule）动态解析生成
 * - 账号规则变量：{accountType}、{currency}、{balanceDirection}、{ownerType}、{subjectCode}、{yyyyMMdd}、{yyyyMM}、{yyyy}、{seq}
 * - 户名规则变量：{accountTypeName}、{currencyName}、{directionName}、{ownerTypeName}、{subjectName}、{ownerName}、{ownerId}
 * - 支持指定长度变量：例如 {seq1}、{seq2}、{seq3}，以及任意变量截取如 {subjectCode4} 等
 * <p>
 * 内部账户：INNER + subjectCode + seq3，Redis key 不含日期，永久递增不重置
 * <p>
 * 并发安全：所有 Redis 序号操作使用 Lua 脚本原子化，避免 isExists/set/increment 竞态。
 * 注意：事务回滚后 Redis 序列号不会回退，会出现跳号。财务系统可接受（序列号仅保证唯一性，不要求连续）。
 */
@Component
public class AccountNoGenerator {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.BASIC_ISO_DATE;
    private static final ZoneId ZONE_SHANGHAI = ZoneId.of("Asia/Shanghai");

    private static final String EXT_SEQ_KEY_PREFIX = "account:seq:ext:";
    private static final String INNER_SEQ_KEY_PREFIX = "account:seq:inner:";

    /** 匹配形如 {variable} 或 {variable3} 或 {variable:3} 的占位符 */
    private static final Pattern TAG_PATTERN = Pattern.compile("\\{([a-zA-Z0-9_:]+)\\}");
    /** 解析占位符名称与可选的长度限制，如 seq3 -> name=seq, len=3；yyyyMMdd -> name=yyyyMMdd, len=null */
    private static final Pattern TAG_PARSE_PATTERN = Pattern.compile("^([a-zA-Z]+)(?::?(\\d+))?$");

    /** Lua: INCR 后，若 key 不存在则设置 TTL（25 小时）。原子操作，避免竞态。 */
    private static final String LUA_INCR_EXPIRE =
            "local exists = redis.call('EXISTS', KEYS[1]); " +
            "local val = redis.call('INCR', KEYS[1]); " +
            "if exists == 0 then redis.call('EXPIRE', KEYS[1], 90000); end; " +
            "return val;";

    private static final String LUA_INCR =
            "return redis.call('INCR', KEYS[1]);";

    private final RedissonClient redissonClient;
    private final String orgCode;

    public AccountNoGenerator(RedissonClient redissonClient,
                              @Value("${accounting.org-code:001}") String orgCode) {
        this.redissonClient = redissonClient;
        this.orgCode = orgCode;
    }

    /**
     * 生成外部客户账户编号（默认规则：{orgCode}{yyyyMMdd}{seq5}）
     */
    public String generateExternalAccountNo() {
        String dateStr = LocalDate.now(ZONE_SHANGHAI).format(DATE_FMT);
        String key = EXT_SEQ_KEY_PREFIX + orgCode + ":" + dateStr;
        Long seq = redissonClient.getScript(LongCodec.INSTANCE)
                .eval(RScript.Mode.READ_WRITE, LUA_INCR_EXPIRE, RScript.ReturnType.INTEGER, Collections.singletonList(key));
        return orgCode + dateStr + String.format("%05d", seq);
    }

    /**
     * 根据模板配置的规则与上下文生成外部客户账户编号
     *
     * 支持占位符：
     * - {accountType}       : 账户类型编码（如 CASH、SETTLE、贷款本金）
     * - {currency}          : 币种编码（如 CNY、USD）
     * - {balanceDirection}  : 借贷方向（1-借 / 2-贷），亦支持 {direction}
     * - {ownerType}         : 客户类型（1-个人 / 2-企业 / 99-其他），亦支持 {customerType}
     * - {subjectCode}       : 会计科目编码
     * - {yyyyMMdd}          : 年月日 (如 20260920)
     * - {yyyyMM}            : 年月 (如 202609)
     * - {yyyy}              : 年份 (如 2026)，支持 {yyyy2} 取后两位 26
     * - {seq}               : 默认 5 位自增序列号 (如 00001)
     * - {seq1} ~ {seqN}     : 指定 N 位自增序列号（如 {seq1} 为 1 位，{seq3} 为 001）
     * - 任意变量+数字       : 支持指定长度截取，如 {subjectCode4}、{accountType3}
     */
    public String generateByRule(String acctNoRule, AccountRuleContext context) {
        if (StrUtil.isBlank(acctNoRule)) {
            return generateExternalAccountNo();
        }

        if (context == null) {
            context = new AccountRuleContext();
        }

        String dateStr = LocalDate.now(ZONE_SHANGHAI).format(DATE_FMT);
        String yearMonth = dateStr.substring(0, 6);
        String year = dateStr.substring(0, 4);

        // 获取序列号 (Redis key 包含 bizCode 与日期，每日自动过期重置)
        String bizCode = StrUtil.isNotBlank(context.getBusinessCode()) ? context.getBusinessCode() : orgCode;
        String seqKey = EXT_SEQ_KEY_PREFIX + bizCode + ":" + dateStr;
        Long seq = redissonClient.getScript(LongCodec.INSTANCE)
                .eval(RScript.Mode.READ_WRITE, LUA_INCR_EXPIRE, RScript.ReturnType.INTEGER, Collections.singletonList(seqKey));

        String directionCode = "";
        if (context.getBalanceDirectionCode() != null) {
            directionCode = String.valueOf(context.getBalanceDirectionCode());
        } else if (context.getBalanceDirection() != null) {
            directionCode = String.valueOf(context.getBalanceDirection().getCode());
        }

        String ownerTypeCode = "";
        if (context.getOwnerTypeCode() != null) {
            ownerTypeCode = String.valueOf(context.getOwnerTypeCode());
        } else if (context.getCustomerType() != null) {
            ownerTypeCode = String.valueOf(context.getCustomerType().getCode());
        }

        Matcher matcher = TAG_PATTERN.matcher(acctNoRule);
        StringBuffer sb = new StringBuffer();

        while (matcher.find()) {
            String fullTag = matcher.group(1);
            Matcher parseMatcher = TAG_PARSE_PATTERN.matcher(fullTag);

            String replacement = "";
            if (parseMatcher.matches()) {
                String varName = parseMatcher.group(1);
                String lenStr = parseMatcher.group(2);
                Integer len = (lenStr != null && !lenStr.isEmpty()) ? Integer.parseInt(lenStr) : null;

                if ("seq".equalsIgnoreCase(varName)) {
                    int seqLen = (len != null && len > 0) ? len : 5;
                    replacement = String.format("%0" + seqLen + "d", seq);
                } else if ("accountType".equalsIgnoreCase(varName)) {
                    replacement = truncate(context.getAccountType(), len);
                } else if ("currency".equalsIgnoreCase(varName)) {
                    replacement = truncate(context.getCurrency(), len);
                } else if ("balanceDirection".equalsIgnoreCase(varName) || "direction".equalsIgnoreCase(varName)) {
                    replacement = truncate(directionCode, len);
                } else if ("ownerType".equalsIgnoreCase(varName) || "customerType".equalsIgnoreCase(varName)) {
                    replacement = truncate(ownerTypeCode, len);
                } else if ("subjectCode".equalsIgnoreCase(varName)) {
                    replacement = truncate(context.getSubjectCode(), len);
                } else if ("yyyyMMdd".equalsIgnoreCase(varName)) {
                    replacement = truncate(dateStr, len);
                } else if ("yyyyMM".equalsIgnoreCase(varName)) {
                    replacement = truncate(yearMonth, len);
                } else if ("yyyy".equalsIgnoreCase(varName)) {
                    if (len != null && len == 2) {
                        replacement = year.substring(2);
                    } else {
                        replacement = truncate(year, len);
                    }
                } else if ("bizCode".equalsIgnoreCase(varName) || "ownerId".equalsIgnoreCase(varName)) {
                    // 业务规范：账号生成规则不应取 bizCode、ownerId，将其置空
                    replacement = "";
                } else {
                    replacement = "";
                }
            }

            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);

        String result = sb.toString();
        if (result.length() > 32) {
            result = result.substring(0, 32);
        }
        return result;
    }

    /**
     * 重载兼容老调用
     */
    public String generateByRule(String acctNoRule, String businessCode, String subjectCode, String ownerId) {
        AccountRuleContext context = AccountRuleContext.builder()
                .businessCode(businessCode)
                .subjectCode(subjectCode)
                .ownerId(ownerId)
                .build();
        return generateByRule(acctNoRule, context);
    }

    /**
     * 根据模板配置的规则与上下文生成外部客户账户名称
     *
     * 支持占位符：
     * - {accountTypeName}    : 账户类型名称（如 "现金账户"、"贷款本金"）
     * - {currencyName}       : 币种名称（如 "人民币"、"美元"）
     * - {directionName}      : 借贷方向名称（如 "借"、"贷"），亦支持 {balanceDirectionName}
     * - {ownerTypeName}      : 客户类型名称（如 "个人"、"企业"、"其他"），亦支持 {customerTypeName}
     * - {subjectName}        : 会计科目名称
     * - {ownerName}          : 客户名称（未传时降级为 ownerId）
     * - {ownerId}            : 客户ID
     * - 任意变量+数字        : 支持指定长度截取，如 {ownerName2}
     */
    public String generateAccountName(String acctNameRule, AccountRuleContext context) {
        if (context == null) {
            context = new AccountRuleContext();
        }

        String displayName = StrUtil.isNotBlank(context.getOwnerName())
                ? context.getOwnerName()
                : (context.getOwnerId() != null ? context.getOwnerId() : "");

        if (StrUtil.isBlank(acctNameRule)) {
            String fallback = displayName;
            if (StrUtil.isNotBlank(context.getSubjectName())) {
                fallback = fallback + "-" + context.getSubjectName();
            }
            return fallback.length() > 32 ? fallback.substring(0, 32) : fallback;
        }

        String directionName = StrUtil.isNotBlank(context.getDirectionName())
                ? context.getDirectionName()
                : (context.getBalanceDirection() != null ? context.getBalanceDirection().getDesc() : "");

        String ownerTypeName = StrUtil.isNotBlank(context.getOwnerTypeName())
                ? context.getOwnerTypeName()
                : (context.getCustomerType() != null ? context.getCustomerType().getDesc() : "");

        Matcher matcher = TAG_PATTERN.matcher(acctNameRule);
        StringBuffer sb = new StringBuffer();

        while (matcher.find()) {
            String fullTag = matcher.group(1);
            Matcher parseMatcher = TAG_PARSE_PATTERN.matcher(fullTag);

            String replacement = "";
            if (parseMatcher.matches()) {
                String varName = parseMatcher.group(1);
                String lenStr = parseMatcher.group(2);
                Integer len = (lenStr != null && !lenStr.isEmpty()) ? Integer.parseInt(lenStr) : null;

                if ("accountTypeName".equalsIgnoreCase(varName)) {
                    replacement = truncate(context.getAccountTypeName(), len);
                } else if ("currencyName".equalsIgnoreCase(varName)) {
                    replacement = truncate(context.getCurrencyName(), len);
                } else if ("directionName".equalsIgnoreCase(varName) || "balanceDirectionName".equalsIgnoreCase(varName)) {
                    replacement = truncate(directionName, len);
                } else if ("ownerTypeName".equalsIgnoreCase(varName) || "customerTypeName".equalsIgnoreCase(varName)) {
                    replacement = truncate(ownerTypeName, len);
                } else if ("subjectName".equalsIgnoreCase(varName)) {
                    replacement = truncate(context.getSubjectName(), len);
                } else if ("ownerName".equalsIgnoreCase(varName)) {
                    replacement = truncate(displayName, len);
                } else if ("ownerId".equalsIgnoreCase(varName)) {
                    replacement = truncate(context.getOwnerId(), len);
                } else if ("bizCode".equalsIgnoreCase(varName)) {
                    // 业务规范：账户名称生成规则不应取 bizCode，将其置空
                    replacement = "";
                } else {
                    replacement = "";
                }
            }

            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);

        String result = sb.toString();
        if (result.length() > 32) {
            result = result.substring(0, 32);
        }
        return result;
    }

    /**
     * 重载兼容老调用
     */
    public String generateAccountName(String acctNameRule, String businessCode, String subjectName, String ownerName, String ownerId) {
        AccountRuleContext context = AccountRuleContext.builder()
                .businessCode(businessCode)
                .subjectName(subjectName)
                .ownerName(ownerName)
                .ownerId(ownerId)
                .build();
        return generateAccountName(acctNameRule, context);
    }

    /**
     * 截断字符串到指定长度
     */
    private String truncate(String val, Integer len) {
        if (val == null) {
            return "";
        }
        if (len != null && len > 0 && val.length() > len) {
            return val.substring(0, len);
        }
        return val;
    }

    /**
     * 生成内部账户编号
     * 格式：INNER + subjectCode + seq3，如 INNER1001001
     * Redis key：account:seq:inner:{subjectCode}（永久递增，不重置）
     */
    public String generateInternalAccountNo(String subjectCode) {
        String key = INNER_SEQ_KEY_PREFIX + subjectCode;
        Long seq = redissonClient.getScript(LongCodec.INSTANCE)
                .eval(RScript.Mode.READ_WRITE, LUA_INCR, RScript.ReturnType.INTEGER, Collections.singletonList(key));
        return "INNER" + subjectCode + String.format("%03d", seq);
    }
}
