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
import com.tiny.platform.infrastructure.tenant.domain.Tenant;
import com.tiny.platform.infrastructure.tenant.repository.TenantRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DictPlatformAdminServiceImplTest {

    private final DictTypeRepository dictTypeRepository = mock(DictTypeRepository.class);
    private final DictItemRepository dictItemRepository = mock(DictItemRepository.class);
    private final TenantRepository tenantRepository = mock(TenantRepository.class);
    private final DictPlatformAdminServiceImpl service =
            new DictPlatformAdminServiceImpl(dictTypeRepository, dictItemRepository, tenantRepository);

    @Test
    void createType_should_persist_platform_tenant() {
        DictTypeCreateUpdateDto dto = new DictTypeCreateUpdateDto();
        dto.setDictCode("ENABLE_STATUS");
        dto.setDictName("启用状态");

        when(dictTypeRepository.existsByDictCodeAndTenantIdIsNull("ENABLE_STATUS")).thenReturn(false);
        when(dictTypeRepository.save(any(DictType.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DictType created = service.createType(dto);

        assertThat(created.getTenantId()).isNull();
        assertThat(created.getDictCode()).isEqualTo("ENABLE_STATUS");
    }

    @Test
    void updateType_when_builtin_locked_should_reject() {
        DictType dictType = new DictType();
        dictType.setId(1L);
        dictType.setTenantId(null);
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
        platformType.setTenantId(null);

        DictItemCreateUpdateDto dto = new DictItemCreateUpdateDto();
        dto.setDictTypeId(10L);
        dto.setValue("A");
        dto.setLabel("Alpha");

        when(dictTypeRepository.findById(10L)).thenReturn(Optional.of(platformType));
        when(dictItemRepository.existsByDictTypeIdAndValueAndTenantIdIsNull(10L, "A")).thenReturn(false);
        when(dictItemRepository.save(any(DictItem.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DictItem created = service.createItem(dto);

        assertThat(created.getTenantId()).isNull();
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

    @Test
    void findTypeOverrideSummaries_should_return_overridden_and_inherited_counts() {
        DictType platformType = new DictType();
        platformType.setId(10L);
        platformType.setTenantId(null);

        DictItem baseA = new DictItem();
        baseA.setId(1L);
        baseA.setDictTypeId(10L);
        baseA.setTenantId(null);
        baseA.setValue("A");
        baseA.setLabel("平台A");
        DictItem baseB = new DictItem();
        baseB.setId(2L);
        baseB.setDictTypeId(10L);
        baseB.setTenantId(null);
        baseB.setValue("B");
        baseB.setLabel("平台B");

        DictItem overlayA = new DictItem();
        overlayA.setId(3L);
        overlayA.setDictTypeId(10L);
        overlayA.setTenantId(7L);
        overlayA.setValue("A");
        overlayA.setLabel("租户A");

        when(dictTypeRepository.findById(10L)).thenReturn(Optional.of(platformType));
        when(dictItemRepository.findPlatformByDictTypeId(10L)).thenReturn(List.of(baseA, baseB));
        when(dictItemRepository.findByDictTypeIdAndTenantIdIsNotNull(10L)).thenReturn(List.of(overlayA));
        Tenant tenant = new Tenant();
        tenant.setId(7L);
        tenant.setCode("t-7");
        tenant.setName("tenant-7");
        when(tenantRepository.findAll()).thenReturn(List.of(tenant));

        var summary = service.findTypeOverrideSummaries(10L);

        assertThat(summary).hasSize(1);
        assertThat(summary.get(0).getTenantId()).isEqualTo(7L);
        assertThat(summary.get(0).getBaselineCount()).isEqualTo(2);
        assertThat(summary.get(0).getOverriddenCount()).isEqualTo(1);
        assertThat(summary.get(0).getInheritedCount()).isEqualTo(1);
        assertThat(summary.get(0).getOrphanOverlayCount()).isEqualTo(0);
    }

    @Test
    void findTypeOverrideSummaries_should_not_count_orphan_overlay_as_overridden() {
        DictType platformType = new DictType();
        platformType.setId(10L);
        platformType.setTenantId(null);

        DictItem baseA = new DictItem();
        baseA.setDictTypeId(10L);
        baseA.setTenantId(null);
        baseA.setValue("A");
        DictItem baseB = new DictItem();
        baseB.setDictTypeId(10L);
        baseB.setTenantId(null);
        baseB.setValue("B");

        DictItem orphanOverlay = new DictItem();
        orphanOverlay.setDictTypeId(10L);
        orphanOverlay.setTenantId(7L);
        orphanOverlay.setValue("X");
        orphanOverlay.setLabel("孤儿覆盖");

        Tenant tenant = new Tenant();
        tenant.setId(7L);
        tenant.setCode("t-7");
        tenant.setName("tenant-7");

        when(dictTypeRepository.findById(10L)).thenReturn(Optional.of(platformType));
        when(dictItemRepository.findPlatformByDictTypeId(10L)).thenReturn(List.of(baseA, baseB));
        when(dictItemRepository.findByDictTypeIdAndTenantIdIsNotNull(10L)).thenReturn(List.of(orphanOverlay));
        when(tenantRepository.findAll()).thenReturn(List.of(tenant));

        var summary = service.findTypeOverrideSummaries(10L);

        assertThat(summary).hasSize(1);
        assertThat(summary.get(0).getOverriddenCount()).isEqualTo(0);
        assertThat(summary.get(0).getInheritedCount()).isEqualTo(2);
        assertThat(summary.get(0).getOrphanOverlayCount()).isEqualTo(1);
    }

    @Test
    void findTypeOverrideSummaries_should_include_tenant_with_full_inheritance() {
        DictType platformType = new DictType();
        platformType.setId(10L);
        platformType.setTenantId(null);

        DictItem baseA = new DictItem();
        baseA.setDictTypeId(10L);
        baseA.setTenantId(null);
        baseA.setValue("A");

        Tenant tenant = new Tenant();
        tenant.setId(8L);
        tenant.setCode("t-8");
        tenant.setName("tenant-8");

        when(dictTypeRepository.findById(10L)).thenReturn(Optional.of(platformType));
        when(dictItemRepository.findPlatformByDictTypeId(10L)).thenReturn(List.of(baseA));
        when(dictItemRepository.findByDictTypeIdAndTenantIdIsNotNull(10L)).thenReturn(List.of());
        when(tenantRepository.findAll()).thenReturn(List.of(tenant));

        var summary = service.findTypeOverrideSummaries(10L);

        assertThat(summary).hasSize(1);
        assertThat(summary.get(0).getTenantId()).isEqualTo(8L);
        assertThat(summary.get(0).getOverriddenCount()).isEqualTo(0);
        assertThat(summary.get(0).getInheritedCount()).isEqualTo(1);
        assertThat(summary.get(0).getOrphanOverlayCount()).isEqualTo(0);
    }

    @Test
    void findTypeOverrideDetails_should_mark_inherited_and_overridden() {
        DictType platformType = new DictType();
        platformType.setId(10L);
        platformType.setTenantId(null);

        DictItem baseA = new DictItem();
        baseA.setId(1L);
        baseA.setSortOrder(1);
        baseA.setDictTypeId(10L);
        baseA.setTenantId(null);
        baseA.setValue("A");
        baseA.setLabel("平台A");
        DictItem baseB = new DictItem();
        baseB.setId(2L);
        baseB.setSortOrder(2);
        baseB.setDictTypeId(10L);
        baseB.setTenantId(null);
        baseB.setValue("B");
        baseB.setLabel("平台B");

        DictItem overlayA = new DictItem();
        overlayA.setId(3L);
        overlayA.setSortOrder(1);
        overlayA.setDictTypeId(10L);
        overlayA.setTenantId(7L);
        overlayA.setValue("A");
        overlayA.setLabel("租户A");

        when(dictTypeRepository.findById(10L)).thenReturn(Optional.of(platformType));
        when(dictItemRepository.findPlatformByDictTypeId(10L)).thenReturn(List.of(baseA, baseB));
        when(dictItemRepository.findByDictTypeIdAndTenantId(10L, 7L)).thenReturn(List.of(overlayA));
        Tenant tenant = new Tenant();
        tenant.setId(7L);
        when(tenantRepository.findById(7L)).thenReturn(Optional.of(tenant));

        var details = service.findTypeOverrideDetails(10L, 7L);

        assertThat(details).hasSize(2);
        assertThat(details.get(0).getStatus()).isEqualTo("OVERRIDDEN");
        assertThat(details.get(0).getBaselineLabel()).isEqualTo("平台A");
        assertThat(details.get(0).getOverlayLabel()).isEqualTo("租户A");
        assertThat(details.get(0).isLabelChanged()).isTrue();
        assertThat(details.get(1).getStatus()).isEqualTo("INHERITED");
        assertThat(details.get(1).getEffectiveLabel()).isEqualTo("平台B");
    }

    @Test
    void findTypeOverrideDetails_should_reject_nonexistent_tenant() {
        DictType platformType = new DictType();
        platformType.setId(10L);
        platformType.setTenantId(null);
        when(dictTypeRepository.findById(10L)).thenReturn(Optional.of(platformType));
        when(tenantRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findTypeOverrideDetails(10L, 404L))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("租户不存在");
    }
}
