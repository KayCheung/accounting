package com.kltb.accounting.core.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kltb.accounting.core.domain.enums.TransferRecordStatusEnum;
import com.kltb.accounting.core.infrastructure.persistence.entity.PeriodEndTransferRecordPO;
import com.kltb.accounting.core.infrastructure.persistence.mapper.PeriodEndTransferRecordMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class PeriodEndTransferRecordRepository {

    private final PeriodEndTransferRecordMapper recordMapper;

    public List<PeriodEndTransferRecordPO> selectByAccountingDate(LocalDate accountingDate) {
        return recordMapper.selectByAccountingDate(accountingDate);
    }

    public boolean existsSuccessfulTransfer(LocalDate accountingDate, String ruleCode) {
        Long count = recordMapper.selectCount(new LambdaQueryWrapper<PeriodEndTransferRecordPO>()
                .eq(PeriodEndTransferRecordPO::getAccountingDate, accountingDate)
                .eq(PeriodEndTransferRecordPO::getRuleCode, ruleCode)
                .eq(PeriodEndTransferRecordPO::getStatus, TransferRecordStatusEnum.SUCCESS)
                .eq(PeriodEndTransferRecordPO::getIsDelete, 0));
        return count != null && count > 0;
    }

    public void insert(PeriodEndTransferRecordPO record) {
        recordMapper.insert(record);
    }
}
