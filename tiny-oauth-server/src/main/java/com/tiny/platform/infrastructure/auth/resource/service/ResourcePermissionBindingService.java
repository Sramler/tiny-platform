package com.tiny.platform.infrastructure.auth.resource.service;

import com.tiny.platform.infrastructure.core.exception.code.ErrorCode;
import com.tiny.platform.infrastructure.core.exception.exception.BusinessException;
import com.tiny.platform.infrastructure.auth.resource.domain.Resource;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 将兼容资源聚合与权限主数据显式绑定，避免运行态继续依赖 permission_code 字符串拼接。
 *
 * <p>历史方法名里仍保留 {@code Resources}，但实际回填来源已经切到
 * {@code menu/ui_action/api_endpoint} 三类 carrier 表。这样即使 legacy
 * {@code resource} 总表下线，权限主数据和 carrier.required_permission_id
 * 仍可继续完整回填。</p>
 */
@Service
public class ResourcePermissionBindingService {

    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    public ResourcePermissionBindingService(NamedParameterJdbcTemplate namedParameterJdbcTemplate) {
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
    }

    public void bindResource(Resource resource, Long actorUserId) {
        if (resource == null) {
            return;
        }
        Long requiredPermissionId = resource.getRequiredPermissionId();
        if (requiredPermissionId != null) {
            String resolvedPermissionCode = findPermissionCodeById(resource.getTenantId(), requiredPermissionId);
            if (resolvedPermissionCode == null) {
                throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "requiredPermissionId does not exist in current tenant scope: " + requiredPermissionId
                );
            }
            resource.setPermission(resolvedPermissionCode);
            return;
        }
        resource.setRequiredPermissionId(null);
    }

    public void backfillPermissionCatalogFromResources(Long tenantId) {
        namedParameterJdbcTemplate.update("""
            INSERT IGNORE INTO `permission` (
              `permission_code`,
              `permission_name`,
              `module_code`,
              `action_code`,
              `permission_type`,
              `description`,
              `enabled`,
              `built_in_flag`,
              `tenant_id`,
              `created_by`,
              `created_at`,
              `updated_by`,
              `updated_at`
            )
            SELECT
              grouped.`permission_code`,
              grouped.`permission_code` AS `permission_name`,
              grouped.`module_code`,
              grouped.`action_code`,
              grouped.`permission_type`,
              '从 carrier 载体同步生成' AS `description`,
              grouped.`enabled`,
              0 AS `built_in_flag`,
              :tenantId AS `tenant_id`,
              NULL AS `created_by`,
              NOW() AS `created_at`,
              NULL AS `updated_by`,
              NOW() AS `updated_at`
            FROM (
              SELECT
                MIN(carriers.`permission`) AS `permission_code`,
                CASE
                  WHEN MIN(carriers.`permission`) REGEXP '^[^:[:space:]]+(:[^:[:space:]]+){2,}$'
                    THEN SUBSTRING_INDEX(MIN(carriers.`permission`), ':', 1)
                  ELSE NULL
                END AS `module_code`,
                CASE
                  WHEN MIN(carriers.`permission`) REGEXP '^[^:[:space:]]+(:[^:[:space:]]+){2,}$'
                    THEN SUBSTRING_INDEX(MIN(carriers.`permission`), ':', -1)
                  ELSE NULL
                END AS `action_code`,
                CASE
                  WHEN MAX(CASE WHEN carriers.`carrier_type` = 'API' THEN 1 ELSE 0 END) = 1 THEN 'API'
                  WHEN MAX(CASE WHEN carriers.`carrier_type` = 'BUTTON' THEN 1 ELSE 0 END) = 1 THEN 'BUTTON'
                  WHEN MAX(CASE WHEN carriers.`carrier_type` = 'MENU' THEN 1 ELSE 0 END) = 1 THEN 'MENU'
                  ELSE 'OTHER'
                END AS `permission_type`,
                CASE
                  WHEN MAX(CASE WHEN carriers.`enabled` = 1 THEN 1 ELSE 0 END) = 1 THEN 1
                  ELSE 0
                END AS `enabled`
              FROM (
                SELECT
                  TRIM(m.`permission`) AS `permission`,
                  m.`enabled`,
                  'MENU' AS `carrier_type`
                FROM `menu` m
                WHERE ((:tenantId IS NULL AND m.`tenant_id` IS NULL) OR m.`tenant_id` = :tenantId)
                  AND m.`permission` IS NOT NULL
                  AND TRIM(m.`permission`) <> ''
                UNION ALL
                SELECT
                  TRIM(a.`permission`) AS `permission`,
                  a.`enabled`,
                  'BUTTON' AS `carrier_type`
                FROM `ui_action` a
                WHERE ((:tenantId IS NULL AND a.`tenant_id` IS NULL) OR a.`tenant_id` = :tenantId)
                  AND a.`permission` IS NOT NULL
                  AND TRIM(a.`permission`) <> ''
                UNION ALL
                SELECT
                  TRIM(e.`permission`) AS `permission`,
                  e.`enabled`,
                  'API' AS `carrier_type`
                FROM `api_endpoint` e
                WHERE ((:tenantId IS NULL AND e.`tenant_id` IS NULL) OR e.`tenant_id` = :tenantId)
                  AND e.`permission` IS NOT NULL
                  AND TRIM(e.`permission`) <> ''
              ) carriers
              GROUP BY carriers.`permission`
            ) grouped
            """, tenantParams(tenantId));
    }

    public int bindRequiredPermissionIdsForResources(Long tenantId) {
        MapSqlParameterSource params = tenantParams(tenantId);
        int updated = 0;
        updated += namedParameterJdbcTemplate.update("""
            UPDATE `menu` m
            JOIN `permission` p
              ON p.`normalized_tenant_id` = IFNULL(m.`tenant_id`, 0)
             AND p.`permission_code` = TRIM(m.`permission`)
            SET m.`required_permission_id` = p.`id`
            WHERE ((:tenantId IS NULL AND m.`tenant_id` IS NULL) OR m.`tenant_id` = :tenantId)
              AND m.`permission` IS NOT NULL
              AND TRIM(m.`permission`) <> ''
              AND (m.`required_permission_id` IS NULL OR m.`required_permission_id` <> p.`id`)
            """, params);
        updated += namedParameterJdbcTemplate.update("""
            UPDATE `ui_action` a
            JOIN `permission` p
              ON p.`normalized_tenant_id` = IFNULL(a.`tenant_id`, 0)
             AND p.`permission_code` = TRIM(a.`permission`)
            SET a.`required_permission_id` = p.`id`
            WHERE ((:tenantId IS NULL AND a.`tenant_id` IS NULL) OR a.`tenant_id` = :tenantId)
              AND a.`permission` IS NOT NULL
              AND TRIM(a.`permission`) <> ''
              AND (a.`required_permission_id` IS NULL OR a.`required_permission_id` <> p.`id`)
            """, params);
        updated += namedParameterJdbcTemplate.update("""
            UPDATE `api_endpoint` e
            JOIN `permission` p
              ON p.`normalized_tenant_id` = IFNULL(e.`tenant_id`, 0)
             AND p.`permission_code` = TRIM(e.`permission`)
            SET e.`required_permission_id` = p.`id`
            WHERE ((:tenantId IS NULL AND e.`tenant_id` IS NULL) OR e.`tenant_id` = :tenantId)
              AND e.`permission` IS NOT NULL
              AND TRIM(e.`permission`) <> ''
              AND (e.`required_permission_id` IS NULL OR e.`required_permission_id` <> p.`id`)
            """, params);
        return updated;
    }

    private String findPermissionCodeById(Long tenantId, Long permissionId) {
        List<String> permissionCodes = namedParameterJdbcTemplate.query(
            """
            SELECT p.`permission_code`
            FROM `permission` p
            WHERE p.`normalized_tenant_id` = IFNULL(:tenantId, 0)
              AND p.`id` = :permissionId
            LIMIT 1
            """,
            tenantParams(tenantId).addValue("permissionId", permissionId),
            (rs, rowNum) -> rs.getString("permission_code")
        );
        if (permissionCodes.isEmpty()) {
            return null;
        }
        return permissionCodes.getFirst();
    }

    private MapSqlParameterSource tenantParams(Long tenantId) {
        return new MapSqlParameterSource().addValue("tenantId", tenantId);
    }

}
