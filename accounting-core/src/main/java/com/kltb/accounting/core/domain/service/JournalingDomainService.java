// accounting-core/src/main/java/com/kltb/accounting/core/domain/service/JournalingDomainService.java
package com.kltb.accounting.core.domain.service;

import com.kltb.accounting.api.request.JournalDetailRequest;
import com.kltb.accounting.core.domain.enums.BusinessRecordStatusEnum;
import com.kltb.accounting.core.domain.enums.CustomerTypeEnum;
import com.kltb.accounting.core.domain.enums.TradeTypeEnum;
import com.kltb.accounting.core.domain.enums.TransactionStatusEnum;
import com.kltb.accounting.core.infrastructure.account.TransactionNoGenerator;
import com.kltb.accounting.core.infrastructure.persistence.entity.BusinessDetailPO;
import com.kltb.accounting.core.infrastructure.persistence.entity.BusinessRecordPO;
import com.kltb.accounting.core.infrastructure.persistence.entity.TransactionPO;
import com.kltb.accounting.core.infrastructure.cache.AccountingDateCache;
import com.kltb.accounting.core.infrastructure.persistence.repository.BusinessDetailRepository;
import com.kltb.accounting.core.infrastructure.persistence.repository.BusinessRecordRepository;
import com.kltb.accounting.core.infrastructure.persistence.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 流水入库领域服务
 * <p>
 * 负责：幂等检查、会计日期确定、流水持久化、事务编号生成
 * 注意：此类不包含预开户检查逻辑（由独立的 AccountPreCheckDomainService 负责，S1 修复）
 * <p>
 * 是否记账：是（流水持久化是记账流程入口）
 * 异常处理：幂等检查返回 null 表示首次请求，非空表示已处理过
 */
@Service
@RequiredArgsConstructor
public class JournalingDomainService {

    private final BusinessRecordRepository businessRecordRepository;
    private final BusinessDetailRepository businessDetailRepository;
    private final TransactionRepository transactionRepository;
    private final TransactionNoGenerator transactionNoGenerator;
    private final AccountingDateCache accountingDateCache;
    private final TransactionTemplate transactionTemplate;

    /**
     * 幂等检查：按 traceNo + traceSeq 查询已存在的流水
     *
     * @param traceNo  系统跟踪号
     * @param traceSeq 序列号
     * @return 已存在的流水PO，不存在时返回null
     */
    public BusinessRecordPO checkIdempotent(String traceNo, Integer traceSeq) {
        return businessRecordRepository.selectByTraceNo(traceNo, traceSeq);
    }

    /**
     * 确定会计日期
     * <p>
     * 日切后：从全局缓存读取会计日期
     * 首次启动：缓存无值时从 DB 兜底 → 系统日期
     *
     * @param tradeTime 交易时间（保留参数，但不再作为会计日期来源）
     * @return 会计日期
     */
    public LocalDate determineAccountingDate(LocalDateTime tradeTime) {
        return accountingDateCache.getCurrentDate();
    }

    /**
     * 在事务中写入流水 + 明细 + 创建事务记录
     *
     * @param traceNo      系统跟踪号
     * @param traceSeq     序列号
     * @param businessCode 业务线编码
     * @param tradingCode  交易编码
     * @param payChannel   支付渠道
     * @param tradeType    交易类别（已在上层预校验，P1-6 修复）
     * @param amount       交易金额
     * @param tradeTime    交易时间
     * @param summary      摘要
     * @param details      流水明细列表
     * @param accountingDate 会计日期
     * @return 领域层结果对象
     */
    public JournalSubmitResult persistJournal(
            String traceNo, Integer traceSeq, String businessCode,
            String tradingCode, String payChannel, Integer tradeType,
            BigDecimal amount, LocalDateTime tradeTime, String summary,
            List<JournalDetailRequest> details, LocalDate accountingDate) {

        // P1-1 修复：使用 TransactionTemplate.execute() 返回值直接返回 txnNo，消除 String[] 闭包反模式
        return transactionTemplate.execute(status -> {
            // 1. 生成事务编号
            String txnNo = transactionNoGenerator.generate();

            // 2. 写入 t_business_record
            BusinessRecordPO record = new BusinessRecordPO();
            record.setTraceNo(traceNo).setTraceSeq(traceSeq);
            record.setBusinessCode(businessCode).setTradingCode(tradingCode);
            record.setPayChannel(payChannel);
            record.setTradeType(TradeTypeEnum.fromCode(tradeType));
            record.setAmount(amount).setTradeTime(tradeTime);
            record.setAccountingDate(accountingDate).setSummary(summary);
            record.setStatus(BusinessRecordStatusEnum.PROCESSING);
            businessRecordRepository.save(record);

            // 3. 写入 t_business_detail（逐条）
            for (JournalDetailRequest detail : details) {
                BusinessDetailPO detailPO = new BusinessDetailPO();
                detailPO.setTraceNo(traceNo).setTraceSeq(traceSeq);
                detailPO.setCustomerId(detail.getCustomerId());
                detailPO.setCustomerType(CustomerTypeEnum.fromValue(detail.getCustomerType()));
                detailPO.setFundsType(detail.getFundsType());
                detailPO.setItemCode(detail.getItemCode()); // N2 修复
                detailPO.setAmount(detail.getAmount());
                businessDetailRepository.save(detailPO);
            }

            // 4. 创建 t_transaction
            TransactionPO transaction = new TransactionPO();
            transaction.setTxnNo(txnNo).setTraceNo(traceNo);
            transaction.setAccountingDate(accountingDate);
            transaction.setAmount(amount).setCurrency("CNY");
            transaction.setStatus(TransactionStatusEnum.PROCESSING);
            transaction.setRelateAccountCount(0); // 预开户后更新
            transactionRepository.save(transaction);

            return new JournalSubmitResult(traceNo, accountingDate, txnNo);
        });
    }
}
