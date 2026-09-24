package com.kltb.accounting.core.domain.service;

import com.kltb.accounting.api.constant.ResultCode;
import com.kltb.accounting.core.domain.enums.ChangeDirectionEnum;
import com.kltb.accounting.core.domain.enums.DebitCreditEnum;
import com.kltb.accounting.core.domain.enums.TradeTypeEnum;
import com.kltb.accounting.core.domain.enums.VoucherEntryStatusEnum;
import com.kltb.accounting.core.domain.enums.VoucherStatusEnum;
import com.kltb.accounting.core.infrastructure.account.RedisSequenceGenerator;
import com.kltb.accounting.core.infrastructure.cache.AccountingDateCache;
import com.kltb.accounting.core.infrastructure.persistence.entity.AccountingVoucherAuxiliaryPO;
import com.kltb.accounting.core.infrastructure.persistence.entity.AccountingVoucherEntryPO;
import com.kltb.accounting.core.infrastructure.persistence.entity.AccountingVoucherPO;
import com.kltb.accounting.core.infrastructure.persistence.repository.AccountingVoucherRepository;
import com.kltb.accounting.core.infrastructure.redis.DistributedLockTemplate;
import com.kltb.accounting.core.shared.context.TenantContext;
import com.kltb.accounting.core.shared.exception.AccountException;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 红冲（冲账）核心领域服务
 * <p>
 * 职责：
 * 1. 前置校验（凭证已过账 + 分录已全部过账 + 未重复红冲）
 * 2. 生成红冲凭证（trade_type=RED，借贷方向对调，金额保持正数）
 * 3. 红冲过账（走标准过账链路）
 * 4. 状态联动（原凭证标记 REVERSED，红冲凭证标记 POSTED）
 * <p>
 * 核心原则：
 * - 红冲不可删除原凭证及其分录，必须保留完整审计轨迹
 * - 红冲凭证的会计日期 = 发起红冲时的会计日期（不是原凭证会计日期）
 * - 红冲凭证的借贷方向对调，但金额保持正数（负数运算违反财务律法）
 * <p>
 * 与 Step 11 Rollback 的边界：RollbackDomainService 负责过账失败回滚（异常路径），
 * 本服务负责业务红冲（正常业务流程，原凭证已过账后需要冲销）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReversalDomainService {

    private static final String REVERSAL_LOCK_FORMAT = "reversal:%s";

    private final AccountingVoucherRepository voucherRepository;
    private final RedisSequenceGenerator seqGen;
    private final DistributedLockTemplate distributedLockTemplate;
    private final TransactionTemplate transactionTemplate;
    private final PostingDomainService postingDomainService;
    private final AccountingDateCache accountingDateCache;

    /**
     * 执行凭证红冲（主流程）
     * <p>
     * 在分布式锁内完成全部红冲逻辑，防止并发红冲同一凭证。
     *
     * @param origVoucherNo  原凭证号
     * @param bookkeeperName 记账人姓名
     * @param summary        红冲摘要
     * @return 红冲结果
     */
    public ReversalResult executeReversal(String origVoucherNo, String bookkeeperName, String summary) {
        if (origVoucherNo == null || origVoucherNo.trim().isEmpty()) {
            throw new AccountException(ResultCode.REVERSAL_ORIGINAL_NOT_FOUND, "原凭证号不能为空");
        }

        // 锁外预检查：快速失败
        AccountingVoucherPO preCheckVoucher = voucherRepository.selectByVoucherNoSimple(origVoucherNo);
        validateReversable(preCheckVoucher);

        // 分布式锁内执行完整红冲流程
        String lockKey = String.format(REVERSAL_LOCK_FORMAT, origVoucherNo);
        return distributedLockTemplate.execute(
                lockKey,
                3, -1,
                () -> doExecuteReversal(origVoucherNo, bookkeeperName, summary)
        );
    }

    /**
     * 在分布式锁内执行红冲（含事务控制）
     */
    private ReversalResult doExecuteReversal(String origVoucherNo, String bookkeeperName, String summary) {
        // 获取当前会计日期
        LocalDate accountingDate = accountingDateCache.getCurrentDate();
        // 双重检查（锁内再次校验）
        AccountingVoucherPO currentVoucher = voucherRepository.selectByVoucherNoSimple(origVoucherNo);
        validateReversable(currentVoucher);

        LocalDateTime now = LocalDateTime.now();

        // 查询原凭证所有分录
        List<AccountingVoucherEntryPO> origEntries = voucherRepository.selectEntriesByVoucherNo(origVoucherNo);

        // 批量查询所有辅助核算项（按 entryId 分组，避免 N+1 查询）
        Map<String, List<AccountingVoucherAuxiliaryPO>> auxByEntryId =
                batchQueryAuxiliaries(origEntries);

        // 生成红冲凭证号
        String reversalVoucherNo = seqGen.generate("REV", accountingDate, 6, 25);

        // 计算红冲金额（与原凭证相同）
        BigDecimal reversalAmount = currentVoucher.getAmount();

        // 构建红冲摘要
        String reversalSummary = summary != null && !summary.trim().isEmpty()
                ? summary
                : "红冲凭证:" + origVoucherNo;

        // 在事务内完成：写入红冲凭证 + 分录 + 辅助项 + 过账 + 状态更新
        return transactionTemplate.execute(status -> {
                // 1. 写入红冲凭证（使用锁内双重检查后的 currentVoucher，避免引用过期数据）
                AccountingVoucherPO reversalVoucher = buildReversalVoucher(
                        reversalVoucherNo, currentVoucher, accountingDate, reversalSummary,
                        bookkeeperName, now, reversalAmount);
                voucherRepository.insert(reversalVoucher);

                // 2. 复制并反转分录
                List<AccountingVoucherEntryPO> reversalEntries = new ArrayList<>();
                for (int i = 0; i < origEntries.size(); i++) {
                    AccountingVoucherEntryPO origEntry = origEntries.get(i);
                    AccountingVoucherEntryPO reversalEntry = buildReversalEntry(
                            reversalVoucherNo, origEntry, i + 1, accountingDate, now);
                    reversalEntries.add(reversalEntry);
                }
                voucherRepository.batchInsertEntries(reversalEntries);

                // 3. 复制辅助核算项（批量查询 + 按 rowNum 索引匹配，O(n) 而非 O(n×m)）
                for (int i = 0; i < origEntries.size(); i++) {
                    AccountingVoucherEntryPO origEntry = origEntries.get(i);
                    List<AccountingVoucherAuxiliaryPO> origAuxiliaries =
                            auxByEntryId.get(origEntry.getEntryId());
                    if (origAuxiliaries != null && !origAuxiliaries.isEmpty()) {
                        // reversalEntries 按 rowNum = i+1 顺序构建，直接用索引访问
                        AccountingVoucherEntryPO matchingReversalEntry = reversalEntries.get(i);
                        List<AccountingVoucherAuxiliaryPO> reversalAuxiliaries = new ArrayList<>();
                        for (AccountingVoucherAuxiliaryPO origAux : origAuxiliaries) {
                            AccountingVoucherAuxiliaryPO reversalAux = buildReversalAuxiliary(
                                    matchingReversalEntry, origAux, accountingDate);
                            reversalAuxiliaries.add(reversalAux);
                        }
                        voucherRepository.batchInsertAuxiliaries(reversalAuxiliaries);
                    }
                }

                // 4. 执行过账（红冲分录走标准过账链路）
                postingDomainService.executeRealTimePosting(reversalEntries, accountingDate);

                // 5. 更新红冲凭证状态为 POSTED
                reversalVoucher.setStatus(VoucherStatusEnum.POSTED);
                reversalVoucher.setPostTime(now);
                voucherRepository.updateStatusByVoucherNo(reversalVoucherNo, VoucherStatusEnum.POSTED.getCode());

                // 6. 更新原凭证状态为 REVERSED
                voucherRepository.updateStatusAndBookkeeper(
                        origVoucherNo, VoucherStatusEnum.REVERSED, bookkeeperName);

                log.info("[REVERSAL] 红冲完成 origVoucherNo={} reversalVoucherNo={} entryCount={}",
                        origVoucherNo, reversalVoucherNo, reversalEntries.size());

                return new ReversalResult(
                        origVoucherNo,
                        reversalVoucherNo,
                        reversalAmount,
                        accountingDate,
                        reversalEntries.size(),
                        now,
                        TradeTypeEnum.RED.getCode());
        });
    }

    /**
     * 批量查询辅助核算项（按 entryId 分组）
     * 避免在循环中逐条查询导致的 N+1 问题
     */
    private Map<String, List<AccountingVoucherAuxiliaryPO>> batchQueryAuxiliaries(
            List<AccountingVoucherEntryPO> entries) {
        if (entries == null || entries.isEmpty()) {
            return new HashMap<>();
        }
        List<String> entryIds = entries.stream()
                .map(AccountingVoucherEntryPO::getEntryId)
                .collect(Collectors.toList());
        List<AccountingVoucherAuxiliaryPO> allAuxiliaries =
                voucherRepository.selectAuxiliaryByEntryIds(entryIds);
        return allAuxiliaries.stream()
                .collect(Collectors.groupingBy(AccountingVoucherAuxiliaryPO::getEntryId));
    }

    /**
     * 判断凭证是否可被红冲
     */
    public boolean isReversable(String voucherNo) {
        try {
            AccountingVoucherPO voucher = voucherRepository.selectByVoucherNoSimple(voucherNo);
            validateReversable(voucher);
            return true;
        } catch (AccountException e) {
            log.debug("[REVERSAL] 凭证不可红冲 voucherNo={} reason={}", voucherNo, e.getMessage());
            return false;
        }
    }

    /**
     * 查询某凭证的所有红冲记录
     */
    public List<AccountingVoucherPO> queryReversalRecords(String origVoucherNo) {
        return voucherRepository.selectReversalByOrig(origVoucherNo);
    }

    /**
     * 红冲前置校验（锁外预检查 + 锁内双重检查共用）
     */
    private void validateReversable(AccountingVoucherPO voucher) {
        if (voucher == null) {
            throw new AccountException(ResultCode.REVERSAL_ORIGINAL_NOT_FOUND, "原凭证不存在");
        }

        // 以下检查仅在凭证非 null 时执行
        // 检查状态 == POSTED(3)
        if (voucher.getStatus() != VoucherStatusEnum.POSTED) {
            throw new AccountException(ResultCode.REVERSAL_ORIGINAL_NOT_POSTED,
                    "原凭证未过账，不可红冲: voucherNo=" + voucher.getVoucherNo()
                            + ", status=" + voucher.getStatus().getDesc());
        }

        // 检查所有分录 status == POSTED(2)
        List<AccountingVoucherEntryPO> entries = voucherRepository.selectEntriesByVoucherNo(voucher.getVoucherNo());
        for (AccountingVoucherEntryPO entry : entries) {
            if (entry.getStatus() != VoucherEntryStatusEnum.POSTED) {
                throw new AccountException(ResultCode.REVERSAL_ENTRIES_NOT_ALL_POSTED,
                        "原凭证分录未全部过账: entryId=" + entry.getEntryId()
                                + ", status=" + entry.getStatus().getDesc());
            }
        }

        // 检查不存在重复红冲
        List<AccountingVoucherPO> existingReversals = voucherRepository.selectReversalByOrig(voucher.getVoucherNo());
        if (!existingReversals.isEmpty()) {
            throw new AccountException(ResultCode.REVERSAL_ALREADY_EXISTS,
                    "红冲记录已存在，不可重复红冲: origVoucherNo=" + voucher.getVoucherNo()
                            + ", reversalCount=" + existingReversals.size());
        }
    }

    /**
     * 构建红冲凭证 PO
     */
    private AccountingVoucherPO buildReversalVoucher(
            String reversalVoucherNo,
            AccountingVoucherPO origVoucher,
            LocalDate accountingDate,
            String summary,
            String bookkeeperName,
            LocalDateTime now,
            BigDecimal amount) {

        AccountingVoucherPO voucher = new AccountingVoucherPO();
        voucher.setVoucherNo(reversalVoucherNo);
        voucher.setTxnNo("");
        voucher.setTraceNo(origVoucher.getTraceNo());
        voucher.setTraceSeq(origVoucher.getTraceSeq());
        voucher.setVoucherType(origVoucher.getVoucherType());
        voucher.setPostingType(origVoucher.getPostingType());
        voucher.setBusinessCode(origVoucher.getBusinessCode());
        voucher.setTradingCode(origVoucher.getTradingCode());
        voucher.setPayChannel(origVoucher.getPayChannel());
        voucher.setTradeType(TradeTypeEnum.RED);
        voucher.setTradeTime(now);
        voucher.setAmount(amount);
        voucher.setStatus(VoucherStatusEnum.POSTING);
        voucher.setAccountingDate(accountingDate);
        voucher.setSummary(summary);
        voucher.setAttachmentCount(origVoucher.getAttachmentCount());
        voucher.setOrigVoucherNo(origVoucher.getVoucherNo());
        voucher.setBookkeeperName(bookkeeperName);
        voucher.setTenantId(TenantContext.get());
        return voucher;
    }

    /**
     * 构建红冲分录 PO（借贷方向对调）
     */
    private AccountingVoucherEntryPO buildReversalEntry(
            String reversalVoucherNo,
            AccountingVoucherEntryPO origEntry,
            int rowNum,
            LocalDate accountingDate,
            LocalDateTime now) {

        AccountingVoucherEntryPO entry = new AccountingVoucherEntryPO();
        entry.setVoucherNo(reversalVoucherNo);
        // 生成新分录流水号
        entry.setEntryId(seqGen.generate("REV_ENTRY", now, "yyyyMMddHHmmssSSS", 4, 2));
        entry.setRowNum(rowNum);
        entry.setSubjectCode(origEntry.getSubjectCode());
        entry.setAccountNo(origEntry.getAccountNo());

        // 关键：借贷方向对调（借→贷，贷→借）
        DebitCreditEnum originalDebitCredit = origEntry.getDebitCredit();
        if (originalDebitCredit == DebitCreditEnum.DEBIT) {
            entry.setDebitCredit(DebitCreditEnum.CREDIT);
        } else if (originalDebitCredit == DebitCreditEnum.CREDIT) {
            entry.setDebitCredit(DebitCreditEnum.DEBIT);
        } else {
            throw new AccountException(ResultCode.REVERSAL_ENTRY_DIRECTION_INVALID,
                    "原分录借贷方向非法: entryId=" + origEntry.getEntryId());
        }

        // 金额保持正数
        entry.setAmount(origEntry.getAmount());
        entry.setCurrency(origEntry.getCurrency());
        entry.setExchangeRate(origEntry.getExchangeRate());
        entry.setUnitPrice(origEntry.getUnitPrice());
        entry.setQuantity(origEntry.getQuantity());
        entry.setPricingUnit(origEntry.getPricingUnit());
        entry.setSummary("红冲:" + origEntry.getSummary());
        entry.setStatus(VoucherEntryStatusEnum.PENDING);
        entry.setAccountingDate(accountingDate);
        // 复制原分录的过账标记（实时/异步/缓冲），红冲分录走相同过账链路
        entry.setUnilateral(origEntry.getUnilateral());
        entry.setBuffered(origEntry.getBuffered());

        // change_direction 不修改（过账引擎会根据 debit_credit + balance_direction 重新计算）
        entry.setChangeDirection(origEntry.getChangeDirection());

        entry.setBalanceUpdateTime(null);
        entry.setTenantId(TenantContext.get());
        return entry;
    }

    /**
     * 构建红冲辅助核算项 PO
     */
    private AccountingVoucherAuxiliaryPO buildReversalAuxiliary(
            AccountingVoucherEntryPO reversalEntry,
            AccountingVoucherAuxiliaryPO origAux,
            LocalDate accountingDate) {

        AccountingVoucherAuxiliaryPO aux = new AccountingVoucherAuxiliaryPO();
        aux.setVoucherNo(reversalEntry.getVoucherNo());
        aux.setEntryId(reversalEntry.getEntryId());
        aux.setSubjectCode(origAux.getSubjectCode());
        aux.setAuxType(origAux.getAuxType());
        aux.setAuxCode(origAux.getAuxCode());
        aux.setAuxName(origAux.getAuxName());
        // 红冲对调 change_direction（增→减，减→增），保证辅助项报表与借贷方向一致
        ChangeDirectionEnum origDir = origAux.getChangeDirection();
        aux.setChangeDirection(
                origDir == ChangeDirectionEnum.INCREASE ? ChangeDirectionEnum.DECREASE : ChangeDirectionEnum.INCREASE);
        aux.setAmount(origAux.getAmount());
        aux.setAccountingDate(accountingDate);
        aux.setTenantId(TenantContext.get());
        return aux;
    }

    /**
     * 红冲结果
     */
    @Data
    public static class ReversalResult {
        private final String origVoucherNo;
        private final String reversalVoucherNo;
        private final BigDecimal amount;
        private final LocalDate accountingDate;
        private final int entryCount;
        private final LocalDateTime reversaledAt;
        private final Integer tradeType;

        public ReversalResult(String origVoucherNo, String reversalVoucherNo,
                              BigDecimal amount, LocalDate accountingDate,
                              int entryCount, LocalDateTime reversaledAt,
                              Integer tradeType) {
            this.origVoucherNo = origVoucherNo;
            this.reversalVoucherNo = reversalVoucherNo;
            this.amount = amount;
            this.accountingDate = accountingDate;
            this.entryCount = entryCount;
            this.reversaledAt = reversaledAt;
            this.tradeType = tradeType;
        }
    }
}
