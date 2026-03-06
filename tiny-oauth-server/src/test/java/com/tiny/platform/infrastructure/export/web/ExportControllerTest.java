package com.tiny.platform.infrastructure.export.web;

import com.tiny.platform.infrastructure.export.core.ExportRequest;
import com.tiny.platform.infrastructure.export.persistence.ExportTaskEntity;
import com.tiny.platform.infrastructure.export.service.ExportService;
import com.tiny.platform.infrastructure.export.service.ExportTaskService;
import com.tiny.platform.infrastructure.export.service.ExportTaskStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExportControllerTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void submitAsyncShouldReturnAcceptedLocationAndTaskId() {
        ExportService exportService = Mockito.mock(ExportService.class);
        ExportTaskService exportTaskService = Mockito.mock(ExportTaskService.class);
        ExportController controller = new ExportController(exportService, exportTaskService);
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken("u-1", "N/A", List.of())
        );
        ExportRequest request = new ExportRequest();
        request.setSheets(List.of(new com.tiny.platform.infrastructure.export.core.SheetConfig()));
        when(exportService.submitAsync(request, "u-1")).thenReturn("task-1");

        ResponseEntity<Map<String, String>> response = controller.submitAsync(request);

        assertEquals(202, response.getStatusCode().value());
        assertEquals("/export/task/task-1", response.getHeaders().getLocation().toString());
        assertEquals("task-1", response.getBody().get("taskId"));
    }

    @Test
    void listTasksShouldUseAdminAndUserScope() {
        ExportService exportService = Mockito.mock(ExportService.class);
        ExportTaskService exportTaskService = Mockito.mock(ExportTaskService.class);
        ExportController controller = new ExportController(exportService, exportTaskService);
        ExportTaskEntity adminTask = task("admin-task", "u-admin", ExportTaskStatus.SUCCESS);
        ExportTaskEntity userTask = task("user-task", "u-1", ExportTaskStatus.RUNNING);
        when(exportTaskService.findAllTasks()).thenReturn(List.of(adminTask));
        when(exportTaskService.findUserTasks("u-1")).thenReturn(List.of(userTask));

        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken("admin", "N/A", List.of(new SimpleGrantedAuthority("ROLE_ADMIN")))
        );
        ResponseEntity<?> adminResponse = controller.listTasks();
        assertEquals(List.of(adminTask), adminResponse.getBody());

        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken("u-1", "N/A", List.of())
        );
        ResponseEntity<?> userResponse = controller.listTasks();
        assertEquals(List.of(userTask), userResponse.getBody());
    }

    @Test
    void getTaskShouldReturnNotFoundWhenTaskDoesNotExist() {
        ExportService exportService = Mockito.mock(ExportService.class);
        ExportTaskService exportTaskService = Mockito.mock(ExportTaskService.class);
        ExportController controller = new ExportController(exportService, exportTaskService);
        when(exportTaskService.findByTaskId("missing")).thenReturn(Optional.empty());

        ResponseEntity<?> response = controller.getTask("missing");

        assertEquals(404, response.getStatusCode().value());
        assertFalse(response.hasBody());
    }

    @Test
    void downloadTaskResultShouldEnforceOwnershipAndStreamSuccessFile() throws Exception {
        ExportService exportService = Mockito.mock(ExportService.class);
        ExportTaskService exportTaskService = Mockito.mock(ExportTaskService.class);
        ExportController controller = new ExportController(exportService, exportTaskService);

        ExportTaskEntity task = task("task-1", "owner", ExportTaskStatus.SUCCESS);
        Path file = Files.createTempFile("export-controller-test", ".xlsx");
        byte[] content = "xlsx-content".getBytes(StandardCharsets.UTF_8);
        Files.write(file, content);
        task.setFilePath(file.toString());
        when(exportTaskService.findByTaskId("task-1")).thenReturn(Optional.of(task));

        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken("other-user", "N/A", List.of())
        );
        MockHttpServletResponse forbidden = new MockHttpServletResponse();
        controller.downloadTaskResult("task-1", forbidden);
        assertEquals(403, forbidden.getStatus());

        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken("owner", "N/A", List.of())
        );
        MockHttpServletResponse ok = new MockHttpServletResponse();
        controller.downloadTaskResult("task-1", ok);

        assertEquals(200, ok.getStatus());
        assertEquals("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", ok.getContentType());
        assertTrue(ok.getHeader("Content-Disposition").contains(file.getFileName().toString()));
        assertArrayEquals(content, ok.getContentAsByteArray());

        Files.deleteIfExists(file);
        verify(exportTaskService, Mockito.times(2)).findByTaskId("task-1");
    }

    @Test
    void downloadTaskResultShouldReturnConflictWhenTaskIsNotSuccessful() throws Exception {
        ExportService exportService = Mockito.mock(ExportService.class);
        ExportTaskService exportTaskService = Mockito.mock(ExportTaskService.class);
        ExportController controller = new ExportController(exportService, exportTaskService);

        ExportTaskEntity task = task("task-2", "owner", ExportTaskStatus.RUNNING);
        when(exportTaskService.findByTaskId("task-2")).thenReturn(Optional.of(task));
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken("owner", "N/A", List.of())
        );

        MockHttpServletResponse response = new MockHttpServletResponse();
        controller.downloadTaskResult("task-2", response);

        assertEquals(409, response.getStatus());
        assertEquals("Task status: RUNNING", response.getContentAsString());
    }

    @Test
    void downloadTaskResultShouldReturnNotFoundWhenTaskDoesNotExist() throws Exception {
        ExportService exportService = Mockito.mock(ExportService.class);
        ExportTaskService exportTaskService = Mockito.mock(ExportTaskService.class);
        ExportController controller = new ExportController(exportService, exportTaskService);
        when(exportTaskService.findByTaskId("missing-task")).thenReturn(Optional.empty());
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken("owner", "N/A", List.of())
        );

        MockHttpServletResponse response = new MockHttpServletResponse();
        controller.downloadTaskResult("missing-task", response);

        assertEquals(404, response.getStatus());
    }

    @Test
    void downloadTaskResultShouldReturnNotFoundWhenFileIsMissing() throws Exception {
        ExportService exportService = Mockito.mock(ExportService.class);
        ExportTaskService exportTaskService = Mockito.mock(ExportTaskService.class);
        ExportController controller = new ExportController(exportService, exportTaskService);

        ExportTaskEntity task = task("task-3", "owner", ExportTaskStatus.SUCCESS);
        task.setFilePath("/tmp/export/not-exist-file.xlsx");
        when(exportTaskService.findByTaskId("task-3")).thenReturn(Optional.of(task));
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken("owner", "N/A", List.of())
        );

        MockHttpServletResponse response = new MockHttpServletResponse();
        controller.downloadTaskResult("task-3", response);

        assertEquals(404, response.getStatus());
    }

    private ExportTaskEntity task(String taskId, String userId, ExportTaskStatus status) {
        ExportTaskEntity entity = new ExportTaskEntity();
        entity.setTaskId(taskId);
        entity.setUserId(userId);
        entity.setStatus(status);
        return entity;
    }
}
