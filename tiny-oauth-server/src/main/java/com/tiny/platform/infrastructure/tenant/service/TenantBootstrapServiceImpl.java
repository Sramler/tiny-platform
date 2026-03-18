package com.tiny.platform.infrastructure.tenant.service;

import com.tiny.platform.infrastructure.auth.resource.domain.Resource;
import com.tiny.platform.infrastructure.auth.resource.repository.ResourceRepository;
import com.tiny.platform.infrastructure.auth.resource.support.PlatformControlPlaneResourcePolicy;
import com.tiny.platform.infrastructure.auth.role.domain.Role;
import com.tiny.platform.infrastructure.auth.role.repository.RoleRepository;
import com.tiny.platform.infrastructure.auth.role.repository.RoleResourceRelationProjection;
import com.tiny.platform.infrastructure.tenant.config.PlatformTenantProperties;
import com.tiny.platform.infrastructure.tenant.domain.Tenant;
import com.tiny.platform.infrastructure.tenant.repository.TenantRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 新租户从“平台模板”或“平台/模板租户”复制角色与资源。
 * 优先使用平台模板（role/resource 中 tenant_id IS NULL）；若无则回退到配置的平台租户 code（默认 "default"）。
 * 见 docs/TINY_PLATFORM_AUTHORIZATION_LEGACY_REMOVAL_PLAN.md §4、TINY_PLATFORM_AUTHORIZATION_MODEL.md。
 */
@Service
public class TenantBootstrapServiceImpl implements TenantBootstrapService {

    private final TenantRepository tenantRepository;
    private final ResourceRepository resourceRepository;
    private final RoleRepository roleRepository;
    private final PlatformTenantProperties platformTenantProperties;

    public TenantBootstrapServiceImpl(
        TenantRepository tenantRepository,
        ResourceRepository resourceRepository,
        RoleRepository roleRepository,
        PlatformTenantProperties platformTenantProperties
    ) {
        this.tenantRepository = tenantRepository;
        this.resourceRepository = resourceRepository;
        this.roleRepository = roleRepository;
        this.platformTenantProperties = platformTenantProperties;
    }

    @Override
    @Transactional
    public void bootstrapFromDefaultTenant(Tenant targetTenant) {
        Long targetTenantId = requireTargetTenantId(targetTenant);

        List<Resource> platformResources = resourceRepository.findByTenantIdIsNullOrderBySortAscIdAsc();
        List<Role> platformRoles = roleRepository.findByTenantIdIsNullOrderByIdAsc();
        if (!platformResources.isEmpty() && !platformRoles.isEmpty()) {
            ResourceCloneResult resourceCloneResult = cloneResourcesFromSourceList(platformResources, targetTenantId);
            List<RoleResourceRelationProjection> platformRelations = roleRepository.findRoleResourceRelationsByTenantIdIsNull();
            cloneRolesAndRelationsFromLists(platformRoles, platformRelations, resourceCloneResult, targetTenantId);
            return;
        }

        String sourceCode = platformTenantProperties.getPlatformTenantCode();
        Tenant sourceTenant = tenantRepository.findByCode(sourceCode)
            .orElseThrow(() -> new IllegalStateException("平台/模板租户不存在（code=" + sourceCode + "），无法初始化新租户权限模型"));

        if (Objects.equals(sourceTenant.getId(), targetTenantId)) {
            return;
        }

        ResourceCloneResult resourceCloneResult = cloneResources(sourceTenant.getId(), targetTenantId);
        cloneRolesAndRelations(sourceTenant.getId(), targetTenantId, resourceCloneResult);
    }

    private ResourceCloneResult cloneResourcesFromSourceList(List<Resource> allSourceResources, Long targetTenantId) {
        Set<Long> skippedSourceResourceIds = allSourceResources.stream()
            .filter(PlatformControlPlaneResourcePolicy::isPlatformOnlyResource)
            .map(Resource::getId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        List<Resource> sourceResources = allSourceResources.stream()
            .filter(resource -> !PlatformControlPlaneResourcePolicy.isPlatformOnlyResource(resource))
            .toList();
        if (sourceResources.isEmpty()) {
            return new ResourceCloneResult(Map.of(), skippedSourceResourceIds);
        }

        Map<Long, Resource> clonedBySourceId = new LinkedHashMap<>();
        List<Resource> clonedResources = new ArrayList<>();
        for (Resource source : sourceResources) {
            Resource clone = new Resource();
            clone.setTenantId(targetTenantId);
            clone.setResourceLevel("TENANT");
            clone.setName(source.getName());
            clone.setUrl(source.getUrl());
            clone.setUri(source.getUri());
            clone.setMethod(source.getMethod());
            clone.setIcon(source.getIcon());
            clone.setShowIcon(source.getShowIcon());
            clone.setSort(source.getSort());
            clone.setComponent(source.getComponent());
            clone.setRedirect(source.getRedirect());
            clone.setHidden(source.getHidden());
            clone.setKeepAlive(source.getKeepAlive());
            clone.setTitle(source.getTitle());
            clone.setPermission(source.getPermission());
            clone.setType(source.getType());
            clone.setEnabled(source.getEnabled());
            clone.setParentId(null);
            clonedBySourceId.put(source.getId(), clone);
            clonedResources.add(clone);
        }

        resourceRepository.saveAll(clonedResources);

        boolean hasParentReference = false;
        for (Resource source : sourceResources) {
            if (source.getParentId() == null) {
                continue;
            }
            Resource clonedParent = clonedBySourceId.get(source.getParentId());
            if (clonedParent == null || clonedParent.getId() == null) {
                throw new IllegalStateException("默认租户资源父节点映射缺失，无法复制资源树");
            }
            clonedBySourceId.get(source.getId()).setParentId(clonedParent.getId());
            hasParentReference = true;
        }
        if (hasParentReference) {
            resourceRepository.saveAll(clonedResources);
        }

        Map<Long, Long> resourceIdMapping = new LinkedHashMap<>();
        for (Resource source : sourceResources) {
            Resource cloned = clonedBySourceId.get(source.getId());
            if (cloned == null || cloned.getId() == null) {
                throw new IllegalStateException("默认租户资源复制失败，缺少目标资源ID");
            }
            resourceIdMapping.put(source.getId(), cloned.getId());
        }
        return new ResourceCloneResult(resourceIdMapping, skippedSourceResourceIds);
    }

    private ResourceCloneResult cloneResources(Long sourceTenantId, Long targetTenantId) {
        List<Resource> allSourceResources = resourceRepository.findByTenantIdOrderBySortAscIdAsc(sourceTenantId);
        return cloneResourcesFromSourceList(allSourceResources, targetTenantId);
    }

    private void cloneRolesAndRelationsFromLists(List<Role> sourceRoles, List<RoleResourceRelationProjection> relations,
                                                 ResourceCloneResult resourceCloneResult, Long targetTenantId) {
        if (sourceRoles.isEmpty()) {
            return;
        }

        Map<Long, Role> clonedBySourceRoleId = new LinkedHashMap<>();
        List<Role> clonedRoles = new ArrayList<>();
        for (Role source : sourceRoles) {
            Role clone = new Role();
            clone.setTenantId(targetTenantId);
            clone.setRoleLevel("TENANT");
            clone.setCode(source.getCode());
            clone.setName(source.getName());
            clone.setEnabled(source.isEnabled());
            clone.setBuiltin(source.isBuiltin());
            clone.setDescription(source.getDescription());
            clonedBySourceRoleId.put(source.getId(), clone);
            clonedRoles.add(clone);
        }

        roleRepository.saveAll(clonedRoles);

        for (RoleResourceRelationProjection relation : relations) {
            Role targetRole = clonedBySourceRoleId.get(relation.getRoleId());
            Long sourceResourceId = relation.getResourceId();
            Long targetResourceId = resourceCloneResult.resourceIdMapping().get(sourceResourceId);
            if (targetRole == null || targetRole.getId() == null) {
                throw new IllegalStateException("默认租户角色复制失败，缺少目标角色ID");
            }
            if (targetResourceId == null) {
                if (resourceCloneResult.skippedSourceResourceIds().contains(sourceResourceId)) {
                    continue;
                }
                throw new IllegalStateException("默认租户角色资源关联复制失败，缺少目标资源ID");
            }
            roleRepository.addRoleResourceRelation(targetTenantId, targetRole.getId(), targetResourceId);
        }
    }

    private void cloneRolesAndRelations(Long sourceTenantId, Long targetTenantId, ResourceCloneResult resourceCloneResult) {
        List<Role> sourceRoles = roleRepository.findByTenantIdOrderByIdAsc(sourceTenantId);
        List<RoleResourceRelationProjection> relations = roleRepository.findRoleResourceRelationsByTenantId(sourceTenantId);
        cloneRolesAndRelationsFromLists(sourceRoles, relations, resourceCloneResult, targetTenantId);
    }

    private Long requireTargetTenantId(Tenant targetTenant) {
        if (targetTenant == null || targetTenant.getId() == null) {
            throw new IllegalArgumentException("目标租户不存在，无法初始化权限模型");
        }
        return targetTenant.getId();
    }

    private record ResourceCloneResult(Map<Long, Long> resourceIdMapping, Set<Long> skippedSourceResourceIds) {
    }
}
