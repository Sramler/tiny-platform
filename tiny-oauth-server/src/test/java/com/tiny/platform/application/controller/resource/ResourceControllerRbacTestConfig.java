package com.tiny.platform.application.controller.resource;

import com.tiny.platform.application.controller.menu.security.MenuManagementAccessGuard;
import com.tiny.platform.application.controller.resource.security.ResourceManagementAccessGuard;
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
import com.tiny.platform.core.oauth.security.ApiEndpointRequirementFilter;
import com.tiny.platform.infrastructure.auth.resource.service.ResourceService;

import java.util.List;

@TestConfiguration
@Profile("rbac-test")
@EnableWebSecurity
@EnableMethodSecurity
public class ResourceControllerRbacTestConfig {

    @Bean
    public ResourceManagementAccessGuard resourceManagementAccessGuard() {
        return new ResourceManagementAccessGuard();
    }

    @Bean
    public MenuManagementAccessGuard menuManagementAccessGuard() {
        return new MenuManagementAccessGuard();
    }

    @Bean
    public SecurityFilterChain testSecurityFilterChain(HttpSecurity http,
                                                       ResourceService resourceService) throws Exception {
        http.csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(c -> c.anyRequest().permitAll());
        http.addFilterAfter(new ApiEndpointRequirementFilter(resourceService), org.springframework.security.web.authentication.AnonymousAuthenticationFilter.class);
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
