package com.tiny.platform.core.oauth.config;

import jakarta.annotation.Nonnull;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 从 JWT 提取 GrantedAuthority，与 JwtTokenCustomizer 写入的 claims 一致。
 *
 * <p>顺序：1）scope 转为 SCOPE_*；2）authorities 数组逐项；3）若 authorities 为空则用 permissions。
 * 使 Bearer 请求时方法级鉴权（@PreAuthorize / AccessGuard）能正确识别规范权限码。</p>
 */
public class TinyPlatformJwtGrantedAuthoritiesConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    private static final String AUTHORITIES_CLAIM = "authorities";
    private static final String PERMISSIONS_CLAIM = "permissions";

    private final JwtGrantedAuthoritiesConverter scopeConverter = new JwtGrantedAuthoritiesConverter();

    @Override
    public Collection<GrantedAuthority> convert(@Nonnull Jwt jwt) {
        Set<GrantedAuthority> scopeAuthorities = new LinkedHashSet<>(scopeConverter.convert(jwt));
        Set<GrantedAuthority> fromAuthorities = parseStringListClaim(jwt.getClaim(AUTHORITIES_CLAIM));
        Set<GrantedAuthority> fromPermissions = fromAuthorities.isEmpty()
            ? parseStringListClaim(jwt.getClaim(PERMISSIONS_CLAIM))
            : Set.of();

        return Stream.of(scopeAuthorities, fromAuthorities, fromPermissions)
            .flatMap(Collection::stream)
            .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static Set<GrantedAuthority> parseStringListClaim(Object claim) {
        if (claim == null) {
            return Set.of();
        }
        if (claim instanceof Collection<?> coll) {
            Set<GrantedAuthority> out = new LinkedHashSet<>();
            for (Object item : coll) {
                String s = asString(item);
                if (s != null && !s.isBlank()) {
                    out.add(new SimpleGrantedAuthority(s.trim()));
                }
            }
            return out;
        }
        String s = asString(claim);
        if (s == null || s.isBlank()) {
            return Set.of();
        }
        return Collections.singleton(new SimpleGrantedAuthority(s.trim()));
    }

    private static String asString(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String str) {
            return str;
        }
        return value.toString();
    }
}
