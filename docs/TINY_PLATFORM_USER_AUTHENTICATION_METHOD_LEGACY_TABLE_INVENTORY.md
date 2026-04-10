# `user_authentication_method` 旧表下线 inventory（CARD-09B1）

> 目的：在 `CARD-09A`（主链零 runtime 读旧表）完成后，清点仓库内对 `user_authentication_method` 的残留引用，明确 **CARD-09B2**（清代码/脚本/测试，不删表）与 **CARD-09B3**（Liquibase 最终 drop + 物理下线）分工，并记录进入 **09B3** 的前置条件。

## 1. 与 09A / 09B 的区分

| 阶段 | 含义 |
|------|------|
| **09A 已完成** | `tiny-oauth-server` 生产主链认证读路径已以 `user_auth_credential` + `user_auth_scope_policy` 为真源；旧表不参与主链鉴权读。 |
| **09B1（本文档）** | 只做 inventory 与 drop 前置确认，**不删表、不改业务主链语义**。 |
| **09B2** | 删除仓库内对旧表 **物理存在** 的 JPA/主路径依赖；保留迁移历史与最终 drop 所需最小面。 |
| **09B3** | **已完成**：Liquibase `134-drop-user-authentication-method.yaml` + `schema.sql` / `data.sql` 与 `tiny-web` 侧物理下线口径；**bridge 迁移期正式结束**。 |

## 2. Drop 前置检查清单

| 检查项 | 状态（实施 09B2/09B3 时更新） |
|--------|-------------------------------|
| 运行时主链不再读旧表（09A） | 已满足（任务清单口径） |
| 运行时主链不再写旧表（双写已收口到新模型） | **已满足**（09B2 移除旧 repository；仅 `UserAuthenticationBridgeWriter` 写新表） |
| backfill / 对账证据（CARD-06） | 已有：`legacy vs new-model diff = empty` |
| real-link 认证链证据（08A/08B） | 任务清单标记已完成；CI/本地以 playbook 为准 |
| 跨模块：`tiny-web` 若与主库共用 schema | **已满足**：`tiny-web` 已改为 `user_auth_credential` + `user_auth_scope_policy`（`UserAuthPasswordLookupService`） |

## 3. 残留引用清单（按处理归属）

### 3.1 CARD-09B2 应清理（代码 / 测试 / 非 drop 脚本）

| 区域 | 说明 |
|------|------|
| `tiny-oauth-server` `UserAuthenticationMethod` | 保留为 **非 JPA 的内存载体/DTO**（`UserAuthenticationMethodProfile.storageRecord()`），**不得**再映射 `user_authentication_method` 表 |
| `UserAuthenticationMethodRepository` | **删除**；所有原 `save`/查询 改为仅 `UserAuthenticationBridgeWriter` + 新表 |
| `UserAuthenticationMethodProfileService` | 仅依赖 `UserAuthScopePolicyRepository`；删除 legacy-only 构造与旧表回退分支 |
| `MultiAuthenticationProvider` / `TotpVerificationGuard` / `UserServiceImpl` / `TenantServiceImpl` | 去掉对旧 repository 的注入与 fallback |
| `SecurityServiceImpl` | 去掉未使用的 `UserAuthenticationMethodRepository` 构造参数 |
| 单测与集成测试 | 改为 mock 新模型 repository / `UserAuthenticationBridgeWriter` |
| `tiny-web` | 删除旧 JPA entity/repository；密码读取改为 **JDBC 查询新表**（或等价实现） |
| 运维/历史 SQL（`tiny-web` 下大量 `UPDATE user_authentication_method` 样例） | 标注 **仅历史审计/旧环境**；或改写为指向新表（可选，非阻塞 drop） |

### 3.2 CARD-09B3 才执行（最终物理下线）

| 区域 | 说明 |
|------|------|
| Liquibase 新 changeset | `dropTable` + 适当 `preConditions`（表存在才删） |
| `tiny-oauth-server/src/main/resources/schema.sql` | 删除 `CREATE TABLE user_authentication_method` 块 |
| `tiny-web/src/main/resources/schema.sql` | 已切换为 `user_auth_credential` / `user_auth_scope_policy` DDL，不再包含旧表 |
| 文档 | `TINY_PLATFORM_AUTHORIZATION_TASK_LIST.md`、本 inventory：标记 bridge 结束 |

### 3.3 Liquibase 历史 changeset（只读）

| 说明 |
|------|
| `018-add-tenant-id-auth.yaml` 等 **历史** 文件仍包含建表语句 — **不得**改写已发布 checksum；仅通过 **新** changeset 在尾部 drop。 |

## 4. 09B2 / 09B3 边界小结

- **09B2**：仓库内不再假设「存在名为 `user_authentication_method` 的表」来跑主路径；JPA 映射与 `JpaRepository` 下线；表可仍存在（DB 未执行 drop）。
- **09B3**：通过迁移链执行 drop；`schema.sql` 与种子数据与新模型一致；文档声明迁移期结束。

## 5. 剩余风险

- **共用数据库**：若某环境仍依赖未迁移的旁路脚本写入旧表，drop 将失败或导致静默损坏 — 需在目标环境做一次迁移 dry-run / 备份。
- **历史 SQL 文档**：仓库内教学用 SQL 可能仍出现表名 — 以注释标明「历史」即可，不作为运行前提。

---

*CARD-09B3 已完成：旧表 `user_authentication_method` 由 Liquibase 尾部 changeset 删除；仓库主路径与 `schema.sql` 不再包含该表。*

*本文件随 CARD-09B2 / 09B3 落地同步更新。*
