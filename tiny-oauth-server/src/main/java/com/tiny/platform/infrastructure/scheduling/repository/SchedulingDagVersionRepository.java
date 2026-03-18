package com.tiny.platform.infrastructure.scheduling.repository;

import com.tiny.platform.infrastructure.scheduling.model.SchedulingDagVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SchedulingDagVersionRepository extends JpaRepository<SchedulingDagVersion, Long> {
    Optional<SchedulingDagVersion> findByIdAndTenantId(Long id, Long tenantId);

    List<SchedulingDagVersion> findByDagId(Long dagId);
    
    Optional<SchedulingDagVersion> findByDagIdAndVersionNo(Long dagId, Integer versionNo);
    
    Optional<SchedulingDagVersion> findByDagIdAndStatus(Long dagId, String status);

    List<SchedulingDagVersion> findByDagIdInAndStatus(List<Long> dagIds, String status);

    /** 租户维度：列表页批量查 ACTIVE 版本（当前仅用于同租户列表 enrich） */
    List<SchedulingDagVersion> findByDagIdInAndStatusAndTenantId(List<Long> dagIds, String status, Long tenantId);

    /** 租户维度：按 DAG + 租户查版本，无请求上下文时使用（如 Worker/恢复 Runner） */
    List<SchedulingDagVersion> findByDagIdAndTenantId(Long dagId, Long tenantId);

    /** 租户维度：按 DAG + 状态 + 租户查 ACTIVE 版本 */
    Optional<SchedulingDagVersion> findByDagIdAndStatusAndTenantId(Long dagId, String status, Long tenantId);
    
    @Query("SELECT MAX(dv.versionNo) FROM SchedulingDagVersion dv WHERE dv.dagId = :dagId")
    Integer findMaxVersionNoByDagId(@Param("dagId") Long dagId);
}
