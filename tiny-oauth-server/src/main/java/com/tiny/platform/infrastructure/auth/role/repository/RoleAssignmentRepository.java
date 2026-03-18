package com.tiny.platform.infrastructure.auth.role.repository;

import com.tiny.platform.infrastructure.auth.role.domain.RoleAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface RoleAssignmentRepository extends JpaRepository<RoleAssignment, Long> {

    @Query("""
        select distinct ra.roleId
        from RoleAssignment ra
        where ra.principalType = 'USER'
          and ra.principalId = :userId
          and ra.scopeType = 'TENANT'
          and ra.tenantId = :tenantId
          and ra.status = 'ACTIVE'
          and ra.startTime <= :now
          and (ra.endTime is null or ra.endTime > :now)
        order by ra.roleId asc
        """)
    List<Long> findActiveRoleIdsForUserInTenant(
        @Param("userId") Long userId,
        @Param("tenantId") Long tenantId,
        @Param("now") LocalDateTime now
    );

    @Query("""
        select distinct ra.principalId
        from RoleAssignment ra
        where ra.principalType = 'USER'
          and ra.roleId = :roleId
          and ra.scopeType = 'TENANT'
          and ra.tenantId = :tenantId
          and ra.status = 'ACTIVE'
          and ra.startTime <= :now
          and (ra.endTime is null or ra.endTime > :now)
        order by ra.principalId asc
        """)
    List<Long> findActiveUserIdsForRoleInTenant(
        @Param("roleId") Long roleId,
        @Param("tenantId") Long tenantId,
        @Param("now") LocalDateTime now
    );

    @Query("""
        select max(ra.updatedAt)
        from RoleAssignment ra
        where ra.principalType = 'USER'
          and ra.principalId = :userId
          and ra.scopeType = 'TENANT'
          and ra.tenantId = :tenantId
          and ra.status = 'ACTIVE'
          and ra.startTime <= :now
          and (ra.endTime is null or ra.endTime > :now)
        """)
    LocalDateTime findLatestUpdatedAtForActiveUserInTenant(
        @Param("userId") Long userId,
        @Param("tenantId") Long tenantId,
        @Param("now") LocalDateTime now
    );

    @Modifying
    @Transactional
    @Query("""
        delete from RoleAssignment ra
        where ra.principalType = 'USER'
          and ra.principalId = :userId
          and ra.scopeType = 'TENANT'
          and ra.tenantId = :tenantId
        """)
    void deleteUserAssignmentsInTenant(
        @Param("userId") Long userId,
        @Param("tenantId") Long tenantId
    );

    @Modifying
    @Transactional
    @Query("""
        delete from RoleAssignment ra
        where ra.principalType = 'USER'
          and ra.roleId = :roleId
          and ra.scopeType = 'TENANT'
          and ra.tenantId = :tenantId
        """)
    void deleteRoleAssignmentsInTenant(
        @Param("roleId") Long roleId,
        @Param("tenantId") Long tenantId
    );

}
