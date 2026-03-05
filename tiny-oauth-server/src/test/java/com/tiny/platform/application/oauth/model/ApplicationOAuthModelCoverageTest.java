package com.tiny.platform.application.oauth.model;

import com.tiny.platform.application.oauth.enums.ResourceType;
import com.tiny.platform.application.oauth.validation.PasswordConfirm;
import com.tiny.platform.application.oauth.validation.PasswordConfirmValidator;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApplicationOAuthModelCoverageTest {

    @Test
    void should_cover_resource_type_and_converters() throws Exception {
        assertThat(ResourceType.fromCode(null)).isEqualTo(ResourceType.DIRECTORY);
        assertThat(ResourceType.fromCode(0)).isEqualTo(ResourceType.DIRECTORY);
        assertThat(ResourceType.fromCode(1)).isEqualTo(ResourceType.MENU);
        assertThat(ResourceType.fromCode(2)).isEqualTo(ResourceType.BUTTON);
        assertThat(ResourceType.fromCode(3)).isEqualTo(ResourceType.API);
        assertThat(ResourceType.MENU.getCode()).isEqualTo(1);
        assertThat(ResourceType.MENU.getDescription()).isEqualTo("菜单");
        assertThat(ResourceType.MENU).hasToString("菜单");
        assertThatThrownBy(() -> ResourceType.fromCode(99))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("无效的资源类型 code");

        ResourceTypeConverter typeConverter = new ResourceTypeConverter();
        assertThat(typeConverter.convertToDatabaseColumn(ResourceType.API)).isEqualTo(3);
        assertThat(typeConverter.convertToDatabaseColumn(null)).isNull();
        assertThat(typeConverter.convertToEntityAttribute(1)).isEqualTo(ResourceType.MENU);

        JsonStringConverter jsonConverter = new JsonStringConverter();
        Map<String, Object> payload = new HashMap<>();
        payload.put("name", "demo");
        payload.put("count", 2);
        String json = jsonConverter.convertToDatabaseColumn(payload);
        assertThat(json).contains("\"name\":\"demo\"");
        assertThat(jsonConverter.convertToDatabaseColumn(null)).isEqualTo("{}");
        assertThat(jsonConverter.convertToEntityAttribute(json))
            .containsEntry("name", "demo")
            .containsEntry("count", 2);
        assertThat(jsonConverter.convertToEntityAttribute(null)).isEmpty();
        assertThat(jsonConverter.convertToEntityAttribute("")).isEmpty();
        assertThat(jsonConverter.convertToEntityAttribute("{bad-json")).isEmpty();

        Map<String, Object> recursive = new HashMap<>();
        recursive.put("self", recursive);
        assertThat(jsonConverter.convertToDatabaseColumn(recursive)).isEqualTo("{}");
    }

    @Test
    void should_cover_user_role_and_resource_dtos() throws Exception {
        RoleRequestDto roleRequest = new RoleRequestDto();
        roleRequest.setName("admin");
        roleRequest.setCode("ROLE_ADMIN");
        roleRequest.setDescription("desc");
        assertThat(roleRequest.getName()).isEqualTo("admin");
        assertThat(roleRequest.getCode()).isEqualTo("ROLE_ADMIN");
        assertThat(roleRequest.getDescription()).isEqualTo("desc");

        RoleCreateUpdateDto roleCreate = new RoleCreateUpdateDto();
        roleCreate.setId(1L);
        roleCreate.setName("admin");
        roleCreate.setCode("ROLE_ADMIN");
        roleCreate.setDescription("desc");
        roleCreate.setBuiltin(true);
        roleCreate.setEnabled(true);
        roleCreate.setUserIds(List.of(1L, 2L));
        assertThat(roleCreate.getId()).isEqualTo(1L);
        assertThat(roleCreate.getUserIds()).containsExactly(1L, 2L);
        assertThat(roleCreate.isBuiltin()).isTrue();
        assertThat(roleCreate.isEnabled()).isTrue();

        LocalDateTime created = LocalDateTime.of(2026, 3, 3, 10, 0);
        LocalDateTime updated = LocalDateTime.of(2026, 3, 3, 11, 0);
        RoleResponseDto roleResponse = new RoleResponseDto(1L, "admin", "ROLE_ADMIN", "desc", true, false, created, updated);
        assertThat(roleResponse.getId()).isEqualTo(1L);
        assertThat(roleResponse.getName()).isEqualTo("admin");
        assertThat(roleResponse.getCode()).isEqualTo("ROLE_ADMIN");
        assertThat(roleResponse.getDescription()).isEqualTo("desc");
        assertThat(roleResponse.isBuiltin()).isTrue();
        assertThat(roleResponse.isEnabled()).isFalse();
        assertThat(roleResponse.getCreatedAt()).isEqualTo(created);
        assertThat(roleResponse.getUpdatedAt()).isEqualTo(updated);
        roleResponse.setEnabled(true);
        assertThat(roleResponse.isEnabled()).isTrue();

        UserRequestDto userRequest = new UserRequestDto();
        userRequest.setUsername("alice");
        userRequest.setNickname("Alice");
        assertThat(userRequest.getUsername()).isEqualTo("alice");
        assertThat(userRequest.getNickname()).isEqualTo("Alice");

        UserCreateUpdateDto userCreate = new UserCreateUpdateDto();
        userCreate.setUsername("alice");
        userCreate.setNickname("Alice");
        userCreate.setEmail("alice@example.com");
        userCreate.setPhone("13800000000");
        userCreate.setPassword("secret1");
        userCreate.setConfirmPassword("secret1");
        userCreate.setEnabled(true);
        userCreate.setAccountNonExpired(true);
        userCreate.setAccountNonLocked(false);
        userCreate.setCredentialsNonExpired(true);
        userCreate.setRoleIds(List.of(1L, 2L));
        assertThat(userCreate.isCreateMode()).isTrue();
        assertThat(userCreate.needUpdatePassword()).isTrue();
        userCreate.setId(9L);
        userCreate.setPassword("  ");
        assertThat(userCreate.isCreateMode()).isFalse();
        assertThat(userCreate.needUpdatePassword()).isFalse();
        assertThat(userCreate.getRoleIds()).containsExactly(1L, 2L);
        assertThat(userCreate.getPhone()).isEqualTo("13800000000");
        assertThat(userCreate.getAccountNonLocked()).isFalse();

        UserResponseDto userResponse = new UserResponseDto(2L, "bob", "Bob", true, true, false, true, created, 3, created.minusHours(1), true, 8);
        assertThat(userResponse.getId()).isEqualTo(2L);
        assertThat(userResponse.getUsername()).isEqualTo("bob");
        assertThat(userResponse.getNickname()).isEqualTo("Bob");
        assertThat(userResponse.isEnabled()).isTrue();
        assertThat(userResponse.isAccountNonExpired()).isTrue();
        assertThat(userResponse.isAccountNonLocked()).isFalse();
        assertThat(userResponse.isCredentialsNonExpired()).isTrue();
        assertThat(userResponse.getLastLoginAt()).isEqualTo(created);
        assertThat(userResponse.getFailedLoginCount()).isEqualTo(3);
        assertThat(userResponse.getLastFailedLoginAt()).isEqualTo(created.minusHours(1));
        assertThat(userResponse.isTemporarilyLocked()).isTrue();
        assertThat(userResponse.getLockRemainingMinutes()).isEqualTo(8);

        ResourceRequestDto resourceRequest = new ResourceRequestDto();
        resourceRequest.setName("menu");
        resourceRequest.setUrl("/menu");
        resourceRequest.setUri("/api/menu");
        resourceRequest.setPermission("menu:view");
        resourceRequest.setTitle("Menu");
        resourceRequest.setType(1);
        resourceRequest.setParentId(5L);
        resourceRequest.setHidden(true);
        resourceRequest.setEnabled(false);
        resourceRequest.setPage(2);
        resourceRequest.setSize(50);
        assertThat(resourceRequest.getName()).isEqualTo("menu");
        assertThat(resourceRequest.getUrl()).isEqualTo("/menu");
        assertThat(resourceRequest.getUri()).isEqualTo("/api/menu");
        assertThat(resourceRequest.getPermission()).isEqualTo("menu:view");
        assertThat(resourceRequest.getTitle()).isEqualTo("Menu");
        assertThat(resourceRequest.getType()).isEqualTo(1);
        assertThat(resourceRequest.getParentId()).isEqualTo(5L);
        assertThat(resourceRequest.getHidden()).isTrue();
        assertThat(resourceRequest.getEnabled()).isFalse();
        assertThat(resourceRequest.getPage()).isEqualTo(2);
        assertThat(resourceRequest.getSize()).isEqualTo(50);

        ResourceCreateUpdateDto resourceCreate = new ResourceCreateUpdateDto();
        resourceCreate.setId(3L);
        resourceCreate.setName("menu");
        resourceCreate.setTitle("Menu");
        resourceCreate.setUrl("/menu");
        resourceCreate.setUri("/api/menu");
        resourceCreate.setMethod("POST");
        resourceCreate.setIcon("icon");
        resourceCreate.setShowIcon(false);
        resourceCreate.setSort(99);
        resourceCreate.setComponent("MenuView");
        resourceCreate.setRedirect("/home");
        resourceCreate.setHidden(true);
        resourceCreate.setKeepAlive(true);
        resourceCreate.setPermission("menu:edit");
        resourceCreate.setType(2);
        resourceCreate.setParentId(8L);
        assertThat(resourceCreate.getId()).isEqualTo(3L);
        assertThat(resourceCreate.getMethod()).isEqualTo("POST");
        assertThat(resourceCreate.isShowIcon()).isFalse();
        assertThat(resourceCreate.isHidden()).isTrue();
        assertThat(resourceCreate.isKeepAlive()).isTrue();
        assertThat(resourceCreate.getPermission()).isEqualTo("menu:edit");
        assertThat(resourceCreate.getType()).isEqualTo(2);
        assertThat(resourceCreate.getParentId()).isEqualTo(8L);

        ResourceResponseDto emptyResponse = new ResourceResponseDto();
        emptyResponse.setId(4L);
        emptyResponse.setName("res");
        emptyResponse.setTitle("Res");
        emptyResponse.setUrl("/res");
        emptyResponse.setUri("/api/res");
        emptyResponse.setMethod("GET");
        emptyResponse.setIcon("icon");
        emptyResponse.setShowIcon(true);
        emptyResponse.setSort(1);
        emptyResponse.setComponent("ResView");
        emptyResponse.setRedirect("/home");
        emptyResponse.setHidden(false);
        emptyResponse.setKeepAlive(true);
        emptyResponse.setPermission("res:view");
        emptyResponse.setType(1);
        emptyResponse.setTypeName("菜单");
        emptyResponse.setParentId(0L);
        emptyResponse.setChildren(List.of());
        emptyResponse.setLeaf(true);
        emptyResponse.setEnabled(true);
        assertThat(emptyResponse.getId()).isEqualTo(4L);
        assertThat(emptyResponse.getChildren()).isEmpty();
        assertThat(emptyResponse.getLeaf()).isTrue();
        assertThat(emptyResponse.getEnabled()).isTrue();
        assertThat(emptyResponse.getTypeName()).isEqualTo("菜单");

        ResourceResponseDto projected = new ResourceResponseDto(
            5L, "proj", "Projection", "/proj", "icon",
            Boolean.TRUE, 7, "ProjView", "/home",
            Boolean.FALSE, Boolean.TRUE, "proj:view",
            ResourceType.API, 9L, false
        );
        assertThat(projected.getId()).isEqualTo(5L);
        assertThat(projected.getType()).isEqualTo(3);
        assertThat(projected.getLeaf()).isFalse();
        assertThat(projected.getShowIcon()).isTrue();
        assertThat(projected.getHidden()).isFalse();
        assertThat(projected.getKeepAlive()).isTrue();

        ResourceSortDto sort1 = new ResourceSortDto();
        sort1.setId(1L);
        sort1.setSort(10);
        sort1.setParentId(2L);
        assertThat(sort1.getId()).isEqualTo(1L);
        assertThat(sort1.getSort()).isEqualTo(10);
        assertThat(sort1.getParentId()).isEqualTo(2L);

        ResourceSortDto sort2 = new ResourceSortDto(2L, 20);
        assertThat(sort2.getId()).isEqualTo(2L);
        assertThat(sort2.getSort()).isEqualTo(20);

        ResourceSortDto sort3 = new ResourceSortDto(3L, 30, 4L);
        assertThat(sort3.getParentId()).isEqualTo(4L);

        ResourceDTO resourceDTO = new ResourceDTO();
        assertThat(resourceDTO).isNotNull();
        Field[] fields = ResourceDTO.class.getDeclaredFields();
        assertThat(fields).hasSize(4);
        for (Field field : fields) {
            field.setAccessible(true);
        }
        fields[0].set(resourceDTO, 1L);
        fields[1].set(resourceDTO, "title");
        fields[2].set(resourceDTO, "/path");
        fields[3].set(resourceDTO, 2);
        assertThat(fields[0].get(resourceDTO)).isEqualTo(1L);

        ResourceProjection projection = new ResourceProjection() {
            @Override public Long getId() { return 1L; }
            @Override public String getName() { return "n"; }
            @Override public String getTitle() { return "t"; }
            @Override public String getUrl() { return "/u"; }
            @Override public String getIcon() { return "i"; }
            @Override public Boolean getShowIcon() { return true; }
            @Override public Integer getSort() { return 1; }
            @Override public String getComponent() { return "c"; }
            @Override public String getRedirect() { return "r"; }
            @Override public Boolean getHidden() { return false; }
            @Override public Boolean getKeepAlive() { return true; }
            @Override public String getPermission() { return "p"; }
            @Override public Integer getType() { return 1; }
            @Override public Long getParentId() { return 2L; }
            @Override public Integer getLeaf() { return 1; }
        };
        assertThat(projection.getPermission()).isEqualTo("p");
        assertThat(projection.getLeaf()).isEqualTo(1);
    }

    @Test
    void should_cover_password_confirm_validation_and_annotation() {
        PasswordConfirm annotation = UserCreateUpdateDto.class.getAnnotation(PasswordConfirm.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.message()).isEqualTo("密码和确认密码不一致");
        assertThat(annotation.groups()).isEmpty();
        assertThat(annotation.payload()).isEmpty();

        PasswordConfirmValidator validator = new PasswordConfirmValidator();
        @SuppressWarnings("unchecked")
        jakarta.validation.ConstraintValidatorContext context = mock(jakarta.validation.ConstraintValidatorContext.class);
        jakarta.validation.ConstraintValidatorContext.ConstraintViolationBuilder builder =
            mock(jakarta.validation.ConstraintValidatorContext.ConstraintViolationBuilder.class);
        when(context.buildConstraintViolationWithTemplate(anyString())).thenReturn(builder);
        when(builder.addConstraintViolation()).thenReturn(context);

        validator.initialize(annotation);

        UserCreateUpdateDto createMissingPassword = new UserCreateUpdateDto();
        createMissingPassword.setUsername("alice");
        createMissingPassword.setNickname("Alice");
        assertThat(validator.isValid(createMissingPassword, context)).isFalse();
        verify(context).disableDefaultConstraintViolation();

        UserCreateUpdateDto createShortPassword = new UserCreateUpdateDto();
        createShortPassword.setUsername("alice");
        createShortPassword.setNickname("Alice");
        createShortPassword.setPassword("123");
        createShortPassword.setConfirmPassword("123");
        assertThat(validator.isValid(createShortPassword, context)).isFalse();

        UserCreateUpdateDto createMissingConfirm = new UserCreateUpdateDto();
        createMissingConfirm.setUsername("alice");
        createMissingConfirm.setNickname("Alice");
        createMissingConfirm.setPassword("123456");
        assertThat(validator.isValid(createMissingConfirm, context)).isFalse();

        UserCreateUpdateDto createMismatch = new UserCreateUpdateDto();
        createMismatch.setUsername("alice");
        createMismatch.setNickname("Alice");
        createMismatch.setPassword("123456");
        createMismatch.setConfirmPassword("654321");
        assertThat(validator.isValid(createMismatch, context)).isFalse();

        UserCreateUpdateDto createValid = new UserCreateUpdateDto();
        createValid.setUsername("alice");
        createValid.setNickname("Alice");
        createValid.setPassword("123456");
        createValid.setConfirmPassword("123456");
        assertThat(validator.isValid(createValid, context)).isTrue();

        UserCreateUpdateDto editNoPassword = new UserCreateUpdateDto();
        editNoPassword.setId(1L);
        editNoPassword.setUsername("alice");
        editNoPassword.setNickname("Alice");
        editNoPassword.setPassword(" ");
        assertThat(validator.isValid(editNoPassword, context)).isTrue();
    }
}
