// accounting-core/src/main/java/com/kltb/accounting/core/infrastructure/persistence/repository/ManualVoucherApplyRepository.java
package com.kltb.accounting.core.infrastructure.persistence.repository;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kltb.accounting.api.request.ManualVoucherApplyPageRequest;
import com.kltb.accounting.core.domain.enums.ManualVoucherApplyStatusEnum;
import com.kltb.accounting.core.infrastructure.persistence.entity.*;
import com.kltb.accounting.core.infrastructure.persistence.mapper.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.List;

/**
 * 手工记账申请仓储层（独立审批流与流转日志仓储）
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class ManualVoucherApplyRepository {

    private final ManualVoucherApplyMapper applyMapper;
    private final ManualVoucherApplyEntryMapper entryMapper;
    private final ManualVoucherApplyAuxiliaryMapper auxiliaryMapper;
    private final ManualVoucherApplyAttachmentMapper attachmentMapper;
    private final ManualVoucherAuditLogMapper auditLogMapper;

    public int insertApply(ManualVoucherApplyPO po) {
        return applyMapper.insert(po);
    }

    public int updateApply(ManualVoucherApplyPO po) {
        return applyMapper.updateById(po);
    }

    public ManualVoucherApplyPO selectByApplyNo(String applyNo) {
        return applyMapper.selectOne(new LambdaQueryWrapper<ManualVoucherApplyPO>()
                .eq(ManualVoucherApplyPO::getApplyNo, applyNo)
                .eq(ManualVoucherApplyPO::getIsDelete, 0));
    }

    public IPage<ManualVoucherApplyPO> selectPage(ManualVoucherApplyPageRequest request) {
        Page<ManualVoucherApplyPO> page = new Page<>(request.getPageNo(), request.getPageSize());
        LambdaQueryWrapper<ManualVoucherApplyPO> wrapper = new LambdaQueryWrapper<ManualVoucherApplyPO>()
                .eq(ManualVoucherApplyPO::getIsDelete, 0);

        if (StrUtil.isNotBlank(request.getApplyNo())) {
            wrapper.like(ManualVoucherApplyPO::getApplyNo, request.getApplyNo().trim());
        }
        if (StrUtil.isNotBlank(request.getVoucherNo())) {
            wrapper.like(ManualVoucherApplyPO::getVoucherNo, request.getVoucherNo().trim());
        }
        if (request.getApplyStatus() != null) {
            wrapper.eq(ManualVoucherApplyPO::getApplyStatus, ManualVoucherApplyStatusEnum.fromCode(request.getApplyStatus()));
        }
        if (StrUtil.isNotBlank(request.getMakerName())) {
            wrapper.like(ManualVoucherApplyPO::getMakerName, request.getMakerName().trim());
        }
        if (StrUtil.isNotBlank(request.getSummary())) {
            wrapper.like(ManualVoucherApplyPO::getSummary, request.getSummary().trim());
        }
        if (request.getStartDate() != null) {
            wrapper.ge(ManualVoucherApplyPO::getAccountingDate, request.getStartDate());
        }
        if (request.getEndDate() != null) {
            wrapper.le(ManualVoucherApplyPO::getAccountingDate, request.getEndDate());
        }
        if (request.getCreateStartTime() != null) {
            wrapper.ge(ManualVoucherApplyPO::getCreateTime, request.getCreateStartTime());
        }
        if (request.getCreateEndTime() != null) {
            wrapper.le(ManualVoucherApplyPO::getCreateTime, request.getCreateEndTime());
        }

        wrapper.orderByDesc(ManualVoucherApplyPO::getId);
        return applyMapper.selectPage(page, wrapper);
    }

    public void insertEntries(List<ManualVoucherApplyEntryPO> entries) {
        if (entries == null || entries.isEmpty()) {
            return;
        }
        for (ManualVoucherApplyEntryPO entry : entries) {
            entryMapper.insert(entry);
        }
    }

    public void deleteEntriesByApplyNo(String applyNo) {
        entryMapper.delete(new LambdaQueryWrapper<ManualVoucherApplyEntryPO>()
                .eq(ManualVoucherApplyEntryPO::getApplyNo, applyNo));
    }

    public List<ManualVoucherApplyEntryPO> selectEntriesByApplyNo(String applyNo) {
        return entryMapper.selectList(new LambdaQueryWrapper<ManualVoucherApplyEntryPO>()
                .eq(ManualVoucherApplyEntryPO::getApplyNo, applyNo)
                .eq(ManualVoucherApplyEntryPO::getIsDelete, 0)
                .orderByAsc(ManualVoucherApplyEntryPO::getRowNum));
    }

    public List<ManualVoucherApplyEntryPO> selectEntriesByApplyNos(List<String> applyNos) {
        if (applyNos == null || applyNos.isEmpty()) {
            return Collections.emptyList();
        }
        return entryMapper.selectList(new LambdaQueryWrapper<ManualVoucherApplyEntryPO>()
                .in(ManualVoucherApplyEntryPO::getApplyNo, applyNos)
                .eq(ManualVoucherApplyEntryPO::getIsDelete, 0)
                .orderByAsc(ManualVoucherApplyEntryPO::getRowNum));
    }

    public int insertAuditLog(ManualVoucherAuditLogPO logPO) {
        return auditLogMapper.insert(logPO);
    }

    public List<ManualVoucherAuditLogPO> selectAuditLogsByApplyNo(String applyNo) {
        return auditLogMapper.selectList(new LambdaQueryWrapper<ManualVoucherAuditLogPO>()
                .eq(ManualVoucherAuditLogPO::getApplyNo, applyNo)
                .eq(ManualVoucherAuditLogPO::getIsDelete, 0)
                .orderByAsc(ManualVoucherAuditLogPO::getOperateTime)
                .orderByAsc(ManualVoucherAuditLogPO::getId));
    }

    public Long countByStatus(ManualVoucherApplyStatusEnum status) {
        LambdaQueryWrapper<ManualVoucherApplyPO> wrapper = new LambdaQueryWrapper<ManualVoucherApplyPO>()
                .eq(ManualVoucherApplyPO::getIsDelete, 0);
        if (status != null) {
            wrapper.eq(ManualVoucherApplyPO::getApplyStatus, status);
        }
        return applyMapper.selectCount(wrapper);
    }

    // ==================== 辅助核算分摊项 CRUD ====================

    public void insertAuxiliaries(List<ManualVoucherApplyAuxiliaryPO> list) {
        if (list == null || list.isEmpty()) {
            return;
        }
        for (ManualVoucherApplyAuxiliaryPO item : list) {
            auxiliaryMapper.insert(item);
        }
    }

    public void deleteAuxiliariesByApplyNo(String applyNo) {
        auxiliaryMapper.delete(new LambdaQueryWrapper<ManualVoucherApplyAuxiliaryPO>()
                .eq(ManualVoucherApplyAuxiliaryPO::getApplyNo, applyNo));
    }

    public List<ManualVoucherApplyAuxiliaryPO> selectAuxiliariesByApplyNo(String applyNo) {
        return auxiliaryMapper.selectList(new LambdaQueryWrapper<ManualVoucherApplyAuxiliaryPO>()
                .eq(ManualVoucherApplyAuxiliaryPO::getApplyNo, applyNo)
                .eq(ManualVoucherApplyAuxiliaryPO::getIsDelete, 0)
                .orderByAsc(ManualVoucherApplyAuxiliaryPO::getEntryRowNum)
                .orderByAsc(ManualVoucherApplyAuxiliaryPO::getId));
    }

    public List<ManualVoucherApplyAuxiliaryPO> selectAuxiliariesByApplyNos(List<String> applyNos) {
        if (applyNos == null || applyNos.isEmpty()) {
            return Collections.emptyList();
        }
        return auxiliaryMapper.selectList(new LambdaQueryWrapper<ManualVoucherApplyAuxiliaryPO>()
                .in(ManualVoucherApplyAuxiliaryPO::getApplyNo, applyNos)
                .eq(ManualVoucherApplyAuxiliaryPO::getIsDelete, 0)
                .orderByAsc(ManualVoucherApplyAuxiliaryPO::getEntryRowNum)
                .orderByAsc(ManualVoucherApplyAuxiliaryPO::getId));
    }

    // ==================== 凭证原始附件 CRUD ====================

    public void insertAttachments(List<ManualVoucherApplyAttachmentPO> list) {
        if (list == null || list.isEmpty()) {
            return;
        }
        for (ManualVoucherApplyAttachmentPO item : list) {
            attachmentMapper.insert(item);
        }
    }

    public void deleteAttachmentsByApplyNo(String applyNo) {
        attachmentMapper.delete(new LambdaQueryWrapper<ManualVoucherApplyAttachmentPO>()
                .eq(ManualVoucherApplyAttachmentPO::getApplyNo, applyNo));
    }

    public List<ManualVoucherApplyAttachmentPO> selectAttachmentsByApplyNo(String applyNo) {
        return attachmentMapper.selectList(new LambdaQueryWrapper<ManualVoucherApplyAttachmentPO>()
                .eq(ManualVoucherApplyAttachmentPO::getApplyNo, applyNo)
                .eq(ManualVoucherApplyAttachmentPO::getIsDelete, 0)
                .orderByAsc(ManualVoucherApplyAttachmentPO::getId));
    }

    public List<ManualVoucherApplyAttachmentPO> selectAttachmentsByApplyNos(List<String> applyNos) {
        if (applyNos == null || applyNos.isEmpty()) {
            return Collections.emptyList();
        }
        return attachmentMapper.selectList(new LambdaQueryWrapper<ManualVoucherApplyAttachmentPO>()
                .in(ManualVoucherApplyAttachmentPO::getApplyNo, applyNos)
                .eq(ManualVoucherApplyAttachmentPO::getIsDelete, 0)
                .orderByAsc(ManualVoucherApplyAttachmentPO::getId));
    }
}
