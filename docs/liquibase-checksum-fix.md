# Liquibase Checksum 验证失败修复

## 问题描述

启动应用时，Liquibase 报错：
```
Validation Failed:
     1 changesets check sum
          db/changelog/002-create-schema.yaml::create-schema-tables::tiny 
          was: 9:fb891acf1a658ef96be08c4d0b200380 
          but is now: 9:88d95f52919a9d9f25fbc9adbcd47135
```

## 根本原因

修改了 `schema.sql` 文件（添加了 `uk_resource_name` 唯一约束），导致：
- `002-create-schema.yaml` 引用的 `schema.sql` 内容变化
- Liquibase 重新计算 changeSet 的 checksum
- 数据库中记录的是旧的 checksum
- Liquibase 验证失败，拒绝执行

## 解决方案

### 方案 1: 更新数据库中的 checksum（推荐）

**适用场景**：确定修改是安全的，且已执行的 changeSet 需要更新

**步骤**：

1. 连接到数据库：
```bash
mysql -u root -p tiny_web
```

2. 更新 checksum：
```sql
UPDATE DATABASECHANGELOG 
SET MD5SUM = '9:88d95f52919a9d9f25fbc9adbcd47135'
WHERE ID = 'create-schema-tables' 
  AND AUTHOR = 'tiny' 
  AND FILENAME = 'db/changelog/002-create-schema.yaml';
```

3. 验证更新：
```sql
SELECT ID, AUTHOR, FILENAME, MD5SUM 
FROM DATABASECHANGELOG 
WHERE ID = 'create-schema-tables';
```

### 方案 2: 清除 changeSet 记录（谨慎使用）

**适用场景**：确定可以安全地重新执行该 changeSet

**步骤**：

1. 连接到数据库：
```bash
mysql -u root -p tiny_web
```

2. 删除 changeSet 记录：
```sql
DELETE FROM DATABASECHANGELOG 
WHERE ID = 'create-schema-tables' 
  AND AUTHOR = 'tiny' 
  AND FILENAME = 'db/changelog/002-create-schema.yaml';
```

3. **注意**：这会重新执行 `schema.sql`，可能导致：
   - 表结构重新创建（如果使用 `CREATE TABLE IF NOT EXISTS`，通常安全）
   - 数据丢失（如果使用 `DROP TABLE`，会丢失数据）

### 方案 3: 禁用 checksum 验证（不推荐）

**适用场景**：临时解决，不推荐用于生产环境

**配置**：
```yaml
spring:
  liquibase:
    enabled: true
    change-log: classpath:db/changelog/db.changelog-master.yaml
    # 禁用 checksum 验证（不推荐）
    validate-on-migrate: false
```

## 推荐方案

**使用方案 1（更新 checksum）**，因为：
- ✅ 安全：不会重新执行已执行的 changeSet
- ✅ 准确：checksum 反映当前文件状态
- ✅ 符合 Liquibase 最佳实践

## 验证步骤

1. 更新 checksum 后，重启应用
2. 检查 Liquibase 日志，确认验证通过
3. 验证数据库约束：
```sql
SHOW CREATE TABLE resource;
-- 确认有 uk_resource_name 约束
```

## 预防措施

1. **避免修改已执行的 changeSet**：
   - 如果必须修改，创建新的 changeSet
   - 使用 `preConditions` 确保安全执行

2. **使用 Liquibase 原生语法**：
   - 逐步将 SQL 文件转换为 Liquibase 原生语法
   - 更好的版本控制和变更追踪

3. **文档化变更**：
   - 记录每次修改的原因
   - 在 changeSet 中添加详细注释

