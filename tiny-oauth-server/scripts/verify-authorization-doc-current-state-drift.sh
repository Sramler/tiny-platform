#!/usr/bin/env bash
# 启发式文档当前态漂移守卫。
#
# 用途：
# - 扫描权限/授权相关文档中的高风险关键词，提醒“这句话可能把历史/桥接期写成当前态”。
# - 默认只做提醒，不作为硬失败门禁；如需本地严格模式，可设置 DOC_DRIFT_STRICT=1。
#
# 用法（仓库根目录）：
#   bash tiny-oauth-server/scripts/verify-authorization-doc-current-state-drift.sh
#
# 白名单：
# - 行内/邻近行标记：`CARD-14I: allow-historical`
# - 独立 ignore 文件：`tiny-oauth-server/scripts/verify-authorization-doc-current-state-drift.ignore`
#
# 退出码：
# - 0：脚本完成（默认即使命中提醒也不失败）
# - 1：仅当 DOC_DRIFT_STRICT=1 且存在需人工复核的 current/other 命中
# - 2：环境前置未满足（例如缺少 rg）
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "${ROOT_DIR}"

ALLOW_MARKER="${DOC_DRIFT_ALLOW_MARKER:-CARD-14I: allow-historical}"
IGNORE_FILE="${DOC_DRIFT_IGNORE_FILE:-${ROOT_DIR}/tiny-oauth-server/scripts/verify-authorization-doc-current-state-drift.ignore}"
STRICT_MODE="${DOC_DRIFT_STRICT:-0}"

if ! command -v rg >/dev/null 2>&1; then
  echo "verify-authorization-doc-current-state-drift: rg 不可用 → exit 2（环境前置未满足）" >&2
  exit 2
fi

declare -a SCAN_FILES=()
declare -a IGNORE_PATTERNS=()
declare -a REVIEW_CURRENT=()
declare -a REVIEW_OTHER=()
declare -a HISTORICAL_REFERENCE=()
declare -a META_REFERENCE=()
declare -a GUARDED_CONTEXT=()

TMP_RAW_HITS="$(mktemp "${TMPDIR:-/tmp}/auth-doc-drift.raw.XXXXXX")"
TMP_COMBINED_HITS="$(mktemp "${TMPDIR:-/tmp}/auth-doc-drift.combined.XXXXXX")"
trap 'rm -f "${TMP_RAW_HITS}" "${TMP_COMBINED_HITS}"' EXIT INT TERM

load_scan_files() {
  local file
  while IFS= read -r file; do
    [[ -z "${file}" ]] && continue
    SCAN_FILES+=("${file}")
  done < <(
    {
      [[ -f "AGENTS.md" ]] && printf '%s\n' "AGENTS.md"
      [[ -d "docs" ]] && rg --files docs
      [[ -d ".agent/src/rules" ]] && rg --files .agent/src/rules
      [[ -f "tiny-oauth-server/src/main/resources/schema.sql" ]] && printf '%s\n' "tiny-oauth-server/src/main/resources/schema.sql"
      [[ -f "tiny-oauth-server/src/main/resources/menu_resource_data.sql" ]] && printf '%s\n' "tiny-oauth-server/src/main/resources/menu_resource_data.sql"
    } | rg '\.(md|sql)$' | awk '!seen[$0]++'
  )
}

load_ignore_patterns() {
  if [[ ! -f "${IGNORE_FILE}" ]]; then
    return 0
  fi

  while IFS= read -r line; do
    [[ -z "${line}" ]] && continue
    [[ "${line}" =~ ^[[:space:]]*# ]] && continue
    IGNORE_PATTERNS+=("${line}")
  done < "${IGNORE_FILE}"
}

is_current_truth_file() {
  local file="$1"
  case "${file}" in
    AGENTS.md|\
    docs/TINY_PLATFORM_AUTHORIZATION_DOC_MAP.md|\
    docs/TINY_PLATFORM_AUTHORIZATION_MODEL.md|\
    docs/TINY_PLATFORM_AUTHORIZATION_TASK_LIST.md|\
    docs/TINY_PLATFORM_AUTHORIZATION_LAYERED_MODEL.md|\
    docs/TINY_PLATFORM_SESSION_BEARER_AUTH_MATRIX.md|\
    docs/TINY_PLATFORM_API_ENDPOINT_GUARD_COVERAGE.md|\
    docs/TINY_PLATFORM_TENANT_GOVERNANCE.md|\
    docs/TINY_PLATFORM_PERMISSION_IDENTIFIER_SPEC.md|\
    .agent/src/rules/90-tiny-platform.rules.md|\
    .agent/src/rules/91-tiny-platform-auth.rules.md|\
    .agent/src/rules/92-tiny-platform-permission.rules.md|\
    .agent/src/rules/93-tiny-platform-authorization-model.rules.md|\
    .agent/src/rules/94-tiny-platform-tenant-governance.rules.md)
      return 0
      ;;
    *)
      return 1
      ;;
  esac
}

is_historical_file() {
  local file="$1"
  case "${file}" in
    docs/TINY_PLATFORM_AUTHORIZATION_PHASE1_TECHNICAL_DESIGN.md|\
    docs/TINY_PLATFORM_AUTHORIZATION_PHASE2_RBAC3_TECHNICAL_DESIGN.md|\
    docs/TINY_PLATFORM_AUTHORIZATION_LEGACY_REMOVAL_PLAN.md|\
    docs/TINY_PLATFORM_PERMISSION_REFACTOR_FINAL_APPROVAL.md|\
    docs/PERMISSION_REFACTOR_COMPATIBILITY_LAYER_ASSESSMENT.md|\
    docs/PERMISSION_REFACTOR_*.md|\
    docs/TINY_PLATFORM_MODULE_GAP_ANALYSIS.md|\
    docs/tiny-platform-saas-overall-design.md|\
    tiny-oauth-server/src/main/resources/schema.sql|\
    tiny-oauth-server/src/main/resources/menu_resource_data.sql)
      return 0
      ;;
    *)
      return 1
      ;;
  esac
}

is_meta_file() {
  local file="$1"
  case "${file}" in
    docs/TINY_PLATFORM_PLATFORM_SCOPE_CURSOR_TASK_CARDS.md)
      return 0
      ;;
    *)
      return 1
      ;;
  esac
}

has_inline_allow_marker() {
  local file="$1"
  local line_no="$2"
  local start=1
  if (( line_no > 2 )); then
    start=$((line_no - 2))
  fi
  sed -n "${start},${line_no}p" "${file}" | rg -F -q "${ALLOW_MARKER}"
}

has_historical_context_marker() {
  local file="$1"
  local line_no="$2"
  local start=1
  if (( line_no > 2 )); then
    start=$((line_no - 2))
  fi
  sed -n "${start},${line_no}p" "${file}" | rg -q "历史|桥接期|阶段性|快照|归档|附录|非当前运行态真相源|历史设计|历史讨论稿|Archived Snapshot|historical evidence"
}

has_meta_text_marker() {
  local text="$1"
  printf '%s\n' "${text}" | rg -q "关键词|示例|例如|复制给 Cursor|提示词|建议验证|allow-historical|扫描高风险关键词|rg -n|启发式守卫|heuristic|CARD-[0-9A-Z]+|正式执行卡见|执行卡见"
}

has_guarded_context_marker() {
  local file="$1"
  local line_no="$2"
  local start=1
  if (( line_no > 2 )); then
    start=$((line_no - 2))
  fi
  sed -n "${start},${line_no}p" "${file}" | rg -q "仅保留|兼容字段|历史输入|已收口|已完成|已移除|已删除|已切换|不再|勿将|不是|不等于|非当前|当前真相源|当前运行态功能权限真相源|只认显式|fail-closed|归档|快照|阶段性"
}

matches_ignore_pattern() {
  local record="$1"
  local pattern
  if [[ ${#IGNORE_PATTERNS[@]} -eq 0 ]]; then
    return 1
  fi
  for pattern in "${IGNORE_PATTERNS[@]}"; do
    if printf '%s\n' "${record}" | rg --pcre2 -q "${pattern}"; then
      return 0
    fi
  done
  return 1
}

collect_hits() {
  local entry label regex hit file rest line_no text
  : > "${TMP_RAW_HITS}"
  while IFS= read -r entry; do
    [[ -z "${entry}" ]] && continue
    label="${entry%%:::*}"
    regex="${entry#*:::}"
    while IFS= read -r hit; do
      file="${hit%%:*}"
      rest="${hit#*:}"
      line_no="${rest%%:*}"
      text="${rest#*:}"
      printf '%s\t%s\t%s\t%s\n' "${file}" "${line_no}" "${text}" "${label}" >> "${TMP_RAW_HITS}"
    done < <(rg -n --no-heading --pcre2 "${regex}" "${SCAN_FILES[@]}" || true)
  done <<'EOF'
resource_permission:::resource\.permission
platform_gt_global:::PLATFORM\s*>\s*GLOBAL
new_model_fallback:::新模型优先\s*\+\s*fallback|新模型优先.*fallback
role_admin_fallback:::(ROLE_ADMIN|ADMIN).*(兜底|快捷路径|放行|通行)
default_platform_semantics:::(default|DEFAULT).*(平台语义|平台租户|platform)|(?:平台语义|平台租户|platform).*(default|DEFAULT)
role_codes_tail:::roleCodes\s*<-\s*ROLE_\*|roleCodes.*ROLE_\*|ROLE_\*.*roleCodes|role\.getCode\(\).*(authority|authorities)
EOF
}

combine_hits() {
  if [[ ! -s "${TMP_RAW_HITS}" ]]; then
    : > "${TMP_COMBINED_HITS}"
    return 0
  fi

  awk -F '\t' '
    BEGIN { OFS = "\t" }
    {
      key = $1 SUBSEP $2
      if (!(key in seen)) {
        seen[key] = 1
        order[++n] = key
        file[key] = $1
        line[key] = $2
        text[key] = $3
        labels[key] = $4
      } else if (labels[key] !~ "(^|,)" $4 "(,|$)") {
        labels[key] = labels[key] "," $4
      }
    }
    END {
      for (i = 1; i <= n; i++) {
        key = order[i]
        print file[key], line[key], text[key], labels[key]
      }
    }
  ' "${TMP_RAW_HITS}" > "${TMP_COMBINED_HITS}"
}

classify_hits() {
  local file line_no text labels record
  while IFS=$'\t' read -r file line_no text labels; do
    [[ -z "${file}" ]] && continue
    record="${file}:${line_no}:${text}"

    if has_inline_allow_marker "${file}" "${line_no}" || matches_ignore_pattern "${record}"; then
      continue
    fi

    if is_meta_file "${file}" || has_meta_text_marker "${text}"; then
      META_REFERENCE+=("${record} [${labels}]")
    elif is_historical_file "${file}" || has_historical_context_marker "${file}" "${line_no}"; then
      HISTORICAL_REFERENCE+=("${record} [${labels}]")
    elif has_guarded_context_marker "${file}" "${line_no}"; then
      GUARDED_CONTEXT+=("${record} [${labels}]")
    elif is_current_truth_file "${file}"; then
      REVIEW_CURRENT+=("${record} [${labels}]")
    else
      REVIEW_OTHER+=("${record} [${labels}]")
    fi
  done < "${TMP_COMBINED_HITS}"
}

print_bucket() {
  local title="$1"
  shift
  local item
  [[ $# -eq 0 ]] && return 0
  echo
  echo "## ${title} ($#)"
  for item in "$@"; do
    echo "- ${item}"
  done
}

array_count() {
  local array_name="$1"
  local count=0
  eval "count=\${#${array_name}[@]}"
  printf '%s\n' "${count}"
}

print_named_bucket() {
  local title="$1"
  local array_name="$2"
  local count item
  count="$(array_count "${array_name}")"
  [[ "${count}" == "0" ]] && return 0
  echo
  echo "## ${title} (${count})"
  eval 'for item in "${'"${array_name}"'[@]}"; do echo "- ${item}"; done'
}

load_scan_files
load_ignore_patterns
collect_hits
combine_hits
classify_hits

echo "==> Authorization doc current-state drift scan (heuristic)"
echo "==> Scope: AGENTS.md / docs / .agent/src/rules / historical SQL reference files"
echo "==> Allow marker: ${ALLOW_MARKER}"
echo "==> Ignore file: ${IGNORE_FILE}"
echo "==> Patterns: resource_permission, platform_gt_global, new_model_fallback, role_admin_fallback, default_platform_semantics, role_codes_tail"
echo "==> Reminder: this is a heuristic reminder, not a hard CI gate."

print_named_bucket "Review Current-Truth Files" "REVIEW_CURRENT"
print_named_bucket "Review Other Files" "REVIEW_OTHER"
print_named_bucket "Guarded / Already-Qualified Hits" "GUARDED_CONTEXT"
print_named_bucket "Historical / Archived Reference Hits" "HISTORICAL_REFERENCE"
print_named_bucket "Meta / Task-Card Hits" "META_REFERENCE"

echo
echo "==> Checklist"
echo "1. 如果命中的是当前态文档，确认是否把历史/桥接期写成了现状；必要时改成当前事实或补真相源链接。"
echo "2. 如果命中的是历史材料，确认首页或邻近段落是否已有“历史 / 快照 / 非当前真相源”标记。"
echo "3. 如果该句必须保留且已人工确认，可加 \"${ALLOW_MARKER}\"，或把正则加入 ignore 文件。"
echo "4. 若命中出现在 schema / 参考 SQL，请优先补头注释与入口指针，不要把历史样例改写成当前 migration。"

REVIEW_CURRENT_COUNT="$(array_count REVIEW_CURRENT)"
REVIEW_OTHER_COUNT="$(array_count REVIEW_OTHER)"
GUARDED_COUNT="$(array_count GUARDED_CONTEXT)"
HISTORICAL_COUNT="$(array_count HISTORICAL_REFERENCE)"
META_COUNT="$(array_count META_REFERENCE)"
REVIEW_COUNT=$(( REVIEW_CURRENT_COUNT + REVIEW_OTHER_COUNT ))

echo
echo "==> Summary"
echo "current_truth_review=${REVIEW_CURRENT_COUNT}"
echo "other_review=${REVIEW_OTHER_COUNT}"
echo "guarded_context=${GUARDED_COUNT}"
echo "historical_reference=${HISTORICAL_COUNT}"
echo "meta_reference=${META_COUNT}"

if (( REVIEW_COUNT == 0 )); then
  echo "==> Result: no review-needed hits in current/other files."
  exit 0
fi

echo "==> Result: review-needed hits found (heuristic only)."
if [[ "${STRICT_MODE}" == "1" ]]; then
  exit 1
fi
exit 0
