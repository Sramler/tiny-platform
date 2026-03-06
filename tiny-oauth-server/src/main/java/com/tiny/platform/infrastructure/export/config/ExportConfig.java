package com.tiny.platform.infrastructure.export.config;

import com.tiny.platform.infrastructure.export.writer.WriterAdapter;
import com.tiny.platform.infrastructure.export.writer.fesod.FesodWriterAdapter;
import com.tiny.platform.infrastructure.export.writer.poi.POIWriterAdapter;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;
import java.util.Locale;

/**
 * ExportConfig —— Spring Bean 配置
 *
 * 在此注册：
 *  - writerAdapter（POI 或 Fesod 任选其一）
 *  - providers 映射（exportType -> DataProvider）
 *  - aggregateMap 映射（aggregateKey -> AggregateStrategy）
 *  - topInfoDecorator
 *  - exportExecutor
 *
 * 注意：生产环境请把 providers 与 aggregateMap 注入为具体业务实现
 */
@Configuration
public class ExportConfig {

    @Bean
    public WriterAdapter writerAdapter(
        @Value("${export.writer.type:fesod}") String writerType,
        @Value("${export.poi.row-access-window-size:200}") int rowAccessWindowSize,
        @Value("${export.poi.max-rows-per-sheet:1048576}") int maxRowsPerSheet,
        @Value("${export.poi.compress-temp-files:true}") boolean compressTempFiles,
        @Value("${export.fesod.batch-size:1024}") int fesodBatchSize,
        @Value("${export.fesod.max-rows-per-sheet:1048576}") int fesodMaxRowsPerSheet
    ) {
        String normalized = writerType == null ? "fesod" : writerType.trim().toLowerCase(Locale.ROOT);
        if ("fesod".equals(normalized)) {
            return new FesodWriterAdapter(fesodBatchSize, fesodMaxRowsPerSheet);
        }
        if (!"poi".equals(normalized)) {
            throw new IllegalArgumentException("unsupported export.writer.type: " + writerType);
        }
        // 默认使用 POI SXSSF 流式写，避免一次性占用大量内存
        // rowAccessWindowSize 可按行宽/内存调优，200 对多数场景足够
        return new POIWriterAdapter(rowAccessWindowSize, maxRowsPerSheet, compressTempFiles);
    }

    @Bean
    public ThreadPoolTaskExecutor exportExecutor(
        @Qualifier("mdcTaskDecorator") TaskDecorator mdcTaskDecorator,
        @Value("${export.executor.core-pool-size:8}") int corePoolSize,
        @Value("${export.executor.max-pool-size:16}") int maxPoolSize,
        @Value("${export.executor.queue-capacity:1000}") int queueCapacity,
        @Value("${export.executor.keep-alive-seconds:60}") int keepAliveSeconds
    ) {
        ThreadPoolTaskExecutor t = new ThreadPoolTaskExecutor();
        // 线程数：导出多为 I/O + 序列化任务，适度放大并发，避免超出数据源连接池上限
        t.setCorePoolSize(corePoolSize);
        t.setMaxPoolSize(maxPoolSize);
        // 队列：更偏向“排队不丢”，如需更快拒绝/反馈可调低到 200~300，并在业务侧提示“稍后再试”
        t.setQueueCapacity(queueCapacity);
        // 资源回收：允许核心线程超时释放，减少空闲占用
        t.setKeepAliveSeconds(keepAliveSeconds);
        t.setAllowCoreThreadTimeOut(true);
        t.setThreadNamePrefix("export-exec-");
        t.setTaskDecorator(mdcTaskDecorator);
        // 拒绝策略：队列满时立即拒绝，避免回退到请求线程执行导致接口线程被长任务占用
        t.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        // 优雅关闭：等待队列与执行中的任务完成，避免中断写文件
        t.setWaitForTasksToCompleteOnShutdown(true);
        t.setAwaitTerminationSeconds(60);
        t.initialize();
        return t;
    }
}
