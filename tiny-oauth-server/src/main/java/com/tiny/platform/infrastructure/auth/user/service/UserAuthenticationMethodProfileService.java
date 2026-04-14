package com.tiny.platform.infrastructure.auth.user.service;

import com.tiny.platform.core.oauth.tenant.TenantContextContract;
import com.tiny.platform.infrastructure.auth.user.domain.UserAuthCredential;
import com.tiny.platform.infrastructure.auth.user.domain.UserAuthScopePolicy;
import com.tiny.platform.infrastructure.auth.user.domain.UserAuthenticationMethod;
import com.tiny.platform.infrastructure.auth.user.repository.UserAuthScopePolicyRepository;
import com.tiny.platform.infrastructure.auth.user.support.UserAuthenticationMethodProfile;
import com.tiny.platform.infrastructure.auth.user.support.UserAuthenticationMethodProfiles;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * 认证方式运行时读取服务。
 *
 * <p>只读取新模型 {@code user_auth_credential + user_auth_scope_policy}，且只读取与当前激活作用域匹配的
 * {@code scope_key}，不再做跨作用域合并或第二段回退查询（CARD-13A）。</p>
 */
@Service
public class UserAuthenticationMethodProfileService {

    private final UserAuthScopePolicyRepository scopePolicyRepository;

    @Autowired
    public UserAuthenticationMethodProfileService(UserAuthScopePolicyRepository scopePolicyRepository) {
        this.scopePolicyRepository = scopePolicyRepository;
    }

    public List<UserAuthenticationMethodProfile> loadEnabledMethodProfiles(
            Long userId,
            String activeScopeType,
            Long activeTenantId) {
        String scopeKey = resolveScopeKey(activeScopeType, activeTenantId);
        return loadRowsFromNewModel(userId, scopeKey).stream()
                .map(UserAuthenticationMethodProfiles::from)
                .filter(UserAuthenticationMethodProfile::isMethodEnabled)
                .sorted(Comparator.comparingInt(UserAuthenticationMethodProfile::authenticationPriority))
                .toList();
    }

    public Optional<UserAuthenticationMethodProfile> findEffectiveMethodProfile(
            Long userId,
            String activeScopeType,
            Long activeTenantId,
            String authenticationProvider,
            String authenticationType) {
        String scopeKey = resolveScopeKey(activeScopeType, activeTenantId);
        return findRowFromNewModel(
                userId,
                scopeKey,
                authenticationProvider,
                authenticationType
        ).map(UserAuthenticationMethodProfiles::from);
    }

    public boolean existsEffectiveMethod(
            Long userId,
            String activeScopeType,
            Long activeTenantId,
            String authenticationProvider,
            String authenticationType) {
        return findEffectiveMethodProfile(
                userId,
                activeScopeType,
                activeTenantId,
                authenticationProvider,
                authenticationType
        ).isPresent();
    }

    /**
     * 写路径载体字段：与 {@link UserAuthenticationBridgeWriter#normalizeScopeId} 一致——
     * {@code PLATFORM}/{@code GLOBAL} 作用域的 {@code scope_id} 为 {@code null}（非平台租户主键）；
     * {@code TENANT} 作用域为真实 {@code tenant_id}。
     * 平台态认证策略写入使用 {@code scope_type=PLATFORM}、{@code scope_key=PLATFORM}，不是 {@code GLOBAL}。
     */
    public Long resolveStorageTenantIdForWrite(String activeScopeType, Long activeTenantId) {
        return isPlatformScope(activeScopeType) ? null : activeTenantId;
    }

    private List<UserAuthenticationMethod> loadRowsFromNewModel(Long userId, String scopeKey) {
        if (userId == null || scopeKey == null) {
            return List.of();
        }
        return scopePolicyRepository.findByUserIdAndScopeKey(userId, scopeKey).stream()
                .map(this::toStorageRecordFromNewModel)
                .toList();
    }

    private Optional<UserAuthenticationMethod> findRowFromNewModel(
            Long userId,
            String scopeKey,
            String authenticationProvider,
            String authenticationType) {
        if (userId == null
                || scopeKey == null
                || authenticationProvider == null
                || authenticationType == null) {
            return Optional.empty();
        }
        return scopePolicyRepository.findByUserIdAndAuthenticationProviderAndAuthenticationTypeAndScopeKey(
                        userId, authenticationProvider, authenticationType, scopeKey
                )
                .map(this::toStorageRecordFromNewModel);
    }

    private UserAuthenticationMethod toStorageRecordFromNewModel(UserAuthScopePolicy scopePolicy) {
        UserAuthCredential credential = scopePolicy.getCredential();
        UserAuthenticationMethod record = new UserAuthenticationMethod();
        record.setUserId(credential.getUserId());
        record.setTenantId(scopePolicy.getScopeId());
        record.setAuthenticationProvider(credential.getAuthenticationProvider());
        record.setAuthenticationType(credential.getAuthenticationType());
        record.setAuthenticationConfiguration(credential.getAuthenticationConfiguration());
        record.setLastVerifiedAt(credential.getLastVerifiedAt());
        record.setLastVerifiedIp(credential.getLastVerifiedIp());
        record.setExpiresAt(credential.getExpiresAt());
        record.setIsPrimaryMethod(scopePolicy.getIsPrimaryMethod());
        record.setIsMethodEnabled(scopePolicy.getIsMethodEnabled());
        record.setAuthenticationPriority(scopePolicy.getAuthenticationPriority());
        record.setRuntimeScopeType(scopePolicy.getScopeType());
        record.setRuntimeScopeKey(scopePolicy.getScopeKey());
        return record;
    }

    private String resolveScopeKey(String activeScopeType, Long activeTenantId) {
        if (isPlatformScope(activeScopeType)) {
            return UserAuthenticationBridgeWriter.buildScopeKey(
                    UserAuthenticationBridgeWriter.SCOPE_TYPE_PLATFORM, null);
        }
        if (isGlobalScope(activeScopeType)) {
            return UserAuthenticationBridgeWriter.buildScopeKey(
                    UserAuthenticationBridgeWriter.SCOPE_TYPE_GLOBAL, null);
        }
        return UserAuthenticationBridgeWriter.buildScopeKey(
                UserAuthenticationBridgeWriter.SCOPE_TYPE_TENANT,
                activeTenantId
        );
    }

    private boolean isPlatformScope(String activeScopeType) {
        if (activeScopeType == null || activeScopeType.isBlank()) {
            return false;
        }
        return TenantContextContract.SCOPE_TYPE_PLATFORM.equals(
                activeScopeType.trim().toUpperCase(Locale.ROOT)
        );
    }

    private boolean isGlobalScope(String activeScopeType) {
        if (activeScopeType == null || activeScopeType.isBlank()) {
            return false;
        }
        return "GLOBAL".equals(activeScopeType.trim().toUpperCase(Locale.ROOT));
    }

}
