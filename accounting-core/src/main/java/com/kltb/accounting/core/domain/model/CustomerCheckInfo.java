// accounting-core/src/main/java/com/kltb/accounting/core/domain/model/CustomerCheckInfo.java
package com.kltb.accounting.core.domain.model;

import com.kltb.accounting.core.domain.enums.CustomerTypeEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 预开户客户检查信息模型
 * <p>
 * 封装客户类型及客户名称，供预开户检查与自动开户户名生成使用。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerCheckInfo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 客户类型
     */
    private CustomerTypeEnum customerType;

    /**
     * 客户名称（用于开户户名规则生成）
     */
    private String customerName;
}
