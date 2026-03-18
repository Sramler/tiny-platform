package com.tiny.platform.core.oauth.config;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

import com.tiny.platform.core.oauth.security.LoginFailurePolicy;
import com.tiny.platform.core.oauth.security.MultiFactorAuthenticationSessionManager;
import com.tiny.platform.core.oauth.security.AuthUserResolutionService;
import com.tiny.platform.core.oauth.service.SecurityService;
import com.tiny.platform.core.oauth.tenant.TenantContextContract;
import com.tiny.platform.infrastructure.auth.user.repository.UserRepository;
import com.tiny.platform.infrastructure.tenant.repository.TenantRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@SpringBootTest(
    webEnvironment = WebEnvironment.MOCK,
    classes = DefaultSecurityConfigUserEndpointIntegrationTest.SecurityTestApp.class
)
@AutoConfigureMockMvc
class DefaultSecurityConfigUserEndpointIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("anonymous 访问 /sys/users/** 应被认证层拦截")
    void sysUsersProbeShouldRequireAuthentication() throws Exception {
        mockMvc.perform(get("/sys/users/probe").sessionAttr(TenantContextContract.SESSION_ACTIVE_TENANT_ID_KEY, 1L))
            .andExpect(status().isUnauthorized())
            .andExpect(header().string("WWW-Authenticate", "Bearer"));
    }

    @Test
    @DisplayName("authenticated 用户可以访问 /sys/users/**")
    void sysUsersProbeShouldAllowAuthenticatedUser() throws Exception {
        mockMvc.perform(get("/sys/users/probe")
                .sessionAttr(TenantContextContract.SESSION_ACTIVE_TENANT_ID_KEY, 1L)
                .with(user("alice").roles("USER")))
            .andExpect(status().isOk())
            .andExpect(content().string("ok"));
    }

    @SpringBootConfiguration
    @Import({
        DefaultSecurityConfig.class,
        DefaultSecurityConfigUserEndpointIntegrationTest.ProbeController.class,
        org.springframework.boot.autoconfigure.http.HttpMessageConvertersAutoConfiguration.class,
        org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration.class
    })
    static class SecurityTestApp {

        @Bean("corsConfigurationSource")
        CorsConfigurationSource corsConfigurationSource() {
            CorsConfiguration configuration = new CorsConfiguration();
            configuration.addAllowedOriginPattern("*");
            configuration.addAllowedHeader("*");
            configuration.addAllowedMethod("*");
            UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
            source.registerCorsConfiguration("/**", configuration);
            return source;
        }

        @Bean
        AuthenticationProvider authenticationProvider() {
            return Mockito.mock(AuthenticationProvider.class);
        }

        @Bean
        SecurityService securityService() {
            return Mockito.mock(SecurityService.class);
        }

        @Bean
        UserRepository userRepository() {
            return Mockito.mock(UserRepository.class);
        }

        @Bean
        AuthUserResolutionService authUserResolutionService() {
            return Mockito.mock(AuthUserResolutionService.class);
        }

        @Bean
        TenantRepository tenantRepository() {
            return Mockito.mock(TenantRepository.class);
        }

        @Bean
        FrontendProperties frontendProperties() {
            return new FrontendProperties();
        }

        @Bean
        MultiFactorAuthenticationSessionManager multiFactorAuthenticationSessionManager() {
            return Mockito.mock(MultiFactorAuthenticationSessionManager.class);
        }

        @Bean
        com.tiny.platform.core.oauth.service.AuthenticationAuditService authenticationAuditService() {
            return Mockito.mock(com.tiny.platform.core.oauth.service.AuthenticationAuditService.class);
        }

        @Bean
        LoginFailurePolicy loginFailurePolicy() {
            return Mockito.mock(LoginFailurePolicy.class);
        }

        @Bean
        JwtDecoder jwtDecoder() {
            return token -> {
                throw new UnsupportedOperationException("JWT decoding is not needed in this test");
            };
        }
    }

    @RestController
    static class ProbeController {
        @GetMapping("/sys/users/probe")
        public String probe() {
            return "ok";
        }
    }
}
