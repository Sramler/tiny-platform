package com.tiny.platform.infrastructure.scheduling.repository;

import com.tiny.platform.infrastructure.scheduling.model.SchedulingDag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SchedulingDagRepository extends JpaRepository<SchedulingDag, Long>, JpaSpecificationExecutor<SchedulingDag> {
    Optional<SchedulingDag> findByTenantIdAndCode(Long tenantId, String code);
}


