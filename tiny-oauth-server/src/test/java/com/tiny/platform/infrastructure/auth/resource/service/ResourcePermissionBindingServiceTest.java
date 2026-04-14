package com.tiny.platform.infrastructure.auth.resource.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.tiny.platform.infrastructure.auth.resource.domain.Resource;
import com.tiny.platform.infrastructure.core.exception.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.List;

class ResourcePermissionBindingServiceTest {

    @Test
    void bindResource_should_leave_binding_empty_when_permission_blank_and_id_missing() {
        NamedParameterJdbcTemplate jdbcTemplate = Mockito.mock(NamedParameterJdbcTemplate.class);
        ResourcePermissionBindingService service = new ResourcePermissionBindingService(jdbcTemplate);

        Resource resource = new Resource();
        resource.setPermission("   ");

        service.bindResource(resource, 7L);

        assertThat(resource.getRequiredPermissionId()).isNull();
        assertThat(resource.getPermission()).isEqualTo("   ");
        verifyNoMoreInteractions(jdbcTemplate);
    }

    @Test
    void bindResource_should_resolve_permission_code_from_explicit_required_permission_id() {
        NamedParameterJdbcTemplate jdbcTemplate = Mockito.mock(NamedParameterJdbcTemplate.class);
        ResourcePermissionBindingService service = new ResourcePermissionBindingService(jdbcTemplate);

        Resource resource = new Resource();
        resource.setTenantId(2L);
        resource.setRequiredPermissionId(88L);

        when(jdbcTemplate.query(
            any(String.class),
            any(MapSqlParameterSource.class),
            Mockito.<RowMapper<String>>any()
        )).thenReturn(List.of("system:user:list"));

        service.bindResource(resource, 7L);

        assertThat(resource.getRequiredPermissionId()).isEqualTo(88L);
        assertThat(resource.getPermission()).isEqualTo("system:user:list");
        verify(jdbcTemplate).query(
            any(String.class),
            any(MapSqlParameterSource.class),
            Mockito.<RowMapper<String>>any()
        );
    }

    @Test
    void bindResource_should_reject_unknown_required_permission_id_in_current_tenant_scope() {
        NamedParameterJdbcTemplate jdbcTemplate = Mockito.mock(NamedParameterJdbcTemplate.class);
        ResourcePermissionBindingService service = new ResourcePermissionBindingService(jdbcTemplate);

        Resource resource = new Resource();
        resource.setTenantId(2L);
        resource.setRequiredPermissionId(88L);

        when(jdbcTemplate.query(
            any(String.class),
            any(MapSqlParameterSource.class),
            Mockito.<RowMapper<String>>any()
        )).thenReturn(List.of());

        assertThatThrownBy(() -> service.bindResource(resource, 7L))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("requiredPermissionId does not exist");
    }

    @Test
    void bindResource_should_resolve_required_permission_id_for_existing_permission_only() {
        NamedParameterJdbcTemplate jdbcTemplate = Mockito.mock(NamedParameterJdbcTemplate.class);
        ResourcePermissionBindingService service = new ResourcePermissionBindingService(jdbcTemplate);

        Resource resource = new Resource();
        resource.setTenantId(2L);
        resource.setPermission("system:user:list");

        when(jdbcTemplate.query(
            any(String.class),
            any(MapSqlParameterSource.class),
            Mockito.<RowMapper<Long>>any()
        )).thenReturn(List.of(88L));

        service.bindResource(resource, 7L);

        assertThat(resource.getRequiredPermissionId()).isEqualTo(88L);
        assertThat(resource.getPermission()).isEqualTo("system:user:list");
        verify(jdbcTemplate).query(
            any(String.class),
            any(MapSqlParameterSource.class),
            Mockito.<RowMapper<Long>>any()
        );
    }
}
