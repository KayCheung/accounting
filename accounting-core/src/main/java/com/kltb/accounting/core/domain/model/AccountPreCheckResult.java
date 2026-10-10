package com.kltb.accounting.core.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 预开户检查与自动开户执行结果
 * <p>
 * 封装已排好序的账户编号列表及内存映射字典，供后续加锁与凭证分录解析直接复用，杜绝二次查库。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountPreCheckResult {

    /**
     * 涉及的真实账户编号列表（已按 account_no 升序排列，供 Step 10/11/12 加锁防死锁使用）
     */
    @Builder.Default
    private List<String> sortedAccountNos = Collections.emptyList();

    /**
     * 账户映射字典（Key: ownerId + ":" + subjectCode，Value: 真实的 account_no）
     */
    @Builder.Default
    private Map<String, String> accountMapping = Collections.emptyMap();

    /**
     * 根据 ownerId 与 subjectCode 快速解析真实账户编号（内存 O(1) 查找）
     */
    public String resolveAccountNo(String ownerId, String subjectCode) {
        if (accountMapping == null || ownerId == null || subjectCode == null) {
            return null;
        }
        return accountMapping.get(ownerId + ":" + subjectCode);
    }
}
