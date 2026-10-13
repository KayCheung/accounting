#!/usr/bin/env bash
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(dirname "$SCRIPT_DIR")"

echo "=============================================================================="
echo "[Smart Accounting Engine] 容器化环境一键启动脚本 (Linux/macOS)"
echo "=============================================================================="

# 1. 检查后端构建产物
JAR_PATH="$ROOT_DIR/accounting-core/target/accounting-core-1.0.0-SNAPSHOT.jar"
if [ ! -f "$JAR_PATH" ]; then
    echo "[INFO] 未检测到后端 Jar 包，正在执行 Maven 打包..."
    cd "$ROOT_DIR"
    mvn clean package -DskipTests
    cd "$SCRIPT_DIR"
    if [ ! -f "$JAR_PATH" ]; then
        echo "[ERROR] Maven 打包失败，请检查编译环境！"
        exit 1
    fi
fi

# 2. 检查前端构建产物
if [ ! -f "$ROOT_DIR/accounting-ui/dist/index.html" ]; then
    echo "[WARN] 未检测到前端 dist 产物，正在尝试自动打包前端..."
    cd "$ROOT_DIR/accounting-ui"
    npm run build || true
    cd "$SCRIPT_DIR"
fi

# 3. 启动容器编排
cd "$SCRIPT_DIR"
echo "[INFO] 正在拉取镜像并启动容器集群..."
docker compose pull mysql redis nacos rocketmq-namesrv rocketmq-broker rocketmq-dashboard xxl-job-admin || true
docker compose up -d --build

echo ""
echo "=============================================================================="
echo "[SUCCESS] 智能账务核心系统集群启动完成！"
echo "=============================================================================="
echo "1. 前端管理控制台:   http://localhost:8000"
echo "2. 后端 Swagger 文档: http://localhost:8080/swagger-ui.html"
echo "3. Nacos 3.x 控制台:  http://localhost:8848/nacos (账号: dev / 密码: dev@kltb@2026)"
echo "4. RocketMQ 仪表板:   http://localhost:8088"
echo "5. XXL-JOB 调度中心:  http://localhost:8082/xxl-job-admin (账号: admin / 密码: 123456)"
echo "6. MySQL 数据库:      localhost:3306 (账号: root / 密码: dev@1234)"
echo "7. Redis 缓存服务:    localhost:6379 (密码: dev@1234)"
echo "=============================================================================="
echo "查看运行日志: docker compose logs -f backend"
echo "停止所有服务: ./stop.sh"
echo "=============================================================================="
