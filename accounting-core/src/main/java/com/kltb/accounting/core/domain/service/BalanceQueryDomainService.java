package com.kltb.accounting.core.domain.service;

import com.kltb.accounting.api.constant.ResultCode;
import com.kltb.accounting.core.domain.enums.BalanceTypeEnum;
import com.kltb.accounting.core.infrastructure.persistence.entity.AccountDetailPO;
import com.kltb.accounting.core.infrastructure.persistence.entity.AccountFreezeDetailPO;
import com.kltb.accounting.core.infrastructure.persistence.entity.AccountPO;
import com.kltb.accounting.core.infrastructure.persistence.entity.SubAccountPO;
import com.kltb.accounting.core.infrastructure.persistence.repository.AccountDetailRepository;
import com.kltb.accounting.core.infrastructure.persistence.repository.AccountRepository;
import com.kltb.accounting.core.infrastructure.persistence.repository.BufferPostingDetailRepository;
import com.kltb.accounting.core.infrastructure.persistence.repository.FreezeDetailRepository;
import com.kltb.accounting.core.infrastructure.persistence.repository.SubAccountRepository;
import com.kltb.accounting.core.shared.exception.AccountException;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

/**
 * 余额查询领域服务
 * <p>
 * 职责：
 * 1. 聚合余额查询（主账户 + 可用子账户 + 冻结子账户 + 缓冲预估）
 * 2. 账户明细分页查询
 * 3. 冻结记录分页查询
 * <p>
 * 是否记账：否，纯查询操作
 * 异常处理：
 *   - AccountException → 账户不存在/参数非法等
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BalanceQueryDomainService {

    private final AccountRepository accountRepository;
    private final SubAccountRepository subAccountRepository;
    private final BufferPostingDetailRepository bufferPostingDetailRepository;
    private final AccountDetailRepository accountDetailRepository;
    private final FreezeDetailRepository freezeDetailRepository;

    /**
     * 聚合余额查询（主账户 + 可用 + 冻结 + 缓冲预估）
     * <p>
     * 功能描述：查询指定账户的完整余额信息，包括主账户余额、可用子账户余额汇总、
     * 冻结子账户余额汇总、缓冲预估金额。
     * <p>
     * 是否记账：否，纯查询操作。
     * <p>
     * 异常处理：AccountException → 账户不存在时抛出。
     *
     * @param accountNo 账户编号
     * @return 聚合余额 DTO
     */
    public AggregateBalanceDTO queryAggregateBalance(String accountNo) {
        // 1. 查询主账户
        AccountPO account = accountRepository.selectBalanceByAccountNo(accountNo);
        if (account == null) {
            throw new AccountException(ResultCode.ACCOUNT_NOT_FOUND, "账户不存在: " + accountNo);
        }

        // 2. 查询子账户列表
        List<SubAccountPO> subAccounts = subAccountRepository.selectBalanceByAccountNo(accountNo);

        // 3. 汇总缓冲预估
        BigDecimal bufferEstimate = bufferPostingDetailRepository.sumPendingAmountByAccountNo(accountNo);

        // 4. 应用层组装
        BigDecimal availableBalance = BigDecimal.ZERO;
        BigDecimal frozenBalance = BigDecimal.ZERO;
        for (SubAccountPO sub : subAccounts) {
            if (sub.getBalance() != null) {
                if (BalanceTypeEnum.AVAILABLE.equals(sub.getBalanceType())) {
                    availableBalance = availableBalance.add(sub.getBalance());
                } else if (BalanceTypeEnum.FROZEN.equals(sub.getBalanceType())) {
                    frozenBalance = frozenBalance.add(sub.getBalance());
                }
            }
        }

        AggregateBalanceDTO dto = new AggregateBalanceDTO();
        dto.setAccountNo(account.getAccountNo());
        dto.setAccountName(account.getAccountName());
        dto.setSubjectCode(account.getSubjectCode());
        dto.setMainBalance(account.getBalance() != null ? account.getBalance() : BigDecimal.ZERO);
        dto.setAvailableBalance(availableBalance);
        dto.setFrozenBalance(frozenBalance);
        dto.setBufferEstimate(bufferEstimate);
        dto.setTotalBalance(availableBalance.add(frozenBalance));
        dto.setStatus(account.getStatus() != null ? account.getStatus().getCode() : null);
        dto.setRiskStatus(account.getRiskStatus() != null ? account.getRiskStatus().getCode() : null);
        dto.setBalanceDirection(account.getBalanceDirection() != null ? account.getBalanceDirection().getCode() : null);
        dto.setCurrency(account.getCurrency());
        dto.setQueryTime(currentTime());
        return dto;
    }

    /**
     * 获取当前时间，便于测试时覆写
     */
    protected LocalDateTime currentTime() {
        return LocalDateTime.now();
    }

    /**
     * 账户明细分页查询
     * <p>
     * 功能描述：按账户编号、日期范围、交易类别、借贷方向等条件分页查询账户变动明细。
     * <p>
     * 是否记账：否，纯查询操作。
     * <p>
     * 异常处理：无特殊异常处理，无数据时返回空列表。
     *
     * @param accountNo   账户编号
     * @param startDate   起始日期
     * @param endDate     结束日期
     * @param tradeType   交易类别
     * @param debitCredit 借贷方向
     * @param pageNo      页码
     * @param pageSize    每页条数
     * @return 分页结果
     */
    public AccountDetailPageResult queryAccountDetails(
            String accountNo, LocalDate startDate, LocalDate endDate,
            Integer tradeType, Integer debitCredit, int pageNo, int pageSize) {
        long offset = (long) (pageNo - 1) * pageSize;
        com.baomidou.mybatisplus.core.metadata.IPage<AccountDetailPO> page =
                accountDetailRepository.selectPageByCondition(
                        accountNo, startDate, endDate, tradeType, debitCredit, offset, pageSize);
        Long total = page.getTotal();
        long pages = page.getPages();
        return new AccountDetailPageResult(total, pages, pageNo, page.getRecords());
    }

    /**
     * 冻结记录分页查询
     * <p>
     * 功能描述：按账户编号和状态分页查询冻结记录。
     * <p>
     * 是否记账：否，纯查询操作。
     * <p>
     * 异常处理：无特殊异常处理，无数据时返回空列表。
     *
     * @param accountNo 账户编号
     * @param status    状态过滤（可选）
     * @param pageNo    页码
     * @param pageSize  每页条数
     * @return 分页结果
     */
    public FreezeRecordPageResult queryFreezeRecords(
            String accountNo, Integer status, int pageNo, int pageSize) {
        long offset = (long) (pageNo - 1) * pageSize;
        com.baomidou.mybatisplus.core.metadata.IPage<AccountFreezeDetailPO> page =
                freezeDetailRepository.selectPageByAccountNo(accountNo, status, offset, pageSize);
        Long total = page != null ? page.getTotal() : 0L;
        long pages = page != null ? page.getPages() : 0L;
        List<AccountFreezeDetailPO> records = page != null ? page.getRecords() : Collections.emptyList();
        return new FreezeRecordPageResult(total, pages, pageNo, records != null ? records : Collections.emptyList());
    }

    // ==================== DTO ====================

    /**
     * 聚合余额内部 DTO
     */
    @Data
    public static class AggregateBalanceDTO {
        private String accountNo;
        private String accountName;
        private String subjectCode;
        private BigDecimal mainBalance;
        private BigDecimal availableBalance;
        private BigDecimal frozenBalance;
        private BigDecimal bufferEstimate;
        private BigDecimal totalBalance;
        private Integer status;
        private Integer riskStatus;
        private Integer balanceDirection;
        private String currency;
        private LocalDateTime queryTime;
    }

    /**
     * 账户明细分页结果
     */
    @Data
    public static class AccountDetailPageResult {
        private final Long total;
        private final Long pages;
        private final Integer current;
        private final List<AccountDetailPO> list;

        public AccountDetailPageResult(Long total, Long pages, Integer current, List<AccountDetailPO> list) {
            this.total = total;
            this.pages = pages;
            this.current = current;
            this.list = list;
        }
    }

    /**
     * 冻结记录分页结果
     */
    @Data
    public static class FreezeRecordPageResult {
        private final Long total;
        private final Long pages;
        private final Integer current;
        private final List<AccountFreezeDetailPO> list;

        public FreezeRecordPageResult(Long total, Long pages, Integer current, List<AccountFreezeDetailPO> list) {
            this.total = total;
            this.pages = pages;
            this.current = current;
            this.list = list;
        }
    }
}
