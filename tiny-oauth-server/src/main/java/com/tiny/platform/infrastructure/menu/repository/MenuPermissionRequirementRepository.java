package com.tiny.platform.infrastructure.menu.repository;

import com.tiny.platform.infrastructure.auth.resource.repository.CarrierPermissionRequirementRow;
import com.tiny.platform.infrastructure.menu.domain.MenuPermissionRequirement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface MenuPermissionRequirementRepository extends JpaRepository<MenuPermissionRequirement, Long> {

    void deleteByMenuIdAndRequirementGroup(Long menuId, Integer requirementGroup);

    void deleteByMenuId(Long menuId);

    @Query("""
        SELECT CASE WHEN COUNT(r) > 0 THEN true ELSE false END
        FROM MenuPermissionRequirement r
        WHERE r.permissionId = :permissionId
          AND ((:tenantId IS NULL AND r.tenantId IS NULL) OR r.tenantId = :tenantId)
        """)
    boolean existsByPermissionIdAndTenantScope(@Param("permissionId") Long permissionId,
                                               @Param("tenantId") Long tenantId);

    @Query(value = """
        SELECT
          r.menu_id AS carrierId,
          r.requirement_group AS requirementGroup,
          r.sort_order AS sortOrder,
          p.permission_code AS permissionCode,
          r.negated AS negated,
          p.enabled AS permissionEnabled
        FROM menu_permission_requirement r
        JOIN permission p ON p.id = r.permission_id
        WHERE r.menu_id IN (:menuIds)
        ORDER BY r.menu_id ASC, r.requirement_group ASC, r.sort_order ASC, r.id ASC
        """, nativeQuery = true)
    List<CarrierPermissionRequirementRow> findRowsByMenuIdIn(@Param("menuIds") Collection<Long> menuIds);
}
