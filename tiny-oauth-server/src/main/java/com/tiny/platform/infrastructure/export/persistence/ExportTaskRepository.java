package com.tiny.platform.infrastructure.export.persistence;

import com.tiny.platform.infrastructure.export.service.ExportTaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ExportTaskRepository extends JpaRepository<ExportTaskEntity, Long>, JpaSpecificationExecutor<ExportTaskEntity> {

    Optional<ExportTaskEntity> findByTaskId(String taskId);

    long deleteByTaskId(String taskId);

    List<ExportTaskEntity> findByUserIdOrderByCreatedAtDesc(String userId);

    List<ExportTaskEntity> findByStatusOrderByCreatedAtAsc(ExportTaskStatus status);

    List<ExportTaskEntity> findByExpireAtBefore(LocalDateTime deadline);

    @Query("""
        select coalesce(sum(t.fileSizeBytes), 0) from ExportTaskEntity t
        where t.tenantId = :tenantId
          and t.status = :status
        """)
    Long sumFileSizeBytesByTenantIdAndStatus(@Param("tenantId") Long tenantId,
                                             @Param("status") ExportTaskStatus status);

    @Query("select t from ExportTaskEntity t where t.status = :status and (t.lastHeartbeat is null or t.lastHeartbeat < :threshold)")
    List<ExportTaskEntity> findStuckTasks(@Param("status") ExportTaskStatus status,
                                          @Param("threshold") LocalDateTime threshold);
}
