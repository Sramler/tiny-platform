package com.tiny.platform.application.controller.resource;

import com.tiny.platform.infrastructure.auth.resource.domain.Resource;
import com.tiny.platform.infrastructure.auth.resource.enums.ResourceType;
import com.tiny.platform.infrastructure.auth.resource.dto.ResourceCreateUpdateDto;
import com.tiny.platform.infrastructure.auth.resource.dto.ResourceRequestDto;
import com.tiny.platform.infrastructure.auth.resource.dto.ResourceResponseDto;
import com.tiny.platform.infrastructure.auth.resource.dto.ResourceSortDto;
import com.tiny.platform.infrastructure.auth.resource.service.ResourceService;
import com.tiny.platform.infrastructure.core.dto.PageResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;

/**
 * 资源管理控制器
 * 提供资源管理和菜单管理的REST API接口
 */
@RestController
@RequestMapping("/sys/resources")
public class ResourceController {
    
    private final ResourceService resourceService;
    
    public ResourceController(ResourceService resourceService) {
        this.resourceService = resourceService;
    }

    // ==================== 资源管理API ====================

    /**
     * 分页查询资源
     * @param query 查询条件
     * @param pageable 分页参数
     * @return 分页结果
     */
    @GetMapping
    public ResponseEntity<PageResponse<ResourceResponseDto>> getResources(
            @Valid ResourceRequestDto query,
            @PageableDefault(size = 10, sort = "sort", direction = Sort.Direction.ASC) Pageable pageable
    ) {
        return ResponseEntity.ok(new PageResponse<>(resourceService.resources(query, pageable)));
    }

    /**
     * 根据ID获取资源详情
     * @param id 资源ID
     * @return 资源详情
     */
    @GetMapping("/{id}")
    public ResponseEntity<Resource> getResource(@PathVariable("id") Long id) {
        return resourceService.findById(id)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    /**
     * 创建资源
     * @param resourceDto 资源创建DTO
     * @return 创建的资源
     */
    @PostMapping
    public ResponseEntity<Resource> create(@Valid @RequestBody ResourceCreateUpdateDto resourceDto) {
        Resource resource = resourceService.createFromDto(resourceDto);
        return ResponseEntity.ok(resource);
    }

    /**
     * 更新资源
     * @param id 资源ID
     * @param resourceDto 资源更新DTO
     * @return 更新后的资源
     */
    @PutMapping("/{id}")
    public ResponseEntity<Resource> update(@PathVariable("id") Long id, @Valid @RequestBody ResourceCreateUpdateDto resourceDto) {
        resourceDto.setId(id);
        Resource resource = resourceService.updateFromDto(resourceDto);
        return ResponseEntity.ok(resource);
    }

    /**
     * 删除资源
     * @param id 资源ID
     * @return 删除结果
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") Long id) {
        resourceService.delete(id);
        return ResponseEntity.noContent().build();
    }
    
    /**
     * 批量删除资源
     * @param ids 资源ID列表
     * @return 删除结果
     */
    @PostMapping("/batch/delete")
    public ResponseEntity<Map<String, Object>> batchDelete(@RequestBody List<Long> ids) {
        resourceService.batchDelete(ids);
        return ResponseEntity.ok(Map.of("success", true, "message", "批量删除成功"));
    }

    // ==================== 菜单管理API ====================

    /**
     * 分页查询菜单（type为0-目录，1-菜单）
     * @param name 菜单名称
     * @param title 菜单标题
     * @param permission 权限标识
     * @param pageable 分页参数
     * @return 分页结果
     */
    @GetMapping("/menus")
    public ResponseEntity<PageResponse<ResourceResponseDto>> getMenus(
            @RequestParam(value = "name", required = false) String name,
            @RequestParam(value = "title", required = false) String title,
            @RequestParam(value = "permission", required = false) String permission,
            @PageableDefault(size = 10, sort = "sort", direction = Sort.Direction.ASC) Pageable pageable
    ) {
        ResourceRequestDto query = new ResourceRequestDto();
        query.setName(name);
        query.setTitle(title);
        query.setPermission(permission);
        query.setType(ResourceType.MENU.getCode()); // 只查询菜单类型
        
        return ResponseEntity.ok(new PageResponse<>(resourceService.resources(query, pageable)));
    }

    /**
     * 获取菜单树结构（type为0-目录，1-菜单）
     * @return 菜单树
     */
    @GetMapping("/menus/tree")
    public ResponseEntity<List<ResourceResponseDto>> getMenuTree() {
        List<Resource> menuResources = resourceService.findByTypeIn(List.of(ResourceType.DIRECTORY, ResourceType.MENU));
        List<ResourceResponseDto> tree = resourceService.buildResourceTree(menuResources);
        return ResponseEntity.ok(tree);
    }

    /**
     * 创建菜单
     * @param resourceDto 菜单创建DTO
     * @return 创建的菜单
     */
    @PostMapping("/menus")
    public ResponseEntity<Resource> createMenu(@Valid @RequestBody ResourceCreateUpdateDto resourceDto) {
        // 确保类型为目录或菜单
        if (resourceDto.getType() == null || (resourceDto.getType() != ResourceType.DIRECTORY.getCode() && resourceDto.getType() != ResourceType.MENU.getCode())) {
            resourceDto.setType(ResourceType.MENU.getCode());
        }
        
        Resource resource = resourceService.createFromDto(resourceDto);
        return ResponseEntity.ok(resource);
    }

    /**
     * 更新菜单
     * @param id 菜单ID
     * @param resourceDto 菜单更新DTO
     * @return 更新后的菜单
     */
    @PutMapping("/menus/{id}")
    public ResponseEntity<Resource> updateMenu(@PathVariable("id") Long id, @Valid @RequestBody ResourceCreateUpdateDto resourceDto) {
        // 设置ID
        resourceDto.setId(id);
        
        // 确保类型为目录或菜单
        if (resourceDto.getType() == null || (resourceDto.getType() != ResourceType.DIRECTORY.getCode() && resourceDto.getType() != ResourceType.MENU.getCode())) {
            resourceDto.setType(ResourceType.MENU.getCode());
        }
        
        Resource resource = resourceService.updateFromDto(resourceDto);
        return ResponseEntity.ok(resource);
    }

    /**
     * 删除菜单
     * @param id 菜单ID
     * @return 删除结果
     */
    @DeleteMapping("/menus/{id}")
    public ResponseEntity<Void> deleteMenu(@PathVariable("id") Long id) {
        resourceService.delete(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * 批量删除菜单
     * @param ids 菜单ID列表
     * @return 删除结果
     */
    @PostMapping("/menus/batch/delete")
    public ResponseEntity<Map<String, Object>> batchDeleteMenus(@RequestBody List<Long> ids) {
        resourceService.batchDelete(ids);
        return ResponseEntity.ok(Map.of("success", true, "message", "批量删除成功"));
    }

    /**
     * 更新菜单排序
     * @param id 菜单ID
     * @param sort 新的排序值
     * @return 更新后的菜单
     */
    @PutMapping("/menus/{id}/sort")
    public ResponseEntity<Resource> updateMenuSort(@PathVariable("id") Long id, @RequestParam("sort") Integer sort) {
        Resource resource = resourceService.updateSort(id, sort);
        return ResponseEntity.ok(resource);
    }

    // ==================== 通用资源API ====================
    
    /**
     * 根据资源类型获取资源列表
     * @param type 资源类型
     * @return 资源列表
     */
    @GetMapping("/type/{type}")
    public ResponseEntity<List<Resource>> getResourcesByType(@PathVariable("type") Integer type) {
        ResourceType resourceType = ResourceType.fromCode(type);
        List<Resource> resources = resourceService.findByType(resourceType);
        return ResponseEntity.ok(resources);
    }
    
    /**
     * 根据父级ID获取子资源列表
     * @param parentId 父级资源ID
     * @return 子资源列表
     */
    @GetMapping("/parent/{parentId}")
    public ResponseEntity<List<Resource>> getResourcesByParentId(@PathVariable("parentId") Long parentId) {
        List<Resource> resources = resourceService.findByParentId(parentId);
        return ResponseEntity.ok(resources);
    }
    
    /**
     * 获取顶级资源列表
     * @return 顶级资源列表
     */
    @GetMapping("/top-level")
    public ResponseEntity<List<Resource>> getTopLevelResources() {
        List<Resource> resources = resourceService.findTopLevel();
        return ResponseEntity.ok(resources);
    }
    
    /**
     * 获取资源树结构
     * @return 资源树
     */
    @GetMapping("/tree")
    public ResponseEntity<List<ResourceResponseDto>> getResourceTree() {
        List<Resource> allResources = resourceService.findTopLevel();
        // 递归获取所有子资源
        List<Resource> allResourcesWithChildren = getAllResourcesRecursively(allResources);
        List<ResourceResponseDto> tree = resourceService.buildResourceTree(allResourcesWithChildren);
        return ResponseEntity.ok(tree);
    }
    
    /**
     * 更新资源排序
     * @param id 资源ID
     * @param sort 新的排序值
     * @return 更新后的资源
     */
    @PutMapping("/{id}/sort")
    public ResponseEntity<Resource> updateSort(@PathVariable("id") Long id, @RequestParam("sort") Integer sort) {
        Resource resource = resourceService.updateSort(id, sort);
        return ResponseEntity.ok(resource);
    }
    
    /**
     * 获取资源类型列表
     * @return 资源类型列表
     */
    @GetMapping("/types")
    public ResponseEntity<List<ResourceType>> getResourceTypes() {
        List<ResourceType> types = resourceService.getResourceTypes();
        return ResponseEntity.ok(types);
    }
    
    /**
     * 根据权限标识获取资源列表
     * @param permission 权限标识
     * @return 资源列表
     */
    @GetMapping("/permission/{permission}")
    public ResponseEntity<List<Resource>> getResourcesByPermission(@PathVariable("permission") String permission) {
        List<Resource> resources = resourceService.findByPermission(permission);
        return ResponseEntity.ok(resources);
    }
    
    /**
     * 检查资源名称是否存在
     * @param name 资源名称
     * @param excludeId 要排除的资源ID
     * @return 是否存在
     */
    @GetMapping("/check-name")
    public ResponseEntity<Map<String, Boolean>> checkNameExists(
            @RequestParam("name") String name,
            @RequestParam(value = "excludeId", required = false) Long excludeId) {
        boolean exists = resourceService.existsByName(name, excludeId);
        return ResponseEntity.ok(Map.of("exists", exists));
    }
    
    /**
     * 检查资源URL是否存在
     * @param url 资源URL
     * @param excludeId 要排除的资源ID
     * @return 是否存在
     */
    @GetMapping("/check-url")
    public ResponseEntity<Map<String, Boolean>> checkUrlExists(
            @RequestParam("url") String url,
            @RequestParam(value = "excludeId", required = false) Long excludeId) {
        boolean exists = resourceService.existsByUrl(url, excludeId);
        return ResponseEntity.ok(Map.of("exists", exists));
    }
    
    /**
     * 检查资源URI是否存在
     * @param uri 资源URI
     * @param excludeId 要排除的资源ID
     * @return 是否存在
     */
    @GetMapping("/check-uri")
    public ResponseEntity<Map<String, Boolean>> checkUriExists(
            @RequestParam("uri") String uri,
            @RequestParam(value = "excludeId", required = false) Long excludeId) {
        boolean exists = resourceService.existsByUri(uri, excludeId);
        return ResponseEntity.ok(Map.of("exists", exists));
    }
    
    /**
     * 递归获取所有子资源
     * @param resources 资源列表
     * @return 包含子资源的完整列表
     */
    private List<Resource> getAllResourcesRecursively(List<Resource> resources) {
        List<Resource> allResources = new java.util.ArrayList<>(resources);
        for (Resource resource : resources) {
            List<Resource> children = resourceService.findByParentId(resource.getId());
            if (!children.isEmpty()) {
                allResources.addAll(getAllResourcesRecursively(children));
            }
        }
        return allResources;
    }
} 