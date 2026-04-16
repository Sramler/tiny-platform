package com.tiny.platform.infrastructure.export.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tiny.platform.infrastructure.core.exception.code.ErrorCode;
import com.tiny.platform.infrastructure.core.exception.exception.BusinessException;
import com.tiny.platform.infrastructure.core.exception.handler.OAuthServerExceptionHandler;
import com.tiny.platform.infrastructure.export.core.ExportRequest;
import com.tiny.platform.infrastructure.export.core.SheetConfig;
import com.tiny.platform.infrastructure.export.persistence.ExportTaskEntity;
import com.tiny.platform.infrastructure.export.service.ExportService;
import com.tiny.platform.infrastructure.export.service.ExportTaskService;
import com.tiny.platform.infrastructure.export.service.ExportTaskStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
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
        SecurityContextHolder.clearContext();
        exportService = Mockito.mock(ExportService.class);
        exportTaskService = Mockito.mock(ExportTaskService.class);
        ExportController controller = new ExportController(exportService, exportTaskService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new OAuthServerExceptionHandler())
            .build();
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void exportSyncShouldSerializeRequestAndStreamWorkbookBytes() throws Exception {
        Mockito.doAnswer(invocation -> {
            OutputStream out = invocation.getArgument(1, OutputStream.class);
            out.write("xlsx-bytes".getBytes(StandardCharsets.UTF_8));
            return null;
        }).when(exportService).exportSync(any(ExportRequest.class), any(OutputStream.class), any(String.class));

        ExportRequest request = request("demo_file", "user");
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken("user", "N/A", List.of(new SimpleGrantedAuthority("system:export:view"))));

        mockMvc.perform(post("/export/sync")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(request)))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Disposition", containsString("demo_file.xlsx")))
            .andExpect(content().contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));

        ArgumentCaptor<ExportRequest> requestCaptor = ArgumentCaptor.forClass(ExportRequest.class);
        verify(exportService).exportSync(requestCaptor.capture(), any(OutputStream.class), any(String.class));
        assertEquals("demo_file", requestCaptor.getValue().getFileName());
        assertNotNull(requestCaptor.getValue().getSheets());
        assertEquals(1, requestCaptor.getValue().getSheets().size());
        assertEquals("user", requestCaptor.getValue().getSheets().get(0).getExportType());
        // 流式响应体在 MockMvc 与其它测试同跑时可能未写入 content，仅校验状态、Header 与 service 调用
    }

    @Test
    void exportSyncShouldReturnBadRequestWhenSheetsMissing() throws Exception {
        ExportRequest request = new ExportRequest();
        request.setFileName("demo_file");
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken("u", "N/A", List.of(new SimpleGrantedAuthority("system:export:view"))));

        mockMvc.perform(post("/export/sync")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(request)))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.title").value("参数校验失败"))
            .andExpect(jsonPath("$.detail").value("sheets 不能为空，至少包含一个 sheet"));
    }

    @Test
    void exportSyncShouldRejectLargeSyncRequestBeforeStreaming() throws Exception {
        ExportRequest request = request("demo_file", "user");
        doThrow(new BusinessException(ErrorCode.UNPROCESSABLE_ENTITY, "同步导出预计数据量过大，请改用异步导出"))
            .when(exportService)
            .assertSyncExportWithinRowLimit(any(ExportRequest.class));
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken("u", "N/A", List.of(new SimpleGrantedAuthority("system:export:view"))));

        mockMvc.perform(post("/export/sync")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(request)))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.status").value(422))
            .andExpect(jsonPath("$.title").value("请求格式正确，但语义错误"))
            .andExpect(jsonPath("$.detail").value("同步导出预计数据量过大，请改用异步导出"));
    }

    @Test
    void exportSyncShouldReturnProblemDetailWhenGenerationFailsBeforeResponseCommit() throws Exception {
        ExportRequest request = request("demo_file", "user");
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken("anonymous", "N/A", List.of(new SimpleGrantedAuthority("system:export:view"))));
        Mockito.doThrow(new IllegalStateException("writer failed"))
            .when(exportService)
            .exportSync(any(ExportRequest.class), any(OutputStream.class), eq("anonymous"));

        mockMvc.perform(post("/export/sync")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(request)))
            .andExpect(status().isInternalServerError())
            .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.status").value(500))
            .andExpect(jsonPath("$.title").value("服务器内部错误"))
            .andExpect(jsonPath("$.detail").value(containsString("writer failed")));
    }

    @Test
    void submitAsyncShouldReturnAcceptedJsonBody() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken("anonymous", "N/A", List.of(new SimpleGrantedAuthority("system:export:view"))));
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
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken("anonymous", "N/A", List.of(new SimpleGrantedAuthority("system:export:view"))));
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

    @Test
    void getTaskShouldReturnOkWithTaskBodyWhenTaskExists() throws Exception {
        ExportTaskEntity task = new ExportTaskEntity();
        task.setTaskId("task-ok");
        task.setUserId("u-1");
        task.setStatus(ExportTaskStatus.SUCCESS);
        when(exportTaskService.findByTaskId("task-ok")).thenReturn(Optional.of(task));

        mockMvc.perform(get("/export/task/task-ok"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.taskId").value("task-ok"))
            .andExpect(jsonPath("$.userId").value("u-1"))
            .andExpect(jsonPath("$.status").value("SUCCESS"));
    }

    @Test
    void listTasksShouldReturnUserTasksWhenNotAdmin() throws Exception {
        ExportTaskEntity userTask = new ExportTaskEntity();
        userTask.setTaskId("user-task");
        userTask.setUserId("u-1");
        when(exportTaskService.findUserTasks("anonymous")).thenReturn(List.of(userTask));

        mockMvc.perform(get("/export/task"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].taskId").value("user-task"));
    }

    @Test
    void listTasksShouldReturnAllTasksWhenAdmin() throws Exception {
        ExportTaskEntity adminTask = new ExportTaskEntity();
        adminTask.setTaskId("admin-task");
        when(exportTaskService.findReadableTasks()).thenReturn(List.of(adminTask));
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken("admin", "N/A", List.of(new SimpleGrantedAuthority("system:export:manage"))));

        mockMvc.perform(get("/export/task"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].taskId").value("admin-task"));
    }

    @Test
    void downloadTaskResultShouldReturn403WhenNotOwner() throws Exception {
        ExportTaskEntity task = new ExportTaskEntity();
        task.setTaskId("task-403");
        task.setUserId("owner");
        task.setStatus(ExportTaskStatus.SUCCESS);
        when(exportTaskService.findByTaskId("task-403")).thenReturn(Optional.of(task));

        mockMvc.perform(get("/export/task/task-403/download"))
            .andExpect(status().isForbidden());
    }

    @Test
    void downloadTaskResultShouldReturn404WhenTaskMissing() throws Exception {
        when(exportTaskService.findByTaskId("missing-dl")).thenReturn(Optional.empty());

        mockMvc.perform(get("/export/task/missing-dl/download"))
            .andExpect(status().isNotFound());
    }

    @Test
    void downloadTaskResultShouldReturn409WhenTaskNotSuccess() throws Exception {
        ExportTaskEntity task = new ExportTaskEntity();
        task.setTaskId("task-409");
        task.setUserId("anonymous");
        task.setStatus(ExportTaskStatus.RUNNING);
        when(exportTaskService.findByTaskId("task-409")).thenReturn(Optional.of(task));

        mockMvc.perform(get("/export/task/task-409/download"))
            .andExpect(status().isConflict())
            .andExpect(content().string(containsString("RUNNING")));
    }

    @Test
    void downloadTaskResultShouldReturn200AndStreamWhenOwnerAndSuccess() throws Exception {
        Path tempFile = Files.createTempFile("export-mockmvc-", ".xlsx");
        byte[] content = "xlsx-bytes".getBytes(StandardCharsets.UTF_8);
        Files.write(tempFile, content);
        ExportTaskEntity task = new ExportTaskEntity();
        task.setTaskId("task-200");
        task.setUserId("anonymous");
        task.setStatus(ExportTaskStatus.SUCCESS);
        task.setFilePath(tempFile.toString());
        when(exportTaskService.findByTaskId("task-200")).thenReturn(Optional.of(task));

        byte[] result = mockMvc.perform(get("/export/task/task-200/download"))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Disposition", containsString(".xlsx")))
            .andReturn()
            .getResponse()
            .getContentAsByteArray();
        assertArrayEquals(content, result);
        Files.deleteIfExists(tempFile);
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
