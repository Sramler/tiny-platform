# 结构迁移说明

## 当前状态

### 已完成

1. ✅ **文档更新**
   - `frontend-module-design.md`：更新理想结构，增加基础设施层
   - `REFACTORING_PLAN.md`：更新目录结构和包名映射

2. ✅ **基础设施目录创建**
   - `infrastructure/plugin/`：插件管理基础设施（目录结构已创建）
   - `infrastructure/feature/`：Feature Toggle 基础设施（目录结构已创建）

3. ✅ **清理错误目录**
   - 删除了空的 `business/user/`、`business/role/`、`business/menu/` 目录

### 待完成

1. ⚠️ **代码迁移**
   - `com.tiny.oauthserver.sys.model.User` → `com.tiny.platform.infrastructure.auth.user.domain.User`
   - `com.tiny.oauthserver.sys.model.Role` → `com.tiny.platform.infrastructure.auth.role.domain.Role`
   - `com.tiny.oauthserver.sys.model.Resource` → `com.tiny.platform.infrastructure.auth.resource.domain.Resource`
   - `com.tiny.oauthserver.sys.model.Menu` → `com.tiny.platform.infrastructure.menu.domain.Menu`
   - 相关的 Repository、Service、Controller 也需要迁移

2. ⚠️ **Dict 模块迁移**
   - `com.tiny.dict` → `com.tiny.platform.core.dict`（从 business 移到 core）

3. ⚠️ **SaaS 基础设施实现**
   - `infrastructure/plugin/`：实现 Plugin 实体、Repository、Service、Interceptor
   - `infrastructure/feature/`：实现 Feature 实体、Repository、Service、Interceptor

## 目录结构说明

### 基础设施层（infrastructure）

- **系统运行必需，不可抽离**
- 包含：exception、auth、menu、plugin、feature、security、config

### 核心业务层（core）

- **平台核心能力，可抽离但重要**
- **所有租户必须拥有**
- 包含：oauth、tenant、dict、workflow

### 业务模块层（business）

- **可抽离为独立模块**
- **租户可选安装**
- 包含：export、scheduling

## 下一步行动

1. 迁移 `com.tiny.oauthserver.sys.*` 到 `com.tiny.platform.infrastructure.*`
2. 迁移 `com.tiny.dict` 到 `com.tiny.platform.core.dict`
3. 实现 `infrastructure/plugin/` 和 `infrastructure/feature/` 的完整功能
4. 更新所有相关的 import 语句
5. 更新 Spring Boot 的扫描路径

