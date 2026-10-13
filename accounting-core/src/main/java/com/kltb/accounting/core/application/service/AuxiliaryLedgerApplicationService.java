package com.kltb.accounting.core.application.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kltb.accounting.api.request.AuxiliaryLedgerQueryRequest;
import com.kltb.accounting.api.response.*;
import com.kltb.accounting.core.application.converter.DictConverter;
import com.kltb.accounting.core.application.dto.DictResponse;
import com.kltb.accounting.core.domain.enums.BalanceDirectionEnum;
import com.kltb.accounting.core.domain.enums.ChangeDirectionEnum;
import com.kltb.accounting.core.domain.enums.DebitCreditEnum;
import com.kltb.accounting.core.domain.enums.TradeTypeEnum;
import com.kltb.accounting.core.domain.enums.VoucherStatusEnum;
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
        return getAuxiliaryItems(auxType, startDate, endDate, null);
    }

    /**
     * 查询指定核算类别与凭证状态下有凭证入账记录的核算项目列表
     */
    public List<AuxiliaryItemResponse> getAuxiliaryItems(String auxType, LocalDate startDate, LocalDate endDate, Integer voucherStatus) {
        if (auxType == null || auxType.isBlank()) {
            return Collections.emptyList();
        }
        List<AuxiliaryItemStatBO> statList = auxiliaryRepository.selectDistinctItems(auxType, startDate, endDate, voucherStatus);
        return statList.stream()
                .map(stat -> {
                    BigDecimal debit = stat.getDebitAmount() != null ? stat.getDebitAmount() : BigDecimal.ZERO;
                    BigDecimal credit = stat.getCreditAmount() != null ? stat.getCreditAmount() : BigDecimal.ZERO;
                    BigDecimal net = debit.subtract(credit).abs();
                    String netDirection = BalanceDirectionEnum.determineDirection(debit, credit).getDesc();
                    return AuxiliaryItemResponse.builder()
                            .auxCode(stat.getAuxCode())
                            .auxName(stat.getAuxName())
                            .recordCount(stat.getRecordCount())
                            .totalAmount(stat.getTotalAmount() != null ? stat.getTotalAmount() : BigDecimal.ZERO)
                            .debitAmount(debit)
                            .creditAmount(credit)
                            .netAmount(net)
                            .netDirectionDesc(netDirection)
                            .build();
                })
                .collect(Collectors.toList());
    }

    /**
     * 查询辅助核算账簿统计 KPI 概览指标
     */
    public AuxiliarySummaryResponse getAuxiliarySummary(AuxiliaryLedgerQueryRequest request) {
        AuxiliarySummaryBO bo = auxiliaryRepository.selectSummary(request);
        BigDecimal grandTotal = bo.getTotalAmount() != null ? bo.getTotalAmount() : BigDecimal.ZERO;
        BigDecimal debitTotal = bo.getDebitAmount() != null ? bo.getDebitAmount() : BigDecimal.ZERO;
        BigDecimal creditTotal = bo.getCreditAmount() != null ? bo.getCreditAmount() : BigDecimal.ZERO;
        BigDecimal netTotal = debitTotal.subtract(creditTotal).abs();
        String netDirection = BalanceDirectionEnum.determineDirection(debitTotal, creditTotal).getDesc();

        // 计算排名前列的核算项分布（Top 5）
        List<AuxiliaryItemStatBO> allItems = auxiliaryRepository.selectDistinctItems(
                request.getAuxType(), request.getStartDate(), request.getEndDate(), request.getVoucherStatus());

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
                .debitAmount(debitTotal)
                .creditAmount(creditTotal)
                .netAmount(netTotal)
                .netDirectionDesc(netDirection)
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
        String amountMode = (request.getAmountMode() != null && !request.getAmountMode().isBlank())
                ? request.getAmountMode().toUpperCase() : "NET";
        boolean isGrossMode = "GROSS".equals(amountMode);

        // 1. 获取动态列（该核算类别下所有的核算项）
        List<AuxiliaryItemResponse> columns = getAuxiliaryItems(
                request.getAuxType(), request.getStartDate(), request.getEndDate(), request.getVoucherStatus());

        // 2. 获取单元格金额列表
        List<AuxiliaryMatrixCellBO> cells = auxiliaryRepository.selectMatrixCellAmounts(request);
        if (cells.isEmpty()) {
            return AuxiliaryMatrixResponse.builder()
                    .columns(columns)
                    .rows(Collections.emptyList())
                    .columnTotals(Collections.emptyMap())
                    .columnNetTotals(Collections.emptyMap())
                    .grandTotal(BigDecimal.ZERO)
                    .grandNetTotal(BigDecimal.ZERO)
                    .amountMode(amountMode)
                    .build();
        }

        // 3. 预加载所有科目信息，用于组装科目名称与账类
        Map<String, AccountSubjectPO> subjectMap = subjectRepository.selectAllSubjects().stream()
                .collect(Collectors.toMap(AccountSubjectPO::getSubjectCode, Function.identity(), (a, b) -> a));

        // 4. 按科目分组聚合行数据（分别聚合发生额 gross 与净额 net）
        Map<String, Map<String, BigDecimal>> subjectGrossMap = new LinkedHashMap<>();
        Map<String, Map<String, BigDecimal>> subjectNetMap = new LinkedHashMap<>();

        for (AuxiliaryMatrixCellBO cell : cells) {
            String subject = cell.getSubjectCode();
            String aux = cell.getAuxCode();
            BigDecimal cellGross = cell.getAmount() != null ? cell.getAmount() : BigDecimal.ZERO;
            BigDecimal cellDebit = cell.getDebitAmount() != null ? cell.getDebitAmount() : BigDecimal.ZERO;
            BigDecimal cellCredit = cell.getCreditAmount() != null ? cell.getCreditAmount() : BigDecimal.ZERO;
            BigDecimal cellNet = cellDebit.subtract(cellCredit).abs();

            subjectGrossMap.computeIfAbsent(subject, k -> new HashMap<>()).put(aux, cellGross);
            subjectNetMap.computeIfAbsent(subject, k -> new HashMap<>()).put(aux, cellNet);
        }

        // 5. 计算全表总计与列合计
        BigDecimal grandGrossTotal = BigDecimal.ZERO;
        BigDecimal grandNetTotal = BigDecimal.ZERO;
        Map<String, BigDecimal> columnGrossTotals = new HashMap<>();
        Map<String, BigDecimal> columnNetTotals = new HashMap<>();
        List<AuxiliaryMatrixRowResponse> rows = new ArrayList<>();

        for (String subjectCode : subjectGrossMap.keySet()) {
            Map<String, BigDecimal> grossMap = subjectGrossMap.getOrDefault(subjectCode, Collections.emptyMap());
            Map<String, BigDecimal> netMap = subjectNetMap.getOrDefault(subjectCode, Collections.emptyMap());

            BigDecimal rowGrossTotal = BigDecimal.ZERO;
            for (Map.Entry<String, BigDecimal> entry : grossMap.entrySet()) {
                BigDecimal amt = entry.getValue() != null ? entry.getValue() : BigDecimal.ZERO;
                rowGrossTotal = rowGrossTotal.add(amt);
                columnGrossTotals.merge(entry.getKey(), amt, BigDecimal::add);
            }
            grandGrossTotal = grandGrossTotal.add(rowGrossTotal);

            BigDecimal rowNetTotal = BigDecimal.ZERO;
            for (Map.Entry<String, BigDecimal> entry : netMap.entrySet()) {
                BigDecimal amt = entry.getValue() != null ? entry.getValue() : BigDecimal.ZERO;
                rowNetTotal = rowNetTotal.add(amt);
                columnNetTotals.merge(entry.getKey(), amt, BigDecimal::add);
            }
            grandNetTotal = grandNetTotal.add(rowNetTotal);

            AccountSubjectPO subjectPO = subjectMap.get(subjectCode);
            String subjectName = subjectPO != null ? subjectPO.getSubjectName() : subjectCode;
            Integer categoryCode = subjectPO != null && subjectPO.getSubjectCategory() != null
                    ? subjectPO.getSubjectCategory().getCode() : null;
            String categoryDesc = subjectPO != null && subjectPO.getSubjectCategory() != null
                    ? subjectPO.getSubjectCategory().getDesc() : "";

            Map<String, BigDecimal> displayAmounts = isGrossMode ? grossMap : netMap;
            BigDecimal displayTotal = isGrossMode ? rowGrossTotal : rowNetTotal;

            rows.add(AuxiliaryMatrixRowResponse.builder()
                    .subjectCode(subjectCode)
                    .subjectName(subjectName)
                    .subjectCategory(categoryCode)
                    .subjectCategoryDesc(categoryDesc)
                    .amounts(displayAmounts)
                    .grossAmounts(grossMap)
                    .netAmounts(netMap)
                    .totalAmount(displayTotal)
                    .grossTotalAmount(rowGrossTotal)
                    .netTotalAmount(rowNetTotal)
                    .percentage(BigDecimal.ZERO)
                    .build());
        }

        // 6. 补充各行占全表总计的百分比（发生额基准）
        if (grandGrossTotal.compareTo(BigDecimal.ZERO) > 0) {
            for (AuxiliaryMatrixRowResponse row : rows) {
                if (row.getGrossTotalAmount() != null && row.getGrossTotalAmount().compareTo(BigDecimal.ZERO) > 0) {
                    row.setPercentage(row.getGrossTotalAmount().multiply(HUNDRED)
                            .divide(grandGrossTotal, 2, RoundingMode.HALF_UP));
                }
            }
        }

        // 7. 按科目编码排序
        rows.sort(Comparator.comparing(AuxiliaryMatrixRowResponse::getSubjectCode));

        Map<String, BigDecimal> displayColumnTotals = isGrossMode ? columnGrossTotals : columnNetTotals;
        BigDecimal displayGrandTotal = isGrossMode ? grandGrossTotal : grandNetTotal;

        return AuxiliaryMatrixResponse.builder()
                .columns(columns)
                .rows(rows)
                .columnTotals(displayColumnTotals)
                .columnNetTotals(columnNetTotals)
                .grandTotal(displayGrandTotal)
                .grandNetTotal(grandNetTotal)
                .amountMode(amountMode)
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
            BigDecimal debit = bo.getDebitAmount() != null ? bo.getDebitAmount() : BigDecimal.ZERO;
            BigDecimal credit = bo.getCreditAmount() != null ? bo.getCreditAmount() : BigDecimal.ZERO;
            BigDecimal net = debit.subtract(credit).abs();
            String netDirection = BalanceDirectionEnum.determineDirection(debit, credit).getDesc();
            BigDecimal pct = BigDecimal.ZERO;
            if (totalSum.compareTo(BigDecimal.ZERO) > 0 && amt.compareTo(BigDecimal.ZERO) > 0) {
                pct = amt.multiply(HUNDRED).divide(totalSum, 2, RoundingMode.HALF_UP);
            }

            result.add(AuxiliarySubjectDetailResponse.builder()
                    .subjectCode(bo.getSubjectCode())
                    .subjectName(subjectName)
                    .subjectCategory(categoryCode)
                    .subjectCategoryDesc(categoryDesc)
                    .debitAmount(debit)
                    .creditAmount(credit)
                    .netAmount(net)
                    .netDirectionDesc(netDirection)
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
        // 1. 确定借贷方向：优先取分录自身借贷方向，若为空则由辅助核算增减方向推导（增 -> 借，减 -> 贷）
        DebitCreditEnum dcEnum = DebitCreditEnum.fromCode(bo.getDebitCredit());
        if (dcEnum == null) {
            ChangeDirectionEnum cdEnum = ChangeDirectionEnum.fromCode(bo.getChangeDirection());
            dcEnum = DebitCreditEnum.fromChangeDirection(cdEnum);
        }

        BigDecimal amt = bo.getAmount() != null ? bo.getAmount() : BigDecimal.ZERO;
        BigDecimal debitAmt = (DebitCreditEnum.DEBIT == dcEnum) ? amt : BigDecimal.ZERO;
        BigDecimal creditAmt = (DebitCreditEnum.CREDIT == dcEnum) ? amt : BigDecimal.ZERO;

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
                .changeDirectionDesc(ChangeDirectionEnum.getDescByCode(bo.getChangeDirection()))
                .debitCredit(dcEnum.getCode())
                .debitCreditDesc(dcEnum.getDesc())
                .debitAmount(debitAmt)
                .creditAmount(creditAmt)
                .amount(amt)
                .voucherStatus(bo.getVoucherStatus())
                .voucherStatusDesc(VoucherStatusEnum.getDescByCode(bo.getVoucherStatus()))
                .tradeType(bo.getTradeType())
                .tradeTypeDesc(TradeTypeEnum.getDescByCode(bo.getTradeType()))
                .build();
    }
}
