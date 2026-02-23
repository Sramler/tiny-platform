package com.tiny.platform.infrastructure.auth.role.repository;

import com.tiny.platform.infrastructure.auth.role.domain.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RoleRepository extends JpaRepository<Role, Long>, JpaSpecificationExecutor<Role> {

    @Query("select r from Role r left join fetch r.users where r.id = :id")
    Optional<Role> findByIdFetchUsers(@Param("id") Long id);

    Optional<Role> findByIdAndTenantId(Long id, Long tenantId);

    Optional<Role> findByCodeAndTenantId(String code, Long tenantId);

    Optional<Role> findByNameAndTenantId(String name, Long tenantId);

    List<Role> findByIdInAndTenantId(List<Long> ids, Long tenantId);

    /**
     * 查询角色已经分配的所有资源ID
     */
    @Query("select res.id from Role role join role.resources res where role.id = :roleId")
    List<Long> findResourceIdsByRoleId(@Param("roleId") Long roleId);

    @Modifying
    @Query(value = "DELETE FROM role_resource WHERE role_id = :roleId AND tenant_id = :tenantId", nativeQuery = true)
    void deleteRoleResourceRelations(@Param("roleId") Long roleId, @Param("tenantId") Long tenantId);

    @Modifying
    @Query(value = "INSERT INTO role_resource (tenant_id, role_id, resource_id) VALUES (:tenantId, :roleId, :resourceId)", nativeQuery = true)
    void addRoleResourceRelation(@Param("tenantId") Long tenantId, @Param("roleId") Long roleId, @Param("resourceId") Long resourceId);
}
