package com.tiny.platform.infrastructure.auth.user.service;

import com.tiny.platform.infrastructure.auth.user.domain.UserAuthCredential;
import com.tiny.platform.infrastructure.auth.user.domain.UserAuthScopePolicy;
import com.tiny.platform.infrastructure.auth.user.domain.UserAuthenticationMethod;
import com.tiny.platform.infrastructure.auth.user.repository.UserAuthCredentialRepository;
import com.tiny.platform.infrastructure.auth.user.repository.UserAuthScopePolicyRepository;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 认证凭证与作用域策略写入（新模型 {@code user_auth_credential} + {@code user_auth_scope_policy}）。
 */
@Service
public class UserAuthenticationBridgeWriter {

  public static final String SCOPE_TYPE_GLOBAL = "GLOBAL";
  public static final String SCOPE_TYPE_PLATFORM = "PLATFORM";
  public static final String SCOPE_TYPE_TENANT = "TENANT";

  private final UserAuthCredentialRepository credentialRepository;
  private final UserAuthScopePolicyRepository scopePolicyRepository;

  public UserAuthenticationBridgeWriter(
      UserAuthCredentialRepository credentialRepository,
      UserAuthScopePolicyRepository scopePolicyRepository) {
    this.credentialRepository = credentialRepository;
    this.scopePolicyRepository = scopePolicyRepository;
  }

  @Transactional
  public void upsert(
      Long userId,
      String authenticationProvider,
      String authenticationType,
      Map<String, Object> authenticationConfiguration,
      LocalDateTime lastVerifiedAt,
      String lastVerifiedIp,
      LocalDateTime expiresAt,
      String scopeType,
      Long scopeId,
      Boolean isPrimaryMethod,
      Boolean isMethodEnabled,
      Integer authenticationPriority) {
    UserAuthCredential credential = credentialRepository
        .findByUserIdAndAuthenticationProviderAndAuthenticationType(
            userId, authenticationProvider, authenticationType)
        .orElseGet(UserAuthCredential::new);
    credential.setUserId(userId);
    credential.setAuthenticationProvider(authenticationProvider);
    credential.setAuthenticationType(authenticationType);
    credential.setAuthenticationConfiguration(copy(authenticationConfiguration));
    credential.setLastVerifiedAt(lastVerifiedAt);
    credential.setLastVerifiedIp(lastVerifiedIp);
    credential.setExpiresAt(expiresAt);
    UserAuthCredential savedCredential = credentialRepository.save(credential);

    String normalizedScopeType = normalizeScopeType(scopeType);
    Long normalizedScopeId = normalizeScopeId(normalizedScopeType, scopeId);
    String scopeKey = buildScopeKey(normalizedScopeType, normalizedScopeId);

    UserAuthScopePolicy scopePolicy = scopePolicyRepository
        .findByCredentialIdAndScopeKey(savedCredential.getId(), scopeKey)
        .orElseGet(UserAuthScopePolicy::new);
    scopePolicy.setCredentialId(savedCredential.getId());
    scopePolicy.setScopeType(normalizedScopeType);
    scopePolicy.setScopeId(normalizedScopeId);
    scopePolicy.setScopeKey(scopeKey);
    scopePolicy.setIsPrimaryMethod(Boolean.TRUE.equals(isPrimaryMethod));
    scopePolicy.setIsMethodEnabled(!Boolean.FALSE.equals(isMethodEnabled));
    scopePolicy.setAuthenticationPriority(authenticationPriority == null ? 0 : authenticationPriority);
    scopePolicyRepository.save(scopePolicy);
  }

  @Transactional
  public void upsertRuntime(UserAuthenticationMethod method) {
    if (method == null
        || method.getUserId() == null
        || method.getAuthenticationProvider() == null
        || method.getAuthenticationType() == null) {
      return;
    }
    String scopeType = resolveRuntimeScopeType(method);
    Long scopeId = resolveRuntimeScopeId(method, scopeType);
    upsert(
        method.getUserId(),
        method.getAuthenticationProvider(),
        method.getAuthenticationType(),
        method.getAuthenticationConfiguration(),
        method.getLastVerifiedAt(),
        method.getLastVerifiedIp(),
        method.getExpiresAt(),
        scopeType,
        scopeId,
        method.getIsPrimaryMethod(),
        method.getIsMethodEnabled(),
        method.getAuthenticationPriority());
  }

  @Transactional
  public void deleteRuntime(UserAuthenticationMethod method) {
    if (method == null
        || method.getUserId() == null
        || method.getAuthenticationProvider() == null
        || method.getAuthenticationType() == null) {
      return;
    }
    String scopeType = resolveRuntimeScopeType(method);
    Long scopeId = resolveRuntimeScopeId(method, scopeType);
    deleteScope(
        method.getUserId(),
        method.getAuthenticationProvider(),
        method.getAuthenticationType(),
        scopeType,
        scopeId);
  }

  @Transactional
  public void deleteScope(Long userId, String authenticationProvider, String authenticationType, String scopeType,
                          Long scopeId) {
    credentialRepository
        .findByUserIdAndAuthenticationProviderAndAuthenticationType(userId, authenticationProvider, authenticationType)
        .ifPresent(credential -> {
          String normalizedScopeType = normalizeScopeType(scopeType);
          Long normalizedScopeId = normalizeScopeId(normalizedScopeType, scopeId);
          String scopeKey = buildScopeKey(normalizedScopeType, normalizedScopeId);
          Long credentialId = credential.getId();
          if (credentialId == null) {
            return;
          }
          scopePolicyRepository.deleteByCredentialIdAndScopeKey(credentialId, scopeKey);
          if (scopePolicyRepository.countByCredentialId(credentialId) == 0L) {
            credentialRepository.deleteById(credentialId);
          }
        });
  }

  private String normalizeScopeType(String scopeType) {
    if (scopeType == null || scopeType.isBlank()) {
      return SCOPE_TYPE_TENANT;
    }
    String normalized = scopeType.trim().toUpperCase(Locale.ROOT);
    return switch (normalized) {
      case SCOPE_TYPE_GLOBAL, SCOPE_TYPE_PLATFORM, SCOPE_TYPE_TENANT -> normalized;
      default -> SCOPE_TYPE_TENANT;
    };
  }

  private Long normalizeScopeId(String scopeType, Long scopeId) {
    if (SCOPE_TYPE_GLOBAL.equals(scopeType) || SCOPE_TYPE_PLATFORM.equals(scopeType)) {
      return null;
    }
    return scopeId;
  }

  private String resolveRuntimeScopeType(UserAuthenticationMethod method) {
    if (method.getRuntimeScopeType() != null && !method.getRuntimeScopeType().isBlank()) {
      return normalizeScopeType(method.getRuntimeScopeType());
    }
    return method.getTenantId() == null ? SCOPE_TYPE_GLOBAL : SCOPE_TYPE_TENANT;
  }

  private Long resolveRuntimeScopeId(UserAuthenticationMethod method, String scopeType) {
    if (SCOPE_TYPE_GLOBAL.equals(scopeType) || SCOPE_TYPE_PLATFORM.equals(scopeType)) {
      return null;
    }
    return method.getTenantId();
  }

  public static String buildScopeKey(String scopeType, Long scopeId) {
    if (SCOPE_TYPE_GLOBAL.equals(scopeType)) {
      return SCOPE_TYPE_GLOBAL;
    }
    if (SCOPE_TYPE_PLATFORM.equals(scopeType)) {
      return SCOPE_TYPE_PLATFORM;
    }
    return SCOPE_TYPE_TENANT + ":" + scopeId;
  }

  private Map<String, Object> copy(Map<String, Object> configuration) {
    if (configuration == null || configuration.isEmpty()) {
      return new HashMap<>();
    }
    return new HashMap<>(configuration);
  }
}
