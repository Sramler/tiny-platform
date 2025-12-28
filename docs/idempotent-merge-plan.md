# Idempotent 模块合并到 tiny-oauth-server 方案

## 一、合并目标

将 `tiny-idempotent-platform` 模块合并到 `tiny-oauth-server` 中，使其成为 SaaS 平台的基础设施能力，便于未来演进。

## 二、合并策略

### 2.1 包结构映射

| 原模块 | 原包名 | 新包名 | 说明 |
|--------|--------|--------|------|
| idempotent-core | `com.tiny.idempotent.core` | `com.tiny.platform.infrastructure.idempotent.core` | 核心引擎，纯 Java |
| idempotent-sdk | `com.tiny.idempotent.sdk` | `com.tiny.platform.infrastructure.idempotent.sdk` | 业务使用层（注解、Facade） |
| idempotent-repository | `com.tiny.idempotent.repository` | `com.tiny.platform.infrastructure.idempotent.repository` | 存储实现（Redis/DB/Memory） |
| idempotent-starter | `com.tiny.idempotent.starter` | `com.tiny.platform.infrastructure.idempotent.starter` | Spring Boot 自动配置 |
| idempotent-console | `com.tiny.idempotent.console` | `com.tiny.platform.application.controller.idempotent` | Web 管控台 |

### 2.2 目录结构

```
tiny-oauth-server/src/main/java/com/tiny/platform/
├── infrastructure/
│   └── idempotent/
│       ├── core/              # 核心引擎（纯 Java，无框架依赖）
│       │   ├── engine/
│       │   ├── key/
│       │   ├── record/
│       │   ├── repository/    # 抽象接口
│       │   ├── context/
│       │   ├── strategy/
│       │   ├── exception/
│       │   └── mq/
│       ├── sdk/               # 业务使用层
│       │   ├── annotation/
│       │   ├── aspect/
│       │   ├── facade/
│       │   └── resolver/
│       ├── repository/        # 存储实现
│       │   ├── database/
│       │   ├── redis/
│       │   └── memory/
│       └── starter/           # Spring Boot 自动配置
│           ├── autoconfigure/
│           ├── controller/
│           └── properties/
└── application/
    └── controller/
        └── idempotent/        # Web 管控台
            ├── IdempotentConsoleController.java
            └── IdempotentMetricsController.java
```

## 三、合并步骤

### 3.1 文件迁移（已完成）

- ✅ 复制 idempotent-core 到 `platform.infrastructure.idempotent.core`
- ✅ 复制 idempotent-sdk 到 `platform.infrastructure.idempotent.sdk`
- ✅ 复制 idempotent-repository 到 `platform.infrastructure.idempotent.repository`
- ✅ 复制 idempotent-starter 到 `platform.infrastructure.idempotent.starter`
- ✅ 复制 idempotent-console 到 `platform.application.controller.idempotent`

### 3.2 包名更新（进行中）

需要批量更新所有文件的包名和引用：

1. **core 模块**：`com.tiny.idempotent.core.*` → `com.tiny.platform.infrastructure.idempotent.core.*`
2. **sdk 模块**：`com.tiny.idempotent.sdk.*` → `com.tiny.platform.infrastructure.idempotent.sdk.*`
3. **repository 模块**：`com.tiny.idempotent.repository.*` → `com.tiny.platform.infrastructure.idempotent.repository.*`
4. **starter 模块**：`com.tiny.idempotent.starter.*` → `com.tiny.platform.infrastructure.idempotent.starter.*`
5. **console 模块**：`com.tiny.idempotent.console.*` → `com.tiny.platform.application.controller.idempotent.*`

### 3.3 异常处理统一

- 将 `IdempotentException` 改为继承 `BusinessException`
- 使用统一的 `ErrorCode` 体系
- 更新异常处理引用：`com.tiny.platform.common.exception.*` → `com.tiny.platform.infrastructure.core.exception.*`

### 3.4 依赖配置更新

1. **移除外部依赖**：从 `tiny-oauth-server/pom.xml` 移除 `idempotent-starter` 依赖
2. **更新扫描路径**：在 `OauthServerApplication` 中确保扫描 `com.tiny.platform.infrastructure.idempotent` 包
3. **资源文件**：复制 `idempotent-starter/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`

### 3.5 数据库表创建

确保幂等性相关的数据库表已创建（如果需要）：

```sql
CREATE TABLE sys_idempotent_token (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    idempotent_key VARCHAR(512) NOT NULL UNIQUE COMMENT '幂等性 Key',
    state VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING/SUCCESS/FAILED/EXPIRED',
    ttl_seconds BIGINT NOT NULL COMMENT 'TTL（秒）',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    expire_at DATETIME NOT NULL COMMENT '过期时间',
    INDEX idx_key (idempotent_key),
    INDEX idx_expire_at (expire_at)
) COMMENT='幂等性 Token 表';
```

## 四、架构优势

### 4.1 符合 SaaS 平台演进

- **统一的基础设施**：幂等性作为平台级基础设施能力
- **清晰的层次结构**：core → sdk → repository → starter → application
- **易于扩展**：未来可以轻松添加新的存储实现或 MQ 支持

### 4.2 保持模块独立性

- **core 模块**：纯 Java，无框架依赖，可独立测试
- **repository 模块**：存储实现可插拔（Redis/DB/Memory）
- **starter 模块**：Spring Boot 自动配置，按需启用

### 4.3 统一异常处理

- 使用统一的 `ErrorCode` 体系
- 使用统一的 `BusinessException`
- 使用统一的异常处理器

## 五、后续工作

1. ✅ 文件迁移
2. ⏳ 包名更新
3. ⏳ 异常处理统一
4. ⏳ 依赖配置更新
5. ⏳ 测试验证
6. ⏳ 文档更新

## 六、注意事项

1. **保持向后兼容**：确保现有使用 idempotent 的代码不受影响
2. **测试覆盖**：确保所有功能正常工作
3. **文档更新**：更新 README 和使用文档
4. **清理工作**：合并完成后，可以删除 `tiny-idempotent-platform` 模块（如果不再需要）

