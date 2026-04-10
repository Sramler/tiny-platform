package com.tiny.platform.application.oauth.workflow;

import com.tiny.platform.core.oauth.model.SecurityUser;
import org.camunda.bpm.engine.IdentityService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.stream.Collectors;

@Component
@ConditionalOnBean(IdentityService.class)
public class CamundaIdentityBridgeFilter extends OncePerRequestFilter {

    private final IdentityService identityService;
    private final TenantResolver tenantResolver;

    public CamundaIdentityBridgeFilter(IdentityService identityService, TenantResolver tenantResolver) {
        this.identityService = identityService;
        this.tenantResolver = tenantResolver;
    }

    @Override
    protected void doFilterInternal(@org.springframework.lang.NonNull HttpServletRequest request,
                                    @org.springframework.lang.NonNull HttpServletResponse response,
                                    @org.springframework.lang.NonNull FilterChain filterChain)
            throws ServletException, IOException {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated()) {
                String userId = auth.getName();
                List<String> groups = resolveRoleCodes(auth).stream()
                        .map(a -> a.replaceFirst("^ROLE_", ""))
                        .collect(Collectors.toList());
                List<String> tenants = tenantResolver.resolveTenantIds(request, auth);
                identityService.setAuthentication(userId, groups, tenants);
            }
            filterChain.doFilter(request, response);
        } finally {
            identityService.clearAuthentication();
        }
    }

    private Collection<String> resolveRoleCodes(Authentication auth) {
        LinkedHashSet<String> roleCodes = new LinkedHashSet<>();
        addRoleCodesFromCarrier(roleCodes, auth.getPrincipal());
        if (roleCodes.isEmpty()) {
            // MFA session token keeps SecurityUser in details; principal is username string.
            addRoleCodesFromCarrier(roleCodes, auth.getDetails());
        }
        if (!roleCodes.isEmpty()) {
            return roleCodes;
        }
        return List.of();
    }

    private void addRoleCodesFromCarrier(LinkedHashSet<String> roleCodes, Object carrier) {
        if (carrier instanceof SecurityUser securityUser && securityUser.getRoleCodes() != null) {
            roleCodes.addAll(securityUser.getRoleCodes());
            return;
        }
        if (carrier instanceof Jwt jwt) {
            Object claim = jwt.getClaim("roleCodes");
            if (claim instanceof Collection<?> collection) {
                collection.stream()
                        .map(String::valueOf)
                        .filter(code -> code != null && !code.isBlank())
                        .forEach(roleCodes::add);
            }
        }
    }

}

