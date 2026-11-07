# 智能账务核心系统 (Smart Accounting Engine) Docker 一键部署套件

本项目已完成全栈核心依赖的原生升级，彻底移除了兼容过渡层与猴子补丁（Monkey Patch），全面基于现代化架构体系运行：
- **核心框架**：Spring Boot `3.3.5` + Spring Cloud `2023.0.3`
- **注册与配置中心**：Spring Cloud Alibaba `2025.0.0.0` 原生集成 `Nacos 3.x`（`nacos-client:3.0.3`）
- **持久层组件**：MyBatis-Plus `3.5.9` + `mybatis-plus-jsqlparser:3.5.9`
- **分布式锁与缓存**：Redisson `4.8.0`（全面适配 4.x Long 类型脚本返回）
- **分布式任务调度**：XXL-JOB `3.4.2`
- **API 规范**：SpringDoc OpenAPI `2.6.0`

---

## 目录结构说明

```
docker/
├── .env                         # 统一环境变量（端口、密码、版本标签）
├── docker-compose.yml           # 全组件一键编排定义
├── start.bat / stop.bat         # Windows 一键启停脚本
├── start.sh / stop.sh           # Linux / macOS 一键启停脚本
├── backend/
│   └── Dockerfile               # 后端 OpenJDK 21 JRE 容器镜像构建文件
├── frontend/
│   └── Dockerfile               # 前端 Nginx 静态托管与反向代理镜像构建文件
├── mysql/
│   ├── conf/my.cnf              # MySQL 8.0 性能与字符集 (utf8mb4) 配置文件
│   └── init/
│       ├── 01_init_databases.sql    # 创建 accounting, xxl_job, nacos 3 个数据库
│       ├── 02_accounting_schema.sql # V1~V23 全量账务表结构与初始字典数据
│       ├── 03_xxl_job.sql           # XXL-JOB 调度中心核心系统表
│       └── 04_nacos_mysql.sql       # Nacos 3.x 数据库表与预置 accounting-core.yml 配置
├── redis/
│   └── conf/redis.conf          # Redis 7.2 配置文件（含密码、AOF/RDB 持久化）
├── rocketmq/
│   └── conf/broker.conf         # RocketMQ 5.3.1 Broker 配置文件
└── nginx/
    └── nginx.conf               # 前端托管与 /accounting 反代后端配置
```

---

## 端口与默认访问账密汇总

| 服务名称 | 容器内端口 | 宿主机端口 | Web 控制台 / 访问地址 | 默认账号 / 密码 |
|---|---|---|---|---|
| **前端管理控制台** | 80 | **8000** | [http://localhost:8000](http://localhost:8000) | - |
| **后端 API (Swagger)** | 8080 | **8080** | [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html) | - |
| **Nacos 3.x 控制台** | 8848 | **8848** | [http://localhost:8848/nacos](http://localhost:8848/nacos) | `dev` / `dev@kltb@2026` |
| **RocketMQ 仪表盘** | 8080 | **8088** | [http://localhost:8088](http://localhost:8088) | 无 |
| **XXL-JOB 调度中心** | 8080 | **8082** | [http://localhost:8082/xxl-job-admin](http://localhost:8082/xxl-job-admin) | `admin` / `123456` |
| **MySQL 8.0** | 3306 | **3306** | `localhost:3306` | `root` / `dev@1234` |
| **Redis 7.2** | 6379 | **6379** | `localhost:6379` | `dev@1234` |
| **RocketMQ NameServer** | 9876 | **9876** | `localhost:9876` | - |
| **RocketMQ Broker** | 10911 | **10911** | `localhost:10911` | - |
| **XXL-JOB 执行器** | 9999 | **9999** | - | Token: `default_secret` |

---

## 一键部署步骤

### 1. 前置准备
- 确保宿主机已安装并启动 **Docker** 与 **Docker Compose**（Docker Desktop 或 Docker Engine 20.10+）。
- 确保端口 `8000`, `8080`, `8848`, `9848`, `9876`, `8088`, `8082`, `3306`, `6379` 未被其他本地程序占用。

### 2. 编译打包应用（如需重新打包）
根目录下执行：
```bash
# 后端编译打包（生成 Spring Boot 可执行 Fat Jar）
mvn clean package -DskipTests

# 前端构建打包（生成 accounting-ui/dist）
cd accounting-ui
npm run build
cd ..
```
*注：一键启动脚本会自动检测并按需编译，若已存在产物则跳过编译直接构建启动。*

### 3. 一键启动集群

#### Windows 环境：
双击运行或在 CMD/PowerShell 中执行：
```cmd
cd docker
start.bat
```

#### Linux / macOS 环境：
赋予权限后执行：
```bash
cd docker
chmod +x start.sh stop.sh
./start.sh
```

---

## 常用运维命令

- **查看集群运行状态**：
  ```bash
  docker compose ps
  ```
- **查看后端服务运行日志**：
  ```bash
  docker compose logs -f backend
  ```
- **查看 Nacos 服务日志**：
  ```bash
  docker compose logs -f nacos
  ```
- **安全停止所有服务**：
  ```bash
  # Windows:
  stop.bat
  # Linux:
  ./stop.sh
  ```
- **彻底清理数据库与数据卷（重置所有状态）**：
  ```bash
  docker compose down -v
  ```
