package com.tiny.platform.core.oauth.multitenancy;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsent;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class IssuerDelegatingOAuth2ServicesTest {

    @Test
    void registeredClientRepositoryFallsBackToDefaultDelegateWhenIssuerTenantIsUnknown() {
        TenantPerIssuerComponentRegistry registry = new TenantPerIssuerComponentRegistry();
        CurrentIssuerIdentifierResolver resolver = mock(CurrentIssuerIdentifierResolver.class);
        RegisteredClientRepository defaultDelegate = mock(RegisteredClientRepository.class);
        RegisteredClientRepository tenantDelegate = mock(RegisteredClientRepository.class);
        RegisteredClient registeredClient = mock(RegisteredClient.class);

        registry.register("default", RegisteredClientRepository.class, tenantDelegate);
        when(resolver.resolveCurrentTenantCode()).thenReturn("bench-1m");
        when(defaultDelegate.findByClientId("vue-client")).thenReturn(registeredClient);

        IssuerDelegatingRegisteredClientRepository repository =
                new IssuerDelegatingRegisteredClientRepository(registry, resolver, defaultDelegate);

        assertThat(repository.findByClientId("vue-client")).isSameAs(registeredClient);
        verify(defaultDelegate).findByClientId("vue-client");
        verifyNoInteractions(tenantDelegate);
    }

    @Test
    void authorizationServiceFallsBackToDefaultDelegateWhenIssuerTenantIsUnknown() {
        TenantPerIssuerComponentRegistry registry = new TenantPerIssuerComponentRegistry();
        CurrentIssuerIdentifierResolver resolver = mock(CurrentIssuerIdentifierResolver.class);
        OAuth2AuthorizationService defaultDelegate = mock(OAuth2AuthorizationService.class);
        OAuth2AuthorizationService tenantDelegate = mock(OAuth2AuthorizationService.class);
        OAuth2Authorization authorization = mock(OAuth2Authorization.class);

        registry.register("default", OAuth2AuthorizationService.class, tenantDelegate);
        when(resolver.resolveCurrentTenantCode()).thenReturn("bench-1m");
        when(defaultDelegate.findById("auth-id")).thenReturn(authorization);

        IssuerDelegatingOAuth2AuthorizationService service =
                new IssuerDelegatingOAuth2AuthorizationService(registry, resolver, defaultDelegate);

        assertThat(service.findById("auth-id")).isSameAs(authorization);
        verify(defaultDelegate).findById("auth-id");
        verifyNoInteractions(tenantDelegate);
    }

    @Test
    void authorizationConsentServiceFallsBackToDefaultDelegateWhenIssuerTenantIsUnknown() {
        TenantPerIssuerComponentRegistry registry = new TenantPerIssuerComponentRegistry();
        CurrentIssuerIdentifierResolver resolver = mock(CurrentIssuerIdentifierResolver.class);
        OAuth2AuthorizationConsentService defaultDelegate = mock(OAuth2AuthorizationConsentService.class);
        OAuth2AuthorizationConsentService tenantDelegate = mock(OAuth2AuthorizationConsentService.class);

        registry.register("default", OAuth2AuthorizationConsentService.class, tenantDelegate);
        when(resolver.resolveCurrentTenantCode()).thenReturn("bench-1m");

        IssuerDelegatingOAuth2AuthorizationConsentService service =
                new IssuerDelegatingOAuth2AuthorizationConsentService(registry, resolver, defaultDelegate);

        assertThat(service.findById("client-id", "alice")).isNull();
        verify(defaultDelegate).findById("client-id", "alice");
        verifyNoInteractions(tenantDelegate);
    }
}
