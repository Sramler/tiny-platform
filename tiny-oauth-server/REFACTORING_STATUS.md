# SaaS 平台目录结构重构状态

## 已完成

### 1. 目录结构创建 ✅
已创建新的 SaaS 平台目录结构：
- `com.tiny.platform.infrastructure/` - 核心基础设施
- `com.tiny.platform.core/` - 核心业务
- `com.tiny.platform.business/` - 业务模块
- `com.tiny.platform.application/` - 应用层

### 2. 基础设施层 - 异常处理模块 ✅
- ✅ 已移动 `com.tiny.common.exception` → `com.tiny.platform.infrastructure.exception`
- ✅ 已更新所有包名和导入语句
- ✅ 文件列表：
  - `base/BaseExceptionHandler.java`
  - `code/ErrorCode.java`
  - `exception/BusinessException.java`
  - `exception/NotFoundException.java`
  - `exception/UnauthorizedException.java`
  - `response/ErrorResponse.java`
  - `util/ExceptionUtils.java`
  - `util/ResponseUtils.java`

### 3. 应用扫描路径更新 ✅
- ✅ 已更新 `OauthServerApplication.java` 的扫描路径，包含 `com.tiny.platform`

## 待完成

### 1. 基础设施层
- [ ] 移动配置类到 `infrastructure.config`
  - `oauthserver.config.*` → `infrastructure.config.*` 或 `core.oauth.config.*`
- [ ] 移动安全相关到 `infrastructure.security`
  - `oauthserver.sys.security.*` → `infrastructure.security.*`
- [ ] 移动通用工具到 `infrastructure.common.util`
  - `oauthserver.util.*` → `infrastructure.common.util.*`

### 2. 核心业务层
- [ ] 移动 OAuth2 核心到 `core.oauth`
  - `oauthserver.oauth.*` → `core.oauth.service.*`
  - `oauthserver.config.AuthorizationServerConfig` → `core.oauth.config.*`
- [ ] 移动工作流到 `core.workflow`
  - `oauthserver.workflow.*` → `core.workflow.*`
  - `oauthserver.core.WorkflowService` → `core.workflow.*`

### 3. 基础设施层 - 认证授权（待完成）
- [ ] 移动用户管理到 `infrastructure.auth.user`
  - `oauthserver.sys.model.User` → `infrastructure.auth.user.domain.*`
  - `oauthserver.sys.repository.UserRepository` → `infrastructure.auth.user.repository.*`
  - `oauthserver.sys.service.UserService` → `infrastructure.auth.user.service.*`
- [ ] 移动角色权限到 `infrastructure.auth.role`
  - `oauthserver.sys.model.Role` → `infrastructure.auth.role.domain.*`
  - `oauthserver.sys.service.RoleService` → `infrastructure.auth.role.service.*`
- [ ] 移动资源管理到 `infrastructure.auth.resource`
  - `oauthserver.sys.model.Resource` → `infrastructure.auth.resource.domain.*`
  - `oauthserver.sys.service.ResourceService` → `infrastructure.auth.resource.service.*`
- [ ] 移动安全相关到 `infrastructure.auth.security`
  - `oauthserver.sys.security.*` → `infrastructure.auth.security.*`

### 4. 基础设施层 - 菜单（待完成）
- [ ] 移动菜单管理到 `infrastructure.menu`
  - `oauthserver.sys.model.Menu` → `infrastructure.menu.domain.*`
  - `oauthserver.sys.service.MenuService` → `infrastructure.menu.service.*`

### 5. 业务模块层（待完成）
- [ ] 移动导出功能到 `business.export`
  - `export.*` → `business.export.*`
- [ ] 移动调度功能到 `business.scheduling`
  - `scheduling.*` → `business.scheduling.*`

### 6. 应用层（待完成）
- [ ] 移动所有 Controller 到 `application.controller`
  - `oauthserver.sys.controller.UserController` → `application.controller.auth.UserController`
  - `oauthserver.sys.controller.RoleController` → `application.controller.auth.RoleController`
  - `oauthserver.sys.controller.ResourceController` → `application.controller.auth.ResourceController`
  - `oauthserver.sys.controller.MenuController` → `application.controller.menu.MenuController`
  - `oauthserver.workflow.controller.*` → `application.controller.workflow.*`
  - `export.web.ExportController` → `application.controller.export.*`
  - `scheduling.controller.*` → `application.controller.scheduling.*`

### 7. 更新引用（待完成）
- [ ] 更新所有文件中的导入语句
- [ ] 更新 `GlobalExceptionHandler` 的包引用
- [ ] 更新所有 Service、Repository 的包引用
- [ ] 更新 `SecurityUser` 中的 Resource 引用
- [ ] 更新所有 Controller 中的包引用

## 下一步操作建议

由于这是一个大规模重构，建议：

1. **逐步迁移**：先完成基础设施层，再处理核心业务层，最后处理业务模块
2. **批量替换**：使用 IDE 的重构功能批量重命名包
3. **测试验证**：每完成一个模块，进行编译测试
4. **删除旧代码**：确认新代码工作正常后，删除旧目录

## 包名映射表

| 旧包名 | 新包名 | 性质 |
|--------|--------|------|
| `com.tiny.common.exception` | `com.tiny.platform.infrastructure.exception` | ✅ 基础设施 |
| `com.tiny.oauthserver.sys.model.User` | `com.tiny.platform.infrastructure.auth.user.domain` | ✅ 基础设施 |
| `com.tiny.oauthserver.sys.model.Role` | `com.tiny.platform.infrastructure.auth.role.domain` | ✅ 基础设施 |
| `com.tiny.oauthserver.sys.model.Resource` | `com.tiny.platform.infrastructure.auth.resource.domain` | ✅ 基础设施 |
| `com.tiny.oauthserver.sys.model.Menu` | `com.tiny.platform.infrastructure.menu.domain` | ✅ 基础设施 |
| `com.tiny.oauthserver.sys.security.*` | `com.tiny.platform.infrastructure.auth.security.*` | ✅ 基础设施 |
| `com.tiny.oauthserver.config` | `com.tiny.platform.infrastructure.config` / `com.tiny.platform.core.oauth.config` | ✅ 基础设施 |
| `com.tiny.oauthserver.sys.controller.*` | `com.tiny.platform.application.controller.auth.*` / `application.controller.menu.*` | 应用层 |
| `com.tiny.oauthserver.workflow` | `com.tiny.platform.core.workflow` | 核心业务 |
| `com.tiny.oauthserver.oauth` | `com.tiny.platform.core.oauth` | 核心业务 |
| `com.tiny.export` | `com.tiny.platform.business.export` | 业务模块 |
| `com.tiny.scheduling` | `com.tiny.platform.business.scheduling` | 业务模块 |

