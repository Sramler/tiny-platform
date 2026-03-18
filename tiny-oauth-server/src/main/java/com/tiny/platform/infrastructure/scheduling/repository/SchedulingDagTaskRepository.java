package com.tiny.platform.infrastructure.scheduling.repository;

import com.tiny.platform.infrastructure.scheduling.model.SchedulingDagTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SchedulingDagTaskRepository extends JpaRepository<SchedulingDagTask, Long>, JpaSpecificationExecutor<SchedulingDagTask> {
    Optional<SchedulingDagTask> findByIdAndTenantId(Long id, Long tenantId);

    List<SchedulingDagTask> findByDagVersionId(Long dagVersionId);
    
    Optional<SchedulingDagTask> findByDagVersionIdAndNodeCode(Long dagVersionId, String nodeCode);

    /** 租户维度：按版本 + 租户查节点列表，无请求上下文时使用（如 Worker） */
    List<SchedulingDagTask> findByDagVersionIdAndTenantId(Long dagVersionId, Long tenantId);

    /** 租户维度：按版本 + 节点编码 + 租户查节点，无请求上下文时使用 */
    Optional<SchedulingDagTask> findByDagVersionIdAndNodeCodeAndTenantId(Long dagVersionId, String nodeCode, Long tenantId);
    
    List<SchedulingDagTask> findByTaskId(Long taskId);
    
    @Modifying
    @Query("DELETE FROM SchedulingDagTask dt WHERE dt.dagVersionId = :dagVersionId")
    void deleteByDagVersionId(@Param("dagVersionId") Long dagVersionId);
}
