#!/usr/bin/env bash
# Real-link E2E：幂等准备调度/平台治理权限与用户（口径与 permission/resource 的 normalized_tenant_id 一致）。
# 内嵌 sys_idempotent_token DDL 仅用于缺少迁移的本地沙箱，与权限链“历史兼容”无关。
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKEND_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
WORKSPACE_ROOT="$(cd "${BACKEND_ROOT}/.." && pwd)"

read_env() {
  local name
  local value
  for name in "$@"; do
    value="${!name-}"
    if [[ -n "${value//[[:space:]]/}" ]]; then
      printf '%s' "$value"
      return 0
    fi
  done
  return 1
}

is_placeholder_value() {
  local value="$1"
  [[ "$value" == \<* && "$value" == *\> ]]
}

require_value() {
  local label="$1"
  local value="$2"
  if [[ -z "${value//[[:space:]]/}" ]] || is_placeholder_value "$value"; then
    printf '缺少必需的 E2E 配置: %s\n' "$label" >&2
    exit 1
  fi
}

DB_HOST="$(read_env E2E_DB_HOST E2E_MYSQL_HOST || true)"
DB_HOST="${DB_HOST:-127.0.0.1}"
DB_PORT="$(read_env E2E_DB_PORT E2E_MYSQL_PORT || true)"
DB_PORT="${DB_PORT:-3306}"
DB_NAME="$(read_env E2E_DB_NAME E2E_MYSQL_DATABASE || true)"
DB_NAME="${DB_NAME:-tiny_web}"
DB_USER="$(read_env E2E_DB_USER E2E_MYSQL_USER || true)"
DB_USER="${DB_USER:-root}"
DB_PASSWORD="$(read_env E2E_DB_PASSWORD E2E_MYSQL_PASSWORD MYSQL_ROOT_PASSWORD || true)"

TENANT_CODE="$(read_env E2E_TENANT_CODE || true)"
E2E_USERNAME="$(read_env E2E_USERNAME || true)"
E2E_PASSWORD="$(read_env E2E_PASSWORD || true)"
E2E_TOTP_SECRET="$(read_env E2E_TOTP_SECRET || true)"

# 可选的“未绑定 TOTP 首绑专用”身份；如果未配置，则跳过首绑用户准备。
BIND_TENANT_CODE="$(read_env E2E_TENANT_CODE_BIND E2E_TENANT_CODE || true)"
BIND_USERNAME="$(read_env E2E_USERNAME_BIND || true)"
BIND_PASSWORD="$(read_env E2E_PASSWORD_BIND || true)"

# 可选的“调度只读”身份；如果未配置，则跳过只读用户准备。
READONLY_TENANT_CODE="$(read_env E2E_TENANT_CODE_READONLY E2E_TENANT_CODE || true)"
READONLY_USERNAME="$(read_env E2E_USERNAME_READONLY || true)"
READONLY_PASSWORD="$(read_env E2E_PASSWORD_READONLY || true)"
READONLY_TOTP_SECRET="$(read_env E2E_TOTP_SECRET_READONLY || true)"

require_value "E2E_DB_PASSWORD / E2E_MYSQL_PASSWORD / MYSQL_ROOT_PASSWORD" "${DB_PASSWORD}"
require_value "E2E_TENANT_CODE" "${TENANT_CODE}"
require_value "E2E_USERNAME" "${E2E_USERNAME}"
require_value "E2E_PASSWORD" "${E2E_PASSWORD}"
require_value "E2E_TOTP_SECRET" "${E2E_TOTP_SECRET}"

export E2E_EFFECTIVE_DB_HOST="${DB_HOST}"
export E2E_EFFECTIVE_DB_PORT="${DB_PORT}"
export E2E_EFFECTIVE_DB_NAME="${DB_NAME}"
export E2E_EFFECTIVE_DB_USER="${DB_USER}"
export E2E_EFFECTIVE_DB_PASSWORD="${DB_PASSWORD}"
export E2E_EFFECTIVE_TENANT_CODE="${TENANT_CODE}"
export E2E_EFFECTIVE_USERNAME="${E2E_USERNAME}"
export E2E_EFFECTIVE_PASSWORD="${E2E_PASSWORD}"
export E2E_EFFECTIVE_TOTP_SECRET="${E2E_TOTP_SECRET}"

export E2E_BIND_TENANT_CODE="${BIND_TENANT_CODE}"
export E2E_BIND_USERNAME="${BIND_USERNAME}"
export E2E_BIND_PASSWORD="${BIND_PASSWORD}"
export E2E_READONLY_TENANT_CODE="${READONLY_TENANT_CODE}"
export E2E_READONLY_USERNAME="${READONLY_USERNAME}"
export E2E_READONLY_PASSWORD="${READONLY_PASSWORD}"
export E2E_READONLY_TOTP_SECRET="${READONLY_TOTP_SECRET}"

CLASSPATH_FILE="${TMPDIR:-/tmp}/tiny-oauth-e2e-runtime-classpath.txt"

mvn -pl tiny-oauth-server -q dependency:build-classpath \
  -Dmdep.outputFile="${CLASSPATH_FILE}" \
  -Dmdep.includeScope=runtime \
  -f "${WORKSPACE_ROOT}/pom.xml" >/dev/null

#
# NOTE:
# Playwright globalSetup (and some CI runners) may execute this script in a non-interactive environment
# where JShell console IO can crash. Feed JShell via a script file to ensure deterministic execution.
#
JSHELL_SCRIPT_FILE="$(mktemp "${TMPDIR:-/tmp}/tiny-oauth-e2e-auth.XXXXXX.jsh")"
cat >"${JSHELL_SCRIPT_FILE}" <<'EOF'
import java.sql.*;
import java.time.*;
import java.util.Locale;

String dbHost = System.getenv("E2E_EFFECTIVE_DB_HOST");
String dbPort = System.getenv("E2E_EFFECTIVE_DB_PORT");
String dbName = System.getenv("E2E_EFFECTIVE_DB_NAME");
String dbUser = System.getenv("E2E_EFFECTIVE_DB_USER");
String dbPassword = System.getenv("E2E_EFFECTIVE_DB_PASSWORD");
String tenantCode = System.getenv("E2E_EFFECTIVE_TENANT_CODE");
String username = System.getenv("E2E_EFFECTIVE_USERNAME");
String rawPassword = System.getenv("E2E_EFFECTIVE_PASSWORD");
String totpSecret = System.getenv("E2E_EFFECTIVE_TOTP_SECRET");
String readonlyTenantCode = System.getenv("E2E_READONLY_TENANT_CODE");
String readonlyUsername = System.getenv("E2E_READONLY_USERNAME");
String readonlyPassword = System.getenv("E2E_READONLY_PASSWORD");
String readonlyTotpSecret = System.getenv("E2E_READONLY_TOTP_SECRET");
String jdbcUrl = "jdbc:mysql://" + dbHost + ":" + dbPort + "/" + dbName + "?useSSL=false&allowPublicKeyRetrieval=true&useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai";

Long ensureTenant(Connection connection, String rawTenantCode, String displayName) throws SQLException {
    if (rawTenantCode == null || rawTenantCode.isBlank()) {
        throw new IllegalStateException("缺少租户编码");
    }
    String tenantCode = rawTenantCode.trim().toLowerCase(Locale.ROOT);

    Long tenantId = null;
    try (PreparedStatement ps = connection.prepareStatement("SELECT id FROM tenant WHERE code = ? LIMIT 1")) {
        ps.setString(1, tenantCode);
        try (ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                tenantId = rs.getLong(1);
            }
        }
    }

    if (tenantId == null) {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO tenant (code, name, enabled, expires_at, created_at, updated_at, deleted_at) VALUES (?, ?, true, NULL, NOW(), NOW(), NULL)",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, tenantCode);
            ps.setString(2, displayName);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    tenantId = rs.getLong(1);
                }
            }
        }
    }

    if (tenantId == null) {
        throw new IllegalStateException("创建租户失败: " + tenantCode);
    }

    try (PreparedStatement ps = connection.prepareStatement(
            "UPDATE tenant SET enabled = true, lifecycle_status = 'ACTIVE', expires_at = NULL, deleted_at = NULL, updated_at = NOW() WHERE id = ?")) {
        ps.setLong(1, tenantId);
        ps.executeUpdate();
    }

    return tenantId;
}

Long findTenantIdByCode(Connection connection, String rawTenantCode) throws SQLException {
    if (rawTenantCode == null || rawTenantCode.isBlank()) {
        return null;
    }
    try (PreparedStatement ps = connection.prepareStatement("SELECT id FROM tenant WHERE code = ? LIMIT 1")) {
        ps.setString(1, rawTenantCode.trim().toLowerCase(Locale.ROOT));
        try (ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return rs.getLong(1);
            }
        }
    }
    return null;
}

Long findResourceId(Connection connection, Long tenantId, String name) throws SQLException {
    if (tenantId == null) {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT id FROM resource WHERE tenant_id IS NULL AND name = ? LIMIT 1")) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        }
    } else {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT id FROM resource WHERE tenant_id = ? AND name = ? LIMIT 1")) {
            ps.setLong(1, tenantId);
            ps.setString(2, name);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        }
    }
    return null;
}

Long findResourceIdByPermission(Connection connection, Long tenantId, String permission) throws SQLException {
    if (tenantId == null) {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT id FROM resource WHERE tenant_id IS NULL AND permission = ? LIMIT 1")) {
            ps.setString(1, permission);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        }
    } else {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT id FROM resource WHERE tenant_id = ? AND permission = ? LIMIT 1")) {
            ps.setLong(1, tenantId);
            ps.setString(2, permission);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        }
    }
    return null;
}

Long findRoleIdByCode(Connection connection, Long tenantId, String roleCode) throws SQLException {
    try (PreparedStatement ps = connection.prepareStatement(
            "SELECT id FROM role WHERE tenant_id = ? AND code = ? LIMIT 1")) {
        ps.setLong(1, tenantId);
        ps.setString(2, roleCode);
        try (ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return rs.getLong(1);
            }
        }
    }
    return null;
}

void verifyDefaultSchedulingBootstrapTemplate(Connection connection) throws SQLException {
    Long defaultTenantId = findTenantIdByCode(connection, "default");
    if (defaultTenantId == null) {
        throw new IllegalStateException("默认租户 default 不存在，无法作为调度 E2E 权限模板来源");
    }

    Long adminRoleId = findRoleIdByCode(connection, defaultTenantId, "ROLE_TENANT_ADMIN");
    if (adminRoleId == null) {
        throw new IllegalStateException("默认租户缺少 ROLE_TENANT_ADMIN，无法作为调度 E2E 权限模板来源");
    }

    String[] requiredAuthorities = new String[] {
        "scheduling:console:view",
        "scheduling:console:config",
        "scheduling:run:control",
        "scheduling:audit:view",
        "scheduling:cluster:view",
        "scheduling:*"
    };
    for (String authority : requiredAuthorities) {
        if (findResourceIdByPermission(connection, defaultTenantId, authority) == null) {
            throw new IllegalStateException("默认租户缺少调度 authority 模板资源: " + authority);
        }
    }

    Long wildcardResourceId = findResourceIdByPermission(connection, defaultTenantId, "scheduling:*");
    boolean wildcardBound = false;
    try (PreparedStatement ps = connection.prepareStatement(
            """
            SELECT 1
            FROM role_permission rp
            JOIN permission p
              ON p.id = rp.permission_id
             AND p.normalized_tenant_id = rp.normalized_tenant_id
            JOIN resource res
              ON res.id = ?
             AND res.tenant_id = rp.tenant_id
             AND res.normalized_tenant_id = rp.normalized_tenant_id
            WHERE rp.tenant_id = ?
              AND rp.role_id = ?
              AND p.permission_code = res.permission
            LIMIT 1
            """)) {
        ps.setLong(1, wildcardResourceId);
        ps.setLong(2, defaultTenantId);
        ps.setLong(3, adminRoleId);
        try (ResultSet rs = ps.executeQuery()) {
            wildcardBound = rs.next();
        }
    }
    if (!wildcardBound) {
        throw new IllegalStateException("默认租户 ROLE_TENANT_ADMIN 未绑定 scheduling:*，调度 E2E 权限模板不完整");
    }

    System.out.println("Verified default scheduling bootstrap template: tenant=default");
}

Long ensureAuthorityResource(Connection connection, Long tenantId, String authority, String title) throws SQLException {
    Long resourceId = findResourceId(connection, tenantId, authority);
    if (resourceId != null) {
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE resource SET permission = ?, title = ?, hidden = 1, type = 2, enabled = 1, updated_at = NOW() WHERE id = ? AND tenant_id = ?")) {
            ps.setString(1, authority);
            ps.setString(2, title);
            ps.setLong(3, resourceId);
            ps.setLong(4, tenantId);
            ps.executeUpdate();
        }
        return resourceId;
    }

    Long schedulingParentId = findResourceId(connection, tenantId, "scheduling");
    try (PreparedStatement ps = connection.prepareStatement(
            "INSERT INTO resource (tenant_id, name, url, uri, method, icon, show_icon, sort, component, redirect, hidden, keep_alive, title, permission, type, parent_id, enabled, created_at, updated_at) " +
                    "VALUES (?, ?, '', '', '', '', 0, 999, '', '', 1, 0, ?, ?, 2, ?, 1, NOW(), NOW())",
            Statement.RETURN_GENERATED_KEYS)) {
        ps.setLong(1, tenantId);
        ps.setString(2, authority);
        ps.setString(3, title);
        ps.setString(4, authority);
        if (schedulingParentId == null) {
            ps.setNull(5, Types.BIGINT);
        } else {
            ps.setLong(5, schedulingParentId);
        }
        ps.executeUpdate();
        try (ResultSet rs = ps.getGeneratedKeys()) {
            if (rs.next()) {
                return rs.getLong(1);
            }
        }
    }
    throw new IllegalStateException("创建调度 authority 资源失败: " + authority);
}

Long ensureHiddenAuthorityResource(Connection connection, Long tenantId, String authority, String title) throws SQLException {
    Long resourceId = findResourceIdByPermission(connection, tenantId, authority);
    if (resourceId == null) {
        resourceId = findResourceId(connection, tenantId, authority);
    }
    if (resourceId != null) {
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE resource SET name = ?, permission = ?, title = ?, hidden = 1, type = 2, resource_level = ?, enabled = 1, updated_at = NOW() WHERE id = ?")) {
            ps.setString(1, authority);
            ps.setString(2, authority);
            ps.setString(3, title);
            ps.setString(4, tenantId == null ? "PLATFORM" : "TENANT");
            ps.setLong(5, resourceId);
            ps.executeUpdate();
        }
        return resourceId;
    }

    try (PreparedStatement ps = connection.prepareStatement(
            "INSERT INTO resource (tenant_id, name, url, uri, method, icon, show_icon, sort, component, redirect, hidden, keep_alive, title, permission, type, parent_id, enabled, resource_level, created_at, updated_at) " +
                    "VALUES (?, ?, '', '', '', '', 0, 999, '', '', 1, 0, ?, ?, 2, NULL, 1, ?, NOW(), NOW())",
            Statement.RETURN_GENERATED_KEYS)) {
        if (tenantId == null) {
            ps.setNull(1, Types.BIGINT);
        } else {
            ps.setLong(1, tenantId);
        }
        ps.setString(2, authority);
        ps.setString(3, title);
        ps.setString(4, authority);
        ps.setString(5, tenantId == null ? "PLATFORM" : "TENANT");
        ps.executeUpdate();
        try (ResultSet rs = ps.getGeneratedKeys()) {
            if (rs.next()) {
                return rs.getLong(1);
            }
        }
    }
    throw new IllegalStateException("创建治理 authority 资源失败: " + authority);
}

void ensureRolePermissionBinding(Connection connection, Long tenantId, Long roleId, Long resourceId) throws SQLException {
    try (PreparedStatement ps = connection.prepareStatement(
            """
            INSERT IGNORE INTO role_permission (tenant_id, role_id, permission_id)
            SELECT ?, ?, p.id
            FROM resource r
            JOIN permission p
              ON p.permission_code = r.permission
             AND p.normalized_tenant_id = r.normalized_tenant_id
            WHERE r.id = ?
              AND p.enabled = 1
            """)) {
        if (tenantId == null) {
            ps.setNull(1, Types.BIGINT);
        } else {
            ps.setLong(1, tenantId);
        }
        ps.setLong(2, roleId);
        ps.setLong(3, resourceId);
        ps.executeUpdate();
    }
}

void ensureSchedulingAdminAuthority(Connection connection, Long tenantId, Long roleId) throws SQLException {
    Long wildcardResourceId = ensureAuthorityResource(connection, tenantId, "scheduling:*", "调度全权限");
    ensureRolePermissionBinding(connection, tenantId, roleId, wildcardResourceId);
    // HeaderBar 打开「切换作用域」时会拉取 ORG/DEPT 选项（GET /sys/org/list）；调度 E2E 身份需具备读权限，否则会 403 并被前端导向异常页。
    Long orgListResourceId = ensureHiddenAuthorityResource(connection, tenantId, "system:org:list", "组织列表");
    ensureRolePermissionBinding(connection, tenantId, roleId, orgListResourceId);
}

void ensurePlatformGovernanceAuthorities(Connection connection, Long tenantId, Long roleId) throws SQLException {
    String[][] authorities = new String[][] {
            {"system:tenant:list", "租户列表访问"},
            {"system:tenant:view", "租户详情访问"},
            {"system:tenant:freeze", "租户冻结"},
            {"system:tenant:unfreeze", "租户解冻"},
            {"system:tenant:decommission", "租户下线"},
            {"system:audit:auth:view", "授权审计查看"},
            {"system:audit:auth:export", "授权审计导出"},
            {"system:audit:authentication:view", "认证审计查看"},
            {"system:audit:authentication:export", "认证审计导出"}
    };
    for (String[] authority : authorities) {
        Long resourceId = ensureHiddenAuthorityResource(connection, tenantId, authority[0], authority[1]);
        ensureRolePermissionBinding(connection, tenantId, roleId, resourceId);
    }
}

try (Connection connection = DriverManager.getConnection(jdbcUrl, dbUser, dbPassword)) {
    connection.setAutoCommit(false);
    verifyDefaultSchedulingBootstrapTemplate(connection);

    boolean idempotentTokenTableExists = false;
    try (PreparedStatement ps = connection.prepareStatement(
            "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = ? AND table_name = 'sys_idempotent_token'")) {
        ps.setString(1, dbName);
        try (ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                idempotentTokenTableExists = rs.getInt(1) > 0;
            }
        }
    }
    if (!idempotentTokenTableExists) {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                CREATE TABLE sys_idempotent_token (
                  id VARCHAR(512) NOT NULL,
                  state VARCHAR(20) NOT NULL DEFAULT 'PENDING',
                  expire_time DATETIME NOT NULL,
                  created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  PRIMARY KEY (id)
                )
                """);
            statement.execute("CREATE INDEX idx_idempotent_expire_time ON sys_idempotent_token (expire_time)");
            statement.execute("CREATE INDEX idx_idempotent_state ON sys_idempotent_token (state)");
        }
    } else {
        boolean hasStateColumn = false;
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = ? AND table_name = 'sys_idempotent_token' AND column_name = 'state'")) {
            ps.setString(1, dbName);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    hasStateColumn = rs.getInt(1) > 0;
                }
            }
        }
        if (!hasStateColumn) {
            try (Statement statement = connection.createStatement()) {
                statement.execute(
                        "ALTER TABLE sys_idempotent_token ADD COLUMN state VARCHAR(20) NOT NULL DEFAULT 'PENDING' AFTER id");
                statement.execute("CREATE INDEX idx_idempotent_state ON sys_idempotent_token (state)");
            }
        }
    }

String normalizedTenantCode = tenantCode.trim().toLowerCase(Locale.ROOT);
    String platformTenantCode = System.getenv("E2E_PLATFORM_TENANT_CODE");
String skipSchedulingAdminAuth = System.getenv("E2E_SKIP_SCHEDULING_ADMIN_AUTH");
    String tenantDisplayName = "default".equals(normalizedTenantCode)
            ? "默认租户"
            : "E2E租户(" + normalizedTenantCode + ")";
    Long tenantId = ensureTenant(connection, normalizedTenantCode, tenantDisplayName);

    Long userId = null;
    Long existingUserTenantId = null;
    try (PreparedStatement ps = connection.prepareStatement(
            "SELECT id, tenant_id FROM user WHERE username = ? LIMIT 1")) {
        ps.setString(1, username);
        try (ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                userId = rs.getLong(1);
                existingUserTenantId = rs.getLong(2);
            }
        }
    }
    if (userId == null) {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO user (tenant_id, username, nickname, enabled, account_non_expired, account_non_locked, credentials_non_expired, failed_login_count) VALUES (?, ?, ?, true, true, true, true, 0)",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, tenantId);
            ps.setString(2, username);
            ps.setString(3, "E2E管理员");
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    userId = rs.getLong(1);
                }
            }
        }
    }
    if (userId == null) {
        throw new IllegalStateException("创建 E2E 用户失败: " + username);
    }

    // 共享测试库约定：user.username 全局唯一。若同名行落在其它 tenant，迁移到当前 E2E tenant，
    // 否则按租户登录会解析失败（幂等）。
    if (existingUserTenantId != null && !existingUserTenantId.equals(tenantId)) {
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE user SET tenant_id = ? WHERE id = ?")) {
            ps.setLong(1, tenantId);
            ps.setLong(2, userId);
            ps.executeUpdate();
        }
        // authentication methods 也依赖 tenant_id
        // 若目标 tenant 已存在同一 user_id 的 authentication_method（历史残留/重复执行），会触发唯一约束。
        // 先删除目标 tenant 的同一 user_id 记录，再迁移 tenant_id，保证幂等。
        try (PreparedStatement ps = connection.prepareStatement(
                "DELETE FROM user_authentication_method WHERE tenant_id = ? AND user_id = ?")) {
            ps.setLong(1, tenantId);
            ps.setLong(2, userId);
            ps.executeUpdate();
        }
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE user_authentication_method SET tenant_id = ? WHERE user_id = ?")) {
            ps.setLong(1, tenantId);
            ps.setLong(2, userId);
            ps.executeUpdate();
        }
    }

    try (PreparedStatement ps = connection.prepareStatement(
            "UPDATE user SET enabled = true, account_non_expired = true, account_non_locked = true, credentials_non_expired = true, failed_login_count = 0, last_failed_login_at = NULL WHERE id = ?")) {
        ps.setLong(1, userId);
        ps.executeUpdate();
    }

    // tenant_user membership（AuthUserResolutionService 依赖该表判断租户内用户有效性）
    try (PreparedStatement ps = connection.prepareStatement(
            "INSERT IGNORE INTO tenant_user (tenant_id, user_id, status, is_default, joined_at, created_at, updated_at) " +
                    "VALUES (?, ?, 'ACTIVE', ?, NOW(), NOW(), NOW())")) {
        ps.setLong(1, tenantId);
        ps.setLong(2, userId);
        ps.setBoolean(3, true);
        ps.executeUpdate();
    }

    Long roleId = null;
    try (PreparedStatement ps = connection.prepareStatement(
            "SELECT id FROM role WHERE tenant_id = ? AND code = 'ROLE_TENANT_ADMIN' LIMIT 1")) {
        ps.setLong(1, tenantId);
        try (ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                roleId = rs.getLong(1);
            }
        }
    }
    if (roleId == null) {
        String roleDisplayName = "default".equals(tenantCode)
                ? "系统管理员"
                : "系统管理员(" + tenantCode + ")";
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO role (tenant_id, code, name, description, builtin, enabled) VALUES (?, 'ROLE_TENANT_ADMIN', ?, 'real e2e tenant admin role', true, true)",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, tenantId);
            ps.setString(2, roleDisplayName);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    roleId = rs.getLong(1);
                }
            }
        }
    }
    if (roleId == null) {
        throw new IllegalStateException("未找到或创建 ROLE_TENANT_ADMIN 失败");
    }

    try (PreparedStatement ps = connection.prepareStatement(
            "INSERT IGNORE INTO role_assignment (principal_type, principal_id, role_id, tenant_id, scope_type, scope_id, status, start_time, end_time, granted_by, granted_at) VALUES ('USER', ?, ?, ?, 'TENANT', ?, 'ACTIVE', NOW(), NULL, NULL, NOW())")) {
        ps.setLong(1, userId);
        ps.setLong(2, roleId);
        ps.setLong(3, tenantId);
        ps.setLong(4, tenantId);
        ps.executeUpdate();
    }
    if (!"true".equalsIgnoreCase(skipSchedulingAdminAuth)) {
        ensureSchedulingAdminAuthority(connection, tenantId, roleId);
    } else {
        System.out.println("Skipped scheduling admin authority bootstrap for user: " + username);
    }
    if (platformTenantCode != null && !platformTenantCode.isBlank()
            && normalizedTenantCode.equals(platformTenantCode.trim().toLowerCase(Locale.ROOT))) {
        // When the active tenant is configured as PLATFORM tenant, login scope becomes PLATFORM.
        // Provide at least one platform role assignment so AuthUserResolutionService can resolve the user.
        Long platformAdminRoleId = null;
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT id FROM role WHERE tenant_id IS NULL AND code = 'ROLE_PLATFORM_ADMIN' LIMIT 1")) {
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    platformAdminRoleId = rs.getLong(1);
                }
            }
        }
        if (platformAdminRoleId == null) {
            throw new IllegalStateException("缺少平台角色 ROLE_PLATFORM_ADMIN，无法完成 PLATFORM 登录所需赋权");
        }
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT IGNORE INTO role_assignment " +
                        "(principal_type, principal_id, role_id, tenant_id, scope_type, scope_id, status, start_time, end_time, granted_by, granted_at) " +
                        "VALUES ('USER', ?, ?, NULL, 'PLATFORM', NULL, 'ACTIVE', NOW(), NULL, NULL, NOW())")) {
            ps.setLong(1, userId);
            ps.setLong(2, platformAdminRoleId);
            ps.executeUpdate();
        }
        // Tenant management (/sys/tenants) is platform-scope only (TenantContext.activeScopeType=PLATFORM).
        // Bind system:tenant:* to ROLE_PLATFORM_ADMIN; resource/permission rows use this tenantId so joins match generated normalized_tenant_id.
        ensurePlatformGovernanceAuthorities(connection, tenantId, platformAdminRoleId);
    }

    String passwordJson = "{\"password\":\"{noop}" + rawPassword + "\",\"created_by\":\"real-e2e\",\"hash_algorithm\":\"noop\",\"password_version\":1,\"password_changed_at\":\"" + Instant.now().toString() + "\"}";
    try (PreparedStatement ps = connection.prepareStatement(
            "INSERT INTO user_authentication_method (tenant_id, user_id, authentication_provider, authentication_type, authentication_configuration, is_primary_method, is_method_enabled, authentication_priority, created_at, updated_at) " +
                    "VALUES (?, ?, 'LOCAL', 'PASSWORD', CAST(? AS JSON), true, true, 0, NOW(), NOW()) " +
                    "ON DUPLICATE KEY UPDATE authentication_configuration = CAST(VALUES(authentication_configuration) AS JSON), is_primary_method = true, is_method_enabled = true, authentication_priority = 0, updated_at = NOW()")) {
        ps.setLong(1, tenantId);
        ps.setLong(2, userId);
        ps.setString(3, passwordJson);
        ps.executeUpdate();
    }

    String totpJson = "{\"digits\":6,\"issuer\":\"TinyOAuthServer\",\"period\":30,\"activated\":true,\"secretKey\":\"" + totpSecret + "\",\"otpauthUri\":\"otpauth://totp/TinyOAuthServer:" + username + "?secret=" + totpSecret + "&issuer=TinyOAuthServer&digits=6&period=30\"}";
    try (PreparedStatement ps = connection.prepareStatement(
            "INSERT INTO user_authentication_method (tenant_id, user_id, authentication_provider, authentication_type, authentication_configuration, is_primary_method, is_method_enabled, authentication_priority, created_at, updated_at) " +
                    "VALUES (?, ?, 'LOCAL', 'TOTP', CAST(? AS JSON), false, true, 1, NOW(), NOW()) " +
                    "ON DUPLICATE KEY UPDATE authentication_configuration = CAST(VALUES(authentication_configuration) AS JSON), is_primary_method = false, is_method_enabled = true, authentication_priority = 1, updated_at = NOW()")) {
        ps.setLong(1, tenantId);
        ps.setLong(2, userId);
        ps.setString(3, totpJson);
        ps.executeUpdate();
    }

    System.out.println("Prepared real scheduling e2e auth user: tenant=" + tenantCode + ", username=" + username);

    // =========================
    // 可选：准备“未绑定 TOTP 首绑专用”用户
    // =========================
    String bindTenantCode = System.getenv("E2E_BIND_TENANT_CODE");
    String bindUsername = System.getenv("E2E_BIND_USERNAME");
    String bindPassword = System.getenv("E2E_BIND_PASSWORD");

    if (bindUsername != null && !bindUsername.isBlank() && bindPassword != null && !bindPassword.isBlank()) {
        String effectiveBindTenantCode = (bindTenantCode != null && !bindTenantCode.isBlank())
                ? bindTenantCode
                : tenantCode;

        String normalizedBindTenantCode = effectiveBindTenantCode.trim().toLowerCase(Locale.ROOT);
        String bindTenantDisplayName = "default".equals(normalizedBindTenantCode)
                ? "默认租户"
                : "E2E租户(" + normalizedBindTenantCode + ")";
        Long bindTenantId = ensureTenant(connection, normalizedBindTenantCode, bindTenantDisplayName);

        Long bindUserId = null;
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT id FROM user WHERE tenant_id = ? AND username = ? LIMIT 1")) {
            ps.setLong(1, bindTenantId);
            ps.setString(2, bindUsername);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    bindUserId = rs.getLong(1);
                }
            }
        }
        if (bindUserId == null) {
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO user (tenant_id, username, nickname, enabled, account_non_expired, account_non_locked, credentials_non_expired, failed_login_count) VALUES (?, ?, ?, true, true, true, true, 0)",
                    Statement.RETURN_GENERATED_KEYS)) {
                ps.setLong(1, bindTenantId);
                ps.setString(2, bindUsername);
                ps.setString(3, "E2E首绑用户");
                ps.executeUpdate();
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (rs.next()) {
                        bindUserId = rs.getLong(1);
                    }
                }
            }
        }
        if (bindUserId == null) {
            throw new IllegalStateException("创建 E2E 首绑用户失败: " + bindUsername);
        }

        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE user SET enabled = true, account_non_expired = true, account_non_locked = true, credentials_non_expired = true, failed_login_count = 0, last_failed_login_at = NULL WHERE id = ? AND tenant_id = ?")) {
            ps.setLong(1, bindUserId);
            ps.setLong(2, bindTenantId);
            ps.executeUpdate();
        }

        Long bindRoleId = null;
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT id FROM role WHERE tenant_id = ? AND code = 'ROLE_TENANT_ADMIN' LIMIT 1")) {
            ps.setLong(1, bindTenantId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    bindRoleId = rs.getLong(1);
                }
            }
        }
        if (bindRoleId == null) {
            String bindRoleDisplayName = "default".equals(effectiveBindTenantCode)
                    ? "系统管理员(bind)"
                    : "系统管理员(bind-" + effectiveBindTenantCode + ")";
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO role (tenant_id, code, name, description, builtin, enabled) VALUES (?, 'ROLE_TENANT_ADMIN', ?, 'real e2e tenant admin role (bind)', true, true)",
                    Statement.RETURN_GENERATED_KEYS)) {
                ps.setLong(1, bindTenantId);
                ps.setString(2, bindRoleDisplayName);
                ps.executeUpdate();
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (rs.next()) {
                        bindRoleId = rs.getLong(1);
                    }
                }
            }
        }
        if (bindRoleId == null) {
            throw new IllegalStateException("未找到或创建 ROLE_TENANT_ADMIN (bind) 失败");
        }

        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT IGNORE INTO role_assignment (principal_type, principal_id, role_id, tenant_id, scope_type, scope_id, status, start_time, end_time, granted_by, granted_at) VALUES ('USER', ?, ?, ?, 'TENANT', ?, 'ACTIVE', NOW(), NULL, NULL, NOW())")) {
            ps.setLong(1, bindUserId);
            ps.setLong(2, bindRoleId);
            ps.setLong(3, bindTenantId);
            ps.setLong(4, bindTenantId);
            ps.executeUpdate();
        }
        ensureSchedulingAdminAuthority(connection, bindTenantId, bindRoleId);

        String bindPasswordJson = "{\"password\":\"{noop}" + bindPassword + "\",\"created_by\":\"real-e2e-bind\",\"hash_algorithm\":\"noop\",\"password_version\":1,\"password_changed_at\":\"" + Instant.now().toString() + "\"}";
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO user_authentication_method (tenant_id, user_id, authentication_provider, authentication_type, authentication_configuration, is_primary_method, is_method_enabled, authentication_priority, created_at, updated_at) " +
                        "VALUES (?, ?, 'LOCAL', 'PASSWORD', CAST(? AS JSON), true, true, 0, NOW(), NOW()) " +
                        "ON DUPLICATE KEY UPDATE authentication_configuration = CAST(VALUES(authentication_configuration) AS JSON), is_primary_method = true, is_method_enabled = true, authentication_priority = 0, updated_at = NOW()")) {
            ps.setLong(1, bindTenantId);
            ps.setLong(2, bindUserId);
            ps.setString(3, bindPasswordJson);
            ps.executeUpdate();
        }

        // 删除该用户所有 LOCAL/TOTP 记录，确保每次 real E2E 运行前都是“未绑定 TOTP”的首绑状态。
        try (PreparedStatement ps = connection.prepareStatement(
                "DELETE FROM user_authentication_method WHERE tenant_id = ? AND user_id = ? AND authentication_provider = 'LOCAL' AND authentication_type = 'TOTP'")) {
            ps.setLong(1, bindTenantId);
            ps.setLong(2, bindUserId);
            ps.executeUpdate();
        }

        System.out.println("Prepared real e2e bind user without TOTP: tenant=" + effectiveBindTenantCode + ", username=" + bindUsername);
    }

    // =========================
    // 可选：准备“调度只读”用户
    // =========================
    if (readonlyUsername != null && !readonlyUsername.isBlank()
            && readonlyPassword != null && !readonlyPassword.isBlank()
            && readonlyTotpSecret != null && !readonlyTotpSecret.isBlank()) {
        String effectiveReadonlyTenantCode = (readonlyTenantCode != null && !readonlyTenantCode.isBlank())
                ? readonlyTenantCode
                : tenantCode;

        String normalizedReadonlyTenantCode = effectiveReadonlyTenantCode.trim().toLowerCase(Locale.ROOT);
        String readonlyTenantDisplayName = "default".equals(normalizedReadonlyTenantCode)
                ? "默认租户"
                : "E2E租户(" + normalizedReadonlyTenantCode + ")";
        Long readonlyTenantId = ensureTenant(connection, normalizedReadonlyTenantCode, readonlyTenantDisplayName);

        Long readonlyUserId = null;
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT id FROM user WHERE tenant_id = ? AND username = ? LIMIT 1")) {
            ps.setLong(1, readonlyTenantId);
            ps.setString(2, readonlyUsername);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    readonlyUserId = rs.getLong(1);
                }
            }
        }
        if (readonlyUserId == null) {
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO user (tenant_id, username, nickname, enabled, account_non_expired, account_non_locked, credentials_non_expired, failed_login_count) VALUES (?, ?, ?, true, true, true, true, 0)",
                    Statement.RETURN_GENERATED_KEYS)) {
                ps.setLong(1, readonlyTenantId);
                ps.setString(2, readonlyUsername);
                ps.setString(3, "E2E调度只读");
                ps.executeUpdate();
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (rs.next()) {
                        readonlyUserId = rs.getLong(1);
                    }
                }
            }
        }
        if (readonlyUserId == null) {
            throw new IllegalStateException("创建 E2E 调度只读用户失败: " + readonlyUsername);
        }

        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE user SET enabled = true, account_non_expired = true, account_non_locked = true, credentials_non_expired = true, failed_login_count = 0, last_failed_login_at = NULL WHERE id = ? AND tenant_id = ?")) {
            ps.setLong(1, readonlyUserId);
            ps.setLong(2, readonlyTenantId);
            ps.executeUpdate();
        }

        Long readonlyRoleId = null;
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT id FROM role WHERE tenant_id = ? AND code = 'ROLE_SCHEDULING_READONLY' LIMIT 1")) {
            ps.setLong(1, readonlyTenantId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    readonlyRoleId = rs.getLong(1);
                }
            }
        }
        if (readonlyRoleId == null) {
            String readonlyRoleDisplayName = "default".equals(effectiveReadonlyTenantCode)
                    ? "调度只读"
                    : "调度只读(" + effectiveReadonlyTenantCode + ")";
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO role (tenant_id, code, name, description, builtin, enabled) VALUES (?, 'ROLE_SCHEDULING_READONLY', ?, 'real e2e scheduling readonly role', false, true)",
                    Statement.RETURN_GENERATED_KEYS)) {
                ps.setLong(1, readonlyTenantId);
                ps.setString(2, readonlyRoleDisplayName);
                ps.executeUpdate();
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (rs.next()) {
                        readonlyRoleId = rs.getLong(1);
                    }
                }
            }
        }
        if (readonlyRoleId == null) {
            throw new IllegalStateException("未找到或创建 ROLE_SCHEDULING_READONLY 失败");
        }

        Long schedulingReadResourceId = ensureAuthorityResource(connection, readonlyTenantId, "scheduling:console:view", "调度控制面查看权限");
        ensureRolePermissionBinding(connection, readonlyTenantId, readonlyRoleId, schedulingReadResourceId);

        try (PreparedStatement ps = connection.prepareStatement(
                "DELETE FROM role_assignment WHERE tenant_id = ? AND principal_type = 'USER' AND principal_id = ? AND role_id <> ?")) {
            ps.setLong(1, readonlyTenantId);
            ps.setLong(2, readonlyUserId);
            ps.setLong(3, readonlyRoleId);
            ps.executeUpdate();
        }
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT IGNORE INTO role_assignment (principal_type, principal_id, role_id, tenant_id, scope_type, scope_id, status, start_time, end_time, granted_by, granted_at) VALUES ('USER', ?, ?, ?, 'TENANT', ?, 'ACTIVE', NOW(), NULL, NULL, NOW())")) {
            ps.setLong(1, readonlyUserId);
            ps.setLong(2, readonlyRoleId);
            ps.setLong(3, readonlyTenantId);
            ps.setLong(4, readonlyTenantId);
            ps.executeUpdate();
        }

        String readonlyPasswordJson = "{\"password\":\"{noop}" + readonlyPassword + "\",\"created_by\":\"real-e2e-readonly\",\"hash_algorithm\":\"noop\",\"password_version\":1,\"password_changed_at\":\"" + Instant.now().toString() + "\"}";
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO user_authentication_method (tenant_id, user_id, authentication_provider, authentication_type, authentication_configuration, is_primary_method, is_method_enabled, authentication_priority, created_at, updated_at) " +
                        "VALUES (?, ?, 'LOCAL', 'PASSWORD', CAST(? AS JSON), true, true, 0, NOW(), NOW()) " +
                        "ON DUPLICATE KEY UPDATE authentication_configuration = CAST(VALUES(authentication_configuration) AS JSON), is_primary_method = true, is_method_enabled = true, authentication_priority = 0, updated_at = NOW()")) {
            ps.setLong(1, readonlyTenantId);
            ps.setLong(2, readonlyUserId);
            ps.setString(3, readonlyPasswordJson);
            ps.executeUpdate();
        }

        String readonlyTotpJson = "{\"digits\":6,\"issuer\":\"TinyOAuthServer\",\"period\":30,\"activated\":true,\"secretKey\":\"" + readonlyTotpSecret + "\",\"otpauthUri\":\"otpauth://totp/TinyOAuthServer:" + readonlyUsername + "?secret=" + readonlyTotpSecret + "&issuer=TinyOAuthServer&digits=6&period=30\"}";
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO user_authentication_method (tenant_id, user_id, authentication_provider, authentication_type, authentication_configuration, is_primary_method, is_method_enabled, authentication_priority, created_at, updated_at) " +
                        "VALUES (?, ?, 'LOCAL', 'TOTP', CAST(? AS JSON), false, true, 1, NOW(), NOW()) " +
                        "ON DUPLICATE KEY UPDATE authentication_configuration = CAST(VALUES(authentication_configuration) AS JSON), is_primary_method = false, is_method_enabled = true, authentication_priority = 1, updated_at = NOW()")) {
            ps.setLong(1, readonlyTenantId);
            ps.setLong(2, readonlyUserId);
            ps.setString(3, readonlyTotpJson);
            ps.executeUpdate();
        }

        System.out.println("Prepared real scheduling readonly e2e user: tenant=" + effectiveReadonlyTenantCode + ", username=" + readonlyUsername);
    }

    connection.commit();
}
/exit
EOF

jshell --class-path "$(cat "${CLASSPATH_FILE}")" "${JSHELL_SCRIPT_FILE}"
rm -f "${JSHELL_SCRIPT_FILE}"
