package com.tiny.platform.infrastructure.tenant.repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

@org.springframework.stereotype.Repository
public interface TenantPermissionSummaryRepository extends Repository<com.tiny.platform.infrastructure.tenant.domain.Tenant, Long> {

    @Query(
        value = """
            SELECT
              (SELECT COUNT(*)
                 FROM role ro
                WHERE ro.tenant_id = :tenantId) AS totalRoles,
              (SELECT COUNT(*)
                 FROM role ro
                WHERE ro.tenant_id = :tenantId
                  AND ro.enabled = 1) AS enabledRoles,
              (SELECT COUNT(*)
                 FROM permission p
                WHERE p.normalized_tenant_id = :tenantId
                  AND p.enabled = 1) AS totalPermissions,
              (SELECT COUNT(DISTINCT rp.permission_id)
                 FROM role_permission rp
                 JOIN role ro
                   ON ro.id = rp.role_id
                  AND rp.normalized_tenant_id = IFNULL(ro.tenant_id, 0)
                 JOIN permission p
                   ON p.id = rp.permission_id
                  AND p.normalized_tenant_id = rp.normalized_tenant_id
                  AND p.enabled = 1
                WHERE ro.tenant_id = :tenantId) AS assignedPermissions,
              ((SELECT COUNT(*)
                  FROM menu m
                 WHERE m.tenant_id = :tenantId
                   AND LOWER(m.resource_level) = 'tenant')
               + (SELECT COUNT(*)
                    FROM ui_action ua
                   WHERE ua.tenant_id = :tenantId
                     AND LOWER(ua.resource_level) = 'tenant')
               + (SELECT COUNT(*)
                    FROM api_endpoint ae
                   WHERE ae.tenant_id = :tenantId
                     AND LOWER(ae.resource_level) = 'tenant')) AS totalCarriers,
              ((SELECT COUNT(*)
                  FROM menu m
                 WHERE m.tenant_id = :tenantId
                   AND LOWER(m.resource_level) = 'tenant'
                   AND m.required_permission_id IS NOT NULL)
               + (SELECT COUNT(*)
                    FROM ui_action ua
                   WHERE ua.tenant_id = :tenantId
                     AND LOWER(ua.resource_level) = 'tenant'
                     AND ua.required_permission_id IS NOT NULL)
               + (SELECT COUNT(*)
                    FROM api_endpoint ae
                   WHERE ae.tenant_id = :tenantId
                     AND LOWER(ae.resource_level) = 'tenant'
                     AND ae.required_permission_id IS NOT NULL)) AS boundCarriers,
              (SELECT COUNT(*)
                 FROM menu m
                WHERE m.tenant_id = :tenantId
                  AND LOWER(m.resource_level) = 'tenant') AS menuCarriers,
              (SELECT COUNT(*)
                 FROM ui_action ua
                WHERE ua.tenant_id = :tenantId
                  AND LOWER(ua.resource_level) = 'tenant') AS uiActionCarriers,
              (SELECT COUNT(*)
                 FROM api_endpoint ae
                WHERE ae.tenant_id = :tenantId
                  AND LOWER(ae.resource_level) = 'tenant') AS apiEndpointCarriers
            """,
        nativeQuery = true
    )
    TenantPermissionSummaryProjection summarizeByTenantId(@Param("tenantId") Long tenantId);

}
