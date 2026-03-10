package com.tiny.platform.core.dict.service.impl;

import com.tiny.platform.core.dict.dto.DictItemCreateUpdateDto;
import com.tiny.platform.core.dict.dto.DictItemQueryDto;
import com.tiny.platform.core.dict.dto.DictItemResponseDto;
import com.tiny.platform.core.dict.dto.DictTypeCreateUpdateDto;
import com.tiny.platform.core.dict.dto.DictTypeQueryDto;
import com.tiny.platform.core.dict.dto.DictTypeResponseDto;
import com.tiny.platform.core.dict.model.DictItem;
import com.tiny.platform.core.dict.model.DictType;
import com.tiny.platform.core.dict.repository.DictItemRepository;
import com.tiny.platform.core.dict.repository.DictTypeRepository;
import com.tiny.platform.core.dict.service.DictPlatformAdminService;
import com.tiny.platform.core.dict.support.DictTenantScope;
import com.tiny.platform.infrastructure.core.exception.code.ErrorCode;
import com.tiny.platform.infrastructure.core.exception.exception.BusinessException;
import com.tiny.platform.infrastructure.core.exception.exception.NotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 平台字典管理服务实现。
 */
@Service
public class DictPlatformAdminServiceImpl implements DictPlatformAdminService {

    private final DictTypeRepository dictTypeRepository;
    private final DictItemRepository dictItemRepository;

    public DictPlatformAdminServiceImpl(DictTypeRepository dictTypeRepository, DictItemRepository dictItemRepository) {
        this.dictTypeRepository = dictTypeRepository;
        this.dictItemRepository = dictItemRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<DictTypeResponseDto> queryTypes(DictTypeQueryDto query, Pageable pageable) {
        return dictTypeRepository.findVisibleByConditions(
                StringUtils.hasText(query.getDictCode()) ? query.getDictCode() : null,
                StringUtils.hasText(query.getDictName()) ? query.getDictName() : null,
                DictTenantScope.PLATFORM_TENANT_ID,
                query.getEnabled(),
                pageable
        ).map(DictTypeResponseDto::new);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<DictType> findTypeById(Long id) {
        return dictTypeRepository.findById(id)
                .filter(dictType -> DictTenantScope.isPlatformTenant(dictType.getTenantId()));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<DictType> findTypeByCode(String dictCode) {
        return dictTypeRepository.findByDictCodeAndTenantId(dictCode, DictTenantScope.PLATFORM_TENANT_ID);
    }

    @Override
    @Transactional
    public DictType createType(DictTypeCreateUpdateDto dto) {
        if (dictTypeRepository.existsByDictCodeAndTenantId(dto.getDictCode(), DictTenantScope.PLATFORM_TENANT_ID)) {
            throw new BusinessException(ErrorCode.RESOURCE_ALREADY_EXISTS, "平台字典编码已存在: " + dto.getDictCode());
        }

        DictType dictType = new DictType();
        dictType.setDictCode(dto.getDictCode());
        dictType.setDictName(dto.getDictName());
        dictType.setDescription(dto.getDescription());
        dictType.setTenantId(DictTenantScope.PLATFORM_TENANT_ID);
        dictType.setCategoryId(dto.getCategoryId());
        dictType.setEnabled(dto.getEnabled() != null ? dto.getEnabled() : true);
        dictType.setSortOrder(dto.getSortOrder() != null ? dto.getSortOrder() : 0);
        dictType.setCreatedAt(LocalDateTime.now());
        dictType.setUpdatedAt(LocalDateTime.now());
        return dictTypeRepository.save(dictType);
    }

    @Override
    @Transactional
    public DictType updateType(Long id, DictTypeCreateUpdateDto dto) {
        DictType dictType = findMutablePlatformType(id);
        if (!dictType.getDictCode().equals(dto.getDictCode())
                && dictTypeRepository.existsByDictCodeAndTenantId(dto.getDictCode(), DictTenantScope.PLATFORM_TENANT_ID)) {
            throw new BusinessException(ErrorCode.RESOURCE_ALREADY_EXISTS, "平台字典编码已存在: " + dto.getDictCode());
        }

        dictType.setDictCode(dto.getDictCode());
        dictType.setDictName(dto.getDictName());
        dictType.setDescription(dto.getDescription());
        dictType.setCategoryId(dto.getCategoryId());
        dictType.setEnabled(dto.getEnabled() != null ? dto.getEnabled() : true);
        dictType.setSortOrder(dto.getSortOrder() != null ? dto.getSortOrder() : 0);
        dictType.setUpdatedAt(LocalDateTime.now());
        return dictTypeRepository.save(dictType);
    }

    @Override
    @Transactional
    public void deleteType(Long id) {
        DictType dictType = findMutablePlatformType(id);
        dictItemRepository.deleteByDictTypeId(dictType.getId());
        dictTypeRepository.delete(dictType);
    }

    @Override
    @Transactional
    public void batchDeleteTypes(List<Long> ids) {
        for (Long id : ids) {
            deleteType(id);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Page<DictItemResponseDto> queryItems(DictItemQueryDto query, Pageable pageable) {
        if (query.getDictTypeId() != null && !isPlatformType(query.getDictTypeId())) {
            return Page.empty(pageable);
        }
        Page<DictItem> page = dictItemRepository.findVisibleByConditions(
                DictTenantScope.PLATFORM_TENANT_ID,
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
    public Optional<DictItem> findItemById(Long id) {
        Optional<DictItem> item = dictItemRepository.findById(id)
                .filter(dictItem -> DictTenantScope.isPlatformTenant(dictItem.getTenantId()))
                .filter(dictItem -> isPlatformType(dictItem.getDictTypeId()));
        item.ifPresent(this::preloadDictType);
        return item;
    }

    @Override
    @Transactional(readOnly = true)
    public List<DictItem> findItemsByType(Long dictTypeId) {
        findPlatformType(dictTypeId);
        List<DictItem> items = dictItemRepository.findVisibleByDictTypeId(dictTypeId, DictTenantScope.PLATFORM_TENANT_ID);
        preloadDictType(items);
        return items;
    }

    @Override
    @Transactional(readOnly = true)
    public List<DictItem> findItemsByCode(String dictCode) {
        Optional<DictType> dictType = findTypeByCode(dictCode);
        if (dictType.isEmpty()) {
            return List.of();
        }
        List<DictItem> items = dictItemRepository.findVisibleByDictTypeId(
                dictType.get().getId(),
                DictTenantScope.PLATFORM_TENANT_ID
        );
        preloadDictType(items);
        return items;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, String> getDictMap(String dictCode) {
        Map<String, String> map = new HashMap<>();
        for (DictItem item : findItemsByCode(dictCode)) {
            map.put(item.getValue(), item.getLabel());
        }
        return map;
    }

    @Override
    @Transactional(readOnly = true)
    public String getLabel(String dictCode, String value) {
        return getDictMap(dictCode).getOrDefault(value, "");
    }

    @Override
    @Transactional
    public DictItem createItem(DictItemCreateUpdateDto dto) {
        DictType dictType = findMutablePlatformType(dto.getDictTypeId());
        if (dictItemRepository.existsByDictTypeIdAndValueAndTenantId(
                dictType.getId(),
                dto.getValue(),
                DictTenantScope.PLATFORM_TENANT_ID
        )) {
            throw new BusinessException(ErrorCode.RESOURCE_ALREADY_EXISTS, "平台字典项值已存在: " + dto.getValue());
        }

        DictItem dictItem = new DictItem();
        dictItem.setDictTypeId(dictType.getId());
        dictItem.setValue(dto.getValue());
        dictItem.setLabel(dto.getLabel());
        dictItem.setDescription(dto.getDescription());
        dictItem.setTenantId(DictTenantScope.PLATFORM_TENANT_ID);
        dictItem.setIsBuiltin(false);
        dictItem.setEnabled(dto.getEnabled() != null ? dto.getEnabled() : true);
        dictItem.setSortOrder(dto.getSortOrder() != null ? dto.getSortOrder() : 0);
        dictItem.setCreatedAt(LocalDateTime.now());
        dictItem.setUpdatedAt(LocalDateTime.now());
        return dictItemRepository.save(dictItem);
    }

    @Override
    @Transactional
    public DictItem updateItem(Long id, DictItemCreateUpdateDto dto) {
        DictItem dictItem = findMutablePlatformItem(id);
        DictType dictType = findMutablePlatformType(dictItem.getDictTypeId());

        if (!dictItem.getDictTypeId().equals(dto.getDictTypeId())) {
            throw new BusinessException(ErrorCode.RESOURCE_STATE_INVALID, "不允许修改字典项所属字典类型");
        }

        if (!dictItem.getValue().equals(dto.getValue())
                && dictItemRepository.existsByDictTypeIdAndValueAndTenantIdAndIdNot(
                        dictType.getId(),
                        dto.getValue(),
                        DictTenantScope.PLATFORM_TENANT_ID,
                        id
        )) {
            throw new BusinessException(ErrorCode.RESOURCE_ALREADY_EXISTS, "平台字典项值已被使用: " + dto.getValue());
        }

        dictItem.setValue(dto.getValue());
        dictItem.setLabel(dto.getLabel());
        dictItem.setDescription(dto.getDescription());
        dictItem.setEnabled(dto.getEnabled() != null ? dto.getEnabled() : true);
        dictItem.setSortOrder(dto.getSortOrder() != null ? dto.getSortOrder() : 0);
        dictItem.setUpdatedAt(LocalDateTime.now());
        return dictItemRepository.save(dictItem);
    }

    @Override
    @Transactional
    public void deleteItem(Long id) {
        DictItem dictItem = findMutablePlatformItem(id);
        dictItemRepository.delete(dictItem);
    }

    @Override
    @Transactional
    public void batchDeleteItems(List<Long> ids) {
        for (Long id : ids) {
            deleteItem(id);
        }
    }

    private boolean isPlatformType(Long dictTypeId) {
        return dictTypeRepository.findById(dictTypeId)
                .map(dictType -> DictTenantScope.isPlatformTenant(dictType.getTenantId()))
                .orElse(false);
    }

    private DictType findPlatformType(Long id) {
        return dictTypeRepository.findById(id)
                .filter(dictType -> DictTenantScope.isPlatformTenant(dictType.getTenantId()))
                .orElseThrow(() -> new NotFoundException("平台字典类型不存在: " + id));
    }

    private DictType findMutablePlatformType(Long id) {
        DictType dictType = findPlatformType(id);
        if (Boolean.TRUE.equals(dictType.getBuiltinLocked())) {
            throw new BusinessException(ErrorCode.RESOURCE_STATE_INVALID, "内置平台字典已锁定，不允许修改");
        }
        return dictType;
    }

    private DictItem findMutablePlatformItem(Long id) {
        DictItem dictItem = dictItemRepository.findById(id)
                .filter(item -> DictTenantScope.isPlatformTenant(item.getTenantId()))
                .orElseThrow(() -> new NotFoundException("平台字典项不存在: " + id));
        DictType dictType = findMutablePlatformType(dictItem.getDictTypeId());
        dictItem.setDictType(dictType);
        return dictItem;
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
