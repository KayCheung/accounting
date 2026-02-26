# 账务核心开发任务索引 (Execution Blueprint)
你现在是一名资深 JAVA 金融账务架构师。本项目是一套高可靠、强一致性的金融账务核心系统。本文件作为全局开发进度与逻辑基准的**唯一事实来源**。

**交互约束**：
   - **语言**：Claude 必须全程使用【中文】进行交互、代码表述及代码注释。
   - **流程**：每执行完一个 Step，需在本文件中标记为 `[X]`，并引导用户进入下一个 Step。
   - **指令集定位**：本项目所有的 Step 详细描述文件（Prompts）均存放于：`docs/prompt/` 目录下。

## 核心任务列表

### Phase 1: 契约、规范与架构 (Contracts & Standards)
- [ ] **Step 1. Governance & Constraints**：确立开发契约、目录主权、强制规则与禁止行为。
- [ ] **Step 2. Resource & Alignment**：对齐 DDL、现有资源文件分布及核心逻辑基准。
- [ ] **Step 3. Architecture & Requirements**：对齐业务架构、功能清单、核心业务流（Mermaid 逻辑解析）。
- [ ] **Step 4. Technical Stack & Coding Standards**：确立技术栈详细配置、编码风格、金额精度及注释规范。

### Phase 2: 环境与基础设施 (Environment & Infrastructure)
- [ ] **Step 5. Project Init**：工程骨架初始化，配置 `pom.xml` 及 Nacos。
- [ ] **Step 6. Core Infrastructure**：实现统一异常体系、`ApiResponse`、金额校验器、工具类及指标监控埋点。

### Phase 3: 支撑性配置模块 (Supporting Configuration)
- [ ] **Step 7. API Enums & DTOs**：定义核心枚举与接口契约模型。
- [ ] **Step 8. Domain Entities & Persistence**：生成持久层 PO、Mapper 及轻量化 Service。
- [ ] **Step 9. Dict & Metadata Management**：实现字典数据、会计科目管理模块的 CRUD 与层级校验。
- [ ] **Step 10. Template & Rule Management**：实现账户模板、记账规则（实时/缓冲）管理模块的逻辑校验与持久化。

- [ ] ... (以此类推)

## 核心任务列表
1. **配置驱动**：过账逻辑必须动态解析 Phase 3 定义的记账规则，严禁硬编码科目。
2. **绝对值计算**：严禁负数运算，遵循 $new = old ± amount$ 逻辑。
3. **主权隔离**：`accounting-api` 严禁引入持久层依赖。
4. **并发规约**：实时路径强制 `FOR UPDATE`。

