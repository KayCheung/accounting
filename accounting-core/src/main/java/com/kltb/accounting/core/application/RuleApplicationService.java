package com.kltb.accounting.core.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kltb.accounting.api.constant.ResultCode;
import com.kltb.accounting.api.request.RuleAuxiliaryRequest;
import com.kltb.accounting.api.request.RuleCreateRequest;
import com.kltb.accounting.api.request.RuleEntryRequest;
import com.kltb.accounting.api.request.RuleQueryRequest;
import com.kltb.accounting.api.request.RuleUpdateRequest;
import com.kltb.accounting.api.response.PageResponse;
import com.kltb.accounting.core.application.converter.RuleConverter;
import com.kltb.accounting.core.application.dto.RuleAuxiliaryResponse;
import com.kltb.accounting.core.application.dto.RuleEntryResponse;
import com.kltb.accounting.core.application.dto.RuleResponse;
import com.kltb.accounting.core.domain.enums.AllocationMethodEnum;
import com.kltb.accounting.core.domain.enums.RuleStatusEnum;
import com.kltb.accounting.core.infrastructure.persistence.entity.AccountSubjectPO;
import com.kltb.accounting.core.infrastructure.persistence.entity.AccountingRuleAuxiliaryPO;
import com.kltb.accounting.core.infrastructure.persistence.entity.AccountingRuleDetailPO;
import com.kltb.accounting.core.infrastructure.persistence.entity.AccountingRulePO;
import com.kltb.accounting.core.infrastructure.persistence.repository.AccountingRuleRepository;
import com.kltb.accounting.core.infrastructure.persistence.repository.AccountingVoucherRepository;
import com.kltb.accounting.core.infrastructure.persistence.repository.SubjectRepository;
import com.kltb.accounting.core.shared.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.SpelParseException;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import cn.hutool.core.util.StrUtil;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 记账规则应用服务
 *
 * 是否记账：否（配置管理类用例编排）
 * 异常处理：
 *   - ServiceException → 全局拦截器捕获，返回对应 ResultCode
 *   - 数据库/框架异常 → 包装为 SYSTEM_ERROR 后抛出
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RuleApplicationService {

    private static final ExpressionParser SPEL_PARSER = new SpelExpressionParser();

    private final AccountingRuleRepository accountingRuleRepository;
    private final AccountingVoucherRepository accountingVoucherRepository;
    private final SubjectRepository subjectRepository;
    private final TransactionTemplate transactionTemplate;

    /**
     * 创建记账规则
     *
     * 是否记账：否
     */
    public void create(RuleCreateRequest request) {
        transactionTemplate.execute(status -> {
            try {
                // 1. 唯一键校验
                AccountingRulePO existing = accountingRuleRepository.selectByBusinessKey(
                        request.getBusinessCode(), request.getTradingCode(), request.getPayChannel());
                if (existing != null) {
                    throw new ServiceException(ResultCode.PARAM_ERROR,
                            "业务线+交易编码+支付渠道组合已存在记账规则");
                }

                // 2. 明细校验
                validateEntries(request.getEntries());

                // 3. 插入规则主表
                AccountingRulePO po = RuleConverter.toPO(request);
                accountingRuleRepository.insertRule(po);

                // 4. 批量插入明细和辅助核算项
                insertDetailsAndAuxiliaries(po.getId(), request.getEntries());
                return null;
            } catch (ServiceException e) {
                throw e;
            } catch (Exception e) {
                log.error("[RULE-CREATE] 创建规则失败 ruleName={} error={}",
                        request.getRuleName(), e.getMessage(), e);
                throw new ServiceException(ResultCode.SYSTEM_ERROR, "创建规则失败");
            }
        });
    }

    /**
     * 更新记账规则
     *
     * 是否记账：否
     */
    public void update(Long ruleId, RuleUpdateRequest request) {
        transactionTemplate.execute(status -> {
            try {
                AccountingRulePO po = accountingRuleRepository.selectRuleById(ruleId);
                if (po == null) {
                    throw new ServiceException(ResultCode.RULE_NOT_FOUND, "记账规则不存在");
                }

                // 已启用规则禁止修改
                if (po.getStatus() == RuleStatusEnum.ENABLED) {
                    throw new ServiceException(ResultCode.OPERATION_NOT_ALLOWED,
                            "已启用的规则不允许修改，请先停用再修改");
                }

                // 若包含明细，执行相同校验
                if (request.getEntries() != null && !request.getEntries().isEmpty()) {
                    validateEntries(request.getEntries());
                }

                // 更新基本信息
                RuleConverter.updatePO(request, po);
                accountingRuleRepository.updateRuleById(po);

                // 全量替换明细
                if (request.getEntries() != null && !request.getEntries().isEmpty()) {
                    accountingRuleRepository.deleteRuleDetailByRuleId(ruleId);
                    insertDetailsAndAuxiliaries(ruleId, request.getEntries());
                }
                return null;
            } catch (ServiceException e) {
                throw e;
            } catch (Exception e) {
                log.error("[RULE-UPDATE] 更新规则失败 ruleId={} error={}",
                        ruleId, e.getMessage(), e);
                throw new ServiceException(ResultCode.SYSTEM_ERROR, "更新规则失败");
            }
        });
    }

    /**
     * 启用记账规则
     *
     * 是否记账：否
     */
    public void enable(Long ruleId) {
        transactionTemplate.execute(status -> {
            try {
                AccountingRulePO po = accountingRuleRepository.selectRuleById(ruleId);
                if (po == null) {
                    throw new ServiceException(ResultCode.RULE_NOT_FOUND, "记账规则不存在");
                }
                if (po.getStatus() == RuleStatusEnum.ENABLED) {
                    return null;
                }
                po.setStatus(RuleStatusEnum.ENABLED);
                po.setUpdateId("system");
                po.setUpdateName("system");
                accountingRuleRepository.updateRuleById(po);
                return null;
            } catch (ServiceException e) {
                throw e;
            } catch (Exception e) {
                log.error("[RULE-ENABLE] 启用规则失败 ruleId={} error={}",
                        ruleId, e.getMessage(), e);
                throw new ServiceException(ResultCode.SYSTEM_ERROR, "启用规则失败");
            }
        });
    }

    /**
     * 停用记账规则
     *
     * 是否记账：否
     */
    public void disable(Long ruleId) {
        transactionTemplate.execute(status -> {
            try {
                AccountingRulePO po = accountingRuleRepository.selectRuleById(ruleId);
                if (po == null) {
                    throw new ServiceException(ResultCode.RULE_NOT_FOUND, "记账规则不存在");
                }

                // 停用联动校验：检查无关联凭证引用（按业务键匹配，
                // 凭证表增加 rule_id 字段后可改为按 rule_id 精准校验）
                long voucherCount = accountingVoucherRepository.countByBusinessKey(
                        po.getBusinessCode(), po.getTradingCode(), po.getPayChannel());
                if (voucherCount > 0) {
                    throw new ServiceException(ResultCode.OPERATION_NOT_ALLOWED,
                            "该规则存在关联凭证（" + voucherCount + "条），无法停用");
                }

                po.setStatus(RuleStatusEnum.DISABLED);
                po.setUpdateId("system");
                po.setUpdateName("system");
                accountingRuleRepository.updateRuleById(po);
                return null;
            } catch (ServiceException e) {
                throw e;
            } catch (Exception e) {
                log.error("[RULE-DISABLE] 停用规则失败 ruleId={} error={}",
                        ruleId, e.getMessage(), e);
                throw new ServiceException(ResultCode.SYSTEM_ERROR, "停用规则失败");
            }
        });
    }

    /**
     * 查询单个规则（含明细与辅助核算项）
     *
     * 是否记账：否
     */
    public RuleResponse getById(Long ruleId) {
        AccountingRulePO po = accountingRuleRepository.selectRuleById(ruleId);
        if (po == null) {
            throw new ServiceException(ResultCode.RULE_NOT_FOUND, "记账规则不存在");
        }

        // 查询明细与辅助核算项（一次批量查询，避免N+1）
        List<AccountingRuleDetailPO> details = accountingRuleRepository.selectDetailsWithAuxiliary(ruleId);
        Map<Long, List<AccountingRuleAuxiliaryPO>> auxMap = accountingRuleRepository.selectAuxiliariesByRuleId(ruleId);
        List<RuleEntryResponse> entryResponses = new ArrayList<>();
        for (AccountingRuleDetailPO detail : details) {
            List<AccountingRuleAuxiliaryPO> auxiliaries = auxMap.getOrDefault(detail.getId(), List.of());
            List<RuleAuxiliaryResponse> auxResponses = new ArrayList<>(auxiliaries.size());
            for (AccountingRuleAuxiliaryPO aux : auxiliaries) {
                auxResponses.add(RuleConverter.toAuxiliaryResponse(aux));
            }
            entryResponses.add(RuleConverter.toEntryResponse(detail, auxResponses));
        }

        return RuleConverter.toResponse(po, entryResponses);
    }

    /**
     * 分页查询规则
     *
     * 是否记账：否
     */
    public PageResponse<RuleResponse> page(RuleQueryRequest request) {
        RuleStatusEnum status = RuleStatusEnum.fromCode(request.getStatus());

        LambdaQueryWrapper<AccountingRulePO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StrUtil.isNotBlank(request.getBusinessCode()), AccountingRulePO::getBusinessCode, request.getBusinessCode())
                .eq(StrUtil.isNotBlank(request.getTradingCode()), AccountingRulePO::getTradingCode, request.getTradingCode())
                .eq(Objects.nonNull(status), AccountingRulePO::getStatus, status)
                .orderByDesc(AccountingRulePO::getId);

        Page<AccountingRulePO> page = new Page<>(request.getPageNo(), request.getPageSize());
        Page<AccountingRulePO> result = accountingRuleRepository.pageRule(page, wrapper);

        return PageResponse.<RuleResponse>builder()
                .total(result.getTotal())
                .pages(result.getPages())
                .current(result.getCurrent())
                .list(RuleConverter.toResponseList(result.getRecords()))
                .build();
    }

    /**
     * 校验规则明细列表
     */
    private void validateEntries(List<RuleEntryRequest> entries) {
        // 1. 借贷双方校验
        boolean hasDebit = entries.stream().anyMatch(e -> e.getDebitCredit() != null && e.getDebitCredit() == 1);
        boolean hasCredit = entries.stream().anyMatch(e -> e.getDebitCredit() != null && e.getDebitCredit() == 2);
        if (!hasDebit || !hasCredit) {
            throw new ServiceException(ResultCode.PARAM_ERROR, "记账规则必须包含借贷双方分录行");
        }

        // 2. 逐条校验
        for (RuleEntryRequest entry : entries) {
            // 科目存在性校验
            AccountSubjectPO subject = subjectRepository.selectByCode(entry.getSubjectCode());
            if (subject == null) {
                throw new ServiceException(ResultCode.SUBJECT_NOT_FOUND,
                        "规则明细中的科目不存在：" + entry.getSubjectCode());
            }

            // SpEL 脚本校验
            if (entry.getExtendScript() != null && !entry.getExtendScript().isBlank()) {
                validateSpelScript(entry.getExtendScript());
            }

            // 辅助核算项校验
            if (entry.getAuxiliaries() != null && !entry.getAuxiliaries().isEmpty()) {
                validateAuxiliaries(entry.getAuxiliaries());
            }
        }
    }

    /**
     * SpEL 脚本预加载校验
     */
    private void validateSpelScript(String script) {
        if (script == null || script.isBlank()) {
            return;
        }
        try {
            SPEL_PARSER.parseExpression(script);
        } catch (SpelParseException e) {
            throw new ServiceException(ResultCode.PARAM_ERROR, "SpEL脚本语法错误：" + e.getMessage());
        }
    }

    /**
     * 辅助核算项校验：按比例分摊时总和须等于 1
     */
    private void validateAuxiliaries(List<RuleAuxiliaryRequest> auxiliaries) {
        boolean hasProportional = auxiliaries.stream()
                .anyMatch(a -> a.getAllocationMethod() != null && a.getAllocationMethod() == 3);
        if (hasProportional) {
            BigDecimal totalRatio = auxiliaries.stream()
                    .filter(a -> a.getAllocationMethod() != null && a.getAllocationMethod() == 3)
                    .map(a -> a.getAllocationValue() != null ? a.getAllocationValue() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            if (totalRatio.compareTo(new BigDecimal("1.000000")) != 0) {
                throw new ServiceException(ResultCode.PARAM_ERROR,
                        "按比例分摊的辅助核算项，分摊值之和必须等于1");
            }
        }
    }

    /**
     * 批量插入明细和辅助核算项
     */
    private void insertDetailsAndAuxiliaries(Long ruleId, List<RuleEntryRequest> entries) {
        for (RuleEntryRequest entry : entries) {
            AccountingRuleDetailPO detailPO = RuleConverter.toDetailPO(entry, ruleId);
            accountingRuleRepository.insertRuleDetail(detailPO);

            if (entry.getAuxiliaries() != null && !entry.getAuxiliaries().isEmpty()) {
                for (RuleAuxiliaryRequest aux : entry.getAuxiliaries()) {
                    AccountingRuleAuxiliaryPO auxPO = RuleConverter.toAuxiliaryPO(aux, ruleId, detailPO.getId());
                    accountingRuleRepository.insertRuleAuxiliary(auxPO);
                }
            }
        }
    }
}
