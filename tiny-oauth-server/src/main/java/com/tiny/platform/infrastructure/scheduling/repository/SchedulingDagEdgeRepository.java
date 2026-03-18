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

    /** 租户维度：按版本 + 租户查边，无请求上下文时使用（如 DependencyChecker） */
    List<SchedulingDagEdge> findByDagVersionIdAndTenantId(Long dagVersionId, Long tenantId);

    /** 租户维度：按版本 + 上游节点 + 租户查边 */
    List<SchedulingDagEdge> findByDagVersionIdAndFromNodeCodeAndTenantId(Long dagVersionId, String fromNodeCode, Long tenantId);

    /** 租户维度：按版本 + 下游节点 + 租户查边 */
    List<SchedulingDagEdge> findByDagVersionIdAndToNodeCodeAndTenantId(Long dagVersionId, String toNodeCode, Long tenantId);

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

