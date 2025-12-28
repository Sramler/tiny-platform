package com.tiny.platform.application.oauth.workflow;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.List;

@Component
public class HeaderTenantResolver implements TenantResolver {

    @Override
    public List<String> resolveTenantIds(HttpServletRequest request, Authentication authentication) {
        String tenantId = request.getHeader("X-Tenant-Id");
        if (tenantId == null || tenantId.isBlank()) {
            return Collections.emptyList();
        }
        return Collections.singletonList(tenantId.trim());
    }
}


