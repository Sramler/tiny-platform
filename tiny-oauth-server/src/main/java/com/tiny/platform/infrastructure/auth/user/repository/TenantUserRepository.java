package com.tiny.platform.infrastructure.auth.user.repository;

import com.tiny.platform.infrastructure.auth.user.domain.TenantUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface TenantUserRepository extends JpaRepository<TenantUser, Long> {

    boolean existsByTenantIdAndUserIdAndStatus(Long tenantId, Long userId, String status);

    boolean existsByTenantIdAndStatus(Long tenantId, String status);

    @Query("select tu.userId from TenantUser tu where tu.tenantId = :tenantId and tu.status = :status")
    List<Long> findUserIdsByTenantIdAndStatus(@Param("tenantId") Long tenantId, @Param("status") String status);

    @Query("select tu.userId from TenantUser tu where tu.tenantId = :tenantId and tu.status = :status and tu.userId in :userIds")
    List<Long> findUserIdsByTenantIdAndUserIdInAndStatus(@Param("tenantId") Long tenantId,
                                                         @Param("userIds") Collection<Long> userIds,
                                                         @Param("status") String status);

    Optional<TenantUser> findByTenantIdAndUserId(Long tenantId, Long userId);

    Optional<TenantUser> findByTenantIdAndUserIdAndStatus(Long tenantId, Long userId, String status);
}
