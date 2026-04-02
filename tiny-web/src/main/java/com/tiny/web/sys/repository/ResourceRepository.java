package com.tiny.web.sys.repository;

import java.util.List;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 演示模块：直接基于 canonical carrier（menu / ui_action / api_endpoint）做访问检查，
 * 不再回退 legacy {@code resource} 总表。
 */
@Repository
public class ResourceRepository {

    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    public ResourceRepository(NamedParameterJdbcTemplate namedParameterJdbcTemplate) {
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
    }

    public List<GrantedResourceAccessRow> findGrantedResourceAccessRows(long tenantId, String role) {
        return namedParameterJdbcTemplate.query("""
            SELECT DISTINCT carrier_access.path AS path, carrier_access.method AS method
            FROM role ro
            JOIN role_permission rp
              ON rp.role_id = ro.id
             AND rp.tenant_id = :tenantId
            JOIN permission p
              ON p.id = rp.permission_id
             AND p.normalized_tenant_id = rp.normalized_tenant_id
            JOIN (
              SELECT
                IFNULL(m.tenant_id, 0) AS normalized_tenant_id,
                m.required_permission_id AS permission_id,
                m.path AS path,
                '' AS method
              FROM menu m
              WHERE m.enabled = 1
                AND m.required_permission_id IS NOT NULL
                AND TRIM(IFNULL(m.path, '')) <> ''
              UNION ALL
              SELECT
                IFNULL(a.tenant_id, 0) AS normalized_tenant_id,
                a.required_permission_id AS permission_id,
                a.page_path AS path,
                '' AS method
              FROM ui_action a
              WHERE a.enabled = 1
                AND a.required_permission_id IS NOT NULL
                AND TRIM(IFNULL(a.page_path, '')) <> ''
              UNION ALL
              SELECT
                IFNULL(e.tenant_id, 0) AS normalized_tenant_id,
                e.required_permission_id AS permission_id,
                e.uri AS path,
                e.method AS method
              FROM api_endpoint e
              WHERE e.enabled = 1
                AND e.required_permission_id IS NOT NULL
                AND TRIM(IFNULL(e.uri, '')) <> ''
            ) carrier_access
              ON carrier_access.permission_id = p.id
             AND carrier_access.normalized_tenant_id = p.normalized_tenant_id
            WHERE ro.tenant_id = :tenantId
              AND (ro.code = :role OR ro.name = :role)
            """,
            new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("role", role),
            (rs, rowNum) -> new GrantedResourceAccessRow(
                rs.getString("path"),
                rs.getString("method")
            )
        );
    }
}
