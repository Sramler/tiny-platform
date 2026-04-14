# Tiny Platform 登录链路与权限模型测试矩阵

> 目标：以“脚本门禁 + integration + 少量真实 E2E”的分层策略，锁住登录主链、权限主断点与数据权限默认安全边界。  
> 状态术语口径：按 `docs/TINY_PLATFORM_AUTHORIZATION_DOC_MAP.md` 的术语字典（已落地 / 已闭合 / 待闭合）解释。  
> 当前文档角色：执行清单（可直接分配给实现与审计）。

---

## 1. 范围与分层

- **P0（本周必须）**：登录链路与统一守卫关键断点，优先阻断高风险回归。
- **P1（紧随 P0）**：登录后权限生效一致性（菜单下发、按钮门控、版本漂移）。
- **P2（闭环）**：`@DataScope` 运行态边界与“默认不放大权限”。

测试层级说明：

- `script`：快速门禁，判断主链是否断裂。
- `integration`：定位后端链路具体失效点。
- `e2e-smoke`：验证真实整链路可用，不追求全量 UI 覆盖。

---

## 2. 执行任务表

> 责任人默认占位为 `TBD`，由排期时填写。  
> 预计工时为单人净工时估算，含用例实现与最小稳定性修正。

| 测试ID | 优先级 | 测试名 | 前置数据 | 核心断言 | 测试层级 | 认证路径(Session/JWT) | 失败定位路径 | 责任人 | 预计工时 | 本轮执行结果 |
|---|---|---|---|---|---|---|---|---|---|---|
| AUTH-P0-01 | P0 | 平台管理员登录成功_上下文契约完整 | 平台管理员账号可用，`tenant_user`/`role_assignment` 就绪 | 登录成功；会话/claims 同时包含 `activeTenantId`、`activeScopeType`、`permissions`、`permissionsVersion` 且值合法 | integration + e2e-smoke | Session + JWT | 认证解析链（`AuthUserResolutionService`、`UserDetailsServiceImpl`）-> claims 组装链（JWT/session） | TBD | 0.5d | PASS (2026-03-27) |
| AUTH-P0-02 | P0 | 租户管理员登录成功_上下文契约完整 | 租户管理员账号可用，租户状态 ACTIVE，assignment 有效 | 登录成功；`activeTenantId` 命中目标租户；`activeScopeType=TENANT`；`permissions` 非空；`permissionsVersion` 存在 | integration | Session + JWT | `TenantContextFilter` -> assignment 展开链（`EffectiveRoleResolutionService`） | TBD | 0.5d | PASS (2026-03-27) |
| AUTH-P0-03 | P0 | FROZEN租户登录拒绝 | 目标租户切换为 FROZEN | 登录被拒绝，无法进入业务会话 | integration + e2e-smoke | Session + JWT | 生命周期策略链（`TenantLifecycleReadPolicy`/相关 guard）-> 认证入口 | TBD | 0.5d | PASS (2026-03-27) |
| AUTH-P0-04 | P0 | 无membership登录拒绝 | 用户存在但无有效 `tenant_user` | 登录被拒绝，不签发可用会话 | integration | Session + JWT | membership 解析链（`tenant_user`） | TBD | 0.5d | PASS (2026-03-27) |
| AUTH-P0-05 | P0 | 低权限用户登录后管理写拒绝 | 普通用户有 membership，但不具备管理写权限 | 登录成功；管理写接口 403；只读接口按权限可访问 | integration + e2e-smoke | Session + JWT | `role_assignment -> permission` 展开链 -> AccessGuard / `@PreAuthorize` | TBD | 0.5d | PASS (2026-03-27) |
| AUTH-P0-06 | P0 | 已登记api_endpoint_ALLOW | `api_endpoint` 已登记，requirement 满足 | 请求通过（2xx） | integration | JWT（API优先，可补Session） | `ApiEndpointRequirementFilter` -> `ResourceServiceImpl.evaluateApiEndpointRequirement` | TBD | 0.5d | PASS (2026-03-27) |
| AUTH-P0-07 | P0 | 已登记api_endpoint_DENY | `api_endpoint` 已登记，requirement 不满足或 permission disabled | 请求拒绝（403） | integration | JWT（API优先，可补Session） | evaluator requirement 分支、permission.enabled 分支 | TBD | 0.5d | PASS (2026-03-27) |
| AUTH-P0-08 | P0 | 未登记api_endpoint显式拒绝 | 选择明确未登记接口，且不补 requirement | 统一守卫返回 403（CARD-13B 起未登记 endpoint fail-closed） | integration | JWT（API优先，可补Session） | `ApiEndpointRequirementFilter` 未登记分支判定 | TBD | 0.5d | PASS (2026-04-11) |
| AUTH-P1-01 | P1 | 菜单下发一致性_permission_requirement | 同一用户构造两套权限场景（有/无对应 requirement） | 登录后菜单树按 permission/requirement 正确下发（节点显示/隐藏一致） | integration(API) + e2e-smoke | Session（前端主路径） | 菜单运行态查询链（`MenuServiceImpl`）-> requirement evaluator | TBD | 1d | PASS (2026-03-27) |
| AUTH-P1-02 | P1 | 按钮门控一致性_ui_action | 页面写按钮对应 `ui_action` requirement 可控 | 按钮按 `ui_action` requirement 正确隐藏/禁用；无本地常量兜底放行 | e2e-smoke + 前端单测 | Session（前端主路径） | 前端 runtime 门控 -> 后端 `ui_action` 返回链 | TBD | 1d | PASS (2026-03-27) |
| AUTH-P1-03 | P1 | permissionsVersion漂移后权限刷新 | 登录后修改 assignment / role_permission | 旧会话触发版本漂移后权限收敛到新状态，无旧权限残留 | integration | Session + JWT | `PermissionVersionService` + JWT/session 刷新链 | TBD | 1d | PASS (2026-03-27) |
| AUTH-P1-04 | P1 | 审计记录可检索性_allow_deny | 开启审计并执行 allow/deny 动作 | 可按关键字段检索到事件（含 decision/reason） | integration | N/A（服务侧） | `AuthorizationAuditService` / controller 查询参数链 | TBD | 0.5d | PASS (2026-03-27) |
| AUTH-P1-05 | P1 | 非法_ORG_DEPT_active_scope_fail_closed | session 或 Bearer 携带非法 ORG/DEPT active scope（缺 id、跨租户、类型不匹配、非成员、bearer/session 成对冲突、**Session 仅 `activeScopeId` 与 JWT 显式成对不一致**、JWT 孤儿 `activeScopeId` 等） | **不得 500**；**成对解析**：禁止 JWT `activeScopeType` + Session `activeScopeId` 混拼；JWT 无 `activeScopeType` 时 ORG/DEPT id 仅 Session；二者成对不一致 → **401**（有 Bearer）并重置 session scope 为 TENANT + 清理 `SecurityContext`；无 Bearer 非法 → **403** + 清理 `SecurityContext`；`POST /sys/users/current/active-scope` 非法 **400/403**，缺依赖 **503**；**M4 写成功** 返回 `tokenRefreshRequired`/`newActiveScope*`，**写后旧 JWT（显式 scope）与 Session 不一致** → 下一请求 **401** `invalid_active_scope`（见矩阵 §8） | unit/integration | Session + JWT | `TenantContextFilter.resolvePairedActiveScope` / `handleInvalidActiveScope`；`UserController.switchActiveScope`；`TenantContextFilterTest`（含 id-only session 残留与 Bearer 401 后 session 安全上下文清理） | TBD | 0.5d | PASS (2026-03-28) |
| AUTH-P1-06 | P1 | Session_only_当前用户上下文契约 | 已登录 Web Session；无 Bearer；Session 中存在 `activeTenantId` / `activeScopeType` / `activeScopeId` | `GET /sys/users/current` 等 Session 主路径返回的当前用户信息包含 Session 中的 `activeScopeType` / `activeScopeId` 与 `permissionsVersion`；不依赖 Bearer claims | unit/integration | Session | `UserController.getCurrentUser`；Session 读取链；必要时配合 `TenantContextFilter` session-only 路径 | TBD | 0.25d | PASS (2026-03-29) |
| AUTH-P1-07 | P1 | Bearer_explicit_scope_pair_contract | Bearer 含 `activeTenantId` + 显式 `activeScopeType`（ORG/DEPT 还需 `activeScopeId`）；Session 可为空或存在一致残留 | JWT 显式 type 时，active scope **整对来自 JWT**；Session 仅可一致，冲突时 **401** `invalid_active_scope`，并重置 Session scope / 清理 `SPRING_SECURITY_CONTEXT` | unit | JWT + Session residue | `TenantContextFilter.resolvePairedActiveScope`；`resolveBearerScopeIdPair`；`handleInvalidActiveScope` | TBD | 0.25d | PASS (2026-03-29) |
| AUTH-P1-08 | P1 | Bearer_without_explicit_scopeType_uses_session_pair_or_rejects_orphan_claim | Bearer 含 `activeTenantId`，但不显式带 `activeScopeType`；Session 提供成对 scope；另备 JWT 孤儿 `activeScopeId` 反例 | JWT 未显式带 type 时，active scope **整对来自 Session**；若 JWT 只带 `activeScopeId` 不带 type，则按孤儿声明 **401** `invalid_active_scope` 拒绝 | unit | JWT + Session | `TenantContextFilter.resolvePairedActiveScope`；`bearerHasOrphanActiveScopeIdClaim`；Session pair fallback | TBD | 0.25d | PASS (2026-03-29) |
| AUTH-P1-09 | P1 | OIDC_prompt_none_unauthenticated_returns_login_required_not_form_login_failure | 已登记 OIDC client 与 redirect_uri；未建立可用登录态（可存在仅用于租户解析的匿名 session）；发起 `/oauth2/authorize?...prompt=none` | 返回 OAuth/OIDC 语义的 `login_required` 到 `redirect_uri` / `silent-renew.html`，而不是误判成普通表单登录失败或 `/login?error=true&message=...` | integration + e2e-smoke | OIDC browser / no authenticated session | `authorizationServerSecurityFilterChain` → `OAuth2AuthorizationEndpointFilter`；silent renew 主线 | TBD | 0.5d | PASS (2026-03-29) |
| AUTH-P2-01 | P2 | DataScope_SELF | 用户仅 SELF 规则 | 列表仅返回本人数据 | integration | JWT（API主路径） | `DataScopeResolverService` -> `DataScopeSpecification` | TBD | 0.5d | PASS (2026-03-27) |
| AUTH-P2-02 | P2 | DataScope_ORG_DEPT | 主部门与组织树数据齐备；**active scope** 下 **Contract B**：ORG scope 时 ORG 形规则锚活动组织、DEPT 形规则锚主部门（与 TENANT 一致）；DEPT scope 锚活动部门 | ORG/DEPT 返回符合层级边界的数据；切换 scope 后控制面列表（用户 / 导出任务 / **DAG 列表** 等）重拉后端；**export** 可读列表在库侧 `Specification`（`ExportTaskServiceReadableQueryIntegrationTest`）；`resolve_contract_b_*` / `users_read_chain_contract_b_*` / `listDags_contract_b_*` 锁住 Contract B | integration + 单测 | JWT（API主路径） | `DataScopeResolverService`；`SchedulingService.listDags`；`ExportTaskService.findReadableTasks`（`tenant_id`+owner OR） | TBD | 0.5d | PASS (2026-03-28 Contract B) |
| AUTH-P2-06 | P2 | Scheduling_DagRun_history_operational_contract | 租户内 DAG 运行历史页；与 DAG 列表的 DataScope 边界区分 | **正式不接入** `@DataScope`：`getDagRuns` 不读 `DataScopeContext`；`DataScopeContext=selfOnly` 时仍能拉取所选 DAG 的 Run（`getDagRuns_contract_*`）；`DagHistory` 展示说明文案且不监听 `active-scope-changed`（`DagHistory.test.ts`）；`Dag.vue` 仍监听 scope 并重拉列表 | unit | Session + JWT | `SchedulingService.getDagRuns`；`DagHistory.vue`；`SchedulingServiceTenantScopeTest`；`Dag.test.ts` | TBD | 0.25d | PASS (2026-03-28) |
| AUTH-P2-07 | P2 | Control_plane_menu_resource_dict_active_scope_evidence | 菜单/资源/租户字典控制面与 Header active scope | **后端**：`MenuServiceImpl` / `ResourceServiceImpl` 树读与 `DictTypeServiceImpl.query` / `DictItemServiceImpl.query` 消费 `DataScopeContext`（`MenuServiceImplTest`、`ResourceServiceImplTest`、`DictTypeServiceImplTest`、`DictItemServiceImplTest.query_withoutDataScopeRestriction_*` 等）；`DataScopeResolverService` 对 `menu`/`dict` 在 ORG/DEPT active scope 下走成对角色解析（`resolve_menu_module_*`、`resolve_dict_module_*`）。**前端**：`Menu.vue`/`resource.vue`/`dictType.vue`/`dictItem.vue` 监听 `active-scope-changed` 重拉；字典页**仅在有活动租户**时重拉（`dictType.test.ts`/`dictItem.test.ts`）。 | unit | Session + JWT | `MenuServiceImpl`；`ResourceServiceImpl`；`DictTypeServiceImpl`；`DataScopeResolverService`；对应 `.vue` | TBD | 0.25d | PASS (2026-03-28) |
| AUTH-P2-03 | P2 | DataScope_CUSTOM | `role_data_scope_item` 含 ORG/DEPT/USER 明细 | 仅命中 CUSTOM 明细范围数据 | integration | JWT（API主路径） | CUSTOM item 加载链 | TBD | 0.5d | PASS (2026-03-27) |
| AUTH-P2-04 | P2 | DataScope_READ_ALL | READ=ALL 规则存在 | 返回全租户可见范围（符合模块语义） | integration | JWT（API主路径） | scope 优先级合并逻辑 | TBD | 0.5d | PASS (2026-03-27) |
| AUTH-P2-05 | P2 | DataScope_无规则不放大 | 移除模块 `role_data_scope` 规则 | 不允许意外放大为全量；回退最小权限（SELF/空集按模块约定） | integration + e2e-smoke(抽样) | JWT（API主路径） | resolver 默认分支与查询拼装链 | TBD | 0.5d | PASS (2026-03-27) |

---

## 2.1 Script 门禁映射（最小执行集）

| 脚本ID | 脚本 | 目标覆盖 | 对应任务ID | 执行时机 | 本轮执行结果 |
|---|---|---|---|---|---|
| AUTH-SCRIPT-01 | `tiny-oauth-server/scripts/verify-platform-login-auth-chain.sh` | 登录主链可用性、认证上下文基础契约 | `AUTH-P0-01` ~ `AUTH-P0-05` | 本地提交前 + CI PR | PASS (2026-03-27) |
| AUTH-SCRIPT-02 | `tiny-oauth-server/scripts/verify-platform-dev-bootstrap.sh` | 开发环境 bootstrap + 登录链连通性，避免环境性假阳性/假阴性 | `AUTH-P0-*`（环境前置） | 每日首轮验证 + CI 夜间 | PASS (2026-03-27) |

## 2.2 Session / Bearer 来源矩阵覆盖映射

> 目的：把 [TINY_PLATFORM_SESSION_BEARER_AUTH_MATRIX.md](./TINY_PLATFORM_SESSION_BEARER_AUTH_MATRIX.md) 的 M0–M5 运行态口径映射到当前测试证据，避免“文档有矩阵、门禁没入口”。

| 矩阵场景 | 当前主要证据 | 结论 |
| --- | --- | --- |
| M0 匿名请求 | `TenantContextFilterTest.shouldRejectMissingActiveTenantForAuthorizeRequest_withoutRedirectLoop`; `AuthenticationFlowE2eProfileIntegrationTest.unauthenticatedPromptNoneShouldReturnLoginRequiredToRedirectUri` | **已覆盖** |
| M1 仅 Session | `UserControllerTest.getCurrentUser_should_include_active_scope_and_permissionsVersion_when_session_has_scope`; `DefaultSecurityConfigUserEndpointIntegrationTest.currentUserShouldAllowSessionOnlyRequestWithActiveScope`; `DefaultSecurityConfigUserEndpointIntegrationTest.currentActiveScopeSwitchShouldSucceedWithSessionOnlyAuthentication`; `AUTH-P0-01` / `AUTH-P0-02` 登录链 | **已覆盖** |
| M2 仅 Bearer，JWT 显式带 scope type | `TenantContextFilterTest.shouldRejectBearerOrgScopeWhenUnitInvalid_with401Not500` 等 | **已覆盖** |
| M3 仅 Bearer，JWT 未显式带 scope type | `TenantContextFilterTest.shouldUseSessionPairedScopeWhenBearerOmitsActiveScopeType`; `shouldRejectOrphanBearerActiveScopeIdWithoutType_with401` | **已覆盖** |
| M4 Bearer + Session 并存且一致 | `TenantContextFilterTest.shouldAcceptBearerAndSessionWhenPairedActiveScopeMatches`; `shouldAcceptWhenBearerOrgMatchesSessionScopeIdOnlyWithoutType`; `DefaultSecurityConfigUserEndpointIntegrationTest.currentUserShouldAllowWhenBearerAndSessionScopeMatch`；`POST /sys/users/current/active-scope` M4 写成功（`currentActiveScopeSwitchShouldSucceedWithM4BearerAndRequireTokenRefresh`）；写后旧 JWT fail-closed + 刷新后恢复（`currentActiveScopeM4WriteThenStaleBearerJwtFailsUntilRefreshed`） | **已覆盖** |
| M5 Bearer + Session 并存但冲突 | `TenantContextFilterTest.shouldReject401WhenBearerAndSessionActiveScopeConflict`; `shouldReject401WhenBearerOrgPairConflictsWithSessionOrgId_notMixBearerTypeWithSessionId`; `shouldClearSessionSecurityContextOnBearerInvalidActiveScope`; `DefaultSecurityConfigUserEndpointIntegrationTest.sysUsersProbeShouldFailClosedWhenBearerAndSessionScopeConflict`; `DefaultSecurityConfigUserEndpointIntegrationTest.currentUserShouldFailClosedWhenBearerAndSessionScopeConflict`; `DefaultSecurityConfigUserEndpointIntegrationTest.currentActiveScopeSwitchShouldFailClosedWhenBearerAndSessionScopeConflict` | **已覆盖** |

### 2.3 User 控制面：M4 **读** vs M4 **写**（正式契约证据）

> 目的：避免把 §4 矩阵里的「M4 放行」误解成「所有 `/sys/users/**` 同一行为」。详细语义见 [TINY_PLATFORM_SESSION_BEARER_AUTH_MATRIX.md](./TINY_PLATFORM_SESSION_BEARER_AUTH_MATRIX.md) §8。

| 契约 | 端点 | 测试证据（integration / unit） |
| --- | --- | --- |
| **M4 读**：只读当前用户，不写 Session scope | `GET /sys/users/current` | `DefaultSecurityConfigUserEndpointIntegrationTest.currentUserShouldAllowSessionOnlyRequestWithActiveScope`（M1）；`currentUserShouldAllowWhenBearerAndSessionScopeMatch`（M4 读 + JWT）；`currentUserShouldFailClosedWhenBearerAndSessionScopeConflict`（M5）；`UserControllerTest.getCurrentUser_should_include_active_scope_and_permissionsVersion_when_session_has_scope` |
| **M4 写**：改 Session 中 `AUTH_ACTIVE_SCOPE_*`，Bearer 写需 `tokenRefreshRequired` | `POST /sys/users/current/active-scope` | `currentActiveScopeSwitchShouldSucceedWithSessionOnlyAuthentication`（`tokenRefreshRequired=false`）；`currentActiveScopeSwitchShouldSucceedWithM4BearerAndRequireTokenRefresh`（M4 写 + `true`）；`currentActiveScopeM4WriteThenStaleBearerJwtFailsUntilRefreshed`（写后旧 JWT → M5；对齐后刷新 JWT → 读成功）；`currentActiveScopeSwitchShouldFailClosedWhenBearerAndSessionScopeConflict`（M5）；`UserControllerTest.switchActiveScope_should_succeed_for_tenant_org_dept_and_fail_closed_on_invalid_input` |
| **前端**：`tokenRefreshRequired` 后 silent renew 再拉用户 | Vue：`switchActiveScope` / `refreshTokenAfterActiveScopeSwitch` / `HeaderBar.confirmSwitchScope` | `src/api/user.test.ts`、`src/auth/auth.test.ts`、**`src/layouts/HeaderBar.test.ts`**；**real-link（chromium）**：`e2e/real/active-scope-token-refresh.spec.ts`（POST `tokenRefreshRequired` + 真实 `prompt=none` + modal 关闭/无 warning + Bearer `GET /sys/users/current` 非 M5；`e2e/.auth/scheduling-tenant-user.json` 来自 globalSetup 派生租户态身份） |

---

## 2.4 CI 门禁映射（real-link）

| 门禁 | 入口 | 覆盖重点 |
| --- | --- | --- |
| Nightly real-link | `.github/workflows/verify-scheduling-real-e2e.yml` | `active-scope-token-refresh`（租户态身份 + `tokenRefreshRequired` + `prompt=none` + Bearer 复验）、`platform-vue-login`、调度主链、只读拒绝、租户生命周期冻结 |
| 本地最小复现 | `npx playwright test -c playwright.real.config.ts --project=chromium e2e/real/active-scope-token-refresh.spec.ts` | 先验证租户态前置（token claims + `/sys/users/current` 含 `activeTenantId`）再进入 active-scope 写链 |

---

## 3. 执行顺序建议

1. **第 0 批（先做）**：`AUTH-SCRIPT-*` 先接入本地与 CI 快速门禁。
2. **第 1 批（当周）**：`AUTH-P0-*` 全部落地并接入门禁。
3. **第 2 批（次周）**：`AUTH-P1-01` ~ `AUTH-P1-04`，完成“登录后权限生效 + 审计可检索性”。
4. **第 3 批**：`AUTH-P2-*` 全量，完成数据权限边界闭环。

---

## 4. 验收口径

- **P0 全绿**：可宣称“登录主链 + 权限主断点已锁住”。
- **P1 全绿**：可宣称“登录后权限生效链路已基本闭环”。
- **P2 全绿**：可宣称“数据权限默认安全边界已可回归验证”。

---

## 5. 关联文档

- `docs/TINY_PLATFORM_AUTHORIZATION_DOC_MAP.md`
- `docs/TINY_PLATFORM_AUTHORIZATION_MODEL.md`
- `docs/TINY_PLATFORM_AUTHORIZATION_LAYERED_MODEL.md`
- `docs/TINY_PLATFORM_SESSION_BEARER_AUTH_MATRIX.md`
- `docs/TINY_PLATFORM_API_ENDPOINT_GUARD_COVERAGE.md`
- `docs/TINY_PLATFORM_DATASCOPE_EXPANSION_GUIDE.md`
- `docs/TINY_PLATFORM_TESTING_PLAYBOOK.md`
- `docs/TINY_PLATFORM_AI_TEST_TASK_TEMPLATE.md`

---

## 6. 本轮执行命令记录（2026-03-27）

```bash
bash tiny-oauth-server/scripts/verify-platform-login-auth-chain.sh
DB_PASSWORD='Tianye0903.' bash tiny-oauth-server/scripts/verify-platform-dev-bootstrap.sh

mvn -pl tiny-oauth-server -Dtest=MultiAuthenticationProviderTest,PartialMfaFormLoginIntegrationTest,TenantContextFilterTest,AuthUserResolutionServiceTest,ResourceControllerApiEndpointTemplateUriIntegrationTest,ResourceControllerRbacIntegrationTest -DfailIfNoTests=false test

mvn -pl tiny-oauth-server -Dtest=PermissionVersionServiceTest,AuthorizationAuditControllerTest,AuthorizationAuditServiceTest,MenuServiceImplTest -DfailIfNoTests=false test
npm --prefix tiny-oauth-server/src/main/webapp run test:unit -- src/views/menu/Menu.test.ts src/views/user/user.test.ts src/views/role/role.test.ts src/views/tenant/Tenant.test.ts src/views/resource/resource.test.ts src/views/audit/AuthorizationAudit.test.ts

mvn -pl tiny-oauth-server -Dtest=DataScopeResolverServiceTest,UserServiceImplTest,ResourceServiceImplTest,OrganizationUnitServiceTest,UserUnitServiceTest -DfailIfNoTests=false test

mvn -pl tiny-oauth-server -Dtest=AuthenticationFlowE2eProfileIntegrationTest\\$OidcPromptNoneBehaviour -DfailIfNoTests=false test
mvn -pl tiny-oauth-server -Dtest=AuthenticationFlowE2eProfileIntegrationTest -DfailIfNoTests=false test
mvn -pl tiny-oauth-server -Dtest=DefaultSecurityConfigUserEndpointIntegrationTest -DfailIfNoTests=false test
```
