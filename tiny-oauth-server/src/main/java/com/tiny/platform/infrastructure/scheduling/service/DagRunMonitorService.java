package com.tiny.platform.infrastructure.scheduling.service;

import com.tiny.platform.infrastructure.scheduling.model.SchedulingDagRun;
import com.tiny.platform.infrastructure.scheduling.model.SchedulingTaskInstance;
import com.tiny.platform.infrastructure.scheduling.repository.SchedulingDagRunRepository;
import com.tiny.platform.infrastructure.scheduling.repository.SchedulingTaskInstanceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * DAG 运行监控服务
 * 负责监控 DAG 运行状态并更新最终状态
 */
@Service
public class DagRunMonitorService {

    private static final Logger logger = LoggerFactory.getLogger(DagRunMonitorService.class);

    private static final int DAG_RUN_PAGE_SIZE = 100;
    private static final int MAX_DAG_RUNS_PER_CYCLE = 300;

    private final SchedulingDagRunRepository dagRunRepository;
    private final SchedulingTaskInstanceRepository taskInstanceRepository;

    @Autowired
    public DagRunMonitorService(
            SchedulingDagRunRepository dagRunRepository,
            SchedulingTaskInstanceRepository taskInstanceRepository) {
        this.dagRunRepository = dagRunRepository;
        this.taskInstanceRepository = taskInstanceRepository;
    }

    /**
     * 定时检查并更新 DAG 运行状态
     * 每 10 秒执行一次
     */
    @Scheduled(fixedDelay = 10000)
    public void monitorDagRuns() {
        int processed = 0;
        try {
            while (processed < MAX_DAG_RUNS_PER_CYCLE) {
                Page<SchedulingDagRun> page = dagRunRepository.findByStatus("RUNNING", PageRequest.of(0, DAG_RUN_PAGE_SIZE));
                if (!page.hasContent()) {
                    break;
                }
                for (SchedulingDagRun run : page.getContent()) {
                    if (processed >= MAX_DAG_RUNS_PER_CYCLE) {
                        return;
                    }
                    updateDagRunStatus(run);
                    processed++;
                }
                if (page.getNumberOfElements() < DAG_RUN_PAGE_SIZE) {
                    break;
                }
            }
        } catch (Exception e) {
            logger.error("监控 DAG 运行状态失败", e);
        }
    }

    /**
     * 更新 DAG 运行状态
     */
    @Transactional
    public void updateDagRunStatus(SchedulingDagRun run) {
        // 获取该运行的所有任务实例
        List<SchedulingTaskInstance> instances = taskInstanceRepository.findByDagRunId(run.getId());

        if (instances.isEmpty()) {
            logger.warn("DAG Run {} 没有任务实例", run.getId());
            return;
        }

        // 统计任务状态
        long total = instances.size();
        long success = instances.stream().filter(i -> "SUCCESS".equals(i.getStatus())).count();
        long failed = instances.stream().filter(i -> "FAILED".equals(i.getStatus())).count();
        long running = instances.stream().filter(i -> "RUNNING".equals(i.getStatus()) || "RESERVED".equals(i.getStatus())).count();
        long pending = instances.stream().filter(i -> "PENDING".equals(i.getStatus())).count();

        // 判断 DAG 运行状态
        String newStatus;
        if (failed > 0) {
            // 有任务失败
            if (running == 0 && pending == 0) {
                // 所有任务都已完成（部分失败）
                newStatus = "PARTIAL_FAILED";
            } else {
                // 还有任务在运行或等待
                newStatus = "RUNNING";
            }
        } else if (success == total) {
            // 所有任务都成功
            newStatus = "SUCCESS";
        } else if (running > 0 || pending > 0) {
            // 还有任务在运行或等待
            newStatus = "RUNNING";
        } else {
            // 其他情况，保持运行中
            newStatus = "RUNNING";
        }

        // 如果状态发生变化，更新
        String previousStatus = run.getStatus();
        if (!newStatus.equals(previousStatus)) {
            run.setStatus(newStatus);
            if ("SUCCESS".equals(newStatus) || "FAILED".equals(newStatus) || "PARTIAL_FAILED".equals(newStatus)) {
                run.setEndTime(LocalDateTime.now());
            }
            dagRunRepository.save(run);

            logger.info("DAG Run {} 状态更新: {} -> {}, 成功: {}/{}, 失败: {}", 
                    run.getId(), previousStatus, newStatus, success, total, failed);
        }
    }
}

