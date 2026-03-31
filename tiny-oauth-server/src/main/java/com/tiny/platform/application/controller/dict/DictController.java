package com.tiny.platform.application.controller.dict;

import com.tiny.platform.core.dict.dto.*;
import com.tiny.platform.core.dict.model.DictItem;
import com.tiny.platform.core.dict.model.DictType;
import com.tiny.platform.core.dict.service.DictItemService;
import com.tiny.platform.core.dict.service.DictTypeService;
import com.tiny.platform.infrastructure.core.dto.PageResponse;
import com.tiny.platform.infrastructure.idempotent.sdk.annotation.Idempotent;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
    @PreAuthorize("@dictManagementAccessGuard.canReadType(authentication)")
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
    @PreAuthorize("@dictManagementAccessGuard.canReadType(authentication)")
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
    @PreAuthorize("@dictManagementAccessGuard.canCreateType(authentication)")
    @Idempotent(key = "#request.getHeader('X-Idempotency-Key')", failOpen = false)
    public ResponseEntity<DictType> createDictType(@Valid @RequestBody DictTypeCreateUpdateDto dto) {
        DictType dictType = dictTypeService.create(dto);
        return ResponseEntity.ok(dictType);
    }

    /**
     * 更新字典类型
     */
    @PutMapping("/types/{id}")
    @PreAuthorize("@dictManagementAccessGuard.canUpdateType(authentication)")
    @Idempotent(key = "#request.getHeader('X-Idempotency-Key')", failOpen = false)
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
    @PreAuthorize("@dictManagementAccessGuard.canDeleteType(authentication)")
    @Idempotent(key = "#request.getHeader('X-Idempotency-Key')", failOpen = false)
    public ResponseEntity<Void> deleteDictType(@PathVariable("id") Long id) {
        dictTypeService.delete(id);
        return ResponseEntity.ok().build();
    }

    /**
     * 批量删除字典类型
     */
    @PostMapping("/types/batch/delete")
    @PreAuthorize("@dictManagementAccessGuard.canDeleteType(authentication)")
    @Idempotent(key = "#request.getHeader('X-Idempotency-Key')", failOpen = false)
    public ResponseEntity<Void> batchDeleteDictTypes(@RequestBody List<Long> ids) {
        dictTypeService.batchDelete(ids);
        return ResponseEntity.ok().build();
    }

    /**
     * 查询当前租户可见的字典类型（平台 + 当前租户）
     */
    @GetMapping("/types/current")
    public ResponseEntity<List<DictType>> getVisibleDictTypes() {
        List<DictType> dictTypes = dictTypeService.findVisibleTypes();
        return ResponseEntity.ok(dictTypes);
    }

    // ==================== 字典项管理 ====================

    /**
     * 分页查询字典项
     */
    @GetMapping("/items")
    @PreAuthorize("@dictManagementAccessGuard.canReadItem(authentication)")
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
    @PreAuthorize("@dictManagementAccessGuard.canReadItem(authentication)")
    public ResponseEntity<DictItem> getDictItem(@PathVariable("id") Long id) {
        return dictItemService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * 根据字典类型ID获取字典项列表
     */
    @GetMapping("/items/type/{dictTypeId}")
    @PreAuthorize("@dictManagementAccessGuard.canReadItem(authentication)")
    public ResponseEntity<List<DictItem>> getDictItemsByType(@PathVariable("dictTypeId") Long dictTypeId) {
        List<DictItem> items = dictItemService.findByDictTypeId(dictTypeId);
        return ResponseEntity.ok(items);
    }

    /**
     * 根据字典编码获取字典项列表（支持多租户）
     */
    @GetMapping("/items/code/{dictCode}")
    public ResponseEntity<List<DictItem>> getDictItemsByCode(@PathVariable("dictCode") String dictCode) {
        List<DictItem> items = dictItemService.findByDictCode(dictCode);
        return ResponseEntity.ok(items);
    }

    /**
     * 根据字典编码获取字典映射（value -> label）
     */
    @GetMapping("/items/map/{dictCode}")
    public ResponseEntity<Map<String, String>> getDictMap(@PathVariable("dictCode") String dictCode) {
        Map<String, String> map = dictItemService.getDictMap(dictCode);
        return ResponseEntity.ok(map);
    }

    /**
     * 根据字典编码和值获取标签
     */
    @GetMapping("/items/label/{dictCode}/{value}")
    public ResponseEntity<String> getLabel(
            @PathVariable("dictCode") String dictCode,
            @PathVariable("value") String value
    ) {
        String label = dictItemService.getLabel(dictCode, value);
        return ResponseEntity.ok(label);
    }

    /**
     * 创建字典项
     */
    @PostMapping("/items")
    @PreAuthorize("@dictManagementAccessGuard.canCreateItem(authentication)")
    @Idempotent(key = "#request.getHeader('X-Idempotency-Key')", failOpen = false)
    public ResponseEntity<DictItem> createDictItem(@Valid @RequestBody DictItemCreateUpdateDto dto) {
        DictItem dictItem = dictItemService.create(dto);
        return ResponseEntity.ok(dictItem);
    }

    /**
     * 更新字典项
     */
    @PutMapping("/items/{id}")
    @PreAuthorize("@dictManagementAccessGuard.canUpdateItem(authentication)")
    @Idempotent(key = "#request.getHeader('X-Idempotency-Key')", failOpen = false)
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
    @PreAuthorize("@dictManagementAccessGuard.canDeleteItem(authentication)")
    @Idempotent(key = "#request.getHeader('X-Idempotency-Key')", failOpen = false)
    public ResponseEntity<Void> deleteDictItem(@PathVariable("id") Long id) {
        dictItemService.delete(id);
        return ResponseEntity.ok().build();
    }

    /**
     * 批量删除字典项
     */
    @PostMapping("/items/batch/delete")
    @PreAuthorize("@dictManagementAccessGuard.canDeleteItem(authentication)")
    @Idempotent(key = "#request.getHeader('X-Idempotency-Key')", failOpen = false)
    public ResponseEntity<Void> batchDeleteDictItems(@RequestBody List<Long> ids) {
        dictItemService.batchDelete(ids);
        return ResponseEntity.ok().build();
    }
}
