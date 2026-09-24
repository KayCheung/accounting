# 项目结构

## 模块划分

```
accounting/
├── accounting-api/       # 纯净契约层（严禁引入持久层依赖）
├── accounting-core/      # 核心业务层（DDD 四层架构）
├── accounting-job/       # 定时任务（缓冲入账 / 日切 / 冻结超时）
├── accounting-admin/     # 管理后台 BFF 层
└── docs/                 # 设计文档（只读）
```

## accounting-api 包结构

```
com.kltb.accounting.api/
├── request/      # 入参 DTO
├── response/     # 出参 DTO（ApiResponse / PageResponse）
├── facade/       # Facade 接口定义
├── enums/        # 业务枚举
└── constant/     # 常量（ResultCode 等）
```

## accounting-core 包结构（DDD 四层）

```
com.kltb.accounting.core/
├── interfaces/          # Controller、MQ 监听器（仅参数校验和格式转换）
├── application/         # 应用服务（用例编排、事务边界）
│   ├── service/
│   └── assembler/       # DTO ↔ 领域对象转换
├── domain/              # 领域层（不依赖框架，不直接操作数据库）
│   ├── model/
│   │   ├── aggregate/   # 聚合根
│   │   ├── entity/      # 领域实体
│   │   └── valueobject/ # 值对象（VO 后缀）
│   ├── service/         # 领域服务
│   ├── repository/      # 仓储接口（domain 层定义）
│   ├── factory/
│   └── enums/
└── infrastructure/      # 基础设施层
    ├── persistence/
    │   ├── entity/      # PO（持久化对象，以 PO 后缀区分）
    │   ├── mapper/      # MyBatis-Plus Mapper
    │   ├── converter/   # PO ↔ 领域对象转换
    │   └── repository/  # 仓储实现
    ├── messaging/       # RocketMQ 生产者
    ├── cache/           # Redis / Caffeine 缓存
    └── config/          # Spring 配置类
```

## 关键约定

- 所有 Java 文件首行注释必须写完整包路径，便于定位
- PO 类命名以 `PO` 结尾，值对象以 `VO` 结尾，聚合根以 `Aggregate` 结尾
- Mapper XML 放在 `src/main/resources/mapper/` 下
- 测试代码镜像主代码包结构，放在 `src/test/java/`

## 只读文件规则

- `docs/` 目录下所有文件**只读，严禁修改**
- 唯一例外：`docs/prompt/FIN-Core_Blueprint.md`（进度锚点，每完成一个 Step 更新）

## 领域模型参考

- 完整表结构：`docs/design/domain-model.md`（27 张表，按 6 个域划分）
- 流程图：`docs/design/flowchart/`（入账、开户、冻结、EOD、红冲等）
- 执行进度：`docs/prompt/FIN-Core_Blueprint.md`
