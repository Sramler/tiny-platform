package com.tiny.platform.core.oauth.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.AuthenticationDetailsSource;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.WebAuthenticationDetails;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.cors.CorsConfigurationSource;

import jakarta.servlet.http.HttpServletRequest;

import com.tiny.platform.infrastructure.auth.user.repository.UserRepository;
import com.tiny.platform.core.oauth.security.MultiFactorAuthenticationSessionManager;
import com.tiny.platform.core.oauth.service.SecurityService;

@Configuration
@Order(2)
@EnableMethodSecurity(jsr250Enabled = true, securedEnabled = true)
public class DefaultSecurityConfig {

    private final CorsConfigurationSource corsConfigurationSource;

    private final UserDetailsService userDetailsService;

    public DefaultSecurityConfig(@Qualifier("corsConfigurationSource")CorsConfigurationSource corsConfigurationSource,
                                UserDetailsService userDetailsService) {
        this.corsConfigurationSource = corsConfigurationSource;
        this.userDetailsService = userDetailsService;
    }

    @Bean
    @Order(2)
    public SecurityFilterChain defaultSecurityFilterChain(HttpSecurity http,
                                                          AuthenticationProvider authenticationProvider,
                                                          CustomLoginSuccessHandler customLoginSuccessHandler,
                                                          CustomLoginFailureHandler customLoginFailureHandler)
            throws Exception {
        http
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(
                                "/login",
                                "/favicon.ico",
                                "/error",
                                "/webjars/**",
                                "/assets/**",
                                "/css/**",
                                "/js/**"
                        ).permitAll()
                        .requestMatchers("/sys/users/**").permitAll()
                        .anyRequest().authenticated()
                )
                .formLogin(formLogin -> formLogin
                        .loginPage("/login")
                        .loginProcessingUrl("/login")
                        .successHandler(customLoginSuccessHandler)
                        .failureHandler(customLoginFailureHandler)
                        .authenticationDetailsSource(customAuthenticationDetailsSource())
                        .permitAll()
                )
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .csrf(csrf -> csrf.disable())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(Customizer.withDefaults()))
                .authenticationProvider(authenticationProvider)
                .userDetailsService(userDetailsService);
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    public AuthenticationDetailsSource<HttpServletRequest, WebAuthenticationDetails> customAuthenticationDetailsSource() {
        return new CustomWebAuthenticationDetailsSource();
    }

    /**
     * 配置 SecurityContextRepository
     * <p>
     * Spring Boot 默认不会自动创建 SecurityContextRepository bean，
     * 需要手动创建。使用 HttpSessionSecurityContextRepository 作为默认实现。
     *
     * @return SecurityContextRepository bean
     */
    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    public CustomLoginSuccessHandler customLoginSuccessHandler(SecurityService securityService,
                                                               UserRepository userRepository,
                                                               FrontendProperties frontendProperties,
                                                               MultiFactorAuthenticationSessionManager sessionManager,
                                                               com.tiny.platform.core.oauth.service.AuthenticationAuditService auditService) {
        return new CustomLoginSuccessHandler(securityService, userRepository, frontendProperties, sessionManager, auditService);
    }

    @Bean
    public CustomLoginFailureHandler customLoginFailureHandler(UserRepository userRepository,
                                                               com.tiny.platform.core.oauth.service.AuthenticationAuditService auditService) {
        return new CustomLoginFailureHandler(userRepository, auditService);
    }
}
