package com.kltb.accounting.core.infrastructure.spel;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.kltb.accounting.core.infrastructure.persistence.entity.BusinessDetailPO;
import com.kltb.accounting.core.infrastructure.persistence.entity.BusinessRecordPO;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * 账务核心 SpEL 计算统一上下文实体
 * <p>
 * 提供统一的根对象 (#root) 与变量注册：
 * 兼容已有表达式 (#root.amount * 0.1, #root.amount)，
 * 同时支持业务方传入的扩展属性 (#extra['creditParty'] 或 #root.extra['creditParty'])。
 */
@Data
@NoArgsConstructor
public class JournalSpelContext {

    /** 当前明细发生金额（向下兼容 #root.amount） */
    private BigDecimal amount;

    /** 主单扩展属性 Map */
    private Map<String, Object> extra = new HashMap<>();

    /** 明细项扩展属性 Map */
    private Map<String, Object> detailExtra = new HashMap<>();

    /** 流水明细 PO */
    private BusinessDetailPO detail;

    /** 流水主单 PO */
    private BusinessRecordPO journal;

    public JournalSpelContext(BigDecimal amount,
                              Map<String, Object> extra,
                              Map<String, Object> detailExtra,
                              BusinessDetailPO detail,
                              BusinessRecordPO journal) {
        this.amount = amount != null ? amount : BigDecimal.ZERO;
        if (extra != null) {
            this.extra.putAll(extra);
        }
        if (detailExtra != null) {
            this.detailExtra.putAll(detailExtra);
        }
        this.detail = detail;
        this.journal = journal;
    }

    /**
     * 工厂方法：从 BusinessDetailPO 与 BusinessRecordPO 构建上下文
     */
    public static JournalSpelContext of(BusinessDetailPO detail, BusinessRecordPO journal) {
        return of(null, detail, journal);
    }

    /**
     * 工厂方法：指定金额并从 BusinessDetailPO 与 BusinessRecordPO 构建上下文
     */
    public static JournalSpelContext of(BigDecimal amount, BusinessDetailPO detail, BusinessRecordPO journal) {
        BigDecimal finalAmount = amount != null ? amount : (detail != null && detail.getAmount() != null ? detail.getAmount() : BigDecimal.ZERO);
        Map<String, Object> extra = parseJsonToMap(journal != null ? journal.getExtraAttrs() : null);
        Map<String, Object> detailExtra = parseJsonToMap(detail != null ? detail.getExtraAttrs() : null);
        return new JournalSpelContext(finalAmount, extra, detailExtra, detail, journal);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parseJsonToMap(String jsonStr) {
        if (StrUtil.isBlank(jsonStr)) {
            return new HashMap<>();
        }
        try {
            return JSONUtil.toBean(jsonStr, Map.class);
        } catch (Exception e) {
            return new HashMap<>();
        }
    }
}
