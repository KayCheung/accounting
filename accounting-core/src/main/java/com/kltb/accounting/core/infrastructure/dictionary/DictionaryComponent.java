package com.kltb.accounting.core.infrastructure.dictionary;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.kltb.accounting.api.constant.DictTypeEnum;
import com.kltb.accounting.core.domain.enums.AvailableStatusEnum;
import com.kltb.accounting.core.domain.enums.TradeTypeEnum;
import com.kltb.accounting.core.infrastructure.persistence.entity.DictionaryPO;
import com.kltb.accounting.core.infrastructure.persistence.repository.DictionaryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * 字典与扩展属性 (ext_json) 公共服务组件
 * <p>
 * 提供通用 ext_json 解析、类型安全提取、按编码/名称灵活反查字典，
 * 以及高频业务领域（如凭证类型 VoucherTypeMeta）元数据装配能力。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DictionaryComponent {

    private final DictionaryRepository dictionaryRepository;

    /**
     * 按字典类型与编码（或名称）反查字典PO
     *
     * @param dictType   字典类型
     * @param codeOrName 字典编码或字典名称
     * @return 字典PO，若未找到返回 null
     */
    public DictionaryPO findByTypeAndCodeOrName(String dictType, String codeOrName) {
        if (StrUtil.isBlank(dictType) || StrUtil.isBlank(codeOrName)) {
            return null;
        }

        // 1. 精确匹配 dictCode
        DictionaryPO dict = dictionaryRepository.selectByTypeAndCode(dictType, codeOrName);
        if (dict != null) {
            return dict;
        }

        // 2. 遍历该类型下所有字典项，按编码（忽略大小写）或名称匹配
        List<DictionaryPO> list = dictionaryRepository.selectByType(dictType);
        for (DictionaryPO po : list) {
            if (codeOrName.equalsIgnoreCase(po.getDictCode()) || codeOrName.equals(po.getDictName())) {
                return po;
            }
        }

        return null;
    }

    /**
     * 按系统字典类型枚举与编码（或名称）反查字典PO
     */
    public DictionaryPO findByTypeAndCodeOrName(DictTypeEnum dictType, String codeOrName) {
        return findByTypeAndCodeOrName(dictType != null ? dictType.getCode() : null, codeOrName);
    }

    /**
     * 解析字典项 ext_json 为 JSONObject
     */
    public JSONObject parseExtJson(DictionaryPO dict) {
        if (dict == null || StrUtil.isBlank(dict.getExtJson())) {
            return new JSONObject();
        }
        return parseExtJson(dict.getExtJson());
    }

    /**
     * 解析 JSON 字符串为 JSONObject（安全容错）
     */
    public JSONObject parseExtJson(String extJson) {
        if (StrUtil.isBlank(extJson)) {
            return new JSONObject();
        }
        try {
            return JSONUtil.parseObj(extJson);
        } catch (Exception e) {
            log.warn("[字典组件] 解析 ext_json 失败: json={}, error={}", extJson, e.getMessage());
            return new JSONObject();
        }
    }

    /**
     * 读取 ext_json 中的 String 属性
     */
    public String getExtString(DictionaryPO dict, String key, String defaultValue) {
        JSONObject json = parseExtJson(dict);
        return json.containsKey(key) ? json.getStr(key) : defaultValue;
    }

    /**
     * 读取 ext_json 中的 Integer 属性
     */
    public Integer getExtInteger(DictionaryPO dict, String key, Integer defaultValue) {
        JSONObject json = parseExtJson(dict);
        return json.containsKey(key) ? json.getInt(key) : defaultValue;
    }

    /**
     * 读取 ext_json 中的 BigDecimal 属性
     */
    public BigDecimal getExtBigDecimal(DictionaryPO dict, String key, BigDecimal defaultValue) {
        JSONObject json = parseExtJson(dict);
        return json.containsKey(key) ? json.getBigDecimal(key) : defaultValue;
    }

    /**
     * 读取 ext_json 中的 Boolean 属性
     */
    public Boolean getExtBoolean(DictionaryPO dict, String key, Boolean defaultValue) {
        JSONObject json = parseExtJson(dict);
        return json.containsKey(key) ? json.getBool(key) : defaultValue;
    }

    /**
     * 将 ext_json 反序列化为指定类型对象
     */
    public <T> T parseExtObject(DictionaryPO dict, Class<T> clazz) {
        if (dict == null || StrUtil.isBlank(dict.getExtJson())) {
            return null;
        }
        try {
            return JSONUtil.toBean(dict.getExtJson(), clazz);
        } catch (Exception e) {
            log.warn("[字典组件] 转换 ext_json 对象失败: dictCode={}, error={}", dict.getDictCode(), e.getMessage());
            return null;
        }
    }

    /**
     * 获取凭证类型的统一元数据对象（VoucherTypeMeta）
     * <p>
     * 自动从 t_dictionary(dict_type='voucher_type') 中检索对应字典项并提取 ext_json 元数据。
     * 若未检索到字典项，提供优雅降级兜底，保障系统永不中断。
     *
     * @param voucherTypeCodeOrName 凭证类型编码或中文名称（如 RECEIPT, 收款凭证, 转账凭证, ADJUST 等）
     * @return 凭证类型元数据对象
     */
    public VoucherTypeMeta getVoucherTypeMeta(String voucherTypeCodeOrName) {
        DictionaryPO dict = findByTypeAndCodeOrName(DictTypeEnum.VOUCHER_TYPE, voucherTypeCodeOrName);

        if (dict != null) {
            JSONObject ext = parseExtJson(dict);
            String title = ext.containsKey("title") ? ext.getStr("title") : dict.getDictName();
            String prefix = ext.containsKey("prefix") ? ext.getStr("prefix") : derivePrefixFromName(dict.getDictName());
            Integer tradeType = ext.containsKey("tradeType") ? ext.getInt("tradeType") : TradeTypeEnum.NORMAL.getCode();
            String tradingCode = ext.containsKey("tradingCode") ? ext.getStr("tradingCode") : "TRANSFER";
            String payChannel = ext.containsKey("payChannel") ? ext.getStr("payChannel") : "INTERNAL";

            String voucherNoPrefix = ext.containsKey("voucherNoPrefix") && StrUtil.isNotBlank(ext.getStr("voucherNoPrefix"))
                    ? ext.getStr("voucherNoPrefix").trim().toUpperCase()
                    : deriveVoucherNoPrefix(dict.getDictCode(), dict.getDictName());

            return VoucherTypeMeta.builder()
                    .dictCode(dict.getDictCode())
                    .dictName(dict.getDictName())
                    .title(StrUtil.isNotBlank(title) ? title : "记账凭证")
                    .prefix(StrUtil.isNotBlank(prefix) ? prefix : "记")
                    .voucherNoPrefix(voucherNoPrefix)
                    .tradeType(tradeType)
                    .tradingCode(tradingCode)
                    .payChannel(payChannel)
                    .build();
        }

        // 优雅降级兜底：未匹配到字典项时根据入参推导
        String rawName = StrUtil.isNotBlank(voucherTypeCodeOrName) ? voucherTypeCodeOrName : "记账凭证";
        String derivedPrefix = derivePrefixFromName(rawName);
        String derivedVoucherNoPrefix = deriveVoucherNoPrefix(rawName, rawName);

        return VoucherTypeMeta.builder()
                .dictCode(rawName)
                .dictName(rawName)
                .title(rawName)
                .prefix(derivedPrefix)
                .voucherNoPrefix(derivedVoucherNoPrefix)
                .tradeType(TradeTypeEnum.NORMAL.getCode())
                .tradingCode("TRANSFER")
                .payChannel("INTERNAL")
                .build();
    }

    private String derivePrefixFromName(String name) {
        if (StrUtil.isBlank(name)) {
            return "记";
        }
        // 默认取名称首字，如“收款凭证”->“收”，“调账凭证”->“调”
        return name.substring(0, 1);
    }

    /**
     * 根据凭证类型编码或名称智能推导凭证流水号前缀（英文缩写）
     * 规则：
     * 收款凭证 (RECEIPT) -> REC
     * 付款凭证 (PAYMENT) -> PAY
     * 转账凭证 (TRANSFER) -> TRF
     * 调账凭证 (ADJUST) -> ADJ
     * 冲账凭证 (REVERSAL) -> REV
     * 期末结转凭证 (PERIOD_END) -> PET
     * 记账凭证/其它 -> VOU
     */
    private String deriveVoucherNoPrefix(String code, String name) {
        String upperCode = StrUtil.isNotBlank(code) ? code.toUpperCase() : "";
        String safeName = StrUtil.isNotBlank(name) ? name : "";

        if (upperCode.contains("RECEIPT") || safeName.contains("收款")) {
            return "REC";
        }
        if (upperCode.contains("PAYMENT") || upperCode.contains("PAY") || safeName.contains("付款")) {
            return "PAY";
        }
        if (upperCode.contains("TRANSFER") || safeName.contains("转账") || safeName.contains("转帐")) {
            return "TRF";
        }
        if (upperCode.contains("ADJUST") || safeName.contains("调账") || safeName.contains("调整")) {
            return "ADJ";
        }
        if (upperCode.contains("REVERSAL") || safeName.contains("冲账") || safeName.contains("红冲") || safeName.contains("冲销")) {
            return "REV";
        }
        if (upperCode.contains("PERIOD_END") || safeName.contains("结转") || safeName.contains("结账")) {
            return "PET";
        }
        return "VOU";
    }

    /**
     * 校验指定类型的字典项是否存在且处于启用状态
     *
     * @param dictType 字典分类
     * @param dictCode 字典编码或名称
     * @return true-合法启用；false-不存在或已禁用
     */
    public boolean isDictValid(DictTypeEnum dictType, String dictCode) {
        DictionaryPO po = findByTypeAndCodeOrName(dictType, dictCode);
        return po != null && po.getStatus() == AvailableStatusEnum.ENABLED;
    }

    /**
     * 校验指定类型的字典项是否存在且处于启用状态（字符串入参）
     *
     * @param dictType 字典分类字符串
     * @param dictCode 字典编码或名称
     * @return true-合法启用；false-不存在或已禁用
     */
    public boolean isDictValid(String dictType, String dictCode) {
        DictionaryPO po = findByTypeAndCodeOrName(dictType, dictCode);
        return po != null && po.getStatus() == AvailableStatusEnum.ENABLED;
    }

    /**
     * 解析币种中文名称（优先从字典表 t_dictionary 查询 dict_name）
     *
     * @param currencyCode 币种代码（如 CNY, USD, EUR, HKD）
     * @return 币种中文名称（如 人民币、美元 等）；若未查到字典或未定义则优雅返回原币种代码
     */
    public String resolveCurrencyName(String currencyCode) {
        if (StrUtil.isBlank(currencyCode)) {
            return "";
        }
        DictionaryPO po = findByTypeAndCodeOrName(DictTypeEnum.CURRENCY, currencyCode);
        return po != null && StrUtil.isNotBlank(po.getDictName()) ? po.getDictName() : currencyCode;
    }
}
