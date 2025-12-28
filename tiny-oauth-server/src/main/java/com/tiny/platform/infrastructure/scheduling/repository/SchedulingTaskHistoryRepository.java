package com.tiny.platform.infrastructure.scheduling.repository;

import com.tiny.platform.infrastructure.scheduling.model.SchedulingTaskHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SchedulingTaskHistoryRepository extends JpaRepository<SchedulingTaskHistory, Long>, JpaSpecificationExecutor<SchedulingTaskHistory> {
    List<SchedulingTaskHistory> findByTaskInstanceId(Long taskInstanceId);
    
    List<SchedulingTaskHistory> findByDagRunId(Long dagRunId);
    
    List<SchedulingTaskHistory> findByDagRunIdAndNodeCode(Long dagRunId, String nodeCode);
    
    @Modifying
    @Query("DELETE FROM SchedulingTaskHistory th WHERE th.dagRunId = :dagRunId")
    void deleteByDagRunId(@Param("dagRunId") Long dagRunId);
}


