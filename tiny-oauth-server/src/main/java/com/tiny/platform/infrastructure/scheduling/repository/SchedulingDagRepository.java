package com.tiny.platform.infrastructure.scheduling.repository;

import com.tiny.platform.infrastructure.scheduling.model.SchedulingDag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

@Repository
public interface SchedulingDagRepository extends JpaRepository<SchedulingDag, Long>, JpaSpecificationExecutor<SchedulingDag> {
    Optional<SchedulingDag> findByIdAndTenantId(Long id, Long tenantId);

    Optional<SchedulingDag> findByTenantIdAndCode(Long tenantId, String code);

    /** 查询已启用且配置了 Cron 的 DAG，用于启动时恢复 Quartz Job */
    @Query("SELECT d FROM SchedulingDag d WHERE d.enabled = true AND d.cronExpression IS NOT NULL AND d.cronExpression <> '' AND (d.cronEnabled IS NULL OR d.cronEnabled = true)")
    List<SchedulingDag> findAllEnabledWithCron();
}

