package com.tiny.platform.application.controller.menu;

import com.tiny.platform.application.controller.menu.security.MenuManagementAccessGuard;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

@TestConfiguration
@Profile("rbac-test")
@EnableWebSecurity
@EnableMethodSecurity
public class MenuControllerRbacTestConfig {

    @Bean
    public MenuManagementAccessGuard menuManagementAccessGuard() {
        return new MenuManagementAccessGuard();
    }

    @Bean
    public SecurityFilterChain testSecurityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(c -> c.anyRequest().permitAll());
        return http.build();
    }
}
