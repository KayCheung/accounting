// accounting-core/src/main/java/com/kltb/accounting/core/infrastructure/persistence/repository/ManualVoucherApplyRepository.java
package com.kltb.accounting.core.infrastructure.persistence.repository;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kltb.accounting.api.constant.ManualVoucherApplyStatusEnum;
import com.kltb.accounting.api.request.ManualVoucherApplyPageRequest;
import com.kltb.accounting.core.infrastructure.persistence.entity.ManualVoucherApplyAttachmentPO;
import com.kltb.accounting.core.infrastructure.persistence.entity.ManualVoucherApplyAuxiliaryPO;
import com.kltb.accounting.core.infrastructure.persistence.entity.ManualVoucherApplyEntryPO;
import com.kltb.accounting.core.infrastructure.persistence.entity.ManualVoucherApplyPO;
import com.kltb.accounting.core.infrastructure.persistence.entity.ManualVoucherAuditLogPO;
import com.kltb.accounting.core.infrastructure.persistence.mapper.ManualVoucherApplyAttachmentMapper;
import com.kltb.accounting.core.infrastructure.persistence.mapper.ManualVoucherApplyAuxiliaryMapper;
import com.kltb.accounting.core.infrastructure.persistence.mapper.ManualVoucherApplyEntryMapper;
import com.kltb.accounting.core.infrastructure.persistence.mapper.ManualVoucherApplyMapper;
import com.kltb.accounting.core.infrastructure.persistence.mapper.ManualVoucherAuditLogMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
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
    private final JdbcTemplate jdbcTemplate;

    /**
     * 服务初始化时自动校验并创建新表，确保物理表在数据库中安全就绪
     */
    @PostConstruct
    public void initTables() {
        try {
            log.info("[手工记账] 开始检查并初始化独立审批流数据表...");
            jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS t_manual_voucher_apply (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
                    apply_no VARCHAR(32) NOT NULL COMMENT '申请单号',
                    voucher_type VARCHAR(32) NOT NULL DEFAULT '记账凭证' COMMENT '凭证类型',
                    trade_type TINYINT NOT NULL DEFAULT 2 COMMENT '交易类别：1-正常, 2-调账',
                    accounting_date DATE NOT NULL COMMENT '会计日期',
                    summary VARCHAR(255) NOT NULL DEFAULT '' COMMENT '申请摘要',
                    attachment_count INT NOT NULL DEFAULT 0 COMMENT '附件数',
                    total_debit_amount DECIMAL(18,6) NOT NULL DEFAULT 0.000000 COMMENT '借方合计金额',
                    total_credit_amount DECIMAL(18,6) NOT NULL DEFAULT 0.000000 COMMENT '贷方合计金额',
                    apply_status TINYINT NOT NULL DEFAULT 1 COMMENT '审批状态：1-草稿, 2-待初审, 3-初审驳回, 4-待复核, 5-复核驳回, 6-待记账, 7-已记账, 8-已作废',
                    maker_name VARCHAR(32) NOT NULL DEFAULT '' COMMENT '制单人姓名',
                    auditor_name VARCHAR(32) NOT NULL DEFAULT '' COMMENT '初审人姓名',
                    audit_time DATETIME NULL DEFAULT NULL COMMENT '初审时间',
                    audit_opinion VARCHAR(255) NOT NULL DEFAULT '' COMMENT '初审意见/驳回原因',
                    reviewer_name VARCHAR(32) NOT NULL DEFAULT '' COMMENT '复核人姓名',
                    review_time DATETIME NULL DEFAULT NULL COMMENT '复核时间',
                    review_opinion VARCHAR(255) NOT NULL DEFAULT '' COMMENT '复核意见/驳回原因',
                    bookkeeper_name VARCHAR(32) NOT NULL DEFAULT '' COMMENT '记账人姓名',
                    bookkeeping_time DATETIME NULL DEFAULT NULL COMMENT '记账时间',
                    voucher_no VARCHAR(32) NULL DEFAULT NULL COMMENT '正式入账凭证号',
                    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                    is_delete BIGINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标识',
                    tenant_id INT NOT NULL DEFAULT -1 COMMENT '租户ID',
                    UNIQUE KEY uk_apply_no (apply_no),
                    KEY idx_apply_status (apply_status),
                    KEY idx_voucher_no (voucher_no),
                    KEY idx_accounting_date (accounting_date)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='手工记账申请流转表';
            """);

            jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS t_manual_voucher_apply_entry (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
                    apply_no VARCHAR(32) NOT NULL COMMENT '关联申请单号',
                    row_num INT NOT NULL COMMENT '分录行号',
                    subject_code VARCHAR(32) NOT NULL COMMENT '会计科目编码',
                    account_no VARCHAR(32) NOT NULL DEFAULT '' COMMENT '账户编号',
                    debit_credit TINYINT NOT NULL COMMENT '借贷方向：1-借, 2-贷',
                    amount DECIMAL(18,6) NOT NULL COMMENT '分录金额',
                    currency VARCHAR(32) NOT NULL DEFAULT 'CNY' COMMENT '币种',
                    summary VARCHAR(255) NOT NULL DEFAULT '' COMMENT '分录摘要',
                    is_unilateral TINYINT NOT NULL DEFAULT 1 COMMENT '资金单边处理(1-实时, 0-普通)',
                    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                    is_delete BIGINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标识',
                    tenant_id INT NOT NULL DEFAULT -1 COMMENT '租户ID',
                    KEY idx_apply_no (apply_no)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='手工记账申请分录明细表';
            """);

            jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS t_manual_voucher_audit_log (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
                    apply_no VARCHAR(32) NOT NULL COMMENT '关联申请单号',
                    action VARCHAR(32) NOT NULL COMMENT '审批流动作',
                    action_desc VARCHAR(64) NOT NULL DEFAULT '' COMMENT '动作描述',
                    operator_name VARCHAR(32) NOT NULL DEFAULT '' COMMENT '操作人姓名',
                    operator_role VARCHAR(32) NOT NULL DEFAULT '' COMMENT '操作角色',
                    pre_status TINYINT NULL DEFAULT NULL COMMENT '流转前状态',
                    post_status TINYINT NULL DEFAULT NULL COMMENT '流转后状态',
                    opinion VARCHAR(255) NOT NULL DEFAULT '' COMMENT '审批意见/驳回原因',
                    operate_time DATETIME NOT NULL COMMENT '操作时间',
                    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                    is_delete BIGINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标识',
                    tenant_id INT NOT NULL DEFAULT -1 COMMENT '租户ID',
                    KEY idx_apply_no (apply_no),
                    KEY idx_operate_time (operate_time)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='手工记账审批流转审计日志表';
            """);

            jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS t_manual_voucher_apply_auxiliary (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
                    apply_no VARCHAR(32) NOT NULL COMMENT '关联申请单号',
                    entry_row_num INT NOT NULL COMMENT '关联分录行号',
                    subject_code VARCHAR(32) NOT NULL COMMENT '会计科目编码',
                    aux_type VARCHAR(32) NOT NULL COMMENT '辅助核算类型',
                    aux_type_name VARCHAR(64) NOT NULL DEFAULT '' COMMENT '辅助核算类型名称',
                    aux_code VARCHAR(32) NOT NULL COMMENT '辅助核算项目编码',
                    aux_name VARCHAR(64) NOT NULL COMMENT '辅助核算项目名称',
                    change_direction TINYINT NOT NULL DEFAULT 1 COMMENT '增减方向：1-增, 2-减',
                    amount DECIMAL(18,6) NOT NULL DEFAULT 0.000000 COMMENT '核算金额',
                    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                    is_delete BIGINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标识',
                    tenant_id INT NOT NULL DEFAULT -1 COMMENT '租户ID',
                    KEY idx_apply_no (apply_no)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='手工凭证申请辅助核算分摊表';
            """);

            jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS t_manual_voucher_apply_attachment (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
                    apply_no VARCHAR(32) NOT NULL COMMENT '关联申请单号',
                    file_name VARCHAR(128) NOT NULL DEFAULT '' COMMENT '附件文件名称',
                    file_path VARCHAR(255) NOT NULL COMMENT '附件存储地址/URL',
                    file_size BIGINT NOT NULL DEFAULT 0 COMMENT '附件大小(字节)',
                    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                    is_delete BIGINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标识',
                    tenant_id INT NOT NULL DEFAULT -1 COMMENT '租户ID',
                    KEY idx_apply_no (apply_no)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='手工凭证申请附件表';
            """);
            log.info("[手工记账] 独立审批流数据表初始化校验通过！");
        } catch (Exception e) {
            log.warn("[手工记账] 自动建表初始化跳过或失败(可能已存在或权限受限): {}", e.getMessage());
        }
    }

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
