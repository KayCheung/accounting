package com.kltb.accounting.core.infrastructure.account;

import com.kltb.accounting.core.domain.enums.BalanceDirectionEnum;
import com.kltb.accounting.core.domain.enums.CustomerTypeEnum;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RScript;
import org.redisson.api.RedissonClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class AccountNoGeneratorTest {

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private RScript rScript;

    private AccountNoGenerator generator;

    @BeforeEach
    void setUp() {
        lenient().when(redissonClient.getScript(any(org.redisson.client.codec.Codec.class))).thenReturn(rScript);
        lenient().when(rScript.eval(any(), anyString(), any(), anyList())).thenReturn(42L);
        generator = new AccountNoGenerator(redissonClient, "001");
    }

    @Test
    @DisplayName("generateByRule: 支持新变量 {accountType}-{currency}-{balanceDirection}-{ownerType}-{seq3}")
    void generateByRule_newVariables_shouldFormatCorrectly() {
        String rule = "{accountType}-{currency}-{balanceDirection}-{ownerType}-{seq3}";
        AccountRuleContext context = AccountRuleContext.builder()
                .businessCode("LOAN_24")
                .accountType("CASH")
                .currency("CNY")
                .balanceDirection(BalanceDirectionEnum.DEBIT)
                .customerType(CustomerTypeEnum.PERSONAL)
                .subjectCode("100101")
                .build();

        String result = generator.generateByRule(rule, context);

        // balanceDirection: 1, ownerType: 1, seq3: 042
        assertThat(result).isEqualTo("CASH-CNY-1-1-042");
    }

    @Test
    @DisplayName("generateByRule: 动态支持 {seq1}、{seq2}、{seq4}、{seq6} 等任意位数序号")
    void generateByRule_dynamicSeqLength_shouldFormatCorrectly() {
        AccountRuleContext context = AccountRuleContext.builder()
                .accountType("SETTLE")
                .build();

        // seq=42
        assertThat(generator.generateByRule("{accountType}-{seq1}", context)).isEqualTo("SETTLE-42");
        assertThat(generator.generateByRule("{accountType}-{seq2}", context)).isEqualTo("SETTLE-42");
        assertThat(generator.generateByRule("{accountType}-{seq3}", context)).isEqualTo("SETTLE-042");
        assertThat(generator.generateByRule("{accountType}-{seq4}", context)).isEqualTo("SETTLE-0042");
        assertThat(generator.generateByRule("{accountType}-{seq5}", context)).isEqualTo("SETTLE-00042");
        assertThat(generator.generateByRule("{accountType}-{seq6}", context)).isEqualTo("SETTLE-000042");
    }

    @Test
    @DisplayName("generateByRule: 支持任意变量指定截取长度如 {subjectCode4} 与 {accountType3}")
    void generateByRule_variableLengthTruncation_shouldTruncate() {
        String rule = "{subjectCode4}-{accountType3}-{seq3}";
        AccountRuleContext context = AccountRuleContext.builder()
                .subjectCode("10020101")
                .accountType("DEPOSIT")
                .build();

        String result = generator.generateByRule(rule, context);

        assertThat(result).isEqualTo("1002-DEP-042");
    }

    @Test
    @DisplayName("generateByRule: 不应解析 bizCode 和 ownerId（置为空）")
    void generateByRule_bizCodeAndOwnerId_shouldBeEmpty() {
        String rule = "{bizCode}{ownerId}{subjectCode}-{seq3}";
        AccountRuleContext context = AccountRuleContext.builder()
                .businessCode("LOAN_24")
                .ownerId("CUST001")
                .subjectCode("1001")
                .build();

        String result = generator.generateByRule(rule, context);

        // bizCode 和 ownerId 置空，剩下 1001-042
        assertThat(result).isEqualTo("1001-042");
    }

    @Test
    @DisplayName("generateByRule: 规则为空时降级为默认外部账号生成")
    void generateByRule_blankRule_shouldFallbackToDefault() {
        String result = generator.generateByRule("", "LOAN_24", "1001", "CUST88");

        assertThat(result).startsWith("001");
        assertThat(result).endsWith("00042");
    }

    @Test
    @DisplayName("generateByRule: 超过32位时自动安全截断")
    void generateByRule_over32Chars_shouldTruncateTo32() {
        String rule = "VERY_LONG_PREFIX_FOR_ACCOUNT_RULE_{seq8}_EXTRA_POSTFIX";
        AccountRuleContext context = AccountRuleContext.builder().build();

        String result = generator.generateByRule(rule, context);

        assertThat(result).hasSize(32);
    }

    @Test
    @DisplayName("generateAccountName: 正常规则替换 {accountTypeName}、{currencyName}、{directionName}、{ownerTypeName}、{ownerName}")
    void generateAccountName_normalRule_shouldReplaceVariables() {
        String rule = "{ownerName}-{accountTypeName}-{currencyName}-{directionName}";
        AccountRuleContext context = AccountRuleContext.builder()
                .ownerName("张三")
                .accountTypeName("现金账户")
                .currencyName("人民币")
                .directionName("借")
                .build();

        String name = generator.generateAccountName(rule, context);

        assertThat(name).isEqualTo("张三-现金账户-人民币-借");
    }

    @Test
    @DisplayName("generateAccountName: ownerName 为空时降级使用 ownerId")
    void generateAccountName_nullOwnerName_shouldFallbackToOwnerId() {
        String rule = "{ownerName}-{subjectName}";
        AccountRuleContext context = AccountRuleContext.builder()
                .ownerId("CUST001")
                .subjectName("利息账户")
                .build();

        String name = generator.generateAccountName(rule, context);

        assertThat(name).isEqualTo("CUST001-利息账户");
    }

    @Test
    @DisplayName("generateAccountName: 不应包含 bizCode，若传入 bizCode 占位符置空")
    void generateAccountName_bizCode_shouldBeEmpty() {
        String rule = "{bizCode}{ownerName}-{subjectName}";
        AccountRuleContext context = AccountRuleContext.builder()
                .businessCode("LOAN_24")
                .ownerName("李四")
                .subjectName("担保费")
                .build();

        String name = generator.generateAccountName(rule, context);

        assertThat(name).isEqualTo("李四-担保费");
    }

    @Test
    @DisplayName("generateAccountName: 支持名称变量指定长度如 {ownerName2}")
    void generateAccountName_lengthTruncation_shouldTruncate() {
        String rule = "{ownerName2}的{accountTypeName}";
        AccountRuleContext context = AccountRuleContext.builder()
                .ownerName("张三丰")
                .accountTypeName("基本户")
                .build();

        String name = generator.generateAccountName(rule, context);

        assertThat(name).isEqualTo("张三的基本户");
    }

    @Test
    @DisplayName("generateAccountName: 规则为空时降级拼接")
    void generateAccountName_blankRule_shouldFallback() {
        String name = generator.generateAccountName(null, "LOAN_24", "贷款本金", "张三", "CUST001");

        assertThat(name).isEqualTo("张三-贷款本金");
    }
}
