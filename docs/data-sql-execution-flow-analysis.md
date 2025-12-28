# data.sql 执行流程分析

## 执行流程

### 当前配置

```yaml
spring:
  sql:
    init:
      mode: never  # ✅ 已禁用 Spring SQL 初始化
  liquibase:
    enabled: true  # ✅ 启用 Liquibase
    change-log: classpath:db/changelog/db.changelog-master.yaml
```

### 执行顺序

```
应用启动
    ↓
Liquibase 初始化
    ↓
读取 db.changelog-master.yaml
    ↓
执行 changeSet（按顺序）
    ↓
├─ 002-create-schema.yaml → 执行 schema.sql（创建表结构）
├─ 003-insert-initial-data.yaml → 执行 data.sql（插入初始数据）
└─ 001-create-demo-export-usage-procedure.sql → 创建存储过程
```

### Liquibase 执行机制

1. **首次执行**：
   - Liquibase 创建 `DATABASECHANGELOG` 表记录已执行的 changeSet
   - 执行所有 changeSet
   - 记录每个 changeSet 的 ID、author、checksum、执行时间等

2. **后续执行**：
   - 检查 `DATABASECHANGELOG` 表
   - 只执行未执行过的 changeSet
   - 如果 changeSet 的 checksum 变化，会重新执行（可能导致问题）

## 可能导致脏数据的原因

### 1. ❌ INSERT IGNORE 的局限性

**问题**：
- `INSERT IGNORE` 在数据已存在时会**忽略插入**，但**不会更新**现有数据
- 如果现有数据的字段值与 `data.sql` 中的不同，不会同步更新

**示例**：
```sql
-- data.sql 中定义
INSERT IGNORE INTO `resource` (`name`, `url`, `title`) VALUES
('user', '/system/user', '用户管理');

-- 如果数据库中已存在：
-- name='user', url='/old/path', title='旧标题'
-- INSERT IGNORE 会忽略，不会更新 url 和 title
```

**影响**：
- 数据不一致
- 字段值过时
- 无法通过重新执行 `data.sql` 修复数据

### 2. ⚠️ resource 表缺少唯一约束

**问题**：
- `resource` 表**没有** `name` 或 `url` 的唯一约束
- `INSERT IGNORE` 无法判断数据是否已存在
- 可能导致重复插入

**schema.sql 中的定义**：
```sql
CREATE TABLE IF NOT EXISTS `resource` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `name` VARCHAR(100) NOT NULL,  -- ❌ 没有 UNIQUE 约束
    `url` VARCHAR(200) NOT NULL DEFAULT '',  -- ❌ 没有 UNIQUE 约束
    ...
);
```

**影响**：
- 可能插入重复的 `name` 或 `url`
- `INSERT IGNORE` 无法防止重复（因为没有唯一约束触发）

### 3. ⚠️ parent_id 依赖问题

**问题**：
- `resource` 表使用 `parent_id` 建立父子关系
- `data.sql` 中使用硬编码的 ID（如 `parent_id: 1`）
- 如果父资源不存在或 ID 不匹配，会导致数据不一致

**示例**：
```sql
-- 假设 system 目录的 ID 不是 1
INSERT IGNORE INTO `resource` (`name`, `url`, `parent_id`) VALUES
('user', '/system/user', 1);  -- ❌ 如果 system 的 ID 不是 1，parent_id 会错误
```

### 4. ⚠️ Liquibase changeSet 重复执行

**问题**：
- 如果 changeSet 的 checksum 变化，Liquibase 会重新执行
- 修改 `data.sql` 会导致 checksum 变化
- 重新执行可能导致数据重复或不一致

**触发条件**：
- 修改 `data.sql` 文件内容
- 修改 changeSet 的 ID 或 author
- 手动清除 `DATABASECHANGELOG` 记录

### 5. ⚠️ JPA ddl-auto: update 冲突

**问题**：
- `hibernate.ddl-auto: update` 会自动更新表结构
- 可能与 Liquibase 管理的表结构冲突
- 可能导致字段不一致

**当前配置**：
```yaml
jpa:
  hibernate:
    ddl-auto: update  # ⚠️ 可能与 Liquibase 冲突
```

### 6. ⚠️ 其他 SQL 文件干扰

**问题**：
- 存在其他 SQL 文件可能插入相同数据：
  - `menu_data_insert.sql`
  - `menu_resource_data.sql`
  - `insert.sql`
- 如果这些文件被手动执行，会导致重复数据

## 解决方案

### 方案 1: 使用 REPLACE INTO 或 ON DUPLICATE KEY UPDATE（推荐）

**优点**：
- 如果数据存在，会更新字段值
- 如果数据不存在，会插入新数据
- 保证数据一致性

**缺点**：
- 需要唯一约束（`name` 或 `url`）
- 会覆盖现有数据（可能不是期望的行为）

**实现**：
```sql
-- 需要先添加唯一约束
ALTER TABLE `resource` ADD UNIQUE KEY `uk_resource_name` (`name`);

-- 使用 ON DUPLICATE KEY UPDATE
INSERT INTO `resource` (`name`, `url`, `title`) VALUES
('user', '/system/user', '用户管理')
ON DUPLICATE KEY UPDATE
    `url` = VALUES(`url`),
    `title` = VALUES(`title`);
```

### 方案 2: 添加唯一约束 + 使用 INSERT IGNORE（当前方案优化）

**优点**：
- 防止重复插入
- 不覆盖现有数据

**缺点**：
- 无法更新已存在的数据
- 需要手动修复不一致的数据

**实现**：
```sql
-- 添加唯一约束
ALTER TABLE `resource` ADD UNIQUE KEY `uk_resource_name` (`name`);

-- 使用 INSERT IGNORE（已在使用）
INSERT IGNORE INTO `resource` (`name`, `url`, `title`) VALUES
('user', '/system/user', '用户管理');
```

### 方案 3: 使用 Liquibase 的 preConditions（推荐）

**优点**：
- 只在数据不存在时插入
- 不依赖唯一约束
- 更灵活的控制

**实现**：
```yaml
databaseChangeLog:
  - changeSet:
      id: insert-initial-data
      author: tiny
      changes:
        - sqlFile:
            path: classpath:data.sql
            preConditions:
              - onFail: MARK_RAN
              - sqlCheck:
                  expectedResult: 0
                  sql: SELECT COUNT(*) FROM resource WHERE name = 'system'
```

### 方案 4: 拆分 changeSet，使用条件插入

**优点**：
- 更细粒度的控制
- 可以单独处理每个资源

**实现**：
```yaml
databaseChangeLog:
  - changeSet:
      id: insert-resource-system
      author: tiny
      changes:
        - sql:
            sql: |
              INSERT INTO `resource` (`name`, `url`, `title`)
              SELECT 'system', '/system', '系统管理'
              WHERE NOT EXISTS (
                  SELECT 1 FROM `resource` WHERE `name` = 'system'
              )
```

## 推荐方案

### 短期修复（立即执行）

1. **添加唯一约束**：
   ```sql
   ALTER TABLE `resource` ADD UNIQUE KEY `uk_resource_name` (`name`);
   ```

2. **保持 INSERT IGNORE**：
   - 已在使用，无需修改
   - 添加唯一约束后，`INSERT IGNORE` 可以正确防止重复

3. **修复 parent_id 依赖**：
   - 使用 `SELECT` 查询获取父资源 ID，而不是硬编码
   - 已在 `data.sql` 中使用（`@scheduling_dir_id`）

### 长期优化（后续改进）

1. **迁移到 Liquibase 原生语法**：
   - 将 `data.sql` 拆分为多个 changeSet
   - 使用条件插入（`WHERE NOT EXISTS`）
   - 更好的版本控制和追踪

2. **禁用 JPA ddl-auto**：
   ```yaml
   jpa:
     hibernate:
       ddl-auto: none  # 由 Liquibase 统一管理
   ```

3. **清理其他 SQL 文件**：
   - 删除或迁移 `menu_data_insert.sql`、`menu_resource_data.sql` 等
   - 统一由 Liquibase 管理

## 验证步骤

1. **检查唯一约束**：
   ```sql
   SHOW CREATE TABLE resource;
   -- 确认是否有 uk_resource_name 约束
   ```

2. **检查重复数据**：
   ```sql
   SELECT name, COUNT(*) as count
   FROM resource
   GROUP BY name
   HAVING count > 1;
   ```

3. **检查数据一致性**：
   ```sql
   -- 检查 parent_id 是否正确
   SELECT r1.id, r1.name, r1.parent_id, r2.name as parent_name
   FROM resource r1
   LEFT JOIN resource r2 ON r1.parent_id = r2.id
   WHERE r1.parent_id IS NOT NULL AND r2.id IS NULL;
   ```

4. **检查 Liquibase 执行记录**：
   ```sql
   SELECT * FROM DATABASECHANGELOG
   WHERE id = 'insert-initial-data'
   ORDER BY dateexecuted DESC;
   ```

## 总结

**当前问题**：
- `resource` 表缺少唯一约束，`INSERT IGNORE` 无法防止重复
- `INSERT IGNORE` 无法更新已存在的数据
- 可能存在多个数据源插入相同数据

**推荐修复**：
1. 添加 `resource.name` 的唯一约束
2. 保持 `INSERT IGNORE`（已在使用）
3. 后续迁移到 Liquibase 原生语法，使用条件插入

