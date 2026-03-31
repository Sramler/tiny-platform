package com.tiny.platform.infrastructure.tenant.repository;

import com.tiny.platform.infrastructure.tenant.domain.Tenant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface TenantRepository extends JpaRepository<Tenant, Long>, JpaSpecificationExecutor<Tenant> {
    Optional<Tenant> findByCode(String code);
    Optional<Tenant> findByDomain(String domain);
    boolean existsByCode(String code);
    boolean existsByDomain(String domain);

    default Optional<String> findLoginBlockedLifecycleStatus(Long tenantId) {
        if (tenantId == null) {
            return Optional.empty();
        }
        return findById(tenantId)
            .map(Tenant::getLifecycleStatus)
            .filter(status -> "FROZEN".equalsIgnoreCase(status)
                || "DECOMMISSIONED".equalsIgnoreCase(status));
    }

    default boolean isTenantFrozen(Long tenantId) {
        return findLoginBlockedLifecycleStatus(tenantId)
            .filter(status -> "FROZEN".equalsIgnoreCase(status))
            .isPresent();
    }

    default boolean isTenantDecommissioned(Long tenantId) {
        return findLoginBlockedLifecycleStatus(tenantId)
            .filter(status -> "DECOMMISSIONED".equalsIgnoreCase(status))
            .isPresent();
    }
}
