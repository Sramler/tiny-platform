package com.tiny.platform.core.dict.service;

import com.tiny.platform.core.dict.dto.DictTypeCreateUpdateDto;
import com.tiny.platform.core.dict.dto.DictTypeQueryDto;
import com.tiny.platform.core.dict.dto.DictTypeResponseDto;
import com.tiny.platform.core.dict.model.DictType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

/**
 * 字典类型服务接口
 */
public interface DictTypeService {

    /**
     * 分页查询字典类型
     */
    Page<DictTypeResponseDto> query(DictTypeQueryDto query, Pageable pageable);

    /**
     * 根据ID查找字典类型
     */
    Optional<DictType> findById(Long id);

    /**
     * 根据字典编码查找
     */
    Optional<DictType> findByDictCode(String dictCode);

    /**
     * 创建字典类型
     */
    DictType create(DictTypeCreateUpdateDto dto);

    /**
     * 更新字典类型
     */
    DictType update(Long id, DictTypeCreateUpdateDto dto);

    /**
     * 删除字典类型
     */
    void delete(Long id);

    /**
     * 批量删除字典类型
     */
    void batchDelete(List<Long> ids);

    /**
     * 根据租户ID查找所有字典类型
     */
    List<DictType> findByTenantId(Long tenantId);

    /**
     * 检查字典编码是否存在
     */
    boolean existsByDictCode(String dictCode, Long excludeId);
}

