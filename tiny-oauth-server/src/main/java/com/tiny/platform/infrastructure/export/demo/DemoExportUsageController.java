package com.tiny.platform.infrastructure.export.demo;

import com.tiny.platform.infrastructure.core.dto.PageResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.bind.annotation.*;

import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/demo/export-usage")
public class DemoExportUsageController {

    private final DemoExportUsageService service;
    private final ThreadPoolTaskExecutor exportExecutor;

    public DemoExportUsageController(DemoExportUsageService service,
                                     ThreadPoolTaskExecutor exportExecutor) {
        this.service = service;
        this.exportExecutor = exportExecutor;
    }

    @PostMapping
    public ResponseEntity<DemoExportUsageEntity> create(@RequestBody DemoExportUsageEntity entity) {
        return ResponseEntity.ok(service.create(entity));
    }

    /**
     * 调用存储过程生成批量测试数据
     */
    @PostMapping("/generate")
    public ResponseEntity<Map<String, Object>> generate(
            @RequestParam(defaultValue = "7") int days,
            @RequestParam(defaultValue = "2000") int rowsPerDay,
            @RequestParam(defaultValue = "0") int targetRows,
            @RequestParam(defaultValue = "false") boolean clearExisting) {
        long start = System.currentTimeMillis();
        // #region agent log
        try (FileWriter fw = new FileWriter("/Users/bliu/code/tiny-platform/.cursor/debug.log", true)) {
            fw.write("{\"sessionId\":\"debug-session\",\"runId\":\"pre-fix\",\"hypothesisId\":\"H2\",\"location\":\"DemoExportUsageController.generate:before\",\"message\":\"generate endpoint called\",\"data\":{\"days\":" + days + ",\"rowsPerDay\":" + rowsPerDay + ",\"targetRows\":" + targetRows + ",\"clearExisting\":" + clearExisting + "},\"timestamp\":" + System.currentTimeMillis() + "}\n");
        } catch (IOException ignored) {
        }
        // #endregion agent log

        // 同步调用存储过程，让前端等待任务完成（配合前端单次请求自定义 timeout）
        service.generateDemoData(days, rowsPerDay, targetRows, clearExisting);

        long elapsed = System.currentTimeMillis() - start;
        // #region agent log
        try (FileWriter fw = new FileWriter("/Users/bliu/code/tiny-platform/.cursor/debug.log", true)) {
            fw.write("{\"sessionId\":\"debug-session\",\"runId\":\"pre-fix\",\"hypothesisId\":\"H2\",\"location\":\"DemoExportUsageController.generate:after\",\"message\":\"generate endpoint finished\",\"data\":{\"elapsedMs\":" + elapsed + "},\"timestamp\":" + System.currentTimeMillis() + "}\n");
        } catch (IOException ignored) {
        }
        // #endregion agent log

        return ResponseEntity.ok(Map.of(
                "message", "已生成测试数据",
                "days", days,
                "rowsPerDay", rowsPerDay,
                "targetRows", targetRows,
                "clearExisting", clearExisting,
                "elapsedMs", elapsed
        ));
    }

    /**
     * 清空所有测试数据
     */
    @PostMapping("/clear")
    public ResponseEntity<Map<String, Object>> clear() {
        service.clearAll();
        return ResponseEntity.ok(Map.of("message", "已清空所有测试数据"));
    }

    @PutMapping("/{id}")
    public ResponseEntity<DemoExportUsageEntity> update(@PathVariable Long id,
                                                        @RequestBody DemoExportUsageEntity entity) {
        return ResponseEntity.ok(service.update(id, entity));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{id}")
    public ResponseEntity<DemoExportUsageEntity> get(@PathVariable Long id) {
        Optional<DemoExportUsageEntity> opt = service.findById(id);
        return opt.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * 分页查询测试数据列表（REST 风格：GET /demo/export-usage）
     */
    @GetMapping
    public ResponseEntity<PageResponse<DemoExportUsageEntity>> list(
            @RequestParam(required = false) String tenantCode,
            @RequestParam(required = false) String productCode,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate,
            @PageableDefault(size = 10, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(
                new PageResponse<>(service.search(tenantCode, productCode, status, startDate, endDate, pageable)));
    }
}


