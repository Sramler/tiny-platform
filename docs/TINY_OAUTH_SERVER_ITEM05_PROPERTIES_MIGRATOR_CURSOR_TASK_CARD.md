# Tiny OAuth Server ITEM-05 Properties Migrator Cursor 任务卡

> 状态：可执行任务卡  
> 适用仓库：`/Users/bliu/code/tiny-platform`  
> 目标模块：`tiny-oauth-server`  
> 目标：临时引入 `spring-boot-properties-migrator`，分别跑 `dev / ci / e2e` 三条启动路径，收集并收口 Spring Boot 4 属性迁移风险  
> 非目标：本卡不处理 Camunda fork、Webapps、Jackson 3 全量统一、Security 7 运行时重构

---

## 1. 使用方式

把本文件中的“复制给 Cursor 的提示词”整段交给 Cursor，并要求它：

- 先阅读本文件列出的约束与相关文档；
- 再按本文件给出的执行顺序、命令模板、验收标准直接实施；
- 不要把本卡扩大成“顺手做其他 Boot 4 清理项”；
- 完成后必须返回：
  - 修改文件清单
  - 实际执行命令
  - 每个 profile 的日志结论
  - 最终是否发现属性迁移项
  - 清理后的剩余风险

---

## 2. 必读文档与规则

Cursor 在执行前必须先阅读：

- `AGENTS.md`
- `docs/TINY_OAUTH_SERVER_SPRING_BOOT4_OFFICIAL_UPGRADE_CHECKLIST.md`
- `docs/TINY_PLATFORM_CAMUNDA7_SB4_FORK_TASK_CARDS.md`
- `docs/TINY_PLATFORM_TESTING_PLAYBOOK.md`
- `tiny-oauth-server/src/main/resources/application.yaml`
- `tiny-oauth-server/src/main/resources/application-dev.yaml`
- `tiny-oauth-server/src/main/resources/application-ci.yaml`
- `tiny-oauth-server/src/main/resources/application-e2e.yaml`
- `.agent/src/rules/50-testing.rules.md`
- `.agent/src/rules/58-cicd.rules.md`
- `.agent/src/rules/90-tiny-platform.rules.md`
- `.agent/src/rules/91-tiny-platform-auth.rules.md`

官方依据：

- Spring Boot 4 Migration Guide  
  [https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide)
- Deprecated Application Properties  
  [https://docs.spring.io/spring-boot/appendix/deprecated-application-properties/index.html](https://docs.spring.io/spring-boot/appendix/deprecated-application-properties/index.html)

---

## 3. 当前已知事实

执行本卡前，以下结论已经成立，不需要 Cursor 重复分析：

- `tiny-oauth-server` 已完成 Spring Boot 4 基线迁移
- 当前全量测试结果为：
  - `Tests run: 1166, Failures: 0, Errors: 0, Skipped: 2`
- `ITEM-04` 已完成一轮代码审计与回归补强：
  - `PathPatternRequestMatcher` 已落地
  - 未检出 `AntPathRequestMatcher` / `MvcRequestMatcher` / `PortResolver`
  - `TenantContextFilterTest` 已显式锁定相对 `/login?redirect=...` 行为
- 已做过一次静态核对，目前未直接命中明显旧属性，但这不等于运行时完全没有迁移提示
- `tiny-oauth-server` 当前未检出 `@PropertySource` / `PropertySource(...)`，因此不会直接命中官方文档提到的“过晚加入 Environment 的属性不会被 migrator 感知”的高概率盲区

因此，本卡的目标不是“证明项目能跑”，而是：

用官方推荐的 `spring-boot-properties-migrator` 做一次性运行时扫描，把真正可能残留的属性迁移项查清楚，并在执行后移除该依赖。

---

## 4. 固定边界

### 4.1 本卡允许改动

- `tiny-oauth-server/pom.xml`
- `docs/TINY_OAUTH_SERVER_SPRING_BOOT4_OFFICIAL_UPGRADE_CHECKLIST.md`
- 必要时新增：
  - `docs/TINY_OAUTH_SERVER_ITEM05_PROPERTIES_MIGRATOR_REPORT.md`

### 4.2 本卡禁止改动

- 不改 Camunda fork 仓库
- 不改业务代码实现
- 不顺手清理其他 warning
- 不把 `spring-boot-properties-migrator` 作为长期依赖保留
- 不读取 `~/.zprofile` / `~/.zshrc` / `~/.bashrc`

### 4.3 环境变量读取约束

只能使用白名单环境变量，不允许读取用户 shell 配置文件。

推荐使用的环境变量：

- `DB_HOST`
- `DB_PORT`
- `DB_NAME`
- `DB_USER`
- `DB_PASSWORD`
- `MYSQL_PORT`
- `MYSQL_ROOT_PASSWORD`
- `E2E_DB_HOST`
- `E2E_DB_PORT`
- `E2E_DB_NAME`
- `E2E_DB_USER`
- `E2E_DB_PASSWORD`
- `E2E_FRONTEND_BASE_URL`

### 4.4 脏工作区约束

当前仓库不是干净工作区。

- 只能修改本卡允许改动的文件
- 不允许回滚、覆盖、格式化或顺手整理与本卡无关的已有改动
- 若执行过程中发现与本卡直接冲突的外部改动，必须在结果中明确记录

---

## 5. 成功标准

完成本卡后，必须满足：

1. 已对 `dev / ci / e2e` 三条启动路径分别执行一次运行时属性迁移扫描
2. 每条路径都有独立日志文件和扫描结论
3. 如果发现迁移项，必须明确：
   - 命中 profile
   - 命中属性
   - 官方建议替代项
   - 是否已处理
4. 如果未发现迁移项，也必须留下“已扫描、无命中”的书面证据
5. `spring-boot-properties-migrator` 在任务结束前已从 `pom.xml` 移除
6. 最终至少回归：
   - `mvn -pl tiny-oauth-server -DskipTests compile -Dmaven.repo.local=/usr/local/data/repo`
   - `mvn -pl tiny-oauth-server test -Dmaven.repo.local=/usr/local/data/repo`
7. 如果某个 profile 因非环境问题启动失败，必须单独记录为“启动阻塞错误”，不能伪装成“无迁移项”

---

## 6. 推荐执行顺序

按以下顺序执行，不要并行：

1. `CARD-05A` 临时注入 migrator 依赖
2. `CARD-05B` 扫描 `dev` profile
3. `CARD-05C` 扫描 `ci` profile
4. `CARD-05D` 扫描 `e2e` profile
5. `CARD-05E` 汇总结果、移除依赖、回归验证、同步文档

原因：

- 同一 Maven 模块禁止并发 `compile` / `test` / `spring-boot:run`
- `tiny-oauth-server/target` 共享，乱序并发容易制造假失败

---

## 6.1 公共运行骨架

所有 `dev / ci / e2e` 子卡都必须复用以下公共规则：

1. 日志目录固定为：
   - `/Users/bliu/code/tiny-platform/tiny-oauth-server/target/item05-properties-migrator`
2. 每次启动前先删除同名旧日志，避免读到陈旧输出
3. `spring-boot:run`、`compile`、`test` 都统一追加：
   - `-Dmaven.repo.local=/usr/local/data/repo`
4. 每次启动都必须校验：
   - 日志里是否出现目标 profile 生效信息
   - 日志里是否出现 migrator 提示
   - 日志里是否出现应用启动成功标记
   - 进程退出时是否做了显式停止与 `wait`
5. 启动成功标记建议至少检查以下任一项：
   - `Started OauthServerApplication`
   - `Started .* in .* seconds`
6. 属性迁移扫描建议至少检查以下关键词：
   - `PropertiesMigration`
   - `Properties migration`
   - `deprecated`
   - `renamed`
   - `replacement`
7. 如果日志里出现数据库连接失败、端口占用、缺少环境变量、前置服务不可达等问题：
   - 可记为“环境未满足”
8. 如果日志里出现与环境无关的应用启动异常：
   - 不能记为“无迁移项”
   - 必须在结果文档单列“启动阻塞错误”

推荐轮询骨架：

```bash
for _ in $(seq 1 90); do
  if ! kill -0 "${APP_PID}" 2>/dev/null; then
    break
  fi
  if rg -n "Started OauthServerApplication|Started .* in .* seconds|PropertiesMigration|Properties migration|deprecated|renamed|replacement|APPLICATION FAILED TO START|Exception" "${LOG_FILE}" >/dev/null 2>&1; then
    break
  fi
  sleep 2
done

kill "${APP_PID}" 2>/dev/null || true
wait "${APP_PID}" 2>/dev/null || true
```

---

## 7. 任务卡

### CARD-05A 临时注入 migrator 依赖

**目标**

在 `tiny-oauth-server/pom.xml` 中临时加入 `spring-boot-properties-migrator`，只用于本轮扫描。

**改动范围**

- `tiny-oauth-server/pom.xml`

**实现要求**

- 依赖使用：
  - `org.springframework.boot:spring-boot-properties-migrator`
- Maven `scope` 使用：
  - `runtime`
- 必须添加简短注释，明确这是 `ITEM-05` 临时依赖

**验收标准**

- `mvn -pl tiny-oauth-server -DskipTests compile -Dmaven.repo.local=/usr/local/data/repo` 通过
- 依赖已进入运行类路径

**禁止事项**

- 不要把它加成长期 starter
- 不要同时修改其他依赖坐标

---

### CARD-05B 扫描 `dev` profile

**目标**

以 `dev` profile 启动 `tiny-oauth-server`，收集 `spring-boot-properties-migrator` 输出。

**前置条件**

- 本机 MySQL 可达
- 白名单环境变量至少能提供一组有效数据库连接信息

**推荐环境归一化**

```bash
export ITEM05_DB_HOST="${DB_HOST:-127.0.0.1}"
export ITEM05_DB_PORT="${DB_PORT:-3306}"
export ITEM05_DB_NAME="${DB_NAME:-tiny_web}"
export ITEM05_DB_USER="${DB_USER:-root}"
export ITEM05_DB_PASSWORD="${DB_PASSWORD:-${E2E_DB_PASSWORD:-${MYSQL_ROOT_PASSWORD:-}}}"
```

若 `ITEM05_DB_PASSWORD` 为空：

- 将本卡记为“环境未满足”，不要伪造运行结论

**推荐执行方式**

```bash
mkdir -p /Users/bliu/code/tiny-platform/tiny-oauth-server/target/item05-properties-migrator

DEV_LOG=/Users/bliu/code/tiny-platform/tiny-oauth-server/target/item05-properties-migrator/dev.log
rm -f "${DEV_LOG}"

(
  cd /Users/bliu/code/tiny-platform
  SPRING_DATASOURCE_URL="jdbc:mysql://${ITEM05_DB_HOST}:${ITEM05_DB_PORT}/${ITEM05_DB_NAME}?useSSL=false&allowPublicKeyRetrieval=true&useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai&sessionVariables=transaction_isolation='READ-COMMITTED'&rewriteBatchedStatements=true" \
  SPRING_DATASOURCE_USERNAME="${ITEM05_DB_USER}" \
  SPRING_DATASOURCE_PASSWORD="${ITEM05_DB_PASSWORD}" \
  mvn -pl tiny-oauth-server spring-boot:run \
    -Dmaven.repo.local=/usr/local/data/repo \
    -Dspring-boot.run.profiles=dev \
    -Dspring-boot.run.arguments="--server.port=19001"
) >"${DEV_LOG}" 2>&1 &
APP_PID=$!
```

等待策略：

- 轮询日志，直到出现启动成功、迁移输出、或进程异常退出
- 收到结果后正常停止进程

**日志检查要求**

至少检查：

- `migrat`
- `deprecated`
- `replacement`
- `renamed`
- `PropertiesMigration`

**验收标准**

- `dev.log` 已生成
- 日志中能确认 `dev` profile 已实际生效
- 能明确回答：
  - `dev` 是否出现属性迁移提示
  - 若出现，具体属性是什么
  - 是否需要代码改动

---

### CARD-05C 扫描 `ci` profile

**目标**

以 `ci` profile 启动 `tiny-oauth-server`，收集 `spring-boot-properties-migrator` 输出。

**前置条件**

- 本机 MySQL 可达
- 至少有：
  - `MYSQL_ROOT_PASSWORD`
  - 或可回退到 `ITEM05_DB_PASSWORD`

**推荐环境归一化**

```bash
export ITEM05_CI_MYSQL_PORT="${MYSQL_PORT:-${DB_PORT:-3306}}"
export ITEM05_CI_MYSQL_ROOT_PASSWORD="${MYSQL_ROOT_PASSWORD:-${DB_PASSWORD:-${E2E_DB_PASSWORD:-}}}"
```

若 `ITEM05_CI_MYSQL_ROOT_PASSWORD` 为空：

- 将 `ci` 子卡标记为“环境未满足”

**推荐执行方式**

```bash
CI_LOG=/Users/bliu/code/tiny-platform/tiny-oauth-server/target/item05-properties-migrator/ci.log
rm -f "${CI_LOG}"

(
  cd /Users/bliu/code/tiny-platform
  MYSQL_PORT="${ITEM05_CI_MYSQL_PORT}" \
  MYSQL_ROOT_PASSWORD="${ITEM05_CI_MYSQL_ROOT_PASSWORD}" \
  mvn -pl tiny-oauth-server spring-boot:run \
    -Dmaven.repo.local=/usr/local/data/repo \
    -Dspring-boot.run.profiles=ci \
    -Dspring-boot.run.arguments="--server.port=19002"
) >"${CI_LOG}" 2>&1 &
APP_PID=$!
```

**日志检查要求**

与 `dev` 相同。

**验收标准**

- `ci.log` 已生成
- 日志中能确认 `ci` profile 已实际生效
- 已给出 `ci` profile 的迁移项结论

---

### CARD-05D 扫描 `e2e` profile

**目标**

以 `e2e` profile 启动 `tiny-oauth-server`，收集 `spring-boot-properties-migrator` 输出。

**前置条件**

- 必须具备 `E2E_DB_PASSWORD`
- 推荐同时具备：
  - `E2E_DB_HOST`
  - `E2E_DB_PORT`
  - `E2E_DB_NAME`
  - `E2E_DB_USER`
  - `E2E_FRONTEND_BASE_URL`

**说明**

- `application-e2e.yaml` 中已显式关闭 Camunda 主链：
  - `camunda.bpm.enabled=false`
- 因此本卡重点是看属性迁移提示，不是流程引擎验证

**推荐执行方式**

```bash
E2E_LOG=/Users/bliu/code/tiny-platform/tiny-oauth-server/target/item05-properties-migrator/e2e.log
rm -f "${E2E_LOG}"

(
  cd /Users/bliu/code/tiny-platform
  E2E_DB_HOST="${E2E_DB_HOST:-127.0.0.1}" \
  E2E_DB_PORT="${E2E_DB_PORT:-3306}" \
  E2E_DB_NAME="${E2E_DB_NAME:-tiny_web}" \
  E2E_DB_USER="${E2E_DB_USER:-root}" \
  E2E_DB_PASSWORD="${E2E_DB_PASSWORD}" \
  E2E_FRONTEND_BASE_URL="${E2E_FRONTEND_BASE_URL:-http://localhost:5173}" \
  mvn -pl tiny-oauth-server spring-boot:run \
    -Dmaven.repo.local=/usr/local/data/repo \
    -Dspring-boot.run.profiles=e2e \
    -Dspring-boot.run.arguments="--server.port=19003"
) >"${E2E_LOG}" 2>&1 &
APP_PID=$!
```

**验收标准**

- `e2e.log` 已生成
- 日志中能确认 `e2e` profile 已实际生效
- 若环境变量不足，必须明确写成“环境未满足”，不能写成“无迁移项”

---

### CARD-05E 汇总、移除依赖、回归验证

**目标**

把扫描结果形成书面结论，然后移除临时依赖，恢复工作区干净状态。

**改动范围**

- `tiny-oauth-server/pom.xml`
- `docs/TINY_OAUTH_SERVER_SPRING_BOOT4_OFFICIAL_UPGRADE_CHECKLIST.md`
- 可选新增：
  - `docs/TINY_OAUTH_SERVER_ITEM05_PROPERTIES_MIGRATOR_REPORT.md`

**必须完成**

1. 从 `tiny-oauth-server/pom.xml` 删除临时 migrator 依赖
2. 形成结果文档，至少包含：
   - 执行日期
   - 执行分支
   - 扫描 profile
   - 每个 profile 的启动前提
   - 每个 profile 的日志文件路径
   - 命中的迁移项
   - 未命中则写“无命中”
   - 环境不满足则明确写“环境未满足”
3. 更新升级清单中 `ITEM-05` 的实际执行结论
4. 最终回归：
   - `mvn -pl tiny-oauth-server -DskipTests compile -Dmaven.repo.local=/usr/local/data/repo`
   - `mvn -pl tiny-oauth-server test -Dmaven.repo.local=/usr/local/data/repo`

**验收标准**

- 工作区不再保留 `spring-boot-properties-migrator`
- 扫描结果已有文档证据
- 回归命令通过

---

## 8. 交付物要求

Cursor 最终应至少交付：

1. 修改文件清单
2. 实际执行命令
3. 结果文档
4. 日志文件路径
5. 回归结果
6. 剩余风险

建议结果文档命名：

- `docs/TINY_OAUTH_SERVER_ITEM05_PROPERTIES_MIGRATOR_REPORT.md`

建议最小报告表头：

```md
| profile | 状态 | 启动阻塞错误 | 日志路径 | 命中属性 | 替代属性/官方建议 | 是否已处理 | 备注 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| dev | 无迁移项 | 无 | ... | - | - | 不涉及 | ... |
| ci | 有迁移项 | 无 | ... | ... | ... | 已处理/未处理 | ... |
| e2e | 环境未满足 | 无 | ... | - | - | 不涉及 | 缺少 E2E_DB_PASSWORD |
```

---

## 9. 复制给 Cursor 的提示词

```text
请执行 tiny-platform 的 ITEM-05：使用 spring-boot-properties-migrator 做一次性属性迁移扫描，并严格遵守：

- AGENTS.md
- docs/TINY_OAUTH_SERVER_SPRING_BOOT4_OFFICIAL_UPGRADE_CHECKLIST.md
- docs/TINY_PLATFORM_CAMUNDA7_SB4_FORK_TASK_CARDS.md
- docs/TINY_PLATFORM_TESTING_PLAYBOOK.md
- .agent/src/rules/50-testing.rules.md
- .agent/src/rules/58-cicd.rules.md
- .agent/src/rules/90-tiny-platform.rules.md
- .agent/src/rules/91-tiny-platform-auth.rules.md
- docs/TINY_OAUTH_SERVER_ITEM05_PROPERTIES_MIGRATOR_CURSOR_TASK_CARD.md

本卡只做：
- 临时加入 spring-boot-properties-migrator
- 分别跑 dev / ci / e2e 三条启动路径
- 收集并总结属性迁移提示
- 任务结束前移除 migrator 依赖
- 更新升级清单与结果文档

本卡不要做：
- 不要改业务代码
- 不要顺手处理 Jackson / Security / Camunda 其他任务
- 不要并发运行同一 Maven 模块的 compile / test / spring-boot:run
- 不要读取 ~/.zprofile、~/.zshrc、~/.bashrc

执行要求：
- 直接改代码和文档，不要只给计划
- 启动日志统一保存到 tiny-oauth-server/target/item05-properties-migrator/
- 对 dev / ci / e2e 分别给出结论：
  - 有迁移项 / 无迁移项 / 环境未满足
- 若遇到非环境原因启动失败，必须额外写出“启动阻塞错误”，不能把它吞成“无迁移项”
- 若环境变量不足，必须明确写“环境未满足”，不能冒充扫描通过
- 最后必须移除 spring-boot-properties-migrator
- 最后必须回归：
  - mvn -pl tiny-oauth-server -DskipTests compile -Dmaven.repo.local=/usr/local/data/repo
  - mvn -pl tiny-oauth-server test -Dmaven.repo.local=/usr/local/data/repo

输出要求：
- 修改文件清单
- 执行命令
- 日志路径
- 每个 profile 的扫描结论
- 剩余风险
```

---

## 10. Codex 审计点

Codex 审计时重点检查：

1. `spring-boot-properties-migrator` 是否真的只做临时依赖
2. 三条 profile 是否都给出了真实结论，而不是只跑了其中一条
3. “环境未满足” 是否与“无迁移项”严格区分
4. 日志路径是否真实可追溯
5. 扫描结束后是否已移除 migrator 依赖
6. 最终回归是否仍保持：
   - `Tests run: 1166, Failures: 0, Errors: 0, Skipped: 2`
