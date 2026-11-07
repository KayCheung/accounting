#!/usr/bin/env bash
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "=============================================================================="
echo "[Smart Accounting Engine] 容器化环境一键停止脚本 (Linux/macOS)"
echo "=============================================================================="

docker compose down

echo "[SUCCESS] 所有服务容器已安全停止！"
echo "如需彻底清理数据库与数据卷，请手动执行: docker compose down -v"
