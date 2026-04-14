# Tiny Platform 授权遗留彻底下线计划（不再兼容旧模型）

> 状态更新：本文件保留为 **遗留清退计划 / 历史快照 / 非当前运行态真相源**。  
> 当前仓库的授权主链、当前完成度与文档读取入口，请优先以 [TINY_PLATFORM_AUTHORIZATION_DOC_MAP.md](./TINY_PLATFORM_AUTHORIZATION_DOC_MAP.md)、[TINY_PLATFORM_AUTHORIZATION_MODEL.md](./TINY_PLATFORM_AUTHORIZATION_MODEL.md) 与 [TINY_PLATFORM_AUTHORIZATION_TASK_LIST.md](./TINY_PLATFORM_AUTHORIZATION_TASK_LIST.md) 为准。  
> 本文中若出现 `resource.permission`、`user_role`、`default` 租户或旧兼容清退步骤，应按“遗留收口计划 / 历史评估窗口”理解，不直接代表当前运行态仍按该口径工作。  
>
> 目标：按最新授权模型与权限标识符规范推进，**运行态不再兼容旧权限模型**（不再读 `user_role`、不再依赖 `user.tenant_id` 做授权）。  
> 说明：这是“面向未来架构负责”的收口计划，建议按多 PR / 多迭代推进，每一步都可验证、可回滚。

关联：

- `docs/TINY_PLATFORM_AUTHORIZATION_DOC_MAP.md`
- `docs/TINY_PLATFORM_AUTHORIZATION_MODEL.md`
- `docs/TINY_PLATFORM_LEGACY_COMPATIBILITY_INVENTORY.md`
- `docs/TINY_PLATFORM_AUTHORIZATION_TASK_LIST.md`
- `docs/TINY_PLATFORM_PERMISSION_IDENTIFIER_SPEC.md`

---

## 0. 收口原则（硬约束）

1. **运行态不做 alias**：旧权限码/旧模型只允许出现在“迁移脚本”中作为被替换值，不允许运行时继续兼容。
2. **安全失败（fail-closed）**：任何“旧数据导致权限缺失”的场景，必须明确失败并给出修复路径（补 migration/补回填），不得放宽鉴权。
3. **最小授权上下文不退化**：以 `activeTenantId + permissions (+ permissionsVersion)` 为准；本文后文若提及 `resource.permission`，均指当时迁移窗口的兼容输入口径，当前运行态功能权限真相源已演进为 `role_assignment -> role_permission -> permission`。

---

## 1. 下线 `ADMIN`（历史角色码）——已完成

> 目标：管理员角色码仅 `ROLE_ADMIN`，运行态不再把 `ADMIN` 当管理员。

- 前端：已移除 `ADMIN` 的权限判断。
- 后端：`LegacyAuthConstants` 已收口为只认 `ROLE_ADMIN`。

剩余工作（数据侧）：

- 清理角色表中 `code='ADMIN'` 的存量数据（迁移为 `ROLE_ADMIN` 或删除）。

验证：

- 使用仅含 `ADMIN` 的测试账号访问 `/sys/*` 应被拒绝。
- 使用 `ROLE_ADMIN` 的账号访问控制面能力应保持可用。

---

## 2. 下线 `user_role`（旧授权关系表）与 legacy 回退（最高优先级）

> 目标：运行态**只**从 `role_assignment` 解析有效角色，不再从 `user_role` 回退。

### 2.1 数据准备（必须先做）

- 确认已执行：`041-add-tenant-user-and-role-assignment.yaml`
  - `role_assignment` 已从 `user_role` 回填。
- 增加一条“数据一致性校验 SQL”（建议加入新的 Liquibase changelog 或运维脚本）：
  - 统计同租户下 `user_role` 与 `role_assignment` 角色集合差异（diff）。
  - 发现差异必须先补回填或修数据，禁止靠运行态回退兜底。

### 2.2 代码改造（分两步）

**Step A：关闭登录链路 legacy 回退（已做过一次启用回退，需要反向收口）**

- `AuthUserResolutionService.resolveUserInActiveTenant`：将 `allowLegacyFallback` 固定为 `false`。

**Step B：移除 role 解析中的 legacy 查询**

- 删除 `RoleAssignmentRepository.findLegacyRoleIdsByUserIdAndTenantId` 及其实现/SQL。
- `EffectiveRoleResolutionService` 删除 allowLegacyFallback 分支（或保留参数但强制 false，并标注待删）。

### 2.3 数据库下线（最终一步）

- Liquibase：
  - 删除 `user_role` 表（或先 rename 为 `user_role_legacy` 并设置只读保护，再最终 drop）。
  - 删除相关索引/外键。

验证：

- 单元测试/集成测试：
  - `EffectiveRoleResolutionService` 在 assignment 为空时必须返回空（而不是回退）。
  - 登录成功后的 authorities/permissions 完全来自 assignment 角色展开。
- 线上检查：
  - 任意角色分配变更后，`permissionsVersion` 触发 token/session 失效逻辑正确生效。

### 2.4 验证矩阵（登录→认证成功、拒绝分支、权限标识符与权限模型）

> 说明：下表是本计划编写/收口时期的验证矩阵快照，用于说明当时需要覆盖的风险面；是否已闭合、是否仍是当前主线阻塞，请回到 [TINY_PLATFORM_AUTHORIZATION_TASK_LIST.md](./TINY_PLATFORM_AUTHORIZATION_TASK_LIST.md) 与 [TINY_PLATFORM_AUTHORIZATION_MODEL.md](./TINY_PLATFORM_AUTHORIZATION_MODEL.md) 裁决。  

**必须先跑通以下用例**，再推进 2.3 数据库下线或后续步骤：

| 维度 | 说明 | 测试类/方法 |
|------|------|-------------|
| 登录→认证成功 | 表单登录后会话为完整认证、含 activeTenantId、authorities 含权限标识符 | `PartialMfaFormLoginIntegrationTest#formLoginWithMfaDisabled_sessionHasFullAuthAndActiveTenant` |
| 租户不匹配拒绝 | 已认证用户请求头/会话租户与当前租户不一致时 403 + tenant_mismatch | `AuthenticationFlowE2ERegressionTest` 中 tenantMatched=false 的矩阵用例 |
| 权限不足拒绝 | 仅有 ROLE_USER、无 system:user:list 时访问 /sys/users 返回 403 | `UserControllerRbacIntegrationTest#list_deniesWithoutReadAuthority` |
| 匿名拒绝 | 未认证访问受保护接口 401 | `DefaultSecurityConfigUserEndpointIntegrationTest#sysUsersProbeShouldRequireAuthentication`、`UserControllerRbacIntegrationTest#list_deniesAnonymous` |
| JWT 权限标识符 | access token claims 中 `permissions` 仅含规范权限码（domain:resource:action），不含 ROLE_* | `JwtTokenCustomizerTest` 中 accessToken 的 authorities/permissions 断言 |
| 权限模型（仅 assignment） | 角色解析仅走 role_assignment、无 user_role 回退 | `EffectiveRoleResolutionServiceTest`、`UserDetailsServiceImplTest`、`AuthUserResolutionServiceTest` |

执行示例（tiny-oauth-server）：

```bash
mvn -pl tiny-oauth-server test -Dtest=PartialMfaFormLoginIntegrationTest#formLoginWithMfaDisabled_sessionHasFullAuthAndActiveTenant
mvn -pl tiny-oauth-server test -Dtest=AuthenticationFlowE2ERegressionTest
mvn -pl tiny-oauth-server test -Dtest=UserControllerRbacIntegrationTest
mvn -pl tiny-oauth-server test -Dtest=JwtTokenCustomizerTest
mvn -pl tiny-oauth-server test -Dtest=EffectiveRoleResolutionServiceTest,UserDetailsServiceImplTest,AuthUserResolutionServiceTest
```

回滚：

- 回滚顺序：先回滚应用（恢复回退）-> 再回滚删表 migration（如已执行则需要备份或回放）。

---

## 3. 下线 `user.tenant_id` 作为授权依据（中高优先级）

> 目标：授权与可见性以 membership（`tenant_user`）与 activeTenantId 为准，`user.tenant_id` 仅保留为展示/审计字段或最终下线。

### 3.1 读路径全面切换（先于改表约束）

- 登录：只依赖 `tenant_user` membership 校验当前租户下是否 ACTIVE。
- 用户列表/详情：以 `tenant_user` 作为“租户内可见用户集合”的来源（已在部分路径实现）。
- 任何 “findByIdAndTenantId” 风格方法：逐步替换为 “membership 校验 + findById”。

### 3.2 双写期结束与字段收口

- 停止在 `UserServiceImpl.create/update` 中强制 `setTenantId(requireTenantId())`（改为仅在 membership 写入）。**已做**：`UserServiceImpl.create` 不再写入 `user.tenant_id`，仅调用 `ensureTenantMembership`。
- 数据库约束调整（需配套数据清理）：
  - `user.tenant_id` 改为可空。**已做**：Liquibase `044-user-tenant-id-nullable-stop-dual-write.yaml` 删除 `uk_user_tenant_username` 并将 `user.tenant_id` 改为 NULL。
  - 新增全局唯一：`uk_user_username (username)`。**已做**：Liquibase `045-user-username-global-unique.yaml`（执行前 sqlCheck 要求无重复 username）。

验证：

- 同一平台账号加入多个租户：切换 `activeTenantId` 后权限与可见性正确变化。
- 任一 query 不得因 `user.tenant_id` 缺失导致越权放行（必须 fail closed）。

---

## 4. 平台模板与 default 租户解耦（与 3、2 并行推进）

> 目标：TenantBootstrap 不再把 code=default 当平台语义载体，改为平台模板（`role_level/resource_level`）派生。

- 设计基线已在 `docs/TINY_PLATFORM_AUTHORIZATION_PHASE1_TECHNICAL_DESIGN.md` §2.2 给出。
- 实施进展：
  1. **已做**：落库 `role_level`/`resource_level`，`role`/`resource`/`role_resource` 的 `tenant_id` 可空；Liquibase `046-role-resource-level-platform-template.yaml`，CHECK 约束 PLATFORM⇒tenant_id NULL、TENANT⇒tenant_id NOT NULL。
  2. **已做**：`TenantBootstrapServiceImpl` 优先从平台模板（`tenant_id IS NULL`）克隆，若无模板则仅在**显式配置** `platform-tenant-code` 时回退到该租户 code 做一次回填（CARD-13C，不再默认 `default`）。
  3. **未做**：清理 default 租户中的“模板数据”依赖（可选，视是否迁移现有 default 数据为平台模板而定）。

验证：

- 新租户创建后：角色/资源/菜单来源可追踪为平台模板派生，而非 default 租户复制。

---

## 5. 建议的执行顺序（推荐）

1. **数据 diff 校验 + 修复**（保证 041 回填完整）
2. **关闭登录链路 legacy 回退**
3. **移除 legacy 查询代码**
4. **删表：user_role**
5. **完成 user.tenant_id 读路径替换后，再停双写与改约束**
6. **平台模板解耦 default（Bootstrap 迁移）**

---

## 6. 交付物清单（每个 PR 最少包含）

- 变更说明：影响面、风险、回滚方案
- 自动化验证：至少 1 条允许路径 + 1 条拒绝路径（跨租户/缺权限）
- 数据迁移：changelog + 校验 SQL（或校验脚本）
