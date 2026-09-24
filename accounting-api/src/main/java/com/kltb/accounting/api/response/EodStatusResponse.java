package com.kltb.accounting.api.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 日切状态响应 DTO（Step 17S 新增）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EodStatusResponse {

    private static final Map<Integer, String> STATUS_DESC = Map.of(
            1, "未开始", 2, "切日中", 3, "清理中", 4, "快照中",
            5, "试算中", 6, "结转中", 7, "归档中", 8, "已完成", 9, "失败"
    );

    /** 会计日期 */
    private String accountingDate;

    /** 状态码 */
    private Integer status;

    /** 状态描述 */
    private String statusDesc;

    /** 切日完成时间 */
    private LocalDateTime switchDateTime;

    /** 归档完成时间 */
    private LocalDateTime archiveDateTime;

    /** 失败阶段 */
    private String failedStage;

    /** 失败原因 */
    private String failReason;

    /** 总耗时（毫秒） */
    private Long totalDurationMs;

    /**
     * 从参数构建响应 DTO（避免 API 层依赖 Core 层 PO）
     */
    public static EodStatusResponse from(
            String accountingDate, Integer status, LocalDateTime switchDateTime,
            LocalDateTime archiveDateTime, String failedStage, String failReason,
            Long totalDurationMs) {
        return EodStatusResponse.builder()
                .accountingDate(accountingDate)
                .status(status)
                .statusDesc(STATUS_DESC.getOrDefault(status, "未知"))
                .switchDateTime(switchDateTime)
                .archiveDateTime(archiveDateTime)
                .failedStage(failedStage)
                .failReason(failReason)
                .totalDurationMs(totalDurationMs)
                .build();
    }
}
