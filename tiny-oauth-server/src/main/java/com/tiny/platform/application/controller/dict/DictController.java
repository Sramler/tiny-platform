package com.tiny.platform.application.controller.dict;

import com.tiny.platform.core.dict.dto.*;
import com.tiny.platform.core.dict.model.DictItem;
import com.tiny.platform.core.dict.model.DictType;
import com.tiny.platform.core.dict.service.DictItemService;
import com.tiny.platform.core.dict.service.DictTypeService;
import com.tiny.platform.infrastructure.core.dto.PageResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 字典管理控制器
 */
@RestController
@RequestMapping("/dict")
public class DictController {

    private final DictTypeService dictTypeService;
    private final DictItemService dictItemService;

    public DictController(DictTypeService dictTypeService, DictItemService dictItemService) {
        this.dictTypeService = dictTypeService;
        this.dictItemService = dictItemService;
    }

    // ==================== 字典类型管理 ====================

    /**
     * 分页查询字典类型
     */
    @GetMapping("/types")
    public ResponseEntity<PageResponse<DictTypeResponseDto>> getDictTypes(
            @Valid DictTypeQueryDto query,
            @PageableDefault(size = 10, sort = "sortOrder", direction = Sort.Direction.ASC) Pageable pageable
    ) {
        Page<DictTypeResponseDto> page = dictTypeService.query(query, pageable);
        return ResponseEntity.ok(new PageResponse<>(page));
    }

    /**
     * 根据ID获取字典类型详情
     */
    @GetMapping("/types/{id}")
    public ResponseEntity<DictType> getDictType(@PathVariable("id") Long id) {
        return dictTypeService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * 根据字典编码获取字典类型
     */
    @GetMapping("/types/code/{dictCode}")
    public ResponseEntity<DictType> getDictTypeByCode(@PathVariable("dictCode") String dictCode) {
        return dictTypeService.findByDictCode(dictCode)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * 创建字典类型
     */
    @PostMapping("/types")
    public ResponseEntity<DictType> createDictType(@Valid @RequestBody DictTypeCreateUpdateDto dto) {
        DictType dictType = dictTypeService.create(dto);
        return ResponseEntity.ok(dictType);
    }

    /**
     * 更新字典类型
     */
    @PutMapping("/types/{id}")
    public ResponseEntity<DictType> updateDictType(
            @PathVariable("id") Long id,
            @Valid @RequestBody DictTypeCreateUpdateDto dto
    ) {
        DictType dictType = dictTypeService.update(id, dto);
        return ResponseEntity.ok(dictType);
    }

    /**
     * 删除字典类型
     */
    @DeleteMapping("/types/{id}")
    public ResponseEntity<Void> deleteDictType(@PathVariable("id") Long id) {
        dictTypeService.delete(id);
        return ResponseEntity.ok().build();
    }

    /**
     * 批量删除字典类型
     */
    @PostMapping("/types/batch/delete")
    public ResponseEntity<Void> batchDeleteDictTypes(@RequestBody List<Long> ids) {
        dictTypeService.batchDelete(ids);
        return ResponseEntity.ok().build();
    }

    /**
     * 根据租户ID获取字典类型列表
     */
    @GetMapping("/types/tenant/{tenantId}")
    public ResponseEntity<List<DictType>> getDictTypesByTenant(@PathVariable("tenantId") Long tenantId) {
        List<DictType> dictTypes = dictTypeService.findByTenantId(tenantId);
        return ResponseEntity.ok(dictTypes);
    }

    // ==================== 字典项管理 ====================

    /**
     * 分页查询字典项
     */
    @GetMapping("/items")
    public ResponseEntity<PageResponse<DictItemResponseDto>> getDictItems(
            @Valid DictItemQueryDto query,
            @PageableDefault(size = 10, sort = "sortOrder", direction = Sort.Direction.ASC) Pageable pageable
    ) {
        Page<DictItemResponseDto> page = dictItemService.query(query, pageable);
        return ResponseEntity.ok(new PageResponse<>(page));
    }

    /**
     * 根据ID获取字典项详情
     */
    @GetMapping("/items/{id}")
    public ResponseEntity<DictItem> getDictItem(@PathVariable("id") Long id) {
        return dictItemService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * 根据字典类型ID获取字典项列表
     */
    @GetMapping("/items/type/{dictTypeId}")
    public ResponseEntity<List<DictItem>> getDictItemsByType(@PathVariable("dictTypeId") Long dictTypeId) {
        List<DictItem> items = dictItemService.findByDictTypeId(dictTypeId);
        return ResponseEntity.ok(items);
    }

    /**
     * 根据字典编码获取字典项列表（支持多租户）
     */
    @GetMapping("/items/code/{dictCode}")
    public ResponseEntity<List<DictItem>> getDictItemsByCode(
            @PathVariable("dictCode") String dictCode,
            @RequestParam(value = "tenantId", required = false, defaultValue = "0") Long tenantId
    ) {
        List<DictItem> items = dictItemService.findByDictCode(dictCode, tenantId);
        return ResponseEntity.ok(items);
    }

    /**
     * 根据字典编码获取字典映射（value -> label）
     */
    @GetMapping("/items/map/{dictCode}")
    public ResponseEntity<Map<String, String>> getDictMap(
            @PathVariable("dictCode") String dictCode,
            @RequestParam(value = "tenantId", required = false, defaultValue = "0") Long tenantId
    ) {
        Map<String, String> map = dictItemService.getDictMap(dictCode, tenantId);
        return ResponseEntity.ok(map);
    }

    /**
     * 根据字典编码和值获取标签
     */
    @GetMapping("/items/label/{dictCode}/{value}")
    public ResponseEntity<String> getLabel(
            @PathVariable("dictCode") String dictCode,
            @PathVariable("value") String value,
            @RequestParam(value = "tenantId", required = false, defaultValue = "0") Long tenantId
    ) {
        String label = dictItemService.getLabel(dictCode, value, tenantId);
        return ResponseEntity.ok(label);
    }

    /**
     * 创建字典项
     */
    @PostMapping("/items")
    public ResponseEntity<DictItem> createDictItem(@Valid @RequestBody DictItemCreateUpdateDto dto) {
        DictItem dictItem = dictItemService.create(dto);
        return ResponseEntity.ok(dictItem);
    }

    /**
     * 更新字典项
     */
    @PutMapping("/items/{id}")
    public ResponseEntity<DictItem> updateDictItem(
            @PathVariable("id") Long id,
            @Valid @RequestBody DictItemCreateUpdateDto dto
    ) {
        DictItem dictItem = dictItemService.update(id, dto);
        return ResponseEntity.ok(dictItem);
    }

    /**
     * 删除字典项
     */
    @DeleteMapping("/items/{id}")
    public ResponseEntity<Void> deleteDictItem(@PathVariable("id") Long id) {
        dictItemService.delete(id);
        return ResponseEntity.ok().build();
    }

    /**
     * 批量删除字典项
     */
    @PostMapping("/items/batch/delete")
    public ResponseEntity<Void> batchDeleteDictItems(@RequestBody List<Long> ids) {
        dictItemService.batchDelete(ids);
        return ResponseEntity.ok().build();
    }
}

