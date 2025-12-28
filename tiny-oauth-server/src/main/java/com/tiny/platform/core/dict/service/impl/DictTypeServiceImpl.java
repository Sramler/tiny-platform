package com.tiny.platform.core.dict.service.impl;

import com.tiny.platform.core.dict.dto.DictTypeCreateUpdateDto;
import com.tiny.platform.core.dict.dto.DictTypeQueryDto;
import com.tiny.platform.core.dict.dto.DictTypeResponseDto;
import com.tiny.platform.core.dict.model.DictType;
import com.tiny.platform.core.dict.repository.DictItemRepository;
import com.tiny.platform.core.dict.repository.DictTypeRepository;
import com.tiny.platform.core.dict.service.DictTypeService;
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
    public Page<DictTypeResponseDto> query(DictTypeQueryDto query, Pageable pageable) {
        Page<DictType> page = dictTypeRepository.findByConditions(
                StringUtils.hasText(query.getDictCode()) ? query.getDictCode() : null,
                StringUtils.hasText(query.getDictName()) ? query.getDictName() : null,
                query.getTenantId(),
                query.getEnabled(),
                pageable
        );
        return page.map(DictTypeResponseDto::new);
    }

    @Override
    public Optional<DictType> findById(Long id) {
        return dictTypeRepository.findById(id);
    }

    @Override
    public Optional<DictType> findByDictCode(String dictCode) {
        return dictTypeRepository.findByDictCode(dictCode);
    }

    @Override
    @Transactional
    public DictType create(DictTypeCreateUpdateDto dto) {
        // 检查字典编码是否已存在
        if (dictTypeRepository.existsByDictCode(dto.getDictCode())) {
            throw new BusinessException(ErrorCode.RESOURCE_ALREADY_EXISTS, "字典编码已存在: " + dto.getDictCode());
        }

        DictType dictType = new DictType();
        dictType.setDictCode(dto.getDictCode());
        dictType.setDictName(dto.getDictName());
        dictType.setDescription(dto.getDescription());
        dictType.setTenantId(dto.getTenantId() != null ? dto.getTenantId() : 0L);
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
        DictType dictType = dictTypeRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("字典类型不存在: " + id));

        // 检查是否为锁定的内置字典，锁定后不允许修改关键字段
        if (Boolean.TRUE.equals(dictType.getBuiltinLocked())) {
            // 锁定后不允许修改字典编码
            if (!dictType.getDictCode().equals(dto.getDictCode())) {
                throw new BusinessException(ErrorCode.RESOURCE_STATE_INVALID, "内置字典已锁定，不允许修改字典编码");
            }
        }

        // 检查字典编码是否已被其他字典类型使用
        if (!dictType.getDictCode().equals(dto.getDictCode()) &&
            dictTypeRepository.existsByDictCodeAndIdNot(dto.getDictCode(), id)) {
            throw new BusinessException(ErrorCode.RESOURCE_ALREADY_EXISTS, "字典编码已被使用: " + dto.getDictCode());
        }

        dictType.setDictCode(dto.getDictCode());
        dictType.setDictName(dto.getDictName());
        dictType.setDescription(dto.getDescription());
        dictType.setCategoryId(dto.getCategoryId());
        dictType.setEnabled(dto.getEnabled());
        dictType.setSortOrder(dto.getSortOrder());
        dictType.setUpdatedAt(LocalDateTime.now());

        return dictTypeRepository.save(dictType);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        DictType dictType = dictTypeRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("字典类型不存在: " + id));

        // 检查是否为锁定的内置字典
        if (Boolean.TRUE.equals(dictType.getBuiltinLocked())) {
            throw new BusinessException(ErrorCode.RESOURCE_STATE_INVALID, "内置字典已锁定，不允许删除");
        }

        // 删除关联的字典项（级联删除）
        dictItemRepository.deleteByDictTypeId(id);

        // 删除字典类型
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
    public List<DictType> findByTenantId(Long tenantId) {
        return dictTypeRepository.findByTenantIdOrderBySortOrderAsc(tenantId);
    }

    @Override
    public boolean existsByDictCode(String dictCode, Long excludeId) {
        if (excludeId == null) {
            return dictTypeRepository.existsByDictCode(dictCode);
        }
        return dictTypeRepository.existsByDictCodeAndIdNot(dictCode, excludeId);
    }
}

