package com.tiny.platform.infrastructure.scheduling.repository;

import com.tiny.platform.infrastructure.scheduling.model.SchedulingDagEdge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SchedulingDagEdgeRepository extends JpaRepository<SchedulingDagEdge, Long> {
    List<SchedulingDagEdge> findByDagVersionId(Long dagVersionId);
    
    List<SchedulingDagEdge> findByDagVersionIdAndFromNodeCode(Long dagVersionId, String fromNodeCode);
    
    List<SchedulingDagEdge> findByDagVersionIdAndToNodeCode(Long dagVersionId, String toNodeCode);

    @Modifying
    @Query("""
            UPDATE SchedulingDagEdge de
               SET de.fromNodeCode = :newNodeCode
             WHERE de.dagVersionId = :dagVersionId
               AND de.fromNodeCode = :oldNodeCode
            """)
    int updateFromNodeCode(
            @Param("dagVersionId") Long dagVersionId,
            @Param("oldNodeCode") String oldNodeCode,
            @Param("newNodeCode") String newNodeCode);

    @Modifying
    @Query("""
            UPDATE SchedulingDagEdge de
               SET de.toNodeCode = :newNodeCode
             WHERE de.dagVersionId = :dagVersionId
               AND de.toNodeCode = :oldNodeCode
            """)
    int updateToNodeCode(
            @Param("dagVersionId") Long dagVersionId,
            @Param("oldNodeCode") String oldNodeCode,
            @Param("newNodeCode") String newNodeCode);
    
    @Modifying
    @Query("DELETE FROM SchedulingDagEdge de WHERE de.dagVersionId = :dagVersionId")
    void deleteByDagVersionId(@Param("dagVersionId") Long dagVersionId);
}

