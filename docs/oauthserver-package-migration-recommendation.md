# OAuthServer 包迁移建议

## 一、核心结论

### ❌ 不应该完全迁移

**理由**：
1. `OauthServerApplication` 主应用类不应该迁移（必须在应用包下）
2. OAuth2 特定配置应该保留在应用层（`config/` 包）
3. 系统 Controller（Login, Security, Index）是应用特定的

### ✅ 应该部分迁移 + 清理重复

**策略**：
1. **保留应用特定代码**在 `oauthserver` 包
2. **删除重复代码**，统一使用 `platform` 包下的实现
3. **迁移工具类**到 `platform.infrastructure.core.util`

## 二、当前问题

### 1. ⚠️ 代码重复严重

**发现的重复**：
- ✅ `oauthserver.sys.model.UserCreateUpdateDto` = `platform.infrastructure.auth.user.dto.UserCreateUpdateDto`
- ✅ `oauthserver.sys.model.RoleCreateUpdateDto` = `platform.infrastructure.auth.role.dto.RoleCreateUpdateDto`
- ✅ `oauthserver.sys.model.ResourceCreateUpdateDto` = `platform.infrastructure.auth.resource.dto.ResourceCreateUpdateDto`
- ✅ 其他 DTO 也存在类似重复

**影响**：
- 代码重复，维护成本高
- 容易产生不一致
- 违反 DRY 原则

### 2. ⚠️ 职责不清

**问题**：
- `oauthserver.sys` 包下的代码职责不清晰
- 有些代码应该属于平台基础设施（如 User, Role, Resource 相关）
- 有些代码是 OAuth Server 特定的（如 OAuth2 配置）

## 三、推荐方案

### 方案：部分迁移 + 清理重复（推荐 ⭐⭐⭐⭐⭐）

#### 1. 保留在 oauthserver（应用特定）

```
com.tiny.oauthserver/
├── OauthServerApplication.java     # ✅ 主应用类（必须保留）
├── config/                        # ✅ OAuth2、Security 配置（应用特定）
│   ├── AuthorizationServerConfig.java
│   ├── DefaultSecurityConfig.java
│   ├── OAuth2DataConfig.java
│   └── ...
└── sys/
    └── controller/                # ✅ 系统 Controller（应用特定）
        ├── LoginController.java
        ├── SecurityController.java
        ├── IndexController.java
        └── MessagesController.java
```

**理由**：
- 这些是 OAuth Server 应用特定的代码
- 不应该放在平台基础设施中
- 符合应用层职责

#### 2. 删除重复代码（高优先级）

**需要删除的重复 DTO**：
- ❌ `oauthserver.sys.model.UserCreateUpdateDto` → 使用 `platform.infrastructure.auth.user.dto.UserCreateUpdateDto`
- ❌ `oauthserver.sys.model.UserRequestDto` → 使用 `platform.infrastructure.auth.user.dto.UserRequestDto`
- ❌ `oauthserver.sys.model.UserResponseDto` → 使用 `platform.infrastructure.auth.user.dto.UserResponseDto`
- ❌ `oauthserver.sys.model.RoleCreateUpdateDto` → 使用 `platform.infrastructure.auth.role.dto.RoleCreateUpdateDto`
- ❌ `oauthserver.sys.model.RoleRequestDto` → 使用 `platform.infrastructure.auth.role.dto.RoleRequestDto`
- ❌ `oauthserver.sys.model.RoleResponseDto` → 使用 `platform.infrastructure.auth.role.dto.RoleResponseDto`
- ❌ `oauthserver.sys.model.ResourceCreateUpdateDto` → 使用 `platform.infrastructure.auth.resource.dto.ResourceCreateUpdateDto`
- ❌ `oauthserver.sys.model.ResourceRequestDto` → 使用 `platform.infrastructure.auth.resource.dto.ResourceRequestDto`
- ❌ `oauthserver.sys.model.ResourceResponseDto` → 使用 `platform.infrastructure.auth.resource.dto.ResourceResponseDto`
- ❌ `oauthserver.sys.model.ResourceSortDto` → 使用 `platform.infrastructure.auth.resource.dto.ResourceSortDto`

**操作步骤**：
1. 更新所有 Controller 使用 `platform` 包下的 DTO
2. 删除 `oauthserver.sys.model` 下的重复 DTO
3. 验证编译和运行

#### 3. 迁移工具类（中优先级）

**oauthserver.util/** → **platform.infrastructure.core.util/**
- `DeviceUtils.java`
- `IpUtils.java`
- `PemUtils.java`
- `QrCodeUtil.java`

**理由**：
- 工具类是基础设施能力
- 应该放在平台基础设施层
- 可以被其他模块复用

#### 4. 检查 Service 和 Repository 重复（中优先级）

**需要检查**：
- `oauthserver.sys.service.AvatarService` vs `platform.infrastructure.auth.user.service.AvatarService`
- `oauthserver.sys.service.SecurityService` vs `platform.infrastructure.auth.security.service.*`
- `oauthserver.sys.repository.*` vs `platform.infrastructure.auth.*.repository.*`

**如果重复**：
- 删除 `oauthserver` 下的，使用 `platform` 下的
- 更新所有引用

#### 5. 工作流代码评估（低优先级）

**oauthserver.workflow/** vs **platform.core.workflow/**
- 检查是否有重复
- 如果有重复，合并到 `platform.core.workflow`
- 如果没有重复，保留在 `oauthserver.workflow`（应用特定实现）

## 四、最终架构

### 目标架构

```
com.tiny.platform/
├── infrastructure/          # 基础设施层（可复用）
│   ├── auth/               # 认证授权基础设施
│   │   ├── user/
│   │   │   ├── dto/        # ✅ 统一使用这里的 DTO
│   │   │   ├── service/    # ✅ 统一使用这里的 Service
│   │   │   └── repository/ # ✅ 统一使用这里的 Repository
│   │   ├── role/
│   │   └── resource/
│   ├── core/               # 核心基础设施
│   │   ├── exception/      # 异常处理
│   │   ├── util/           # ✅ 工具类（从 oauthserver.util 迁移）
│   │   └── ...
│   └── ...
├── application/            # 应用层（业务逻辑）
│   └── controller/        # 业务 Controller
└── ...

com.tiny.oauthserver/       # OAuth Server 应用特定代码
├── OauthServerApplication.java  # ✅ 主应用类
├── config/                 # ✅ OAuth2、Security 配置
└── sys/
    └── controller/        # ✅ 系统 Controller（Login, Security等）
```

### 原则

1. **platform 包**：平台基础设施，可复用，符合 DDD 分层
2. **oauthserver 包**：OAuth Server 应用特定代码，不可复用

## 五、迁移优先级

### 高优先级（立即执行）

1. ✅ **删除重复 DTO**
   - 更新 Controller 使用 `platform` 包下的 DTO
   - 删除 `oauthserver.sys.model` 下的重复 DTO
   - 预计影响：17 个文件

### 中优先级（近期执行）

2. ⚠️ **迁移工具类**
   - 迁移 `oauthserver.util` → `platform.infrastructure.core.util`
   - 更新所有引用
   - 预计影响：4 个文件 + 引用

3. ⚠️ **检查 Service 和 Repository 重复**
   - 如果重复，删除 `oauthserver` 下的
   - 更新所有引用

### 低优先级（长期优化）

4. ⚠️ **工作流代码合并**
   - 评估 `oauthserver.workflow` 与 `platform.core.workflow` 的重复
   - 如果有重复，考虑合并

## 六、实施建议

### 阶段 1：清理重复 DTO（推荐立即执行）

**步骤**：
1. 检查所有 Controller 使用的 DTO
2. 更新为使用 `platform` 包下的 DTO
3. 删除 `oauthserver.sys.model` 下的重复 DTO
4. 验证编译和运行

**预计工作量**：2-4 小时

### 阶段 2：迁移工具类

**步骤**：
1. 迁移 `oauthserver.util` → `platform.infrastructure.core.util`
2. 更新所有引用
3. 验证编译和运行

**预计工作量**：1-2 小时

### 阶段 3：检查 Service 和 Repository

**步骤**：
1. 对比 `oauthserver.sys.service` 与 `platform.infrastructure.auth.*.service`
2. 对比 `oauthserver.sys.repository` 与 `platform.infrastructure.auth.*.repository`
3. 如果重复，删除 `oauthserver` 下的
4. 更新所有引用

**预计工作量**：2-4 小时

## 七、总结

### 是否应该完全迁移？

**答案：不应该完全迁移**

**理由**：
1. ❌ `OauthServerApplication` 主应用类不应该迁移
2. ❌ OAuth2 特定配置应该保留在应用层
3. ❌ 系统 Controller 是应用特定的

### 应该做什么？

**部分迁移 + 清理重复**：
1. ✅ **保留应用特定代码**在 `oauthserver` 包
2. ✅ **删除重复代码**，统一使用 `platform` 包下的实现
3. ✅ **迁移工具类**到 `platform.infrastructure.core.util`
4. ✅ **逐步重构**，统一架构

### 优先级

1. **高优先级**：清理重复 DTO（17 个文件）
2. **中优先级**：迁移工具类（4 个文件）
3. **低优先级**：检查 Service 和 Repository 重复

**结论**：不应该完全迁移，但应该清理重复代码，统一使用 `platform` 包下的基础设施实现。

