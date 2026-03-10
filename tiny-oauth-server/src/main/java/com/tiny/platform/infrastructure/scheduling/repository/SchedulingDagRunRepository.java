package com.tiny.platform.infrastructure.scheduling.repository;

import com.tiny.platform.infrastructure.scheduling.model.SchedulingDagRun;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Collection;

@Repository
public interface SchedulingDagRunRepository extends JpaRepository<SchedulingDagRun, Long>, JpaSpecificationExecutor<SchedulingDagRun> {
    Optional<SchedulingDagRun> findByIdAndTenantId(Long id, Long tenantId);

    List<SchedulingDagRun> findByDagId(Long dagId);

    List<SchedulingDagRun> findByDagIdInAndStatusInOrderByIdDesc(Collection<Long> dagIds, Collection<String> statuses);

    List<SchedulingDagRun> findByDagIdOrderByIdDesc(Long dagId);

    Optional<SchedulingDagRun> findByRunNo(String runNo);

    List<SchedulingDagRun> findByDagIdAndStatus(Long dagId, String status);

    Optional<SchedulingDagRun> findTopByDagIdAndStatusInOrderByIdDesc(Long dagId, Collection<String> statuses);

    Optional<SchedulingDagRun> findTopByDagIdAndStatusOrderByIdDesc(Long dagId, String status);

    long countByDagId(Long dagId);

    List<SchedulingDagRun> findByStatus(String status);

    Page<SchedulingDagRun> findByStatus(String status, Pageable pageable);

    /**
     * 运行统计聚合（一次 SQL）：total/success/failed/completedCount/avgDurationMs。
     * 用于大数据量下避免全量加载到内存。
     */
    @Query(value = "SELECT COUNT(*) AS total, "
            + "SUM(CASE WHEN status = 'SUCCESS' THEN 1 ELSE 0 END) AS success, "
            + "SUM(CASE WHEN status IN ('FAILED','PARTIAL_FAILED') THEN 1 ELSE 0 END) AS failed, "
            + "SUM(CASE WHEN start_time IS NOT NULL AND end_time IS NOT NULL THEN 1 ELSE 0 END) AS completed_count, "
            + "AVG(CASE WHEN start_time IS NOT NULL AND end_time IS NOT NULL THEN TIMESTAMPDIFF(MICROSECOND, start_time, end_time) / 1000.0 END) AS avg_ms "
            + "FROM scheduling_dag_run WHERE dag_id = :dagId", nativeQuery = true)
    Object[] getDagRunStatsAggregation(@Param("dagId") Long dagId);

    /**
     * 分位数：按耗时升序取第 offset 条（0-based）的 duration_ms，用于 P95/P99。
     * 无满足条件的行时返回 null。
     */
    @Query(value = "SELECT duration_ms FROM ("
            + "SELECT TIMESTAMPDIFF(MICROSECOND, start_time, end_time) / 1000.0 AS duration_ms "
            + "FROM scheduling_dag_run WHERE dag_id = :dagId AND start_time IS NOT NULL AND end_time IS NOT NULL "
            + "ORDER BY duration_ms LIMIT 1 OFFSET :offset"
            + ") t", nativeQuery = true)
    Double getDurationMsAtOffset(@Param("dagId") Long dagId, @Param("offset") int offset);
}
