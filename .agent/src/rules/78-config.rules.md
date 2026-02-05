# 78 配置管理规范

## 适用范围

- 适用于：`**/application*.yaml`、`**/application*.yml`、`**/.env*`、配置文件相关代码
- 不适用于：第三方库配置文件（但使用时应遵循配置管理原则）

## 总体策略

1. **环境隔离**：不同环境使用不同的配置文件，避免配置混用。
2. **敏感信息保护**：敏感配置（密码、密钥）不硬编码，使用环境变量或配置中心。
3. **配置优先级**：明确配置加载优先级，支持环境变量覆盖。

---

## 禁止（Must Not）

### 1) 敏感信息

- ❌ 配置文件中硬编码敏感信息：密码、密钥、Token、私钥、连接串。
- ❌ 将敏感配置提交到版本控制（应使用 `.env.local` 或配置中心）。
- ❌ 生产配置提交到代码仓库（应使用配置中心或环境变量）。

### 2) 配置管理

- ❌ 不同环境混用同一配置文件（应使用 `application-{profile}.yaml`）。
- ❌ 配置文件中使用绝对路径（应使用相对路径或环境变量）。

### 3) 配置格式

- ❌ 配置文件格式不规范（应使用 YAML 或 Properties，保持格式一致）。
- ❌ 配置项命名不规范（应使用 kebab-case 或 camelCase，保持一致性）。

---

## 必须（Must）

### 1) 配置文件组织

- ✅ 环境隔离：使用 `application-{profile}.yaml` 区分不同环境（dev、test、prod）。
- ✅ 默认配置：`application.yaml` 包含所有环境的通用配置。
- ✅ 环境特定配置：`application-{profile}.yaml` 包含环境特定配置，覆盖默认配置。

### 2) 敏感信息管理

- ✅ 敏感配置：使用环境变量或配置中心管理敏感信息。
- ✅ 配置模板：提供 `application.example.yaml` 或 `.env.example` 作为配置模板。
- ✅ 配置验证：启动时验证必需配置项，缺失时给出明确错误提示。

### 3) 配置优先级

- ✅ 配置优先级：系统属性 > 环境变量 > `application-{profile}.yaml` > `application.yaml`。
- ✅ 环境变量覆盖：支持通过环境变量覆盖配置文件中的值。

### 4) 前端配置

- ✅ 前端环境变量：使用 `.env`、`.env.development`、`.env.production` 管理环境配置。
- ✅ 环境变量前缀：前端环境变量必须以 `VITE_` 开头（Vite 要求）。

---

## 应该（Should）

### 1) 配置分组

- ⚠️ 配置分组：按功能分组（数据库、Redis、OAuth2、日志等），便于管理。
- ⚠️ 配置注释：关键配置项添加注释说明用途和可选值。

### 2) 配置验证

- ⚠️ 配置验证：使用 `@ConfigurationProperties` 和 Bean Validation 验证配置。
- ⚠️ 配置默认值：为配置项提供合理的默认值，避免配置缺失导致错误。

### 3) 配置中心

- ⚠️ 配置中心：生产环境建议使用配置中心（如 Nacos、Apollo）管理配置。
- ⚠️ 配置加密：敏感配置在配置中心中加密存储。

### 4) 配置文档

- ⚠️ 配置文档：关键配置项应有文档说明，包括默认值、可选值、影响范围。

---

## 可以（May）

- 💡 使用 Spring Cloud Config：统一管理分布式配置。
- 💡 配置热更新：支持配置热更新，无需重启应用。

---

## 例外与裁决

### 开发环境

- 开发环境：开发环境可使用 `.env.local` 存储本地配置，但不应提交到版本控制。

### 第三方库

- 第三方库：第三方库配置遵循其规范，不强制修改。

### 冲突裁决

- 安全规范（40-security）优先于本规范（敏感信息保护）。
- 平台特定规则（90+）优先于本规范。

---

## 示例

### ✅ 正例：后端配置文件组织

```yaml
# application.yaml（通用配置）
spring:
  application:
    name: oauth-server
  profiles:
    active: ${SPRING_PROFILES_ACTIVE:dev}

logging:
  level:
    root: INFO
    com.tiny: INFO

---
# application-dev.yaml（开发环境）
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/tiny_platform?useSSL=false
    username: ${DB_USERNAME:root}
    password: ${DB_PASSWORD:password}

logging:
  level:
    com.tiny: DEBUG

---
# application-prod.yaml（生产环境）
spring:
  datasource:
    url: ${DB_URL}
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}  # ✅ 从环境变量读取

logging:
  level:
    root: WARN
    com.tiny: INFO
```

### ❌ 反例：硬编码敏感信息

```yaml
# ❌ 错误：硬编码密码
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/tiny_platform
    username: root
    password: mypassword123  # ❌ 硬编码密码
```

### ✅ 正例：前端环境变量配置

```bash
# .env（通用配置）
VITE_ENABLE_CONSOLE_WARN=true
VITE_ENABLE_CONSOLE_ERROR=true

# .env.development（开发环境）
VITE_APP_ENV=dev
VITE_API_BASE_URL=http://localhost:9000
VITE_ENABLE_CONSOLE_DEBUG=true
VITE_LOG_LEVEL=debug

# .env.production（生产环境）
VITE_APP_ENV=prod
VITE_API_BASE_URL=https://api.example.com
VITE_ENABLE_CONSOLE_DEBUG=false
VITE_LOG_LEVEL=error
```

### ✅ 正例：配置验证（@ConfigurationProperties）

```java
@ConfigurationProperties(prefix = "app.oauth2")
@Validated
public class OAuth2Properties {
    @NotBlank
    private String clientId;
    
    @NotBlank
    private String clientSecret;
    
    @NotEmpty
    private List<String> redirectUris;
    
    @Min(3600)
    @Max(86400)
    private int accessTokenValiditySeconds = 3600;
    
    // getters and setters
}
```

### ✅ 正例：环境变量覆盖

```bash
# 启动时通过环境变量覆盖配置
export DB_USERNAME=admin
export DB_PASSWORD=secret123
export SPRING_PROFILES_ACTIVE=prod
java -jar app.jar
```

### ✅ 正例：配置模板（.env.example）

```bash
# .env.example（配置模板）
# 复制为 .env.local 后修改

# API 配置
VITE_API_BASE_URL=http://localhost:9000

# OIDC 配置
VITE_OIDC_AUTHORITY=http://localhost:9000
VITE_OIDC_CLIENT_ID=web-frontend
VITE_OIDC_REDIRECT_URI=http://localhost:5173/callback

# 日志配置
VITE_ENABLE_CONSOLE_LOG=true
VITE_LOG_LEVEL=debug
```

### ✅ 正例：配置分组

```yaml
# application.yaml
spring:
  application:
    name: oauth-server

# 数据库配置
datasource:
  driver-class-name: com.mysql.cj.jdbc.Driver
  url: ${DB_URL:jdbc:mysql://localhost:3306/tiny_platform}
  username: ${DB_USERNAME:root}
  password: ${DB_PASSWORD:password}

# Redis 配置
redis:
  host: ${REDIS_HOST:localhost}
  port: ${REDIS_PORT:6379}
  password: ${REDIS_PASSWORD:}

# OAuth2 配置
oauth2:
  client:
    registration:
      web-frontend:
        client-id: ${OAUTH2_CLIENT_ID:web-frontend}
        client-secret: ${OAUTH2_CLIENT_SECRET:secret}
        redirect-uris: ${OAUTH2_REDIRECT_URIS:http://localhost:5173/callback}

# 日志配置
logging:
  level:
    root: INFO
    com.tiny: INFO
```

### ✅ 正例：配置中心使用

```java
@RefreshScope
@ConfigurationProperties(prefix = "app.config")
public class AppConfig {
    private String apiKey;
    private String secretKey;
    
    // getters and setters
}
```

### ❌ 反例：配置混用

```yaml
# ❌ 错误：开发和生产配置混在一起
spring:
  datasource:
    # 开发环境
    url: jdbc:mysql://localhost:3306/tiny_platform
    # 生产环境
    # url: jdbc:mysql://prod-db:3306/tiny_platform
```
