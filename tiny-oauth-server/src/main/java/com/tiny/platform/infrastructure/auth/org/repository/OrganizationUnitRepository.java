package com.tiny.platform.infrastructure.auth.org.repository;

import com.tiny.platform.infrastructure.auth.org.domain.OrganizationUnit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OrganizationUnitRepository extends JpaRepository<OrganizationUnit, Long> {

    List<OrganizationUnit> findByTenantIdOrderBySortOrderAsc(Long tenantId);

    List<OrganizationUnit> findByTenantIdAndParentIdOrderBySortOrderAsc(Long tenantId, Long parentId);

    /**
     * 查询租户内所有根节点（parent_id IS NULL）。
     */
    List<OrganizationUnit> findByTenantIdAndParentIdIsNullOrderBySortOrderAsc(Long tenantId);

    Optional<OrganizationUnit> findByTenantIdAndCode(Long tenantId, String code);

    Optional<OrganizationUnit> findByIdAndTenantId(Long id, Long tenantId);

    boolean existsByTenantIdAndCode(Long tenantId, String code);

    boolean existsByTenantIdAndParentId(Long tenantId, Long parentId);

    @Query("""
        select ou.id from OrganizationUnit ou
        where ou.tenantId = :tenantId and ou.parentId = :parentId
        """)
    List<Long> findChildIdsByTenantIdAndParentId(
        @Param("tenantId") Long tenantId,
        @Param("parentId") Long parentId
    );

    @Query("""
        select ou from OrganizationUnit ou
        where ou.tenantId = :tenantId and ou.unitType = :unitType and ou.status = 'ACTIVE'
        order by ou.sortOrder asc
        """)
    List<OrganizationUnit> findActiveByTenantIdAndUnitType(
        @Param("tenantId") Long tenantId,
        @Param("unitType") String unitType
    );
}
