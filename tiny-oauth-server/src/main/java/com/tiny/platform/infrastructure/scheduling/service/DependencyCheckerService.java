package com.tiny.platform.infrastructure.scheduling.service;

import com.tiny.platform.infrastructure.scheduling.model.SchedulingTaskInstance;
import com.tiny.platform.infrastructure.scheduling.repository.SchedulingTaskInstanceRepository;
import com.tiny.platform.infrastructure.scheduling.repository.SchedulingDagEdgeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 依赖检查服务
 * 负责检查任务实例的依赖关系是否满足，以及是否因上游失败/取消而不可达（用于 DAG 运行状态收敛）。
 */
@Service
public class DependencyCheckerService {

    private static final Logger logger = LoggerFactory.getLogger(DependencyCheckerService.class);

    /** 上游处于这些状态时，下游 PENDING 视为不可达，应标为 SKIPPED */
    private static final Set<String> UPSTREAM_TERMINAL_FAIL_OR_SKIP = Set.of("FAILED", "CANCELLED", "SKIPPED");

    private final SchedulingTaskInstanceRepository taskInstanceRepository;
    private final SchedulingDagEdgeRepository dagEdgeRepository;

    @Autowired
    public DependencyCheckerService(
            SchedulingTaskInstanceRepository taskInstanceRepository,
            SchedulingDagEdgeRepository dagEdgeRepository) {
        this.taskInstanceRepository = taskInstanceRepository;
        this.dagEdgeRepository = dagEdgeRepository;
    }

    /**
     * 检查任务实例的依赖是否全部满足
     */
    public boolean checkDependencies(SchedulingTaskInstance instance) {
        if (instance.getDagVersionId() == null || instance.getNodeCode() == null) {
            logger.warn("任务实例缺少必要信息，无法检查依赖, instanceId: {}", instance.getId());
            return false;
        }

        // 1. 查找所有上游节点（依赖的节点）
        List<String> upstreamNodeCodes = dagEdgeRepository
                .findByDagVersionIdAndToNodeCode(instance.getDagVersionId(), instance.getNodeCode())
                .stream()
                .map(edge -> edge.getFromNodeCode())
                .collect(Collectors.toList());

        if (upstreamNodeCodes.isEmpty()) {
            return true;
        }

        // 一次 SQL：统计上游节点中有 SUCCESS 实例的不同节点数，等于上游数即依赖全部满足
        long successCount = taskInstanceRepository.countDistinctNodeCodesByDagRunIdAndNodeCodeInAndStatus(
                instance.getDagRunId(), upstreamNodeCodes, "SUCCESS");
        boolean allCompleted = successCount == upstreamNodeCodes.size();
        if (!allCompleted) {
            logger.debug("任务实例 {} 的上游任务未全部完成, 需要: {}, 已成功: {}",
                    instance.getId(), upstreamNodeCodes.size(), successCount);
        }
        return allCompleted;
    }

    /**
     * 检查是否是下游任务
     */
    public boolean isDownstreamTask(String fromNodeCode, String toNodeCode, Long dagVersionId) {
        return dagEdgeRepository
                .findByDagVersionIdAndFromNodeCode(dagVersionId, fromNodeCode)
                .stream()
                .anyMatch(edge -> edge.getToNodeCode().equals(toNodeCode));
    }

    /**
     * 判断当前实例是否因上游已失败/取消/跳过而不可达。
     * 用于 DAG 运行状态收敛：将此类 PENDING 实例标为 SKIPPED。
     * 按「每节点最新实例」判断：同一 (dagRunId, nodeCode) 可能有多条（如重试新建），只认 id 最大的那条；
     * 仅当该最新实例为 FAILED/CANCELLED/SKIPPED 时才视为该上游终态失败，避免与节点重试冲突。
     *
     * @param instance        当前任务实例（通常为 PENDING）
     * @param instancesInRun  同一次 DAG Run 下的全部任务实例（需包含上游实例）
     * @return 若任一上游节点的最新实例状态为 FAILED/CANCELLED/SKIPPED 则 true，否则 false；无上游依赖返回 false
     */
    public boolean hasAnyUpstreamInTerminalFailOrSkipped(
            SchedulingTaskInstance instance,
            List<SchedulingTaskInstance> instancesInRun) {
        if (instance.getDagVersionId() == null || instance.getNodeCode() == null) {
            return false;
        }
        List<String> upstreamNodeCodes = dagEdgeRepository
                .findByDagVersionIdAndToNodeCode(instance.getDagVersionId(), instance.getNodeCode())
                .stream()
                .map(edge -> edge.getFromNodeCode())
                .collect(Collectors.toList());
        if (upstreamNodeCodes.isEmpty()) {
            return false;
        }
        Set<String> upstreamNodeSet = Set.copyOf(upstreamNodeCodes);
        // 按 nodeCode 分组，每组只保留 id 最大的实例（同一节点重试会新建实例，最新一条代表该节点当前状态）
        Map<String, SchedulingTaskInstance> latestByNode = instancesInRun.stream()
                .filter(up -> up.getNodeCode() != null && upstreamNodeSet.contains(up.getNodeCode()))
                .collect(Collectors.toMap(SchedulingTaskInstance::getNodeCode, u -> u, (a, b) -> {
                    if (a.getId() == null) return b;
                    if (b.getId() == null) return a;
                    return a.getId() < b.getId() ? b : a;
                }));
        return latestByNode.values().stream()
                .anyMatch(up -> up.getStatus() != null && UPSTREAM_TERMINAL_FAIL_OR_SKIP.contains(up.getStatus()));
    }
}

