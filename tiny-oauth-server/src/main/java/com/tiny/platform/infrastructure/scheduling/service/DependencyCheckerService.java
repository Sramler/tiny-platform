package com.tiny.platform.infrastructure.scheduling.service;

import com.tiny.platform.infrastructure.scheduling.model.SchedulingTaskInstance;
import com.tiny.platform.infrastructure.scheduling.repository.SchedulingTaskInstanceRepository;
import com.tiny.platform.infrastructure.scheduling.repository.SchedulingDagEdgeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 依赖检查服务
 * 负责检查任务实例的依赖关系是否满足
 */
@Service
public class DependencyCheckerService {

    private static final Logger logger = LoggerFactory.getLogger(DependencyCheckerService.class);

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
            // 没有上游依赖，可以直接执行
            return true;
        }

        // 2. 检查所有上游任务是否已完成
        List<SchedulingTaskInstance> upstreamInstances = taskInstanceRepository
                .findByDagRunId(instance.getDagRunId())
                .stream()
                .filter(upstream -> upstreamNodeCodes.contains(upstream.getNodeCode()))
                .collect(Collectors.toList());

        if (upstreamInstances.size() < upstreamNodeCodes.size()) {
            logger.debug("任务实例 {} 的上游任务未全部创建, 需要: {}, 实际: {}", 
                    instance.getId(), upstreamNodeCodes.size(), upstreamInstances.size());
            return false;
        }

        // 3. 检查所有上游任务是否都成功完成
        Set<String> completedUpstreamNodes = upstreamInstances.stream()
                .filter(upstream -> "SUCCESS".equals(upstream.getStatus()))
                .map(SchedulingTaskInstance::getNodeCode)
                .collect(Collectors.toSet());

        boolean allCompleted = completedUpstreamNodes.size() == upstreamNodeCodes.size();
        
        if (!allCompleted) {
            Set<String> missingNodes = upstreamNodeCodes.stream()
                    .filter(nodeCode -> !completedUpstreamNodes.contains(nodeCode))
                    .collect(Collectors.toSet());
            logger.debug("任务实例 {} 的上游任务未全部完成, 未完成节点: {}", 
                    instance.getId(), missingNodes);
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
}

