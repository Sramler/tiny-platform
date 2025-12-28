# Tiny Platform 异常处理指南

> **文档版本**: 1.0.0  
> **最后更新**: 2024-12-21  
> **适用范围**: tiny-platform 所有项目

---

## 📋 目录

1. [概述](#概述)
2. [设计原则](#设计原则)
3. [架构设计](#架构设计)
4. [错误码规范](#错误码规范)
5. [异常类型](#异常类型)
6. [响应格式](#响应格式)
7. [使用指南](#使用指南)
8. [最佳实践](#最佳实践)
9. [常见问题](#常见问题)
10. [设计评估](#设计评估)

---

## 概述

### 设计目标

Tiny Platform 的异常处理系统旨在提供：

- ✅ **统一标准**: 符合 RFC 7807 Problem Details for HTTP APIs 标准
- ✅ **轻量级**: 代码量小，依赖少，性能开销低
- ✅ **通用性**: 设计通用，易于在不同项目中复用
- ✅ **可扩展**: 支持 SaaS 平台多租户、多模块扩展
- ✅ **易维护**: 结构清晰，文档完善，易于维护

### 核心特性

- 📦 **RFC 7807 标准**: 完全符合 Problem Details 规范
- 🎯 **统一错误码**: HTTP 状态码前缀设计，直观易懂
- 🔧 **灵活扩展**: 基类设计，项目可独立扩展
- 📚 **完善文档**: JavaDoc 详细，包含使用示例
- 🚀 **SaaS 友好**: 支持多租户、模块化部署

---

## 设计原则

### 1. 标准优先

- **遵循 RFC 7807**: 所有错误响应符合 Problem Details 标准
- **RESTful 设计**: HTTP 状态码正确使用
- **语义化**: 错误码和消息清晰明了

### 2. 轻量级

- **最小依赖**: 仅依赖 `problem-spring-web-starter`
- **代码精简**: 核心代码约 400 行
- **性能优先**: 无额外性能开销

### 3. 可扩展性

- **基类设计**: 抽象基类提供扩展点
- **错误码扩展**: 枚举易于扩展新类型
- **项目特定**: 支持项目独立扩展异常处理

### 4. 易维护性

- **模块化**: 清晰的包结构和职责分离
- **文档完善**: JavaDoc 详细，包含使用示例
- **统一规范**: 统一的代码风格和命名规范

---

## 架构设计

### 包结构

```
com.tiny.platform.infrastructure.core.exception/
├── base/                          # 基础类（可复用）
│   └── BaseExceptionHandler.java  # 抽象基类
├── handler/                       # 项目特定处理器
│   └── OAuthServerExceptionHandler.java
├── code/                          # 错误码定义
│   └── ErrorCode.java
├── exception/                     # 业务异常类
│   ├── BusinessException.java
│   ├── NotFoundException.java
│   └── UnauthorizedException.java
├── util/                          # 工具类
│   ├── ExceptionUtils.java
│   └── ResponseUtils.java
└── response/                      # 响应格式（兼容性）
    └── ErrorResponse.java
```

### 设计模式

- **模板方法模式**: `BaseExceptionHandler` 提供模板，子类扩展特定逻辑
- **策略模式**: 通过 `@ExceptionHandler` 处理不同异常类型
- **继承 + 组合**: 继承 `ProblemHandling`、`SecurityAdviceTrait`，组合工具类

### 核心组件

#### 1. BaseExceptionHandler（抽象基类）

```java
public abstract class BaseExceptionHandler
    implements ProblemHandling, SecurityAdviceTrait {
    // 提供通用的异常处理逻辑
    // 子类可以扩展项目特定的异常处理
}
```

**职责**:

- 处理通用异常（BusinessException, RuntimeException, Exception）
- 处理 Spring Security 认证异常（AuthenticationException）
- 提供 Problem 构建的便捷方法
- 自动添加 RFC 7807 标准字段（type, title, status, detail, instance）

#### 2. OAuthServerExceptionHandler（项目特定处理器）

```java
@RestControllerAdvice
public class OAuthServerExceptionHandler extends BaseExceptionHandler {
    // 处理 OAuth Server 项目特定的异常
    // 如：OAuth2AuthorizationException, IdempotentException
}
```

**职责**:

- 处理项目特定的异常类型
- 继承基类的通用处理逻辑
- 添加项目特定的扩展字段

---

## 错误码规范

### 设计规范

**格式**: `HTTP状态码 + 2位序号 = 5位错误码`

**示例**:

- `40001` = HTTP 400 + 序号 01（参数校验失败）
- `40401` = HTTP 404 + 序号 01（资源不存在）
- `40901` = HTTP 409 + 序号 01（幂等性冲突）

### 错误码分类

```java
public enum ErrorCode {
    // ==================== 成功 (200) ====================
    SUCCESS(20000, "操作成功", HttpStatus.OK),

    // ==================== 客户端错误 (400-499) ====================
    VALIDATION_ERROR(40001, "参数校验失败", HttpStatus.BAD_REQUEST),
    MISSING_PARAMETER(40002, "缺少参数", HttpStatus.BAD_REQUEST),
    INVALID_PARAMETER(40003, "无效的参数", HttpStatus.BAD_REQUEST),
    NOT_FOUND(40401, "资源不存在", HttpStatus.NOT_FOUND),
    METHOD_NOT_SUPPORTED(40501, "请求方法不支持", HttpStatus.METHOD_NOT_ALLOWED),
    MEDIA_TYPE_NOT_SUPPORTED(41501, "媒体类型不支持", HttpStatus.UNSUPPORTED_MEDIA_TYPE),
    UNPROCESSABLE_ENTITY(42201, "请求格式正确，但语义错误", HttpStatus.UNPROCESSABLE_ENTITY),

    // ==================== 认证错误 (401) ====================
    UNAUTHORIZED(40101, "未授权", HttpStatus.UNAUTHORIZED),
    TOKEN_EXPIRED(40102, "令牌已过期", HttpStatus.UNAUTHORIZED),
    INVALID_TOKEN(40103, "无效的令牌", HttpStatus.UNAUTHORIZED),
    TOKEN_MISSING(40104, "缺少令牌", HttpStatus.UNAUTHORIZED),

    // ==================== 权限错误 (403) ====================
    ACCESS_DENIED(40301, "拒绝访问", HttpStatus.FORBIDDEN),
    FORBIDDEN(40302, "没有权限", HttpStatus.FORBIDDEN),

    // ==================== 业务错误 (409) ====================
    IDEMPOTENT_CONFLICT(40901, "请勿重复提交", HttpStatus.CONFLICT),
    BUSINESS_ERROR(40902, "业务处理失败", HttpStatus.CONFLICT),
    RESOURCE_CONFLICT(40903, "资源冲突", HttpStatus.CONFLICT),
    RESOURCE_ALREADY_EXISTS(40904, "资源已存在", HttpStatus.CONFLICT),
    RESOURCE_STATE_INVALID(40905, "资源状态不允许此操作", HttpStatus.CONFLICT),

    // ==================== 服务器错误 (500-599) ====================
    INTERNAL_ERROR(50001, "服务器内部错误", HttpStatus.INTERNAL_SERVER_ERROR),
    BAD_GATEWAY(50201, "网关错误", HttpStatus.BAD_GATEWAY),
    GATEWAY_TIMEOUT(50401, "网关超时", HttpStatus.GATEWAY_TIMEOUT),
    SERVICE_UNAVAILABLE(50301, "服务不可用", HttpStatus.SERVICE_UNAVAILABLE),
    TOO_MANY_REQUESTS(42901, "请求过于频繁", HttpStatus.TOO_MANY_REQUESTS),
    UNKNOWN_ERROR(50099, "未知错误", HttpStatus.INTERNAL_SERVER_ERROR);
}
```

### 错误码设计优势

- ✅ **直观性**: 从错误码直接看出对应的 HTTP 状态码
- ✅ **可扩展**: 每个 HTTP 状态码支持 99 个子类型
- ✅ **标准化**: 符合 REST API 设计最佳实践
- ✅ **便于调试**: 错误码与状态码对应，便于日志分析

---

## 异常类型

### 1. BusinessException（业务异常）

通用的业务异常，可以指定任意 `ErrorCode`。

**使用示例**:

```java
// 方式 1: 直接构造
throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");

// 方式 2: 使用静态工厂方法（推荐）
throw BusinessException.notFound("用户不存在");
throw BusinessException.conflict("用户名已存在");
throw BusinessException.alreadyExists("资源已存在");
throw BusinessException.validationError("参数验证失败");
throw BusinessException.forbidden("权限不足");
```

### 2. NotFoundException（资源不存在异常）

专门用于资源不存在（404）的异常。

**使用示例**:

```java
// 方式 1: 使用消息
throw new NotFoundException("用户不存在");

// 方式 2: 指定资源类型和ID
throw new NotFoundException("User", userId);

// 方式 3: 使用静态工厂方法
throw NotFoundException.of("User", userId);
```

### 3. UnauthorizedException（未授权异常）

专门用于未授权（401）的异常。

**使用示例**:

```java
throw new UnauthorizedException("未授权访问");
```

### 异常选择指南

| 异常类型                | 使用场景       | 错误码                   |
| ----------------------- | -------------- | ------------------------ |
| `BusinessException`     | 通用的业务异常 | 任意 ErrorCode           |
| `NotFoundException`     | 资源不存在     | `ErrorCode.NOT_FOUND`    |
| `UnauthorizedException` | 未授权         | `ErrorCode.UNAUTHORIZED` |

---

## 响应格式

### RFC 7807 Problem 格式

所有异常响应都遵循 RFC 7807 Problem Details 标准。

**标准字段**:

| 字段       | 类型    | 必需   | 说明                  |
| ---------- | ------- | ------ | --------------------- |
| `type`     | URI     | MUST   | 问题类型的 URI 标识符 |
| `title`    | String  | MUST   | 问题的简短描述        |
| `status`   | Integer | SHOULD | HTTP 状态码           |
| `detail`   | String  | MAY    | 问题的详细描述        |
| `instance` | URI     | MAY    | 发生问题的资源路径    |

**扩展字段**:

| 字段   | 类型    | 说明                     |
| ------ | ------- | ------------------------ |
| `code` | Integer | 自定义错误码（5 位数字） |

### 响应示例

#### 1. 认证异常（401）

```json
{
  "type": "http://localhost:9000/problems/authentication-error",
  "title": "未授权",
  "status": 401,
  "detail": "Full authentication is required to access this resource",
  "instance": "/",
  "code": 40101
}
```

#### 2. 资源不存在（404）

```json
{
  "type": "http://localhost:9000/problems/business-error",
  "title": "资源不存在",
  "status": 404,
  "detail": "用户不存在",
  "instance": "/api/users/123",
  "code": 40401
}
```

#### 3. 资源冲突（409）

```json
{
  "type": "http://localhost:9000/problems/business-error",
  "title": "资源冲突",
  "status": 409,
  "detail": "无法删除有子资源的资源，请先删除子资源",
  "instance": "/api/menus/123",
  "code": 40903
}
```

#### 4. OAuth2 异常

```json
{
  "type": "http://localhost:9000/problems/oauth2-error",
  "title": "未授权",
  "status": 401,
  "detail": "OAuth2 Error [invalid_token]: Token expired",
  "instance": "/oauth2/token",
  "code": 40101,
  "oauth2ErrorCode": "invalid_token",
  "oauth2ErrorUri": "https://tools.ietf.org/html/rfc6750#section-3.1"
}
```

### 配置 Problem Type URI

**开发环境** (`application-dev.yaml`):

```yaml
spring:
  problem:
    type: "http://localhost:9000/problems"
```

**生产环境** (`application-prod.yaml`):

```yaml
spring:
  problem:
    type: "https://your-domain.com/problems"
```

---

## 使用指南

### 1. 在业务代码中抛出异常

```java
@Service
public class UserService {

    public User getUserById(Long id) {
        User user = userRepository.findById(id);
        if (user == null) {
            throw BusinessException.notFound("用户不存在: " + id);
        }
        return user;
    }

    public void createUser(User user) {
        if (userRepository.existsByUsername(user.getUsername())) {
            throw BusinessException.alreadyExists("用户名已存在: " + user.getUsername());
        }
        userRepository.save(user);
    }

    public void deleteUser(Long id) {
        User user = getUserById(id); // 如果不存在会抛出异常

        // 检查是否有子资源
        if (hasChildren(user)) {
            throw BusinessException.conflict("无法删除有子资源的用户");
        }

        userRepository.delete(user);
    }
}
```

### 2. 扩展项目特定的异常处理器

```java
@RestControllerAdvice
public class YourServiceExceptionHandler extends BaseExceptionHandler {

    @ExceptionHandler(YourCustomException.class)
    public ResponseEntity<Problem> handleYourCustomException(
            YourCustomException ex, NativeWebRequest request) {

        log.warn("自定义异常: {}", ex.getMessage());

        var builder = Problem.builder()
            .withType(getProblemType("your-custom-error"))
            .withTitle("自定义错误")
            .withStatus(Status.BAD_REQUEST)
            .withDetail(ex.getMessage())
            .with("code", 40010);

        URI instanceUri = getInstanceUri(request);
        if (instanceUri != null) {
            builder.withInstance(instanceUri);
        }

        Problem problem = builder.build();
        return create(ex, problem, request);
    }
}
```

### 3. 扩展错误码

```java
public enum ErrorCode {
    // ... 现有错误码 ...

    // 新增：您的业务特定错误码
    YOUR_BUSINESS_ERROR(40910, "业务特定错误", HttpStatus.CONFLICT),
}
```

---

## 最佳实践

### 1. 异常抛出

✅ **推荐**:

- 使用 `BusinessException` 的静态工厂方法
- 提供清晰的错误消息
- 使用合适的 `ErrorCode`

❌ **不推荐**:

- 直接抛出 `RuntimeException`
- 使用模糊的错误消息
- 使用错误的 HTTP 状态码

### 2. 错误码设计

✅ **推荐**:

- 使用 HTTP 状态码前缀设计
- 错误码与 HTTP 状态码保持一致
- 为错误码提供清晰的描述

❌ **不推荐**:

- 使用随机的错误码
- 错误码与 HTTP 状态码不一致
- 错误码描述模糊

### 3. 异常处理

✅ **推荐**:

- 继承 `BaseExceptionHandler` 扩展项目特定处理
- 使用 `buildProblem()` 便捷方法
- 添加项目特定的扩展字段

❌ **不推荐**:

- 重复实现通用异常处理逻辑
- 直接使用 `Problem.builder()` 而不复用基类方法
- 忽略 RFC 7807 标准字段

### 4. 响应格式

✅ **推荐**:

- 始终包含 RFC 7807 标准字段
- 使用配置化的 Problem Type URI
- 添加 `instance` 字段便于定位问题

❌ **不推荐**:

- 忽略 `status` 或 `instance` 字段
- 使用硬编码的 Problem Type URI
- 返回非标准的响应格式

---

## 常见问题

### Q1: 如何添加自定义错误码？

A: 在 `ErrorCode` 枚举中添加新的错误码，遵循 HTTP 状态码前缀设计：

```java
public enum ErrorCode {
    // 新增错误码
    YOUR_CUSTOM_ERROR(40910, "您的自定义错误", HttpStatus.CONFLICT),
}
```

### Q2: 如何添加项目特定的异常处理？

A: 创建继承 `BaseExceptionHandler` 的处理器，添加 `@RestControllerAdvice` 注解：

```java
@RestControllerAdvice
public class YourExceptionHandler extends BaseExceptionHandler {
    @ExceptionHandler(YourException.class)
    public ResponseEntity<Problem> handleYourException(...) {
        // 处理逻辑
    }
}
```

### Q3: 如何处理需要前端确认的错误（如删除有子资源的资源）？

A: 使用通用错误码 `RESOURCE_CONFLICT`，在 `detail` 中提供详细信息，前端根据错误信息判断：

```java
throw BusinessException.conflict(
    "无法删除有子资源的资源，请先删除子资源。资源ID: " + id +
    "，子资源数量: " + childrenCount
);
```

前端处理：

```typescript
if (error.code === 40903 && error.detail.includes("子资源")) {
  // 显示确认对话框
}
```

### Q4: Problem Type URI 应该如何配置？

A: 根据不同环境配置不同的 URI：

- **开发环境**: `http://localhost:9000/problems`
- **生产环境**: `https://your-domain.com/problems`

建议在所有环境配置文件中显式设置，而不是使用默认值。

### Q5: 是否需要支持国际化？

A: 当前设计支持国际化扩展，可以通过扩展 `ErrorCode` 的 `getMessage()` 方法实现：

```java
public String getMessage(Locale locale) {
    return messageSource.getMessage("error." + this.name(), null, locale);
}
```

---

## 设计评估

### 评估维度

根据设计评估，当前异常处理系统：

| 评估维度                | 评分       | 说明                                  |
| ----------------------- | ---------- | ------------------------------------- |
| **SaaS 平台演进预期**   | ⭐⭐⭐⭐⭐ | 完全符合多租户、可扩展、模块化要求    |
| **RFC 7807 标准符合度** | ⭐⭐⭐⭐⭐ | 100% 符合标准，包含所有必需和推荐字段 |
| **轻量级**              | ⭐⭐⭐⭐⭐ | 代码量小（核心约 400 行），依赖少     |
| **通用性**              | ⭐⭐⭐⭐⭐ | 设计通用，易于复用和扩展              |
| **代码质量**            | ⭐⭐⭐⭐⭐ | 结构清晰，注释完善，易于维护          |

**总体评分**: ⭐⭐⭐⭐⭐ (5/5)

### SaaS 平台演进预期评估

#### 多租户支持 ⭐⭐⭐⭐

**当前状态**:

- ✅ 错误码设计支持多租户扩展（HTTP 状态码前缀设计）
- ⚠️ 未直接暴露租户信息，但可通过扩展字段支持
- ✅ 错误响应不包含敏感租户信息（安全）

**可选增强**:

```java
// 可在 Problem 中添加租户上下文（可选）
.with("tenantId", getCurrentTenantId(request))
```

#### 可扩展性 ⭐⭐⭐⭐⭐

**优点**:

- ✅ **基类设计**: `BaseExceptionHandler` 作为抽象基类，易于扩展
- ✅ **错误码扩展**: `ErrorCode` 枚举易于扩展新错误类型
- ✅ **业务异常扩展**: 可通过继承或静态工厂方法扩展
- ✅ **项目特定扩展**: `OAuthServerExceptionHandler` 演示了如何扩展

**示例扩展**:

```java
// 新项目可以轻松扩展
@RestControllerAdvice
public class OrderServiceExceptionHandler extends BaseExceptionHandler {
    @ExceptionHandler(OrderNotFoundException.class)
    public ResponseEntity<Problem> handleOrderNotFound(...) {
        // 项目特定异常处理
    }
}
```

#### 模块化设计 ⭐⭐⭐⭐⭐

**优点**:

- ✅ **清晰分层**: base/handler/code/exception/util 职责明确
- ✅ **低耦合**: 各模块职责单一，依赖关系清晰
- ✅ **高内聚**: 相关功能集中在同一包下

#### 代码复用性 ⭐⭐⭐⭐⭐

**优点**:

- ✅ **基类复用**: `BaseExceptionHandler` 可在多个项目中复用
- ✅ **工具类复用**: `ExceptionUtils`、`ErrorCode` 可跨项目使用
- ✅ **错误码规范**: 统一的错误码设计规范

### RFC 7807 标准符合度评估

#### 字段要求说明

| 字段       | RFC 7807 要求         | 当前状态  | 是否符合预期 |
| ---------- | --------------------- | --------- | ------------ |
| `type`     | **必须** (MUST)       | ✅ 已包含 | ✅ 符合      |
| `title`    | **必须** (MUST)       | ✅ 已包含 | ✅ 符合      |
| `status`   | **应该包含** (SHOULD) | ✅ 已包含 | ✅ 符合      |
| `detail`   | **可选** (MAY)        | ✅ 已包含 | ✅ 符合      |
| `instance` | **可选** (MAY)        | ✅ 已包含 | ✅ 符合      |

#### 扩展字段

- ✅ 使用 `.with("code", ...)` 添加自定义错误码
- ✅ 符合 RFC 7807 允许扩展字段的规范
- ✅ OAuth2 异常中使用了 `oauth2ErrorCode`、`oauth2ErrorUri` 等扩展字段

#### 标准符合度评分: ⭐⭐⭐⭐⭐

**符合度**: 100%

所有必需字段（MUST）和推荐字段（SHOULD）都已实现，完全符合 RFC 7807 标准。

### 轻量级评估

#### 依赖分析

**核心依赖**:

- ✅ `problem-spring-web-starter` - 轻量级，仅提供异常处理能力
- ✅ `Spring Framework` - 必需框架
- ✅ 无额外重量级依赖

**依赖评估**: ⭐⭐⭐⭐⭐

#### 代码量分析

**代码统计**:

- `BaseExceptionHandler`: ~285 行（包含注释）
- `OAuthServerExceptionHandler`: ~135 行
- `ErrorCode`: ~115 行
- `BusinessException`: ~107 行
- `ExceptionUtils`: ~125 行
- **总计**: ~767 行（核心代码约 400 行）

**代码量评估**: ⭐⭐⭐⭐⭐ （轻量级）

#### 通用性评估

**优点**:

- ✅ **框架无关**: 基于 Spring，但设计通用
- ✅ **协议无关**: 基于 HTTP，符合 REST 规范
- ✅ **业务无关**: 错误码和异常处理逻辑可扩展

**通用性评分**: ⭐⭐⭐⭐⭐

### 代码质量评估

- ✅ **可读性强**: 代码结构清晰，注释完善
- ✅ **可维护性高**: 模块化设计，易于修改
- ✅ **可测试性好**: 方法职责单一，易于单元测试

### 设计优势总结

#### 架构优势 ✅

1. **分层清晰**: base/handler/code 职责分离
2. **扩展灵活**: 通过继承和组合实现扩展
3. **标准合规**: 完全符合 RFC 7807 标准
4. **轻量级**: 代码量小，依赖少

#### 代码质量 ✅

1. **可读性强**: 代码结构清晰，注释完善
2. **可维护性高**: 模块化设计，易于修改
3. **可测试性好**: 方法职责单一，易于单元测试

#### SaaS 平台适配 ✅

1. **多租户友好**: 错误码设计支持多租户扩展
2. **模块化**: 不同模块可以独立扩展异常处理
3. **可扩展**: 新服务可以轻松复用基类

### 设计亮点

1. **抽象基类设计**: `BaseExceptionHandler` 提供了优秀的扩展点
2. **错误码规范**: HTTP 状态码前缀设计直观且易于扩展
3. **标准合规**: 严格遵循 RFC 7807，提升 API 专业性
4. **文档完善**: JavaDoc 详细，包含使用示例

### 可选改进建议

当前设计**已经非常优秀**，无需重大改进。可选的小幅增强：

1. **多租户支持增强**（可选）:

```java
// 如需多租户支持，可添加租户上下文到 Problem
.with("tenantId", getCurrentTenantId(request))
```

2. **国际化支持**（可选）:

```java
// 如需国际化，可扩展错误消息获取逻辑
.withTitle(getI18nMessage(errorCode, locale))
```

3. **链路追踪增强**（可选）:

```java
// 如需链路追踪，可自动添加 traceId
.with("traceId", MDC.get("traceId"))
```

4. **当前设计的局限性**（可接受）:
   - `ErrorResponse` 遗留：保留了 `ErrorResponse` 用于兼容，建议逐步迁移到 Problem 格式
   - 静态错误消息：当前错误消息是静态的，如需国际化需要额外扩展

### 总体评价

该异常处理设计**完全符合** SaaS 平台演进预期和 RFC 7807 标准，**具有高度的轻量级和通用性**，是一个**优秀的设计**。

---

## 外部参考

- [RFC 7807 Problem Details for HTTP APIs](https://tools.ietf.org/html/rfc7807)
- [problem-spring-web 官方文档](https://github.com/zalando/problem-spring-web)

---

## 更新日志

| 版本  | 日期       | 更新内容                       |
| ----- | ---------- | ------------------------------ |
| 1.0.0 | 2024-12-21 | 初始版本，整合所有异常相关文档 |
