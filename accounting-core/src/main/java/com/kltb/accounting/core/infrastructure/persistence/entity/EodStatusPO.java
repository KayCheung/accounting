package com.kltb.accounting.core.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.kltb.accounting.core.domain.enums.EodStatusEnum;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 日切状态表 PO（Step 17S 新增）
 * <p>
 * DDL: docs/sql/6-infra.sql (t_eod_status)
 * </p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName("t_eod_status")
public class EodStatusPO extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 会计日期（T日） */
    private LocalDate accountingDate;

    /**
     * 日切状态：
     * 1-未开始, 2-切日中, 3-清理中, 4-快照中,
     * 5-试算中, 6-结转中, 7-归档中, 8-完成, 9-失败
     */
    @TableField("eod_status")
    private EodStatusEnum eodStatus;

    /** 切日完成时间 */
    private LocalDateTime switchDateTime;

    /** 归档完成时间 */
    private LocalDateTime archiveDateTime;

    /** 失败阶段（如 CLEANUP, TRIAL_BALANCE, GL_RECONCILIATION 等） */
    private String failedStage;

    /** 失败原因 */
    private String failReason;

    /** 总耗时（毫秒） */
    private Long totalDurationMs;

    /**
     * 判断当前日切是否已完成归档
     */
    public boolean isCompleted() {
        return EodStatusEnum.isCompleted(this.eodStatus);
    }

    /**
     * 获取状态码数值（空安全）
     */
    public Integer getStatusCode() {
        return this.eodStatus != null ? this.eodStatus.getCode() : null;
    }

    /**
     * 便捷支持根据整数状态码设置枚举（兼容遗留调用）
     */
    public EodStatusPO setEodStatus(Integer code) {
        this.eodStatus = EodStatusEnum.fromCode(code);
        return this;
    }

    /**
     * 设置枚举状态
     */
    public EodStatusPO setEodStatus(EodStatusEnum eodStatus) {
        this.eodStatus = eodStatus;
        return this;
    }
}
