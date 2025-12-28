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
    List<SchedulingDagTask> findByDagVersionId(Long dagVersionId);
    
    Optional<SchedulingDagTask> findByDagVersionIdAndNodeCode(Long dagVersionId, String nodeCode);
    
    List<SchedulingDagTask> findByTaskId(Long taskId);
    
    @Modifying
    @Query("DELETE FROM SchedulingDagTask dt WHERE dt.dagVersionId = :dagVersionId")
    void deleteByDagVersionId(@Param("dagVersionId") Long dagVersionId);
}

