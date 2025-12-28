package com.tiny.platform.core.dict.service;

import com.tiny.platform.core.dict.dto.DictItemCreateUpdateDto;
import com.tiny.platform.core.dict.dto.DictItemQueryDto;
import com.tiny.platform.core.dict.dto.DictItemResponseDto;
import com.tiny.platform.core.dict.model.DictItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 字典项服务接口
 */
public interface DictItemService {

    /**
     * 分页查询字典项
     */
    Page<DictItemResponseDto> query(DictItemQueryDto query, Pageable pageable);

    /**
     * 根据ID查找字典项
     */
    Optional<DictItem> findById(Long id);

    /**
     * 根据字典类型ID查找所有字典项
     */
    List<DictItem> findByDictTypeId(Long dictTypeId);

    /**
     * 根据字典编码查找字典项（支持多租户）
     */
    List<DictItem> findByDictCode(String dictCode, Long tenantId);

    /**
     * 根据字典编码获取字典映射（value -> label）
     */
    Map<String, String> getDictMap(String dictCode, Long tenantId);

    /**
     * 根据字典编码和值获取标签
     */
    String getLabel(String dictCode, String value, Long tenantId);

    /**
     * 创建字典项
     */
    DictItem create(DictItemCreateUpdateDto dto);

    /**
     * 更新字典项
     */
    DictItem update(Long id, DictItemCreateUpdateDto dto);

    /**
     * 删除字典项
     */
    void delete(Long id);

    /**
     * 批量删除字典项
     */
    void batchDelete(List<Long> ids);

    /**
     * 根据字典类型ID删除所有字典项
     */
    void deleteByDictTypeId(Long dictTypeId);
}

