package com.kltb.accounting.core.application.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kltb.accounting.api.constant.ResultCode;
import com.kltb.accounting.api.request.*;
import com.kltb.accounting.api.response.*;
import com.kltb.accounting.core.domain.enums.*;
import com.kltb.accounting.core.domain.service.PeriodEndTransferDomainService;
import com.kltb.accounting.core.domain.service.PeriodEndTransferDomainService.TransferRuleResult;
import com.kltb.accounting.core.infrastructure.persistence.entity.*;
import com.kltb.accounting.core.infrastructure.persistence.repository.*;
import com.kltb.accounting.core.shared.exception.AccountException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 期末结转应用服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PeriodEndTransferApplicationService {

    private final PeriodEndTransferRuleRepository ruleRepository;
    private final PeriodEndTransferRecordRepository recordRepository;
    private final PeriodEndTransferDomainService transferDomainService;
    private final SubjectRepository subjectRepository;

    /**
     * 分页查询结转规则
     */
    public PageResponse<TransferRuleResponse> pageRules(TransferRuleQueryRequest request) {
        Page<PeriodEndTransferRulePO> poPage = ruleRepository.selectPage(
                request.getPageNo(),
                request.getPageSize(),
                request.getRuleCode(),
                request.getRuleName(),
                request.getTransferType(),
                request.getStatus(),
                request.getAutoTransfer(),
                request.getPeriodCycle());


        List<TransferRuleResponse> list = poPage.getRecords().stream()
                .map(this::toRuleResponse)
                .toList();

        return PageResponse.<TransferRuleResponse>builder()
                .total(poPage.getTotal())
                .pages(poPage.getPages())
                .current(poPage.getCurrent())
                .list(list)
                .build();
    }

    /**
     * 查询规则详情
     */
    public TransferRuleResponse getRuleById(Long id) {
        PeriodEndTransferRulePO po = ruleRepository.findById(id);
        if (po == null) {
            throw new AccountException(ResultCode.TRANSFER_RULE_NOT_FOUND, "期末结转规则不存在: id=" + id);
        }
        return toRuleResponse(po);
    }

    /**
     * 创建结转规则
     */
    public Long createRule(TransferRuleCreateRequest request) {
        // 1. 唯一性校验
        if (ruleRepository.existsByRuleCode(request.getRuleCode(), null)) {
            throw new AccountException(ResultCode.TRANSFER_RULE_CODE_EXISTS,
                    "期末结转规则编码已存在: " + request.getRuleCode());
        }

        // 2. 目标科目末级有效性校验
        validateTargetSubject(request.getTargetSubjectCode());

        // 3. 构建并持久化 PO
        PeriodEndTransferRulePO po = new PeriodEndTransferRulePO();
        po.setRuleCode(request.getRuleCode().trim());
        po.setRuleName(request.getRuleName().trim());
        po.setTransferType(TransferTypeEnum.fromCode(request.getTransferType()));
        po.setSourceSubjectCode(request.getSourceSubjectCode().trim());
        po.setTargetSubjectCode(request.getTargetSubjectCode().trim());
        po.setTransferDirection(TransferDirectionEnum.fromCode(request.getTransferDirection()));
        po.setSummaryTemplate(request.getSummaryTemplate() != null ? request.getSummaryTemplate().trim() : "");
        po.setExecuteOrder(request.getExecuteOrder() != null ? request.getExecuteOrder() : 10);
        po.setStatus(AvailableStatusEnum.ENABLED);
        po.setAutoTransfer(request.getAutoTransfer() != null ? request.getAutoTransfer() : Boolean.TRUE);
        po.setPeriodCycle(request.getPeriodCycle() != null
                ? com.kltb.accounting.core.domain.enums.PeriodCycleEnum.fromCode(request.getPeriodCycle())
                : com.kltb.accounting.core.domain.enums.PeriodCycleEnum.MONTHLY);

        ruleRepository.insert(po);
        log.info("[TRANSFER-APP] 创建结转规则成功: id={}, code={}", po.getId(), po.getRuleCode());
        return po.getId();
    }

    /**
     * 修改结转规则
     */
    public void updateRule(Long id, TransferRuleUpdateRequest request) {
        PeriodEndTransferRulePO po = ruleRepository.findById(id);
        if (po == null) {
            throw new AccountException(ResultCode.TRANSFER_RULE_NOT_FOUND, "期末结转规则不存在: id=" + id);
        }

        // 目标科目末级有效性校验
        validateTargetSubject(request.getTargetSubjectCode());

        po.setRuleName(request.getRuleName().trim());
        po.setTransferType(TransferTypeEnum.fromCode(request.getTransferType()));
        po.setSourceSubjectCode(request.getSourceSubjectCode().trim());
        po.setTargetSubjectCode(request.getTargetSubjectCode().trim());
        po.setTransferDirection(TransferDirectionEnum.fromCode(request.getTransferDirection()));
        if (request.getSummaryTemplate() != null) {
            po.setSummaryTemplate(request.getSummaryTemplate().trim());
        }
        if (request.getExecuteOrder() != null) {
            po.setExecuteOrder(request.getExecuteOrder());
        }
        if (request.getAutoTransfer() != null) {
            po.setAutoTransfer(request.getAutoTransfer());
        }
        if (request.getPeriodCycle() != null) {
            po.setPeriodCycle(com.kltb.accounting.core.domain.enums.PeriodCycleEnum.fromCode(request.getPeriodCycle()));
        }

        ruleRepository.updateById(po);
        log.info("[TRANSFER-APP] 修改结转规则成功: id={}, code={}", id, po.getRuleCode());
    }

    /**
     * 切换规则启用/停用状态
     */
    public void updateRuleStatus(Long id, Integer status) {
        PeriodEndTransferRulePO po = ruleRepository.findById(id);
        if (po == null) {
            throw new AccountException(ResultCode.TRANSFER_RULE_NOT_FOUND, "期末结转规则不存在: id=" + id);
        }
        AvailableStatusEnum statusEnum = AvailableStatusEnum.fromCode(status);
        if (statusEnum == null) {
            throw new AccountException(ResultCode.PARAM_ERROR, "非法的状态值: " + status);
        }
        ruleRepository.updateStatus(id, statusEnum);
        log.info("[TRANSFER-APP] 切换规则状态成功: id={}, status={}", id, statusEnum.getDesc());
    }

    /**
     * 逻辑删除规则
     */
    public void deleteRule(Long id) {
        PeriodEndTransferRulePO po = ruleRepository.findById(id);
        if (po == null) {
            throw new AccountException(ResultCode.TRANSFER_RULE_NOT_FOUND, "期末结转规则不存在: id=" + id);
        }
        ruleRepository.deleteById(id);
        log.info("[TRANSFER-APP] 删除结转规则成功: id={}, code={}", id, po.getRuleCode());
    }

    /**
     * 结转试算与分录预览
     */
    public TransferPreviewResponse previewTransfer(TransferExecuteRequest request) {
        return transferDomainService.previewTransfer(
                request.getAccountingDate(),
                request.getRuleCode(),
                request.getTransferType());
    }

    /**
     * 执行期末结转
     */
    public TransferExecuteBatchResponse executeTransfer(TransferExecuteRequest request) {
        long startTime = System.currentTimeMillis();

        List<TransferRuleResult> results = transferDomainService.executeTransfer(
                request.getAccountingDate(),
                request.getRuleCode(),
                request.getTransferType(),
                request.isForceRetry());

        int successCount = 0;
        int failedCount = 0;
        BigDecimal grandTotal = BigDecimal.ZERO;
        List<TransferExecuteItemResponse> items = new ArrayList<>();

        for (TransferRuleResult r : results) {
            TransferExecuteItemResponse item = new TransferExecuteItemResponse();
            item.setRuleCode(r.getRuleCode());
            item.setRuleName(r.getRuleName());
            item.setTransferNo(r.getTransferNo());
            item.setVoucherNo(r.getVoucherNo());
            item.setTotalAmount(r.getTotalAmount());
            item.setStatus(r.getStatus() != null ? r.getStatus().getCode() : null);
            item.setStatusDesc(r.getStatus() != null ? r.getStatus().getDesc() : "");
            item.setFailReason(r.getFailReason());

            if (r.getStatus() == TransferRecordStatusEnum.SUCCESS) {
                successCount++;
                grandTotal = grandTotal.add(r.getTotalAmount());
            } else {
                failedCount++;
            }
            items.add(item);
        }

        TransferExecuteBatchResponse response = new TransferExecuteBatchResponse();
        response.setAccountingDate(request.getAccountingDate());
        response.setTotalRules(results.size());
        response.setSuccessCount(successCount);
        response.setFailedCount(failedCount);
        response.setTotalAmount(grandTotal);
        response.setTotalDurationMs(System.currentTimeMillis() - startTime);
        response.setItems(items);

        return response;
    }

    /**
     * 分页查询结转历史记录
     */
    public PageResponse<TransferRecordResponse> pageRecords(TransferRecordQueryRequest request) {
        Page<PeriodEndTransferRecordPO> poPage = recordRepository.selectPage(
                request.getPageNo(),
                request.getPageSize(),
                request.getStartDate(),
                request.getEndDate(),
                request.getTransferNo(),
                request.getRuleCode(),
                request.getVoucherNo(),
                request.getStatus());

        List<TransferRecordResponse> list = poPage.getRecords().stream()
                .map(this::toRecordResponse)
                .toList();

        return PageResponse.<TransferRecordResponse>builder()
                .total(poPage.getTotal())
                .pages(poPage.getPages())
                .current(poPage.getCurrent())
                .list(list)
                .build();
    }

    /**
     * 查询单条结转记录详情
     */
    public TransferRecordResponse getRecordByTransferNo(String transferNo) {
        PeriodEndTransferRecordPO po = recordRepository.findByTransferNo(transferNo);
        if (po == null) {
            throw new AccountException(ResultCode.TRANSFER_RECORD_NOT_FOUND, "期末结转记录不存在: transferNo=" + transferNo);
        }
        return toRecordResponse(po);
    }

    private void validateTargetSubject(String targetSubjectCode) {
        AccountSubjectPO subject = subjectRepository.selectByCode(targetSubjectCode);
        if (subject == null) {
            throw new AccountException(ResultCode.SUBJECT_NOT_FOUND, "目标科目不存在: " + targetSubjectCode);
        }
        if (subject.getLeaf() == null || !subject.getLeaf()) {
            throw new AccountException(ResultCode.SUBJECT_NOT_LEAF, "目标科目必须为末级科目: " + targetSubjectCode);
        }
    }

    private TransferRuleResponse toRuleResponse(PeriodEndTransferRulePO po) {
        TransferRuleResponse resp = new TransferRuleResponse();
        resp.setId(po.getId());
        resp.setRuleCode(po.getRuleCode());
        resp.setRuleName(po.getRuleName());
        resp.setTransferType(po.getTransferType() != null ? po.getTransferType().getCode() : null);
        resp.setTransferTypeDesc(po.getTransferType() != null ? po.getTransferType().getDesc() : "");
        resp.setSourceSubjectCode(po.getSourceSubjectCode());
        resp.setTargetSubjectCode(po.getTargetSubjectCode());

        AccountSubjectPO targetSubject = subjectRepository.selectByCode(po.getTargetSubjectCode());
        resp.setTargetSubjectName(targetSubject != null ? targetSubject.getSubjectName() : "");

        resp.setTransferDirection(po.getTransferDirection() != null ? po.getTransferDirection().getCode() : null);
        resp.setTransferDirectionDesc(po.getTransferDirection() != null ? po.getTransferDirection().getDesc() : "");
        resp.setSummaryTemplate(po.getSummaryTemplate());
        resp.setExecuteOrder(po.getExecuteOrder());
        resp.setStatus(po.getStatus() != null ? po.getStatus().getCode() : null);
        resp.setAutoTransfer(po.getAutoTransfer());
        resp.setPeriodCycle(po.getPeriodCycle() != null ? po.getPeriodCycle().getCode() : null);
        resp.setPeriodCycleDesc(po.getPeriodCycle() != null ? po.getPeriodCycle().getDesc() : "");
        resp.setCreateTime(po.getCreateTime());
        resp.setUpdateTime(po.getUpdateTime());
        return resp;
    }


    private TransferRecordResponse toRecordResponse(PeriodEndTransferRecordPO po) {
        TransferRecordResponse resp = new TransferRecordResponse();
        resp.setId(po.getId());
        resp.setTransferNo(po.getTransferNo());
        resp.setAccountingDate(po.getAccountingDate());
        resp.setTransferType(po.getTransferType() != null ? po.getTransferType().getCode() : null);
        resp.setTransferTypeDesc(po.getTransferType() != null ? po.getTransferType().getDesc() : "");
        resp.setRuleCode(po.getRuleCode());

        PeriodEndTransferRulePO rule = ruleRepository.findByRuleCode(po.getRuleCode());
        resp.setRuleName(rule != null ? rule.getRuleName() : po.getRuleCode());

        resp.setVoucherNo(po.getVoucherNo());
        resp.setTotalAmount(po.getTotalAmount());
        resp.setStatus(po.getStatus() != null ? po.getStatus().getCode() : null);
        resp.setStatusDesc(po.getStatus() != null ? po.getStatus().getDesc() : "");
        resp.setFailReason(po.getFailReason());
        resp.setExecuteTime(po.getExecuteTime());
        resp.setFinishTime(po.getFinishTime());
        return resp;
    }
}
