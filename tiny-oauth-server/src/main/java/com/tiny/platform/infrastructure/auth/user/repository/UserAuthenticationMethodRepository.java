package com.tiny.platform.infrastructure.auth.user.repository;

import com.tiny.platform.infrastructure.auth.user.domain.UserAuthenticationMethod;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserAuthenticationMethodRepository extends JpaRepository<UserAuthenticationMethod, Long> {

    /**
     * 根据用户ID查找所有认证方法
     */
    List<UserAuthenticationMethod> findByUserIdAndTenantId(Long userId, Long tenantId);

    /**
     * 根据用户ID和认证提供者查找认证方法
     */
    List<UserAuthenticationMethod> findByUserIdAndTenantIdAndAuthenticationProvider(Long userId, Long tenantId, String authenticationProvider);

    /**
     * 根据用户ID、认证提供者和认证类型查找认证方法
     */
    Optional<UserAuthenticationMethod> findByUserIdAndTenantIdAndAuthenticationProviderAndAuthenticationType(
            Long userId, Long tenantId, String authenticationProvider, String authenticationType);

    /**
     * 查找用户的主要认证方法
     */
    @Query("SELECT uam FROM UserAuthenticationMethod uam WHERE uam.userId = :userId AND uam.tenantId = :tenantId AND uam.isPrimaryMethod = true")
    Optional<UserAuthenticationMethod> findPrimaryMethodByUserId(@Param("userId") Long userId, @Param("tenantId") Long tenantId);

    /**
     * 查找用户启用的认证方法（按优先级排序）
     */
    @Query("SELECT uam FROM UserAuthenticationMethod uam WHERE uam.userId = :userId AND uam.tenantId = :tenantId AND uam.isMethodEnabled = true ORDER BY uam.authenticationPriority ASC")
    List<UserAuthenticationMethod> findEnabledMethodsByUserId(@Param("userId") Long userId, @Param("tenantId") Long tenantId);

    /**
     * 检查用户是否有指定的认证方法
     */
    boolean existsByUserIdAndTenantIdAndAuthenticationProviderAndAuthenticationType(
            Long userId, Long tenantId, String authenticationProvider, String authenticationType);
}
