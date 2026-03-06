package com.tiny.platform.infrastructure.export.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 定时清理过期导出任务与落盘文件。
 */
@Component
public class ExportTaskCleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(ExportTaskCleanupScheduler.class);

    private final ExportTaskService exportTaskService;

    public ExportTaskCleanupScheduler(ExportTaskService exportTaskService) {
        this.exportTaskService = exportTaskService;
    }

    @Scheduled(fixedDelayString = "${export.cleanup.fixed-delay-ms:600000}")
    public void cleanupExpiredTasks() {
        int removed = exportTaskService.cleanupExpired(LocalDateTime.now());
        if (removed > 0) {
            log.info("Cleaned expired export tasks count={}", removed);
        }
    }
}
