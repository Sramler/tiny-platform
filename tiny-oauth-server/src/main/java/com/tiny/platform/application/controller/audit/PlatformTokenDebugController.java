package com.tiny.platform.application.controller.audit;

import com.tiny.platform.core.oauth.config.TinyPlatformJwtGrantedAuthoritiesConverter;
import com.tiny.platform.core.oauth.tenant.TenantContext;
import com.tiny.platform.infrastructure.core.exception.code.ErrorCode;
import com.tiny.platform.infrastructure.core.exception.exception.BusinessException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 平台 Token 只读排障控制器。
 *
 * <p>仅做 decode/展示，不做签发、改写、刷新。</p>
 */
@RestController
@RequestMapping("/sys/platform/token-debug")
public class PlatformTokenDebugController {

    private final JwtDecoder jwtDecoder;
    private final TinyPlatformJwtGrantedAuthoritiesConverter authoritiesConverter =
        new TinyPlatformJwtGrantedAuthoritiesConverter();

    public PlatformTokenDebugController(JwtDecoder jwtDecoder) {
        this.jwtDecoder = jwtDecoder;
    }

    @PostMapping("/decode")
    @PreAuthorize("@authorizationAuditAccessGuard.canView(authentication) || @authenticationAuditAccessGuard.canView(authentication)")
    public ResponseEntity<TokenDecodeResponse> decode(@RequestBody TokenDecodeRequest request) {
        if (!TenantContext.isPlatformScope()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "仅平台作用域允许使用 token decode 工具");
        }
        String token = request == null ? null : normalize(request.token());
        if (token == null) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "token 不能为空");
        }
        try {
            Jwt jwt = jwtDecoder.decode(token);
            if (jwt == null) {
                throw new BusinessException(ErrorCode.INVALID_PARAMETER, "token 解析失败: 空 payload");
            }
            List<String> authorities = toAuthorityList(authoritiesConverter.convert(jwt));
            List<String> permissions = toStringList(jwt.getClaim("permissions"));
            List<String> roleCodes = toStringList(jwt.getClaim("roleCodes"));
            String permissionsVersion = normalize(asString(jwt.getClaim("permissionsVersion")));
            String activeScopeType = normalize(asString(jwt.getClaim("activeScopeType")));
            Long activeTenantId = toLong(jwt.getClaim("activeTenantId"));

            Map<String, Object> claims = new LinkedHashMap<>(jwt.getClaims());
            return ResponseEntity.ok(new TokenDecodeResponse(
                authorities,
                permissions,
                roleCodes,
                permissionsVersion,
                activeScopeType,
                activeTenantId,
                claims
            ));
        } catch (JwtException ex) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "token 解析失败: " + ex.getMessage());
        }
    }

    private List<String> toAuthorityList(Collection<GrantedAuthority> grantedAuthorities) {
        if (grantedAuthorities == null || grantedAuthorities.isEmpty()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (GrantedAuthority authority : grantedAuthorities) {
            if (authority == null || authority.getAuthority() == null) {
                continue;
            }
            String normalized = normalize(authority.getAuthority());
            if (normalized != null && !values.contains(normalized)) {
                values.add(normalized);
            }
        }
        return values;
    }

    private List<String> toStringList(Object value) {
        if (value instanceof Collection<?> collection) {
            List<String> result = new ArrayList<>();
            for (Object item : collection) {
                String normalized = normalize(asString(item));
                if (normalized != null) {
                    result.add(normalized);
                }
            }
            return result;
        }
        String normalized = normalize(asString(value));
        return normalized == null ? List.of() : List.of(normalized);
    }

    private Long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text) {
            String normalized = normalize(text);
            if (normalized == null) {
                return null;
            }
            try {
                return Long.valueOf(normalized);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private String asString(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String text) {
            return text;
        }
        return value.toString();
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public record TokenDecodeRequest(String token) {
    }

    public record TokenDecodeResponse(
        List<String> authorities,
        List<String> permissions,
        List<String> roleCodes,
        String permissionsVersion,
        String activeScopeType,
        Long activeTenantId,
        Map<String, Object> claims
    ) {
    }
}

