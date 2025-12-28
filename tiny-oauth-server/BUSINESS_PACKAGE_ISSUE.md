# Business 包结构问题分析

## 问题描述

在 `com.tiny.platform.business` 包下存在 `user`、`role`、`menu` 目录，这是**不合理的**。

## 为什么不合理？

### 1. 违反架构设计原则

根据架构分析文档（`ARCHITECTURE_ANALYSIS.md`），`User`、`Role`、`Menu` 是**核心基础设施**，不是业务模块：

| 模块 | 性质 | 是否可抽离 | 离开后能否运行 | 应该放在哪里 |
|------|------|-----------|--------------|------------|
| **User** | 基础设施 | ❌ 不可抽离 | ❌ 无法运行 | `infrastructure.auth.user` |
| **Role** | 基础设施 | ❌ 不可抽离 | ❌ 无法运行 | `infrastructure.auth.role` |
| **Menu** | 基础设施 | ❌ 不可抽离 | ❌ 无法运行 | `infrastructure.menu` |

### 2. 业务模块的定义

**业务模块（business）**应该满足：
- ✅ 独立功能
- ✅ 可抽离
- ✅ 不影响系统核心运行

**基础设施（infrastructure）**应该满足：
- ✅ 系统运行必需
- ✅ 不可抽离
- ✅ 被多个模块依赖

### 3. 当前错误的结构

```
com.tiny.platform.business/
├── user/          ❌ 错误：应该是 infrastructure.auth.user
├── role/          ❌ 错误：应该是 infrastructure.auth.role
├── menu/          ❌ 错误：应该是 infrastructure.menu
├── export/        ✅ 正确：可抽离的业务模块
├── scheduling/    ✅ 正确：可抽离的业务模块
└── dict/          ✅ 正确：可抽离的业务模块
```

## 正确的结构

### 应该的结构

```
com.tiny.platform/
├── infrastructure/          # 核心基础设施（系统运行必需，不可抽离）
│   ├── exception/          # ✅ 异常处理
│   ├── auth/              # ✅ 认证授权基础设施
│   │   ├── user/          # ✅ 用户（认证核心）
│   │   ├── role/          # ✅ 角色（权限核心）
│   │   ├── resource/      # ✅ 资源（权限控制核心）
│   │   └── security/      # ✅ 安全相关
│   ├── menu/              # ✅ 菜单基础设施
│   ├── config/            # ✅ 基础设施配置
│   └── common/            # ✅ 通用工具
│
├── business/               # 业务模块（可抽离为独立模块）
│   ├── dict/              # ✅ 数据字典（可抽离）
│   ├── export/            # ✅ 导出功能（可抽离）
│   └── scheduling/        # ✅ 调度功能（可抽离）
│
├── core/                   # 核心业务（可抽离为独立模块）
│   ├── oauth/             # ✅ OAuth2 核心
│   └── workflow/          # ✅ 工作流核心
│
└── application/            # 应用层
    └── controller/        # ✅ 控制器
```

## 修正方案

### 需要移动的目录

1. **`business.user` → `infrastructure.auth.user`**
   - 原因：User 是认证授权核心，系统运行必需
   - 影响：所有依赖 User 的代码需要更新导入路径

2. **`business.role` → `infrastructure.auth.role`**
   - 原因：Role 是权限管理核心，系统运行必需
   - 影响：所有依赖 Role 的代码需要更新导入路径

3. **`business.menu` → `infrastructure.menu`**
   - 原因：Menu 是前端路由核心，系统运行必需
   - 影响：所有依赖 Menu 的代码需要更新导入路径

### 修正步骤

1. **移动目录结构**
   ```bash
   # 移动 user
   mv src/main/java/com/tiny/platform/business/user \
      src/main/java/com/tiny/platform/infrastructure/auth/user
   
   # 移动 role
   mv src/main/java/com/tiny/platform/business/role \
      src/main/java/com/tiny/platform/infrastructure/auth/role
   
   # 移动 menu
   mv src/main/java/com/tiny/platform/business/menu \
      src/main/java/com/tiny/platform/infrastructure/menu
   ```

2. **更新包名**
   - 更新所有 Java 文件的 `package` 声明
   - 更新所有 `import` 语句

3. **更新扫描路径**
   - 确保 Spring Boot 能够扫描到新的包路径

## 关键区别总结

| 特征 | 基础设施（infrastructure） | 业务模块（business） |
|------|-------------------------|-------------------|
| **User** | ✅ 是 | ❌ 否 |
| **Role** | ✅ 是 | ❌ 否 |
| **Menu** | ✅ 是 | ❌ 否 |
| **Resource** | ✅ 是 | ❌ 否 |
| **Dict** | ❌ 否 | ✅ 是 |
| **Export** | ❌ 否 | ✅ 是 |
| **Scheduling** | ❌ 否 | ✅ 是 |

## 结论

**`business` 包下存在 `user`、`role`、`menu` 是不合理的**，需要立即修正：

1. ✅ 将这些模块移动到 `infrastructure` 包下
2. ✅ 更新所有相关的包名和导入
3. ✅ 确保架构设计的一致性

这样可以：
- 明确模块的职责和性质
- 避免架构混乱
- 便于后续的模块抽离和维护

