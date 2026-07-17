package com.tiny.platform.infrastructure.workflow.repository;

import com.tiny.platform.infrastructure.workflow.model.ProcessModelScopeType;
import com.tiny.platform.infrastructure.workflow.model.WorkflowGovernanceAssetEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WorkflowGovernanceAssetRepository extends JpaRepository<WorkflowGovernanceAssetEntity, Long> {

    @Query("""
        select max(a.version) from WorkflowGovernanceAssetEntity a
        where a.scopeType = :scopeType
          and ((:tenantId is null and a.tenantId is null) or a.tenantId = :tenantId)
          and a.assetType = :assetType
          and a.assetKey = :assetKey
        """)
    Integer findMaxVersionInScope(
        @Param("scopeType") ProcessModelScopeType scopeType,
        @Param("tenantId") Long tenantId,
        @Param("assetType") String assetType,
        @Param("assetKey") String assetKey
    );
}
