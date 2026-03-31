package com.tiny.platform.infrastructure.auth.datascope.repository;

import com.tiny.platform.infrastructure.auth.datascope.domain.RoleDataScope;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RoleDataScopeRepository extends JpaRepository<RoleDataScope, Long> {

    List<RoleDataScope> findByTenantIdAndRoleId(Long tenantId, Long roleId);

    List<RoleDataScope> findByTenantId(Long tenantId);

    Optional<RoleDataScope> findByTenantIdAndRoleIdAndModuleAndAccessType(
        Long tenantId, Long roleId, String module, String accessType);

    /**
     * 查询指定角色集合在某个模块中的所有数据范围规则。
     */
    @Query("""
        select rds from RoleDataScope rds
        where rds.tenantId = :tenantId
          and rds.module = :module
          and rds.accessType = :accessType
          and rds.roleId in :roleIds
        """)
    List<RoleDataScope> findByTenantIdAndModuleAndAccessTypeAndRoleIdIn(
        @Param("tenantId") Long tenantId,
        @Param("module") String module,
        @Param("accessType") String accessType,
        @Param("roleIds") List<Long> roleIds
    );

    void deleteByTenantIdAndRoleIdAndModuleAndAccessType(
        Long tenantId, Long roleId, String module, String accessType);
}
