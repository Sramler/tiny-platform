package com.tiny.platform.core.dict.service.impl;

import com.tiny.platform.core.dict.dto.DictItemCreateUpdateDto;
import com.tiny.platform.core.dict.dto.DictItemQueryDto;
import com.tiny.platform.core.dict.dto.DictItemResponseDto;
import com.tiny.platform.core.dict.model.DictItem;
import com.tiny.platform.core.dict.model.DictType;
import com.tiny.platform.core.dict.repository.DictItemRepository;
import com.tiny.platform.core.dict.repository.DictTypeRepository;
import com.tiny.platform.core.oauth.tenant.TenantContext;
import com.tiny.platform.infrastructure.auth.datascope.framework.DataScopeContext;
import com.tiny.platform.infrastructure.auth.datascope.framework.ResolvedDataScope;
import com.tiny.platform.infrastructure.auth.org.repository.UserUnitRepository;
import com.tiny.platform.infrastructure.auth.user.repository.TenantUserRepository;
import com.tiny.platform.infrastructure.auth.user.repository.UserRepository;
import com.tiny.platform.infrastructure.core.exception.code.ErrorCode;
import com.tiny.platform.infrastructure.core.exception.exception.BusinessException;
import com.tiny.platform.infrastructure.core.exception.exception.NotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DictItemServiceImplTest {

    private final DictItemRepository dictItemRepository = mock(DictItemRepository.class);
    private final DictTypeRepository dictTypeRepository = mock(DictTypeRepository.class);
    private final TenantUserRepository tenantUserRepository = mock(TenantUserRepository.class);
    private final UserUnitRepository userUnitRepository = mock(UserUnitRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final DictItemServiceImpl service = new DictItemServiceImpl(
            dictItemRepository,
            dictTypeRepository,
            tenantUserRepository,
            userUnitRepository,
            userRepository
    );

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        DataScopeContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void should_create_platform_overlay_using_current_tenant_context() {
        TenantContext.setActiveTenantId(7L);

        DictType platformType = dictType(10L, null, "ENABLE_STATUS");
        DictItem platformItem = dictItem(1L, 10L, null, "ENABLED", "启用", platformType);
        platformItem.setDescription("平台描述");
        DictItemCreateUpdateDto dto = new DictItemCreateUpdateDto();
        dto.setDictTypeId(10L);
        dto.setValue("ENABLED");
        dto.setLabel("可用");

        when(dictTypeRepository.findById(10L)).thenReturn(Optional.of(platformType));
        when(dictItemRepository.findByDictTypeIdAndValueAndTenantIdIsNull(10L, "ENABLED"))
                .thenReturn(Optional.of(platformItem));
        when(dictItemRepository.existsByDictTypeIdAndValueAndTenantId(10L, "ENABLED", 7L)).thenReturn(false);
        when(dictItemRepository.save(any(DictItem.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DictItem created = service.create(dto);

        assertThat(created.getTenantId()).isEqualTo(7L);
        assertThat(created.getLabel()).isEqualTo("可用");
        assertThat(created.getDescription()).isEqualTo("平台描述");
    }

    @Test
    void should_capture_current_username_when_creating_item() {
        TenantContext.setActiveTenantId(7L);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("dict-admin", "n/a", List.of())
        );

        DictType tenantType = dictType(10L, 7L, "CUSTOM_STATUS");
        DictItemCreateUpdateDto dto = new DictItemCreateUpdateDto();
        dto.setDictTypeId(10L);
        dto.setValue("OPEN");
        dto.setLabel("开启");

        when(dictTypeRepository.findById(10L)).thenReturn(Optional.of(tenantType));
        when(dictItemRepository.existsByDictTypeIdAndValueAndTenantId(10L, "OPEN", 7L)).thenReturn(false);
        when(dictItemRepository.save(any(DictItem.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DictItem created = service.create(dto);

        assertThat(created.getCreatedBy()).isEqualTo("dict-admin");
        assertThat(created.getUpdatedBy()).isEqualTo("dict-admin");
    }

    @Test
    void should_reject_unknown_platform_overlay_value() {
        TenantContext.setActiveTenantId(7L);

        DictType platformType = dictType(10L, null, "ENABLE_STATUS");
        DictItemCreateUpdateDto dto = new DictItemCreateUpdateDto();
        dto.setDictTypeId(10L);
        dto.setValue("UNKNOWN");
        dto.setLabel("未知");

        when(dictTypeRepository.findById(10L)).thenReturn(Optional.of(platformType));
        when(dictItemRepository.findByDictTypeIdAndValueAndTenantIdIsNull(10L, "UNKNOWN"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(dto))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_PARAMETER));
    }

    @Test
    void should_keep_platform_metadata_when_updating_platform_overlay() {
        TenantContext.setActiveTenantId(7L);

        DictType platformType = dictType(10L, null, "ENABLE_STATUS");
        DictItem platformItem = dictItem(1L, 10L, null, "ENABLED", "启用", platformType);
        platformItem.setDescription("平台描述");
        platformItem.setEnabled(false);
        platformItem.setSortOrder(9);
        DictItem tenantOverlay = dictItem(2L, 10L, 7L, "ENABLED", "可用", platformType);

        DictItemCreateUpdateDto dto = new DictItemCreateUpdateDto();
        dto.setDictTypeId(10L);
        dto.setValue("ENABLED");
        dto.setLabel("租户可用");
        dto.setDescription("租户描述");
        dto.setEnabled(true);
        dto.setSortOrder(1);

        when(dictItemRepository.findById(2L)).thenReturn(Optional.of(tenantOverlay));
        when(dictTypeRepository.findById(10L)).thenReturn(Optional.of(platformType));
        when(dictItemRepository.findByDictTypeIdAndValueAndTenantIdIsNull(10L, "ENABLED"))
                .thenReturn(Optional.of(platformItem));
        when(dictItemRepository.save(any(DictItem.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DictItem updated = service.update(2L, dto);

        assertThat(updated.getLabel()).isEqualTo("租户可用");
        assertThat(updated.getDescription()).isEqualTo("平台描述");
        assertThat(updated.getEnabled()).isFalse();
        assertThat(updated.getSortOrder()).isEqualTo(9);
    }

    @Test
    void query_withoutDataScopeRestriction_usesPagedRepositoryPath_forTenantDictType() {
        TenantContext.setActiveTenantId(7L);
        DataScopeContext.clear();

        DictType tenantType = dictType(20L, 7L, "TENANT_ITEMS");
        when(dictTypeRepository.findById(20L)).thenReturn(Optional.of(tenantType));

        DictItemQueryDto query = new DictItemQueryDto();
        query.setDictTypeId(20L);
        Pageable pageable = PageRequest.of(0, 10);
        DictItem row = dictItem(99L, 20L, 7L, "V1", "L1", tenantType);
        when(dictItemRepository.findVisibleByConditions(
                eq(7L),
                eq(20L),
                isNull(),
                isNull(),
                isNull(),
                eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(row), pageable, 1));

        Page<DictItemResponseDto> result = service.query(query, pageable);

        assertThat(result.getContent()).singleElement()
                .satisfies(dto -> assertThat(dto.getValue()).isEqualTo("V1"));
        verify(dictItemRepository).findVisibleByConditions(7L, 20L, null, null, null, pageable);
    }

    @Test
    void should_prefer_tenant_overlay_when_building_dict_map() {
        TenantContext.setActiveTenantId(7L);

        DictType platformType = dictType(10L, null, "ENABLE_STATUS");
        DictItem platformItem = dictItem(1L, 10L, null, "ENABLED", "启用", platformType);
        DictItem tenantOverlay = dictItem(2L, 10L, 7L, "ENABLED", "可用", platformType);

        when(dictTypeRepository.findByDictCodeAndTenantId("ENABLE_STATUS", 7L)).thenReturn(Optional.empty());
        when(dictTypeRepository.findByDictCodeAndTenantIdIsNull("ENABLE_STATUS")).thenReturn(Optional.of(platformType));
        when(dictItemRepository.findVisibleByDictTypeId(10L, 7L)).thenReturn(List.of(platformItem, tenantOverlay));

        Map<String, String> dictMap = service.getDictMap("ENABLE_STATUS");

        assertThat(dictMap).containsEntry("ENABLED", "可用");
    }

    @Test
    void findByDictTypeId_should_fall_back_to_platform_item_when_hidden_overlay_is_filtered() {
        TenantContext.setActiveTenantId(7L);
        DataScopeContext.set(ResolvedDataScope.ofUnitsAndUsers(java.util.Set.of(), java.util.Set.of(2L), false));

        DictType platformType = dictType(10L, null, "ENABLE_STATUS");
        DictItem platformItem = dictItem(1L, 10L, null, "ENABLED", "平台启用", platformType);
        DictItem hiddenOverlay = dictItem(2L, 10L, 7L, "ENABLED", "租户启用", platformType);
        hiddenOverlay.setCreatedBy("bob");

        when(dictTypeRepository.findById(10L)).thenReturn(Optional.of(platformType));
        when(dictItemRepository.findVisibleByDictTypeId(10L, 7L)).thenReturn(List.of(platformItem, hiddenOverlay));
        when(tenantUserRepository.findUserIdsByTenantIdAndUserIdInAndStatus(7L, java.util.Set.of(2L), "ACTIVE"))
                .thenReturn(List.of(2L));
        when(userRepository.findUsernamesByIdIn(java.util.Set.of(2L))).thenReturn(List.of("alice"));

        List<DictItem> result = service.findByDictTypeId(10L);

        assertThat(result).singleElement().satisfies(item -> {
            assertThat(item.getTenantId()).isNull();
            assertThat(item.getLabel()).isEqualTo("平台启用");
        });
    }

    @Test
    void getLabel_returns_tenant_overlay_when_same_value_exists_in_platform_and_tenant() {
        TenantContext.setActiveTenantId(7L);
        DictType platformType = dictType(10L, null, "ENABLE_STATUS");
        DictItem platformItem = dictItem(1L, 10L, null, "ENABLED", "启用", platformType);
        DictItem tenantOverlay = dictItem(2L, 10L, 7L, "ENABLED", "可用", platformType);

        when(dictTypeRepository.findByDictCodeAndTenantId("ENABLE_STATUS", 7L)).thenReturn(Optional.empty());
        when(dictTypeRepository.findByDictCodeAndTenantIdIsNull("ENABLE_STATUS")).thenReturn(Optional.of(platformType));
        when(dictItemRepository.findVisibleByDictTypeId(10L, 7L)).thenReturn(List.of(platformItem, tenantOverlay));

        String label = service.getLabel("ENABLE_STATUS", "ENABLED");

        assertThat(label).isEqualTo("可用");
    }

    @Test
    void findByDictCode_returns_merged_platform_and_tenant_items_with_tenant_override() {
        TenantContext.setActiveTenantId(7L);
        DictType platformType = dictType(10L, null, "ENABLE_STATUS");
        DictItem platformA = dictItem(1L, 10L, null, "A", "平台A", platformType);
        DictItem platformB = dictItem(2L, 10L, null, "B", "平台B", platformType);
        DictItem tenantB = dictItem(3L, 10L, 7L, "B", "租户B", platformType);
        platformB.setDescription("平台描述");
        platformB.setEnabled(false);
        platformB.setSortOrder(7);

        when(dictTypeRepository.findByDictCodeAndTenantId("ENABLE_STATUS", 7L)).thenReturn(Optional.empty());
        when(dictTypeRepository.findByDictCodeAndTenantIdIsNull("ENABLE_STATUS")).thenReturn(Optional.of(platformType));
        when(dictItemRepository.findVisibleByDictTypeId(10L, 7L))
                .thenReturn(List.of(platformA, platformB, tenantB));

        List<DictItem> merged = service.findByDictCode("ENABLE_STATUS");

        assertThat(merged).hasSize(2);
        assertThat(merged.stream().filter(i -> "A".equals(i.getValue())).map(DictItem::getLabel).findFirst())
                .hasValue("平台A");
        DictItem mergedOverlay = merged.stream().filter(i -> "B".equals(i.getValue())).findFirst().orElseThrow();
        assertThat(mergedOverlay.getLabel()).isEqualTo("租户B");
        assertThat(mergedOverlay.getDescription()).isEqualTo("平台描述");
        assertThat(mergedOverlay.getEnabled()).isFalse();
        assertThat(mergedOverlay.getSortOrder()).isEqualTo(7);
        assertThat(mergedOverlay.getTenantId()).isEqualTo(7L);
    }

    @Test
    void should_reject_deleting_platform_item() {
        TenantContext.setActiveTenantId(7L);

        DictType platformType = dictType(10L, null, "ENABLE_STATUS");
        DictItem platformItem = dictItem(1L, 10L, null, "ENABLED", "启用", platformType);

        when(dictItemRepository.findById(1L)).thenReturn(Optional.of(platformItem));
        when(dictTypeRepository.findById(10L)).thenReturn(Optional.of(platformType));

        assertThatThrownBy(() -> service.delete(1L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.RESOURCE_STATE_INVALID));
    }

    @Test
    void should_reject_access_to_other_tenant_custom_type() {
        TenantContext.setActiveTenantId(7L);

        DictType otherTenantType = dictType(20L, 8L, "PRIVATE_STATUS");
        DictItemCreateUpdateDto dto = new DictItemCreateUpdateDto();
        dto.setDictTypeId(20L);
        dto.setValue("A");
        dto.setLabel("Alpha");

        when(dictTypeRepository.findById(20L)).thenReturn(Optional.of(otherTenantType));

        assertThatThrownBy(() -> service.create(dto))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void findById_whenItemBelongsToOtherTenant_returnsEmpty() {
        TenantContext.setActiveTenantId(7L);
        DictType otherTenantType = dictType(20L, 8L, "PRIVATE_STATUS");
        DictItem otherTenantItem = dictItem(100L, 20L, 8L, "X", "其他租户项", otherTenantType);
        when(dictItemRepository.findById(100L)).thenReturn(Optional.of(otherTenantItem));
        when(dictTypeRepository.findById(20L)).thenReturn(Optional.of(otherTenantType));

        assertThat(service.findById(100L)).isEmpty();
    }

    @Test
    void update_whenItemBelongsToOtherTenant_throwsNotFound() {
        TenantContext.setActiveTenantId(7L);
        DictType otherTenantType = dictType(20L, 8L, "PRIVATE_STATUS");
        DictItem otherTenantItem = dictItem(100L, 20L, 8L, "X", "其他租户项", otherTenantType);
        DictItemCreateUpdateDto dto = new DictItemCreateUpdateDto();
        dto.setDictTypeId(20L);
        dto.setValue("X");
        dto.setLabel("Updated");
        when(dictItemRepository.findById(100L)).thenReturn(Optional.of(otherTenantItem));
        when(dictTypeRepository.findById(20L)).thenReturn(Optional.of(otherTenantType));

        assertThatThrownBy(() -> service.update(100L, dto))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("字典项不存在");
    }

    @Test
    void delete_whenItemBelongsToOtherTenant_throwsNotFound() {
        TenantContext.setActiveTenantId(7L);
        DictType otherTenantType = dictType(20L, 8L, "PRIVATE_STATUS");
        DictItem otherTenantItem = dictItem(100L, 20L, 8L, "X", "其他租户项", otherTenantType);
        when(dictItemRepository.findById(100L)).thenReturn(Optional.of(otherTenantItem));
        when(dictTypeRepository.findById(20L)).thenReturn(Optional.of(otherTenantType));

        assertThatThrownBy(() -> service.delete(100L))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("字典项不存在");
    }

    private static DictType dictType(Long id, Long tenantId, String dictCode) {
        DictType dictType = new DictType();
        dictType.setId(id);
        dictType.setTenantId(tenantId);
        dictType.setDictCode(dictCode);
        dictType.setDictName(dictCode + "-name");
        dictType.setSortOrder(id.intValue());
        return dictType;
    }

    private static DictItem dictItem(
            Long id,
            Long dictTypeId,
            Long tenantId,
            String value,
            String label,
            DictType dictType
    ) {
        DictItem dictItem = new DictItem();
        dictItem.setId(id);
        dictItem.setDictTypeId(dictTypeId);
        dictItem.setTenantId(tenantId);
        dictItem.setValue(value);
        dictItem.setLabel(label);
        dictItem.setDictType(dictType);
        return dictItem;
    }
}
