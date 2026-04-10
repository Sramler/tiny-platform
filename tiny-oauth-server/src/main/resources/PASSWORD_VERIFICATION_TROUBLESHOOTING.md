# tiny-oauth-server 密码验证失败问题排查指南

## 问题描述

用户 `user_0001` 在 `tiny-oauth-server` 项目中验证密码失败。

## 已修复的问题

### 1. 密码键名优先级不一致

- **问题**：`MultiAuthenticationProvider.authenticatePassword` 方法优先查找 `passwordHash`，最后才查找 `password`，但更新脚本使用的是 `password` 键。
- **修复**：修改为优先查找 `password` 键（与 `tiny_web` 保持一致），同时支持向后兼容（`passwordHash`, `password_hash`, `encodedPassword`, `hash`）。

### 2. 缺少密码哈希规范化

- **问题**：`authenticatePassword` 方法没有对密码哈希进行规范化处理（添加 `{bcrypt}` 前缀），导致 `DelegatingPasswordEncoder` 无法正确识别密码格式。
- **修复**：添加了 `normalizePasswordHash` 方法，自动为缺少前缀的 BCrypt 密码添加 `{bcrypt}` 前缀。

### 3. 调试日志不足

- **问题**：缺少详细的调试日志，难以定位问题。
- **修复**：添加了详细的调试日志，包括：
  - 认证配置键和内容
  - 密码读取过程
  - 密码哈希规范化过程
  - 密码验证结果

### 4. 平台登录（无 tenantCode）误用硬编码 default 租户查认证方式

- **问题**：`MultiAuthenticationProvider` 在 PLATFORM 作用域下曾用 `tenant.code=default` 解析 `user_authentication_method.tenant_id`，与 `tiny.platform.tenant.platform-tenant-code`、`ensure-platform-admin.sh` 的 `PLATFORM_TENANT_CODE` 不一致时，会查错行或查不到行，表现为「密码错误」或「未配置认证方式」。
- **修复**：PLATFORM 登录改为使用 `PlatformTenantResolver.getPlatformTenantId()`（与配置一致）；仅在解析失败时回退 `default` 并打 WARN 日志（见 `[auth-login] PLATFORM 登录`）。

## 排查步骤

### 步骤 1: 检查数据库中的数据

执行以下 SQL 查询，检查数据库中的密码数据：

```sql
-- 查看所有用户的密码配置
SELECT
    uam.id AS method_id,
    uam.user_id,
    u.username,
    JSON_UNQUOTE(JSON_EXTRACT(uam.authentication_configuration, '$.password')) AS password,
    JSON_EXTRACT(uam.authentication_configuration, '$.passwordHash') AS passwordHash,
    JSON_EXTRACT(uam.authentication_configuration, '$.password_hash') AS password_hash,
    CASE
        WHEN JSON_UNQUOTE(JSON_EXTRACT(uam.authentication_configuration, '$.password')) LIKE '{bcrypt}%' THEN '✅ 有 {bcrypt} 前缀（password 键）'
        WHEN JSON_EXTRACT(uam.authentication_configuration, '$.passwordHash') IS NOT NULL THEN '⚠️ 使用 passwordHash 键（旧格式）'
        WHEN JSON_EXTRACT(uam.authentication_configuration, '$.password_hash') IS NOT NULL THEN '⚠️ 使用 password_hash 键（旧格式）'
        WHEN JSON_UNQUOTE(JSON_EXTRACT(uam.authentication_configuration, '$.password')) LIKE '{%' THEN '⚠️ 使用其他格式'
        ELSE '❌ 格式错误'
    END AS password_format
FROM user_authentication_method uam
INNER JOIN user u ON u.id = uam.user_id
WHERE uam.authentication_provider = 'LOCAL'
  AND uam.authentication_type = 'PASSWORD'
  AND (
      JSON_EXTRACT(uam.authentication_configuration, '$.password') IS NOT NULL
      OR JSON_EXTRACT(uam.authentication_configuration, '$.passwordHash') IS NOT NULL
      OR JSON_EXTRACT(uam.authentication_configuration, '$.password_hash') IS NOT NULL
  );
```

**预期结果**：

- `password` 字段应该是：`{bcrypt}$2a$10$4EIhU0zLHczmflOEv4FmwePyA3GDH04Jo/UIYbbiz/XH6IE173DEu`
- `password_format` 应该是：`✅ 有 {bcrypt} 前缀（password 键）`

### 步骤 2: 更新数据库中的密码

如果数据库中的数据不正确，执行 `update_password_to_123456.sql` 脚本：

```bash
mysql -u root -p tiny_web < update_password_to_123456.sql
```

或者手动执行 SQL：

```sql
-- 更新密码为 123456（BCrypt 编码）
UPDATE user_authentication_method
SET authentication_configuration = JSON_SET(
    COALESCE(authentication_configuration, JSON_OBJECT()),
    '$.password',
    '{bcrypt}$2a$10$4EIhU0zLHczmflOEv4FmwePyA3GDH04Jo/UIYbbiz/XH6IE173DEu' -- 123456
)
WHERE authentication_provider = 'LOCAL'
  AND authentication_type = 'PASSWORD'
  AND (
      JSON_EXTRACT(authentication_configuration, '$.password') IS NOT NULL
      OR JSON_EXTRACT(authentication_configuration, '$.passwordHash') IS NOT NULL
      OR JSON_EXTRACT(authentication_configuration, '$.password_hash') IS NOT NULL
  );

-- 删除旧的键名，统一使用 password 键
UPDATE user_authentication_method
SET authentication_configuration = JSON_REMOVE(
    authentication_configuration,
    '$.passwordHash',
    '$.password_hash',
    '$.encodedPassword',
    '$.hash'
)
WHERE authentication_provider = 'LOCAL'
  AND authentication_type = 'PASSWORD'
  AND JSON_EXTRACT(authentication_configuration, '$.password') IS NOT NULL
  AND (
      JSON_EXTRACT(authentication_configuration, '$.passwordHash') IS NOT NULL
      OR JSON_EXTRACT(authentication_configuration, '$.password_hash') IS NOT NULL
      OR JSON_EXTRACT(authentication_configuration, '$.encodedPassword') IS NOT NULL
      OR JSON_EXTRACT(authentication_configuration, '$.hash') IS NOT NULL
  );
```

### 步骤 3: 查看应用程序日志

启动应用程序，尝试登录，查看日志输出。日志应该包含以下信息：

```
DEBUG - 用户 user_0001 的认证配置内容: {password={bcrypt}$2a$10$...}
DEBUG - 用户 user_0001 的认证配置键: [password, ...]
DEBUG - 用户 user_0001 从 password 键读取密码
DEBUG - 用户 user_0001 成功读取密码，密码长度: 67, 密码前缀: {bcrypt}$2a
DEBUG - 用户 user_0001 密码验证开始
DEBUG - 用户输入的密码长度: 6
DEBUG - 存储的密码哈希（原始）: {bcrypt}$2a$10$4EIhU0...
DEBUG - 存储的密码哈希（规范化后）: {bcrypt}$2a$10$4EIhU0...
DEBUG - 密码哈希是否有前缀: true
DEBUG - 用户 user_0001 密码验证结果: 成功
INFO - 用户 user_0001 密码认证成功
```

### 步骤 4: 验证密码哈希值

使用 `PasswordVerificationTest.java` 验证密码哈希值：

```bash
cd tiny_web
mvn compile exec:java -Dexec.mainClass="com.tiny.web.PasswordVerificationTest" -Dexec.classpathScope=test
```

**预期结果**：所有测试应该通过（✅ 密码正确）

## 常见问题

### 问题 1: 数据库中的数据没有更新

**症状**：日志显示密码哈希值与数据库中的不一致。

**解决方法**：

1. 执行 `update_password_to_123456.sql` 脚本
2. 检查 SQL 更新语句是否成功执行
3. 检查数据库连接是否正确
4. 检查事务是否提交

### 问题 2: JSON 解析失败

**症状**：日志显示 `config` 为空或 `password` 键不存在。

**解决方法**：

1. 检查 JSON 格式是否正确
2. 检查 `JsonStringConverter` 是否正确配置
3. 检查数据库中的 JSON 数据是否有特殊字符
4. 检查 JSON 键名是否使用了 `password`（新格式）还是 `passwordHash`（旧格式）

### 问题 3: 密码哈希值不正确

**症状**：日志显示密码验证失败，但数据库中的哈希值看起来正确。

**解决方法**：

1. 使用 `PasswordVerificationTest.java` 验证哈希值
2. 检查哈希值是否有特殊字符或空格
3. 检查哈希值长度是否正确
4. 检查哈希值是否有 `{bcrypt}` 前缀

### 问题 4: 键名不匹配

**症状**：日志显示 `password` 键不存在，但数据库中有 `passwordHash` 键。

**解决方法**：

1. 执行 `update_password_to_123456.sql` 脚本，统一使用 `password` 键
2. 或者等待代码自动兼容（代码已经支持向后兼容）
3. 手动更新数据库，将 `passwordHash` 键迁移为 `password` 键

## 修复内容总结

### 1. 密码键名优先级

- **修改前**：优先查找 `passwordHash`，然后查找 `password_hash`，最后查找 `password`
- **修改后**：优先查找 `password`，然后查找 `passwordHash`，最后查找其他键名（向后兼容）

### 2. 密码哈希规范化

- **修改前**：没有规范化处理，直接使用数据库中的值
- **修改后**：添加了 `normalizePasswordHash` 方法，自动为缺少前缀的 BCrypt 密码添加 `{bcrypt}` 前缀

### 3. 调试日志

- **修改前**：只有基本的错误日志
- **修改后**：添加了详细的调试日志，包括配置内容、密码读取过程、验证结果等

## 相关文件

- `MultiAuthenticationProvider.java` - 多认证提供者（已修复）
- `update_password_to_123456.sql` - 密码更新脚本
- `application.yaml` - 日志配置（已更新）

## 下一步

1. **重启应用程序**
2. **执行密码更新脚本**（如果数据库中的数据不正确）
3. **查看应用程序日志**（DEBUG 级别）
4. **尝试登录**（用户名：`user_0001`，密码：`123456`）

## 联系支持

如果问题仍然存在，请提供以下信息：

1. **应用程序日志**（包含 DEBUG 级别的日志）
2. **数据库查询结果**（执行步骤 1 的 SQL 查询结果）
3. **错误信息**（完整的异常堆栈）
4. **配置信息**（`application.yaml` 中的相关配置）
