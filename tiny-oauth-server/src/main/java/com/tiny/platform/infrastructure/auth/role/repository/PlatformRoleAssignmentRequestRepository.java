package com.tiny.platform.infrastructure.auth.role.repository;

import com.tiny.platform.infrastructure.auth.role.domain.PlatformRoleAssignmentRequest;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PlatformRoleAssignmentRequestRepository extends JpaRepository<PlatformRoleAssignmentRequest, Long> {

    boolean existsByTargetUserIdAndRoleIdAndActionTypeAndStatus(
        Long targetUserId,
        Long roleId,
        String actionType,
        String status
    );

    @Query("""
        select r from PlatformRoleAssignmentRequest r
        where (:targetUserId is null or r.targetUserId = :targetUserId)
          and (:status is null or r.status = :status)
        """)
    Page<PlatformRoleAssignmentRequest> search(
        @Param("targetUserId") Long targetUserId,
        @Param("status") String status,
        Pageable pageable
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select r from PlatformRoleAssignmentRequest r
        where r.id = :id
        """)
    Optional<PlatformRoleAssignmentRequest> findByIdForUpdate(@Param("id") Long id);
}
