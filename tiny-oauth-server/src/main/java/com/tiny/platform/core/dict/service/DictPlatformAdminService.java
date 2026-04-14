package com.tiny.platform.core.dict.service;

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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 平台字典管理服务接口。
 */
public interface DictPlatformAdminService {

    Page<DictTypeResponseDto> queryTypes(DictTypeQueryDto query, Pageable pageable);

    Optional<DictType> findTypeById(Long id);

    Optional<DictType> findTypeByCode(String dictCode);

    DictType createType(DictTypeCreateUpdateDto dto);

    DictType updateType(Long id, DictTypeCreateUpdateDto dto);

    void deleteType(Long id);

    void batchDeleteTypes(List<Long> ids);

    Page<DictItemResponseDto> queryItems(DictItemQueryDto query, Pageable pageable);

    Optional<DictItem> findItemById(Long id);

    List<DictItem> findItemsByType(Long dictTypeId);

    List<DictItem> findItemsByCode(String dictCode);

    List<PlatformDictOverrideSummaryDto> findTypeOverrideSummaries(Long dictTypeId);

    List<PlatformDictOverrideDetailDto> findTypeOverrideDetails(Long dictTypeId, Long tenantId);

    Map<String, String> getDictMap(String dictCode);

    String getLabel(String dictCode, String value);

    DictItem createItem(DictItemCreateUpdateDto dto);

    DictItem updateItem(Long id, DictItemCreateUpdateDto dto);

    void deleteItem(Long id);

    void batchDeleteItems(List<Long> ids);
}
