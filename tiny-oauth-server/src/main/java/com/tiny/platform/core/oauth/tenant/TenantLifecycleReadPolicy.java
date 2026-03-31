package com.tiny.platform.core.oauth.tenant;

import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import org.springframework.util.AntPathMatcher;

/**
 * 冻结/下线租户在平台作用域下的最小只读白名单。
 *
 * <p>策略采用默认拒绝，仅对显式路径 + 方法组合放行；
 * 具体权限校验仍需命中对应 authority。
 */
public class TenantLifecycleReadPolicy {

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    private final List<Rule> frozenRules = List.of(
        Rule.get("/sys/tenants", "tenant", "system:tenant:list", "system:tenant:view"),
        Rule.get("/sys/tenants/*", "tenant", "system:tenant:list", "system:tenant:view"),
        Rule.get("/sys/audit/authorization", "audit", "system:audit:auth:view"),
        Rule.get("/sys/audit/authorization/export", "audit", "system:audit:auth:export"),
        Rule.get("/sys/audit/authorization/summary", "audit", "system:audit:auth:view"),
        Rule.get("/sys/audit/authorization/by-event-type", "audit", "system:audit:auth:view"),
        Rule.get("/sys/audit/authorization/by-user/*", "audit", "system:audit:auth:view"),
        Rule.get("/sys/audit/authentication", "audit", "system:audit:authentication:view"),
        Rule.get("/sys/audit/authentication/export", "audit", "system:audit:authentication:export"),
        Rule.get("/sys/audit/authentication/summary", "audit", "system:audit:authentication:view")
    );

    private final List<Rule> decommissionedRules = List.of(
        Rule.get("/sys/tenants", "tenant", "system:tenant:list", "system:tenant:view"),
        Rule.get("/sys/tenants/*", "tenant", "system:tenant:list", "system:tenant:view"),
        Rule.get("/sys/audit/authorization", "audit", "system:audit:auth:view"),
        Rule.get("/sys/audit/authorization/export", "audit", "system:audit:auth:export"),
        Rule.get("/sys/audit/authorization/summary", "audit", "system:audit:auth:view"),
        Rule.get("/sys/audit/authorization/by-event-type", "audit", "system:audit:auth:view"),
        Rule.get("/sys/audit/authorization/by-user/*", "audit", "system:audit:auth:view"),
        Rule.get("/sys/audit/authentication", "audit", "system:audit:authentication:view"),
        Rule.get("/sys/audit/authentication/export", "audit", "system:audit:authentication:export"),
        Rule.get("/sys/audit/authentication/summary", "audit", "system:audit:authentication:view")
    );

    public Optional<AllowedReadAccess> resolve(HttpServletRequest request, String lifecycleStatus) {
        if (request == null || lifecycleStatus == null) {
            return Optional.empty();
        }
        String normalizedStatus = lifecycleStatus.trim().toUpperCase(Locale.ROOT);
        List<Rule> rules = switch (normalizedStatus) {
            case "FROZEN" -> frozenRules;
            case "DECOMMISSIONED" -> decommissionedRules;
            default -> List.of();
        };
        String method = request.getMethod();
        String path = request.getRequestURI();
        return rules.stream()
            .filter(rule -> rule.matches(method, path))
            .findFirst()
            .map(rule -> new AllowedReadAccess(rule.module(), rule.requiredAuthorities()));
    }

    public record AllowedReadAccess(String module, Set<String> requiredAuthorities) {
    }

    private record Rule(String method, String pattern, String module, Set<String> requiredAuthorities) {

        private static Rule get(String pattern, String module, String... authorities) {
            return new Rule("GET", pattern, module, new LinkedHashSet<>(List.of(authorities)));
        }

        private boolean matches(String requestMethod, String requestPath) {
            return method.equalsIgnoreCase(requestMethod) && PATH_MATCHER.match(pattern, requestPath);
        }
    }
}
