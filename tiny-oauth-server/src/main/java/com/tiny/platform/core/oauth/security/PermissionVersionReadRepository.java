package com.tiny.platform.core.oauth.security;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Set;

@Repository
public class PermissionVersionReadRepository {

    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    public PermissionVersionReadRepository(NamedParameterJdbcTemplate namedParameterJdbcTemplate) {
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
    }

    public List<PermissionSnapshot> findPermissionSnapshotsByRoleIds(Set<Long> roleIds, Long tenantId) {
        if (roleIds == null || roleIds.isEmpty()) {
            return List.of();
        }
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("roleIds", roleIds)
                .addValue("normalizedTenantId", normalizeTenantId(tenantId));
        return namedParameterJdbcTemplate.query(
                """
                SELECT DISTINCT p.permission_code, p.enabled, p.tenant_id, p.updated_at
                FROM role_permission rp
                JOIN permission p ON p.id = rp.permission_id
                WHERE rp.normalized_tenant_id = :normalizedTenantId
                  AND p.normalized_tenant_id = :normalizedTenantId
                  AND rp.role_id IN (:roleIds)
                """,
                params,
                (rs, rowNum) -> new PermissionSnapshot(
                        trimToNull(rs.getString("permission_code")),
                        rs.getBoolean("enabled"),
                        nullableLong(rs.getObject("tenant_id")),
                        rs.getObject("updated_at", LocalDateTime.class)
                )
        );
    }

    public List<RoleHierarchySnapshot> findRoleHierarchySnapshotsByRoleIds(Collection<Long> roleIds, Long tenantId) {
        if (roleIds == null || roleIds.isEmpty()) {
            return List.of();
        }
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("roleIds", roleIds)
                .addValue("tenantId", tenantId);
        return namedParameterJdbcTemplate.query(
                """
                SELECT DISTINCT parent_role_id, child_role_id, tenant_id, updated_at
                FROM role_hierarchy
                WHERE tenant_id <=> :tenantId
                  AND (parent_role_id IN (:roleIds) OR child_role_id IN (:roleIds))
                """,
                params,
                (rs, rowNum) -> new RoleHierarchySnapshot(
                        nullableLong(rs.getObject("parent_role_id")),
                        nullableLong(rs.getObject("child_role_id")),
                        nullableLong(rs.getObject("tenant_id")),
                        rs.getObject("updated_at", LocalDateTime.class)
                )
        );
    }

    private long normalizeTenantId(Long tenantId) {
        return tenantId == null ? 0L : tenantId;
    }

    private static Long nullableLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.valueOf(value.toString());
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    public record PermissionSnapshot(String permissionCode, boolean enabled, Long tenantId, LocalDateTime updatedAt) {
    }

    public record RoleHierarchySnapshot(Long parentRoleId, Long childRoleId, Long tenantId, LocalDateTime updatedAt) {
    }
}
