package com.tiny.platform.infrastructure.auth.resource.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tiny.platform.infrastructure.auth.resource.domain.Resource;
import com.tiny.platform.infrastructure.auth.resource.enums.ResourceType;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

class ResourcePermissionBindingServiceTest {

    @Test
    void bindResource_should_clear_required_permission_id_when_permission_blank() {
        NamedParameterJdbcTemplate jdbcTemplate = Mockito.mock(NamedParameterJdbcTemplate.class);
        ResourcePermissionBindingService service = new ResourcePermissionBindingService(jdbcTemplate);

        Resource resource = new Resource();
        resource.setPermission("   ");
        resource.setRequiredPermissionId(99L);

        service.bindResource(resource, 7L);

        assertThat(resource.getRequiredPermissionId()).isNull();
        verify(jdbcTemplate, never()).update(any(String.class), any(MapSqlParameterSource.class));
    }

    @Test
    void bindResource_should_resolve_required_permission_id_for_non_blank_permission() {
        NamedParameterJdbcTemplate jdbcTemplate = Mockito.mock(NamedParameterJdbcTemplate.class);
        ResourcePermissionBindingService service = new ResourcePermissionBindingService(jdbcTemplate);

        Resource resource = new Resource();
        resource.setTenantId(2L);
        resource.setType(ResourceType.MENU);
        resource.setEnabled(true);
        resource.setPermission("system:user:list");

        when(jdbcTemplate.queryForObject(any(String.class), any(MapSqlParameterSource.class), Mockito.eq(Long.class)))
            .thenReturn(88L);

        service.bindResource(resource, 7L);

        assertThat(resource.getRequiredPermissionId()).isEqualTo(88L);
        verify(jdbcTemplate).update(any(String.class), any(MapSqlParameterSource.class));
        verify(jdbcTemplate).queryForObject(any(String.class), any(MapSqlParameterSource.class), Mockito.eq(Long.class));
    }
}
