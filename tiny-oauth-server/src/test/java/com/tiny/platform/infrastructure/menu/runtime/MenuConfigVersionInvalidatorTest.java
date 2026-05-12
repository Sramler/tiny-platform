package com.tiny.platform.infrastructure.menu.runtime;

import com.tiny.platform.core.oauth.tenant.TenantContext;
import com.tiny.platform.core.oauth.tenant.TenantContextContract;
import com.tiny.platform.infrastructure.runtime.version.RuntimeVersionKey;
import com.tiny.platform.infrastructure.runtime.version.RuntimeVersionStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class MenuConfigVersionInvalidatorTest {

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void shouldBumpTenantMenuConfigVersionForCurrentTenantScope() {
        RuntimeVersionStore runtimeVersionStore = mock(RuntimeVersionStore.class);
        MenuConfigVersionInvalidator invalidator = new MenuConfigVersionInvalidator(runtimeVersionStore);
        TenantContext.setActiveTenantId(2L);
        TenantContext.setActiveScopeType(TenantContextContract.SCOPE_TYPE_TENANT);

        invalidator.bumpCurrentMenuConfigVersion("menu_update", 7L);

        verify(runtimeVersionStore).bump(
            RuntimeVersionKey.of("MENU_CONFIG", 2L, "TENANT", 2L),
            "menu_update",
            7L
        );
    }

    @Test
    void shouldBumpPlatformMenuConfigVersionForCurrentPlatformScope() {
        RuntimeVersionStore runtimeVersionStore = mock(RuntimeVersionStore.class);
        MenuConfigVersionInvalidator invalidator = new MenuConfigVersionInvalidator(runtimeVersionStore);
        TenantContext.setActiveTenantId(null);
        TenantContext.setActiveScopeType(TenantContextContract.SCOPE_TYPE_PLATFORM);

        invalidator.bumpCurrentMenuConfigVersion("menu_update", 7L);

        verify(runtimeVersionStore).bump(
            RuntimeVersionKey.of("MENU_CONFIG", null, "PLATFORM", null),
            "menu_update",
            7L
        );
    }
}
