package com.tiny.platform.infrastructure.auth.role.repository;

import com.tiny.platform.infrastructure.auth.role.domain.RoleCardinality;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface RoleCardinalityRepository extends JpaRepository<RoleCardinality, Long> {

    @Query("""
        select rc
        from RoleCardinality rc
        where rc.tenantId = :tenantId
        """)
    List<RoleCardinality> findByTenantId(@Param("tenantId") Long tenantId);

    @Query("""
        select rc
        from RoleCardinality rc
        where rc.tenantId = :tenantId
          and rc.scopeType = :scopeType
          and rc.roleId in :roleIds
        """)
    List<RoleCardinality> findByTenantIdAndScopeTypeAndRoleIdIn(
        @Param("tenantId") Long tenantId,
        @Param("scopeType") String scopeType,
        @Param("roleIds") Collection<Long> roleIds
    );

    @Modifying
    @Transactional
    @Query("""
        delete from RoleCardinality rc
        where rc.tenantId = :tenantId
          and rc.roleId = :roleId
          and rc.scopeType = :scopeType
        """)
    void deleteByTenantIdAndRoleIdAndScopeType(
        @Param("tenantId") Long tenantId,
        @Param("roleId") Long roleId,
        @Param("scopeType") String scopeType
    );
}

