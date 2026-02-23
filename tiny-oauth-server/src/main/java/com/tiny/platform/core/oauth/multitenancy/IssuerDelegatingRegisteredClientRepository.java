package com.tiny.platform.core.oauth.multitenancy;

import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;

import java.util.Objects;

/**
 * 按当前 issuer 委派 RegisteredClientRepository。
 */
public class IssuerDelegatingRegisteredClientRepository implements RegisteredClientRepository {

    private final TenantPerIssuerComponentRegistry registry;
    private final CurrentIssuerIdentifierResolver issuerResolver;
    private final RegisteredClientRepository defaultDelegate;

    public IssuerDelegatingRegisteredClientRepository(TenantPerIssuerComponentRegistry registry,
                                                      CurrentIssuerIdentifierResolver issuerResolver,
                                                      RegisteredClientRepository defaultDelegate) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.issuerResolver = Objects.requireNonNull(issuerResolver, "issuerResolver");
        this.defaultDelegate = Objects.requireNonNull(defaultDelegate, "defaultDelegate");
    }

    @Override
    public void save(RegisteredClient registeredClient) {
        resolveDelegate().save(registeredClient);
    }

    @Override
    public RegisteredClient findById(String id) {
        return resolveDelegate().findById(id);
    }

    @Override
    public RegisteredClient findByClientId(String clientId) {
        return resolveDelegate().findByClientId(clientId);
    }

    private RegisteredClientRepository resolveDelegate() {
        String tenantCode = issuerResolver.resolveCurrentTenantCode();
        if (tenantCode == null || tenantCode.isBlank()) {
            return defaultDelegate;
        }
        RegisteredClientRepository delegate = registry.get(tenantCode, RegisteredClientRepository.class);
        if (delegate == null) {
            throw new IllegalStateException("No RegisteredClientRepository found for issuer tenantCode=" + tenantCode);
        }
        return delegate;
    }
}
