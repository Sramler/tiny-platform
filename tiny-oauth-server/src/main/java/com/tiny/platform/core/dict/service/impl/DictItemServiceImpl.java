package com.tiny.platform.core.dict.service.impl;

import com.tiny.platform.core.dict.dto.DictItemCreateUpdateDto;
import com.tiny.platform.core.dict.dto.DictItemQueryDto;
import com.tiny.platform.core.dict.dto.DictItemResponseDto;
import com.tiny.platform.core.dict.model.DictItem;
import com.tiny.platform.core.dict.model.DictType;
import com.tiny.platform.core.dict.repository.DictItemRepository;
import com.tiny.platform.core.dict.repository.DictTypeRepository;
import com.tiny.platform.core.dict.service.DictItemService;
import com.tiny.platform.core.dict.support.DictTenantScope;
import com.tiny.platform.infrastructure.core.exception.code.ErrorCode;
import com.tiny.platform.infrastructure.core.exception.exception.BusinessException;
import com.tiny.platform.infrastructure.core.exception.exception.NotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * 字典项服务实现类
 */
@Service
public class DictItemServiceImpl implements DictItemService {

    private final DictItemRepository dictItemRepository;
    private final DictTypeRepository dictTypeRepository;

    public DictItemServiceImpl(DictItemRepository dictItemRepository, DictTypeRepository dictTypeRepository) {
        this.dictItemRepository = dictItemRepository;
        this.dictTypeRepository = dictTypeRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<DictItemResponseDto> query(DictItemQueryDto query, Pageable pageable) {
        // 租户隔离仅以 TenantContext 为准，不使用 query 中的 tenantId
        Long currentTenantId = DictTenantScope.requireCurrentTenantId();
        if (query.getDictTypeId() != null && !isVisibleType(query.getDictTypeId(), currentTenantId)) {
            return new PageImpl<>(List.of(), pageable, 0);
        }

        if (query.getDictTypeId() == null) {
            List<DictItem> mergedItems = queryMergedVisibleItems(query, currentTenantId, pageable);
            return toPage(mergedItems.stream().map(DictItemResponseDto::new).toList(), pageable);
        }

        DictType dictType = findAccessibleType(query.getDictTypeId(), currentTenantId);
        if (DictTenantScope.isPlatformTenant(dictType.getTenantId())) {
            List<DictItem> mergedItems = queryMergedVisibleItems(query, currentTenantId, pageable);
            return toPage(mergedItems.stream().map(DictItemResponseDto::new).toList(), pageable);
        }

        Page<DictItem> page = dictItemRepository.findVisibleByConditions(
                currentTenantId,
                query.getDictTypeId(),
                StringUtils.hasText(query.getValue()) ? query.getValue() : null,
                StringUtils.hasText(query.getLabel()) ? query.getLabel() : null,
                query.getEnabled(),
                pageable
        );
        preloadDictType(page.getContent());
        return page.map(DictItemResponseDto::new);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<DictItem> findById(Long id) {
        Long currentTenantId = DictTenantScope.requireCurrentTenantId();
        Optional<DictItem> item = dictItemRepository.findById(id)
                .filter(dictItem -> isVisibleItem(dictItem, currentTenantId));
        item.ifPresent(this::preloadDictType);
        return item;
    }

    @Override
    @Transactional(readOnly = true)
    public List<DictItem> findByDictTypeId(Long dictTypeId) {
        Long currentTenantId = DictTenantScope.requireCurrentTenantId();
        DictType dictType = findAccessibleType(dictTypeId, currentTenantId);
        List<DictItem> items = dictItemRepository.findVisibleByDictTypeId(dictTypeId, currentTenantId);
        preloadDictType(items);
        return mergeVisibleItems(dictType, items);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DictItem> findByDictCode(String dictCode) {
        Long currentTenantId = DictTenantScope.requireCurrentTenantId();
        Optional<DictType> dictType = resolveVisibleType(dictCode, currentTenantId);
        if (dictType.isEmpty()) {
            return List.of();
        }
        List<DictItem> items = dictItemRepository.findVisibleByDictTypeId(dictType.get().getId(), currentTenantId);
        preloadDictType(items);
        return mergeVisibleItems(dictType.get(), items);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, String> getDictMap(String dictCode) {
        List<DictItem> items = findByDictCode(dictCode);
        Map<String, String> map = new HashMap<>();
        for (DictItem item : items) {
            map.put(item.getValue(), item.getLabel());
        }
        return map;
    }

    @Override
    @Transactional(readOnly = true)
    public String getLabel(String dictCode, String value) {
        Map<String, String> dictMap = getDictMap(dictCode);
        return dictMap.getOrDefault(value, "");
    }

    @Override
    @Transactional
    public DictItem create(DictItemCreateUpdateDto dto) {
        Long currentTenantId = DictTenantScope.requireCurrentTenantId();
        DictType dictType = findAccessibleType(dto.getDictTypeId(), currentTenantId);
        DictItem platformBaseItem = null;
        if (DictTenantScope.isPlatformTenant(dictType.getTenantId())) {
            platformBaseItem = findPlatformBaseItem(dictType.getId(), dto.getValue());
        }

        if (dictItemRepository.existsByDictTypeIdAndValueAndTenantId(dto.getDictTypeId(), dto.getValue(), currentTenantId)) {
            throw new BusinessException(ErrorCode.RESOURCE_ALREADY_EXISTS, "字典项值已存在: " + dto.getValue());
        }

        DictItem dictItem = new DictItem();
        dictItem.setDictTypeId(dto.getDictTypeId());
        dictItem.setValue(dto.getValue());
        dictItem.setLabel(dto.getLabel());
        dictItem.setTenantId(currentTenantId);
        dictItem.setIsBuiltin(false);
        if (platformBaseItem != null) {
            applyOverlayDefaults(dictItem, platformBaseItem);
        } else {
            dictItem.setDescription(dto.getDescription());
            dictItem.setEnabled(dto.getEnabled() != null ? dto.getEnabled() : true);
            dictItem.setSortOrder(dto.getSortOrder() != null ? dto.getSortOrder() : 0);
        }
        dictItem.setCreatedAt(LocalDateTime.now());
        dictItem.setUpdatedAt(LocalDateTime.now());

        return dictItemRepository.save(dictItem);
    }

    @Override
    @Transactional
    public DictItem update(Long id, DictItemCreateUpdateDto dto) {
        Long currentTenantId = DictTenantScope.requireCurrentTenantId();
        DictItem dictItem = findWritableItem(id, currentTenantId);
        DictType dictType = findAccessibleType(dictItem.getDictTypeId(), currentTenantId);

        if (!dictItem.getDictTypeId().equals(dto.getDictTypeId())) {
            throw new BusinessException(ErrorCode.RESOURCE_STATE_INVALID, "不允许修改字典项所属字典类型");
        }

        if (DictTenantScope.isPlatformTenant(dictType.getTenantId())) {
            DictItem platformBaseItem = findPlatformBaseItem(dictType.getId(), dictItem.getValue());
            if (!dictItem.getValue().equals(dto.getValue())) {
                throw new BusinessException(ErrorCode.RESOURCE_STATE_INVALID, "平台字典覆盖项不允许修改 value");
            }
            dictItem.setDescription(platformBaseItem.getDescription());
            dictItem.setEnabled(platformBaseItem.getEnabled());
            dictItem.setSortOrder(platformBaseItem.getSortOrder());
        } else if (!dictItem.getValue().equals(dto.getValue()) &&
                dictItemRepository.existsByDictTypeIdAndValueAndTenantIdAndIdNot(
                        dto.getDictTypeId(),
                        dto.getValue(),
                        currentTenantId,
                        id
                )) {
            throw new BusinessException(ErrorCode.RESOURCE_ALREADY_EXISTS, "字典项值已被使用: " + dto.getValue());
        }

        dictItem.setDictTypeId(dto.getDictTypeId());
        dictItem.setValue(dto.getValue());
        dictItem.setLabel(dto.getLabel());
        dictItem.setTenantId(currentTenantId);
        if (!DictTenantScope.isPlatformTenant(dictType.getTenantId())) {
            dictItem.setDescription(dto.getDescription());
            dictItem.setEnabled(dto.getEnabled() != null ? dto.getEnabled() : true);
            dictItem.setSortOrder(dto.getSortOrder() != null ? dto.getSortOrder() : 0);
        }
        dictItem.setUpdatedAt(LocalDateTime.now());

        return dictItemRepository.save(dictItem);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Long currentTenantId = DictTenantScope.requireCurrentTenantId();
        DictItem dictItem = findWritableItem(id, currentTenantId);
        dictItemRepository.delete(dictItem);
    }

    @Override
    @Transactional
    public void batchDelete(List<Long> ids) {
        for (Long id : ids) {
            delete(id);
        }
    }

    @Override
    @Transactional
    public void deleteByDictTypeId(Long dictTypeId) {
        dictItemRepository.deleteByDictTypeId(dictTypeId);
    }

    private Optional<DictType> resolveVisibleType(String dictCode, Long currentTenantId) {
        Optional<DictType> tenantType = dictTypeRepository.findByDictCodeAndTenantId(dictCode, currentTenantId);
        if (tenantType.isPresent()) {
            return tenantType;
        }
        return dictTypeRepository.findByDictCodeAndTenantId(dictCode, DictTenantScope.PLATFORM_TENANT_ID);
    }

    private DictType findAccessibleType(Long dictTypeId, Long currentTenantId) {
        DictType dictType = dictTypeRepository.findById(dictTypeId)
                .orElseThrow(() -> new NotFoundException("字典类型不存在: " + dictTypeId));
        if (!DictTenantScope.isVisibleTenant(dictType.getTenantId(), currentTenantId)) {
            throw new NotFoundException("字典类型不存在: " + dictTypeId);
        }
        return dictType;
    }

    private DictItem findWritableItem(Long id, Long currentTenantId) {
        DictItem dictItem = dictItemRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("字典项不存在: " + id));
        if (!isVisibleItem(dictItem, currentTenantId)) {
            throw new NotFoundException("字典项不存在: " + id);
        }
        if (DictTenantScope.isPlatformTenant(dictItem.getTenantId())) {
            throw new BusinessException(ErrorCode.RESOURCE_STATE_INVALID, "平台字典项只读，不允许修改");
        }
        return dictItem;
    }

    private boolean isVisibleType(Long dictTypeId, Long currentTenantId) {
        return dictTypeRepository.findById(dictTypeId)
                .map(dictType -> DictTenantScope.isVisibleTenant(dictType.getTenantId(), currentTenantId))
                .orElse(false);
    }

    private boolean isVisibleItem(DictItem dictItem, Long currentTenantId) {
        Optional<DictType> dictType = dictTypeRepository.findById(dictItem.getDictTypeId());
        if (dictType.isEmpty()) {
            return false;
        }
        if (DictTenantScope.isPlatformTenant(dictType.get().getTenantId())) {
            return DictTenantScope.isPlatformTenant(dictItem.getTenantId())
                    || DictTenantScope.isCurrentTenant(dictItem.getTenantId(), currentTenantId);
        }
        return DictTenantScope.isCurrentTenant(dictType.get().getTenantId(), currentTenantId)
                && DictTenantScope.isCurrentTenant(dictItem.getTenantId(), currentTenantId);
    }

    private DictItem findPlatformBaseItem(Long dictTypeId, String value) {
        return dictItemRepository.findByDictTypeIdAndValueAndTenantId(
                dictTypeId,
                value,
                DictTenantScope.PLATFORM_TENANT_ID
        ).orElseThrow(() -> new BusinessException(ErrorCode.INVALID_PARAMETER, "平台字典仅允许覆盖既有 value: " + value));
    }

    private void applyOverlayDefaults(DictItem target, DictItem platformBaseItem) {
        target.setDescription(platformBaseItem.getDescription());
        target.setEnabled(platformBaseItem.getEnabled());
        target.setSortOrder(platformBaseItem.getSortOrder());
    }

    private List<DictItem> queryMergedVisibleItems(DictItemQueryDto query, Long currentTenantId, Pageable pageable) {
        List<DictItem> visibleItems = dictItemRepository.findVisibleByConditions(
                currentTenantId,
                query.getDictTypeId(),
                StringUtils.hasText(query.getValue()) ? query.getValue() : null,
                StringUtils.hasText(query.getLabel()) ? query.getLabel() : null,
                query.getEnabled(),
                mergedQuerySort(pageable)
        );
        preloadDictType(visibleItems);
        return sortItems(mergeVisibleItems(visibleItems), pageable);
    }

    private List<DictItem> mergeVisibleItems(List<DictItem> items) {
        Map<String, DictItem> platformItems = new HashMap<>();
        Map<String, DictItem> tenantItems = new HashMap<>();
        for (DictItem item : items) {
            String key = item.getDictTypeId() + ":" + item.getValue();
            if (DictTenantScope.isPlatformTenant(item.getTenantId())) {
                platformItems.put(key, item);
            } else {
                tenantItems.put(key, item);
            }
        }

        Map<String, DictItem> merged = new LinkedHashMap<>();
        for (DictItem item : items) {
            String key = item.getDictTypeId() + ":" + item.getValue();
            if (merged.containsKey(key)) {
                continue;
            }
            DictItem platformItem = platformItems.get(key);
            DictItem tenantItem = tenantItems.get(key);
            if (platformItem != null && tenantItem != null) {
                DictItem overlay = copyItem(platformItem);
                overlay.setId(tenantItem.getId());
                overlay.setTenantId(tenantItem.getTenantId());
                overlay.setLabel(tenantItem.getLabel());
                overlay.setCreatedAt(tenantItem.getCreatedAt());
                overlay.setUpdatedAt(tenantItem.getUpdatedAt());
                overlay.setCreatedBy(tenantItem.getCreatedBy());
                overlay.setUpdatedBy(tenantItem.getUpdatedBy());
                merged.put(key, overlay);
                continue;
            }
            merged.put(key, copyItem(tenantItem != null ? tenantItem : platformItem));
        }
        return new ArrayList<>(merged.values());
    }

    private List<DictItem> mergeVisibleItems(DictType dictType, List<DictItem> items) {
        if (!DictTenantScope.isPlatformTenant(dictType.getTenantId())) {
            return items;
        }
        return mergeVisibleItems(items);
    }

    private Sort mergedQuerySort(Pageable pageable) {
        if (pageable.getSort().isSorted()) {
            return pageable.getSort();
        }
        return Sort.by(Sort.Order.asc("sortOrder"), Sort.Order.asc("id"));
    }

    private List<DictItem> sortItems(List<DictItem> items, Pageable pageable) {
        Sort sort = pageable.getSort().isSorted()
                ? pageable.getSort()
                : Sort.by(Sort.Order.asc("sortOrder"), Sort.Order.asc("id"));
        Comparator<DictItem> comparator = null;
        for (Sort.Order order : sort) {
            Comparator<DictItem> next = comparatorFor(order);
            if (next == null) {
                continue;
            }
            comparator = comparator == null ? next : comparator.thenComparing(next);
        }
        if (comparator == null) {
            comparator = comparatorFor(Sort.Order.asc("sortOrder"))
                    .thenComparing(comparatorFor(Sort.Order.asc("id")));
        }
        return items.stream().sorted(comparator).toList();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private Comparator<DictItem> comparatorFor(Sort.Order order) {
        Function<DictItem, ? extends Comparable> extractor = switch (order.getProperty()) {
            case "id" -> DictItem::getId;
            case "dictTypeId" -> DictItem::getDictTypeId;
            case "dictCode" -> item -> item.getDictType() == null ? null : item.getDictType().getDictCode();
            case "value" -> DictItem::getValue;
            case "label" -> DictItem::getLabel;
            case "description" -> DictItem::getDescription;
            case "recordTenantId" -> DictItem::getTenantId;
            case "isBuiltin" -> DictItem::getIsBuiltin;
            case "enabled" -> DictItem::getEnabled;
            case "sortOrder" -> DictItem::getSortOrder;
            case "createdAt" -> DictItem::getCreatedAt;
            case "updatedAt" -> DictItem::getUpdatedAt;
            default -> null;
        };
        if (extractor == null) {
            return null;
        }
        Comparator<Comparable> valueComparator = Comparator.nullsLast(Comparator.naturalOrder());
        Comparator<DictItem> comparator = Comparator.comparing(extractor, valueComparator);
        return order.isAscending() ? comparator : comparator.reversed();
    }

    private DictItem copyItem(DictItem source) {
        DictItem copy = new DictItem();
        copy.setId(source.getId());
        copy.setDictTypeId(source.getDictTypeId());
        copy.setDictType(source.getDictType());
        copy.setValue(source.getValue());
        copy.setLabel(source.getLabel());
        copy.setDescription(source.getDescription());
        copy.setTenantId(source.getTenantId());
        copy.setIsBuiltin(source.getIsBuiltin());
        copy.setEnabled(source.getEnabled());
        copy.setSortOrder(source.getSortOrder());
        copy.setExtAttrs(source.getExtAttrs());
        copy.setCreatedAt(source.getCreatedAt());
        copy.setUpdatedAt(source.getUpdatedAt());
        copy.setCreatedBy(source.getCreatedBy());
        copy.setUpdatedBy(source.getUpdatedBy());
        return copy;
    }

    private <T> Page<T> toPage(List<T> items, Pageable pageable) {
        int start = Math.toIntExact(pageable.getOffset());
        if (start >= items.size()) {
            return new PageImpl<>(List.of(), pageable, items.size());
        }
        int end = Math.min(start + pageable.getPageSize(), items.size());
        return new PageImpl<>(items.subList(start, end), pageable, items.size());
    }

    private void preloadDictType(DictItem dictItem) {
        if (dictItem.getDictType() != null) {
            dictItem.getDictType().getDictCode();
        }
    }

    private void preloadDictType(List<DictItem> items) {
        items.forEach(this::preloadDictType);
    }
}
