package com.tiny.platform.infrastructure.scheduling.controller;

import com.tiny.platform.infrastructure.core.dto.PageResponse;
import com.tiny.platform.infrastructure.scheduling.dto.*;
import com.tiny.platform.infrastructure.scheduling.model.*;
import com.tiny.platform.infrastructure.scheduling.service.QuartzSchedulerService;
import com.tiny.platform.infrastructure.scheduling.service.SchedulingService;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 企业级 DAG 调度控制器
 */
@RestController
@RequestMapping("/scheduling")
public class SchedulingController {

    private final SchedulingService schedulingService;
    private final QuartzSchedulerService quartzSchedulerService;

    public SchedulingController(SchedulingService schedulingService, QuartzSchedulerService quartzSchedulerService) {
        this.schedulingService = schedulingService;
        this.quartzSchedulerService = quartzSchedulerService;
    }

    // ==================== TaskType - 任务类型 ====================

    /**
     * 创建任务类型
     */
    @PostMapping("/task-type")
    public ResponseEntity<SchedulingTaskType> createTaskType(@Valid @RequestBody SchedulingTaskTypeCreateUpdateDto dto) {
        return ResponseEntity.ok(schedulingService.createTaskType(dto));
    }

    /**
     * 更新任务类型
     */
    @PutMapping("/task-type/{id}")
    public ResponseEntity<SchedulingTaskType> updateTaskType(
            @PathVariable Long id,
            @Valid @RequestBody SchedulingTaskTypeCreateUpdateDto dto) {
        return ResponseEntity.ok(schedulingService.updateTaskType(id, dto));
    }

    /**
     * 删除任务类型
     */
    @DeleteMapping("/task-type/{id}")
    public ResponseEntity<Void> deleteTaskType(@PathVariable Long id) {
        schedulingService.deleteTaskType(id);
        return ResponseEntity.ok().build();
    }

    /**
     * 查看任务类型详情
     */
    @GetMapping("/task-type/{id}")
    public ResponseEntity<SchedulingTaskType> getTaskType(@PathVariable Long id) {
        return schedulingService.getTaskType(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * 分页查询任务类型列表
     */
    @GetMapping("/task-type/list")
    public ResponseEntity<PageResponse<SchedulingTaskType>> listTaskTypes(
            @RequestParam(required = false) Long tenantId,
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String name,
            @PageableDefault(size = 10, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(new PageResponse<>(schedulingService.listTaskTypes(tenantId, code, name, pageable)));
    }

    // ==================== Task - 任务实例定义 ====================

    /**
     * 创建任务实例
     */
    @PostMapping("/task")
    public ResponseEntity<SchedulingTask> createTask(@Valid @RequestBody SchedulingTaskCreateUpdateDto dto) {
        return ResponseEntity.ok(schedulingService.createTask(dto));
    }

    /**
     * 更新任务
     */
    @PutMapping("/task/{id}")
    public ResponseEntity<SchedulingTask> updateTask(
            @PathVariable Long id,
            @Valid @RequestBody SchedulingTaskCreateUpdateDto dto) {
        return ResponseEntity.ok(schedulingService.updateTask(id, dto));
    }

    /**
     * 删除任务
     */
    @DeleteMapping("/task/{id}")
    public ResponseEntity<Void> deleteTask(@PathVariable Long id) {
        schedulingService.deleteTask(id);
        return ResponseEntity.ok().build();
    }

    /**
     * 查看任务详情
     */
    @GetMapping("/task/{id}")
    public ResponseEntity<SchedulingTask> getTask(@PathVariable Long id) {
        return schedulingService.getTask(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * 分页查询任务列表
     */
    @GetMapping("/task/list")
    public ResponseEntity<PageResponse<SchedulingTask>> listTasks(
            @RequestParam(required = false) Long tenantId,
            @RequestParam(required = false) Long typeId,
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String name,
            @PageableDefault(size = 10, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(new PageResponse<>(schedulingService.listTasks(tenantId, typeId, code, name, pageable)));
    }

    // ==================== DAG（编排流程） ====================

    /**
     * 创建 DAG
     */
    @PostMapping("/dag")
    public ResponseEntity<SchedulingDag> createDag(@Valid @RequestBody SchedulingDagCreateUpdateDto dto) {
        return ResponseEntity.ok(schedulingService.createDag(dto));
    }

    /**
     * 更新 DAG
     */
    @PutMapping("/dag/{id}")
    public ResponseEntity<SchedulingDag> updateDag(
            @PathVariable Long id,
            @Valid @RequestBody SchedulingDagCreateUpdateDto dto) {
        return ResponseEntity.ok(schedulingService.updateDag(id, dto));
    }

    /**
     * 删除 DAG
     */
    @DeleteMapping("/dag/{id}")
    public ResponseEntity<Void> deleteDag(@PathVariable Long id) {
        schedulingService.deleteDag(id);
        return ResponseEntity.ok().build();
    }

    /**
     * 查看 DAG 详情
     */
    @GetMapping("/dag/{id}")
    public ResponseEntity<SchedulingDag> getDag(@PathVariable Long id) {
        return schedulingService.getDag(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * 分页查询 DAG 列表
     */
    @GetMapping("/dag/list")
    public ResponseEntity<PageResponse<SchedulingDag>> listDags(
            @RequestParam(required = false) Long tenantId,
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String name,
            @PageableDefault(size = 10, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(new PageResponse<>(schedulingService.listDags(tenantId, code, name, pageable)));
    }

    // ==================== DAG Version ====================

    /**
     * 创建 DAG 新版本
     */
    @PostMapping("/dag/{dagId}/version")
    public ResponseEntity<SchedulingDagVersion> createDagVersion(
            @PathVariable Long dagId,
            @Valid @RequestBody SchedulingDagVersionCreateUpdateDto dto) {
        dto.setDagId(dagId);
        return ResponseEntity.ok(schedulingService.createDagVersion(dagId, dto));
    }

    /**
     * 更新 DAG 版本
     */
    @PutMapping("/dag/{dagId}/version/{versionId}")
    public ResponseEntity<SchedulingDagVersion> updateDagVersion(
            @PathVariable Long dagId,
            @PathVariable Long versionId,
            @Valid @RequestBody SchedulingDagVersionCreateUpdateDto dto) {
        return ResponseEntity.ok(schedulingService.updateDagVersion(dagId, versionId, dto));
    }

    /**
     * 查看 DAG 版本详情
     */
    @GetMapping("/dag/{dagId}/version/{versionId}")
    public ResponseEntity<SchedulingDagVersion> getDagVersion(
            @PathVariable Long dagId,
            @PathVariable Long versionId) {
        return schedulingService.getDagVersion(dagId, versionId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * 查询 DAG 所有版本
     */
    @GetMapping("/dag/{dagId}/version/list")
    public ResponseEntity<List<SchedulingDagVersion>> listDagVersions(@PathVariable Long dagId) {
        return ResponseEntity.ok(schedulingService.listDagVersions(dagId));
    }

    // ==================== DAG Node（节点） ====================

    /**
     * 添加 DAG 节点
     */
    @PostMapping("/dag/{dagId}/version/{versionId}/node")
    public ResponseEntity<SchedulingDagTask> createDagNode(
            @PathVariable Long dagId,
            @PathVariable Long versionId,
            @Valid @RequestBody SchedulingDagTaskCreateUpdateDto dto) {
        dto.setDagVersionId(versionId);
        return ResponseEntity.ok(schedulingService.createDagNode(dagId, versionId, dto));
    }

    /**
     * 更新节点
     */
    @PutMapping("/dag/{dagId}/version/{versionId}/node/{nodeId}")
    public ResponseEntity<SchedulingDagTask> updateDagNode(
            @PathVariable Long dagId,
            @PathVariable Long versionId,
            @PathVariable Long nodeId,
            @Valid @RequestBody SchedulingDagTaskCreateUpdateDto dto) {
        return ResponseEntity.ok(schedulingService.updateDagNode(dagId, versionId, nodeId, dto));
    }

    /**
     * 删除节点
     */
    @DeleteMapping("/dag/{dagId}/version/{versionId}/node/{nodeId}")
    public ResponseEntity<Void> deleteDagNode(
            @PathVariable Long dagId,
            @PathVariable Long versionId,
            @PathVariable Long nodeId) {
        schedulingService.deleteDagNode(dagId, versionId, nodeId);
        return ResponseEntity.ok().build();
    }

    /**
     * 查看节点详情
     */
    @GetMapping("/dag/{dagId}/version/{versionId}/node/{nodeId}")
    public ResponseEntity<SchedulingDagTask> getDagNode(
            @PathVariable Long dagId,
            @PathVariable Long versionId,
            @PathVariable Long nodeId) {
        return schedulingService.getDagNode(dagId, versionId, nodeId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * 查询版本下的所有节点
     */
    @GetMapping("/dag/{dagId}/version/{versionId}/nodes")
    public ResponseEntity<List<SchedulingDagTask>> getDagNodes(
            @PathVariable Long dagId,
            @PathVariable Long versionId) {
        return ResponseEntity.ok(schedulingService.getDagNodes(dagId, versionId));
    }

    /**
     * 查询上游节点
     */
    @GetMapping("/dag/{dagId}/version/{versionId}/node/{nodeId}/up")
    public ResponseEntity<List<SchedulingDagTask>> getUpstreamNodes(
            @PathVariable Long dagId,
            @PathVariable Long versionId,
            @PathVariable Long nodeId) {
        return ResponseEntity.ok(schedulingService.getUpstreamNodes(dagId, versionId, nodeId));
    }

    /**
     * 查询下游节点
     */
    @GetMapping("/dag/{dagId}/version/{versionId}/node/{nodeId}/down")
    public ResponseEntity<List<SchedulingDagTask>> getDownstreamNodes(
            @PathVariable Long dagId,
            @PathVariable Long versionId,
            @PathVariable Long nodeId) {
        return ResponseEntity.ok(schedulingService.getDownstreamNodes(dagId, versionId, nodeId));
    }

    // ==================== DAG Edge（节点依赖） ====================

    /**
     * 新增节点依赖
     */
    @PostMapping("/dag/{dagId}/version/{versionId}/edge")
    public ResponseEntity<SchedulingDagEdge> createDagEdge(
            @PathVariable Long dagId,
            @PathVariable Long versionId,
            @Valid @RequestBody SchedulingDagEdgeCreateDto dto) {
        dto.setDagVersionId(versionId);
        return ResponseEntity.ok(schedulingService.createDagEdge(dagId, versionId, dto));
    }

    /**
     * 删除节点依赖
     */
    @DeleteMapping("/dag/{dagId}/version/{versionId}/edge/{edgeId}")
    public ResponseEntity<Void> deleteDagEdge(
            @PathVariable Long dagId,
            @PathVariable Long versionId,
            @PathVariable Long edgeId) {
        schedulingService.deleteDagEdge(dagId, versionId, edgeId);
        return ResponseEntity.ok().build();
    }

    /**
     * 查询版本下的所有依赖
     */
    @GetMapping("/dag/{dagId}/version/{versionId}/edges")
    public ResponseEntity<List<SchedulingDagEdge>> getDagEdges(
            @PathVariable Long dagId,
            @PathVariable Long versionId) {
        return ResponseEntity.ok(schedulingService.getDagEdges(dagId, versionId));
    }

    // ==================== DAG 调度触发/控制 ====================

    /**
     * 触发整个 DAG 执行
     */
    @PostMapping("/dag/{dagId}/trigger")
    public ResponseEntity<SchedulingDagRun> triggerDag(
            @PathVariable Long dagId,
            @RequestParam(required = false, defaultValue = "system") String triggeredBy) {
        return ResponseEntity.ok(schedulingService.triggerDag(dagId, triggeredBy));
    }

    /**
     * 暂停 DAG 执行
     */
    @PostMapping("/dag/{dagId}/pause")
    public ResponseEntity<Void> pauseDag(@PathVariable Long dagId) {
        schedulingService.pauseDag(dagId);
        return ResponseEntity.ok().build();
    }

    /**
     * 恢复 DAG 执行
     */
    @PostMapping("/dag/{dagId}/resume")
    public ResponseEntity<Void> resumeDag(@PathVariable Long dagId) {
        schedulingService.resumeDag(dagId);
        return ResponseEntity.ok().build();
    }

    /**
     * 强制停止 DAG 执行
     */
    @PostMapping("/dag/{dagId}/stop")
    public ResponseEntity<Void> stopDag(@PathVariable Long dagId) {
        schedulingService.stopDag(dagId);
        return ResponseEntity.ok().build();
    }

    /**
     * 对失败的 DAG 进行整体重试
     */
    @PostMapping("/dag/{dagId}/retry")
    public ResponseEntity<Void> retryDag(@PathVariable Long dagId) {
        schedulingService.retryDag(dagId);
        return ResponseEntity.ok().build();
    }

    // ==================== DAG 节点调度 ====================

    /**
     * 单独触发节点执行
     */
    @PostMapping("/dag/{dagId}/node/{nodeId}/trigger")
    public ResponseEntity<Void> triggerNode(
            @PathVariable Long dagId,
            @PathVariable Long nodeId) {
        schedulingService.triggerNode(dagId, nodeId);
        return ResponseEntity.ok().build();
    }

    /**
     * 对失败节点重试
     */
    @PostMapping("/dag/{dagId}/node/{nodeId}/retry")
    public ResponseEntity<Void> retryNode(
            @PathVariable Long dagId,
            @PathVariable Long nodeId) {
        schedulingService.retryNode(dagId, nodeId);
        return ResponseEntity.ok().build();
    }

    /**
     * 暂停节点
     */
    @PostMapping("/dag/{dagId}/node/{nodeId}/pause")
    public ResponseEntity<Void> pauseNode(
            @PathVariable Long dagId,
            @PathVariable Long nodeId) {
        schedulingService.pauseNode(dagId, nodeId);
        return ResponseEntity.ok().build();
    }

    /**
     * 恢复节点
     */
    @PostMapping("/dag/{dagId}/node/{nodeId}/resume")
    public ResponseEntity<Void> resumeNode(
            @PathVariable Long dagId,
            @PathVariable Long nodeId) {
        schedulingService.resumeNode(dagId, nodeId);
        return ResponseEntity.ok().build();
    }

    // ==================== 运行历史 ====================

    /**
     * 查询 DAG 所有运行历史
     */
    @GetMapping("/dag/{dagId}/runs")
    public ResponseEntity<PageResponse<SchedulingDagRun>> getDagRuns(
            @PathVariable Long dagId,
            @PageableDefault(size = 10, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(new PageResponse<>(schedulingService.getDagRuns(dagId, pageable)));
    }

    /**
     * 查看 DAG 单次运行详情
     */
    @GetMapping("/dag/{dagId}/run/{runId}")
    public ResponseEntity<SchedulingDagRun> getDagRun(
            @PathVariable Long dagId,
            @PathVariable Long runId) {
        return schedulingService.getDagRun(dagId, runId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * 查看该次运行的所有节点执行记录
     */
    @GetMapping("/dag/{dagId}/run/{runId}/nodes")
    public ResponseEntity<List<SchedulingTaskInstance>> getDagRunNodes(
            @PathVariable Long dagId,
            @PathVariable Long runId) {
        return ResponseEntity.ok(schedulingService.getDagRunNodes(dagId, runId));
    }

    /**
     * 查看单节点执行详情
     */
    @GetMapping("/dag/{dagId}/run/{runId}/node/{nodeId}")
    public ResponseEntity<SchedulingTaskInstance> getDagRunNode(
            @PathVariable Long dagId,
            @PathVariable Long runId,
            @PathVariable Long nodeId) {
        return schedulingService.getDagRunNode(dagId, runId, nodeId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * 查看任务实例执行日志
     */
    @GetMapping("/task-instance/{instanceId}/log")
    public ResponseEntity<String> getTaskInstanceLog(@PathVariable Long instanceId) {
        return ResponseEntity.ok(schedulingService.getTaskInstanceLog(instanceId));
    }

    /**
     * 查看任务执行历史
     */
    @GetMapping("/task-history/{historyId}")
    public ResponseEntity<SchedulingTaskHistory> getTaskHistory(@PathVariable Long historyId) {
        return schedulingService.getTaskHistory(historyId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // ==================== 审计与监控 ====================

    /**
     * 分页查询操作审计记录
     */
    @GetMapping("/audit/list")
    public ResponseEntity<PageResponse<SchedulingAudit>> listAudits(
            @RequestParam(required = false) Long tenantId,
            @RequestParam(required = false) String objectType,
            @RequestParam(required = false) String action,
            @PageableDefault(size = 10, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(new PageResponse<>(schedulingService.listAudits(tenantId, objectType, action, pageable)));
    }

    /**
     * 查询任务默认参数
     */
    @GetMapping("/task-param/{taskId}")
    public ResponseEntity<String> getTaskParam(@PathVariable Long taskId) {
        return schedulingService.getTask(taskId)
                .map(task -> ResponseEntity.ok(task.getParams() != null ? task.getParams() : "{}"))
                .orElse(ResponseEntity.notFound().build());
    }

    // ==================== Quartz 集群状态 ====================

    /**
     * 获取 Quartz 调度器集群状态
     * 用于确认当前 Quartz 是以集群模式还是单机模式运行
     */
    @GetMapping("/quartz/cluster-status")
    public ResponseEntity<Map<String, Object>> getQuartzClusterStatus() {
        QuartzSchedulerService.ClusterStatusInfo status = quartzSchedulerService.getClusterStatus();
        
        Map<String, Object> result = new HashMap<>();
        result.put("schedulerName", status.getSchedulerName());
        result.put("schedulerInstanceId", status.getSchedulerInstanceId());
        result.put("isClustered", status.isClustered());
        result.put("isStarted", status.isStarted());
        result.put("isInStandbyMode", status.isInStandbyMode());
        result.put("numberOfJobsExecuted", status.getNumberOfJobsExecuted());
        result.put("schedulerStarted", status.getSchedulerStarted());
        result.put("clusterMode", status.isClustered() ? "集群模式" : "单机模式");
        result.put("status", status.isStarted() ? (status.isInStandbyMode() ? "待机" : "运行中") : "已停止");
        
        return ResponseEntity.ok(result);
    }
}

