package com.tiny.platform.application.controller.tenant;

import com.tiny.platform.application.controller.tenant.security.TenantManagementAccessGuard;
import com.tiny.platform.infrastructure.tenant.config.PlatformTenantProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@TestConfiguration
@Profile("rbac-test")
@EnableWebSecurity
@EnableMethodSecurity
public class TenantControllerRbacTestConfig {

    @Bean
    public PlatformTenantProperties platformTenantProperties() {
        return new PlatformTenantProperties();
    }

    @Bean
    public TenantManagementAccessGuard tenantManagementAccessGuard(
        com.tiny.platform.infrastructure.tenant.repository.TenantRepository tenantRepository,
        PlatformTenantProperties platformTenantProperties
    ) {
        return new TenantManagementAccessGuard(tenantRepository, platformTenantProperties);
    }

    @Bean
    public SecurityFilterChain testSecurityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(c -> c.anyRequest().permitAll());
        return http.build();
    }

    @Bean
    public WebMvcConfigurer pageableConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
                resolvers.add(new PageableHandlerMethodArgumentResolver());
            }
        };
    }
}
