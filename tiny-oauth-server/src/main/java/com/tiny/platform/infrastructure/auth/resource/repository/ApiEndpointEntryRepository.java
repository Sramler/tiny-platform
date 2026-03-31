package com.tiny.platform.infrastructure.auth.resource.repository;

import com.tiny.platform.infrastructure.auth.resource.domain.ApiEndpointEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface ApiEndpointEntryRepository extends JpaRepository<ApiEndpointEntry, Long>, JpaSpecificationExecutor<ApiEndpointEntry> {
    @Query("""
        SELECT e
        FROM ApiEndpointEntry e
        WHERE ((:tenantId IS NULL AND e.tenantId IS NULL) OR e.tenantId = :tenantId)
          AND LOWER(e.resourceLevel) = LOWER(:resourceLevel)
          AND e.requiredPermissionId IN :requiredPermissionIds
        ORDER BY e.id ASC
        """)
    List<ApiEndpointEntry> findByRequiredPermissionIdInAndScope(@Param("requiredPermissionIds") Collection<Long> requiredPermissionIds,
                                                                @Param("tenantId") Long tenantId,
                                                                @Param("resourceLevel") String resourceLevel);

    @Query("""
        SELECT CASE WHEN COUNT(e) > 0 THEN true ELSE false END
        FROM ApiEndpointEntry e
        WHERE e.requiredPermissionId = :permissionId
          AND ((:tenantId IS NULL AND e.tenantId IS NULL) OR e.tenantId = :tenantId)
        """)
    boolean existsByRequiredPermissionIdAndTenantScope(@Param("permissionId") Long permissionId,
                                                       @Param("tenantId") Long tenantId);
}
