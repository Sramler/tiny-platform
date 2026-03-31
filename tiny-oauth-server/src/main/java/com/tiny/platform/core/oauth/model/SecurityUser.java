package com.tiny.platform.core.oauth.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.tiny.platform.core.oauth.config.jackson.SecurityUserLongDeserializer;
import com.tiny.platform.core.oauth.config.jackson.SecurityUserLongSerializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.tiny.platform.core.oauth.tenant.ActiveTenantResponseSupport;
import com.tiny.platform.core.oauth.tenant.TenantContext;
import com.tiny.platform.infrastructure.auth.role.domain.Role;
import com.tiny.platform.infrastructure.auth.user.domain.User;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Stream;

/**
 * SecurityUser 是 Spring Security 的认证用户对象，仅用于安全框架内部，
 * 避免将包含 ORM 懒加载字段的实体类（如 User）直接存入 Session。
 */
@JsonTypeName("securityUser")
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "@type")
public class SecurityUser implements UserDetails {

    private final Long userId;

    private final Long activeTenantId;

    // 用户名
    private final String username;

    // 加密后的密码
    private final String password;

    // 权限列表（通常来源于角色）
    private final Collection<? extends GrantedAuthority> authorities;

    // 当前权限版本指纹，用于识别会话/Token 权限是否漂移
    private final String permissionsVersion;

    // 账号是否未过期
    private final boolean accountNonExpired;

    // 账号是否未锁定
    private final boolean accountNonLocked;

    // 凭证（密码）是否未过期
    private final boolean credentialsNonExpired;

    // 是否启用
    private final boolean enabled;

    /**
     * 构造函数，基于数据库中查询出的 User 实体构建出安全框架使用的对象。
     * 这样可以避免将 User 实体（含懒加载字段）直接放入 Session。
     * 注意：User 表的 password 字段已废弃，实际密码在 user_authentication_method 表中。
     */
    public SecurityUser(User user) {
        this(user, "");
    }

    /**
     * 构造函数，基于数据库中查询出的 User 实体和自定义密码构建出安全框架使用的对象。
     * 用于从 user_authentication_method 表获取密码的场景。
     */
    public SecurityUser(User user, String password) {
        this(user, password, resolveActiveTenantId(user), Set.of());
    }

    public SecurityUser(User user, Long activeTenantId, Set<Role> roles) {
        this(user, "", activeTenantId, roles);
    }

    public SecurityUser(User user, String password, Long activeTenantId, Set<Role> roles) {
        this(user, password, activeTenantId, roles, null);
    }

    public SecurityUser(User user, String password, Long activeTenantId, Set<Role> roles, String permissionsVersion) {
        this(user, password, activeTenantId, buildAuthorities(roles), permissionsVersion);
    }

    public SecurityUser(User user,
                        String password,
                        Long activeTenantId,
                        Collection<? extends GrantedAuthority> authorities,
                        String permissionsVersion) {
        this.userId = user.getId();
        this.activeTenantId = activeTenantId;
        this.username = user.getUsername();
        this.password = password != null ? password : "";
        this.authorities = authorities != null ? authorities : java.util.List.of();
        this.permissionsVersion = permissionsVersion;
        this.accountNonExpired = user.isAccountNonExpired();
        this.accountNonLocked = user.isAccountNonLocked();
        this.credentialsNonExpired = user.isCredentialsNonExpired();
        this.enabled = user.isEnabled();
    }

    private static Long resolveActiveTenantId(User user) {
        Long activeTenantId = TenantContext.getActiveTenantId();
        if (activeTenantId != null && activeTenantId > 0) {
            return activeTenantId;
        }
        return ActiveTenantResponseSupport.resolveActiveTenantId(
                SecurityContextHolder.getContext().getAuthentication()
        );
    }

    public SecurityUser(
            Long userId,
            Long activeTenantId,
            String username,
            String password,
            Collection<? extends GrantedAuthority> authorities,
            boolean accountNonExpired,
            boolean accountNonLocked,
            boolean credentialsNonExpired,
            boolean enabled) {
        this(
                userId,
                activeTenantId,
                username,
                password,
                authorities,
                accountNonExpired,
                accountNonLocked,
                credentialsNonExpired,
                enabled,
                null
        );
    }

    @JsonCreator
    public SecurityUser(
            @JsonProperty("userId") Long userId,
            @JsonProperty("activeTenantId") Long activeTenantId,
            @JsonProperty("username") String username,
            @JsonProperty("password") String password,
            @JsonProperty("authorities") Collection<? extends GrantedAuthority> authorities,
            @JsonProperty("accountNonExpired") boolean accountNonExpired,
            @JsonProperty("accountNonLocked") boolean accountNonLocked,
            @JsonProperty("credentialsNonExpired") boolean credentialsNonExpired,
            @JsonProperty("enabled") boolean enabled,
            @JsonProperty("permissionsVersion") String permissionsVersion) {
        this.userId = userId;
        this.activeTenantId = activeTenantId;
        this.username = username;
        this.password = password;
        this.authorities = authorities;
        this.permissionsVersion = permissionsVersion;
        this.accountNonExpired = accountNonExpired;
        this.accountNonLocked = accountNonLocked;
        this.credentialsNonExpired = credentialsNonExpired;
        this.enabled = enabled;
    }

    /**
     * 构建 authority 列表：仅使用 role.code，不使用 role.name。
     * 历史 role.resources 授权聚合已迁移到 SecurityUserAuthorityService（role_permission 主链路）。
     */
    private static Collection<? extends GrantedAuthority> buildAuthorities(Set<Role> roles) {
        Set<Role> effectiveRoles = roles != null ? roles : Set.of();
        return effectiveRoles.stream()
                .flatMap(role -> authorityStream(role.getCode()))
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    public static Collection<? extends GrantedAuthority> buildAuthoritiesFromRoles(Set<Role> roles) {
        return buildAuthorities(roles);
    }

    private static Stream<org.springframework.security.core.authority.SimpleGrantedAuthority> authorityStream(String... candidates) {
        Set<String> normalizedValues = new LinkedHashSet<>();
        for (String value : candidates) {
            addAuthorityValue(normalizedValues, value);
        }
        return normalizedValues.stream()
                .map(SimpleGrantedAuthority::new);
    }

    private static void addAuthorityValue(Set<String> values, String value) {
        if (value == null) {
            return;
        }
        String normalized = value.trim();
        if (!normalized.isEmpty()) {
            values.add(normalized);
        }
    }

    /**
     * 获取当前用户的权限集合（角色转换而来）
     * 此方法是认证和授权的关键点
     */
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return this.authorities; // 此处原来写成 List.of() 是错误的，需返回真实权限
    }

    /**
     * 获取用户ID。
     * <p>
     * <b>序列化优化</b>：
     * - 使用自定义序列化器将 Long 序列化为 String，避免 Spring Security allowlist 检查失败
     * - 使用自定义反序列化器支持从 String 或 Number 反序列化，兼容新旧数据格式
     * <p>
     * 这是符合官方指南的扩展方式，通过 Jackson 注解而不是修改框架内部实现。
     *
     * @return 用户ID
     */
    @JsonSerialize(using = SecurityUserLongSerializer.class)
    @JsonDeserialize(using = SecurityUserLongDeserializer.class)
    public Long getUserId() {
        return userId;
    }

    @JsonProperty("activeTenantId")
    @JsonSerialize(using = SecurityUserLongSerializer.class)
    @JsonDeserialize(using = SecurityUserLongDeserializer.class)
    public Long getActiveTenantId() {
        return activeTenantId;
    }

    public String getPermissionsVersion() {
        return permissionsVersion;
    }

    /**
     * 返回用户密码（已加密）
     */
    @Override
    public String getPassword() {
        return this.password;
    }

    /**
     * 返回用户名
     */
    @Override
    public String getUsername() {
        return this.username;
    }

    /**
     * 账号是否未过期（true 表示有效）
     */
    @Override
    public boolean isAccountNonExpired() {
        return this.accountNonExpired;
    }

    /**
     * 账号是否未锁定（true 表示未锁定）
     */
    @Override
    public boolean isAccountNonLocked() {
        return this.accountNonLocked;
    }

    /**
     * 密码是否未过期（true 表示有效）
     */
    @Override
    public boolean isCredentialsNonExpired() {
        return this.credentialsNonExpired;
    }

    /**
     * 用户是否启用（true 表示启用）
     */
    @Override
    public boolean isEnabled() {
        return this.enabled;
    }
}
