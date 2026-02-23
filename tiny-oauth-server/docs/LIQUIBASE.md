### Liquibase 使用说明 & 规范（tiny-oauth-server）

本文档记录 **tiny-oauth-server 模块接入 Liquibase 的完整过程、踩过的坑和约定的使用规范**，重点围绕：

- demo 导出测试表 `demo_export_usage` 的存储过程 `sp_generate_demo_export_usage`

说明：`demo_export_usage` 已将 `tenant_code` 统一迁移为 `tenant_id`，历史数据默认写入 `tenant_id=1`（见 `019-rename-demo-export-tenant-id.yaml`）。
- 后续在本项目中使用 Liquibase 管理 DDL / DML 的统一做法

目录：

1. 配置入口（依赖 & Spring Boot 集成）
2. 变更文件结构（changelog 布局）
3. 接入过程中的错误 & 最终修复方案
4. 新增 / 修改存储过程的推荐模板
5. DDL / DML 接入规范建议
6. 验证步骤（回归本次问题）

---

#### 1. 配置入口（依赖 & Spring Boot 集成）

- Maven 依赖：在 `tiny-oauth-server/pom.xml` 中引入：

  ```xml
  <dependency>
    <groupId>org.liquibase</groupId>
    <artifactId>liquibase-core</artifactId>
  </dependency>
  ```

- Spring Boot 配置：在 `tiny-oauth-server/src/main/resources/application.yaml` 中：

  ```yaml
  spring:
    liquibase:
      enabled: true
      change-log: classpath:db/changelog/db.changelog-master.yaml
  ```

> 说明：当前阶段，**表结构仍由 `schema.sql` 管理**，Liquibase 只负责存储过程等“复杂 DDL”。后续可逐步将表结构迁移到 Liquibase。

---

#### 2. 变更文件结构（changelog 布局）

- Master changelog：
  - `src/main/resources/db/changelog/db.changelog-master.yaml`
- 存储过程定义：
  - `src/main/resources/db/changelog/001-create-demo-export-usage-procedure.sql`

当前 master changelog 内容（简化版，仅演示 `sp_generate_demo_export_usage`）：

```yaml
databaseChangeLog:
  - changeSet:
      id: sp_generate_demo_export_usage_v1
      author: tiny
      changes:
        - sql:
            dbms: mysql
            sql: "DROP PROCEDURE IF EXISTS sp_generate_demo_export_usage"
        - sqlFile:
            path: db/changelog/001-create-demo-export-usage-procedure.sql
            relativeToChangelogFile: false
            splitStatements: false
            stripComments: false
```

`001-create-demo-export-usage-procedure.sql` 只包含 **单条** `CREATE PROCEDURE` 语句，不包含 `DROP` 和 `DELIMITER`：

```sql
CREATE PROCEDURE sp_generate_demo_export_usage(
    IN p_tenant_id BIGINT,
    IN p_days INT,
    IN p_rows_per_day INT,
    IN p_target_rows INT,
    IN p_clear_existing TINYINT(1)
)
BEGIN
    -- 存储过程体：按 tenant_id 生成 demo_export_usage 测试数据
    -- ...
END;
```

---

#### 3. 为什么要这样拆分？（本次问题的 fix & 错误记录）

##### 3.1 接入过程遇到的主要错误

- **错误 1：`schema.sql` + `DELIMITER` 导致存储过程无法创建**

  - 在 `schema.sql` 中使用 MySQL 客户端风格：
    - `DROP PROCEDURE IF EXISTS ...;`
    - `DELIMITER $$`
    - `CREATE PROCEDURE ... BEGIN ... END$$`
    - `DELIMITER ;`
  - Spring Boot 通过 JDBC 执行脚本，不认识 `DELIMITER`，只会按 `;` 简单切分 SQL：
    - `DROP PROCEDURE ...;` 能执行成功；
    - `CREATE PROCEDURE ...` 被不完整地切开，导致语法错误；
    - 配置了 `continue-on-error: true`，错误被吞掉，只留下“过程被 DROP 掉”的结果。
  - 现象：
    - 每次应用重启后，调用 `CALL sp_generate_demo_export_usage(...)` 报 `PROCEDURE tiny_web.sp_generate_demo_export_usage does not exist`；
    - 在 Navicat 中手动执行脚本可以成功创建过程，但重启后过程又消失。

- **错误 2：Liquibase 中将 `DROP + CREATE` 放在同一个 SQL 文件**

  - 初始写法（示意）：

    ```sql
    DROP PROCEDURE IF EXISTS sp_generate_demo_export_usage;
    CREATE PROCEDURE sp_generate_demo_export_usage(...) BEGIN ... END;
    ```

    ```yaml
    - sqlFile:
        path: db/changelog/001-create-demo-export-usage-procedure.sql
        splitStatements: false
    ```

  - 因为 `splitStatements: false`，Liquibase 会把整个文件作为“一条 SQL 语句”提交给 MySQL，MySQL 报：
    - `SQLSyntaxErrorException: You have an error in your SQL syntax ... near 'CREATE PROCEDURE sp_generate_demo_export_usage(...)'`
  - 如果改成 `splitStatements: true`，又会在存储过程体内部根据 `;` 错误拆分，依然不行。

- **错误 3：changelog 路径写错导致文件找不到**

  - 日志报错：

    ```text
    Error parsing classpath:/db/changelog/db.changelog-master.yaml :
    The file db/changelog/db/changelog/001-create-demo-export-usage-procedure.sql was not found ...
    ```

  - 原因：
    - `db.changelog-master.yaml` 中使用了 `file: db/changelog/001-...sql`，同时又叠加了 `relativeToChangelogFile` / classpath 前缀；
    - 最终解析成 `db/changelog/db/changelog/...`，与实际路径不符。

##### 3.2 修复思路（当前方案）

- **避免 `DELIMITER`**：

  - 在 Liquibase 的 SQL 文件中只写标准的：

    ```sql
    CREATE PROCEDURE ... BEGIN ... END;
    ```

  - 不使用 `DELIMITER` 这类仅客户端认识的语法。

- **在一个 changeSet 中拆成两个 step：`DROP` + `CREATE`**

  ```yaml
  databaseChangeLog:
    - changeSet:
        id: sp_generate_demo_export_usage_v1
        author: tiny
        changes:
          - sql:
              dbms: mysql
              sql: "DROP PROCEDURE IF EXISTS sp_generate_demo_export_usage"
          - sqlFile:
              path: db/changelog/001-create-demo-export-usage-procedure.sql
              relativeToChangelogFile: false
              splitStatements: false
              stripComments: false
  ```

  - `<sql>` 执行简单的 `DROP PROCEDURE`，不会被错误拆分；
  - `<sqlFile>` 加载只包含 `CREATE PROCEDURE` 的文件，且 `splitStatements: false`，防止过程体内部的 `;` 被拆分。
  - 对业务来说，这两个 step 同属一个 changeSet，视为一个整体：要么一起成功，要么一起失败。

- **修正 changelog 路径**
  - 统一约定：`sqlFile.path` 从 classpath 根开始，例如 `db/changelog/xxx.sql`；
  - `relativeToChangelogFile: false`，避免路径再叠加一层 `db/changelog/`。

---

#### 4. 新增 / 修改其他存储过程的推荐模板

以后如需为其他表增加存储过程，建议按以下约定：

1. **命名规范**

   - changeSet `id`：`sp_xxx_v1` / `sp_xxx_v2`（带版本号，方便今后升级）；
   - SQL 文件名：`sp_xxx_v1.sql`，放在 `db/changelog/` 目录下。

2. **changelog 示例**

在 `db.changelog-master.yaml` 中新增：

```yaml
- changeSet:
    id: sp_some_procedure_v1
    author: tiny
    changes:
      - sql:
          dbms: mysql
          sql: "DROP PROCEDURE IF EXISTS sp_some_procedure"
      - sqlFile:
          path: db/changelog/sp_some_procedure_v1.sql
          relativeToChangelogFile: false
          splitStatements: false
          stripComments: false
```

对应的 `sp_some_procedure_v1.sql`：

```sql
CREATE PROCEDURE sp_some_procedure(...)
BEGIN
    -- 过程体逻辑
END;
```

3. **升级存储过程**

- 不要修改已经执行过的 changeSet（避免和生产库 `DATABASECHANGELOG` 记录不一致）。
- 如需修改存储过程：
  - 新增一个 `sp_some_procedure_v2` 的 changeSet 和 SQL 文件；
  - 重复 “DROP + CREATE” 模式；
  - 由 Liquibase 在新环境执行 v1 + v2，在已运行环境只执行新增的 v2。

---

#### 5. DDL / DML 接入规范建议

为了后续在更多对象上使用 Liquibase 时保持一致，这里补充一份本项目内的建议规范：

##### 5.1 表结构（DDL）

- 目前大量表结构仍在 `schema.sql` 中，通过 `spring.sql.init` 初始化：
  - 适合开发/演示环境快速建库；
  - 短期内可以继续沿用，但**不建议**把复杂 DDL（过程/触发器）再放进来。
- 如需逐步迁移到 Liquibase：

  - 每次结构变更（新增表/字段/索引）定义一个单独 changeSet；
  - 表名、索引名建议在 changeSet `id` 中体现，方便回溯。
  - 当大部分结构迁移到 Liquibase 后，建议在生产/预发环境中**关闭** `spring.sql.init`，避免 `schema.sql` 被重复执行，配置示例：

    ```yaml
    spring:
      sql:
        init:
          mode: never # 生产环境关闭 schema.sql / data.sql 的自动执行
      liquibase:
        enabled: true
        change-log: classpath:db/changelog/db.changelog-master.yaml
    ```

  - 开发环境可以保留 `schema.sql` 用于“一键建库”，但要确保其中不再包含复杂 DDL（特别是 `DELIMITER` + 存储过程定义）。

##### 5.2 存储过程 / 函数 / 触发器

- 统一使用 Liquibase 管理，**不再**通过 `schema.sql` + `DELIMITER` 方式初始化。
- 模板（以过程为例）：
  - 一个 changeSet 中包含：
    - `<sql>`：`DROP PROCEDURE IF EXISTS ...`；
    - `<sqlFile>`：只包含 `CREATE PROCEDURE ... BEGIN ... END;` 的 SQL 文件，`splitStatements: false`。
- 多个过程时：
  - 每个过程独立一个 changeSet + 一个 SQL 文件；
  - 命名规范统一（`sp_xxx_vN`），方便查找和升级。

##### 5.3 数据初始化 / 迁移（DML）

- 开发环境 demo 数据可以继续用 `data.sql`；
- 需要按版本演化的数据变更（例如批量迁移某字段、修复历史数据）建议写成 Liquibase changeSet（`<sql>` 或 `<sqlFile>`），记录在 `DATABASECHANGELOG` 中，便于环境一致。

---

#### 6. 验证步骤（用于回归本次问题）

1. 在 MySQL 中连接到 `tiny_web`，执行：`DROP PROCEDURE IF EXISTS sp_generate_demo_export_usage;`。
2. 在项目根目录运行：`mvn -pl tiny-oauth-server -DskipTests spring-boot:run`，观察启动日志中 Liquibase 无报错，应用成功启动。
3. 在数据库中执行：`SHOW PROCEDURE STATUS WHERE Db = 'tiny_web' AND Name = 'sp_generate_demo_export_usage';`，确认过程存在。
4. 打开前端“测试数据”页面，点击“生成测试数据”，观察接口不再因存储过程问题返回 400/500，且表格数据正常刷新。
