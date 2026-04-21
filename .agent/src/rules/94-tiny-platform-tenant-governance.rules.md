# 94 tiny-platform 租户治理修复基线（平台特定）

## 适用范围

- 适用于：租户生命周期、租户治理审计、租户配额、租户控制面前端入口
- 典型文件：`**/tenant/**`、`**/auth/audit/**`、`**/oauth/session/**`、`**/export/**`、`**/webapp/src/views/tenant/**`、`**/webapp/src/views/audit/**`
- 配套文档：`docs/TINY_PLATFORM_TENANT_GOVERNANCE.md`、`docs/TINY_PLATFORM_TENANT_GOVERNANCE_CURSOR_FIX_PROMPT.md`

## 禁止（Must Not）

- ❌ 不得回退 `FROZEN` 对登录、发 token、新会话创建和非登录请求入口阻断的既有行为。
- ❌ 不得继续在租户治理链路中直接抛 `RuntimeException("...")` 作为最终错误模型。
- ❌ 不得继续手拼租户治理审计 `eventDetail` 的 JSON 字符串。
- ❌ 不得为了满足配额需求虚构新的“通用文件中心”“统一导入中心”或不存在的上传链路。
- ❌ 不得把平台作用域治理例外扩散成租户作用域白名单。
- ❌ 本轮不得顺手扩大到 `@DataScope` 扩面、PBAC、全量审计中心重构。
- ❌ 不得把 `POST /sys/tenants/platform-template/initialize` 当作“新建租户向导”的单租户初始化步骤或最终提交入口复用。
- ❌ 不得在“新建租户”步骤里直接暴露完整角色树 / 菜单树 / 权限树做手工授权，导致向导退化成 RBAC 控制台。
- ❌ 不得在步骤切换时偷偷创建租户、偷偷创建管理员或提前落平台模板副本，形成半初始化脏数据。

## 必须（Must）

- ✅ 租户生命周期读路径采用“租户作用域 fail-closed，平台作用域治理继续可用”的固定策略。
- ✅ `FROZEN`：拒绝登录 / 发 token / 新会话创建；除 `/login` 等需要返回业务错误的入口外，租户作用域请求统一拒绝。
- ✅ `DECOMMISSIONED`：租户作用域请求统一拒绝；平台作用域仅允许显式白名单内的治理只读动作。
- ✅ 生命周期例外必须落实为代码中的可执行策略，不得只停留在文档说明。
- ✅ 生命周期例外采用“默认拒绝 + 显式白名单”模型，不得通过散落在各 Controller 的隐式 if/else 形成第二套策略。
- ✅ `tenant scope` 在 `FROZEN` / `DECOMMISSIONED` 下不得保留任何治理例外；例外只允许存在于 `activeScopeType=PLATFORM`。
- ✅ `FROZEN` 允许的最小平台白名单仅限：
  - 租户基础详情查看
  - 授权审计 / 认证审计查看
  - 审计 CSV 导出
  - 账单 / 配额 / 套餐查看
  - 历史导出 / 历史调度 / 历史会话查看
- ✅ `FROZEN` 明确禁止：
  - 任何业务写操作
  - 任何新导出任务
  - 调度执行 / 重试 / 暂停 / 恢复
  - 普通业务数据查询 API 白名单化
- ✅ `DECOMMISSIONED` 允许的最小平台白名单仅限：
  - 租户基础详情查看
  - 授权审计 / 认证审计查看
  - 审计 CSV 导出
  - 账单 / 配额查看
  - 已归档产物查看 / 下载
- ✅ `DECOMMISSIONED` 明确禁止：
  - 任何新任务创建
  - 历史业务查询 API 白名单化
  - 调度执行 / 重试 / 暂停 / 恢复
  - 任何业务写操作
- ✅ 审计导出、归档下载、账单导出这类高敏感只读动作必须使用独立权限码，不得复用普通 `view`。
- ✅ 所有 `FROZEN` / `DECOMMISSIONED` 白名单访问都必须进入统一审计，至少记录：`tenantId`、`tenantLifecycleStatus`、`actor`、`resourcePermission`、`reason`、`result`。
- ✅ 对 `DECOMMISSIONED` 的高敏感平台白名单访问，应默认要求 MFA 已完成和工单 / 原因字段；若当前实现缺少校验链路，文档与规则必须明确标记为待补，不得默认为开放。
- ✅ 租户治理错误必须统一收敛到 `BusinessException`、`NotFoundException`、`ErrorCode`，并通过全局异常处理输出稳定错误响应。
- ✅ 生命周期非法流转、已下线后禁止修改、禁止直接修改 `lifecycleStatus` 等状态问题，必须使用稳定错误码表达，不得让前端依赖 message 文案猜测。
- ✅ 租户治理审计 detail 必须使用结构化 JSON 序列化，至少包含：`action`、`tenant`、`operator`、`before`、`after`、`diff`；存在业务原因时同时包含 `reason`。
- ✅ 授权审计列表、summary、CSV 导出在租户治理 detail 上必须保持字段口径一致。
- ✅ 本轮租户治理审计查询至少支持：`actorUserId`、`result`、`resourcePermission`、`detailReason`。
- ✅ `maxUsers` 必须在真实用户写路径生效，至少覆盖：租户创建时初始管理员创建、`UserServiceImpl.create(...)`、`UserServiceImpl.createFromDto(...)`。
- ✅ `maxUsers = null` 视为无限制；若新建租户时 `maxUsers < 1`，必须判定为非法参数。
- ✅ `maxStorageGb` 本轮只允许落到仓库里真实存在的存储写链路，至少覆盖头像上传和导出文件落盘。
- ✅ 配额统计服务应可复用；总存储量至少包含头像与导出文件已落盘大小，不得仅存字段不执行。
- ✅ 租户前端治理入口至少补齐 `includeDeleted` 查询开关和租户详情只读视图，并与现有后端 API 对齐。
- ✅ 当“新建租户”链路同时涉及租户创建、平台模板派生、初始管理员创建和默认角色/菜单注入时，前端应采用受控 `Steps` 向导，而不是继续停留在单表单弹窗。
- ✅ 新建租户向导前置步骤只允许做本地校验、服务端预检查和结果预览；真正写库必须通过最终一次提交触发统一后端编排。
- ✅ `POST /sys/tenants` 应继续承担最终提交语义；如需支持步骤校验，应新增 dry-run / precheck 契约，而不是把创建接口拆成多次半写入。
- ✅ `/sys/tenants/platform-template/initialize` 必须继续保持“平台模板缺失时的全局补齐”定位，不得在产品语义上漂移成租户创建向导的一部分。
- ✅ 新建租户 / 租户初始化向导类任务卡必须显式写清“本卡负责什么、不负责什么、Liquibase 责任边界、最低验证门禁”；不得把 precheck、最终提交、结果页、平台模板补齐或异步化责任口头外推到下一卡。

## 应该（Should）

- ⚠️ 生命周期策略应优先通过统一守卫或过滤层表达，避免不同模块各自发明例外。
- ⚠️ 建议以“端点类别 -> 生命周期白名单”方式集中建模，例如 `TenantLifecycleReadPolicy` / `TenantLifecycleAccessGuard`，避免业务模块自行判断。
- ⚠️ 租户详情视图优先做成列表侧抽屉或弹窗，不强制新增独立路由页面。
- ⚠️ 若实现成本可控，可在审计筛选中增加 `afterLifecycleStatus`、`beforeLifecycleStatus`。
- ⚠️ 配额服务建议优先提供“当前用量 + 目标增量”的统一校验接口，便于头像、导出、后续附件链路复用。
- ⚠️ 新测试应优先断言状态码、错误码、拒绝路径和边界例外，而不是只断言 message。
- ⚠️ 新建租户向导建议拆出专用容器组件（如 `TenantCreateWizard`），`TenantForm` 保留为基础信息或兼容编辑子块，避免单组件继续膨胀。
- ⚠️ 结果页建议明确展示：租户 ID、初始化摘要、初始管理员账号标识、后续可跳转的治理入口（详情 / 权限摘要 / 模板 diff）。

## 可以（May）

- 💡 可以新增租户治理专用的 detail builder / serializer，集中维护审计结构。
- 💡 可以为配额校验补充轻量统计缓存，但缓存失效策略必须明确且默认安全。
- 💡 可以在前端详情视图中展示生命周期、套餐、到期时间、配额、联系人、备注、创建/更新时间等只读信息。

## 例外与裁决

- `@DataScope` 扩面由 `docs/TINY_PLATFORM_DATASCOPE_EXPANSION_GUIDE.md` 与授权模型主线控制，本规范不处理。
- 租户命名、平台租户语义和模板术语由 `docs/TINY_PLATFORM_TENANT_NAMING_GUIDELINES.md` 与 `docs/TINY_PLATFORM_TENANT_GOVERNANCE.md` 约束。
- 认证链路、JWT / Session / MFA 的基础约束由 `91-tiny-platform-auth.rules.md` 约束。
- 授权模型与作用域主线仍以 `93-tiny-platform-authorization-model.rules.md` 为准；本规范只约束租户治理剩余修复基线。

## 示例

### ✅ 正例

```text
FROZEN:
- /login 继续进入认证链并返回“租户已冻结”
- 租户作用域非登录请求入口统一 403 tenant_frozen
- 平台作用域仅允许显式白名单内的详情/审计/账单/配额/历史记录查看

配额:
- 创建初始管理员前先校验 maxUsers
- 头像上传前按 tenant 当前已用字节 + 待写入字节校验 maxStorageGb
```

### ❌ 反例

```text
TenantServiceImpl 继续抛 RuntimeException("租户不存在")
eventDetail 用字符串拼出 {\"before\":\"ACTIVE\"}
为 maxStorageGb 新增一个并不存在的“统一附件中心”再去做校验
前端列表支持 includeDeleted，但搜索/重置/分页没有把该参数带回接口
在 DECOMMISSIONED 下顺手放开“所有平台只读接口”
```
