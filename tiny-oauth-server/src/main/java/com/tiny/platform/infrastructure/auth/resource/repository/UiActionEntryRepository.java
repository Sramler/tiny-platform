package com.tiny.platform.infrastructure.auth.resource.repository;

import com.tiny.platform.infrastructure.auth.resource.domain.UiActionEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface UiActionEntryRepository extends JpaRepository<UiActionEntry, Long>, JpaSpecificationExecutor<UiActionEntry> {
    boolean existsByParentMenuIdAndTenantId(Long parentMenuId, Long tenantId);

    boolean existsByParentMenuIdAndTenantIdIsNull(Long parentMenuId);

    @Query("""
        SELECT a
        FROM UiActionEntry a
        WHERE ((:tenantId IS NULL AND a.tenantId IS NULL) OR a.tenantId = :tenantId)
          AND LOWER(a.resourceLevel) = LOWER(:resourceLevel)
          AND a.requiredPermissionId IN :requiredPermissionIds
        ORDER BY a.sort ASC, a.id ASC
        """)
    List<UiActionEntry> findByRequiredPermissionIdInAndScope(@Param("requiredPermissionIds") Collection<Long> requiredPermissionIds,
                                                             @Param("tenantId") Long tenantId,
                                                             @Param("resourceLevel") String resourceLevel);

    @Query("""
        SELECT CASE WHEN COUNT(a) > 0 THEN true ELSE false END
        FROM UiActionEntry a
        WHERE a.requiredPermissionId = :permissionId
          AND ((:tenantId IS NULL AND a.tenantId IS NULL) OR a.tenantId = :tenantId)
        """)
    boolean existsByRequiredPermissionIdAndTenantScope(@Param("permissionId") Long permissionId,
                                                       @Param("tenantId") Long tenantId);
}
