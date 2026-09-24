package com.kltb.accounting.core.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kltb.accounting.api.constant.ResultCode;
import com.kltb.accounting.api.request.BufferRuleCreateRequest;
import com.kltb.accounting.api.request.BufferRuleQueryRequest;
import com.kltb.accounting.api.request.BufferRuleUpdateRequest;
import com.kltb.accounting.api.response.PageResponse;
import com.kltb.accounting.core.application.converter.BufferRuleConverter;
import com.kltb.accounting.core.application.dto.BufferRuleResponse;
import com.kltb.accounting.core.domain.enums.BufferModeEnum;
import com.kltb.accounting.core.domain.enums.DebitCreditEnum;
import com.kltb.accounting.core.domain.enums.RuleStatusEnum;
import com.kltb.accounting.core.infrastructure.persistence.entity.BufferPostingRulePO;
import com.kltb.accounting.core.infrastructure.persistence.repository.BufferPostingRuleRepository;
import com.kltb.accounting.core.shared.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import cn.hutool.core.util.StrUtil;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 缓冲入账规则应用服务
 *
 * 是否记账：否（配置管理类用例编排）
 * 异常处理：
 *   - ServiceException → 全局拦截器捕获，返回对应 ResultCode
 *   - 数据库/框架异常 → 包装为 SYSTEM_ERROR 后抛出
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BufferRuleApplicationService {

    private final BufferPostingRuleRepository bufferPostingRuleRepository;
    private final TransactionTemplate transactionTemplate;

    /**
     * 创建缓冲入账规则
     *
     * 是否记账：否
     */
    public void create(BufferRuleCreateRequest request) {
        // 1. 科目/账户非空校验
        validateSubjectOrAccount(request.getSubjectCode(), request.getAccountNo());

        // 2. 时间合法性校验
        validateTimeRange(request.getEffectiveTime(), request.getExpirationTime());

        transactionTemplate.execute(status -> {
            try {
                // 3. 时间区间重叠校验
                validateTimeOverlap(request.getBusinessCode(), request.getTradingCode(),
                        request.getPayChannel(), request.getEffectiveTime(),
                        request.getExpirationTime(), null);

                // 4. 插入
                BufferPostingRulePO po = BufferRuleConverter.toPO(request);
                bufferPostingRuleRepository.insert(po);
                return null;
            } catch (ServiceException e) {
                throw e;
            } catch (Exception e) {
                log.error("[BUFFER-RULE-CREATE] 创建缓冲规则失败 ruleName={} error={}",
                        request.getRuleName(), e.getMessage(), e);
                throw new ServiceException(ResultCode.SYSTEM_ERROR, "创建缓冲规则失败");
            }
        });
    }

    /**
     * 更新缓冲入账规则
     *
     * 是否记账：否
     */
    public void update(Long ruleId, BufferRuleUpdateRequest request) {
        BufferPostingRulePO po = bufferPostingRuleRepository.selectById(ruleId);
        if (po == null) {
            throw new ServiceException(ResultCode.DATA_NOT_FOUND, "缓冲规则不存在");
        }

        // 1. 科目/账户非空校验（使用更新后的值）

        String subjectCode = Optional.ofNullable(request.getSubjectCode()).orElse(po.getSubjectCode());
        String accountNo = Optional.ofNullable(request.getAccountNo()).orElse(po.getAccountNo());
        validateSubjectOrAccount(subjectCode, accountNo);

        // 2. 时间合法性校验
        LocalDateTime effectiveTime = request.getEffectiveTime() != null
                ? request.getEffectiveTime() : po.getEffectiveTime();
        LocalDateTime expirationTime = request.getExpirationTime() != null
                ? request.getExpirationTime() : po.getExpirationTime();
        validateTimeRange(effectiveTime, expirationTime);

        transactionTemplate.execute(status -> {
            try {
                // 3. 时间区间重叠校验（排除自身）
                // 注：缓冲规则的业务键（businessCode/tradingCode/payChannel）创建后不可变，
                // 故使用旧的业务键做重叠校验。若未来需求允许修改业务键，需先校验新组合下的时间重叠。
                validateTimeOverlap(po.getBusinessCode(), po.getTradingCode(),
                        po.getPayChannel(), effectiveTime, expirationTime, ruleId);

                // 4. 更新字段
                BufferRuleConverter.updatePO(request, po);
                bufferPostingRuleRepository.updateById(po);
                return null;
            } catch (ServiceException e) {
                throw e;
            } catch (Exception e) {
                log.error("[BUFFER-RULE-UPDATE] 更新缓冲规则失败 ruleId={} error={}",
                        ruleId, e.getMessage(), e);
                throw new ServiceException(ResultCode.SYSTEM_ERROR, "更新缓冲规则失败");
            }
        });
    }

    /**
     * 停用缓冲入账规则
     *
     * 是否记账：否
     */
    public void disable(Long ruleId) {
        transactionTemplate.execute(status -> {
            try {
                BufferPostingRulePO po = bufferPostingRuleRepository.selectById(ruleId);
                if (po == null) {
                    throw new ServiceException(ResultCode.DATA_NOT_FOUND, "缓冲规则不存在");
                }

                // 无联动校验（缓冲规则停用不影响已入缓冲的数据）
                po.setStatus(RuleStatusEnum.DISABLED);
                po.setUpdateId("system");
                po.setUpdateName("system");
                bufferPostingRuleRepository.updateById(po);
                return null;
            } catch (ServiceException e) {
                throw e;
            } catch (Exception e) {
                log.error("[BUFFER-RULE-DISABLE] 停用缓冲规则失败 ruleId={} error={}",
                        ruleId, e.getMessage(), e);
                throw new ServiceException(ResultCode.SYSTEM_ERROR, "停用缓冲规则失败");
            }
        });
    }

    /**
     * 启用缓冲入账规则
     *
     * 是否记账：否
     */
    public void enable(Long ruleId) {
        transactionTemplate.execute(status -> {
            try {
                BufferPostingRulePO po = bufferPostingRuleRepository.selectById(ruleId);
                if (po == null) {
                    throw new ServiceException(ResultCode.DATA_NOT_FOUND, "缓冲规则不存在");
                }

                if (RuleStatusEnum.ENABLED.equals(po.getStatus())) {
                    return null;
                }

                // 1. 时间合法性校验
                validateTimeRange(po.getEffectiveTime(), po.getExpirationTime());

                // 2. 时间区间重叠校验（排除自身）
                validateTimeOverlap(po.getBusinessCode(), po.getTradingCode(),
                        po.getPayChannel(), po.getEffectiveTime(),
                        po.getExpirationTime(), ruleId);

                // 3. 状态置为启用
                po.setStatus(RuleStatusEnum.ENABLED);
                po.setUpdateId("system");
                po.setUpdateName("system");
                bufferPostingRuleRepository.updateById(po);
                return null;
            } catch (ServiceException e) {
                throw e;
            } catch (Exception e) {
                log.error("[BUFFER-RULE-ENABLE] 启用缓冲规则失败 ruleId={} error={}",
                        ruleId, e.getMessage(), e);
                throw new ServiceException(ResultCode.SYSTEM_ERROR, "启用缓冲规则失败");
            }
        });
    }

    /**
     * 查询单个缓冲规则
     *
     * 是否记账：否
     */
    public BufferRuleResponse getById(Long ruleId) {
        BufferPostingRulePO po = bufferPostingRuleRepository.selectById(ruleId);
        if (po == null) {
            throw new ServiceException(ResultCode.DATA_NOT_FOUND, "缓冲规则不存在");
        }
        return BufferRuleConverter.toResponse(po);
    }

    /**
     * 分页查询缓冲规则
     *
     * 是否记账：否
     */
    public PageResponse<BufferRuleResponse> page(BufferRuleQueryRequest request) {
        BufferModeEnum bufferMode = BufferModeEnum.fromCode(request.getBufferMode());
        RuleStatusEnum status = RuleStatusEnum.fromCode(request.getStatus());

        LambdaQueryWrapper<BufferPostingRulePO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StrUtil.isNotBlank(request.getBusinessCode()), BufferPostingRulePO::getBusinessCode, request.getBusinessCode())
                .eq(Objects.nonNull(bufferMode), BufferPostingRulePO::getBufferMode, bufferMode)
                .eq(Objects.nonNull(status), BufferPostingRulePO::getStatus, status)
                .orderByDesc(BufferPostingRulePO::getId);

        Page<BufferPostingRulePO> page = new Page<>(request.getPageNo(), request.getPageSize());
        Page<BufferPostingRulePO> result = bufferPostingRuleRepository.page(page, wrapper);

        return PageResponse.<BufferRuleResponse>builder()
                .total(result.getTotal())
                .pages(result.getPages())
                .current(result.getCurrent())
                .list(BufferRuleConverter.toResponseList(result.getRecords()))
                .build();
    }

    /**
     * 科目/账户非空校验
     */
    private void validateSubjectOrAccount(String subjectCode, String accountNo) {
        boolean subjectEmpty = subjectCode == null || subjectCode.isBlank();
        boolean accountEmpty = accountNo == null || accountNo.isBlank();
        if (subjectEmpty && accountEmpty) {
            throw new ServiceException(ResultCode.PARAM_ERROR,
                    "会计科目与账户编号必须有一个不为空");
        }
    }

    /**
     * 时间合法性校验
     */
    private void validateTimeRange(LocalDateTime effectiveTime, LocalDateTime expirationTime) {
        if (effectiveTime.isAfter(expirationTime)) {
            throw new ServiceException(ResultCode.PARAM_ERROR,
                    "生效时间不得晚于失效时间");
        }
    }

    /**
     * 时间区间重叠校验
     */
    private void validateTimeOverlap(String businessCode, String tradingCode, String payChannel,
                                      LocalDateTime effectiveTime, LocalDateTime expirationTime,
                                      Long excludeId) {
        List<BufferPostingRulePO> overlapping = bufferPostingRuleRepository.selectOverlappingRules(
                businessCode, tradingCode, payChannel, effectiveTime, expirationTime, excludeId);
        if (!overlapping.isEmpty()) {
            String conflictNames = overlapping.stream()
                    .map(BufferPostingRulePO::getRuleName)
                    .collect(java.util.stream.Collectors.joining("、"));
            throw new ServiceException(ResultCode.OPERATION_NOT_ALLOWED,
                    "该业务组合下存在时间区间重叠的缓冲规则：" + conflictNames);
        }
    }
}
