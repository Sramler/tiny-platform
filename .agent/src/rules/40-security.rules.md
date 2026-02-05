# 40 安全规范（全局红线）

## 适用范围

- 适用于：全仓库（前端、后端、数据库、配置）
- 不适用于：第三方库内部代码（但使用时应遵循安全最佳实践）

## 总体策略

1. **安全优先**：安全规范优先级最高，任何规则冲突时安全规范优先。
2. **最小权限**：默认拒绝，明确授权。
3. **纵深防御**：多层安全防护，不依赖单一安全措施。

---

## 禁止（Must Not）

### 1) 敏感信息泄漏（A04, A09）

- ❌ 任何形式泄漏敏感信息：密码、密钥、Token/JWT、私钥、连接串。
- ❌ 日志中输出敏感信息：密码、密钥、Token、Cookie、私钥、完整证件号/银行卡号等。
- ❌ 响应中包含敏感信息：密码、密钥、完整堆栈信息、内部路径。
- ❌ 硬编码密钥/密码：必须从配置或密钥管理系统读取。

### 2) 访问控制（A01）

- ❌ 生产环境关闭鉴权、放宽权限、硬编码后门。
- ❌ 跳过权限检查（假设已授权）。
- ❌ 客户端权限验证：权限判断必须在服务端执行。

### 3) 注入攻击（A05）

- ❌ SQL 注入：禁止使用字符串拼接 SQL，必须使用参数化查询。
- ❌ 命令注入：禁止将用户输入直接拼接到命令字符串。
- ❌ LDAP 注入：禁止将用户输入直接拼接到 LDAP 查询。

### 4) 加密与密码（A04, A07）

- ❌ 使用弱加密算法：MD5、SHA-1、DES、RC4 等已废弃算法。
- ❌ 密码明文存储：必须使用强哈希算法（bcrypt、scrypt、Argon2）。
- ❌ 重复使用 IV/nonce：每次加密必须使用随机 IV/nonce。

### 5) 配置安全（A02）

- ❌ 生产环境暴露调试信息：堆栈跟踪、内部端点（如 Actuator）未授权访问。
- ❌ 使用默认/弱密码：必须修改默认密码，使用强密码策略。

### 6) 会话与认证（A07）

- ❌ 会话固定攻击：登录后必须重新生成会话 ID。
- ❌ 长期有效 Token：Access Token 必须设置合理过期时间。

### 7) 反序列化（A08）

- ❌ 不安全反序列化：禁止反序列化不可信数据，使用白名单机制。

---

## 必须（Must）

### 1) 敏感信息保护

- ✅ 涉及鉴权/权限/多租户的变更必须说明：影响面、回滚方案、最小化改动。
- ✅ 输出日志必须脱敏（按项目安全策略）。
- ✅ 密钥管理：使用密钥管理系统（如 Vault、HSM），不硬编码。
- ✅ 密码存储：使用强哈希算法（bcrypt、scrypt、Argon2），包含盐值。

### 2) 访问控制

- ✅ 默认拒绝：所有端点默认需要认证，明确授权的才允许访问。
- ✅ 权限验证：每个业务操作必须验证权限，不依赖前端验证。
- ✅ 多租户隔离：所有业务查询必须包含 `tenant_id` 过滤。

### 3) 输入验证

- ✅ 所有外部输入必须验证：HTTP 参数、Header、JSON、文件内容、MQ 消息。
- ✅ 使用白名单验证：优先使用白名单，避免仅使用黑名单。
- ✅ 参数化查询：SQL 使用 `#{}` 或 `PreparedStatement`，禁止 `${}`。

### 4) 加密与哈希

- ✅ 使用现代加密算法：AES-GCM、RSA-OAEP、SHA-256+。
- ✅ IV/nonce 随机生成：每次加密使用随机且唯一的 IV/nonce。
- ✅ 密钥轮换：定期轮换密钥，安全退役旧密钥。

### 5) 会话与认证

- ✅ 会话安全：使用 `HttpOnly`、`Secure`、`SameSite` Cookie 标志。
- ✅ Token 过期：Access Token 短期（如 1 小时），Refresh Token 长期（如 7 天）。
- ✅ 多因素认证：特权用户必须启用 MFA。

### 6) 日志与监控

- ✅ 安全事件日志：记录登录失败、访问拒绝、Token 问题等安全事件。
- ✅ 日志脱敏：不记录密码、密钥、完整 PII 等敏感信息。
- ✅ 安全告警：异常访问、多次失败登录等触发告警。

### 7) 异常处理

- ✅ 异常信息脱敏：不向用户暴露内部堆栈、路径、技术细节。
- ✅ 安全失败：错误时默认拒绝访问（fail closed）。

---

## 应该（Should）

### 1) 依赖管理

- ⚠️ 依赖更新与高危漏洞需及时处理并记录。
- ⚠️ 使用漏洞扫描工具：OWASP Dependency-Check、Snyk、Nexus IQ。
- ⚠️ 维护软件物料清单（SBOM）：使用 CycloneDX 等标准。

### 2) 配置管理

- ⚠️ 集中配置管理：使用 Spring Profiles、外部配置中心。
- ⚠️ 配置加密：敏感配置加密存储，限制文件权限。
- ⚠️ 定期审计配置：检查开放权限、不必要服务、开放端口。

### 3) 安全设计

- ⚠️ 威胁建模：早期识别威胁，设计缓解措施。
- ⚠️ 安全代码审查：代码审查包含安全检查清单。
- ⚠️ 安全测试：集成 SAST、DAST、IAST 到 CI/CD。

### 4) 文档

- ⚠️ 重要安全决策写入文档（ADR/README/注释均可）。

---

## 可以（May）

- 💡 使用安全框架：Spring Security、Apache Shiro 等成熟框架。
- 💡 安全扫描：集成 SAST/DAST 工具到 CI/CD 流程。
- 💡 密钥轮换自动化：使用密钥管理系统自动轮换。

---

## 例外与裁决

### 开发环境

- 开发环境可适当放宽（如关闭 HTTPS），但必须明确标注且不能提交到生产配置。
- 开发环境调试信息可暴露，但必须使用独立配置。

### 测试数据

- 测试数据可使用弱密码，但必须使用独立的测试数据库。
- 测试环境必须与生产环境隔离。

### 冲突裁决

- 安全规范优先级最高，任何规则冲突时安全规范优先。
- 紧急安全修复可简化流程，但必须事后补全文档和测试。

---

## 示例

### ✅ 正例：日志脱敏

```java
// 日志脱敏
log.info("User login: userId={}, username={}", userId, maskUsername(username));

// 密码验证（不记录密码）
if (passwordEncoder.matches(rawPassword, encodedPassword)) {
    // 登录成功
}

// 敏感信息不输出到响应
public UserDTO toDTO(User user) {
    return UserDTO.builder()
        .id(user.getId())
        .username(user.getUsername())
        // ❌ 不包含 password 字段
        .build();
}
```

### ❌ 反例：日志中泄漏密码、响应中包含敏感信息

```java
// 错误：日志中泄漏密码、响应中包含敏感信息
log.info("User login: username={}, password={}", username, password); // ❌ 泄漏密码

public UserDTO toDTO(User user) {
    return UserDTO.builder()
        .id(user.getId())
        .username(user.getUsername())
        .password(user.getPassword()) // ❌ 响应中包含密码
        .build();
}
```

### ✅ 正例：参数化查询（防止 SQL 注入）

```java
// ✅ 使用参数化查询
@Query("SELECT u FROM User u WHERE u.username = :username AND u.tenantId = :tenantId")
User findByUsernameAndTenantId(@Param("username") String username, @Param("tenantId") Long tenantId);

// ✅ MyBatis 使用 #{} 而非 ${}
@Select("SELECT * FROM user WHERE username = #{username} AND tenant_id = #{tenantId}")
User findByUsernameAndTenantId(@Param("username") String username, @Param("tenantId") Long tenantId);
```

### ❌ 反例：SQL 注入风险

```java
// ❌ 错误：字符串拼接 SQL
String sql = "SELECT * FROM user WHERE username = '" + username + "'";
// ❌ MyBatis 使用 ${} 导致注入风险
@Select("SELECT * FROM user WHERE username = '${username}'")
User findByUsername(String username);
```

### ✅ 正例：密码哈希存储

```java
// ✅ 使用 bcrypt 哈希密码
BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
String hashedPassword = encoder.encode(rawPassword);

// ✅ 验证密码
if (encoder.matches(rawPassword, hashedPassword)) {
    // 密码正确
}
```

### ❌ 反例：弱密码存储

```java
// ❌ 错误：使用弱哈希算法
MessageDigest md = MessageDigest.getInstance("MD5"); // ❌ MD5 已废弃
byte[] hash = md.digest(password.getBytes());

// ❌ 错误：明文存储
user.setPassword(rawPassword); // ❌ 明文存储
```

### ✅ 正例：访问控制（默认拒绝）

```java
@PreAuthorize("hasRole('ADMIN')")
@GetMapping("/admin/users")
public List<User> getUsers() {
    // 只有 ADMIN 角色可以访问
}

// ✅ 多租户隔离
@PreAuthorize("hasPermission(#tenantId, 'TENANT', 'READ')")
public List<User> getUsersByTenant(Long tenantId) {
    // 验证租户权限
    return userRepository.findByTenantId(tenantId);
}
```

### ❌ 反例：跳过权限检查

```java
// ❌ 错误：假设已授权，未验证权限
@GetMapping("/admin/users")
public List<User> getUsers() {
    // ❌ 未验证权限，任何人都可以访问
    return userRepository.findAll();
}
```

### ✅ 正例：异常信息脱敏

```java
@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception e) {
        // ✅ 不暴露内部堆栈
        ErrorResponse response = ErrorResponse.builder()
            .code("INTERNAL_ERROR")
            .message("系统内部错误，请联系管理员")
            // ❌ 不包含 stackTrace
            .build();
        return ResponseEntity.status(500).body(response);
    }
}
```

### ❌ 反例：暴露内部信息

```java
// ❌ 错误：暴露完整堆栈信息
@ExceptionHandler(Exception.class)
public ResponseEntity<Map<String, Object>> handleException(Exception e) {
    Map<String, Object> response = new HashMap<>();
    response.put("error", e.getMessage());
    response.put("stackTrace", e.getStackTrace()); // ❌ 暴露堆栈
    return ResponseEntity.status(500).body(response);
}
```

### ✅ 正例：密钥管理

```java
// ✅ 从配置或密钥管理系统读取
@Value("${app.encryption.key}")
private String encryptionKey;

// ✅ 使用密钥管理系统
@Autowired
private VaultTemplate vaultTemplate;

public String getSecretKey() {
    return vaultTemplate.read("secret/data/app/key").getData().get("key");
}
```

### ❌ 反例：硬编码密钥

```java
// ❌ 错误：硬编码密钥
private static final String ENCRYPTION_KEY = "my-secret-key-12345"; // ❌ 硬编码
```
