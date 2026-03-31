#!/usr/bin/env bash
# 顺序执行同一模块 tiny-oauth-server 的 compile 与 test，避免并发执行多个 Maven 目标时
# 争抢 tiny-oauth-server/target（可能出现 NoSuchFileException、半写入 .class）。
#
# 用法（仓库根目录）:
#   bash tiny-oauth-server/scripts/mvn-tiny-oauth-server-gate-sequential.sh
#
# 环境变量:
#   MVN              默认 mvn（须已在 PATH）
#   MAVEN_TEST       覆盖 -Dtest= 列表（逗号分隔）；默认为一组常用门禁单测类名
#   GATE_CLEAN_FIRST=1  先执行 mvn -pl tiny-oauth-server clean，减少 JaCoCo exec 与 class 不一致告警（可信覆盖率场景）
#
# 说明：不替代 CI；仅供本地/助手顺序跑门禁。详见 docs/TINY_PLATFORM_TESTING_PLAYBOOK.md §1.3。
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "${ROOT_DIR}"

MVN="${MVN:-mvn}"

DEFAULT_TEST="TenantBootstrapServiceImplTest,TenantServiceImplTest,DataScopeResolverServiceTest,UserServiceImplTest,TenantContextFilterTest,UserControllerTest,PermissionVersionServiceTest,DictTypeServiceImplTest,DictItemServiceImplTest"
MAVEN_TEST="${MAVEN_TEST:-${DEFAULT_TEST}}"

if [[ "${GATE_CLEAN_FIRST:-}" == "1" ]]; then
  echo "==> [0] ${MVN} -pl tiny-oauth-server clean (GATE_CLEAN_FIRST=1)"
  "${MVN}" -pl tiny-oauth-server clean
fi

echo "==> [1/2] ${MVN} -pl tiny-oauth-server -DskipTests compile"
"${MVN}" -pl tiny-oauth-server -DskipTests compile

echo "==> [2/2] ${MVN} -pl tiny-oauth-server -Dtest=... test (${MAVEN_TEST})"
"${MVN}" -pl tiny-oauth-server -Dtest="${MAVEN_TEST}" -DfailIfNoTests=false test

echo "==> mvn-tiny-oauth-server-gate-sequential: OK"
