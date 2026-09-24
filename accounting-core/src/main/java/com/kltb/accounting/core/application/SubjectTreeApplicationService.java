package com.kltb.accounting.core.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kltb.accounting.api.constant.ResultCode;
import com.kltb.accounting.api.request.AuxiliaryCreateRequest;
import com.kltb.accounting.api.request.AuxiliaryUpdateRequest;
import com.kltb.accounting.core.application.converter.SubjectTreeConverter;
import com.kltb.accounting.core.application.dto.AuxiliaryResponse;
import com.kltb.accounting.core.application.dto.SubjectResponse;
import com.kltb.accounting.core.domain.enums.AvailableStatusEnum;
import com.kltb.accounting.core.infrastructure.persistence.entity.AccountSubjectAuxiliaryPO;
import com.kltb.accounting.core.infrastructure.persistence.entity.AccountSubjectPO;
import com.kltb.accounting.core.infrastructure.persistence.mapper.AccountSubjectAuxiliaryMapper;
import com.kltb.accounting.core.infrastructure.persistence.repository.SubjectRepository;
import com.kltb.accounting.core.shared.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import cn.hutool.core.util.StrUtil;
import com.kltb.accounting.core.domain.enums.SubjectCategoryEnum;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 科目树 + 辅助核算项应用服务
 *
 * 是否记账：否（配置管理类用例编排）
 * 异常处理：
 *   - ServiceException → 全局拦截器捕获，返回对应 ResultCode
 *   - 数据库/框架异常 → 包装为 SYSTEM_ERROR 后抛出
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubjectTreeApplicationService {

    private final SubjectRepository subjectRepository;
    private final AccountSubjectAuxiliaryMapper subjectAuxiliaryMapper;
    private final TransactionTemplate transactionTemplate;

    /**
     * 科目树形查询（懒加载 + 全量）
     *
     * 是否记账：否
     *
     * @param parentId 父科目ID（不传或<=0→根节点，传值→懒加载直接子节点）
     * @param status   状态过滤（默认只返回启用状态，0表示全部）
     * @return 科目列表（扁平）
     */
    public List<SubjectResponse> queryTree(Long parentId, Integer status) {
        return queryTree(parentId, status, null, null);
    }

    /**
     * 科目树形查询（懒加载 + 账类过滤）
     *
     * 是否记账：否
     *
     * @param parentId        父科目ID（不传或<=0→根节点，传值→懒加载直接子节点）
     * @param status          状态过滤（1=启用，2=停用，0表示全部）
     * @param subjectCategory 账类过滤（0=表外, 1=资产, 2=负债, 3=权益, 4=共同, 5=成本, 6=损益）
     * @return 科目列表（扁平）
     */
    public List<SubjectResponse> queryTree(Long parentId, Integer status, Integer subjectCategory) {
        return queryTree(parentId, status, subjectCategory, null);
    }

    /**
     * 科目树形查询（支持懒加载 + 跨级关键字搜索 + 账类/状态过滤）
     *
     * 是否记账：否
     *
     * @param parentId        父科目ID（不传或<=0→根节点，传值→懒加载直接子节点）
     * @param status          状态过滤（1=启用，2=停用，0表示全部）
     * @param subjectCategory 账类过滤（0=表外, 1=资产, 2=负债, 3=权益, 4=共同, 5=成本, 6=损益）
     * @param keyword         编码或名称关键字（若提供，则进行跨级全局模糊搜索）
     * @return 科目列表（扁平）
     */
    public List<SubjectResponse> queryTree(Long parentId, Integer status, Integer subjectCategory, String keyword) {
        AvailableStatusEnum statusEnum = AvailableStatusEnum.fromCode(status);
        SubjectCategoryEnum categoryEnum = SubjectCategoryEnum.fromCode(subjectCategory);

        boolean hasKeyword = StrUtil.isNotBlank(keyword);
        boolean hasStatus = Objects.nonNull(statusEnum);
        boolean hasCategory = Objects.nonNull(categoryEnum);
        boolean isSpecificParent = Objects.nonNull(parentId) && parentId > 0;

        LambdaQueryWrapper<AccountSubjectPO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(isSpecificParent, AccountSubjectPO::getParentSubjectId, parentId)
                .eq(!isSpecificParent && !hasKeyword && !hasStatus && !hasCategory, AccountSubjectPO::getParentSubjectId, 0L)
                .and(hasKeyword, w -> w.like(AccountSubjectPO::getSubjectCode, keyword.trim()).or().like(AccountSubjectPO::getSubjectName, keyword.trim()))
                .eq(hasStatus, AccountSubjectPO::getStatus, statusEnum)
                .eq(hasCategory, AccountSubjectPO::getSubjectCategory, categoryEnum)
                .orderByAsc(AccountSubjectPO::getSubjectCode);

        List<AccountSubjectPO> poList = subjectRepository.selectList(wrapper);

        List<SubjectResponse> respList = SubjectTreeConverter.toSubjectResponseList(poList);

        // 填充父科目信息
        Set<Long> parentIds = poList.stream()
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

        return respList;
    }

    /**
     * 为科目添加辅助核算项
     *
     * 是否记账：否
     *
     * @param subjectCode 科目编码
     * @param request     创建请求
     */
    public void createAuxiliary(String subjectCode, AuxiliaryCreateRequest request) {
        transactionTemplate.execute(status -> {
            try {
                // 1. 检查科目是否存在
                AccountSubjectPO subject = subjectRepository.selectByCode(subjectCode);
                if (subject == null) {
                    throw new ServiceException(ResultCode.SUBJECT_NOT_FOUND, "科目不存在：" + subjectCode);
                }

                // 2. 预检查唯一键（subjectCode + auxiliaryType）
                AccountSubjectAuxiliaryPO existing = subjectAuxiliaryMapper.selectOne(
                        new LambdaQueryWrapper<AccountSubjectAuxiliaryPO>()
                                .eq(AccountSubjectAuxiliaryPO::getSubjectCode, subjectCode)
                                .eq(AccountSubjectAuxiliaryPO::getAuxiliaryType, request.getAuxiliaryType()));
                if (existing != null) {
                    throw new ServiceException(ResultCode.PARAM_ERROR,
                            "该科目的辅助核算项已存在：" + request.getAuxiliaryType());
                }

                // 3. 插入
                AccountSubjectAuxiliaryPO po = SubjectTreeConverter.toAuxiliaryPO(request, subjectCode);
                subjectAuxiliaryMapper.insert(po);
                return null;
            } catch (ServiceException e) {
                throw e;
            } catch (Exception e) {
                log.error("[AUX-CREATE] 创建辅助核算项失败 subjectCode={} auxiliaryType={} error={}",
                        subjectCode, request.getAuxiliaryType(), e.getMessage(), e);
                throw new ServiceException(ResultCode.SYSTEM_ERROR, "创建辅助核算项失败");
            }
        });
    }

    /**
     * 删除辅助核算项
     *
     * 是否记账：否
     *
     * @param subjectCode    科目编码
     * @param auxiliaryType  辅助核算项类型
     */
    public void deleteAuxiliary(String subjectCode, String auxiliaryType) {
        transactionTemplate.execute(status -> {
            try {
                // 1. 查询是否存在
                AccountSubjectAuxiliaryPO aux = subjectAuxiliaryMapper.selectOne(
                        new LambdaQueryWrapper<AccountSubjectAuxiliaryPO>()
                                .eq(AccountSubjectAuxiliaryPO::getSubjectCode, subjectCode)
                                .eq(AccountSubjectAuxiliaryPO::getAuxiliaryType, auxiliaryType));
                if (aux == null) {
                    throw new ServiceException(ResultCode.DATA_NOT_FOUND,
                            "辅助核算项不存在：" + subjectCode + "/" + auxiliaryType);
                }

                // 2. 必填保护：required=true 的项目不允许删除
                if (Boolean.TRUE.equals(aux.getRequired())) {
                    throw new ServiceException(ResultCode.OPERATION_NOT_ALLOWED,
                            "该辅助核算项为必填项，不允许删除");
                }

                // 3. 逻辑删除
                subjectAuxiliaryMapper.logicDeleteById(aux.getId(), System.currentTimeMillis());
                return null;
            } catch (ServiceException e) {
                throw e;
            } catch (Exception e) {
                log.error("[AUX-DELETE] 删除辅助核算项失败 subjectCode={} auxiliaryType={} error={}",
                        subjectCode, auxiliaryType, e.getMessage(), e);
                throw new ServiceException(ResultCode.SYSTEM_ERROR, "删除辅助核算项失败");
            }
        });
    }

    /**
     * 更新辅助核算项
     *
     * 是否记账：否
     *
     * @param subjectCode    科目编码
     * @param auxiliaryType  辅助核算项类型
     * @param request        更新请求
     */
    public void updateAuxiliary(String subjectCode, String auxiliaryType, AuxiliaryUpdateRequest request) {
        transactionTemplate.execute(status -> {
            try {
                // 1. 查询是否存在
                AccountSubjectAuxiliaryPO aux = subjectAuxiliaryMapper.selectOne(
                        new LambdaQueryWrapper<AccountSubjectAuxiliaryPO>()
                                .eq(AccountSubjectAuxiliaryPO::getSubjectCode, subjectCode)
                                .eq(AccountSubjectAuxiliaryPO::getAuxiliaryType, auxiliaryType));
                if (aux == null) {
                    throw new ServiceException(ResultCode.DATA_NOT_FOUND,
                            "辅助核算项不存在：" + subjectCode + "/" + auxiliaryType);
                }

                // 2. 更新
                SubjectTreeConverter.updateAuxiliaryPO(request, aux);
                subjectAuxiliaryMapper.updateById(aux);
                return null;
            } catch (ServiceException e) {
                throw e;
            } catch (Exception e) {
                log.error("[AUX-UPDATE] 更新辅助核算项失败 subjectCode={} auxiliaryType={} error={}",
                        subjectCode, auxiliaryType, e.getMessage(), e);
                throw new ServiceException(ResultCode.SYSTEM_ERROR, "更新辅助核算项失败");
            }
        });
    }

    /**
     * 查询科目的辅助核算项列表
     *
     * 是否记账：否
     */
    public List<AuxiliaryResponse> listAuxiliary(String subjectCode) {
        List<AccountSubjectAuxiliaryPO> list = subjectAuxiliaryMapper.selectList(
                new LambdaQueryWrapper<AccountSubjectAuxiliaryPO>()
                        .eq(AccountSubjectAuxiliaryPO::getSubjectCode, subjectCode)
                        .orderByAsc(AccountSubjectAuxiliaryPO::getId));
        return SubjectTreeConverter.toAuxiliaryResponseList(list);
    }
}
