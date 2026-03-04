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
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DictControllerTest {

    @Test
    void should_cover_all_dict_type_endpoints() {
        DictTypeService dictTypeService = mock(DictTypeService.class);
        DictItemService dictItemService = mock(DictItemService.class);
        DictController controller = new DictController(dictTypeService, dictItemService);

        DictTypeQueryDto query = new DictTypeQueryDto();
        Pageable pageable = PageRequest.of(0, 10);
        DictTypeResponseDto responseDto = new DictTypeResponseDto();
        responseDto.setId(1L);
        responseDto.setDictCode("STATUS");
        DictType dictType = dictType(2L, "COLOR");
        DictTypeCreateUpdateDto dto = new DictTypeCreateUpdateDto();

        when(dictTypeService.query(query, pageable)).thenReturn(new PageImpl<>(List.of(responseDto), pageable, 1));
        when(dictTypeService.findById(2L)).thenReturn(Optional.of(dictType));
        when(dictTypeService.findById(99L)).thenReturn(Optional.empty());
        when(dictTypeService.findByDictCode("COLOR")).thenReturn(Optional.of(dictType));
        when(dictTypeService.findByDictCode("NONE")).thenReturn(Optional.empty());
        when(dictTypeService.create(dto)).thenReturn(dictType);
        when(dictTypeService.update(2L, dto)).thenReturn(dictType);
        when(dictTypeService.findByTenantId(7L)).thenReturn(List.of(dictType));

        PageResponse<DictTypeResponseDto> pageBody = controller.getDictTypes(query, pageable).getBody();
        assertThat(pageBody).isNotNull();
        assertThat(pageBody.getContent()).containsExactly(responseDto);

        assertThat(controller.getDictType(2L).getBody()).isEqualTo(dictType);
        assertThat(controller.getDictType(99L).getStatusCode().value()).isEqualTo(404);

        assertThat(controller.getDictTypeByCode("COLOR").getBody()).isEqualTo(dictType);
        assertThat(controller.getDictTypeByCode("NONE").getStatusCode().value()).isEqualTo(404);

        assertThat(controller.createDictType(dto).getBody()).isEqualTo(dictType);
        assertThat(controller.updateDictType(2L, dto).getBody()).isEqualTo(dictType);

        assertThat(controller.deleteDictType(3L).getStatusCode().value()).isEqualTo(200);
        verify(dictTypeService).delete(3L);
        assertThat(controller.batchDeleteDictTypes(List.of(1L, 2L)).getStatusCode().value()).isEqualTo(200);
        verify(dictTypeService).batchDelete(List.of(1L, 2L));

        assertThat(controller.getDictTypesByTenant(7L).getBody()).containsExactly(dictType);
    }

    @Test
    void should_cover_all_dict_item_endpoints() {
        DictTypeService dictTypeService = mock(DictTypeService.class);
        DictItemService dictItemService = mock(DictItemService.class);
        DictController controller = new DictController(dictTypeService, dictItemService);

        DictItemQueryDto query = new DictItemQueryDto();
        Pageable pageable = PageRequest.of(1, 5);
        DictItemResponseDto responseDto = new DictItemResponseDto();
        responseDto.setId(1L);
        responseDto.setValue("1");
        DictItem dictItem = dictItem(2L, 10L, "A", "Alpha");
        DictItemCreateUpdateDto dto = new DictItemCreateUpdateDto();

        when(dictItemService.query(query, pageable)).thenReturn(new PageImpl<>(List.of(responseDto), pageable, 1));
        when(dictItemService.findById(2L)).thenReturn(Optional.of(dictItem));
        when(dictItemService.findById(99L)).thenReturn(Optional.empty());
        when(dictItemService.findByDictTypeId(10L)).thenReturn(List.of(dictItem));
        when(dictItemService.findByDictCode("STATUS", 0L)).thenReturn(List.of(dictItem));
        when(dictItemService.getDictMap("STATUS", 0L)).thenReturn(Map.of("1", "启用"));
        when(dictItemService.getLabel("STATUS", "1", 0L)).thenReturn("启用");
        when(dictItemService.create(dto)).thenReturn(dictItem);
        when(dictItemService.update(2L, dto)).thenReturn(dictItem);

        PageResponse<DictItemResponseDto> pageBody = controller.getDictItems(query, pageable).getBody();
        assertThat(pageBody).isNotNull();
        assertThat(pageBody.getContent()).containsExactly(responseDto);

        assertThat(controller.getDictItem(2L).getBody()).isEqualTo(dictItem);
        assertThat(controller.getDictItem(99L).getStatusCode().value()).isEqualTo(404);
        assertThat(controller.getDictItemsByType(10L).getBody()).containsExactly(dictItem);
        assertThat(controller.getDictItemsByCode("STATUS", 0L).getBody()).containsExactly(dictItem);
        assertThat(controller.getDictMap("STATUS", 0L).getBody()).containsEntry("1", "启用");
        assertThat(controller.getLabel("STATUS", "1", 0L).getBody()).isEqualTo("启用");
        assertThat(controller.createDictItem(dto).getBody()).isEqualTo(dictItem);
        assertThat(controller.updateDictItem(2L, dto).getBody()).isEqualTo(dictItem);

        assertThat(controller.deleteDictItem(3L).getStatusCode().value()).isEqualTo(200);
        verify(dictItemService).delete(3L);
        assertThat(controller.batchDeleteDictItems(List.of(1L, 2L)).getStatusCode().value()).isEqualTo(200);
        verify(dictItemService).batchDelete(List.of(1L, 2L));
    }

    private static DictType dictType(Long id, String code) {
        DictType dictType = new DictType();
        dictType.setId(id);
        dictType.setDictCode(code);
        dictType.setDictName(code + "-name");
        return dictType;
    }

    private static DictItem dictItem(Long id, Long typeId, String value, String label) {
        DictItem dictItem = new DictItem();
        dictItem.setId(id);
        dictItem.setDictTypeId(typeId);
        dictItem.setValue(value);
        dictItem.setLabel(label);
        return dictItem;
    }
}
