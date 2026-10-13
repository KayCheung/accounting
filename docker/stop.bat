@echo off
chcp 65001 > nul
echo ==============================================================================
echo [Smart Accounting Engine] 容器化环境一键停止脚本
echo ==============================================================================

cd /d "%~dp0"

echo [INFO] 正在停止并移除所有容器...
docker compose down

echo [SUCCESS] 所有服务容器已安全停止！
echo 如需彻底清理数据库与数据卷，请手动执行: docker compose down -v
pause
