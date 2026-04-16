package com.tiny.platform.infrastructure.auth.user.repository;

import com.tiny.platform.infrastructure.auth.user.domain.PlatformUserProfile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PlatformUserProfileRepository extends JpaRepository<PlatformUserProfile, Long> {

    Optional<PlatformUserProfile> findByUserId(Long userId);

    boolean existsByUserId(Long userId);

    boolean existsByUserIdAndStatus(Long userId, String status);

    @Query(value = """
        SELECT
          pup.user_id AS userId,
          u.username AS username,
          u.nickname AS nickname,
          COALESCE(NULLIF(TRIM(pup.display_name), ''), NULLIF(TRIM(u.nickname), ''), u.username) AS displayName,
          u.enabled AS userEnabled,
          pup.status AS platformStatus,
          EXISTS(
            SELECT 1
            FROM role_assignment ra
            WHERE ra.principal_type = 'USER'
              AND ra.principal_id = u.id
              AND ra.scope_type = 'PLATFORM'
              AND ra.tenant_id IS NULL
              AND ra.scope_id IS NULL
              AND ra.status = 'ACTIVE'
              AND ra.start_time <= CURRENT_TIMESTAMP
              AND (ra.end_time IS NULL OR ra.end_time > CURRENT_TIMESTAMP)
          ) AS hasPlatformRoleAssignment,
          pup.updated_at AS updatedAt
        FROM platform_user_profile pup
        JOIN `user` u ON u.id = pup.user_id
        WHERE (:keyword IS NULL
            OR u.username LIKE CONCAT('%', :keyword, '%')
            OR COALESCE(u.nickname, '') LIKE CONCAT('%', :keyword, '%')
            OR COALESCE(pup.display_name, '') LIKE CONCAT('%', :keyword, '%'))
          AND (:enabled IS NULL OR u.enabled = :enabled)
          AND (:status IS NULL OR pup.status = :status)
        ORDER BY pup.updated_at DESC, pup.user_id DESC
        """,
        countQuery = """
        SELECT COUNT(*)
        FROM platform_user_profile pup
        JOIN `user` u ON u.id = pup.user_id
        WHERE (:keyword IS NULL
            OR u.username LIKE CONCAT('%', :keyword, '%')
            OR COALESCE(u.nickname, '') LIKE CONCAT('%', :keyword, '%')
            OR COALESCE(pup.display_name, '') LIKE CONCAT('%', :keyword, '%'))
          AND (:enabled IS NULL OR u.enabled = :enabled)
          AND (:status IS NULL OR pup.status = :status)
        """,
        nativeQuery = true)
    Page<PlatformUserListProjection> findPage(@Param("keyword") String keyword,
                                              @Param("enabled") Boolean enabled,
                                              @Param("status") String status,
                                              Pageable pageable);

    @Query(value = """
        SELECT
          pup.user_id AS userId,
          u.username AS username,
          u.nickname AS nickname,
          COALESCE(NULLIF(TRIM(pup.display_name), ''), NULLIF(TRIM(u.nickname), ''), u.username) AS displayName,
          u.email AS email,
          u.phone AS phone,
          u.enabled AS userEnabled,
          u.account_non_expired AS accountNonExpired,
          u.account_non_locked AS accountNonLocked,
          u.credentials_non_expired AS credentialsNonExpired,
          pup.status AS platformStatus,
          EXISTS(
            SELECT 1
            FROM role_assignment ra
            WHERE ra.principal_type = 'USER'
              AND ra.principal_id = u.id
              AND ra.scope_type = 'PLATFORM'
              AND ra.tenant_id IS NULL
              AND ra.scope_id IS NULL
              AND ra.status = 'ACTIVE'
              AND ra.start_time <= CURRENT_TIMESTAMP
              AND (ra.end_time IS NULL OR ra.end_time > CURRENT_TIMESTAMP)
          ) AS hasPlatformRoleAssignment,
          u.last_login_at AS lastLoginAt,
          pup.created_at AS createdAt,
          pup.updated_at AS updatedAt
        FROM platform_user_profile pup
        JOIN `user` u ON u.id = pup.user_id
        WHERE pup.user_id = :userId
        """,
        nativeQuery = true)
    Optional<PlatformUserDetailProjection> findDetailByUserId(@Param("userId") Long userId);
}
