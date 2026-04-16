# Tiny OAuth Server ITEM-05 Properties Migrator Report

执行日期：2026-04-16  
执行分支：`codex/tiny-platform-sb4-camunda7-fork`

> 目的：临时引入 `spring-boot-properties-migrator`，分别在 `dev / ci / e2e` 三条启动路径上扫描 Spring Boot 4 配置属性迁移风险，并在结束后移除依赖。

---

## 扫描结果总表

迁移器关键词检索使用的 rg 模式（每条 profile 各跑一次）：  
`PropertiesMigration|Properties migration|No properties migration|deprecated application properties|renamed.*application properties|replaced.*application properties`

| profile | 状态 | 启动阻塞错误 | 日志路径 | 命中属性 | 替代属性/官方建议 | 是否已处理 | 备注 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| dev | 无迁移项 | 无 | `tiny-oauth-server/target/item05-properties-migrator/dev.log` | - | - | 不涉及 | `dev.log` 中上述迁移器关键词无匹配；日志中可见 `profile is active: "dev"` 与 `Started OauthServerApplication` |
| ci | 无迁移项 | 无 | `tiny-oauth-server/target/item05-properties-migrator/ci.log` | - | - | 不涉及 | `ci.log` 中上述迁移器关键词无匹配；日志中可见 `profile is active: "ci"` 与 `Started OauthServerApplication` |
| e2e | 无迁移项 | 无 | `tiny-oauth-server/target/item05-properties-migrator/e2e.log` | - | - | 不涉及 | `e2e.log` 中上述迁移器关键词无匹配；日志中可见 `profile is active: "e2e"` 与 `Started OauthServerApplication` |

---

## 各 profile 启动前提与复核证据

| profile | 启动前提 | 关键复核证据 |
| --- | --- | --- |
| dev | 本机 MySQL 可达；`DB_HOST/DB_PORT/DB_NAME/DB_USER/DB_PASSWORD` 可用，或可回退到 `E2E_DB_PASSWORD` / `MYSQL_ROOT_PASSWORD` | `dev.log` 中可见 `The following 1 profile is active: "dev"` 与 `Started OauthServerApplication` |
| ci | 本机 MySQL 可达；`MYSQL_ROOT_PASSWORD` 可用，`MYSQL_PORT` 可选覆盖 | `ci.log` 中可见 `The following 1 profile is active: "ci"`、`Started OauthServerApplication`；同一日志的运行类路径中可见 `spring-boot-properties-migrator-4.0.0.jar`，说明临时 migrator 已真实进入运行类路径 |
| e2e | `E2E_DB_PASSWORD` 必需；`E2E_DB_HOST/E2E_DB_PORT/E2E_DB_NAME/E2E_DB_USER/E2E_FRONTEND_BASE_URL` 可按需覆盖 | `e2e.log` 中可见 `The following 1 profile is active: "e2e"` 与 `Started OauthServerApplication` |

---

## 实际执行命令（无敏感信息）

### CARD-05A（临时注入 + 编译可用性）
- `mvn -pl tiny-oauth-server -DskipTests compile -Dmaven.repo.local=/usr/local/data/repo`（引入临时 `spring-boot-properties-migrator` 后执行，结果 `BUILD SUCCESS`）

### CARD-05B / 05C / 05D（dev / ci / e2e 扫描）
- dev：`spring-boot:run` + `-Dspring-boot.run.profiles=dev` + `--server.port=19001`
- ci：`spring-boot:run` + `-Dspring-boot.run.profiles=ci` + `--server.port=19002`
- e2e：`spring-boot:run` + `-Dspring-boot.run.profiles=e2e` + `--server.port=19003`

> DB/E2E 凭证由环境变量注入（本报告不复述具体值）。

### CARD-05E（移除依赖后回归）
- compile：`mvn -pl tiny-oauth-server -DskipTests compile -Dmaven.repo.local=/usr/local/data/repo`（`BUILD SUCCESS`）
- test：`mvn -pl tiny-oauth-server test -Dmaven.repo.local=/usr/local/data/repo`（`Tests run: 1166, Failures: 0, Errors: 0, Skipped: 2`，`BUILD SUCCESS`）

---

## 剩余风险

本次“无命中”结论基于迁移器的典型输出关键词（见上方 rg 模式）在 dev/ci/e2e 日志中的检索结果；若迁移器在某些环境下以不同措辞输出或依赖日志级别，仍可能存在未被捕获的迁移项。后续若新增/调整相关配置项，建议再复扫一次。
