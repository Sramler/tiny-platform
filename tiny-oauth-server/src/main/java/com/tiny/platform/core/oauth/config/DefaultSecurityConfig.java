package com.tiny.platform.core.oauth.config;

import com.tiny.platform.core.oauth.security.AuthenticationFactorAuthorities;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authentication.AuthenticationDetailsSource;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.WebAuthenticationDetails;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.cors.CorsConfigurationSource;

import jakarta.servlet.http.HttpServletRequest;

import com.tiny.platform.infrastructure.auth.user.repository.UserRepository;
import com.tiny.platform.infrastructure.tenant.repository.TenantRepository;
import com.tiny.platform.core.oauth.security.MultiFactorAuthenticationSessionManager;
import com.tiny.platform.core.oauth.service.SecurityService;
import com.tiny.platform.core.oauth.tenant.TenantContextFilter;

@Configuration
@Order(2)
@EnableMethodSecurity(jsr250Enabled = true, securedEnabled = true)
public class DefaultSecurityConfig {

    static final RequestMatcher CSRF_PROTECTED_PATHS = new OrRequestMatcher(
            new AntPathRequestMatcher("/login", "POST"),
            new AntPathRequestMatcher("/self/security/**", "POST")
    );

    private final CorsConfigurationSource corsConfigurationSource;

    public DefaultSecurityConfig(@Qualifier("corsConfigurationSource")CorsConfigurationSource corsConfigurationSource) {
        this.corsConfigurationSource = corsConfigurationSource;
    }

    @Bean
    @Order(2)
    public SecurityFilterChain defaultSecurityFilterChain(HttpSecurity http,
                                                          AuthenticationProvider authenticationProvider,
                                                          CustomLoginSuccessHandler customLoginSuccessHandler,
                                                          CustomLoginFailureHandler customLoginFailureHandler,
                                                          TenantContextFilter tenantContextFilter)
            throws Exception {
        http
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(
                                "/login",
                                "/csrf",
                                "/favicon.ico",
                                "/error",
                                "/webjars/**",
                                "/assets/**",
                                "/css/**",
                                "/js/**"
                        ).permitAll()
                        // challenge 端点允许 partial MFA token 继续完成绑定/验证流程。
                        .requestMatchers(
                                "/self/security/status",
                                "/self/security/totp-bind",
                                "/self/security/totp-verify",
                                "/self/security/totp/pre-bind",
                                "/self/security/totp/bind-form",
                                "/self/security/totp/check-form",
                                "/self/security/totp/skip",
                                "/self/security/skip-mfa-remind"
                        ).access((authentication, context) ->
                                new AuthorizationDecision(hasChallengeFlowAccess(authentication.get())))
                        // 需要完整登录态，但不要求已完成 TOTP 的安全接口。
                        .requestMatchers(
                                "/self/security/totp/bind",
                                "/self/security/totp/check"
                        ).access((authentication, context) ->
                                new AuthorizationDecision(hasSensitiveSecurityAccess(authentication.get())))
                        .requestMatchers(
                                "/scheduling/executors",
                                "/scheduling/quartz/cluster-status"
                        ).access((authentication, context) ->
                                new AuthorizationDecision(hasSchedulingAdminAccess(authentication.get())))
                        // 高敏操作：必须完整登录且已完成 TOTP。
                        .requestMatchers("/self/security/totp/unbind").access((authentication, context) ->
                                new AuthorizationDecision(hasTotpSensitiveAccess(authentication.get())))
                        .requestMatchers("/self/security/**").denyAll()
                        .requestMatchers("/sys/users/**").permitAll()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(tenantContextFilter, UsernamePasswordAuthenticationFilter.class)
                .formLogin(formLogin -> formLogin
                        .loginPage("/login")
                        .loginProcessingUrl("/login")
                        .successHandler(customLoginSuccessHandler)
                        .failureHandler(customLoginFailureHandler)
                        .authenticationDetailsSource(customAuthenticationDetailsSource())
                        .permitAll()
                )
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfTokenRepository())
                        .requireCsrfProtectionMatcher(CSRF_PROTECTED_PATHS)
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(Customizer.withDefaults()))
                .authenticationProvider(authenticationProvider);
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    public CookieCsrfTokenRepository csrfTokenRepository() {
        CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repository.setCookiePath("/");
        repository.setCookieName("XSRF-TOKEN");
        repository.setHeaderName("X-XSRF-TOKEN");
        repository.setParameterName("_csrf");
        return repository;
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
    public TenantContextFilter tenantContextFilter(TenantRepository tenantRepository) {
        return new TenantContextFilter(tenantRepository);
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
                                                               com.tiny.platform.core.oauth.service.AuthenticationAuditService auditService,
                                                               com.tiny.platform.core.oauth.security.LoginFailurePolicy loginFailurePolicy) {
        return new CustomLoginFailureHandler(userRepository, auditService, loginFailurePolicy);
    }

    public static boolean hasChallengeFlowAccess(Authentication authentication) {
        return authentication != null
                && (authentication.isAuthenticated() || AuthenticationFactorAuthorities.hasAnyFactor(authentication));
    }

    public static boolean hasSensitiveSecurityAccess(Authentication authentication) {
        return authentication != null && authentication.isAuthenticated();
    }

    public static boolean hasTotpSensitiveAccess(Authentication authentication) {
        return hasSensitiveSecurityAccess(authentication)
                && AuthenticationFactorAuthorities.hasFactor(
                authentication,
                com.tiny.platform.core.oauth.security.MultiFactorAuthenticationToken.AuthenticationFactorType.TOTP
        );
    }

    public static boolean hasSchedulingAdminAccess(Authentication authentication) {
        if (!hasSensitiveSecurityAccess(authentication)) {
            return false;
        }
        if (authentication.getAuthorities() == null) {
            return false;
        }
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            if ("ROLE_ADMIN".equals(authority.getAuthority())) {
                return true;
            }
        }
        return false;
    }
}
