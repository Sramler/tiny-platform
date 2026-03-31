# Tiny Platform `api_endpoint` 统一守卫覆盖清单

> 目的：把“统一守卫到底覆盖了哪些接口、还缺哪些接口”落成可交付的**覆盖清单 + 证据**，避免用“后端能力已接入”冒充“运行时已闭合”。

---

## 1. 统一守卫的判定口径（必须先明确）

### 1.1 统一守卫在哪里生效

- `ApiEndpointRequirementFilter` 已挂载在 Spring Security filter chain 上，对已认证请求执行统一 requirement 判定。
- 统一守卫只对**已登记**的 `api_endpoint` 生效；未登记接口保持现状（不一刀切拦截）。

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
3. **未登记，当前仍只靠旧 Guard**
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

---

## 4. 模块覆盖清单（按“应纳入统一功能权限守卫”的控制面/平台能力分组）

> 说明：
> - 这里的“已登记”优先以**初始化 `data.sql`（resource API 记录）+ carrier split backfill** 的目标态为依据；若某环境未跑 seed/backfill，则登记状态可能不一致，需要在 rollout 中校验。
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
- **分类**：有意豁免（当前不纳入统一守卫）
- **原因**：
  - `data.sql` 当前未包含 `/dict/**` 的 `api_endpoint` 登记；现阶段不在本轮/本迭代内新增 dict 的统一守卫登记口径，避免扩大“登记覆盖 + rollout 校验 + 运营回填”的范围
  - dict 同时承担运行态字典查询能力与控制面写能力；目前继续依赖既有 `@PreAuthorize`/AccessGuard 做主保护，避免在未完成登记治理前引入“部分接口被统一守卫接管、部分仍旧 Guard”的误解
- **当前口径**：dict 仍依赖旧 Guard；统一守卫对 dict 不做强制拦截
- **重新纳入触发条件**：
  - 明确 dict 的“纳入范围”（至少控制面写接口，是否包含运行态 read 接口）
  - 补齐最小 `api_endpoint` 登记与 `api_endpoint_permission_requirement` compatibility group，并通过 rollout 校验
  - 至少新增 1 组 dict 真实模块 controller ALLOW/DENY 证明后，方可在本清单中提升证据等级

---

## 5. 本轮结论（当前状态总览）

- **证据等级总览（与第 4 节模块清单一一对应）**：
  - **真实模块 controller 证明**：`tenant`、`user`、`role`、`menu`、`authorization audit`、`authentication audit`、`scheduling`
  - **真实 filter-chain 证明（静态 + 模板 URI）**：`resource`（`/sys/resources` 与 `/sys/resources/{id}`）
  - **有意豁免**：`dict`（当前不纳入统一守卫，继续依赖旧 Guard）
- **当前仍需补齐的最高优先级缺口**：
  - 在 `resource` 模块补更多真实端点覆盖（当前主要证明静态/模板 read 端点）

