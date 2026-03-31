package com.tiny.platform.infrastructure.menu.repository;

import com.tiny.platform.infrastructure.menu.domain.MenuEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface MenuEntryRepository extends JpaRepository<MenuEntry, Long>, JpaSpecificationExecutor<MenuEntry> {

    List<MenuEntry> findByTenantIdAndTypeInOrderBySortAsc(Long tenantId, Collection<Integer> types);

    List<MenuEntry> findByTenantIdAndTypeInAndParentIdIsNullOrderBySortAsc(Long tenantId, Collection<Integer> types);

    List<MenuEntry> findByTenantIdAndTypeInAndParentIdOrderBySortAsc(Long tenantId, Collection<Integer> types, Long parentId);

    List<MenuEntry> findByTenantIdIsNullAndTypeInOrderBySortAsc(Collection<Integer> types);

    List<MenuEntry> findByTenantIdIsNullAndTypeInAndParentIdIsNullOrderBySortAsc(Collection<Integer> types);

    List<MenuEntry> findByTenantIdIsNullAndTypeInAndParentIdOrderBySortAsc(Collection<Integer> types, Long parentId);

    @Query("""
        SELECT m
        FROM MenuEntry m
        WHERE ((:tenantId IS NULL AND m.tenantId IS NULL) OR m.tenantId = :tenantId)
          AND LOWER(m.resourceLevel) = LOWER(:resourceLevel)
          AND m.requiredPermissionId IN :requiredPermissionIds
        ORDER BY m.sort ASC, m.id ASC
        """)
    List<MenuEntry> findByRequiredPermissionIdInAndScope(@Param("requiredPermissionIds") Collection<Long> requiredPermissionIds,
                                                         @Param("tenantId") Long tenantId,
                                                         @Param("resourceLevel") String resourceLevel);

    @Query("""
        SELECT CASE WHEN COUNT(m) > 0 THEN true ELSE false END
        FROM MenuEntry m
        WHERE m.requiredPermissionId = :permissionId
          AND ((:tenantId IS NULL AND m.tenantId IS NULL) OR m.tenantId = :tenantId)
        """)
    boolean existsByRequiredPermissionIdAndTenantScope(@Param("permissionId") Long permissionId,
                                                       @Param("tenantId") Long tenantId);

    boolean existsByParentIdAndTenantId(Long parentId, Long tenantId);
}
