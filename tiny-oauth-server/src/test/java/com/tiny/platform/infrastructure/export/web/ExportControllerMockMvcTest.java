package com.tiny.platform.infrastructure.export.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tiny.platform.infrastructure.core.exception.code.ErrorCode;
import com.tiny.platform.infrastructure.core.exception.exception.BusinessException;
import com.tiny.platform.infrastructure.core.exception.handler.OAuthServerExceptionHandler;
import com.tiny.platform.infrastructure.export.core.ExportRequest;
import com.tiny.platform.infrastructure.export.core.SheetConfig;
import com.tiny.platform.infrastructure.export.service.ExportService;
import com.tiny.platform.infrastructure.export.service.ExportTaskService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ExportControllerMockMvcTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private ExportService exportService;
    private ExportTaskService exportTaskService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        exportService = Mockito.mock(ExportService.class);
        exportTaskService = Mockito.mock(ExportTaskService.class);
        ExportController controller = new ExportController(exportService, exportTaskService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new OAuthServerExceptionHandler())
            .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
            .build();
    }

    @Test
    void exportSyncShouldSerializeRequestAndStreamWorkbookBytes() throws Exception {
        Mockito.doAnswer(invocation -> {
            OutputStream out = invocation.getArgument(1, OutputStream.class);
            out.write("xlsx-bytes".getBytes(StandardCharsets.UTF_8));
            return null;
        }).when(exportService).exportSync(any(ExportRequest.class), any(OutputStream.class), eq("anonymous"));

        ExportRequest request = request("demo_file", "user");

        mockMvc.perform(post("/export/sync")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(request)))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Disposition", "attachment; filename=\"demo_file.xlsx\""))
            .andExpect(content().contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .andExpect(content().bytes("xlsx-bytes".getBytes(StandardCharsets.UTF_8)));

        ArgumentCaptor<ExportRequest> requestCaptor = ArgumentCaptor.forClass(ExportRequest.class);
        verify(exportService).exportSync(requestCaptor.capture(), any(OutputStream.class), eq("anonymous"));
        assertEquals("demo_file", requestCaptor.getValue().getFileName());
        assertNotNull(requestCaptor.getValue().getSheets());
        assertEquals(1, requestCaptor.getValue().getSheets().size());
        assertEquals("user", requestCaptor.getValue().getSheets().get(0).getExportType());
    }

    @Test
    void submitAsyncShouldReturnAcceptedJsonBody() throws Exception {
        when(exportService.submitAsync(any(ExportRequest.class), eq("anonymous"))).thenReturn("task-100");

        mockMvc.perform(post("/export/async")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(request("demo_async", "user"))))
            .andExpect(status().isAccepted())
            .andExpect(header().string("Location", "/export/task/task-100"))
            .andExpect(jsonPath("$.taskId").value("task-100"));
    }

    @Test
    void submitAsyncShouldMapBusinessExceptionToProblemDetail() throws Exception {
        when(exportService.submitAsync(any(ExportRequest.class), eq("anonymous")))
            .thenThrow(new BusinessException(ErrorCode.TOO_MANY_REQUESTS, "您有过多并发导出任务，请稍后重试"));

        mockMvc.perform(post("/export/async")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(request("demo_async", "user"))))
            .andExpect(status().isTooManyRequests())
            .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.status").value(429))
            .andExpect(jsonPath("$.title").value("请求过于频繁"))
            .andExpect(jsonPath("$.detail").value("您有过多并发导出任务，请稍后重试"));
    }

    @Test
    void getTaskShouldReturnNotFoundWhenTaskMissing() throws Exception {
        when(exportTaskService.findByTaskId("missing")).thenReturn(Optional.empty());

        mockMvc.perform(get("/export/task/missing"))
            .andExpect(status().isNotFound())
            .andExpect(content().string(""));
    }

    private ExportRequest request(String fileName, String exportType) {
        ExportRequest request = new ExportRequest();
        request.setFileName(fileName);
        SheetConfig sheet = new SheetConfig();
        sheet.setSheetName("sheet-1");
        sheet.setExportType(exportType);
        request.setSheets(List.of(sheet));
        return request;
    }
}
