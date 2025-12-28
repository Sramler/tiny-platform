# SaaS 平台目录结构重构方案 V2（修正版）

## 核心原则

### 基础设施 vs 业务模块

**基础设施（infrastructure）**：
- ✅ 系统运行必需
- ✅ 不可抽离
- ✅ 被多个模块依赖
- 例如：User、Role、Menu、Exception、Config

**业务模块（business）**：
- ✅ 独立功能
- ✅ 可抽离
- ✅ 不影响系统核心运行
- 例如：Export、Scheduling

## 修正后的目录结构

```
com.tiny.platform/
├── infrastructure/          # 核心基础设施（系统运行必需，不可抽离）
│   ├── exception/         # ✅ 异常处理（已完成）
│   ├── auth/              # 🔄 认证授权基础设施
│   │   ├── user/          # 用户管理（认证核心）
│   │   │   ├── domain/    # User 实体
│   │   │   ├── repository/ # UserRepository
│   │   │   └── service/   # UserService
│   │   ├── role/          # 角色管理（权限核心）
│   │   │   ├── domain/    # Role 实体
│   │   │   ├── repository/ # RoleRepository
│   │   │   └── service/   # RoleService
│   │   ├── resource/       # 资源管理
│   │   │   ├── domain/    # Resource 实体
│   │   │   ├── repository/ # ResourceRepository
│   │   │   └── service/   # ResourceService
│   │   └── security/      # 安全相关
│   │       ├── UserDetailsServiceImpl
│   │       ├── SecurityUser
│   │       ├── MultiAuthenticationProvider
│   │       └── ...
│   ├── menu/              # 🔄 菜单基础设施
│   │   ├── domain/        # Menu 实体
│   │   ├── repository/    # MenuRepository
│   │   └── service/      # MenuService
│   ├── config/            # 基础设施配置
│   │   ├── security/      # 安全配置
│   │   ├── jackson/       # Jackson 配置
│   │   └── ...
│   └── common/            # 通用工具
│       ├── util/
│       └── annotation/
│
├── core/                   # 核心业务（可抽离为独立模块）
│   ├── oauth/             # OAuth2 核心
│   │   ├── config/        # OAuth2 配置
│   │   ├── service/       # OAuth2 服务
│   │   └── model/         # OAuth2 模型
│   ├── tenant/            # 租户管理（如需要）
│   └── workflow/          # 工作流核心
│       ├── api/
│       ├── camunda/
│       └── core/
│
├── business/               # 业务模块（可抽离为独立模块）
│   ├── export/            # ✅ 导出功能（可抽离）
│   └── scheduling/        # ✅ 调度功能（可抽离）
│
└── application/            # 应用层
    └── controller/        # 控制器
        ├── auth/          # 认证授权相关（User, Role, Resource）
        ├── menu/          # 菜单管理
        ├── export/        # 导出功能
        └── scheduling/    # 调度功能
```

## 为什么 User、Role、Menu 是基础设施？

### User（用户）
- ❌ **不能抽离**：被 Spring Security 认证核心依赖
  - `UserDetailsService` 必需
  - `MultiAuthenticationProvider` 必需
  - `SecurityUser` 包装必需
  - OAuth2 JWT 生成必需
- ✅ **系统运行必需**：没有 User，系统无法进行认证

### Role（角色）
- ❌ **不能抽离**：被权限体系核心依赖
  - RBAC 权限判断必需
  - `SecurityUser` 权限加载必需
  - `@PreAuthorize` 注解必需
- ✅ **系统运行必需**：没有 Role，系统无法进行权限控制

### Menu（菜单）
- ❌ **不能抽离**：被前端路由核心依赖
  - 前端动态路由加载必需（`router/index.ts`）
  - 权限控制必需
- ✅ **系统运行必需**：没有 Menu，前端无法动态加载路由

## 包名映射（修正版）

### 基础设施层（系统运行必需，不可抽离）
- `com.tiny.common.exception` → `com.tiny.platform.infrastructure.exception` ✅
- `com.tiny.oauthserver.sys.model.User` → `com.tiny.platform.infrastructure.auth.user.domain.User`
- `com.tiny.oauthserver.sys.model.Role` → `com.tiny.platform.infrastructure.auth.role.domain.Role`
- `com.tiny.oauthserver.sys.model.Resource` → `com.tiny.platform.infrastructure.auth.resource.domain.Resource`
- `com.tiny.oauthserver.sys.model.Menu` → `com.tiny.platform.infrastructure.menu.domain.Menu`
- `com.tiny.oauthserver.sys.repository.*` → `com.tiny.platform.infrastructure.auth.*.repository.*`
- `com.tiny.oauthserver.sys.service.UserService` → `com.tiny.platform.infrastructure.auth.user.service.UserService`
- `com.tiny.oauthserver.sys.service.RoleService` → `com.tiny.platform.infrastructure.auth.role.service.RoleService`
- `com.tiny.oauthserver.sys.service.MenuService` → `com.tiny.platform.infrastructure.menu.service.MenuService`
- `com.tiny.oauthserver.sys.security.*` → `com.tiny.platform.infrastructure.auth.security.*`

### 核心业务层（可抽离为独立模块）
- `com.tiny.oauthserver.workflow` → `com.tiny.platform.core.workflow`
- `com.tiny.oauthserver.oauth` → `com.tiny.platform.core.oauth`

### 业务模块层（可抽离为独立模块）
- `com.tiny.export` → `com.tiny.platform.business.export`
- `com.tiny.scheduling` → `com.tiny.platform.business.scheduling`

### 应用层
- `com.tiny.oauthserver.sys.controller.UserController` → `com.tiny.platform.application.controller.auth.UserController`
- `com.tiny.oauthserver.sys.controller.RoleController` → `com.tiny.platform.application.controller.auth.RoleController`
- `com.tiny.oauthserver.sys.controller.MenuController` → `com.tiny.platform.application.controller.menu.MenuController`
- `com.tiny.oauthserver.workflow.controller.*` → `com.tiny.platform.application.controller.workflow.*`
- `com.tiny.export.web.ExportController` → `com.tiny.platform.application.controller.export.*`
- `com.tiny.scheduling.controller.*` → `com.tiny.platform.application.controller.scheduling.*`

