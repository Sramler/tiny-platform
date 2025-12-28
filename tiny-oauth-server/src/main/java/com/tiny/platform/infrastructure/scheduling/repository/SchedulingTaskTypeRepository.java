package com.tiny.platform.infrastructure.scheduling.repository;

import com.tiny.platform.infrastructure.scheduling.model.SchedulingTaskType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SchedulingTaskTypeRepository extends JpaRepository<SchedulingTaskType, Long>, JpaSpecificationExecutor<SchedulingTaskType> {
    Optional<SchedulingTaskType> findByTenantIdAndCode(Long tenantId, String code);
}


