package com.tiny.platform.infrastructure.auth.role.repository;

import com.tiny.platform.infrastructure.auth.role.domain.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RoleRepository extends JpaRepository<Role, Long>, JpaSpecificationExecutor<Role> {

    List<Role> findByTenantIdOrderByIdAsc(Long tenantId);

    long countByTenantId(Long tenantId);

    default boolean existsByTenantId(Long tenantId) {
        return countByTenantId(tenantId) > 0;
    }

    /** 平台模板：tenant_id IS NULL，见 §4 平台模板与 default 解耦 */
    List<Role> findByTenantIdIsNullOrderByIdAsc();

    Optional<Role> findByIdAndTenantId(Long id, Long tenantId);

    Optional<Role> findByCodeAndTenantId(String code, Long tenantId);

    Optional<Role> findByNameAndTenantId(String name, Long tenantId);

    List<Role> findByIdInAndTenantId(List<Long> ids, Long tenantId);

    List<Role> findByIdInAndTenantIdOrderByIdAsc(List<Long> ids, Long tenantId);

    List<Role> findByIdInAndTenantIdIsNullOrderByIdAsc(List<Long> ids);

    /**
     * 查询角色已经分配的所有资源ID（新模型口径：role_permission -> permission，carrier 通过 required_permission_id 绑定）。
     */
    @Query(value = """
        SELECT DISTINCT c.id
        FROM role ro
        JOIN role_permission rp
          ON rp.role_id = ro.id
         AND rp.normalized_tenant_id = IFNULL(ro.tenant_id, 0)
        JOIN (
            SELECT m.id, m.required_permission_id, IFNULL(m.tenant_id, 0) AS normalized_tenant_id
            FROM menu m
            UNION ALL
            SELECT a.id, a.required_permission_id, IFNULL(a.tenant_id, 0) AS normalized_tenant_id
            FROM ui_action a
            UNION ALL
            SELECT e.id, e.required_permission_id, IFNULL(e.tenant_id, 0) AS normalized_tenant_id
            FROM api_endpoint e
        ) c
          ON c.required_permission_id = rp.permission_id
         AND c.normalized_tenant_id = rp.normalized_tenant_id
        WHERE ro.id = :roleId
        ORDER BY c.id ASC
        """, nativeQuery = true)
    List<Long> findResourceIdsByRoleId(@Param("roleId") Long roleId);

    @Query(value = """
        SELECT DISTINCT rp.permission_id
        FROM role_permission rp
        WHERE rp.role_id = :roleId
          AND rp.tenant_id <=> :tenantId
        ORDER BY rp.permission_id ASC
        """, nativeQuery = true)
    List<Long> findPermissionIdsByRoleIdAndTenantId(@Param("roleId") Long roleId, @Param("tenantId") Long tenantId);

    @Query(value = """
        SELECT DISTINCT rp.permission_id
        FROM role_permission rp
        WHERE rp.role_id IN (:roleIds)
          AND rp.tenant_id <=> :tenantId
        ORDER BY rp.permission_id ASC
        """, nativeQuery = true)
    List<Long> findPermissionIdsByRoleIdsAndTenantId(@Param("roleIds") List<Long> roleIds, @Param("tenantId") Long tenantId);

    @Modifying
    @Query(value = "DELETE FROM role_permission WHERE role_id = :roleId AND tenant_id <=> :tenantId", nativeQuery = true)
    void deleteRolePermissionRelations(@Param("roleId") Long roleId, @Param("tenantId") Long tenantId);

    /**
     * When a carrier becomes the last reference of a permission under a tenant scope,
     * revoke all role_permission relations by {@code permission_id}.
     */
    @Modifying
    @Query(value = "DELETE FROM role_permission WHERE permission_id = :permissionId AND tenant_id <=> :tenantId", nativeQuery = true)
    void deleteRolePermissionRelationsByPermissionIdAndTenantId(@Param("permissionId") Long permissionId,
                                                                   @Param("tenantId") Long tenantId);

    @Modifying
    @Query(value = """
        INSERT IGNORE INTO role_permission (tenant_id, role_id, permission_id)
        VALUES (:tenantId, :roleId, :permissionId)
        """, nativeQuery = true)
    void addRolePermissionRelationByPermissionId(@Param("tenantId") Long tenantId,
                                                 @Param("roleId") Long roleId,
                                                 @Param("permissionId") Long permissionId);

    /**
     * 租户内角色已授权资源对（主模型：role_permission → permission；carrier 通过 required_permission_id 绑定）。
     */
    @Query(value = """
        SELECT DISTINCT ro.id AS roleId, c.id AS resourceId
        FROM role ro
        JOIN role_permission rp
          ON rp.role_id = ro.id
         AND rp.normalized_tenant_id = IFNULL(ro.tenant_id, 0)
        JOIN (
            SELECT m.id, m.required_permission_id, IFNULL(m.tenant_id, 0) AS normalized_tenant_id
            FROM menu m
            UNION ALL
            SELECT a.id, a.required_permission_id, IFNULL(a.tenant_id, 0) AS normalized_tenant_id
            FROM ui_action a
            UNION ALL
            SELECT e.id, e.required_permission_id, IFNULL(e.tenant_id, 0) AS normalized_tenant_id
            FROM api_endpoint e
        ) c
          ON c.required_permission_id = rp.permission_id
         AND c.normalized_tenant_id = rp.normalized_tenant_id
        WHERE ro.tenant_id = :tenantId
        ORDER BY ro.id ASC, c.id ASC
        """, nativeQuery = true)
    List<RoleResourceRelationProjection> findGrantedRoleCarrierPairsByTenantId(@Param("tenantId") Long tenantId);

    /**
     * 平台模板（tenant_id IS NULL 角色/资源）已授权资源对，口径同 {@link #findGrantedRoleCarrierPairsByTenantId}。
     */
    @Query(value = """
        SELECT DISTINCT ro.id AS roleId, c.id AS resourceId
        FROM role ro
        JOIN role_permission rp
          ON rp.role_id = ro.id
         AND rp.normalized_tenant_id = IFNULL(ro.tenant_id, 0)
        JOIN (
            SELECT m.id, m.required_permission_id, IFNULL(m.tenant_id, 0) AS normalized_tenant_id
            FROM menu m
            UNION ALL
            SELECT a.id, a.required_permission_id, IFNULL(a.tenant_id, 0) AS normalized_tenant_id
            FROM ui_action a
            UNION ALL
            SELECT e.id, e.required_permission_id, IFNULL(e.tenant_id, 0) AS normalized_tenant_id
            FROM api_endpoint e
        ) c
          ON c.required_permission_id = rp.permission_id
         AND c.normalized_tenant_id = rp.normalized_tenant_id
        WHERE ro.tenant_id IS NULL
        ORDER BY ro.id ASC, c.id ASC
        """, nativeQuery = true)
    List<RoleResourceRelationProjection> findGrantedRoleCarrierPairsForPlatformTemplate();

    @Query(value = """
        SELECT
          p.id AS id,
          p.permission_code AS permissionCode,
          p.permission_name AS permissionName
        FROM permission p
        WHERE p.normalized_tenant_id = IFNULL(:tenantId, 0)
          AND p.enabled = 1
          AND (
            :keyword IS NULL
            OR :keyword = ''
            OR LOWER(p.permission_code) LIKE CONCAT('%', LOWER(:keyword), '%')
            OR LOWER(p.permission_name) LIKE CONCAT('%', LOWER(:keyword), '%')
          )
        ORDER BY p.permission_code ASC
        """, nativeQuery = true)
    List<PermissionOptionProjection> findPermissionOptionsByTenantIdAndKeyword(@Param("tenantId") Long tenantId,
                                                                                @Param("keyword") String keyword,
                                                                                Pageable pageable);
}
