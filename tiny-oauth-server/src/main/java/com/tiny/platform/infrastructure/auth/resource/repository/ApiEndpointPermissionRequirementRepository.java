package com.tiny.platform.infrastructure.auth.resource.repository;

import com.tiny.platform.infrastructure.auth.resource.domain.ApiEndpointPermissionRequirement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface ApiEndpointPermissionRequirementRepository extends JpaRepository<ApiEndpointPermissionRequirement, Long> {

    void deleteByApiEndpointIdAndRequirementGroup(Long apiEndpointId, Integer requirementGroup);

    void deleteByApiEndpointId(Long apiEndpointId);

    @Query("""
        SELECT CASE WHEN COUNT(r) > 0 THEN true ELSE false END
        FROM ApiEndpointPermissionRequirement r
        WHERE r.permissionId = :permissionId
          AND ((:tenantId IS NULL AND r.tenantId IS NULL) OR r.tenantId = :tenantId)
        """)
    boolean existsByPermissionIdAndTenantScope(@Param("permissionId") Long permissionId,
                                               @Param("tenantId") Long tenantId);

    @Query(value = """
        SELECT
          r.api_endpoint_id AS carrierId,
          r.requirement_group AS requirementGroup,
          r.sort_order AS sortOrder,
          p.permission_code AS permissionCode,
          r.negated AS negated,
          p.enabled AS permissionEnabled
        FROM api_endpoint_permission_requirement r
        JOIN permission p ON p.id = r.permission_id
        WHERE r.api_endpoint_id IN (:apiEndpointIds)
        ORDER BY r.api_endpoint_id ASC, r.requirement_group ASC, r.sort_order ASC, r.id ASC
        """, nativeQuery = true)
    List<CarrierPermissionRequirementRow> findRowsByApiEndpointIdIn(@Param("apiEndpointIds") Collection<Long> apiEndpointIds);
}
