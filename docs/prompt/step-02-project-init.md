# step-02-project-init · 待填充

> 本文件为Step 2工程从零初始化的详细文档，明确任务目标、执行标准及后续行动。

## 1. 任务目标（Mission）

完成工程从零初始化，搭建多模块Maven骨架，配置异常、返回体、日志、中间件等基础组件，验证工程启动及CI流水线正常，为后续开发提供稳定工程基础。

## 2. 详细子任务列表（含技术要点）

### 2.1 TL：用 Vibe Coding 生成多模块 Maven 工程骨架

- 基于项目需求，通过Vibe Coding生成符合规范的多模块Maven工程结构。

### 2.2 TL：配置统一异常体系

- 定义`ServiceException`异常类、`ResultCode`状态码、`GlobalExceptionHandler`全局异常处理器。

### 2.3 TL：配置统一返回体和分页结构

- 封装统一返回体`Result<T>`和分页结构`PageResult<T>`，规范接口输出格式。

### 2.4 TL：配置日志规范

- 配置日志输出规范，通过MDC注入`traceId`，实现链路追踪。

### 2.5 BE-A：配置 Flyway 及版本管理

- 配置Flyway组件，将`/docs/sql/all-tables.sql`纳入版本管理，实现数据库脚本自动化执行。

### 2.6 BE-A：配置 MyBatis-Plus 基础设施

- 配置MyBatis-Plus乐观锁、分页插件、逻辑删除功能，完善持久层基础配置。

### 2.7 TL：验证工程启动及CI流水线

- 验证工程本地启动无报错，`/actuator/health`接口返回200，确保CI流水线执行正常（绿色）。

## 3. 完成标准（Checklist）

- ✅ 多模块Maven工程骨架生成，结构合规。

- ✅ 统一异常体系、返回体、分页结构、日志规范配置完成。

- ✅ Flyway配置完成，`/docs/sql/all-tables.sql`纳入版本管理，建表成功。

- ✅ MyBatis-Plus基础设施配置完成，功能可用。

- ✅ 工程本地启动无报错，`/actuator/health`返回200，CI流水线绿色。

- ✅ 多模块工程PR合并完成。

## 4. 下一步行动

1. TL牵头，BE-A配合，梳理工程基础配置文档，同步至团队。

2. 全员熟悉工程结构及基础配置，明确开发规范。

3. 进入后续Step，基于初始化工程开展持久层、业务层开发。

4. 持续监控CI流水线状态，及时处理工程启动及配置相关问题。