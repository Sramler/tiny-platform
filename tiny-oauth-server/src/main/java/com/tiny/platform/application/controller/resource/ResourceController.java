package com.tiny.platform.application.controller.resource;

import com.tiny.platform.infrastructure.auth.resource.domain.Resource;
import com.tiny.platform.infrastructure.auth.resource.enums.ResourceType;
import com.tiny.platform.infrastructure.auth.resource.dto.ResourceCreateUpdateDto;
import com.tiny.platform.infrastructure.auth.resource.dto.ResourceRequestDto;
import com.tiny.platform.infrastructure.auth.resource.dto.ResourceResponseDto;
import com.tiny.platform.infrastructure.auth.resource.service.ResourceService;
import com.tiny.platform.infrastructure.core.dto.PageResponse;
import com.tiny.platform.infrastructure.idempotent.sdk.annotation.Idempotent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
    @PreAuthorize("@resourceManagementAccessGuard.canRead(authentication)")
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
    @PreAuthorize("@resourceManagementAccessGuard.canRead(authentication)")
    public ResponseEntity<ResourceResponseDto> getResource(@PathVariable("id") Long id) {
        return resourceService.findDetailById(id)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    /**
     * 创建资源
     * @param resourceDto 资源创建DTO
     * @return 创建的资源
     */
    @PostMapping
    @Idempotent(key = "#request.getHeader('X-Idempotency-Key')", failOpen = false)
    @PreAuthorize("@resourceManagementAccessGuard.canCreate(authentication)")
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
    @Idempotent(key = "#request.getHeader('X-Idempotency-Key')", failOpen = false)
    @PreAuthorize("@resourceManagementAccessGuard.canUpdate(authentication)")
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
    @Idempotent(key = "#request.getHeader('X-Idempotency-Key')", failOpen = false)
    @PreAuthorize("@resourceManagementAccessGuard.canDelete(authentication)")
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
    @Idempotent(key = "#request.getHeader('X-Idempotency-Key')", failOpen = false)
    @PreAuthorize("@resourceManagementAccessGuard.canDelete(authentication)")
    public ResponseEntity<Map<String, Object>> batchDelete(@RequestBody List<Long> ids) {
        resourceService.batchDelete(ids);
        return ResponseEntity.ok(Map.of("success", true, "message", "批量删除成功"));
    }

    // ==================== 通用资源API ====================
    
    /**
     * 根据资源类型获取资源列表
     * @param type 资源类型
     * @return 资源列表
     */
    @GetMapping("/type/{type}")
    @PreAuthorize("@resourceManagementAccessGuard.canRead(authentication)")
    public ResponseEntity<List<ResourceResponseDto>> getResourcesByType(@PathVariable("type") Integer type) {
        ResourceType resourceType = ResourceType.fromCode(type);
        List<ResourceResponseDto> resources = resourceService.findDtosByType(resourceType);
        return ResponseEntity.ok(resources);
    }
    
    /**
     * 根据父级ID获取子资源列表
     * @param parentId 父级资源ID
     * @return 子资源列表
     */
    @GetMapping("/parent/{parentId}")
    @PreAuthorize("@resourceManagementAccessGuard.canRead(authentication)")
    public ResponseEntity<List<ResourceResponseDto>> getResourcesByParentId(@PathVariable("parentId") Long parentId) {
        List<ResourceResponseDto> resources = resourceService.findChildDtos(parentId);
        return ResponseEntity.ok(resources);
    }
    
    /**
     * 获取顶级资源列表
     * @return 顶级资源列表
     */
    @GetMapping("/top-level")
    @PreAuthorize("@resourceManagementAccessGuard.canRead(authentication)")
    public ResponseEntity<List<ResourceResponseDto>> getTopLevelResources() {
        List<ResourceResponseDto> resources = resourceService.findTopLevelDtos();
        return ResponseEntity.ok(resources);
    }
    
    /**
     * 获取资源树结构
     * @return 资源树
     */
    @GetMapping("/tree")
    @PreAuthorize("@resourceManagementAccessGuard.canRead(authentication)")
    public ResponseEntity<List<ResourceResponseDto>> getResourceTree() {
        return ResponseEntity.ok(resourceService.findResourceTreeDtos());
    }

    /**
     * 获取当前用户在指定页面下可见的运行时按钮载体。
     * <p>迁移期由前端管理页消费，用于替代直接基于权限码的按钮显隐判断。</p>
     */
    @GetMapping("/runtime/ui-actions")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<ResourceResponseDto>> getRuntimeUiActions(@RequestParam("pagePath") String pagePath) {
        return ResponseEntity.ok(resourceService.findAllowedUiActionDtos(pagePath));
    }

    /**
     * 判断当前用户是否可访问指定 API 载体。
     * <p>迁移期供统一路由守卫和调试用途消费。</p>
     */
    @GetMapping("/runtime/api-access")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, Boolean>> getRuntimeApiAccess(
            @RequestParam("method") String method,
            @RequestParam("uri") String uri) {
        return ResponseEntity.ok(Map.of("allowed", resourceService.canAccessApiEndpoint(method, uri)));
    }

    /**
     * 更新资源排序
     * @param id 资源ID
     * @param sort 新的排序值
     * @return 更新后的资源
     */
    @PutMapping("/{id}/sort")
    @Idempotent(key = "#request.getHeader('X-Idempotency-Key')", failOpen = false)
    @PreAuthorize("@resourceManagementAccessGuard.canUpdate(authentication)")
    public ResponseEntity<Resource> updateSort(@PathVariable("id") Long id, @RequestParam("sort") Integer sort) {
        Resource resource = resourceService.updateSort(id, sort);
        return ResponseEntity.ok(resource);
    }
    
    /**
     * 获取资源类型列表
     * @return 资源类型列表
     */
    @GetMapping("/types")
    @PreAuthorize("@resourceManagementAccessGuard.canRead(authentication)")
    public ResponseEntity<List<ResourceType>> getResourceTypes() {
        List<ResourceType> types = resourceService.getResourceTypes();
        return ResponseEntity.ok(types);
    }
    
    /**
     * 检查资源名称是否存在
     * @param name 资源名称
     * @param excludeId 要排除的资源ID
     * @return 是否存在
     */
    @GetMapping("/check-name")
    @PreAuthorize("@resourceManagementAccessGuard.canRead(authentication)")
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
    @PreAuthorize("@resourceManagementAccessGuard.canRead(authentication)")
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
    @PreAuthorize("@resourceManagementAccessGuard.canRead(authentication)")
    public ResponseEntity<Map<String, Boolean>> checkUriExists(
            @RequestParam("uri") String uri,
            @RequestParam(value = "excludeId", required = false) Long excludeId) {
        boolean exists = resourceService.existsByUri(uri, excludeId);
        return ResponseEntity.ok(Map.of("exists", exists));
    }
    
}
