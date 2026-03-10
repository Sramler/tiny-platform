package com.tiny.platform.application.controller.dict;

import com.tiny.platform.core.dict.dto.DictItemCreateUpdateDto;
import com.tiny.platform.core.dict.dto.DictItemQueryDto;
import com.tiny.platform.core.dict.dto.DictItemResponseDto;
import com.tiny.platform.core.dict.dto.DictTypeCreateUpdateDto;
import com.tiny.platform.core.dict.dto.DictTypeQueryDto;
import com.tiny.platform.core.dict.dto.DictTypeResponseDto;
import com.tiny.platform.core.dict.model.DictItem;
import com.tiny.platform.core.dict.model.DictType;
import com.tiny.platform.core.dict.service.DictPlatformAdminService;
import com.tiny.platform.infrastructure.core.dto.PageResponse;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlatformDictControllerTest {

    private static DictType dictType(Long id, String code) {
        DictType t = new DictType();
        t.setId(id);
        t.setDictCode(code);
        t.setDictName(code + "-name");
        t.setTenantId(0L);
        return t;
    }

    private static DictItem dictItem(Long id, Long typeId, String value, String label) {
        DictItem i = new DictItem();
        i.setId(id);
        i.setDictTypeId(typeId);
        i.setValue(value);
        i.setLabel(label);
        i.setTenantId(0L);
        return i;
    }

    @Nested
    class DictTypeEndpoints {

        @Test
        void getDictTypes_returnsPageFromService() {
            DictPlatformAdminService service = mock(DictPlatformAdminService.class);
            PlatformDictController controller = new PlatformDictController(service);
            DictTypeQueryDto query = new DictTypeQueryDto();
            Pageable pageable = PageRequest.of(0, 10);
            DictTypeResponseDto dto = new DictTypeResponseDto();
            dto.setId(1L);
            dto.setDictCode("STATUS");
            when(service.queryTypes(any(DictTypeQueryDto.class), eq(pageable)))
                    .thenReturn(new PageImpl<>(List.of(dto), pageable, 1));

            PageResponse<DictTypeResponseDto> body = controller.getDictTypes(query, pageable).getBody();

            assertThat(body).isNotNull();
            assertThat(body.getContent()).containsExactly(dto);
            verify(service).queryTypes(any(DictTypeQueryDto.class), eq(pageable));
        }

        @Test
        void createDictType_callsService() {
            DictPlatformAdminService service = mock(DictPlatformAdminService.class);
            PlatformDictController controller = new PlatformDictController(service);
            DictTypeCreateUpdateDto dto = new DictTypeCreateUpdateDto();
            dto.setDictCode("STATUS");
            dto.setDictName("状态");
            DictType created = dictType(1L, "STATUS");
            when(service.createType(dto)).thenReturn(created);

            assertThat(controller.createDictType(dto).getBody()).isEqualTo(created);
            verify(service).createType(dto);
        }

        @Test
        void getDictTypeByCode_whenFound_returnsBody() {
            DictPlatformAdminService service = mock(DictPlatformAdminService.class);
            PlatformDictController controller = new PlatformDictController(service);
            DictType type = dictType(1L, "STATUS");
            when(service.findTypeByCode("STATUS")).thenReturn(Optional.of(type));

            assertThat(controller.getDictTypeByCode("STATUS").getBody()).isEqualTo(type);
        }
    }

    @Nested
    class DictItemEndpoints {

        @Test
        void getDictItems_returnsPageFromService() {
            DictPlatformAdminService service = mock(DictPlatformAdminService.class);
            PlatformDictController controller = new PlatformDictController(service);
            DictItemQueryDto query = new DictItemQueryDto();
            Pageable pageable = PageRequest.of(0, 10);
            DictItemResponseDto dto = new DictItemResponseDto();
            dto.setId(1L);
            dto.setValue("1");
            when(service.queryItems(any(DictItemQueryDto.class), eq(pageable)))
                    .thenReturn(new PageImpl<>(List.of(dto), pageable, 1));

            PageResponse<DictItemResponseDto> body = controller.getDictItems(query, pageable).getBody();

            assertThat(body).isNotNull();
            assertThat(body.getContent()).containsExactly(dto);
            verify(service).queryItems(any(DictItemQueryDto.class), eq(pageable));
        }

        @Test
        void getDictMap_returnsMapFromService() {
            DictPlatformAdminService service = mock(DictPlatformAdminService.class);
            PlatformDictController controller = new PlatformDictController(service);
            when(service.getDictMap("STATUS")).thenReturn(Map.of("1", "启用"));

            assertThat(controller.getDictMap("STATUS").getBody()).containsEntry("1", "启用");
        }

        @Test
        void createDictItem_callsService() {
            DictPlatformAdminService service = mock(DictPlatformAdminService.class);
            PlatformDictController controller = new PlatformDictController(service);
            DictItemCreateUpdateDto dto = new DictItemCreateUpdateDto();
            dto.setDictTypeId(10L);
            dto.setValue("A");
            dto.setLabel("Alpha");
            DictItem created = dictItem(1L, 10L, "A", "Alpha");
            when(service.createItem(dto)).thenReturn(created);

            assertThat(controller.createDictItem(dto).getBody()).isEqualTo(created);
            verify(service).createItem(dto);
        }
    }
}
