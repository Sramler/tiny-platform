package com.tiny.platform.infrastructure.auth.resource.repository;

import com.tiny.platform.infrastructure.menu.domain.MenuEntry;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Carrier-backed read projections that must no longer hang off the legacy
 * {@code resource} repository.
 *
 * <p>These queries already read from {@code menu/ui_action/api_endpoint}; the
 * dedicated repository makes that runtime dependency explicit and keeps
 * {@link ResourceRepository} focused on legacy compatibility only.</p>
 */
@org.springframework.stereotype.Repository
public interface CarrierProjectionRepository extends Repository<MenuEntry, Long> {

    @Query(value = """
        SELECT c.id AS id,
               c.tenant_id AS tenantId,
               c.resource_level AS resourceLevel,
               c.name AS name,
               c.url AS url,
               c.uri AS uri,
               c.method AS method,
               c.icon AS icon,
               c.show_icon AS showIcon,
               c.sort AS sort,
               c.component AS component,
               c.redirect AS redirect,
               c.hidden AS hidden,
               c.keep_alive AS keepAlive,
               c.title AS title,
               c.permission AS permission,
               c.required_permission_id AS requiredPermissionId,
               c.type_code AS typeCode,
               c.parent_id AS parentId,
               c.enabled AS enabled
        FROM (
            SELECT m.id,
                   m.tenant_id,
                   CONVERT(m.resource_level USING utf8mb4) COLLATE utf8mb4_0900_ai_ci AS resource_level,
                   CONVERT(m.name USING utf8mb4) COLLATE utf8mb4_0900_ai_ci AS name,
                   CONVERT(m.path USING utf8mb4) COLLATE utf8mb4_0900_ai_ci AS url,
                   CONVERT('' USING utf8mb4) COLLATE utf8mb4_0900_ai_ci AS uri,
                   CONVERT('' USING utf8mb4) COLLATE utf8mb4_0900_ai_ci AS method,
                   CONVERT(m.icon USING utf8mb4) COLLATE utf8mb4_0900_ai_ci AS icon,
                   m.show_icon,
                   m.sort,
                   CONVERT(m.component USING utf8mb4) COLLATE utf8mb4_0900_ai_ci AS component,
                   CONVERT(m.redirect USING utf8mb4) COLLATE utf8mb4_0900_ai_ci AS redirect,
                   m.hidden,
                   m.keep_alive,
                   CONVERT(m.title USING utf8mb4) COLLATE utf8mb4_0900_ai_ci AS title,
                   CONVERT(m.permission USING utf8mb4) COLLATE utf8mb4_0900_ai_ci AS permission,
                   m.required_permission_id,
                   m.type AS type_code,
                   m.parent_id,
                   m.enabled
            FROM menu m
            WHERE ((:tenantId IS NULL AND m.tenant_id IS NULL) OR m.tenant_id = :tenantId)
              AND LOWER(m.resource_level) = LOWER(:resourceLevel)
            UNION ALL
            SELECT a.id,
                   a.tenant_id,
                   CONVERT(a.resource_level USING utf8mb4) COLLATE utf8mb4_0900_ai_ci AS resource_level,
                   CONVERT(a.name USING utf8mb4) COLLATE utf8mb4_0900_ai_ci AS name,
                   CONVERT(a.page_path USING utf8mb4) COLLATE utf8mb4_0900_ai_ci AS url,
                   CONVERT('' USING utf8mb4) COLLATE utf8mb4_0900_ai_ci AS uri,
                   CONVERT('' USING utf8mb4) COLLATE utf8mb4_0900_ai_ci AS method,
                   CONVERT('' USING utf8mb4) COLLATE utf8mb4_0900_ai_ci AS icon,
                   FALSE AS show_icon,
                   a.sort,
                   CONVERT('' USING utf8mb4) COLLATE utf8mb4_0900_ai_ci AS component,
                   CONVERT('' USING utf8mb4) COLLATE utf8mb4_0900_ai_ci AS redirect,
                   FALSE AS hidden,
                   FALSE AS keep_alive,
                   CONVERT(a.title USING utf8mb4) COLLATE utf8mb4_0900_ai_ci AS title,
                   CONVERT(a.permission USING utf8mb4) COLLATE utf8mb4_0900_ai_ci AS permission,
                   a.required_permission_id,
                   2 AS type_code,
                   a.parent_menu_id AS parent_id,
                   a.enabled
            FROM ui_action a
            WHERE ((:tenantId IS NULL AND a.tenant_id IS NULL) OR a.tenant_id = :tenantId)
              AND LOWER(a.resource_level) = LOWER(:resourceLevel)
            UNION ALL
            SELECT e.id,
                   e.tenant_id,
                   CONVERT(e.resource_level USING utf8mb4) COLLATE utf8mb4_0900_ai_ci AS resource_level,
                   CONVERT(e.name USING utf8mb4) COLLATE utf8mb4_0900_ai_ci AS name,
                   CONVERT('' USING utf8mb4) COLLATE utf8mb4_0900_ai_ci AS url,
                   CONVERT(e.uri USING utf8mb4) COLLATE utf8mb4_0900_ai_ci AS uri,
                   CONVERT(e.method USING utf8mb4) COLLATE utf8mb4_0900_ai_ci AS method,
                   CONVERT('' USING utf8mb4) COLLATE utf8mb4_0900_ai_ci AS icon,
                   FALSE AS show_icon,
                   0 AS sort,
                   CONVERT('' USING utf8mb4) COLLATE utf8mb4_0900_ai_ci AS component,
                   CONVERT('' USING utf8mb4) COLLATE utf8mb4_0900_ai_ci AS redirect,
                   FALSE AS hidden,
                   FALSE AS keep_alive,
                   CONVERT(e.title USING utf8mb4) COLLATE utf8mb4_0900_ai_ci AS title,
                   CONVERT(e.permission USING utf8mb4) COLLATE utf8mb4_0900_ai_ci AS permission,
                   e.required_permission_id,
                   3 AS type_code,
                   NULL AS parent_id,
                   e.enabled
            FROM api_endpoint e
            WHERE ((:tenantId IS NULL AND e.tenant_id IS NULL) OR e.tenant_id = :tenantId)
              AND LOWER(e.resource_level) = LOWER(:resourceLevel)
        ) c
        ORDER BY c.sort ASC, c.id ASC
        """, nativeQuery = true)
    List<CarrierTemplateResourceSnapshotView> findTemplateSnapshotViewsByScope(
        @Param("tenantId") Long tenantId,
        @Param("resourceLevel") String resourceLevel
    );

    @Query(value = """
        SELECT c.id AS id, c.permission AS permission, c.required_permission_id AS requiredPermissionId
        FROM (
            SELECT m.id,
                   CONVERT(m.permission USING utf8mb4) COLLATE utf8mb4_0900_ai_ci AS permission,
                   m.required_permission_id
            FROM menu m
            WHERE m.id IN (:ids)
              AND ((:tenantId IS NULL AND m.tenant_id IS NULL) OR m.tenant_id = :tenantId)
              AND LOWER(m.resource_level) = LOWER(:resourceLevel)
            UNION ALL
            SELECT a.id,
                   CONVERT(a.permission USING utf8mb4) COLLATE utf8mb4_0900_ai_ci AS permission,
                   a.required_permission_id
            FROM ui_action a
            WHERE a.id IN (:ids)
              AND ((:tenantId IS NULL AND a.tenant_id IS NULL) OR a.tenant_id = :tenantId)
              AND LOWER(a.resource_level) = LOWER(:resourceLevel)
            UNION ALL
            SELECT e.id,
                   CONVERT(e.permission USING utf8mb4) COLLATE utf8mb4_0900_ai_ci AS permission,
                   e.required_permission_id
            FROM api_endpoint e
            WHERE e.id IN (:ids)
              AND ((:tenantId IS NULL AND e.tenant_id IS NULL) OR e.tenant_id = :tenantId)
              AND LOWER(e.resource_level) = LOWER(:resourceLevel)
        ) c
        """, nativeQuery = true)
    List<RoleResourcePermissionBindingView> findPermissionBindingViewsByIdsAndScope(
        @Param("ids") List<Long> ids,
        @Param("tenantId") Long tenantId,
        @Param("resourceLevel") String resourceLevel
    );
}
