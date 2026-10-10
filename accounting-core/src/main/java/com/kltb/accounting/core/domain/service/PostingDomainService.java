package com.kltb.accounting.core.domain.service;

import cn.hutool.core.util.StrUtil;
import com.kltb.accounting.api.constant.ResultCode;
import com.kltb.accounting.core.domain.enums.AccountStatusEnum;
import com.kltb.accounting.core.domain.enums.BalanceTypeEnum;
import com.kltb.accounting.core.domain.enums.ChangeDirectionEnum;
import com.kltb.accounting.core.domain.enums.VoucherEntryStatusEnum;
import com.kltb.accounting.core.infrastructure.account.AccountBalanceCalculator;
import com.kltb.accounting.core.infrastructure.account.AccountValidator;
import com.kltb.accounting.core.infrastructure.persistence.entity.*;
import com.kltb.accounting.core.infrastructure.persistence.repository.*;
import com.kltb.accounting.core.shared.exception.AccountException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 实时过账领域服务
 * <p>
 * 职责：加锁 → 余额计算 → 账户更新 → 明细快照 → 分录状态更新与持久化
 * 注意：此类不包含事务，由 Application Service 统一控制事务边界。
 * <p>
 * 是否记账：是
 * 异常处理：
 *   - [AccountException] → 余额不足 / 账户状态异常 → 由上层 catch 触发回滚
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PostingDomainService {

    private final AccountRepository accountRepository;
    private final SubAccountRepository subAccountRepository;
    private final AccountDetailRepository accountDetailRepository;
    private final SubAccountDetailRepository subAccountDetailRepository;
    private final AccountingVoucherRepository accountingVoucherRepository;
    private final BusinessRecordRepository businessRecordRepository;
    private final FreezeDetailRepository freezeDetailRepository;
    private final AccountingRuleRepository accountingRuleRepository;
    private final com.kltb.accounting.core.infrastructure.account.FreezeIdGenerator freezeIdGenerator;

    /**
     * 实时过账：锁定账户 → 计算余额 → 更新余额 → 记录明细 → 更新分录状态
     * <p>
     * 约束：
     * 1. 凭证为聚合根，所有业务元数据（txnNo、traceNo、channel、tradeTime等）严格100%继承自上游凭证，禁止在过账阶段塞入任何伪造默认值；
     * 2. 会计日期严格使用凭证对象的 accountingDate，禁止外部透传产生分歧；
     * 3. 若凭证对象为空或核心字段缺失，执行快速失败（Fast-Fail）。
     *
     * @param voucher 记账凭证对象（聚合根，必须包含完备的上游业务元数据）
     * @param entries 需要实时过账的分录列表（is_unilateral=1，调用方按 account_no 升序排列）
     */
    public void executeRealTimePosting(
        AccountingVoucherPO voucher,
        List<AccountingVoucherEntryPO> entries
    ) {
        if (entries == null || entries.isEmpty()) {
            return;
        }

        if (voucher == null) {
            throw new AccountException(ResultCode.PARAM_ERROR, "实时过账凭证对象不能为空");
        }
        if (StrUtil.isBlank(voucher.getTxnNo())) {
            throw new AccountException(ResultCode.PARAM_ERROR,
                "凭证事务号(txnNo)缺失，拒绝过账: voucherNo=" + voucher.getVoucherNo());
        }

        // 检查流水是否关联预冻结单
        AccountFreezeDetailPO origFreezeRecord = null;
        if (StrUtil.isNotBlank(voucher.getTraceNo()) && businessRecordRepository != null) {
            BusinessRecordPO businessRecord = businessRecordRepository.selectByTraceNo(voucher.getTraceNo());
            if (businessRecord != null && StrUtil.isNotBlank(businessRecord.getOrigFreezeNo()) && freezeDetailRepository != null) {
                origFreezeRecord = freezeDetailRepository.selectByVoucherNo(businessRecord.getOrigFreezeNo());
                if (origFreezeRecord == null) {
                    origFreezeRecord = freezeDetailRepository.selectByTraceNo(businessRecord.getOrigFreezeNo());
                }
            }
        }

        // 检查记账规则是否配置了入金冻结时长（freeze_duration）
        AccountingRulePO rule = null;
        if (accountingRuleRepository != null && StrUtil.isNotBlank(voucher.getBusinessCode()) && StrUtil.isNotBlank(voucher.getTradingCode())) {
            rule = accountingRuleRepository.selectByBusinessKey(
                voucher.getBusinessCode(), voucher.getTradingCode(), voucher.getPayChannel());
        }

        // 按 account_no 分组去重（保持升序）
        List<String> accountNos = entries.stream()
            .map(AccountingVoucherEntryPO::getAccountNo)
            .distinct()
            .collect(Collectors.toList());

        // 批量加锁查询账户
        List<AccountPO> accounts = accountRepository.selectForUpdateBatch(accountNos);
        Map<String, AccountPO> accountMap = accounts.stream()
            .collect(Collectors.toMap(AccountPO::getAccountNo, a -> a));

        // 查询子账户
        Map<String, List<SubAccountPO>> subAccountMap = new LinkedHashMap<>();
        for (String accountNo : accountNos) {
            List<SubAccountPO> subs = subAccountRepository.selectForUpdate(accountNo);
            subAccountMap.put(accountNo, subs);
        }

        // 预校验：所有分录的目标账户必须存在且状态可记账（Fast-Fail，防止部分入账后因账户异常回滚）
        for (AccountingVoucherEntryPO entry : entries) {
            AccountPO account = accountMap.get(entry.getAccountNo());
            AccountValidator.validateExists(account, entry.getAccountNo());
            AccountValidator.validatePostable(account, entry.getChangeDirection());
        }

        // 逐分录处理
        List<AccountDetailPO> accountDetails = new ArrayList<>();
        List<SubAccountDetailPO> subAccountDetails = new ArrayList<>();

        for (AccountingVoucherEntryPO entry : entries) {
            AccountPO account = accountMap.get(entry.getAccountNo());
            List<SubAccountPO> subs = subAccountMap.get(entry.getAccountNo());

            // 判断是否是预冻结核销出金（出金方扣减冻结账户）
            boolean isPreFreezePayout = (origFreezeRecord != null
                    && com.kltb.accounting.core.domain.enums.FreezeStatusEnum.FROZEN.equals(origFreezeRecord.getStatus())
                    && entry.getAccountNo().equals(origFreezeRecord.getAccountNo())
                    && ChangeDirectionEnum.DECREASE.equals(entry.getChangeDirection()));

            // 判断是否是规则配置入金冻结（入金方入冻结账户）
            boolean isRuleInflowFreeze = (rule != null
                    && rule.getFreezeDuration() != null
                    && rule.getFreezeDuration() > 0
                    && ChangeDirectionEnum.INCREASE.equals(entry.getChangeDirection()));

            // 计算主账户余额
            BigDecimal oldBalance = account.getBalance();
            BigDecimal newBalance = AccountBalanceCalculator.calculateNewBalance(
                oldBalance,
                entry.getAmount(),
                entry.getChangeDirection()
            );

            // 更新主账户（乐观锁版本号由 MyBatis-Plus 插件自动管理，严禁业务层手动自增破坏 CAS 校验）
            account.setBalance(newBalance);
            accountRepository.updateById(account);

            // 确定目标子账户类型（常规：AVAILABLE；预冻结核销或入金冻结：FROZEN）
            BalanceTypeEnum targetBalanceType = (isPreFreezePayout || isRuleInflowFreeze)
                    ? BalanceTypeEnum.FROZEN
                    : BalanceTypeEnum.AVAILABLE;

            SubAccountPO subAccount = (subs != null)
                ? subs.stream().filter(s -> targetBalanceType.equals(s.getBalanceType())).findFirst().orElse(null)
                : null;
            BigDecimal subOldBalance = BigDecimal.ZERO;
            BigDecimal subNewBalance = BigDecimal.ZERO;

            if (subAccount != null) {
                subOldBalance = subAccount.getBalance();
                subNewBalance = AccountBalanceCalculator.calculateNewBalance(
                    subOldBalance,
                    entry.getAmount(),
                    entry.getChangeDirection()
                );
                subAccount.setBalance(subNewBalance);
                subAccountRepository.updateById(subAccount);
            }

            // 若为预冻结出金，更新冻结记录的已扣减金额和剩余冻结金额
            if (isPreFreezePayout && freezeDetailRepository != null) {
                BigDecimal currentDeducted = origFreezeRecord.getDeductedAmount() != null
                        ? origFreezeRecord.getDeductedAmount() : BigDecimal.ZERO;
                BigDecimal newDeducted = currentDeducted.add(entry.getAmount());
                BigDecimal remainingFreeze = origFreezeRecord.getFreezeAmount().subtract(entry.getAmount());
                com.kltb.accounting.core.domain.enums.FreezeStatusEnum newStatus = remainingFreeze.compareTo(BigDecimal.ZERO) <= 0
                        ? com.kltb.accounting.core.domain.enums.FreezeStatusEnum.UNFROZEN
                        : com.kltb.accounting.core.domain.enums.FreezeStatusEnum.FROZEN;
                freezeDetailRepository.updateAmountsAndStatus(
                        origFreezeRecord.getVoucherNo(),
                        remainingFreeze.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : remainingFreeze,
                        origFreezeRecord.getUnfrozenAmount(),
                        newDeducted,
                        newStatus,
                        origFreezeRecord.getVersion()
                );
            }

            // 若为规则配置入金冻结，创建新的冻结记录（由 AutoUnfreezeJobHandler 到期自动解冻）
            if (isRuleInflowFreeze && freezeDetailRepository != null && freezeIdGenerator != null) {
                AccountFreezeDetailPO inflowFreeze = new AccountFreezeDetailPO();
                String freezeId = freezeIdGenerator.generate();
                inflowFreeze.setVoucherNo(freezeId);
                inflowFreeze.setAccountNo(entry.getAccountNo());
                inflowFreeze.setBusinessCode(voucher.getBusinessCode());
                inflowFreeze.setTradingCode(voucher.getTradingCode());
                inflowFreeze.setTraceNo(voucher.getTraceNo());
                inflowFreeze.setFreezeAmount(entry.getAmount());
                inflowFreeze.setOrigFreezeAmount(entry.getAmount());
                inflowFreeze.setUnfrozenAmount(BigDecimal.ZERO);
                inflowFreeze.setDeductedAmount(BigDecimal.ZERO);
                inflowFreeze.setStatus(com.kltb.accounting.core.domain.enums.FreezeStatusEnum.FROZEN);
                inflowFreeze.setTradeTime(LocalDateTime.now());
                inflowFreeze.setExpireTime(LocalDateTime.now().plusSeconds(rule.getFreezeDuration()));
                inflowFreeze.setSummary("规则配置入金冻结(" + rule.getFreezeDuration() + "秒)");
                freezeDetailRepository.insert(inflowFreeze);
            }

            // 写入 t_account_detail（所有业务元数据100%严格继承自凭证）
            AccountDetailPO detail = new AccountDetailPO();
            detail.setVoucherNo(voucher.getVoucherNo())
                .setEntryId(entry.getEntryId())
                .setTxnNo(StrUtil.isNotBlank(voucher.getTxnNo()) ? voucher.getTxnNo() : "")
                .setTraceNo(StrUtil.isNotBlank(voucher.getTraceNo()) ? voucher.getTraceNo() : (StrUtil.isNotBlank(voucher.getVoucherNo()) ? voucher.getVoucherNo() : ""))
                .setTraceSeq(voucher.getTraceSeq() != null ? voucher.getTraceSeq() : 1)
                .setSubjectCode(entry.getSubjectCode())
                .setAccountNo(entry.getAccountNo())
                .setBusinessCode(StrUtil.isNotBlank(voucher.getBusinessCode()) ? voucher.getBusinessCode() : "")
                .setTradingCode(StrUtil.isNotBlank(voucher.getTradingCode()) ? voucher.getTradingCode() : "")
                .setPayChannel(StrUtil.isNotBlank(voucher.getPayChannel()) ? voucher.getPayChannel() : "")
                .setTradeType(voucher.getTradeType())
                .setTradeTime(voucher.getTradeTime())
                .setDebitCredit(entry.getDebitCredit())
                .setChangeDirection(entry.getChangeDirection())
                .setCurrency(entry.getCurrency())
                .setPreBalance(oldBalance)
                .setAmount(entry.getAmount())
                .setPostBalance(newBalance)
                .setAccountingDate(voucher.getAccountingDate())
                .setSummary(StrUtil.isNotBlank(entry.getSummary()) ? entry.getSummary() : voucher.getSummary())
                .setTenantId(voucher.getTenantId());
            accountDetails.add(detail);

            // 写入 t_sub_account_detail
            if (subAccount != null) {
                SubAccountDetailPO subDetail = new SubAccountDetailPO();
                subDetail.setVoucherNo(voucher.getVoucherNo())
                    .setEntryId(entry.getEntryId())
                    .setTxnNo(StrUtil.isNotBlank(voucher.getTxnNo()) ? voucher.getTxnNo() : "")
                    .setTraceNo(StrUtil.isNotBlank(voucher.getTraceNo()) ? voucher.getTraceNo() : (StrUtil.isNotBlank(voucher.getVoucherNo()) ? voucher.getVoucherNo() : ""))
                    .setTraceSeq(voucher.getTraceSeq() != null ? voucher.getTraceSeq() : 1)
                    .setAccountNo(entry.getAccountNo())
                    .setBalanceType(subAccount.getBalanceType() != null ? subAccount.getBalanceType() : BalanceTypeEnum.AVAILABLE)
                    .setTradingCode(StrUtil.isNotBlank(voucher.getTradingCode()) ? voucher.getTradingCode() : "")
                    .setTradeType(voucher.getTradeType())
                    .setTradeTime(voucher.getTradeTime())
                    .setDebitCredit(entry.getDebitCredit())
                    .setChangeDirection(entry.getChangeDirection())
                    .setCurrency(entry.getCurrency())
                    .setPreBalance(subOldBalance)
                    .setAmount(entry.getAmount())
                    .setPostBalance(subNewBalance)
                    .setAccountingDate(voucher.getAccountingDate())
                    .setSummary(StrUtil.isNotBlank(entry.getSummary()) ? entry.getSummary() : voucher.getSummary())
                    .setTenantId(voucher.getTenantId());
                subAccountDetails.add(subDetail);
            }

            // 更新分录状态为已过账并持久化到数据库
            entry.setStatus(VoucherEntryStatusEnum.POSTED);
            entry.setBalanceUpdateTime(LocalDateTime.now());
            accountingVoucherRepository.updateEntryById(entry);
        }

        // 批量写入明细
        accountDetailRepository.batchInsert(accountDetails);
        if (!subAccountDetails.isEmpty()) {
            subAccountDetailRepository.batchInsert(subAccountDetails);
        }

        log.info("[POSTING] 实时过账完成: voucherNo={}, entryCount={}, detailCount={}",
            voucher.getVoucherNo(), entries.size(), accountDetails.size());
    }
}
