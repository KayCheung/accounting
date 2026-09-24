// accounting-admin/src/main/java/com/kltb/accounting/admin/AccountingAdminApplication.java
package com.kltb.accounting.admin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * FIN-Core 管理后台 BFF 层启动类
 * <p>
 * 负责：字典管理 / 科目管理 / 规则管理 / 凭证查询 / 账户查询 / 余额查询等运营接口
 */
@SpringBootApplication
public class AccountingAdminApplication {

    public static void main(String[] args) {
        SpringApplication.run(AccountingAdminApplication.class, args);
    }
}
