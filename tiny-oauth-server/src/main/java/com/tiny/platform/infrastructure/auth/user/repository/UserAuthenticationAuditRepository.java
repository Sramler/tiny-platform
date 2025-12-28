package com.tiny.platform.infrastructure.auth.user.repository;

import com.tiny.platform.infrastructure.auth.user.domain.UserAuthenticationAudit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户认证审计 Repository
 */
@Repository
public interface UserAuthenticationAuditRepository extends JpaRepository<UserAuthenticationAudit, Long> {

    /**
     * 根据用户ID查询审计记录
     */
    List<UserAuthenticationAudit> findByUserIdOrderByCreatedAtDesc(Long userId);

    /**
     * 根据用户名查询审计记录
     */
    List<UserAuthenticationAudit> findByUsernameOrderByCreatedAtDesc(String username);

    /**
     * 根据事件类型查询审计记录
     */
    List<UserAuthenticationAudit> findByEventTypeOrderByCreatedAtDesc(String eventType);

    /**
     * 根据用户ID和事件类型查询审计记录
     */
    List<UserAuthenticationAudit> findByUserIdAndEventTypeOrderByCreatedAtDesc(Long userId, String eventType);

    /**
     * 根据用户ID分页查询审计记录
     */
    Page<UserAuthenticationAudit> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    /**
     * 根据用户ID和事件类型分页查询审计记录
     */
    Page<UserAuthenticationAudit> findByUserIdAndEventTypeOrderByCreatedAtDesc(Long userId, String eventType, Pageable pageable);

    /**
     * 根据用户名分页查询审计记录
     */
    Page<UserAuthenticationAudit> findByUsernameOrderByCreatedAtDesc(String username, Pageable pageable);

    /**
     * 查询指定时间范围内的审计记录
     */
    @Query("SELECT a FROM UserAuthenticationAudit a WHERE a.createdAt BETWEEN :startTime AND :endTime ORDER BY a.createdAt DESC")
    List<UserAuthenticationAudit> findByCreatedAtBetween(@Param("startTime") LocalDateTime startTime, 
                                                          @Param("endTime") LocalDateTime endTime);

    /**
     * 查询失败事件
     */
    List<UserAuthenticationAudit> findBySuccessFalseOrderByCreatedAtDesc();

    /**
     * 查询指定用户的失败事件
     */
    List<UserAuthenticationAudit> findByUserIdAndSuccessFalseOrderByCreatedAtDesc(Long userId);

    /**
     * 统计指定用户的登录次数
     */
    @Query("SELECT COUNT(a) FROM UserAuthenticationAudit a WHERE a.userId = :userId AND a.eventType = 'LOGIN' AND a.success = true")
    Long countSuccessfulLoginsByUserId(@Param("userId") Long userId);

    /**
     * 统计指定用户的失败登录次数
     */
    @Query("SELECT COUNT(a) FROM UserAuthenticationAudit a WHERE a.userId = :userId AND a.eventType = 'LOGIN' AND a.success = false")
    Long countFailedLoginsByUserId(@Param("userId") Long userId);
}
