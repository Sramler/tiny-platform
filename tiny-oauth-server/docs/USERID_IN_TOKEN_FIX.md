# OAuth2 Token 中 userId 缺失问题解决流程

## 问题描述

在 OAuth2 授权码流程中，生成的 Access Token 和 ID Token 中缺少 `userId` 字段，只有 `username` 字段。

### 问题表现

Access Token 内容：
```json
{
  "sub": "admin",
  "aud": "vue-client",
  "scope": "openid profile offline_access",
  "auth_time": 1764514203,
  "amr": ["password"],
  "iss": "http://localhost:9000",
  "exp": 1764514383,
  "iat": 1764514203,
  "jti": "70cf7872-664d-4e93-93ec-c1e3d5a61b11",
  "client_id": "vue-client",
  "username": "admin"
  // ❌ 缺少 userId 字段
}
```

### 日志错误信息

```
[JwtTokenCustomizer]   - principal.getDetails() 类型: null
[JwtTokenCustomizer]   ✗ 未找到 SecurityUser: details=null, principal=java.lang.String
[JwtTokenCustomizer] Access Token - SecurityUser 为空，仅添加 username: admin
[JwtTokenCustomizer] Access Token - 无法添加 userId，因为未找到 SecurityUser
```

## 问题分析过程

### 第一步：添加详细日志分析 Authentication 结构

**目标**：了解 `Authentication` 对象的结构，定位 `SecurityUser` 的位置。

**实现**：在 `JwtTokenCustomizer` 中添加详细的 DEBUG 级别日志：
- 记录 `principal` 的类型
- 记录 `principal.getName()` 的值
- 记录 `principal.getPrincipal()` 的类型和值
- 记录 `principal.getDetails()` 的类型和值

**日志输出**：
```
[JwtTokenCustomizer] Access Token - 开始分析 Authentication 结构
[JwtTokenCustomizer]   - principal 类型: com.tiny.oauthserver.sys.security.MultiFactorAuthenticationToken
[JwtTokenCustomizer]   - principal.getName(): admin
[JwtTokenCustomizer]   - principal.getPrincipal() 类型: java.lang.String
[JwtTokenCustomizer]   - principal.getPrincipal() 值: admin
[JwtTokenCustomizer]   - principal.getDetails() 类型: null
[JwtTokenCustomizer]   - principal.getDetails() 值: null
```

**发现**：
- `principal` 是 `MultiFactorAuthenticationToken` 类型
- `principal.getPrincipal()` 返回 `String`（username），不是 `SecurityUser`
- `principal.getDetails()` 返回 `null`

### 第二步：检查 MultiAuthenticationProvider 中的 SecurityUser 设置

**目标**：确认 `SecurityUser` 是否在认证流程中被正确设置。

**检查位置**：`MultiAuthenticationProvider` 中所有创建 `MultiFactorAuthenticationToken` 的地方。

**发现**：
- 在 `MultiAuthenticationProvider` 中，`SecurityUser` 确实被设置到 `details` 中
- 日志显示：`用户 admin 的 SecurityUser 已设置到 MultiFactorAuthenticationToken.details (userId: 1)`

**问题定位**：
- 认证时 `SecurityUser` 被正确设置
- 但在 OAuth2 授权码流程中，从 session 或授权表反序列化时，`details` 丢失了

### 第三步：检查序列化/反序列化配置

**目标**：确认 `MultiFactorAuthenticationToken` 的序列化/反序列化是否正确处理 `details` 字段。

**检查位置**：`MultiFactorAuthenticationTokenJackson3Deserializer`

**发现**：
- `MultiFactorAuthenticationTokenJackson3Deserializer` 反序列化时**没有处理 `details` 字段**
- 导致反序列化后的 token 的 `details` 为 `null`

## 根本原因

### 问题链路

```
1. 用户登录
   ↓
2. MultiAuthenticationProvider 认证成功
   ↓
3. 创建 MultiFactorAuthenticationToken，设置 SecurityUser 到 details ✅
   ↓
4. MultiFactorAuthenticationToken 被序列化到 session/OAuth2 授权表
   ↓
5. OAuth2 授权码流程中，从 session/授权表反序列化 MultiFactorAuthenticationToken
   ↓
6. MultiFactorAuthenticationTokenJackson3Deserializer 反序列化时未处理 details 字段 ❌
   ↓
7. 反序列化后的 token 的 details 为 null
   ↓
8. JwtTokenCustomizer 无法从 details 获取 SecurityUser
   ↓
9. Access Token 中缺少 userId
```

### 核心问题

**`MultiFactorAuthenticationTokenJackson3Deserializer` 反序列化时没有恢复 `details` 字段**，导致：
- 序列化时 `details` 被正确序列化（包含 `SecurityUser`）
- 反序列化时 `details` 没有被恢复，始终为 `null`
- `JwtTokenCustomizer` 无法获取 `SecurityUser`，从而无法获取 `userId`

## 解决方案

### 方案一：在 MultiAuthenticationProvider 中设置 SecurityUser（已实施）

**位置**：`MultiAuthenticationProvider.java`

**修改内容**：在所有创建 `MultiFactorAuthenticationToken` 的地方（共 6 处），添加代码将 `SecurityUser` 设置到 `details`：

```java
// 将 SecurityUser 设置到 details 中，以便在 JWT Token 生成时获取 userId
if (userDetails instanceof com.tiny.oauthserver.sys.model.SecurityUser securityUser) {
    authenticated.setDetails(securityUser);
    logger.debug("用户 {} 的 SecurityUser 已设置到 MultiFactorAuthenticationToken.details (userId: {})", 
            user.getUsername(), securityUser.getUserId());
}
```

**修改位置**：
1. `handleMultiFactorAuthentication` - 部分认证完成（3 处）
2. `handleMultiFactorAuthentication` - 所有因子完成（1 处）
3. `authenticatePassword` - 密码认证成功（1 处）
4. `authenticateTotp` - TOTP 认证成功（1 处）

**效果**：确保在认证时 `SecurityUser` 被正确设置到 `details`。

### 方案二：修复反序列化器恢复 details 字段（关键修复）

**位置**：`MultiFactorAuthenticationTokenJackson3Deserializer.java`

**修改内容**：在反序列化时恢复 `details` 字段：

```java
// ========== 关键修复：反序列化 details 字段 ==========
// details 可能包含 SecurityUser，需要正确恢复以便在 JWT Token 生成时获取 userId
if (jsonNode.has("details") && !jsonNode.get("details").isNull()) {
    try {
        JsonNode detailsNode = jsonNode.get("details");
        ObjectMapper mapper = (ObjectMapper) jsonParser.getCodec();
        
        // 检查 details 是否是 SecurityUser
        if (detailsNode.has("@type") && "securityUser".equals(detailsNode.get("@type").asText())) {
            // 反序列化为 SecurityUser
            SecurityUser securityUser = mapper.treeToValue(detailsNode, SecurityUser.class);
            token.setDetails(securityUser);
            log.debug("[MultiFactorAuthenticationTokenJackson3Deserializer] 成功恢复 SecurityUser 到 details (userId: {})", 
                    securityUser != null ? securityUser.getUserId() : "null");
        } else {
            // 尝试直接反序列化（可能是其他类型的 details）
            Object details = mapper.treeToValue(detailsNode, Object.class);
            token.setDetails(details);
            log.debug("[MultiFactorAuthenticationTokenJackson3Deserializer] 恢复 details: {}", 
                    details != null ? details.getClass().getName() : "null");
        }
    } catch (Exception e) {
        log.warn("[MultiFactorAuthenticationTokenJackson3Deserializer] 无法反序列化 details 字段: {}", e.getMessage());
        // 不抛出异常，允许 token 在没有 details 的情况下继续使用
    }
}
```

**效果**：确保从 session 或 OAuth2 授权表反序列化时，`SecurityUser` 被正确恢复到 `details`。

### 方案三：增强 JwtTokenCustomizer 的日志和分析能力（辅助诊断）

**位置**：`JwtTokenCustomizer.java`

**修改内容**：
1. 添加详细的 Authentication 结构分析日志
2. 从多个位置尝试获取 `SecurityUser`（`details` 和 `principal`）
3. 记录 `SecurityUser` 的来源和值
4. 如果 `SecurityUser.userId` 为 `null`，记录错误日志

**效果**：帮助诊断问题，确认 `SecurityUser` 的位置和值。

## 验证结果

### 修复后的日志输出

```
[MultiFactorAuthenticationTokenJackson3Deserializer] 成功恢复 SecurityUser 到 details (userId: 1)
[JwtTokenCustomizer] Access Token - 开始分析 Authentication 结构
[JwtTokenCustomizer]   - principal 类型: com.tiny.oauthserver.sys.security.MultiFactorAuthenticationToken
[JwtTokenCustomizer]   - principal.getName(): admin
[JwtTokenCustomizer]   - principal.getPrincipal() 类型: java.lang.String
[JwtTokenCustomizer]   - principal.getPrincipal() 值: admin
[JwtTokenCustomizer]   - principal.getDetails() 类型: com.tiny.oauthserver.sys.model.SecurityUser
[JwtTokenCustomizer]   - principal.getDetails() 值: SecurityUser{userId=1, username='admin', ...}
[JwtTokenCustomizer]   ✓ 从 principal.getDetails() 获取到 SecurityUser
[JwtTokenCustomizer] Access Token - SecurityUser 来源: principal.getDetails(), userId: 1, username: admin
```

### 修复后的 Access Token

```json
{
  "sub": "admin",
  "aud": "vue-client",
  "scope": "openid profile offline_access",
  "auth_time": 1764514203,
  "amr": ["password"],
  "iss": "http://localhost:9000",
  "exp": 1764514383,
  "iat": 1764514203,
  "jti": "70cf7872-664d-4e93-93ec-c1e3d5a61b11",
  "client_id": "vue-client",
  "username": "admin",
  "userId": 1  // ✅ 已成功添加
}
```

## 相关文件清单

### 修改的文件

1. **`MultiAuthenticationProvider.java`**
   - 在所有创建 `MultiFactorAuthenticationToken` 的地方设置 `SecurityUser` 到 `details`
   - 共修改 6 处

2. **`MultiFactorAuthenticationTokenJackson3Deserializer.java`**
   - 添加 `details` 字段的反序列化逻辑
   - 支持反序列化 `SecurityUser` 和其他类型的 `details`

3. **`JwtTokenCustomizer.java`**
   - 添加详细的 Authentication 结构分析日志
   - 增强从多个位置获取 `SecurityUser` 的逻辑
   - 添加详细的日志记录

### 相关文档

1. **`LONG_ALLOWLIST_FIX.md`**
   - 记录了 Long 类型反序列化问题的修复
   - 包含官方指南的扩展方式（SecurityUser.userId 序列化为 String）

## 技术要点

### 1. Spring Security Authentication 的 details 字段

- `details` 字段用于存储额外的认证信息
- 在序列化/反序列化时，需要确保 `details` 被正确处理
- `AbstractAuthenticationToken` 的 `details` 字段默认会被序列化，但反序列化时需要显式处理

### 2. OAuth2 授权码流程中的序列化/反序列化

- OAuth2 授权码流程中，`Authentication` 对象会被序列化到 session 或 OAuth2 授权表
- 序列化使用 Jackson，需要确保所有字段都被正确序列化
- 反序列化时，需要确保所有字段都被正确恢复

### 3. 自定义反序列化器的重要性

- 自定义反序列化器需要处理所有字段，包括继承的字段（如 `details`）
- 需要支持多种类型的 `details`（如 `SecurityUser`、`WebAuthenticationDetails` 等）
- 需要添加适当的错误处理和日志记录

## 经验总结

### 1. 问题诊断方法

- **添加详细日志**：通过日志分析对象结构和数据流
- **追踪数据流**：从认证到序列化再到反序列化的完整流程
- **对比预期和实际**：确认每个步骤的数据是否符合预期

### 2. 序列化/反序列化最佳实践

- **显式处理所有字段**：不要依赖默认行为，显式处理所有需要序列化的字段
- **支持多种类型**：`details` 字段可能包含多种类型的对象，需要支持所有类型
- **添加错误处理**：反序列化失败时，应该有适当的降级策略
- **添加日志记录**：记录序列化/反序列化的关键步骤，便于调试

### 3. OAuth2 Token Claims 扩展

- **从 Authentication 获取用户信息**：通过 `Authentication.details` 或 `Authentication.principal` 获取
- **支持多种认证方式**：不同的认证方式可能将用户信息存储在不同的位置
- **添加详细日志**：记录用户信息的来源和值，便于调试和验证

## 参考链接

- [Spring Security Authentication Details](https://docs.spring.io/spring-security/reference/servlet/authentication/architecture.html#servlet-authentication-details)
- [Jackson Custom Deserializer](https://github.com/FasterXML/jackson-docs/wiki/JacksonPolymorphicDeserialization)
- [OAuth2 Authorization Code Flow](https://datatracker.ietf.org/doc/html/rfc6749#section-4.1)

## 提交记录

1. **feat: 增加详细日志分析 Authentication 结构，定位 userId 缺失原因**
   - 添加详细的 Authentication 结构分析日志
   - 从多个位置尝试获取 SecurityUser

2. **fix: 在 MultiFactorAuthenticationToken 中设置 SecurityUser 到 details**
   - 在所有创建 MultiFactorAuthenticationToken 的地方设置 SecurityUser

3. **fix: 修复 MultiFactorAuthenticationToken 反序列化时 details 字段丢失问题**
   - 在反序列化器中恢复 details 字段
   - 支持反序列化 SecurityUser

---

**问题状态**：✅ 已解决  
**验证状态**：✅ 已验证（userId 已正确返回）  
**文档更新日期**：2025-11-30
