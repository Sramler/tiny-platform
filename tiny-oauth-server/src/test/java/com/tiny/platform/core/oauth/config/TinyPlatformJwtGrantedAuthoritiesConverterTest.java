package com.tiny.platform.core.oauth.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

class TinyPlatformJwtGrantedAuthoritiesConverterTest {

    private final TinyPlatformJwtGrantedAuthoritiesConverter converter = new TinyPlatformJwtGrantedAuthoritiesConverter();

    @Test
    void mergesAuthoritiesAndPermissionsClaimsWhenBothPresent() {
        Jwt jwt = Jwt.withTokenValue("t")
            .header("alg", "none")
            .claim("authorities", List.of("ROLE_TENANT_ADMIN"))
            .claim("permissions", List.of("scheduling:*", "system:org:list"))
            .subject("u1")
            .build();

        var authorities = converter.convert(jwt).stream()
            .map(GrantedAuthority::getAuthority)
            .collect(Collectors.toSet());

        assertThat(authorities).contains("ROLE_TENANT_ADMIN", "scheduling:*", "system:org:list");
    }

    @Test
    void deduplicatesOverlappingAuthoritiesAndPermissions() {
        Jwt jwt = Jwt.withTokenValue("t")
            .header("alg", "none")
            .claim("authorities", List.of("scheduling:console:config"))
            .claim("permissions", List.of("scheduling:console:config"))
            .subject("u1")
            .build();

        var authorities = converter.convert(jwt);
        long count = authorities.stream().filter(a -> "scheduling:console:config".equals(a.getAuthority())).count();
        assertThat(count).isEqualTo(1);
    }
}
