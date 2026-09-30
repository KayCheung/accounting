package com.kltb.accounting.core.infrastructure.dictionary;

import com.kltb.accounting.api.constant.DictTypeEnum;
import com.kltb.accounting.core.domain.enums.AvailableStatusEnum;
import com.kltb.accounting.core.infrastructure.persistence.entity.DictionaryPO;
import com.kltb.accounting.core.infrastructure.persistence.repository.DictionaryRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * DictionaryComponent 单元测试
 */
@ExtendWith(MockitoExtension.class)
class DictionaryComponentTest {

    @Mock
    private DictionaryRepository dictionaryRepository;

    @InjectMocks
    private DictionaryComponent dictionaryComponent;

    @Test
    @DisplayName("isDictValid: 启用状态字典项返回 true，停用或不存在返回 false")
    void testIsDictValid() {
        DictionaryPO enabledDict = new DictionaryPO();
        enabledDict.setDictType(DictTypeEnum.CURRENCY.getCode());
        enabledDict.setDictCode("CNY");
        enabledDict.setStatus(AvailableStatusEnum.ENABLED);

        DictionaryPO disabledDict = new DictionaryPO();
        disabledDict.setDictType(DictTypeEnum.CURRENCY.getCode());
        disabledDict.setDictCode("USD");
        disabledDict.setStatus(AvailableStatusEnum.DISABLED);

        when(dictionaryRepository.selectByTypeAndCode(DictTypeEnum.CURRENCY.getCode(), "CNY")).thenReturn(enabledDict);
        when(dictionaryRepository.selectByTypeAndCode(DictTypeEnum.CURRENCY.getCode(), "USD")).thenReturn(disabledDict);
        when(dictionaryRepository.selectByTypeAndCode(DictTypeEnum.CURRENCY.getCode(), "JPY")).thenReturn(null);
        when(dictionaryRepository.selectByType(DictTypeEnum.CURRENCY.getCode())).thenReturn(List.of(enabledDict, disabledDict));

        assertThat(dictionaryComponent.isDictValid(DictTypeEnum.CURRENCY, "CNY")).isTrue();
        assertThat(dictionaryComponent.isDictValid(DictTypeEnum.CURRENCY, "USD")).isFalse();
        assertThat(dictionaryComponent.isDictValid(DictTypeEnum.CURRENCY, "JPY")).isFalse();
    }

    @Test
    @DisplayName("resolveCurrencyName: 字典命中返回名称，未命中返回币种原代码")
    void testResolveCurrencyName() {
        DictionaryPO cny = new DictionaryPO();
        cny.setDictType(DictTypeEnum.CURRENCY.getCode());
        cny.setDictCode("CNY");
        cny.setDictName("人民币");
        cny.setStatus(AvailableStatusEnum.ENABLED);

        when(dictionaryRepository.selectByTypeAndCode(DictTypeEnum.CURRENCY.getCode(), "CNY")).thenReturn(cny);
        when(dictionaryRepository.selectByTypeAndCode(DictTypeEnum.CURRENCY.getCode(), "EUR")).thenReturn(null);
        when(dictionaryRepository.selectByType(DictTypeEnum.CURRENCY.getCode())).thenReturn(List.of(cny));

        assertThat(dictionaryComponent.resolveCurrencyName("CNY")).isEqualTo("人民币");
        assertThat(dictionaryComponent.resolveCurrencyName("EUR")).isEqualTo("EUR");
        assertThat(dictionaryComponent.resolveCurrencyName("")).isEqualTo("");
    }

    @Test
    @DisplayName("getVoucherTypeMeta: 正确解析 ext_json 元数据与降级兜底")
    void testGetVoucherTypeMeta() {
        DictionaryPO receipt = new DictionaryPO();
        receipt.setDictType(DictTypeEnum.VOUCHER_TYPE.getCode());
        receipt.setDictCode("RECEIPT");
        receipt.setDictName("收款凭证");
        receipt.setExtJson("{\"title\":\"收款凭证\",\"prefix\":\"收\",\"tradeType\":1,\"tradingCode\":\"CASH_IN\",\"payChannel\":\"ALIPAY\"}");

        when(dictionaryRepository.selectByTypeAndCode(DictTypeEnum.VOUCHER_TYPE.getCode(), "RECEIPT")).thenReturn(receipt);

        VoucherTypeMeta meta = dictionaryComponent.getVoucherTypeMeta("RECEIPT");
        assertThat(meta.getTitle()).isEqualTo("收款凭证");
        assertThat(meta.getPrefix()).isEqualTo("收");
        assertThat(meta.getVoucherNoPrefix()).isEqualTo("REC");
        assertThat(meta.getTradingCode()).isEqualTo("CASH_IN");
        assertThat(meta.getPayChannel()).isEqualTo("ALIPAY");

        // 测试显式指定 voucherNoPrefix 的场景
        receipt.setExtJson("{\"title\":\"收款凭证\",\"prefix\":\"收\",\"voucherNoPrefix\":\"RCP\",\"tradeType\":1}");
        VoucherTypeMeta explicitMeta = dictionaryComponent.getVoucherTypeMeta("RECEIPT");
        assertThat(explicitMeta.getVoucherNoPrefix()).isEqualTo("RCP");

        // 测试降级兜底
        when(dictionaryRepository.selectByTypeAndCode(DictTypeEnum.VOUCHER_TYPE.getCode(), "UNKNOWN")).thenReturn(null);
        when(dictionaryRepository.selectByType(DictTypeEnum.VOUCHER_TYPE.getCode())).thenReturn(List.of(receipt));

        VoucherTypeMeta fallback = dictionaryComponent.getVoucherTypeMeta("UNKNOWN");
        assertThat(fallback.getTitle()).isEqualTo("UNKNOWN");
        assertThat(fallback.getPrefix()).isEqualTo("U");
        assertThat(fallback.getVoucherNoPrefix()).isEqualTo("VOU");

        // 测试各类型智能前缀推导
        assertThat(dictionaryComponent.getVoucherTypeMeta("付款凭证").getVoucherNoPrefix()).isEqualTo("PAY");
        assertThat(dictionaryComponent.getVoucherTypeMeta("TRANSFER").getVoucherNoPrefix()).isEqualTo("TRF");
        assertThat(dictionaryComponent.getVoucherTypeMeta("调账凭证").getVoucherNoPrefix()).isEqualTo("ADJ");
        assertThat(dictionaryComponent.getVoucherTypeMeta("REVERSAL").getVoucherNoPrefix()).isEqualTo("REV");
        assertThat(dictionaryComponent.getVoucherTypeMeta("期末结转凭证").getVoucherNoPrefix()).isEqualTo("PET");
    }
}
