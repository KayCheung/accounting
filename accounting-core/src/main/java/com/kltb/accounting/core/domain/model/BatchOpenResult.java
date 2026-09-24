package com.kltb.accounting.core.domain.model;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;

/**
 * 批量开户结果
 */
@Data
@Accessors(chain = true)
public class BatchOpenResult {

    private Integer totalCount;
    private Integer alreadyExists;
    private Integer newlyCreated;
    private Integer failed;
    private List<String> failedReasons;

    public BatchOpenResult() {
        this.totalCount = 0;
        this.alreadyExists = 0;
        this.newlyCreated = 0;
        this.failed = 0;
        this.failedReasons = new ArrayList<>();
    }

    public void incrementAlreadyExists() { this.alreadyExists++; }
    public void incrementNewlyCreated() { this.newlyCreated++; }
    public void incrementFailed() { this.failed++; }
    public void addFailedReason(String reason) { this.failedReasons.add(reason); }
}
