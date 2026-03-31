#!/usr/bin/env bash
# 本地一键验证 tiny-platform dev stack：
#   1) 通过 verify-platform-dev-bootstrap.sh 自愈并验证 DB + oauth-server
#   2) 如前端未运行，则自愈启动 Vite dev server
#   3) 对前后端做最小健康检查
#
# 用法（仓库根目录）:
#   bash tiny-oauth-server/scripts/verify-platform-local-dev-stack.sh
#
# 退出码:
#   0 — 全部通过
#   1 — 前置满足，但启动或验证失败
#   2 — 环境前置未满足（例如 DB_PASSWORD / npm 缺失）
#
# 说明：
# - 数据库 / 后端前置由 verify-platform-dev-bootstrap.sh 负责；其支持 `DB_*` 与兼容别名 `E2E_DB_*`，exit 2 为环境缺口，不得误判成代码失败。
# - 若本脚本自动拉起前端，会在退出时清理本次启动的进程。
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "${ROOT_DIR}"

FRONTEND_PORT="${FRONTEND_PORT:-5173}"
FRONTEND_WAIT_SEC="${FRONTEND_WAIT_SEC:-120}"
FRONTEND_HEALTH_URL="${FRONTEND_HEALTH_URL:-http://127.0.0.1:${FRONTEND_PORT}/login}"
FRONTEND_START_CMD="${FRONTEND_START_CMD:-npm --prefix ${ROOT_DIR}/tiny-oauth-server/src/main/webapp run dev -- --host 127.0.0.1 --port ${FRONTEND_PORT}}"
SKIP_FRONTEND_START="${SKIP_FRONTEND_START:-0}"
FORCE_START_FRONTEND="${FORCE_START_FRONTEND:-0}"

FRONTEND_LOG=""
SCRIPT_STARTED_FRONTEND=0

frontend_ok() {
  curl -sf "${FRONTEND_HEALTH_URL}" >/dev/null 2>&1
}

wait_for_frontend() {
  local deadline=$((SECONDS + FRONTEND_WAIT_SEC))
  echo "==> Waiting for frontend ${FRONTEND_HEALTH_URL} (max ${FRONTEND_WAIT_SEC}s)"
  while (( SECONDS < deadline )); do
    if frontend_ok; then
      echo "==> Frontend HTTP ready"
      return 0
    fi
    sleep 2
  done
  echo "ERROR: frontend did not become ready. See log: ${FRONTEND_LOG}" >&2
  if [[ -n "${FRONTEND_LOG}" && -s "${FRONTEND_LOG}" ]]; then
    tail -n 80 "${FRONTEND_LOG}" >&2 || true
  fi
  return 1
}

cleanup_frontend() {
  if [[ "${SCRIPT_STARTED_FRONTEND}" != "1" ]]; then
    return 0
  fi
  if [[ -n "${FRONTEND_PID:-}" ]] && kill -0 "${FRONTEND_PID}" 2>/dev/null; then
    kill "${FRONTEND_PID}" 2>/dev/null || true
    wait "${FRONTEND_PID}" 2>/dev/null || true
  fi
  if command -v lsof >/dev/null 2>&1; then
    local p
    for _ in 1 2; do
      for p in $(lsof -tiTCP:"${FRONTEND_PORT}" -sTCP:LISTEN 2>/dev/null || true); do
        kill "${p}" 2>/dev/null || true
      done
      sleep 1
    done
  fi
}

trap cleanup_frontend EXIT INT TERM

echo "==> Step 0: verify backend/dev bootstrap"
bash "${ROOT_DIR}/tiny-oauth-server/scripts/verify-platform-dev-bootstrap.sh"

if ! command -v npm >/dev/null 2>&1; then
  echo "verify-platform-local-dev-stack: npm 不可用 → exit 2（环境前置未满足）" >&2
  exit 2
fi

if [[ "${SKIP_FRONTEND_START}" == "1" ]]; then
  echo "==> Step 1: SKIP_FRONTEND_START=1，不启动前端"
  if ! frontend_ok; then
    echo "ERROR: frontend not ready at ${FRONTEND_HEALTH_URL}" >&2
    exit 1
  fi
elif [[ "${FORCE_START_FRONTEND}" == "1" ]]; then
  if frontend_ok; then
    echo "ERROR: FORCE_START_FRONTEND=1 但 ${FRONTEND_HEALTH_URL} 已可用；请先释放端口或取消 FORCE。" >&2
    exit 1
  fi
  if command -v lsof >/dev/null 2>&1 && lsof -iTCP:"${FRONTEND_PORT}" -sTCP:LISTEN -n -P >/dev/null 2>&1; then
    echo "ERROR: port ${FRONTEND_PORT} 已被占用且无法识别为前端健康页 ${FRONTEND_HEALTH_URL}。" >&2
    exit 1
  fi
  FRONTEND_LOG="$(mktemp "${TMPDIR:-/tmp}/tiny-platform-frontend-dev.XXXXXX.log")"
  SCRIPT_STARTED_FRONTEND=1
  echo "==> Step 1: FORCE_START_FRONTEND，启动前端 dev server"
  echo "==> 日志: ${FRONTEND_LOG}"
  (
    cd "${ROOT_DIR}"
    exec bash -lc "${FRONTEND_START_CMD}"
  ) >"${FRONTEND_LOG}" 2>&1 &
  FRONTEND_PID=$!
  wait_for_frontend
else
  if frontend_ok; then
    echo "==> Step 1: 检测到已运行的前端（${FRONTEND_HEALTH_URL}），跳过启动"
  else
    FRONTEND_LOG="$(mktemp "${TMPDIR:-/tmp}/tiny-platform-frontend-dev.XXXXXX.log")"
    SCRIPT_STARTED_FRONTEND=1
    echo "==> Step 1: 未检测到前端，自动启动 Vite dev server"
    echo "==> 日志: ${FRONTEND_LOG}"
    (
      cd "${ROOT_DIR}"
      exec bash -lc "${FRONTEND_START_CMD}"
    ) >"${FRONTEND_LOG}" 2>&1 &
    FRONTEND_PID=$!
    wait_for_frontend
  fi
fi

echo "==> Step 2: local dev stack ready (backend + frontend)"
echo "==> verify-platform-local-dev-stack: OK"
