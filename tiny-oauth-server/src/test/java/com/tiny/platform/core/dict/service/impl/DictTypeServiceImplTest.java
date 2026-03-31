package com.tiny.platform.core.dict.service.impl;

import com.tiny.platform.core.dict.dto.DictTypeCreateUpdateDto;
import com.tiny.platform.core.dict.dto.DictTypeQueryDto;
import com.tiny.platform.core.dict.dto.DictTypeResponseDto;
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
import org.springframework.data.domain.Sort;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DictTypeServiceImplTest {

    private final DictTypeRepository dictTypeRepository = mock(DictTypeRepository.class);
    private final DictItemRepository dictItemRepository = mock(DictItemRepository.class);
    private final TenantUserRepository tenantUserRepository = mock(TenantUserRepository.class);
    private final UserUnitRepository userUnitRepository = mock(UserUnitRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final DictTypeServiceImpl service = new DictTypeServiceImpl(
            dictTypeRepository,
            dictItemRepository,
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
    void should_use_tenant_context_for_query_instead_of_query_tenant_id() {
        TenantContext.setActiveTenantId(7L);

        DictTypeQueryDto query = new DictTypeQueryDto();
        Pageable pageable = PageRequest.of(0, 10);
        when(dictTypeRepository.findVisibleByConditions(null, null, 7L, null, pageable))
                .thenReturn(Page.empty(pageable));

        service.query(query, pageable);

        verify(dictTypeRepository).findVisibleByConditions(null, null, 7L, null, pageable);
    }

    @Test
    void should_create_type_using_current_tenant_context() {
        TenantContext.setActiveTenantId(7L);

        DictTypeCreateUpdateDto dto = new DictTypeCreateUpdateDto();
        dto.setDictCode("CUSTOM_STATUS");
        dto.setDictName("自定义状态");

        when(dictTypeRepository.existsByDictCodeAndTenantId("CUSTOM_STATUS", 7L)).thenReturn(false);
        when(dictTypeRepository.existsByDictCodeAndTenantIdIsNull("CUSTOM_STATUS")).thenReturn(false);
        when(dictTypeRepository.save(any(DictType.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DictType created = service.create(dto);

        assertThat(created.getTenantId()).isEqualTo(7L);
        assertThat(created.getDictCode()).isEqualTo("CUSTOM_STATUS");
    }

    @Test
    void should_capture_current_username_when_creating_type() {
        TenantContext.setActiveTenantId(7L);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("dict-admin", "n/a", List.of())
        );

        DictTypeCreateUpdateDto dto = new DictTypeCreateUpdateDto();
        dto.setDictCode("AUDIT_STATUS");
        dto.setDictName("审计状态");

        when(dictTypeRepository.existsByDictCodeAndTenantId("AUDIT_STATUS", 7L)).thenReturn(false);
        when(dictTypeRepository.existsByDictCodeAndTenantIdIsNull("AUDIT_STATUS")).thenReturn(false);
        when(dictTypeRepository.save(any(DictType.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DictType created = service.create(dto);

        assertThat(created.getCreatedBy()).isEqualTo("dict-admin");
        assertThat(created.getUpdatedBy()).isEqualTo("dict-admin");
    }

    @Test
    void should_reject_creating_type_when_code_is_reserved_by_platform() {
        TenantContext.setActiveTenantId(7L);

        DictTypeCreateUpdateDto dto = new DictTypeCreateUpdateDto();
        dto.setDictCode("ENABLE_STATUS");
        dto.setDictName("启用状态覆盖");

        when(dictTypeRepository.existsByDictCodeAndTenantId("ENABLE_STATUS", 7L)).thenReturn(false);
        when(dictTypeRepository.existsByDictCodeAndTenantIdIsNull("ENABLE_STATUS")).thenReturn(true);

        assertThatThrownBy(() -> service.create(dto))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.RESOURCE_ALREADY_EXISTS));
    }

    @Test
    void should_reject_updating_platform_type() {
        TenantContext.setActiveTenantId(7L);

        DictType platformType = dictType(1L, null, "ENABLE_STATUS");
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
        TenantContext.setActiveTenantId(7L);
        DictType otherTenantType = dictType(1L, 8L, "OTHER_STATUS");
        when(dictTypeRepository.findById(1L)).thenReturn(Optional.of(otherTenantType));

        assertThat(service.findById(1L)).isEmpty();
    }

    @Test
    void query_withoutDataScopeRestriction_usesPagedRepositoryPath() {
        TenantContext.setActiveTenantId(7L);
        DataScopeContext.clear();

        DictTypeQueryDto query = new DictTypeQueryDto();
        Pageable pageable = PageRequest.of(0, 10);
        DictType row = dictType(9L, 7L, "TENANT_ONLY");
        when(dictTypeRepository.findVisibleByConditions(null, null, 7L, null, pageable))
                .thenReturn(new PageImpl<>(List.of(row), pageable, 1));

        Page<DictTypeResponseDto> result = service.query(query, pageable);

        assertThat(result.getContent()).extracting(DictTypeResponseDto::getDictCode).containsExactly("TENANT_ONLY");
        verify(dictTypeRepository).findVisibleByConditions(null, null, 7L, null, pageable);
        verify(dictTypeRepository, never()).findVisibleByConditions(isNull(), isNull(), eq(7L), isNull(), any(Sort.class));
    }

    @Test
    void query_should_keep_platform_types_and_filter_hidden_tenant_types_under_data_scope() {
        TenantContext.setActiveTenantId(7L);
        DataScopeContext.set(ResolvedDataScope.ofUnitsAndUsers(java.util.Set.of(), java.util.Set.of(2L), false));

        DictType platformType = dictType(1L, null, "PLATFORM_STATUS");
        DictType visibleTenantType = dictType(2L, 7L, "VISIBLE_STATUS");
        visibleTenantType.setCreatedBy("alice");
        DictType hiddenTenantType = dictType(3L, 7L, "HIDDEN_STATUS");
        hiddenTenantType.setCreatedBy("bob");

        when(dictTypeRepository.findVisibleByConditions(null, null, 7L, null, Sort.by(Sort.Order.asc("sortOrder"), Sort.Order.asc("id"))))
                .thenReturn(List.of(platformType, visibleTenantType, hiddenTenantType));
        when(tenantUserRepository.findUserIdsByTenantIdAndUserIdInAndStatus(7L, java.util.Set.of(2L), "ACTIVE"))
                .thenReturn(List.of(2L));
        when(userRepository.findUsernamesByIdIn(java.util.Set.of(2L))).thenReturn(List.of("alice"));

        Page<DictTypeResponseDto> result = service.query(new DictTypeQueryDto(), PageRequest.of(0, 10));

        assertThat(result.getContent())
                .extracting(DictTypeResponseDto::getDictCode)
                .containsExactly("PLATFORM_STATUS", "VISIBLE_STATUS");
    }

    @Test
    void update_whenTypeBelongsToOtherTenant_throwsNotFound() {
        TenantContext.setActiveTenantId(7L);
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
        TenantContext.setActiveTenantId(7L);
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
        dictType.setSortOrder(id.intValue());
        return dictType;
    }
}
