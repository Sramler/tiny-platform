# Tiny Platform 授权模型第一阶段主交付技术设计

> 状态更新：本文件保留为 **Phase1 历史技术设计基线 / 非当前运行态真相源**。  
> 当前仓库的运行态授权主链、当前完成度与平台/租户边界，请优先以 [TINY_PLATFORM_AUTHORIZATION_MODEL.md](./TINY_PLATFORM_AUTHORIZATION_MODEL.md)、[TINY_PLATFORM_AUTHORIZATION_TASK_LIST.md](./TINY_PLATFORM_AUTHORIZATION_TASK_LIST.md) 与 [TINY_PLATFORM_TENANT_GOVERNANCE.md](./TINY_PLATFORM_TENANT_GOVERNANCE.md) 为准。

> 状态：技术设计基线  
> 适用范围：`auth / oauth / security / tenant / role / resource / menu / user`  
> 关联文档：
> - [TINY_PLATFORM_AUTHORIZATION_MODEL.md](./TINY_PLATFORM_AUTHORIZATION_MODEL.md)
> - [TINY_PLATFORM_PERMISSION_IDENTIFIER_SPEC.md](./TINY_PLATFORM_PERMISSION_IDENTIFIER_SPEC.md)

---

## 1. 目标与范围

本文件定义“第一阶段主交付”的可实施技术方案。第一阶段的目标不是一次性做完 `RBAC3 + Scope + Data Scope`，而是完成下面这轮基础跃迁：

1. 从 `user.tenant_id + user_role` 过渡到 `tenant_user + role_assignment`；
2. 把 `PLATFORM` 从“默认租户兼容语义”中拆出来，变成正式作用域；
3. 收口运行态会话与 JWT 契约，去掉展示型 authority；
4. 补齐剩余控制面的方法级 RBAC，避免旧接口继续绕过新模型；
5. 阶段一历史裁决：当时保留 `resource.permission` 作为运行态兼容字段，不额外引入第二套 permission 目录；当前真相源已演进为 `role_permission -> permission`。

### 第一阶段明确不做

1. 不实现 `organization_unit / user_unit` 的正式上线；
2. 不实现 `role_hierarchy / role_mutex / role_prerequisite / role_cardinality`；
3. 不实现完整 `Data Scope` 运行时过滤；
4. 不引入独立 `permission` 表；
5. 不一步删除 `user.tenant_id` 或 `user_role`。

---

## 2. 已冻结的实现契约

本节内容在第一阶段内按“硬约束”执行，不再边做边改方向。

### 2.1 账号模型

1. `user` 表代表平台账号；
2. 一个 `user` 可以通过 `tenant_user` 加入多个租户；
3. `role_assignment.principal_id` 继续指向 `user.id`，不指向 `tenant_user.id`；
4. Phase 1 假设 `username` 升级为全局唯一登录标识；
5. 现有“同名用户分布在不同租户”的历史数据，必须在切换前做一次数据扫描与清理。

说明：

- 当前实现按 `(tenant_id, username)` 唯一加载用户，无法支撑“一个账号加入多个租户”的运行时模型；
- 因此第一阶段必须把登录身份从“租户内用户”切到“平台账号 + 活动上下文”。

### 2.2 平台作用域与模板层级

1. `PLATFORM` 是正式作用域；
2. 平台模板与租户模板允许共表存储；
3. 角色模板层级和授权实例作用域必须分开表达：
   - 模板层级使用 `role_level` / `resource_level`
   - 授权实例作用域使用 `role_assignment.scope_type / scope_id`
4. 平台模板行允许 `tenant_id IS NULL`；
5. 平台模板只允许平台控制面维护，租户侧只允许读取或派生副本。

### 2.3 `role_assignment` 的唯一性与有效性

`role_assignment` 采用“单业务键、状态切换”的模型，不使用多条重叠历史记录表示同一授权。

业务键：

```text
(principal_type, principal_id, role_id, scope_type, scope_id, tenant_id)
```

统一有效判定：

```text
status = ACTIVE
and start_time <= now
and (end_time is null or end_time > now)
```

规则：

1. 同一业务键只保留一条记录；
2. 临时授权通过更新 `start_time / end_time` 完成；
3. 停用或过期后不参与运行态权限计算；
4. 历史变化通过审计日志保留，不通过新增重复 assignment 保留。

### 2.4 运行态最小授权上下文

第一阶段运行态上下文统一为：

```text
userId
activeScopeType
activeTenantId
permissions
roleCodes (optional, compatibility only)
activeRoleAssignmentIds (optional)
```

约束：

1. `role.name` 不再进入 authority / Session / JWT；
2. `activeTenantId`：
   - `TENANT` 作用域下必填
   - `PLATFORM` 作用域下允许为空
3. 如果未来引入 `ORG/DEPT`，继续扩展 `activeOrgId / activeDeptId`，但第一阶段先保留字段位，不落业务逻辑。

### 2.5 Data Scope 的参照物契约

虽然第一阶段不实现完整 Data Scope，但语义先定死，避免后续实现分叉：

1. `SELF`：始终相对当前用户；
2. `DEPT / DEPT_AND_CHILD / ORG / ORG_AND_CHILD`：
   - 若 assignment 自身带 `scope_type=DEPT/ORG`，优先相对 `assignment.scope_id`
   - 否则回退到用户当前主组织/主部门
3. `CUSTOM` 初期仅支持 `ORG / DEPT / USER`。

### 2.6 兼容退出规则

1. 第一阶段完成后，禁止新增代码继续直接依赖 `user.tenant_id` 做真实授权判断；
2. 第一阶段完成后，禁止新增代码继续直接依赖 `user_role` 作为运行时权限真相源；
3. `user.tenant_id` 与 `user_role` 仅保留为兼容字段/兼容表，直到第二阶段结束后再清退。

---

## 3. 当前代码基线与受影响链路

| 位置 | 当前行为 | 第一阶段调整 |
| --- | --- | --- |
| `core/oauth/security/UserDetailsServiceImpl` | 按 `username + legacy tenantId/tenantCode` 加载用户 | 改成按全局账号加载，再校验 `tenant_user` membership 和 `role_assignment` |
| `core/oauth/model/SecurityUser` | authority 由 `role.code + role.name + resource.permission` 展开 | 改成规范 `permissions + 兼容 roleCodes`，移除 `role.name` |
| `core/oauth/config/JwtTokenCustomizer` | JWT claims 写 legacy `tenantId + authorities` | 改成 `activeScopeType + activeTenantId + permissions` |
| `core/oauth/tenant/TenantContextFilter` | 只维护 legacy tenant 视图 | 保留兼容能力，同时引入统一授权上下文 |
| `infrastructure/auth/user/domain/User` | `tenant_id` 为必填且带 `user_role` 多对多 | `tenant_id` 降为兼容字段；运行态角色不再靠 `@ManyToMany user_role` |
| `infrastructure/auth/user/repository/UserRepository` | 大量方法直接依赖 `tenant_id` 与 `user_role` | 新增全局用户查询和 membership/assignment 查询 |
| `application/controller/role/RoleController` | 尚未补方法级 RBAC | 第一阶段补齐 |
| `application/controller/resource/ResourceController` | 尚未补方法级 RBAC | 第一阶段补齐 |
| `infrastructure/auth/role/service/RoleServiceImpl` | 直接维护 `user_role` | 改成维护 `role_assignment`，必要时兼容双写 |
| `infrastructure/auth/resource/service/ResourceServiceImpl` | 默认所有资源都属于某租户 | 支持平台模板与租户模板共表 |
| `infrastructure/menu/service/MenuServiceImpl` | 按 tenant + authority 下发菜单 | 继续按 permissions 过滤，但 permissions 来源切到 assignment 链路 |
| `infrastructure/tenant/service/TenantBootstrapServiceImpl` | 从默认租户复制角色/资源模板 | 迁移到“平台模板 + 租户模板派生”的新模型 |

---

## 4. 第一阶段目标数据模型

### 4.1 新增 `tenant_user`

用途：

- 表达平台账号与租户的 membership；
- 作为登录后“用户可切换哪些租户”的唯一来源；
- 承担租户成员状态与默认上下文属性。

建议字段：

```sql
CREATE TABLE tenant_user (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  status VARCHAR(16) NOT NULL COMMENT 'ACTIVE/INVITED/SUSPENDED/LEFT',
  is_default BOOLEAN NOT NULL DEFAULT FALSE,
  joined_at DATETIME NOT NULL,
  left_at DATETIME NULL,
  last_activated_at DATETIME NULL,
  created_at DATETIME NOT NULL,
  updated_at DATETIME NOT NULL,
  UNIQUE KEY uk_tenant_user_tenant_user (tenant_id, user_id),
  KEY idx_tenant_user_user_status (user_id, status),
  KEY idx_tenant_user_tenant_status (tenant_id, status)
);
```

约束：

1. `tenant_id + user_id` 唯一；
2. 一个用户最多一个 `is_default=true`，先在 Service 层保证，必要时第二阶段补数据库约束；
3. `ACTIVE` 以外状态不参与租户上下文切换。

### 4.2 新增 `role_assignment`

用途：

- 替代 `user_role` 承担正式授权关系；
- 显式表达 `PLATFORM / TENANT` 作用域；
- 为后续 `ORG / DEPT / POST / GROUP` 扩展预留标准入口。

建议字段：

```sql
CREATE TABLE role_assignment (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  principal_type VARCHAR(16) NOT NULL COMMENT 'USER',
  principal_id BIGINT NOT NULL,
  role_id BIGINT NOT NULL,
  tenant_id BIGINT NULL COMMENT 'PLATFORM 作用域下为空',
  scope_type VARCHAR(16) NOT NULL COMMENT 'PLATFORM/TENANT',
  scope_id BIGINT NULL,
  status VARCHAR(16) NOT NULL COMMENT 'ACTIVE/DISABLED/EXPIRED',
  start_time DATETIME NOT NULL,
  end_time DATETIME NULL,
  granted_by BIGINT NULL,
  granted_at DATETIME NOT NULL,
  updated_at DATETIME NOT NULL,
  UNIQUE KEY uk_role_assignment_scope (
    principal_type,
    principal_id,
    role_id,
    tenant_id,
    scope_type,
    scope_id
  ),
  KEY idx_role_assignment_principal_active (
    principal_type,
    principal_id,
    status,
    start_time,
    end_time
  ),
  KEY idx_role_assignment_role_scope (role_id, scope_type, tenant_id),
  KEY idx_role_assignment_tenant_scope (tenant_id, scope_type, status)
);
```

约束：

1. `scope_type=PLATFORM` 时：
   - `tenant_id IS NULL`
   - `scope_id IS NULL`
2. `scope_type=TENANT` 时：
   - `tenant_id IS NOT NULL`
   - `scope_id = tenant_id`
3. 第一阶段 `principal_type` 固定为 `USER`，其余枚举只作为保留位；
4. 运行态只展开有效 assignment，不展开全部历史行。

### 4.3 调整 `user`

第一阶段不删除 `user.tenant_id`，但做降级处理：

1. `tenant_id` 由“真实租户归属”降级为兼容字段；
2. 新登录、菜单、授权计算、控制面 RBAC 不再以它为真相源；
3. 新增全局唯一账号约束：

```sql
UNIQUE KEY uk_user_username (username)
```

并逐步废弃当前：

```sql
UNIQUE KEY uk_user_tenant_username (tenant_id, username)
```

迁移前置条件：

1. 必须先扫描并解决跨租户重名 `username`；
2. 若无法一次性完成数据清理，则第一阶段不得切换登录模型。

### 4.4 调整 `role`

第一阶段推荐结构：

```sql
ALTER TABLE role
  MODIFY tenant_id BIGINT NULL,
  ADD COLUMN role_level VARCHAR(16) NOT NULL DEFAULT 'TENANT';
```

约束语义：

1. `role_level=PLATFORM` 时，`tenant_id IS NULL`；
2. `role_level=TENANT` 时，`tenant_id IS NOT NULL`；
3. 平台模板和租户模板共表，但通过 `role_level` 区分。

唯一约束策略：

- 不能继续只依赖 `(tenant_id, code)`；
- 推荐新增持久化生成列 `owner_key`：

```sql
owner_key =
  CASE
    WHEN tenant_id IS NULL THEN CONCAT('PLATFORM:', role_level)
    ELSE CONCAT('TENANT:', tenant_id)
  END
```

并改为：

```sql
UNIQUE KEY uk_role_owner_code (owner_key, code)
UNIQUE KEY uk_role_owner_name (owner_key, name)
```

第一阶段只需要把该策略写进 migration 设计，不必先在 `schema.sql` 与全部初始化 SQL 中一次做完。

### 4.5 调整 `resource`

第一阶段推荐结构：

```sql
ALTER TABLE resource
  MODIFY tenant_id BIGINT NULL,
  ADD COLUMN resource_level VARCHAR(16) NOT NULL DEFAULT 'TENANT';
```

约束语义：

1. `resource_level=PLATFORM` 时，`tenant_id IS NULL`；
2. `resource_level=TENANT` 时，`tenant_id IS NOT NULL`；
3. 平台模板只允许平台控制面维护；
4. 租户如需自定义平台模板能力，应通过复制一条 `tenant_id` 非空的租户副本实现。

### 4.6 保留 `user_role` 与 `role_resource`

第一阶段处理方式：

1. `role_resource` 保留，继续表示“角色模板拥有哪些功能权限”；
2. `user_role` 不再作为未来真相源，但保留兼容期；
3. 兼容期内：
   - 角色分配变更优先写 `role_assignment`
   - 如旧页面或旧服务仍依赖 `user_role`，允许临时双写
4. 第一阶段结束标准之一：新代码不再从 `user_role` 读运行态权限。

---

## 5. 运行时技术方案

### 5.1 登录与成员关系解析

第一阶段仍保留“登录时带当前租户”的交互方式，但实现改为：

1. 用户输入 `username + credential + tenantCode`；
2. 先按全局 `username` 加载平台账号；
3. 再校验该账号是否存在有效 `tenant_user` membership；
4. （Phase1 当时口径）再基于该租户展开 `role_assignment -> role_resource -> resource.permission`；当前仓库运行态已切换为 `role_assignment -> role_permission -> permission -> resource`；
5. 生成带当前活动上下文的 `SecurityUser`。

平台控制面登录：

1. 允许无 `activeTenantId` 输入登录到 `PLATFORM` scope；
2. 要求存在 `scope_type=PLATFORM` 的有效 `role_assignment`；
3. 与租户数据面分离审计。

### 5.2 授权上下文

第一阶段不强制重命名 `TenantContext`，但要补一层正式授权上下文。

建议新增：

```text
AuthorizationContext
  - userId
  - activeScopeType
  - activeTenantId
  - activeRoleAssignmentIds
  - permissions
```

迁移策略：

1. 现有 `TenantContext` 继续保留，作为兼容视图；
2. `activeScopeType=TENANT` 时：
   - `TenantContext.tenantId = activeTenantId`
3. `activeScopeType=PLATFORM` 时：
   - `TenantContext` 可为空
   - 只允许访问平台控制面

### 5.3 `SecurityUser` 重构

`SecurityUser` 第一阶段应从“JPA User + roles/resources 懒加载快照”改成“认证快照对象”。

建议属性：

```text
userId
username
activeScopeType
activeTenantId
permissions
roleCodes
activeRoleAssignmentIds
enabled/account flags
```

显式移除：

1. `role.name` authority；
2. 对 `User.roles` 懒加载关系的隐式依赖。

### 5.4 JWT / Session 契约

Phase 1 默认策略：

1. Session 模式保留完整 `permissions` 集合；
2. Access Token 模式默认也保留完整 `permissions`；
3. 兼容少量 `roleCodes`，仅用于粗粒度守卫；
4. 不再发出展示型 authority。

Access Token 最小 claims：

```json
{
  "sub": "...",
  "userId": 123,
  "username": "alice",
  "activeScopeType": "TENANT",
  "activeTenantId": 2001,
  "permissions": ["system:user:list", "system:user:create"],
  "roleCodes": ["ROLE_ADMIN"]
}
```

降级策略：

1. 若未来 JWT 体积超阈值，可退化为：
   - `activeRoleAssignmentIds`
   - `permissionsVersion`
2. 但第一阶段默认不引入该降级模式的完整实现，只在设计中预留字段。

### 5.5 上下文切换

第一阶段建议新增：

```text
POST /auth/context/switch
```

请求：

```json
{
  "scopeType": "TENANT",
  "activeTenantId": 2001
}
```

行为：

1. 校验当前用户是否具备目标上下文的有效 membership / assignment；
2. 刷新 Session 或签发新 JWT；
3. 记录审计日志：
   - `actorUserId`
   - `fromScopeType/fromTenantId`
   - `toScopeType/toTenantId`
   - `switchedAt`

### 5.6 菜单与控制面授权

第一阶段菜单不重新设计，继续按 `resource.permission` + 当前 permissions 下发。

必须调整：

1. 菜单树、按钮可见性、控制器 `@PreAuthorize` 都以同一份 permissions 为准；
2. `RoleController`、`ResourceController` 纳入与 `TenantController`、`UserController` 同等级别的 RBAC 收口；
3. 平台模板的读写接口必须与租户模板接口隔离或显式加平台守卫。

---

## 6. 数据迁移与切换顺序

### 6.1 Schema 迁移顺序

第一阶段 migration 建议按以下顺序拆：

1. `041-add-tenant-user-and-authz-columns`
   - 新增 `tenant_user`
   - `user.username` 全局唯一准备
   - `role.role_level`
   - `resource.resource_level`
2. `042-backfill-tenant-user-from-user`
   - 从 `user.tenant_id` 回填 membership
3. `043-add-role-assignment`
   - 新增 `role_assignment`
4. `044-backfill-role-assignment-from-user-role`
   - 从 `user_role` 回填租户级 assignment
5. `045-auth-control-plane-rbac-hardening`
   - 补控制面资源/权限初始化

### 6.2 回填规则

#### `tenant_user`

```text
for each user:
  insert tenant_user(tenant_id=user.tenant_id, user_id=user.id, status=ACTIVE, is_default=true)
```

#### `role_assignment`

```text
for each user_role row:
  insert role_assignment(
    principal_type='USER',
    principal_id=user_id,
    role_id=role_id,
    tenant_id=tenant_id,
    scope_type='TENANT',
    scope_id=tenant_id,
    status='ACTIVE',
    start_time=now,
    granted_at=now
  )
```

### 6.3 兼容期策略

第一阶段采用“三步切换”：

1. **先回填**
   - 新表和新列到位
   - 历史数据补齐
2. **再双写**
   - 角色分配同时写 `role_assignment` 和 `user_role`
   - 租户成员变更同时写 `tenant_user` 与兼容字段
3. **后切读**
   - 登录、菜单、JWT、`@PreAuthorize` 优先读 `tenant_user + role_assignment`
   - 如需保底，可在短期内允许 `user_role` fallback，但必须打日志并纳入清理清单

### 6.4 第一阶段完成门槛

只有满足以下条件，第一阶段才算完成：

1. 新登录链路已不再依赖 `findUserByUsernameAndTenantId`；
2. `SecurityUser` authority 不再包含 `role.name`；
3. （Phase1 完成口径）运行态权限已由 `role_assignment -> role_resource -> resource.permission` 生成；当前仓库已进一步收口为 `role_assignment -> role_permission -> permission -> resource`；
4. `RoleController` 与 `ResourceController` 已补方法级 RBAC；
5. 新代码不再直接读取 `user.tenant_id` 或 `user_role` 作为真相源；
6. 菜单下发和后端控制器使用同一套 permissions。

---

## 7. 代码级实施包

### 7.1 包 A：数据模型与仓储

涉及：

- `schema.sql`
- `db/changelog/*.yaml`
- `User`
- `Role`
- `Resource`
- 新增 `TenantUser`
- 新增 `RoleAssignment`
- 相关 Repository

输出：

1. 新表和新列；
2. 回填 migration；
3. 兼容读写 Repository；
4. 数据扫描脚本或 SQL 检查项。

### 7.2 包 B：认证链路

涉及：

- `UserDetailsServiceImpl`
- `SecurityUser`
- `JwtTokenCustomizer`
- `TenantContextFilter`
- 认证相关测试

输出：

1. 新的账号加载与 membership 校验；
2. 新的 authority 构造；
3. 新的 JWT claims；
4. 上下文切换接口基础能力。

### 7.3 包 C：控制面 RBAC 收口

涉及：

- `RoleController`
- `ResourceController`
- 相关 AccessGuard
- 前端角色/资源管理页

输出：

1. 方法级 `@PreAuthorize`
2. 前后端权限点对齐
3. 403 拒绝路径测试

### 7.4 包 D：菜单与平台模板

涉及：

- `MenuServiceImpl`
- `ResourceServiceImpl`
- `TenantBootstrapServiceImpl`

输出：

1. 平台模板只读与租户副本派生规则；
2. 菜单继续按 permissions 下发；
3. bootstrap 从“默认租户复制”逐步迁到“平台模板派生”。

---

## 8. 测试与门禁

第一阶段至少需要这些测试：

1. **Migration smoke**
   - `tenant_user` 回填正确
   - `role_assignment` 回填正确
   - 平台模板列和唯一约束正确
2. **认证集成测试**
   - 租户登录成功
   - 无 membership 拒绝
   - 平台 scope 登录成功 / 非平台 assignment 拒绝
3. **JWT / Session 测试**
   - claims 中存在 `activeScopeType / activeTenantId / permissions`
   - `role.name` 不再出现
4. **控制面 RBAC**
   - `RoleController` / `ResourceController` 的允许与拒绝路径
5. **菜单与权限下发**
   - 相同用户切换租户后 permissions 和菜单树会变化
6. **兼容期测试**
   - 双写期间 `user_role` 与 `role_assignment` 保持一致

---

## 9. 主要风险与前置检查

第一阶段正式开工前必须先完成这些检查：

1. **跨租户重名账号扫描**
   - 因为 `username` 需要升级为全局唯一
2. **平台模板数据盘点**
   - 当前哪些角色/资源实际上是“平台级模板”，需要从默认租户抽离
3. **旧代码读路径盘点**
   - 哪些模块仍直接依赖 `user.tenant_id`
   - 哪些模块仍直接依赖 `user_role`
4. **控制面权限点盘点**
   - `role/*`、`resource/*` 的权限码与菜单资源是否已规范

---

## 10. 建议的实施顺序

建议按下面顺序推进，避免大改互相打架：

1. 先补 `RoleController / ResourceController` 的控制面 RBAC；
2. 再做 `tenant_user + role_assignment` 的表结构和回填 migration；
3. 然后改 `UserDetailsServiceImpl / SecurityUser / JwtTokenCustomizer`；
4. 再切菜单和运行态权限读取；
5. 最后再开始真正的平台模板与默认租户语义拆分。

这个顺序的目的是先收口安全边界，再改底层授权模型，避免在兼容期同时放大越权面。
