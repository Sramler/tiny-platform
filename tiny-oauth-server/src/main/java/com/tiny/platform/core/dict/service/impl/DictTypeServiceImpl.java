package com.tiny.platform.core.dict.service.impl;

import com.tiny.platform.core.dict.dto.DictTypeCreateUpdateDto;
import com.tiny.platform.core.dict.dto.DictTypeQueryDto;
import com.tiny.platform.core.dict.dto.DictTypeResponseDto;
import com.tiny.platform.core.dict.model.DictType;
import com.tiny.platform.core.dict.repository.DictItemRepository;
import com.tiny.platform.core.dict.repository.DictTypeRepository;
import com.tiny.platform.core.dict.service.DictTypeService;
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
import java.util.List;
import java.util.Optional;

/**
 * 字典类型服务实现类
 */
@Service
public class DictTypeServiceImpl implements DictTypeService {

    private final DictTypeRepository dictTypeRepository;
    private final DictItemRepository dictItemRepository;

    public DictTypeServiceImpl(DictTypeRepository dictTypeRepository, DictItemRepository dictItemRepository) {
        this.dictTypeRepository = dictTypeRepository;
        this.dictItemRepository = dictItemRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<DictTypeResponseDto> query(DictTypeQueryDto query, Pageable pageable) {
        // 租户隔离仅以 TenantContext 为准，不使用 query 中的 tenantId
        Long currentTenantId = DictTenantScope.requireCurrentTenantId();
        Page<DictType> page = dictTypeRepository.findVisibleByConditions(
                StringUtils.hasText(query.getDictCode()) ? query.getDictCode() : null,
                StringUtils.hasText(query.getDictName()) ? query.getDictName() : null,
                currentTenantId,
                query.getEnabled(),
                pageable
        );
        return page.map(DictTypeResponseDto::new);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<DictType> findById(Long id) {
        Long currentTenantId = DictTenantScope.requireCurrentTenantId();
        return dictTypeRepository.findById(id)
                .filter(dictType -> DictTenantScope.isVisibleTenant(dictType.getTenantId(), currentTenantId));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<DictType> findByDictCode(String dictCode) {
        Long currentTenantId = DictTenantScope.requireCurrentTenantId();
        Optional<DictType> tenantType = dictTypeRepository.findByDictCodeAndTenantId(dictCode, currentTenantId);
        if (tenantType.isPresent()) {
            return tenantType;
        }
        return dictTypeRepository.findByDictCodeAndTenantId(dictCode, DictTenantScope.PLATFORM_TENANT_ID);
    }

    @Override
    @Transactional
    public DictType create(DictTypeCreateUpdateDto dto) {
        Long currentTenantId = DictTenantScope.requireCurrentTenantId();
        validateDictCodeAvailability(dto.getDictCode(), currentTenantId, null);

        DictType dictType = new DictType();
        dictType.setDictCode(dto.getDictCode());
        dictType.setDictName(dto.getDictName());
        dictType.setDescription(dto.getDescription());
        dictType.setTenantId(currentTenantId);
        dictType.setCategoryId(dto.getCategoryId());
        dictType.setEnabled(dto.getEnabled() != null ? dto.getEnabled() : true);
        dictType.setSortOrder(dto.getSortOrder() != null ? dto.getSortOrder() : 0);
        dictType.setCreatedAt(LocalDateTime.now());
        dictType.setUpdatedAt(LocalDateTime.now());

        return dictTypeRepository.save(dictType);
    }

    @Override
    @Transactional
    public DictType update(Long id, DictTypeCreateUpdateDto dto) {
        Long currentTenantId = DictTenantScope.requireCurrentTenantId();
        DictType dictType = findMutableType(id, currentTenantId);

        if (!dictType.getDictCode().equals(dto.getDictCode())) {
            validateDictCodeAvailability(dto.getDictCode(), currentTenantId, id);
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
    public void delete(Long id) {
        Long currentTenantId = DictTenantScope.requireCurrentTenantId();
        DictType dictType = findMutableType(id, currentTenantId);

        dictItemRepository.deleteByDictTypeId(id);
        dictTypeRepository.delete(dictType);
    }

    @Override
    @Transactional
    public void batchDelete(List<Long> ids) {
        for (Long id : ids) {
            delete(id);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<DictType> findVisibleTypes() {
        Long currentTenantId = DictTenantScope.requireCurrentTenantId();
        return dictTypeRepository.findVisibleByTenantId(currentTenantId);
    }

    @Override
    public boolean existsByDictCode(String dictCode, Long excludeId) {
        Long currentTenantId = DictTenantScope.requireCurrentTenantId();
        if (excludeId == null) {
            return dictTypeRepository.existsByDictCodeAndTenantId(dictCode, currentTenantId)
                    || dictTypeRepository.existsByDictCodeAndTenantId(dictCode, DictTenantScope.PLATFORM_TENANT_ID);
        }
        return dictTypeRepository.existsByDictCodeAndTenantIdAndIdNot(dictCode, currentTenantId, excludeId)
                || dictTypeRepository.existsByDictCodeAndTenantIdAndIdNot(
                        dictCode,
                        DictTenantScope.PLATFORM_TENANT_ID,
                        excludeId
                );
    }

    private DictType findMutableType(Long id, Long currentTenantId) {
        DictType dictType = findAccessibleType(id, currentTenantId);
        if (DictTenantScope.isPlatformTenant(dictType.getTenantId())) {
            throw new BusinessException(ErrorCode.RESOURCE_STATE_INVALID, "平台字典只读，不允许修改");
        }
        if (Boolean.TRUE.equals(dictType.getBuiltinLocked())) {
            throw new BusinessException(ErrorCode.RESOURCE_STATE_INVALID, "内置字典已锁定，不允许修改");
        }
        return dictType;
    }

    private DictType findAccessibleType(Long id, Long currentTenantId) {
        DictType dictType = dictTypeRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("字典类型不存在: " + id));
        if (!DictTenantScope.isVisibleTenant(dictType.getTenantId(), currentTenantId)) {
            throw new NotFoundException("字典类型不存在: " + id);
        }
        return dictType;
    }

    private void validateDictCodeAvailability(String dictCode, Long currentTenantId, Long excludeId) {
        boolean currentTenantConflict = excludeId == null
                ? dictTypeRepository.existsByDictCodeAndTenantId(dictCode, currentTenantId)
                : dictTypeRepository.existsByDictCodeAndTenantIdAndIdNot(dictCode, currentTenantId, excludeId);
        if (currentTenantConflict) {
            throw new BusinessException(ErrorCode.RESOURCE_ALREADY_EXISTS, "字典编码已存在: " + dictCode);
        }

        boolean platformConflict = excludeId == null
                ? dictTypeRepository.existsByDictCodeAndTenantId(dictCode, DictTenantScope.PLATFORM_TENANT_ID)
                : dictTypeRepository.existsByDictCodeAndTenantIdAndIdNot(
                        dictCode,
                        DictTenantScope.PLATFORM_TENANT_ID,
                        excludeId
                );
        if (platformConflict) {
            throw new BusinessException(ErrorCode.RESOURCE_ALREADY_EXISTS, "字典编码已被平台字典保留: " + dictCode);
        }
    }
}
