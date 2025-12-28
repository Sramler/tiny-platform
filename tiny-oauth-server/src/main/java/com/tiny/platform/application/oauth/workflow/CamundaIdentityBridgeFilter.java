package com.tiny.platform.application.oauth.workflow;

import org.camunda.bpm.engine.IdentityService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

@Component
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
                List<String> groups = auth.getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority)
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
}


