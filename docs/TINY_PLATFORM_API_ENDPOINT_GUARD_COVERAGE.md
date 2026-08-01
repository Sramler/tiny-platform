# Tiny Platform `api_endpoint` 统一守卫覆盖清单

> 目的：把“统一守卫到底覆盖了哪些接口、还缺哪些接口”落成可交付的**覆盖清单 + 证据**，避免用“后端能力已接入”冒充“运行时已闭合”。

---

## 1. 统一守卫的判定口径（必须先明确）

### 1.1 统一守卫在哪里生效

- `ApiEndpointRequirementFilter` 已挂载在 Spring Security filter chain 上，对已认证请求执行统一 requirement 判定。
- 业务接口采用 **fail-closed**：同 method 下找不到严格模板匹配的 enabled `api_endpoint` 时，以 `URI_TEMPLATE_NOT_MATCHED` 拒绝；不得把“未登记”当作继续沿用旧 Guard 的放行条件。
- 仅登录/OAuth 协议、静态资源及少量已认证自服务/lookup 接口按精确 method + path 豁免。豁免不得使用目录级通配替代载体治理。

### 1.2 “已被统一守卫接管”的必要条件

对任意请求 \(`method`, `requestURI`\)，满足以下条件才算“已接管”：

- **登记存在**：在 `api_endpoint` 中能找到同一 `method` 的 entry，且 `enabled=true`
- **URI 严格模板匹配**：`api_endpoint.uri` 允许模板段 \(`{id}`\)，匹配要求**逐段严格匹配**，禁止 `startsWith/contains` 模糊兜底
- **命中后 fail-closed**：
  - `required_permission_id` 缺失 → DENY
  - `api_endpoint_permission_requirement` 行缺失 → DENY
  - requirement 关联到的 `permission.enabled=false` → DENY
  - requirement 不满足 → DENY

> 备注：因为统一守卫的“接管”是**数据驱动**的，所以“Controller 里存在某个接口”并不等价于“它已被统一守卫接管”。

---

## 2. 覆盖分类（本文件的输出格式）

每条接口或接口组，必须落入以下 4 类之一：

1. **已登记且已被统一守卫接管**
2. **已登记但仍缺真实覆盖证明**（只有服务层/代码能力，或测试仍是 mock 决策）
3. **未登记，统一守卫拒绝（必须补载体或证明属于精确基础设施豁免）**
4. **有意豁免**（登录/公开/健康检查/静态资源等）

缺口原因必须标注为：

- 未登记
- method/path 不一致
- requirement 缺失
- 测试未覆盖
- 有意豁免

---

## 3. 证据来源（本轮允许的最小证明）

### 3.1 证据等级（从低到高）

1. **服务层证明**
   - 定义：仅证明 `ResourceServiceImpl` / evaluator 的匹配与 fail-closed 语义成立
   - 局限：不能证明 Spring Security filter-chain 中的统一守卫已对真实 HTTP 请求生效

2. **同路径 filter-chain 证明**
   - 定义：MockMvc 请求经过 `ApiEndpointRequirementFilter`，并进入真实 `ResourceServiceImpl.evaluateApiEndpointRequirement(...)` 与 evaluator 分支；但 Controller 可能是测试专用 `TestController`
   - 价值：证明“统一守卫链路”可工作（命中/拒绝/不误伤）
   - 局限：仍不足以证明“真实模块 Controller + 真实请求路径”在当前生产式配置下已被接管

3. **真实模块 controller 证明（最高）**
   - 定义：MockMvc 请求直接命中真实模块 `*Controller`（tenant/user/role 等），并被 `ApiEndpointRequirementFilter` 统一守卫 ALLOW/DENY
   - 价值：证明“真实模块入口已被统一守卫接管”（在不强制加载完整 JWT/MFA/OAuth2 的前提下）

本轮新增的真实证明：

- `tiny-oauth-server/src/test/java/com/tiny/platform/application/controller/resource/ResourceControllerApiEndpointTemplateUriIntegrationTest.java`
  - 覆盖：`/sys/resources`（静态 URI）、`/sys/resources/{id}`（模板 URI）、未登记（段数不一致）不误伤
- `tiny-oauth-server/src/test/java/com/tiny/platform/core/oauth/security/ApiEndpointControllerMappingDriftIT.java`
  - 在真实 MySQL/Spring MVC 上比较实际 Controller method/template 与同 scope enabled `api_endpoint`、主 permission 和 requirement；同时拒绝运行时等价的重复模板。
  - 执行入口：`bash tiny-oauth-server/scripts/verify-api-endpoint-controller-drift.sh`。

---

## 4. 模块覆盖清单（按“应纳入统一功能权限守卫”的控制面/平台能力分组）

> 说明：
> - 这里的“已登记”以 Liquibase 迁移后的真实数据库状态和漂移门禁为依据，不再以 `data.sql` 目标态推断。
> - “缺真实覆盖证明”表示：当前没有在测试中证明“真实 filter-chain 命中该 entry 时会 ALLOW/DENY”，而不仅是 mock 决策。

### 4.1 tenant（`TenantController` / `system:tenant:*`）

- **接口组**：`/sys/tenants/**`
- **分类**：已登记，已有统一守卫证据
- **证据等级**：
  - **真实模块 controller 证明**：`TenantControllerApiEndpointGuardRealControllerIntegrationTest`
    - allow：`tenant_realTenantController_sysTenants_allow_shouldReturn200_whenRequirementSatisfied_staticUri`
    - deny：`tenant_realTenantController_sysTenants_deny_shouldReturn403_whenPermissionDisabled_staticUri`
  - **同路径 filter-chain 证明（降级保留）**：`TenantApiEndpointGuardFilterChainIntegrationTest`
    - allow：`tenant_sysTenants_allow_shouldReturn200_whenRequirementSatisfied_staticUri`
    - deny：`tenant_sysTenants_deny_shouldReturn403_whenPermissionDisabled_staticUri`

### 4.2 user（`UserController` / `system:user:*`）

- **接口组**：`/sys/users/**`
- **分类**：已登记，已有统一守卫证据
- **证据等级**：
  - **真实模块 controller 证明**：`UserControllerApiEndpointGuardRealControllerIntegrationTest`
    - allow：`user_realUserController_sysUsers_allow_shouldReturn200_whenRequirementSatisfied_staticUri`
    - deny：`user_realUserController_sysUsers_deny_shouldReturn403_whenPermissionDisabled_staticUri`
  - **同路径 filter-chain 证明（降级保留）**：`UserApiEndpointGuardFilterChainIntegrationTest`
    - allow：`user_sysUsers_allow_shouldReturn200_whenRequirementSatisfied_staticUri`
    - deny：`user_sysUsers_deny_shouldReturn403_whenPermissionDisabled_staticUri`
- **备注**：包含模板 URI：`/sys/users/{id}`（PUT/DELETE）

### 4.3 role（`RoleController` / `system:role:*`）

- **接口组**：`/sys/roles/**`
- **分类**：已登记，已有统一守卫证据
- **证据等级**：
  - **真实模块 controller 证明**：`RoleControllerApiEndpointGuardRealControllerIntegrationTest`
    - allow：`role_realRoleController_sysRoles_allow_shouldReturn200_whenRequirementSatisfied_staticUri`
    - deny：`role_realRoleController_sysRoles_deny_shouldReturn403_whenPermissionDisabled_staticUri`
  - **同路径 filter-chain 证明（降级保留）**：`RoleApiEndpointGuardFilterChainIntegrationTest`
    - allow：`role_sysRoles_allow_shouldReturn200_whenRequirementSatisfied_staticUri`
    - deny：`role_sysRoles_deny_shouldReturn403_whenPermissionDisabled_staticUri`
- **备注**：包含模板 URI：`/sys/roles/{id}`（PUT/DELETE）

### 4.4 menu（`MenuController` / `system:menu:*`）

- **接口组**：`/sys/menus/**`
- **分类**：已登记，已有统一守卫证据
- **证据等级**：
  - **真实模块 controller 证明**：`MenuControllerApiEndpointGuardRealControllerIntegrationTest`
    - allow：`menu_realMenuController_sysMenus_allow_shouldReturn200_whenRequirementSatisfied_staticUri`
    - deny：`menu_realMenuController_sysMenus_deny_shouldReturn403_whenPermissionDisabled_staticUri`
- **备注**：包含模板 URI：`/sys/menus/{id}`、以及 `/sys/menus/{id}/sort`

### 4.5 resource（`ResourceController` / `system:resource:*`）

- **接口组**：`/sys/resources/**`
- **分类**：已登记且已被统一守卫接管（部分接口有真实证明）
- **证据**：`ResourceControllerApiEndpointTemplateUriIntegrationTest`
- **备注**：
  - 已证明：`GET /sys/resources`（静态 URI）、`GET /sys/resources/{id}`（模板 URI）
  - 仍缺：其它 write/read 端点的真实证明（如 POST/PUT/DELETE、batch、tree 等）

### 4.6 audit（`AuthorizationAuditController` / `AuthenticationAuditController`）

- **授权审计**：`/sys/audit/authorization/**`
  - **分类**：已登记，已有统一守卫证据
  - **证据等级**：
    - **真实模块 controller 证明**：`AuthorizationAuditControllerApiEndpointGuardRealControllerIntegrationTest`
      - allow：`audit_realAuthorizationAuditController_sysAuditAuthorization_allow_shouldReturn200_whenRequirementSatisfied_staticUri`
      - deny：`audit_realAuthorizationAuditController_sysAuditAuthorization_deny_shouldReturn403_whenPermissionDisabled_staticUri`
    - **同路径 filter-chain 证明（降级保留）**：`AuthorizationAuditApiEndpointGuardFilterChainIntegrationTest`
      - allow：`audit_sysAuditAuthorization_allow_shouldReturn200_whenRequirementSatisfied_staticUri`
      - deny：`audit_sysAuditAuthorization_deny_shouldReturn403_whenPermissionDisabled_staticUri`
- **认证审计**：`/sys/audit/authentication/**`
  - **分类**：已登记，已有统一守卫证据
  - **证据等级**：
    - **真实模块 controller 证明**：`AuthenticationAuditControllerApiEndpointGuardRealControllerIntegrationTest`
      - allow：`audit_realAuthenticationAuditController_sysAuditAuthentication_allow_shouldReturn200_whenRequirementSatisfied_staticUri`
      - deny：`audit_realAuthenticationAuditController_sysAuditAuthentication_deny_shouldReturn403_whenPermissionDisabled_staticUri`

### 4.7 scheduling（`SchedulingController`）

- **接口组**：`/scheduling/**`（同时承载控制面与运行态操作）
- **分类**：已登记，已有统一守卫证据
- **证据等级**：
  - **真实模块 controller 证明**：`SchedulingControllerApiEndpointGuardRealControllerIntegrationTest`
    - allow：`scheduling_realSchedulingController_taskTypeList_allow_shouldReturn200_whenRequirementSatisfied_staticUri`
    - deny：`scheduling_realSchedulingController_taskTypeList_deny_shouldReturn403_whenPermissionDisabled_staticUri`
  - **同路径 filter-chain 证明（降级保留）**：`SchedulingApiEndpointGuardFilterChainIntegrationTest`
    - allow：`scheduling_taskTypeList_allow_shouldReturn200_whenRequirementSatisfied_staticUri`
    - deny：`scheduling_taskTypeList_deny_shouldReturn403_whenPermissionDisabled_staticUri`
- **备注**：该模块端点数量多，建议后续以“高风险写操作 + run/node 操作语义”优先补真实证明，而不是全量罗列一次性补齐。

### 4.8 dict（`DictController` / `PlatformDictController`）

- **接口组**：`/dict/**`、`/sys/dict/**`（若存在）
- **分类**：控制面已登记；运行态 lookup 精确豁免。
- **控制面**：平台/租户字典类型、字典项的管理读写映射由 changeset 211 建立载体和 requirement，继续叠加既有方法守卫。
- **精确豁免**：只包括 GET `/dict/types/code/*`、`/dict/types/current`、`/dict/items/code/*`、`/dict/items/map/*`、`/dict/items/label/*/*`；这些是已认证页面展示 lookup，不等价于 `/dict/**` 通配放行。
- **防漂移**：新增 dict mapping 若既不在迁移载体中，也不匹配上述精确 method/path，漂移门禁直接失败。

---

## 5. 本轮结论（当前状态总览）

- **证据等级总览（与第 4 节模块清单一一对应）**：
  - **真实模块 controller 证明**：`tenant`、`user`、`role`、`menu`、`authorization audit`、`authentication audit`、`scheduling`
  - **真实 filter-chain 证明（静态 + 模板 URI）**：`resource`（`/sys/resources` 与 `/sys/resources/{id}`）
  - **真实 DB Controller 漂移证明**：所有未精确豁免、非 disabled-fallback 的受保护 MVC mapping 均有同 scope 载体、主 permission 与 requirement。
  - **精确基础设施豁免**：运行态 dict lookup、当前用户头像/登录历史、process health；不包含目录通配。
- **当前仍需补齐的最高优先级缺口**：
  - 漂移门禁证明“载体存在且结构完整”，不替代每个高风险写接口的真实 ALLOW/DENY、数据权限与租户隔离行为测试。
  - `ProcessDisabledFallbackController` 仅在 Camunda 关闭时提供 503 envelope，明确排除于权限载体目录；不得为它创建 `/process/**` 通配载体。
