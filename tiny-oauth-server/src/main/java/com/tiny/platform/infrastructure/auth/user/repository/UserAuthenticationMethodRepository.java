package com.tiny.platform.infrastructure.auth.user.repository;

import com.tiny.platform.infrastructure.auth.user.domain.UserAuthenticationMethod;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * <p>认证方法查询：{@code tenant_id IS NULL} 表示用户级全局配置；登录时优先匹配当前解析租户下的行，再回退全局行
 * （合并逻辑见 {@code MultiAuthenticationProvider}）。</p>
 */
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

    List<UserAuthenticationMethod> findByUserIdAndTenantIdIsNullAndIsMethodEnabledTrueOrderByAuthenticationPriorityAsc(Long userId);

    Optional<UserAuthenticationMethod> findByUserIdAndTenantIdIsNullAndAuthenticationProviderAndAuthenticationType(
            Long userId, String authenticationProvider, String authenticationType);

    boolean existsByUserIdAndTenantIdIsNullAndAuthenticationProviderAndAuthenticationType(
            Long userId, String authenticationProvider, String authenticationType);

    /**
     * 检查用户是否有指定的认证方法
     */
    boolean existsByUserIdAndTenantIdAndAuthenticationProviderAndAuthenticationType(
            Long userId, Long tenantId, String authenticationProvider, String authenticationType);

    /**
     * 先查租户内，再查 {@code tenant_id IS NULL} 全局行。
     */
    default Optional<UserAuthenticationMethod> findEffectiveAuthenticationMethod(
            Long userId,
            Long tenantId,
            String authenticationProvider,
            String authenticationType) {
        if (tenantId != null) {
            Optional<UserAuthenticationMethod> tenantRow = findByUserIdAndTenantIdAndAuthenticationProviderAndAuthenticationType(
                    userId, tenantId, authenticationProvider, authenticationType);
            if (tenantRow.isPresent()) {
                return tenantRow;
            }
        }
        return findByUserIdAndTenantIdIsNullAndAuthenticationProviderAndAuthenticationType(
                userId, authenticationProvider, authenticationType);
    }

    /**
     * 是否存在有效认证方法（租户内或全局）。
     */
    default boolean existsEffectiveAuthenticationMethod(
            Long userId, Long tenantId, String authenticationProvider, String authenticationType) {
        if (tenantId != null
                && existsByUserIdAndTenantIdAndAuthenticationProviderAndAuthenticationType(
                        userId, tenantId, authenticationProvider, authenticationType)) {
            return true;
        }
        return existsByUserIdAndTenantIdIsNullAndAuthenticationProviderAndAuthenticationType(
                userId, authenticationProvider, authenticationType);
    }
}
