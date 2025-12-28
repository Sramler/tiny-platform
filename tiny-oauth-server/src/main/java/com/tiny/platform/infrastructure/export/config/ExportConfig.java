package com.tiny.platform.infrastructure.export.config;

import com.tiny.platform.infrastructure.export.writer.WriterAdapter;
import com.tiny.platform.infrastructure.export.writer.poi.POIWriterAdapter;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

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
    public WriterAdapter writerAdapter() {
        // 默认使用 POI SXSSF 流式写，避免一次性占用大量内存
        // rowAccessWindowSize 可按行宽/内存调优，200 对多数场景足够
        return new POIWriterAdapter(200);
    }

    @Bean
    public ThreadPoolTaskExecutor exportExecutor(
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
        // 拒绝策略：队列满时由提交线程执行，形成背压而非直接抛异常
        t.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        // 优雅关闭：等待队列与执行中的任务完成，避免中断写文件
        t.setWaitForTasksToCompleteOnShutdown(true);
        t.setAwaitTerminationSeconds(60);
        t.initialize();
        return t;
    }
}