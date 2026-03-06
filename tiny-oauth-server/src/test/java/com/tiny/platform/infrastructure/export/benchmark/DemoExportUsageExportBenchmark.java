package com.tiny.platform.infrastructure.export.benchmark;

import com.tiny.platform.OauthServerApplication;
import com.tiny.platform.infrastructure.export.core.ColumnNode;
import com.tiny.platform.infrastructure.export.core.ExportRequest;
import com.tiny.platform.infrastructure.export.core.SheetConfig;
import com.tiny.platform.infrastructure.export.demo.DemoExportUsageService;
import com.tiny.platform.infrastructure.export.service.ExportService;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.OutputStream;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.ThreadMXBean;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * DemoExportUsage 导出性能基准（100w / 1000w）。
 * 运行方式：
 * 1) mvn -pl tiny-oauth-server -DskipTests test-compile dependency:build-classpath -Dmdep.outputFile=/tmp/tiny-oauth-cp.txt
 * 2) java -cp "tiny-oauth-server/target/test-classes:tiny-oauth-server/target/classes:$(cat /tmp/tiny-oauth-cp.txt)" \
 *      com.tiny.platform.infrastructure.export.benchmark.DemoExportUsageExportBenchmark
 */
public final class DemoExportUsageExportBenchmark {

    private static final long TENANT_ID = 1L;

    private DemoExportUsageExportBenchmark() {
    }

    public static void main(String[] args) throws Exception {
        boolean skipGenerate = hasFlag(args, "--skip-generate");
        ConfigurableApplicationContext context = new SpringApplicationBuilder(OauthServerApplication.class)
            .properties(
                "server.port=0",
                "logging.level.root=INFO",
                "logging.level.com.tiny.platform.infrastructure.export=INFO",
                "logging.level.org.camunda=ERROR",
                "logging.level.org.camunda.bpm.engine.persistence=ERROR",
                "logging.level.org.springframework.security=ERROR",
                "logging.level.org.hibernate.SQL=ERROR",
                "http.request-log.enabled=false"
            )
            .run(args);
        try {
            DemoExportUsageService demoService = context.getBean(DemoExportUsageService.class);
            ExportService exportService = context.getBean(ExportService.class);
            JdbcTemplate jdbcTemplate = context.getBean(JdbcTemplate.class);

            List<Integer> targets = parseTargets(args);
            for (Integer target : targets) {
                String label = target >= 10_000_000 ? "10M" : (target >= 1_000_000 ? "1M" : target + "ROWS");
                runScenario(label, target, skipGenerate, demoService, exportService, jdbcTemplate);
            }
        } finally {
            context.close();
        }
    }

    private static void runScenario(
            String label,
            int targetRows,
            boolean skipGenerate,
            DemoExportUsageService demoService,
            ExportService exportService,
            JdbcTemplate jdbcTemplate
    ) throws Exception {
        System.out.printf("=== Scenario %s (targetRows=%d) ===%n", label, targetRows);

        long generatedRows;
        if (!skipGenerate) {
            GenerationPlan generationPlan = resolveGenerationPlan(targetRows);
            Instant genStart = Instant.now();
            demoService.generateDemoData(
                TENANT_ID,
                generationPlan.days(),
                generationPlan.rowsPerDay(),
                targetRows,
                true
            );
            generatedRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM demo_export_usage WHERE tenant_id = ?",
                Long.class,
                TENANT_ID
            );
            long generationMs = Duration.between(genStart, Instant.now()).toMillis();
            System.out.printf(
                "Data generation done: rows=%d, durationMs=%d, days=%d, rowsPerDay=%d%n",
                generatedRows, generationMs, generationPlan.days(), generationPlan.rowsPerDay()
            );
        } else {
            generatedRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM demo_export_usage WHERE tenant_id = ?",
                Long.class,
                TENANT_ID
            );
            System.out.printf(
                "Data generation skipped: rows=%d%n",
                generatedRows
            );
        }

        ExportRequest request = buildRequest();
        Path outFile = Files.createTempFile("demo-export-" + label.toLowerCase() + "-", ".xlsx");

        GcSnapshot gcBefore = GcSnapshot.capture();
        MemorySnapshot memBefore = MemorySnapshot.capture();
        ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
        int threadBefore = threadMXBean.getThreadCount();
        ResourceSampler sampler = new ResourceSampler();
        sampler.start();

        Instant exportStart = Instant.now();
        boolean success = false;
        String error = null;
        try (OutputStream os = Files.newOutputStream(outFile)) {
            exportService.exportSync(request, os, "benchmark");
            success = true;
        } catch (Exception ex) {
            error = ex.getClass().getSimpleName() + ": " + ex.getMessage();
        }
        long exportMs = Duration.between(exportStart, Instant.now()).toMillis();
        sampler.stop();

        GcSnapshot gcAfter = GcSnapshot.capture();
        MemorySnapshot memAfter = MemorySnapshot.capture();
        int threadAfter = threadMXBean.getThreadCount();

        forceGc(3, 1500L);
        MemorySnapshot memAfterGc = MemorySnapshot.capture();
        GcSnapshot gcAfterForced = GcSnapshot.capture();

        long fileSizeBytes = Files.exists(outFile) ? Files.size(outFile) : 0L;
        System.out.printf(
            "RESULT label=%s success=%s rows=%d exportMs=%d fileBytes=%d error=\"%s\"%n",
            label, success, generatedRows, exportMs, fileSizeBytes, error == null ? "" : error
        );
        System.out.printf(
            "MEMORY label=%s heapUsedBeforeMB=%.2f heapUsedAfterMB=%.2f heapUsedAfterGcMB=%.2f heapCommittedAfterGcMB=%.2f peakHeapUsedMB=%.2f%n",
            label,
            memBefore.heapUsedMb(),
            memAfter.heapUsedMb(),
            memAfterGc.heapUsedMb(),
            memAfterGc.heapCommittedMb(),
            sampler.getPeakHeapUsedMb()
        );
        System.out.printf(
            "GC label=%s gcCountDelta=%d gcTimeDeltaMs=%d gcCountAfterForcedDelta=%d gcTimeAfterForcedDeltaMs=%d%n",
            label,
            gcAfter.count - gcBefore.count,
            gcAfter.timeMs - gcBefore.timeMs,
            gcAfterForced.count - gcBefore.count,
            gcAfterForced.timeMs - gcBefore.timeMs
        );
        System.out.printf(
            "THREAD label=%s threadBefore=%d threadAfter=%d peakThreadCount=%d%n",
            label,
            threadBefore,
            threadAfter,
            sampler.getPeakThreadCount()
        );
        System.out.println();
    }

    private static ExportRequest buildRequest() {
        ExportRequest request = new ExportRequest();
        request.setFileName("demo-export-benchmark");
        request.setPageSize(5000);

        SheetConfig sheet = new SheetConfig();
        sheet.setSheetName("demo_export_usage");
        sheet.setExportType("demo_export_usage");
        Map<String, Object> filters = new HashMap<>();
        filters.put("tenantId", TENANT_ID);
        sheet.setFilters(filters);
        sheet.setColumns(List.of(
            leaf("ID", "id"),
            leaf("TenantID", "tenantId"),
            leaf("UsageDate", "usageDate"),
            leaf("ProductCode", "productCode"),
            leaf("ProductName", "productName"),
            leaf("UsageQty", "usageQty"),
            leaf("Amount", "amount"),
            leaf("Currency", "currency"),
            leaf("Status", "status"),
            leaf("CreatedAt", "createdAt")
        ));
        request.setSheets(List.of(sheet));
        return request;
    }

    private static ColumnNode leaf(String title, String field) {
        ColumnNode node = new ColumnNode();
        node.setTitle(title);
        node.setField(field);
        node.setChildren(new ArrayList<>());
        return node;
    }

    private static void forceGc(int rounds, long sleepMs) throws InterruptedException {
        for (int i = 0; i < rounds; i++) {
            System.gc();
            Thread.sleep(sleepMs);
        }
    }

    private static List<Integer> parseTargets(String[] args) {
        if (args == null || args.length == 0) {
            return List.of(1_000_000, 10_000_000);
        }
        List<Integer> targets = new ArrayList<>();
        for (String arg : args) {
            if (arg == null || arg.isBlank()) {
                continue;
            }
            String normalized = arg.trim().toUpperCase();
            if (normalized.startsWith("--")) {
                continue;
            }
            if ("1M".equals(normalized)) {
                targets.add(1_000_000);
                continue;
            }
            if ("10M".equals(normalized)) {
                targets.add(10_000_000);
                continue;
            }
            targets.add(Integer.parseInt(normalized));
        }
        return targets.isEmpty() ? List.of(1_000_000, 10_000_000) : targets;
    }

    private static boolean hasFlag(String[] args, String flag) {
        if (args == null || args.length == 0 || flag == null || flag.isBlank()) {
            return false;
        }
        for (String arg : args) {
            if (flag.equalsIgnoreCase(arg == null ? "" : arg.trim())) {
                return true;
            }
        }
        return false;
    }

    private static GenerationPlan resolveGenerationPlan(int targetRows) {
        if (targetRows >= 10_000_000) {
            return new GenerationPlan(80, 200_000);
        }
        if (targetRows >= 1_000_000) {
            return new GenerationPlan(40, 80_000);
        }
        return new GenerationPlan(7, 10_000);
    }

    private record GenerationPlan(int days, int rowsPerDay) {
    }

    private record MemorySnapshot(long heapUsed, long heapCommitted) {
        static MemorySnapshot capture() {
            MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();
            MemoryUsage heap = memoryMXBean.getHeapMemoryUsage();
            return new MemorySnapshot(heap.getUsed(), heap.getCommitted());
        }

        double heapUsedMb() {
            return heapUsed / 1024.0 / 1024.0;
        }

        double heapCommittedMb() {
            return heapCommitted / 1024.0 / 1024.0;
        }
    }

    private record GcSnapshot(long count, long timeMs) {
        static GcSnapshot capture() {
            long count = 0;
            long time = 0;
            for (GarbageCollectorMXBean bean : ManagementFactory.getGarbageCollectorMXBeans()) {
                long c = bean.getCollectionCount();
                long t = bean.getCollectionTime();
                if (c >= 0) {
                    count += c;
                }
                if (t >= 0) {
                    time += t;
                }
            }
            return new GcSnapshot(count, time);
        }
    }

    private static final class ResourceSampler {
        private final MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();
        private final ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
        private final AtomicLong peakHeapUsed = new AtomicLong(0);
        private final AtomicLong peakThreadCount = new AtomicLong(0);
        private ScheduledExecutorService scheduler;

        void start() {
            scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "export-benchmark-sampler");
                t.setDaemon(true);
                return t;
            });
            scheduler.scheduleAtFixedRate(this::sampleOnce, 0, 1, TimeUnit.SECONDS);
        }

        void stop() {
            if (scheduler == null) {
                return;
            }
            scheduler.shutdownNow();
            scheduler = null;
            sampleOnce();
        }

        double getPeakHeapUsedMb() {
            return peakHeapUsed.get() / 1024.0 / 1024.0;
        }

        long getPeakThreadCount() {
            return peakThreadCount.get();
        }

        private void sampleOnce() {
            long heapUsed = memoryMXBean.getHeapMemoryUsage().getUsed();
            peakHeapUsed.accumulateAndGet(heapUsed, Math::max);
            peakThreadCount.accumulateAndGet(threadMXBean.getThreadCount(), Math::max);
        }
    }
}
