package com.tiny.platform.application.controller.dict;

import com.tiny.platform.core.dict.dto.DictItemCreateUpdateDto;
import com.tiny.platform.core.dict.dto.DictItemQueryDto;
import com.tiny.platform.core.dict.dto.DictItemResponseDto;
import com.tiny.platform.core.dict.dto.DictTypeCreateUpdateDto;
import com.tiny.platform.core.dict.dto.DictTypeQueryDto;
import com.tiny.platform.core.dict.dto.DictTypeResponseDto;
import com.tiny.platform.core.dict.dto.PlatformDictOverrideDetailDto;
import com.tiny.platform.core.dict.dto.PlatformDictOverrideSummaryDto;
import com.tiny.platform.core.dict.model.DictItem;
import com.tiny.platform.core.dict.model.DictType;
import com.tiny.platform.core.dict.service.DictPlatformAdminService;
import com.tiny.platform.infrastructure.core.dto.PageResponse;
import com.tiny.platform.infrastructure.idempotent.sdk.annotation.Idempotent;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 平台字典管理控制器。
 */
@RestController
@RequestMapping("/platform/dict")
@PreAuthorize("@dictPlatformAccessGuard.canManagePlatformDict(authentication)")
public class PlatformDictController {

    private final DictPlatformAdminService dictPlatformAdminService;

    public PlatformDictController(DictPlatformAdminService dictPlatformAdminService) {
        this.dictPlatformAdminService = dictPlatformAdminService;
    }

    @GetMapping("/types")
    public ResponseEntity<PageResponse<DictTypeResponseDto>> getDictTypes(
            @Valid DictTypeQueryDto query,
            @PageableDefault(size = 10, sort = "sortOrder", direction = Sort.Direction.ASC) Pageable pageable
    ) {
        Page<DictTypeResponseDto> page = dictPlatformAdminService.queryTypes(query, pageable);
        return ResponseEntity.ok(new PageResponse<>(page));
    }

    @GetMapping("/types/{id}")
    public ResponseEntity<DictType> getDictType(@PathVariable("id") Long id) {
        return dictPlatformAdminService.findTypeById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/types/code/{dictCode}")
    public ResponseEntity<DictType> getDictTypeByCode(@PathVariable("dictCode") String dictCode) {
        return dictPlatformAdminService.findTypeByCode(dictCode)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/types")
    @Idempotent(key = "#request.getHeader('X-Idempotency-Key')", failOpen = false)
    public ResponseEntity<DictType> createDictType(@Valid @RequestBody DictTypeCreateUpdateDto dto) {
        return ResponseEntity.ok(dictPlatformAdminService.createType(dto));
    }

    @PutMapping("/types/{id}")
    @Idempotent(key = "#request.getHeader('X-Idempotency-Key')", failOpen = false)
    public ResponseEntity<DictType> updateDictType(
            @PathVariable("id") Long id,
            @Valid @RequestBody DictTypeCreateUpdateDto dto
    ) {
        return ResponseEntity.ok(dictPlatformAdminService.updateType(id, dto));
    }

    @DeleteMapping("/types/{id}")
    @Idempotent(key = "#request.getHeader('X-Idempotency-Key')", failOpen = false)
    public ResponseEntity<Void> deleteDictType(@PathVariable("id") Long id) {
        dictPlatformAdminService.deleteType(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/types/batch/delete")
    @Idempotent(key = "#request.getHeader('X-Idempotency-Key')", failOpen = false)
    public ResponseEntity<Void> batchDeleteDictTypes(@RequestBody List<Long> ids) {
        dictPlatformAdminService.batchDeleteTypes(ids);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/items")
    public ResponseEntity<PageResponse<DictItemResponseDto>> getDictItems(
            @Valid DictItemQueryDto query,
            @PageableDefault(size = 10, sort = "sortOrder", direction = Sort.Direction.ASC) Pageable pageable
    ) {
        Page<DictItemResponseDto> page = dictPlatformAdminService.queryItems(query, pageable);
        return ResponseEntity.ok(new PageResponse<>(page));
    }

    @GetMapping("/items/{id}")
    public ResponseEntity<DictItem> getDictItem(@PathVariable("id") Long id) {
        return dictPlatformAdminService.findItemById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/items/type/{dictTypeId}")
    public ResponseEntity<List<DictItem>> getDictItemsByType(@PathVariable("dictTypeId") Long dictTypeId) {
        return ResponseEntity.ok(dictPlatformAdminService.findItemsByType(dictTypeId));
    }

    @GetMapping("/items/code/{dictCode}")
    public ResponseEntity<List<DictItem>> getDictItemsByCode(@PathVariable("dictCode") String dictCode) {
        return ResponseEntity.ok(dictPlatformAdminService.findItemsByCode(dictCode));
    }

    @GetMapping("/types/{dictTypeId}/overrides")
    public ResponseEntity<List<PlatformDictOverrideSummaryDto>> getTypeOverrideSummaries(
            @PathVariable("dictTypeId") Long dictTypeId
    ) {
        return ResponseEntity.ok(dictPlatformAdminService.findTypeOverrideSummaries(dictTypeId));
    }

    @GetMapping("/types/{dictTypeId}/overrides/{tenantId}")
    public ResponseEntity<List<PlatformDictOverrideDetailDto>> getTypeOverrideDetails(
            @PathVariable("dictTypeId") Long dictTypeId,
            @PathVariable("tenantId") Long tenantId
    ) {
        return ResponseEntity.ok(dictPlatformAdminService.findTypeOverrideDetails(dictTypeId, tenantId));
    }

    @GetMapping("/items/map/{dictCode}")
    public ResponseEntity<Map<String, String>> getDictMap(@PathVariable("dictCode") String dictCode) {
        return ResponseEntity.ok(dictPlatformAdminService.getDictMap(dictCode));
    }

    @GetMapping("/items/label/{dictCode}/{value}")
    public ResponseEntity<String> getLabel(
            @PathVariable("dictCode") String dictCode,
            @PathVariable("value") String value
    ) {
        return ResponseEntity.ok(dictPlatformAdminService.getLabel(dictCode, value));
    }

    @PostMapping("/items")
    @Idempotent(key = "#request.getHeader('X-Idempotency-Key')", failOpen = false)
    public ResponseEntity<DictItem> createDictItem(@Valid @RequestBody DictItemCreateUpdateDto dto) {
        return ResponseEntity.ok(dictPlatformAdminService.createItem(dto));
    }

    @PutMapping("/items/{id}")
    @Idempotent(key = "#request.getHeader('X-Idempotency-Key')", failOpen = false)
    public ResponseEntity<DictItem> updateDictItem(
            @PathVariable("id") Long id,
            @Valid @RequestBody DictItemCreateUpdateDto dto
    ) {
        return ResponseEntity.ok(dictPlatformAdminService.updateItem(id, dto));
    }

    @DeleteMapping("/items/{id}")
    @Idempotent(key = "#request.getHeader('X-Idempotency-Key')", failOpen = false)
    public ResponseEntity<Void> deleteDictItem(@PathVariable("id") Long id) {
        dictPlatformAdminService.deleteItem(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/items/batch/delete")
    @Idempotent(key = "#request.getHeader('X-Idempotency-Key')", failOpen = false)
    public ResponseEntity<Void> batchDeleteDictItems(@RequestBody List<Long> ids) {
        dictPlatformAdminService.batchDeleteItems(ids);
        return ResponseEntity.ok().build();
    }
}
