#!/usr/bin/env bash
# W-Off proof pack (steps 1-3): static guards + targeted unit/integration tests.
# Step 4 (DROP role_resource) is applied by Liquibase 117 on application migrate.
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT_DIR"

echo "[role-resource-removal-proof] repo root: ${ROOT_DIR}"

echo "== 1) Static guard: no legacy role_resource repository hooks or JPA join table in app sources =="
legacy_api_hits() {
  if command -v rg >/dev/null 2>&1; then
    rg -n 'addRoleResourceRelation|deleteRoleResourceRelations|findRoleResourceRelationsByTenantId' \
      tiny-oauth-server/src/main/java tiny-web/src/main/java 2>/dev/null || true
  else
    grep -RInE 'addRoleResourceRelation|deleteRoleResourceRelations|findRoleResourceRelationsByTenantId' \
      tiny-oauth-server/src/main/java tiny-web/src/main/java 2>/dev/null || true
  fi
}
join_table_hits() {
  if command -v rg >/dev/null 2>&1; then
    rg -n '@JoinTable\s*\(\s*name\s*=\s*"role_resource"' \
      tiny-oauth-server/src/main/java tiny-web/src/main/java 2>/dev/null || true
  else
    grep -RInE '@JoinTable\([^)]*role_resource' \
      tiny-oauth-server/src/main/java tiny-web/src/main/java 2>/dev/null || true
  fi
}
if legacy_api_hits | grep -q .; then
  legacy_api_hits
  echo "[role-resource-removal-proof] FAIL: legacy role_resource repository API still referenced" >&2
  exit 1
fi
if join_table_hits | grep -q .; then
  join_table_hits
  echo "[role-resource-removal-proof] FAIL: JPA @JoinTable role_resource still present" >&2
  exit 1
fi

echo "== 2) Targeted Maven tests: permission + bootstrap + menu RBAC =="
mvn -q -pl tiny-oauth-server -Dpermission.signal.source=TEST test "-Dtest=TenantBootstrapServiceImplTest,RoleServiceImplTest,SecurityUserTest,JwtTokenCustomizerTest,PermissionVersionServiceTest,UserDetailsServiceImplTest,SecurityUserAuthorityServiceTest,MenuControllerRbacIntegrationTest"

echo "== 3) Optional DB checks: set MYSQL_* or MYSQL_PASSWORD =="
if [[ -n "${MYSQL_PASSWORD:-}" || -n "${MYSQL_ROOT_PASSWORD:-}" ]]; then
  PW="${MYSQL_PASSWORD:-${MYSQL_ROOT_PASSWORD}}"
  HOST="${MYSQL_HOST:-127.0.0.1}"
  PORT="${MYSQL_PORT:-3306}"
  USER="${MYSQL_USER:-root}"
  DB="${MYSQL_DB:-tiny_web}"
  mysql -h"$HOST" -P"$PORT" -u"$USER" -p"$PW" "$DB" \
    < "$ROOT_DIR/tiny-oauth-server/scripts/verify-role-permission-canonical-health.sql" | head -n 5
else
  echo "[role-resource-removal-proof] skip DB (no MYSQL_PASSWORD / MYSQL_ROOT_PASSWORD)"
fi

echo "[role-resource-removal-proof] PASS"
