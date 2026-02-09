#!/usr/bin/env bash
# 调度中心示例数据自动化验证脚本
# 用途：校验 data.sql 中的调度示例数据是否已正确写入数据库（任务类型 → 任务 → DAG → 版本 → 节点 → 边 → 运行 → 实例 → 历史）
# 使用：在项目根目录或 tiny-oauth-server 目录执行；可通过环境变量覆盖数据库连接与 mysql 路径（见下方）。
# 依赖：mysql 客户端可用（可用 SCHEDULING_VERIFY_MYSQL_BIN 指定路径），且已执行过 Liquibase（含 data.sql）。

set -e

# MySQL 客户端路径（未设置时使用 PATH 中的 mysql）
MYSQL_BIN="${SCHEDULING_VERIFY_MYSQL_BIN:-mysql}"

# 数据库连接（可通过环境变量覆盖，不硬编码密码；默认 127.0.0.1 避免 mysql 客户端走 socket 导致连不上）
SCHEDULING_VERIFY_DB_HOST="${SCHEDULING_VERIFY_DB_HOST:-127.0.0.1}"
SCHEDULING_VERIFY_DB_PORT="${SCHEDULING_VERIFY_DB_PORT:-3306}"
SCHEDULING_VERIFY_DB_USER="${SCHEDULING_VERIFY_DB_USER:-root}"
SCHEDULING_VERIFY_DB_PASSWORD="${SCHEDULING_VERIFY_DB_PASSWORD:-}"
SCHEDULING_VERIFY_DB_NAME="${SCHEDULING_VERIFY_DB_NAME:-tiny_web}"

# 进入脚本所在目录的上级（tiny-oauth-server），便于相对路径一致
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR/.."

MYSQL_CMD=("$MYSQL_BIN" -h "$SCHEDULING_VERIFY_DB_HOST" -P "$SCHEDULING_VERIFY_DB_PORT" -u "$SCHEDULING_VERIFY_DB_USER" "$SCHEDULING_VERIFY_DB_NAME" -N -s)
if [[ -n "$SCHEDULING_VERIFY_DB_PASSWORD" ]]; then
  export MYSQL_PWD="$SCHEDULING_VERIFY_DB_PASSWORD"
fi

echo "=== 调度中心示例数据验证 ==="
echo "mysql: $MYSQL_BIN"
echo "数据库: $SCHEDULING_VERIFY_DB_HOST:$SCHEDULING_VERIFY_DB_PORT/$SCHEDULING_VERIFY_DB_NAME"
echo ""

fail() {
  echo "FAIL: $1"
  exit 1
}

# 0) 连接与表存在性检查（区分“连接失败”与“表不存在”）
MYSQL_ERR=$(mktemp)
trap 'rm -f "$MYSQL_ERR"' EXIT
if ! "${MYSQL_CMD[@]}" -e "SELECT 1;" 2>"$MYSQL_ERR"; then
  echo "FAIL: 无法连接数据库（$SCHEDULING_VERIFY_DB_HOST:$SCHEDULING_VERIFY_DB_PORT，用户 $SCHEDULING_VERIFY_DB_USER，库 $SCHEDULING_VERIFY_DB_NAME）"
  [[ -s "$MYSQL_ERR" ]] && echo "mysql 错误输出:" && cat "$MYSQL_ERR"
  exit 1
fi
tbl_count=$( "${MYSQL_CMD[@]}" -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = '$SCHEDULING_VERIFY_DB_NAME' AND table_name = 'scheduling_task_type';" 2>/dev/null || echo "0" )
if [[ "${tbl_count:-0}" -eq 0 ]]; then
  fail "表 scheduling_task_type 不存在（请先执行 Liquibase 或 data.sql 中的调度示例数据）"
fi

# 1) 任务类型：存在 DEMO_SHELL
n=$( "${MYSQL_CMD[@]}" -e "SELECT COUNT(*) FROM scheduling_task_type WHERE code = 'DEMO_SHELL';" 2>/dev/null )
[[ "${n:-0}" -ge 1 ]] || fail "scheduling_task_type 中未找到 code=DEMO_SHELL 的示例数据"
echo "  [OK] 任务类型: DEMO_SHELL 存在"

# 2) 任务：存在 demo_task_a、demo_task_b
n=$( "${MYSQL_CMD[@]}" -e "SELECT COUNT(*) FROM scheduling_task WHERE code IN ('demo_task_a','demo_task_b');" 2>/dev/null )
[[ "${n:-0}" -eq 2 ]] || fail "scheduling_task 中应存在 2 条示例任务（demo_task_a, demo_task_b），当前: $n"
echo "  [OK] 任务: demo_task_a, demo_task_b 共 2 条"

# 3) DAG：存在 demo_dag
n=$( "${MYSQL_CMD[@]}" -e "SELECT COUNT(*) FROM scheduling_dag WHERE code = 'demo_dag';" 2>/dev/null )
[[ "${n:-0}" -ge 1 ]] || fail "scheduling_dag 中未找到 code=demo_dag 的示例数据"
echo "  [OK] DAG: demo_dag 存在"

# 4) DAG 版本：该 DAG 存在 ACTIVE 版本
n=$( "${MYSQL_CMD[@]}" -e "SELECT COUNT(*) FROM scheduling_dag_version v JOIN scheduling_dag d ON d.id = v.dag_id WHERE d.code = 'demo_dag' AND v.status = 'ACTIVE';" 2>/dev/null )
[[ "${n:-0}" -ge 1 ]] || fail "scheduling_dag_version 中未找到 demo_dag 的 ACTIVE 版本"
echo "  [OK] DAG 版本: demo_dag 存在 ACTIVE 版本"

# 5) DAG 节点：至少 2 个节点
n=$( "${MYSQL_CMD[@]}" -e "SELECT COUNT(*) FROM scheduling_dag_task t JOIN scheduling_dag_version v ON v.id = t.dag_version_id JOIN scheduling_dag d ON d.id = v.dag_id WHERE d.code = 'demo_dag';" 2>/dev/null )
[[ "${n:-0}" -ge 2 ]] || fail "scheduling_dag_task 中 demo_dag 应至少 2 个节点，当前: $n"
echo "  [OK] DAG 节点: 至少 2 个"

# 6) DAG 边：至少 1 条边
n=$( "${MYSQL_CMD[@]}" -e "SELECT COUNT(*) FROM scheduling_dag_edge e JOIN scheduling_dag_version v ON v.id = e.dag_version_id JOIN scheduling_dag d ON d.id = v.dag_id WHERE d.code = 'demo_dag';" 2>/dev/null )
[[ "${n:-0}" -ge 1 ]] || fail "scheduling_dag_edge 中 demo_dag 应至少 1 条边，当前: $n"
echo "  [OK] DAG 边: 至少 1 条"

# 7) DAG 运行：存在 RUN-DEMO-001
n=$( "${MYSQL_CMD[@]}" -e "SELECT COUNT(*) FROM scheduling_dag_run WHERE run_no = 'RUN-DEMO-001';" 2>/dev/null )
[[ "${n:-0}" -ge 1 ]] || fail "scheduling_dag_run 中未找到 run_no=RUN-DEMO-001 的示例运行"
echo "  [OK] DAG 运行: RUN-DEMO-001 存在"

# 8) 任务实例：该运行下至少 2 条实例
n=$( "${MYSQL_CMD[@]}" -e "SELECT COUNT(*) FROM scheduling_task_instance WHERE dag_run_id = (SELECT id FROM scheduling_dag_run WHERE run_no = 'RUN-DEMO-001' LIMIT 1);" 2>/dev/null )
[[ "${n:-0}" -ge 2 ]] || fail "scheduling_task_instance 中 RUN-DEMO-001 应至少 2 条节点实例，当前: $n"
echo "  [OK] 任务实例: RUN-DEMO-001 下至少 2 条"

# 9) 任务历史：至少 2 条
n=$( "${MYSQL_CMD[@]}" -e "SELECT COUNT(*) FROM scheduling_task_history WHERE dag_run_id = (SELECT id FROM scheduling_dag_run WHERE run_no = 'RUN-DEMO-001' LIMIT 1);" 2>/dev/null )
[[ "${n:-0}" -ge 2 ]] || fail "scheduling_task_history 中 RUN-DEMO-001 应至少 2 条，当前: $n"
echo "  [OK] 任务历史: 至少 2 条"

unset MYSQL_PWD 2>/dev/null || true
echo ""
echo "=== 全部检查通过：调度示例数据完整，前端可正常查看 DAG 管理 / 任务类型 / 任务管理 / 运行历史 ==="
