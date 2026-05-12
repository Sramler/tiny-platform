package com.tiny.platform.infrastructure.menu.runtime;

import com.tiny.platform.core.oauth.tenant.TenantContext;
import com.tiny.platform.core.oauth.tenant.TenantContextContract;
import com.tiny.platform.infrastructure.runtime.version.RuntimeVersionKey;
import com.tiny.platform.infrastructure.runtime.version.RuntimeVersionSnapshot;
import com.tiny.platform.infrastructure.runtime.version.RuntimeVersionStore;
import org.springframework.stereotype.Service;

@Service
public class MenuConfigVersionInvalidator {

    private final RuntimeVersionStore runtimeVersionStore;

    public MenuConfigVersionInvalidator(RuntimeVersionStore runtimeVersionStore) {
        this.runtimeVersionStore = runtimeVersionStore;
    }

    public RuntimeVersionSnapshot bumpCurrentMenuConfigVersion(String reason, Long actorUserId) {
        Long tenantId = TenantContext.isPlatformScope() ? null : TenantContext.getActiveTenantId();
        String scopeType = TenantContext.isPlatformScope()
            ? TenantContextContract.SCOPE_TYPE_PLATFORM
            : TenantContextContract.SCOPE_TYPE_TENANT;
        return bumpMenuConfigVersion(MenuConfigVersionKeys.fromActiveScope(tenantId, scopeType), reason, actorUserId);
    }

    public RuntimeVersionSnapshot bumpMenuConfigVersion(RuntimeVersionKey key, String reason, Long actorUserId) {
        return runtimeVersionStore.bump(key, reason, actorUserId);
    }
}
