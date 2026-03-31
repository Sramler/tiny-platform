# Tiny Platform 租户治理修复 Prompt（Cursor 参考文档）

> 状态：可执行修复提示词  
> 适用范围：`tenant / auth / audit / user / export / webapp/tenant`  
> 目标：为 Cursor 提供“问题 2 / 3 / 4 / 5 / 6”的统一修复基线，避免它再次自行发明第二套策略  
> 非目标：本轮**不**继续扩 `@DataScope`；问题 1（`FROZEN` 登录与入口阻断）已单独收口，不在本提示词中重复实现

---

## 1. 使用方式

把本文件中的“复制给 Cursor 的提示词”整段交给 Cursor，并要求它：

- 先阅读本文件列出的约束和设计裁决；
- 再按本文件的文件范围、边界和验收标准实施；
- 不要自行扩大范围到 `@DataScope` 扩面、PBAC、全量审计中心重构等后续议题。

---

## 2. 本轮要修的问题

本轮只覆盖下列问题：

2. 生命周期读路径策略不够明确  
3. 错误模型与 API 返回不一致  
4. 租户治理审计 detail 缺少结构化与字段级可查询性  
5. 配额 `maxUsers / maxStorageGb` 仍是“只存不执行”  
6. 租户前端治理入口仍缺少 `includeDeleted` 与详情视图

问题 1 已处理：

- `FROZEN` 已阻断登录 / 发 token / 新会话创建；
- `FROZEN` 非登录请求也已在入口统一拒绝；
- 本提示词不得回退这条行为。

---

## 3. 设计裁决

下面这些是**本轮固定设计**，Cursor 不应重新发明。

### 3.1 生命周期读路径策略

采用“租户作用域 fail-closed，平台作用域治理继续可用”的策略。

#### `ACTIVE`

- 正常放行。

#### `FROZEN`

- 登录 / 发 token / 新会话创建：拒绝。
- 租户作用域请求：除 `/login` 这类需要返回业务错误的入口外，其余请求统一在入口拒绝。
- 租户内写操作：继续由 `TenantLifecycleGuard` 做防御式拦截。
- 平台作用域治理查询：允许。
- 平台作用域治理导出 / 审计 / 租户详情：允许。

#### `DECOMMISSIONED`

- 所有租户作用域请求统一拒绝。
- 平台作用域治理查询、审计、导出仍允许。
- 不为已下线租户开放任何租户内例外白名单。

#### 本轮实现要求

- 不再为 `FROZEN` 单独保留“租户内只读 API”白名单。
- 生命周期例外仅允许“平台作用域治理 API”继续访问目标租户记录。
- 需要在代码中形成可执行策略，而不是只写文档注释。

---

### 3.2 错误模型统一

`TenantServiceImpl` 及相关治理链路禁止继续直接抛 `RuntimeException("...")`。

统一使用：

- `BusinessException`
- `NotFoundException`
- `ErrorCode`

错误码裁决如下：

| 场景 | 错误码 |
| --- | --- |
| 必填字段缺失 | `MISSING_PARAMETER` |
| 参数格式错误 / 非法状态值 | `INVALID_PARAMETER` |
| 编码/域名重复 | `RESOURCE_ALREADY_EXISTS` |
| 生命周期流转非法 / 已下线后禁止修改 / 禁止直接改 lifecycleStatus | `RESOURCE_STATE_INVALID` |
| 资源不存在 | `NOT_FOUND` |
| 平台模板或初始化不变量破坏（如缺少 `ROLE_ADMIN`） | `BUSINESS_ERROR` |
| 作用域不符 / 治理动作无权限 | `FORBIDDEN` |

要求：

- Controller 继续走全局异常处理，返回统一 Problem / ErrorResponse；
- 前端不再依赖 message 文案猜错误类型；
- 新测试要断言状态码与错误码，而不只断言 message。

---

### 3.3 审计 detail 结构化

租户治理审计 detail 不再手拼 JSON 字符串。

统一改为：

- 使用 `ObjectMapper` 或集中帮助类序列化；
- 定义稳定字段结构；
- 支持最少一组字段级查询。

#### 建议 detail 结构

```json
{
  "action": "TENANT_FREEZE",
  "tenant": {
    "id": 12,
    "code": "acme",
    "name": "Acme"
  },
  "operator": {
    "userId": 1,
    "username": "platform_admin",
    "scopeType": "PLATFORM"
  },
  "reason": "manual freeze",
  "before": {
    "enabled": true,
    "lifecycleStatus": "ACTIVE",
    "maxUsers": 100
  },
  "after": {
    "enabled": true,
    "lifecycleStatus": "FROZEN",
    "maxUsers": 100
  },
  "diff": {
    "lifecycleStatus": {
      "before": "ACTIVE",
      "after": "FROZEN"
    }
  }
}
```

#### 本轮至少支持的查询字段

不要尝试做全量 JSON DSL。本轮只做够用的字段级查询：

- `actorUserId`
- `result`
- `resourcePermission`
- `detailReason`

如果实现成本可控，再加：

- `afterLifecycleStatus`
- `beforeLifecycleStatus`

要求：

- 列表查询、summary、CSV 导出三条链路保持一致；
- 前端授权审计页补对应筛选项；
- 不允许继续保留手拼 JSON 逻辑。

---

### 3.4 配额执行策略

#### `maxUsers`

本轮必须生效在真实用户写路径：

- 租户创建时的初始管理员创建；
- `UserServiceImpl.create(...) / createFromDto(...)`；
- 如果当前仓库没有独立“用户导入”端点，不要为了满足描述去虚构一个导入接口。

实现要求：

- 提取可复用的 `TenantQuotaService` 或等价服务；
- 校验基于当前租户 `ACTIVE` membership 数量；
- `maxUsers = null` 视为无限制；
- 新建租户如果 `maxUsers` 小于 1，应直接视为非法参数，因为系统要求创建初始管理员。

#### `maxStorageGb`

本轮先落到仓库里真实存在的存储写链路，不要虚构“通用文件中心”：

- 用户头像上传：[AvatarServiceImpl.java](/Users/bliu/code/tiny-platform/tiny-oauth-server/src/main/java/com/tiny/platform/core/oauth/service/impl/AvatarServiceImpl.java)
- 导出文件落盘：[ExportTaskService.java](/Users/bliu/code/tiny-platform/tiny-oauth-server/src/main/java/com/tiny/platform/infrastructure/export/service/ExportTaskService.java)

建议策略：

- 统一按字节统计；
- 头像大小来自 `user_avatar.file_size`；
- 导出文件大小需要补记录字段，例如 `export_task.file_size_bytes`，在 `markSuccess(...)` 时写入；
- 总使用量 = 头像存储 + 导出文件存储；
- 超限时返回 `RESOURCE_STATE_INVALID` 或 `BUSINESS_ERROR`，但整个系统内要统一一种。

注意：

- 不要求本轮做对象存储、附件中心、跨模块存储治理；
- 但要把配额服务设计成后续可以复用。

---

### 3.5 前端治理入口

本轮前端至少补两个治理入口：

1. `includeDeleted`
2. 租户详情视图

#### `includeDeleted`

要求：

- `tenant.ts` 暴露 `includeDeleted` 查询参数；
- `Tenant.vue` 搜索区域增加显式开关；
- 列表、分页、重置、搜索要全部带上它。

#### 租户详情视图

本轮优先做“详情抽屉 / 详情弹窗”，不强制上升为独立路由页面。

要求：

- 复用现有 `GET /sys/tenants/{id}`；
- 详情中展示生命周期、套餐、到期时间、配额、联系人、备注、创建/更新时间；
- 从租户列表可直接进入详情；
- 详情是只读视图，不要和编辑表单混在一起。

---

## 4. 文件范围建议

### 后端

- `tiny-oauth-server/src/main/java/com/tiny/platform/infrastructure/tenant/service/TenantServiceImpl.java`
- `tiny-oauth-server/src/main/java/com/tiny/platform/infrastructure/tenant/guard/TenantLifecycleGuard.java`
- `tiny-oauth-server/src/main/java/com/tiny/platform/core/oauth/tenant/TenantContextFilter.java`
- `tiny-oauth-server/src/main/java/com/tiny/platform/infrastructure/auth/audit/service/AuthorizationAuditService.java`
- `tiny-oauth-server/src/main/java/com/tiny/platform/infrastructure/auth/audit/service/AuthorizationAuditQuery.java`
- `tiny-oauth-server/src/main/java/com/tiny/platform/application/controller/audit/AuthorizationAuditController.java`
- `tiny-oauth-server/src/main/java/com/tiny/platform/core/oauth/service/impl/AvatarServiceImpl.java`
- `tiny-oauth-server/src/main/java/com/tiny/platform/infrastructure/export/service/ExportTaskService.java`
- `tiny-oauth-server/src/main/java/com/tiny/platform/infrastructure/export/persistence/ExportTaskEntity.java`
- `tiny-oauth-server/src/main/java/com/tiny/platform/infrastructure/auth/user/service/UserServiceImpl.java`
- `tiny-oauth-server/src/main/resources/db/changelog/*.yaml`

### 前端

- `tiny-oauth-server/src/main/webapp/src/api/tenant.ts`
- `tiny-oauth-server/src/main/webapp/src/views/tenant/Tenant.vue`
- `tiny-oauth-server/src/main/webapp/src/views/tenant/TenantForm.vue`
- `tiny-oauth-server/src/main/webapp/src/api/audit.ts`
- `tiny-oauth-server/src/main/webapp/src/views/audit/AuthorizationAudit.vue`

### 文档

- `docs/TINY_PLATFORM_TENANT_GOVERNANCE.md`
- `docs/TINY_PLATFORM_AUTHORIZATION_TASK_LIST.md`
- 必要时同步 `docs/TINY_PLATFORM_MODULE_GAP_ANALYSIS.md`

---

## 5. 验收标准

### 生命周期策略

- `FROZEN` 和 `DECOMMISSIONED` 的租户作用域行为有可执行代码，不只停留在文档。
- 平台作用域查询冻结/下线租户记录仍然可用。
- 不新增租户内例外白名单。

### 错误模型

- `TenantServiceImpl` 不再残留租户治理相关 `RuntimeException("...")`。
- 错误码、HTTP 状态码、前端错误处理口径一致。

### 审计

- 租户治理审计 detail 改为结构化序列化。
- 列表 / summary / 导出支持新增筛选字段。
- 前端能使用这些字段筛选。

### 配额

- `maxUsers` 至少在租户创建初始管理员和普通用户创建上生效。
- `maxStorageGb` 至少在头像上传和导出文件落盘上生效。

### 前端

- 租户列表支持 `includeDeleted`。
- 租户详情只读视图可从列表进入。

---

## 6. 必补测试

### 后端

- `TenantServiceImplTest`
- `TenantControllerTest`
- `TenantContextFilterTest`
- `AuthorizationAuditControllerTest`
- `AuthorizationAuditServiceTest`
- `UserServiceImplTest`
- `AvatarServiceImplTest`
- `ExportTaskServiceTest`

### 前端

- `src/api/tenant.test.ts`
- `src/views/tenant/Tenant.test.ts`
- 租户详情组件 / 抽屉测试
- `src/api/audit.test.ts`
- `src/views/audit/AuthorizationAudit.test.ts`

### 最低执行命令

```bash
mvn -pl tiny-oauth-server -Dtest=TenantServiceImplTest,TenantControllerTest,TenantContextFilterTest,AuthorizationAuditControllerTest,AuthorizationAuditServiceTest,UserServiceImplTest,AvatarServiceImplTest,ExportTaskServiceTest test
npm --prefix tiny-oauth-server/src/main/webapp run test:unit -- src/api/tenant.test.ts src/views/tenant/Tenant.test.ts src/api/audit.test.ts src/views/audit/AuthorizationAudit.test.ts
mvn -pl tiny-oauth-server -DskipTests compile
npm --prefix tiny-oauth-server/src/main/webapp run build-only
```

---

## 7. 复制给 Cursor 的提示词

```text
请为 tiny-platform 修复“租户治理剩余问题 2 / 3 / 4 / 5 / 6”，并严格按下面约束执行，不要自行扩大范围。

必须先阅读：
- AGENTS.md
- docs/TINY_PLATFORM_TENANT_GOVERNANCE.md
- docs/TINY_PLATFORM_AUTHORIZATION_MODEL.md
- docs/TINY_PLATFORM_AUTHORIZATION_TASK_LIST.md
- docs/TINY_PLATFORM_TENANT_GOVERNANCE_CURSOR_FIX_PROMPT.md
- docs/TINY_PLATFORM_TESTING_PLAYBOOK.md
- .agent/src/rules/50-testing.rules.md
- .agent/src/rules/90-tiny-platform.rules.md
- .agent/src/rules/91-tiny-platform-auth.rules.md
- .agent/src/rules/93-tiny-platform-authorization-model.rules.md

本轮只修：
2. 生命周期读路径策略
3. 错误模型与 API 返回一致性
4. 审计 detail 结构化与字段级可查询
5. 配额 maxUsers / maxStorageGb 执行
6. 租户前端治理入口（includeDeleted + 详情视图）

明确禁止：
- 不继续扩 @DataScope
- 不做 PBAC / OPA / 策略中心
- 不做“全系统统一文件中心”式重构
- 不虚构用户导入接口
- 不把运行时登录链路、菜单树、平台模板 bootstrap 改成 owner 过滤模式

固定设计裁决：
- FROZEN / DECOMMISSIONED 采用“租户作用域 fail-closed，平台作用域治理继续可用”
- FROZEN 不再保留租户内只读 API 白名单
- TenantServiceImpl 禁止继续抛 RuntimeException("...")
- 租户治理审计 detail 禁止继续手拼 JSON 字符串
- maxStorageGb 本轮只落到真实存在的存储写链路：头像上传 + 导出文件
- 租户详情本轮优先实现只读详情抽屉，不强制做独立路由页面

错误码裁决：
- 缺少参数 -> MISSING_PARAMETER
- 参数非法/格式错误 -> INVALID_PARAMETER
- 编码/域名重复 -> RESOURCE_ALREADY_EXISTS
- 生命周期非法/状态不允许 -> RESOURCE_STATE_INVALID
- 资源不存在 -> NOT_FOUND
- 模板或初始化不变量破坏 -> BUSINESS_ERROR
- 作用域不符 -> FORBIDDEN

审计 detail 最少结构：
- action
- tenant{id,code,name}
- operator{userId,username,scopeType}
- reason
- before
- after
- diff

审计最少新增查询字段：
- actorUserId
- result
- resourcePermission
- detailReason

文件范围优先：
- tiny-oauth-server/src/main/java/com/tiny/platform/infrastructure/tenant/service/TenantServiceImpl.java
- tiny-oauth-server/src/main/java/com/tiny/platform/infrastructure/tenant/guard/TenantLifecycleGuard.java
- tiny-oauth-server/src/main/java/com/tiny/platform/core/oauth/tenant/TenantContextFilter.java
- tiny-oauth-server/src/main/java/com/tiny/platform/infrastructure/auth/audit/service/AuthorizationAuditService.java
- tiny-oauth-server/src/main/java/com/tiny/platform/infrastructure/auth/audit/service/AuthorizationAuditQuery.java
- tiny-oauth-server/src/main/java/com/tiny/platform/application/controller/audit/AuthorizationAuditController.java
- tiny-oauth-server/src/main/java/com/tiny/platform/core/oauth/service/impl/AvatarServiceImpl.java
- tiny-oauth-server/src/main/java/com/tiny/platform/infrastructure/export/service/ExportTaskService.java
- tiny-oauth-server/src/main/java/com/tiny/platform/infrastructure/export/persistence/ExportTaskEntity.java
- tiny-oauth-server/src/main/java/com/tiny/platform/infrastructure/auth/user/service/UserServiceImpl.java
- tiny-oauth-server/src/main/webapp/src/api/tenant.ts
- tiny-oauth-server/src/main/webapp/src/views/tenant/Tenant.vue
- tiny-oauth-server/src/main/webapp/src/api/audit.ts
- tiny-oauth-server/src/main/webapp/src/views/audit/AuthorizationAudit.vue

交付要求：
- 直接修改代码，不只给方案
- 给出修改文件清单
- 给出执行过的验证命令
- 给出尚未覆盖的剩余风险
- 同步更新相关文档状态

至少执行：
- mvn -pl tiny-oauth-server -Dtest=TenantServiceImplTest,TenantControllerTest,TenantContextFilterTest,AuthorizationAuditControllerTest,AuthorizationAuditServiceTest,UserServiceImplTest,AvatarServiceImplTest,ExportTaskServiceTest test
- npm --prefix tiny-oauth-server/src/main/webapp run test:unit -- src/api/tenant.test.ts src/views/tenant/Tenant.test.ts src/api/audit.test.ts src/views/audit/AuthorizationAudit.test.ts
- mvn -pl tiny-oauth-server -DskipTests compile
- npm --prefix tiny-oauth-server/src/main/webapp run build-only
```

---

## 8. 备注

如果 Cursor 在实现过程中发现：

- 当前审计仓储层不适合直接支持 JSON 字段检索；
- `maxStorageGb` 需要补表字段才能落地；
- 平台作用域治理 API 存在和生命周期入口策略冲突的旧逻辑；

应优先做“最小可执行修复”，并把剩余结构性问题写入结果说明，而不是为了“一次做完”引入第二轮大重构。

---

## 9. 单点执行 Prompt（用于防止 Cursor 停在计划层）

如果 Cursor 对“2 / 3 / 4 / 5 / 6 一次性全做”只返回计划、不开始修改，就不要继续围绕范围来回拉扯，直接把下面某一条整段交给它，并要求它只完成这一条。

### 9.1 只修第 2 点：生命周期读路径策略

```text
按 AGENTS.md、docs/TINY_PLATFORM_TENANT_GOVERNANCE_CURSOR_FIX_PROMPT.md 和 .agent/src/rules/94-tiny-platform-tenant-governance.rules.md 直接开始实施，不要只输出计划。

这轮只修复第 2 点：生命周期读路径策略。

固定约束：
- 不扩散到 3 / 4 / 5 / 6
- 不回退已完成的 FROZEN 登录 / 发 token / 新会话阻断
- 采用“租户作用域 fail-closed，平台作用域治理继续可用”
- 不新增租户内只读 API 白名单
- 只允许平台作用域治理查询 / 审计 / 导出继续访问目标租户记录

要求：
1. 直接修改代码，不要停在分析
2. 只改与生命周期策略相关的最小文件集
3. 补后端回归测试，至少覆盖：
   - FROZEN 租户作用域请求拒绝
   - DECOMMISSIONED 租户作用域请求拒绝
   - 平台作用域治理读取冻结/下线租户仍可用
4. 执行测试并汇报结果
5. 最后给出修改文件清单和剩余风险

优先文件：
- tiny-oauth-server/src/main/java/com/tiny/platform/core/oauth/tenant/TenantContextFilter.java
- tiny-oauth-server/src/main/java/com/tiny/platform/infrastructure/tenant/guard/TenantLifecycleGuard.java
- tiny-oauth-server/src/main/java/com/tiny/platform/infrastructure/tenant/service/TenantServiceImpl.java
- tiny-oauth-server/src/test/java/com/tiny/platform/core/oauth/tenant/TenantContextFilterTest.java
- tiny-oauth-server/src/test/java/com/tiny/platform/infrastructure/tenant/service/TenantServiceImplTest.java

至少执行：
- mvn -pl tiny-oauth-server -Dtest=TenantContextFilterTest,TenantServiceImplTest,TenantControllerTest test
- mvn -pl tiny-oauth-server -DskipTests compile
```

### 9.2 只修第 3 点：错误模型与 API 返回一致性

```text
按 AGENTS.md、docs/TINY_PLATFORM_TENANT_GOVERNANCE_CURSOR_FIX_PROMPT.md 和 .agent/src/rules/94-tiny-platform-tenant-governance.rules.md 直接开始实施，不要只输出计划。

这轮只修复第 3 点：租户治理错误模型与 API 返回一致性。

固定约束：
- 不扩散到 2 / 4 / 5 / 6
- TenantServiceImpl 及租户治理链路禁止继续抛 RuntimeException("...")
- 必须统一到 BusinessException / NotFoundException / ErrorCode
- 测试必须断言状态码和错误码，不要只断言 message

错误码裁决：
- 缺少参数 -> MISSING_PARAMETER
- 参数非法 / 格式错误 -> INVALID_PARAMETER
- 编码 / 域名重复 -> RESOURCE_ALREADY_EXISTS
- 生命周期非法 / 状态不允许 -> RESOURCE_STATE_INVALID
- 资源不存在 -> NOT_FOUND
- 模板或初始化不变量破坏 -> BUSINESS_ERROR
- 作用域不符 -> FORBIDDEN

要求：
1. 直接改代码，不要只给方案
2. 只处理租户治理相关异常，不顺手改全仓库
3. 补后端测试，覆盖：
   - 非法生命周期流转
   - 禁止直接修改 lifecycleStatus
   - 租户不存在
   - 重复编码 / 域名
4. 执行测试并汇报状态码 + errorCode
5. 最后列出仍未统一的异常点

优先文件：
- tiny-oauth-server/src/main/java/com/tiny/platform/infrastructure/tenant/service/TenantServiceImpl.java
- tiny-oauth-server/src/main/java/com/tiny/platform/application/controller/tenant/TenantController.java
- tiny-oauth-server/src/test/java/com/tiny/platform/infrastructure/tenant/service/TenantServiceImplTest.java
- tiny-oauth-server/src/test/java/com/tiny/platform/application/controller/tenant/TenantControllerTest.java

至少执行：
- mvn -pl tiny-oauth-server -Dtest=TenantServiceImplTest,TenantControllerTest,TenantControllerRbacIntegrationTest test
- mvn -pl tiny-oauth-server -DskipTests compile
```

### 9.3 只修第 4 点：审计 detail 结构化与可查询

```text
按 AGENTS.md、docs/TINY_PLATFORM_TENANT_GOVERNANCE_CURSOR_FIX_PROMPT.md 和 .agent/src/rules/94-tiny-platform-tenant-governance.rules.md 直接开始实施，不要只输出计划。

这轮只修复第 4 点：租户治理审计 detail 结构化与字段级可查询性。

固定约束：
- 不扩散到 2 / 3 / 5 / 6
- 禁止继续手拼 JSON 字符串
- 使用 ObjectMapper 或集中 helper 做结构化序列化
- 本轮只做最少够用查询字段，不做全量 JSON DSL

最少 detail 结构：
- action
- tenant{id,code,name}
- operator{userId,username,scopeType}
- reason
- before
- after
- diff

最少新增查询字段：
- actorUserId
- result
- resourcePermission
- detailReason

要求：
1. 直接改代码，不要只讨论设计
2. 保持列表 / summary / CSV 导出字段口径一致
3. 补后端和前端测试，验证新增筛选字段能真正生效
4. 执行测试并汇报
5. 最后明确哪些 detail 字段仍未支持筛选

优先文件：
- tiny-oauth-server/src/main/java/com/tiny/platform/infrastructure/auth/audit/service/AuthorizationAuditService.java
- tiny-oauth-server/src/main/java/com/tiny/platform/infrastructure/auth/audit/service/AuthorizationAuditQuery.java
- tiny-oauth-server/src/main/java/com/tiny/platform/application/controller/audit/AuthorizationAuditController.java
- tiny-oauth-server/src/main/java/com/tiny/platform/infrastructure/tenant/service/TenantServiceImpl.java
- tiny-oauth-server/src/main/webapp/src/api/audit.ts
- tiny-oauth-server/src/main/webapp/src/views/audit/AuthorizationAudit.vue
- tiny-oauth-server/src/test/java/com/tiny/platform/application/controller/audit/AuthorizationAuditControllerTest.java
- tiny-oauth-server/src/test/java/com/tiny/platform/infrastructure/auth/audit/service/AuthorizationAuditServiceTest.java
- tiny-oauth-server/src/main/webapp/src/views/audit/AuthorizationAudit.test.ts

至少执行：
- mvn -pl tiny-oauth-server -Dtest=AuthorizationAuditControllerTest,AuthorizationAuditServiceTest,TenantServiceImplTest test
- npm --prefix tiny-oauth-server/src/main/webapp run test:unit -- src/api/audit.test.ts src/views/audit/AuthorizationAudit.test.ts
- mvn -pl tiny-oauth-server -DskipTests compile
- npm --prefix tiny-oauth-server/src/main/webapp run build-only
```

### 9.4 只修第 5 点：配额 maxUsers / maxStorageGb 执行

```text
按 AGENTS.md、docs/TINY_PLATFORM_TENANT_GOVERNANCE_CURSOR_FIX_PROMPT.md 和 .agent/src/rules/94-tiny-platform-tenant-governance.rules.md 直接开始实施，不要只输出计划。

这轮只修复第 5 点：配额 maxUsers / maxStorageGb 运行时执行。

固定约束：
- 不扩散到 2 / 3 / 4 / 6
- 不虚构用户导入接口
- 不虚构统一文件中心
- maxUsers 只先落到真实用户写路径
- maxStorageGb 只先落到真实存储写链路：头像上传 + 导出文件落盘

要求：
1. 直接改代码，不要只给设计
2. 抽一个可复用的 TenantQuotaService 或等价服务
3. maxUsers 至少覆盖：
   - 租户创建初始管理员
   - UserServiceImpl.create(...)
   - UserServiceImpl.createFromDto(...)
4. maxStorageGb 至少覆盖：
   - AvatarServiceImpl.uploadAvatar(...)
   - ExportTaskService 导出文件成功落盘后的大小记录与校验
5. maxUsers = null 视为无限制；新建租户时 maxUsers < 1 判为非法参数
6. 补后端测试并执行验证
7. 最后列出当前仍未受配额保护的写链路

优先文件：
- tiny-oauth-server/src/main/java/com/tiny/platform/infrastructure/tenant/service/TenantServiceImpl.java
- tiny-oauth-server/src/main/java/com/tiny/platform/infrastructure/auth/user/service/UserServiceImpl.java
- tiny-oauth-server/src/main/java/com/tiny/platform/core/oauth/service/impl/AvatarServiceImpl.java
- tiny-oauth-server/src/main/java/com/tiny/platform/infrastructure/export/service/ExportTaskService.java
- tiny-oauth-server/src/main/java/com/tiny/platform/infrastructure/export/persistence/ExportTaskEntity.java
- tiny-oauth-server/src/test/java/com/tiny/platform/infrastructure/tenant/service/TenantServiceImplTest.java
- tiny-oauth-server/src/test/java/com/tiny/platform/infrastructure/auth/user/service/UserServiceImplTest.java
- tiny-oauth-server/src/test/java/com/tiny/platform/core/oauth/service/impl/AvatarServiceImplTest.java
- tiny-oauth-server/src/test/java/com/tiny/platform/infrastructure/export/service/ExportTaskServiceTest.java

至少执行：
- mvn -pl tiny-oauth-server -Dtest=TenantServiceImplTest,UserServiceImplTest,AvatarServiceImplTest,ExportTaskServiceTest test
- mvn -pl tiny-oauth-server -DskipTests compile
```

### 9.5 只修第 6 点：前端治理入口

```text
按 AGENTS.md、docs/TINY_PLATFORM_TENANT_GOVERNANCE_CURSOR_FIX_PROMPT.md 和 .agent/src/rules/94-tiny-platform-tenant-governance.rules.md 直接开始实施，不要只输出计划。

这轮只修复第 6 点：租户前端治理入口。

固定约束：
- 不扩散到 2 / 3 / 4 / 5
- 复用现有 GET /sys/tenants/{id}
- 本轮优先实现只读详情抽屉 / 弹窗，不强制独立路由
- includeDeleted 要贯穿列表、搜索、重置、分页

要求：
1. 直接修改前端代码，不只给方案
2. tenant.ts 暴露 includeDeleted 参数
3. Tenant.vue 搜索区增加 includeDeleted 开关
4. 列表页增加“查看详情”入口
5. 详情展示至少包含：
   - lifecycleStatus
   - packageName
   - expiredAt
   - maxUsers / maxStorageGb
   - contactName / contactPhone / contactEmail
   - remark
   - createdAt / updatedAt
6. 补前端测试并执行 build
7. 最后列出仍缺失但非本轮必须的治理入口

优先文件：
- tiny-oauth-server/src/main/webapp/src/api/tenant.ts
- tiny-oauth-server/src/main/webapp/src/views/tenant/Tenant.vue
- 如有需要新增：tiny-oauth-server/src/main/webapp/src/views/tenant/TenantDetailDrawer.vue
- tiny-oauth-server/src/main/webapp/src/api/tenant.test.ts
- tiny-oauth-server/src/main/webapp/src/views/tenant/Tenant.test.ts

至少执行：
- npm --prefix tiny-oauth-server/src/main/webapp run test:unit -- src/api/tenant.test.ts src/views/tenant/Tenant.test.ts
- npm --prefix tiny-oauth-server/src/main/webapp run build-only
```
