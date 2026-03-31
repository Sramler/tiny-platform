# Liquibase 117：`role_resource` 表下线运行手册

> 对应变更：`117-drop-role-resource-legacy.yaml`（`DROP TABLE role_resource`）  
> 前置：应用代码已不读写 `role_resource`；`role_permission` 已为运行态主关系（116 等已回填）。

## 1. 迁移前（预发 / 生产 cutover 前）

1. 在**目标库**执行（需 MySQL 客户端，`MYSQL_PWD` 或 `-p`）：

   ```bash
   mysql ... tiny_web < tiny-oauth-server/scripts/verify-pre-liquibase-117-role-resource-readiness.sql
   ```

2. 关注输出：

   - `missing_in_role_permission`：建议为 **0**（若表仍存在，表示 RR 上有权限未投影到 RP）。
   - `role_permission_rows` / `permission_rows`：应大于 0（视环境 seed 而定）。

3. 跑应用侧门禁（可选但推荐）：

   ```bash
   bash tiny-oauth-server/scripts/verify-role-resource-legacy-removal-proof-pack.sh
   ```

## 2. 执行迁移

- 使用常规发布链路启动应用，让 **Liquibase** 执行到 **117**（或在该环境执行 `liquibase update` 等价流程）。
- `117` 使用 `preConditions`：`role_resource` 不存在时 **`onFail: MARK_RAN`**，重复执行安全。

## 3. 迁移后验证

```bash
mysql ... tiny_web < tiny-oauth-server/scripts/verify-post-liquibase-117-canonical-health.sql
```

期望：

- `role_resource_table_exists` = **0**
- `role_permission_rows` / `permission_rows` 与预期一致

再跑：

```bash
bash tiny-oauth-server/scripts/run-permission-dev-smoke-10m.sh
```

应出现日志：`role_resource absent: skip legacy reconcile`。

## 4. 回滚（仅作预案，成本高）

应用回滚（`git revert` / 旧制品）**不会**自动恢复已 DROP 的表。若必须恢复：

1. 从**迁移前备份**还原整库，或  
2. 手工执行与 **002 / 018** 等一致的 `role_resource` 建表 DDL（须与当前 `role`/`resource`/`tenant` 外键一致），再从备份恢复 `role_resource` 数据。

> 不建议在无备份情况下对生产执行“空表重建”；会导致历史绑定丢失。

## 5. 相关脚本

| 脚本 | 用途 |
|------|------|
| `verify-pre-liquibase-117-role-resource-readiness.sql` | 仅当仍存在 `role_resource` 时：RR→RP gap + 行数 |
| `verify-post-liquibase-117-canonical-health.sql` | 表已删：`information_schema` 校验 + RP/permission 计数 |
| `verify-liquibase-117-drop-gates.sh` | 封装上述 SQL 的本地/CI 一键检查（需 `MYSQL_*`） |
