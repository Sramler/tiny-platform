package com.tiny.platform.infrastructure.auth.role.repository;

import com.tiny.platform.infrastructure.auth.role.domain.RoleHierarchy;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface RoleHierarchyRepository extends JpaRepository<RoleHierarchy, Long> {

    @Query("""
        select rh
        from RoleHierarchy rh
        where rh.tenantId = :tenantId
        """)
    List<RoleHierarchy> findByTenantId(@Param("tenantId") Long tenantId);

    @Query("""
        select rh
        from RoleHierarchy rh
        where rh.tenantId is null
        """)
    List<RoleHierarchy> findByTenantIdIsNull();

    @Query("""
        select rh
        from RoleHierarchy rh
        where rh.tenantId = :tenantId
          and rh.childRoleId in :childRoleIds
        """)
    List<RoleHierarchy> findByTenantIdAndChildRoleIdIn(
        @Param("tenantId") Long tenantId,
        @Param("childRoleIds") Collection<Long> childRoleIds
    );

    @Query("""
        select rh
        from RoleHierarchy rh
        where rh.tenantId = :tenantId
          and rh.parentRoleId in :parentRoleIds
        """)
    List<RoleHierarchy> findByTenantIdAndParentRoleIdIn(
        @Param("tenantId") Long tenantId,
        @Param("parentRoleIds") Collection<Long> parentRoleIds
    );

    @Query("""
        select rh
        from RoleHierarchy rh
        where rh.tenantId is null
          and rh.childRoleId in :childRoleIds
        """)
    List<RoleHierarchy> findByTenantIdIsNullAndChildRoleIdIn(
        @Param("childRoleIds") Collection<Long> childRoleIds
    );

    @Query("""
        select rh
        from RoleHierarchy rh
        where rh.tenantId is null
          and rh.parentRoleId in :parentRoleIds
        """)
    List<RoleHierarchy> findByTenantIdIsNullAndParentRoleIdIn(
        @Param("parentRoleIds") Collection<Long> parentRoleIds
    );

    @Modifying
    @Transactional
    @Query("""
        delete from RoleHierarchy rh
        where rh.tenantId = :tenantId
          and rh.childRoleId = :childRoleId
          and rh.parentRoleId = :parentRoleId
        """)
    void deleteByTenantIdAndChildRoleIdAndParentRoleId(
        @Param("tenantId") Long tenantId,
        @Param("childRoleId") Long childRoleId,
        @Param("parentRoleId") Long parentRoleId
    );

    @Modifying
    @Transactional
    @Query("""
        delete from RoleHierarchy rh
        where rh.tenantId is null
          and rh.childRoleId = :childRoleId
          and rh.parentRoleId = :parentRoleId
        """)
    void deleteByTenantIdIsNullAndChildRoleIdAndParentRoleId(
        @Param("childRoleId") Long childRoleId,
        @Param("parentRoleId") Long parentRoleId
    );
}
