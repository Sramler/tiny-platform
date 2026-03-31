package com.tiny.platform.infrastructure.auth.resource.repository;

import com.tiny.platform.infrastructure.auth.resource.domain.UiActionPermissionRequirement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface UiActionPermissionRequirementRepository extends JpaRepository<UiActionPermissionRequirement, Long> {

    void deleteByUiActionIdAndRequirementGroup(Long uiActionId, Integer requirementGroup);

    void deleteByUiActionId(Long uiActionId);

    @Query("""
        SELECT CASE WHEN COUNT(r) > 0 THEN true ELSE false END
        FROM UiActionPermissionRequirement r
        WHERE r.permissionId = :permissionId
          AND ((:tenantId IS NULL AND r.tenantId IS NULL) OR r.tenantId = :tenantId)
        """)
    boolean existsByPermissionIdAndTenantScope(@Param("permissionId") Long permissionId,
                                               @Param("tenantId") Long tenantId);

    @Query(value = """
        SELECT
          r.ui_action_id AS carrierId,
          r.requirement_group AS requirementGroup,
          r.sort_order AS sortOrder,
          p.permission_code AS permissionCode,
          r.negated AS negated,
          p.enabled AS permissionEnabled
        FROM ui_action_permission_requirement r
        JOIN permission p ON p.id = r.permission_id
        WHERE r.ui_action_id IN (:uiActionIds)
        ORDER BY r.ui_action_id ASC, r.requirement_group ASC, r.sort_order ASC, r.id ASC
        """, nativeQuery = true)
    List<CarrierPermissionRequirementRow> findRowsByUiActionIdIn(@Param("uiActionIds") Collection<Long> uiActionIds);
}
