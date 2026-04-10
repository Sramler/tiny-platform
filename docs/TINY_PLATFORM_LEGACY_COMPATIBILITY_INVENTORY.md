# Tiny Platform 遗留与兼容代码清单

> 与授权模型、租户、用户解析相关的遗留包袱，便于分阶段收口与下线。  
> 关联：`docs/TINY_PLATFORM_AUTHORIZATION_MODEL.md`、`docs/TINY_PLATFORM_PERMISSION_IDENTIFIER_SPEC.md`、`.agent/src/rules/92-tiny-platform-permission.rules.md`、`.agent/src/rules/93-tiny-platform-authorization-model.rules.md`。
> 说明：本文件只维护遗留来源、兼容现状与下线策略；不承担“当前完成度/优先级”的唯一维护职责。

---

## 0. 按权限模型与权限标识符的演进路径

- **权限真相源**：运行态功能权限已统一为 `role_assignment -> role_permission -> permission`；`resource.permission` 仅保留为兼容字段、运营可读字段和历史回填输入，新增逻辑不得再把它当唯一真相源。
- **控制面 Guard**：各 AccessGuard 仅使用规范权限码（如 `system:user:list`、`system:role:edit`、`idempotent:ops:view`、`dict:platform:manage`）。`LegacyAuthConstants` 已删除，ROLE_ADMIN 兜底已移除。平台级入口通过 `TenantContext.isPlatformScope()` 判定，不再查库。
- **平台级入口**：租户管理、幂等治理、字典平台等仅允许“当前平台租户 + 管理员”或“当前平台租户 + 对应规范码”（如 `system:tenant:list`、`idempotent:ops:view`、`dict:platform:manage`），见权限规范文档“控制面与平台级”小节。
- **角色码演进**：运行时代码已不再特殊识别 `ADMIN`；`ROLE_ADMIN` 只是当前 seed 中的管理员角色码。若历史环境仍残留 `ADMIN` 数据，应通过迁移脚本清理，而不是继续在运行时兼容。
- **导出能力**：导出接口已支持规范码 `system:export:view`（具备则可用导出）；查看全部任务/下载他人结果仍仅允许管理员，与当前导出权限语义一致。

### 0.1 兼容清退分桶（2026-03）

- **可立刻删除（已完成）**：旧菜单/资源运行时查询 helper，包括 `MenuServiceImpl.mergeTreeMenus(...)`、`MenuServiceImpl.resolveCurrentUsername()`、`MenuEntryRepository.findGranted*ByUsername*`、`ResourceRepository.findGrantedResourcesByUsername*`。
- **已迁 runtime 后删除（已完成本轮迁移）**：资源管理控制面的运行时按钮门控、API 访问判断、资源树与按类型读路径已迁到 `menu / ui_action / api_endpoint + requirement`，并据此删除对应 legacy 查询。
- **已完成退场**：`resource` 总表已从活动 schema 与运行时主线退出，并由 Liquibase 131 物理删除；`resource.permission` 仅作为历史迁移名词、旧文档字段来源和参考 SQL 口径保留。当前仍需保留的是少量显式安全语义与历史/迁移说明，例如 `CarrierCompatibilitySafetyService`（`replaceCompatibilityRequirement(requirement_group=0)` 与 `existsPermissionReference`），而不是 `resource` 表本身。

---

## 1. 用户与租户模型（已按 Phase1 收口）

| 项 | 位置 | 现状 | 收口结果 / 建议 |
|----|------|------|------------------|
| **user.tenant_id 双写** | `User` 实体、`UserServiceImpl` | Liquibase `044-user-tenant-id-nullable-stop-dual-write.yaml` 已将 `tenant_id` 改为可空，`UserServiceImpl.create*` 不再写入 `user.tenant_id`，仅通过 `tenant_user` 维护 membership。 | 运行态授权与可见性已完全依赖 `tenant_user + activeTenantId`；`user.tenant_id` 仅保留为展示/审计字段。 |
| **uk_user_tenant_username** | `User` 表约束 | `044`/`045` 已删除 `(tenant_id, username)` 唯一约束并新增全局唯一 `uk_user_username (username)`。 | 已达到“平台账号 username 全局唯一”的目标态。 |
| **findByIdAndTenantId** | `UserRepository` | 仍存在少量 `findByIdAndTenantId` 风格方法用于老代码路径；新登录与授权链路已统一走 `tenant_user` membership。 | 新逻辑只用 membership + `findById`；后续重构时可逐步淘汰 `findByIdAndTenantId` 读路径，但不再用于授权判定。 |

---

## 2. 平台语义双轨（“default” vs tenant_id=0）

| 项 | 位置 | 说明 | 收口建议 |
|----|------|------|----------|
| **平台 = code "default" 的租户** | `PlatformTenantProperties` / `PlatformTenantResolver`、`IdempotentProperties`、`TenantBootstrapServiceImpl` | 平台控制面 Guard 已不再依赖 `default`，但平台租户解析与 bootstrap 模板回退仍以配置的 `platformTenantCode`（默认 `default`）作为兼容承载。 | 长期改为 `scope_type=PLATFORM` + 平台模板层级；不再把 `code=default` 作为模板/平台语义的最后回退。 |
| ~~**平台 = tenant_id 0**~~ | `DictTenantScope.PLATFORM_TENANT_ID = 0`，字典模块各处 `isPlatformTenant(tenantId)` | ✅ 已收口：字典已迁移到 `tenant_id IS NULL`，与“default 租户”可能不一致（若 default 的 id≠0）。 | **当前约定**：`tenant_id=0` 仅字典模块表示“平台字典”；其余控制面（菜单、幂等、租户管理等）使用“code=default 的租户”或配置。可配置化留待与平台租户统一时做（T7.1）。 |

---

## 3. 角色码与权限展示

| 项 | 位置 | 说明 | 收口建议 |
|----|------|------|----------|
| ~~**"ADMIN" 与 "ROLE_ADMIN" 并存**~~ | 旧：各 AccessGuard 中 `Set.of("ROLE_ADMIN", "ADMIN")` | ✅ **已收口**：所有 Guard 已移除 ROLE_ADMIN/ADMIN 兜底，改为纯细粒度权限码判定。`LegacyAuthConstants` 已物理删除。默认 seed 通过 `role_permission -> permission` 将全部权限码授予 ROLE_ADMIN 角色。 | 已达目标态。 |
| **role.name 用于展示/下游** | `RoleController`（返回 name/code）、`CamundaIdentityProvider`（`group.setName(role.getName())`） | 运行态鉴权已不依赖 role.name，但 API 与工作流仍用 name。 | 展示可保留；Camunda 若强依赖 name，可保留至工作流迁移或改为用 role.code。 |
| **role.getCode() 进入 authority** | `SecurityUser.buildAuthorities`、`PermissionVersionService` | 角色 code 作为 authority，与 resource.permission 一起组成权限。 | 已符合“不把 role.name 放入 authority”；若未来仅用 permissions，可逐步不再把 role.code 放入。 |

---

## 4. user_role 表与读回退（已下线）

| 项 | 位置 | 现状 | 收口结果 |
|----|------|------|----------|
| **user_role 读回退** | 旧：`RoleAssignmentRepository.findLegacyRoleIdsByUserIdAndTenantId`、`EffectiveRoleResolutionService` legacy 分支 | Phase1 已移除所有对 `user_role` 的运行时读取与回退逻辑：`EffectiveRoleResolutionService` 仅通过 `RoleAssignmentSyncService` + `role_assignment` 解析有效角色，`AuthUserResolutionService` 也只基于 assignment。 | 运行态权限来源已统一为 `role_assignment -> role_permission -> permission -> resource`，不再依赖 `user_role`。 |
| **user_role 表 / user_role_legacy** | Liquibase `041`、`042`、`043`、`047` | `041`/`042` 完成从 `user_role` 回填 `role_assignment` 与一致性校验，`043` 将表重命名为 `user_role_legacy` 进入只读观察期，`047-drop-user-role-legacy.yaml` 在 MySQL 环境中物理删除 `user_role_legacy`。 | `user_role`/`user_role_legacy` 已在主线迁移中下线；CI 的 `migration-smoke-mysql` workflow 通过额外步骤确认该表不存在，保证回退彻底关闭。 |

---

## 5. 租户 Bootstrap 与菜单

| 项 | 位置 | 说明 | 收口建议 |
|----|------|------|----------|
| **平台模板缺失时的首次回填** | `TenantBootstrapServiceImpl.bootstrapFromPlatformTemplate` | 新租户 bootstrap 已统一从 `tenant_id IS NULL` 的平台模板派生；仅当历史环境尚未回填模板时，才会按配置的平台租户 code（默认 `default`）补一次模板。 | 当前可接受；长期可继续把“一次性回填”下沉为显式迁移或后台运维脚本。 |
| ~~**菜单树平台租户硬编码**~~ | ~~`MenuServiceImpl`~~ | ✅ 已收口：菜单树过滤已按 `TenantContext.isPlatformScope()` 与平台专属资源策略处理，不再查 `default` 或依赖 `ADMIN`/`ROLE_ADMIN` 兜底。 | 已达目标态。 |

---

## 6. 其他零散兼容

| 项 | 位置 | 说明 | 收口建议 |
|----|------|------|----------|
| **ExportController 鉴权** | `ExportController` | 已移除 `LegacyAuthConstants` 依赖，导出能力使用规范码 `system:export:view`；查看全部任务/下载他人结果仍保留管理员语义。 | 当前可接受；若后续拆独立 AccessGuard，可继续收口“管理员专属能力”。 |
| **recordTenantId 等展示字段** | 各 DTO（如 `UserResponseDto`、`RoleResponseDto`、`ResourceResponseDto` 等） | 响应中 `setRecordTenantId(entity.getTenantId())`，用于前端/审计展示。 | 非授权逻辑，可保留；若平台模板将来 tenant_id 可空，展示层需约定“平台”的展示方式。 |
| **User 注释掉的 role.getName()** | `User.java` 内注释 | 历史用 role.name 作 authority 的代码已注释。 | 保持注释或删除，避免误导。 |

---

## 7. 汇总：按优先级收口（更新状态）

- **高（与授权/多租户强相关）**  
  - 平台语义双轨（default vs tenant_id=0）统一为可配置的“平台租户/作用域”。  
  - ✅ user.tenant_id 与 uk_user_tenant_username 已按“平台账号 + membership”目标态迁移（停双写、tenant_id 可空、username 全局唯一），仅剩少量展示/审计用途。  

- **中（一致性/可维护性）**  
  - TenantBootstrap 从“default 租户复制”改为“平台模板 + 租户派生”。  
  - 平台租户解析逐步从 `platformTenantCode` 回退演进为显式平台模板层级。  

- **低（清理与文档）**  
  - ✅ user_role / user_role_legacy 表已通过 Liquibase 043/047 下线，运行态与数据侧均不再依赖。  
  - ExportController 等零散鉴权收口到 AccessGuard + 规范码。  
  - role.name 在 Camunda/API 的用法标注为“展示/兼容”，与鉴权解耦。

**任务清单 T1/T2/T3 收口**：T1.1 调度迁移脚本注释、T1.2 参考 SQL 注释、T2.1 菜单页权限常量注释、T3.1 控制面 RBAC 核查已完成，见 `TINY_PLATFORM_AUTHORIZATION_TASK_LIST.md`。

上述项均属“兼容/遗留”范畴，按阶段逐步收口即可，避免一次性大改。

---

## 8. CARD-10 兼容壳边界（2026-04）

### 8.1 JWT / Session 解码兼容窗口（CARD-10A）

- （更新：CARD-11D 已完成）`SecurityUser` 反序列化已移除“`roleCodes` 缺失时从 `authorities` 恢复 `ROLE_*`”兼容逻辑，旧 session 快照不再半兼容恢复角色码。
- （更新：CARD-11D 已完成）`TinyPlatformJwtGrantedAuthoritiesConverter` 已拒绝 `ROLE_*` authority claim 的旧混合态解码；Bearer 主链仅接受 scope + 非 `ROLE_*` authorities + permissions。
- 主线现状：JWT / Session 只认当前显式契约；旧混合态 payload 视为不支持输入。

### 8.2 roleCodes 消费者 fallback（CARD-10B）

- （更新：CARD-11B 已完成）`roleCodes` 消费者主线仅接受显式 `roleCodes`（`SecurityUser` principal/details 或 JWT `roleCodes` claim）。
- （更新：CARD-11B 已完成）历史 `ROLE_* authorities` 反推 roleCodes 的 fallback 已删除，legacy JWT 缺失 `roleCodes` 时不再回退。
- 主线现状：通用 `ROLE_* authorities` 不再作为 roleCodes 消费链输入来源。

### 8.3 default/platformTenantCode 兼容壳（CARD-10C）

- （更新：CARD-11C 已完成）`TenantContextFilter` 主链已移除基于 `PlatformTenantResolver` 的平台 scope 推断，平台主语义仅认显式 `PLATFORM` scope。
- `MenuServiceImpl` 写侧不再回退 `platformTenantCode/default`，改为要求显式 `activeTenantId`。
- `PlatformTenantResolver` 仅保留给 bootstrap / 历史入口兼容路径；新业务主链禁止新增依赖。

### 8.4 carrier requirement fallback（CARD-10D）

- （更新：CARD-11A 已完成）主线已删除 requirement 缺失时按 `fallbackPermission` 的放行兼容路径；
- requirement 缺失统一 fail-closed，并通过审计 reason（如 `REQUIREMENT_ROWS_MISSING_FAIL_CLOSED`）显式诊断；
- 新增 carrier 仍必须优先补齐 requirement 行，不允许依赖兼容兜底。
