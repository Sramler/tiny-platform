package com.tiny.platform.infrastructure.auth.resource.repository;

import com.tiny.platform.infrastructure.auth.resource.domain.Resource;
import com.tiny.platform.infrastructure.auth.resource.dto.ResourceProjection;
import com.tiny.platform.infrastructure.auth.resource.dto.ResourceResponseDto;
import com.tiny.platform.infrastructure.auth.resource.enums.ResourceType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

/**
 * Legacy compatibility interface retained only for bridge constructors and
 * older tests that still mock a "resource repository".
 *
 * <p>The runtime application no longer wires a Spring Data repository for the
 * legacy {@code resource} table. Any remaining production reads must go
 * through carrier-backed repositories instead.</p>
 */
public interface ResourceRepository extends CarrierProjectionRepository {

    <S extends Resource> S save(S entity);

    <S extends Resource> List<S> saveAll(Iterable<S> entities);

    void delete(Resource entity);

    Optional<Resource> findById(Long id);

    List<Resource> findAllById(Iterable<Long> ids);

    Optional<Resource> findOne(Specification<Resource> specification);

    Page<Resource> findAll(Specification<Resource> specification, Pageable pageable);

    List<Resource> findByParentIdAndTenantIdOrderBySortAsc(Long parentId, Long tenantId);

    List<Resource> findByParentIdOrderBySortAsc(Long parentId);

    List<Resource> findByParentIdIsNullOrderBySortAsc();

    List<Resource> findByTypeOrderBySortAsc(ResourceType type);

    List<Resource> findByTypeInOrderBySortAsc(List<ResourceType> types);

    Optional<Resource> findByTenantIdAndResourceLevelAndCarrierTypeAndCarrierSourceId(
        Long tenantId,
        String resourceLevel,
        String carrierType,
        Long carrierSourceId
    );

    List<RoleResourcePermissionBindingView> findRolePermissionBindingViewsByIdsAndScope(
        List<Long> ids,
        Long tenantId,
        String resourceLevel
    );

    default Page<ResourceProjection> findMenusByNativeSql(List<Integer> types,
                                                          int typesSize,
                                                          Long parentId,
                                                          String title,
                                                          String name,
                                                          String permission,
                                                          Boolean enabled,
                                                          Long tenantId,
                                                          Pageable pageable) {
        throw new UnsupportedOperationException("Legacy compatibility interface only");
    }

    default Page<ResourceResponseDto> findMenusByJpqlDto(List<Integer> types,
                                                         Long parentId,
                                                         String title,
                                                         String name,
                                                         String permission,
                                                         Boolean enabled,
                                                         Long tenantId,
                                                         Pageable pageable) {
        throw new UnsupportedOperationException("Legacy compatibility interface only");
    }

    default List<CarrierTemplateResourceSnapshotView> findCarrierTemplateSnapshotViewsByScope(Long tenantId,
                                                                                              String resourceLevel) {
        return findTemplateSnapshotViewsByScope(tenantId, resourceLevel);
    }

    default List<RoleResourcePermissionBindingView> findCarrierPermissionBindingViewsByIdsAndScope(List<Long> ids,
                                                                                                    Long tenantId,
                                                                                                    String resourceLevel) {
        return findPermissionBindingViewsByIdsAndScope(ids, tenantId, resourceLevel);
    }
}
