package com.tiny.platform.infrastructure.auth.user.service;

import com.tiny.platform.core.oauth.tenant.TenantContextContract;
import com.tiny.platform.infrastructure.auth.user.domain.UserAuthCredential;
import com.tiny.platform.infrastructure.auth.user.domain.UserAuthScopePolicy;
import com.tiny.platform.infrastructure.auth.user.domain.UserAuthenticationMethod;
import com.tiny.platform.infrastructure.auth.user.repository.UserAuthScopePolicyRepository;
import com.tiny.platform.infrastructure.auth.user.support.UserAuthenticationMethodMerge;
import com.tiny.platform.infrastructure.auth.user.support.UserAuthenticationMethodProfile;
import com.tiny.platform.infrastructure.auth.user.support.UserAuthenticationMethodProfiles;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * 认证方式运行时读取服务。
 *
 * <p>只读取新模型 {@code user_auth_credential + user_auth_scope_policy}。</p>
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
        ScopeSelection selection = resolveScopeSelection(activeScopeType, activeTenantId);
        List<UserAuthenticationMethod> primaryRows = loadRowsFromNewModel(userId, selection.primaryScopeKey());
        List<UserAuthenticationMethod> fallbackRows = loadFallbackRows(userId, selection);

        return UserAuthenticationMethodMerge.mergePreferPrimary(primaryRows, fallbackRows).stream()
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
        ScopeSelection selection = resolveScopeSelection(activeScopeType, activeTenantId);
        Optional<UserAuthenticationMethod> primary = findRowFromNewModel(
                userId,
                selection.primaryScopeKey(),
                authenticationProvider,
                authenticationType
        );
        if (primary.isPresent()) {
            return primary.map(UserAuthenticationMethodProfiles::from);
        }
        return findRowFromNewModel(
                userId,
                selection.fallbackScopeKey(),
                authenticationProvider,
                authenticationType
        )
                .map(UserAuthenticationMethodProfiles::from);
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
     * 新写路径的桥接规则：平台侧优先写入全局行（tenant_id IS NULL），租户侧仍写入真实 tenant_id。
     */
    public Long resolveStorageTenantIdForWrite(String activeScopeType, Long activeTenantId) {
        return isPlatformScope(activeScopeType) ? null : activeTenantId;
    }

    private List<UserAuthenticationMethod> loadFallbackRows(Long userId, ScopeSelection selection) {
        if (Objects.equals(selection.primaryScopeKey(), selection.fallbackScopeKey())) {
            return List.of();
        }
        return loadRowsFromNewModel(userId, selection.fallbackScopeKey());
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

    private ScopeSelection resolveScopeSelection(String activeScopeType, Long activeTenantId) {
        if (isPlatformScope(activeScopeType)) {
            return new ScopeSelection(
                    UserAuthenticationBridgeWriter.buildScopeKey(UserAuthenticationBridgeWriter.SCOPE_TYPE_PLATFORM, null),
                    UserAuthenticationBridgeWriter.buildScopeKey(UserAuthenticationBridgeWriter.SCOPE_TYPE_GLOBAL, null)
            );
        }
        if (isGlobalScope(activeScopeType)) {
            String scopeKey = UserAuthenticationBridgeWriter.buildScopeKey(
                    UserAuthenticationBridgeWriter.SCOPE_TYPE_GLOBAL,
                    null
            );
            return new ScopeSelection(scopeKey, scopeKey);
        }
        return new ScopeSelection(
                UserAuthenticationBridgeWriter.buildScopeKey(
                        UserAuthenticationBridgeWriter.SCOPE_TYPE_TENANT,
                        activeTenantId
                ),
                UserAuthenticationBridgeWriter.buildScopeKey(
                        UserAuthenticationBridgeWriter.SCOPE_TYPE_GLOBAL,
                        null
                )
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

    private record ScopeSelection(String primaryScopeKey, String fallbackScopeKey) {
    }

}
