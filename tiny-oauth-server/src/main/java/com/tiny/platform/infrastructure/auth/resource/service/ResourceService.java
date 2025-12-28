package com.tiny.platform.infrastructure.auth.resource.service;

import com.tiny.platform.infrastructure.auth.resource.domain.Resource;
import com.tiny.platform.infrastructure.auth.resource.dto.ResourceCreateUpdateDto;
import com.tiny.platform.infrastructure.auth.resource.dto.ResourceRequestDto;
import com.tiny.platform.infrastructure.auth.resource.dto.ResourceResponseDto;
import com.tiny.platform.infrastructure.auth.resource.enums.ResourceType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

/**
 * 资源服务接口
 * 定义资源管理的业务方法
 */
public interface ResourceService {
    
    /**
     * 分页查询资源
     * @param query 查询条件
     * @param pageable 分页参数
     * @return 分页结果
     */
    Page<ResourceResponseDto> resources(ResourceRequestDto query, Pageable pageable);
    
    /**
     * 根据ID查找资源
     * @param id 资源ID
     * @return 资源对象
     */
    Optional<Resource> findById(Long id);
    
    /**
     * 创建资源
     * @param resource 资源对象
     * @return 创建的资源
     */
    Resource create(Resource resource);
    
    /**
     * 更新资源
     * @param id 资源ID
     * @param resource 资源对象
     * @return 更新后的资源
     */
    Resource update(Long id, Resource resource);
    
    /**
     * 从DTO创建资源
     * @param resourceDto 资源DTO
     * @return 创建的资源
     */
    Resource createFromDto(ResourceCreateUpdateDto resourceDto);
    
    /**
     * 从DTO更新资源
     * @param resourceDto 资源DTO
     * @return 更新后的资源
     */
    Resource updateFromDto(ResourceCreateUpdateDto resourceDto);
    
    /**
     * 删除资源
     * @param id 资源ID
     */
    void delete(Long id);
    
    /**
     * 批量删除资源
     * @param ids 资源ID列表
     */
    void batchDelete(List<Long> ids);
    
    /**
     * 根据资源类型查找资源
     * @param type 资源类型
     * @return 资源列表
     */
    List<Resource> findByType(ResourceType type);
    
    /**
     * 根据多个资源类型查找资源
     * @param types 资源类型列表
     * @return 资源列表
     */
    List<Resource> findByTypeIn(List<ResourceType> types);
    
    /**
     * 根据父级ID查找子资源
     * @param parentId 父级资源ID
     * @return 子资源列表
     */
    List<Resource> findByParentId(Long parentId);
    
    /**
     * 查找顶级资源
     * @return 顶级资源列表
     */
    List<Resource> findTopLevel();
    
    /**
     * 构建资源树结构
     * @param resources 资源列表
     * @return 树形结构的资源列表
     */
    List<ResourceResponseDto> buildResourceTree(List<Resource> resources);
    
    /**
     * 根据名称查找资源
     * @param name 资源名称
     * @return 资源对象
     */
    Optional<Resource> findByName(String name);
    
    /**
     * 根据URL查找资源
     * @param url 前端路径
     * @return 资源对象
     */
    Optional<Resource> findByUrl(String url);
    
    /**
     * 根据URI查找资源
     * @param uri 后端API路径
     * @return 资源对象
     */
    Optional<Resource> findByUri(String uri);
    
    /**
     * 根据权限标识查找资源
     * @param permission 权限标识
     * @return 资源列表
     */
    List<Resource> findByPermission(String permission);
    
    /**
     * 检查资源名称是否存在
     * @param name 资源名称
     * @param excludeId 要排除的资源ID
     * @return 是否存在
     */
    boolean existsByName(String name, Long excludeId);
    
    /**
     * 检查资源URL是否存在
     * @param url 前端路径
     * @param excludeId 要排除的资源ID
     * @return 是否存在
     */
    boolean existsByUrl(String url, Long excludeId);
    
    /**
     * 检查资源URI是否存在
     * @param uri 后端API路径
     * @param excludeId 要排除的资源ID
     * @return 是否存在
     */
    boolean existsByUri(String uri, Long excludeId);
    
    /**
     * 更新资源排序
     * @param id 资源ID
     * @param sort 新的排序值
     * @return 更新后的资源
     */
    Resource updateSort(Long id, Integer sort);
    
    /**
     * 获取资源类型列表
     * @return 资源类型列表
     */
    List<ResourceType> getResourceTypes();
    
    /**
     * 根据角色ID查找资源
     * @param roleId 角色ID
     * @return 资源列表
     */
    List<Resource> findByRoleId(Long roleId);
    
    /**
     * 根据用户ID查找资源
     * @param userId 用户ID
     * @return 资源列表
     */
    List<Resource> findByUserId(Long userId);
} 