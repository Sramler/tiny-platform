package com.tiny.platform.infrastructure.scheduling.repository;

import com.tiny.platform.infrastructure.scheduling.model.SchedulingTaskInstance;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface SchedulingTaskInstanceRepository extends JpaRepository<SchedulingTaskInstance, Long>, JpaSpecificationExecutor<SchedulingTaskInstance> {
    List<SchedulingTaskInstance> findByDagRunId(Long dagRunId);
    
    List<SchedulingTaskInstance> findByDagRunIdAndNodeCode(Long dagRunId, String nodeCode);
    
    List<SchedulingTaskInstance> findByStatusAndScheduledAtLessThanEqual(String status, LocalDateTime scheduledAt);

    Page<SchedulingTaskInstance> findByStatusAndScheduledAtLessThanEqual(String status, LocalDateTime scheduledAt, Pageable pageable);
    
    Optional<SchedulingTaskInstance> findByIdAndStatus(Long id, String status);

    boolean existsByDagRunIdAndNodeCodeAndStatusIn(Long dagRunId, String nodeCode, Iterable<String> statuses);

    boolean existsByTaskIdAndStatusIn(Long taskId, Iterable<String> statuses);

    boolean existsByTaskIdAndNodeCodeAndStatusIn(Long taskId, String nodeCode, Iterable<String> statuses);
    
    @Modifying
    @Query("UPDATE SchedulingTaskInstance ti SET ti.status = :status, ti.lockedBy = :lockedBy, ti.lockTime = :lockTime WHERE ti.id = :id AND ti.status = 'PENDING'")
    int reserveTaskInstance(@Param("id") Long id, @Param("status") String status, @Param("lockedBy") String lockedBy, @Param("lockTime") LocalDateTime lockTime);
}


