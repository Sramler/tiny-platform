package com.tiny.platform.application.oauth.workflow;

import com.tiny.platform.core.oauth.tenant.TenantContextContract;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.List;

@Component
public class HeaderTenantResolver implements TenantResolver {

    @Override
    public List<String> resolveTenantIds(HttpServletRequest request, Authentication authentication) {
        String activeTenantId = normalizeHeader(request.getHeader(TenantContextContract.ACTIVE_TENANT_ID_HEADER));
        if (activeTenantId != null) {
            return Collections.singletonList(activeTenantId);
        }

        return Collections.emptyList();
    }

    private String normalizeHeader(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

}
