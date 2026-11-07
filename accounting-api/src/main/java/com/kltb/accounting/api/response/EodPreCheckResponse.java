package com.kltb.accounting.api.response;

import com.fasterxml.jackson.annotation.JsonFormat;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EodPreCheckResponse {

    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    private LocalDate accountingDate;
    private boolean allPassed;
    private List<CheckItem> checks;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CheckItem {
        private String name;
        private int count;
        private boolean passed;
    }
}
