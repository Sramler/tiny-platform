#!/usr/bin/env bash
# 验证平台侧工作流模板不只是草稿 UI：模板 XML 必须可校验、可部署到 Camunda，
# 启动实例前必须通过业务发起权限闸口和业务申请数据校验，审批任务完成前
# 必须写入标准状态流变量。
#
# Tier 1 不依赖外部数据库：运行真实 Camunda 内存引擎集成测试。
# Tier 2 在 DB_PASSWORD 存在时自动检查本机 dev DB 中的平台工作流角色/权限 seed。
#
# 用法（仓库根目录）:
#   bash tiny-oauth-server/scripts/verify-platform-workflow-template-runtime.sh
#
# 可选：
#   MAVEN_BIN=/path/to/mvn bash tiny-oauth-server/scripts/verify-platform-workflow-template-runtime.sh
#   VERIFY_PLATFORM_WORKFLOW_TEMPLATE_DB=1 DB_PASSWORD='...' bash tiny-oauth-server/scripts/verify-platform-workflow-template-runtime.sh
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "${ROOT_DIR}"

resolve_maven() {
  if [[ -n "${MAVEN_BIN:-}" ]]; then
    echo "${MAVEN_BIN}"
    return
  fi
  if command -v mvn >/dev/null 2>&1; then
    command -v mvn
    return
  fi
  find "${HOME}/.m2/wrapper/dists" -path "*/bin/mvn" -type f 2>/dev/null | sort -r | head -1
}

MAVEN_CMD="$(resolve_maven)"
if [[ -z "${MAVEN_CMD}" || ! -x "${MAVEN_CMD}" ]]; then
  echo "Missing Maven executable. Set MAVEN_BIN=/path/to/mvn or install mvn in PATH." >&2
  exit 2
fi

echo "==> Tier 1: template -> validate -> deploy -> business data -> task state flow (Camunda in-memory)"
"${MAVEN_CMD}" -nsu -pl tiny-oauth-server -Dtest=PlatformWorkflowBusinessDataValidationServiceTest,WorkflowBusinessRequestServiceTest,PlatformPermissionPublishServiceTest,PlatformRoleBaselineServiceTest,WorkflowGovernanceAssetServiceTest,ProcessControllerTest,ProcessModelTemplateRuntimeIntegrationTest,PlatformWorkflowBusinessClosureIntegrationTest test

if [[ -n "${DB_PASSWORD:-}" ]]; then
  echo "==> Tier 2: platform workflow business assets in local dev DB"
  VERIFY_PLATFORM_TEMPLATE_MIN_ROWS=1 bash tiny-oauth-server/scripts/verify-platform-template-row-counts.sh
elif [[ "${VERIFY_PLATFORM_WORKFLOW_TEMPLATE_DB:-}" == "1" ]]; then
  echo "VERIFY_PLATFORM_WORKFLOW_TEMPLATE_DB=1 requires DB_PASSWORD." >&2
  exit 2
else
  echo "==> Tier 2: skipped (set DB_PASSWORD to verify local dev DB role/permission seed)"
fi

echo "==> verify-platform-workflow-template-runtime: OK"
