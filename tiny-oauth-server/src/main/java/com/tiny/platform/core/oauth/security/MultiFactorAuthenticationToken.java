package com.tiny.platform.core.oauth.security;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * MultiFactorAuthenticationToken - 完整重构版
 *
 * 改进：
 * - 枚举名称改为 AuthenticationProviderType 和 AuthenticationFactorType，更直观
 * - 使用 EnumSet 表示已完成认证因子
 * - credentials 可被擦除，@JsonIgnore 避免序列化到前端
 * - 不依赖 mutable 字段实现 equals/hashCode
 * - 提供 promoteToFullyAuthenticated 方法升级为完全认证
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class MultiFactorAuthenticationToken extends AbstractAuthenticationToken {

    private static final long serialVersionUID = 1L;

    /**
     * 认证提供者类型
     */
    public enum AuthenticationProviderType {
        LOCAL, GITHUB, GOOGLE, LDAP, UNKNOWN;

        public static AuthenticationProviderType from(String s) {
            if (s == null) return UNKNOWN;
            try {
                return AuthenticationProviderType.valueOf(s.trim().toUpperCase());
            } catch (Exception e) {
                return UNKNOWN;
            }
        }
    }

    /**
     * 认证因子类型
     */
    public enum AuthenticationFactorType {
        PASSWORD, TOTP, OAUTH2, EMAIL, MFA, UNKNOWN;

        public static AuthenticationFactorType from(String s) {
            if (s == null) return UNKNOWN;
            try {
                return AuthenticationFactorType.valueOf(s.trim().toUpperCase());
            } catch (Exception e) {
                return UNKNOWN;
            }
        }
    }

    // --- 核心字段 ---

    private final String username;

    /**
     * credentials 标记为可被清除（非 final）
     * 且对 Jackson 隐藏，避免不小心序列化到前端/日志
     */
    @JsonIgnore
    private Object credentials;

    private final AuthenticationProviderType provider;
    private final EnumSet<AuthenticationFactorType> completedFactors;

    // --- constructors ---

    /** 未认证构造器（字符串兼容） */
    public MultiFactorAuthenticationToken(String username, Object credentials, String provider, String initialFactor) {
        this(username, credentials,
                AuthenticationProviderType.from(provider),
                AuthenticationFactorType.from(initialFactor));
    }

    /** 未认证构造器（枚举） */
    public MultiFactorAuthenticationToken(String username, Object credentials,
                                          AuthenticationProviderType provider,
                                          AuthenticationFactorType initialFactor) {
        super(Collections.emptyList());
        this.username = Objects.requireNonNull(username, "username");
        this.credentials = credentials;
        this.provider = provider == null ? AuthenticationProviderType.UNKNOWN : provider;
        this.completedFactors = EnumSet.noneOf(AuthenticationFactorType.class);
        if (initialFactor != null && initialFactor != AuthenticationFactorType.UNKNOWN) {
            this.completedFactors.add(initialFactor);
        }
        setAuthenticated(false);
    }

    /** 已认证构造器（带 authorities, 支持多个因子） */
    public MultiFactorAuthenticationToken(String username,
                                          Object credentials,
                                          AuthenticationProviderType provider,
                                          Set<AuthenticationFactorType> completedFactors,
                                          Collection<? extends GrantedAuthority> authorities) {
        super(authorities == null ? Collections.emptyList() : List.copyOf(authorities));
        this.username = Objects.requireNonNull(username, "username");
        this.credentials = credentials;
        this.provider = provider == null ? AuthenticationProviderType.UNKNOWN : provider;
        this.completedFactors = completedFactors == null || completedFactors.isEmpty()
                ? EnumSet.noneOf(AuthenticationFactorType.class)
                : EnumSet.copyOf(completedFactors);
        setAuthenticated(true);
    }

    /** 已认证构造器（单因子） */
    public MultiFactorAuthenticationToken(String username,
                                          Object credentials,
                                          AuthenticationProviderType provider,
                                          AuthenticationFactorType initialFactor,
                                          Collection<? extends GrantedAuthority> authorities) {
        super(authorities == null ? Collections.emptyList() : List.copyOf(authorities));
        this.username = Objects.requireNonNull(username, "username");
        this.credentials = credentials;
        this.provider = provider == null ? AuthenticationProviderType.UNKNOWN : provider;
        this.completedFactors = EnumSet.noneOf(AuthenticationFactorType.class);
        if (initialFactor != null && initialFactor != AuthenticationFactorType.UNKNOWN) {
            this.completedFactors.add(initialFactor);
        }
        setAuthenticated(true);
    }

    /**
     * JSON 反序列化友好构造（仅用于需要把 token 存/取到 DB 的专用 mapper）
     * 注意：默认 webObjectMapper 不应序列化/反序列化 token。如果需要，请只在专用 mapper 上启用。
     */
    @JsonCreator
    public static MultiFactorAuthenticationToken createForJackson(
            @JsonProperty("username") String username,
            @JsonProperty("credentials") Object credentials,
            @JsonProperty("provider") String providerStr,
            @JsonProperty("completedFactors") Set<String> completedFactors,
            @JsonProperty("authorities") Collection<? extends GrantedAuthority> authorities
    ) {
        Set<AuthenticationFactorType> factorSet = null;
        if (completedFactors != null) {
            factorSet = completedFactors.stream()
                    .filter(Objects::nonNull)
                    .map(AuthenticationFactorType::from)
                    .collect(java.util.stream.Collectors.toSet());
        }
        return new MultiFactorAuthenticationToken(username, credentials,
                AuthenticationProviderType.from(providerStr),
                factorSet,
                authorities);
    }


    // --- getters / override ---

    @Override
    public Object getPrincipal() {
        return username;
    }

    /**
     * credentials 对外接口仍然存在，但被 @JsonIgnore 标记，不会被默认 Jackson 输出
     */
    @Override
    public Object getCredentials() {
        return credentials;
    }

    /**
     * 获取用户名
     */
    public String getUsername() {
        return username;
    }

    /**
     * 获取字符串形式的 provider（向后兼容）
     */
    public String getAuthenticationProvider() {
        return provider == null ? null : provider.name();
    }

    /**
     * 获取枚举形式的 provider（类型安全）
     */
    public AuthenticationProviderType getProvider() {
        return provider;
    }

    /**
     * 获取字符串形式的认证类型（向后兼容）
     */
    public String getAuthenticationType() {
        // 返回第一个已完成的因子，如果没有则返回 UNKNOWN
        synchronized (completedFactors) {
            if (completedFactors.isEmpty()) {
                return AuthenticationFactorType.UNKNOWN.name();
            }
            // 优先返回 PASSWORD，然后是 TOTP，最后是其他
            if (completedFactors.contains(AuthenticationFactorType.PASSWORD)) {
                return AuthenticationFactorType.PASSWORD.name();
            }
            if (completedFactors.contains(AuthenticationFactorType.TOTP)) {
                return AuthenticationFactorType.TOTP.name();
            }
            return completedFactors.iterator().next().name();
        }
    }

    /**
     * 返回已完成因子的不可变视图（线程安全）
     */
    public Set<AuthenticationFactorType> getCompletedFactors() {
        synchronized (completedFactors) {
            return Collections.unmodifiableSet(EnumSet.copyOf(completedFactors));
        }
    }

    public boolean hasCompletedFactor(AuthenticationFactorType factor) {
        if (factor == null || factor == AuthenticationFactorType.UNKNOWN) return false;
        synchronized (completedFactors) {
            return completedFactors.contains(factor);
        }
    }

    public boolean hasCompletedFactor(String factorStr) {
        return hasCompletedFactor(AuthenticationFactorType.from(factorStr));
    }

    public boolean hasCompletedAllFactors(Set<AuthenticationFactorType> required) {
        if (required == null || required.isEmpty()) return true;
        synchronized (completedFactors) {
            return completedFactors.containsAll(required);
        }
    }

    public MultiFactorAuthenticationToken addCompletedFactor(AuthenticationFactorType factor) {
        if (factor == null || factor == AuthenticationFactorType.UNKNOWN) return this;
        synchronized (completedFactors) {
            completedFactors.add(factor);
        }
        return this;
    }

    public MultiFactorAuthenticationToken addCompletedFactor(String factorStr) {
        return addCompletedFactor(AuthenticationFactorType.from(factorStr));
    }

    public MultiFactorAuthenticationToken promoteToFullyAuthenticated(Collection<? extends GrantedAuthority> authorities) {
        EnumSet<AuthenticationFactorType> newCompletedFactors = EnumSet.copyOf(this.completedFactors);
        if (!newCompletedFactors.contains(AuthenticationFactorType.TOTP)) {
            newCompletedFactors.add(AuthenticationFactorType.TOTP);
        }
        MultiFactorAuthenticationToken newToken = new MultiFactorAuthenticationToken(
                this.username,
                null,
                this.provider,
                newCompletedFactors,
                authorities == null ? Collections.emptyList() : List.copyOf(authorities)
        );
        if (this.getDetails() != null) {
            newToken.setDetails(this.getDetails());
        }
        return newToken;
    }

    @Override
    public void eraseCredentials() {
        super.eraseCredentials();
        this.credentials = null;
    }

    @Override
    public String toString() {
        synchronized (completedFactors) {
            return "MultiFactorAuthenticationToken{" +
                    "username='" + username + '\'' +
                    ", provider=" + provider +
                    ", completedFactors=" + completedFactors +
                    ", authenticated=" + isAuthenticated() +
                    ", authorities=" + getAuthorities() +
                    '}';
        }
    }

    @Override
    public int hashCode() {
        return Objects.hash(username, provider, EnumSet.copyOf(completedFactors), getAuthorities());
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof MultiFactorAuthenticationToken other)) return false;
        return Objects.equals(username, other.username)
                && Objects.equals(provider, other.provider)
                && Objects.equals(EnumSet.copyOf(completedFactors), EnumSet.copyOf(other.completedFactors))
                && Objects.equals(getAuthorities(), other.getAuthorities());
    }
}
