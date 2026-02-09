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
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface SchedulingTaskInstanceRepository extends JpaRepository<SchedulingTaskInstance, Long>, JpaSpecificationExecutor<SchedulingTaskInstance> {
    List<SchedulingTaskInstance> findByDagRunId(Long dagRunId);
    
    List<SchedulingTaskInstance> findByDagRunIdAndNodeCode(Long dagRunId, String nodeCode);
    
    List<SchedulingTaskInstance> findByStatusAndScheduledAtLessThanEqual(String status, LocalDateTime scheduledAt);

    Page<SchedulingTaskInstance> findByStatusAndScheduledAtLessThanEqual(String status, LocalDateTime scheduledAt, Pageable pageable);

    /**
     * 查询可被 Worker 拾取的 PENDING 任务：已到调度时间且已到重试时间（若有）。
     * 条件：status='PENDING' AND scheduled_at<=scheduledAtLimit AND (next_retry_at IS NULL OR next_retry_at<=nextRetryAtLimit)
     */
    @Query("SELECT ti FROM SchedulingTaskInstance ti WHERE ti.status = :status AND ti.scheduledAt <= :scheduledAtLimit AND (ti.nextRetryAt IS NULL OR ti.nextRetryAt <= :nextRetryAtLimit) ORDER BY ti.scheduledAt ASC")
    Page<SchedulingTaskInstance> findPendingReadyForExecution(
            @Param("status") String status,
            @Param("scheduledAtLimit") LocalDateTime scheduledAtLimit,
            @Param("nextRetryAtLimit") LocalDateTime nextRetryAtLimit,
            Pageable pageable);

    Optional<SchedulingTaskInstance> findByIdAndStatus(Long id, String status);

    boolean existsByDagRunIdAndNodeCodeAndStatusIn(Long dagRunId, String nodeCode, Iterable<String> statuses);

    boolean existsByTaskIdAndStatusIn(Long taskId, Iterable<String> statuses);

    boolean existsByTaskIdAndNodeCodeAndStatusIn(Long taskId, String nodeCode, Iterable<String> statuses);

    /** KEYED 并发：按 taskId + concurrencyKey 统计活跃实例 */
    boolean existsByTaskIdAndConcurrencyKeyAndStatusIn(Long taskId, String concurrencyKey, Iterable<String> statuses);

    /** 依赖检查：统计同一 DAG Run 下指定节点中至少有一个 SUCCESS 实例的不同节点数（一次 SQL） */
    @Query("SELECT COUNT(DISTINCT ti.nodeCode) FROM SchedulingTaskInstance ti WHERE ti.dagRunId = :dagRunId AND ti.nodeCode IN :nodeCodes AND ti.status = :status")
    long countDistinctNodeCodesByDagRunIdAndNodeCodeInAndStatus(
            @Param("dagRunId") Long dagRunId, @Param("nodeCodes") Collection<String> nodeCodes, @Param("status") String status);

    /** 下游调度：按 DAG Run + 节点编码列表查询 PENDING 且 scheduledAt 为空的实例 */
    List<SchedulingTaskInstance> findByDagRunIdAndNodeCodeInAndStatusAndScheduledAtIsNull(
            Long dagRunId, Collection<String> nodeCodes, String status);

    /** 僵尸回收：查询 status in (RESERVED,RUNNING) 且 lock_time < 指定时间的实例 */
    List<SchedulingTaskInstance> findByStatusInAndLockTimeBefore(Set<String> statuses, LocalDateTime lockTimeBefore);

    /** 僵尸回收：置为 PENDING、清空锁，并设 scheduledAt=now、nextRetryAt=null 以便立即重排；排除 error_message 含 TIMEOUT 的实例（与 result 分离，避免误伤正常返回值）。 */
    @Modifying
    @Query("UPDATE SchedulingTaskInstance e SET e.status = 'PENDING', e.lockedBy = null, e.lockTime = null, e.scheduledAt = :now, e.nextRetryAt = null WHERE e.status IN :statuses AND e.lockTime < :before AND (e.errorMessage IS NULL OR e.errorMessage NOT LIKE '%TIMEOUT%')")
    int recoverZombies(@Param("statuses") Set<String> statuses, @Param("before") LocalDateTime before, @Param("now") LocalDateTime now);

    @Modifying
    @Query("UPDATE SchedulingTaskInstance ti SET ti.status = :status, ti.lockedBy = :lockedBy, ti.lockTime = :lockTime WHERE ti.id = :id AND ti.status = 'PENDING'")
    int reserveTaskInstance(@Param("id") Long id, @Param("status") String status, @Param("lockedBy") String lockedBy, @Param("lockTime") LocalDateTime lockTime);
}


