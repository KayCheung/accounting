package com.kltb.accounting.core.application;

import com.kltb.accounting.api.constant.ResultCode;
import com.kltb.accounting.api.request.AccountOpenRequest;
import com.kltb.accounting.api.request.AccountTemplateBatchOpenRequest;
import com.kltb.accounting.api.request.InternalAccountOpenRequest;
import com.kltb.accounting.api.response.AccountOpenResponse;
import com.kltb.accounting.api.response.BatchOpenResultResponse;
import com.kltb.accounting.core.application.assembler.AccountOpeningAssembler;
import com.kltb.accounting.core.domain.enums.CustomerTypeEnum;
import com.kltb.accounting.core.domain.model.BatchOpenResult;
import com.kltb.accounting.core.domain.service.AccountOpeningDomainService;
import com.kltb.accounting.core.infrastructure.persistence.entity.AccountPO;
import com.kltb.accounting.core.infrastructure.persistence.repository.AccountRepository;
import com.kltb.accounting.core.shared.exception.AccountException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 开户应用服务（用例编排）
 * <p>
 * 职责：
 * 1. 将 Request DTO 转换为领域方法参数
 * 2. 调用领域服务完成开户
 * 3. 将返回的 PO 转换为 Response DTO
 * 4. 预留事件通知
 * <p>
 * 开户持久化在 AccountOpeningDomainService 内部完成（通过 TransactionTemplate 管理事务），
 * 本类不直接调用 Repository 做 insert/update。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountOpeningApplicationService {

    private final AccountOpeningDomainService accountOpeningDomainService;
    private final AccountOpeningAssembler accountOpeningAssembler;
    private final AccountRepository accountRepository;

    /**
     * 外部客户开户（单账户）
     */
    public AccountOpenResponse openExternalAccount(AccountOpenRequest request) {
        AccountPO account = accountOpeningDomainService.openExternalAccount(
                request.getBusinessCode(),
                request.getCustomerId(),
                request.getCustomerName(),
                CustomerTypeEnum.fromValue(request.getCustomerType()),
                request.getSubjectCode(),
                request.getRequestNo()
        );

        // TODO Step 10 接入真实 MQ
        log.info("Account opened event (reserved): accountNo={}", account.getAccountNo());

        return accountOpeningAssembler.toResponse(account);
    }

    /**
     * 按模板批量开立客户账户（单模板多科目账户批量开出）
     */
    public List<AccountOpenResponse> openExternalAccounts(AccountTemplateBatchOpenRequest request) {
        List<AccountPO> accounts = accountOpeningDomainService.openExternalAccounts(
                request.getBusinessCode(),
                request.getCustomerId(),
                request.getCustomerName(),
                CustomerTypeEnum.fromValue(request.getCustomerType()),
                null,
                request.getTemplateName(),
                request.getRequestNo()
        );

        List<AccountOpenResponse> responses = new ArrayList<>(accounts.size());
        for (AccountPO acc : accounts) {
            responses.add(accountOpeningAssembler.toResponse(acc));
        }
        return responses;
    }

    /**
     * 内部账户开户
     */
    public AccountOpenResponse openInternalAccount(InternalAccountOpenRequest request) {
        AccountPO account = accountOpeningDomainService.openInternalAccount(
                request.getSubjectCode()
        );
        log.info("Internal account opened: accountNo={}", account.getAccountNo());
        return accountOpeningAssembler.toResponse(account);
    }

    /**
     * 批量扫描内部账户
     */
    public BatchOpenResultResponse batchOpenInternalAccounts() {
        BatchOpenResult result = accountOpeningDomainService.scanAndOpenInternalAccounts();
        return accountOpeningAssembler.toBatchResponse(result);
    }

    /**
     * 查询账户信息
     */
    public AccountOpenResponse getAccountInfo(String accountNo) {
        AccountPO account = accountRepository.selectByAccountNo(accountNo);
        if (account == null) {
            throw new AccountException(ResultCode.ACCOUNT_NOT_FOUND, "账户不存在: " + accountNo);
        }
        return accountOpeningAssembler.toResponse(account);
    }
}
