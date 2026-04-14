package com.tiny.platform.infrastructure.auth.permission.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PermissionManagementRepository extends JpaRepository<com.tiny.platform.infrastructure.auth.role.domain.Role, Long> {

    @Query(value = """
        SELECT
          p.id AS id,
          p.permission_code AS permissionCode,
          p.permission_name AS permissionName,
          p.module_code AS moduleCode,
          p.enabled AS enabled,
          p.updated_at AS updatedAt,
          (
            SELECT COUNT(DISTINCT rp.role_id)
            FROM role_permission rp
            WHERE rp.permission_id = p.id
              AND rp.normalized_tenant_id = p.normalized_tenant_id
          ) AS boundRoleCount
        FROM permission p
        WHERE p.normalized_tenant_id = IFNULL(:tenantId, 0)
          AND (:keyword IS NULL
               OR :keyword = ''
               OR LOWER(p.permission_code) LIKE CONCAT('%', LOWER(:keyword), '%')
               OR LOWER(p.permission_name) LIKE CONCAT('%', LOWER(:keyword), '%'))
          AND (:moduleCode IS NULL OR :moduleCode = '' OR p.module_code = :moduleCode)
          AND (:enabled IS NULL OR p.enabled = :enabled)
        ORDER BY p.module_code ASC, p.permission_code ASC
        """, nativeQuery = true)
    List<PermissionListProjection> findPermissionList(@Param("tenantId") Long tenantId,
                                                      @Param("keyword") String keyword,
                                                      @Param("moduleCode") String moduleCode,
                                                      @Param("enabled") Boolean enabled);

    @Query(value = """
        SELECT
          p.id AS id,
          p.permission_code AS permissionCode,
          p.permission_name AS permissionName,
          p.module_code AS moduleCode,
          p.enabled AS enabled,
          p.updated_at AS updatedAt
        FROM permission p
        WHERE p.id = :permissionId
          AND p.normalized_tenant_id = IFNULL(:tenantId, 0)
        LIMIT 1
        """, nativeQuery = true)
    Optional<PermissionDetailProjection> findPermissionDetail(@Param("tenantId") Long tenantId,
                                                              @Param("permissionId") Long permissionId);

    @Query(value = """
        SELECT
          r.id AS roleId,
          r.code AS roleCode,
          r.name AS roleName
        FROM role_permission rp
        JOIN role r ON r.id = rp.role_id
        WHERE rp.permission_id = :permissionId
          AND rp.normalized_tenant_id = IFNULL(:tenantId, 0)
          AND r.normalized_tenant_id = IFNULL(:tenantId, 0)
        ORDER BY r.code ASC
        """, nativeQuery = true)
    List<PermissionRoleBindingProjection> findBoundRoles(@Param("tenantId") Long tenantId,
                                                         @Param("permissionId") Long permissionId);

    @Modifying
    @Query(value = """
        UPDATE permission p
        SET p.enabled = :enabled,
            p.updated_at = :updatedAt
        WHERE p.id = :permissionId
          AND p.normalized_tenant_id = IFNULL(:tenantId, 0)
        """, nativeQuery = true)
    int updatePermissionEnabled(@Param("tenantId") Long tenantId,
                                @Param("permissionId") Long permissionId,
                                @Param("enabled") boolean enabled,
                                @Param("updatedAt") LocalDateTime updatedAt);

    interface PermissionListProjection {
        Long getId();
        String getPermissionCode();
        String getPermissionName();
        String getModuleCode();
        Boolean getEnabled();
        LocalDateTime getUpdatedAt();
        Integer getBoundRoleCount();
    }

    interface PermissionDetailProjection {
        Long getId();
        String getPermissionCode();
        String getPermissionName();
        String getModuleCode();
        Boolean getEnabled();
        LocalDateTime getUpdatedAt();
    }

    interface PermissionRoleBindingProjection {
        Long getRoleId();
        String getRoleCode();
        String getRoleName();
    }
}
