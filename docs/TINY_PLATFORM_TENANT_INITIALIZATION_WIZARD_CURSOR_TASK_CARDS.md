# Tiny Platform 租户初始化向导 Cursor 任务卡

> 状态：可执行任务卡
> 适用范围：`tiny-oauth-server` 租户控制面 / 新建租户向导 / 租户初始化 precheck / 前端步骤编排
> 目标：把“新建租户从单表单升级为受控初始化向导”拆成小步任务卡交给 Cursor 执行，由 Codex 负责审计
> 当前态真相源：`AGENTS.md`、`docs/TINY_PLATFORM_TENANT_GOVERNANCE.md` §4.6、`docs/TINY_PLATFORM_TESTING_PLAYBOOK.md`、`.agent/src/rules/90-tiny-platform.rules.md`、`.agent/src/rules/94-tiny-platform-tenant-governance.rules.md`

> 当前态摘要（2026-04-19）：
> - `POST /sys/tenants` 已承担“创建租户 + 平台模板派生 + 初始管理员创建 + 初始管理员角色赋权”的一次性编排职责
> - `POST /sys/tenants/platform-template/initialize` 只负责 **`tenant_id IS NULL` 平台模板缺失时** 的全局补齐，不是单租户初始化步骤
> - 前端 create/edit 已分流：create 走 `TenantCreateWizard`，edit 走 `TenantForm`
> - `POST /sys/tenants/precheck` 已接入确认步骤，支持阻断项、warning、初始化摘要展示
> - 最终创建由 wizard 一次性调用 `POST /sys/tenants`，并在 wizard 内展示成功/失败结果页
> - create drawer 已禁用 close icon / mask / Esc；结果页进入租户详情会带 `query.from` 返回上下文
> - 结果页 3 个治理入口已细化为 `section=overview|permission-summary|template-diff`，并复用 `/platform/tenants/:id` 做分区聚焦落点

---

## 1. 使用方式

一次只给 Cursor 一张任务卡，不要并行发两张。

每张卡都要求 Cursor：

- 先阅读本文件列出的固定约束与设计裁决；
- 再按本卡给出的文件范围、验收标准和限制直接实施；
- 不要把本卡擅自扩大成“顺手把后面几张也做了”；
- 若本卡新增或修改 `/sys/**` 控制面端点、`api_endpoint` / `menu_permission_requirement` / `role_permission` 回填、`db/changelog/**` 或 `db.changelog-master.yaml`，完成前必须真实执行一次 `SpringLiquibase` / 应用启动验证；
- 完成后必须返回：
  - 修改文件清单
  - 执行命令
  - 测试结果
  - 剩余风险

Codex 的职责不是代替 Cursor 实施，而是按本文件末尾的审计口径做结果审计。

---

## 2. 固定约束

以下约束对所有任务卡都成立。

### 2.1 必读规则与文档

- `AGENTS.md`
- `docs/TINY_PLATFORM_TENANT_GOVERNANCE.md`
- `docs/TINY_PLATFORM_TESTING_PLAYBOOK.md`
- `.agent/src/rules/50-testing.rules.md`
- `.agent/src/rules/90-tiny-platform.rules.md`
- `.agent/src/rules/94-tiny-platform-tenant-governance.rules.md`

### 2.2 当前已经完成的基线

下列内容已在主线工作区落地，本轮任务卡不得回退：

- `/system/tenant` 平台侧运行时 `ui_action` 载体已补齐，`platform_admin` 可见“新建租户”动作
- 租户控制面 `create/update/freeze/unfreeze/decommission/delete` 主链已经存在
- `POST /sys/tenants/platform-template/initialize` 与 `GET /sys/tenants/{id}/platform-template/diff` 已明确职责边界
- 前端 create/edit 已分流：create 由 `TenantCreateWizard` 承担完整向导链路，`TenantForm.vue` 仅保留 edit / 基础信息子块语义

### 2.3 本轮禁止事项

- 不要把 `/sys/tenants/platform-template/initialize` 混进新建租户步骤条
- 不要在步骤切换时偷偷创建租户、管理员或平台模板副本
- 不要在“新建租户”里直接暴露完整角色树 / 菜单树 / 权限树做手工授权
- 不要为了向导引入新的租户生命周期状态或异步任务中心，除非卡片明确要求
- 不要把 edit 流程和 create 向导强行揉成同一套状态机

### 2.4 租户初始化向导任务卡责任边界与最低验证

| 卡片 | 本卡必须负责 | 本卡不负责 | Liquibase 责任边界 | 最低验证门禁 |
| --- | --- | --- | --- | --- |
| `CARD-TW-01` | 新建入口改为 `TenantCreateWizard` 壳、步骤骨架、`TenantForm` 角色重划分（create vs edit） | precheck 后端、最终提交结果页、平台模板语义改造 | 默认不要求；若顺手新增菜单/权限/端点注册则同卡承担真实迁移验证 | 前端定向测试 + 构建 |
| `CARD-TW-02` | `POST /sys/tenants/precheck` 后端 contract、DTO、service dry-run、统一守卫回填、real-controller 测试 | 最终创建写链语义改造、前端步骤渲染、结果页 | 只要新增/修改 `api_endpoint` / requirement / master include / changeset，就必须同卡完成真实 `SpringLiquibase` / 启动验证 | 后端定向测试 + real-controller 测试 + 真实迁移/启动验证 |
| `CARD-TW-03` | 向导前端接入 precheck、确认页/摘要页、阻断与 warning 展示、步骤状态机 | 最终提交结果页、create 后端响应扩展 | 默认不要求；若新增菜单、权限或新端点调用链依赖的初始化数据，则同卡承担真实迁移验证 | 前端 API/页面测试 + 构建 |
| `CARD-TW-04` | 最终提交与结果页：预检查通过后一次性调用 `POST /sys/tenants`、提交态、成功/失败结果页 | 平台模板初始化、自定义角色树授权、异步任务编排 | 默认不要求；若为结果页新增后端 endpoint/permission/changeset，则责任不外推到下一卡 | 前端 API/页面测试 + 构建；如触及后端新 contract 再补后端定向测试 |
| `CARD-TW-05` | 文档/提示词/残余旧入口清理、回归收口、最低 smoke 说明 | 新业务语义扩展、二期异步化、审批流 | 默认不要求；若卡内顺手触发迁移改动，则同卡验证 | 文档同步 + 前端回归 + 必要 smoke 记录 |
| `CARD-TW-06` | 结果页治理入口细化：3 个按钮带 `section` 落点，`TenantDetail` 按分区聚焦并保留 `query.from` 返回上下文 | 新后端 contract、新路由体系、异步初始化扩展 | 默认不要求；禁止把前端分区落点改造外推为后端迁移卡 | 前端定向测试 + 构建 |

责任划分补充：

- 后端卡如果新增了新 endpoint、changeset、requirement 或统一守卫数据，但没有做真实 `SpringLiquibase` / 启动验证，只能算“代码已改、交付未完成”。
- 前端卡如果因为“页面可用”新增了菜单、权限或隐藏依赖的 lookup / endpoint，也不能把后端初始化链和迁移责任口头外推给下一卡。
- 每张卡都必须显式说明“本卡负责什么、不负责什么”；不能把 precheck、最终提交、结果页、平台模板补齐、异步化等边界默认留空。

### 2.5 Liquibase 真跑门禁

下列改动一旦出现，就进入“必须真跑迁移/启动验证”的范围：

- 新增或修改 `db/changelog/**`
- 修改 `db.changelog-master.yaml`
- 新增或修改 `api_endpoint` / `api_endpoint_permission_requirement`
- 新增或修改 `menu_permission_requirement`
- 新增或修改 `role_permission`
- 新增权限 seed、菜单 seed、DDL、索引、唯一键、nullable、generated column

最小完成条件：

- 至少跑通一次真实 `SpringLiquibase` 执行路径，证明 changeset 会被应用加载；
- 报告中必须说明用了哪条命令、是 existing DB 还是 fresh DB 路径、实际是否越过 Liquibase；
- 若未跑，交付状态只能写“阻塞/未验证”，不得写“已完成，待用户启动验证”。

---

## 3. 执行顺序

建议按以下顺序交给 Cursor：

1. `CARD-TW-01` 新建入口升级为 `TenantCreateWizard` 壳与步骤骨架
2. `CARD-TW-02` 租户初始化 precheck 后端 contract
3. `CARD-TW-03` 向导前端接入 precheck 与确认页
4. `CARD-TW-04` 最终提交与结果页收口
5. `CARD-TW-05` 文档、回归与旧入口收口
6. `CARD-TW-06` 结果页治理入口细化与租户详情分区聚焦

执行顺序说明：

- `CARD-TW-01` 先把 create 与 edit 形态拆开，避免后面 precheck/result 都继续堆在单表单里
- `CARD-TW-02` 先补后端 dry-run，再让前端做确认页与阻断信息展示
- `CARD-TW-03` 和 `CARD-TW-04` 分开，是为了避免“向导骨架 + precheck + 最终提交 + 结果页”一次膨胀成超大卡
- `CARD-TW-05` 是收口卡，用于清理旧假设、防止文档与执行入口继续漂移
- `CARD-TW-06` 在不新增后端 contract 的前提下，把结果页治理入口从“同一默认落点”收口成“同页不同分区落点”

---

## 4. 任务卡

### CARD-TW-01 新建入口升级为 `TenantCreateWizard` 壳与步骤骨架

**目标**

把租户列表页的“新建租户”从当前单表单 create 模式升级为专用 `TenantCreateWizard` 容器，但暂不接后端 precheck。

**为什么先做**

如果 create 与 edit 还共用同一块单表单状态，后续 precheck、确认页和结果页都会继续往 `TenantForm.vue` 里堆，组件边界会越来越乱。

**范围**

- `tiny-oauth-server/src/main/webapp/src/views/tenant/Tenant.vue`
- `tiny-oauth-server/src/main/webapp/src/views/tenant/TenantForm.vue`
- 新增 `tiny-oauth-server/src/main/webapp/src/views/tenant/TenantCreateWizard.vue`
- 与租户 create 入口直接相关的前端测试

**明确要求**

- “新建租户”改为打开 `TenantCreateWizard`
- edit 仍保留现有 edit 表单链，不强行并入向导
- wizard 至少包含 4 个显式步骤占位：
  - 基础信息
  - 初始化策略
  - 初始管理员
  - 确认
- 当前卡不接后端 precheck，不做最终结果页
- 不改后端 create 语义

**验收标准**

- `Tenant.vue` 中 create 与 edit 入口不再共用同一块表单状态
- `TenantCreateWizard` 已成为 create 主入口容器
- `TenantForm` 不再继续承担 create 全流程状态机
- 页面测试覆盖“点击新建 -> 打开 wizard 壳”的新行为

**建议验证**

- `node ./node_modules/vitest/vitest.mjs run src/views/tenant/Tenant.test.ts`
- `npm --prefix tiny-oauth-server/src/main/webapp run build`

---

### CARD-TW-02 租户初始化 precheck 后端 contract

**目标**

新增 `POST /sys/tenants/precheck`，对步骤 1~3 聚合数据做 dry-run / precheck，返回阻断项、警告项和初始化摘要。

**为什么排第二**

只有先有稳定的后端 precheck contract，前端确认页和最终提交前阻断才不会变成猜测文案。

**范围**

- `tiny-oauth-server/src/main/java/com/tiny/platform/application/controller/tenant/TenantController.java`
- `tiny-oauth-server/src/main/java/com/tiny/platform/infrastructure/tenant/service/**`
- `tiny-oauth-server/src/main/java/com/tiny/platform/infrastructure/tenant/dto/**`
- 若需要统一守卫注册：`db/changelog/**`、`db.changelog-master.yaml`
- 与 precheck 直接相关的后端测试

**明确要求**

- 新增 `POST /sys/tenants/precheck`
- precheck 必须是 dry-run：
  - 不创建租户
  - 不创建管理员
  - 不写平台模板副本
- 至少覆盖：
  - `code` / `domain` 唯一性
  - `maxUsers` / `maxStorageGb` 基本合法性
  - 平台模板是否已就绪
  - 初始管理员用户名/邮箱/手机号冲突检查（若当前链路已有约束）
  - 默认角色/菜单/权限模板摘要
- 返回结构至少包含：
  - `ok`
  - `blockingIssues`
  - `warnings`
  - `initializationSummary`
- 若新增控制面 endpoint 导致统一守卫要登记 `api_endpoint`，同卡必须补齐 requirement 回填和 real-controller 测试

**本卡不负责**

- 不改 `POST /sys/tenants` 最终 create 语义
- 不做前端步骤页和结果页
- 不引入新的生命周期状态或异步初始化任务

**验收标准**

- precheck 失败时能返回明确阻断原因，而不是靠 message 字符串猜测
- precheck 成功时能返回初始化摘要供前端确认页展示
- 若触及 `api_endpoint` / changeset，必须有真实 `SpringLiquibase` / 启动验证证据

**建议验证**

- `mvn -q -Dtest=TenantControllerTest,TenantServiceImplTest test`
- real-controller 守卫测试
- `mvn -q -DskipTests spring-boot:run -Dspring-boot.run.arguments=--server.port=0`

---

### CARD-TW-03 向导前端接入 precheck 与确认页

**目标**

让 `TenantCreateWizard` 在“确认”步骤调用 `POST /sys/tenants/precheck`，渲染阻断项、warning 和初始化摘要。

**为什么排第三**

先把 precheck 接通，用户才能在最终提交前看到“将创建什么、哪里会被阻断”，而不是最后点提交才第一次发现错误。

**范围**

- `tiny-oauth-server/src/main/webapp/src/views/tenant/TenantCreateWizard.vue`
- `tiny-oauth-server/src/main/webapp/src/api/tenant.ts`
- `tiny-oauth-server/src/main/webapp/src/views/tenant/*.test.ts`

**明确要求**

- 向导在进入确认步骤或点击“开始预检查”时调用 precheck
- `blockingIssues` 存在时，必须阻断最终提交按钮
- `warnings` 与 `initializationSummary` 必须可读展示
- 不在本卡实现最终提交成功/失败结果页
- 不新增后端逻辑分支去迎合前端临时状态

**本卡不负责**

- 不改 `POST /sys/tenants` contract
- 不引入 create 结果页
- 不改平台模板初始化按钮语义

**验收标准**

- precheck 成功/失败/warning 三类分支都有 UI 反馈
- 没有通过 precheck 时不能触发最终提交
- 页面测试锁定上述行为

**建议验证**

- `node ./node_modules/vitest/vitest.mjs run src/views/tenant/Tenant.test.ts`
- `npm --prefix tiny-oauth-server/src/main/webapp run build`

---

### CARD-TW-04 最终提交与结果页收口

**目标**

在 precheck 通过后，由向导一次性调用 `POST /sys/tenants` 完成真正创建，并输出明确结果页。

**为什么单独成卡**

最终提交涉及提交态、失败态、成功态和结果页，如果和 wizard 骨架、precheck 一起做，极易把责任混在一起。

**范围**

- `tiny-oauth-server/src/main/webapp/src/views/tenant/TenantCreateWizard.vue`
- `tiny-oauth-server/src/main/webapp/src/api/tenant.ts`
- 与 create 成功/失败流程直接相关的前端测试

**明确要求**

- 最终提交只能在 precheck 通过后触发
- 调用链必须只有一次正式 `POST /sys/tenants`
- 结果页至少展示：
  - 新租户 ID / 名称 / 编码
  - 初始管理员账号标识（不回显明文密码）
  - 初始化摘要
  - 可跳转入口（详情 / 权限摘要 / 模板 diff）
- 提交失败时必须停留在结果/错误态，不静默关闭

**本卡不负责**

- 不把 `/platform-template/initialize` 混进结果页动作
- 不扩展到异步任务编排或“后台继续初始化”
- 不改租户创建的后端业务语义，除非为了稳定显示结果必须补充极小 contract

**验收标准**

- 用户能完成“填写 -> precheck -> 最终提交 -> 查看结果”的闭环
- 成功/失败状态明确，不泄露明文密码
- 没有因为结果页需要而引入新的隐式写操作

**建议验证**

- `node ./node_modules/vitest/vitest.mjs run src/views/tenant/Tenant.test.ts`
- `npm --prefix tiny-oauth-server/src/main/webapp run build`

---

### CARD-TW-05 文档、回归与旧入口收口

**目标**

收口文档、提示词、残余旧入口与测试假设，防止仓库继续把“新建租户 = 单表单 create”当成当前态。

**范围**

- `docs/TINY_PLATFORM_TENANT_GOVERNANCE.md`
- 本任务卡文档
- 相关前端测试、提示词、说明文档

**明确要求**

- 同步更新租户治理文档中的当前态描述
- 如 `TenantForm` 仍保留 create 残余注释/文案/测试假设，本卡负责清掉
- 补一条最小 smoke 说明，明确“create 走 wizard、edit 保留表单”的现状
- 明确写清以下当前态约束：
  - precheck 是 `POST /sys/tenants/precheck` dry-run
  - 最终提交是 wizard 内一次性 `POST /sys/tenants`
  - 成功/失败结果页已存在，成功后不自动关闭
  - create drawer 禁用 close icon / mask / Esc 关闭
  - 结果页进入租户详情时保留 `query.from`

**本卡不负责**

- 不新增业务语义
- 不引入新的后端 endpoint
- 不扩展二期异步化 / 审批流 / 自定义角色树授权

**验收标准**

- 文档、提示词、测试与当前实现一致
- 仓库内不再把 create 当前态写成“单表单 create”
- 剩余未做项明确标为后续，不留模糊口径

**建议验证**

- 定向前端测试
- 文档 diff 自检

---

### CARD-TW-06 结果页治理入口细化与租户详情分区聚焦

**目标**

把 `TenantCreateWizard` 成功结果页上的 3 个入口细化为明确治理分区落点，同时继续复用现有 `/platform/tenants/:id` 详情页与现有后端 API。

**范围**

- `tiny-oauth-server/src/main/webapp/src/views/tenant/TenantCreateWizard.vue`
- `tiny-oauth-server/src/main/webapp/src/views/platform/tenants/TenantDetail.vue`
- 对应前端测试
- `docs/TINY_PLATFORM_TENANT_GOVERNANCE.md`
- 本任务卡文档

**明确要求**

- 结果页入口参数改为：
  - 查看租户详情：`section=overview`
  - 查看权限摘要：`section=permission-summary`
  - 查看模板差异：`section=template-diff`
- `TenantDetail` 读取 `route.query.section` 并做 fail-safe：
  - 合法值：`overview` / `permission-summary` / `template-diff`
  - 非法值回落到 `overview`
- `TenantDetail` 提供轻量分区导航与可见聚焦状态，首次进入或 `query.section` 变化时自动聚焦对应分区
- 保留 `query.from` 返回上下文，不破坏现有返回按钮行为
- 不新增后端 endpoint，不新增 Liquibase / `api_endpoint` 回填，不拆新路由

**本卡不负责**

- 不改 `POST /sys/tenants` / `POST /sys/tenants/precheck` 语义
- 不新增异步初始化、审批流、任务中心
- 不把 `TenantDetail` 重构为全新页面体系
- 不治理与本卡无关的既有 TS 类型债

**验收标准**

- 结果页 3 个按钮不再共用同一个默认落点
- 租户详情页可根据 `section` 呈现明确分区聚焦，非法值安全回落
- 返回按钮保持 `query.from` 上下文返回
- 前端测试覆盖：入口参数分流、分区聚焦、非法回落、返回上下文不受影响

**建议验证**

- `node ./node_modules/vitest/vitest.mjs run src/views/tenant/TenantCreateWizard.test.ts src/views/platform/tenants/TenantDetail.test.ts src/views/tenant/Tenant.test.ts`
- `./node_modules/.bin/vite build`

---

## 5. Codex 审计口径

对每张卡，Codex 至少检查以下内容：

- 是否真的只实现了本卡范围，而没有偷偷扩大到下一卡
- 是否明确写清“本卡负责什么、不负责什么”
- 若触及统一守卫/changeset，是否补齐了 `api_endpoint` / requirement / `SpringLiquibase` 验证
- 是否出现了“步骤切换提前落库”的隐式写入
- 是否把 `/platform-template/initialize` 错误地混入租户创建向导
- 是否把 precheck、最终提交、结果页、异步化混成一个超大卡，导致边界失真
