# Tiny Platform 权限标识符命名规范

> 状态：权限命名主线规范文档  
> 适用范围：后端鉴权、前端权限点、菜单资源、初始化数据、权限字典  
> 关联主线：`TINY_PLATFORM_AUTHORIZATION_MODEL.md`、`TINY_PLATFORM_AUTHORIZATION_TASK_LIST.md`、`.agent/src/rules/92-tiny-platform-permission.rules.md`

> 说明：
> - 本文件只负责“权限码如何命名、如何迁移、如何避免旧码并存”。
> - 当前完成度、优先级与真实剩余项，以 `TINY_PLATFORM_AUTHORIZATION_TASK_LIST.md` 为准。

## 1. 目标

本规范用于统一 tiny-platform 的权限标识命名，约束后端鉴权、前端权限点、菜单资源、初始化数据和权限字典，避免出现以下问题：

- 不同模块各自发明权限码，导致授权和审计困难
- 只有粗粒度 CRUD 或直接使用 SQL 动作，无法表达真实业务行为
- 多租户、多模块场景中权限语义冲突，难以持续演进

本规范采用“规范码唯一来源”的治理思路：

- 运行时代码、JWT authority、前端权限点、菜单资源、权限字典只允许使用规范码
- 历史旧权限码只允许出现在一次性迁移脚本或迁移回顾文档中，用于完成就地改写

## 2. 规范格式

默认使用三段式：

```text
<domain>:<resource>:<action>
```

只有子资源需要独立授权时，才使用四段式：

```text
<domain>:<resource>:<subresource>:<action>
```

推荐顺序：

1. 优先三段式
2. 只有三段式无法准确表达时才升到四段式
3. 不再把两段式 `resource:action` 作为新规范

## 3. 命名规则

- 全部小写
- 层级分隔符统一使用冒号 `:`
- 多词资源或动作使用短横线 `-`
- `resource` 使用单数，如 `user`、`role`、`tenant`
- `action` 使用业务语义词，而不是数据库动作词

示例：

```text
system:user:view
system:user:reset-password
system:role:assign-permission
workflow:task:approve
system:role:permission:assign
```

反例：

```text
RESOURCE:user:read
user:update
sys_user_edit
user:manage
```

## 4. 动作分层

### 4.1 通用基础动作

推荐优先收敛到以下动作：

- `list`
- `view`
- `create`
- `edit`
- `delete`
- `import`
- `export`
- `enable`
- `disable`
- `assign`
- `audit`
- `config`

补充常见动作：

- `upload`
- `download`
- `preview`
- `print`
- `lock`
- `unlock`
- `publish`
- `unpublish`
- `bind`
- `unbind`

### 4.2 领域扩展动作

`workflow`：

- `submit`
- `approve`
- `reject`
- `withdraw`
- `claim`
- `delegate`
- `transfer`
- `terminate`

`scheduling`：

- `execute`
- `pause`
- `resume`
- `retry`
- `trigger`
- `view-audit`

`tenant`：

- `assign-package`
- `renew`
- `freeze`
- `unfreeze`

`iam` / `system`：

- `grant`
- `revoke`
- `reset-password`
- `assign-role`
- `assign-permission`

### 4.3 资源自定义动作

当通用动作和领域动作无法表达真实业务含义时，才引入自定义动作。自定义动作必须满足：

- 业务意义明确
- 拥有独立授权价值
- 需要单独审计或高风险隔离
- 命名清晰，不使用私有缩写

示例：

- `system:user:reset-password`
- `workflow:task:claim`
- `tenant:service:assign-package`

## 5. tiny-platform 默认约定

### 5.1 读权限

- 列表使用 `list`
- 详情使用 `view`
- `query` 不再作为权限动作；复杂检索仍归入 `list` 或使用更明确的业务动作

示例：

```text
system:user:list
system:user:view
```

### 5.2 写权限

- 新增使用 `create`
- 修改使用 `edit`
- 删除使用 `delete`
- 历史 `update` 必须直接迁移为 `edit`

示例：

```text
system:user:create
system:user:edit
system:user:delete
```

### 5.3 关系权限

默认优先三段式动作：

```text
system:user:assign-role
system:role:assign-permission
```

只有子资源自身需要独立授权维度时，才使用四段式：

```text
system:user:role:assign
system:role:permission:assign
```

### 5.4 模块入口权限

目录菜单或模块入口如果仅表示“进入某个业务域”，统一使用 `entry:view`：

```text
system:entry:view
scheduling:entry:view
profile:entry:view
```

这样可以避免继续使用两段式 `system:view`、`scheduling:view` 之类的旧风格权限。

### 5.5 控制面与平台级

平台级控制面（租户管理、幂等治理、字典平台等）当前态已收口为“**平台 scope + 规范码**”；不再把 `ROLE_ADMIN` / `ADMIN` 当作运行时 Guard 的快捷判断。导出能力另按 `system:export:view` / `system:export:manage` 区分“本人任务”与“管理全部任务”。

| 能力 | 规范码 | 说明 |
| --- | --- | --- |
| 租户管理（平台作用域） | `system:tenant:list`、`system:tenant:create` 等 | 当前 Guard 为“平台 scope + 对应规范码”。 |
| 幂等治理页 | `idempotent:ops:view` | 平台 scope + 本码。 |
| 字典平台管理 | `dict:platform:manage` | 平台级字典维护；Guard 为纯规范码。 |
| 导出（提交/查看本人任务） | `system:export:view` | 具备本码即可使用导出；查看全部任务/下载他人结果需 `system:export:manage`。 |

角色码 `ROLE_ADMIN` / 历史兼容码 `ADMIN` 仅作为角色数据、seed 与显式 `roleCodes` 语义存在，不再作为平台控制面 Guard 的快捷判断，见 [TINY_PLATFORM_LEGACY_COMPATIBILITY_INVENTORY.md](./TINY_PLATFORM_LEGACY_COMPATIBILITY_INVENTORY.md)。

## 6. 现状与迁移策略

当前仓库内曾存在多套权限风格并存：

- 三段式规范倾向：`system:user:view`
- 两段式历史风格：`menu:create`、`menu:update`
- 技术型读权限：`user:list:query`
- 模块汇总型动作：`scheduling:read`
- 过时示例风格：`RESOURCE:user:read`

迁移原则：

1. 新增代码只写规范码，不继续复制历史风格
2. 运行态 Guard、`@PreAuthorize`、JWT authority、前端 `v-permission`、菜单资源和初始化数据只接受规范码
3. 历史旧权限码只允许出现在一次性迁移脚本中，作为 `WHERE` / `CASE` 的被替换值
4. 同一能力必须就地收敛到唯一规范码，不维护 alias 表，不在运行态保留多码并存

典型迁移目标：

| 历史值 | 规范码 | 说明 |
| --- | --- | --- |
| `user:list:query` | `system:user:list` | 历史列表查询权限 |
| `user:update` | `system:user:edit` | 历史更新动作 |
| `menu:view` / `menu:list` | `system:menu:list` | 菜单控制面读权限 |
| `scheduling:dag:list` / `scheduling:task:list` / `scheduling:task-type:list` / `scheduling:dag-run:list` | `scheduling:console:view` | 历史调度页面只读权限 |
| `scheduling:audit:list` | `scheduling:audit:view` | 历史调度审计列表权限 |
| `scheduling:read` | `scheduling:console:view` | 调度控制面只读权限 |
| `system:view` / `scheduling` | `system:entry:view` / `scheduling:entry:view` | 模块入口菜单权限 |

## 7. 与实现层的结合

后端：

- `@PreAuthorize("hasAuthority('system:user:create')")`
- AccessGuard 常量只定义规范码

前端：

- 路由 `meta.permission`
- `v-permission`
- 按钮级操作声明

数据与字典：

- `menu / ui_action / api_endpoint.permission`（兼容字符串 / 运营可读字段）
- `menu / ui_action / api_endpoint.required_permission_id`
- 初始化 SQL / Liquibase
- 权限字典导出接口与权限管理页面

## 8. 治理要求

- 新增权限码必须同步更新后端、前端、初始化数据和文档
- 自定义动作必须经过评审后纳入字典
- 定期扫描重复权限、未使用权限、旧权限码残留
- 高风险动作应具备单独审计日志策略

## 9. 与 `.agent` 规则的关系

- 可执行约束位于 [92-tiny-platform-permission.rules.md](../.agent/src/rules/92-tiny-platform-permission.rules.md)
- 平台级总规则位于 [90-tiny-platform.rules.md](../.agent/src/rules/90-tiny-platform.rules.md)
- 认证链路与 token/claims 约束位于 [91-tiny-platform-auth.rules.md](../.agent/src/rules/91-tiny-platform-auth.rules.md)

本文件负责完整说明与迁移指导；AI 生成和代码审查时，以 `.agent/src/rules/**` 为准。

## 10. 与授权模型文档的边界

本文件只回答“权限码如何命名、如何迁移、如何避免旧码并存”，不负责定义完整授权模型。

以下内容不以本文件为主，而以 [TINY_PLATFORM_AUTHORIZATION_MODEL.md](./TINY_PLATFORM_AUTHORIZATION_MODEL.md) 为主：

- 角色如何分配
- 是否引入 `role_assignment`
- 是否引入 `tenant_user`
- `RBAC3` 的继承、互斥、先决条件、基数限制
- `Scope`（PLATFORM/TENANT/ORG/DEPT）如何表达
- `Data Scope` 如何建模与计算

当前 tiny-platform 的运行态功能权限真相源是：

- `role_permission -> permission`
- 后端 `@PreAuthorize` / AccessGuard 与 `*_permission_requirement` 的消费链
- JWT / Session 中显式 `permissions` 契约
- 初始化 SQL / Liquibase 中的 `permission` 主数据与 carrier 绑定

当前仍可能被误读为“真相源”、但实际上只剩兼容/运营语义的包括：

- `menu / ui_action / api_endpoint.permission` 字符串
- `/sys/resources` 与 `Resource` 兼容聚合 DTO
- 显式 `roleCodes` 与少量角色码兼容承接

在已完成统一迁移的前提下，不应在业务模块中自行引入第二套独立 `permission` 目录或平行权限语义；若文档叙述与 `.agent/src/rules/**` 冲突，以规则源为准。
