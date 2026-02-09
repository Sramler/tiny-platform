# 调度触发设计与实现说明

本文档说明 DAG 调度任务的触发链路，保证**手动触发**与 **Cron 定时触发**均可正常执行。

## 1. 触发方式概览

| 触发方式 | 入口 | 创建 Run 时机 | Quartz 使用 |
|----------|------|----------------|-------------|
| 手动触发 | `SchedulingService.triggerDag()` | **先**创建 `SchedulingDagRun`，再触发 Job | 一次性 Job，JobDataMap 带 `dagRunId`、`dagVersionId` |
| Cron 定时 | Quartz CronTrigger 触发 `DagExecutionJob` | **Job 内**`executeDag(dagId, null, null)` 时创建 Run | 持久化 Job，仅传 `dagId` |

## 2. 手动触发链路

1. **API**：`POST /api/scheduling/dags/{id}/trigger`
2. **SchedulingService.triggerDag(dagId, triggeredBy)**  
   - 取 DAG 当前激活版本 `SchedulingDagVersion`。  
   - **先**创建 `SchedulingDagRun`（status=RUNNING, triggerType=MANUAL）。  
   - 调用 `quartzSchedulerService.triggerDagNow(dag, run.getId(), version.getId())`。  
3. **QuartzSchedulerService.triggerDagNow(dag, dagRunId, dagVersionId)**  
   - 创建一次性 Quartz Job，JobDataMap 写入 `dagId`、`dagRunId`、`dagVersionId`。  
   - 立即触发执行。  
4. **DagExecutionJob.execute()**  
   - 从 JobDataMap 读取 `dagRunId`、`dagVersionId`（非空）。  
   - 调用 `schedulingService.executeDag(dagId, dagRunId, dagVersionId)`。  
5. **SchedulingService.executeDag(dagId, dagRunId, dagVersionId)**  
   - 因 `dagRunId != null`，走“已有 run”分支：使用已有 `SchedulingDagRun`，不再创建。  
   - 根据版本创建任务实例并进入执行流程。

**结论**：手动触发时 Run 在业务层先落库，再交给 Quartz 执行，保证 Run 与执行一一对应。

## 3. Cron 定时触发链路

1. **DAG 创建/更新**  
   - `createDag` / `updateDag` 中若 `SchedulingDagCreateUpdateDto.cronExpression` 非空，调用  
     `quartzSchedulerService.createOrUpdateDagJob(dag, cronExpression)`。  
   - 若更新时清空 cron，调用 `quartzSchedulerService.deleteDagJob(dagId)`。  
2. **Quartz** 按 Cron 表达式触发 `DagExecutionJob`，JobDataMap 仅包含 `dagId`（无 dagRunId/dagVersionId）。  
3. **DagExecutionJob.execute()**  
   - 读取到 `dagRunId`、`dagVersionId` 为空。  
   - 调用 `schedulingService.executeDag(dagId, null, null)`。  
4. **SchedulingService.executeDag(dagId, null, null)**  
   - 走“定时触发”分支：取当前 ACTIVE 版本，**在此时**创建 `SchedulingDagRun`（triggerType=SCHEDULE, runNo=RUN-时间戳）。  
   - 创建任务实例并执行。

**结论**：定时触发时 Run 在 `executeDag` 内创建，与 Quartz 的 Cron 触发一一对应。

## 4. 其他操作与 Quartz 的配合

- **删除 DAG**：`deleteDag()` 中调用 `quartzSchedulerService.deleteDagJob(id)`；失败仅打日志，不阻止 DB 删除。  
- **停止 DAG**：`stopDag()` 调用 `pauseDagJob(dagId)`，并将 RUNNING 的 Run 标为 CANCELLED。  
- **重试 DAG**：`retryDag()` 为失败 Run 创建新的 retry Run，并调用 `triggerDagNow(dag, retryRun.getId(), retryRun.getDagVersionId())`，与手动触发一致。  
- **节点级操作**：`triggerNode` / `retryNode` / `pauseNode` / `resumeNode` 已实现，不依赖 Quartz，仅操作任务实例与 Run 状态。

## 5. 任务执行相关

- **最大重试次数**：`TaskWorkerService.getMaxRetry(instance)` 按 节点 `SchedulingDagTask.maxRetry` → 任务 `SchedulingTask.maxRetry` → 任务类型 `SchedulingTaskType.defaultMaxRetry` 顺序取值，默认 0。  
- **参数解析**：`TaskExecutorService` 使用 `parseJsonToMap`（ObjectMapper）解析并合并任务/节点/实例参数，无“返回空 Map”的占位实现。  
- **失败处理**：`handleTaskFailure` 根据 `getMaxRetry(instance)` 与 `instance.getAttemptNo()` 决定是否重试或标记最终失败。

## 6. Cron 配置持久化与重启恢复

- **业务表持久化**：`scheduling_dag.cron_expression` 存储 Cron 表达式；创建/更新 DAG 时同时落库并同步到 Quartz。
- **Quartz 持久化**：JobStore 使用 JDBC（QRTZ_* 表），Job/Trigger 已持久化。
- **重启恢复**：应用启动时 `QuartzCronRecoveryRunner` 查询所有已启用且配置了 Cron 的 DAG，逐个调用 `createOrUpdateDagJob`，保证即使 Quartz 表被清空或首次部署，也能从业务表恢复定时任务。

## 7. 小结

- 手动触发：先建 Run → 再触发 Quartz Job（带 dagRunId/dagVersionId）→ Job 内执行已有 Run。  
- Cron 触发：Quartz 按 Cron 触发 Job（仅 dagId）→ `executeDag(dagId, null, null)` 内建 Run 并执行。  
- Cron 持久化：cron_expression 存 scheduling_dag 表；启动时从 DB 同步到 Quartz，支持重启恢复。  
- 删除/停止/重试、节点级操作、重试次数与参数解析均已按上述设计实现，调度任务可正常触发与执行。
