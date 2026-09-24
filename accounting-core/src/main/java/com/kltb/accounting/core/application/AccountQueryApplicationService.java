package com.kltb.accounting.core.application;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kltb.accounting.api.request.AccountPageQueryRequest;
import com.kltb.accounting.api.response.AccountPageResponse;
import com.kltb.accounting.api.response.PageResponse;
import com.kltb.accounting.core.domain.enums.AccountStatusEnum;
import com.kltb.accounting.core.domain.enums.BalanceDirectionEnum;
import com.kltb.accounting.core.domain.enums.OwnerTypeEnum;
import com.kltb.accounting.core.domain.enums.RiskStatusEnum;
import com.kltb.accounting.core.infrastructure.persistence.entity.AccountPO;
import com.kltb.accounting.core.infrastructure.persistence.entity.AccountSubjectPO;
import com.kltb.accounting.core.infrastructure.persistence.entity.DictionaryPO;
import com.kltb.accounting.core.infrastructure.persistence.entity.SubAccountPO;
import com.kltb.accounting.core.infrastructure.persistence.repository.AccountRepository;
import com.kltb.accounting.core.infrastructure.persistence.repository.SubjectRepository;
import com.kltb.accounting.core.infrastructure.persistence.repository.SubAccountRepository;
import com.kltb.accounting.core.infrastructure.redis.DictionaryCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 账户综合查询应用服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountQueryApplicationService {

    private final AccountRepository accountRepository;
    private final SubAccountRepository subAccountRepository;
    private final SubjectRepository subjectRepository;

    @Autowired(required = false)
    private DictionaryCacheService dictionaryCacheService;

    /**
     * 账户多维分页检索
     */
    public PageResponse<AccountPageResponse> page(AccountPageQueryRequest request) {
        if (request == null) {
            request = new AccountPageQueryRequest();
        }

        Page<AccountPO> pageParam = new Page<>(
                request.getPageNo() != null ? request.getPageNo() : 1,
                request.getPageSize() != null ? request.getPageSize() : 20
        );

        LambdaQueryWrapper<AccountPO> wrapper = new LambdaQueryWrapper<>();

        // 账户大类过滤：CUSTOMER-客户账户，INTERNAL-内部账户
        if ("CUSTOMER".equalsIgnoreCase(request.getAccountCategory())) {
            wrapper.ne(AccountPO::getOwnerId, "INNER");
        } else if ("INTERNAL".equalsIgnoreCase(request.getAccountCategory())) {
            wrapper.eq(AccountPO::getOwnerId, "INNER");
        }

        // 统一 MyBatis-Plus condition 动态条件
        wrapper.like(StrUtil.isNotBlank(request.getAccountNo()), AccountPO::getAccountNo, StrUtil.trim(request.getAccountNo()))
                .like(StrUtil.isNotBlank(request.getAccountName()), AccountPO::getAccountName, StrUtil.trim(request.getAccountName()))
                .like(StrUtil.isNotBlank(request.getOwnerId()), AccountPO::getOwnerId, StrUtil.trim(request.getOwnerId()))
                .eq(request.getOwnerType() != null, AccountPO::getOwnerType, request.getOwnerType())
                .eq(StrUtil.isNotBlank(request.getSubjectCode()), AccountPO::getSubjectCode, StrUtil.trim(request.getSubjectCode()))
                .eq(StrUtil.isNotBlank(request.getAccountType()), AccountPO::getAccountType, StrUtil.trim(request.getAccountType()))
                .eq(StrUtil.isNotBlank(request.getCurrency()), AccountPO::getCurrency, StrUtil.trim(request.getCurrency()))
                .eq(request.getStatus() != null, AccountPO::getStatus, request.getStatus())
                .eq(request.getRiskStatus() != null, AccountPO::getRiskStatus, request.getRiskStatus())
                .ge(request.getStartDate() != null, AccountPO::getOpenDate, request.getStartDate())
                .le(request.getEndDate() != null, AccountPO::getOpenDate, request.getEndDate())
                .orderByDesc(AccountPO::getId);

        Page<AccountPO> resultPage = accountRepository.selectPage(pageParam, wrapper);
        List<AccountPO> records = resultPage.getRecords();

        if (records == null || records.isEmpty()) {
            return PageResponse.<AccountPageResponse>builder()
                    .current(resultPage.getCurrent())
                    .pages(resultPage.getPages())
                    .total(resultPage.getTotal())
                    .list(Collections.emptyList())
                    .build();
        }

        // 1. 批量查询子账户余额（避免 N+1 查询）
        List<String> accountNos = records.stream()
                .map(AccountPO::getAccountNo)
                .filter(StrUtil::isNotBlank)
                .collect(Collectors.toList());

        List<SubAccountPO> subAccounts = subAccountRepository.selectByAccountNos(accountNos);
        // Map<accountNo, Map<balanceType, balance>>
        Map<String, Map<Integer, BigDecimal>> subBalanceMap = new HashMap<>();
        for (SubAccountPO sub : subAccounts) {
            if (sub.getAccountNo() == null || sub.getBalanceType() == null) continue;
            subBalanceMap.computeIfAbsent(sub.getAccountNo(), k -> new HashMap<>())
                    .put(sub.getBalanceType().getCode(), sub.getBalance() != null ? sub.getBalance() : BigDecimal.ZERO);
        }

        // 2. 批量查询会计科目名称
        Set<String> subjectCodes = records.stream()
                .map(AccountPO::getSubjectCode)
                .filter(StrUtil::isNotBlank)
                .collect(Collectors.toSet());

        Map<String, String> subjectNameMap = new HashMap<>();
        if (!subjectCodes.isEmpty()) {
            List<AccountSubjectPO> subjects = subjectRepository.selectList(
                    new LambdaQueryWrapper<AccountSubjectPO>().in(AccountSubjectPO::getSubjectCode, subjectCodes));
            if (subjects != null) {
                for (AccountSubjectPO sub : subjects) {
                    subjectNameMap.put(sub.getSubjectCode(), sub.getSubjectName());
                }
            }
        }

        // 3. 组装响应对象
        List<AccountPageResponse> responses = new ArrayList<>(records.size());
        for (AccountPO po : records) {
            Map<Integer, BigDecimal> balances = subBalanceMap.getOrDefault(po.getAccountNo(), Collections.emptyMap());
            BigDecimal available = balances.getOrDefault(1, BigDecimal.ZERO);
            BigDecimal frozen = balances.getOrDefault(2, BigDecimal.ZERO);

            OwnerTypeEnum ownerTypeEnum = po.getOwnerType();
            String ownerTypeDesc = ownerTypeEnum != null ? ownerTypeEnum.getDesc() : "其他";

            AccountStatusEnum statusEnum = po.getStatus();
            String statusDesc = statusEnum != null ? statusEnum.getDesc() : "未知";

            RiskStatusEnum riskStatusEnum = po.getRiskStatus();
            String riskStatusDesc = riskStatusEnum != null ? riskStatusEnum.getDesc() : "正常";

            BalanceDirectionEnum directionEnum = po.getBalanceDirection();
            String directionDesc = directionEnum != null ? directionEnum.getDesc() : "借";

            String subjectName = subjectNameMap.getOrDefault(po.getSubjectCode(), "");
            String accountTypeName = resolveAccountTypeName(po.getAccountType());

            responses.add(AccountPageResponse.builder()
                    .id(po.getId())
                    .accountNo(po.getAccountNo())
                    .accountName(po.getAccountName())
                    .ownerId(po.getOwnerId())
                    .ownerType(ownerTypeEnum != null ? ownerTypeEnum.getCode() : null)
                    .ownerTypeDesc(ownerTypeDesc)
                    .subjectCode(po.getSubjectCode())
                    .subjectName(subjectName)
                    .accountType(po.getAccountType())
                    .accountTypeName(accountTypeName)
                    .currency(po.getCurrency())
                    .balanceDirection(directionEnum != null ? directionEnum.getCode() : 1)
                    .balanceDirectionDesc(directionDesc)
                    .openingBalance(po.getOpeningBalance() != null ? po.getOpeningBalance() : BigDecimal.ZERO)
                    .balance(po.getBalance() != null ? po.getBalance() : BigDecimal.ZERO)
                    .availableBalance(available)
                    .frozenBalance(frozen)
                    .status(statusEnum != null ? statusEnum.getCode() : null)
                    .statusDesc(statusDesc)
                    .riskStatus(riskStatusEnum != null ? riskStatusEnum.getCode() : null)
                    .riskStatusDesc(riskStatusDesc)
                    .requestNo(po.getRequestNo())
                    .openDate(po.getOpenDate())
                    .inactiveDate(po.getInactiveDate())
                    .createdAt(po.getCreateTime())
                    .build());
        }

        return PageResponse.<AccountPageResponse>builder()
                .current(resultPage.getCurrent())
                .pages(resultPage.getPages())
                .total(resultPage.getTotal())
                .list(responses)
                .build();
    }

    private String resolveAccountTypeName(String accountType) {
        if (StrUtil.isBlank(accountType)) {
            return "";
        }
        if (dictionaryCacheService != null) {
            try {
                List<DictionaryPO> dicts = dictionaryCacheService.getByType("account_type");
                if (dicts != null) {
                    for (DictionaryPO po : dicts) {
                        if (accountType.equalsIgnoreCase(po.getDictCode())) {
                            return po.getDictName();
                        }
                    }
                }
            } catch (Exception ignored) {
            }
        }
        switch (accountType.toUpperCase()) {
            case "CASH": return "现金账户";
            case "DEPOSIT": return "存款账户";
            case "SETTLE": return "结算账户";
            default: return accountType;
        }
    }
}
