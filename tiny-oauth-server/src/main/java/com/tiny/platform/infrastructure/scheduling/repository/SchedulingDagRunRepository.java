package com.tiny.platform.infrastructure.scheduling.repository;

import com.tiny.platform.infrastructure.scheduling.model.SchedulingDagRun;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SchedulingDagRunRepository extends JpaRepository<SchedulingDagRun, Long>, JpaSpecificationExecutor<SchedulingDagRun> {
    List<SchedulingDagRun> findByDagId(Long dagId);
    
    Optional<SchedulingDagRun> findByRunNo(String runNo);
    
    List<SchedulingDagRun> findByDagIdAndStatus(Long dagId, String status);
    
    List<SchedulingDagRun> findByStatus(String status);

    Page<SchedulingDagRun> findByStatus(String status, Pageable pageable);
}


