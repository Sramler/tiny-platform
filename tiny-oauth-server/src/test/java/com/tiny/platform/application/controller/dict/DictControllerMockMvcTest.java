package com.tiny.platform.application.controller.dict;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.tiny.platform.core.dict.dto.DictItemCreateUpdateDto;
import com.tiny.platform.core.dict.dto.DictTypeCreateUpdateDto;
import com.tiny.platform.core.dict.model.DictItem;
import com.tiny.platform.core.dict.model.DictType;
import com.tiny.platform.core.dict.service.DictItemService;
import com.tiny.platform.core.dict.service.DictTypeService;
import com.tiny.platform.infrastructure.core.exception.code.ErrorCode;
import com.tiny.platform.infrastructure.core.exception.exception.BusinessException;
import com.tiny.platform.infrastructure.core.exception.handler.OAuthServerExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Dict 控制器 MockMvc 测试（参照 export 的 ExportControllerMockMvcTest）.
 * 覆盖 HTTP 层、参数绑定与异常映射为 ProblemDetail。
 */
class DictControllerMockMvcTest {

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private DictTypeService dictTypeService;
    private DictItemService dictItemService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        dictTypeService = Mockito.mock(DictTypeService.class);
        dictItemService = Mockito.mock(DictItemService.class);
        DictController controller = new DictController(dictTypeService, dictItemService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new OAuthServerExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .build();
    }

    @Nested
    class DictTypeHttp {

        @Test
        void getTypes_returns200AndPageJson() throws Exception {
            when(dictTypeService.query(any(), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 10), 0));

            mockMvc.perform(get("/dict/types")
                            .param("page", "0")
                            .param("size", "10"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.content").isArray())
                    .andExpect(jsonPath("$.totalElements").value(0));
        }

        @Test
        void getTypeById_whenFound_returns200AndBody() throws Exception {
            DictType type = new DictType();
            type.setId(1L);
            type.setDictCode("STATUS");
            type.setDictName("状态");
            when(dictTypeService.findById(1L)).thenReturn(Optional.of(type));

            mockMvc.perform(get("/dict/types/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(1))
                    .andExpect(jsonPath("$.dictCode").value("STATUS"))
                    .andExpect(jsonPath("$.dictName").value("状态"));
        }

        @Test
        void getTypeById_whenNotFound_returns404() throws Exception {
            when(dictTypeService.findById(999L)).thenReturn(Optional.empty());

            mockMvc.perform(get("/dict/types/999"))
                    .andExpect(status().isNotFound());
        }

        @Test
        void getTypesCurrent_returns200AndArray() throws Exception {
            DictType type = new DictType();
            type.setId(1L);
            type.setDictCode("STATUS");
            when(dictTypeService.findVisibleTypes()).thenReturn(List.of(type));

            mockMvc.perform(get("/dict/types/current"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$[0].id").value(1))
                    .andExpect(jsonPath("$[0].dictCode").value("STATUS"));
        }

        @Test
        void postType_returns200AndBody() throws Exception {
            DictTypeCreateUpdateDto dto = new DictTypeCreateUpdateDto();
            dto.setDictCode("CUSTOM");
            dto.setDictName("自定义");
            DictType created = new DictType();
            created.setId(1L);
            created.setDictCode("CUSTOM");
            created.setDictName("自定义");
            when(dictTypeService.create(any(DictTypeCreateUpdateDto.class))).thenReturn(created);

            mockMvc.perform(post("/dict/types")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsBytes(dto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(1))
                    .andExpect(jsonPath("$.dictCode").value("CUSTOM"));
        }

        @Test
        void whenServiceThrowsBusinessException_returnsProblemDetail() throws Exception {
            when(dictTypeService.query(any(), any()))
                    .thenThrow(new BusinessException(ErrorCode.FORBIDDEN, "缺少有效租户上下文"));

            mockMvc.perform(get("/dict/types").param("page", "0").param("size", "10"))
                    .andExpect(status().isForbidden())
                    .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.status").value(403))
                    .andExpect(jsonPath("$.detail").value(containsString("租户")));
        }
    }

    @Nested
    class DictItemHttp {

        @Test
        void getItems_returns200AndPageJson() throws Exception {
            when(dictItemService.query(any(), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 10), 0));

            mockMvc.perform(get("/dict/items")
                            .param("page", "0")
                            .param("size", "10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray())
                    .andExpect(jsonPath("$.totalElements").value(0));
        }

        @Test
        void getItemById_whenFound_returns200AndBody() throws Exception {
            DictItem item = new DictItem();
            item.setId(1L);
            item.setDictTypeId(10L);
            item.setValue("A");
            item.setLabel("Alpha");
            when(dictItemService.findById(1L)).thenReturn(Optional.of(item));

            mockMvc.perform(get("/dict/items/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(1))
                    .andExpect(jsonPath("$.value").value("A"))
                    .andExpect(jsonPath("$.label").value("Alpha"));
        }

        @Test
        void getItemById_whenNotFound_returns404() throws Exception {
            when(dictItemService.findById(999L)).thenReturn(Optional.empty());

            mockMvc.perform(get("/dict/items/999"))
                    .andExpect(status().isNotFound());
        }

        @Test
        void getItemsByCode_returns200AndArray() throws Exception {
            DictItem item = new DictItem();
            item.setId(1L);
            item.setValue("1");
            item.setLabel("启用");
            when(dictItemService.findByDictCode("STATUS")).thenReturn(List.of(item));

            mockMvc.perform(get("/dict/items/code/STATUS"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].value").value("1"))
                    .andExpect(jsonPath("$[0].label").value("启用"));
        }

        @Test
        void getDictMap_returns200AndMap() throws Exception {
            when(dictItemService.getDictMap("STATUS")).thenReturn(Map.of("1", "启用", "0", "禁用"));

            mockMvc.perform(get("/dict/items/map/STATUS"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.1").value("启用"))
                    .andExpect(jsonPath("$.0").value("禁用"));
        }

        @Test
        void getLabel_returns200AndText() throws Exception {
            when(dictItemService.getLabel("STATUS", "1")).thenReturn("启用");

            mockMvc.perform(get("/dict/items/label/STATUS/1"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("启用")));
        }

        @Test
        void postItem_returns200AndBody() throws Exception {
            DictItemCreateUpdateDto dto = new DictItemCreateUpdateDto();
            dto.setDictTypeId(10L);
            dto.setValue("X");
            dto.setLabel("项X");
            DictItem created = new DictItem();
            created.setId(1L);
            created.setDictTypeId(10L);
            created.setValue("X");
            created.setLabel("项X");
            when(dictItemService.create(any(DictItemCreateUpdateDto.class))).thenReturn(created);

            mockMvc.perform(post("/dict/items")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsBytes(dto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(1))
                    .andExpect(jsonPath("$.value").value("X"));
        }

        @Test
        void deleteItem_returns200() throws Exception {
            mockMvc.perform(delete("/dict/items/1"))
                    .andExpect(status().isOk());
        }
    }
}
