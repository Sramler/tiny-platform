package com.tiny.platform.infrastructure.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.tiny.platform.infrastructure.auth.resource.domain.Resource;
import com.tiny.platform.infrastructure.auth.resource.dto.ResourceResponseDto;
import com.tiny.platform.infrastructure.auth.resource.enums.ResourceType;
import com.tiny.platform.infrastructure.auth.role.domain.Role;
import com.tiny.platform.infrastructure.auth.role.dto.RoleResponseDto;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TenantNamingJsonContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void raw_entities_should_serialize_record_tenant_id_without_legacy_tenant_id() throws Exception {
        Role role = new Role();
        role.setId(1L);
        role.setTenantId(7L);
        role.setName("role");
        role.setCode("ROLE_X");

        Resource resource = new Resource();
        resource.setId(2L);
        resource.setTenantId(8L);
        resource.setName("resource");
        resource.setTitle("Resource");
        resource.setType(ResourceType.MENU);

        assertRecordTenantJson(role, 7L);
        assertRecordTenantJson(resource, 8L);
    }

    @Test
    void control_plane_dtos_should_serialize_record_tenant_id() throws Exception {
        RoleResponseDto roleDto = new RoleResponseDto(1L, "Role", "ROLE_X", "desc", false, true, null, null);
        roleDto.setRecordTenantId(7L);

        ResourceResponseDto resourceDto = new ResourceResponseDto();
        resourceDto.setId(2L);
        resourceDto.setName("resource");
        resourceDto.setRecordTenantId(8L);

        assertRecordTenantJson(roleDto, 7L);
        assertRecordTenantJson(resourceDto, 8L);
    }

    private void assertRecordTenantJson(Object value, long expectedTenantId) throws Exception {
        String json = objectMapper.writeValueAsString(value);
        assertThat(json).contains("\"recordTenantId\":" + expectedTenantId);
        assertThat(json).doesNotContain("\"tenantId\":");
    }
}
