# Tiny Platform `resource` 兼容层渐进退场计划

> 目标：在不硬切、不降安全边界的前提下，把 `resource` 从“主写兼容层”推进到**运行时完全退场并物理下线**。
> 状态术语：按 `docs/TINY_PLATFORM_AUTHORIZATION_DOC_MAP.md` 的术语字典解释（已落地 / 待闭合 / 可删除 / 暂时保留）。

---

## 1. 当前口径（与真相源一致）

- 功能权限真相源：`role_assignment -> role_permission -> permission`
- 载体层：`menu / ui_action / api_endpoint` 与 `*_permission_requirement` 已落地
- 当前写链：正常 `create/update/updateSort/delete` 已不再保存 compatibility `resource` 行；写入只落到 carrier（`menu / ui_action / api_endpoint`）并维护 `compatibility group` 回填（`requirement_group=0`）
- 当前状态：运行时主线与常规 bootstrap / smoke / e2e 脚本已退出对 `resource` 表的依赖；物理删除已通过 `131-drop-resource-legacy.yaml` 落到 Liquibase 主链末端
- 当前剩余项：仅保留明确标记为历史用途的 pre-117 readiness SQL；它们不再属于运行时或当前门禁依赖

---

## 2. `resource` 当前剩余职责清单

## 2.1 写路径职责

| 职责 | 状态 | 说明 |
|---|---|---|
| 控制面写入口中的 `resource` 兼容保存/managed entity 装载 | 已下线（已完成） | 正常 create/update/updateSort/delete 不再维护 compatibility `resource` 行；managed entity 装载与唯一性校验依托 carrier 写链/读链完成。 |
| 共享 ID 对齐（`resource.id == carrier.id`） | 已落地（已解耦） | 兼容行已 locator 化，compatibility group 回填已按显式 carrier 主键，carrier 新增主键已改为自增；shared-id 不再构成任何运行时退场的阻断口径。 |
| compatibility group 回填（`requirement_group=0`） | 已删除 | CARD-13B 已移除 runtime compatibility 回填；`CarrierPermissionReferenceSafetyService` 仅保留 `existsPermissionReference` 安全校验，不再维护 requirement_group 语义。 |

## 2.2 读路径职责（已细化到具体消费者）

| 读链消费者 | 状态 | 说明 | 迁移前提/风险 |
|---|---|---|---|
| `ResourceServiceImpl.findByRoleId(...)` | 已落地（已迁） | 已从 `role -> resourceId -> resource` 迁到 `role_permission -> permission_id -> menu/ui_action/api_endpoint(required_permission_id)` | 需持续验证混合载体返回顺序与 DTO 字段稳定 |
| `ResourceServiceImpl.findByUserId(...)` | 已落地（已迁） | 已从“按角色回读 resourceId 再查 resource”迁到“effective roles -> permission_id -> carrier 读模型” | 需持续验证多角色并集去重与 tenant scope 一致性 |
| `RoleServiceImpl.updateRoleResources` 的授权输入校验读取 | 已落地（已迁） | 已从 `resource` 最小投影读取切到 carrier 读模型（`menu/ui_action/api_endpoint`）并提取 `required_permission_id`；主契约已切 `permissionIds` | 需持续验证混合 carrier、跨租户拒绝、同 permission 去重 |
| `TenantBootstrapServiceImpl` 模板复制读取 | 已落地（主读已迁） | 模板资源实体复制主读已切到 carrier template snapshot；模板复制直接写入 carrier（menu/ui_action/api_endpoint），授权回放只依赖 carrier `required_permission_id`，不再保留 resource fallback；`resource` 在 bootstrap 中不再作为模板主读来源 | 继续验证 bootstrap/backfill/rollout 全链路稳定性；后续进入阶段 2 时只允许继续收缩历史字段/观测依赖 |
| 资源管理控制面部分列表/校验查询（`ResourceServiceImpl` 其余兼容查询） | 已落地（主读已迁） | 资源管理控制面的列表、按类型、详情、按字段/权限查询与存在性校验已切到 `menu/ui_action/api_endpoint` 读模型组装；`resource` 不再承担控制面主读来源 | 持续验证控制面搜索/分页/详情/父级选择回归；后续只允许继续收缩极小 fallback，不应回退到 resource 主读 |
| 菜单树/按钮/API 运行时读取 | 已落地（已迁） | 已优先走 carrier 读链 | 持续回归验证即可 |

## 2.3 runtime / compatibility / ops 依赖

| 依赖项 | 状态 | 说明 | 退出前提 |
|---|---|---|---|
| `ResourceCarrierProjectionSyncService.upsertCarrierProjections` | 已删除 | 正常 create/update/updateSort 已改为 service 内直接写 carrier，不再经由 bridge 双写 | 无 |
| `ResourceCarrierProjectionSyncService.deleteCarrierProjectionsByResourceId(resourceId)` | 已删除 | 删除链已改为 service 内直接按 id 删除 carrier 记录，不再经由 bridge 清理 | 无 |
| `replaceCompatibilityRequirement(binding)`（历史 compatibility 类上，已删除） | 已删除 | requirement_group=0 compatibility 回填已在 CARD-13B 退场；CARD-14B 后类名为 `CarrierPermissionReferenceSafetyService`，不再包含该方法 | 无 |
| `CarrierPermissionReferenceSafetyService.existsPermissionReference(permissionId, tenantId)` | 暂时保留 | “最后载体撤 role_permission”唯一判定，但已改为 carrier/requirement 显式查询 | 等价判定链迁移完成并覆盖回归 |
| 登录/权限矩阵与 bootstrap 脚本 | 已落地 | 已形成脚本门禁 + 定向回归 | 持续执行，不是阻塞删除项 |

---

## 3. 本轮已完成收缩（2026-03-27）

- **阶段 0 闸门已通过（事务边界）**：`RoleServiceImpl.updateRoleResources` 已加事务并改为“先校验后删写”，fail-closed 异常不会留下“旧授权已删、新授权未写”的中间态。
- **`TenantBootstrapServiceImpl` 模板主读已切换**：平台模板与 configured tenant 回填两条链路均改为 carrier template snapshot 主读，统一从 `menu/ui_action/api_endpoint` 组装 `Resource` 风格快照驱动复制。
- **bootstrap 依赖已清场**：平台模板回填与新租户派生均直接写入 carrier 表；授权回放只依赖 carrier `required_permission_id`（缺失即 fail-closed），不再保留 resource fallback。
- **辅助映射冲突保护**：`toPermissionIdMap` 对“同一 resourceId 出现多个不同 required_permission_id”改为 fail-closed 抛错，不再静默覆盖。
- **主读迁移等价性回归已补齐一层**：`BUTTON / API` 字段复制、平台模板回填链路与 parentId 两段式回填均有直接回归覆盖，证明 carrier snapshot 不只是“能读到”，而是已能驱动实际 clone/save 流程。
- **写链已缩到 direct carrier write/delete**：`ResourceServiceImpl` 与 `MenuServiceImpl` 的正常 create/update/updateSort/delete 已下线 compatibility `resource` 主动保存，改为 service 内直接保存/删除 `menu_entry / ui_action_entry / api_endpoint_entry`；运行时主线不再对 `resource` 表做任何伴随写入/删除。
- **legacy projection bridge 已退出主线**：`ResourceCarrierProjectionSyncService` 的 legacy `sync/delete` 双写接口已下线；剩余兼容安全语义已迁到 `CarrierPermissionReferenceSafetyService`，不再以 projection sync bridge 形式存在于运行时主线。
- **共享 ID 前置条件继续清零（本轮）**：`RoleRepository.findResourceIdsByRoleId`、`findGrantedRoleCarrierPairsByTenantId`、`findGrantedRoleCarrierPairsForPlatformTemplate` 已从 `resource.required_permission_id` 反查改为直接从 `menu/ui_action/api_endpoint(required_permission_id)` union 推导；bootstrap 的 `assertPermissionBindingsReady` 也已改为 carrier template snapshot 校验，不再主读 `resource` 快照做绑定就绪判断。
- **显式 locator 字段已落地（历史资产维度）**：`resource` 新增 `carrier_type + carrier_source_id` 并完成历史 backfill；该维度仅用于历史资产治理/迁移输入/排障定位，不作为运行时主线的兼容删除或定位前提。
- **角色授权主契约已切换（本轮）**：`/sys/roles/{id}/resources` 已只接受 `permissionIds` 正式契约；运行时最终写入只落 `role_permission(permission_id)`。
- **菜单父子校验与递归删除读链收缩**：`MenuServiceImpl` 已把父级合法性校验、循环引用检查、子菜单递归枚举从 `ResourceRepository` 迁到 `MenuEntryRepository`（carrier 读链优先）。
- **carrier projection 读链已拆库（本轮）**：角色授权校验与租户 bootstrap 使用的 carrier template / permission snapshot 查询已从 legacy `ResourceRepository` seam 抽到独立的 `CarrierProjectionRepository`；`ResourceRepository` 兼容接口已在后续清理阶段删除。
- **菜单遗留 native `resource` 读链已清除（本轮）**：`MenuServiceImpl` 中未再使用的 `findMenusByNativeHibernate(...)` 及其 `resource` 原生 SQL 映射辅助方法已删除，避免误判为仍存在运行时主读依赖。
- **rollout 门禁已改认 carrier 真相链（本轮）**：`verify-authorization-model-rollout.sh` 不再以 `resource` 行数或 `resource.required_permission_id` 作为 canonical 对照，改为直接核对 `menu/ui_action/api_endpoint`、`required_permission_id` 与 requirement 兼容组。
- **平台模板自举脚本已切 carrier 计数（本轮）**：`verify-platform-template-row-counts.sh` 与 `verify-platform-dev-bootstrap.sh` 不再要求 `resource` 平台模板行数，统一改为 `role + (menu/ui_action/api_endpoint)` 总量门禁。
- **调度 E2E seed 已切权限真相链（本轮）**：`scripts/e2e/ensure-scheduling-e2e-auth.sh` 不再创建/读取 legacy `resource` authority 行，改为直接维护 `permission + role_permission`。
- **调度迁移 smoke 已切 permission/carrier 口径（本轮）**：`verify-scheduling-migration-smoke.sh` 对调度 authority、控制面 URI 与平台菜单残留的校验已改为 `permission / menu / ui_action / api_endpoint`，不再把 `resource` 当 canonical 校验源。
- **删除链保持安全边界不变**：菜单删除仍执行 `existsPermissionReference` 判断，仅在最后载体时撤销 `role_permission`，未放宽权限策略。
- **`resource` 角色明确**：删除路径只删除 carrier 记录，并保留“最后载体撤权”安全语义；不再对 `resource` 表做 locator 清理，也不再把 `resource` 当作任何运行时删除链路的一部分。

---

## 4. 最终下线结论

| 结论项 | 当前状态 | 说明 |
|---|---|---|
| `tiny-oauth-server` 运行时读写 | 已下线 | 运行时主线不再读写 `resource` 表，`Resource` 仅保留兼容 DTO 语义，legacy `ResourceRepository` bridge 也已删除 |
| 权限回填链路 | 已下线 | `ResourcePermissionBindingService` 已改为直接基于 `menu/ui_action/api_endpoint` 回填 `permission` 与 `required_permission_id` |
| `tiny-web` 演示模块 | 已下线 | 访问检查已改为 JDBC 直读 canonical carrier，不再依赖 `resource` 实体、仓储或 seed |
| 常规脚本门禁 | 已下线 | dev/bootstrap/e2e/smoke/permission 修复统计脚本已迁到 carrier / permission 口径 |
| 物理删除 | 已落地 | `131-drop-resource-legacy.yaml` 已接入 `db.changelog-master.yaml` 末端 |

唯一保留的直接 `resource` SQL 为 **`verify-pre-liquibase-117-role-resource-readiness.sql`**，其用途是历史 pre-117 只读审计；不属于运行时、门禁或当前迁移主链。

**结论：`resource` 已完成运行时退场，并已具备物理删除路径。**

---

## 5. 后续推进方向（历史审计，仅存量保留）

- 仅剩历史 pre-117 readiness SQL 是否归档到单独目录，可按仓库整理节奏处理
- 若需要为旧库补出物理删除 SOP，可在本文件之外单独补一页 DBA/回滚说明

---

## 6. 结果备注

- 由于 `131-drop-resource-legacy.yaml` 放在 master 最末端，fresh install 仍能先跑完历史 `resource` 相关 changeSet，再在链路末端安全删除总表
- 若本地数据库尚未跑到 131，`resource` 物理表可能暂时仍存在；这属于库版本未追平，不代表当前代码仍依赖它

---

## 7. legacy `resource` seam 消费者分级证据表（阶段 D）

| 消费者 | 主要用途 | 当前分级 | 处理结论 |
|---|---|---|---|
| `ResourceServiceImpl` | 资源管理控制面 / carrier 读写 | 已收缩（本轮） | 控制面主读已切 carrier；正常 create/update/updateSort/delete 仅写 carrier 并维护 `requirement_group=0`；运行时不再对 `resource` 表做任何业务读写 |
| `MenuServiceImpl` | 菜单写链、父子关系校验、删除链 | 已落地（写链已迁） | 父子校验/递归枚举已切到 `MenuEntryRepository`；未使用的 `resource` native 菜单分页实现已删除；删除链 direct-delete carrier，并保持“最后载体撤权”判定 |
| `RoleServiceImpl` | `updateRoleResources` 读取资源并写角色授权 | 已降为历史服务别名 | API 主契约已只接受 `permissionIds`；运行时写入统一落 `role_permission(permission_id)` |
| `TenantBootstrapServiceImpl` | 平台模板资源快照复制、授权回放 | 已落地（主读已迁） | 模板资源主读已切到 carrier template snapshot；复制与授权回放不再依赖 `resource` 表，不保留 resource fallback |

本轮收缩已完成并闭合为一致口径：删除链与控制面读写均已切到 carrier（`menu/ui_action/api_endpoint`）与 permission 真相链；`ResourceServiceImpl.findByRoleId/findByUserId`、`RoleServiceImpl.updateRoleResources`、`TenantBootstrapServiceImpl` 的模板复制与授权回放均不再依赖 `resource` 表。legacy `ResourceCarrierProjectionSyncService` 已退出运行时主线，剩余 `replaceCompatibilityRequirement` 与 `existsPermissionReference` 保留为 carrier requirement/最后引用的显式安全语义，并由 `CarrierPermissionReferenceSafetyService` 承接。  
**阶段结论：`resource compatibility` 已退出运行时主线。** requirement 外键始终指向 carrier 主键，compatibility group(`requirement_group=0`) 也已改为基于真实 carrier id 回填；当前与 `resource` 相关的剩余事项仅属于历史字段承载（如 `resource.permission`）与历史资产治理/物理清理议题。

### 3.1 `TenantBootstrapServiceImpl` 依赖拆解（本轮）

| 依赖用途 | 当前读取来源 | 分类 | 本轮结论 |
|---|---|---|---|
| 角色授权回放绑定准备（targetResourceId -> required_permission_id） | carrier template snapshot | 已落地 | 已实现并回归；缺失绑定/快照不完整时 fail-closed，不保留 resource fallback |
| 菜单树结构复制（parentId 两段式重建） | carrier template snapshot | 已落地（保持原保存顺序） | 主读已迁，仍保留现有两段式 saveAll / parentId 回填流程 |
| UI action 页面字段复制（pagePath/title/sort/enabled 等） | carrier template snapshot | 已落地 | 已通过 snapshot -> Resource 风格对象组装驱动复制 |
| api_endpoint 字段复制（uri/method/title/enabled 等） | carrier template snapshot | 已落地 | 已通过 snapshot -> Resource 风格对象组装驱动复制 |
| 两段式保存顺序依赖（先 saveAll 分配 id，再回填 parent） | Resource 风格 clone 输入 + 现有 saveAll 流程 | 暂时保留 | 本轮未改保存顺序；风险已从“主读来源”收窄为“保存时序是否需要进一步重构” |
