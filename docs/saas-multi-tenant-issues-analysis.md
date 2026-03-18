# SaaS 多租户改造问题对照分析报告

## 执行时间

2025-01-XX

## 分析范围

- `tiny-oauth-server` 模块
- User / Role / Resource 实体结构
- TenantFilter / TenantContext 实现
- SecurityFilterChain 配置
- JWT Token 自定义器
- OAuth2 Authorization / Client 存储模型

> 说明：
> - 本文保留了 2025 年早期问题分析语境，包含部分已完成整改的历史项。
> - 当前实现已经前移到 `TenantContextFilter + activeTenantId + X-Active-Tenant-Id`。
> - 文中凡是把 `tenantId` 用作“当前活动租户上下文”、或把 `TenantFilter / X-Tenant-Id` 作为目标实现的段落，应优先按现行契约理解，而不是按历史草案继续实现。

---

## 1. 必须改（不改就不算 SaaS 多租户）

### ✅ 1.1 引入"租户实体"与"租户成员"层（核心问题）

**问题状态：❌ 缺失**

**现状：**

- ❌ 没有 `Tenant` 实体
- ❌ 没有 `tenant` 表
- ❌ 没有 `TenantUser` 实体
- ❌ 没有 `tenant_user` 表

**影响：**

- 无法支持"一个用户属于多个租户"
- 无法建立租户与用户的关联关系
- 无法实现租户级别的权限隔离

**必须新增：**

1. `tenant` 表：租户（隔离边界）
2. `tenant_user` 表：租户成员（连接 platform_user 与 tenant 的关键中间层）

---

### ✅ 1.2 "user_role 必须拆解"为 tenant_user_role / platform_user_role

**问题状态：❌ 未拆分**

**现状：**

```java
// User.java:38-42
@ManyToMany(fetch = FetchType.LAZY)
@JoinTable(name = "user_role",
    joinColumns = @JoinColumn(name = "user_id"),
    inverseJoinColumns = @JoinColumn(name = "role_id"))
@JsonIgnore
private Set<Role> roles = new HashSet<>();
```

**问题：**

- ✅ 确认存在 `user_role` 表（schema.sql:37-47）
- ❌ User ↔ Role 是直接 ManyToMany，全局绑定
- ❌ 一个人一旦有 `ROLE_ADMIN`，就变成"全局管理员"（很危险）
- ❌ 无法表达"在租户 A 是管理员，在租户 B 是普通用户"

**必须改造：**

- ❌ 不存在 `tenant_user_role` 表
- ❌ 不存在 `platform_user_role` 表
- 需要将 `user_role` 拆分为：
  - `platform_user_role`：平台层角色（平台运维、超级管理员等）
  - `tenant_user_role`：租户内角色（租户管理员、普通成员等）

**影响代码：**

- `UserDetailsServiceImpl`：必须改为按 `active_tid` 加载 `tenant_user_role`
- `SecurityUser`：权限计算必须区分平台角色和租户角色

---

### ✅ 1.3 Role 必须"带作用域"，否则 code 全局唯一会把你锁死

**问题状态：❌ 未改造**

**现状：**

```java
// Role.java:19
@Column(nullable = false, unique = true, length = 50)
private String code; // 权限标识：ROLE_ADMIN
```

**问题：**

- ✅ 确认 `role.code` 全局唯一（schema.sql:32）
- ❌ 每个租户都想要"管理员/审批员/报送员"等角色 code（会冲突）
- ❌ 平台也需要"PLATFORM_ADMIN"这种角色（与租户角色冲突）

**必须改造（二选一）：**

**方案 A：推荐 - Role 拆分两张表（最清晰）**

- `platform_role` 表
- `tenant_role` 表

**方案 B：折中 - Role 增加 scope + tenant_id（改动小）**

- `role.scope`：`PLATFORM` / `TENANT`
- `role.tenant_id`：当 `scope=TENANT` 时必填；`scope=PLATFORM` 时为 null
- 唯一约束：`(scope, tenant_id, code)` 唯一

**当前状态：**

- ❌ 未实现任何方案

---

### ✅ 1.4 租户解析必须"强制"，禁止 default 回退

**问题状态：✅ 已完成整改（保留为历史问题记录）**

**当前契约：**

```java
// 当前实现：TenantContextFilter / HeaderTenantResolver
String activeTenantId = request.getHeader("X-Active-Tenant-Id");
if (activeTenantId == null || activeTenantId.isBlank()) {
    throw tenantMissingOrUnauthorized();
}
TenantContext.setTenantId(parseActiveTenantId(activeTenantId));
```

**问题：**

- ❌ 缺失 tenant → 自动 fallback 为 `"default"`（SaaS 大忌）
- ❌ 没有白名单机制
- ❌ 没有明确错误码（应该返回 400/401）

**必须改造：**

- ✅ 允许无 tenant 的端点（白名单）：
  - `/login`
  - `/oauth2/token`（如果支持密码/表单登录）
  - `/.well-known/**`、`/oauth2/jwks` 等
- ❌ 除白名单外：缺失 tenant → 直接 400/401（明确错误码）

**当前 Header 契约：**

- 当前活动租户请求头：`X-Active-Tenant-Id`
- `X-Tenant-ID` / `X-Tenant-Id` 属于历史遗留写法，不再作为当前上下文契约

---

### ✅ 1.5 接入 SecurityFilterChain（Order 1 / Order 2 都要接）

**问题状态：❌ 未接入**

**现状：**

```java
// AuthorizationServerConfig.java:54-55
@Bean
@Order(1)
public SecurityFilterChain authorizationServerSecurityFilterChain(...)
```

```java
// DefaultSecurityConfig.java:28-29
@Configuration
@Order(2)
public class DefaultSecurityConfig {
```

**问题：**

- ❌ `TenantFilter` 没有接入到 `@Order(1)` 的 `AuthorizationServerConfig`
- ❌ `TenantFilter` 没有接入到 `@Order(2)` 的 `DefaultSecurityConfig`
- ❌ 只有 `MfaAuthorizationEndpointFilter` 被接入（AuthorizationServerConfig:66）

**影响：**

- token 里写不出 `tid`
- `UserDetails` 加载角色时不知道租户
- 审计日志缺 tenant

**必须改造：**

- 在两条 `SecurityFilterChain` 中都添加 `TenantFilter`
- 确保 `TenantFilter` 在认证之前执行

---

### ✅ 1.6 OAuth2 Authorization / Consent 数据必须按租户隔离（新增必改）

**问题状态：❌ 缺失**

**现状：**

```java
// OAuth2DataConfig.java:110-131
@Bean(name = "oauth2AuthorizationService")
public OAuth2AuthorizationService oauth2AuthorizationService(...) {
    JdbcOAuth2AuthorizationService service = new JdbcOAuth2AuthorizationService(jdbcTemplate, registeredClientRepository);
    // ... 没有 tenant_id 隔离
}
```

```java
// OAuth2DataConfig.java:155-161
@Bean
public OAuth2AuthorizationConsentService customOAuth2AuthorizationConsentService(...) {
    return new JdbcOAuth2AuthorizationConsentService(jdbcTemplate, registeredClientRepository);
    // ... 没有 tenant_id 隔离
}
```

**问题：**

- ❌ `oauth2_authorization` 表没有 `tenant_id` 字段
- ❌ `oauth2_authorization_consent` 表没有 `tenant_id` 字段
- ❌ `JdbcOAuth2AuthorizationService` 创建/查询 authorization 时没有绑定 `TenantContext`
- ❌ `JdbcOAuth2AuthorizationConsentService` 创建/查询 consent 时没有绑定 `TenantContext`

**风险：**

- 🔴 **跨租户授权数据混用**：不同租户的 `authorization_code` 可能冲突
- 🔴 **潜在跨租户授权读取**：租户 A 可能读取到租户 B 的授权信息（高危）
- 🔴 **consent 数据串租**：用户对客户端的授权同意可能被其他租户看到

**必须改造：**

1. **数据库表结构改造：**

   - `oauth2_authorization` 表增加 `tenant_id` 字段（可为 null，用于平台级授权）
   - `oauth2_authorization_consent` 表增加 `tenant_id` 字段（可为 null，用于平台级授权）
   - 唯一约束调整：`(tenant_id, id)` 或 `(tenant_id, registered_client_id, principal_name)`

2. **Service 层改造：**

   - 自定义 `JdbcOAuth2AuthorizationService` 包装类：
     - `save()` 时自动从 `TenantContext` 获取 `tenant_id` 并写入
     - `findById()` / `findByToken()` 时自动按 `tenant_id` 过滤
   - 自定义 `JdbcOAuth2AuthorizationConsentService` 包装类：
     - `save()` 时自动从 `TenantContext` 获取 `tenant_id` 并写入
     - `findById()` 时自动按 `tenant_id` 过滤

3. **查询逻辑改造：**
   - 所有查询 authorization / consent 的地方必须加上 `tenant_id` 条件
   - 平台级授权（`tenant_id = null`）仅平台管理员可见

**参考实现：**

```java
// 自定义包装类示例
public class TenantAwareOAuth2AuthorizationService implements OAuth2AuthorizationService {
    private final JdbcOAuth2AuthorizationService delegate;

    @Override
    public void save(OAuth2Authorization authorization) {
        String activeTenantId = TenantContext.getCurrentTenant();
        // 将 activeTenantId 写入 authorization 的 attributes 或扩展字段
        // 然后调用 delegate.save()
    }

    @Override
    public void remove(OAuth2Authorization authorization) {
        // 验证 tenant_id 匹配后才删除
        delegate.remove(authorization);
    }

    @Override
    public OAuth2Authorization findById(String id) {
        String activeTenantId = TenantContext.getCurrentTenant();
        // 查询时加上 tenant_id 条件
        return delegate.findById(id);
    }

    @Override
    public OAuth2Authorization findByToken(String token, OAuth2TokenType tokenType) {
        String activeTenantId = TenantContext.getCurrentTenant();
        // 查询时加上 tenant_id 条件
        return delegate.findByToken(token, tokenType);
    }
}
```

---

### ✅ 1.7 OAuth2 Client（RegisteredClient）必须有租户边界（新增必改）

**问题状态：❌ 缺失**

**现状：**

```java
// OAuth2DataConfig.java:67-69
@Bean
public RegisteredClientRepository registeredClientRepository(JdbcTemplate jdbcTemplate) {
    return new JdbcRegisteredClientRepository(jdbcTemplate);
    // ... 没有 tenant_id 隔离
}
```

```java
// RegisteredClientConfig.java:50
RegisteredClient existing = repository.findByClientId(config.getClientId());
// ... client_id 全局唯一，没有 tenant 边界
```

**问题：**

- ❌ `oauth2_registered_client` 表没有 `tenant_id` 字段
- ❌ `client_id` 全局唯一，多租户场景下易发生冲突
- ❌ `JdbcRegisteredClientRepository.findByClientId()` 全局查询，没有租户过滤
- ❌ 租户 A 的客户端可能被租户 B 看到（越权风险）

**风险：**

- 🔴 **client_id 冲突**：不同租户无法使用相同的 `client_id`（如 "web-client"）
- 🔴 **跨租户客户端可见**：租户 A 可能看到租户 B 的客户端配置（高危）
- 🔴 **私有化部署困难**：每个租户需要独立的客户端配置，但当前模型不支持

**必须改造：**

1. **数据库表结构改造：**

   - `oauth2_registered_client` 表增加 `tenant_id` 字段（可为 null，用于平台级客户端）
   - `oauth2_registered_client` 表增加 `scope` 字段（`PLATFORM` / `TENANT`，可选）
   - 唯一约束调整：`(tenant_id, client_id)` 唯一（允许不同租户使用相同 `client_id`）

2. **Repository 层改造：**

   - 自定义 `JdbcRegisteredClientRepository` 包装类：
     - `save()` 时自动从 `TenantContext` 获取 `tenant_id` 并写入
     - `findById()` / `findByClientId()` 时自动按 `tenant_id` 过滤
     - 平台级客户端（`tenant_id = null` 或 `scope = PLATFORM`）全局可见
     - 租户级客户端（`tenant_id != null` 且 `scope = TENANT`）仅当前租户可见

3. **查询逻辑改造：**
   - 所有查询 RegisteredClient 的地方必须加上 `tenant_id` 条件
   - 平台级客户端：`tenant_id IS NULL OR scope = 'PLATFORM'`
   - 租户级客户端：`tenant_id = ? AND scope = 'TENANT'`

**参考实现：**

```java
// 自定义包装类示例
public class TenantAwareRegisteredClientRepository implements RegisteredClientRepository {
    private final JdbcRegisteredClientRepository delegate;

    @Override
    public void save(RegisteredClient registeredClient) {
        String activeTenantId = TenantContext.getCurrentTenant();
        // 将 activeTenantId 写入 registered_client 表的 tenant_id 列
        // 然后调用 delegate.save()
    }

    @Override
    public RegisteredClient findById(String id) {
        String activeTenantId = TenantContext.getCurrentTenant();
        // 查询时加上 tenant_id 条件
        return delegate.findById(id);
    }

    @Override
    public RegisteredClient findByClientId(String clientId) {
        String activeTenantId = TenantContext.getCurrentTenant();
        // 查询时加上 tenant_id 条件
        // 平台级客户端：tenant_id IS NULL OR scope = 'PLATFORM'
        // 租户级客户端：tenant_id = ? AND scope = 'TENANT'
        return delegate.findByClientId(clientId);
    }
}
```

**迁移建议：**

- 第一阶段：现有客户端迁移为平台级客户端（`tenant_id = null`，`scope = PLATFORM`）
- 第二阶段：新客户端按租户创建（`tenant_id = 当前租户`，`scope = TENANT`）

---

## 2. 建议改（做了会稳很多，但可分期）

### ⚠️ 2.1 JWT 里固化标准租户 claim：active_tid（强烈建议）

**问题状态：❌ 未实现**

**现状：**

```java
// JwtTokenCustomizer.java:116-221
private void customizeAccessToken(JwtEncodingContext context, Authentication principal) {
    // ... 只添加了 userId, username, authorities, client_id, scope, auth_time, amr
    // ❌ 没有 active_tid
    // ❌ 没有 tenants
    // ❌ 没有 tenant_user_id
}
```

**问题：**

- ❌ JWT 中没有 `active_tid`（当前租户）
- ❌ JWT 中没有 `tenants`（所属租户列表）
- ❌ JWT 中没有 `tenant_user_id`（租户成员 ID）

**建议改造：**

- `sub`：`platform_user.id`（人）
- `active_tid`：当前租户（上下文）
- `tenants`：所属租户列表（可选）
- `tenant_user_id`：（可选，后端查权限更快）

**资源服务器校验：**

- Header `tid` 与 token `active_tid` 不一致 → 401（TenantMismatch）

---

### ⚠️ 2.2 Resource 建议保持"全局资源目录"，再加 tenant 覆盖表

**问题状态：✅ 符合建议**

**现状：**

```java
// Resource.java:13-14
@Entity
@Table(name = "resource")
public class Resource implements Serializable {
    // ... 没有 tenant_id 字段
}
```

**分析：**

- ✅ Resource 本身没有 `tenant_id`（符合建议）
- ❌ 没有 `tenant_resource` 表（租户启用/禁用/排序/标题覆盖等）

**建议改造（可选，可后置）：**

- `resource`：全局目录（API 权限点 / 菜单 / 功能开关等）
- `tenant_resource`：租户启用/禁用/排序/标题覆盖等（可选）

---

### ⚠️ 2.3 管理域分离：平台管理接口与租户管理接口要分开

**问题状态：❌ 未分离**

**现状：**

- ❌ 没有 `/platform/**` 路径（平台管理员）
- ❌ 没有 `/tenant/**` 路径（租户管理员）
- 所有接口混在一起

**建议改造：**

- `/platform/**`：平台管理员
- `/tenant/**`：租户管理员

---

## 3. 暂时不要动（否则改造成本暴涨）

### ✅ 3.1 不建议立刻推翻 Resource 的树/菜单结构

**状态：✅ 保持现状**

- Resource 看起来兼具"菜单+权限点"用途（还有 children transient 的树）
- 这块先保持"资源目录"不动，先把授权关系落到 `tenant_user` 层上更重要

---

### ✅ 3.2 不建议立刻大改 User 表名/迁移为 platform_user

**状态：✅ 保持现状**

- 可以先保持表名 `user`，在代码语义上明确它是 platform user
- 等稳定后再做表重命名迁移

---

## 4. 改造清单（按文件/模块落点列出来）

### 4.1 必改清单（落代码点）

#### 4.1.1 TenantFilter 改造（历史项）

**文件：** `tiny-oauth-server/src/main/java/com/tiny/platform/application/oauth/workflow/TenantFilter.java`

**说明：**

- 该历史项已被当前实现替代：
  - 认证链主入口：`TenantContextFilter`
  - 当前上下文头名：`X-Active-Tenant-Id`
- `TenantFilter` 已不再作为目标实现保留

---

#### 4.1.2 SecurityFilterChain 接入 TenantFilter（历史项）

**文件：**

- `tiny-oauth-server/src/main/java/com/tiny/platform/core/oauth/config/AuthorizationServerConfig.java`
- `tiny-oauth-server/src/main/java/com/tiny/platform/core/oauth/config/DefaultSecurityConfig.java`

**说明：**

- 当前实现已经改为在认证链中接入 `TenantContextFilter`
- 后续不应再回退到 `TenantFilter + X-Tenant-Id` 方案

---

#### 4.1.3 新增租户实体层

**需要新增：**

- ❌ `Tenant` 实体（`infrastructure/auth/tenant/domain/Tenant.java`）
- ❌ `TenantUser` 实体（`infrastructure/auth/tenant/domain/TenantUser.java`）
- ❌ `TenantRepository`
- ❌ `TenantUserRepository`
- ❌ `TenantService`
- ❌ `TenantUserService`
- ❌ 数据库迁移脚本：创建 `tenant` 和 `tenant_user` 表

---

#### 4.1.4 替换 user_role 关系

**需要新增：**

- ❌ `TenantUserRole` 实体（`infrastructure/auth/tenant/domain/TenantUserRole.java`）
- ❌ `PlatformUserRole` 实体（`infrastructure/auth/user/domain/PlatformUserRole.java`）
- ❌ 数据库迁移脚本：创建 `tenant_user_role` 和 `platform_user_role` 表
- ❌ 数据迁移脚本：将现有 `user_role` 数据迁移到新表

**需要修改：**

- ❌ `User.java`：移除 `@ManyToMany` 的 `roles` 字段
- ❌ `UserDetailsServiceImpl`：按 `active_tid` 加载 `tenant_user_role`
- ❌ `SecurityUser`：权限计算必须区分平台角色和租户角色

---

#### 4.1.5 Role 改造（带作用域）

**方案 A（推荐）：拆分表**

- ❌ 新增 `PlatformRole` 实体
- ❌ 新增 `TenantRole` 实体
- ❌ 数据库迁移脚本：创建 `platform_role` 和 `tenant_role` 表
- ❌ 数据迁移脚本：将现有 `role` 数据迁移到新表

**方案 B（折中）：增加字段**

- ❌ `Role.java`：增加 `scope` 字段（`PLATFORM` / `TENANT`）
- ❌ `Role.java`：增加 `tenantId` 字段（可为 null）
- ❌ 修改唯一约束：`(scope, tenant_id, code)` 唯一
- ❌ 数据库迁移脚本：修改 `role` 表结构

---

#### 4.1.6 OAuth2 Authorization 数据租户隔离

**文件：** `tiny-oauth-server/src/main/java/com/tiny/platform/core/oauth/config/OAuth2DataConfig.java`

**必须改：**

- ❌ 数据库迁移脚本：`oauth2_authorization` 表增加 `tenant_id` 字段
- ❌ 数据库迁移脚本：`oauth2_authorization_consent` 表增加 `tenant_id` 字段
- ❌ 创建 `TenantAwareOAuth2AuthorizationService` 包装类：
  - 包装 `JdbcOAuth2AuthorizationService`
  - `save()` 时自动写入 `tenant_id`（从 `TenantContext` 获取）
  - `findById()` / `findByToken()` 时自动按 `tenant_id` 过滤
- ❌ 创建 `TenantAwareOAuth2AuthorizationConsentService` 包装类：
  - 包装 `JdbcOAuth2AuthorizationConsentService`
  - `save()` 时自动写入 `tenant_id`
  - `findById()` 时自动按 `tenant_id` 过滤
- ❌ 修改 `OAuth2DataConfig.oauth2AuthorizationService()`：使用包装类
- ❌ 修改 `OAuth2DataConfig.customOAuth2AuthorizationConsentService()`：使用包装类

---

#### 4.1.7 OAuth2 RegisteredClient 租户化

**文件：**

- `tiny-oauth-server/src/main/java/com/tiny/platform/core/oauth/config/OAuth2DataConfig.java`
- `tiny-oauth-server/src/main/java/com/tiny/platform/core/oauth/config/RegisteredClientConfig.java`

**必须改：**

- ❌ 数据库迁移脚本：`oauth2_registered_client` 表增加 `tenant_id` 字段
- ❌ 数据库迁移脚本：`oauth2_registered_client` 表增加 `scope` 字段（`PLATFORM` / `TENANT`）
- ❌ 修改唯一约束：`(tenant_id, client_id)` 唯一
- ❌ 创建 `TenantAwareRegisteredClientRepository` 包装类：
  - 包装 `JdbcRegisteredClientRepository`
  - `save()` 时自动写入 `tenant_id`（从 `TenantContext` 获取）
  - `findById()` / `findByClientId()` 时自动按 `tenant_id` 过滤
  - 平台级客户端：`tenant_id IS NULL OR scope = 'PLATFORM'`
  - 租户级客户端：`tenant_id = ? AND scope = 'TENANT'`
- ❌ 修改 `OAuth2DataConfig.registeredClientRepository()`：使用包装类
- ❌ 修改 `RegisteredClientConfig.registerClients()`：注册时写入 `tenant_id`

---

### 4.2 建议改清单

#### 4.2.1 JwtTokenCustomizer 改造

**文件：** `tiny-oauth-server/src/main/java/com/tiny/platform/core/oauth/config/JwtTokenCustomizer.java`

**建议改：**

- ❌ `customizeAccessToken()`：写入 `active_tid` / `tenants` / `tenant_user_id`
- ❌ 资源端校验：token `tid` 与 header `tid` 一致性校验

---

#### 4.2.2 tenant_resource（可后置）

**需要新增：**

- ❌ `TenantResource` 实体
- ❌ 数据库迁移脚本：创建 `tenant_resource` 表

---

## 5. 你现在最好的"第一刀"从哪下（建议顺序）

### 第一刀：把 TenantFilter 改成强制模式 + 两条链接入（1 天内能闭环）

**优先级：🔴 最高**

**任务：**

1. 修改 `TenantFilter.java`：去掉 `default` 回退，加入白名单，统一 Header
2. 在 `AuthorizationServerConfig` 和 `DefaultSecurityConfig` 中接入 `TenantFilter`
3. 测试：确保无 tenant 的请求返回 400/401

---

### 第二刀：加 tenant / tenant_user 两张表，把"一人多租户"落地

**优先级：🔴 最高**

**任务：**

1. 创建 `Tenant` 实体和表
2. 创建 `TenantUser` 实体和表
3. 创建 Repository 和 Service
4. 测试：确保可以创建租户、添加租户成员

---

### 第三刀：把 user_role 替换为 tenant_user_role（权限归位）

**优先级：🔴 最高**

**任务：**

1. 创建 `TenantUserRole` 实体和表
2. 创建 `PlatformUserRole` 实体和表（如果需要平台角色）
3. 修改 `UserDetailsServiceImpl`：按 `active_tid` 加载租户角色
4. 修改 `SecurityUser`：权限计算区分平台和租户
5. 数据迁移：将现有 `user_role` 数据迁移到新表

---

### 第四刀：Role 增加 scope+tenant_id 或拆表

**优先级：🟡 高**

**任务：**

1. 选择方案 A（拆表）或方案 B（加字段）
2. 执行数据库迁移
3. 修改 `Role` 实体和相关查询逻辑
4. 测试：确保不同租户可以有相同 code 的角色

---

### 第五刀：JWT 写入 active_tid 并做一致性校验

**优先级：🟡 高**

**任务：**

1. 修改 `JwtTokenCustomizer`：写入 `active_tid` / `tenants` / `tenant_user_id`
2. 在资源服务器添加校验：Header `tid` 与 token `active_tid` 一致性
3. 测试：确保 token 中包含租户信息，且校验生效

---

## 6. 问题总结

### 必须改（7 项）

1. ❌ **缺失租户实体层**：没有 `Tenant` 和 `TenantUser`
2. ❌ **user_role 未拆分**：需要拆分为 `tenant_user_role` 和 `platform_user_role`
3. ❌ **Role 未带作用域**：`code` 全局唯一，无法支持租户自定义角色
4. ❌ **TenantFilter 有 default 回退**：缺失 tenant 时自动 fallback 为 `"default"`（高风险）
5. ❌ **TenantFilter 未接入 SecurityFilterChain**：两条链都没有接入
6. ❌ **OAuth2 Authorization 数据无租户隔离**：`oauth2_authorization` / `oauth2_authorization_consent` 表缺少 `tenant_id`（高风险）
7. ❌ **RegisteredClient 跨租户可见**：`oauth2_registered_client` 表缺少 `tenant_id`，`client_id` 全局唯一（高风险）

### 建议改（3 项）

1. ❌ **JWT 未写入租户信息**：没有 `active_tid` / `tenants` / `tenant_user_id`
2. ⚠️ **Resource 保持全局**：符合建议，但缺少 `tenant_resource` 覆盖表（可后置）
3. ❌ **管理域未分离**：没有 `/platform/**` 和 `/tenant/**` 路径分离

### 暂时不要动（2 项）

1. ✅ **Resource 树结构**：保持现状
2. ✅ **User 表名**：保持 `user`，暂不迁移为 `platform_user`

---

## 7. 风险评估

### 🔴 高风险项

1. **TenantFilter 的 default 回退**：可能导致数据串租、误入默认租户
2. **user_role 全局绑定**：一个人有 `ROLE_ADMIN` 就变成全局管理员
3. **Role code 全局唯一**：无法支持租户自定义角色，会冲突
4. **OAuth2 Authorization 数据无租户隔离**：跨租户授权数据混用，潜在跨租户授权读取（高危）
5. **RegisteredClient 跨租户可见**：不同租户的客户端配置可能被其他租户看到（高危）

### 🟡 中风险项

1. **TenantFilter 未接入 SecurityFilterChain**：token 和 UserDetails 可能拿不到 tenant
2. **JWT 未写入租户信息**：资源服务器无法校验租户一致性

---

## 8. 下一步行动

1. **立即执行第一刀**：改造 `TenantFilter` 并接入两条 `SecurityFilterChain`
2. **并行执行第二刀和第三刀**：创建租户实体层，拆分 `user_role`
3. **执行第四刀**：Role 增加作用域
4. **执行第五刀**：JWT 写入租户信息

---

## 附录：代码位置索引

### 关键文件

- `User.java`: `tiny-oauth-server/src/main/java/com/tiny/platform/infrastructure/auth/user/domain/User.java`
- `Role.java`: `tiny-oauth-server/src/main/java/com/tiny/platform/infrastructure/auth/role/domain/Role.java`
- `Resource.java`: `tiny-oauth-server/src/main/java/com/tiny/platform/infrastructure/auth/resource/domain/Resource.java`
- `TenantFilter.java`: `tiny-oauth-server/src/main/java/com/tiny/platform/application/oauth/workflow/TenantFilter.java`
- `TenantContext.java`: `tiny-oauth-server/src/main/java/com/tiny/platform/application/oauth/workflow/TenantContext.java`
- `AuthorizationServerConfig.java`: `tiny-oauth-server/src/main/java/com/tiny/platform/core/oauth/config/AuthorizationServerConfig.java`
- `DefaultSecurityConfig.java`: `tiny-oauth-server/src/main/java/com/tiny/platform/core/oauth/config/DefaultSecurityConfig.java`
- `UserDetailsServiceImpl.java`: `tiny-oauth-server/src/main/java/com/tiny/platform/core/oauth/security/UserDetailsServiceImpl.java`
- `JwtTokenCustomizer.java`: `tiny-oauth-server/src/main/java/com/tiny/platform/core/oauth/config/JwtTokenCustomizer.java`
- `SecurityUser.java`: `tiny-oauth-server/src/main/java/com/tiny/platform/core/oauth/model/SecurityUser.java`
- `OAuth2DataConfig.java`: `tiny-oauth-server/src/main/java/com/tiny/platform/core/oauth/config/OAuth2DataConfig.java`
- `RegisteredClientConfig.java`: `tiny-oauth-server/src/main/java/com/tiny/platform/core/oauth/config/RegisteredClientConfig.java`
- `schema.sql`: `tiny-oauth-server/src/main/resources/schema.sql`

### OAuth2 相关表（Spring Authorization Server 标准表）

- `oauth2_authorization`: 存储授权信息（authorization_code、access_token、refresh_token 等）
- `oauth2_authorization_consent`: 存储授权同意信息（用户对客户端的授权同意）
- `oauth2_registered_client`: 存储注册的客户端信息（client_id、client_secret、redirect_uri 等）
