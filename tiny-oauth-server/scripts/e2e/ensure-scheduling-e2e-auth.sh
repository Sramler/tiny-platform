#!/usr/bin/env bash
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

CLASSPATH_FILE="${TMPDIR:-/tmp}/tiny-oauth-e2e-runtime-classpath.txt"

mvn -pl tiny-oauth-server -q dependency:build-classpath \
  -Dmdep.outputFile="${CLASSPATH_FILE}" \
  -Dmdep.includeScope=runtime \
  -f "${WORKSPACE_ROOT}/pom.xml" >/dev/null

jshell --class-path "$(cat "${CLASSPATH_FILE}")" <<EOF
import java.sql.*;
import java.time.*;

String dbHost = System.getenv("E2E_EFFECTIVE_DB_HOST");
String dbPort = System.getenv("E2E_EFFECTIVE_DB_PORT");
String dbName = System.getenv("E2E_EFFECTIVE_DB_NAME");
String dbUser = System.getenv("E2E_EFFECTIVE_DB_USER");
String dbPassword = System.getenv("E2E_EFFECTIVE_DB_PASSWORD");
String tenantCode = System.getenv("E2E_EFFECTIVE_TENANT_CODE");
String username = System.getenv("E2E_EFFECTIVE_USERNAME");
String rawPassword = System.getenv("E2E_EFFECTIVE_PASSWORD");
String totpSecret = System.getenv("E2E_EFFECTIVE_TOTP_SECRET");
String jdbcUrl = "jdbc:mysql://" + dbHost + ":" + dbPort + "/" + dbName + "?useSSL=false&allowPublicKeyRetrieval=true&useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai";

try (Connection connection = DriverManager.getConnection(jdbcUrl, dbUser, dbPassword)) {
    connection.setAutoCommit(false);

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
        throw new IllegalStateException("未找到租户: " + tenantCode);
    }

    Long userId = null;
    try (PreparedStatement ps = connection.prepareStatement(
            "SELECT id FROM user WHERE tenant_id = ? AND username = ? LIMIT 1")) {
        ps.setLong(1, tenantId);
        ps.setString(2, username);
        try (ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                userId = rs.getLong(1);
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

    try (PreparedStatement ps = connection.prepareStatement(
            "UPDATE user SET enabled = true, account_non_expired = true, account_non_locked = true, credentials_non_expired = true, failed_login_count = 0, last_failed_login_at = NULL WHERE id = ? AND tenant_id = ?")) {
        ps.setLong(1, userId);
        ps.setLong(2, tenantId);
        ps.executeUpdate();
    }

    Long roleId = null;
    try (PreparedStatement ps = connection.prepareStatement(
            "SELECT id FROM role WHERE tenant_id = ? AND code = 'ROLE_ADMIN' LIMIT 1")) {
        ps.setLong(1, tenantId);
        try (ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                roleId = rs.getLong(1);
            }
        }
    }
    if (roleId == null) {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO role (tenant_id, code, name, description, builtin, enabled) VALUES (?, 'ROLE_ADMIN', '系统管理员', 'real e2e admin role', true, true)",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, tenantId);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    roleId = rs.getLong(1);
                }
            }
        }
    }
    if (roleId == null) {
        throw new IllegalStateException("未找到或创建 ROLE_ADMIN 失败");
    }

    try (PreparedStatement ps = connection.prepareStatement(
            "INSERT IGNORE INTO user_role (tenant_id, user_id, role_id) VALUES (?, ?, ?)")) {
        ps.setLong(1, tenantId);
        ps.setLong(2, userId);
        ps.setLong(3, roleId);
        ps.executeUpdate();
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

    connection.commit();
    System.out.println("Prepared real scheduling e2e auth user: tenant=" + tenantCode + ", username=" + username);
}
/exit
EOF
