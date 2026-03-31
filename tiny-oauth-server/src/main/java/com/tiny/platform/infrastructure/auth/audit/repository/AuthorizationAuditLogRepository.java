package com.tiny.platform.infrastructure.auth.audit.repository;

import com.tiny.platform.infrastructure.auth.audit.domain.AuthorizationAuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AuthorizationAuditLogRepository
    extends JpaRepository<AuthorizationAuditLog, Long>, JpaSpecificationExecutor<AuthorizationAuditLog> {

    Page<AuthorizationAuditLog> findByTenantIdOrderByCreatedAtDesc(Long tenantId, Pageable pageable);

    Page<AuthorizationAuditLog> findByTenantIdAndEventTypeOrderByCreatedAtDesc(
        Long tenantId, String eventType, Pageable pageable);

    List<AuthorizationAuditLog> findByTargetUserIdAndTenantIdOrderByCreatedAtDesc(
        Long targetUserId, Long tenantId);

    @Query("""
        select count(a) from AuthorizationAuditLog a
        where (:tenantId is null or a.tenantId = :tenantId)
          and (:eventType is null or a.eventType = :eventType)
          and (:targetUserId is null or a.targetUserId = :targetUserId)
          and (:startTime is null or a.createdAt >= :startTime)
          and (:endTime is null or a.createdAt <= :endTime)
        """)
    long countByFilters(@Param("tenantId") Long tenantId,
                        @Param("eventType") String eventType,
                        @Param("targetUserId") Long targetUserId,
                        @Param("startTime") LocalDateTime startTime,
                        @Param("endTime") LocalDateTime endTime);

    @Query("""
        select count(a) from AuthorizationAuditLog a
        where (:tenantId is null or a.tenantId = :tenantId)
          and (:eventType is null or a.eventType = :eventType)
          and (:targetUserId is null or a.targetUserId = :targetUserId)
          and (:startTime is null or a.createdAt >= :startTime)
          and (:endTime is null or a.createdAt <= :endTime)
          and a.result = 'SUCCESS'
        """)
    long countSuccessByFilters(@Param("tenantId") Long tenantId,
                               @Param("eventType") String eventType,
                               @Param("targetUserId") Long targetUserId,
                               @Param("startTime") LocalDateTime startTime,
                               @Param("endTime") LocalDateTime endTime);

    @Query("""
        select count(a) from AuthorizationAuditLog a
        where (:tenantId is null or a.tenantId = :tenantId)
          and (:eventType is null or a.eventType = :eventType)
          and (:targetUserId is null or a.targetUserId = :targetUserId)
          and (:startTime is null or a.createdAt >= :startTime)
          and (:endTime is null or a.createdAt <= :endTime)
          and a.result = 'DENIED'
        """)
    long countDeniedByFilters(@Param("tenantId") Long tenantId,
                              @Param("eventType") String eventType,
                              @Param("targetUserId") Long targetUserId,
                              @Param("startTime") LocalDateTime startTime,
                              @Param("endTime") LocalDateTime endTime);

    @Query("""
        select a.eventType, count(a) from AuthorizationAuditLog a
        where (:tenantId is null or a.tenantId = :tenantId)
          and (:eventType is null or a.eventType = :eventType)
          and (:targetUserId is null or a.targetUserId = :targetUserId)
          and (:startTime is null or a.createdAt >= :startTime)
          and (:endTime is null or a.createdAt <= :endTime)
        group by a.eventType
        order by count(a) desc, a.eventType asc
        """)
    List<Object[]> countGroupedByEventType(@Param("tenantId") Long tenantId,
                                           @Param("eventType") String eventType,
                                           @Param("targetUserId") Long targetUserId,
                                           @Param("startTime") LocalDateTime startTime,
                                           @Param("endTime") LocalDateTime endTime);

    /**
     * 清理指定日期之前的审计日志（retention 策略）。
     */
    @Modifying
    @Query("delete from AuthorizationAuditLog a where a.createdAt < :cutoff")
    int deleteByCreatedAtBefore(@Param("cutoff") LocalDateTime cutoff);
}
