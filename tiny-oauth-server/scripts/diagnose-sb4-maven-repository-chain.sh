#!/usr/bin/env bash
# 诊断 tiny-platform / sb4 Maven 仓库链路：
#   1) 生成 effective settings / effective POM（默认带上 CAMUNDA_GITHUB_PACKAGES_ENABLED=true）
#   2) 输出脱敏后的 settings / repositories / pluginRepositories 摘要
#   3) 扫描本地 Maven 仓库中的 camunda-nexus / camunda-public-repository / JBoss public 等历史解析痕迹
#
# 用法（仓库根目录）:
#   bash tiny-oauth-server/scripts/diagnose-sb4-maven-repository-chain.sh
#
# 环境变量:
#   MVN                    默认 mvn
#   ENABLE_CAMUNDA_PROFILE 默认 1；为 1 时生成 effective POM 时临时注入 CAMUNDA_GITHUB_PACKAGES_ENABLED=true
#   LOCAL_REPO_OVERRIDE    覆盖要扫描的本地 Maven 仓库目录；默认取 effective settings 的 <localRepository>
#   TRACE_LIMIT            默认 12；控制历史痕迹样本文件数量
#   KEEP_WORK_DIR          默认 0；为 1 时保留中间 effective XML 与 report 目录（注意其中可能包含 raw effective settings）
#
# 退出码:
#   0 — 诊断完成
#   1 — 执行失败
#   2 — 环境前置未满足（如 mvn / rg 不可用）
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "${ROOT_DIR}"

MVN="${MVN:-mvn}"
ENABLE_CAMUNDA_PROFILE="${ENABLE_CAMUNDA_PROFILE:-1}"
LOCAL_REPO_OVERRIDE="${LOCAL_REPO_OVERRIDE:-}"
TRACE_LIMIT="${TRACE_LIMIT:-12}"
KEEP_WORK_DIR="${KEEP_WORK_DIR:-0}"

require_cmd() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "diagnose-sb4-maven-repository-chain: 缺少命令 '$1' → exit 2（环境前置未满足）" >&2
    exit 2
  fi
}

require_cmd "${MVN}"
require_cmd rg
require_cmd awk
require_cmd sed
require_cmd sort
require_cmd mktemp
require_cmd tee

WORK_DIR="$(mktemp -d "${TMPDIR:-/tmp}/tiny-platform-maven-chain.XXXXXX")"
SETTINGS_XML="${WORK_DIR}/effective-settings.xml"
POM_XML="${WORK_DIR}/effective-pom.xml"
REPORT_TXT="${WORK_DIR}/report.txt"

cleanup() {
  if [[ "${KEEP_WORK_DIR}" == "1" ]]; then
    return 0
  fi
  rm -rf "${WORK_DIR}" 2>/dev/null || true
}

trap cleanup EXIT INT TERM

append_line() {
  printf '%s\n' "$*" | tee -a "${REPORT_TXT}"
}

append_blank() {
  printf '\n' | tee -a "${REPORT_TXT}"
}

append_command_output() {
  "$@" | tee -a "${REPORT_TXT}"
}

trim_xml_value() {
  sed -n "s:.*<$1>\\(.*\\)</$1>.*:\\1:p"
}

extract_settings_mirrors() {
  awk '
    /<mirror>/{in_mirror=1; id=""; url=""; mirrorOf=""; blocked=""; next}
    in_mirror && /<id>/{line=$0; sub(/^.*<id>/, "", line); sub(/<\/id>.*$/, "", line); id=line}
    in_mirror && /<url>/{line=$0; sub(/^.*<url>/, "", line); sub(/<\/url>.*$/, "", line); url=line}
    in_mirror && /<mirrorOf>/{line=$0; sub(/^.*<mirrorOf>/, "", line); sub(/<\/mirrorOf>.*$/, "", line); mirrorOf=line}
    in_mirror && /<blocked>/{line=$0; sub(/^.*<blocked>/, "", line); sub(/<\/blocked>.*$/, "", line); blocked=line}
    in_mirror && /<\/mirror>/{printf "%s|%s|%s|%s\n", id, url, mirrorOf, blocked; in_mirror=0}
  ' "${SETTINGS_XML}" | awk -F'|' 'NF && !seen[$0]++'
}

extract_settings_repositories() {
  awk '
    /<repositories>/{in_repositories=1; next}
    /<\/repositories>/{in_repositories=0; next}
    in_repositories && /<repository>/{in_repo=1; id=""; url=""; next}
    in_repo && /<id>/{line=$0; sub(/^.*<id>/, "", line); sub(/<\/id>.*$/, "", line); id=line}
    in_repo && /<url>/{line=$0; sub(/^.*<url>/, "", line); sub(/<\/url>.*$/, "", line); url=line}
    in_repo && /<\/repository>/{printf "%s|%s\n", id, url; in_repo=0}
  ' "${SETTINGS_XML}" | awk -F'|' 'NF && !seen[$0]++'
}

extract_pom_repositories() {
  awk '
    /<pluginRepositories>/{context="plugin"; next}
    /<\/pluginRepositories>/{if (context=="plugin") context=""; next}
    /<repositories>/{if (context=="") context="repo"; next}
    /<\/repositories>/{if (context=="repo") context=""; next}
    context=="repo" && /<repository>/{in_repo=1; id=""; url=""; next}
    context=="repo" && in_repo && /<id>/{line=$0; sub(/^.*<id>/, "", line); sub(/<\/id>.*$/, "", line); id=line}
    context=="repo" && in_repo && /<url>/{line=$0; sub(/^.*<url>/, "", line); sub(/<\/url>.*$/, "", line); url=line}
    context=="repo" && in_repo && /<\/repository>/{printf "%s|%s\n", id, url; in_repo=0}
  ' "${POM_XML}" | awk -F'|' 'NF && !seen[$0]++'
}

extract_pom_plugin_repositories() {
  awk '
    /<pluginRepositories>/{context="plugin"; next}
    /<\/pluginRepositories>/{if (context=="plugin") context=""; next}
    context=="plugin" && /<pluginRepository>/{in_repo=1; id=""; url=""; next}
    context=="plugin" && in_repo && /<id>/{line=$0; sub(/^.*<id>/, "", line); sub(/<\/id>.*$/, "", line); id=line}
    context=="plugin" && in_repo && /<url>/{line=$0; sub(/^.*<url>/, "", line); sub(/<\/url>.*$/, "", line); url=line}
    context=="plugin" && in_repo && /<\/pluginRepository>/{printf "%s|%s\n", id, url; in_repo=0}
  ' "${POM_XML}" | awk -F'|' 'NF && !seen[$0]++'
}

print_pair_list() {
  local prefix="$1"
  while IFS='|' read -r left right extra1 extra2; do
    [[ -z "${left}" && -z "${right}" ]] && continue
    if [[ -n "${extra1:-}" ]]; then
      append_line "${prefix}${left} -> ${right} (mirrorOf=${extra1}, blocked=${extra2})"
    else
      append_line "${prefix}${left} -> ${right}"
    fi
  done
}

count_trace_files() {
  local pattern="$1"
  if [[ ! -d "${LOCAL_REPO}" ]]; then
    echo 0
    return 0
  fi
  rg -l --glob '*.lastUpdated' --glob 'resolver-status.properties' --glob '_remote.repositories' -F "${pattern}" "${LOCAL_REPO}" 2>/dev/null | wc -l | tr -d ' '
}

collect_trace_samples() {
  if [[ ! -d "${LOCAL_REPO}" ]]; then
    return 0
  fi
  rg -l \
    --glob '*.lastUpdated' \
    --glob 'resolver-status.properties' \
    --glob '_remote.repositories' \
    -e 'camunda-nexus|camunda-public-repository|JBoss public|repo.spring.io/milestone|artifacts.camunda.com/artifactory/public|repository.jboss.org/nexus/content/groups/public' \
    "${LOCAL_REPO}" 2>/dev/null | sort | head -n "${TRACE_LIMIT}"
}

summarize_remote_source() {
  local sample_file="$1"
  if [[ -f "${sample_file}" ]]; then
    sed -n '1,40p' "${sample_file}" | tee -a "${REPORT_TXT}"
  else
    append_line "  (sample file not found)"
  fi
}

append_line "# Tiny Platform SB4 Maven 仓库链路诊断"
append_line
append_line "工作目录: ${WORK_DIR}"
append_line "仓库根目录: ${ROOT_DIR}"
append_line "Maven 命令: ${MVN}"
append_line "ENABLE_CAMUNDA_PROFILE=${ENABLE_CAMUNDA_PROFILE}"
append_blank

append_line "==> [1/4] 生成 effective settings"
"${MVN}" -q help:effective-settings -Doutput="${SETTINGS_XML}"

append_line "==> [2/4] 生成 effective POM"
if [[ "${ENABLE_CAMUNDA_PROFILE}" == "1" ]]; then
  CAMUNDA_GITHUB_PACKAGES_ENABLED=true "${MVN}" -q help:effective-pom -Doutput="${POM_XML}"
else
  "${MVN}" -q help:effective-pom -Doutput="${POM_XML}"
fi

append_line "==> [3/4] 解析 effective settings / effective POM"
LOCAL_REPO="$(trim_xml_value localRepository < "${SETTINGS_XML}" | head -n 1)"
if [[ -z "${LOCAL_REPO}" ]]; then
  LOCAL_REPO="${HOME}/.m2/repository"
fi
if [[ -n "${LOCAL_REPO_OVERRIDE}" ]]; then
  LOCAL_REPO="${LOCAL_REPO_OVERRIDE}"
fi

append_blank
append_line "**Environment**"
append_command_output "${MVN}" -version
append_blank

append_line "**Effective Settings**"
append_line "localRepository=${LOCAL_REPO}"
ACTIVE_PROFILES="$(trim_xml_value activeProfile < "${SETTINGS_XML}" | paste -sd ',' -)"
if [[ -z "${ACTIVE_PROFILES}" ]]; then
  ACTIVE_PROFILES="(none)"
fi
append_line "activeProfiles=${ACTIVE_PROFILES}"
append_line "mirrors:"
extract_settings_mirrors | print_pair_list "  - "
append_line "settings.repositories:"
extract_settings_repositories | print_pair_list "  - "
append_blank

append_line "**Effective POM Repositories**"
append_line "repositories:"
extract_pom_repositories | print_pair_list "  - "
append_line "pluginRepositories:"
extract_pom_plugin_repositories | print_pair_list "  - "
append_blank

append_line "==> [4/4] 扫描本地 Maven 仓库历史解析痕迹"
append_line
append_line "**Local Repository Trace Summary**"
append_line "camunda-nexus files=$(count_trace_files 'camunda-nexus')"
append_line "camunda-public-repository files=$(count_trace_files 'camunda-public-repository')"
append_line "JBoss public files=$(count_trace_files 'JBoss public')"
append_line "repo.spring.io/milestone files=$(count_trace_files 'repo.spring.io/milestone')"
append_line "artifacts.camunda.com/artifactory/public files=$(count_trace_files 'artifacts.camunda.com/artifactory/public')"
append_line "repository.jboss.org/nexus/content/groups/public files=$(count_trace_files 'repository.jboss.org/nexus/content/groups/public')"
append_blank

append_line "trace samples (first ${TRACE_LIMIT} files):"
TRACE_SAMPLES="$(collect_trace_samples || true)"
if [[ -z "${TRACE_SAMPLES}" ]]; then
  append_line "  - (no matching trace files)"
else
  while IFS= read -r sample; do
    [[ -n "${sample}" ]] || continue
    append_line "  - ${sample}"
  done <<< "${TRACE_SAMPLES}"
fi
append_blank

SPRING_CORE_REMOTE_FILE="${LOCAL_REPO}/org/springframework/spring-core/7.0.7-SNAPSHOT/_remote.repositories"
SPRING_CORE_STATUS_FILE="${LOCAL_REPO}/org/springframework/spring-core/7.0.7-SNAPSHOT/resolver-status.properties"

append_line "sample: spring-core remote source"
summarize_remote_source "${SPRING_CORE_REMOTE_FILE}"
append_blank

append_line "sample: spring-core resolver status"
summarize_remote_source "${SPRING_CORE_STATUS_FILE}"
append_blank

append_line "**Diagnosis Summary**"
append_line "- 当前 effective settings 未必能解释本地仓库中所有历史候选仓库痕迹。"
append_line "- 当前 effective POM 主链若未出现 camunda-nexus，则不要把它视为项目默认仓库。"
append_line "- 若 spring-core 的 _remote.repositories 显示 spring-snapshots，则说明当前成功来源仍是 spring-snapshots。"
append_line "- 若冷仓验证 github-camunda-fork，需要另行准备 GitHub Packages 读取凭证。"
append_blank

if [[ "${KEEP_WORK_DIR}" == "1" ]]; then
  append_line "report: ${REPORT_TXT}"
  append_line "work dir kept: ${WORK_DIR}"
else
  append_line "report: streamed to stdout only (temporary work dir will be removed)"
  append_line "work dir cleanup: enabled"
fi

echo "==> diagnose-sb4-maven-repository-chain: OK"
