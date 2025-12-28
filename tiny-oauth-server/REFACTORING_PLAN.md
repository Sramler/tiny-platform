# SaaS 平台目录结构重构方案

## 目标

按照 SaaS 平台核心基础设施重构目录结构，方便以后抽离相关业务。

## 新的目录结构

```
com.tiny.platform/
├── infrastructure/          # 核心基础设施（系统运行必需，不可抽离）
│   ├── exception/          # ✅ 异常处理
│   │   ├── base/
│   │   ├── code/
│   │   ├── exception/
│   │   ├── response/
│   │   └── util/
│   ├── auth/              # 🔄 认证授权基础设施（NEW）
│   │   ├── user/          # 用户实体、仓储、服务（认证核心）
│   │   │   ├── domain/    # User 实体
│   │   │   ├── repository/ # UserRepository
│   │   │   └── service/   # UserService
│   │   ├── role/          # 角色实体、仓储、服务（权限核心）
│   │   │   ├── domain/    # Role 实体
│   │   │   ├── repository/ # RoleRepository
│   │   │   └── service/   # RoleService
│   │   ├── resource/      # 资源实体、仓储、服务（权限控制核心）
│   │   │   ├── domain/    # Resource 实体
│   │   │   ├── repository/ # ResourceRepository
│   │   │   └── service/   # ResourceService
│   │   └── security/      # 安全相关（UserDetailsService, SecurityUser 等）
│   ├── menu/              # 🔄 菜单基础设施（NEW）
│   │   ├── domain/        # Menu 实体
│   │   ├── repository/    # MenuRepository
│   │   └── service/      # MenuService
│   ├── plugin/            # 🔄 插件管理基础设施（NEW）
│   │   ├── domain/        # Plugin 实体
│   │   ├── repository/    # PluginRepository
│   │   ├── service/       # PluginService
│   │   └── interceptor/   # PluginInstallFilter
│   ├── feature/           # 🔄 Feature Toggle 基础设施（NEW）
│   │   ├── domain/        # Feature 实体
│   │   ├── repository/    # FeatureRepository
│   │   ├── service/       # FeatureService
│   │   └── interceptor/   # FeatureToggleFilter
│   ├── config/            # 基础设施配置
│   │   ├── security/      # 安全配置
│   │   ├── jackson/       # Jackson 配置
│   │   └── ...           # 其他配置
│   └── common/            # 通用工具
│       ├── util/
│       └── annotation/
│
├── core/                   # 核心业务（可抽离为独立模块，但重要）
│   ├── oauth/             # OAuth2 核心
│   │   ├── config/        # OAuth2 配置
│   │   ├── service/       # OAuth2 服务
│   │   └── model/         # OAuth2 模型
│   ├── tenant/            # 租户管理
│   ├── dict/              # ✅ 数据字典（平台核心能力，可抽离）
│   │   ├── domain/        # DictType, DictItem 实体
│   │   ├── repository/    # DictRepository
│   │   ├── service/       # DictService
│   │   └── cache/         # DictCacheManager
│   └── workflow/          # 工作流核心
│       ├── api/
│       ├── camunda/
│       └── core/
│
├── business/               # 业务模块（可抽离为独立模块）
│   ├── export/            # ✅ 导出功能（可抽离）
│   └── scheduling/        # ✅ 调度功能（可抽离）
│   ⚠️ 注意：business 包下不应包含 user、role、menu（这些是基础设施）
│   ⚠️ 注意：dict 已移到 core.dict（平台核心能力）
│
├── application/            # 应用层
│   ├── controller/        # 控制器
│   │   ├── auth/          # 认证授权相关（User, Role, Resource）
│   │   ├── menu/          # 菜单管理
│   │   ├── dict/          # 数据字典
│   │   ├── export/        # 导出功能
│   │   ├── scheduling/    # 调度功能
│   │   └── workflow/      # 工作流
│   └── dto/               # 数据传输对象
│
└── OauthServerApplication.java
```

## 重构步骤

1. 创建新的目录结构
2. 移动 common.exception → infrastructure.exception
3. 移动 oauthserver.config → infrastructure.config 和 core.oauth.config
4. 移动 oauthserver.sys → infrastructure.auth (user, role, resource) 和 infrastructure.menu
5. 移动 oauthserver.workflow → core.workflow
6. 移动 oauthserver.oauth → core.oauth
7. 移动 export → business.export
8. 移动 scheduling → business.scheduling
9. 移动所有 controller → application.controller
10. 更新包名和扫描路径

## 包名映射

### 基础设施层（系统运行必需，不可抽离）

- `com.tiny.common.exception` → `com.tiny.platform.infrastructure.exception` ✅
- `com.tiny.oauthserver.sys.model.User` → `com.tiny.platform.infrastructure.auth.user.domain.User`
- `com.tiny.oauthserver.sys.model.Role` → `com.tiny.platform.infrastructure.auth.role.domain.Role`
- `com.tiny.oauthserver.sys.model.Resource` → `com.tiny.platform.infrastructure.auth.resource.domain.Resource`（权限控制核心）
- `com.tiny.oauthserver.sys.model.Menu` → `com.tiny.platform.infrastructure.menu.domain.Menu`
- `com.tiny.oauthserver.sys.repository.UserRepository` → `com.tiny.platform.infrastructure.auth.user.repository.UserRepository`
- `com.tiny.oauthserver.sys.service.UserService` → `com.tiny.platform.infrastructure.auth.user.service.UserService`
- `com.tiny.oauthserver.sys.security.*` → `com.tiny.platform.infrastructure.auth.security.*`
- `com.tiny.oauthserver.config` → `com.tiny.platform.infrastructure.config` / `com.tiny.platform.core.oauth.config`
- `com.tiny.platform.infrastructure.plugin.*` → **NEW** 插件管理基础设施
- `com.tiny.platform.infrastructure.feature.*` → **NEW** Feature Toggle 基础设施

### 核心业务层（可抽离为独立模块，但重要）

- `com.tiny.oauthserver.workflow` → `com.tiny.platform.core.workflow`
- `com.tiny.oauthserver.oauth` → `com.tiny.platform.core.oauth`
- `com.tiny.dict` → `com.tiny.platform.core.dict` ⚠️ **已从 business 移到 core**（平台核心能力）
- `com.tiny.platform.core.tenant.*` → **NEW** 租户管理

### 业务模块层（可抽离为独立模块）

- `com.tiny.export` → `com.tiny.platform.business.export`
- `com.tiny.scheduling` → `com.tiny.platform.business.scheduling`

⚠️ **重要**：`business` 包下不应包含 `user`、`role`、`menu`，这些应该放在 `infrastructure` 包下。

### 应用层

- `com.tiny.oauthserver.sys.controller.*` → `com.tiny.platform.application.controller.auth.*` / `com.tiny.platform.application.controller.menu.*`
- `com.tiny.oauthserver.workflow.controller.*` → `com.tiny.platform.application.controller.workflow.*`
- `com.tiny.dict.controller.*` → `com.tiny.platform.application.controller.dict.*`（如果存在）
- `com.tiny.export.web.ExportController` → `com.tiny.platform.application.controller.export.*`
- `com.tiny.scheduling.controller.*` → `com.tiny.platform.application.controller.scheduling.*`

## 关键设计原则

### 基础设施 vs 业务模块

**基础设施（infrastructure）**：

- ✅ 系统运行必需
- ✅ 不可抽离
- ✅ 被多个模块依赖
- 例如：User、Role、Resource、Menu、Exception、Config、Plugin、Feature

**核心业务（core）**：

- ⚠️ 平台核心能力
- ⚠️ 可抽离但重要
- ⚠️ 所有租户必须拥有
- 例如：OAuth2、Workflow、Dict、Tenant

**业务模块（business）**：

- ✅ 独立功能
- ✅ 可抽离
- ✅ 不影响系统核心运行
- ✅ 租户可选安装
- 例如：Export、Scheduling
