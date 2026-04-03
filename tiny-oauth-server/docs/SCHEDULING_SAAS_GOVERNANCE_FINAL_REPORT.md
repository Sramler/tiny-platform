# Scheduling 模块 SaaS 治理最终总报告

> 完成时间：2026-03  
> 范围：tiny-oauth-server scheduling 控制面与 Worker 多租户/权限/数据一致性/公平性/元数据租户硬化
>
> 说明：
> - 本文中剩余 `tenantId` / `tenant_id` 主要表示存储层租户归属字段、数据库列名或仓储过滤条件。
> - 当前运行时上下文契约已经前移到 `activeTenantId` 与 `X-Active-Tenant-Id`，不应再把本文中的 `tenantId` 叙述理解为当前活动租户外部 API 命名。

---

## 1. 总体结论

### 1.1 本轮完成内容

- **ACTIVE 版本与 Quartz 生命周期统一**：启动恢复仅恢复「enabled + cronEnabled + 有 ACTIVE 版本」的 DAG；版本激活/归档/DAG 启用与 Cron 开关变化均触发 Quartz 同步；前端准确展示 Cron 是否真正生效。
- **调度控制面 RBAC 硬化**：后端所有接口按 `scheduling:console:view` / `scheduling:console:config` / `scheduling:run:control` / `scheduling:audit:view` / `scheduling:cluster:view` 做 `@PreAuthorize`；前端按权限隐藏/禁用按钮并给出提示。
- **versionNo 唯一性与建库链路一致**：Liquibase 补充 `(dag_id, version_no)` 唯一约束（035）；Service 层将 DB 冲突翻译为「版本号已被占用，请刷新后重试」；前端版本管理弹窗可提示可执行反馈。
- **Worker 多租户公平性**：单轮扫描内引入「每租户批量上限」`scheduling.worker.max-tasks-per-tenant-per-cycle`（默认 100），高负载租户不会在一轮内吃满 Worker，保留 SINGLETON/KEYED/PARALLEL 语义。
- **编排元数据租户维度硬化**：`scheduling_dag_version` / `scheduling_dag_task` / `scheduling_dag_edge` 补充 `tenant_id` 列，Liquibase 回填并收紧非空 + 索引（036）；创建版本/节点/边时显式写入存储层租户归属字段。

### 1.2 已关闭或主体完成的基础问题

| 问题 | 状态 |
|------|------|
| 有 Quartz Job 但无 ACTIVE 版本导致持续报错 | 已关闭（恢复时校验 ACTIVE） |
| 从无 ACTIVE 到有 ACTIVE 时未自动补建 Quartz Job | 已关闭（版本状态变化触发同步） |
| 调度控制面无租户内 RBAC | **主体完成**：后端 @PreAuthorize + 前端体验已实现；**收口**：RBAC 拒绝路径已由 SchedulingControllerRbacIntegrationTest 真实覆盖（Method Security 生效）。 |
| versionNo 唯一约束仅存在于 schema.sql、Liquibase 缺失 | 已关闭（035 迁移 + 冲突翻译） |
| Worker 纯全局 PENDING 队列、易饿死低负载租户 | **主体完成**：每租户 cap 已实现；**收口**：属第一版 cap，不等于完全消除饥饿风险，建议下一轮做 stronger validation。 |
| 编排元数据表无 tenant_id、隔离仅靠 Service 链式校验 | **主体完成**：补列 + 回填 + 写入路径已做；**收口**：Repository 读路径仍未彻底双保险，建议下一轮做 tenant-filter hardening。 |
| 错误与日志脱敏 | **主体完成**：SchedulingErrorSanitizer/SchedulingLogSanitizer + 异常统一文案已做；**收口**：getTaskInstanceLog() 已修正为不向前端暴露 logPath/stackTrace，仅返回脱敏摘要，并由 SchedulingServiceGetTaskInstanceLogTest 覆盖。 |
| 平台级菜单入口在普通租户暴露 | **主体完成**：新租户 bootstrap 不再复制 `tenant` / `idempotentOps` 平台菜单；菜单树读取也会按平台管理员身份过滤这两类平台入口，避免历史复制数据继续在侧边栏或菜单管理页暴露。 |

### 1.3 仍残留/需持续关注的问题

- **Repository 层租户过滤**：version/task/edge 已带 tenant_id，但当前读路径仍以「DAG/版本 ID + Service 校验」为主；后续可逐步增加 `findByXxxAndTenantId` 并在 Worker/DependencyChecker 等无请求上下文场景使用，形成第二道闸。
- **调度 E2E 与 Nightly**：real-link Nightly 已补固定清单与失败产物上传（调度编排 + 双向跨租户拒绝 + 同租户只读 RBAC 拒绝）；`ensure-scheduling-e2e-auth.sh` 现会在运行前校验默认租户 bootstrap 模板（`ROLE_ADMIN` + 037 authority 资源 + `scheduling:*` 绑定）；但该套件仍未在本轮真实执行。
- **real-link 租户创建链路更贴近真实应用**：`real.global.setup.ts` 现在会对 secondary / readonly 这类“不同于主租户”的测试租户，优先使用平台自动化身份的真实 bearer token 调 `/sys/tenants` 创建租户，再由 E2E helper 仅补用户与认证方式；cross-tenant/readonly 不再完全依赖直写 `tenant` 表。
- **租户管理接口已收口为平台管理员入口**：`/sys/tenants` 现通过 `TenantManagementAccessGuard` 限制为默认平台租户中的 `ROLE_ADMIN/ADMIN` 才可访问；real-link 若需动态创建第二租户/readonly 租户，改由 `E2E_PLATFORM_*` 平台自动化身份调用真实 `/sys/tenants`，不再依赖普通租户管理员越权。
- **认证 authority 映射收口**：`SecurityUser` 现仅发出 `role.code` / `role.name` / `resource.permission`，避免真实 JWT 继续夹带旧式资源名 authority，运行态 authority 统一按规范权限码鉴权。
- **调度 authority 资源正式化**：Liquibase `037` 已为默认租户补充 `scheduling:console:view` / `scheduling:console:config` / `scheduling:run:control` / `scheduling:audit:view` / `scheduling:cluster:view` / `scheduling:*` 的隐藏 authority 资源，并把 `ROLE_ADMIN` 绑定到 `scheduling:*`；不再只靠 E2E bootstrap 临时补资源。
- **调度菜单权限码规范化**：Liquibase `039` 与 `data.sql` 已把历史 `scheduling:dag:list` / `scheduling:task:list` / `scheduling:task-type:list` / `scheduling:dag-run:list` / `scheduling:audit:list` 收口到 `scheduling:console:view` / `scheduling:audit:view`，使 fresh install、升级迁移、前端页面守卫与后端 RBAC 使用同一套规范码。
- **控制面菜单 URI 规范化**：Liquibase `040` 与 `data.sql` 已把默认控制面菜单资源中历史 `/api/users`、`/api/roles`、`/api/resources/menus`、`/api/resources` 收口到当前真实控制器路径 `/sys/users`、`/sys/roles`、`/sys/menus`、`/sys/resources`，避免 fresh install 与升级库在资源元数据上继续漂移。
- **新租户权限模板初始化**：Tenant 创建链路已接入默认租户模板 bootstrap，新租户会复制默认租户的资源树、角色与 `role_resource` 关系，不再只有默认租户天然具备调度 authority。
- **平台菜单模板收口**：默认租户模板中的 `tenant` / `idempotentOps` 平台菜单不再复制到新租户；对于历史上已复制过的平台菜单，`/sys/menus/tree`、`/sys/menus/tree/all` 与子菜单读取会按当前平台管理员身份做过滤。
- **历史平台菜单清理迁移**：Liquibase `038` 已补“清理非默认租户中的 `tenant` / `idempotentOps` 平台菜单与角色绑定”，避免平台入口长期以脏数据形式残留在普通租户资源表中。
- **菜单下发更贴近用户授权**：`/sys/menus/tree` 与按父节点加载子菜单，对普通用户已改为“目录按租户查、菜单按当前用户 `user -> role -> resource` 关系查”；不再总是先查整租户菜单再在 Service 里裁剪。
- **菜单管理接口与页面已收口 RBAC**：`/sys/menus`、`/sys/menus/tree/all`、`/sys/menus/parent/{id}` 与菜单增删改已按 `system:menu:list`、`system:menu:create`、`system:menu:edit`、`system:menu:delete`、`system:menu:batch-delete` 等权限收口；`Menu.vue` 无管理读取权限时不再请求后台菜单管理接口。
- **菜单前端 API 契约已对齐**：`batchDeleteMenus` 与 `updateMenuSort` 已统一走 `MenuController` 的 `/sys/menus/**`，不再从前端误打旧的 `/sys/resources/menus/**`；`menu.ts` 中仓库内无人使用且后端不存在的旧导出已清理。

### 1.4 错误与日志脱敏（主体完成 + 收口）

- **异常响应脱敏**：SchedulingExceptions 新增 `systemError(String userFacingMessage, Throwable cause)`；SchedulingService/Quartz/DataIntegrityViolationException 等统一改为抛出统一文案，详细原因仅写服务器日志。
- **任务执行失败文案脱敏**：SchedulingErrorSanitizer.sanitizeForPersistence 对写入或返回前端的错误文案做脱敏；TaskExecutorService/TaskWorkerService 在构造 failure 时使用该脱敏。
- **日志脱敏**：SchedulingLogSanitizer.maskParamsForLog 仅输出参数键名与数量；LoggingTaskExecutor/ShellTaskExecutor/UpperCaseExecutor 使用该摘要或 sanitizeMessageForLog。
- **getTaskInstanceLog 收口（纠偏）**：`getTaskInstanceLog()` 不再向前端暴露原始 logPath、完整 stackTrace、内部异常细节；成功返回有限结果摘要（经 sanitizeForLogResponse），失败仅返回脱敏后的错误摘要；由 SchedulingServiceGetTaskInstanceLogTest 明确验证不返回 logPath、不返回 stackTrace、失败信息脱敏。

### 1.5 Repository 租户过滤（主体完成，未彻底双保险）

- **Repository 带 tenantId 的查询**：SchedulingDagVersionRepository / SchedulingDagTaskRepository / SchedulingDagEdgeRepository 已增加 findByXxxAndTenantId 系列。
- **无请求上下文场景**：QuartzCronRecoveryRunner、TaskWorkerService、TaskExecutorService、DependencyCheckerService 等在 tenantId 非空时优先使用租户查询；createDagTaskInstances 在 run/version 均有 tenantId 时按租户过滤。
- **继续推进（只读热点路径）**：`getDagVersion` / `listDagVersions` / `getDagNode` / `getDagNodes` / `getUpstreamNodes` / `getDownstreamNodes` / `getDagEdges` / `getDagRunNodes` 已切换到 tenant-aware repository 查询，减少只读接口退回无租户仓储方法的机会。
- **收口**：读路径仍以「DAG/版本 ID + Service 校验」为主，Repository 层未全面改为「必须带 tenant_id」的第二道闸；建议下一轮做 repository tenant-filter hardening。

---

## 2. 分阶段结果（高密度总结）

| 阶段 | 一句话总结 |
|------|-------------|
| **阶段 0** | 建立事实基线：梳理控制面组件、默认建库链路（Liquibase + JPA ddl-auto）、已有约束、无 RBAC、Worker 无公平性、前端页面职责明确；输出风险排序与确认/推断结论。 |
| **阶段 1** | ACTIVE 与 Quartz 统一：恢复 Runner 仅恢复有 ACTIVE 版本的 DAG；createDagVersion/updateDagVersion 在状态涉 ACTIVE 时触发 Quartz 同步；前端 Dag/DagDetail 展示 Cron 生效状态与原因。 |
| **阶段 2** | RBAC 硬化：SchedulingAccessGuard + Controller 全端点 @PreAuthorize；前端 TaskType/Task/Dag/DagDetail/DagHistory/Audit 按权限控制按钮与提示。 |
| **阶段 3** | versionNo 唯一：Liquibase 035 增加 uk_scheduling_dag_version_dag_version；Service 捕获 DataIntegrityViolationException 转用户可读错误；DagDetail 冲突时提示刷新重试。 |
| **阶段 4** | Worker 公平性：processPendingTasks 内按租户计数，每租户每轮不超过 max-tasks-per-tenant-per-cycle；单测 TaskWorkerServiceFairnessTest 校验 cap 生效。 |
| **阶段 5** | 元数据租户硬化：三张编排表补 tenant_id + 036 迁移回填与非空+索引；SchedulingService 在 createDagVersion/createDagNode/createDagEdge 中显式写入 tenantId。 |

---

## 3. 关键改动文件

### 3.1 后端（Java）

| 文件 | 变更要点 |
|------|----------|
| `config/QuartzCronRecoveryRunner.java` | 恢复前检查 DAG 是否存在 ACTIVE 版本，无则跳过 |
| `service/SchedulingService.java` | 版本创建/更新时在涉 ACTIVE 时 syncDagCronToQuartz；createDagVersion 写 version.tenantId、捕获 versionNo 冲突并抛可读异常；createDagNode/createDagEdge 写 node/edge.tenantId；runQuartzOperation/trigger 失败抛 systemError(统一文案)；DataIntegrityViolationException 非版本冲突时抛 systemError；validateJsonDocument 不暴露 e.getMessage() |
| `controller/SchedulingController.java` | 全端点 @PreAuthorize 使用 SchedulingAccessGuard 五类权限 |
| `security/SchedulingAccessGuard.java` | 新增：READ / MANAGE_CONFIG / OPERATE_RUN / VIEW_AUDIT / VIEW_CLUSTER_STATUS 及 can* 方法 |
| `security/SchedulingErrorSanitizer.java` | 新增：sanitizeForPersistence 对错误文案脱敏（堆栈/异常类名/过长→「任务执行失败」） |
| `security/SchedulingLogSanitizer.java` | 新增：maskParamsForLog、sanitizeMessageForLog，供执行器日志脱敏 |
| `auth/resource/support/PlatformControlPlaneResourcePolicy.java` | 新增：统一判定 `tenant` / `idempotentOps` 等平台级控制面资源 |
| `service/TaskWorkerService.java` | processPendingTasks 内 processedPerTenant + maxTasksPerTenantPerCycle 每租户 cap；执行异常时 failure 使用 SchedulingErrorSanitizer.sanitizeForPersistence |
| `service/TaskExecutorService.java` | 执行失败返回 failure(sanitize(e.getMessage()), e)；parseJsonToMap 校验异常不暴露 e.getMessage() |
| `service/JsonSchemaValidationService.java` | 校验/解析异常文案改为固定「解析 JSON Schema 失败/参数校验失败，请检查格式」 |
| `exception/SchedulingExceptions.java` | 新增 systemError(String userFacingMessage, Throwable cause) 不暴露 cause 文案 |
| `job/DagExecutionJob.java` | JobExecutionException 不携带 e.getMessage()，仅统一「DAG执行失败」等 |
| `executor/LoggingTaskExecutor.java` | 日志使用 SchedulingLogSanitizer.maskParamsForLog(params) |
| `executor/ShellTaskExecutor.java` | 同上 |
| `executor/UpperCaseExecutor.java` | in/out 使用 sanitizeMessageForLog 截断 |
| `model/SchedulingDagVersion.java` | 新增 tenantId 字段 |
| `model/SchedulingDagTask.java` | 新增 tenantId 字段 |
| `model/SchedulingDagEdge.java` | 新增 tenantId 字段 |
| `menu/service/MenuServiceImpl.java` | 菜单树/子菜单读取对普通用户改为按当前用户角色资源下发；完整菜单树仍用于后台配置；同时按平台管理员身份过滤平台级菜单 |
| `application/controller/menu/MenuController.java` | 菜单管理读写接口增加 RBAC，运行态 `/sys/menus/tree` 与后台管理读写分离 |
| `application/controller/menu/security/MenuManagementAccessGuard.java` | 菜单管理 READ/CREATE/UPDATE/DELETE 权限守卫，统一使用 `system:menu:*` 规范码 |
| `tenant/service/TenantBootstrapServiceImpl.java` | 新租户 bootstrap 时跳过平台级菜单资源，并跳过对应 role_resource 绑定 |

### 3.2 前端（Vue）

| 文件 | 变更要点 |
|------|----------|
| `Dag.vue` | Cron 生效状态列 + isCronEffective / getCronEffectiveReason；按钮按 canManageSchedulingConfig / canOperateSchedulingRun 控制 |
| `DagDetail.vue` | Cron 生效描述项；版本提交冲突时提示「请刷新后重试」；配置/运行按钮按权限禁用与提示 |
| `DagHistory.vue` | 运行操作按钮按 canOperateSchedulingRun 控制 |
| `TaskType.vue` | 增删改按钮按 canManageSchedulingConfig 控制 |
| `Task.vue` | 同上 |
| `Audit.vue` | 按 canViewSchedulingAudit 控制加载与提示 |

### 3.3 数据库迁移（Liquibase）

| 文件 | 变更要点 |
|------|----------|
| `035-enforce-dag-version-no-unique.yaml` | 若不存在则添加 uk_scheduling_dag_version_dag_version (dag_id, version_no)；重复数据时 SIGNAL 报错 |
| `036-add-tenant-id-to-scheduling-dag-metadata.yaml` | 为 scheduling_dag_version / scheduling_dag_task / scheduling_dag_edge 增加 tenant_id、回填、NOT NULL、租户相关索引 |
| `038-cleanup-platform-only-menus-from-non-default-tenants.yaml` | 删除历史非默认租户中误复制的 `tenant` / `idempotentOps` 平台菜单及对应 `role_resource` 绑定 |
| `039-normalize-permission-codes.yaml` | 将历史调度页面权限与旧聚合权限统一迁移到 `scheduling:console:view` / `scheduling:audit:view` 等规范码 |
| `040-normalize-control-plane-resource-uris.yaml` | 将历史控制面菜单资源的旧 `/api/*` URI 迁移到当前 `/sys/*` 控制器路径 |
| `db.changelog-master.yaml` | include 035、036、037、038、039、040 |

### 3.4 测试

| 文件 | 变更要点 |
|------|----------|
| `config/QuartzCronRecoveryRunnerTest.java` | 无 ACTIVE 不恢复、有 ACTIVE 恢复、版本状态变化触发同步等 |
| `security/SchedulingAccessGuardTest.java` | 五类权限与 wildcard 的通过/拒绝 |
| `security/SchedulingErrorSanitizerTest.java` | 错误文案脱敏：null/空、TIMEOUT/CANCELLED 保留、堆栈/异常类名→兜底文案 |
| `service/SchedulingServiceGetTaskInstanceLogTest.java` | **纠偏轮新增**：不返回原始 logPath、不返回 stackTrace、失败信息经脱敏 |
| `controller/SchedulingControllerRbacIntegrationTest.java` | **纠偏轮新增**：在 Method Security 真实生效的上下文中验证 READ/MANAGE_CONFIG/OPERATE_RUN/VIEW_AUDIT/VIEW_CLUSTER_STATUS 允许与拒绝及 scheduling:* 通过 |
| `controller/SchedulingControllerRbacTestConfig.java` | **纠偏轮新增**：最小测试配置（@EnableMethodSecurity + SchedulingAccessGuard + SecurityFilterChain + Pageable） |
| `menu/service/MenuServiceImplTest.java` | 平台菜单过滤差异 + 普通用户菜单树/子菜单按当前用户名授权下发，不再回退整租户菜单查询 |
| `application/controller/menu/MenuControllerRbacIntegrationTest.java` | 菜单管理接口在 Method Security 生效时的允许/拒绝路径 |
| `views/menu/Menu.test.ts` | 菜单管理页有权限时加载数据，无权限时展示 guard 且不请求 `/sys/menus` |
| `tenant/service/TenantBootstrapServiceImplTest.java` | 新租户 bootstrap 不复制平台菜单资源，也不保留其 role_resource 关联 |
| `service/TaskWorkerServiceFairnessTest.java` | 每租户 cap 生效，高负载租户不超上限 |
| `service/TaskWorkerServiceCancellationTest.java` | setUp 中设置 maxTasksPerTenantPerCycle=100，保证 processPendingTasks 分页与 reserve 行为可测 |
| 前端单测（TaskType/Task/Dag/DagDetail/Audit 等） | 补充 message.warning mock，按权限的按钮状态或拦截 |

---

## 4. 测试结果

### 4.1 纠偏收口轮实际执行过的命令

```bash
# 任务 A：getTaskInstanceLog 脱敏
mvn -q -pl tiny-oauth-server test -Dtest=SchedulingServiceGetTaskInstanceLogTest -DfailIfNoTests=false

# 任务 B：RBAC 拒绝路径（Method Security 真实生效）
mvn -q -pl tiny-oauth-server test -Dtest=SchedulingControllerRbacIntegrationTest -DfailIfNoTests=false

# 继续推进：repository tenant-filter hardening（调度只读热点路径）
mvn -q -pl tiny-oauth-server test -Dtest=SchedulingServiceTenantScopeTest,SchedulingServiceGetTaskInstanceLogTest,SchedulingControllerRbacIntegrationTest,TaskWorkerServiceFairnessTest -DfailIfNoTests=false
```

### 4.2 成功/失败

- 上述两条命令在纠偏收口轮执行中均**通过**（退出码 0）。
- 其他阶段遗留的单测（QuartzCronRecoveryRunnerTest、SchedulingAccessGuardTest、SchedulingServiceTest、TaskWorkerServiceFairnessTest 等）未在本轮再次执行，以历史通过为准。

### 4.3 未跑或需说明的测试

- **Liquibase 迁移**：migration smoke 脚本现已显式校验 `035/036/037/038/039/040`、默认租户 bootstrap 模板前提，以及“非默认租户不存在平台菜单”“默认租户调度菜单权限已规范化”“默认控制面菜单 URI 已规范化”这三类结果；但本轮仍未在真实 MySQL 上实跑该 workflow，**建议**：下一轮在 real MySQL CI 中真正执行。
- **Migration smoke 基础设施**：已补 `scripts/verify-scheduling-migration-smoke.sh` 与 `.github/workflows/verify-migration-smoke-mysql.yml`，用于显式校验 035/036/037/038/039/040 的索引、列、回填、平台菜单清理、权限与 URI 规范化结果；**但本轮未在真实 MySQL 上实跑**。
- **E2E / real-link**：本轮未实际执行 real-link，但已补 `verify-scheduling-real-e2e.yml` 的固定调度清单（`scheduling-dag-orchestration` + 双向跨租户拒绝 + `scheduling-rbac-readonly`）与 artifact 上传；新增 readonly 自动化身份用于真实 RBAC 拒绝回归。
- **全量 scheduling 单测**：可执行 `mvn -q -pl tiny-oauth-server test -Dtest='**/scheduling/**/*Test'` 做回归。

---

## 5. 风险与后续建议

### 5.1 当前仍需继续治理的点

- Migration 在真实 MySQL 上的验证（migration smoke / real MySQL CI）。
- E2E 与 Nightly 中调度相关用例（权限、跨租户、Cron、run/node 级操作）的覆盖与环境标准化。
- Worker 公平性更强验证（第一版 cap 不等于完全消除饥饿）。
- Repository 层租户过滤硬化（读路径双保险）。

### 5.2 是否建议继续下一轮

- **建议继续**。控制面权限、数据一致性、公平性、元数据租户、错误与日志脱敏、Repository 租户查询均已主体完成，纠偏轮已收口「getTaskInstanceLog 脱敏」与「RBAC 拒绝路径真实测试」。下一轮建议见 5.3。

### 5.3 建议的下一轮顺序与说明

1. **migration smoke / real MySQL CI**  
   - 在 CI 或测试环境对 035/036 等调度相关 Liquibase 变更跑完整迁移与回滚验证。  
   - **为什么不能只靠 E2E/Nightly**：迁移正确性是数据层前提，若迁移在真实 MySQL 上失败或与 JPA/schema.sql 不一致，E2E 再全也会被环境或建库方式掩盖；migration smoke 应在 Nightly 或独立流水线中显式执行，且不依赖应用已启动的 E2E。

2. **real-link E2E / Nightly scheduling suite**  
   - 已固定调度 Nightly real-link 套件：`scheduling-dag-orchestration` + `cross-tenant-a-to-b` + `cross-tenant-b-to-a` + `scheduling-rbac-readonly`，并上传 Playwright/前后端失败产物。  
   - 仍待补的是“真实执行结果”和更长链路 full-chain，不是只读 RBAC 身份本身。  
   - **顺序**：full-chain 不放入默认 PR 快速链路，仅 Nightly 或专项流水线；PR 可跑 smoke 或有限 real-link 子集。

3. **worker fairness stronger validation**  
   - 在现有每租户 cap 基础上，增加对「低负载租户不被长期饿死」的验证或观测（如指标/单测/场景化测试）。

4. **repository tenant-filter hardening**  
   - 读路径逐步改为「必须带 tenant_id」的第二道闸；Worker/DependencyChecker 等无请求上下文场景全面使用 findByXxxAndTenantId，与 Service 链式校验形成双保险。

---

*本报告对应 Scheduling 模块 SaaS 治理阶段 0～5 及纠偏收口轮的交付与结论。*
