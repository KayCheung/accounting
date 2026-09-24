package com.kltb.accounting.core.application.service;

import com.kltb.accounting.api.request.ReversalRequest;
import com.kltb.accounting.api.response.ReversalRecordResponse;
import com.kltb.accounting.api.response.ReversalResponse;
import com.kltb.accounting.core.application.assembler.ReversalAssembler;
import com.kltb.accounting.core.domain.service.ReversalDomainService;
import com.kltb.accounting.core.domain.service.ReversalDomainService.ReversalResult;
import com.kltb.accounting.core.infrastructure.persistence.entity.AccountingVoucherPO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 红冲编排层
 * <p>
 * 职责：
 * 1. 参数校验
 * 2. 调用 ReversalDomainService.executeReversal()
 * 3. 转换响应对象
 * 4. 记录审计日志
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReversalApplicationService {

    private final ReversalDomainService reversalDomainService;
    private final ReversalAssembler reversalAssembler;

    /**
     * 执行凭证红冲
     */
    public ReversalResponse executeReversal(ReversalRequest request) {
        log.info("[REVERSAL] 开始执行红冲 origVoucherNo={} bookkeeperName={}",
                request.getOrigVoucherNo(), request.getBookkeeperName());

        ReversalResult result = reversalDomainService.executeReversal(
                request.getOrigVoucherNo(),
                request.getBookkeeperName(),
                request.getSummary());

        // 通过 Assembler 转换响应（tradeType 已从 ReversalResult 获取，无需额外查询）
        ReversalResponse response = reversalAssembler.toResponse(result);

        log.info("[REVERSAL] 红冲执行成功 origVoucherNo={} reversalVoucherNo={} entryCount={}",
                request.getOrigVoucherNo(), result.getReversalVoucherNo(), result.getEntryCount());

        return response;
    }

    /**
     * 查询某凭证的红冲记录
     */
    public List<ReversalRecordResponse> queryReversalRecords(String origVoucherNo) {
        List<AccountingVoucherPO> reversalRecords = reversalDomainService.queryReversalRecords(origVoucherNo);
        return reversalAssembler.toRecordResponses(reversalRecords);
    }

    /**
     * 判断凭证是否可红冲
     */
    public boolean isReversable(String voucherNo) {
        return reversalDomainService.isReversable(voucherNo);
    }
}
