# 项目结构（Project Structure）

```text
accounting/                                                 # 主项目根目录
  ├── pom.xml                                               #主项目聚合pom.xml
  ├── accounting-api/                                       # API接口模块
  │   ├── pom.xml                                           # API模块pom.xml
  │   └── src/main/java/com/kltb/accounting/api/
  │       ├── request/                                      # API数据入参对象
  │       │   ├── PageRequest.java
  │       │   ├── VoucherRequest.java
  │       │   ├── EntryRequest.java
  │       ├── response/                                     # API数据出参对象
  │       │   ├── ApiResponse.java
  │       │   ├── PageResponse.java
  │       │   ├── VoucherResponse.java
  │       │   ├── EntryResponse.java
  │       ├── facade/                                       # 门面接口
  │       │   ├── AccountingFacade.java
  │       │   ├── VoucherFacade.java
  │       │   └── BalanceFacade.java
  │       └── enums/                                        # API层公共枚举
  │       │   ├── VoucherType.java
  │       │   ├── EntryDirection.java
  │       │   ├── AccountType.java
  │       └── constant/                                     # 常量定义
  │           ├── AccountingConstants.java
  │           └── ResultCode.java
  ├── accounting-core/                                      # 核心业务模块
  │   ├── pom.xml                                           # 核心模块pom.xml
  │   ├── src/main/java/com/kltb/accounting/core/
  │   │   ├── AccountingCoreApplication.java
  │   │   │
  │   │   ├── controller/                                   # # 接口层 REST API实现
  │   │   │   └── AccountingController.java
  │   │   │
  │   │   ├── application/                                  # 应用层 (Application Layer)
  │   │   │   ├── service/                                  # 应用服务
  │   │   │   │   ├── AccountingApplicationService.java
  │   │   │   │   ├── VoucherApplicationService.java
  │   │   │   │   ├── BalanceApplicationService.java
  │   │   │   │   └── ReconciliationApplicationService.java
  │   │   │   ├── assembler/                                # DTO与领域对象转换器
  │   │   │   │   └── VoucherAssembler.java
  │   │   │   └── event/                                    # 应用事件
  │   │   │       └── AccountingEvent.java
  │   │   ├── domain/                                       # 领域层 (Domain Layer)
  │   │   │   ├── model/                                    # 领域实体与聚合
  │   │   │   │   ├── aggregate/                            # 聚合根
  │   │   │   │   │   ├── VoucherAggregate.java
  │   │   │   │   │   ├── AccountAggregate.java
  │   │   │   │   │   └── SubjectAggregate.java
  │   │   │   │   ├── entity/                               # 领域实体
  │   │   │   │   │   ├── VoucherEntity.java
  │   │   │   │   │   ├── EntryEntity.java
  │   │   │   │   │   ├── AccountEntity.java
  │   │   │   │   │   ├── SubAccountEntity.java
  │   │   │   │   │   └── TransactionEntity.java
  │   │   │   │   └── valueobject/                          # 值对象（带VO后缀）
  │   │   │   │       ├── AmountVO.java
  │   │   │   │       ├── DirectionVO.java
  │   │   │   │       ├── BalanceVO.java
  │   │   │   │       ├── TraceInfoVO.java
  │   │   │   │       └── AccountingRuleVO.java
  │   │   │   ├── service/                                  # 领域服务
  │   │   │   │   ├── AccountingDomainService.java
  │   │   │   │   ├── BalanceCalculationService.java
  │   │   │   │   ├── VoucherValidationService.java
  │   │   │   │   └── ReconciliationService.java
  │   │   │   ├── repository/                               # 领域仓储接口
  │   │   │   │   ├── VoucherRepository.java
  │   │   │   │   ├── AccountRepository.java
  │   │   │   │   └── SubjectRepository.java
  │   │   │   ├── factory/                                  # 领域工厂
  │   │   │   │   └── VoucherFactory.java
  │   │   │   ├── strategy/                                 # 记账策略
  │   │   │   │   ├── AccountingStrategy.java
  │   │   │   │   ├── SingleEntryStrategy.java
  │   │   │   │   ├── DoubleEntryStrategy.java
  │   │   │   │   └── RedReverseStrategy.java
  │   │   │   └── enums/                                    # 领域枚举
  │   │   │       ├── VoucherType.java
  │   │   │       ├── EntryDirection.java
  │   │   │       ├── AccountType.java
  │   │   │       ├── BalanceDirection.java
  │   │   │       ├── AccountingStatus.java
  │   │   │       └── TransactionType.java
  │   │   │
  │   │   ├── infrastructure/                               # 基础设施层 (Infrastructure Layer)
  │   │   │   ├── persistence/                              # 持久化实现
  │   │   │   │   ├── entity/                               # 持久化实体
  │   │   │   │   │   ├── Voucher.java
  │   │   │   │   │   ├── Account.java
  │   │   │   │   │   └── Subject.java
  │   │   │   │   ├── mapper/                               # MyBatis映射接口
  │   │   │   │   │   ├── VoucherMapper.java
  │   │   │   │   │   ├── EntryMapper.java
  │   │   │   │   │   ├── AccountMapper.java
  │   │   │   │   │   └── SubjectMapper.java
  │   │   │   │   ├── converter/                            # 持久化对象转换器
  │   │   │   │   │   └── VoucherConverter.java
  │   │   │   │   └── repository/                           # 仓储实现
  │   │   │   │       ├── VoucherRepository.java
  │   │   │   │       ├── AccountRepository.java
  │   │   │   │       └── SubjectRepository.java
  │   │   │   ├── messaging/                                # 消息中间件实现
  │   │   │   │   └── RocketMQProducer.java
  │   │   │   ├── cache/                                    # 缓存实现
  │   │   │   │   └── RedisCacheService.java
  │   │   │   └── config/                                   # 配置类
  │   │   │       ├── TransactionConfig.java
  │   │   │       ├── MybatisPlusConfig.java
  │   │   │       └── NacosConfig.java
  │   │   │
  │   │   └── shared/                                        # 共享组件
  │   │       ├── exception/                                 # 统一异常处理
  │   │       │   ├── GlobalExceptionHandler.java
  │   │       │   ├── BusinessException.java
  │   │       │   └── BaseException.java
  │   │       ├── util/                                      # 工具类
  │   │       │   ├── AccountingUtils.java
  │   │       │   ├── BigDecimalUtil.java
  │   │       │   └── TraceIdUtil.java
  │   │
  │   ├── src/main/resources/
  │   │   ├── bootstrap.yml
  │   │   ├── mapper/                                       # MyBatis映射文件
  │   │   │   ├── VoucherMapper.xml
  │   │   │   ├── EntryMapper.xml
  │   │   │   ├── AccountMapper.xml
  │   │   │   └── SubjectMapper.xml
  │   │   └── logback-spring.xml
  │   └── src/test/java/                                     # 测试代码
  │       └── com/kltb/accounting/core/
  │           ├── service/
  │           │   └── AccountingServiceTest.java
  │           └── domain/
  │               └── BalanceCalculationTest.java
  ├── docs/                                                  # 设计文档
  │   ├── sql/                                               # 数据库脚本
  │   │   └── 1-init-schema.sql               
  │   ├── design/
  │   │   ├── flowchart/                                     # 流程图
  │   │   │   ├── system_architecture.mmd
  │   │   │   ├── accounting_flow.mmd
  │   │   │   └── end_of_day_process.mmd
  │   │   └── project_structure.md
  │   ├── fixed/                                             # 存放 AI 对docs目录下修复后的完整文件和修改建议文件
  │   └── prompt/                                            # 提示词
  ├── .kiro/steering                                         # 持续生效的规约
  ├── scripts/                                               # 脚本文件
  └── README.md
```
**【强制原则】**：
   - 这是一个多模块项目，所有生成的 Java 文件必须严格遵守上述对应的包（Package）路径。
   - **docs 目录下除了 `FIN-Core_Blueprint.md` 文件可以修改，其他所有文件为只读文件，禁止修改**
   - `docs/FIN-Core_Blueprint.md` 文件为任务执行索引文件，每执行完一个任务，更新对应的任务为已执行