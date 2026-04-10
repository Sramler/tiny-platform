package com.tiny.web.sys.security;

import com.tiny.web.sys.ResourceService;
import com.tiny.web.sys.model.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.function.Supplier;

@Component
public class ResourceAuthorizationManager implements AuthorizationManager<RequestAuthorizationContext> {

    @Autowired
    private ResourceService resourceService; // 你可以从数据库加载权限信息

    @Override
    public AuthorizationDecision check(Supplier<Authentication> authentication, RequestAuthorizationContext context) {
        String path = context.getRequest().getRequestURI();
        String method = context.getRequest().getMethod();
        Authentication auth = authentication.get();
        if (auth == null) {
            return new AuthorizationDecision(false);
        }

        Collection<String> roleCodes = resolveRoleCodes(auth);
        boolean hasPermission = roleCodes.stream()
            .anyMatch(role -> resourceService.hasAccess(role, path, method)); // 自定义匹配逻辑

        return new AuthorizationDecision(hasPermission);
    }

    private Collection<String> resolveRoleCodes(Authentication auth) {
        if (auth == null) {
            return java.util.Set.of();
        }
        LinkedHashSet<String> roleCodes = new LinkedHashSet<>();
        if (auth != null && auth.getPrincipal() instanceof User user) {
            roleCodes.addAll(user.getRoleCodes());
        }
        if (!roleCodes.isEmpty()) {
            return roleCodes;
        }
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(code -> code != null && !code.isBlank())
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }
}