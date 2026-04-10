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
        String issuerKey = issuerResolver.resolveCurrentIssuerKey();
        if (issuerKey == null || issuerKey.isBlank()) {
            return defaultDelegate;
        }
        RegisteredClientRepository delegate = registry.get(issuerKey, RegisteredClientRepository.class);
        return delegate != null ? delegate : defaultDelegate;
    }
}
