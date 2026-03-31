package com.tiny.platform.core.oauth.session;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserSessionRepository extends JpaRepository<UserSession, Long> {

    Optional<UserSession> findBySessionId(String sessionId);

    @Query("""
        SELECT s FROM UserSession s
        WHERE s.userId = :userId
          AND s.tenantId = :tenantId
          AND s.status = :status
        ORDER BY COALESCE(s.lastSeenAt, s.createdAt) DESC, s.createdAt DESC
        """)
    List<UserSession> findByUserIdAndTenantIdAndStatusOrderByActivityDesc(Long userId,
                                                                          Long tenantId,
                                                                          UserSessionState status);
}
