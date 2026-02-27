# Step 4: Monitoring & Metrics (Prometheus & Micrometer)
请基于 Spring Boot Actuator 和 Micrometer 完善账务系统的监控体系，实现业务码关联与核心指标采集：

1. **增强原生指标 (Custom WebMvcTags)**：
    - 编写一个 `WebMvcTagsContributor` 的 Bean。
    - **逻辑**：从 `HttpServletRequest` 属性中提取名为 `biz_code` 的值。
    - **作用**：将其注入到 Spring 自带的 `http.server.requests` 指标标签中。

2. **业务状态捕获 (GlobalExceptionHandler 联动)**：
    - 修改 `GlobalExceptionHandler`：在捕获 `GenericException` 或其子类时，除了返回 `ApiResponse`，必须执行 `request.setAttribute("biz_code", e.getErrorCode())`。
    - **拦截器增强**：编写一个简单的 `MetricsTagInterceptor`，在 `preHandle` 或默认情况下将 `biz_code` 设置为 `"200"` 或 `"SUCCESS"`，确保所有正常请求也有标签。

3. **业务指标工具类 (BusinessMetricsCollector)**：
    - 创建工具类并注入 `MeterRegistry`，封装以下财务专项度量方法：
        - `recordPostingDuration(String tradeType, long millis)`：使用 `Timer` 记录过账耗时。
        - `recordAccountBalanceChange(String subjectCode, double amount)`：使用 `Summary` 记录科目金额变动分布。
        - `countAccountException(String type)`：使用 `Counter` 记录特定异常（如余额不足）。
        - `gaugeActiveTransactions()`：使用 `Gauge` 监控活跃事务数。
        - `recordTransactionAmount(String category, double amount)`：统计大额交易分布。
        - `countBalanceInconsistency()`：记录借贷不平衡事件（最高告警级别）。
        - `countOptimisticLockRetry(String accountNo)`：记录热点账户锁重试。
        - `recordRealTimeLag(long businessTime)`：使用 `Histogram` 记录业务端到端处理延迟。

4. **配置说明 (application.yml)**：
    - 开启 `prometheus` 端点：`management.endpoints.web.exposure.include=prometheus`。
    - 开启 Web 指标自动记录：`management.metrics.web.server.request.autotime.enabled=true`。
    - 开启百分位数直方图：`management.metrics.distribution.percentiles-histogram.http.server.requests=true`。

5. **系统健康检查 (HealthIndicator)**：
    - 自定义 `AccountingHealthIndicator` 继承 `AbstractHealthIndicator`。
    - **逻辑**：模拟检查核心账务配置、缓存连接或关键参数是否加载，并返回健康状态。