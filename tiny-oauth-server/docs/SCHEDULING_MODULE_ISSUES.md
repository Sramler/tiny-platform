# 调度模块问题分析

## 测试数据与 node_a / node_b（必读）

**问题**：若仅执行建表（schema）而未执行调度示例数据，则 `scheduling_dag_task` 中无节点，`node_a`、`node_b` 不存在。触发 DAG 或 Worker 执行时会因找不到节点定义而失败或依赖检查异常。

**数据来源**：`node_a`、`node_b` 由以下脚本在 **scheduling_dag_task** 中定义，插入顺序必须保持：

- `src/main/resources/data.sql`（调度段：约 165–196 行）
- `src/main/resources/db/changelog/scheduling-demo-data.sql`

**依赖顺序**：`task_type` → `task` → `dag` → `dag_version` → **`dag_task`（此处定义 node_a、node_b）** → `dag_edge` → `dag_run` → `task_instance` → `task_history`。

**建议**：本地/测试环境需执行上述完整示例数据（或 Liquibase/Flyway 中对应的 scheduling-demo-data），以保证节点存在后再触发 DAG 或查看运行历史。

---

## Quartz instanceId 与「节点」区分（避免节点信息不对应）

**配置**：`org.quartz.scheduler.instanceId: AUTO` 表示 Quartz 使用「主机+时间」等生成调度器实例 ID，用于集群下区分不同 JVM 节点，与业务里的「DAG 节点」无关。

**概念区分**：

| 概念 | 含义 | 存储/来源 |
|------|------|-----------|
| **Quartz 调度器实例** | 哪个 JVM 在跑 Quartz（instanceId: AUTO 时每台机器不同） | Quartz 配置，集群时需共用 JDBC JobStore |
| **DAG 节点（节点 A / 节点 B）** | 工作流里的一个步骤，对应 `node_code`（如 node_a、node_b） | `scheduling_dag_task.node_code`、`scheduling_task_instance.node_code` |
| **任务实例 ID（instanceId）** | 某次 DAG 运行中某节点的执行记录主键 | `scheduling_task_instance.id` |

**依赖语义**：「节点 A 完成后才跑节点 B」= 在**同一 DAG 版本**下，存在边 `from_node_code=A → to_node_code=B`；执行时以 `scheduling_task_instance.node_code` 与 `scheduling_dag_edge` 为准，与 Quartz instanceId 无关。

**若出现「节点信息不对应」**：

1. **接口/前端**：返回任务实例时务必带 `nodeCode`（及可选 `dagVersionId`）；展示名称时应用 `node_code` 去查 `scheduling_dag_task.name`，避免用 instanceId 或其它字段反推节点。
2. **日志**：打日志时区分「任务实例 id」「nodeCode」，例如：`instanceId: {}, nodeCode: {}`，避免只打 instanceId 导致与 Quartz 或它处 instanceId 混淆。
3. **创建实例**：`createDagTaskInstances` 已按版本内节点与边创建实例并设置 `instance.setNodeCode(node.getNodeCode())`，同一 run 内每个实例的 nodeCode 与 dag_version 一致；若仍不对应，检查该 run 是否绑定了正确 version 以及 edge 的 from/to 是否与 dag_task 的 node_code 一致。

---

## 日志分析：TaskWorkerService「处理待处理任务失败」（已修复）

**日志位置**（见 `logback-spring.xml`）：
- **当前正在写入**：`logs/oauth-server.log`（全量）、`logs/oauth-server-error.log`（仅 WARN/ERROR，含堆栈）
- **历史归档**：`logs/oauth-server-error.%d.log.gz`、`logs/oauth-server.%d.log.gz`
- 路径为运行目录下的 `./logs`，或 `logging.file.path` / 环境变量 `LOG_PATH` 指定目录

**现象**：定时任务每 5 秒报错：

```text
[scheduling-1] ERROR ... TaskWorkerService - 处理待处理任务失败
org.springframework.dao.InvalidDataAccessApiUsageException: Executing an update/delete query
Caused by: jakarta.persistence.TransactionRequiredException: Executing an update/delete query
```

**调用链**：`processPendingTasks()` → `self.reserveTask(instance)` → `taskInstanceRepository.reserveTaskInstance(...)`（`@Modifying` UPDATE）。

**根因**：`reserveTaskInstance` 为 JPA 的 update 操作，必须在事务内执行。定时线程执行 `processPendingTasks()` 时无事务；虽通过 self 注入调用 `reserveTask`（带 `@Transactional`），在部分环境下代理未生效或事务未正确绑定，导致 UPDATE 在无事务上下文中执行，触发 `TransactionRequiredException`。

**修复**：对 `TaskWorkerService.reserveTask` 使用 `@Transactional(propagation = Propagation.REQUIRES_NEW)`，确保每次抢占在独立新事务中执行，定时线程调用时也能获得有效事务。

---

## 日志分析：MySQL JSON 列空字符串导致 500（Invalid JSON）— ✅ 已修复

**现象**：编辑任务类型、编辑任务等 PUT 请求返回 500，日志出现：

```text
SQL Error: 3140, SQLState: 22001
Data truncation: Invalid JSON text: "The document is empty." at position 0 in value for column '...'
could not execute batch [...]
```

**根因**：MySQL 的 JSON 列不接受空字符串 `""`（会报 "The document is empty"），只接受合法 JSON（如 `"{}"`、`"[]"`）或 NULL。

**已出现过的列与接口**（保存时若传空字符串会报错）：

| 表 | 列 | 接口/操作 |
|----|----|-----------|
| `scheduling_task_type` | `param_schema` | PUT `/scheduling/task-type/{id}`（编辑任务类型） |
| `scheduling_task` | `params` | PUT `/scheduling/task/{id}`（编辑任务） |
| `scheduling_task` | `retry_policy` | PUT `/scheduling/task/{id}`（编辑任务）。当前仅支持 `{"delaySec": <秒数>}`，失败后延迟该秒数再重试；缺省或无效时默认 60 秒。 |
| `scheduling_dag_version` | `definition` | DAG 版本创建/更新 |
| `scheduling_dag_task` | `override_params` | DAG 节点创建/更新 |
| `scheduling_dag_task` | `meta` | DAG 节点创建/更新 |

**修复**：在 `SchedulingService` 中统一使用 `normalizeJsonColumn(String)`：将 `null` 或空白字符串转为 `null`，写入前对所有上述 JSON 列调用，避免向 MySQL 写入 `""`。

---

## 当前状态（已修复/已实现）

以下项已在代码中实现，本文档保留原问题描述供参考。触发链路详见 [SCHEDULING_TRIGGER_DESIGN.md](./SCHEDULING_TRIGGER_DESIGN.md)。

| 问题 | 状态 | 说明 |
|------|------|------|
| 节点级操作（triggerNode/retryNode/pauseNode/resumeNode） | ✅ 已实现 | `SchedulingService` 中已实现，含 Run 获取/创建、实例状态更新与审计 |
| 任务重试次数来源 | ✅ 已实现 | `TaskWorkerService.getMaxRetry(instance)` 按 节点→任务→任务类型 取值 |
| JSON 参数解析 | ✅ 已实现 | `TaskExecutorService.parseJsonToMap` 使用 ObjectMapper，并与节点/实例参数合并 |
| 手动触发先建 Run 再触发 Job | ✅ 已实现 | `triggerDag` 先创建 `SchedulingDagRun`，再 `triggerDagNow(dag, run.getId(), version.getId())` |
| Cron 定时调度 | ✅ 已实现 | createDag/updateDag 中根据 `cronExpression` 调用 createOrUpdateDagJob/deleteDagJob |
| Cron 配置持久化与重启恢复 | ✅ 已实现 | scheduling_dag.cron_expression 持久化；QuartzCronRecoveryRunner 启动时从 DB 同步到 Quartz |
| Cron 表达式校验 | ✅ 已实现 | 后端使用 Quartz CronExpression 校验（与 Quartz 调度器及前端设计器 Quartz 风格一致，支持 L/? 等） |
| 删除 DAG 清理 Quartz Job | ✅ 已实现 | deleteDag 中调用 deleteDagJob；失败用 logger 记录，不阻止删除 |
| 重试 DAG 触发 Quartz | ✅ 已实现 | retryDag 创建 retry Run 后调用 triggerDagNow |

---

## DAG 运行状态收敛（待实现）

以下拆分为具体事项，每项对应当前实现缺口与建议修复点。**结论：拆分合理，与代码库现状一致，可按条实现。**

### 1. 为什么会“永远 RUNNING”

- **现状**：`DagRunMonitorService.updateDagRunStatus` 只统计 SUCCESS/FAILED/RUNNING/RESERVED/PENDING，未处理「上游失败后下游永远不会被调度」。
- **结果**：存在失败节点 + 下游 PENDING 且 `scheduled_at` 为 null 时，Run 会一直被判为 RUNNING。
- **涉及文件**：`DagRunMonitorService.java`。

### 2. 需要增加的“不可运行”判断

- **原则**：若某节点的上游已失败/取消，且该节点尚未开始，则不应继续等待。
- **建议行为**：下游节点应标记为 SKIPPED（或 CANCELLED），使 Run 可收敛。
- **涉及文件**：`DependencyCheckerService.java`、`DagRunMonitorService.java`。

### 3. 需要补全的终态判定规则

- **建议终态规则**：
  - 全部节点 SUCCESS → Run **SUCCESS**
  - 任一节点 FAILED 且其下游均已 SKIPPED/CANCELLED → Run **FAILED** 或 **PARTIAL_FAILED**
  - 存在 RUNNING/RESERVED 或「可运行 PENDING」→ Run **RUNNING**
  - 全部节点处于 SUCCESS/FAILED/SKIPPED/CANCELLED → Run 进入**终态**
- **涉及文件**：`DagRunMonitorService.java`。

### 4. “可运行 PENDING”与“不可运行 PENDING”的区分

- **现状**：所有 PENDING 等价，导致无法收敛。
- **建议**：
  - PENDING 且上游已失败/取消 → 标记为 **SKIPPED**
  - PENDING 且 `scheduled_at` 为空且上游失败 → 直接 **SKIPPED**
- **涉及文件**：`DependencyCheckerService.java`（提供“上游是否已失败/取消”的判定）。

### 5. 在 DAG Run 监控中补充“清理流程”

- **建议**：在 `updateDagRunStatus` 中新增一步：
  1. 找出该 Run 下所有 PENDING 实例；
  2. 对每个实例检查上游依赖是否「已失败或已取消」；
  3. 若不可达，则将实例改为 **SKIPPED**。
- **涉及文件**：`DagRunMonitorService.java`。

### 6. 处理暂停/停止导致的 Run 收敛

- **现状**：`stopDag` 仅将 Run 置为 CANCELLED，未批量更新 TaskInstance，Worker 仍可能执行 PENDING/RESERVED/RUNNING。
- **建议**：
  - `stopDag` 批量将对应 Run 下的 PENDING/RESERVED/RUNNING 实例标记为 **CANCELLED**；
  - `updateDagRunStatus` 将 CANCELLED（Run 与 Instance）视为终态，不再判为 RUNNING。
- **涉及文件**：`SchedulingService.java`（stopDag）、`DagRunMonitorService.java`。

### 7. 状态枚举对齐与文档

- **建议**：在代码与 schema 注释中明确约定，监控与统计逻辑全覆盖：
  - **TaskInstance**：PENDING / RESERVED / RUNNING / SUCCESS / FAILED / SKIPPED / PAUSED / CANCELLED
  - **DagRun**：SCHEDULED / RUNNING / SUCCESS / FAILED / PARTIAL_FAILED / CANCELLED
- **涉及文件**：`SchedulingTaskInstance.java`（注释）、`DagRunMonitorService.java`（统计与终态判断）、`schema.sql`（表注释；当前 task_instance 注释缺 PAUSED/CANCELLED）。

---

## 一、原未实现功能（已处理，见上表）

### 1. 节点级别的操作（高优先级）— ✅ 已实现

**原位置**: `SchedulingService.java` (约 861–1054 行)

- `triggerNode`：校验节点归属，获取或创建 RUNNING Run，创建或重置该节点任务实例为 PENDING。
- `retryNode`：查找该节点失败实例，创建新重试实例并落库。
- `pauseNode`：将该节点 PENDING/RESERVED 实例标为 PAUSED，已 PAUSED 标为 SKIPPED。
- `resumeNode`：将 PAUSED/SKIPPED 实例恢复为 PENDING。

---

### 2. 任务重试逻辑（高优先级）— ✅ 已实现

**位置**: `TaskWorkerService.getMaxRetry(SchedulingTaskInstance instance)`

- 优先从 `SchedulingDagTask.maxRetry`（同版本同 nodeCode）取值。
- 否则从 `SchedulingTask.maxRetry` 取值。
- 否则从 `SchedulingTaskType.defaultMaxRetry` 取值。
- 默认返回 0（不重试）。异常时打日志并返回 0。

---

### 3. JSON 参数解析（中优先级）— ✅ 已实现

**位置**: `TaskExecutorService.parseJsonToMap` / 参数合并逻辑

- 使用 ObjectMapper 解析 JSON 字符串为 Map。
- 任务默认参数、节点覆盖参数、实例参数已合并后传入执行器。

---

### 4. 下游任务检查逻辑需要完善（中优先级）

**位置**: `TaskWorkerService.java` (231行)

```java
// TODO: 根据 DAG Edge 检查是否是下游任务
```

**问题**:
- 当前实现使用了 `DependencyCheckerService.isDownstreamTask()`，但逻辑可能不够完善
- 需要确保正确识别所有下游任务

**建议**: 验证当前实现是否正确，必要时优化

---

## 基础数据（任务类型、任务）完善项

以下针对「任务类型」与「任务」两块基础数据的可完善点，按优先级与类别整理，便于排期实现。

### 已具备能力（现状）

- **任务类型**：CRUD、租户内编码唯一（`tenant_id` + `code`）、删除前检查是否被任务引用。
- **任务**：CRUD、租户内编码唯一（可选）、必填 `typeId`、删除前检查是否被 DAG 节点引用。
- **参数**：任务类型 `paramSchema`（JSON Schema）、任务 `params`（JSON）；执行前有 `JsonSchemaValidationService` 校验。
- **执行能力**：任务类型绑定 `executor` 标识，运行时由 `TaskExecutorService` 按 bean 名/类名解析；已提供 `shellExecutor`、`loggingTaskExecutor`、`delayTaskExecutor` 等。

### 建议完善项

| 类别 | 项 | 优先级 | 说明 |
|------|----|--------|------|
| **多租户** | 列表/详情租户校验 | 高 | `listTaskTypes` / `listTasks` 的 `tenantId` 为可选，未传时可能跨租户；`getTaskType(id)` / `getTask(id)` 未校验当前用户租户，存在越权风险。建议：从请求头/Token 取当前租户，列表默认按当前租户过滤；详情校验资源归属当前租户。 |
| **多租户** | 创建时租户来源 | 高 | 当前 `tenantId` 来自 DTO（前端传入），若前端未传或传错会导致数据落错租户。建议：创建时忽略 DTO 的 `tenantId`，由后端从 SecurityContext/`X-Tenant-Id` 写入。 |
| **校验** | 任务名称必填 | 中 | ✅ 已实现：DTO 增加 `@NotBlank`；createTask/updateTask 内校验非空并 trim；前端 Task.vue 表单名称项必填校验。 |
| **校验** | typeId 对应任务类型存在且同租户 | 中 | ✅ 已实现：创建任务时必填 `typeId` 并校验任务类型存在且 `tenantId` 一致（`SchedulingService.createTask`）；更新任务时若传入 `typeId` 则校验存在且同租户后更新（`SchedulingService.updateTask`）。 |
| **体验** | 执行器下拉/列表接口 | 中 | ✅ 已实现：`GET /scheduling/executors` 返回已注册执行器标识（bean 名）；TaskType.vue 执行器改为下拉选择，选项来自该接口。 |
| **体验** | 任务类型名称展示 | 低 | ✅ 已实现：前端在 Task.vue 中根据已加载的 taskTypes 用 typeId 解析并展示类型名称，列表列与详情均显示名称。 |
| **数据与文档** | scheduling_task_param 表 | 低 | ✅ 已说明。见本文档「五、文档与体验」：当前参数以 `task.params` 为准，`scheduling_task_param` 表预留未用。 |
| **文档** | 执行器与 identifier 对应关系 | 低 | ✅ 已说明。见 [SCHEDULING_TASK_EXECUTOR_GUIDE.md](./SCHEDULING_TASK_EXECUTOR_GUIDE.md)；前端任务类型表单「执行器」为下拉（来自 `GET /scheduling/executors`），减少填错。 |

### 可选增强（非必须）

- **任务名称唯一性**：若业务需要「同租户下任务名称唯一」，可在 DB 增加 `UNIQUE(tenant_id, name)` 或在 Service 内做唯一校验。
- **paramSchema 前端校验**：✅ 已实现。任务表单「参数」按所选任务类型的 `paramSchema`（JSON Schema）做前端校验（Task.vue 使用 ajv），不通过则提示并阻止提交；列表接口返回的类型含 `paramSchema`，无需额外请求。
- **删除提示**：✅ 已实现。删除任务类型时若有关联任务，后端返回 403 并提示「该任务类型正在被使用（被 X 个任务使用），无法删除。请先解除任务关联后再删除。」（`SchedulingService.deleteTaskType` 使用 `taskRepository.count` 统计后写入异常信息）。

以上完善项实现后，建议在本节更新状态（如「列表/详情租户校验 ✅ 已实现」）。

---

## 二、潜在问题

### 1. 任务执行结果存储格式问题（中优先级）

**位置**: `TaskWorkerService.java` (135行, 144行)

```java
instance.setResult(result.getResult() != null ? result.getResult().toString() : null);
history.setResult(result.getResult() != null ? result.getResult().toString() : null);
```

**问题**:
- 使用 `toString()` 存储结果，可能丢失复杂对象信息
- 数据库字段是 JSON 类型，应该存储 JSON 格式

**修复建议**:
```java
// 使用 JSON 序列化
ObjectMapper objectMapper = new ObjectMapper();
String resultJson = result.getResult() != null 
    ? objectMapper.writeValueAsString(result.getResult()) 
    : null;
instance.setResult(resultJson);
```

---

### 2. 缺少任务超时处理（高优先级）

**问题**:
- 任务定义中有 `timeoutSec` 字段，但没有超时检查逻辑
- 长时间运行的任务不会被自动取消

**影响**:
- 任务可能无限期运行
- 资源浪费

**修复建议**:
- 在 `TaskWorkerService.executeTask()` 中添加超时检查
- 使用 `Future` 和 `ExecutorService` 实现超时控制

---

### 3. 缺少并发策略实现（中优先级）

**问题**:
- 任务定义中有 `concurrencyPolicy` 字段（PARALLEL/SEQUENTIAL/SINGLETON/KEYED）
- 但没有实现并发控制逻辑

**影响**:
- 无法控制任务的并发执行
- SINGLETON 和 KEYED 策略无法生效

**修复建议**:
- 实现并发策略检查
- SINGLETON: 同一任务只能有一个实例运行
- KEYED: 基于 key 的并发控制

---

### 4. 缺少任务执行器注册机制（中优先级）

**问题**:
- `TaskExecutorService.getExecutor()` 通过 Bean 名称或类名查找执行器
- 没有统一的执行器注册机制
- 执行器查找可能失败

**建议**:
- 创建执行器注册表
- 支持动态注册执行器
- 提供执行器发现机制

---

### 5. 缺少审计日志记录（低优先级）

**问题**:
- 有 `SchedulingAudit` 表，但很多操作没有记录审计日志
- 无法追踪操作历史

**建议**:
- 在关键操作（创建、更新、删除、触发）时记录审计日志
- 使用 AOP 或统一的服务层记录

---

### 6. 错误处理不完善（中优先级）

**问题**:
- 很多地方使用 `RuntimeException`，错误信息不够详细
- 缺少统一的异常处理机制
- 错误堆栈可能丢失

**建议**:
- 定义自定义异常类型
- 统一异常处理
- 记录详细的错误信息

---

### 7. 数据一致性问题（高优先级）

**问题**:
- `DagExecutionJob` 中创建任务实例和更新 DAG Run 状态可能不在同一事务中
- 如果创建任务实例失败，DAG Run 状态可能不一致

**建议**:
- 确保关键操作在同一事务中
- 添加事务回滚处理
- 实现补偿机制

---

### 8. 任务执行器示例与文档（低优先级）✅ 已实现

**实现说明**:
- **文档**：[SCHEDULING_TASK_EXECUTOR_GUIDE.md](./SCHEDULING_TASK_EXECUTOR_GUIDE.md) 说明接口、注册方式、内置执行器、从零编写步骤（含完整代码示例）、paramSchema、依赖注入与最佳实践。
- **内置示例**：`shellExecutor`、`loggingTaskExecutor`、`delayTaskExecutor`、`upperCaseExecutor`（模板用），位于 `com.tiny.platform.infrastructure.scheduling.executor`。
- 前端任务类型「执行器」下拉来自 `GET /scheduling/executors`，与上述 Bean 名称一致。

---

### 9. 任务参数验证（中优先级）✅ 已实现

**实现说明**:
- 后端在任务执行前使用任务类型的 `paramSchema`（JSON Schema）校验参数。
- 实现位置：`TaskExecutorService.execute()` 中在调用执行器前调用  
  `JsonSchemaValidationService.validate(taskType.getParamSchema(), params)`。
- `JsonSchemaValidationService` 支持 required / type / enum / minimum / maximum / minLength / maxLength / pattern / properties；当 `paramSchema` 为空或 null 时跳过校验。

---

### 10. 缺少任务执行监控（低优先级）

**问题**:
- 没有任务执行性能监控
- 无法统计任务执行时间、成功率等指标

**建议**:
- 添加指标收集
- 集成监控系统（如 Prometheus）

---

## 三、性能问题

### 1. 定时任务扫描效率（中优先级）

**问题**:
- `TaskWorkerService.processPendingTasks()` 每 5 秒扫描一次所有 PENDING 任务
- `DagRunMonitorService.monitorDagRuns()` 每 10 秒扫描一次所有 RUNNING 的 DAG Run
- 数据量大时可能影响性能

**建议**:
- 使用索引优化查询
- 限制每次扫描的数量
- 考虑使用消息队列

---

### 2. 数据库查询优化（中优先级）

**问题**:
- 某些查询可能没有使用索引
- N+1 查询问题

**建议**:
- 检查查询性能
- 添加必要的索引
- 使用 JOIN 查询减少数据库访问

---

## 四、安全性问题

### 1. 缺少权限控制（高优先级）

**问题**:
- Controller 层没有权限检查
- 任何用户都可以触发、暂停、删除 DAG

**建议**:
- 添加权限注解（如 `@PreAuthorize`）
- 实现租户级别的权限控制

---

### 2. 任务执行器安全（中优先级）

**问题**:
- 任务执行器可以执行任意代码
- 没有执行器白名单机制

**建议**:
- 实现执行器白名单
- 限制可执行的执行器类型
- 添加执行器权限验证

---

## 五、文档与体验（低优先级）

以下两项已落实，便于用户与开发者理解数据含义与配置方式。

### 1. 任务参数存储说明（scheduling_task_param 表）

- **当前约定**：任务运行时参数以 **`scheduling_task.params`**（JSON 列）为准；接口 `GET /scheduling/task-param/{taskId}` 返回的即为该任务的 `params` 字段（未设置时返回 `{}`）。
- **`scheduling_task_param` 表**：建表中保留该表，当前业务逻辑**未使用**，仅作预留或后续扩展（如按 key 独立存储、审计等）。若后续统一为一种存储方式，需在迁移时同步更新接口与 Worker 读取逻辑。

### 2. 执行器与 identifier 对应关系

- **文档**：详见 [SCHEDULING_TASK_EXECUTOR_GUIDE.md](./SCHEDULING_TASK_EXECUTOR_GUIDE.md)。任务类型中的「执行器」填写**已注册的 Bean 名称**（如 `shellExecutor`、`loggingTaskExecutor`、`delayTaskExecutor`）或执行器**全类名**；运行时由 `TaskExecutorService` 按 bean 名/类名解析，与现有执行器实现一一对应。
- **前端**：任务类型表单中「执行器」为下拉选择，选项来自 `GET /scheduling/executors`，避免填错；若需使用未在下拉中列出的执行器，可后续扩展接口或改为可输入并带提示。

---

## 六、建议的修复优先级

### 高优先级（必须修复）
1. ✅ 节点级别的操作实现
2. ✅ 任务重试逻辑修复
3. ✅ 任务超时处理
4. ✅ 数据一致性问题
5. ✅ 权限控制

### 中优先级（应该修复）
1. ✅ JSON 参数解析
2. ✅ 任务执行结果存储格式
3. ✅ 并发策略实现
4. ✅ 任务参数验证
5. ✅ 错误处理完善

### 低优先级（可以后续优化）
1. ✅ 审计日志记录
2. ✅ 任务执行器注册机制
3. ✅ 任务执行监控
4. ✅ 性能优化

---

## 七、总结

当前调度模块的核心功能已基本实现，但还存在以下主要问题：

1. **功能缺失**: 节点级别操作、超时处理、并发策略等
2. **逻辑错误**: 重试逻辑、参数解析等
3. **安全问题**: 权限控制、执行器安全等
4. **性能问题**: 查询优化、扫描效率等

建议按照优先级逐步修复这些问题，确保系统的稳定性和安全性。

