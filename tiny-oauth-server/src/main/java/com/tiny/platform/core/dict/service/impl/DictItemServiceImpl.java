package com.tiny.platform.core.dict.service.impl;

import com.tiny.platform.core.dict.dto.DictItemCreateUpdateDto;
import com.tiny.platform.core.dict.dto.DictItemQueryDto;
import com.tiny.platform.core.dict.dto.DictItemResponseDto;
import com.tiny.platform.core.dict.model.DictItem;
import com.tiny.platform.core.dict.repository.DictItemRepository;
import com.tiny.platform.core.dict.repository.DictTypeRepository;
import com.tiny.platform.core.dict.service.DictItemService;
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
        Page<DictItem> page = dictItemRepository.findByConditions(
                query.getDictTypeId(),
                StringUtils.hasText(query.getValue()) ? query.getValue() : null,
                StringUtils.hasText(query.getLabel()) ? query.getLabel() : null,
                query.getTenantId(),
                query.getEnabled(),
                pageable
        );
        // 在事务内预加载 dictType，避免延迟加载异常
        page.getContent().forEach(item -> {
            if (item.getDictType() != null) {
                item.getDictType().getDictCode(); // 触发延迟加载
            }
        });
        return page.map(DictItemResponseDto::new);
    }

    @Override
    public Optional<DictItem> findById(Long id) {
        return dictItemRepository.findById(id);
    }

    @Override
    public List<DictItem> findByDictTypeId(Long dictTypeId) {
        return dictItemRepository.findByDictTypeIdOrderBySortOrderAsc(dictTypeId);
    }

    @Override
    public List<DictItem> findByDictCode(String dictCode, Long tenantId) {
        return dictItemRepository.findByDictCodeAndTenantId(dictCode, tenantId != null ? tenantId : 0L);
    }

    @Override
    public Map<String, String> getDictMap(String dictCode, Long tenantId) {
        List<DictItem> items = findByDictCode(dictCode, tenantId);
        Map<String, String> map = new HashMap<>();
        for (DictItem item : items) {
            // 租户值覆盖平台值
            map.put(item.getValue(), item.getLabel());
        }
        return map;
    }

    @Override
    public String getLabel(String dictCode, String value, Long tenantId) {
        Map<String, String> dictMap = getDictMap(dictCode, tenantId);
        return dictMap.getOrDefault(value, "");
    }

    @Override
    @Transactional
    public DictItem create(DictItemCreateUpdateDto dto) {
        // 检查字典类型是否存在
        dictTypeRepository.findById(dto.getDictTypeId())
                .orElseThrow(() -> new NotFoundException("字典类型不存在: " + dto.getDictTypeId()));

        // 检查同一字典类型下，同一租户内value是否已存在
        Long tenantId = dto.getTenantId() != null ? dto.getTenantId() : 0L;
        if (dictItemRepository.existsByDictTypeIdAndValueAndTenantId(dto.getDictTypeId(), dto.getValue(), tenantId)) {
            throw new BusinessException(ErrorCode.RESOURCE_ALREADY_EXISTS, "字典项值已存在: " + dto.getValue());
        }

        DictItem dictItem = new DictItem();
        dictItem.setDictTypeId(dto.getDictTypeId());
        dictItem.setValue(dto.getValue());
        dictItem.setLabel(dto.getLabel());
        dictItem.setDescription(dto.getDescription());
        dictItem.setTenantId(tenantId);
        dictItem.setEnabled(dto.getEnabled() != null ? dto.getEnabled() : true);
        dictItem.setSortOrder(dto.getSortOrder() != null ? dto.getSortOrder() : 0);
        dictItem.setCreatedAt(LocalDateTime.now());
        dictItem.setUpdatedAt(LocalDateTime.now());

        return dictItemRepository.save(dictItem);
    }

    @Override
    @Transactional
    public DictItem update(Long id, DictItemCreateUpdateDto dto) {
        DictItem dictItem = dictItemRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("字典项不存在: " + id));

        // 检查同一字典类型下，同一租户内value是否已被其他字典项使用
        Long tenantId = dto.getTenantId() != null ? dto.getTenantId() : 0L;
        if (!dictItem.getValue().equals(dto.getValue()) &&
            dictItemRepository.existsByDictTypeIdAndValueAndTenantIdAndIdNot(
                    dto.getDictTypeId(), dto.getValue(), tenantId, id)) {
            throw new BusinessException(ErrorCode.RESOURCE_ALREADY_EXISTS, "字典项值已被使用: " + dto.getValue());
        }

        dictItem.setDictTypeId(dto.getDictTypeId());
        dictItem.setValue(dto.getValue());
        dictItem.setLabel(dto.getLabel());
        dictItem.setDescription(dto.getDescription());
        dictItem.setTenantId(tenantId);
        dictItem.setEnabled(dto.getEnabled());
        dictItem.setSortOrder(dto.getSortOrder());
        dictItem.setUpdatedAt(LocalDateTime.now());

        return dictItemRepository.save(dictItem);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        DictItem dictItem = dictItemRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("字典项不存在: " + id));
        
        // 检查是否为内置字典项（通过关联的字典类型判断）
        // 在事务内访问延迟加载的 dictType
        if (dictItem.getDictType() != null) {
            // 触发延迟加载并检查锁定状态
            if (Boolean.TRUE.equals(dictItem.getDictType().getBuiltinLocked())) {
                throw new BusinessException(ErrorCode.RESOURCE_STATE_INVALID, "内置字典项已锁定，不允许删除");
            }
        }
        
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
}

