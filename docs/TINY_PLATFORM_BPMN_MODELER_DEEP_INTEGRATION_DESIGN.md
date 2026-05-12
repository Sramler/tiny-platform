# Tiny Platform BPMN 设计器深度集成详细设计

最后更新：2026-05-11

适用仓库：`${REPO_ROOT}`

适用模块：

- 后端：`tiny-oauth-server`
- 前端：`tiny-oauth-server/src/main/webapp`
- 工作流引擎：Camunda 7 Engine Only
- 前端建模器：`bpmn-js` + `bpmn-js-properties-panel`

路径变量约定：

- `${REPO_ROOT}`：tiny-platform 仓库根目录。
- 文中代码路径默认相对 `${REPO_ROOT}`。
- 命令示例若需要进入仓库根目录，统一使用 `cd "${REPO_ROOT}"`。
- 不在设计文档中写入个人机器绝对路径。

## 1. 文档目标

本文档把 BPMN 设计器深度集成拆成可落地、可验收、可回滚的阶段任务。

目标不是“页面能画 BPMN”，而是形成 Tiny Platform 自有的流程设计能力：

- 建模器内核可复用、可测试、可释放资源。
- 设计态保存与运行态部署分离。
- Camunda 7 原生属性与 Tiny Platform 平台扩展字段边界清晰。
- 属性面板能够接入平台角色、用户、表单、权限等数据源。
- 保存、校验、部署、版本、运行态查看形成闭环。
- 平台权限与多租户隔离不被削弱。

## 2. 当前态基线

当前已有能力：

- 前端建模页：`tiny-oauth-server/src/main/webapp/src/views/process/Modeling.vue`
  - 已接入 `BpmnModeler`
  - 已接入 `BpmnPropertiesPanelModule`
  - 已接入 `BpmnPropertiesProviderModule`
  - 已接入 `CamundaPlatformPropertiesProviderModule`
  - 已接入 `camunda-bpmn-moddle`
  - 已有导入、导出、部署、SVG 导出入口
- 前端 API：`tiny-oauth-server/src/main/webapp/src/api/process.ts`
  - 已有 `/process/deploy`
  - 已有 `/process/deploy-with-info`
  - 已有 `/process/validate`
  - 已有流程定义、部署、实例、任务相关 API 封装
- 后端控制器：`tiny-oauth-server/src/main/java/com/tiny/platform/application/oauth/workflow/ProcessController.java`
  - 已有 `/process` 控制面
  - 已有 `@workflowAccessGuard.canView(authentication)`
  - 已有 `@workflowAccessGuard.canConfig(authentication)`
  - 已有 `@workflowAccessGuard.canControlInstance(authentication)`
  - 已有平台 scope 与 tenant scope 的运行态租户解析

当前主要缺口：

- `Modeling.vue` 同时承担页面编排、建模器内核、XML 模板、部署流程和调试日志，职责过重。
- “保存设计”与“部署流程”尚未分离。
- 缺少设计草稿、版本和模型仓库。
- 业务属性尚未形成平台级 schema。
- 属性面板下拉数据尚未统一接平台 API。
- BPMN 校验结果尚未定位到节点。
- 只读 viewer 尚未叠加运行态信息。

## 3. 总体设计原则

1. 设计态与运行态分离。
   - 保存草稿只写平台模型库。
   - 部署才写 Camunda runtime repository。

2. Camunda 7 原生字段优先使用 Camunda moddle。
   - `candidateUsers` 写 `camunda:candidateUsers`
   - `candidateGroups` 写 `camunda:candidateGroups`
   - `formKey` 写 `camunda:formKey`

3. Tiny Platform 自定义字段统一使用固定 namespace。
   - 推荐前缀：`tp`
   - 推荐 namespace：`https://tiny-platform.local/schema/bpmn/tp/1.0`
   - 示例字段：`tp:approvalPolicy`、`tp:permissionCode`、`tp:scopeType`、`tp:businessModule`

4. 属性面板中的平台业务选项必须来自 API。
   - 角色、用户、用户组、表单、权限码、服务任务实现等来自 API。
   - BPMN/Camunda 固定枚举可以本地维护。

5. 校验分级。
   - `error`：阻断部署，原则上也阻断发布版本。
   - `warning`：允许保存草稿；是否允许部署由产品策略决定，默认部署前需二次确认或阻断。

6. 权限与租户隔离不可弱化。
   - 查看类能力使用 `workflow:console:view`。
   - 配置、保存、部署、删除使用 `workflow:console:config`。
   - 实例操作使用 `workflow:instance:control`。
   - 工作流租户管理使用 `workflow:tenant:manage`。
   - platform scope 与 tenant scope 不混查、不混部署。

7. 前端页面只做编排。
   - bpmn-js 实例创建、事件监听、XML/SVG 保存、销毁逻辑下沉到 `src/utils/bpmn/modeler/**`。
   - 页面保留弹窗、导航、消息提示、权限控制和用户交互。

### 3.1 平台流程与租户流程语义

本设计同时支持平台流程与租户流程，但二者必须通过 `scope_type` 显式区分。

平台流程：

- `scope_type = 'PLATFORM'`。
- `tenant_id = null`。
- 仅在 platform scope 下创建、编辑、校验、部署和查看。
- 部署到 Camunda 时使用无租户运行态，即部署服务收到的 workflow tenant 为 `null`。
- 只能使用平台运行态允许的数据源、表单、权限和服务任务实现。
- 不得被 tenant scope 直接读取、编辑、启动或作为租户流程隐式复用。

租户流程：

- `scope_type = 'TENANT'`。
- `tenant_id = 当前 active tenant 数值主键`。
- 仅在对应 tenant scope 下创建、编辑、校验、部署和查看。
- 部署到 Camunda 时必须携带当前 active tenant 作为 workflow tenant。
- 只能使用当前租户可见的数据源、表单、权限和服务任务实现。
- 不得读取或引用 platform-only 资源，除非后续有显式授权的模板发布机制。

模板发布不属于阶段 1.5 范围：

- 平台模型若未来需要作为租户模板下发，必须新增独立的模板发布 / 克隆设计。
- 不得把 platform scope 模型直接当成 tenant scope 模型复用。
- 不得通过修改 `tenant_id` 把同一条 `process_model` 记录在 platform 与 tenant 之间转换。

## 4. 阶段总览

| 阶段 | 名称 | 目标 | 是否阻塞后续 |
| --- | --- | --- | --- |
| 阶段 1 | 建模器内核抽离 | 让 bpmn-js 初始化、事件、导入导出可复用可测试 | 是 |
| 阶段 1.5 | 流程设计保存 | 新增草稿模型，分离保存与部署 | 是 |
| 阶段 1.6 | 流程建模信息架构拆分 | 将流程草稿管理与流程设计画布拆成清晰 Tab / 子视图 | 是 |
| 阶段 2 | 最小业务属性组 | 实现 `candidateUsers` / `candidateGroups` / `formKey` | 是 |
| 阶段 3 | 平台数据源异步化 | 属性选项接 API，并按 scope 过滤 | 否 |
| 阶段 4 | 校验、生命周期、viewer | 完整版本流、错误定位、运行态只读查看 | 否 |

## 5. 阶段 1：建模器内核抽离

### 5.1 目标

把当前 `Modeling.vue` 中的底层 bpmn-js 操作抽离到独立内核，让页面不直接持有建模器实现细节。

### 5.2 新增目录

必须新增：

```text
tiny-oauth-server/src/main/webapp/src/utils/bpmn/modeler/
```

建议文件结构：

```text
modeler/
├── index.ts
├── createModeler.ts
├── modelerTypes.ts
├── modelerEvents.ts
├── modelerXml.ts
├── modelerDefaults.ts
└── createModeler.test.ts
```

### 5.3 内核 API

`src/utils/bpmn/modeler/index.ts` 必须导出：

```ts
export interface TinyBpmnModelerHandle {
  importXml(xml: string): Promise<void>
  saveXml(options?: { format?: boolean }): Promise<string>
  saveSvg(): Promise<string>
  destroy(): void
  getRawModeler(): unknown
}

export interface CreateTinyBpmnModelerOptions {
  container: HTMLElement
  propertiesPanel?: HTMLElement
  enableMinimap?: boolean
  onDirtyChange?: (dirty: boolean) => void
  onSelectionChange?: (context: TinyBpmnSelectionContext) => void
}

export function createModeler(options: CreateTinyBpmnModelerOptions): Promise<TinyBpmnModelerHandle>
```

`TinyBpmnSelectionContext` 至少包含：

```ts
export interface TinyBpmnSelectionContext {
  elementId?: string
  elementType?: string
  businessObjectId?: string
  businessObjectName?: string
  rawElement?: unknown
}
```

### 5.4 事件要求

必须监听：

- `commandStack.changed`
  - 输出 `dirty = true`
  - 成功导入 XML 或手动标记保存后可重置 `dirty = false`
- `selection.changed`
  - 输出当前元素上下文

必须保证：

- `destroy()` 会注销事件监听。
- 路由切换后不重复触发旧监听。
- `destroy()` 可重复调用，不抛错。

### 5.5 `Modeling.vue` 改造要求

`Modeling.vue` 只保留：

- 页面布局
- 按钮与弹窗
- 保存、部署、导入、导出的用户交互编排
- 路由跳转
- 消息提示
- 权限判断

`Modeling.vue` 不应继续直接包含：

- `new BpmnModeler(...)`
- bpmn-js additionalModules 拼装细节
- `commandStack.changed` 原始事件绑定
- `selection.changed` 原始事件绑定
- 大段内置 BPMN XML 模板

### 5.6 验收标准

必须满足：

- 存在 `src/utils/bpmn/modeler/**`。
- `createModeler/importXml/saveXml/saveSvg/destroy` 可从统一入口导入。
- `commandStack.changed` 能驱动脏状态。
- `selection.changed` 能输出当前元素上下文。
- `destroy()` 会释放事件监听。
- `Modeling.vue` 不再直接 `new BpmnModeler(...)`。

### 5.7 建议验证命令

```bash
cd tiny-oauth-server/src/main/webapp
npm run test:unit -- src/utils/bpmn/modeler
npm run type-check
```

## 6. 阶段 1.5：流程设计保存

### 6.1 目标

新增流程设计草稿能力，把“保存设计”和“部署流程”拆开。

保存设计：

- 写入 Tiny Platform 模型库。
- 不部署到 Camunda。
- 可反复编辑。

部署流程：

- 从已保存模型或当前 XML 发布到 Camunda。
- 生成运行态流程定义。
- 写回部署关联信息。

### 6.2 推荐资源模型

新增后端资源：`/process/models`

不建议复用 Camunda 部署表表达未部署草稿。

原因：

- 未部署设计不是 Camunda runtime 概念。
- 草稿需要编辑、版本、校验结果、设计者、SVG 预览等平台字段。
- 复用部署表容易混淆“模型版本”和“流程定义版本”。

### 6.3 推荐数据库表

建议新增表：`process_model`

字段建议：

| 字段 | 类型建议 | 说明 |
| --- | --- | --- |
| `id` | bigint auto_increment | 平台模型 ID，对应 Java `Long` 与前端 `number` |
| `model_key` | varchar | 流程模型稳定 key |
| `name` | varchar | 模型名称 |
| `description` | varchar / text | 描述 |
| `scope_type` | varchar | `PLATFORM` / `TENANT` |
| `tenant_id` | bigint nullable | 租户 scope 下必填；platform scope 必须为空 |
| `normalized_tenant_id` | bigint generated | 归一化租户键，`IFNULL(tenant_id, 0)` |
| `status` | varchar | `DRAFT` / `VALIDATED` / `DEPLOYED` / `ARCHIVED` |
| `version` | int | 平台模型版本 |
| `bpmn_xml` | longtext | BPMN XML |
| `svg` | longtext nullable | SVG 预览 |
| `validation_status` | varchar | `NOT_VALIDATED` / `PASSED` / `FAILED` |
| `validation_summary` | json/text nullable | 最近一次校验摘要 |
| `deployment_id` | varchar nullable | 最近一次 Camunda deploymentId |
| `process_definition_id` | varchar nullable | 最近一次 Camunda processDefinitionId |
| `created_by` | varchar | 创建人 |
| `updated_by` | varchar | 更新人 |
| `created_at` | datetime | 创建时间 |
| `updated_at` | datetime | 更新时间 |

唯一约束必须采用单一 DDL 口径，不得依赖 MySQL `NULL` 在唯一索引中的特殊语义。

当前 DDL 示例以 MySQL 8 为基线；其他数据库落地时必须使用等价的 generated/computed column、函数索引或显式归一化字段实现同一约束语义。

推荐策略：

- `tenant_id` 一律使用平台租户数值主键，类型为 `BIGINT`。
- platform scope：`scope_type = 'PLATFORM'`，`tenant_id = null`，`normalized_tenant_id = 0`。
- tenant scope：`scope_type = 'TENANT'`，`tenant_id` 必填且 `tenant_id > 0`，`normalized_tenant_id = tenant_id`。
- `normalized_tenant_id` 使用 MySQL generated column，与 `runtime_version.normalized_tenant_id` 口径保持一致。
- 统一唯一约束：`scope_type + normalized_tenant_id + model_key + version`。

推荐 Liquibase / DDL 表达：

```sql
ALTER TABLE process_model
  ADD COLUMN normalized_tenant_id BIGINT
    GENERATED ALWAYS AS (IFNULL(tenant_id, 0)) STORED,
  ADD CONSTRAINT uk_process_model_scope_key_version
    UNIQUE (scope_type, normalized_tenant_id, model_key, version);
```

推荐校验约束：

```sql
ALTER TABLE process_model
  ADD CONSTRAINT ck_process_model_scope_type
  CHECK (scope_type IN ('PLATFORM', 'TENANT'));

ALTER TABLE process_model
  ADD CONSTRAINT ck_process_model_tenant_scope
  CHECK (
    (scope_type = 'PLATFORM' AND tenant_id IS NULL)
    OR
    (scope_type = 'TENANT' AND tenant_id IS NOT NULL AND tenant_id > 0)
  );
```

应用层保存前也必须执行同等校验，避免不同数据库版本对 `CHECK` 支持差异造成数据漂移。

如果平台已有统一审计字段规范，应复用现有基类/字段命名。

#### 6.3.1 字段字典

`process_model` 与 `ProcessModelResponse` 字段必须保持以下映射口径：

| DB 字段 | Java / DTO 字段 | TS 字段 | 类型 | 可空 | 示例 | 来源 / 说明 |
| --- | --- | --- | --- | --- | --- | --- |
| `id` | `id` | `id` | `Long` / `number` | 否 | `10001` | 数据库自增主键；前端短期按 `number`，若未来超过 JS 安全整数再迁移为字符串 |
| `model_key` | `modelKey` | `modelKey` | `String` | 否 | `leave_approval` | 优先来自 BPMN `process.id`，也可由创建请求显式指定 |
| `name` | `name` | `name` | `String` | 否 | `请假审批` | 优先来自请求；未提供时可回退 BPMN `process.name` 或 `modelKey` |
| `description` | `description` | `description` | `String` | 是 | `员工请假审批流程` | 用户输入 |
| `scope_type` | `scopeType` | `scopeType` | enum/string | 否 | `TENANT` | 应用层根据当前 active scope 写入，不信任前端提交 |
| `tenant_id` | `recordTenantId` | `recordTenantId` | `Long` / `string \| null` | 是 | `"10001"` | DB 使用 `tenant_id BIGINT`；DTO 暴露为业务记录所属租户 `recordTenantId` 字符串，platform scope 返回 `null` |
| `normalized_tenant_id` | 不暴露 | 不暴露 | `Long` | 否 | `0` | DB generated column，仅用于唯一约束与索引，不进入 API |
| `status` | `status` | `status` | enum/string | 否 | `DRAFT` | 模型生命周期状态 |
| `version` | `version` | `version` | `Integer` / `number` | 否 | `1` | 平台模型版本，不等同 Camunda 流程定义版本 |
| `bpmn_xml` | `bpmnXml` | `bpmnXml` | `String` | 否 | `<bpmn:definitions...>` | 当前设计态 XML |
| `svg` | `svg` | `svg` | `String` | 是 | `<svg...>` | 设计器预览快照，可失败降级为空 |
| `validation_status` | `validationStatus` | `validationStatus` | enum/string | 否 | `NOT_VALIDATED` | 最近一次模型校验状态 |
| `validation_summary` | `validationSummary` | `validationSummary` | `String` | 是 | `BPMN XML 验证通过` | 最近一次校验摘要 |
| `deployment_id` | `deploymentId` | `deploymentId` | `String` | 是 | `dep-001` | 最近一次 Camunda deployment id |
| `process_definition_id` | `processDefinitionId` | `processDefinitionId` | `String` | 是 | `leave_approval:1:abc` | 最近一次 Camunda process definition id |
| `process_definition_key` | `processDefinitionKey` | `processDefinitionKey` | `String` | 是 | `leave_approval` | 最近一次 Camunda process definition key |
| `process_definition_version` | `processDefinitionVersion` | `processDefinitionVersion` | `Integer` / `number` | 是 | `3` | 最近一次 Camunda process definition version |
| `created_by` | `createdBy` | `createdBy` | `String` | 是 | `alice` | 创建人 |
| `created_at` | `createdAt` | `createdAt` | `LocalDateTime` / ISO string | 否 | `2026-05-11T10:00:00` | 创建时间 |
| `updated_by` | `updatedBy` | `updatedBy` | `String` | 是 | `alice` | 最近更新人 |
| `updated_at` | `updatedAt` | `updatedAt` | `LocalDateTime` / ISO string | 否 | `2026-05-11T10:05:00` | 最近更新时间 |
| `deployed_by` | `deployedBy` | `deployedBy` | `String` | 是 | `alice` | 最近部署人 |
| `deployed_at` | `deployedAt` | `deployedAt` | `LocalDateTime` / ISO string | 是 | `2026-05-11T10:10:00` | 最近部署时间 |
| `lock_version` | `lockVersion` | `lockVersion` | `Long` / `number` | 否 | `0` | 乐观锁版本 |

映射规则：

- 前端不得提交 `scopeType` / `recordTenantId` 决定模型归属；归属必须由后端从当前 `TenantContext` 解析。
- `recordTenantId` 是 API 命名，表达“记录所属租户”；DB 字段仍为 `tenant_id BIGINT`。
- platform scope 响应 `recordTenantId = null`；tenant scope 响应 `String.valueOf(tenant_id)`。
- `normalized_tenant_id` 永远不暴露给前端。
- `status` 与 `validationStatus` 的枚举值必须在后端、前端类型、文档中同步；不得出现 `UNKNOWN` 与 `NOT_VALIDATED` 混用。

#### 6.3.2 系统字典与自动转义口径

流程模型字段语义与字段名不进入数据字典维护，例如：

- `latestDesignVersion`：最新设计版本。
- `currentRuntimeVersion`：当前运行版本。
- `hasUndeployedChanges`：是否存在未部署变更。

这些字段是工作流领域契约，必须由 API / DTO / 前端类型固定表达，不能通过字典动态改名或改变语义。

需要进入平台系统字典的是枚举值的展示元数据：

| 字典编码 | 枚举值 | 说明 |
| --- | --- | --- |
| `workflow_model_status` | `DRAFT` / `VALIDATED` / `DEPLOYED` / `ARCHIVED` | 设计态生命周期 |
| `workflow_runtime_state` | `NOT_DEPLOYED` / `CURRENT_RUNTIME` / `HISTORICAL_DEPLOYED` | 运行态部署关系 |
| `workflow_validation_status` | `NOT_VALIDATED` / `PASSED` / `FAILED` | 模型校验状态 |

字典只管理 label / color / sort / i18n 等展示属性，不管理业务判断。业务判断必须基于稳定枚举值，例如 `runtimeState === 'CURRENT_RUNTIME'`，不得基于中文展示文案。

本章节遵循 `docs/tiny-platform-字典指南.md` 的“字典中心边界与分层治理”原则：`workflow_model_status`、`workflow_runtime_state`、`workflow_validation_status` 属于代码固定的状态码 / 枚举码，字典中心只承载展示映射，不能替代后端枚举、前端类型或工作流状态机本身。

实现口径：

- 这些字典是平台级 system seed，允许平台升级维护，不允许普通租户编辑、删除或改编码。
- 前端必须提供统一自动转义能力，例如 `dictLabel(type, value)`、`useDictLabel(type)` 或通用 `DictTag` 组件。
- 组件层只声明字典类型和值，不直接分散请求字典 API。
- 字典数据可以在应用启动、进入工作流模块或首次使用时异步预热并缓存；“异步”指字典来源来自服务端配置，不代表每个单元格单独异步请求。
- 字典 API 失败时必须使用本地兜底 label / color，保证流程草稿列表和版本明细仍可读。

### 6.4 后端 API

新增控制器建议：

```text
ProcessModelController
```

路径建议：

```text
GET    /process/models
GET    /process/models/groups
POST   /process/models
GET    /process/models/{id}
PUT    /process/models/{id}
POST   /process/models/{id}/validate
POST   /process/models/{id}/deploy
POST   /process/models/{id}/versions
DELETE /process/models/{id}
```

接口语义：

| API | 权限 | 说明 |
| --- | --- | --- |
| `GET /process/models` | `workflow:console:view` | 查询模型版本记录列表，按当前 scope 自动过滤 |
| `GET /process/models/groups` | `workflow:console:view` | 查询流程资产分组列表，默认用于流程草稿页 |
| `POST /process/models` | `workflow:console:config` | 创建草稿 |
| `GET /process/models/{id}` | `workflow:console:view` | 查看模型详情 |
| `PUT /process/models/{id}` | `workflow:console:config` | 保存草稿 |
| `POST /process/models/{id}/validate` | `workflow:console:config` | 校验模型 XML |
| `POST /process/models/{id}/deploy` | `workflow:console:config` | 部署模型到 Camunda |
| `POST /process/models/{id}/versions` | `workflow:console:config` | 固化新平台版本 |
| `DELETE /process/models/{id}` | `workflow:console:config` | 删除草稿或归档模型 |

#### 6.4.1 设计态与运行态 API 边界

`/process/models/**` 是设计态资产控制面，只负责 Tiny Platform 的流程模型、草稿、版本、校验摘要和设计态部署编排。

`/process/deploy`、`/process/deploy-with-info`、`/process/start`、`/process/instances`、`/process/tasks` 等现有 `/process/**` 接口是运行态或兼容控制面，面向 Camunda 部署、流程定义、流程实例、任务和历史数据。

强约束：

- `ProcessModelController` 负责设计态模型资产，不得把草稿保存逻辑放回 `ProcessController`。
- `/process/models/{id}/deploy` 可以调用运行态部署服务，但必须以模型 ID 为唯一设计态入口，部署成功后只回写该模型的部署关联字段。
- `/process/deploy` 与 `/process/deploy-with-info` 保留为运行态/兼容部署入口，不得写入或隐式创建 `process_model` 记录。
- 同一次用户操作不得同时调用 `/process/models/{id}/deploy` 与 `/process/deploy*` 形成双写。
- `process_model` 是设计态真相源；Camunda repository 是运行态真相源；两者通过 `deployment_id`、`process_definition_id`、`process_definition_version` 做显式关联。
- 设计态列表、草稿编辑、版本查看只能读取 `/process/models/**`。
- 运行态实例、任务、历史和引擎信息只能读取现有运行态接口。

`/process/validate` 迁移口径：

- 阶段 1.5 保留 `/process/validate` 作为无状态 XML 校验兼容入口，不得持久化模型。
- 新增设计态校验必须优先使用 `/process/models/{id}/validate`，并把校验摘要写回模型。
- 前端建模页切到草稿模型后，不再直接依赖 `/process/validate` 作为设计态主入口。
- 后续若需要下线 `/process/validate`，必须先完成前端迁移、测试迁移和兼容公告；下线前不得改变其无状态语义。

`/process/validate` 下线判定条件：

- 前端建模页、模型详情页、部署前门禁均已切到 `/process/models/{id}/validate`。
- `src/api/process.ts` 中不再把 `/process/validate` 暴露给新设计态流程；仅保留兼容方法或标记 deprecated。
- `ProcessControllerTest` 与前端单测已覆盖兼容入口仍保持无状态、不写 `process_model`。
- `ProcessModelControllerTest` 已覆盖模型校验会写回 `validation_status` 与 `validation_summary`。
- 真实启动验证通过，且至少一个前端流程模型保存/校验/部署回归通过。
- 兼容窗口和迁移说明已写入本文件或对应 release note。
- 下线 PR 必须由 workflow 模块 owner 或指定 reviewer 批准，确认兼容窗口、回归结果和迁移说明均已满足。
- 满足以上条件后，才能在后续独立变更中删除或迁移 `/process/validate`，不得和首版 `/process/models/**` 落地合并。

#### 6.4.2 响应契约

新增 `/process/models/**` API 必须使用显式 DTO，不得新增 raw `Map<String, Object>` 响应风格。

当前 `tiny-oauth-server` 的统一错误响应基础设施是：

- `com.tiny.platform.infrastructure.core.exception.base.BaseExceptionHandler`
- `org.springframework.http.ProblemDetail`
- `com.tiny.platform.infrastructure.core.exception.code.ErrorCode`
- `com.tiny.platform.infrastructure.core.exception.exception.BusinessException`

当前 `tiny-oauth-server` 没有统一成功响应包装类型；`tiny-web` 模块中的 `GlobalResponse` 不作为 `tiny-oauth-server` 新接口的默认契约。

首版 `/process/models/**` 响应规则：

- 成功响应返回显式 DTO，例如 `ResponseEntity<ProcessModelResponse>`、`ResponseEntity<List<ProcessModelResponse>>`、`ResponseEntity<ProcessModelValidationResponse>`。
- 失败响应通过抛出 `BusinessException` 或参数校验异常交给 `BaseExceptionHandler` 统一转成 RFC 7807 `ProblemDetail`。
- 不新增 `success/data/error` 二次包装。
- 不新增 raw `Map<String, Object>` 成功或失败响应。
- 不在控制器里 `try/catch` 后吞异常并返回 200。

成功响应：

```json
{
  "id": 10001,
  "modelKey": "leave-approval",
  "name": "请假审批",
  "scopeType": "TENANT",
  "recordTenantId": "10001",
  "status": "DRAFT",
  "version": 1,
  "validationStatus": "NOT_VALIDATED",
  "updatedAt": "2026-05-10T10:00:00"
}
```

失败响应：

```json
{
  "type": "about:blank",
  "title": "资源不存在",
  "status": 404,
  "detail": "流程模型不存在",
  "instance": "/process/models/10001",
  "code": 40401,
  "path": "/process/models/10001",
  "traceId": "optional-trace-id"
}
```

推荐错误码：

| HTTP 状态 | 当前 `ErrorCode` | 场景 |
| --- | --- | --- |
| 400 | `INVALID_PARAMETER` / `VALIDATION_ERROR` | 请求字段非法 |
| 403 | `FORBIDDEN` / `ACCESS_DENIED` | 无权限或 scope 不匹配 |
| 404 | `NOT_FOUND` | 模型不存在或不可见 |
| 409 | `RESOURCE_CONFLICT` / `RESOURCE_STATE_INVALID` | 版本冲突、并发保存冲突、状态不允许 |
| 422 | `UNPROCESSABLE_ENTITY` | BPMN 校验失败 |
| 500 | `INTERNAL_ERROR` | 未预期服务端错误 |

控制器不得吞掉异常后统一返回 200；错误 HTTP 状态必须与错误语义匹配。

如后续需要工作流模型专用错误码，必须扩展 `ErrorCode` 并保持 5 位数字错误码规范；不得在 `/process/models/**` 内私自发明字符串错误码。

### 6.5 前端 API

在 `src/api/process.ts` 中新增 `processModelApi`：

```ts
export interface ProcessModel {
  id: number
  modelKey: string
  name: string
  description?: string
  scopeType: 'PLATFORM' | 'TENANT'
  recordTenantId?: string
  status: 'DRAFT' | 'VALIDATED' | 'DEPLOYED' | 'ARCHIVED'
  runtimeState: 'NOT_DEPLOYED' | 'CURRENT_RUNTIME' | 'HISTORICAL_DEPLOYED'
  version: number
  bpmnXml?: string
  svg?: string
  validationStatus?: 'NOT_VALIDATED' | 'PASSED' | 'FAILED'
  deploymentId?: string
  processDefinitionId?: string
  updatedAt?: string
  updatedBy?: string
}

export interface ProcessModelGroup {
  modelKey: string
  name: string
  scopeType: 'PLATFORM' | 'TENANT'
  recordTenantId?: string
  latestVersion: number
  latestDesignVersion: number
  latestStatus: 'DRAFT' | 'VALIDATED' | 'DEPLOYED' | 'ARCHIVED'
  currentRuntimeVersion?: number
  currentDeploymentId?: string
  hasUndeployedChanges: boolean
  versionCount: number
  updatedAt?: string
  updatedBy?: string
  latestModel: ProcessModel
  versions: ProcessModel[]
}

export const processModelApi = {
  listModels: (...) => ...,
  listModelGroups: (...) => ...,
  createModel: (...) => ...,
  getModel: (...) => ...,
  saveModel: (...) => ...,
  validateModel: (...) => ...,
  deployModel: (...) => ...,
  createVersion: (...) => ...,
  deleteModel: (...) => ...,
}
```

所有写操作必须接入现有 idempotency 机制。

### 6.6 前端页面能力

`Modeling.vue` 必须新增或改造：

- 新建模型
- 打开草稿
- 保存草稿
- 另存为
- 部署流程
- 离开页面前脏状态提醒
- 保存成功后重置 dirty 状态
- 保存失败时保留 dirty 状态

部署按钮文案不得再表达为“保存”。

建议按钮：

- `新建`
- `打开`
- `保存草稿`
- `校验`
- `部署`
- `导出 BPMN`
- `导出 SVG`

### 6.7 自动保存策略

阶段 1.5 可选，但建议预留：

- 监听 dirty 状态。
- 变更后 3-5 秒节流保存。
- 仅对已存在模型 ID 的草稿自动保存。
- 新模型未首次手动保存前不自动保存。
- 自动保存失败时显示非阻断提示，并保留 dirty 状态。

### 6.8 验收标准

必须满足：

- 保存草稿不会调用 Camunda 部署。
- 部署流程必须显式点击部署或调用 `/process/models/{id}/deploy`。
- 刷新页面后能从模型库恢复 BPMN XML。
- 从已部署定义反向编辑时，必须另存为新草稿，不得直接修改运行态定义。
- tenant scope 只能看到当前租户模型。
- platform scope 不接受按租户过滤混查。

### 6.9 建议验证命令

后端定向测试：

```bash
cd "${REPO_ROOT}"
./mvnw -pl tiny-oauth-server -Dtest=ProcessModelControllerTest test
```

前端定向测试：

```bash
cd tiny-oauth-server/src/main/webapp
npm run test:unit -- src/api/process.test.ts src/views/process/Modeling.test.ts
npm run type-check
```

如涉及 Liquibase：

```bash
cd "${REPO_ROOT}"
bash tiny-oauth-server/scripts/verify-platform-local-dev-stack.sh
```

若本地环境缺少数据库变量或 MySQL，退出码 `2` 视为环境前置未满足，不视为代码失败。

## 7. 阶段 1.6：流程建模信息架构拆分

### 7.1 目标

阶段 1.6 是阶段 1.5 的收尾，不新增新的流程引擎能力，重点把“模型资产管理”和“BPMN 画布编辑”拆开，避免 `Modeling.vue` 在阶段 2 继续承载列表、版本、业务属性、校验和部署等所有职责。

拆分后的入口语义：

- `流程草稿`：流程模型资产列表、状态、版本、最近更新时间、校验/部署动作入口。
- `流程设计`：打开某个草稿后的 BPMN 画布、属性面板、保存草稿、校验、部署当前模型。

强约束：

- `流程草稿` 读取 `/process/models/**` 设计态资产。
- `流程设计` 通过 `modelId` 打开单个草稿。
- 不在草稿列表中直接持有 bpmn-js 实例。
- 不把运行态部署列表、实例、任务、历史混入草稿列表。
- 保存草稿仍然不得调用 Camunda 部署。

### 7.2 页面结构

推荐把现有“流程建模”入口拆为一个容器页和两个子视图：

```text
views/process/modeling/
├── ProcessModelingWorkspace.vue
├── ProcessDraftList.vue
└── ProcessDesigner.vue
```

若短期不移动文件，也必须达到等价职责边界：

- `ProcessModelingWorkspace.vue` 或当前路由容器只负责 Tab、路由 query、公共状态编排。
- `ProcessDraftList.vue` 只负责草稿列表和草稿级操作。
- `ProcessDesigner.vue` 或现有 `Modeling.vue` 只负责画布设计与当前模型保存。

推荐 Tab：

```text
流程建模
├── 流程草稿
└── 流程设计
```

默认进入 `流程草稿`。

只有以下情况进入 `流程设计`：

- 点击某个草稿的“设计”。
- 新建草稿成功后自动打开该草稿。
- URL 中存在合法 `modelId`。

### 7.3 路由与状态

推荐继续复用当前流程建模路由，使用 query 表达当前视图：

```text
/process/modeling?tab=drafts
/process/modeling?tab=design&modelId=10001
```

平台运行态页仍按已有 platform runtime path 规则保留 scope query。

说明：

- 阶段 1.6 优先复用当前流程建模路由，避免在草稿保存刚落地时同步调整菜单、权限回填、平台 runtime path 与导航守卫。
- `tab` query 只作为阶段 1.6 的低风险过渡方案；不得扩散为新的全局 Tab 规范。
- 若后续仓库路由治理要求 Tab 状态统一收口为 path 子路由，可迁移为 `/process/modeling/drafts` 与 `/process/modeling/design/:modelId`。
- path 子路由迁移必须单独发起，包含菜单/权限 seed、平台 runtime path、旧 query 兼容跳转与前端回归测试。

路由规则：

- `tab` 缺省时等同 `drafts`。
- `tab=design` 但缺少合法 `modelId` 时回退到 `drafts`，并提示先选择草稿。
- 从草稿列表进入设计页时，必须保留当前 active tenant / platform scope query。
- 切换 scope 后必须重新加载草稿列表，不得复用上一 scope 的缓存草稿。
- 设计页存在 dirty 状态时，切换 Tab、切换草稿、离开路由均必须提示。

### 7.4 流程草稿 Tab

`流程草稿` 默认必须按“流程资产”展示，而不是按“流程版本记录”平铺展示。

原因：

- 一个 `modelKey` 可能存在多个草稿 / 校验 / 已部署版本。
- 若默认平铺版本记录，同一流程的多个版本会挤占列表和分页，导致其他流程资产需要翻页才能看到。
- SaaS 工作台首页应优先帮助用户找到“流程”，再进入该流程的版本明细。

展示约束：

- 默认使用平台统一表格风格，主列表一行展示一个流程资产。
- 版本明细展开后也必须保持一行一个版本，不使用卡片块或多行信息块替代表格。
- 名称、Key、更新时间、部署 ID 等长文本在单元格内单行省略，依赖横向滚动承载宽表。

设计态与运行态必须拆开展示：

- `status` 是设计态生命周期，只表达该模型版本在设计中心里的状态。
- `runtimeState` 是运行态状态，只表达该模型版本与线上运行版本的关系。
- `DEPLOYED` 不等于当前线上生效版本；它只表示“这个设计版本曾成功部署过”。
- `CURRENT_RUNTIME` 才表示“该版本是当前线上生效版本”。
- `HISTORICAL_DEPLOYED` 表示“该版本部署过，但已不是当前线上生效版本”。

`runtimeState` 枚举：

| 值 | 含义 |
| --- | --- |
| `NOT_DEPLOYED` | 未部署 |
| `CURRENT_RUNTIME` | 当前运行 |
| `HISTORICAL_DEPLOYED` | 历史已部署 |

展示转义口径：

- 字段名和字段语义固定在代码与 API 契约中，`最新设计版本`、`当前运行版本`、`未部署变更` 不是可配置字典项。
- `status` / `runtimeState` / `validationStatus` 的枚举展示使用系统字典自动转义。
- 阶段 1.6 允许先使用前端本地 fallback 映射；阶段 3 接入平台字典 API 后，切换为“字典异步预热 + 统一自动转义 + fallback”。
- UI 业务判断只能使用原始枚举值，不能使用转义后的 label。

因此默认信息架构为：

```text
流程草稿
├── 流程资产 A（最新版本、状态、校验、更新时间、版本数）
│   └── 版本明细：v3 / v2 / v1
└── 流程资产 B（最新版本、状态、校验、更新时间、版本数）
    └── 版本明细：v2 / v1
```

`流程草稿` 至少提供：

- 流程资产列表。
- 版本明细展开。
- 新建草稿。
- 打开最新版本设计。
- 打开指定版本设计。
- 保存状态展示。
- 校验状态展示。
- 部署状态展示。
- 最近更新时间 / 更新人。
- 模型版本。
- 版本数量。
- scope 标识。

推荐流程资产列表字段：

| 字段 | 来源 | 说明 |
| --- | --- | --- |
| 名称 | `ProcessModelGroup.name` 或 `latestModel.name` | 流程显示名 |
| Key | `ProcessModelGroup.modelKey` | BPMN process id / 模型稳定 key |
| 最新设计版本 | `ProcessModelGroup.latestDesignVersion` | 默认打开 / 校验 / 部署的版本 |
| 当前运行版本 | `ProcessModelGroup.currentRuntimeVersion` | 当前线上生效版本；无则显示未部署 |
| 版本数 | `ProcessModelGroup.versionCount` | 展开后查看版本明细 |
| 最新状态 | `latestModel.status` | `DRAFT` / `VALIDATED` / `DEPLOYED` / `ARCHIVED` |
| 最新校验 | `latestModel.validationStatus` | `NOT_VALIDATED` / `PASSED` / `FAILED` |
| 未部署变更 | `ProcessModelGroup.hasUndeployedChanges` | 最新设计版本是否领先当前运行版本 |
| Scope | `scopeType` + `recordTenantId` | 平台 / 租户 |
| 更新时间 | `ProcessModelGroup.updatedAt` 或最新更新时间 | 排序默认字段 |
| 更新人 | `ProcessModelGroup.updatedBy` 或最新更新人 | 审计辅助 |

推荐版本明细字段：

| 字段 | 来源 | 说明 |
| --- | --- | --- |
| 版本 | `ProcessModel.version` | 具体可打开版本 |
| 设计状态 | `ProcessModel.status` | 该版本生命周期状态 |
| 部署态 | `ProcessModel.runtimeState` | `NOT_DEPLOYED` / `CURRENT_RUNTIME` / `HISTORICAL_DEPLOYED` |
| 校验 | `ProcessModel.validationStatus` | 该版本校验状态 |
| 更新时间 | `ProcessModel.updatedAt` | 该版本更新时间 |
| 更新人 | `ProcessModel.updatedBy` | 该版本更新人 |
| 部署 ID | `ProcessModel.deploymentId` | 已部署版本关联运行态部署 |

首版动作建议：

- `新建草稿`
- 资产行：`设计最新版本`、`校验最新版本`、`部署最新版本`、`展开版本`
- 版本行：`设计`、`校验`、`部署`

API 口径：

- `GET /process/models`：版本记录列表，保留给兼容、导出、调试或后续版本详情页使用。
- `GET /process/models/groups`：流程资产分组列表，作为 `流程草稿` 默认数据源。
- `GET /process/models/{id}`：打开指定版本。

运行态计算口径：

- `runtimeState` 与 `currentRuntimeVersion` 必须由后端计算，前端不得只靠版本号推断。
- 阶段 1.6 可先基于当前 scope 内 `process_model` 的 `deploymentId` / `deployedAt` / `status` 计算当前运行版本。
- 阶段 4 引入回滚、版本迁移、运行态对账后，`CURRENT_RUNTIME` 必须以引擎 runtime / repository 中的当前生效流程定义为准。

分页约束：

- 前端不得在分页后的 `/process/models` 局部结果上自行分组作为默认资产列表。
- 若列表引入分页，必须由后端 `/process/models/groups` 按 `scopeType + tenant_id + model_key` 完成分组、排序和分页。
- 阶段 1.6 若短期仍无分页，可在前端对全量 `/process/models` 做兼容分组，但这只能作为过渡实现，不得作为长期方案。

阶段 1.6 暂不强制实现：

- 归档。
- 删除。
- 复制版本。
- 批量操作。
- SVG 预览缩略图。

这些能力可在阶段 4 生命周期流中补齐。

### 7.5 流程设计 Tab

`流程设计` 是当前 `Modeling.vue` 的收敛方向。

必须显示当前模型上下文：

- 模型名称。
- `modelKey`。
- 版本。
- 状态。
- 校验状态。
- scope。
- dirty 状态。

必须保留当前设计能力：

- BPMN 画布。
- properties panel。
- 导入本地 BPMN。
- 导出 BPMN。
- 导出 SVG。
- 保存草稿。
- 部署。

建议新增：

- 返回草稿列表。
- 当前模型标题栏。
- 保存成功后的更新时间提示。
- 校验入口。

设计页不得承担：

- 全量草稿列表渲染。
- 运行态部署列表渲染。
- 实例、任务、历史数据查询。

### 7.6 组件职责边界

`ProcessModelingWorkspace.vue`：

- 维护当前 `tab`。
- 维护路由 query。
- 处理从草稿列表到设计页的导航。
- 处理 dirty 离开确认。

`ProcessDraftList.vue`：

- 默认调用 `processModelApi.listModelGroups()`。
- 仅在兼容旧接口或版本详情需要时调用 `processModelApi.listModels()`。
- 调用 `processModelApi.createModel()` 创建草稿。
- 调用 `processModelApi.validateModel()`。
- 调用 `processModelApi.deployModel()`。
- 展示失败态、空态和加载态。

`ProcessDesigner.vue` / `Modeling.vue`：

- 接收 `modelId`。
- 调用 `processModelApi.getModel(modelId)` 加载 XML。
- 调用 `processModelApi.saveModel(modelId, payload)` 保存草稿。
- 调用 `createModeler()` 初始化画布。
- 监听 dirty 与 selection。
- 销毁时释放 modeler。

`processModelApi`：

- 继续作为设计态 API 唯一前端入口。
- 方法命名必须以 6.5 的契约为准：`listModels/listModelGroups/createModel/getModel/saveModel/validateModel/deployModel/createVersion/deleteModel`。
- 不在组件中直接拼 `/process/models/**` URL。

### 7.7 UI 状态要求

必须覆盖：

- loading：草稿列表加载、打开草稿、保存草稿、校验、部署。
- empty：当前 scope 下无草稿。
- error：列表加载失败、打开草稿失败、保存失败。
- dirty：设计页存在未保存变更。
- forbidden：403 时展示无权限状态，不吞错误。
- scope mismatch：当前 scope 不可见该模型时回到草稿列表并提示。

按钮状态：

- 未打开模型时，不显示或禁用保存/部署当前模型。
- `DEPLOYED` 模型不得直接保存覆盖；需要 fork 新草稿的能力在阶段 4 补齐，阶段 1.6 可只读或提示。
- `保存草稿` 失败后必须保留 dirty。
- `保存草稿` 成功后必须重置 dirty。

### 7.8 验收标准

必须满足：

- 进入流程建模默认展示 `流程草稿`，而不是直接进入空画布。
- 草稿列表默认展示流程资产分组，而不是版本记录平铺。
- 同一 `modelKey` 的多个版本不得挤占多个资产行。
- 草稿列表默认读取 `/process/models/groups`；若阶段内仍走 `/process/models`，必须证明是全量无分页兼容分组。
- 点击草稿进入 `流程设计`，URL 携带 `tab=design&modelId=...`。
- 展开流程资产后，可打开指定版本，URL 携带该版本 `modelId`。
- 设计页能展示当前模型名称、版本、状态、scope。
- 设计页保存草稿后，回到草稿列表能看到更新时间或状态变化。
- 从设计页切换 Tab / 离开页面时，若 dirty 为 true 必须提示。
- 草稿列表不得初始化 bpmn-js。
- `Modeling.vue` 或最终设计画布组件不再包含草稿列表请求逻辑。
- `Modeling.vue` 若继续保留，建议控制在 500 行以内；若短期无法降到 500 行，必须至少把草稿列表、Tab 容器和 API 编排拆出独立组件 / composable。
- tenant scope 只能列出当前租户草稿；platform scope 只能列出 platform 草稿。

### 7.9 建议验证命令

前端定向测试：

```bash
cd tiny-oauth-server/src/main/webapp
npm run test:unit -- src/api/process.test.ts src/views/process/Modeling.test.ts
npm run type-check
```

若新增独立列表组件，必须补充：

```bash
npm run test:unit -- src/views/process/modeling/ProcessDraftList.test.ts
```

如阶段 1.6 不新增后端 API / DDL，可不重复跑 Liquibase；但如果修改 `/process/models/**` 契约或 DTO，仍需跑后端定向测试。

## 8. 阶段 2：最小业务属性组

### 8.1 目标

在 properties panel 中实现第一批业务可用字段：

- `candidateUsers`
- `candidateGroups`
- `formKey`

这些字段必须写入 BPMN XML，刷新页面后可从 XML 完整恢复。

### 8.2 字段落点

Camunda 7 原生字段：

| UI 字段 | BPMN XML 字段 | 适用元素 |
| --- | --- | --- |
| 候选用户 | `camunda:candidateUsers` | `bpmn:UserTask` |
| 候选组 | `camunda:candidateGroups` | `bpmn:UserTask` |
| 表单 Key | `camunda:formKey` | `bpmn:UserTask` / `bpmn:StartEvent` |

这些字段不使用 `tp:*`。

### 8.3 自定义属性面板模块

建议新增：

```text
tiny-oauth-server/src/main/webapp/src/utils/bpmn/properties/
├── index.ts
├── tinyPlatformPropertiesProvider.ts
├── entries/
│   ├── CandidateUsersEntry.ts
│   ├── CandidateGroupsEntry.ts
│   └── FormKeyEntry.ts
└── propertiesTypes.ts
```

阶段 2 可以先使用静态输入框或简单 select。

阶段 3 再接平台 API。

### 8.4 写入要求

必须使用 bpmn-js 的 `modeling.updateProperties(...)` 或 properties panel 推荐的 command helper 写入，确保：

- 支持撤销/重做。
- 触发 `commandStack.changed`。
- 不直接字符串拼 XML。

### 8.5 恢复要求

导入 XML 后：

- 点击用户任务，属性面板能显示已有 `camunda:candidateUsers`。
- 点击用户任务，属性面板能显示已有 `camunda:candidateGroups`。
- 点击用户任务或开始事件，属性面板能显示已有 `camunda:formKey`。

### 8.6 验收标准

必须满足：

- 新增字段写入 BPMN XML。
- 刷新页面或重新打开草稿后字段完整恢复。
- 撤销/重做可用。
- dirty 状态正确变化。
- 导出的 BPMN 文件包含对应 Camunda 属性。

### 8.7 建议验证命令

```bash
cd tiny-oauth-server/src/main/webapp
npm run test:unit -- src/utils/bpmn/properties src/views/process/Modeling.test.ts
npm run type-check
```

## 9. 阶段 3：平台数据源异步化

### 9.1 目标

把 properties panel 中的平台业务选项改为 API 数据源，并按 active tenant / platform scope 过滤。

### 9.2 必须接 API 的选项

必须来自 API：

- 用户
- 角色 / 用户组
- 表单定义
- 权限码
- 服务任务实现 / connector key
- 业务模块列表

可以本地维护：

- BPMN 固定元素类型
- Camunda 固定布尔项
- async before / async after
- multi instance 固定枚举
- job priority 这类非平台字典项

### 9.3 数据源适配层

建议新增：

```text
tiny-oauth-server/src/main/webapp/src/utils/bpmn/datasource/
├── index.ts
├── bpmnPropertyDataSource.ts
├── dataSourceTypes.ts
└── bpmnPropertyDataSource.test.ts
```

统一封装：

```ts
export interface BpmnPropertyOption {
  label: string
  value: string
  disabled?: boolean
  description?: string
}

export interface BpmnPropertyDataSource {
  listCandidateUsers(query: BpmnDataSourceQuery): Promise<BpmnPropertyOption[]>
  listCandidateGroups(query: BpmnDataSourceQuery): Promise<BpmnPropertyOption[]>
  listFormKeys(query: BpmnDataSourceQuery): Promise<BpmnPropertyOption[]>
  listPermissionCodes(query: BpmnDataSourceQuery): Promise<BpmnPropertyOption[]>
}
```

### 9.4 scope 过滤

所有平台业务选项必须根据当前上下文过滤：

- tenant scope：只返回当前租户可见数据。
- platform scope：只返回平台运行态允许使用的数据。
- 禁止在前端用 query 手动跨租户取数。

前端只传当前已有的 active tenant / platform context，不自行构造越权租户 ID。

### 9.5 失败态 UI

必须覆盖：

- 超时
- 403
- 空数据
- 其他服务端错误

降级策略：

| 场景 | UI 表现 | 是否阻断编辑 |
| --- | --- | --- |
| 超时 | 显示重试入口 | 不阻断已有值展示 |
| 403 | 显示无权限读取选项 | 阻断新选择，不清空已有 XML 值 |
| 空数据 | 显示空状态 | 不阻断手工保留已有值 |
| 500 | 显示加载失败 + 重试 | 不阻断已有值展示 |

### 9.6 缓存策略

建议：

- 同一页面会话内短缓存。
- 切换租户或 platform scope 时清空缓存。
- 权限变化或运行时版本变化后清空缓存。

不得：

- 把权限相关选项长期缓存到 localStorage 作为真相源。

### 9.7 验收标准

必须满足：

- 平台业务下拉项来自 API。
- active tenant / platform scope 切换后数据隔离正确。
- API 失败态有 UI 降级。
- 已写入 XML 的旧值不会因 API 暂时失败而被清空。

### 9.8 建议验证命令

```bash
cd tiny-oauth-server/src/main/webapp
npm run test:unit -- src/utils/bpmn/datasource src/utils/bpmn/properties src/views/process/Modeling.test.ts
npm run type-check
```

## 10. 阶段 4：校验、生命周期、只读 viewer

### 10.1 目标

把阶段 1.5 的草稿保存升级为完整流程模型生命周期，并完成设计态与运行态闭环。

### 10.2 校验门禁

保存前：

- 执行前端本地规则校验。
- 允许保存草稿，但必须记录校验结果。

部署前：

- 执行前端本地规则校验。
- 已保存草稿必须调用 `/process/models/{id}/validate`。
- `/process/validate` 仅用于未保存 XML 的兼容校验或历史入口，不作为设计态主门禁。
- `error` 必须阻断部署。
- `warning` 默认阻断部署，除非产品明确允许二次确认部署。

### 10.3 本地规则建议

第一批本地规则：

- 流程必须有且仅有一个 executable process。
- `process id` 必须符合命名规范。
- 用户任务若需要处理人，则必须配置候选用户或候选组。
- 配置了表单的节点，其 `formKey` 必须存在于平台表单列表。
- 服务任务必须配置平台支持的 connector key 或 delegate expression。
- 平台自定义字段必须使用 `tp:*` namespace。
- tenant scope 下不得配置 platform-only 资源。

### 10.4 错误定位

校验结果结构建议：

```ts
export interface BpmnValidationIssue {
  level: 'error' | 'warning'
  code: string
  message: string
  elementId?: string
  property?: string
}
```

前端必须提供：

- 错误摘要列表。
- 点击错误跳转并选中节点。
- overlay 高亮错误节点。
- warning 与 error 样式区分。

### 10.5 生命周期状态

建议模型状态：

```text
DRAFT -> VALIDATED -> DEPLOYED -> ARCHIVED
```

状态语义：

| 状态 | 说明 | 可编辑 | 可部署 |
| --- | --- | --- | --- |
| `DRAFT` | 草稿 | 是 | 需校验 |
| `VALIDATED` | 已通过校验但未部署 | 是，编辑后回到 DRAFT | 是 |
| `DEPLOYED` | 已部署版本 | 否，需 fork 新草稿 | 否 |
| `ARCHIVED` | 已归档 | 否 | 否 |

`DEPLOYED` 口径：

- 默认不允许 redeploy 同一平台模型版本。
- 任何业务变更都必须 fork 新草稿，形成新的平台模型版本后再部署。
- 如果部署请求已提交但结果处于失败或未知状态，只允许走带审计的运维修复/重试入口，不复用普通部署按钮。
- 运维修复/重试不得修改 `bpmn_xml`、`model_key`、`version` 等设计态字段。
- 运维修复/重试必须记录操作人、原因、原始 deploymentId、重试时间、重试结果。
- 若需要常态化支持 redeploy，必须先新增显式状态和接口，例如 `DEPLOY_FAILED` / `/process/models/{id}/deploy-retry`，不得在 `DEPLOYED` 上隐式重跑。

### 10.6 版本策略

平台模型版本与 Camunda 流程定义版本必须区分：

- `process_model.version`：平台设计模型版本。
- `process_definition.version`：Camunda 运行态流程定义版本。

部署成功后写回：

- `deploymentId`
- `processDefinitionId`
- `processDefinitionKey`
- `processDefinitionVersion`

从已部署版本继续编辑：

- 必须 fork 成新草稿。
- 不得直接修改 `DEPLOYED` 版本。

### 10.7 只读 viewer

新增只读 viewer 能力，用于：

- 流程定义详情。
- 部署详情。
- 流程实例详情。
- 历史版本详情。

建议新增：

```text
tiny-oauth-server/src/main/webapp/src/utils/bpmn/viewer/
├── index.ts
├── createViewer.ts
├── viewerOverlays.ts
└── createViewer.test.ts
```

运行态叠加信息至少包括：

- 当前 token / active activity
- 已完成节点
- 异常节点

可选扩展：

- 节点耗时
- 处理人
- SLA 状态
- 重试次数
- incident 信息

### 10.8 验收标准

必须满足：

- 保存草稿与部署流程分离。
- 部署前执行本地与后端双层校验。
- 错误能定位到节点。
- 模型有明确状态流。
- 已部署版本只读，继续编辑必须 fork 新草稿。
- 只读 viewer 能叠加当前 token、完成节点、异常节点。

### 10.9 建议验证命令

前端：

```bash
cd tiny-oauth-server/src/main/webapp
npm run test:unit -- src/utils/bpmn src/views/process
npm run type-check
```

后端：

```bash
cd "${REPO_ROOT}"
./mvnw -pl tiny-oauth-server -Dtest=ProcessModelControllerTest,ProcessControllerTest test
```

全链路本地门禁：

```bash
cd "${REPO_ROOT}"
bash tiny-oauth-server/scripts/verify-platform-local-dev-stack.sh
```

## 11. 平台扩展字段 schema

### 11.1 namespace 决策

推荐：

```xml
xmlns:tp="https://tiny-platform.local/schema/bpmn/tp/1.0"
```

不得：

- 使用无前缀自定义字段。
- 把平台自定义字段塞进 Camunda 原生字段。
- 用字符串注释承载结构化平台配置。

### 11.2 第一批平台字段建议

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `tp:businessModule` | string | 业务模块 |
| `tp:permissionCode` | string | 节点操作所需权限码 |
| `tp:approvalPolicy` | string | 审批策略 |
| `tp:scopeType` | string | `PLATFORM` / `TENANT` |
| `tp:formVersion` | string/int | 表单版本 |
| `tp:connectorKey` | string | 平台服务任务实现 key |

阶段 2 不强制实现这些字段。

阶段 3 或阶段 4 开始引入时，必须同步：

- moddle descriptor
- properties panel
- XML 导入恢复
- 校验规则
- 单测

### 11.3 moddle 描述符

推荐新增：

```text
tiny-oauth-server/src/main/webapp/src/utils/bpmn/moddle/
├── tiny-platform.json
├── index.ts
└── tinyPlatformModdle.test.ts
```

`tiny-platform.json` 必须显式声明：

- `name`
- `uri`
- `prefix`
- `types`
- 字段类型
- 字段是否为属性或 `extensionElements` 子元素

示例骨架：

```json
{
  "name": "TinyPlatform",
  "uri": "https://tiny-platform.local/schema/bpmn/tp/1.0",
  "prefix": "tp",
  "xml": {
    "tagAlias": "lowerCase"
  },
  "types": [
    {
      "name": "NodeConfig",
      "superClass": ["Element"],
      "properties": [
        { "name": "permissionCode", "type": "String", "isAttr": true },
        { "name": "approvalPolicy", "type": "String", "isAttr": true },
        { "name": "connectorKey", "type": "String", "isAttr": true }
      ]
    }
  ]
}
```

实现时优先把平台结构化配置写入 `bpmn:extensionElements` 下的 `tp:*` 元素；只有天然属于 BPMN/Camunda 原生语义的字段，才写 Camunda 原生属性。

### 11.4 schema 版本与兼容策略

`tp` namespace 第一版固定为：

```text
https://tiny-platform.local/schema/bpmn/tp/1.0
```

兼容规则：

- 小版本新增字段时，不修改 namespace；旧客户端忽略未知字段，新客户端保留未知字段。
- 删除字段必须先标记 deprecated，不得直接让导入失败。
- 读取旧 XML 时，未知 `tp:*` 字段必须保留在 XML 中，不得在保存时丢弃。
- 不能识别的 `tp:*` 字段在属性面板中可不展示，但保存 XML 时必须 round-trip 保留。
- 只有出现破坏性结构变化时才新增 namespace，例如 `https://tiny-platform.local/schema/bpmn/tp/2.0`。
- 新 namespace 出现时，导入逻辑必须同时支持 `tp/1.0` 和 `tp/2.0`，并提供迁移校验提示。

阶段 3 或阶段 4 一旦引入 `tp:*` 字段，验收必须增加：

- `tp:*` 字段导入不丢失。
- 未知 `tp:*` 字段保存后仍存在。
- 旧 namespace XML 可打开。
- schema 迁移 warning 可展示。

## 12. 后端落地清单

### 12.1 新增包建议

```text
tiny-oauth-server/src/main/java/com/tiny/platform/application/oauth/workflow/model/
├── ProcessModelController.java
├── ProcessModelRequest.java
├── ProcessModelResponse.java
├── ProcessModelValidationResponse.java
└── ProcessModelDeployResponse.java
```

```text
tiny-oauth-server/src/main/java/com/tiny/platform/infrastructure/workflow/model/
├── ProcessModelEntity.java
├── ProcessModelRepository.java
└── ProcessModelService.java
```

如当前项目已有更合适的分层规范，应按现有分层落地。

边界要求：

- `ProcessModelController` 只承载设计态模型控制面。
- `ProcessController` 继续承载现有运行态/兼容控制面。
- 阶段 1.5 不迁移实例、任务、历史、引擎信息接口。
- 新增草稿、版本、模型校验、模型部署编排不得落入 `ProcessController`。
- 若后续重构 `/process/deploy*`，必须先写兼容迁移计划，不得在同一接口中同时承担“直接部署 XML”和“保存/部署模型”两种职责。

### 12.2 必须保留的安全约束

- 所有模型读 API 需要 `workflow:console:view`。
- 所有模型写 API 需要 `workflow:console:config`。
- 所有写 API 接 idempotency。
- tenant scope 查询必须绑定当前 active tenant。
- platform scope 不允许通过 `recordTenantId` 跨查租户模型。

### 12.3 Liquibase 要求

涉及新增表时必须：

- 新增 `db/changelog/*.yaml`。
- include 到 `db.changelog-master.yaml`。
- 执行真实 SpringLiquibase / 应用启动验证。

不能只跑单测后宣称完成。

## 13. 前端落地清单

### 13.1 新增或改造模块

必须新增：

```text
src/utils/bpmn/modeler/**
```

阶段 1.5 建议新增：

```text
src/api/process.ts                # 增加 processModelApi
src/views/process/Modeling.vue    # 接入草稿保存
```

阶段 1.6 建议新增或拆分：

```text
src/views/process/modeling/ProcessModelingWorkspace.vue  # 流程建模 Tab 容器
src/views/process/modeling/ProcessDraftList.vue          # 流程草稿列表
src/views/process/modeling/ProcessDesigner.vue           # 流程设计画布
```

如短期继续复用 `src/views/process/Modeling.vue`，必须保证其职责已收敛为设计画布，草稿列表和 Tab 编排不得继续堆入同一组件。

阶段 2 建议新增：

```text
src/utils/bpmn/properties/**
```

阶段 3 建议新增：

```text
src/utils/bpmn/datasource/**
```

阶段 4 建议新增：

```text
src/utils/bpmn/viewer/**
```

### 13.2 UI 行为要求

- 页面首次进入可新建空草稿或打开已有模型。
- 未保存修改离开页面必须提醒。
- 保存草稿成功后显示最近保存时间。
- 部署成功后显示 deploymentId，并提供跳转部署列表/定义列表。
- API 失败时不得清空当前画布。
- 属性下拉 API 失败时不得清空 XML 中已有值。

### 13.3 样式要求

- 设计器是工作台，不做营销式 hero。
- 主区域保持画布优先。
- 属性面板固定宽度，可滚动。
- 错误摘要列表不得遮挡画布主要操作。
- mobile 如暂不支持，应明确给出降级提示，不做破碎布局。

## 14. 测试策略

### 14.1 前端单测

必须覆盖：

- `createModeler()` 创建与销毁。
- `importXml()` 成功与失败。
- `saveXml()` 返回 XML。
- `saveSvg()` 返回 SVG。
- `commandStack.changed` dirty 状态。
- `selection.changed` 当前元素上下文。
- 流程草稿 Tab 默认展示。
- 草稿列表加载、空态、失败态。
- 草稿点击后进入 `tab=design&modelId=...`。
- 设计页 dirty 状态下切换 Tab / 离开路由提醒。
- properties 字段写入与恢复。
- API 失败态 UI 降级。
- dirty 离开提醒。

### 14.1.1 阶段 1.6 最小 E2E 冒烟

阶段 1.6 拆分完成后，至少保留一条 real-link 冒烟路径：

```text
流程草稿列表
  -> 新建或打开一个草稿
  -> 进入流程设计
  -> 修改 BPMN（例如调整名称或新增一个节点）
  -> 保存草稿
  -> 返回流程草稿列表
  -> 看到该草稿 updatedAt / 状态发生变化
  -> 再次打开该草稿
  -> BPMN XML 能恢复刚才的修改
```

验收要求：

- 该用例必须经过真实 `processModelApi` 请求，不使用纯前端 mock 代替。
- 如果本地 real-link 因登录、DB 或密钥前置缺失无法执行，必须记录为环境前置缺口。
- 该用例优先使用当前 active tenant；platform scope 需另补一条只读或保存冒烟，确保 platform / tenant 不串数据。

### 14.2 后端单测 / 集成测试

必须覆盖：

- 创建模型。
- 保存草稿。
- 查询模型列表按 scope 隔离。
- 查询模型详情按权限与租户隔离。
- 校验模型。
- 部署模型。
- 已部署模型 fork 新草稿。
- 403 / 无租户上下文 / platform scope 混用。

### 14.3 启动验证

如新增 Liquibase：

- 必须跑真实启动或项目已有本地门禁。
- 无数据库环境时明确标注“环境前置未满足”，不得写“已完成启动验证”。

## 15. 回滚策略

阶段 1：

- 可回滚到原 `Modeling.vue` 直接初始化 bpmn-js。

阶段 1.5：

- 新增 `/process/models` 不影响现有 `/process/deploy`。
- 若草稿模型不可用，保留当前直接部署入口作为临时 fallback。

阶段 1.6：

- 可回滚为单页 `Modeling.vue` 直接进入设计画布。
- 回滚时必须保留 `/process/models/**` 与草稿保存能力。
- 不得回滚为“保存即部署”的旧语义。

阶段 2：

- 自定义 properties provider 可从 additionalModules 中移除。
- 已写入的 Camunda 原生属性仍可被 Camunda 识别。

阶段 3：

- API 数据源失败时可降级为手工输入旧值。

阶段 4：

- viewer 叠加层可独立关闭，不影响 BPMN XML 展示。

## 16. 开放问题

落地前必须确认：

1. 草稿模型 API 是否最终采用 `/process/models`？
   - 推荐采用。
   - 不推荐复用部署表表达未部署版本。

2. Tiny Platform BPMN 扩展字段 namespace 是否固定为 `tp:*`？
   - 推荐固定。
   - 推荐 namespace：`https://tiny-platform.local/schema/bpmn/tp/1.0`

3. platform scope 与 tenant scope 的流程模型是否物理隔离？
   - 短期可按当前后端口径做逻辑隔离。
   - 新增 `process_model` 时必须至少有 `scope_type` 与 `tenant_id`。
   - 是否拆表可留到模型库容量和合规要求明确后再决策。

4. warning 是否允许部署？
   - 默认建议不允许直接部署。
   - 如允许，必须二次确认并记录审计。

5. 自动保存是否阶段 1.5 同步上线？
   - 建议先预留接口。
   - 第一版可以只做手动保存和离开提醒。

## 17. 推荐实施顺序

严格按以下顺序推进：

1. 新增 `src/utils/bpmn/modeler/**`。
2. 给 modeler 内核补单测。
3. 改造 `Modeling.vue` 使用内核。
4. 验证现有导入、导出、部署不回退。
5. 设计并新增 `process_model` 表。
6. 新增 `/process/models` 后端 API。
7. 明确 `/process/models/**` 与现有 `/process/**` 边界，补控制器边界测试。
8. 前端新增 `processModelApi`。
9. `Modeling.vue` 增加保存草稿、打开草稿、离开提醒。
10. 拆分流程建模入口为 `流程草稿` / `流程设计`。
11. 草稿列表接入 `/process/models`，设计页通过 `modelId` 打开草稿。
12. 补 dirty 切 Tab / 离开路由提醒。
13. 新增最小 properties provider。
14. 写入并恢复 `candidateUsers/candidateGroups/formKey`。
15. 接入平台数据源。
16. 补 API 失败态和 scope 切换测试。
17. 新增本地校验规则。
18. 对接后端校验并展示错误摘要。
19. 增加 overlay 错误定位。
20. 增加版本状态流。
21. 增加只读 viewer 和运行态叠加。
22. 跑前端定向测试、后端定向测试、真实启动验证。

## 18. 阶段完成定义

阶段 1 完成定义：

- 内核存在。
- 页面接入内核。
- dirty 与 selection 可用。
- 单测通过。

阶段 1.5 完成定义：

- 草稿可创建、保存、打开。
- 保存不部署。
- 部署单独触发。
- scope 隔离可测。

阶段 1.6 完成定义：

- 流程建模默认进入流程草稿。
- 流程草稿与流程设计职责拆分。
- 草稿列表不初始化 bpmn-js。
- 设计页通过 `modelId` 加载草稿。
- dirty 切 Tab / 离开路由有提示。
- 草稿列表与设计页均遵守当前 scope。

阶段 2 完成定义：

- 三个最小业务属性可编辑。
- XML 可持久化。
- 刷新可恢复。
- 撤销/重做可用。

阶段 3 完成定义：

- 平台业务选项来自 API。
- API 失败态可用。
- scope 过滤正确。

阶段 4 完成定义：

- 本地和后端双层校验可用。
- 错误可定位。
- 生命周期状态流可用。
- 已部署版本只读。
- viewer 可叠加运行态信息。
