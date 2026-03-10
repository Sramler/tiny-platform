package com.tiny.platform.core.dict.service.impl;

import com.tiny.platform.core.dict.dto.DictTypeCreateUpdateDto;
import com.tiny.platform.core.dict.dto.DictTypeQueryDto;
import com.tiny.platform.core.dict.model.DictType;
import com.tiny.platform.core.dict.repository.DictItemRepository;
import com.tiny.platform.core.dict.repository.DictTypeRepository;
import com.tiny.platform.core.oauth.tenant.TenantContext;
import com.tiny.platform.infrastructure.core.exception.code.ErrorCode;
import com.tiny.platform.infrastructure.core.exception.exception.BusinessException;
import com.tiny.platform.infrastructure.core.exception.exception.NotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DictTypeServiceImplTest {

    private final DictTypeRepository dictTypeRepository = mock(DictTypeRepository.class);
    private final DictItemRepository dictItemRepository = mock(DictItemRepository.class);
    private final DictTypeServiceImpl service = new DictTypeServiceImpl(dictTypeRepository, dictItemRepository);

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void should_use_tenant_context_for_query_instead_of_query_tenant_id() {
        TenantContext.setTenantId(7L);

        DictTypeQueryDto query = new DictTypeQueryDto();
        Pageable pageable = PageRequest.of(0, 10);
        when(dictTypeRepository.findVisibleByConditions(null, null, 7L, null, pageable))
                .thenReturn(Page.empty(pageable));

        service.query(query, pageable);

        verify(dictTypeRepository).findVisibleByConditions(null, null, 7L, null, pageable);
    }

    @Test
    void should_create_type_using_current_tenant_context() {
        TenantContext.setTenantId(7L);

        DictTypeCreateUpdateDto dto = new DictTypeCreateUpdateDto();
        dto.setDictCode("CUSTOM_STATUS");
        dto.setDictName("自定义状态");

        when(dictTypeRepository.existsByDictCodeAndTenantId("CUSTOM_STATUS", 7L)).thenReturn(false);
        when(dictTypeRepository.existsByDictCodeAndTenantId("CUSTOM_STATUS", 0L)).thenReturn(false);
        when(dictTypeRepository.save(any(DictType.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DictType created = service.create(dto);

        assertThat(created.getTenantId()).isEqualTo(7L);
        assertThat(created.getDictCode()).isEqualTo("CUSTOM_STATUS");
    }

    @Test
    void should_reject_creating_type_when_code_is_reserved_by_platform() {
        TenantContext.setTenantId(7L);

        DictTypeCreateUpdateDto dto = new DictTypeCreateUpdateDto();
        dto.setDictCode("ENABLE_STATUS");
        dto.setDictName("启用状态覆盖");

        when(dictTypeRepository.existsByDictCodeAndTenantId("ENABLE_STATUS", 7L)).thenReturn(false);
        when(dictTypeRepository.existsByDictCodeAndTenantId("ENABLE_STATUS", 0L)).thenReturn(true);

        assertThatThrownBy(() -> service.create(dto))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.RESOURCE_ALREADY_EXISTS));
    }

    @Test
    void should_reject_updating_platform_type() {
        TenantContext.setTenantId(7L);

        DictType platformType = dictType(1L, 0L, "ENABLE_STATUS");
        DictTypeCreateUpdateDto dto = new DictTypeCreateUpdateDto();
        dto.setDictCode("ENABLE_STATUS");
        dto.setDictName("启用状态");

        when(dictTypeRepository.findById(1L)).thenReturn(Optional.of(platformType));

        assertThatThrownBy(() -> service.update(1L, dto))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.RESOURCE_STATE_INVALID));
    }

    @Test
    void findById_whenTypeBelongsToOtherTenant_returnsEmpty() {
        TenantContext.setTenantId(7L);
        DictType otherTenantType = dictType(1L, 8L, "OTHER_STATUS");
        when(dictTypeRepository.findById(1L)).thenReturn(Optional.of(otherTenantType));

        assertThat(service.findById(1L)).isEmpty();
    }

    @Test
    void update_whenTypeBelongsToOtherTenant_throwsNotFound() {
        TenantContext.setTenantId(7L);
        DictType otherTenantType = dictType(1L, 8L, "OTHER_STATUS");
        DictTypeCreateUpdateDto dto = new DictTypeCreateUpdateDto();
        dto.setDictCode("OTHER_STATUS");
        dto.setDictName("其他");
        when(dictTypeRepository.findById(1L)).thenReturn(Optional.of(otherTenantType));

        assertThatThrownBy(() -> service.update(1L, dto))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("字典类型不存在");
    }

    @Test
    void delete_whenTypeBelongsToOtherTenant_throwsNotFound() {
        TenantContext.setTenantId(7L);
        DictType otherTenantType = dictType(1L, 8L, "OTHER_STATUS");
        when(dictTypeRepository.findById(1L)).thenReturn(Optional.of(otherTenantType));

        assertThatThrownBy(() -> service.delete(1L))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("字典类型不存在");
    }

    private static DictType dictType(Long id, Long tenantId, String dictCode) {
        DictType dictType = new DictType();
        dictType.setId(id);
        dictType.setTenantId(tenantId);
        dictType.setDictCode(dictCode);
        dictType.setDictName(dictCode + "-name");
        return dictType;
    }
}
