package com.tiny.platform.core.oauth.security;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Repository
public class PermissionAuthorityReadRepository {

    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    public PermissionAuthorityReadRepository(NamedParameterJdbcTemplate namedParameterJdbcTemplate) {
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
    }

    public Set<String> findPermissionCodesByRoleIds(Set<Long> roleIds, Long tenantId) {
        if (roleIds == null || roleIds.isEmpty()) {
            return Set.of();
        }
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("roleIds", roleIds)
                .addValue("normalizedTenantId", normalizeTenantId(tenantId));
        List<String> rows = namedParameterJdbcTemplate.query(
                """
                SELECT p.permission_code
                FROM role_permission rp
                JOIN permission p ON p.id = rp.permission_id
                WHERE rp.normalized_tenant_id = :normalizedTenantId
                  AND p.normalized_tenant_id = :normalizedTenantId
                  AND rp.role_id IN (:roleIds)
                """,
                params,
                (rs, rowNum) -> rs.getString("permission_code")
        );
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (String row : rows) {
            if (row == null) {
                continue;
            }
            String normalized = row.trim();
            if (!normalized.isEmpty()) {
                result.add(normalized);
            }
        }
        return result;
    }

    public Set<String> findEnabledPermissionCodesByRoleIds(Set<Long> roleIds, Long tenantId) {
        if (roleIds == null || roleIds.isEmpty()) {
            return Set.of();
        }
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("roleIds", roleIds)
            .addValue("normalizedTenantId", normalizeTenantId(tenantId));

        List<String> rows = namedParameterJdbcTemplate.query(
            """
            SELECT DISTINCT p.permission_code
            FROM role_permission rp
            JOIN permission p ON p.id = rp.permission_id
            WHERE rp.normalized_tenant_id = :normalizedTenantId
              AND p.normalized_tenant_id = :normalizedTenantId
              AND rp.role_id IN (:roleIds)
              AND p.enabled = true
            """,
            params,
            (rs, rowNum) -> rs.getString("permission_code")
        );

        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (String row : rows) {
            if (row == null) {
                continue;
            }
            String normalized = row.trim();
            if (!normalized.isEmpty()) {
                result.add(normalized);
            }
        }
        return result;
    }

    public Map<String, Boolean> findEnabledFlagsByPermissionCodes(Set<String> permissionCodes, Long tenantId) {
        if (permissionCodes == null || permissionCodes.isEmpty()) {
            return Map.of();
        }
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("permissionCodes", permissionCodes)
                .addValue("normalizedTenantId", normalizeTenantId(tenantId));
        return namedParameterJdbcTemplate.query(
                """
                SELECT p.permission_code, p.enabled
                FROM permission p
                WHERE p.normalized_tenant_id = :normalizedTenantId
                  AND p.permission_code IN (:permissionCodes)
                """,
                params,
                rs -> {
                    Map<String, Boolean> flags = new LinkedHashMap<>();
                    while (rs.next()) {
                        String code = rs.getString("permission_code");
                        if (code == null) {
                            continue;
                        }
                        String normalized = code.trim();
                        if (normalized.isEmpty()) {
                            continue;
                        }
                        flags.put(normalized, rs.getBoolean("enabled"));
                    }
                    return flags;
                }
        );
    }

    private long normalizeTenantId(Long tenantId) {
        return tenantId == null ? 0L : tenantId;
    }
}
