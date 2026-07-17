package com.tiny.platform.infrastructure.workflow.repository;

import com.tiny.platform.infrastructure.workflow.model.ProcessModelScopeType;
import com.tiny.platform.infrastructure.workflow.model.WorkflowBusinessRequestEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface WorkflowBusinessRequestRepository extends JpaRepository<WorkflowBusinessRequestEntity, Long> {

    Optional<WorkflowBusinessRequestEntity> findByProcessInstanceId(String processInstanceId);

    @Query("""
        SELECT request
        FROM WorkflowBusinessRequestEntity request
        WHERE request.scopeType = :scopeType
          AND request.requestId = :requestId
          AND (
            (:tenantId IS NULL AND request.tenantId IS NULL)
            OR request.tenantId = :tenantId
          )
        """)
    Optional<WorkflowBusinessRequestEntity> findByScopeAndRequestId(
        @Param("scopeType") ProcessModelScopeType scopeType,
        @Param("tenantId") Long tenantId,
        @Param("requestId") String requestId
    );
}
