package com.tiny.platform.infrastructure.tenant.service;

import com.tiny.platform.infrastructure.tenant.domain.Tenant;

public interface TenantBootstrapService {
    void bootstrapFromDefaultTenant(Tenant targetTenant);
}
