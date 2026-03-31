package com.tiny.platform.infrastructure.auth.resource.service;

import com.tiny.platform.infrastructure.auth.resource.domain.Resource;
import com.tiny.platform.infrastructure.auth.resource.enums.ResourceType;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 将资源载体与权限主数据显式绑定，避免运行态继续依赖 permission_code 字符串拼接。
 *
 * <p>当前仍保留 {@code resource.permission} 作为兼容字段和运营可读字段，但新写链路应同步维护
 * {@code resource.required_permission_id}。这样在不拆分 resource 表的前提下，可以先把“能力真相源”
 * 和“载体层”的连接改成显式引用。</p>
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
        String permissionCode = trimToNull(resource.getPermission());
        if (permissionCode == null) {
            resource.setRequiredPermissionId(null);
            return;
        }
        Long requiredPermissionId = ensurePermissionExists(
            resource.getTenantId(),
            permissionCode,
            resource.getType(),
            actorUserId,
            Boolean.TRUE.equals(resource.getEnabled())
        );
        resource.setRequiredPermissionId(requiredPermissionId);
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
              '从 resource 载体同步生成' AS `description`,
              grouped.`enabled`,
              0 AS `built_in_flag`,
              :tenantId AS `tenant_id`,
              NULL AS `created_by`,
              NOW() AS `created_at`,
              NULL AS `updated_by`,
              NOW() AS `updated_at`
            FROM (
              SELECT
                TRIM(r.`permission`) AS `permission_code`,
                CASE
                  WHEN TRIM(r.`permission`) REGEXP '^[^:[:space:]]+(:[^:[:space:]]+){2,}$'
                    THEN SUBSTRING_INDEX(TRIM(r.`permission`), ':', 1)
                  ELSE NULL
                END AS `module_code`,
                CASE
                  WHEN TRIM(r.`permission`) REGEXP '^[^:[:space:]]+(:[^:[:space:]]+){2,}$'
                    THEN SUBSTRING_INDEX(TRIM(r.`permission`), ':', -1)
                  ELSE NULL
                END AS `action_code`,
                CASE
                  WHEN MAX(CASE WHEN r.`type` = 3 THEN 1 ELSE 0 END) = 1 THEN 'API'
                  WHEN MAX(CASE WHEN r.`type` = 2 THEN 1 ELSE 0 END) = 1 THEN 'BUTTON'
                  WHEN MAX(CASE WHEN r.`type` IN (0, 1) THEN 1 ELSE 0 END) = 1 THEN 'MENU'
                  ELSE 'OTHER'
                END AS `permission_type`,
                CASE
                  WHEN MAX(CASE WHEN r.`enabled` = 1 THEN 1 ELSE 0 END) = 1 THEN 1
                  ELSE 0
                END AS `enabled`
              FROM `resource` r
              WHERE r.`normalized_tenant_id` = IFNULL(:tenantId, 0)
                AND r.`permission` IS NOT NULL
                AND TRIM(r.`permission`) <> ''
              GROUP BY TRIM(r.`permission`)
            ) grouped
            """, tenantParams(tenantId));
    }

    public int bindRequiredPermissionIdsForResources(Long tenantId) {
        return namedParameterJdbcTemplate.update("""
            UPDATE `resource` r
            JOIN `permission` p
              ON p.`normalized_tenant_id` = r.`normalized_tenant_id`
             AND p.`permission_code` = TRIM(r.`permission`)
            SET r.`required_permission_id` = p.`id`
            WHERE r.`normalized_tenant_id` = IFNULL(:tenantId, 0)
              AND r.`permission` IS NOT NULL
              AND TRIM(r.`permission`) <> ''
              AND (r.`required_permission_id` IS NULL OR r.`required_permission_id` <> p.`id`)
            """, tenantParams(tenantId));
    }

    private Long ensurePermissionExists(Long tenantId,
                                        String permissionCode,
                                        ResourceType resourceType,
                                        Long actorUserId,
                                        boolean enabled) {
        MapSqlParameterSource params = tenantParams(tenantId)
            .addValue("permissionCode", permissionCode)
            .addValue("permissionName", permissionCode)
            .addValue("moduleCode", deriveModuleCode(permissionCode))
            .addValue("actionCode", deriveActionCode(permissionCode))
            .addValue("permissionType", derivePermissionType(resourceType))
            .addValue("description", "从 resource 载体同步生成")
            .addValue("enabled", enabled ? 1 : 0)
            .addValue("actorUserId", actorUserId);

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
            ) VALUES (
              :permissionCode,
              :permissionName,
              :moduleCode,
              :actionCode,
              :permissionType,
              :description,
              :enabled,
              0,
              :tenantId,
              :actorUserId,
              NOW(),
              :actorUserId,
              NOW()
            )
            """, params);

        return namedParameterJdbcTemplate.queryForObject("""
            SELECT p.`id`
            FROM `permission` p
            WHERE p.`normalized_tenant_id` = IFNULL(:tenantId, 0)
              AND p.`permission_code` = :permissionCode
            LIMIT 1
            """, params, Long.class);
    }

    private MapSqlParameterSource tenantParams(Long tenantId) {
        return new MapSqlParameterSource().addValue("tenantId", tenantId);
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String deriveModuleCode(String permissionCode) {
        return hasCanonicalSegments(permissionCode)
            ? permissionCode.substring(0, permissionCode.indexOf(':'))
            : null;
    }

    private String deriveActionCode(String permissionCode) {
        return hasCanonicalSegments(permissionCode)
            ? permissionCode.substring(permissionCode.lastIndexOf(':') + 1)
            : null;
    }

    private boolean hasCanonicalSegments(String permissionCode) {
        return StringUtils.countOccurrencesOf(permissionCode, ":") >= 2;
    }

    private String derivePermissionType(ResourceType resourceType) {
        if (resourceType == null) {
            return "OTHER";
        }
        return switch (resourceType) {
            case API -> "API";
            case BUTTON -> "BUTTON";
            case DIRECTORY, MENU -> "MENU";
        };
    }
}
