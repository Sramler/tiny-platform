# Tiny Platform 平台角色治理 Cursor 任务卡

> 状态：可执行任务卡  
> 适用范围：`tiny-oauth-server` 平台角色控制面 / 平台用户角色绑定 / 平台 RBAC3 / 平台角色审批  
> 目标：把“平台角色治理闭环”拆成小步任务卡交给 Cursor 执行，由 Codex 负责审计  
> 当前态真相源：`AGENTS.md`、`docs/TINY_PLATFORM_AUTHORIZATION_TASK_LIST.md`、`docs/TINY_PLATFORM_PLATFORM_RUNTIME_RBAC3_GOVERNANCE_DESIGN.md`、`docs/TINY_PLATFORM_PLATFORM_USER_MANAGEMENT_AND_IMPERSONATION_DESIGN.md`

> 当前态摘要（2026-04-17）：
> - 平台登录已要求 `platform_user_profile.status=ACTIVE` 且存在 `role_assignment(scope_type=PLATFORM, tenant_id IS NULL, scope_id IS NULL)`
> - `tenant_id IS NULL + role_level=PLATFORM` 已统一口径为“平台角色”；前端主入口为 `/platform/roles`，兼容保留 `/platform/template-roles`
> - `/platform/users` 后端已补平台角色读写接口：`GET/PUT /platform/users/{id}/roles`
> - `/sys/role-constraints/*` 仍是 tenant-only，不能覆盖平台 RBAC3
> - `findEffectiveRoleIdsForUserInPlatform(...)` 尚未展开 `role_hierarchy`

> 统一设计口径（本文件固定前提）：
> - 平台侧只有一套“平台角色”
> - `tenant_id IS NULL + role_level=PLATFORM` 就是平台角色
> - “模板”只是平台角色在租户 bootstrap 场景中的用途，不是第二套角色类型
> - **禁止**在本轮任务卡中引入 `role_usage=TEMPLATE/RUNTIME`

---

## 1. 使用方式

执行进度（2026-04-17）：

- `CARD-PR-01` 已完成并通过 Codex 审计
- `CARD-PR-02` 已完成并通过 Codex 审计
- 后续默认从 `CARD-PR-03` 开始；保留前两张卡是为了复盘、分支补做和防偏差复用

一次只给 Cursor 一张任务卡，不要并行发两张。

每张卡都要求 Cursor：

- 先阅读本文件列出的固定约束与设计裁决；
- 再按本卡给出的文件范围、验收标准和限制直接实施；
- 不要把本卡擅自扩大成“顺手把后面几张也做了”；
- 若本卡触及 `db/changelog/**`、`db.changelog-master.yaml`、`api_endpoint` / `menu_permission_requirement` / `role_permission` 回填、权限 seed、DDL / 索引 / 唯一键 / nullable 迁移，完成前必须实际跑一次 `SpringLiquibase` / 应用启动验证；只报单测或 grep 结果不算完成；
- 若环境前置不足导致无法执行真实迁移/启动验证，只能如实标记为“阻塞/未验证”，不得写成“已完成，待用户启动时验证”；
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
- `docs/TINY_PLATFORM_TESTING_PLAYBOOK.md`
- `docs/TINY_PLATFORM_AUTHORIZATION_TASK_LIST.md`
- `docs/TINY_PLATFORM_PLATFORM_RUNTIME_RBAC3_GOVERNANCE_DESIGN.md`
- `docs/TINY_PLATFORM_PLATFORM_USER_MANAGEMENT_AND_IMPERSONATION_DESIGN.md`
- `docs/TINY_PLATFORM_PERMISSION_IDENTIFIER_SPEC.md`
- `.agent/src/rules/50-testing.rules.md`
- `.agent/src/rules/58-cicd.rules.md`
- `.agent/src/rules/90-tiny-platform.rules.md`
- `.agent/src/rules/91-tiny-platform-auth.rules.md`
- `.agent/src/rules/92-tiny-platform-permission.rules.md`
- `.agent/src/rules/93-tiny-platform-authorization-model.rules.md`

### 2.2 当前已经完成的基线

下列内容已在主线工作区落地，本轮任务卡不得回退：

- 平台作用域以 `PLATFORM` 为唯一平台运行态语义，不再隐式认 `default`
- `/platform/users` Phase 1 skeleton 已落地，平台用户档案主表为 `platform_user_profile`
- 平台登录链已要求平台用户档案状态与平台角色赋权同时满足
- `role.tenant_id IS NULL + role.role_level=PLATFORM` 已是现有平台角色物理载体
- `CARD-PR-01` 已完成：平台角色页面主语义已统一到“平台角色”，主路由为 `/platform/roles`，兼容 alias 为 `/platform/template-roles`
- `CARD-PR-02` 已完成：`/platform/users/{id}/roles` 后端读写链、平台角色校验、统一守卫回填与最小回归测试已落地
- `role_assignment(scope_type=PLATFORM, tenant_id IS NULL, scope_id IS NULL)` 已是平台角色赋权主链

### 2.3 执行防偏差补充（来自 CARD-PR-01 / CARD-PR-02）

以下补充约束用于避免 Cursor 在后续平台角色卡片中再次出现同类偏差：

- 只要新增或改名 `/platform/**`、`/sys/**` 控制面接口，且该接口应受统一权限守卫治理，就必须在同一张卡里同步补齐 `api_endpoint` / `api_endpoint_permission_requirement` 回填、master include 或等价初始化链路，以及 real-controller 守卫测试；只改 controller/service/unit test 不算完成。
- 若同一 controller 暴露了 GET/POST/DELETE 等多种 method，`api_endpoint` 回填与 real-controller 守卫测试必须覆盖所有实际暴露的 method；不能只补读/写其中一部分后宣称控制面已闭环。
- 凡是 `replace` 语义的批量 ID 输入（如 `roleIds`、`permissionIds`），必须先对 `null`、`0`、负数、越界 ID、错作用域对象做 fail-closed 校验，再决定是否去重；禁止“先静默过滤非法值再部分成功”。
- 涉及前端主路由 path/name 收口时，必须说明兼容 alias、历史路径保留策略，并全局核查 route name/path 的硬编码引用；不能只改页面标题或单一路由声明。
- 卡片交付里若新增了控制面 API，`剩余风险` 必须显式说明“是否依赖 Liquibase/seed 迁移落地后才会真正放行”，避免把“代码已改”误判成“已有环境立即可用”。
- 涉及 `JwtTokenCustomizer`、`SecurityUser`、`permissionsVersion`、登录态快照或平台运行时权限链的卡片，必须保证新签发 access token 的 `authorities` / `permissions` / `roleCodes` 与 `permissionsVersion` 来自同一份当前作用域授权结果；若可通过 `UserDetailsService` 或等价权威链重载，则不得因为已有 permission-style snapshot 就跳过重载，尤其 `PLATFORM` 作用域。
- `/platform/**` 控制面不得把 `/sys/**` 或 tenant-only 控制面接口当作核心读写主链；若平台侧缺最小 lookup / query / action 接口，应优先补 `PLATFORM` 语义下的最小接口，而不是默认借道租户/系统控制面。
- `/platform/**` 页面、接口与前端权限守卫不得把 `system:*` 权限作为本域核心能力的必备前置；若确有历史桥接，任务卡必须显式说明“临时跨域复用”的范围、风险、退出条件和后续收口卡片，否则视为不允许。
- 允许复用 service / repository / domain 逻辑，但 controller path、permission、menu、route、前端 API contract、AccessGuard 与 scope 判定必须保持平台/租户分流；涉及平台路径的数据查询或约束记录时，必须显式支持 `tenant_id IS NULL`，禁止把 `NULL tenantId` 直接塞进 tenant-only 查询后宣称完成。
- 涉及平台控制面主链的测试，除正向通过外，还必须补至少一条反向断言：确认未调用 `/sys/**` 旧入口、未依赖 `system:*` 权限、tenant scope 下会明确阻断，而不是静默回落到租户/系统控制面。
- 平台 RBAC3 卡片的完成态不只看 `/platform/role-constraints/*` controller/repository 是否可用；同卡必须确认平台角色绑定主链（如 `replaceUserPlatformRoleAssignments(...)` 或等价平台赋权写链）已经接入 `RoleConstraintService` 校验与 violation log 产出，否则视为“可配置但不生效”的假闭环。
- 平台 RBAC3 约束写链必须 fail-closed 校验所有被引用 roleId 均为 `tenant_id IS NULL + role_level=PLATFORM` 的平台角色；禁止 tenant roleId 混入 `tenant_id IS NULL` 的平台约束记录。
- 凡是把 RBAC3 / governance 表的 `tenant_id` 从 NOT NULL 改为 NULLABLE 以承载平台记录，同卡必须同步补 `normalized_tenant_id` 或等价唯一键方案、必要的数据去重 SQL 与回归说明；不能只改 nullable 而把数据库级唯一性退化成 service best-effort。

### 2.3.1 CARD-PR-01 ~ CARD-PR-09 责任边界与最低验证

| 卡片 | 本卡必须负责 | 本卡不负责 | Liquibase 责任边界 | 最低验证门禁 |
| --- | --- | --- | --- | --- |
| `CARD-PR-01` | 平台角色页面主语义、路由 path/name/alias、文案与历史命名兼容 | 平台用户角色绑定、RBAC3、审批、后端写链 | 默认不要求；若顺手动了菜单 seed 或路由依赖的后端初始化数据，则同卡承担迁移验证 | 前端定向单测 + 全局路由引用核查 |
| `CARD-PR-02` | `/platform/users/{id}/roles` 后端读写、平台角色 fail-closed 校验、统一守卫回填、real-controller 守卫测试 | 前端编辑 UI、RBAC3 控制面、审批 | 只要新增/修改 `api_endpoint` / requirement / permission backfill / master include，就必须同卡完成真实 `SpringLiquibase` 执行路径验证 | 后端定向测试 + real-controller 测试 + 真实迁移/启动验证 |
| `CARD-PR-03` | `/platform/users` 角色明细展示、角色编辑入口、`GET/PUT /platform/users/{id}/roles` 对接、平台 scope guard | RBAC3、审批后端、租户角色编辑 | 默认不要求；若新增平台 lookup 接口、菜单或 permission requirement，则同卡承担迁移验证 | 前端 API/页面测试 + 构建；如触及迁移再补真实迁移/启动验证 |
| `CARD-PR-04` | 平台 effective role hierarchy 展开、JWT `authorities/permissions/roleCodes` 与 `permissionsVersion` 同源收口 | RBAC3 控制面、审批、前端页面 | 默认不要求；如补平台 hierarchy 相关 seed/DDL，责任不外推到下一卡 | 角色解析/JWT/登录链定向测试 |
| `CARD-PR-05` | 平台 RBAC3 后端路径、`tenant_id IS NULL` 查询/写链、平台赋权主链 enforce、DELETE/GET/POST 守卫回填、唯一键收口 | RBAC3 前端、审批 | 必须承担 `api_endpoint` / DDL / unique / nullable / violation log 相关 changeset 的真实迁移验证 | 后端 controller/service/integration + real-controller + 真实迁移/启动验证 |
| `CARD-PR-06` | 平台 RBAC3 前端页、平台 scope guard、平台域最小 lookup、按钮/权限提示 fail-closed | 审批后端、RBAC3 写链语义改造 | 若新增平台菜单、menu requirement、lookup endpoint requirement 或其它 changeset，同卡必须真跑迁移；不能把菜单可见性问题留给后端卡补 | 前端单测 + 构建；如触及迁移再补真实迁移/启动验证 |
| `CARD-PR-07` | 平台角色审批后端、DDL、权限 seed、`role_permission` 绑定、审批 API、apply 主链与 RBAC3 enforce | 审批前端入口与页面交互 | 必须承担 `162+` 这类治理列/审批表/API endpoint/permission 回填 changeset 的真实迁移验证 | 后端定向测试 + real-controller + 真实迁移/启动验证 |
| `CARD-PR-08` | 审批前端页、平台用户页审批摘要、菜单与 GET/OR 组对齐、前端门禁与请求边界收口 | 新审批状态机、审批后端主语义扩展 | 若新增菜单、`api_endpoint` OR 组或 permission requirement changeset，同卡必须真跑迁移；不能只改前端页面 | 前端单测 + 构建；如触及迁移再补真实迁移/启动验证 |
| `CARD-PR-09` | 平台 RBAC3 审计收口：平台赋权主链 enforce、平台角色合法性校验、平台管理员默认权限回填、DELETE/唯一键/菜单可见性回归锁定 | 新 RBAC3 模型扩展、审批新状态机、impersonation | 若新增/修改 permission、role_permission、menu、`api_endpoint`、DDL 或回填 changeset，必须同卡真跑迁移；不能只交测试或 grep 结果 | 后端定向测试 + real-controller + 真实迁移/启动验证 + 菜单/权限可见性验证 |

责任划分补充：

- 前端卡如果因为“页面可用”而引入了新的平台菜单、lookup endpoint、permission requirement 或 API path，对应的后端初始化链路和 Liquibase 验证责任不外推，仍由当前卡承担。
- 后端卡如果引入了新的控制面路径、菜单入口或 DDL，但未完成真实迁移/启动验证，只能算“代码已改、交付未完成”，不能让用户在首次启动时替任务卡兜底验收。
- 卡片完成态默认要求“本卡范围内的控制面、初始化数据、统一守卫、运行时主链和验证证据”一起闭合；不能把其中一部分口头留给下一卡。

### 2.3.2 Liquibase 真跑门禁

下列改动一旦出现，就进入“必须真跑迁移/启动验证”的范围：

- 新增或修改 `db/changelog/**`
- 修改 `db.changelog-master.yaml`
- 新增或修改 `api_endpoint` / `api_endpoint_permission_requirement`
- 新增或修改 `menu_permission_requirement`
- 新增或修改 `role_permission`
- 新增权限 seed、菜单 seed、DDL、索引、唯一键、nullable、generated column

最小完成条件：

- 至少跑通一次真实 `SpringLiquibase` 执行路径，证明 changeset 会被应用加载，而不是只在源码里看起来正确。
- 推荐优先使用：
  - `bash tiny-oauth-server/scripts/verify-platform-local-dev-stack.sh`
  - 若明确不需要前端联动：`DB_PASSWORD='…' bash tiny-oauth-server/scripts/verify-platform-dev-bootstrap.sh`
  - 若脚本不适用，可直接启动 `OauthServerApplication` 或等价应用入口让 `SpringLiquibase` 真正执行
- 报告中必须说明：
  - 用了哪条命令
  - 是 existing DB 路径还是 fresh DB / migration smoke 路径
  - 实际跑过哪些 changeset 或至少确认应用成功启动越过 Liquibase
  - 若未跑，为什么未跑，以及缺什么前置

执行补充：

- 只改 `src/main/resources/db/changelog/**` 但没有确认实际启动加载的是新资源，不算“已验证”；若应用从 `target/classes` 或 jar 启动，必须确认对应资源已同步到运行产物。
- 对已进入共享库或历史库的 changeset，禁止回改旧正文来“修复启动失败”；必须追加新的尾部 changeset 收口，并验证不会引发 checksum 漂移。
- 若任务触及唯一键、nullable、generated column、表结构兼容性或 `role_permission` / `menu_permission_requirement` / `api_endpoint` 这类核心初始化链，除 existing DB 启动路径外，应优先补一条 fresh DB / migration smoke；若当前环境做不到，必须在剩余风险中明确缺口，而不是省略。
- 环境前置不足时，交付状态只能写“阻塞/未验证”，不得写“已完成，待用户启动”。

### 2.3.3 CARD-PR-01 ~ CARD-PR-09 已暴露的 Cursor 偏差复盘

以下偏差已在本轮真实执行中出现，后续同类卡片一律按“已知高风险模式”处理：

- 只改 controller/service/unit test，漏补 `api_endpoint` / requirement / real-controller 守卫测试，导致代码看似可用、真实部署统一守卫先 403。
- 对 `replace` 语义输入先静默过滤非法值再部分成功，破坏 fail-closed 契约。
- 只刷新 `permissionsVersion`，却继续复用旧 `SecurityUser` / snapshot 的 `authorities` 或 `roleCodes`，造成 token 内容与版本指纹不一致。
- 平台页表面独立，但暗中依赖 `/sys/**` 或无关权限码，导致按钮可见、操作实际不可用。
- 平台 RBAC3 只做了配置面 CRUD，没有把 mutex / prerequisite / cardinality enforce 接入平台赋权主链，形成“可配置但不生效”的假闭环。
- 写 `db/changelog` 时凭相邻表经验假设列存在，没有回看真实基线 DDL/实体，也没有跑一次 `SpringLiquibase`，最终把启动失败留给用户首次发现。
- 只跑前端单测或后端定向测试就宣称“任务完成”，没有确认启动产物实际加载的是修复后的资源。

### 2.4 本轮禁止事项

- 不要新增 `role_usage`、`role_kind`、`template_flag` 一类字段去硬拆平台角色
- 不要新增第二套“平台运行时角色表”或“平台模板角色表”
- 不要让 `/sys/users` 承载平台用户语义
- 不要把 `/sys/role-constraints/*` 直接强行复用成平台前端入口
- 不要弱化 RBAC3 fail-closed 校验
- 不要把平台角色变更默认为“自动持续同步所有租户”
- 不要在没有明确验收的情况下顺手扩大到 impersonation 或 platform -> tenant bridge

---

## 3. 执行顺序

完整顺序如下；截至 2026-04-17，`CARD-PR-01/02` 已完成，新任务默认从 `CARD-PR-03` 开始。

1. `CARD-PR-01` 平台角色语义与页面命名收口
2. `CARD-PR-02` 平台用户角色绑定后端
3. `CARD-PR-03` 平台用户角色绑定前端
4. `CARD-PR-04` 平台 effective role hierarchy 与 token 指纹收口
5. `CARD-PR-05` 平台 RBAC3 后端
6. `CARD-PR-06` 平台 RBAC3 前端
7. `CARD-PR-07` 平台角色审批后端
8. `CARD-PR-08` 平台角色审批前端与门禁同步
9. `CARD-PR-09` 平台 RBAC3 审计收口与平台管理员默认权限回填

执行顺序说明：

- `CARD-PR-01` 先把语义统一，避免后续卡继续在“模板角色/运行时角色”之间摇摆
- `CARD-PR-02 ~ CARD-PR-04` 先补平台角色绑定与运行时生效链
- `CARD-PR-05 ~ CARD-PR-06` 再补平台 RBAC3 治理控制面
- `CARD-PR-07 ~ CARD-PR-08` 最后补审批闭环
- `CARD-PR-09` 是审计驱动的收口卡：用于修复 `PR-05 ~ PR-08` 执行后暴露的“平台 RBAC3 可配置但未完全 enforce / 平台管理员默认不可见”一类运行态缺口
- 若其他设计文档的 `PR-6/7/8` 高层分期与本文件出现命名或拆分差异，执行时一律以 `CARD-PR-06/07/08` 的标题、范围、明确要求和验收标准为准；不得按旧分期名称自行改卡。

---

## 4. 任务卡

### CARD-PR-01 平台角色语义与页面命名收口

**目标**

把当前“平台模板角色”口径收敛成“平台角色”，至少做到文案、路由命名、页面提示不再暗示这是另一套不能赋权的平台角色。

**为什么先做**

如果语义不先统一，后面的角色绑定、RBAC3、审批卡都会在“到底是不是模板角色”上反复打架。

**范围**

- `tiny-oauth-server/src/main/webapp/src/views/platform/template-roles/TemplateRoles.vue`
- `tiny-oauth-server/src/main/webapp/src/views/platform/template-roles/TemplateRoles.test.ts`
- `tiny-oauth-server/src/main/webapp/src/router/index.ts`
- 与该页面直接相关的菜单文案、前端常量、文档注释
- 必要时同步 `docs/TINY_PLATFORM_PLATFORM_RUNTIME_RBAC3_GOVERNANCE_DESIGN.md` 的执行态文案

**明确要求**

- 新主文案统一使用“平台角色”
- 若保留 `/platform/template-roles` 路径，必须把它解释为平台角色治理页的兼容路径，而不是 template-only 页面
- 页面提示需改成“平台角色可作为租户初始化来源；平台用户角色绑定在 `/platform/users` 处理”
- 如修改 route path 或 route name，必须同步核查全局引用，并说明兼容 alias 或无引用证据
- 不在本卡引入新的后端表结构
- 不在本卡直接实现平台用户角色绑定

**验收标准**

- 页面标题、按钮文案、提示文案不再出现“模板角色不能直接分配用户”这类全局否定口径
- Router 中存在清晰的一致命名；如引入 `/platform/roles`，旧路径兼容语义必须明确
- 若改动 route name/path，交付结果中必须说明全局搜索结果或兼容处理结论
- 页面测试覆盖新的文案或路由语义

**建议验证**

- `npm --prefix tiny-oauth-server/src/main/webapp test -- TemplateRoles.test.ts`

**复制给 Cursor 的提示词**

```text
请为 tiny-platform 执行 CARD-PR-01：平台角色语义与页面命名收口。

必须先阅读：
- AGENTS.md
- docs/TINY_PLATFORM_TESTING_PLAYBOOK.md
- docs/TINY_PLATFORM_AUTHORIZATION_TASK_LIST.md
- docs/TINY_PLATFORM_PLATFORM_RUNTIME_RBAC3_GOVERNANCE_DESIGN.md
- docs/TINY_PLATFORM_PERMISSION_IDENTIFIER_SPEC.md
- .agent/src/rules/50-testing.rules.md
- .agent/src/rules/58-cicd.rules.md
- .agent/src/rules/90-tiny-platform.rules.md
- .agent/src/rules/92-tiny-platform-permission.rules.md
- .agent/src/rules/93-tiny-platform-authorization-model.rules.md

本卡固定前提：
- 平台侧只有一套平台角色
- tenant_id IS NULL + role_level=PLATFORM 就是平台角色
- 不允许引入 role_usage 或第二套平台角色模型

本卡只做：
- 收敛 TemplateRoles 页面、路由、提示文案到“平台角色”语义
- 如需保留 /platform/template-roles，只能作为兼容路径或历史命名
- 明确平台用户角色绑定在 /platform/users 处理
- 如改路由 path/name，必须全局搜索引用并在结果中说明兼容策略与核查结果

本卡不要做：
- 不要实现平台用户角色绑定
- 不要改后端表结构
- 不要顺手做 RBAC3 或审批

交付要求：
- 直接改代码，不要只给计划
- 输出修改文件
- 输出执行命令
- 输出测试结果
- 输出剩余风险
```

**Codex 审计点**

- 页面是否仍把平台角色描述成 template-only
- 是否仍残留“不能直接分配用户”的误导性全局口径
- 路由命名和页面命名是否一致
- 若修改 route name/path，是否真的核查了全局引用与兼容 alias

---

### CARD-PR-02 平台用户角色绑定后端

**目标**

为 `/platform/users` 补平台角色查询与绑定后端能力，承认平台用户可以被授予平台角色。

**为什么排第二**

当前平台登录已经依赖平台角色赋权，但平台控制面还没有正式写入口，这是最大的行为缺口。

**范围**

- `tiny-oauth-server/src/main/java/com/tiny/platform/application/controller/user/PlatformUserManagementController.java`
- `tiny-oauth-server/src/main/java/com/tiny/platform/infrastructure/auth/user/service/PlatformUserManagementService.java`
- `tiny-oauth-server/src/main/java/com/tiny/platform/infrastructure/auth/user/service/PlatformUserManagementServiceImpl.java`
- `tiny-oauth-server/src/main/java/com/tiny/platform/infrastructure/auth/user/dto/PlatformUserManagementDtos.java`
- `tiny-oauth-server/src/main/java/com/tiny/platform/infrastructure/auth/role/service/RoleAssignmentSyncService.java`
- `tiny-oauth-server/src/main/java/com/tiny/platform/infrastructure/auth/role/service/RoleServiceImpl.java`
- 必要的 repository / projection / access guard / DTO / 测试

**明确要求**

- 新增平台用户角色查询接口：`GET /platform/users/{userId}/roles`
- 新增平台用户角色替换接口：`PUT /platform/users/{userId}/roles`
- 只允许绑定 `tenant_id IS NULL + role_level=PLATFORM` 的平台角色
- 写入主链必须落到现有 `role_assignment(scope_type=PLATFORM, tenant_id IS NULL, scope_id IS NULL)`
- 新接口若受统一守卫治理，必须同卡补齐 `api_endpoint` / `api_endpoint_permission_requirement` 回填，并接入 changelog master 或等价初始化链
- 必须补 real-controller 守卫测试覆盖新 GET/PUT 路由，不能只做 controller/service 单测
- `roleIds` replace 语义必须对 `null` / `0` / 负数 fail-closed；重复值只能在“已通过合法性校验后”再去重
- 不允许通过 tenant scope 用户可见性校验来错误限制平台用户绑定
- `RoleServiceImpl` 中“平台模板角色不支持直接分配用户”的口径，要么删除，要么收缩为“角色页本身不做用户绑定，平台用户页负责绑定”

**验收标准**

- 后端存在平台用户角色读写 API，且鉴权归属平台控制面
- 非平台角色不能被绑定到平台用户
- 非法 `roleIds` 请求不能被静默过滤为部分成功
- 平台用户详情或独立接口能返回角色明细，不再只有布尔 `hasPlatformRoleAssignment`
- 统一守卫所需的 `api_endpoint` / requirement 证据与 real-controller 测试已补齐
- 相关控制器、服务、集成测试通过

**建议验证**

- `mvn -pl tiny-oauth-server -Dtest=PlatformUserManagementControllerTest,PlatformUserManagementApiEndpointGuardRealControllerIntegrationTest,PlatformUserManagementServiceImplTest,RoleServiceImplTest test`

**复制给 Cursor 的提示词**

```text
请为 tiny-platform 执行 CARD-PR-02：平台用户角色绑定后端。

必须先阅读：
- AGENTS.md
- docs/TINY_PLATFORM_TESTING_PLAYBOOK.md
- docs/TINY_PLATFORM_AUTHORIZATION_TASK_LIST.md
- docs/TINY_PLATFORM_PLATFORM_RUNTIME_RBAC3_GOVERNANCE_DESIGN.md
- docs/TINY_PLATFORM_PLATFORM_USER_MANAGEMENT_AND_IMPERSONATION_DESIGN.md
- .agent/src/rules/50-testing.rules.md
- .agent/src/rules/58-cicd.rules.md
- .agent/src/rules/90-tiny-platform.rules.md
- .agent/src/rules/91-tiny-platform-auth.rules.md
- .agent/src/rules/93-tiny-platform-authorization-model.rules.md

本卡固定前提：
- 平台角色只有一套，不允许新增 role_usage
- 平台用户角色写链必须继续使用 role_assignment(scope_type=PLATFORM, tenant_id IS NULL, scope_id IS NULL)

本卡只做：
- 新增 GET/PUT /platform/users/{userId}/roles
- 让平台用户详情或角色明细接口返回实际角色列表
- 同卡补齐统一守卫所需的 api_endpoint / requirement 回填与 real-controller 守卫测试
- roleIds 中出现 null/0/负数时必须 fail-closed，不能静默过滤
- 收口 RoleServiceImpl 中“平台模板角色不支持直接分配用户”的错误口径

本卡不要做：
- 不要做 RBAC3 hierarchy 展开
- 不要做平台 RBAC3 控制面
- 不要做审批流

交付要求：
- 直接改代码，不要只给计划
- 输出修改文件
- 输出执行命令
- 输出测试结果
- 输出剩余风险
```

**Codex 审计点**

- 平台用户角色写链是否真的写入了 `role_assignment(scope=PLATFORM)`
- 是否拦住了 tenant 角色误绑到平台用户
- 新接口是否真的补了 `api_endpoint` / requirement 与 real-controller 守卫证据
- 非法 `roleIds` 是否 fail-closed，而不是被静默过滤
- 是否还残留“平台角色不可赋给平台用户”的运行时限制

---

### CARD-PR-03 平台用户角色绑定前端

**目标**

让 `/platform/users` 真正可查看和编辑平台用户角色，而不是只看一个布尔状态。

**为什么排第三**

后端写链建立之后，前端要尽快把真实角色明细暴露出来，否则控制面仍然不可治理。

**范围**

- `tiny-oauth-server/src/main/webapp/src/views/platform/users/PlatformUsers.vue`
- `tiny-oauth-server/src/main/webapp/src/views/platform/users/PlatformUsers.test.ts`
- `tiny-oauth-server/src/main/webapp/src/api/platform-user.ts`
- `tiny-oauth-server/src/main/webapp/src/api/platform-user.test.ts`
- 必要时补 `tiny-oauth-server/src/main/webapp/src/api/role.ts` 的平台角色 lookup 能力
- 如改了 `role.ts`，同步补 `tiny-oauth-server/src/main/webapp/src/api/role.test.ts`

**明确要求**

- 当前页面现状是以 `hasPlatformRoleAssignment` 布尔态展示“已绑定/缺少绑定”；本卡要把它升级为“可读角色明细 + 可保存角色绑定”，而不是只换文案
- 平台用户详情需能展示已绑定平台角色列表
- 提供最小可用的“编辑平台角色”入口
- 前端只查询平台角色，不得把租户角色列表混进来；优先复用 `/platform/roles` 已证明可用的平台角色来源或同一主链下的最小查询能力，不要直接把租户用户页的 `getAllRoles()` 当默认来源
- scope guard 继续要求 `PLATFORM`
- 不得破坏现有“租户用户代管”tab、回跳 query、drawer 行为与“仍处于 PLATFORM 作用域、不切 active scope”的既有语义
- 角色绑定保存必须直连 `GET/PUT /platform/users/{userId}/roles`；不要借道 `/sys/users`、`/sys/roles/{id}/users`，也不要引入 `active-scope` 切换
- 如审批尚未落地，本卡只做直写绑定，不预埋假审批 UI

**验收标准**

- 平台用户页不再只停留在 `hasPlatformRoleAssignment` 布尔态
- 可以对单个平台用户发起角色绑定保存
- 平台用户详情或编辑区能看到实际角色列表，而不只是“已绑定/缺少绑定”
- 现有 tenant stewardship 相关测试语义不被破坏
- 页面测试和 API 测试覆盖角色展示、角色保存主路径

**建议验证**

- `npm --prefix tiny-oauth-server/src/main/webapp run test:unit -- PlatformUsers.test.ts platform-user.test.ts`
- 如改了 `role.ts`：`npm --prefix tiny-oauth-server/src/main/webapp run test:unit -- role.test.ts`

**复制给 Cursor 的提示词**

```text
请为 tiny-platform 执行 CARD-PR-03：平台用户角色绑定前端。

必须先阅读：
- AGENTS.md
- docs/TINY_PLATFORM_AUTHORIZATION_TASK_LIST.md
- docs/TINY_PLATFORM_PLATFORM_RUNTIME_RBAC3_GOVERNANCE_DESIGN.md
- docs/TINY_PLATFORM_PLATFORM_USER_MANAGEMENT_AND_IMPERSONATION_DESIGN.md
- .agent/src/rules/50-testing.rules.md
- .agent/src/rules/90-tiny-platform.rules.md
- .agent/src/rules/92-tiny-platform-permission.rules.md

本卡固定前提：
- 平台角色只有一套
- 平台用户角色绑定由 /platform/users 控制面承载

本卡只做：
- 在现有 PlatformUsers 页面和详情/编辑区展示角色明细
- 增加最小可用的平台角色编辑入口，并完成保存回写
- 前端 API 对接 GET/PUT /platform/users/{userId}/roles
- 保持 tenantStewardship tab、query 恢复、drawer 交互和 PLATFORM 作用域语义不变
- 平台角色来源优先复用已存在的平台角色主链；若确实缺最小 lookup，再做受控补充并补测试

本卡不要做：
- 不要预埋审批流假页面
- 不要新增租户角色 lookup
- 不要引入 active scope 切换或 tenant scope 角色编辑
- 不要顺手改 RBAC3 页面

交付要求：
- 直接改代码，不要只给计划
- 输出修改文件
- 输出执行命令
- 输出测试结果
- 输出剩余风险
```

**Codex 审计点**

- 前端是否仍停留在布尔态显示，而没有真实角色列表
- 是否误把租户角色当成平台角色来源，或直接复用了 tenant user 角色 API
- 保存是否真的走 `GET/PUT /platform/users/{id}/roles`
- tenant stewardship 的 route/query/drawer/PLATFORM 语义是否被误伤
- scope guard 是否保持平台作用域限制

---

### CARD-PR-04 平台 effective role hierarchy 与 token 指纹收口

**目标**

让平台角色运行时生效链与租户侧一致，支持 `role_hierarchy` 展开，并把变化反映到 `permissionsVersion` / token 权限结果。

**为什么排第四**

如果绑定页面已经能给平台用户绑角色，但平台运行时不展开继承，那么 RBAC3 hierarchy 页面后续就会失真。

**范围**

- `tiny-oauth-server/src/main/java/com/tiny/platform/infrastructure/auth/role/service/EffectiveRoleResolutionService.java`
- `tiny-oauth-server/src/main/java/com/tiny/platform/core/oauth/config/JwtTokenCustomizer.java`
- `tiny-oauth-server/src/main/java/com/tiny/platform/core/oauth/security/AuthUserResolutionService.java`
- `PermissionVersionService` 及相关测试
- `tiny-oauth-server/src/test/java/com/tiny/platform/infrastructure/auth/role/service/EffectiveRoleResolutionServiceTest.java`
- 必要的 JWT / 登录链测试

**明确要求**

- `findEffectiveRoleIdsForUserInPlatform(...)` 必须像租户侧一样展开 hierarchy
- 展开范围只认 `tenant_id IS NULL` 的平台角色层级边
- 平台 hierarchy 变化必须反映到 `permissionsVersion`
- 平台登录与 JWT 权限集合必须基于 expanded effective roles
- 当 `UserDetailsService` 或等价权威链可用时，`JwtTokenCustomizer` 不得继续优先信任旧 `SecurityUser` permission-style snapshot；`authorities` / `permissions` / `roleCodes` 与 `permissionsVersion` 必须同源于当前运行时结果

**验收标准**

- 只绑定子角色时，平台用户也能拿到父角色权限
- 平台 hierarchy 调整后，`permissionsVersion` 会变化
- 平台 hierarchy / assignment 在登录后、token mint 前发生变化时，新签发 JWT 不得继续沿用旧 snapshot 权限结果（至少由定向 JWT 测试锁定）
- 平台登录链、JWT 生成链、角色解析测试通过

**建议验证**

- `mvn -pl tiny-oauth-server -Dtest=EffectiveRoleResolutionServiceTest,AuthUserResolutionServiceTest,JwtTokenCustomizerTest test`

**复制给 Cursor 的提示词**

```text
请为 tiny-platform 执行 CARD-PR-04：平台 effective role hierarchy 与 token 指纹收口。

必须先阅读：
- AGENTS.md
- docs/TINY_PLATFORM_AUTHORIZATION_TASK_LIST.md
- docs/TINY_PLATFORM_PLATFORM_RUNTIME_RBAC3_GOVERNANCE_DESIGN.md
- .agent/src/rules/50-testing.rules.md
- .agent/src/rules/58-cicd.rules.md
- .agent/src/rules/90-tiny-platform.rules.md
- .agent/src/rules/91-tiny-platform-auth.rules.md
- .agent/src/rules/93-tiny-platform-authorization-model.rules.md

本卡固定前提：
- 平台角色只有一套
- hierarchy 展开不能依赖新增 role_usage 字段

本卡只做：
- 补齐平台 effective role hierarchy 展开
- 同步 permissionsVersion / JWT / 登录链

本卡不要做：
- 不要实现平台 RBAC3 控制面
- 不要做审批

交付要求：
- 直接改代码，不要只给计划
- 输出修改文件
- 输出执行命令
- 输出测试结果
- 输出剩余风险
```

**Codex 审计点**

- 平台 effective role 是否真的展开了 hierarchy
- `permissionsVersion` 是否纳入平台 hierarchy 变化
- JWT / 登录链是否仍然只看 direct assignment
- 是否仍存在“`permissionsVersion` 已更新但 JWT `authorities` / `roleCodes` 仍来自旧 snapshot”的不一致

---

### CARD-PR-05 平台 RBAC3 后端

**目标**

为平台角色补齐后端 RBAC3 控制面，让平台侧能独立维护 hierarchy / mutex / prerequisite / cardinality / violations。

**为什么排第五**

运行时 effective role 生效链先补完，再补控制面，能避免 UI 已可配置但运行时不生效的假闭环。

**范围**

- `tiny-oauth-server/src/main/java/com/tiny/platform/application/controller/role/RoleConstraintRuleController.java`
- `tiny-oauth-server/src/main/java/com/tiny/platform/infrastructure/auth/role/service/RoleConstraintRuleAdminService.java`
- `tiny-oauth-server/src/main/java/com/tiny/platform/infrastructure/auth/role/service/RoleConstraintViolationLogQueryService.java`
- `tiny-oauth-server/src/main/java/com/tiny/platform/infrastructure/auth/role/service/RoleConstraintService.java`
- `tiny-oauth-server/src/main/java/com/tiny/platform/infrastructure/auth/role/repository/*Role*Repository.java`
- 平台侧 requirement / guard / Liquibase / DTO / 测试

**明确要求**

- 新增平台专用入口：`/platform/role-constraints/*`
- tenant path 与 platform path 必须分开建模
- 平台路径查询条件必须是 `tenant_id IS NULL`
- violation log 查询必须支持平台路径
- 不允许简单把 `NULL tenantId` 塞进现有 `= :tenantId` 查询后宣称完成
- 平台用户角色绑定主链必须实际消费平台 RBAC3 校验；不能只补控制面 CRUD
- 平台约束记录只能引用平台角色（`tenant_id IS NULL + role_level=PLATFORM`）
- 如 `tenant_id` 改为 NULLABLE，必须同步补数据库级唯一键收口方案，而不是只依赖 service 的“先删后插”

**验收标准**

- 后端存在独立平台 RBAC3 接口
- hierarchy / mutex / prerequisite / cardinality / violations 全部可在平台路径下访问
- tenant-only 测试不被破坏，新增平台测试能覆盖 null-tenant path
- 平台角色绑定主链会触发 platform scope 的 mutex / prerequisite / cardinality 校验与 violation log / enforce 语义
- DELETE 类平台 RBAC3 路由不会因漏回填 `api_endpoint` 而被统一守卫误拒

**建议验证**

- `mvn -pl tiny-oauth-server -Dtest=RoleConstraintRuleControllerRbacIntegrationTest,RoleConstraintRuleAdminServiceIntegrationTest test`

**复制给 Cursor 的提示词**

```text
请为 tiny-platform 执行 CARD-PR-05：平台 RBAC3 后端。

必须先阅读：
- AGENTS.md
- docs/TINY_PLATFORM_TESTING_PLAYBOOK.md
- docs/TINY_PLATFORM_AUTHORIZATION_TASK_LIST.md
- docs/TINY_PLATFORM_PLATFORM_RUNTIME_RBAC3_GOVERNANCE_DESIGN.md
- .agent/src/rules/50-testing.rules.md
- .agent/src/rules/58-cicd.rules.md
- .agent/src/rules/90-tiny-platform.rules.md
- .agent/src/rules/92-tiny-platform-permission.rules.md
- .agent/src/rules/93-tiny-platform-authorization-model.rules.md

本卡固定前提：
- 平台 RBAC3 不能继续挂在 /sys/role-constraints/*
- 平台侧约束记录使用 tenant_id IS NULL

本卡只做：
- 新增 /platform/role-constraints/* 后端
- 把 repo/service/controller 拆成 tenant path 与 platform path
- 补平台 violation log 查询

本卡不要做：
- 不要先做前端页面
- 不要做审批流
- 不要引入 role_usage

交付要求：
- 直接改代码，不要只给计划
- 输出修改文件
- 输出执行命令
- 输出测试结果
- 输出剩余风险
```

**Codex 审计点**

- 是否真的存在平台专用入口，而不是前端偷偷继续调 `/sys/role-constraints/*`
- repository 是否正确支持 `tenant_id IS NULL`
- violation log 是否可按平台路径查询

---

### CARD-PR-06 平台 RBAC3 前端

**目标**

把平台 RBAC3 控制面真正挂到平台菜单，支持平台角色约束配置和违例查询。

**为什么排第六**

后端 API 先稳定后再做页面，能减少 UI 反复。

**范围**

- `tiny-oauth-server/src/main/webapp/src/api/roleConstraint.ts`
- `tiny-oauth-server/src/main/webapp/src/api/roleConstraint.test.ts`
- 若页面需要角色候选 / 名称补全：`tiny-oauth-server/src/main/webapp/src/api/platform-role.ts`
- 新增平台 RBAC3 页面与测试
- `tiny-oauth-server/src/main/webapp/src/router/index.ts`
- 必要的菜单 requirement / 前端权限守卫

**明确要求**

- 平台页面必须调用 `/platform/role-constraints/*`
- 不复用租户角色约束页的 tenant-only 入口
- scope guard 必须要求 `PLATFORM`
- 页面至少覆盖 hierarchy / mutex / prerequisite / cardinality / violations 的最小可用操作
- 若页面需要角色候选或角色名称补全，必须走 `/platform/roles/options` 或等价平台域 lookup；不得回退 `/sys/roles`，也不得把 `system:role:list` 当成隐形前置权限
- tenant scope、缺少平台前置权限或平台域 lookup 不可用时必须 fail-closed：明确阻断，不发平台写请求，也不 silently fallback 到 `/sys/role-constraints/*` 或其他租户/系统入口

**验收标准**

- 平台菜单中出现平台 RBAC3 页面入口
- 页面在平台作用域下可正常加载与提交
- tenant scope 下明确阻断
- 前端测试覆盖主要读写路径和 scope guard
- 角色候选查询不依赖 `/sys/roles` / `system:role:list`
- 至少有一条反向测试证明 tenant scope 下不会误发平台请求，也不会回退到 `/sys/role-constraints/*`

**建议验证**

- `npm --prefix tiny-oauth-server/src/main/webapp test -- roleConstraint.test.ts`

**复制给 Cursor 的提示词**

```text
请为 tiny-platform 执行 CARD-PR-06：平台 RBAC3 前端。

必须先阅读：
- AGENTS.md
- docs/TINY_PLATFORM_AUTHORIZATION_TASK_LIST.md
- docs/TINY_PLATFORM_PLATFORM_RUNTIME_RBAC3_GOVERNANCE_DESIGN.md
- .agent/src/rules/50-testing.rules.md
- .agent/src/rules/90-tiny-platform.rules.md
- .agent/src/rules/92-tiny-platform-permission.rules.md

本卡固定前提：
- 平台 RBAC3 页面必须走 /platform/role-constraints/*
- tenant scope 下不得误发平台请求
- 若页面需要角色候选，必须走 /platform/roles/options 或等价平台域最小 lookup

本卡只做：
- 新增平台 RBAC3 页面、路由、API 对接、前端守卫
- 最小可用覆盖 hierarchy/mutex/prerequisite/cardinality/violations

本卡不要做：
- 不要继续调用 /sys/role-constraints/*
- 不要借道 /sys/roles 或继续依赖 system:role:list 作为平台 RBAC3 页面前置
- 不要做审批页面

交付要求：
- 直接改代码，不要只给计划
- 输出修改文件
- 输出执行命令
- 输出测试结果
- 输出剩余风险
```

**Codex 审计点**

- 平台前端是否真的改到 `/platform/role-constraints/*`
- 是否补了菜单与 scope guard
- 是否还残留租户态 API 假复用
- 角色候选 / lookup 是否仍偷偷依赖 `/sys/roles` 或 `system:role:list`

---

### CARD-PR-07 平台角色审批后端

**目标**

为高风险平台角色补最小审批闭环，做到“申请 -> 审批 -> RBAC3 终态校验 -> 应用/失败”。

**为什么排第七**

审批一定要建立在平台角色绑定和 RBAC3 已经存在的前提上，否则流程只会成为空壳。

**范围**

- Liquibase changeset
- `role` 治理属性扩展：`risk_level`、`approval_mode`
- 新增 `platform_role_assignment_request` 主表及其 domain / repository / service / controller
- 平台角色绑定写链集成
- 审计事件与测试

**明确要求**

- **只新增** `risk_level`、`approval_mode`，不新增 `role_usage`
- 审批通过时必须再做一次 RBAC3 hard validation
- 审批通过但 apply 失败时，状态必须进入 `FAILED` 或等价失败态
- 对 `approval_mode=NONE` 的角色，平台角色绑定接口仍允许直写
- 对需要审批的角色，平台直写接口必须拒绝并返回清晰原因
- 申请单、审批动作和最终 apply 链路中出现的所有 `roleId` 都必须 fail-closed 校验为 `tenant_id IS NULL + role_level=PLATFORM` 的平台角色；禁止 tenant role 混入审批链
- 所有真实暴露的审批接口 method（如 list / submit / approve / reject / cancel）都必须同卡补齐 `api_endpoint` / `api_endpoint_permission_requirement` 回填、master include 或等价初始化链路，以及 real-controller 守卫测试；不能只改 controller/service/unit test
- 状态机必须终态幂等、失败不可部分应用；不能出现“审批已通过但 apply 结果未落状态”的悬空态

**验收标准**

- 后端存在 `platform_role_assignment_request` 模型与 API
- 平台角色绑定写链能区分直写与走审批
- 审批终态与 RBAC3 校验闭环成立
- 测试覆盖申请、审批、拒绝、apply 失败场景
- 审批申请不能引用 tenant role，且该 fail-closed 约束有测试锁定
- 真实暴露的审批接口不会因漏登记 `api_endpoint` 而在统一守卫下先 403

**建议验证**

- `mvn -pl tiny-oauth-server -Dtest=PlatformUserManagementControllerTest,RoleConstraintRuleAdminServiceIntegrationTest test`

**复制给 Cursor 的提示词**

```text
请为 tiny-platform 执行 CARD-PR-07：平台角色审批后端。

必须先阅读：
- AGENTS.md
- docs/TINY_PLATFORM_TESTING_PLAYBOOK.md
- docs/TINY_PLATFORM_AUTHORIZATION_TASK_LIST.md
- docs/TINY_PLATFORM_PLATFORM_RUNTIME_RBAC3_GOVERNANCE_DESIGN.md
- .agent/src/rules/50-testing.rules.md
- .agent/src/rules/58-cicd.rules.md
- .agent/src/rules/90-tiny-platform.rules.md
- .agent/src/rules/92-tiny-platform-permission.rules.md
- .agent/src/rules/93-tiny-platform-authorization-model.rules.md

本卡固定前提：
- 允许新增 risk_level / approval_mode
- 禁止新增 role_usage
- 审批不是绕过 RBAC3 的手段
- 审批申请引用的 roleId 必须是平台角色
- 所有真实暴露接口 method 都要同步补 api_endpoint requirement 与 real-controller guard 测试

本卡只做：
- 平台角色审批表、后端 API、状态机、写链集成
- 直写与审批分流
- 审批通过时的 RBAC3 终态校验

本卡不要做：
- 不要先做审批前端
- 不要扩大到 impersonation

交付要求：
- 直接改代码，不要只给计划
- 输出修改文件
- 输出执行命令
- 输出测试结果
- 输出剩余风险
```

**Codex 审计点**

- 是否误引入了 `role_usage`
- 审批通过是否真的再次执行 RBAC3 hard validation
- 直写和审批分流是否清晰
- 审批链是否对 roleId 做了平台角色 fail-closed 校验
- 是否把所有审批接口 method 的统一守卫回填与 real-controller 测试一起补齐

---

### CARD-PR-08 平台角色审批前端与门禁同步

**目标**

补齐平台角色审批页、平台用户页审批态展示，以及菜单 / requirement / 审计同步。

**为什么排最后**

审批前端必须建立在审批后端稳定之后，否则 UI 语义会不断返工。

**范围**

- 新增平台审批页和测试
- `tiny-oauth-server/src/main/webapp/src/views/platform/users/PlatformUsers.vue`
- `tiny-oauth-server/src/main/webapp/src/api/platform-user.ts`
- 必要的审批 API 文件、路由、菜单、权限码、统一门禁回填
- 文档与任务清单同步

**明确要求**

- 平台用户页要能显示待审批/最近审批结果摘要
- 新增独立审批页，支持 approve / reject / cancel
- 菜单、`api_endpoint` requirement、前端守卫要与审批权限码对齐
- 不允许出现“页面可见但统一守卫先 403”这类未收口状态
- 平台用户页与独立审批页都必须感知审批态；不能把审批状态只孤立在独立审批页
- 缺权限、跨 scope、无数据或后端返回审批前置不足时必须 fail-closed 展示：不发误请求，不回落到 tenant/system 页面，也不保留误导性的可操作按钮
- 若前端需要角色候选、角色详情摘要或审批对象描述，必须走平台域接口；不得新增对 `/sys/roles` 或其他 tenant-only 接口的依赖

**验收标准**

- 平台审批页可用
- 平台用户页能感知审批态
- 菜单与统一守卫对齐
- 文档和任务清单同步说明审批链已落地
- 权限不足时页面不会出现“按钮可见但接口 403”的误导性可操作态
- 前端测试除 happy path 外，还覆盖至少一条缺权限 / 跨 scope / 审批摘要展示场景

**建议验证**

- `npm --prefix tiny-oauth-server/src/main/webapp test -- PlatformUsers.test.ts`
- `mvn -pl tiny-oauth-server -Dtest=PlatformUserManagementApiEndpointGuardRealControllerIntegrationTest test`

**复制给 Cursor 的提示词**

```text
请为 tiny-platform 执行 CARD-PR-08：平台角色审批前端与门禁同步。

必须先阅读：
- AGENTS.md
- docs/TINY_PLATFORM_TESTING_PLAYBOOK.md
- docs/TINY_PLATFORM_AUTHORIZATION_TASK_LIST.md
- docs/TINY_PLATFORM_PLATFORM_RUNTIME_RBAC3_GOVERNANCE_DESIGN.md
- .agent/src/rules/50-testing.rules.md
- .agent/src/rules/58-cicd.rules.md
- .agent/src/rules/90-tiny-platform.rules.md
- .agent/src/rules/92-tiny-platform-permission.rules.md

本卡固定前提：
- 审批后端已存在
- 菜单、api_endpoint requirement、前端守卫必须一起收口
- 平台用户页与独立审批页都必须同步感知审批态，不能只做单页孤立落地

本卡只做：
- 平台审批页
- 平台用户页审批态摘要
- 菜单/权限码/统一守卫/文档同步

本卡不要做：
- 不要扩大到 impersonation
- 不要重构整个平台用户域模型

交付要求：
- 直接改代码，不要只给计划
- 输出修改文件
- 输出执行命令
- 输出测试结果
- 输出剩余风险
```

**Codex 审计点**

- 菜单、权限码、统一守卫是否真的对齐
- 平台用户页是否能看到审批态，而不是只在独立页面孤立存在
- 文档是否同步到最新落地状态
- 是否新增了对 `/sys/**` 或 `system:*` 的隐形平台审批依赖

---

### CARD-PR-09 平台 RBAC3 审计收口与平台管理员默认权限回填

**目标**

收口平台 RBAC3 在 `CARD-PR-05 ~ CARD-PR-08` 后仍暴露的运行态缺口，确保它不只是“有页面、有 CRUD”，而是：

- 平台赋权主链真正 enforce mutex / prerequisite / cardinality
- 平台约束写链只接受平台角色
- 平台 DELETE 路由、唯一键与统一守卫证据完整
- `ROLE_PLATFORM_ADMIN` 默认可见并可进入 `/platform/role-constraints`

**为什么现在做**

这张卡不是新增一块业务，而是把审计里已经暴露的问题一次性收口，否则会继续出现：

- 平台 RBAC3 菜单/接口“看起来存在”，但平台管理员看不到或真实部署 403
- 平台约束“可配置但不生效”
- tenant role 被写进平台 RBAC3 约束表
- 并发下平台约束数据去重只靠 service best-effort

**范围**

- `tiny-oauth-server/src/main/java/com/tiny/platform/infrastructure/auth/role/service/RoleAssignmentSyncService.java`
- `tiny-oauth-server/src/main/java/com/tiny/platform/infrastructure/auth/role/service/RoleConstraintServiceImpl.java`
- `tiny-oauth-server/src/main/java/com/tiny/platform/infrastructure/auth/role/service/RoleConstraintRuleAdminService.java`
- `tiny-oauth-server/src/main/java/com/tiny/platform/infrastructure/auth/role/service/RoleConstraintViolationLogQueryService.java`
- `tiny-oauth-server/src/main/java/com/tiny/platform/infrastructure/auth/role/repository/*Role*Repository.java`
- 平台 RBAC3 相关 Liquibase changeset（必要时新增尾部回填）
- 平台 RBAC3 / 平台用户角色绑定 / 菜单可见性相关测试

**明确要求**

- 平台用户角色绑定主链 `replaceUserPlatformRoleAssignments(...)` 必须像租户路径一样接入 `roleConstraintService.validateAssignmentsBeforeGrant(...)` 或等价平台 hard validation，不能继续“直接删旧写新”。
- `RoleConstraintServiceImpl` 不允许再对 `tenantId == null` 直接跳过；平台 mutex / prerequisite / cardinality 必须真实阻断，并产出 violation log。
- 平台约束写链（hierarchy / mutex / prerequisite / cardinality）中的所有 `roleId` 都必须 fail-closed 校验为 `tenant_id IS NULL + role_level=PLATFORM` 的平台角色；tenant roleId 进入平台约束表必须直接拒绝。
- 平台 RBAC3 DELETE 路由若当前仍存在漏登记，就必须补齐 `api_endpoint` / `api_endpoint_permission_requirement` 与 real-controller 测试；若仓库已存在 `158` 等回填，则本卡要补“不会回归”的测试证据，而不是口头说已修。
- 平台约束表在 `tenant_id` 可空后的数据库级唯一性必须有明确收口：
  - 若 `159` 已提供 `normalized_tenant_id` 或等价唯一键，本卡要补测试/验证证据锁定它
  - 若仍缺，则同卡新增尾部 changeset 补齐，不能继续只靠 service 的“先删后插”
- `ROLE_PLATFORM_ADMIN` 必须在平台模板与已有环境中补齐：
  - `system:role:constraint:view`
  - `system:role:constraint:edit`
  - `system:role:constraint:violation:view`
  让 `platform_admin` 默认能看到并进入 `/platform/role-constraints`
- 平台 RBAC3 菜单、接口权限与 `ROLE_PLATFORM_ADMIN` 默认绑定回填必须一起验证；不能只补 `role_permission` 不核菜单可见性，也不能只看菜单存在不查角色权限。
- 若新增/修改 Liquibase changeset，必须追加尾部 changeset，不得改写已进库正文；并且必须真跑 `SpringLiquibase` / 应用启动验证。

**验收标准**

- 平台 RBAC3 DELETE 路由在真实统一守卫下不会因漏登记而 403
- 平台用户角色绑定遇到 mutex / prerequisite / cardinality 违规时会被阻断，并有 violation log
- tenant roleId 不能写入平台 RBAC3 约束
- 平台约束去重存在数据库级保护，而不是只依赖 service 幂等
- `ROLE_PLATFORM_ADMIN` 拥有平台 RBAC3 所需三条权限，`platform_admin` 默认可见 `/platform/role-constraints` 菜单
- 交付中包含真实迁移/启动验证，而不只是单元或集成测试

**建议验证**

- `mvn -pl tiny-oauth-server -Dtest=RoleAssignmentSyncServiceTest,RoleConstraintServiceImplTest,RoleConstraintRuleAdminServiceTest,RoleConstraintRuleAdminServiceIntegrationTest,PlatformRoleConstraintApiEndpointGuardRealControllerIntegrationTest test`
- 如补了菜单/角色默认权限回填，增加一条 DB / service 层验证 `ROLE_PLATFORM_ADMIN -> system:role:constraint:*`
- `DB_PASSWORD='…' bash tiny-oauth-server/scripts/verify-platform-dev-bootstrap.sh`
  或直接启动 `OauthServerApplication`，确认新尾部 changeset 真正跑过

**复制给 Cursor 的提示词**

```text
请为 tiny-platform 执行 CARD-PR-09：平台 RBAC3 审计收口与平台管理员默认权限回填。

必须先阅读：
- AGENTS.md
- docs/TINY_PLATFORM_TESTING_PLAYBOOK.md
- docs/TINY_PLATFORM_AUTHORIZATION_TASK_LIST.md
- docs/TINY_PLATFORM_PLATFORM_RUNTIME_RBAC3_GOVERNANCE_DESIGN.md
- docs/TINY_PLATFORM_PLATFORM_ROLE_GOVERNANCE_CURSOR_TASK_CARDS.md
- .agent/src/rules/50-testing.rules.md
- .agent/src/rules/58-cicd.rules.md
- .agent/src/rules/90-tiny-platform.rules.md
- .agent/src/rules/91-tiny-platform-auth.rules.md
- .agent/src/rules/93-tiny-platform-authorization-model.rules.md

本卡固定前提：
- 这是审计收口卡，不新增新的平台角色模型
- 平台 RBAC3 不能停留在“可配置但不生效”
- tenant roleId 不能进入平台 RBAC3 约束表
- 涉及 db/changelog、role_permission、menu、api_endpoint、DDL 的改动必须真跑 SpringLiquibase / 应用启动验证

本卡只做：
- 平台赋权主链接入 RBAC3 enforce 与 violation log
- 平台约束写链的 roleId 平台角色 fail-closed 校验
- DELETE 平台 RBAC3 路由统一守卫证据补齐或回归锁定
- 平台约束数据库级唯一性证据收口
- 为 ROLE_PLATFORM_ADMIN 回填 platform RBAC3 所需默认权限，并验证 platform_admin 可见菜单

本卡不要做：
- 不要新增 role_usage / 第二套平台角色模型
- 不要扩到审批新能力或 impersonation
- 不要顺手重构整个平台用户域

交付要求：
- 直接改代码，不要只给计划
- 输出修改文件
- 输出执行命令
- 输出测试结果
- 输出真实迁移/启动验证结果
- 输出剩余风险
```

**Codex 审计点**

- 平台赋权主链是否真的接入了 mutex / prerequisite / cardinality enforce，而不是仍只展开 hierarchy
- `RoleConstraintServiceImpl` 是否还对 `tenantId == null` 提前返回
- 平台约束写链是否真正拒绝 tenant roleId
- DELETE 路由是否有统一守卫 real-controller 证据，而不是只看 158 changeset 文件存在
- 数据库级唯一性是否真的收口，而不是继续只靠 service best-effort
- `ROLE_PLATFORM_ADMIN` 是否默认拥有 `system:role:constraint:view/edit/violation:view`
- `platform_admin` 看不到平台 RBAC3 菜单的问题是否被真正收口

---

## 5. Codex 总审计口径

每张卡完成后，Codex 至少检查以下问题：

1. 是否破坏了“平台只有一套平台角色”这个前提。
2. 是否引入了 `role_usage`、第二套平台角色表、template/runtime 双语义分表等偏航设计。
3. 是否弱化了平台 / 租户隔离，或让 tenant 角色误入平台链路。
4. 是否让平台 RBAC3 / 审批成为“只写页面、不进运行时”的空壳。
5. 是否同步了必要测试，而不是只改控制器/页面。
6. 是否把菜单、权限码、`api_endpoint` requirement、scope guard 一并收口。

---

## 6. 一句话执行原则

这条主线不要再围绕“要不要拆模板角色与运行时角色”打转，而要直接承认“平台就是一套平台角色”，然后按“语义收口 -> 平台用户绑定 -> 运行时生效 -> RBAC3 -> 审批”的顺序稳步落地。
