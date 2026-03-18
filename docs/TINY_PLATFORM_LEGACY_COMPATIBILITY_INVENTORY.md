# Tiny Platform 遗留与兼容代码清单

> 与授权模型、租户、用户解析相关的遗留包袱，便于分阶段收口与下线。  
> 关联：`docs/TINY_PLATFORM_AUTHORIZATION_MODEL.md`、`docs/TINY_PLATFORM_PERMISSION_IDENTIFIER_SPEC.md`、`.agent/src/rules/92-tiny-platform-permission.rules.md`、`.agent/src/rules/93-tiny-platform-authorization-model.rules.md`。

---

## 0. 按权限模型与权限标识符的演进路径

- **权限真相源**：运行态以 `resource.permission` 为准，权限码命名遵循 [TINY_PLATFORM_PERMISSION_IDENTIFIER_SPEC.md](./TINY_PLATFORM_PERMISSION_IDENTIFIER_SPEC.md)，新增仅使用规范码。
- **控制面 Guard**：各 AccessGuard 已使用规范权限码（如 `system:user:list`、`system:role:edit`、`idempotent:ops:view`、`dict:platform:manage`）与 `LegacyAuthConstants`（ROLE_ADMIN/ADMIN）兼容；具备任一规范码或管理员角色即可通过。目标态为仅依赖 resource 下发的规范码，管理员角色作为迁移期快捷方式保留。
- **平台级入口**：租户管理、幂等治理、字典平台等仅允许“当前平台租户 + 管理员”或“当前平台租户 + 对应规范码”（如 `system:tenant:list`、`idempotent:ops:view`、`dict:platform:manage`），见权限规范文档“控制面与平台级”小节。
- **角色码演进**：ROLE_ADMIN 为规范角色码，ADMIN 为历史兼容码；目标为数据与前端收口后仅保留 ROLE_ADMIN，或完全由“控制面权限集”替代粗粒度管理员判断。
- **导出能力**：导出接口已支持规范码 `system:export:view`（具备则可用导出）；查看全部任务/下载他人结果仍仅允许管理员，与 LegacyAuthConstants 一致。

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
| **平台 = code "default" 的租户** | `TenantManagementAccessGuard`（PLATFORM_TENANT_CODE）、`IdempotentMetricsAccessGuard`（properties.getOps().getPlatformTenantCode）、`MenuServiceImpl`（`tenantRepository.findByCode("default")`）、`TenantBootstrapServiceImpl`（DEFAULT_TENANT_CODE） | 用“默认租户”编码表示平台或模板来源。 | 文档已约定迁移期允许；长期改为 `scope_type=PLATFORM` + 可选配置的“平台租户 ID”，不再用 code 语义。 |
| **平台 = tenant_id 0** | `DictTenantScope.PLATFORM_TENANT_ID = 0`，字典模块各处 `isPlatformTenant(tenantId)` | 字典用 `tenant_id=0` 表示平台字典，与“default 租户”可能不一致（若 default 的 id≠0）。 | **当前约定**：`tenant_id=0` 仅字典模块表示“平台字典”；其余控制面（菜单、幂等、租户管理等）使用“code=default 的租户”或配置。可配置化留待与平台租户统一时做（T7.1）。 |

---

## 3. 角色码与权限展示

| 项 | 位置 | 说明 | 收口建议 |
|----|------|------|----------|
| **"ADMIN" 与 "ROLE_ADMIN" 并存** | `UserManagementAccessGuard`、`RoleManagementAccessGuard`、`ResourceManagementAccessGuard`、`MenuManagementAccessGuard`、`DictPlatformAccessGuard`、`TenantManagementAccessGuard`、`MenuServiceImpl`、`ExportController` | 多处 `Set.of("ROLE_ADMIN", "ADMIN")` 或 `equalsIgnoreCase("ADMIN")`。 | 规范上角色 authority 应为 `ROLE_*`；可统一为仅 `ROLE_ADMIN`，或保留双码但注明兼容期、逐步从数据与前端收口 `ADMIN`。 |
| **role.name 用于展示/下游** | `RoleController`（返回 name/code）、`CamundaIdentityProvider`（`group.setName(role.getName())`） | 运行态鉴权已不依赖 role.name，但 API 与工作流仍用 name。 | 展示可保留；Camunda 若强依赖 name，可保留至工作流迁移或改为用 role.code。 |
| **role.getCode() 进入 authority** | `SecurityUser.buildAuthorities`、`PermissionVersionService` | 角色 code 作为 authority，与 resource.permission 一起组成权限。 | 已符合“不把 role.name 放入 authority”；若未来仅用 permissions，可逐步不再把 role.code 放入。 |

---

## 4. user_role 表与读回退（已下线）

| 项 | 位置 | 现状 | 收口结果 |
|----|------|------|----------|
| **user_role 读回退** | 旧：`RoleAssignmentRepository.findLegacyRoleIdsByUserIdAndTenantId`、`EffectiveRoleResolutionService` legacy 分支 | Phase1 已移除所有对 `user_role` 的运行时读取与回退逻辑：`EffectiveRoleResolutionService` 仅通过 `RoleAssignmentSyncService` + `role_assignment` 解析有效角色，`AuthUserResolutionService` 也只基于 assignment。 | 运行态权限来源已统一为 `role_assignment -> role_resource -> resource.permission`，不再依赖 `user_role`。 |
| **user_role 表 / user_role_legacy** | Liquibase `041`、`042`、`043`、`047` | `041`/`042` 完成从 `user_role` 回填 `role_assignment` 与一致性校验，`043` 将表重命名为 `user_role_legacy` 进入只读观察期，`047-drop-user-role-legacy.yaml` 在 MySQL 环境中物理删除 `user_role_legacy`。 | `user_role`/`user_role_legacy` 已在主线迁移中下线；CI 的 `migration-smoke-mysql` workflow 通过额外步骤确认该表不存在，保证回退彻底关闭。 |

---

## 5. 租户 Bootstrap 与菜单

| 项 | 位置 | 说明 | 收口建议 |
|----|------|------|----------|
| **从 default 租户复制角色/资源** | `TenantBootstrapServiceImpl.bootstrapFromDefaultTenant` | 新租户从 code=default 的租户复制资源和角色，属“默认租户即模板”的遗留语义。 | 目标态改为“平台模板 + 租户派生”；需引入 role/resource 模板层级或平台模板表后再改。 |
| **菜单树平台租户硬编码** | `MenuServiceImpl`（`tenantRepository.findByCode("default")`、`hasAuthority("ROLE_ADMIN", "ADMIN")`） | 平台级菜单过滤、管理员快捷路径依赖 default 与 ADMIN。 | 与“平台语义双轨”一并收口；菜单权限已按 resource.permission，可保留 ADMIN 兼容至角色码统一。 |

---

## 6. 其他零散兼容

| 项 | 位置 | 说明 | 收口建议 |
|----|------|------|----------|
| **ExportController 鉴权** | `ExportController` | 已使用 `LegacyAuthConstants.isAdminAuthority`；导出能力同时支持规范码 `system:export:view`，查看全部任务/下载他人结果仍仅管理员。 | 保持规范码 + 管理员兼容；若后续引入独立 ExportAccessGuard 可再收口。 |
| **recordTenantId 等展示字段** | 各 DTO（如 `UserResponseDto`、`RoleResponseDto`、`ResourceResponseDto` 等） | 响应中 `setRecordTenantId(entity.getTenantId())`，用于前端/审计展示。 | 非授权逻辑，可保留；若平台模板将来 tenant_id 可空，展示层需约定“平台”的展示方式。 |
| **User 注释掉的 role.getName()** | `User.java` 内注释 | 历史用 role.name 作 authority 的代码已注释。 | 保持注释或删除，避免误导。 |

---

## 7. 汇总：按优先级收口（更新状态）

-- **高（与授权/多租户强相关）**  
  - 平台语义双轨（default vs tenant_id=0）统一为可配置的“平台租户/作用域”。  
  - ✅ user.tenant_id 与 uk_user_tenant_username 已按“平台账号 + membership”目标态迁移（停双写、tenant_id 可空、username 全局唯一），仅剩少量展示/审计用途。  

- **中（一致性/可维护性）**  
  - 角色码统一为 `ROLE_ADMIN`，或明确 “ADMIN” 为兼容并文档化。  
  - TenantBootstrap 从“default 租户复制”改为“平台模板 + 租户派生”。  

- **低（清理与文档）**  
  - ✅ user_role / user_role_legacy 表已通过 Liquibase 043/047 下线，运行态与数据侧均不再依赖。  
  - ExportController 等零散鉴权收口到 AccessGuard + 规范码。  
  - role.name 在 Camunda/API 的用法标注为“展示/兼容”，与鉴权解耦。

**任务清单 T1/T2/T3 收口**：T1.1 调度迁移脚本注释、T1.2 参考 SQL 注释、T2.1 菜单页权限常量注释、T3.1 控制面 RBAC 核查已完成，见 `TINY_PLATFORM_AUTHORIZATION_TASK_LIST.md`。

上述项均属“兼容/遗留”范畴，按阶段逐步收口即可，避免一次性大改。
