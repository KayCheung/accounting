package com.kltb.accounting.core.application.service;

import com.kltb.accounting.api.request.ReversalRequest;
import com.kltb.accounting.api.response.ReversalRecordResponse;
import com.kltb.accounting.api.response.ReversalResponse;
import com.kltb.accounting.core.application.assembler.ReversalAssembler;
import com.kltb.accounting.core.domain.service.ReversalDomainService;
import com.kltb.accounting.core.domain.service.ReversalDomainService.ReversalResult;
import com.kltb.accounting.core.infrastructure.persistence.entity.AccountingVoucherPO;
import cn.hutool.core.util.StrUtil;
import com.kltb.accounting.core.domain.enums.BusinessRecordStatusEnum;
import com.kltb.accounting.core.infrastructure.persistence.repository.AccountingVoucherRepository;
import com.kltb.accounting.core.infrastructure.persistence.repository.BusinessRecordRepository;
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
    private final AccountingVoucherRepository voucherRepository;
    private final BusinessRecordRepository businessRecordRepository;

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

        // 若原凭证关联了业务流水，同步更新业务流水状态为已红冲
        try {
            AccountingVoucherPO origVoucher = voucherRepository.selectByVoucherNoSimple(request.getOrigVoucherNo());
            if (origVoucher != null && StrUtil.isNotBlank(origVoucher.getTraceNo())) {
                businessRecordRepository.updateStatusByTraceNo(origVoucher.getTraceNo(), BusinessRecordStatusEnum.REVERSED);
            }
        } catch (Exception e) {
            log.warn("[REVERSAL] 联动更新业务流水状态异常(非阻断): origVoucherNo={} error={}", request.getOrigVoucherNo(), e.getMessage());
        }

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
