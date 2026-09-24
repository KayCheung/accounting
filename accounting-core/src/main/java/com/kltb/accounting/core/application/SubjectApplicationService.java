package com.kltb.accounting.core.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kltb.accounting.api.constant.ResultCode;
import com.kltb.accounting.api.request.SubjectCreateRequest;
import com.kltb.accounting.api.request.SubjectQueryRequest;
import com.kltb.accounting.api.request.SubjectUpdateRequest;
import com.kltb.accounting.api.response.PageResponse;
import com.kltb.accounting.core.application.converter.SubjectConverter;
import com.kltb.accounting.core.application.dto.SubjectResponse;
import com.kltb.accounting.core.domain.enums.AvailableStatusEnum;
import com.kltb.accounting.core.domain.enums.SubjectCategoryEnum;
import com.kltb.accounting.core.infrastructure.persistence.entity.AccountSubjectPO;
import com.kltb.accounting.core.infrastructure.persistence.mapper.AccountMapper;
import com.kltb.accounting.core.infrastructure.persistence.repository.SubjectRepository;
import com.kltb.accounting.core.shared.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 科目应用服务
 *
 * 是否记账：否（配置管理类用例编排）
 * 异常处理：
 *   - ServiceException → 全局拦截器捕获，返回对应 ResultCode
 *   - 数据库/框架异常 → 包装为 SYSTEM_ERROR 后抛出
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubjectApplicationService {

    private final SubjectRepository subjectRepository;
    private final AccountMapper accountMapper;
    private final TransactionTemplate transactionTemplate;

    /**
     * 创建科目
     *
     * 是否记账：否
     *
     * @param request 创建请求
     */
    public void create(SubjectCreateRequest request) {
        transactionTemplate.execute(status -> {
            try {
                // 1. 若 parentSubjectId != 0，校验父科目存在 + 编码前缀
                if (request.getParentSubjectId() != null && request.getParentSubjectId() != 0L) {
                    AccountSubjectPO parent = subjectRepository.selectById(request.getParentSubjectId());
                    if (parent == null) {
                        throw new ServiceException(ResultCode.SUBJECT_NOT_FOUND, "父科目不存在");
                    }
                    String parentCode = parent.getSubjectCode();
                    if (parentCode == null || !request.getSubjectCode().startsWith(parentCode)) {
                        throw new ServiceException(ResultCode.PARAM_ERROR,
                                "科目编码必须以父科目编码为前缀，父科目：" + parentCode);
                    }
                    // 若父科目当前标记为末级，自动转为非末级并禁止记账/开户
                    if (Boolean.TRUE.equals(parent.getLeaf())) {
                        parent.setLeaf(false);
                        parent.setAllowPost(false);
                        parent.setAllowOpenAccount(false);
                        parent.setUpdateId("system");
                        parent.setUpdateName("system");
                        subjectRepository.updateSubjectById(parent);
                    }
                }

                // 2. 检查编码唯一性
                AccountSubjectPO existing = subjectRepository.selectByCode(request.getSubjectCode());
                if (existing != null) {
                    throw new ServiceException(ResultCode.PARAM_ERROR,
                            "科目编码已存在：" + request.getSubjectCode());
                }

                // 3. 构建 PO 并插入
                AccountSubjectPO po = SubjectConverter.toPO(request);
                subjectRepository.insertSubject(po);

                // 4. 科目创建时不触发即时开户，账户在记账时通过 AccountPreCheckDomainService 延迟开户
                return null;
            } catch (ServiceException e) {
                throw e;
            } catch (Exception e) {
                log.error("[SUBJECT-CREATE] 创建科目失败 subjectCode={} error={}",
                        request.getSubjectCode(), e.getMessage(), e);
                throw new ServiceException(ResultCode.SYSTEM_ERROR, "创建科目失败");
            }
        });
    }

    /**
     * 更新科目
     *
     * 是否记账：否
     *
     * @param subjectCode 科目编码
     * @param request     更新请求
     */
    public void update(String subjectCode, SubjectUpdateRequest request) {
        transactionTemplate.execute(status -> {
            try {
                // 1. 查询科目是否存在
                AccountSubjectPO po = subjectRepository.selectByCode(subjectCode);
                if (po == null) {
                    throw new ServiceException(ResultCode.SUBJECT_NOT_FOUND, "科目不存在：" + subjectCode);
                }
                // 2. 若修改账类，需确保无关联账户（防御性校验）
                if (request.getSubjectCategory() != null
                        && po.getSubjectCategory() != null
                        && !request.getSubjectCategory().equals(po.getSubjectCategory().getCode())
                        && hasLinkedAccounts(subjectCode)) {
                    throw new ServiceException(ResultCode.OPERATION_NOT_ALLOWED,
                            "该科目存在关联账户，禁止修改账类");
                }
                // 3. 校验末级科目规则：若存在子科目，不允许改为末级科目
                if (Boolean.TRUE.equals(request.getLeaf()) && subjectRepository.existsSubjectByParentCode(subjectCode)) {
                    throw new ServiceException(ResultCode.OPERATION_NOT_ALLOWED, "该科目存在子科目，无法设为末级科目");
                }
                // 4. 更新允许修改的字段
                SubjectConverter.updatePO(request, po);
                // 非末级科目强制不允许记账和开户
                if (Boolean.FALSE.equals(po.getLeaf())) {
                    po.setAllowPost(false);
                    po.setAllowOpenAccount(false);
                }
                subjectRepository.updateSubjectById(po);
                return null;
            } catch (ServiceException e) {
                throw e;
            } catch (Exception e) {
                log.error("[SUBJECT-UPDATE] 更新科目失败 subjectCode={} error={}",
                        subjectCode, e.getMessage(), e);
                throw new ServiceException(ResultCode.SYSTEM_ERROR, "更新科目失败");
            }
        });
    }

    /**
     * 停用科目（状态改为停用）
     *
     * 是否记账：否
     *
     * @param subjectCode 科目编码
     */
    public void disable(String subjectCode) {
        transactionTemplate.execute(status -> {
            try {
                // 1. 查询科目是否存在
                AccountSubjectPO po = subjectRepository.selectByCode(subjectCode);
                if (po == null) {
                    throw new ServiceException(ResultCode.SUBJECT_NOT_FOUND, "科目不存在：" + subjectCode);
                }

                // 2. 停用联动校验
                if (subjectRepository.existsSubjectByParentCode(subjectCode)) {
                    throw new ServiceException(ResultCode.OPERATION_NOT_ALLOWED,
                            "该科目存在子科目，无法停用");
                }
                if (subjectRepository.countTemplateBySubjectCode(subjectCode) > 0) {
                    throw new ServiceException(ResultCode.OPERATION_NOT_ALLOWED,
                            "该科目被开户模板引用，无法停用");
                }
                if (subjectRepository.countRuleDetailBySubjectCode(subjectCode) > 0) {
                    throw new ServiceException(ResultCode.OPERATION_NOT_ALLOWED,
                            "该科目被记账规则引用，无法停用");
                }

                // 3. 更新状态为停用
                po.setStatus(AvailableStatusEnum.DISABLED);
                po.setUpdateId("system");
                po.setUpdateName("system");
                subjectRepository.updateSubjectById(po);
                return null;
            } catch (ServiceException e) {
                throw e;
            } catch (Exception e) {
                log.error("[SUBJECT-DISABLE] 停用科目失败 subjectCode={} error={}",
                        subjectCode, e.getMessage(), e);
                throw new ServiceException(ResultCode.SYSTEM_ERROR, "停用科目失败");
            }
        });
    }

    /**
     * 按编码查询单个科目
     *
     * 是否记账：否
     */
    public SubjectResponse getByCode(String subjectCode) {
        AccountSubjectPO po = subjectRepository.selectByCode(subjectCode);
        if (po == null) {
            throw new ServiceException(ResultCode.SUBJECT_NOT_FOUND, "科目不存在：" + subjectCode);
        }
        SubjectResponse resp = SubjectConverter.toResponse(po);
        if (po.getParentSubjectId() != null && po.getParentSubjectId() > 0) {
            AccountSubjectPO parent = subjectRepository.selectById(po.getParentSubjectId());
            if (parent != null) {
                resp.setParentSubjectCode(parent.getSubjectCode());
                resp.setParentSubjectName(parent.getSubjectName());
            }
        }
        resp.setHasChildren(!Boolean.TRUE.equals(po.getLeaf()));
        return resp;
    }

    /**
     * 分页查询科目
     *
     * 是否记账：否
     */
    public PageResponse<SubjectResponse> page(SubjectQueryRequest request) {
        SubjectCategoryEnum category = SubjectCategoryEnum.fromCode(request.getSubjectCategory());
        AvailableStatusEnum status = AvailableStatusEnum.fromCode(request.getStatus());

        LambdaQueryWrapper<AccountSubjectPO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Objects.nonNull(category), AccountSubjectPO::getSubjectCategory, category)
                .eq(Objects.nonNull(status), AccountSubjectPO::getStatus, status)
                .eq(Objects.nonNull(request.getLeaf()), AccountSubjectPO::getLeaf, request.getLeaf())
                .orderByAsc(AccountSubjectPO::getSubjectCode);

        Page<AccountSubjectPO> page = new Page<>(request.getPageNo(), request.getPageSize());
        Page<AccountSubjectPO> result = subjectRepository.pageSubject(page, wrapper);

        List<AccountSubjectPO> records = result.getRecords();
        List<SubjectResponse> respList = SubjectConverter.toResponseList(records);
        Set<Long> parentIds = records.stream()
                .map(AccountSubjectPO::getParentSubjectId)
                .filter(id -> id != null && id > 0)
                .collect(Collectors.toSet());
        if (!parentIds.isEmpty()) {
            List<AccountSubjectPO> parents = subjectRepository.selectBatchIds(parentIds);
            Map<Long, AccountSubjectPO> parentMap = parents.stream()
                    .collect(Collectors.toMap(AccountSubjectPO::getId, p -> p, (k1, k2) -> k1));
            for (SubjectResponse item : respList) {
                if (item.getParentSubjectId() != null && item.getParentSubjectId() > 0) {
                    AccountSubjectPO p = parentMap.get(item.getParentSubjectId());
                    if (p != null) {
                        item.setParentSubjectCode(p.getSubjectCode());
                        item.setParentSubjectName(p.getSubjectName());
                    }
                }
            }
        }

        return PageResponse.<SubjectResponse>builder()
                .total(result.getTotal())
                .pages(result.getPages())
                .current(result.getCurrent())
                .list(respList)
                .build();
    }

    /**
     * 检查科目是否存在关联账户（防御性校验，用于更新账类时）
     */
    private boolean hasLinkedAccounts(String subjectCode) {
        return accountMapper.countBySubjectCode(subjectCode) > 0;
    }
}
