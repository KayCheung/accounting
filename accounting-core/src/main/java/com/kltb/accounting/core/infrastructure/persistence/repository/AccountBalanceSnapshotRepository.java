package com.kltb.accounting.core.infrastructure.persistence.repository;

import com.kltb.accounting.core.infrastructure.persistence.entity.AccountBalanceSnapshotPO;
import com.kltb.accounting.core.infrastructure.persistence.mapper.AccountBalanceSnapshotMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class AccountBalanceSnapshotRepository {

    private final AccountBalanceSnapshotMapper snapshotMapper;

    public void batchInsert(List<AccountBalanceSnapshotPO> list) {
        if (list == null || list.isEmpty()) {
            return;
        }
        snapshotMapper.batchInsertSnapshot(list);
    }
}
