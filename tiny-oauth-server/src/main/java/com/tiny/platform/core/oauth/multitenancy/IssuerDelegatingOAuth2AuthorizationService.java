package com.tiny.platform.core.oauth.multitenancy;

import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;

import java.util.Objects;

/**
 * 按当前 issuer 委派 OAuth2AuthorizationService。
 */
public class IssuerDelegatingOAuth2AuthorizationService implements OAuth2AuthorizationService {

    private final TenantPerIssuerComponentRegistry registry;
    private final CurrentIssuerIdentifierResolver issuerResolver;
    private final OAuth2AuthorizationService defaultDelegate;

    public IssuerDelegatingOAuth2AuthorizationService(TenantPerIssuerComponentRegistry registry,
                                                      CurrentIssuerIdentifierResolver issuerResolver,
                                                      OAuth2AuthorizationService defaultDelegate) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.issuerResolver = Objects.requireNonNull(issuerResolver, "issuerResolver");
        this.defaultDelegate = Objects.requireNonNull(defaultDelegate, "defaultDelegate");
    }

    @Override
    public void save(OAuth2Authorization authorization) {
        resolveDelegate().save(authorization);
    }

    @Override
    public void remove(OAuth2Authorization authorization) {
        resolveDelegate().remove(authorization);
    }

    @Override
    public OAuth2Authorization findById(String id) {
        return resolveDelegate().findById(id);
    }

    @Override
    public OAuth2Authorization findByToken(String token, OAuth2TokenType tokenType) {
        return resolveDelegate().findByToken(token, tokenType);
    }

    private OAuth2AuthorizationService resolveDelegate() {
        String tenantCode = issuerResolver.resolveCurrentTenantCode();
        if (tenantCode == null || tenantCode.isBlank()) {
            return defaultDelegate;
        }
        OAuth2AuthorizationService delegate = registry.get(tenantCode, OAuth2AuthorizationService.class);
        if (delegate == null) {
            throw new IllegalStateException("No OAuth2AuthorizationService found for issuer tenantCode=" + tenantCode);
        }
        return delegate;
    }
}
