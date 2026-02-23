package com.tiny.platform.infrastructure.auth.resource.service;

import com.tiny.platform.infrastructure.core.exception.code.ErrorCode;
import com.tiny.platform.infrastructure.core.exception.exception.BusinessException;
import com.tiny.platform.infrastructure.auth.resource.domain.Resource;
import com.tiny.platform.infrastructure.auth.resource.dto.ResourceCreateUpdateDto;
import com.tiny.platform.infrastructure.auth.resource.dto.ResourceRequestDto;
import com.tiny.platform.infrastructure.auth.resource.dto.ResourceResponseDto;
import com.tiny.platform.infrastructure.auth.resource.enums.ResourceType;
import com.tiny.platform.infrastructure.auth.resource.repository.ResourceRepository;
import com.tiny.platform.infrastructure.auth.role.domain.Role;
import com.tiny.platform.infrastructure.auth.role.repository.RoleRepository;
import com.tiny.platform.infrastructure.auth.user.repository.UserRepository;
import com.tiny.platform.core.oauth.tenant.TenantContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 资源服务实现类
 */
@Service
public class ResourceServiceImpl implements ResourceService {

    private final ResourceRepository resourceRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;

    public ResourceServiceImpl(ResourceRepository resourceRepository, UserRepository userRepository, RoleRepository roleRepository) {
        this.resourceRepository = resourceRepository;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
    }

    @Override
    public Page<ResourceResponseDto> resources(ResourceRequestDto query, Pageable pageable) {
        Long tenantId = requireTenantId();
        // 构建查询条件
        ResourceType type = query.getType() != null ? ResourceType.fromCode(query.getType()) : null;
        
        // 使用复合查询方法
        Page<Resource> resourcePage = resourceRepository.findByConditions(
                query.getName(),
                query.getUrl(),
                query.getUri(),
                query.getPermission(),
                query.getTitle(),
                type,
                query.getParentId(),
                query.getHidden(),
                tenantId,
                pageable
        );
        
        // 转换为DTO
        Page<ResourceResponseDto> dtoPage = resourcePage.map(this::toDto);
        
        return dtoPage;
    }

    @Override
    public Optional<Resource> findById(Long id) {
        return resourceRepository.findById(id).filter(r -> r.getTenantId().equals(requireTenantId()));
    }

    @Override
    public Resource create(Resource resource) {
        resource.setTenantId(requireTenantId());
        return resourceRepository.save(resource);
    }

    @Override
    public Resource update(Long id, Resource resource) {
        Long tenantId = requireTenantId();
        Resource existingResource = resourceRepository.findById(id)
                .filter(r -> r.getTenantId().equals(tenantId))
                .orElseThrow(() -> new RuntimeException("资源不存在"));
        
        // 更新字段
        existingResource.setName(resource.getName());
        existingResource.setTitle(resource.getTitle());
        existingResource.setUrl(resource.getUrl());
        existingResource.setUri(resource.getUri());
        existingResource.setMethod(resource.getMethod());
        existingResource.setIcon(resource.getIcon());
        existingResource.setShowIcon(resource.getShowIcon());
        existingResource.setSort(resource.getSort());
        existingResource.setComponent(resource.getComponent());
        existingResource.setRedirect(resource.getRedirect());
        existingResource.setHidden(resource.getHidden());
        existingResource.setKeepAlive(resource.getKeepAlive());
        existingResource.setPermission(resource.getPermission());
        existingResource.setType(resource.getType());
        existingResource.setParentId(resource.getParentId());
        
        return resourceRepository.save(existingResource);
    }

    @Override
    public Resource createFromDto(ResourceCreateUpdateDto resourceDto) {
        Long tenantId = requireTenantId();
        validateResourceByType(resourceDto);

        // 检查名称是否已存在
        if (resourceRepository.findByNameAndTenantId(resourceDto.getName(), tenantId).isPresent()) {
            throw new BusinessException(
                ErrorCode.RESOURCE_ALREADY_EXISTS,
                "资源名称已存在: " + resourceDto.getName()
            );
        }
        
        // 检查URL是否已存在（如果提供了URL）
        if (StringUtils.hasText(resourceDto.getUrl()) && 
            resourceRepository.findByUrlAndTenantId(resourceDto.getUrl(), tenantId).isPresent()) {
            throw new BusinessException(
                ErrorCode.RESOURCE_ALREADY_EXISTS,
                "资源URL已存在: " + resourceDto.getUrl()
            );
        }
        
        // 检查URI是否已存在（如果提供了URI）
        if (StringUtils.hasText(resourceDto.getUri()) && 
            resourceRepository.findByUriAndTenantId(resourceDto.getUri(), tenantId).isPresent()) {
            throw new BusinessException(
                ErrorCode.RESOURCE_ALREADY_EXISTS,
                "资源URI已存在: " + resourceDto.getUri()
            );
        }
        
        // 创建资源对象
        Resource resource = new Resource();
        resource.setTenantId(tenantId);
        resource.setName(resourceDto.getName());
        resource.setTitle(resourceDto.getTitle());
        resource.setUrl(resourceDto.getUrl());
        
        // 设置 uri 和 method 的默认值
        String uri = resourceDto.getUri();
        if (uri == null || uri.trim().isEmpty()) {
            uri = ""; // 设置默认值为空字符串
        }
        resource.setUri(uri);
        
        String method = resourceDto.getMethod();
        if (method == null || method.trim().isEmpty()) {
            method = ""; // 设置默认值为空字符串
        }
        resource.setMethod(method);
        
        resource.setIcon(resourceDto.getIcon());
        resource.setShowIcon(resourceDto.isShowIcon());
        resource.setSort(resourceDto.getSort());
        resource.setComponent(resourceDto.getComponent());
        resource.setRedirect(resourceDto.getRedirect());
        resource.setHidden(resourceDto.isHidden());
        resource.setKeepAlive(resourceDto.isKeepAlive());
        resource.setPermission(resourceDto.getPermission());
        resource.setType(ResourceType.fromCode(resourceDto.getType()));
        resource.setParentId(resourceDto.getParentId());
        
        return resourceRepository.save(resource);
    }

    @Override
    public Resource updateFromDto(ResourceCreateUpdateDto resourceDto) {
        Long tenantId = requireTenantId();
        validateResourceByType(resourceDto);

        Resource existingResource = resourceRepository.findById(resourceDto.getId())
                .filter(r -> r.getTenantId().equals(tenantId))
                .orElseThrow(() -> new BusinessException(
                    ErrorCode.NOT_FOUND,
                    "资源不存在: " + resourceDto.getId()
                ));
        
        // 检查名称是否已被其他资源使用
        Optional<Resource> resourceWithSameName = resourceRepository.findByNameAndTenantId(resourceDto.getName(), tenantId);
        if (resourceWithSameName.isPresent() && !resourceWithSameName.get().getId().equals(resourceDto.getId())) {
            throw new BusinessException(
                ErrorCode.RESOURCE_ALREADY_EXISTS,
                "资源名称已被其他资源使用: " + resourceDto.getName()
            );
        }
        
        // 检查URL是否已被其他资源使用（如果提供了URL）
        if (StringUtils.hasText(resourceDto.getUrl())) {
            Optional<Resource> resourceWithSameUrl = resourceRepository.findByUrlAndTenantId(resourceDto.getUrl(), tenantId);
            if (resourceWithSameUrl.isPresent() && !resourceWithSameUrl.get().getId().equals(resourceDto.getId())) {
                throw new BusinessException(
                    ErrorCode.RESOURCE_ALREADY_EXISTS,
                    "资源URL已被其他资源使用: " + resourceDto.getUrl()
                );
            }
        }
        
        // 检查URI是否已被其他资源使用（如果提供了URI）
        if (StringUtils.hasText(resourceDto.getUri())) {
            Optional<Resource> resourceWithSameUri = resourceRepository.findByUriAndTenantId(resourceDto.getUri(), tenantId);
            if (resourceWithSameUri.isPresent() && !resourceWithSameUri.get().getId().equals(resourceDto.getId())) {
                throw new BusinessException(
                    ErrorCode.RESOURCE_ALREADY_EXISTS,
                    "资源URI已被其他资源使用: " + resourceDto.getUri()
                );
            }
        }
        
        // 更新字段
        existingResource.setName(resourceDto.getName());
        existingResource.setTitle(resourceDto.getTitle());
        existingResource.setUrl(resourceDto.getUrl());
        
        // 设置 uri 和 method 的默认值
        String uri = resourceDto.getUri();
        if (uri == null || uri.trim().isEmpty()) {
            uri = ""; // 设置默认值为空字符串
        }
        existingResource.setUri(uri);
        
        String method = resourceDto.getMethod();
        if (method == null || method.trim().isEmpty()) {
            method = ""; // 设置默认值为空字符串
        }
        existingResource.setMethod(method);
        
        existingResource.setIcon(resourceDto.getIcon());
        existingResource.setShowIcon(resourceDto.isShowIcon());
        existingResource.setSort(resourceDto.getSort());
        existingResource.setComponent(resourceDto.getComponent());
        existingResource.setRedirect(resourceDto.getRedirect());
        existingResource.setHidden(resourceDto.isHidden());
        existingResource.setKeepAlive(resourceDto.isKeepAlive());
        existingResource.setPermission(resourceDto.getPermission());
        existingResource.setType(ResourceType.fromCode(resourceDto.getType()));
        existingResource.setParentId(resourceDto.getParentId());
        
        return resourceRepository.save(existingResource);
    }

    /**
     * 按资源类型校验必填字段：
     * <ul>
     *   <li>接口(API)：必须填写 uri、method</li>
     *   <li>菜单/目录(MENU/DIRECTORY)：必须填写 url、component</li>
     *   <li>按钮(BUTTON)：无额外必填</li>
     * </ul>
     * 与 DB 层 CHECK 约束一致，避免绕过应用写入导致数据无效。
     */
    private void validateResourceByType(ResourceCreateUpdateDto dto) {
        ResourceType type = ResourceType.fromCode(dto.getType());
        switch (type) {
            case API -> {
                if (!StringUtils.hasText(dto.getUri())) {
                    throw new BusinessException(ErrorCode.VALIDATION_ERROR, "接口类型资源必须填写 API 路径(uri)");
                }
                if (!StringUtils.hasText(dto.getMethod())) {
                    throw new BusinessException(ErrorCode.VALIDATION_ERROR, "接口类型资源必须填写 HTTP 方法(method)");
                }
            }
            case MENU, DIRECTORY -> {
                if (!StringUtils.hasText(dto.getUrl())) {
                    throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                            "菜单/目录类型资源必须填写前端路由路径(url)");
                }
                if (!StringUtils.hasText(dto.getComponent())) {
                    throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                            "菜单/目录类型资源必须填写组件路径(component)");
                }
            }
            default -> { /* BUTTON 等无额外必填 */ }
        }
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Long tenantId = requireTenantId();
        // 检查是否有子资源
        List<Resource> children = resourceRepository.findByParentIdAndTenantIdOrderBySortAsc(id, tenantId);
        if (!children.isEmpty()) {
            throw new BusinessException(
                ErrorCode.RESOURCE_CONFLICT,
                String.format("无法删除有子资源的资源，请先删除子资源。资源ID: %d，子资源数量: %d", id, children.size())
            );
        }

        resourceRepository.findById(id)
                .filter(r -> r.getTenantId().equals(tenantId))
                .ifPresent(resourceRepository::delete);
    }

    @Override
    @Transactional
    public void batchDelete(List<Long> ids) {
        Long tenantId = requireTenantId();
        // 检查是否有资源包含子资源
        for (Long id : ids) {
            List<Resource> children = resourceRepository.findByParentIdAndTenantIdOrderBySortAsc(id, tenantId);
            if (!children.isEmpty()) {
                throw new BusinessException(
                    ErrorCode.RESOURCE_CONFLICT,
                    String.format("无法删除有子资源的资源，请先删除子资源。资源ID: %d，子资源数量: %d", id, children.size())
                );
            }
        }

        List<Resource> resources = resourceRepository.findAllById(ids).stream()
                .filter(r -> r.getTenantId().equals(tenantId))
                .toList();
        resourceRepository.deleteAll(resources);
    }

    @Override
    public List<Resource> findByType(ResourceType type) {
        return resourceRepository.findByTypeOrderBySortAsc(type).stream()
                .filter(r -> r.getTenantId().equals(requireTenantId()))
                .toList();
    }

    @Override
    public List<Resource> findByTypeIn(List<ResourceType> types) {
        return resourceRepository.findByTypeInOrderBySortAsc(types).stream()
                .filter(r -> r.getTenantId().equals(requireTenantId()))
                .toList();
    }

    @Override
    public List<Resource> findByParentId(Long parentId) {
        return resourceRepository.findByParentIdAndTenantIdOrderBySortAsc(parentId, requireTenantId());
    }

    @Override
    public List<Resource> findTopLevel() {
        return resourceRepository.findByParentIdIsNullAndTenantIdOrderBySortAsc(requireTenantId());
    }

    @Override
    public List<ResourceResponseDto> buildResourceTree(List<Resource> resources) {
        // 构建ID到资源DTO的映射
        Map<Long, ResourceResponseDto> resourceMap = new HashMap<>();
        List<ResourceResponseDto> rootResources = new ArrayList<>();
        // 1. 先将所有资源转为DTO并建立映射
        for (Resource resource : resources) {
            ResourceResponseDto dto = toDto(resource);
            // 自动补全 enabled 字段，null 时默认 true
            if (dto.getEnabled() == null) {
                dto.setEnabled(Boolean.TRUE);
            }
            // 初始化 children，避免为 null
            dto.setChildren(new ArrayList<>());
            resourceMap.put(resource.getId(), dto);
        }
        // 2. 构建树形结构
        for (Resource resource : resources) {
            ResourceResponseDto dto = resourceMap.get(resource.getId());
            if (resource.getParentId() == null) {
                // 顶级资源，加入根节点列表
                rootResources.add(dto);
            } else {
                // 子资源，加入父节点的 children
                ResourceResponseDto parent = resourceMap.get(resource.getParentId());
                if (parent != null) {
                    parent.getChildren().add(dto);
                }
            }
        }
        // 3. 递归补全 leaf 字段
        for (ResourceResponseDto root : rootResources) {
            fillLeafField(root);
        }
        return rootResources;
    }

    /**
     * 递归补全 leaf 字段，children 为空则 leaf=true，否则 leaf=false
     */
    private void fillLeafField(ResourceResponseDto node) {
        if (node.getChildren() == null) {
            node.setChildren(new ArrayList<>());
        }
        if (node.getChildren().isEmpty()) {
            node.setLeaf(Boolean.TRUE);
        } else {
            node.setLeaf(Boolean.FALSE);
            for (ResourceResponseDto child : node.getChildren()) {
                fillLeafField(child);
            }
        }
    }

    @Override
    public Optional<Resource> findByName(String name) {
        return resourceRepository.findByNameAndTenantId(name, requireTenantId());
    }

    @Override
    public Optional<Resource> findByUrl(String url) {
        return resourceRepository.findByUrlAndTenantId(url, requireTenantId());
    }

    @Override
    public Optional<Resource> findByUri(String uri) {
        return resourceRepository.findByUriAndTenantId(uri, requireTenantId());
    }

    @Override
    public List<Resource> findByPermission(String permission) {
        return resourceRepository.findByPermissionAndTenantId(permission, requireTenantId());
    }

    @Override
    public boolean existsByName(String name, Long excludeId) {
        Long tenantId = requireTenantId();
        if (excludeId == null) {
            return resourceRepository.findByNameAndTenantId(name, tenantId).isPresent();
        }
        return resourceRepository.findByNameAndTenantId(name, tenantId)
                .filter(r -> !r.getId().equals(excludeId))
                .isPresent();
    }

    @Override
    public boolean existsByUrl(String url, Long excludeId) {
        Long tenantId = requireTenantId();
        if (excludeId == null) {
            return resourceRepository.findByUrlAndTenantId(url, tenantId).isPresent();
        }
        return resourceRepository.findByUrlAndTenantId(url, tenantId)
                .filter(r -> !r.getId().equals(excludeId))
                .isPresent();
    }

    @Override
    public boolean existsByUri(String uri, Long excludeId) {
        Long tenantId = requireTenantId();
        if (excludeId == null) {
            return resourceRepository.findByUriAndTenantId(uri, tenantId).isPresent();
        }
        return resourceRepository.findByUriAndTenantId(uri, tenantId)
                .filter(r -> !r.getId().equals(excludeId))
                .isPresent();
    }

    @Override
    public Resource updateSort(Long id, Integer sort) {
        Long tenantId = requireTenantId();
        Resource resource = resourceRepository.findById(id)
                .filter(r -> r.getTenantId().equals(tenantId))
                .orElseThrow(() -> new RuntimeException("资源不存在"));
        resource.setSort(sort);
        return resourceRepository.save(resource);
    }

    @Override
    public List<ResourceType> getResourceTypes() {
        return Arrays.asList(ResourceType.values());
    }

    @Override
    public List<Resource> findByRoleId(Long roleId) {
        Long tenantId = requireTenantId();
        if (roleId == null) {
            return Collections.emptyList();
        }
        List<Long> resourceIds = roleRepository.findResourceIdsByRoleId(roleId);
        if (resourceIds == null || resourceIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<Resource> resources = resourceRepository.findByIdInAndTenantId(resourceIds, tenantId);
        resources.sort(Comparator.comparing(Resource::getSort, Comparator.nullsLast(Integer::compareTo)));
        return resources;
    }

    @Override
    public List<Resource> findByUserId(Long userId) {
        Long tenantId = requireTenantId();
        return userRepository.findByIdAndTenantId(userId, tenantId)
                .map(user -> {
                    // 收集用户的角色ID
                    Set<Long> roleIds = user.getRoles().stream()
                            .filter(Objects::nonNull)
                            .map(Role::getId)
                            .filter(Objects::nonNull)
                            .collect(Collectors.toSet());
                    if (roleIds.isEmpty()) {
                        return Collections.<Resource>emptyList();
                    }

                    // 通过角色ID查询关联的资源ID
                    Set<Long> resourceIds = new HashSet<>();
                    for (Long roleId : roleIds) {
                        List<Long> ids = roleRepository.findResourceIdsByRoleId(roleId);
                        if (ids != null && !ids.isEmpty()) {
                            resourceIds.addAll(ids);
                        }
                    }
                    if (resourceIds.isEmpty()) {
                        return Collections.<Resource>emptyList();
                    }

                    // 查询资源实体并按 sort 升序返回
                    List<Resource> resources = resourceRepository.findByIdInAndTenantId(new ArrayList<>(resourceIds), tenantId);
                    resources.sort(Comparator.comparing(Resource::getSort, Comparator.nullsLast(Integer::compareTo)));
                    return resources;
                })
                .orElse(Collections.emptyList());
    }

    /**
     * 将Resource实体转换为ResourceResponseDto
     */
    private ResourceResponseDto toDto(Resource resource) {
        ResourceResponseDto dto = new ResourceResponseDto();
        dto.setId(resource.getId());
        dto.setName(resource.getName());
        dto.setTitle(resource.getTitle());
        dto.setUrl(resource.getUrl());
        dto.setUri(resource.getUri());
        dto.setMethod(resource.getMethod());
        dto.setIcon(resource.getIcon());
        dto.setShowIcon(resource.getShowIcon());
        dto.setSort(resource.getSort());
        dto.setComponent(resource.getComponent());
        dto.setRedirect(resource.getRedirect());
        dto.setHidden(resource.getHidden());
        dto.setKeepAlive(resource.getKeepAlive());
        dto.setPermission(resource.getPermission());
        dto.setType(resource.getType().getCode());
        dto.setTypeName(getTypeName(resource.getType()));
        dto.setParentId(resource.getParentId());
        dto.setChildren(new ArrayList<>());
        return dto;
    }

    /**
     * 获取资源类型名称
     */
    private String getTypeName(ResourceType type) {
        switch (type) {
            case MENU:
                return "菜单";
            case BUTTON:
                return "按钮";
            case API:
                return "API";
            default:
                return "未知";
        }
    }

    private Long requireTenantId() {
        Long tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            throw new BusinessException(ErrorCode.MISSING_PARAMETER, "缺少租户信息");
        }
        return tenantId;
    }
} 
