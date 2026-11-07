package com.kltb.accounting.core.infrastructure.persistence.repository;

import com.kltb.accounting.core.infrastructure.persistence.entity.AccountBalanceSnapshotPO;
import com.kltb.accounting.core.infrastructure.persistence.mapper.AccountBalanceSnapshotMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

import com.kltb.accounting.core.shared.context.TenantContext;

@Repository
@RequiredArgsConstructor
public class AccountBalanceSnapshotRepository {

    private final AccountBalanceSnapshotMapper snapshotMapper;

    public void batchInsert(List<AccountBalanceSnapshotPO> list) {
        if (list == null || list.isEmpty()) {
            return;
        }
        Integer defaultTenant = TenantContext.get();
        for (AccountBalanceSnapshotPO po : list) {
            if (po.getTenantId() == null) {
                po.setTenantId(defaultTenant != null ? defaultTenant : TenantContext.SYSTEM_TENANT);
            }
        }
        snapshotMapper.batchInsertSnapshot(list);
    }
}
