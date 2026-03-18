package com.tiny.platform.infrastructure.auth.role.repository;

import com.tiny.platform.infrastructure.auth.role.domain.RoleMutex;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface RoleMutexRepository extends JpaRepository<RoleMutex, Long> {

    @Query("""
        select rm
        from RoleMutex rm
        where rm.tenantId = :tenantId
        """)
    List<RoleMutex> findByTenantId(@Param("tenantId") Long tenantId);

    @Query("""
        select rm
        from RoleMutex rm
        where rm.tenantId = :tenantId
          and (rm.leftRoleId in :roleIds or rm.rightRoleId in :roleIds)
        """)
    List<RoleMutex> findByTenantIdAndRoleIds(
        @Param("tenantId") Long tenantId,
        @Param("roleIds") Collection<Long> roleIds
    );

    @Modifying
    @Transactional
    @Query("""
        delete from RoleMutex rm
        where rm.tenantId = :tenantId
          and rm.leftRoleId = :leftRoleId
          and rm.rightRoleId = :rightRoleId
        """)
    void deleteByTenantIdAndLeftRoleIdAndRightRoleId(
        @Param("tenantId") Long tenantId,
        @Param("leftRoleId") Long leftRoleId,
        @Param("rightRoleId") Long rightRoleId
    );
}

