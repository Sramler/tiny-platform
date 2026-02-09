#!/usr/bin/env bash
# 本地一键验证调度中心示例数据（不依赖 GitHub Actions）
# 流程：构建应用 → 启动应用（Liquibase + data.sql）→ 运行验证脚本 → 停止应用
# 使用：在仓库根目录执行；需已安装 JDK、Maven、mysql 客户端，且 MySQL 已启动并可连。
# 环境变量：SCHEDULING_VERIFY_DB_HOST / PORT / USER / PASSWORD / NAME（同 verify-scheduling-demo.sh）；
#          SCHEDULING_VERIFY_MYSQL_BIN 指定 mysql 客户端路径（如 /path/to/mysql/3306/bin/mysql）；
#          MYSQL_ROOT_PASSWORD 未设置时会使用 SCHEDULING_VERIFY_DB_PASSWORD（供应用 ci profile 连接）。

set -e

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"

# 与验证脚本一致的默认值（127.0.0.1 使 mysql 客户端走 TCP，与 ci 应用一致）
export SCHEDULING_VERIFY_DB_HOST="${SCHEDULING_VERIFY_DB_HOST:-127.0.0.1}"
export SCHEDULING_VERIFY_DB_PORT="${SCHEDULING_VERIFY_DB_PORT:-3306}"
export SCHEDULING_VERIFY_DB_USER="${SCHEDULING_VERIFY_DB_USER:-root}"
export SCHEDULING_VERIFY_DB_PASSWORD="${SCHEDULING_VERIFY_DB_PASSWORD:-}"
export SCHEDULING_VERIFY_DB_NAME="${SCHEDULING_VERIFY_DB_NAME:-tiny_web}"
# mysql 客户端路径（可选，未设置时使用 PATH 中的 mysql）
export SCHEDULING_VERIFY_MYSQL_BIN="${SCHEDULING_VERIFY_MYSQL_BIN:-}"
# 应用 ci profile 使用的密码（与验证脚本同一库）
export MYSQL_ROOT_PASSWORD="${MYSQL_ROOT_PASSWORD:-$SCHEDULING_VERIFY_DB_PASSWORD}"
export MYSQL_PORT="${MYSQL_PORT:-$SCHEDULING_VERIFY_DB_PORT}"

echo "=== 本地调度示例数据验证（MySQL: $SCHEDULING_VERIFY_DB_HOST:$SCHEDULING_VERIFY_DB_PORT/$SCHEDULING_VERIFY_DB_NAME）==="
if [[ -n "$SCHEDULING_VERIFY_MYSQL_BIN" ]]; then
  echo "mysql 客户端: $SCHEDULING_VERIFY_MYSQL_BIN"
fi
if [[ -z "$SCHEDULING_VERIFY_DB_PASSWORD" && -z "$MYSQL_ROOT_PASSWORD" ]]; then
  echo "提示: 若本地 MySQL 需要密码，请先 export SCHEDULING_VERIFY_DB_PASSWORD=你的密码"
fi
echo ""

# 启动应用并验证（使用 spring-boot:run 避免依赖 fat jar 打包）
echo "[1/2] 启动应用（执行 Liquibase + data.sql）..."
mvn -q -DskipTests spring-boot:run -pl tiny-oauth-server -Dspring-boot.run.profiles=ci &
APP_PID=$!
trap "kill $APP_PID 2>/dev/null || true; exit 1" INT TERM
trap "kill $APP_PID 2>/dev/null || true" EXIT

# 应用首次启动可能较慢（Liquibase + Camunda 等），最多等 6 分钟；以 9000 端口已监听为准（不依赖 actuator 权限）
for i in $(seq 1 180); do
  if (lsof -i:9000 -sTCP:LISTEN -t >/dev/null 2>&1) || (nc -z 127.0.0.1 9000 2>/dev/null); then
    echo "      应用已就绪 (端口 9000 已监听)."
    sleep 3
    break
  fi
  if ! kill -0 $APP_PID 2>/dev/null; then
    echo "FAIL: 应用进程提前退出"
    exit 1
  fi
  sleep 2
done
(lsof -i:9000 -sTCP:LISTEN -t >/dev/null 2>&1) || (nc -z 127.0.0.1 9000 2>/dev/null) || { echo "FAIL: 应用未在预期时间内就绪（已等约 6 分钟）"; exit 1; }

echo "[2/2] 运行验证脚本 ..."
./tiny-oauth-server/scripts/verify-scheduling-demo.sh

echo ""
echo "=== 本地验证完成 ==="
