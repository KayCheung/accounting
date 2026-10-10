package com.kltb.accounting.core.application.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kltb.accounting.api.request.AuxiliaryLedgerQueryRequest;
import com.kltb.accounting.api.response.*;
import com.kltb.accounting.core.application.converter.DictConverter;
import com.kltb.accounting.core.application.dto.DictResponse;
import com.kltb.accounting.core.domain.enums.SubjectCategoryEnum;
import com.kltb.accounting.core.infrastructure.persistence.bo.*;
import com.kltb.accounting.core.infrastructure.persistence.entity.AccountSubjectPO;
import com.kltb.accounting.core.infrastructure.persistence.entity.DictionaryPO;
import com.kltb.accounting.core.infrastructure.persistence.repository.AccountingVoucherAuxiliaryRepository;
import com.kltb.accounting.core.infrastructure.persistence.repository.DictionaryRepository;
import com.kltb.accounting.core.infrastructure.persistence.repository.SubjectRepository;
import com.kltb.accounting.core.infrastructure.redis.DictionaryCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 辅助核算账簿应用服务
 * <p>
 * 提供多维辅助核算类别与项目查询、汇总对比矩阵分析、科目分布明细及凭证流水台账等核心能力。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuxiliaryLedgerApplicationService {

    private static final String DICT_TYPE_AUXILIARY = "auxiliary_type";
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final AccountingVoucherAuxiliaryRepository auxiliaryRepository;
    private final SubjectRepository subjectRepository;
    private final DictionaryCacheService dictionaryCacheService;
    private final DictionaryRepository dictionaryRepository;

    /**
     * 获取系统支持的所有辅助核算类别（从字典 auxiliary_type 获取）
     */
    public List<DictResponse> getAuxiliaryTypes() {
        List<DictionaryPO> dicts = dictionaryCacheService.getByType(DICT_TYPE_AUXILIARY);
        if (dicts == null || dicts.isEmpty()) {
            dicts = dictionaryRepository.selectByType(DICT_TYPE_AUXILIARY);
        }
        return DictConverter.toResponseList(dicts);
    }

    /**
     * 查询指定核算类别下有凭证入账记录的核算项目列表
     */
    public List<AuxiliaryItemResponse> getAuxiliaryItems(String auxType, LocalDate startDate, LocalDate endDate) {
        if (auxType == null || auxType.isBlank()) {
            return Collections.emptyList();
        }
        List<AuxiliaryItemStatBO> statList = auxiliaryRepository.selectDistinctItems(auxType, startDate, endDate);
        return statList.stream()
                .map(stat -> AuxiliaryItemResponse.builder()
                        .auxCode(stat.getAuxCode())
                        .auxName(stat.getAuxName())
                        .recordCount(stat.getRecordCount())
                        .totalAmount(stat.getTotalAmount() != null ? stat.getTotalAmount() : BigDecimal.ZERO)
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * 查询辅助核算账簿统计 KPI 概览指标
     */
    public AuxiliarySummaryResponse getAuxiliarySummary(AuxiliaryLedgerQueryRequest request) {
        AuxiliarySummaryBO bo = auxiliaryRepository.selectSummary(request);
        BigDecimal grandTotal = bo.getTotalAmount() != null ? bo.getTotalAmount() : BigDecimal.ZERO;

        // 计算排名前列的核算项分布（Top 5）
        List<AuxiliaryItemStatBO> allItems = auxiliaryRepository.selectDistinctItems(
                request.getAuxType(), request.getStartDate(), request.getEndDate());

        List<AuxiliaryItemShareResponse> topItems = new ArrayList<>();
        int count = 0;
        for (AuxiliaryItemStatBO item : allItems) {
            if (count >= 5) {
                break;
            }
            BigDecimal amt = item.getTotalAmount() != null ? item.getTotalAmount() : BigDecimal.ZERO;
            BigDecimal pct = BigDecimal.ZERO;
            if (grandTotal.compareTo(BigDecimal.ZERO) > 0 && amt.compareTo(BigDecimal.ZERO) > 0) {
                pct = amt.multiply(HUNDRED).divide(grandTotal, 2, RoundingMode.HALF_UP);
            }
            topItems.add(AuxiliaryItemShareResponse.builder()
                    .auxCode(item.getAuxCode())
                    .auxName(item.getAuxName())
                    .amount(amt)
                    .percentage(pct)
                    .build());
            count++;
        }

        return AuxiliarySummaryResponse.builder()
                .totalAmount(grandTotal)
                .debitAmount(bo.getDebitAmount() != null ? bo.getDebitAmount() : BigDecimal.ZERO)
                .creditAmount(bo.getCreditAmount() != null ? bo.getCreditAmount() : BigDecimal.ZERO)
                .itemCount(bo.getItemCount() != null ? bo.getItemCount() : 0)
                .subjectCount(bo.getSubjectCount() != null ? bo.getSubjectCount() : 0)
                .voucherCount(bo.getVoucherCount() != null ? bo.getVoucherCount() : 0)
                .topItems(topItems)
                .build();
    }

    /**
     * 查询科目 × 核算项目的二维交叉汇总对比矩阵
     */
    public AuxiliaryMatrixResponse getAuxiliaryMatrix(AuxiliaryLedgerQueryRequest request) {
        // 1. 获取动态列（该核算类别下所有的核算项）
        List<AuxiliaryItemResponse> columns = getAuxiliaryItems(
                request.getAuxType(), request.getStartDate(), request.getEndDate());

        // 2. 获取单元格金额列表
        List<AuxiliaryMatrixCellBO> cells = auxiliaryRepository.selectMatrixCellAmounts(request);
        if (cells.isEmpty()) {
            return AuxiliaryMatrixResponse.builder()
                    .columns(columns)
                    .rows(Collections.emptyList())
                    .columnTotals(Collections.emptyMap())
                    .grandTotal(BigDecimal.ZERO)
                    .build();
        }

        // 3. 预加载所有科目信息，用于组装科目名称与账类
        Map<String, AccountSubjectPO> subjectMap = subjectRepository.selectAllSubjects().stream()
                .collect(Collectors.toMap(AccountSubjectPO::getSubjectCode, Function.identity(), (a, b) -> a));

        // 4. 按科目分组聚合行数据
        Map<String, Map<String, BigDecimal>> subjectAuxAmountMap = new LinkedHashMap<>();
        for (AuxiliaryMatrixCellBO cell : cells) {
            subjectAuxAmountMap
                    .computeIfAbsent(cell.getSubjectCode(), k -> new HashMap<>())
                    .put(cell.getAuxCode(), cell.getAmount());
        }

        // 5. 计算全表总计与列合计
        BigDecimal grandTotal = BigDecimal.ZERO;
        Map<String, BigDecimal> columnTotals = new HashMap<>();
        List<AuxiliaryMatrixRowResponse> rows = new ArrayList<>();

        for (Map.Entry<String, Map<String, BigDecimal>> entry : subjectAuxAmountMap.entrySet()) {
            String subjectCode = entry.getKey();
            Map<String, BigDecimal> auxAmtMap = entry.getValue();

            BigDecimal rowTotal = BigDecimal.ZERO;
            for (Map.Entry<String, BigDecimal> auxEntry : auxAmtMap.entrySet()) {
                BigDecimal amt = auxEntry.getValue() != null ? auxEntry.getValue() : BigDecimal.ZERO;
                rowTotal = rowTotal.add(amt);

                // 累加列合计
                columnTotals.merge(auxEntry.getKey(), amt, BigDecimal::add);
            }
            grandTotal = grandTotal.add(rowTotal);

            AccountSubjectPO subjectPO = subjectMap.get(subjectCode);
            String subjectName = subjectPO != null ? subjectPO.getSubjectName() : subjectCode;
            Integer categoryCode = subjectPO != null && subjectPO.getSubjectCategory() != null
                    ? subjectPO.getSubjectCategory().getCode() : null;
            String categoryDesc = subjectPO != null && subjectPO.getSubjectCategory() != null
                    ? subjectPO.getSubjectCategory().getDesc() : "";

            rows.add(AuxiliaryMatrixRowResponse.builder()
                    .subjectCode(subjectCode)
                    .subjectName(subjectName)
                    .subjectCategory(categoryCode)
                    .subjectCategoryDesc(categoryDesc)
                    .amounts(auxAmtMap)
                    .totalAmount(rowTotal)
                    .percentage(BigDecimal.ZERO) // 下方统一按 grandTotal 算百分比
                    .build());
        }

        // 6. 补充各行占全表总计的百分比
        if (grandTotal.compareTo(BigDecimal.ZERO) > 0) {
            for (AuxiliaryMatrixRowResponse row : rows) {
                if (row.getTotalAmount() != null && row.getTotalAmount().compareTo(BigDecimal.ZERO) > 0) {
                    row.setPercentage(row.getTotalAmount().multiply(HUNDRED)
                            .divide(grandTotal, 2, RoundingMode.HALF_UP));
                }
            }
        }

        // 7. 按科目编码排序
        rows.sort(Comparator.comparing(AuxiliaryMatrixRowResponse::getSubjectCode));

        return AuxiliaryMatrixResponse.builder()
                .columns(columns)
                .rows(rows)
                .columnTotals(columnTotals)
                .grandTotal(grandTotal)
                .build();
    }

    /**
     * 查询辅助核算科目分布明细
     */
    public List<AuxiliarySubjectDetailResponse> getAuxiliarySubjectBreakdown(AuxiliaryLedgerQueryRequest request) {
        List<AuxiliarySubjectBreakdownBO> boList = auxiliaryRepository.selectSubjectBreakdown(request);
        if (boList.isEmpty()) {
            return Collections.emptyList();
        }

        Map<String, AccountSubjectPO> subjectMap = subjectRepository.selectAllSubjects().stream()
                .collect(Collectors.toMap(AccountSubjectPO::getSubjectCode, Function.identity(), (a, b) -> a));

        BigDecimal totalSum = boList.stream()
                .map(bo -> bo.getTotalAmount() != null ? bo.getTotalAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<AuxiliarySubjectDetailResponse> result = new ArrayList<>(boList.size());
        for (AuxiliarySubjectBreakdownBO bo : boList) {
            AccountSubjectPO subjectPO = subjectMap.get(bo.getSubjectCode());
            String subjectName = subjectPO != null ? subjectPO.getSubjectName() : bo.getSubjectCode();
            Integer categoryCode = subjectPO != null && subjectPO.getSubjectCategory() != null
                    ? subjectPO.getSubjectCategory().getCode() : null;
            String categoryDesc = subjectPO != null && subjectPO.getSubjectCategory() != null
                    ? subjectPO.getSubjectCategory().getDesc() : "";

            BigDecimal amt = bo.getTotalAmount() != null ? bo.getTotalAmount() : BigDecimal.ZERO;
            BigDecimal pct = BigDecimal.ZERO;
            if (totalSum.compareTo(BigDecimal.ZERO) > 0 && amt.compareTo(BigDecimal.ZERO) > 0) {
                pct = amt.multiply(HUNDRED).divide(totalSum, 2, RoundingMode.HALF_UP);
            }

            result.add(AuxiliarySubjectDetailResponse.builder()
                    .subjectCode(bo.getSubjectCode())
                    .subjectName(subjectName)
                    .subjectCategory(categoryCode)
                    .subjectCategoryDesc(categoryDesc)
                    .debitAmount(bo.getDebitAmount() != null ? bo.getDebitAmount() : BigDecimal.ZERO)
                    .creditAmount(bo.getCreditAmount() != null ? bo.getCreditAmount() : BigDecimal.ZERO)
                    .totalAmount(amt)
                    .entryCount(bo.getEntryCount() != null ? bo.getEntryCount() : 0)
                    .percentage(pct)
                    .build());
        }

        return result;
    }

    /**
     * 分页查询辅助凭证分录记录明细
     */
    public PageResponse<AuxiliaryEntryRecordResponse> getAuxiliaryEntries(AuxiliaryLedgerQueryRequest request) {
        int pageNo = request.getPageNo() != null && request.getPageNo() > 0 ? request.getPageNo() : 1;
        int pageSize = request.getPageSize() != null && request.getPageSize() > 0 ? request.getPageSize() : 20;

        IPage<AuxiliaryEntryRecordBO> pageParam = new Page<>(pageNo, pageSize);
        IPage<AuxiliaryEntryRecordBO> resultPage = auxiliaryRepository.selectEntryPage(pageParam, request);

        List<AuxiliaryEntryRecordResponse> list = resultPage.getRecords().stream()
                .map(this::toEntryRecordResponse)
                .collect(Collectors.toList());

        return PageResponse.<AuxiliaryEntryRecordResponse>builder()
                .current(resultPage.getCurrent())
                .pages(resultPage.getPages())
                .total(resultPage.getTotal())
                .list(list)
                .build();
    }

    private AuxiliaryEntryRecordResponse toEntryRecordResponse(AuxiliaryEntryRecordBO bo) {
        Integer dc = bo.getDebitCredit();
        if (dc == null) {
            dc = (bo.getChangeDirection() != null && bo.getChangeDirection() == 2) ? 2 : 1;
        }

        BigDecimal amt = bo.getAmount() != null ? bo.getAmount() : BigDecimal.ZERO;
        BigDecimal debitAmt = Integer.valueOf(1).equals(dc) ? amt : BigDecimal.ZERO;
        BigDecimal creditAmt = Integer.valueOf(2).equals(dc) ? amt : BigDecimal.ZERO;

        String digits = bo.getVoucherNo() != null ? bo.getVoucherNo().replaceAll("[^0-9]", "") : "";
        String voucherWord = "记 " + (digits.isEmpty() ? bo.getVoucherNo() : digits);

        return AuxiliaryEntryRecordResponse.builder()
                .id(bo.getId())
                .accountingDate(bo.getAccountingDate())
                .voucherNo(bo.getVoucherNo())
                .voucherWord(voucherWord)
                .entryId(bo.getEntryId())
                .summary(bo.getSummary() != null ? bo.getSummary() : "")
                .auxType(bo.getAuxType())
                .auxCode(bo.getAuxCode())
                .auxName(bo.getAuxName())
                .subjectCode(bo.getSubjectCode())
                .subjectName(bo.getSubjectName() != null ? bo.getSubjectName() : bo.getSubjectCode())
                .changeDirection(bo.getChangeDirection())
                .changeDirectionDesc(bo.getChangeDirection() != null && bo.getChangeDirection() == 2 ? "减少" : "增加")
                .debitCredit(dc)
                .debitCreditDesc(Integer.valueOf(2).equals(dc) ? "贷" : "借")
                .debitAmount(debitAmt)
                .creditAmount(creditAmt)
                .amount(amt)
                .build();
    }
}
