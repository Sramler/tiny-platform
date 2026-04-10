package com.tiny.platform.core.oauth.multitenancy;

import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsent;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService;

import java.util.Objects;

/**
 * 按当前 issuer 委派 OAuth2AuthorizationConsentService。
 */
public class IssuerDelegatingOAuth2AuthorizationConsentService implements OAuth2AuthorizationConsentService {

    private final TenantPerIssuerComponentRegistry registry;
    private final CurrentIssuerIdentifierResolver issuerResolver;
    private final OAuth2AuthorizationConsentService defaultDelegate;

    public IssuerDelegatingOAuth2AuthorizationConsentService(TenantPerIssuerComponentRegistry registry,
                                                             CurrentIssuerIdentifierResolver issuerResolver,
                                                             OAuth2AuthorizationConsentService defaultDelegate) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.issuerResolver = Objects.requireNonNull(issuerResolver, "issuerResolver");
        this.defaultDelegate = Objects.requireNonNull(defaultDelegate, "defaultDelegate");
    }

    @Override
    public void save(OAuth2AuthorizationConsent authorizationConsent) {
        resolveDelegate().save(authorizationConsent);
    }

    @Override
    public void remove(OAuth2AuthorizationConsent authorizationConsent) {
        resolveDelegate().remove(authorizationConsent);
    }

    @Override
    public OAuth2AuthorizationConsent findById(String registeredClientId, String principalName) {
        return resolveDelegate().findById(registeredClientId, principalName);
    }

    private OAuth2AuthorizationConsentService resolveDelegate() {
        String issuerKey = issuerResolver.resolveCurrentIssuerKey();
        if (issuerKey == null || issuerKey.isBlank()) {
            return defaultDelegate;
        }
        OAuth2AuthorizationConsentService delegate = registry.get(issuerKey, OAuth2AuthorizationConsentService.class);
        return delegate != null ? delegate : defaultDelegate;
    }
}
