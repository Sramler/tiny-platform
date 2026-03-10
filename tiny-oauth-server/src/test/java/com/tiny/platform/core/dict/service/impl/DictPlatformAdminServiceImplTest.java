package com.tiny.platform.core.dict.service.impl;

import com.tiny.platform.core.dict.dto.DictItemCreateUpdateDto;
import com.tiny.platform.core.dict.dto.DictTypeCreateUpdateDto;
import com.tiny.platform.core.dict.model.DictItem;
import com.tiny.platform.core.dict.model.DictType;
import com.tiny.platform.core.dict.repository.DictItemRepository;
import com.tiny.platform.core.dict.repository.DictTypeRepository;
import com.tiny.platform.infrastructure.core.exception.code.ErrorCode;
import com.tiny.platform.infrastructure.core.exception.exception.BusinessException;
import com.tiny.platform.infrastructure.core.exception.exception.NotFoundException;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DictPlatformAdminServiceImplTest {

    private final DictTypeRepository dictTypeRepository = mock(DictTypeRepository.class);
    private final DictItemRepository dictItemRepository = mock(DictItemRepository.class);
    private final DictPlatformAdminServiceImpl service =
            new DictPlatformAdminServiceImpl(dictTypeRepository, dictItemRepository);

    @Test
    void createType_should_persist_platform_tenant() {
        DictTypeCreateUpdateDto dto = new DictTypeCreateUpdateDto();
        dto.setDictCode("ENABLE_STATUS");
        dto.setDictName("启用状态");

        when(dictTypeRepository.existsByDictCodeAndTenantId("ENABLE_STATUS", 0L)).thenReturn(false);
        when(dictTypeRepository.save(any(DictType.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DictType created = service.createType(dto);

        assertThat(created.getTenantId()).isZero();
        assertThat(created.getDictCode()).isEqualTo("ENABLE_STATUS");
    }

    @Test
    void updateType_when_builtin_locked_should_reject() {
        DictType dictType = new DictType();
        dictType.setId(1L);
        dictType.setTenantId(0L);
        dictType.setDictCode("ENABLE_STATUS");
        dictType.setBuiltinLocked(true);

        DictTypeCreateUpdateDto dto = new DictTypeCreateUpdateDto();
        dto.setDictCode("ENABLE_STATUS");
        dto.setDictName("启用状态");

        when(dictTypeRepository.findById(1L)).thenReturn(Optional.of(dictType));

        assertThatThrownBy(() -> service.updateType(1L, dto))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.RESOURCE_STATE_INVALID));
    }

    @Test
    void createItem_should_reject_non_platform_type() {
        DictType tenantType = new DictType();
        tenantType.setId(10L);
        tenantType.setTenantId(7L);

        DictItemCreateUpdateDto dto = new DictItemCreateUpdateDto();
        dto.setDictTypeId(10L);
        dto.setValue("A");
        dto.setLabel("Alpha");

        when(dictTypeRepository.findById(10L)).thenReturn(Optional.of(tenantType));

        assertThatThrownBy(() -> service.createItem(dto))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("平台字典类型不存在");
    }

    @Test
    void createItem_should_use_platform_tenant() {
        DictType platformType = new DictType();
        platformType.setId(10L);
        platformType.setTenantId(0L);

        DictItemCreateUpdateDto dto = new DictItemCreateUpdateDto();
        dto.setDictTypeId(10L);
        dto.setValue("A");
        dto.setLabel("Alpha");

        when(dictTypeRepository.findById(10L)).thenReturn(Optional.of(platformType));
        when(dictItemRepository.existsByDictTypeIdAndValueAndTenantId(10L, "A", 0L)).thenReturn(false);
        when(dictItemRepository.save(any(DictItem.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DictItem created = service.createItem(dto);

        assertThat(created.getTenantId()).isZero();
        assertThat(created.getLabel()).isEqualTo("Alpha");
    }

    @Test
    void deleteItem_should_reject_tenant_overlay_item() {
        DictItem tenantOverlay = new DictItem();
        tenantOverlay.setId(1L);
        tenantOverlay.setDictTypeId(10L);
        tenantOverlay.setTenantId(7L);

        when(dictItemRepository.findById(1L)).thenReturn(Optional.of(tenantOverlay));

        assertThatThrownBy(() -> service.deleteItem(1L))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("平台字典项不存在");
    }
}
