package com.tiny.platform.application.controller.menu;

import com.tiny.platform.infrastructure.auth.resource.domain.Resource;
import com.tiny.platform.infrastructure.auth.resource.dto.ResourceCreateUpdateDto;
import com.tiny.platform.infrastructure.auth.resource.dto.ResourceRequestDto;
import com.tiny.platform.infrastructure.auth.resource.dto.ResourceResponseDto;
import com.tiny.platform.infrastructure.menu.service.MenuService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;

/**
 * 菜单管理控制器
 * 只处理 type=0/1 的菜单和目录，底层共用 ResourceRepository
 */
@RestController
@RequestMapping("/sys/menus")
public class MenuController {
    private final MenuService menuService;

    public MenuController(MenuService menuService) {
        this.menuService = menuService;
    }

    /**
     * 查询菜单（type为0-目录，1-菜单），返回list结构
     */
    @GetMapping
    public ResponseEntity<List<ResourceResponseDto>> getMenus(
            @Valid ResourceRequestDto query
    ) {
        return ResponseEntity.ok(menuService.list(query));
    }

    /**
     * 获取菜单树结构（type为0-目录，1-菜单）
     */
    @GetMapping("/tree")
    public ResponseEntity<List<ResourceResponseDto>> getMenuTree() {
        return ResponseEntity.ok(menuService.menuTree());
    }

    /**
     * 获取完整菜单树（包含隐藏/禁用/空目录），供后台授权使用
     */
    @GetMapping("/tree/all")
    public ResponseEntity<List<ResourceResponseDto>> getFullMenuTree() {
        return ResponseEntity.ok(menuService.menuTreeAll());
    }

    /**
     * 根据父级ID获取子菜单（type为0-目录，1-菜单）
     */
    @GetMapping("/parent/{parentId}")
    public ResponseEntity<List<ResourceResponseDto>> getMenusByParentId(@PathVariable("parentId") Long parentId) {
        return ResponseEntity.ok(menuService.getMenusByParentId(parentId));
    }

    /**
     * 创建菜单
     */
    @PostMapping
    public ResponseEntity<Resource> createMenu(@Valid @RequestBody ResourceCreateUpdateDto resourceDto) {
        return ResponseEntity.ok(menuService.createMenu(resourceDto));
    }

    /**
     * 更新菜单
     */
    @PutMapping("/{id}")
    public ResponseEntity<?> updateMenu(@PathVariable("id") Long id, @Valid @RequestBody ResourceCreateUpdateDto resourceDto) {
        resourceDto.setId(id);
        menuService.updateMenu(resourceDto);
        return ResponseEntity.noContent().build();
    }

    /**
     * 删除菜单
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteMenu(@PathVariable("id") Long id) {
        menuService.deleteMenu(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * 批量删除菜单
     */
    @PostMapping("/batch/delete")
    public ResponseEntity<Map<String, Object>> batchDeleteMenus(@RequestBody List<Long> ids) {
        menuService.batchDeleteMenus(ids);
        return ResponseEntity.ok(Map.of("success", true, "message", "批量删除成功"));
    }
} 