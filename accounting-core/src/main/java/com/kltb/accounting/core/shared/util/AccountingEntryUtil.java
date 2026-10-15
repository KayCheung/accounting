package com.kltb.accounting.core.shared.util;

import cn.hutool.core.util.StrUtil;
import com.kltb.accounting.core.infrastructure.persistence.entity.AccountingVoucherEntryPO;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * 记账分录与分户公共工具类
 */
public final class AccountingEntryUtil {

    private AccountingEntryUtil() {
        // 私有构造，工具类禁止实例化
    }

    /**
     * 从任意分录集合中统计去重有效分户数
     *
     * @param items            分录集合
     * @param accountExtractor 分户账号提取函数
     * @param <T>              分录类型
     * @return 去重有效分户数
     */
    public static <T> int countDistinctAccounts(Collection<T> items, Function<T, String> accountExtractor) {
        if (items == null || items.isEmpty()) {
            return 0;
        }
        Set<String> set = new HashSet<>(items.size());
        for (T item : items) {
            if (item != null) {
                String acc = accountExtractor.apply(item);
                if (StrUtil.isNotBlank(acc)) {
                    set.add(acc);
                }
            }
        }
        return set.size();
    }

    /**
     * 从按凭证分组的分录 Map 中统计去重有效分户数
     *
     * @param entryMap 按凭证号分组的分录映射
     * @return 去重有效分户数
     */
    public static int countDistinctAccountsFromEntryMap(Map<?, ? extends Collection<AccountingVoucherEntryPO>> entryMap) {
        if (entryMap == null || entryMap.isEmpty()) {
            return 0;
        }
        Set<String> set = new HashSet<>();
        for (Collection<AccountingVoucherEntryPO> list : entryMap.values()) {
            if (list != null) {
                for (AccountingVoucherEntryPO e : list) {
                    if (e != null && StrUtil.isNotBlank(e.getAccountNo())) {
                        set.add(e.getAccountNo());
                    }
                }
            }
        }
        return set.size();
    }
}
