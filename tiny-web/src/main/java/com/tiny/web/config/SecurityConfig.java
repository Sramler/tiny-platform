package com.tiny.web.config;

import com.tiny.web.sys.security.PasswordAuthenticationProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.server.authorization.token.*;
import org.springframework.security.web.SecurityFilterChain;
import com.tiny.web.sys.repository.UserRepository;
import com.tiny.web.sys.security.UserAuthPasswordLookupService;

import java.util.List;

@Configuration
public class SecurityConfig {
    /**
     * 默认的 Web 安全过滤链，控制如 /login、静态资源的访问
     */
//    @Bean
//    @Order(2)
//    public SecurityFilterChain defaultSecurityFilterChain(HttpSecurity http) throws Exception {
//        http
//                .authorizeHttpRequests(auth -> auth
//                .requestMatchers("/login", "/favicon.ico").permitAll() // 登录页放行
//                .anyRequest().authenticated() // 其他都需登录
//            )
//            .formLogin(Customizer.withDefaults()); // 使用默认表单登录
//        return http.build();
//    }



    @Bean
    @Order(2)
    public SecurityFilterChain defaultSecurityFilterChain(HttpSecurity http,
                                                          AuthenticationProvider passwordAuthenticationProvider) throws Exception {
        // 创建一个只包含我们自定义 AuthenticationProvider 的 AuthenticationManager
        // 这样可以避免 Spring Security 自动创建 DaoAuthenticationProvider
        AuthenticationManager authenticationManager = new ProviderManager(List.of(passwordAuthenticationProvider));
        
        http
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/login", "/favicon.ico", "/error").permitAll() // 登录页和错误页放行
                        .anyRequest().authenticated()
                )
                .formLogin(formLogin -> formLogin
                        .defaultSuccessUrl("/", true)
                        .permitAll()
                ) // ⭐️ 启用表单登录页
                .authenticationManager(authenticationManager) // 使用我们自定义的 AuthenticationManager（只包含 PasswordAuthenticationProvider）
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(Customizer.withDefaults()) // ⭐️ 启用 JWT 资源服务器
                );

        return http.build();
    }

    @Bean
    public AuthenticationProvider passwordAuthenticationProvider(UserRepository userRepository,
                                                                 UserAuthPasswordLookupService userAuthPasswordLookupService,
                                                                 PasswordEncoder passwordEncoder,
                                                                 UserDetailsService userDetailsService) {
        return new PasswordAuthenticationProvider(userRepository, userAuthPasswordLookupService, passwordEncoder, userDetailsService);
    }

    /**
     * 密码编码器，使用 BCrypt 加密
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    public OAuth2TokenGenerator<?> tokenGenerator(JwtEncoder jwtEncoder) {
        JwtGenerator jwtGenerator = new JwtGenerator(jwtEncoder);
        OAuth2AccessTokenGenerator accessTokenGenerator = new OAuth2AccessTokenGenerator();
        OAuth2RefreshTokenGenerator refreshTokenGenerator = new OAuth2RefreshTokenGenerator();

        return new DelegatingOAuth2TokenGenerator(
                accessTokenGenerator,
                refreshTokenGenerator,
                jwtGenerator // 注意：不叫 OidcIdTokenGenerator，JwtGenerator 会负责处理 id_token 和 access_token
        );
    }
}