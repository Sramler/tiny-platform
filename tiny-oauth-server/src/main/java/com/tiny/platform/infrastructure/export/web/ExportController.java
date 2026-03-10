package com.tiny.platform.infrastructure.export.web;

import com.tiny.platform.infrastructure.core.exception.exception.BusinessException;
import com.tiny.platform.infrastructure.export.core.ExportRequest;
import com.tiny.platform.infrastructure.export.persistence.ExportTaskEntity;
import com.tiny.platform.infrastructure.export.service.ExportService;
import com.tiny.platform.infrastructure.export.service.ExportTaskService;
import com.tiny.platform.infrastructure.export.service.ExportTaskStatus;
import com.tiny.platform.core.oauth.model.SecurityUser;
import jakarta.servlet.AsyncContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/export")
public class ExportController {

    private static final Logger log = LoggerFactory.getLogger(ExportController.class);

    private final ExportService exportService;
    private final ExportTaskService exportTaskService;

    public ExportController(ExportService exportService, ExportTaskService exportTaskService) {
        this.exportService = exportService;
        this.exportTaskService = exportTaskService;
    }

    /** 同步导出（阻塞，先生成临时文件，成功后再回传响应流） */
    @PostMapping("/sync")
    public ResponseEntity<StreamingResponseBody> exportSync(@RequestBody ExportRequest request) {
        validateSyncRequest(request);
        exportService.assertSyncExportWithinRowLimit(request);
        String currentUserId = currentUserId();
        String filename = resolveDownloadFilename(request.getFileName());
        Path tempFile = prepareSyncExportTempFile(request, currentUserId);

        StreamingResponseBody body = out -> {
            try (InputStream inputStream = Files.newInputStream(tempFile)) {
                try {
                    inputStream.transferTo(out);
                    out.flush();
                } catch (Exception ex) {
                    if (isClientAbort(ex)) {
                        log.info("sync export aborted by client userId={} fileName={}", currentUserId, filename);
                        return;
                    }
                    throw new RuntimeException("sync export response stream failed", ex);
                }
            } finally {
                Files.deleteIfExists(tempFile);
            }
        };

        return ResponseEntity
            .ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, buildAttachmentHeader(filename))
            .contentType(MediaType.parseMediaType(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .body(body);
    }

    /** 异步任务提交 */
    @PostMapping("/async")
    public ResponseEntity<Map<String, String>> submitAsync(@RequestBody ExportRequest request) {
        String uid = currentUserId();
        String taskId = exportService.submitAsync(request, uid);
        return ResponseEntity.accepted()
            .location(URI.create("/export/task/" + taskId))
            .body(Map.of("taskId", taskId));
    }

    /** 查询任务状态 */
    @GetMapping("/task/{taskId}")
    public ResponseEntity<?> getTask(@PathVariable String taskId) {
        Optional<ExportTaskEntity> task = exportTaskService.findByTaskId(taskId);
        return task.<ResponseEntity<?>>map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /** 查询当前用户任务，管理员可查看全部 */
    @GetMapping("/task")
    public ResponseEntity<?> listTasks() {
        Authentication auth = currentAuthentication();
        boolean isAdmin = hasAdminAuthority(auth);
        String uid = currentUserId(auth);
        if (isAdmin) {
            return ResponseEntity.ok(exportTaskService.findAllTasks());
        }
        return ResponseEntity.ok(exportTaskService.findUserTasks(uid));
    }

    /** 下载异步结果 */
    @GetMapping("/task/{taskId}/download")
    public void downloadTaskResult(@PathVariable String taskId,
                                   HttpServletResponse response) throws Exception {
        Optional<ExportTaskEntity> taskOpt = exportTaskService.findByTaskId(taskId);
        if (taskOpt.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        ExportTaskEntity task = taskOpt.get();
        Authentication auth = currentAuthentication();
        String requester = currentUserId(auth);
        if (!requester.equals(task.getUserId()) && !hasAdminAuthority(auth)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            return;
        }
        if (task.getStatus() != ExportTaskStatus.SUCCESS) {
            response.setStatus(HttpServletResponse.SC_CONFLICT);
            response.getWriter().write("Task status: " + task.getStatus());
            return;
        }
        if (task.getFilePath() == null) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        Path file = Path.of(task.getFilePath());
        if (!Files.exists(file)) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, buildAttachmentHeader(file.getFileName().toString()));
        try (InputStream is = Files.newInputStream(file); OutputStream os = response.getOutputStream()) {
            is.transferTo(os);
            os.flush();
        }
    }

    /** Servlet 异步写回（短任务演示） */
    @PostMapping("/async-servlet")
    public void exportAsyncServlet(@RequestBody ExportRequest request,
                                   HttpServletRequest httpRequest,
                                   HttpServletResponse response) {
        String uid = currentUserId();
        AsyncContext async = httpRequest.startAsync();
        async.setTimeout(60_000L);
        async.start(() -> {
            try (OutputStream os = response.getOutputStream()) {
                response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
                response.setHeader(HttpHeaders.CONTENT_DISPOSITION, buildAttachmentHeader("export-asyncservlet.xlsx"));
                exportService.exportSync(request, os, uid);
                os.flush();
            } catch (Exception ex) {
                try {
                    response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                    response.getWriter().write("Export failed: " + ex.getMessage());
                    response.getWriter().flush();
                } catch (Exception ignored) {
                }
            } finally {
                async.complete();
            }
        });
    }

    private Authentication currentAuthentication() {
        return SecurityContextHolder.getContext().getAuthentication();
    }

    private void validateSyncRequest(ExportRequest request) {
        if (request == null || request.getSheets() == null || request.getSheets().isEmpty()) {
            throw BusinessException.validationError("sheets 不能为空，至少包含一个 sheet");
        }
    }

    private String resolveDownloadFilename(String rawFileName) {
        String normalized = rawFileName == null ? "" : rawFileName
            .replace("\r", "")
            .replace("\n", "")
            .replace("\"", "")
            .replace("/", "_")
            .replace("\\", "_")
            .trim();
        if (normalized.isEmpty()) {
            normalized = "export";
        }
        if (!normalized.toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
            normalized = normalized + ".xlsx";
        }
        return normalized;
    }

    private String buildAttachmentHeader(String filename) {
        return ContentDisposition.attachment()
            .filename(filename, StandardCharsets.UTF_8)
            .build()
            .toString();
    }

    private boolean isClientAbort(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof IOException) {
                String message = current.getMessage();
                if (message == null) {
                    return true;
                }
                String lower = message.toLowerCase(Locale.ROOT);
                if (lower.contains("broken pipe")
                    || lower.contains("connection reset")
                    || lower.contains("connection aborted")
                    || lower.contains("clientabort")) {
                    return true;
                }
            }
            String className = current.getClass().getName();
            if (className.endsWith("ClientAbortException")
                || className.endsWith("AsyncRequestNotUsableException")
                || className.endsWith("EofException")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private Path prepareSyncExportTempFile(ExportRequest request, String currentUserId) {
        Path tempFile = null;
        try {
            tempFile = Files.createTempFile("export-sync-", ".xlsx");
            try (OutputStream outputStream = Files.newOutputStream(tempFile)) {
                exportService.exportSync(request, outputStream, currentUserId);
                outputStream.flush();
            }
            return tempFile;
        } catch (Exception ex) {
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException cleanupEx) {
                    log.warn("failed to cleanup sync export temp file {}", tempFile, cleanupEx);
                }
            }
            if (ex instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new RuntimeException("sync export generation failed", ex);
        }
    }

    private String currentUserId() {
        return currentUserId(currentAuthentication());
    }

    private String currentUserId(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return "anonymous";
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof SecurityUser securityUser && securityUser.getUserId() != null) {
            return String.valueOf(securityUser.getUserId());
        }
        if (principal instanceof UserDetails userDetails) {
            return userDetails.getUsername();
        }
        String name = authentication.getName();
        if (name == null || name.isBlank() || "anonymousUser".equalsIgnoreCase(name)) {
            return "anonymous";
        }
        return name;
    }

    private boolean hasAdminAuthority(Authentication authentication) {
        if (authentication == null) {
            return false;
        }
        return authentication.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .anyMatch(auth -> "ROLE_ADMIN".equalsIgnoreCase(auth) || "ADMIN".equalsIgnoreCase(auth));
    }
}
