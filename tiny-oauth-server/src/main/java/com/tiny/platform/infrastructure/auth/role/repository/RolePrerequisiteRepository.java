package com.tiny.platform.infrastructure.auth.role.repository;

import com.tiny.platform.infrastructure.auth.role.domain.RolePrerequisite;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface RolePrerequisiteRepository extends JpaRepository<RolePrerequisite, Long> {

    @Query("""
        select rp
        from RolePrerequisite rp
        where rp.tenantId = :tenantId
        """)
    List<RolePrerequisite> findByTenantId(@Param("tenantId") Long tenantId);

    @Query("""
        select rp
        from RolePrerequisite rp
        where rp.tenantId = :tenantId
          and rp.roleId in :roleIds
        """)
    List<RolePrerequisite> findByTenantIdAndRoleIdIn(
        @Param("tenantId") Long tenantId,
        @Param("roleIds") Collection<Long> roleIds
    );

    @Modifying
    @Transactional
    @Query("""
        delete from RolePrerequisite rp
        where rp.tenantId = :tenantId
          and rp.roleId = :roleId
          and rp.requiredRoleId = :requiredRoleId
        """)
    void deleteByTenantIdAndRoleIdAndRequiredRoleId(
        @Param("tenantId") Long tenantId,
        @Param("roleId") Long roleId,
        @Param("requiredRoleId") Long requiredRoleId
    );
}

