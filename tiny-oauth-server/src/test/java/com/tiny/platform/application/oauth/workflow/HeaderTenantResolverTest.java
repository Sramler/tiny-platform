package com.tiny.platform.application.oauth.workflow;

import com.tiny.platform.core.oauth.tenant.TenantContextContract;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class HeaderTenantResolverTest {

    private final HeaderTenantResolver resolver = new HeaderTenantResolver();

    @AfterEach
    void tearDown() {
        com.tiny.platform.core.oauth.tenant.TenantContext.clear();
    }

    @Test
    void shouldResolveActiveTenantHeaderForTenantRuntime() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/process/definitions");
        request.addHeader(TenantContextContract.ACTIVE_TENANT_ID_HEADER, " 5 ");

        List<String> tenantIds = resolver.resolveTenantIds(request, null);

        assertThat(tenantIds).containsExactly("5");
    }

    @Test
    void shouldReturnEmptyTenantListInPlatformScope() {
        com.tiny.platform.core.oauth.tenant.TenantContext.setActiveScopeType(
            TenantContextContract.SCOPE_TYPE_PLATFORM
        );
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/process/definitions");

        List<String> tenantIds = resolver.resolveTenantIds(request, null);

        assertThat(tenantIds).isEmpty();
    }

    @Test
    void shouldReturnEmptyTenantListWhenNoActiveTenantHeaderExists() {
        com.tiny.platform.core.oauth.tenant.TenantContext.setActiveScopeType(
            TenantContextContract.SCOPE_TYPE_TENANT
        );
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/process/definitions");

        List<String> tenantIds = resolver.resolveTenantIds(request, null);

        assertThat(tenantIds).isEmpty();
    }
}
