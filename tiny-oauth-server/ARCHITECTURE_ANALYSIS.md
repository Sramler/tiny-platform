# 架构分析：User、Role、Menu 的性质分析

## 分析结果

### User（用户）
**性质：核心基础设施 - 认证授权基础**

**关键依赖关系：**
1. ✅ **Spring Security 核心**：
   - 实现 `UserDetails` 接口
   - 被 `UserDetailsService` 使用（认证必需）
   - 被 `MultiAuthenticationProvider` 使用（认证核心）
   - 被 `SecurityUser` 包装（安全上下文）

2. ✅ **OAuth2 核心**：
   - 被 `JwtTokenCustomizer` 使用（JWT Token 生成）
   - 被 `AuthorizationServerConfig` 使用

3. ✅ **工作流系统**：
   - 被 `CamundaIdentityProvider` 使用
   - 工作流任务分配必需

4. ✅ **业务功能**：
   - 用户管理 CRUD（这是业务功能，但用户实体本身是基础设施）

**结论：User 是系统运行的基础，无法抽离。没有 User，系统无法进行认证和授权。**

---

### Role（角色）
**性质：核心基础设施 - 权限管理基础**

**关键依赖关系：**
1. ✅ **RBAC 核心**：
   - 与 User 多对多关系（权限体系基础）
   - 被 `SecurityUser` 使用（权限判断）
   - 被 `@PreAuthorize` 等注解使用

2. ✅ **工作流系统**：
   - 被 `CamundaIdentityProvider` 使用（组/角色映射）

3. ✅ **业务功能**：
   - 角色管理 CRUD（这是业务功能，但角色实体本身是基础设施）

**结论：Role 是权限体系的基础，无法抽离。没有 Role，系统无法进行权限控制。**

---

### Menu（菜单）
**性质：核心基础设施 - 前端路由和权限控制基础**

**关键依赖关系：**
1. ✅ **前端路由核心**：
   - 被前端动态加载（`router/index.ts` 中的 `generateMenuRoutes`）
   - 前端路由配置的基础数据源

2. ✅ **权限控制**：
   - 与角色/权限关联
   - 控制前端页面访问

3. ✅ **业务功能**：
   - 菜单管理 CRUD（这是业务功能，但菜单实体本身是基础设施）

**结论：Menu 是前端路由和权限控制的基础，无法抽离。没有 Menu，前端无法动态加载路由。**

---

## 重新设计的目录结构

### 正确的分层应该是：

```
com.tiny.platform/
├── infrastructure/          # 核心基础设施（系统运行必需，不可抽离）
│   ├── exception/         # ✅ 异常处理
│   ├── auth/              # 🔄 认证授权基础设施（NEW）
│   │   ├── user/          # 用户实体、仓储、服务
│   │   ├── role/          # 角色实体、仓储、服务
│   │   ├── resource/       # 资源实体、仓储、服务
│   │   └── security/      # 安全相关（UserDetailsService, SecurityUser 等）
│   ├── menu/              # 🔄 菜单基础设施（NEW）
│   │   ├── domain/        # 菜单实体
│   │   ├── repository/    # 菜单仓储
│   │   └── service/       # 菜单服务
│   ├── config/            # 基础设施配置
│   └── common/            # 通用工具
│
├── core/                   # 核心业务（可抽离为独立模块）
│   ├── oauth/             # OAuth2 核心
│   ├── tenant/            # 租户管理
│   └── workflow/          # 工作流核心
│
├── business/               # 业务模块（可抽离为独立模块）
│   ├── export/            # ✅ 导出功能（可抽离）
│   └── scheduling/        # ✅ 调度功能（可抽离）
│
└── application/          # 应用层
    └── controller/        # 控制器
        ├── auth/          # 认证授权相关（User, Role, Resource）
        ├── menu/          # 菜单管理
        ├── export/        # 导出功能
        └── scheduling/    # 调度功能
```

## 关键区别

| 模块 | 性质 | 是否可抽离 | 离开后能否运行 | 理由 |
|------|------|-----------|--------------|------|
| **User** | 基础设施 | ❌ 不可抽离 | ❌ 无法运行 | 认证授权核心，系统运行必需 |
| **Role** | 基础设施 | ❌ 不可抽离 | ❌ 无法运行 | 权限管理核心，系统运行必需 |
| **Resource** | 基础设施 | ❌ 不可抽离 | ❌ 无法运行 | 权限控制核心，细粒度权限必需 |
| **Menu** | 基础设施 | ❌ 不可抽离 | ❌ 无法运行 | 前端路由核心，系统运行必需 |
| **Export** | 业务模块 | ✅ 可抽离 | ✅ 可以运行 | 独立功能，不影响系统核心 |
| **Scheduling** | 业务模块 | ✅ 可抽离 | ✅ 可以运行 | 独立功能，不影响系统核心 |
| **Workflow** | 核心业务 | ⚠️ 可抽离但需保留接口 | ⚠️ 可运行但功能受限 | 业务核心，但可作为独立模块 |

## 建议

1. **将 User、Role、Resource、Menu 放在 `infrastructure.auth` 和 `infrastructure.menu`**
   - 明确它们是基础设施，不是业务模块
   - 强调它们对系统运行的重要性
   - User、Role、Resource 是权限体系的三要素，缺一不可

2. **业务模块只包含真正可抽离的功能**
   - export（导出）
   - scheduling（调度）
   - 其他业务特定功能

3. **核心业务层包含可抽离但重要的业务能力**
   - oauth（OAuth2 核心）
   - workflow（工作流）
   - tenant（租户管理）

