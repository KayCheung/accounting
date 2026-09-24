package com.kltb.accounting.core.application;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kltb.accounting.api.constant.ResultCode;
import com.kltb.accounting.api.request.DictCreateRequest;
import com.kltb.accounting.api.request.DictQueryRequest;
import com.kltb.accounting.api.request.DictUpdateRequest;
import com.kltb.accounting.api.response.PageResponse;
import com.kltb.accounting.core.application.converter.DictConverter;
import com.kltb.accounting.core.application.dto.DictResponse;
import com.kltb.accounting.core.domain.enums.AvailableStatusEnum;
import com.kltb.accounting.core.infrastructure.persistence.entity.DictionaryPO;
import com.kltb.accounting.core.infrastructure.persistence.repository.DictionaryRepository;
import com.kltb.accounting.core.infrastructure.redis.DictionaryCacheService;
import com.kltb.accounting.core.shared.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Objects;

/**
 * 字典应用服务
 *
 * 是否记账：否（配置管理类用例编排）
 * 异常处理：
 *   - ServiceException → 全局拦截器捕获，返回对应 ResultCode
 *   - 数据库/框架异常 → 包装为 SYSTEM_ERROR 后抛出
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DictApplicationService {

    private final DictionaryRepository dictionaryRepository;
    private final DictionaryCacheService dictionaryCacheService;
    private final TransactionTemplate transactionTemplate;

    /**
     * 创建字典项
     *
     * 是否记账：否
     *
     * @param request 创建请求
     */
    public void create(DictCreateRequest request) {
        transactionTemplate.execute(status -> {
            try {
                DictionaryPO existing = dictionaryRepository.selectByTypeAndCode(request.getDictType(), request.getDictCode());
                if (existing != null) {
                    throw new ServiceException(ResultCode.PARAM_ERROR,
                            "字典项已存在：" + request.getDictType() + "/" + request.getDictCode());
                }
                DictionaryPO po = DictConverter.toPO(request);
                dictionaryRepository.insert(po);
                dictionaryCacheService.invalidate(request.getDictType());
                return null;
            } catch (ServiceException e) {
                throw e;
            } catch (Exception e) {
                log.error("[DICT-CREATE] 创建字典项失败 dictType={} dictCode={} error={}",
                        request.getDictType(), request.getDictCode(), e.getMessage(), e);
                throw new ServiceException(ResultCode.SYSTEM_ERROR, "创建字典项失败");
            }
        });
    }

    /**
     * 更新字典项
     *
     * 是否记账：否
     *
     * @param dictType 字典类型
     * @param dictCode 字典编码
     * @param request  更新请求
     */
    public void update(String dictType, String dictCode, DictUpdateRequest request) {
        transactionTemplate.execute(status -> {
            try {
                DictionaryPO po = dictionaryRepository.selectByTypeAndCode(dictType, dictCode);
                if (po == null) {
                    throw new ServiceException(ResultCode.DATA_NOT_FOUND,
                            "字典项不存在：" + dictType + "/" + dictCode);
                }
                DictConverter.updatePO(request, po);
                dictionaryRepository.updateById(po);
                dictionaryCacheService.invalidate(dictType);
                return null;
            } catch (ServiceException e) {
                throw e;
            } catch (Exception e) {
                log.error("[DICT-UPDATE] 更新字典项失败 dictType={} dictCode={} error={}",
                        dictType, dictCode, e.getMessage(), e);
                throw new ServiceException(ResultCode.SYSTEM_ERROR, "更新字典项失败");
            }
        });
    }

    /**
     * 删除字典项（逻辑删除）
     *
     * 是否记账：否
     *
     * @param dictType 字典类型
     * @param dictCode 字典编码
     */
    public void delete(String dictType, String dictCode) {
        transactionTemplate.execute(status -> {
            try {
                DictionaryPO po = dictionaryRepository.selectByTypeAndCode(dictType, dictCode);
                if (po == null) {
                    throw new ServiceException(ResultCode.DATA_NOT_FOUND,
                            "字典项不存在：" + dictType + "/" + dictCode);
                }
                if (Boolean.TRUE.equals(po.getSystem())) {
                    throw new ServiceException(ResultCode.OPERATION_NOT_ALLOWED,
                            "该字典为系统内置，不允许删除");
                }
                dictionaryRepository.logicDeleteById(po.getId(), System.currentTimeMillis());
                dictionaryCacheService.invalidate(dictType);
                return null;
            } catch (ServiceException e) {
                throw e;
            } catch (Exception e) {
                log.error("[DICT-DELETE] 删除字典项失败 dictType={} dictCode={} error={}",
                        dictType, dictCode, e.getMessage(), e);
                throw new ServiceException(ResultCode.SYSTEM_ERROR, "删除字典项失败");
            }
        });
    }

    /**
     * 按类型查询字典列表（走二级缓存）
     *
     * 是否记账：否
     *
     * @param dictType 字典类型
     * @return 字典项列表
     */
    public List<DictResponse> listByType(String dictType) {
        List<DictionaryPO> list = dictionaryCacheService.getByType(dictType);
        return DictConverter.toResponseList(list);
    }

    /**
     * 分页查询字典
     *
     * 是否记账：否
     *
     * @param request 查询请求
     * @return 分页结果
     */
    public PageResponse<DictResponse> page(DictQueryRequest request) {
        LambdaQueryWrapper<DictionaryPO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DictionaryPO::getIsDelete, 0);
        wrapper.eq(StrUtil.isNotBlank(request.getDictType()), DictionaryPO::getDictType, request.getDictType());
        AvailableStatusEnum status = AvailableStatusEnum.fromCode(request.getStatus());
        wrapper.eq(Objects.nonNull(status), DictionaryPO::getStatus, status);
        wrapper.eq(StrUtil.isNotBlank(request.getGroupKey()), DictionaryPO::getGroupKey, request.getGroupKey());
        wrapper.orderByDesc(DictionaryPO::getId);

        Page<DictionaryPO> page = new Page<>(request.getPageNo(), request.getPageSize());
        Page<DictionaryPO> result = dictionaryRepository.selectPage(page, wrapper);

        return PageResponse.<DictResponse>builder()
                .total(result.getTotal())
                .pages(result.getPages())
                .current(result.getCurrent())
                .list(DictConverter.toResponseList(result.getRecords()))
                .build();
    }

    /**
     * 手动刷新字典缓存
     *
     * 是否记账：否
     *
     * @param dictType 字典类型（可选，不传刷新全部）
     */
    public void refreshCache(String dictType) {
        if (dictType != null && !dictType.isBlank()) {
            dictionaryCacheService.refresh(dictType);
        } else {
            dictionaryCacheService.refreshAll();
        }
    }
}
