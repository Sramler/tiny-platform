# 70 数据库/SQL 规范

## 适用范围

- 适用于：`**/*.sql`、`**/schema.sql`、`**/changelog/**`、数据库相关 Java 代码
- 不适用于：第三方库表（如 Quartz QRTZ_*、Camunda act_*）

## 总体策略

1. **命名一致性**：表名单数，字段名下划线，统一风格。
2. **性能优先**：合理使用索引，避免全表扫描。
3. **多租户隔离**：所有业务表必须包含 `tenant_id` 并创建索引。

---

## 禁止（Must Not）

### 1) 命名规范

- ❌ 表名使用复数（应使用单数：user, role, resource）。
- ❌ 字段名使用驼峰命名（应使用下划线：user_id, create_time）。
- ❌ 表名和字段名不使用反引号包裹（避免关键字冲突）。

### 2) 数据类型与约束

- ❌ 时间字段混用 DATETIME 和 TIMESTAMP（统一使用 DATETIME）。
- ❌ 创建表时不指定 ENGINE 和 CHARSET（必须 ENGINE=InnoDB CHARSET=utf8mb4）。
- ❌ 外键字段没有索引（影响查询性能）。
- ❌ 在业务表中创建不包含 `tenant_id` 的唯一性约束（应使用 `tenant_id + 业务字段` 联合唯一）。

### 3) SQL 安全

- ❌ SQL 注入：禁止使用 `${}` 动态拼接 SQL，必须使用 `#{}` 参数化查询。
- ❌ 无索引查询：WHERE 条件字段必须创建索引（避免全表扫描）。

### 4) 查询性能

- ❌ JOIN 超过三张表（应拆分为多个查询或使用视图）。
- ❌ 在循环中执行 SQL（应使用批量操作）。

---

## 必须（Must）

### 1) 表结构规范

- ✅ 表命名：单数形式（user/resource/role），使用反引号包裹。
- ✅ 字段命名：下划线命名（user_id, create_time, update_time）。
- ✅ 字符集：统一使用 `utf8mb4`，排序规则 `utf8mb4_general_ci`。
- ✅ 存储引擎：统一使用 `InnoDB`。
- ✅ 时间字段：统一使用 `DATETIME`，默认值 `CURRENT_TIMESTAMP`，更新时自动更新。
- ✅ 主键：统一使用 `BIGINT AUTO_INCREMENT`，字段名 `id`。

### 2) 注释与文档

- ✅ 注释：表必须有 `COMMENT`，字段必须有 `COMMENT`（中文说明）。
- ✅ 变更记录：使用 Liquibase/Flyway 管理数据库变更，禁止直接修改生产表结构。

### 3) 索引规范

- ✅ 索引：外键字段必须创建索引；常用查询字段（status, created_at 等）必须创建索引。
- ✅ 多租户索引：所有业务表必须包含 `tenant_id` 字段，并创建索引。
- ✅ 唯一性约束：租户级别唯一性使用 `UNIQUE KEY uk_表名_tenant_id_业务字段 (tenant_id, 业务字段)`。

### 4) 外键约束

- ✅ 外键约束策略：**不设置数据库外键约束**，由应用层代码控制关联更新和删除（提高性能、便于分库分表）。

### 5) SQL 安全

- ✅ 参数化查询：SQL 使用 `#{}` 或 `PreparedStatement`，禁止 `${}`。
- ✅ 输入验证：所有 SQL 参数必须验证，防止注入攻击。

---

## 应该（Should）

### 1) 索引优化

- ⚠️ 唯一性约束：业务唯一性使用 `UNIQUE KEY`，命名 `uk_表名_字段名`。
- ⚠️ 索引命名：单列索引 `idx_表名_字段名`，联合索引 `idx_表名_字段1_字段2`。
- ⚠️ 在 varchar 类型字段上建立索引时要指定前缀长度，不要索引整个超长字段。

### 2) 字段设计

- ⚠️ 状态字段：使用 `VARCHAR(32)`，在应用层使用枚举类控制，考虑 MySQL 8.0+ 的 CHECK 约束。
- ⚠️ 软删除：如需软删除，添加 `deleted_at DATETIME NULL` 字段，并创建索引。
- ⚠️ 多租户：所有业务表必须包含 `tenant_id BIGINT` 字段，并创建索引。

### 3) 时间字段

- ⚠️ 时间字段：创建时间 `create_time`，更新时间 `update_time`（不使用 created_at/updated_at）。

### 4) 扩展字段

- ⚠️ 扩展字段：预留 `extra JSON` 字段用于存储扩展配置。
- ⚠️ 版本字段：乐观锁使用 `version INT DEFAULT 0`。

### 5) JOIN 优化

- ⚠️ JOIN 限制：Join 不要超过三张表；被联表字段类型要一致；join 的字段必须有索引。

---

## 可以（May）

- 💡 使用数据库视图简化复杂查询。
- 💡 使用存储过程处理复杂业务逻辑（但需谨慎，不利于迁移和测试）。

---

## 例外与裁决

### 第三方库表

- 第三方库表（Quartz、Camunda）遵循其官方规范，不受本规范约束。

### 历史遗留表

- 历史遗留表：如已存在复数表名或不符合规范的字段，需通过 Liquibase changelog 逐步迁移。

### 冲突裁决

- 平台特定规则（90+）优先于本规范。
- 数据库规范与性能优化冲突时，优先保证查询性能。

---

## 示例

### ✅ 正例：完整的表结构定义

```sql
CREATE TABLE `user` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '用户ID',
  `tenant_id` BIGINT NOT NULL COMMENT '租户ID',
  `username` VARCHAR(50) NOT NULL COMMENT '用户名',
  `email` VARCHAR(100) COMMENT '邮箱',
  `status` VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE/INACTIVE',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted_at` DATETIME NULL COMMENT '删除时间（软删除）',
  `version` INT DEFAULT 0 COMMENT '版本号（乐观锁）',
  `extra` JSON COMMENT '扩展字段',
  KEY `idx_user_tenant_id` (`tenant_id`),
  KEY `idx_user_status` (`status`),
  KEY `idx_user_deleted_at` (`deleted_at`),
  UNIQUE KEY `uk_user_tenant_username` (`tenant_id`, `username`),
  UNIQUE KEY `uk_user_tenant_email` (`tenant_id`, `email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='用户表';
```

### ❌ 反例：表名复数、无反引号、无 ENGINE/CHARSET、外键字段无索引

```sql
-- 错误：表名复数、无反引号、无 ENGINE/CHARSET、外键字段无索引
CREATE TABLE users (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  username VARCHAR(50),
  tenantId BIGINT,
  createdAt DATETIME,
  FOREIGN KEY (tenantId) REFERENCES tenant(id)
);
```

### ✅ 正例：参数化查询（MyBatis）

```xml
<!-- ✅ 使用 #{} 参数化查询 -->
<select id="findByUsernameAndTenantId" resultType="User">
  SELECT * FROM `user`
  WHERE username = #{username}
    AND tenant_id = #{tenantId}
    AND deleted_at IS NULL
</select>
```

### ❌ 反例：SQL 注入风险

```xml
<!-- ❌ 错误：使用 ${} 导致 SQL 注入风险 -->
<select id="findByUsername" resultType="User">
  SELECT * FROM `user`
  WHERE username = '${username}'
</select>
```

### ✅ 正例：联合索引（覆盖常用查询）

```sql
-- ✅ 联合索引覆盖常用查询场景
CREATE INDEX `idx_user_tenant_status` ON `user` (`tenant_id`, `status`);

-- 查询可以使用索引
SELECT * FROM `user` WHERE tenant_id = 1 AND status = 'ACTIVE';
```

### ✅ 正例：varchar 索引前缀长度

```sql
-- ✅ 为长 varchar 字段指定索引前缀长度
CREATE INDEX `idx_user_email_prefix` ON `user` (`email`(50));
```

### ✅ 正例：批量操作（避免循环 SQL）

```java
// ✅ 批量插入
@Insert("<script>" +
    "INSERT INTO `user` (tenant_id, username, email) VALUES " +
    "<foreach collection='users' item='user' separator=','>" +
    "(#{user.tenantId}, #{user.username}, #{user.email})" +
    "</foreach>" +
    "</script>")
void batchInsert(@Param("users") List<User> users);
```

### ❌ 反例：循环中执行 SQL

```java
// ❌ 错误：在循环中执行 SQL
for (User user : users) {
    userMapper.insert(user); // ❌ 性能差，应使用批量操作
}
```
