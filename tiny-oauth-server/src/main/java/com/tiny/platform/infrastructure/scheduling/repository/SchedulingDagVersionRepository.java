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
    List<SchedulingDagVersion> findByDagId(Long dagId);
    
    Optional<SchedulingDagVersion> findByDagIdAndVersionNo(Long dagId, Integer versionNo);
    
    Optional<SchedulingDagVersion> findByDagIdAndStatus(Long dagId, String status);
    
    @Query("SELECT MAX(dv.versionNo) FROM SchedulingDagVersion dv WHERE dv.dagId = :dagId")
    Integer findMaxVersionNoByDagId(@Param("dagId") Long dagId);
}


