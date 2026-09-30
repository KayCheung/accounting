package com.kltb.accounting.core.infrastructure.account;

import cn.hutool.core.util.StrUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 核心业务单号与流水号统一生成器
 * <p>
 * 统一收拢凭证号 (voucherNo)、事务号 (txnNo)、分录ID (entryId)、手工申请单号 (applyNo)、期末结转号 (transferNo) 等核心业务单号的生成规范。
 * 底层基于 Redis 高并发原子递增，具备动态时间窗口与安全降级机制，彻底杜绝硬编码日期与离散实现。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BusinessNoGenerator {

    private static final DateTimeFormatter BASIC_DATE_FMT = DateTimeFormatter.BASIC_ISO_DATE; // yyyyMMdd
    private static final DateTimeFormatter MILLIS_TIME_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    private final RedisSequenceGenerator seqGen;

    /**
     * 生成凭证编号（基于凭证类型前缀，如 REC20260930000001, PAY..., TRF..., VOU...）
     *
     * @param prefix 凭证前缀（REC/PAY/TRF/ADJ/REV/PET/VOU 等）
     * @param date   业务会计日期（若为 null 自动使用当天日期）
     * @return 凭证编号
     */
    public String generateVoucherNo(String prefix, LocalDate date) {
        String cleanPrefix = StrUtil.isNotBlank(prefix) ? prefix.trim().toUpperCase() : "VOU";
        LocalDate safeDate = (date != null) ? date : LocalDate.now();
        try {
            return seqGen.generate(cleanPrefix, safeDate, 6, 25);
        } catch (Exception e) {
            log.warn("[单号生成] Redis 生成凭证号异常，采用动态时间戳降级: prefix={}, error={}", cleanPrefix, e.getMessage());
            String dateStr = safeDate.format(BASIC_DATE_FMT);
            return cleanPrefix + dateStr + String.format("%06d", ThreadLocalRandom.current().nextInt(100000, 999999));
        }
    }

    /**
     * 生成事务编号 (TXN + yyyyMMdd + 6位序号)
     *
     * @param date 业务会计日期（若为 null 自动使用当天日期）
     * @return 事务编号
     */
    public String generateTxnNo(LocalDate date) {
        LocalDate safeDate = (date != null) ? date : LocalDate.now();
        try {
            return seqGen.generate("TXN", safeDate, 6, 25);
        } catch (Exception e) {
            log.warn("[单号生成] Redis 生成事务编号异常，采用动态时间戳降级: error={}", e.getMessage());
            String dateStr = safeDate.format(BASIC_DATE_FMT);
            return "TXN" + dateStr + String.format("%06d", ThreadLocalRandom.current().nextInt(100000, 999999));
        }
    }

    /**
     * 生成分录流水号 (ENT + yyyyMMddHHmmssSSS + 4位序号)
     *
     * @return 分录流水号
     */
    public String generateEntryId() {
        return generateEntryId("ENT");
    }

    /**
     * 生成指定前缀的分录流水号 (prefix + yyyyMMddHHmmssSSS + 4位序号)
     *
     * @param prefix 分录流水号前缀
     * @return 分录流水号
     */
    public String generateEntryId(String prefix) {
        String cleanPrefix = StrUtil.isNotBlank(prefix) ? prefix.trim().toUpperCase() : "ENT";
        LocalDateTime now = LocalDateTime.now();
        try {
            return seqGen.generate(cleanPrefix, now, "yyyyMMddHHmmssSSS", 4, 2);
        } catch (Exception e) {
            log.warn("[单号生成] Redis 生成分录号异常，采用动态时间戳降级: prefix={}, error={}", cleanPrefix, e.getMessage());
            return cleanPrefix + now.format(MILLIS_TIME_FMT) + String.format("%04d", ThreadLocalRandom.current().nextInt(1000, 9999));
        }
    }

    /**
     * 生成手工记账申请单号 (MVA + yyyyMMdd + 6位序号)
     *
     * @param date 申请会计日期（若为 null 自动使用当天日期）
     * @return 手工记账申请单号
     */
    public String generateApplyNo(LocalDate date) {
        LocalDate safeDate = (date != null) ? date : LocalDate.now();
        try {
            return seqGen.generate("MVA", safeDate, 6, 25);
        } catch (Exception e) {
            log.warn("[单号生成] Redis 生成手工申请单号异常，采用动态时间戳降级: error={}", e.getMessage());
            String dateStr = safeDate.format(BASIC_DATE_FMT);
            return "MVA" + dateStr + String.format("%06d", ThreadLocalRandom.current().nextInt(100000, 999999));
        }
    }

    /**
     * 生成期末结转编号 (EODTR + yyyyMMdd + 4位序号)
     *
     * @param date 会计日期（若为 null 自动使用当天日期）
     * @return 期末结转编号
     */
    public String generateTransferNo(LocalDate date) {
        LocalDate safeDate = (date != null) ? date : LocalDate.now();
        try {
            return seqGen.generate("EODTR", safeDate, 4, 25);
        } catch (Exception e) {
            log.warn("[单号生成] Redis 生成结转单号异常，采用动态时间戳降级: error={}", e.getMessage());
            String dateStr = safeDate.format(BASIC_DATE_FMT);
            return "EODTR" + dateStr + String.format("%04d", ThreadLocalRandom.current().nextInt(1000, 9999));
        }
    }
}
