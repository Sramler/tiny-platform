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
}
