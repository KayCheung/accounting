// accounting-core/src/main/java/com/kltb/accounting/core/application/service/JournalingApplicationService.java
package com.kltb.accounting.core.application.service;

import com.kltb.accounting.api.constant.ResultCode;
import com.kltb.accounting.api.request.JournalDetailRequest;
import com.kltb.accounting.api.request.JournalSubmitRequest;
import com.kltb.accounting.api.response.JournalSubmitResponse;
import com.kltb.accounting.core.application.assembler.JournalingAssembler;
import com.kltb.accounting.core.domain.enums.BusinessRecordStatusEnum;
import com.kltb.accounting.core.domain.enums.CustomerTypeEnum;
import com.kltb.accounting.core.domain.enums.TradeTypeEnum;
import com.kltb.accounting.core.domain.service.AccountPreCheckDomainService;
import com.kltb.accounting.core.domain.service.JournalSubmitResult;
import com.kltb.accounting.core.domain.service.JournalingDomainService;
import com.kltb.accounting.core.infrastructure.persistence.entity.BusinessRecordPO;
import com.kltb.accounting.core.infrastructure.persistence.entity.TransactionPO;
import com.kltb.accounting.core.infrastructure.persistence.repository.BusinessRecordRepository;
import com.kltb.accounting.core.infrastructure.persistence.repository.TransactionRepository;
import com.kltb.accounting.core.infrastructure.redis.DistributedLockTemplate;
import com.kltb.accounting.core.shared.exception.AccountException;
import com.kltb.accounting.core.shared.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 记账流水入库应用服务
 * <p>
 * 负责：入口幂等锁控制、用例编排、参数校验（含明细金额合计校验）、FAILED 状态更新
 * <p>
 * 是否记账：是（通过 TransactionTemplate 在 JournalingDomainService 中管理事务）
 * 异常处理：
 * - 参数校验失败 → ParamException（由 @Valid 处理）
 * - 金额合计不等 → ServiceException(PARAM_ERROR)
 * - 幂等冲突 → ServiceException(IDEMPOTENT_CONFLICT)
 * - 预开户失败 → AccountException（拦截并更新流水 status=FAILED）
 * - 数据库唯一约束冲突 → DuplicateKeyException → 返回 IDEMPOTENT_CONFLICT（P0-2 修复）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JournalingApplicationService {

    private final JournalingDomainService journalingDomainService;
    private final AccountPreCheckDomainService accountPreCheckDomainService;
    private final BusinessRecordRepository businessRecordRepository;
    private final TransactionRepository transactionRepository;
    private final DistributedLockTemplate distributedLockTemplate;
    private final JournalingAssembler assembler;
    private final TransactionTemplate transactionTemplate;

    /**
     * 提交记账流水（含入口幂等锁控制）
     * <p>
     * 流程：
     *   1. 参数校验（由 @Valid 完成）
     *   2. 明细金额合计校验
     *   3. 预校验 tradeType / customerType（P1-3/P1-6 修复：失败直接返回 400，不进入事务）
     *   4. 获取幂等锁
     *   5. 幂等检查：已存在 → 返回已有结果（M5 修复）
     *   6. 流水持久化
     *   7. 预开户检查
     *   8. 返回结果
     *   9. 开户失败/异常 → 更新流水 FAILED（P0-3/P1-4 修复：独立事务中更新状态）
     */
    public JournalSubmitResponse submitJournal(JournalSubmitRequest request) {
        // 1. 校验明细金额合计 = 总金额
        BigDecimal detailTotal = request.getDetails().stream()
                .map(JournalDetailRequest::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (request.getAmount().compareTo(detailTotal) != 0) {
            throw new ServiceException(ResultCode.PARAM_ERROR,
                    "流水明细金额合计(" + detailTotal + ")不等于总金额(" + request.getAmount() + ")");
        }

        // 2. P1-6 修复：预校验 tradeType，无效值在进入事务前拦截，返回 400
        if (!TradeTypeEnum.isValid(request.getTradeType())) {
            throw new ServiceException(ResultCode.PARAM_ERROR,
                    "无效的tradeType: " + request.getTradeType());
        }

        // 3. P1-3 修复：预校验 customerType，无效值在进入锁前拦截，返回 400
        validateCustomerTypes(request.getDetails());

        // 4. 幂等锁 Key（含 tenantId，由 DistributedLockTemplate 内部自动拼接）
        String lockKey = "idempotent:trace:" + request.getTraceNo() + "-" + request.getTraceSeq();

        try {
            return distributedLockTemplate.execute(
                    lockKey,
                    0,     // wait 0s（立即失败，不等待）
                    -1,    // lease -1（启用 watchdog 自动续期，防止开户流程超时释放锁，P1-2 修复）
                    () -> doSubmit(request)
            );
        } catch (DuplicateKeyException e) {
            // P0-2 修复：数据库唯一约束兜底，并发场景下 insert 触发唯一索引冲突
            // 此时事务已回滚，查询已有结果返回
            log.warn("[Journal] 唯一约束冲突，返回幂等结果: traceNo={}", request.getTraceNo());
            BusinessRecordPO existing = journalingDomainService.checkIdempotent(
                    request.getTraceNo(), request.getTraceSeq());
            TransactionPO txn = transactionRepository.selectByTraceNo(request.getTraceNo());
            return assembler.toIdempotentResponse(existing, txn);
        } catch (AccountException e) {
            // P0-3 修复：在独立事务中更新 FAILED 状态（而非在原分布式锁事务外直接调用）
            // P1-5 修复：使用返回值判断更新是否成功
            markJournalFailed(request.getTraceNo(), e.getResultCode().getMessage());
            throw e;
        } catch (RuntimeException e) {
            // P1-4 修复：非 AccountException 异常（如基础设施故障）也标记 FAILED
            log.error("[Journal] 流水入库异常，标记 FAILED: traceNo={} error={}",
                    request.getTraceNo(), e.getMessage(), e);
            markJournalFailed(request.getTraceNo(), "系统异常: " + e.getMessage());
            throw e;
        }
    }

    /**
     * 锁内执行
     */
    private JournalSubmitResponse doSubmit(JournalSubmitRequest request) {
        // 幂等检查
        BusinessRecordPO existing = journalingDomainService.checkIdempotent(
                request.getTraceNo(), request.getTraceSeq());
        if (existing != null) {
            // M5 修复：查询已有事务并返回
            TransactionPO txn = transactionRepository.selectByTraceNo(request.getTraceNo());
            return assembler.toIdempotentResponse(existing, txn);
        }

        // 确定会计日期
        var accountingDate = journalingDomainService.determineAccountingDate(request.getTradeTime());

        // 流水持久化（record + detail + transaction）
        JournalSubmitResult result = journalingDomainService.persistJournal(
                request.getTraceNo(), request.getTraceSeq(),
                request.getBusinessCode(), request.getTradingCode(), request.getPayChannel(),
                request.getTradeType(), request.getAmount(), request.getTradeTime(),
                request.getSummary(), request.getDetails(), accountingDate);

        // 预开户检查 + 自动开户（P1-7 修复：传入 traceNo 生成唯一 requestNo）
        Map<String, CustomerTypeEnum> customerMap = buildCustomerMap(request.getDetails());
        accountPreCheckDomainService.checkAndOpenAccounts(
                request.getBusinessCode(), request.getTradingCode(), request.getPayChannel(),
                customerMap, request.getTraceNo());

        return assembler.toResponse(result);
    }

    /**
     * 在独立事务中标记流水为 FAILED（P0-3 修复）
     * <p>
     * 分布式锁内的事务已回滚，此处使用独立 TransactionTemplate 确保 FAILED 状态更新成功提交。
     *
     * @param traceNo 系统跟踪号
     * @param reason  失败原因
     */
    private void markJournalFailed(String traceNo, String reason) {
        try {
            Integer affectedRows = transactionTemplate.execute(status ->
                    businessRecordRepository.updateStatusByTraceNo(traceNo, BusinessRecordStatusEnum.FAILED));
            if (affectedRows == null || affectedRows == 0) {
                log.warn("[Journal] 标记 FAILED 失败，未找到匹配记录: traceNo={}, reason={}", traceNo, reason);
            }
        } catch (Exception ex) {
            // 极端情况：独立事务也失败，记录日志但不阻断原异常抛出
            log.error("[Journal] 标记 FAILED 异常: traceNo={}, reason={}", traceNo, reason, ex);
        }
    }

    /**
     * 构建 customerId → CustomerTypeEnum 映射（M3 修复：构建时绑定）
     */
    private Map<String, CustomerTypeEnum> buildCustomerMap(List<JournalDetailRequest> details) {
        Map<String, CustomerTypeEnum> map = new HashMap<>();
        for (JournalDetailRequest detail : details) {
            map.putIfAbsent(detail.getCustomerId(),
                    CustomerTypeEnum.fromValue(detail.getCustomerType()));
        }
        return map;
    }

    /**
     * P1-3 修复：预校验所有 customerType 值，在进入分布式锁前拦截非法值，返回 400 而非 500
     */
    private void validateCustomerTypes(List<JournalDetailRequest> details) {
        for (JournalDetailRequest detail : details) {
            if (!CustomerTypeEnum.isValid(detail.getCustomerType())) {
                throw new ServiceException(ResultCode.PARAM_ERROR,
                        "无效的customerType: " + detail.getCustomerType());
            }
        }
    }
}
