package com.kltb.accounting.core.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kltb.accounting.api.constant.ResultCode;
import com.kltb.accounting.api.request.TemplateCreateRequest;
import com.kltb.accounting.api.request.TemplateGroupSaveRequest;
import com.kltb.accounting.api.request.TemplateItemRequest;
import com.kltb.accounting.api.request.TemplateQueryRequest;
import com.kltb.accounting.api.request.TemplateUpdateRequest;
import com.kltb.accounting.api.response.PageResponse;
import com.kltb.accounting.core.application.converter.TemplateConverter;
import com.kltb.accounting.core.application.dto.TemplateResponse;
import com.kltb.accounting.core.domain.enums.BalanceDirectionEnum;
import com.kltb.accounting.core.domain.enums.CustomerTypeEnum;
import com.kltb.accounting.core.domain.enums.TemplateStatusEnum;
import com.kltb.accounting.core.infrastructure.persistence.entity.AccountSubjectPO;
import com.kltb.accounting.core.infrastructure.persistence.entity.AccountTemplatePO;
import com.kltb.accounting.core.infrastructure.persistence.mapper.AccountMapper;
import com.kltb.accounting.core.infrastructure.persistence.repository.SubjectRepository;
import com.kltb.accounting.core.shared.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import cn.hutool.core.util.StrUtil;
import java.util.List;
import java.util.Objects;

/**
 * 开户模板应用服务
 *
 * 是否记账：否（配置管理类用例编排）
 * 异常处理：
 *   - ServiceException → 全局拦截器捕获，返回对应 ResultCode
 *   - 数据库/框架异常 → 包装为 SYSTEM_ERROR 后抛出
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TemplateApplicationService {

    private final SubjectRepository subjectRepository;
    private final AccountMapper accountMapper;
    private final TransactionTemplate transactionTemplate;

    /**
     * 创建开户模板
     *
     * 是否记账：否
     */
    public void create(TemplateCreateRequest request) {
        transactionTemplate.execute(status -> {
            try {
                // 1. 科目校验：存在 + 末级 + 允许开户
                validateSubject(request.getSubjectCode());

                // 2. 唯一键校验
                AccountTemplatePO existing = subjectRepository.selectTemplateByBusinessKey(
                        request.getBusinessCode(), request.getCustomerType(), request.getSubjectCode());
                if (existing != null) {
                    throw new ServiceException(ResultCode.PARAM_ERROR,
                            "同一业务线+客户类型+科目的开户模板已存在");
                }

                // 3. 构建 PO 并插入
                AccountTemplatePO po = TemplateConverter.toPO(request);
                subjectRepository.insertTemplate(po);
                return null;
            } catch (ServiceException e) {
                throw e;
            } catch (Exception e) {
                log.error("[TEMPLATE-CREATE] 创建模板失败 templateName={} error={}",
                        request.getTemplateName(), e.getMessage(), e);
                throw new ServiceException(ResultCode.SYSTEM_ERROR, "创建模板失败");
            }
        });
    }

    /**
     * 批量保存开户模板（支持配置多个会计科目账户）
     *
     * 是否记账：否
     */
    public void saveGroup(TemplateGroupSaveRequest request) {
        CustomerTypeEnum customerType = CustomerTypeEnum.fromCode(request.getCustomerType());
        if (customerType == null) {
            throw new ServiceException(ResultCode.PARAM_ERROR, "无效的客户类型");
        }
        TemplateStatusEnum status = TemplateStatusEnum.fromCode(request.getStatus());
        if (status == null) {
            status = TemplateStatusEnum.ENABLED;
        }

        final TemplateStatusEnum finalStatus = status;

        transactionTemplate.execute(txStatus -> {
            try {
                // 1. 校验科目
                for (TemplateItemRequest item : request.getItems()) {
                    validateSubject(item.getSubjectCode());
                }

                // 2. 逐条处理模板项
                for (TemplateItemRequest item : request.getItems()) {
                    AccountTemplatePO existing = null;
                    if (item.getId() != null) {
                        existing = subjectRepository.selectTemplateById(item.getId());
                    }
                    if (existing == null) {
                        existing = subjectRepository.selectTemplateByBusinessKey(
                                request.getBusinessCode(), request.getCustomerType(), item.getSubjectCode());
                    }

                    if (existing != null) {
                        existing.setTemplateName(request.getTemplateName());
                        existing.setAutoOpen(request.getAutoOpen());
                        existing.setStatus(finalStatus);
                        existing.setAccountType(item.getAccountType());
                        existing.setCurrency(StrUtil.isNotBlank(item.getCurrency()) ? item.getCurrency() : "CNY");
                        if (item.getBalanceDirection() != null) {
                            existing.setBalanceDirection(item.getBalanceDirection() == 1 ? BalanceDirectionEnum.DEBIT : BalanceDirectionEnum.CREDIT);
                        }
                        existing.setAcctNoRule(item.getAcctNoRule());
                        existing.setAcctNameRule(item.getAcctNameRule());
                        existing.setUpdateId("system");
                        existing.setUpdateName("system");
                        subjectRepository.updateTemplateById(existing);
                    } else {
                        AccountTemplatePO po = new AccountTemplatePO();
                        po.setTemplateName(request.getTemplateName());
                        po.setBusinessCode(request.getBusinessCode());
                        po.setCustomerType(customerType);
                        po.setAutoOpen(request.getAutoOpen());
                        po.setStatus(finalStatus);
                        po.setSubjectCode(item.getSubjectCode());
                        po.setAccountType(item.getAccountType());
                        po.setCurrency(StrUtil.isNotBlank(item.getCurrency()) ? item.getCurrency() : "CNY");
                        po.setBalanceDirection(item.getBalanceDirection() != null && item.getBalanceDirection() == 1
                                ? BalanceDirectionEnum.DEBIT : BalanceDirectionEnum.CREDIT);
                        po.setAcctNoRule(item.getAcctNoRule());
                        po.setAcctNameRule(item.getAcctNameRule());
                        po.setCreateId("system");
                        po.setCreateName("system");
                        po.setUpdateId("system");
                        po.setUpdateName("system");
                        subjectRepository.insertTemplate(po);
                    }
                }
                return null;
            } catch (ServiceException e) {
                throw e;
            } catch (Exception e) {
                log.error("[TEMPLATE-SAVE-GROUP] 批量保存模板失败 templateName={} error={}",
                        request.getTemplateName(), e.getMessage(), e);
                throw new ServiceException(ResultCode.SYSTEM_ERROR, "批量保存模板失败");
            }
        });
    }

    /**
     * 更新开户模板
     *
     * 是否记账：否
     */
    public void update(Long templateId, TemplateUpdateRequest request) {
        transactionTemplate.execute(status -> {
            try {
                AccountTemplatePO po = subjectRepository.selectTemplateById(templateId);
                if (po == null) {
                    throw new ServiceException(ResultCode.ACCOUNT_TEMPLATE_NOT_FOUND, "模板不存在");
                }
                TemplateConverter.updatePO(request, po);
                subjectRepository.updateTemplateById(po);
                return null;
            } catch (ServiceException e) {
                throw e;
            } catch (Exception e) {
                log.error("[TEMPLATE-UPDATE] 更新模板失败 templateId={} error={}",
                        templateId, e.getMessage(), e);
                throw new ServiceException(ResultCode.SYSTEM_ERROR, "更新模板失败");
            }
        });
    }

    /**
     * 停用开户模板
     *
     * 是否记账：否
     */
    public void disable(Long templateId) {
        transactionTemplate.execute(status -> {
            try {
                AccountTemplatePO template = subjectRepository.selectTemplateById(templateId);
                if (template == null) {
                    throw new ServiceException(ResultCode.ACCOUNT_TEMPLATE_NOT_FOUND, "模板不存在");
                }

                // 停用联动校验：检查是否有已关联的活跃账户
                long activeAccountCount = accountMapper.countBySubjectCodeAndOwnerType(
                        template.getSubjectCode(),
                        template.getCustomerType() != null ? template.getCustomerType().getCode() : null);
                if (activeAccountCount > 0) {
                    throw new ServiceException(ResultCode.OPERATION_NOT_ALLOWED,
                            "该模板存在已关联账户（" + activeAccountCount + "个），无法停用");
                }

                template.setStatus(TemplateStatusEnum.DISABLED);
                template.setUpdateId("system");
                template.setUpdateName("system");
                subjectRepository.updateTemplateById(template);
                return null;
            } catch (ServiceException e) {
                throw e;
            } catch (Exception e) {
                log.error("[TEMPLATE-DISABLE] 停用模板失败 templateId={} error={}",
                        templateId, e.getMessage(), e);
                throw new ServiceException(ResultCode.SYSTEM_ERROR, "停用模板失败");
            }
        });
    }

    /**
     * 查询单个模板
     *
     * 是否记账：否
     */
    public TemplateResponse getById(Long templateId) {
        AccountTemplatePO po = subjectRepository.selectTemplateById(templateId);
        if (po == null) {
            throw new ServiceException(ResultCode.ACCOUNT_TEMPLATE_NOT_FOUND, "模板不存在");
        }
        return TemplateConverter.toResponse(po);
    }

    /**
     * 分页查询模板
     *
     * 是否记账：否
     */
    public PageResponse<TemplateResponse> page(TemplateQueryRequest request) {
        CustomerTypeEnum customerType = CustomerTypeEnum.fromCode(request.getCustomerType());
        TemplateStatusEnum status = TemplateStatusEnum.fromCode(request.getStatus());

        LambdaQueryWrapper<AccountTemplatePO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StrUtil.isNotBlank(request.getBusinessCode()), AccountTemplatePO::getBusinessCode, request.getBusinessCode())
                .eq(Objects.nonNull(customerType), AccountTemplatePO::getCustomerType, customerType)
                .eq(Objects.nonNull(status), AccountTemplatePO::getStatus, status)
                .orderByDesc(AccountTemplatePO::getId);

        Page<AccountTemplatePO> page = new Page<>(request.getPageNo(), request.getPageSize());
        Page<AccountTemplatePO> result = subjectRepository.pageTemplate(page, wrapper);

        return PageResponse.<TemplateResponse>builder()
                .total(result.getTotal())
                .pages(result.getPages())
                .current(result.getCurrent())
                .list(TemplateConverter.toResponseList(result.getRecords()))
                .build();
    }

    /**
     * 科目校验：存在 + 末级 + 允许开户
     */
    private void validateSubject(String subjectCode) {
        AccountSubjectPO subject = subjectRepository.selectByCode(subjectCode);
        if (subject == null) {
            throw new ServiceException(ResultCode.SUBJECT_NOT_FOUND, "科目不存在：" + subjectCode);
        }
        if (!Boolean.TRUE.equals(subject.getLeaf())) {
            throw new ServiceException(ResultCode.OPERATION_NOT_ALLOWED,
                    "开户模板关联科目必须为末级科目：" + subjectCode);
        }
        if (!Boolean.TRUE.equals(subject.getAllowOpenAccount())) {
            throw new ServiceException(ResultCode.OPERATION_NOT_ALLOWED,
                    "开户模板关联科目必须允许开户：" + subjectCode);
        }
    }

}
