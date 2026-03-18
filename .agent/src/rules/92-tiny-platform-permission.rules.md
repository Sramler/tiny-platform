# 92 tiny-platform 权限标识规范（平台特定）

## 适用范围

- 适用于：权限标识定义、`@PreAuthorize` / AccessGuard 鉴权、菜单/资源初始化数据、前端路由权限与按钮权限、权限字典文档
- 典型文件：`**/auth/**`、`**/security/**`、`**/resource/**`、`**/menu/**`、`**/*AccessGuard.java`、`**/*Controller.java`、`**/*.sql`、`**/*.vue`
- 不适用于：`ROLE_*` 角色码、`SCOPE_*` OAuth2 Scope、第三方系统原生权限/Scope 命名

## 禁止（Must Not）

- ❌ 新增权限码继续使用数据库语义动作作为规范名，如 `select`、`insert`、`update`，或以 `update` 代替规范动作 `edit`。
- ❌ 新增权限码缺少业务域，仍沿用无域前缀的两段式 `resource:action` 作为默认规范。
- ❌ 同一能力在运行态并存多个权限码，导致后端、前端、菜单资源和初始化数据语义分叉。
- ❌ 使用语义模糊的动作名，如 `manage`、`handle`、`process`、`operate`。
- ❌ 混用大小写、下划线、驼峰等多种风格，如 `ResetPassword`、`reset_password`、`assignRole`。
- ❌ 把列表、详情、审批、重置密码等不同授权价值的行为合并到一个宽泛权限中。
- ❌ 将页面可见性权限和后台写操作权限混为一个权限码，导致只读用户获得写能力。

## 必须（Must）

- ✅ 新增权限标识默认使用三段式：`<domain>:<resource>:<action>`。
- ✅ 只有子资源语义独立且确有授权价值时，才使用四段式：`<domain>:<resource>:<subresource>:<action>`。
- ✅ `domain` 必须对应稳定业务域或模块边界，如 `system`、`iam`、`workflow`、`scheduling`、`tenant`、`dict`、`file`、`plugin`。
- ✅ `resource` 必须使用单数、业务语义明确的名称，如 `user`、`role`、`menu`、`resource`、`task`、`process`、`job`。
- ✅ `action` 必须使用业务动作词；多词动作使用短横线连接，如 `reset-password`、`assign-role`、`view-audit`。
- ✅ 通用读权限必须区分 `list` 与 `view`；复杂检索仍应归入 `list` 或明确业务动作，不再新增 `query` 作为权限动作。
- ✅ 通用写权限的规范动作使用 `create`、`edit`、`delete`；历史 `update` 类权限必须在代码和数据中直接迁移为 `edit`。
- ✅ 新增关系类权限时，默认使用三段式动作表达，如 `system:user:assign-role`、`system:role:assign-permission`；仅当子资源需要独立授权体系时才升级到四段式。
- ✅ 每个新增权限码都必须在后端鉴权、前端声明、初始化数据/字典、相关文档中保持一致。
- ✅ 运行时代码、JWT authority、菜单资源、前端 `v-permission` 和对外文档只允许使用规范码。
- ✅ 在未完成统一迁移前，运行态权限真相源仍以 `resource.permission` 为准；若拟引入独立 `permission` 目录，必须先更新 `docs/TINY_PLATFORM_AUTHORIZATION_MODEL.md` 并给出迁移方案。

## 应该（Should）

- ⚠️ tiny-platform 的通用基础动作集优先收敛为：`list`、`view`、`create`、`edit`、`delete`、`import`、`export`、`enable`、`disable`、`assign`、`audit`、`config`。
- ⚠️ 页面/菜单访问权限通常使用 `domain:resource:view`；列表接口如果需要独立控制，优先使用 `domain:resource:list`。
- ⚠️ 模块入口或目录菜单若仅表示进入某个业务域，优先使用 `domain:entry:view`，例如 `system:entry:view`、`scheduling:entry:view`。
- ⚠️ 领域扩展动作应按模块维护小而稳定的动作字典，例如：
- `workflow`：`submit`、`approve`、`reject`、`withdraw`、`claim`、`delegate`、`transfer`、`terminate`
- `scheduling`：`execute`、`pause`、`resume`、`retry`、`trigger`、`view-audit`
- `tenant`：`assign-package`、`renew`、`freeze`、`unfreeze`
- ⚠️ 对外文档、权限表、前端 `v-permission`、后端 `hasAuthority` 示例应统一使用规范码。
- ⚠️ 通配符权限如 `scheduling:*` 只应用于少数模块级管理员场景，不应替代细粒度授权设计。

## 可以（May）

- 💡 可以维护独立的 `permission_action` 或等价字典，沉淀通用动作、领域动作、资源自定义动作。
- 💡 可以在权限字典中记录“规范码、适用资源、风险级别、审计要求、前端入口”。
- 💡 一次性迁移脚本可以在 `WHERE` / `CASE` 中引用旧权限码，用于把历史数据改写为规范码。

## 例外与裁决

- 一次性迁移：Liquibase / SQL 迁移可临时出现 `menu:update`、`resource:list:query`、`scheduling:read` 等旧值，前提是目标是把它们直接改写为规范码。
- 运行态收口：Controller、AccessGuard、前端权限判断、JWT authority、初始化数据不得继续接受或下发旧权限码。
- 角色/Scope：`ROLE_ADMIN`、`SCOPE_profile` 等不受本规范约束，分别遵循角色命名与 OAuth2 规范。
- 授权模型：角色分配、Scope、Data Scope、`role_assignment`、`tenant_user` 等授权结构不由本规范定义，统一遵循 `docs/TINY_PLATFORM_AUTHORIZATION_MODEL.md` 与 `93-tiny-platform-authorization-model.rules.md`。
- 冲突时：`90-tiny-platform.rules.md` 优先级最高；认证链路相关内容由 `91-tiny-platform-auth.rules.md` 约束；权限命名本身由本规范收口。

## 示例

### ✅ 正例

```text
system:user:list
system:user:view
system:user:create
system:user:edit
system:user:delete
system:user:reset-password
system:user:assign-role
workflow:task:approve
workflow:task:reject
scheduling:job:execute
system:role:permission:assign
```

### ⚠️ 迁移示例

```text
UPDATE resource SET permission = 'system:user:edit' WHERE permission = 'user:update';
UPDATE resource SET permission = 'system:user:list' WHERE permission = 'user:list:query';
```

### ❌ 反例

```text
user:update
RESOURCE:user:read
sys_user_edit
user:manage
menu:list:query
```
