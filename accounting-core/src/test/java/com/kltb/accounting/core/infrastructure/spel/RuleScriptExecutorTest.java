package com.kltb.accounting.core.infrastructure.spel;

import com.kltb.accounting.core.infrastructure.persistence.entity.BusinessDetailPO;
import com.kltb.accounting.core.infrastructure.persistence.entity.BusinessRecordPO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RuleScriptExecutor 单元测试
 */
class RuleScriptExecutorTest {

    private RuleScriptExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new RuleScriptExecutor();
    }

    @Test
    @DisplayName("普通分录金额 SpEL 计算: #root.amount * 0.1")
    void execute_basicAmount_shouldCalculateCorrectly() {
        BusinessDetailPO detail = new BusinessDetailPO();
        detail.setAmount(new BigDecimal("1000.00"));

        JournalSpelContext context = JournalSpelContext.of(detail, null);

        BigDecimal result = executor.execute("#root.amount * 0.1", context);
        assertThat(result).isEqualByComparingTo(new BigDecimal("100.00"));
    }

    @Test
    @DisplayName("主单扩展属性 SpEL 计算: #extra['rate'] * #amount")
    void execute_extraAttrs_shouldCalculateCorrectly() {
        BusinessDetailPO detail = new BusinessDetailPO();
        detail.setAmount(new BigDecimal("1000.00"));

        BusinessRecordPO journal = new BusinessRecordPO();
        journal.setExtraAttrs("{\"rate\":0.08}");

        JournalSpelContext context = JournalSpelContext.of(detail, journal);

        BigDecimal result = executor.execute("#extra['rate'] * #amount", context);
        assertThat(result).isEqualByComparingTo(new BigDecimal("80.00"));
    }

    @Test
    @DisplayName("细项扩展属性 SpEL 计算: #amount - #detailExtra['discount']")
    void execute_detailExtraAttrs_shouldCalculateCorrectly() {
        BusinessDetailPO detail = new BusinessDetailPO();
        detail.setAmount(new BigDecimal("1000.00"));
        detail.setExtraAttrs("{\"discount\":50}");

        JournalSpelContext context = JournalSpelContext.of(detail, null);

        BigDecimal result = executor.execute("#amount - #detailExtra['discount']", context);
        assertThat(result).isEqualByComparingTo(new BigDecimal("950.00"));
    }

    @Test
    @DisplayName("动态核算编码模板解析: #{#extra['partnerCode']}")
    void executeTemplate_shouldResolvePartnerCode() {
        BusinessRecordPO journal = new BusinessRecordPO();
        journal.setExtraAttrs("{\"partnerCode\":\"PARTNER_ICBC_99\"}");

        JournalSpelContext context = JournalSpelContext.of(null, journal);

        String result = executor.executeTemplate("#{#extra['partnerCode']}", context);
        assertThat(result).isEqualTo("PARTNER_ICBC_99");
    }

    @Test
    @DisplayName("静态编码模板解析: 保持原字符串不变")
    void executeTemplate_staticCode_shouldReturnOrigin() {
        JournalSpelContext context = new JournalSpelContext();
        String result = executor.executeTemplate("DEPT_001", context);
        assertThat(result).isEqualTo("DEPT_001");
    }
}
