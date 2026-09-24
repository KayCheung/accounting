package com.kltb.accounting.core.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kltb.accounting.core.infrastructure.persistence.entity.AccountingVoucherAttachmentPO;
import com.kltb.accounting.core.infrastructure.persistence.entity.AccountingVoucherAuxiliaryPO;
import com.kltb.accounting.core.infrastructure.persistence.entity.AccountingVoucherEntryPO;
import com.kltb.accounting.core.infrastructure.persistence.entity.AccountingVoucherPO;
import com.kltb.accounting.core.infrastructure.persistence.mapper.AccountingVoucherAttachmentMapper;
import com.kltb.accounting.core.infrastructure.persistence.mapper.AccountingVoucherAuxiliaryMapper;
import com.kltb.accounting.core.infrastructure.persistence.mapper.AccountingVoucherEntryMapper;
import com.kltb.accounting.core.infrastructure.persistence.mapper.AccountingVoucherMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 记账凭证持久化仓储
 */
@Repository
@RequiredArgsConstructor
public class AccountingVoucherRepository {

    private final AccountingVoucherMapper voucherMapper;
    private final AccountingVoucherEntryMapper entryMapper;
    private final AccountingVoucherAuxiliaryMapper auxiliaryMapper;
    private final AccountingVoucherAttachmentMapper attachmentMapper;

    /**
     * 按凭证号查询凭证
     *
     * @param voucherNo 凭证号
     * @return 凭证PO，不存在时返回null
     */
    public AccountingVoucherPO selectByVoucherNo(String voucherNo) {
        return voucherMapper.selectWithEntries(voucherNo);
    }

    /**
     * 按系统跟踪号查询凭证列表
     *
     * @param traceNo 系统跟踪号
     * @return 凭证列表，无数据时返回空列表
     */
    public List<AccountingVoucherPO> selectByTraceNo(String traceNo) {
        List<AccountingVoucherPO> result = voucherMapper.selectByTraceNo(traceNo);
        return result != null ? result : Collections.emptyList();
    }

    /**
     * 按原凭证号查询红冲凭证
     *
     * @param origVoucherNo 原凭证号
     * @return 红冲凭证列表，无数据时返回空列表
     */
    public List<AccountingVoucherPO> selectReversalByOrig(String origVoucherNo) {
        List<AccountingVoucherPO> result = voucherMapper.selectReversalByOrig(origVoucherNo);
        return result != null ? result : Collections.emptyList();
    }

    /**
     * 插入凭证
     */
    public void insert(AccountingVoucherPO voucher) {
        voucherMapper.insert(voucher);
    }

    /**
     * 更新凭证（带乐观锁）
     */
    public boolean updateById(AccountingVoucherPO voucher) {
        return voucherMapper.updateById(voucher) > 0;
    }

    /**
     * 按凭证号查询分录列表
     *
     * @param voucherNo 凭证号
     * @return 分录列表，无数据时返回空列表
     */
    public List<AccountingVoucherEntryPO> selectEntriesByVoucherNo(String voucherNo) {
        List<AccountingVoucherEntryPO> result = entryMapper.selectByVoucherNo(voucherNo);
        return result != null ? result : Collections.emptyList();
    }

    /**
     * 查询待过账的分录
     *
     * @param voucherNo 凭证号
     * @return 待过账分录列表，无数据时返回空列表
     */
    public List<AccountingVoucherEntryPO> selectPendingPosting(String voucherNo) {
        List<AccountingVoucherEntryPO> result = entryMapper.selectPendingPosting(voucherNo);
        return result != null ? result : Collections.emptyList();
    }

    /**
     * 按凭证号和状态查询分录
     */
    public List<AccountingVoucherEntryPO> selectEntriesByVoucherNoWithStatus(String voucherNo, Integer status) {
        List<AccountingVoucherEntryPO> result = entryMapper.selectByVoucherNoWithStatus(voucherNo, status);
        return result != null ? result : Collections.emptyList();
    }

    /**
     * 按凭证号和分录ID查询单条分录
     */
    public AccountingVoucherEntryPO selectEntryByVoucherNoAndEntryId(String voucherNo, String entryId) {
        return entryMapper.selectByVoucherNoAndEntryId(voucherNo, entryId);
    }

    /**
     * 插入分录
     */
    public void insertEntry(AccountingVoucherEntryPO entry) {
        entryMapper.insert(entry);
    }

    /**
     * 更新分录（带乐观锁）
     */
    public boolean updateEntryById(AccountingVoucherEntryPO entry) {
        return entryMapper.updateById(entry) > 0;
    }

    /**
     * 按分录ID查询辅助核算项
     *
     * @param entryId 分录流水号
     * @return 辅助核算项列表，无数据时返回空列表
     */
    public List<AccountingVoucherAuxiliaryPO> selectAuxiliaryByEntryId(String entryId) {
        List<AccountingVoucherAuxiliaryPO> result = auxiliaryMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AccountingVoucherAuxiliaryPO>()
                        .eq(AccountingVoucherAuxiliaryPO::getEntryId, entryId)
                        .eq(AccountingVoucherAuxiliaryPO::getIsDelete, 0));
        return result != null ? result : Collections.emptyList();
    }

    /**
     * 批量查询多个分录的辅助核算项（红冲用，避免 N+1 查询）
     *
     * @param entryIds 分录ID列表
     * @return 辅助核算项列表，无数据时返回空列表
     */
    public List<AccountingVoucherAuxiliaryPO> selectAuxiliaryByEntryIds(List<String> entryIds) {
        if (entryIds == null || entryIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<AccountingVoucherAuxiliaryPO> result = auxiliaryMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AccountingVoucherAuxiliaryPO>()
                        .in(AccountingVoucherAuxiliaryPO::getEntryId, entryIds)
                        .eq(AccountingVoucherAuxiliaryPO::getIsDelete, 0));
        return result != null ? result : Collections.emptyList();
    }

    /**
     * 插入辅助核算项
     */
    public void insertAuxiliary(AccountingVoucherAuxiliaryPO auxiliary) {
        auxiliaryMapper.insert(auxiliary);
    }

    /**
     * 按凭证号查询附件列表
     *
     * @param voucherNo 凭证号
     * @return 附件列表，无数据时返回空列表
     */
    public List<AccountingVoucherAttachmentPO> selectAttachmentsByVoucherNo(String voucherNo) {
        List<AccountingVoucherAttachmentPO> result = attachmentMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AccountingVoucherAttachmentPO>()
                        .eq(AccountingVoucherAttachmentPO::getVoucherNo, voucherNo)
                        .eq(AccountingVoucherAttachmentPO::getIsDelete, 0));
        return result != null ? result : Collections.emptyList();
    }

    /**
     * 插入附件
     */
    public void insertAttachment(AccountingVoucherAttachmentPO attachment) {
        attachmentMapper.insert(attachment);
    }

    /**
     * 按业务键统计凭证数量（规则停用校验用）
     *
     * @param businessCode 业务线编码
     * @param tradingCode 交易编码
     * @param payChannel 支付渠道
     * @return 关联凭证数
     */
    public long countByBusinessKey(String businessCode, String tradingCode, String payChannel) {
        return voucherMapper.countByBusinessKey(businessCode, tradingCode, payChannel);
    }

    /**
     * 回填事务编号到凭证
     */
    public int updateTxnNoByVoucherNo(String voucherNo, String txnNo) {
        return voucherMapper.updateTxnNoByVoucherNo(voucherNo, txnNo);
    }

    /**
     * 按凭证号更新凭证状态
     */
    public void updateStatusByVoucherNo(String voucherNo, Integer status) {
        voucherMapper.updateStatusByVoucherNo(voucherNo, status, LocalDateTime.now());
    }

    /**
     * 按凭证号查询凭证（不带分录）
     */
    public AccountingVoucherPO selectByVoucherNoSimple(String voucherNo) {
        return voucherMapper.selectByVoucherNo(voucherNo);
    }

    /**
     * 按会计日期和状态统计凭证数量（Step 17 P0-7）
     */
    public int countByAccountingDateAndStatus(LocalDate accountingDate, Integer status) {
        LambdaQueryWrapper<AccountingVoucherPO> wrapper = new LambdaQueryWrapper<AccountingVoucherPO>()
                .eq(AccountingVoucherPO::getAccountingDate, accountingDate)
                .eq(AccountingVoucherPO::getIsDelete, 0);
        if (status != null) {
            wrapper.eq(AccountingVoucherPO::getStatus, status);
        }
        return voucherMapper.selectCount(wrapper).intValue();
    }

    /**
     * 按凭证状态和会计日期范围查询凭证（Step 12 P0-2）
     */
    public List<AccountingVoucherPO> selectByStatusAndDateRange(
        Integer status, LocalDate startDate, LocalDate endDate,
        String businessCode, int limit) {
        List<AccountingVoucherPO> result = voucherMapper.selectByStatusAndDateRange(
            status, startDate, endDate, businessCode, limit);
        return result != null ? result : Collections.emptyList();
    }

    /**
     * 按凭证状态分组统计（Step 12 P0-2）
     */
    public List<Map<String, Object>> countByStatusGroup(
        LocalDate startDate, LocalDate endDate, String businessCode) {
        List<Map<String, Object>> result = voucherMapper.countByStatusGroup(
            startDate, endDate, businessCode);
        return result != null ? result : Collections.emptyList();
    }

    /**
     * 批量插入分录（红冲用）
     */
    public void batchInsertEntries(List<AccountingVoucherEntryPO> entries) {
        if (entries != null && !entries.isEmpty()) {
            entryMapper.batchInsert(entries);
        }
    }

    /**
     * 批量插入辅助核算项（红冲用）
     */
    public void batchInsertAuxiliaries(List<AccountingVoucherAuxiliaryPO> auxiliaries) {
        if (auxiliaries != null && !auxiliaries.isEmpty()) {
            auxiliaryMapper.batchInsert(auxiliaries);
        }
    }

    /**
     * 更新凭证状态 + 记账人（红冲完成后标记原凭证）
     */
    public int updateStatusAndBookkeeper(String voucherNo,
                                         com.kltb.accounting.core.domain.enums.VoucherStatusEnum status,
                                         String bookkeeperName) {
        return voucherMapper.updateStatusAndBookkeeper(voucherNo, status, bookkeeperName);
    }
}
