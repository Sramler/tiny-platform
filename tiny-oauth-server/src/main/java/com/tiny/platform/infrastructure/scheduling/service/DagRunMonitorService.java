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
import java.util.Set;

/**
 * DAG 运行监控服务
 * 负责监控 DAG 运行状态并更新最终状态，包括：不可达 PENDING→SKIPPED 收敛、Run 终态判定。
 */
@Service
public class DagRunMonitorService {

    private static final Logger logger = LoggerFactory.getLogger(DagRunMonitorService.class);

    private static final int DAG_RUN_PAGE_SIZE = 100;
    private static final int MAX_DAG_RUNS_PER_CYCLE = 300;

    /** Run 已终态时不再更新，避免覆盖 CANCELLED 等 */
    private static final Set<String> RUN_TERMINAL_STATUSES = Set.of("SUCCESS", "FAILED", "PARTIAL_FAILED", "CANCELLED");

    private final SchedulingDagRunRepository dagRunRepository;
    private final SchedulingTaskInstanceRepository taskInstanceRepository;
    private final DependencyCheckerService dependencyChecker;

    @Autowired
    public DagRunMonitorService(
            SchedulingDagRunRepository dagRunRepository,
            SchedulingTaskInstanceRepository taskInstanceRepository,
            DependencyCheckerService dependencyChecker) {
        this.dagRunRepository = dagRunRepository;
        this.taskInstanceRepository = taskInstanceRepository;
        this.dependencyChecker = dependencyChecker;
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
     * 包含：不可达 PENDING→SKIPPED 收敛、按实例状态统计、Run 终态判定（不覆盖 CANCELLED）。
     */
    @Transactional
    public void updateDagRunStatus(SchedulingDagRun run) {
        if (run.getStatus() != null && RUN_TERMINAL_STATUSES.contains(run.getStatus())) {
            return;
        }

        List<SchedulingTaskInstance> instances = taskInstanceRepository.findByDagRunId(run.getId());
        if (instances.isEmpty()) {
            logger.warn("DAG Run {} 没有任务实例", run.getId());
            return;
        }

        // 仅当 Run 已无 RUNNING/RESERVED（无活跃执行）时才做 PENDING→SKIPPED 收敛，避免过早 SKIP 后用户重试上游时下游无法恢复
        long runningCount = instances.stream()
                .filter(i -> "RUNNING".equals(i.getStatus()) || "RESERVED".equals(i.getStatus()))
                .count();
        if (runningCount == 0) {
            boolean changed;
            do {
                changed = false;
                for (SchedulingTaskInstance inst : instances) {
                    if (!"PENDING".equals(inst.getStatus())) {
                        continue;
                    }
                    if (dependencyChecker.hasAnyUpstreamInTerminalFailOrSkipped(inst, instances)) {
                        inst.setStatus("SKIPPED");
                        taskInstanceRepository.save(inst);
                        changed = true;
                    }
                }
                if (changed) {
                    instances = taskInstanceRepository.findByDagRunId(run.getId());
                }
            } while (changed);
        }

        // 统计任务状态（含 SKIPPED、CANCELLED、PAUSED）
        long total = instances.size();
        long success = countStatus(instances, "SUCCESS");
        long failed = countStatus(instances, "FAILED");
        long running = instances.stream()
                .filter(i -> "RUNNING".equals(i.getStatus()) || "RESERVED".equals(i.getStatus()))
                .count();
        long pending = countStatus(instances, "PENDING");
        long skipped = countStatus(instances, "SKIPPED");
        long cancelled = countStatus(instances, "CANCELLED");
        long paused = countStatus(instances, "PAUSED");

        // 终态判定：无 RUNNING/PENDING 时再判终态
        String newStatus;
        if (running > 0 || pending > 0) {
            newStatus = "RUNNING";
        } else if (success == total) {
            newStatus = "SUCCESS";
        } else if (failed > 0) {
            newStatus = success > 0 ? "PARTIAL_FAILED" : "FAILED";
        } else if (cancelled == total) {
            newStatus = "CANCELLED";
        } else {
            // 其余为 SUCCESS/SKIPPED/PAUSED 等，无失败则视为成功
            newStatus = "SUCCESS";
        }

        String previousStatus = run.getStatus();
        if (!newStatus.equals(previousStatus)) {
            run.setStatus(newStatus);
            if (RUN_TERMINAL_STATUSES.contains(newStatus)) {
                run.setEndTime(LocalDateTime.now());
            }
            dagRunRepository.save(run);
            logger.info("DAG Run {} 状态更新: {} -> {}, 成功: {}, 失败: {}, 跳过: {}, 取消: {}, 暂停: {}",
                    run.getId(), previousStatus, newStatus, success, failed, skipped, cancelled, paused);
        }
    }

    private static long countStatus(List<SchedulingTaskInstance> instances, String status) {
        return instances.stream().filter(i -> status.equals(i.getStatus())).count();
    }
}

