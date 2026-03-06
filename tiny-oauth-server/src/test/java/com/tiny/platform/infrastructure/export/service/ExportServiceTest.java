package com.tiny.platform.infrastructure.export.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tiny.platform.infrastructure.core.exception.exception.BusinessException;
import com.tiny.platform.infrastructure.export.core.ColumnNode;
import com.tiny.platform.infrastructure.export.core.DataProvider;
import com.tiny.platform.infrastructure.export.core.ExportRequest;
import com.tiny.platform.infrastructure.export.core.FilterAwareDataProvider;
import com.tiny.platform.infrastructure.export.core.SheetConfig;
import com.tiny.platform.infrastructure.export.core.TopInfoDecorator;
import com.tiny.platform.infrastructure.export.service.SheetWriteModel;
import com.tiny.platform.infrastructure.export.writer.WriterAdapter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExportServiceTest {

    private final List<ThreadPoolTaskExecutor> executors = new ArrayList<>();

    @AfterEach
    void tearDown() {
        for (ThreadPoolTaskExecutor executor : executors) {
            executor.shutdown();
        }
    }

    @Test
    void exportSyncShouldCapPageSizeAndPassLeafFieldsIntoFilterAwareProvider() throws Exception {
        TrackingFilterAwareProvider provider = new TrackingFilterAwareProvider();
        provider.rows.add(Map.of("id", 1L, "name", "A"));
        provider.rows.add(Map.of("id", 2L, "name", "B"));
        CapturingWriterAdapter writer = new CapturingWriterAdapter();
        TopInfoDecorator decorator = (request, exportType) -> List.of(
            List.of("租户A"),
            List.of("导出人")
        );
        ExportTaskService taskService = Mockito.mock(ExportTaskService.class);

        ExportService service = new ExportService(
            writer,
            Map.of("user", provider),
            decorator,
            Map.of(),
            newExecutor(),
            taskService,
            new ObjectMapper(),
            10,
            3,
            2,
            5000,
            new SimpleMeterRegistry()
        );

        ExportRequest request = new ExportRequest();
        request.setPageSize(9999);
        SheetConfig sheet = new SheetConfig();
        sheet.setSheetName("用户列表");
        sheet.setExportType("user");
        sheet.setFilters(new LinkedHashMap<>(Map.of("tenantId", 7, "__mode", "page")));
        sheet.setColumns(List.of(
            new ColumnNode("ID", "id", null),
            new ColumnNode("姓名", "name", null)
        ));
        request.setSheets(List.of(sheet));

        service.exportSync(request, new ByteArrayOutputStream(), "u-1");

        assertEquals(5000, provider.batchSizeSeen.get());
        assertEquals(List.of("id", "name"), provider.leafFieldsSeen.get());
        assertEquals(7, provider.filtersSeenInFetch.get().get("tenantId"));
        assertEquals("page", provider.filtersSeenInFetch.get().get("__mode"));
        assertTrue(provider.clearCalls.get() >= 1);
        assertEquals(List.of(List.of("租户A"), List.of("导出人")), writer.topInfoRows);
        assertEquals(List.of(List.of("ID"), List.of("姓名")), writer.head);
        assertEquals(List.of(List.of(1L, "A"), List.of(2L, "B")), writer.rows);
    }

    @Test
    void submitAsyncShouldRejectBeforeTaskCreationWhenQueueIsSaturated() {
        @SuppressWarnings("unchecked")
        Map<String, DataProvider<?>> providers = Map.of();
        WriterAdapter writer = (out, sheets) -> {};
        ExportTaskService taskService = Mockito.mock(ExportTaskService.class);
        ThreadPoolTaskExecutor executor = Mockito.mock(ThreadPoolTaskExecutor.class);
        ThreadPoolExecutor threadPool = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>());
        threadPool.getQueue().add(() -> {});
        when(executor.getThreadPoolExecutor()).thenReturn(threadPool);
        when(executor.getActiveCount()).thenReturn(0);
        when(executor.getPoolSize()).thenReturn(1);

        ExportService service = new ExportService(
            writer,
            providers,
            (request, exportType) -> List.of(),
            Map.of(),
            executor,
            taskService,
            new ObjectMapper(),
            0,
            3,
            2,
            5000,
            new SimpleMeterRegistry()
        );

        ExportRequest request = new ExportRequest();
        SheetConfig sheet = new SheetConfig();
        sheet.setSheetName("用户列表");
        sheet.setExportType("missing");
        sheet.setColumns(List.of(new ColumnNode("ID", "id", null)));
        request.setSheets(List.of(sheet));

        Exception ex = assertThrows(BusinessException.class, () -> service.submitAsync(request, "u-1"));
        assertTrue(ex.getMessage().contains("任务排队过多"));
        verify(taskService, never()).createPendingTask(anyString(), anyString(), anyString(), anyInt(), any(), any());
        threadPool.shutdownNow();
    }

    private ThreadPoolTaskExecutor newExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(4);
        executor.setThreadNamePrefix("export-service-test-");
        executor.initialize();
        executors.add(executor);
        return executor;
    }

    private static final class CapturingWriterAdapter implements WriterAdapter {
        private final List<List<Object>> rows = new ArrayList<>();
        private List<List<String>> head;
        private List<List<String>> topInfoRows;

        @Override
        public void writeMultiSheet(OutputStream out, List<SheetWriteModel> sheets) {
            for (SheetWriteModel model : sheets) {
                this.head = model.getHead();
                this.topInfoRows = model.getTopInfoRows();
                Iterator<List<Object>> iterator = model.getRows();
                try {
                    while (iterator.hasNext()) {
                        rows.add(iterator.next());
                    }
                } finally {
                    if (iterator instanceof AutoCloseable closeable) {
                        try {
                            closeable.close();
                        } catch (Exception ignored) {
                        }
                    }
                }
            }
        }
    }

    private static final class TrackingFilterAwareProvider implements FilterAwareDataProvider<Map<String, Object>> {
        private final List<Map<String, Object>> rows = new ArrayList<>();
        private final AtomicReference<Map<String, Object>> currentFilters = new AtomicReference<>();
        private final AtomicReference<Map<String, Object>> filtersSeenInFetch = new AtomicReference<>();
        private final AtomicReference<List<String>> leafFieldsSeen = new AtomicReference<>();
        private final AtomicInteger batchSizeSeen = new AtomicInteger();
        private final AtomicInteger clearCalls = new AtomicInteger();

        @Override
        public Iterator<Map<String, Object>> fetchIterator(int batchSize) {
            batchSizeSeen.set(batchSize);
            Map<String, Object> filters = currentFilters.get();
            filtersSeenInFetch.set(filters == null ? null : new LinkedHashMap<>(filters));
            Object leafFields = filters == null ? null : filters.get("__leafFields");
            if (leafFields instanceof List<?> list) {
                List<String> casted = new ArrayList<>();
                for (Object item : list) {
                    casted.add(String.valueOf(item));
                }
                leafFieldsSeen.set(casted);
            }
            return rows.iterator();
        }

        @Override
        public long estimateTotal() {
            return rows.size();
        }

        @Override
        public void setFilters(Map<String, Object> filters) {
            currentFilters.set(filters == null ? null : new LinkedHashMap<>(filters));
        }

        @Override
        public void clearFilters() {
            clearCalls.incrementAndGet();
            currentFilters.set(null);
        }
    }
}
