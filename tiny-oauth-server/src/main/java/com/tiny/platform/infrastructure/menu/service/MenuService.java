package com.tiny.platform.infrastructure.menu.service;

import com.tiny.platform.infrastructure.auth.resource.domain.Resource;
import com.tiny.platform.infrastructure.auth.resource.dto.ResourceCreateUpdateDto;
import com.tiny.platform.infrastructure.auth.resource.dto.ResourceRequestDto;
import com.tiny.platform.infrastructure.auth.resource.dto.ResourceResponseDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

/**
 * 菜单业务接口，专注于 type=0/1 的菜单和目录
 */
public interface MenuService {
    /**
     * 分页查询菜单（type=0/1）
     * @param query 查询条件
     * @param pageable 分页参数
     * @return 菜单分页结果
     */
    Page<ResourceResponseDto> menus(ResourceRequestDto query, Pageable pageable);

    /**
     * 获取菜单树结构（type=0/1）
     * @return 菜单树
     */
    List<ResourceResponseDto> menuTree();

    /**
     * 获取完整菜单树（包含隐藏/禁用/空目录）
     * @return 菜单树
     */
    List<ResourceResponseDto> menuTreeAll();

    /**
     * 根据父级ID查询子菜单（type=0/1）
     * @param parentId 父级ID，null或0表示顶级菜单
     * @return 子菜单列表
     */
    List<ResourceResponseDto> getMenusByParentId(Long parentId);

    /**
     * 创建菜单
     * @param resourceDto 菜单创建DTO
     * @return 创建的菜单
     */
    Resource createMenu(ResourceCreateUpdateDto resourceDto);

    /**
     * 更新菜单
     * @param resourceDto 菜单更新DTO
     * @return 更新后的菜单
     */
    Resource updateMenu(ResourceCreateUpdateDto resourceDto);

    /**
     * 删除菜单
     * @param id 菜单ID
     */
    void deleteMenu(Long id);

    /**
     * 批量删除菜单
     * @param ids 菜单ID列表
     */
    void batchDeleteMenus(List<Long> ids);

    /**
     * 按条件查询菜单（type=0/1），返回list结构
     */
    List<ResourceResponseDto> list(ResourceRequestDto query);
}
