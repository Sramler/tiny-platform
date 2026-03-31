package com.tiny.platform.infrastructure.auth.role.repository;

import com.tiny.platform.infrastructure.auth.role.domain.RoleAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
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
        select distinct ra.roleId
        from RoleAssignment ra
        where ra.principalType = 'USER'
          and ra.principalId = :userId
          and ra.scopeType = 'PLATFORM'
          and ra.tenantId is null
          and ra.scopeId is null
          and ra.status = 'ACTIVE'
          and ra.startTime <= :now
          and (ra.endTime is null or ra.endTime > :now)
        order by ra.roleId asc
        """)
    List<Long> findActiveRoleIdsForUserInPlatform(
        @Param("userId") Long userId,
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
        select distinct ra.principalId
        from RoleAssignment ra
        where ra.principalType = 'USER'
          and ra.roleId in :roleIds
          and ra.scopeType = 'TENANT'
          and ra.tenantId = :tenantId
          and ra.status = 'ACTIVE'
          and ra.startTime <= :now
          and (ra.endTime is null or ra.endTime > :now)
        order by ra.principalId asc
        """)
    List<Long> findActiveUserIdsForRoleIdsInTenant(
        @Param("roleIds") Collection<Long> roleIds,
        @Param("tenantId") Long tenantId,
        @Param("now") LocalDateTime now
    );

    @Query("""
        select distinct ra.principalId
        from RoleAssignment ra
        where ra.principalType = 'USER'
          and ra.roleId = :roleId
          and ra.tenantId = :tenantId
          and ra.scopeType = :scopeType
          and ra.scopeId = :scopeId
          and ra.status = 'ACTIVE'
          and ra.startTime <= :now
          and (ra.endTime is null or ra.endTime > :now)
        order by ra.principalId asc
        """)
    List<Long> findActiveUserIdsForRoleInScope(
        @Param("roleId") Long roleId,
        @Param("tenantId") Long tenantId,
        @Param("scopeType") String scopeType,
        @Param("scopeId") Long scopeId,
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

    @Query("""
        select max(ra.updatedAt)
        from RoleAssignment ra
        where ra.principalType = 'USER'
          and ra.principalId = :userId
          and ra.scopeType = 'PLATFORM'
          and ra.tenantId is null
          and ra.scopeId is null
          and ra.status = 'ACTIVE'
          and ra.startTime <= :now
          and (ra.endTime is null or ra.endTime > :now)
        """)
    LocalDateTime findLatestUpdatedAtForActiveUserInPlatform(
        @Param("userId") Long userId,
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
          and ra.principalId = :userId
          and ra.tenantId = :tenantId
          and ra.scopeType = :scopeType
          and ra.scopeId = :scopeId
        """)
    void deleteUserAssignmentsInScope(
        @Param("userId") Long userId,
        @Param("tenantId") Long tenantId,
        @Param("scopeType") String scopeType,
        @Param("scopeId") Long scopeId
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

    @Modifying
    @Transactional
    @Query("""
        delete from RoleAssignment ra
        where ra.principalType = 'USER'
          and ra.roleId = :roleId
          and ra.tenantId = :tenantId
          and ra.scopeType = :scopeType
          and ra.scopeId = :scopeId
        """)
    void deleteRoleAssignmentsInScope(
        @Param("roleId") Long roleId,
        @Param("tenantId") Long tenantId,
        @Param("scopeType") String scopeType,
        @Param("scopeId") Long scopeId
    );

    /**
     * 统计指定 scope 下某角色的活跃赋权用户数（ORG/DEPT 基数检查用）。
     */
    @Query("""
        select count(distinct ra.principalId)
        from RoleAssignment ra
        where ra.principalType = 'USER'
          and ra.roleId = :roleId
          and ra.tenantId = :tenantId
          and ra.scopeType = :scopeType
          and ra.scopeId = :scopeId
          and ra.status = 'ACTIVE'
          and ra.startTime <= :now
          and (ra.endTime is null or ra.endTime > :now)
        """)
    long countActiveUsersForRoleInScope(
        @Param("roleId") Long roleId,
        @Param("tenantId") Long tenantId,
        @Param("scopeType") String scopeType,
        @Param("scopeId") Long scopeId,
        @Param("now") LocalDateTime now
    );

    /**
     * 查询用户在指定 scope 下的活跃角色 ID 列表。
     */
    @Query("""
        select distinct ra.roleId
        from RoleAssignment ra
        where ra.principalType = 'USER'
          and ra.principalId = :userId
          and ra.tenantId = :tenantId
          and ra.scopeType = :scopeType
          and ra.scopeId = :scopeId
          and ra.status = 'ACTIVE'
          and ra.startTime <= :now
          and (ra.endTime is null or ra.endTime > :now)
        order by ra.roleId asc
        """)
    List<Long> findActiveRoleIdsForUserInScope(
        @Param("userId") Long userId,
        @Param("tenantId") Long tenantId,
        @Param("scopeType") String scopeType,
        @Param("scopeId") Long scopeId,
        @Param("now") LocalDateTime now
    );

    @Query("""
        select ra
        from RoleAssignment ra
        where ra.principalType = 'USER'
          and ra.principalId = :userId
          and ra.tenantId = :tenantId
          and ra.status = 'ACTIVE'
          and ra.startTime <= :now
          and (ra.endTime is null or ra.endTime > :now)
        order by ra.roleId asc
        """)
    List<RoleAssignment> findActiveAssignmentsForUserInTenant(
        @Param("userId") Long userId,
        @Param("tenantId") Long tenantId,
        @Param("now") LocalDateTime now
    );

    @Query("""
        select ra
        from RoleAssignment ra
        where ra.principalType = 'USER'
          and ra.roleId in :roleIds
          and ra.tenantId = :tenantId
          and ra.status = 'ACTIVE'
          and ra.startTime <= :now
          and (ra.endTime is null or ra.endTime > :now)
        order by ra.principalId asc, ra.roleId asc
        """)
    List<RoleAssignment> findActiveAssignmentsForRoleIdsInTenant(
        @Param("roleIds") Collection<Long> roleIds,
        @Param("tenantId") Long tenantId,
        @Param("now") LocalDateTime now
    );

}
