package com.tiny.platform.application.controller.dict;

import com.tiny.platform.core.dict.dto.DictItemCreateUpdateDto;
import com.tiny.platform.core.dict.dto.DictItemQueryDto;
import com.tiny.platform.core.dict.dto.DictItemResponseDto;
import com.tiny.platform.core.dict.dto.DictTypeCreateUpdateDto;
import com.tiny.platform.core.dict.dto.DictTypeQueryDto;
import com.tiny.platform.core.dict.dto.DictTypeResponseDto;
import com.tiny.platform.core.dict.model.DictItem;
import com.tiny.platform.core.dict.model.DictType;
import com.tiny.platform.core.dict.service.DictItemService;
import com.tiny.platform.core.dict.service.DictTypeService;
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

/**
 * Dict 控制器单元测试（参照 export 的 ExportControllerTest 结构）.
 */
class DictControllerTest {

    private static DictType dictType(Long id, String code) {
        DictType t = new DictType();
        t.setId(id);
        t.setDictCode(code);
        t.setDictName(code + "-name");
        return t;
    }

    private static DictItem dictItem(Long id, Long typeId, String value, String label) {
        DictItem i = new DictItem();
        i.setId(id);
        i.setDictTypeId(typeId);
        i.setValue(value);
        i.setLabel(label);
        return i;
    }

    @Nested
    class DictTypeEndpoints {

        @Test
        void getDictTypes_returnsPageFromService() {
            DictTypeService typeService = mock(DictTypeService.class);
            DictController controller = new DictController(typeService, mock(DictItemService.class));
            DictTypeQueryDto query = new DictTypeQueryDto();
            Pageable pageable = PageRequest.of(0, 10);
            DictTypeResponseDto dto = new DictTypeResponseDto();
            dto.setId(1L);
            dto.setDictCode("STATUS");
            when(typeService.query(any(DictTypeQueryDto.class), eq(pageable)))
                    .thenReturn(new PageImpl<>(List.of(dto), pageable, 1));

            PageResponse<DictTypeResponseDto> body = controller.getDictTypes(query, pageable).getBody();

            assertThat(body).isNotNull();
            assertThat(body.getContent()).containsExactly(dto);
            verify(typeService).query(any(DictTypeQueryDto.class), eq(pageable));
        }

        @Test
        void getDictType_whenFound_returns200AndBody() {
            DictTypeService typeService = mock(DictTypeService.class);
            DictController controller = new DictController(typeService, mock(DictItemService.class));
            DictType type = dictType(2L, "COLOR");
            when(typeService.findById(2L)).thenReturn(Optional.of(type));

            assertThat(controller.getDictType(2L).getBody()).isEqualTo(type);
            assertThat(controller.getDictType(2L).getStatusCode().value()).isEqualTo(200);
        }

        @Test
        void getDictType_whenNotFound_returns404() {
            DictTypeService typeService = mock(DictTypeService.class);
            DictController controller = new DictController(typeService, mock(DictItemService.class));
            when(typeService.findById(99L)).thenReturn(Optional.empty());

            assertThat(controller.getDictType(99L).getStatusCode().value()).isEqualTo(404);
            assertThat(controller.getDictType(99L).getBody()).isNull();
        }

        @Test
        void getDictTypeByCode_whenFound_returns200AndBody() {
            DictTypeService typeService = mock(DictTypeService.class);
            DictController controller = new DictController(typeService, mock(DictItemService.class));
            DictType type = dictType(2L, "COLOR");
            when(typeService.findByDictCode("COLOR")).thenReturn(Optional.of(type));

            assertThat(controller.getDictTypeByCode("COLOR").getBody()).isEqualTo(type);
            assertThat(controller.getDictTypeByCode("COLOR").getStatusCode().value()).isEqualTo(200);
        }

        @Test
        void getDictTypeByCode_whenNotFound_returns404() {
            DictTypeService typeService = mock(DictTypeService.class);
            DictController controller = new DictController(typeService, mock(DictItemService.class));
            when(typeService.findByDictCode("NONE")).thenReturn(Optional.empty());

            assertThat(controller.getDictTypeByCode("NONE").getStatusCode().value()).isEqualTo(404);
        }

        @Test
        void createDictType_callsServiceAndReturns200WithBody() {
            DictTypeService typeService = mock(DictTypeService.class);
            DictController controller = new DictController(typeService, mock(DictItemService.class));
            DictTypeCreateUpdateDto dto = new DictTypeCreateUpdateDto();
            dto.setDictCode("CUSTOM");
            dto.setDictName("自定义");
            DictType created = dictType(1L, "CUSTOM");
            when(typeService.create(dto)).thenReturn(created);

            var response = controller.createDictType(dto);
            assertThat(response.getBody()).isEqualTo(created);
            assertThat(response.getStatusCode().value()).isEqualTo(200);
            verify(typeService).create(dto);
        }

        @Test
        void updateDictType_callsServiceAndReturns200WithBody() {
            DictTypeService typeService = mock(DictTypeService.class);
            DictController controller = new DictController(typeService, mock(DictItemService.class));
            DictTypeCreateUpdateDto dto = new DictTypeCreateUpdateDto();
            dto.setDictCode("CUSTOM");
            dto.setDictName("自定义");
            DictType updated = dictType(2L, "CUSTOM");
            when(typeService.update(2L, dto)).thenReturn(updated);

            assertThat(controller.updateDictType(2L, dto).getBody()).isEqualTo(updated);
            verify(typeService).update(2L, dto);
        }

        @Test
        void deleteDictType_callsServiceDeleteAndReturns200() {
            DictTypeService typeService = mock(DictTypeService.class);
            DictController controller = new DictController(typeService, mock(DictItemService.class));

            assertThat(controller.deleteDictType(3L).getStatusCode().value()).isEqualTo(200);
            verify(typeService).delete(3L);
        }

        @Test
        void batchDeleteDictTypes_callsServiceAndReturns200() {
            DictTypeService typeService = mock(DictTypeService.class);
            DictController controller = new DictController(typeService, mock(DictItemService.class));
            List<Long> ids = List.of(1L, 2L);

            assertThat(controller.batchDeleteDictTypes(ids).getStatusCode().value()).isEqualTo(200);
            verify(typeService).batchDelete(ids);
        }

        @Test
        void getVisibleDictTypes_returnsListFromService() {
            DictTypeService typeService = mock(DictTypeService.class);
            DictController controller = new DictController(typeService, mock(DictItemService.class));
            DictType type = dictType(1L, "STATUS");
            when(typeService.findVisibleTypes()).thenReturn(List.of(type));

            assertThat(controller.getVisibleDictTypes().getBody()).containsExactly(type);
        }

    }

    @Nested
    class DictItemEndpoints {

        @Test
        void getDictItems_returnsPageFromService() {
            DictItemService itemService = mock(DictItemService.class);
            DictController controller = new DictController(mock(DictTypeService.class), itemService);
            DictItemQueryDto query = new DictItemQueryDto();
            Pageable pageable = PageRequest.of(0, 10);
            DictItemResponseDto dto = new DictItemResponseDto();
            dto.setId(1L);
            dto.setValue("1");
            when(itemService.query(any(DictItemQueryDto.class), eq(pageable)))
                    .thenReturn(new PageImpl<>(List.of(dto), pageable, 1));

            PageResponse<DictItemResponseDto> body = controller.getDictItems(query, pageable).getBody();

            assertThat(body).isNotNull();
            assertThat(body.getContent()).containsExactly(dto);
            verify(itemService).query(any(DictItemQueryDto.class), eq(pageable));
        }

        @Test
        void getDictItem_whenFound_returns200AndBody() {
            DictItemService itemService = mock(DictItemService.class);
            DictController controller = new DictController(mock(DictTypeService.class), itemService);
            DictItem item = dictItem(2L, 10L, "A", "Alpha");
            when(itemService.findById(2L)).thenReturn(Optional.of(item));

            assertThat(controller.getDictItem(2L).getBody()).isEqualTo(item);
            assertThat(controller.getDictItem(2L).getStatusCode().value()).isEqualTo(200);
        }

        @Test
        void getDictItem_whenNotFound_returns404() {
            DictItemService itemService = mock(DictItemService.class);
            DictController controller = new DictController(mock(DictTypeService.class), itemService);
            when(itemService.findById(99L)).thenReturn(Optional.empty());

            assertThat(controller.getDictItem(99L).getStatusCode().value()).isEqualTo(404);
            assertThat(controller.getDictItem(99L).getBody()).isNull();
        }

        @Test
        void getDictItemsByType_returnsListFromService() {
            DictItemService itemService = mock(DictItemService.class);
            DictController controller = new DictController(mock(DictTypeService.class), itemService);
            DictItem item = dictItem(1L, 10L, "A", "Alpha");
            when(itemService.findByDictTypeId(10L)).thenReturn(List.of(item));

            assertThat(controller.getDictItemsByType(10L).getBody()).containsExactly(item);
        }

        @Test
        void getDictItemsByCode_returnsListFromService() {
            DictItemService itemService = mock(DictItemService.class);
            DictController controller = new DictController(mock(DictTypeService.class), itemService);
            DictItem item = dictItem(1L, 10L, "1", "启用");
            when(itemService.findByDictCode("STATUS")).thenReturn(List.of(item));

            assertThat(controller.getDictItemsByCode("STATUS").getBody()).containsExactly(item);
        }

        @Test
        void getDictMap_returnsMapFromService() {
            DictItemService itemService = mock(DictItemService.class);
            DictController controller = new DictController(mock(DictTypeService.class), itemService);
            when(itemService.getDictMap("STATUS")).thenReturn(Map.of("1", "启用"));

            assertThat(controller.getDictMap("STATUS").getBody()).containsEntry("1", "启用");
        }

        @Test
        void getLabel_returnsLabelFromService() {
            DictItemService itemService = mock(DictItemService.class);
            DictController controller = new DictController(mock(DictTypeService.class), itemService);
            when(itemService.getLabel("STATUS", "1")).thenReturn("启用");

            assertThat(controller.getLabel("STATUS", "1").getBody()).isEqualTo("启用");
        }

        @Test
        void createDictItem_callsServiceAndReturns200WithBody() {
            DictItemService itemService = mock(DictItemService.class);
            DictController controller = new DictController(mock(DictTypeService.class), itemService);
            DictItemCreateUpdateDto dto = new DictItemCreateUpdateDto();
            dto.setDictTypeId(10L);
            dto.setValue("X");
            dto.setLabel("项X");
            DictItem created = dictItem(1L, 10L, "X", "项X");
            when(itemService.create(dto)).thenReturn(created);

            assertThat(controller.createDictItem(dto).getBody()).isEqualTo(created);
            verify(itemService).create(dto);
        }

        @Test
        void updateDictItem_callsServiceAndReturns200WithBody() {
            DictItemService itemService = mock(DictItemService.class);
            DictController controller = new DictController(mock(DictTypeService.class), itemService);
            DictItemCreateUpdateDto dto = new DictItemCreateUpdateDto();
            dto.setDictTypeId(10L);
            dto.setValue("X");
            dto.setLabel("项X");
            DictItem updated = dictItem(2L, 10L, "X", "项X");
            when(itemService.update(2L, dto)).thenReturn(updated);

            assertThat(controller.updateDictItem(2L, dto).getBody()).isEqualTo(updated);
            verify(itemService).update(2L, dto);
        }

        @Test
        void deleteDictItem_callsServiceDeleteAndReturns200() {
            DictItemService itemService = mock(DictItemService.class);
            DictController controller = new DictController(mock(DictTypeService.class), itemService);

            assertThat(controller.deleteDictItem(3L).getStatusCode().value()).isEqualTo(200);
            verify(itemService).delete(3L);
        }

        @Test
        void batchDeleteDictItems_callsServiceAndReturns200() {
            DictItemService itemService = mock(DictItemService.class);
            DictController controller = new DictController(mock(DictTypeService.class), itemService);
            List<Long> ids = List.of(1L, 2L);

            assertThat(controller.batchDeleteDictItems(ids).getStatusCode().value()).isEqualTo(200);
            verify(itemService).batchDelete(ids);
        }
    }
}
