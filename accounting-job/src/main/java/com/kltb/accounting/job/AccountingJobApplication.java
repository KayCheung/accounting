// accounting-job/src/main/java/com/kltb/accounting/job/AccountingJobApplication.java
package com.kltb.accounting.job;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * FIN-Core 定时任务模块启动类
 * <p>
 * 负责：缓冲入账 Job / 日切 EOD Job / 冻结超时 Job / 本地消息补偿 Job
 */
@SpringBootApplication
public class AccountingJobApplication {

    public static void main(String[] args) {
        SpringApplication.run(AccountingJobApplication.class, args);
    }
}
