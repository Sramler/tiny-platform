# 基础设施模块总结

## 基础设施定义

**基础设施（Infrastructure）**：系统运行必需的核心组件，不可抽离，被多个模块依赖。

## 基础设施模块清单

### 1. ✅ Exception（异常处理）
- **位置**：`infrastructure.exception`
- **作用**：统一异常响应格式
- **状态**：✅ 已完成迁移

### 2. ✅ Auth（认证授权基础设施）
- **位置**：`infrastructure.auth`
- **包含**：
  - **User（用户）** - 认证主体
  - **Role（角色）** - 权限组
  - **Resource（资源）** - 权限载体
  - **Security** - 安全相关（UserDetailsService, SecurityUser 等）

#### 2.1 User（用户）
- **性质**：认证授权核心
- **依赖关系**：
  - 被 `UserDetailsService` 使用（Spring Security 认证必需）
  - 被 `MultiAuthenticationProvider` 使用（认证核心）
  - 被 `SecurityUser` 包装（安全上下文）
  - 被 `JwtTokenCustomizer` 使用（JWT Token 生成）
  - 被工作流系统使用（任务分配）
- **结论**：❌ 不可抽离，没有 User 系统无法进行认证

#### 2.2 Role（角色）
- **性质**：权限管理核心
- **依赖关系**：
  - 与 User 多对多关系（RBAC 基础）
  - 与 Resource 多对多关系（权限关联）
  - 被 `SecurityUser` 使用（权限判断）
  - 被 `@PreAuthorize` 等注解使用
  - 被工作流系统使用（组/角色映射）
- **结论**：❌ 不可抽离，没有 Role 系统无法进行权限控制

#### 2.3 Resource（资源）
- **性质**：权限控制核心
- **依赖关系**：
  - 与 Role 多对多关系（`role_resource` 表）
  - 被 `SecurityUser` 使用，生成权限列表（`GrantedAuthority`）
  - Resource 的 `name` 作为权限标识
  - Resource 的 `permission` 用于前端权限控制
  - Resource 的 `uri` 和 `method` 用于后端 API 权限控制
  - 支持多种资源类型：目录、菜单、按钮、接口
- **结论**：❌ 不可抽离，没有 Resource 系统无法进行细粒度权限控制

### 3. ✅ Menu（菜单基础设施）
- **位置**：`infrastructure.menu`
- **性质**：前端路由和权限控制基础
- **依赖关系**：
  - 被前端动态加载（`router/index.ts` 中的 `generateMenuRoutes`）
  - 前端路由配置的基础数据源
  - 与权限系统关联
- **结论**：❌ 不可抽离，没有 Menu 前端无法动态加载路由

### 4. ⏳ Config（基础设施配置）
- **位置**：`infrastructure.config`
- **包含**：安全配置、Jackson 配置等

### 5. ⏳ Common（通用工具）
- **位置**：`infrastructure.common`
- **包含**：通用工具类、注解等

## 权限体系三要素

```
User (用户) - 认证主体
  ↓ 拥有
Role (角色) - 权限组
  ↓ 关联
Resource (资源) - 权限载体
  ↓ 转换为
GrantedAuthority (权限标识)
  ↓ 用于
@PreAuthorize / hasAuthority() (权限判断)
```

**结论**：User、Role、Resource 是权限体系的三要素，都是基础设施，缺一不可。

## 基础设施 vs 业务模块对比

| 类型 | 模块 | 是否可抽离 | 离开后能否运行 |
|------|------|-----------|--------------|
| **基础设施** | User | ❌ | ❌ 无法运行（无法认证） |
| **基础设施** | Role | ❌ | ❌ 无法运行（无法授权） |
| **基础设施** | Resource | ❌ | ❌ 无法运行（无法权限控制） |
| **基础设施** | Menu | ❌ | ❌ 无法运行（前端无路由） |
| **基础设施** | Exception | ❌ | ⚠️ 可运行但无统一异常处理 |
| **业务模块** | Export | ✅ | ✅ 可以运行（不影响核心） |
| **业务模块** | Scheduling | ✅ | ✅ 可以运行（不影响核心） |

## 重构原则

1. **基础设施放在 `infrastructure` 包下**
   - 明确标识为系统运行必需
   - 强调不可抽离的特性

2. **业务模块放在 `business` 包下**
   - 明确标识为可抽离功能
   - 强调独立性和可复用性

3. **核心业务放在 `core` 包下**
   - 业务核心能力
   - 可抽离但需保留接口

