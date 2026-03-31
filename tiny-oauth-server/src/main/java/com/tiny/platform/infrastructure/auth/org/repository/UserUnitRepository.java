package com.tiny.platform.infrastructure.auth.org.repository;

import com.tiny.platform.infrastructure.auth.org.domain.UserUnit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserUnitRepository extends JpaRepository<UserUnit, Long> {

    List<UserUnit> findByTenantIdAndUserIdAndStatus(Long tenantId, Long userId, String status);

    List<UserUnit> findByTenantIdAndUnitIdAndStatus(Long tenantId, Long unitId, String status);

    Optional<UserUnit> findByTenantIdAndUserIdAndUnitId(Long tenantId, Long userId, Long unitId);

    /**
     * 查找用户在租户内的主部门归属。
     */
    @Query("""
        select uu from UserUnit uu
        where uu.tenantId = :tenantId and uu.userId = :userId
          and uu.isPrimary = true and uu.status = 'ACTIVE'
        """)
    Optional<UserUnit> findPrimaryByTenantIdAndUserId(
        @Param("tenantId") Long tenantId,
        @Param("userId") Long userId
    );

    @Query("""
        select count(uu) from UserUnit uu
        where uu.tenantId = :tenantId and uu.unitId = :unitId and uu.status = 'ACTIVE'
        """)
    long countActiveByTenantIdAndUnitId(
        @Param("tenantId") Long tenantId,
        @Param("unitId") Long unitId
    );

    @Query("""
        select distinct uu.userId from UserUnit uu
        where uu.tenantId = :tenantId and uu.unitId in :unitIds and uu.status = :status
        """)
    List<Long> findUserIdsByTenantIdAndUnitIdInAndStatus(
        @Param("tenantId") Long tenantId,
        @Param("unitIds") Collection<Long> unitIds,
        @Param("status") String status
    );

    @Query("""
        select distinct uu.unitId from UserUnit uu
        where uu.tenantId = :tenantId and uu.userId = :userId and uu.status = :status
        """)
    List<Long> findUnitIdsByTenantIdAndUserIdAndStatus(
        @Param("tenantId") Long tenantId,
        @Param("userId") Long userId,
        @Param("status") String status
    );

    boolean existsByTenantIdAndUserIdAndUnitId(Long tenantId, Long userId, Long unitId);
}
