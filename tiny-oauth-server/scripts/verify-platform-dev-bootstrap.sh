#!/usr/bin/env bash
# 平台开发环境：在仓库根目录终端执行一条命令即可完成 1→2→3 验证（无需记 START_OAUTH_SERVER）。
#
# 默认策略（只需 DB_PASSWORD；若仅导出 E2E_DB_*，脚本会兼容回填到 DB_*）:
#   - 若 http://127.0.0.1:OAUTH_PORT/csrf 可用 → 视为已有 oauth-server（如 IDE 已启动），跳过启动，直接 2+3。
#   - 若连接被拒绝 → 自动 mvn package、后台 spring-boot:run(dev)，等待模板落库后 2+3，退出时关闭本次启动的进程。
#
# 步骤:
#   1) 编译 / 必要时启动 oauth-server（dev，触发平台模板自举）
#   2) 平台模板行数 role/carrier（tenant_id IS NULL）均 > 0
#   3) ensure-platform-admin + verify-platform-login-auth-chain（可选 Tier2）
#
# 用法（仓库根目录）:
#   DB_PASSWORD='…' bash tiny-oauth-server/scripts/verify-platform-dev-bootstrap.sh
#
# 可选环境变量:
#   LOAD_LOGIN_SHELL_ENV=1      缺省时允许从 login shell 白名单环境变量回填 DB_* / MYSQL_*（默认开启）
#   LOCAL_ENV_SHELL             默认当前 SHELL（缺省回退 /bin/zsh）；仅用于 login shell 子进程 `printenv`
#   SKIP_OAUTH_SERVER_START=1   永不启动进程（仅 DB + Maven 校验；无现成 server 时步骤 2 可能失败）
#   FORCE_START_OAUTH_SERVER=1  必须由本脚本启动（已有 /csrf 可用时会失败，适合 CI 独占端口）
#   OAUTH_PORT                    默认 9000
#   BOOT_WAIT_SEC / TEMPLATE_WAIT_SEC
#   SKIP_MVN=1                    跳过前置 Maven 单测（TenantBootstrap + auth 解析相关）
#   VERIFY_PLATFORM_LOGIN_E2E=1 + E2E_DB_PASSWORD
#   SKIP_DB_PING=1              跳过 MySQL 连通性预检
#   AUTO_START_MYSQL=1          MySQL 不可达时尝试用本地命令自愈启动（默认开启）
#   MYSQL_BIN                   mysql 客户端路径（默认 PATH 中的 mysql）
#   MYSQL_START_CMD             显式 MySQL 启动命令（优先于 brew/mysql.server 自动探测）
#   MYSQL_START_WAIT_SEC        MySQL 自愈启动后等待秒数（默认 45）
#   DB_* 与 ensure-platform-admin / verify-platform-template-row-counts 一致
#   PLATFORM_TENANT_CODE 或 E2E_PLATFORM_TENANT_CODE（CARD-13E：调用 ensure-platform-admin 前须显式传入平台来源租户 code）
#   E2E_DB_*                 兼容别名；若 DB_* 未设置，则用作 dev-bootstrap 的回填来源
#   远程库时除 DB_* 外可设 SPRING_DATASOURCE_URL（本脚本启动 JVM 时用）
#
# 退出码（正式契约）:
#   0 — 全部通过。
#   1 — 前置已满足，但验证失败（oauth 未就绪、模板空、子脚本失败等）。
#   2 — 环境前置未满足（无 DB_PASSWORD、无 mysql 客户端、无法连库）。**非代码回归结论**。
# 未执行: 未运行本脚本则无退出码；PR/报告应写「未跑 dev-bootstrap」。
#
# 说明：若 Spring 因 Redis 等依赖无法启动，会超时失败，请按 application*.yaml 准备环境。
# 密码仅经环境变量注入，禁止写入仓库。
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "${ROOT_DIR}"

LOCAL_ENV_SHELL="${LOCAL_ENV_SHELL:-${SHELL:-/bin/zsh}}"
LOAD_LOGIN_SHELL_ENV="${LOAD_LOGIN_SHELL_ENV:-1}"
WEBAPP_E2E_LOCAL="${ROOT_DIR}/tiny-oauth-server/src/main/webapp/.env.e2e.local"
DB_HOST="${DB_HOST:-${E2E_DB_HOST:-127.0.0.1}}"
DB_PORT="${DB_PORT:-${E2E_DB_PORT:-3306}}"
DB_NAME="${DB_NAME:-${E2E_DB_NAME:-tiny_web}}"
DB_USER="${DB_USER:-${E2E_DB_USER:-root}}"
MYSQL_BIN="${MYSQL_BIN:-mysql}"
MYSQL_START_CMD="${MYSQL_START_CMD:-}"
MYSQL_START_WAIT_SEC="${MYSQL_START_WAIT_SEC:-45}"
AUTO_START_MYSQL="${AUTO_START_MYSQL:-1}"

OAUTH_PORT="${OAUTH_PORT:-9000}"
BOOT_WAIT_SEC="${BOOT_WAIT_SEC:-180}"
TEMPLATE_WAIT_SEC="${TEMPLATE_WAIT_SEC:-120}"

is_whitelisted_env_name() {
  [[ "$1" =~ ^[A-Z0-9_]+$ ]]
}

strip_wrapping_quotes() {
  local value="$1"
  if [[ "$value" == \"*\" && "$value" == *\" ]]; then
    value="${value:1:${#value}-2}"
  elif [[ "$value" == \'*\' && "$value" == *\' ]]; then
    value="${value:1:${#value}-2}"
  fi
  printf '%s' "$value"
}

read_webapp_local_env() {
  local name="$1"
  local line raw
  if [[ ! -f "${WEBAPP_E2E_LOCAL}" ]]; then
    return 1
  fi
  line="$(grep -E "^${name}=" "${WEBAPP_E2E_LOCAL}" | tail -n 1 || true)"
  if [[ -z "${line}" ]]; then
    return 1
  fi
  raw="${line#*=}"
  raw="${raw%$'\r'}"
  raw="$(strip_wrapping_quotes "${raw}")"
  if [[ -z "${raw//[[:space:]]/}" ]]; then
    return 1
  fi
  printf '%s' "${raw}"
}

read_login_shell_env() {
  local name="$1"
  if ! is_whitelisted_env_name "${name}"; then
    return 1
  fi
  TP_ENV_NAME="${name}" "${LOCAL_ENV_SHELL}" -lc 'printenv "$TP_ENV_NAME"' 2>/dev/null | head -n 1
}

export_first_available_env() {
  local target="$1"
  shift
  local source value
  if [[ -n "${!target:-}" ]]; then
    return 0
  fi
  for source in "$@"; do
    value="$(read_login_shell_env "${source}" || true)"
    if [[ -n "${value}" ]]; then
      export "${target}=${value}"
      return 0
    fi
  done
  return 1
}

hydrate_env_from_login_shell() {
  if [[ "${LOAD_LOGIN_SHELL_ENV}" != "1" ]]; then
    return 0
  fi
  local name value
  local -a whitelist=(
    DB_PASSWORD
    DB_HOST
    DB_PORT
    DB_NAME
    DB_USER
    E2E_DB_PASSWORD
    E2E_DB_HOST
    E2E_DB_PORT
    E2E_DB_NAME
    E2E_DB_USER
    MYSQL_BIN
    MYSQL_START_CMD
    MYSQL_START_WAIT_SEC
    SPRING_DATASOURCE_URL
  )
  for name in "${whitelist[@]}"; do
    if [[ -z "${!name:-}" ]]; then
      value="$(read_login_shell_env "${name}" || true)"
      if [[ -n "${value}" ]]; then
        export "${name}=${value}"
      fi
    fi
  done
  export_first_available_env DB_PASSWORD DB_PASSWORD E2E_DB_PASSWORD || true
  export_first_available_env DB_HOST DB_HOST E2E_DB_HOST || true
  export_first_available_env DB_PORT DB_PORT E2E_DB_PORT || true
  export_first_available_env DB_NAME DB_NAME E2E_DB_NAME || true
  export_first_available_env DB_USER DB_USER E2E_DB_USER || true
  DB_HOST="${DB_HOST:-${E2E_DB_HOST:-127.0.0.1}}"
  DB_PORT="${DB_PORT:-${E2E_DB_PORT:-3306}}"
  DB_NAME="${DB_NAME:-${E2E_DB_NAME:-tiny_web}}"
  DB_USER="${DB_USER:-${E2E_DB_USER:-root}}"
  MYSQL_BIN="${MYSQL_BIN:-mysql}"
  MYSQL_START_WAIT_SEC="${MYSQL_START_WAIT_SEC:-45}"
}

hydrate_env_from_login_shell

hydrate_env_from_webapp_local() {
  local name value
  local -a whitelist=(
    E2E_FRONTEND_BASE_URL
    E2E_FRONTEND_PORT
    E2E_PLATFORM_TENANT_CODE
    E2E_PLATFORM_USERNAME
    E2E_PLATFORM_PASSWORD
    E2E_PLATFORM_TOTP_SECRET
  )
  for name in "${whitelist[@]}"; do
    if [[ -z "${!name:-}" ]]; then
      value="$(read_webapp_local_env "${name}" || true)"
      if [[ -n "${value}" ]]; then
        export "${name}=${value}"
      fi
    fi
  done
}

hydrate_env_from_webapp_local

mysql_ping() {
  env MYSQL_PWD="${DB_PASSWORD}" "${MYSQL_BIN}" \
    -h"${DB_HOST}" -P"${DB_PORT}" -u"${DB_USER}" -D"${DB_NAME}" \
    --connect-timeout=5 -e "SELECT 1" >/dev/null 2>&1
}

wait_for_mysql_ping() {
  local deadline=$((SECONDS + MYSQL_START_WAIT_SEC))
  while (( SECONDS < deadline )); do
    if mysql_ping; then
      return 0
    fi
    sleep 2
  done
  return 1
}

try_start_mysql() {
  if [[ "${AUTO_START_MYSQL}" != "1" ]]; then
    return 1
  fi
  local -a candidates=()
  local cmd=""
  if [[ -n "${MYSQL_START_CMD}" ]]; then
    candidates+=("${MYSQL_START_CMD}")
  fi
  if command -v brew >/dev/null 2>&1; then
    candidates+=("brew services start mysql")
    candidates+=("brew services start mysql@8.0")
    candidates+=("brew services start mysql@8.4")
  fi
  if command -v mysql.server >/dev/null 2>&1; then
    candidates+=("mysql.server start")
  fi
  for cmd in "${candidates[@]}"; do
    [[ -z "${cmd}" ]] && continue
    echo "==> MySQL 不可达，尝试自愈启动: ${cmd}" >&2
    if bash -lc "${cmd}" >/dev/null 2>&1; then
      if wait_for_mysql_ping; then
        echo "==> MySQL 已恢复可达 (${DB_HOST}:${DB_PORT}/${DB_NAME})" >&2
        return 0
      fi
    fi
  done
  return 1
}

preflight_platform_dev_bootstrap() {
  local reasons=()
  if [[ -z "${DB_PASSWORD:-}" ]]; then
    reasons+=("DB_PASSWORD 未设置")
  fi
  if ! command -v "${MYSQL_BIN}" >/dev/null 2>&1; then
    reasons+=("找不到 mysql 客户端：${MYSQL_BIN}")
  fi
  if ((${#reasons[@]} > 0)); then
    echo "verify-platform-dev-bootstrap: 环境前置未满足 → exit 2（非代码失败）" >&2
    for r in "${reasons[@]}"; do
      echo "  - ${r}" >&2
    done
    echo "文档: docs/TINY_PLATFORM_TESTING_PLAYBOOK.md §1.2" >&2
    exit 2
  fi
  if [[ "${SKIP_DB_PING:-}" != "1" ]]; then
    if ! mysql_ping; then
      if ! try_start_mysql; then
        echo "verify-platform-dev-bootstrap: MySQL 不可达 (${DB_HOST}:${DB_PORT} DB=${DB_NAME} user=${DB_USER}) → exit 2（环境/凭证，非代码失败）" >&2
        echo "提示: 确认 mysqld、库名、账号；可配置 MYSQL_START_CMD 或使用 SKIP_DB_PING=1（不推荐）。" >&2
        exit 2
      fi
    fi
  fi
}

preflight_platform_dev_bootstrap

BOOT_LOG=""
SCRIPT_STARTED_OAUTH=0

cleanup_boot() {
  if [[ "${SCRIPT_STARTED_OAUTH}" != "1" ]]; then
    return 0
  fi
  if [[ -n "${BOOT_PID:-}" ]] && kill -0 "${BOOT_PID}" 2>/dev/null; then
    kill "${BOOT_PID}" 2>/dev/null || true
    wait "${BOOT_PID}" 2>/dev/null || true
  fi
  if command -v lsof >/dev/null 2>&1; then
    local p
    for _ in 1 2; do
      for p in $(lsof -tiTCP:"${OAUTH_PORT}" -sTCP:LISTEN 2>/dev/null || true); do
        kill "${p}" 2>/dev/null || true
      done
      sleep 1
    done
    for p in $(lsof -tiTCP:"${OAUTH_PORT}" -sTCP:LISTEN 2>/dev/null || true); do
      kill -9 "${p}" 2>/dev/null || true
    done
  fi
}

trap cleanup_boot EXIT INT TERM

csrf_url="http://127.0.0.1:${OAUTH_PORT}/csrf"

oauth_csrf_ok() {
  curl -sf "${csrf_url}" >/dev/null 2>&1
}

read_platform_template_counts() {
  # 使用 MYSQL_PWD 避免 -p 出现在 argv 中导致 mysql 客户端反复打印 CLI 密码警告
  env MYSQL_PWD="${DB_PASSWORD}" "${MYSQL_BIN}" -h"${DB_HOST}" -P"${DB_PORT}" -u"${DB_USER}" -D "${DB_NAME}" -N -B <<'SQL'
SELECT
  (SELECT COUNT(*) FROM role WHERE tenant_id IS NULL),
  (SELECT COUNT(*) FROM menu WHERE tenant_id IS NULL),
  (SELECT COUNT(*) FROM ui_action WHERE tenant_id IS NULL),
  (SELECT COUNT(*) FROM api_endpoint WHERE tenant_id IS NULL);
SQL
}

wait_for_oauth_csrf() {
  local deadline=$((SECONDS + BOOT_WAIT_SEC))
  echo "==> Waiting for ${csrf_url} (max ${BOOT_WAIT_SEC}s)"
  while (( SECONDS < deadline )); do
    if oauth_csrf_ok; then
      echo "==> OAuth server HTTP ready"
      return 0
    fi
    sleep 2
  done
  echo "ERROR: oauth-server did not become ready. See log: ${BOOT_LOG}" >&2
  if [[ -n "${BOOT_LOG}" && -s "${BOOT_LOG}" ]]; then
    tail -n 80 "${BOOT_LOG}" >&2 || true
  fi
  return 1
}

wait_for_platform_template_rows() {
  local deadline=$((SECONDS + TEMPLATE_WAIT_SEC))
  echo "==> Waiting for platform template rows (tenant_id IS NULL), max ${TEMPLATE_WAIT_SEC}s"
  while (( SECONDS < deadline )); do
    local line role_cnt menu_cnt ui_action_cnt api_endpoint_cnt carrier_cnt
    line="$(read_platform_template_counts)"
    role_cnt="$(echo "${line}" | awk '{print $1}')"
    menu_cnt="$(echo "${line}" | awk '{print $2}')"
    ui_action_cnt="$(echo "${line}" | awk '{print $3}')"
    api_endpoint_cnt="$(echo "${line}" | awk '{print $4}')"
    if [[ "${role_cnt}" =~ ^[0-9]+$ && "${menu_cnt}" =~ ^[0-9]+$ && "${ui_action_cnt}" =~ ^[0-9]+$ && "${api_endpoint_cnt}" =~ ^[0-9]+$ ]]; then
      carrier_cnt=$((menu_cnt + ui_action_cnt + api_endpoint_cnt))
    else
      carrier_cnt=-1
    fi
    if [[ "${role_cnt}" =~ ^[0-9]+$ && "${role_cnt}" -gt 0 && "${carrier_cnt}" -gt 0 ]]; then
      echo "==> platform template: role=${role_cnt} carrier=${carrier_cnt} (menu=${menu_cnt}, ui_action=${ui_action_cnt}, api_endpoint=${api_endpoint_cnt})"
      return 0
    fi
    sleep 3
  done
  echo "ERROR: platform template still empty after wait." >&2
  if [[ "${SCRIPT_STARTED_OAUTH}" == "1" && -n "${BOOT_LOG}" && -s "${BOOT_LOG}" ]]; then
    tail -n 80 "${BOOT_LOG}" >&2 || true
  else
    echo "提示：请用 spring.profiles.active=dev 启动 oauth-server，并确认 tiny.platform.tenant.auto-initialize-platform-template-if-missing 已开启。" >&2
  fi
  return 1
}

apply_spring_datasource_env() {
  export SPRING_DATASOURCE_PASSWORD="${DB_PASSWORD}"
  export SPRING_DATASOURCE_USERNAME="${DB_USER}"
  if [[ -z "${SPRING_DATASOURCE_URL:-}" ]]; then
    if [[ "${DB_HOST}" != "localhost" && "${DB_HOST}" != "127.0.0.1" ]] || [[ "${DB_PORT}" != "3306" ]] || [[ "${DB_NAME}" != "tiny_web" ]]; then
      export SPRING_DATASOURCE_URL="jdbc:mysql://${DB_HOST}:${DB_PORT}/${DB_NAME}?useSSL=false&allowPublicKeyRetrieval=true&useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai&sessionVariables=transaction_isolation='READ-COMMITTED'"
    fi
  fi
}

start_oauth_server_and_wait() {
  local label="$1"
  BOOT_LOG="$(mktemp "${TMPDIR:-/tmp}/tiny-oauth-verify-boot.XXXXXX.log")"
  SCRIPT_STARTED_OAUTH=1
  echo "==> Step 1: ${label}"
  mvn -q -pl tiny-oauth-server package -DskipTests
  apply_spring_datasource_env
  echo "==> 日志: ${BOOT_LOG}"
  (
    cd "${ROOT_DIR}"
    exec mvn -q -pl tiny-oauth-server spring-boot:run \
      -Dspring-boot.run.profiles=dev \
      -Dspring-boot.run.arguments="--server.port=${OAUTH_PORT}"
  ) >"${BOOT_LOG}" 2>&1 &
  BOOT_PID=$!
  wait_for_oauth_csrf
  wait_for_platform_template_rows
}

# --- 步骤 1：是否由本脚本启动 oauth-server ---
if [[ "${SKIP_OAUTH_SERVER_START:-}" == "1" ]]; then
  echo "==> Step 1: SKIP_OAUTH_SERVER_START=1，不启动进程"
  if oauth_csrf_ok; then
    echo "==> 检测到已有 oauth-server (${csrf_url})，将等待平台模板（若尚未落库）"
    wait_for_platform_template_rows
  else
    echo "WARN: 未检测到 oauth-server，后续步骤 2 依赖数据库已有平台模板。" >&2
  fi
elif [[ "${FORCE_START_OAUTH_SERVER:-}" == "1" ]]; then
  if oauth_csrf_ok; then
    echo "ERROR: FORCE_START_OAUTH_SERVER=1 但 ${csrf_url} 已可用；请先释放端口或取消 FORCE。" >&2
    exit 1
  fi
  if command -v lsof >/dev/null 2>&1 && lsof -iTCP:"${OAUTH_PORT}" -sTCP:LISTEN -n -P >/dev/null 2>&1; then
    echo "ERROR: port ${OAUTH_PORT} 已被占用且无法识别为 oauth /csrf。" >&2
    exit 1
  fi
  start_oauth_server_and_wait "FORCE_START — Maven package + spring-boot:run (dev, port=${OAUTH_PORT})"
else
  # 默认：有现成服务就用，否则自启
  if oauth_csrf_ok; then
    echo "==> Step 1: 检测到已运行的 oauth-server（${csrf_url}），跳过启动"
    wait_for_platform_template_rows
  else
    start_oauth_server_and_wait "未检测到 ${csrf_url}，自动编译并启动 oauth-server（dev, port=${OAUTH_PORT}）"
  fi
fi

if [[ "${SKIP_MVN:-}" != "1" ]]; then
  echo "==> Maven: TenantBootstrap + auth resolution quick tests"
  mvn -q -pl tiny-oauth-server test \
    -Dtest=TenantBootstrapServiceImplTest,MultiAuthenticationProviderTest,AuthUserResolutionServiceTest
else
  echo "==> Maven quick tests skipped (SKIP_MVN=1)"
fi

echo "==> Step 2: Platform template row counts (require > 0)"
VERIFY_PLATFORM_TEMPLATE_MIN_ROWS=1 \
  bash "${ROOT_DIR}/tiny-oauth-server/scripts/verify-platform-template-row-counts.sh"

echo "==> Step 3a: ensure-platform-admin"
# CARD-13E：ensure-platform-admin.sh 不再默认 default；与 E2E/应用配置对齐显式传入。
export PLATFORM_TENANT_CODE="${PLATFORM_TENANT_CODE:-${E2E_PLATFORM_TENANT_CODE:-}}"
if [[ -z "${PLATFORM_TENANT_CODE}" ]]; then
  echo "ERROR: 请先设置 PLATFORM_TENANT_CODE 或 E2E_PLATFORM_TENANT_CODE（库内平台来源租户 code，与 application-dev platform-tenant-code 一致）。" >&2
  exit 1
fi
bash "${ROOT_DIR}/tiny-oauth-server/scripts/ensure-platform-admin.sh"

echo "==> Step 3b: Platform login auth chain (Tier1 + optional Tier2)"
bash "${ROOT_DIR}/tiny-oauth-server/scripts/verify-platform-login-auth-chain.sh"

echo "==> verify-platform-dev-bootstrap: OK"
