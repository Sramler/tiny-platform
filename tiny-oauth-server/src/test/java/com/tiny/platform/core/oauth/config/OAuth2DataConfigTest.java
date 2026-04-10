package com.tiny.platform.core.oauth.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tiny.platform.core.oauth.multitenancy.CurrentIssuerIdentifierResolver;
import com.tiny.platform.core.oauth.multitenancy.IssuerDelegatingOAuth2AuthorizationConsentService;
import com.tiny.platform.core.oauth.multitenancy.IssuerDelegatingOAuth2AuthorizationService;
import com.tiny.platform.core.oauth.multitenancy.IssuerDelegatingRegisteredClientRepository;
import com.tiny.platform.core.oauth.multitenancy.TenantPerIssuerComponentRegistry;
import com.tiny.platform.core.oauth.tenant.IssuerTenantSupport;
import com.tiny.platform.infrastructure.tenant.domain.Tenant;
import com.tiny.platform.infrastructure.tenant.repository.TenantRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;

import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OAuth2DataConfigTest {

    @Test
    void shouldCreateCoreBeansAndDelegatingWrappers() {
        OAuth2DataConfig config = new OAuth2DataConfig();
        TenantPerIssuerComponentRegistry registry = config.tenantPerIssuerComponentRegistry();
        assertThat(registry).isNotNull();

        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        RegisteredClientRepository defaultRepo = config.defaultRegisteredClientRepository(jdbcTemplate);
        assertThat(defaultRepo.getClass().getName())
                .isEqualTo("org.springframework.security.oauth2.server.authorization.client.JdbcRegisteredClientRepository");

        CurrentIssuerIdentifierResolver resolver = mock(CurrentIssuerIdentifierResolver.class);
        RegisteredClientRepository wrappedRepo = config.registeredClientRepository(registry, resolver, defaultRepo);
        assertThat(wrappedRepo).isInstanceOf(IssuerDelegatingRegisteredClientRepository.class);

        DataSource dataSource = mock(DataSource.class);
        ObjectMapper authorizationMapper = new ObjectMapper();
        // JdbcOAuth2AuthorizationService 构造器会在初始化阶段读取 DB 元数据；这里用 mock DataSource 验证配置方法可被调用
        // 且在无真实连接时抛出预期异常（避免引入集成数据库依赖）。
        assertThatThrownBy(() -> config.defaultOAuth2AuthorizationService(dataSource, defaultRepo, authorizationMapper))
                .isInstanceOf(Exception.class);

        OAuth2AuthorizationService defaultAuthzService = mock(OAuth2AuthorizationService.class);

        OAuth2AuthorizationService wrappedAuthzService =
                config.oauth2AuthorizationService(registry, resolver, defaultAuthzService);
        assertThat(wrappedAuthzService).isInstanceOf(IssuerDelegatingOAuth2AuthorizationService.class);

        OAuth2AuthorizationConsentService defaultConsentService =
                config.defaultOAuth2AuthorizationConsentService(jdbcTemplate, defaultRepo);
        assertThat(defaultConsentService.getClass().getName())
                .isEqualTo("org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationConsentService");

        OAuth2AuthorizationConsentService wrappedConsentService =
                config.customOAuth2AuthorizationConsentService(registry, resolver, defaultConsentService);
        assertThat(wrappedConsentService).isInstanceOf(IssuerDelegatingOAuth2AuthorizationConsentService.class);
    }

    @Test
    void shouldRegisterOnlyActiveTenantsByNormalizedCode() throws Exception {
        OAuth2DataConfig config = new OAuth2DataConfig();
        TenantRepository tenantRepository = mock(TenantRepository.class);
        TenantPerIssuerComponentRegistry registry = new TenantPerIssuerComponentRegistry();
        RegisteredClientRepository repo = mock(RegisteredClientRepository.class);
        OAuth2AuthorizationService authzService = mock(OAuth2AuthorizationService.class);
        OAuth2AuthorizationConsentService consentService = mock(OAuth2AuthorizationConsentService.class);

        Tenant activeNullExpiry = tenant("  Acme  ", true, null, null);
        Tenant activeFutureExpiry = tenant("BETA", true, null, LocalDateTime.now().plusDays(1));
        Tenant disabled = tenant("disabled", false, null, null);
        Tenant deleted = tenant("deleted", true, LocalDateTime.now(), null);
        Tenant expired = tenant("expired", true, null, LocalDateTime.now().minusDays(1));
        Tenant blankCode = tenant("   ", true, null, null);
        Tenant nullCode = tenant(null, true, null, null);

        when(tenantRepository.findAll()).thenReturn(Arrays.asList(
                null,
                activeNullExpiry,
                activeFutureExpiry,
                disabled,
                deleted,
                expired,
                blankCode,
                nullCode
        ));

        CommandLineRunner runner = config.registerTenantIssuerComponents(
                tenantRepository,
                registry,
                repo,
                authzService,
                consentService
        );

        runner.run();

        assertThat(registry.containsIssuerKey(IssuerTenantSupport.PLATFORM_ISSUER_KEY)).isTrue();
        assertThat(registry.containsTenant("acme")).isTrue();
        assertThat(registry.containsTenant("beta")).isTrue();
        assertThat(registry.containsTenant("disabled")).isFalse();
        assertThat(registry.containsTenant("deleted")).isFalse();
        assertThat(registry.containsTenant("expired")).isFalse();
        assertThat(registry.tenantCodes()).containsExactlyInAnyOrder("acme", "beta");
        assertThat(registry.issuerKeys()).containsExactlyInAnyOrder(
            IssuerTenantSupport.PLATFORM_ISSUER_KEY,
            "acme",
            "beta"
        );

        assertThat(registry.get("acme", RegisteredClientRepository.class)).isSameAs(repo);
        assertThat(registry.get("acme", OAuth2AuthorizationService.class)).isSameAs(authzService);
        assertThat(registry.get("acme", OAuth2AuthorizationConsentService.class)).isSameAs(consentService);
        assertThat(registry.get(IssuerTenantSupport.PLATFORM_ISSUER_KEY, RegisteredClientRepository.class)).isSameAs(repo);
    }

    private static Tenant tenant(String code, boolean enabled, LocalDateTime deletedAt, LocalDateTime expiresAt) {
        Tenant tenant = new Tenant();
        tenant.setCode(code);
        tenant.setEnabled(enabled);
        tenant.setDeletedAt(deletedAt);
        tenant.setExpiresAt(expiresAt);
        tenant.setName(code == null ? "n/a" : code);
        return tenant;
    }
}
