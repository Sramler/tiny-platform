package com.tiny.platform.infrastructure.auth.user.repository;

import com.tiny.platform.infrastructure.auth.user.domain.UserAuthScopePolicy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 认证作用域策略仓库：只负责策略层读写，不承载认证材料。
 */
@Repository
public interface UserAuthScopePolicyRepository extends JpaRepository<UserAuthScopePolicy, Long> {

  List<UserAuthScopePolicy> findByCredentialId(Long credentialId);

  Optional<UserAuthScopePolicy> findByCredentialIdAndScopeKey(Long credentialId, String scopeKey);

  @Query(
      "select policy from UserAuthScopePolicy policy "
          + "join fetch policy.credential credential "
          + "where credential.userId = :userId and policy.scopeKey = :scopeKey")
  List<UserAuthScopePolicy> findByUserIdAndScopeKey(
      @Param("userId") Long userId, @Param("scopeKey") String scopeKey);

  @Query(
      "select policy from UserAuthScopePolicy policy "
          + "join fetch policy.credential credential "
          + "where credential.userId = :userId "
          + "and credential.authenticationProvider = :authenticationProvider "
          + "and credential.authenticationType = :authenticationType "
          + "and policy.scopeKey = :scopeKey")
  Optional<UserAuthScopePolicy>
      findByUserIdAndAuthenticationProviderAndAuthenticationTypeAndScopeKey(
          @Param("userId") Long userId,
          @Param("authenticationProvider") String authenticationProvider,
          @Param("authenticationType") String authenticationType,
          @Param("scopeKey") String scopeKey);

  boolean existsByCredentialUserId(Long userId);

  boolean existsByCredentialUserIdAndCredentialAuthenticationProviderAndCredentialAuthenticationType(
      Long userId, String authenticationProvider, String authenticationType);

  long countByCredentialId(Long credentialId);

  void deleteByCredentialIdAndScopeKey(Long credentialId, String scopeKey);
}
