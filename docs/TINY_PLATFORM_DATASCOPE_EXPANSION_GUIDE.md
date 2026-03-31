# Tiny Platform `@DataScope` 扩面指南

> 状态：实施指南  
> 适用范围：`auth / user / resource / menu / org / scheduling / export / dict` 及后续候选业务模块  
> 配套文档：`TINY_PLATFORM_AUTHORIZATION_MODEL.md`、`TINY_PLATFORM_AUTHORIZATION_TASK_LIST.md`

---

## 1. 目的与边界

本指南只回答一件事：

- 当某个模块准备接入 `@DataScope` 时，什么时候可以接、应该怎么接、如何验证不会把规则做假。

本指南**不是**：

- 数据范围模型总设计文档；
- 当前完成度真相源；
- “所有模块都应该尽快接入 `@DataScope`” 的推进清单。

当前真实进度、优先级与剩余项，仍以 `TINY_PLATFORM_AUTHORIZATION_TASK_LIST.md` 为准。

---

## 2. 当前已经接入运行态的模块

截至当前代码基线，以下模块已经在运行态消费 `@DataScope`：

- `user`
- `resource`
- `menu`
- `org`
- `scheduling`
- `export`
- `dict`

这些模块的共同点是：已经存在或已补齐了可执行的“可见性语义”，而不是只为了“看起来完成”去强行套一个过滤器。

**与 active scope 的关系（2026-03-28）**：请求主链已主线启用 ORG/DEPT `TenantContext.activeScopeType` / `activeScopeId`。**已落地（业务扩面）**：`DataScopeResolverService.resolve` 内（1）用 `TenantContext` 驱动 **有效角色集合**（与 ORG/DEPT scope 赋权一致），（2）对 `role_data_scope` 中 **ORG / DEPT / ORG_AND_CHILD / DEPT_AND_CHILD** 的几何锚点见下节 **Contract B**；在 **activeScopeType=DEPT** 时，ORG 与 DEPT 形规则均以 **活动部门** `activeScopeId` 为起点（ORG 形经 `findReferenceUnit` 向上）；**TENANT**（或未选 ORG/DEPT）时回退 **主部门**，与历史 TENANT 行为一致。凡已挂 `@DataScope` 且经 `DataScopeAspect` 进入解析的模块（user、org、menu、resource、scheduling、export、dict 等）在 **读路径**上自动继承上述语义。**控制面刷新（2026-03-28 第二批）**：除用户列表外，调度 **DAG 列表**（`Dag.vue`，对应 `listDags` / `@DataScope`）、**导出任务列表**（`ExportTask.vue`）在 HeaderBar 切换 active scope 后会监听 `active-scope-changed` 并重拉后端。**第四批（menu/resource 控制面，2026-03-28）**：`Menu.vue`（菜单管理列表/树 API）与 `resource.vue`（`GET /sys/resources/tree` 等）在切换 active scope 后同样监听 `active-scope-changed` 并重拉；`ResourceServiceImpl.findTopLevelDtos`、`findChildDtos`、`findResourceTreeDtos` 与分页 `resources` 一致，在 JPA `Specification` 层追加 `created_by` 数据范围谓词（与 `DataScopeContext` 同源，禁止前端二次过滤）。**字典（dict，2026-03-28 闭环）**：`DictTypeServiceImpl.query` / `DictItemServiceImpl.query` 已 `@DataScope`；租户字典管理 `dictType.vue` / `dictItem.vue` 在**已选活动租户**时监听 `active-scope-changed` 重拉（无租户时不跟 scope，避免与平台态混淆）。**第三批（export query-time）**：`ExportTaskService.findReadableTasks` 在解析 `DataScopeContext` 后，以 JPA `Specification` 在库侧施加 `tenant_id` 与（`user_id` ∈ 可见键 ∪ `username` ∈ 可见键）OR 谓词，排序同前；可见键集合仍在服务层由 `tenant_user` / `user_unit` / `user` 解析，与 scheduling owner key 解析方式一致。扩面新模块时仍须满足本章准入条件；非法 scope 在 Filter 层 **fail-closed**，不得退化为静默扩大读范围。

**运行历史契约（`getDagRuns` / `DagHistory`，正式不接入 `@DataScope`，2026-03-28 收口）**：`SchedulingService.getDagRuns` **无** `@DataScope`，**不**读取 `DataScopeContext`；可见性 = **租户隔离** + `requireDagInTenant`（校验 DAG 属当前租户后，按 `dagId` 与筛选条件分页 `SchedulingDagRun`）。**原因（产品边界，非遗漏）**：运行记录语义以编排/状态/触发/排障为主，当前未将 Run 与「DAG owner 用户集」或 active scope 几何建立**唯一、稳定**映射；若按 `listDags` 同构 owner 谓词收缩，会**静默**损害租户内运维与审计只读视野。**前端**：`DagHistory.vue` **不**监听 `active-scope-changed`；页面含用户可见说明。**证据**：`SchedulingService.getDagRuns` Javadoc；`SchedulingServiceTenantScopeTest.getDagRuns_contract_operational_view_ignores_data_scope_context`、`getDagRunsMethodShouldNotDeclareDataScopeAnnotation`；`DagHistory.test.ts`（说明文案 + scope 事件不重拉）。若未来要改为按 active scope 收缩，须先书面 Run 级可见性规则，再改后端 query-time 与前端联动，**禁止**仅靠前端过滤。

**Contract B（正式产品/技术契约，2026-03-28 固化）**：当 **activeScopeType=ORG** 时，`role_data_scope` 中 **ORG / ORG_AND_CHILD** 的几何起点为 **活动组织** `activeScopeId`；**DEPT / DEPT_AND_CHILD** 的几何起点为用户的 **主部门**（`user_unit` 主记录对应 unit），与 TENANT 作用域下 DEPT 形规则一致，**不是**「活动组织下的默认部门」——因当前产品/模型未定义无歧义的活动组织→默认部门选取规则。若未来改为「相对活动组织解析 DEPT 形规则」（选项 A），必须先书面定义选取规则或 fail-closed 策略，并补 `DataScopeResolverServiceTest` + 至少一条控制面读链测试。实现与注释见 `DataScopeResolverService`；回归见 `DataScopeResolverServiceTest`（`resolve_contract_b_*`、`resolve_menu_module_uses_scoped_effective_roles_when_dept_active_scope`、`resolve_dict_module_uses_scoped_effective_roles_when_org_active_scope`、`resolve_dict_module_uses_scoped_effective_roles_when_dept_active_scope`）、`UserServiceImplTest`（`users_read_chain_contract_b_*`）、`MenuServiceImplTest`（`listShouldApplyCreatedByFilterWhenDataScopeRestricted`）、`ResourceServiceImplTest`（`findTopLevelDtos_returnsEmptyWhenRestrictedDataScopeYieldsNoVisibleCreators`）、`DictTypeServiceImplTest`（`query_withoutDataScopeRestriction_usesPagedRepositoryPath`、`query_should_keep_platform_types_and_filter_hidden_tenant_types_under_data_scope`）、`DictItemServiceImplTest`（`query_withoutDataScopeRestriction_usesPagedRepositoryPath_forTenantDictType`）、`SchedulingServiceTenantScopeTest`（`listDags_contract_b_*`）。

---

## 3. 扩面准入条件

一个模块只有同时满足下面条件，才适合接入 `@DataScope`。

### 3.1 必须先有稳定的可见性语义

至少满足以下之一：

- 资源有稳定 owner 字段，例如 `created_by`、`user_id`、`username`；
- 资源天然从属于组织/部门/租户，可映射到 `unitField`；
- 资源存在平台基线与租户 overlay 的明确覆盖规则。

如果当前连“谁拥有这条记录、谁应该能看见它”都说不清，就不要先接 `@DataScope`。

### 3.2 必须先分清读路径和写路径

- `@DataScope` 优先用于**读路径收敛**；
- 写路径要继续由租户守卫、权限 Guard、业务校验单独控制；
- 不要把“读权限过滤”误当成“写权限保护”。

### 3.3 必须先确认不会误伤运行时公共能力

以下场景不能未经设计直接套 `@DataScope`：

- 运行时菜单树；
- 登录、发 token、刷新 token、会话续期；
- 平台模板初始化与租户 bootstrap；
- 需要平台基线兜底的配置加载；
- 面向全租户公共可见的字典/系统元数据查询。

如果某个查询既服务管理端，又服务运行时，需要先拆职责，再决定是否只对管理端查询接入 `@DataScope`。

### 3.4 必须先有回填与默认策略

接入前需要明确：

- 存量数据的 owner / `created_by` 如何回填；
- 默认管理员是否需要 `READ=ALL` seed；
- 无规则时是否允许回落到 `SELF`；
- overlay 被过滤掉时，是否需要回退显示平台基线值。

---

## 4. 推荐接入顺序

### 步骤 1：先定义模块可见性语义

至少写清楚这 4 个问题：

1. 该模块按谁可见：本人、组织、部门、租户管理员、平台管理员，还是创建者？
2. 该模块是否存在平台基线 / 租户 overlay？
3. 该模块是否允许平台管理员默认看全量？
4. 该模块哪些查询属于管理端读路径，哪些属于运行时公共读路径？

### 步骤 2：补齐数据模型

优先补这些字段或语义：

- `tenant_id`
- `created_by` 或等价 owner 字段
- 与组织/部门的稳定关联字段
- overlay 标识或平台基线识别方式

如果这些字段不存在，不要用“当前登录人 + 模糊猜测”临时拼规则。

### 步骤 3：只在 Service 读方法接入

优先在管理端查询 Service 方法标注：

```java
@DataScope(module = "xxx", accessType = "READ")
```

不要一上来在 Controller、Repository 或运行时公共入口乱加。

### 步骤 4：把 `ResolvedDataScope` 真正下推到查询

至少明确一种可执行映射：

- `ownerField`
- `unitField`
- 平台基线 + overlay 回退逻辑

如果最后 SQL / Specification 不会变，那就不算真正接入。

### 步骤 5：补种子与迁移

至少补：

- owner / `created_by` 存量回填 migration；
- 默认管理员 `READ=ALL` seed；
- 新建记录时写入 owner；
- 必要时的平台基线保护数据。

### 步骤 6：补测试

至少覆盖：

- `SELF`
- `ORG` / `DEPT`
- `CUSTOM`
- 默认管理员 `READ=ALL`
- 平台基线 / overlay 可见性
- 不影响运行时公共查询

---

## 5. 模块类型与推荐策略

### 5.1 owner 型模块

典型模块：

- `resource`
- `menu`
- `scheduling`
- `export`

推荐策略：

- 以 `created_by`、`user_id`、`username` 这类 owner key 为主；
- 先保证新数据写 owner；
- 再补存量回填；
- 最后把管理列表接入 `@DataScope`。

### 5.2 unit 型模块

典型模块：

- `org`
- 部分用户管理查询

推荐策略：

- 以组织/部门树和 `user_unit` 关系为主；
- 树查询要保留祖先路径，不要只返回叶子命中节点；
- 成员查询要同时考虑可见 unit 和可见 user。

### 5.3 baseline + overlay 型模块

典型模块：

- `dict`

推荐策略：

- 平台基线永远不能因为 overlay 被过滤而整条消失；
- overlay 不可见时，要评估是否应回退显示基线值；
- 不要把“租户 overlay 不可见”错误实现成“平台基线也不可见”。

---

## 6. 明确禁止的做法

以下做法禁止：

- 没有 owner / unit 语义时，硬给模块接 `@DataScope`；
- 把运行时菜单树、登录链路、发 token 链路直接按 owner 过滤；
- 只加注解，不改查询谓词；
- 只改新数据，不做存量回填；
- 不补 `READ=ALL` seed，导致控制面在无规则环境下意外缩成 `SELF`；
- 用“当前用户创建的记录通常就是他该看的记录”替代正式可见性设计。

---

## 7. 最小验证清单

每次扩面至少做下面验证：

1. 同一查询在 `SELF / ORG(DEPT) / CUSTOM / ALL` 下返回结果不同。
2. 默认管理员在 seed 完整时不会被意外收窄。
3. 新建记录会写入 owner / `created_by`。
4. 存量数据回填后不会整体消失。
5. 运行时公共入口不受影响。
6. 前端搜索、分页、导出参数与后端契约一致。

推荐最少测试层级：

- 1 个 Service 测试；
- 1 个集成测试或 JPA 测试；
- 1 个前端页面或 API 测试。

---

## 8. 暂不建议直接扩面的模块

如果某个模块仍处于以下状态，应先补模型，再谈 `@DataScope`：

- 没有 owner 字段；
- 没有组织/部门归属；
- 平台模板、副本、overlay 关系不明确；
- 既服务管理端又服务运行时，但尚未拆出独立查询；
- 需要先完成租户生命周期、平台模板、审计或配额规则收口。

简化原则：

- 先把“能否定义正确规则”做对；
- 再把“过滤范围做广”做快。

---

## 9. 与主线文档的关系

- 需要理解模型边界：看 `TINY_PLATFORM_AUTHORIZATION_MODEL.md`
- 需要判断当前哪些模块已接、下一步做什么：看 `TINY_PLATFORM_AUTHORIZATION_TASK_LIST.md`
- 需要做模块盘点：看 `TINY_PLATFORM_MODULE_GAP_ANALYSIS.md`

本文件只负责给出一个统一、保守、可执行的扩面方法，避免未来在不同模块继续发明第二套接入标准。

---

## 10. 前端控制面与活动租户（正式边界，2026-03-29）

租户侧控制面页面（依赖后端 `TenantContext` + `X-Tenant-Id` / localStorage 活动租户与 `@DataScope` 的列表）在监听 `active-scope-changed` 时：

- **必须**通过 `shouldReloadTenantControlPlaneOnActiveScopeChange()`（`tiny-oauth-server/src/main/webapp/src/utils/activeScopeEvents.ts`）判断是否重拉。该函数在 `getActiveTenantId()` 为空时返回 `false`。
- **含义（非缺陷）**：无活动租户 = 平台态或未选租户 / 已清理上下文；此时**不**因 Header 切换 ORG/DEPT scope 而发起租户数据面重拉，避免前端用本地状态推断替代后端 `TenantContext` 与 active scope 的单一真相源。
- **验证**：`activeScopeEvents.test.ts`（有/无活动租户）；租户字典 `dictType.test.ts` / `dictItem.test.ts`（scope 事件与 API 调用次数）。

新增同类页面应复用同一 helper，禁止复制 `if (!getActiveTenantId()) return` 的变体而不读文档。

---

## 11. 构建卫生台账（Maven / JaCoCo / Vite，2026-03-29）

| 类别 | 本轮处理 | 仍保留的 warning / 噪声 | 原因与后续唯一建议动作 |
| --- | --- | --- | --- |
| **Javac** | `DataScopeSpecification`：已废弃的 `Specification.where(null)` 改为 `(root,q,cb)->cb.conjunction()`。`DefaultSecurityConfig`：`AntPathRequestMatcher` 替换为 `PathPatternRequestMatcher`（CSRF 路径匹配）。 | `AuthorizationServerConfig` 的 `OAuth2AuthorizationServerConfiguration.applyDefaultSecurity` 过时；`LongAllowlistModule` 的 `sun.misc.Unsafe`；`OAuth2Password*` 等类中 `AuthorizationGrantType.PASSWORD` 过时；`IdempotentConsoleRepository` 等文件的 deprecation 摘要行。 | 授权服务器与密码扩展、Jackson Unsafe 优化属 **架构/上游升级** 或高回归成本；**延期**：跟 Spring Authorization Server 大版本迁移与 OAuth2 密码模式策略一并评审，禁止为消告警做伪修复。 |
| **JaCoCo** | 文档化。 | 若在同一 `target/` 上多次运行不同 surefire 子集，可能出现 `execution data does not match` / 报告与源码行不一致。 | **触发条件**：复用旧 `jacoco.exec` 或增量编译与 exec 不同步。**为何不影响结论**：`mvn test` 单次会话内 exec 与 class 一致时报告有效。**唯一建议动作**：生成覆盖率或排查 mismatch 前执行 `mvn clean test`（或至少 `clean` 后再跑目标模块全量 test）。 |
| **Vite build** | `vite.config.ts` 设置 `build.chunkSizeWarningLimit: 3072`（KB），消除默认 500kB 阈值对 ~2.8MB 主 chunk 的重复告警。 | `(!) ... dynamically imported by ... but also statically imported`（如 `logger.ts`、`traceId.ts`、`tenant.ts`、`auth.ts`、`router` 等）仍可能出现。 | **原因**：部分模块同时被静态与动态 `import()` 引用，需路由级拆包或统一导入策略才能根治。**延期**：按需做 layout/route lazy + 统一 trace/logger 引导，非本轮；提高阈值**不等于**已做 code-split，仅减少审计噪声。 |

**全量扩展台账**：`TINY_PLATFORM_BUILD_TECH_DEBT_LEDGER.md`（§0 问题分级、§2.1 Maven 同模块并发、`@MockBean` 等）。**dev-bootstrap 退出码**：`TINY_PLATFORM_TESTING_PLAYBOOK.md` §1.2。
