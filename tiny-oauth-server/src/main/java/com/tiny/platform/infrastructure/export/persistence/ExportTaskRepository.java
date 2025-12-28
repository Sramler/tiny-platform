package com.tiny.platform.infrastructure.export.persistence;

import com.tiny.platform.infrastructure.export.service.ExportTaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ExportTaskRepository extends JpaRepository<ExportTaskEntity, Long> {

    Optional<ExportTaskEntity> findByTaskId(String taskId);

    List<ExportTaskEntity> findByUserIdOrderByCreatedAtDesc(String userId);

    List<ExportTaskEntity> findByStatusOrderByCreatedAtAsc(ExportTaskStatus status);

    @Query("select t from ExportTaskEntity t where t.status = :status and (t.lastHeartbeat is null or t.lastHeartbeat < :threshold)")
    List<ExportTaskEntity> findStuckTasks(@Param("status") ExportTaskStatus status,
                                          @Param("threshold") LocalDateTime threshold);

    @Transactional
    @Modifying
    @Query("delete from ExportTaskEntity t where t.expireAt is not null and t.expireAt < :deadline")
    int deleteExpired(LocalDateTime deadline);
}

