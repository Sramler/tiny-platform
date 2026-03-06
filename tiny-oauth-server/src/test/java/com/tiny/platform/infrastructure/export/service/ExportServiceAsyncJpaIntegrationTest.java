package com.tiny.platform.infrastructure.export.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tiny.platform.infrastructure.export.core.AggregateStrategy;
import com.tiny.platform.infrastructure.export.core.ColumnNode;
import com.tiny.platform.infrastructure.export.core.DataProvider;
import com.tiny.platform.infrastructure.export.core.DefaultTopInfoDecorator;
import com.tiny.platform.infrastructure.export.core.ExportRequest;
import com.tiny.platform.infrastructure.export.core.FilterAwareDataProvider;
import com.tiny.platform.infrastructure.export.core.SheetConfig;
import com.tiny.platform.infrastructure.export.core.TopInfoDecorator;
import com.tiny.platform.infrastructure.export.persistence.ExportTaskEntity;
import com.tiny.platform.infrastructure.export.persistence.ExportTaskRepository;
import com.tiny.platform.infrastructure.export.writer.WriterAdapter;
import com.tiny.platform.infrastructure.export.writer.poi.POIWriterAdapter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.transaction.TransactionAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.context.ActiveProfiles;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(
    classes = ExportServiceAsyncJpaIntegrationTest.TestApplication.class,
    properties = {
        "spring.datasource.type=org.springframework.jdbc.datasource.DriverManagerDataSource",
        "spring.datasource.url=jdbc:h2:mem:exportdb;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.liquibase.enabled=false"
    }
)
@ActiveProfiles("integration")
class ExportServiceAsyncJpaIntegrationTest {

    @Autowired
    private ExportService exportService;

    @Autowired
    private ExportTaskService exportTaskService;

    @Autowired
    private ThreadPoolTaskExecutor exportExecutor;

    @AfterEach
    void tearDown() {
        exportExecutor.shutdown();
    }

    @Test
    void submitAsyncShouldPersistTaskAndGenerateWorkbook() throws Exception {
        ExportRequest request = new ExportRequest();
        request.setFileName("integration_export");
        request.setPageSize(100);

        SheetConfig sheet = new SheetConfig();
        sheet.setSheetName("demo_export_usage");
        sheet.setExportType("itExport");
        sheet.setFilters(new LinkedHashMap<>(Map.of("tenantId", 1)));
        sheet.setColumns(List.of(
            new ColumnNode("ID", "id", null),
            new ColumnNode("Name", "name", null)
        ));
        request.setSheets(List.of(sheet));

        String taskId = exportService.submitAsync(request, "integration-user");
        ExportTaskEntity task = waitForTask(taskId);

        assertEquals(ExportTaskStatus.SUCCESS, task.getStatus());
        assertEquals(3L, task.getTotalRows());
        assertEquals(3L, task.getProcessedRows());
        assertEquals(100, task.getProgress());
        assertEquals("/export/task/" + taskId + "/download", task.getDownloadUrl());
        assertNotNull(task.getFilePath());
        assertTrue(Files.exists(Path.of(task.getFilePath())));

        byte[] bytes = Files.readAllBytes(Path.of(task.getFilePath()));
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            assertEquals(1, workbook.getNumberOfSheets());
            assertEquals("demo_export_usage", workbook.getSheetName(0));
            assertEquals("ID", workbook.getSheetAt(0).getRow(0).getCell(0).getStringCellValue());
            assertEquals("Name", workbook.getSheetAt(0).getRow(0).getCell(1).getStringCellValue());
            assertEquals(1D, workbook.getSheetAt(0).getRow(1).getCell(0).getNumericCellValue());
            assertEquals("alpha", workbook.getSheetAt(0).getRow(1).getCell(1).getStringCellValue());
        }

        Files.deleteIfExists(Path.of(task.getFilePath()));
    }

    private ExportTaskEntity waitForTask(String taskId) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while (System.nanoTime() < deadline) {
            Optional<ExportTaskEntity> taskOpt = exportTaskService.findByTaskId(taskId);
            if (taskOpt.isPresent()) {
                ExportTaskEntity task = taskOpt.get();
                if (task.getStatus() == ExportTaskStatus.SUCCESS || task.getStatus() == ExportTaskStatus.FAILED) {
                    return task;
                }
            }
            Thread.sleep(100);
        }
        throw new AssertionError("export task did not finish in time: " + taskId);
    }

    @SpringBootConfiguration
    @EntityScan(basePackageClasses = com.tiny.platform.infrastructure.export.persistence.ExportTaskEntity.class)
    @EnableJpaRepositories(basePackageClasses = ExportTaskRepository.class)
    @ImportAutoConfiguration({
        DataSourceAutoConfiguration.class,
        DataSourceTransactionManagerAutoConfiguration.class,
        HibernateJpaAutoConfiguration.class,
        JpaRepositoriesAutoConfiguration.class,
        TransactionAutoConfiguration.class,
        JacksonAutoConfiguration.class
    })
    @Import({
        ExportTaskService.class,
        ExportService.class,
        TestBeans.class
    })
    static class TestApplication {
    }

    static class TestBeans {
        @Bean
        @Primary
        public WriterAdapter writerAdapter() {
            return new POIWriterAdapter(10, 1_048_576, true);
        }

        @Bean
        @Primary
        public TopInfoDecorator topInfoDecorator() {
            return new DefaultTopInfoDecorator();
        }

        @Bean
        @Primary
        public ObjectMapper objectMapper() {
            return new ObjectMapper();
        }

        @Bean
        @Primary
        public MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }

        @Bean(name = "exportExecutor")
        @Primary
        public ThreadPoolTaskExecutor exportExecutor() {
            ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
            executor.setCorePoolSize(1);
            executor.setMaxPoolSize(1);
            executor.setQueueCapacity(4);
            executor.setThreadNamePrefix("export-it-");
            executor.initialize();
            return executor;
        }

        @Bean(name = "itExport")
        public DataProvider<Map<String, Object>> integrationExportProvider() {
            return new StaticFilterAwareProvider();
        }

        @Bean
        @Primary
        public Map<String, AggregateStrategy> aggregateMap() {
            return Map.of();
        }
    }

    private static final class StaticFilterAwareProvider implements FilterAwareDataProvider<Map<String, Object>> {
        private Map<String, Object> filters;

        @Override
        public Iterator<Map<String, Object>> fetchIterator(int batchSize) {
            List<Map<String, Object>> rows = new ArrayList<>();
            long tenantId = filters != null && filters.get("tenantId") instanceof Number number
                ? number.longValue()
                : 0L;
            rows.add(Map.of("id", 1L, "name", "alpha", "tenantId", tenantId));
            rows.add(Map.of("id", 2L, "name", "beta", "tenantId", tenantId));
            rows.add(Map.of("id", 3L, "name", "gamma", "tenantId", tenantId));
            return rows.iterator();
        }

        @Override
        public long estimateTotal() {
            return 3L;
        }

        @Override
        public void setFilters(Map<String, Object> filters) {
            this.filters = filters == null ? null : new LinkedHashMap<>(filters);
        }

        @Override
        public void clearFilters() {
            this.filters = null;
        }
    }
}
