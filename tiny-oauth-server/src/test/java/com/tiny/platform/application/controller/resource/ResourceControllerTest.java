package com.tiny.platform.application.controller.resource;

import com.tiny.platform.infrastructure.auth.resource.domain.Resource;
import com.tiny.platform.infrastructure.auth.resource.dto.ResourceCreateUpdateDto;
import com.tiny.platform.infrastructure.auth.resource.dto.ResourceRequestDto;
import com.tiny.platform.infrastructure.auth.resource.dto.ResourceResponseDto;
import com.tiny.platform.infrastructure.auth.resource.enums.ResourceType;
import com.tiny.platform.infrastructure.auth.resource.service.ResourceService;
import com.tiny.platform.infrastructure.core.dto.PageResponse;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ResourceControllerTest {

    @Test
    void should_cover_resource_crud_and_query_endpoints() {
        ResourceService resourceService = mock(ResourceService.class);
        ResourceController controller = new ResourceController(resourceService);
        ResourceRequestDto query = new ResourceRequestDto();
        Pageable pageable = PageRequest.of(0, 10);
        ResourceResponseDto responseDto = responseDto(1L, "res-1");
        ResourceResponseDto detailDto = responseDto(2L, "res-2");
        Resource resource = resource(2L, "res-2", ResourceType.API);
        ResourceCreateUpdateDto dto = createDto("res-2", 2);

        when(resourceService.resources(query, pageable)).thenReturn(new PageImpl<>(List.of(responseDto), pageable, 1));
        when(resourceService.findDetailById(2L)).thenReturn(Optional.of(detailDto));
        when(resourceService.findDetailById(99L)).thenReturn(Optional.empty());
        when(resourceService.createFromDto(dto)).thenReturn(resource);
        when(resourceService.updateFromDto(dto)).thenReturn(resource);
        when(resourceService.updateSort(2L, 5)).thenReturn(resource);
        when(resourceService.findDtosByType(ResourceType.API)).thenReturn(List.of(detailDto));
        when(resourceService.findChildDtos(10L)).thenReturn(List.of(detailDto));
        when(resourceService.findTopLevelDtos()).thenReturn(List.of(detailDto));
        when(resourceService.findAllowedUiActionDtos("/system/resource")).thenReturn(List.of(detailDto));
        when(resourceService.canAccessApiEndpoint("GET", "/sys/resources")).thenReturn(true);
        when(resourceService.getResourceTypes()).thenReturn(List.of(ResourceType.values()));
        when(resourceService.findDtosByPermission("perm:a")).thenReturn(List.of(detailDto));
        when(resourceService.existsByName("res", 1L)).thenReturn(true);
        when(resourceService.existsByUrl("/a", 1L)).thenReturn(false);
        when(resourceService.existsByUri("/api/a", 1L)).thenReturn(true);

        PageResponse<ResourceResponseDto> pageBody = controller.getResources(query, pageable).getBody();
        assertThat(pageBody).isNotNull();
        assertThat(pageBody.getContent()).containsExactly(responseDto);

        assertThat(controller.getResource(2L).getBody()).isEqualTo(detailDto);
        assertThat(controller.getResource(99L).getStatusCode().value()).isEqualTo(404);
        assertThat(controller.create(dto).getBody()).isEqualTo(resource);
        assertThat(controller.update(2L, dto).getBody()).isEqualTo(resource);
        assertThat(dto.getId()).isEqualTo(2L);

        assertThat(controller.delete(3L).getStatusCode().value()).isEqualTo(204);
        verify(resourceService).delete(3L);

        assertThat(controller.batchDelete(List.of(1L, 2L)).getBody())
            .containsEntry("success", true)
            .containsEntry("message", "批量删除成功");
        verify(resourceService).batchDelete(List.of(1L, 2L));

        assertThat(controller.getResourcesByType(ResourceType.API.getCode()).getBody()).containsExactly(detailDto);
        assertThat(controller.getResourcesByParentId(10L).getBody()).containsExactly(detailDto);
        assertThat(controller.getTopLevelResources().getBody()).containsExactly(detailDto);
        assertThat(controller.getRuntimeUiActions("/system/resource").getBody()).containsExactly(detailDto);
        assertThat(controller.getRuntimeApiAccess("GET", "/sys/resources").getBody()).containsEntry("allowed", true);
        assertThat(controller.updateSort(2L, 5).getBody()).isEqualTo(resource);
        assertThat(controller.getResourceTypes().getBody()).containsExactly(ResourceType.values());
        assertThat(controller.getResourcesByPermission("perm:a").getBody()).containsExactly(detailDto);
        assertThat(controller.checkNameExists("res", 1L).getBody()).containsEntry("exists", true);
        assertThat(controller.checkUrlExists("/a", 1L).getBody()).containsEntry("exists", false);
        assertThat(controller.checkUriExists("/api/a", 1L).getBody()).containsEntry("exists", true);

        assertThatThrownBy(() -> controller.getResourcesByType(999))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("无效的资源类型");
    }

    @Test
    void should_cover_menu_specific_endpoints_and_recursive_tree() {
        ResourceService resourceService = mock(ResourceService.class);
        ResourceController controller = new ResourceController(resourceService);
        Pageable pageable = PageRequest.of(0, 10);
        ResourceResponseDto responseDto = responseDto(1L, "menu-1");
        Resource root = resource(1L, "root", ResourceType.MENU);
        Resource child = resource(2L, "child", ResourceType.BUTTON);

        when(resourceService.resources(any(ResourceRequestDto.class), eq(pageable)))
            .thenReturn(new PageImpl<>(List.of(responseDto), pageable, 1));
        when(resourceService.findByTypeIn(List.of(ResourceType.DIRECTORY, ResourceType.MENU))).thenReturn(List.of(root));
        when(resourceService.buildResourceTree(List.of(root))).thenReturn(List.of(responseDto));
        when(resourceService.findResourceTreeDtos()).thenReturn(List.of(responseDto));
        when(resourceService.createFromDto(any(ResourceCreateUpdateDto.class))).thenReturn(root);
        when(resourceService.updateFromDto(any(ResourceCreateUpdateDto.class))).thenReturn(root);
        when(resourceService.updateSort(1L, 99)).thenReturn(root);

        assertThat(controller.getMenus("n", "t", "p", pageable).getBody().getContent()).containsExactly(responseDto);
        ArgumentCaptor<ResourceRequestDto> queryCaptor = ArgumentCaptor.forClass(ResourceRequestDto.class);
        verify(resourceService).resources(queryCaptor.capture(), eq(pageable));
        ResourceRequestDto builtQuery = queryCaptor.getValue();
        assertThat(builtQuery.getName()).isEqualTo("n");
        assertThat(builtQuery.getTitle()).isEqualTo("t");
        assertThat(builtQuery.getPermission()).isEqualTo("p");
        assertThat(builtQuery.getType()).isEqualTo(ResourceType.MENU.getCode());

        assertThat(controller.getMenuTree().getBody()).containsExactly(responseDto);
        assertThat(controller.getResourceTree().getBody()).containsExactly(responseDto);

        ResourceCreateUpdateDto createInvalidType = createDto("menu", 2);
        assertThat(controller.createMenu(createInvalidType).getBody()).isEqualTo(root);
        ArgumentCaptor<ResourceCreateUpdateDto> createCaptor = ArgumentCaptor.forClass(ResourceCreateUpdateDto.class);
        verify(resourceService).createFromDto(createCaptor.capture());
        assertThat(createCaptor.getValue().getType()).isEqualTo(ResourceType.MENU.getCode());

        ResourceCreateUpdateDto createDirectory = createDto("dir", ResourceType.DIRECTORY.getCode());
        controller.createMenu(createDirectory);
        assertThat(createDirectory.getType()).isEqualTo(ResourceType.DIRECTORY.getCode());

        ResourceCreateUpdateDto updateNullType = createDto("update", null);
        assertThat(controller.updateMenu(9L, updateNullType).getBody()).isEqualTo(root);
        ArgumentCaptor<ResourceCreateUpdateDto> updateCaptor = ArgumentCaptor.forClass(ResourceCreateUpdateDto.class);
        verify(resourceService).updateFromDto(updateCaptor.capture());
        assertThat(updateCaptor.getValue().getId()).isEqualTo(9L);
        assertThat(updateCaptor.getValue().getType()).isEqualTo(ResourceType.MENU.getCode());

        assertThat(controller.deleteMenu(4L).getStatusCode().value()).isEqualTo(204);
        verify(resourceService).delete(4L);
        assertThat(controller.batchDeleteMenus(List.of(3L, 4L)).getBody())
            .containsEntry("success", true)
            .containsEntry("message", "批量删除成功");
        verify(resourceService).batchDelete(List.of(3L, 4L));
        assertThat(controller.updateMenuSort(1L, 99).getBody()).isEqualTo(root);
    }

    private static ResourceCreateUpdateDto createDto(String name, Integer type) {
        ResourceCreateUpdateDto dto = new ResourceCreateUpdateDto();
        dto.setName(name);
        dto.setTitle(name);
        dto.setType(type);
        return dto;
    }

    private static Resource resource(Long id, String name, ResourceType type) {
        Resource resource = new Resource();
        resource.setId(id);
        resource.setName(name);
        resource.setTitle(name);
        resource.setType(type);
        return resource;
    }

    private static ResourceResponseDto responseDto(Long id, String name) {
        ResourceResponseDto dto = new ResourceResponseDto();
        dto.setId(id);
        dto.setName(name);
        dto.setTitle(name);
        return dto;
    }
}
