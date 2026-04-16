# Tiny Platform Camunda 7 / Spring Boot 4 Fork 坐标显式化迁移卡

最后更新：2026-04-16

状态：Completed（历史阶段文档）

适用仓库：

- 项目仓库：`/Users/bliu/code/tiny-platform`
- Camunda fork 仓库：`/Users/bliu/code/camunda-bpm-platform`

关联文档：

- `docs/TINY_PLATFORM_CAMUNDA7_SB4_FORK_BASELINE.md`
- `docs/TINY_PLATFORM_CAMUNDA7_SB4_FORK_TASK_CARDS.md`

当前状态补充（2026-04-16）：

- 本卡记录的是“显式收敛到内部快照坐标”的阶段性结果
- 当前主线已继续推进 `CARD-C7-05D`
- 默认消费版本已从 `7.24.0-tiny-sb4-01-SNAPSHOT` 进一步收敛到 `7.24.0-tiny-sb4-01`

## 1. 文档目的

本文用于收敛一个已经暴露出来、但尚未正式落地的关键问题：

当前 `tiny-platform` 已经在消费本地安装的 Camunda Spring Boot 4 兼容 fork 产物，
但项目侧和 fork 侧仍然沿用上游风格的 `7.24.0-SNAPSHOT` 版本标识，
这会造成“本机能跑、团队和 CI 不够清晰”的治理风险。

本文不直接执行源码 patch，而是冻结：

- 为什么必须做“显式版本化”
- 显式版本化要改哪些仓库
- 推荐执行顺序
- 验收与回退口径

## 2. 当前事实

### 2.1 项目侧当前引用

当前 `tiny-platform/pom.xml` 已切换为：

- `org.camunda.bpm:camunda-bom:7.24.0-tiny-sb4-01-SNAPSHOT`

当前 `tiny-oauth-server` 通过 BOM 间接使用：

- `org.camunda.bpm.springboot:camunda-bpm-spring-boot-starter`
- `org.camunda.bpm.springboot:camunda-bpm-spring-boot-starter-rest`

### 2.2 当前为何已能显式解析

当前本机 Maven 本地仓库 `/usr/local/data/repo` 中，以下关键坐标已经完成本地安装：

- `org.camunda.bpm:camunda-bom:7.24.0-tiny-sb4-01-SNAPSHOT`
- `org.camunda.bpm:camunda-only-bom:7.24.0-tiny-sb4-01-SNAPSHOT`
- `org.camunda.bpm:camunda-engine:7.24.0-tiny-sb4-01-SNAPSHOT`
- `org.camunda.bpm.springboot:camunda-bpm-spring-boot-starter:7.24.0-tiny-sb4-01-SNAPSHOT`
- `org.camunda.bpm.springboot:camunda-bpm-spring-boot-starter-rest:7.24.0-tiny-sb4-01-SNAPSHOT`
- `org.camunda.spin:camunda-spin-dataformat-all:7.24.0-tiny-sb4-01-SNAPSHOT`
- `org.camunda.spin:camunda-spin-bom:7.24.0-tiny-sb4-01-SNAPSHOT`
- `org.camunda.commons:camunda-commons-bom:7.24.0-tiny-sb4-01-SNAPSHOT`
- `org.camunda.connect:camunda-connect-bom:7.24.0-tiny-sb4-01-SNAPSHOT`

这意味着当前状态已不再是“隐式吃本地 `7.24.0-SNAPSHOT`”，而是项目侧和本地仓库都显式对齐到了内部 fork 版本。

### 2.3 当前风险

继续沿用 `7.24.0-SNAPSHOT` 的主要问题是：

1. 如果本地仓库没有同时安装 `camunda-only-bom` 与二级 import BOM，Maven 会回退访问远端 snapshot 仓库
2. Camunda 私有仓库会对未授权请求返回 `401`，表现为“明明装了 camunda-bom，项目仍然解析失败”
3. `-DskipTests` 只跳过测试执行，不会跳过 `testCompile`，当前 starter 测试源码仍可能因为 Boot 4 移除的测试类而阻塞安装
4. 如果漏装 `camunda-spin-dataformat-all`，`tiny-oauth-server` 会在依赖解析阶段直接失败
5. 团队和 CI 若不复用同一套安装清单，问题会重复出现

## 3. 迁移结论

### 3.1 应当迁移

结论：

应当迁移到显式内部版本。

### 3.2 本轮迁移目标

本轮只做：

- 保留上游 `groupId`
- 保留上游 `artifactId`
- 将 `version` 从 `7.24.0-SNAPSHOT` 显式收敛到内部版本后缀

本轮不做：

- 改 `groupId`
- 改 `artifactId`
- 引入新的制品命名体系
- 一次性接入团队制品库发布流程

### 3.3 目标版本

按基线冻结文档，本轮目标版本为：

- 开发快照：`7.24.0-tiny-sb4-01-SNAPSHOT`
- 首个内部稳定版：`7.24.0-tiny-sb4-01`

## 4. 改造边界

### 4.1 `camunda-bpm-platform`

需要完成：

- 将 fork 仓库根版本切换到 `7.24.0-tiny-sb4-01-SNAPSHOT`
- 让 `camunda-bom`、`camunda-engine`、starter 相关产物全部产出同一显式版本
- 重新执行本地安装，覆盖到 `/usr/local/data/repo`

### 4.2 `tiny-platform`

需要完成：

- 将 `tiny-platform/pom.xml` 中的 `camunda-bom` 版本切到 `7.24.0-tiny-sb4-01-SNAPSHOT`
- 不修改 `tiny-oauth-server/pom.xml` 中的 starter `artifactId`
- 继续保持 `Engine Only + starter-rest` 的最小接入面

### 4.3 暂不触碰

本卡不要求改动：

- `tiny-web`
- Camunda Webapps
- `starter-security` 的生产接入策略
- 业务层 Camunda API 封装

## 5. 推荐执行顺序

1. 在 `camunda-bpm-platform` 中统一版本为 `7.24.0-tiny-sb4-01-SNAPSHOT`
2. 安装 starter 主链产物
3. 安装 `camunda-only-bom` 与二级 import BOM
4. 安装项目真实使用到的补充产物，例如 `camunda-spin-dataformat-all`
5. 在 `tiny-platform` 中切 BOM 版本
6. 执行 `tiny-oauth-server` 的编译、测试和最小流程 smoke
7. 记录回退点和接入结论

## 6. 建议执行清单

### 6.1 Camunda fork 仓库

重点文件：

- `/Users/bliu/code/camunda-bpm-platform/pom.xml`
- `/Users/bliu/code/camunda-bpm-platform/parent/pom.xml`
- `/Users/bliu/code/camunda-bpm-platform/spring-boot-starter/starter/pom.xml`
- `/Users/bliu/code/camunda-bpm-platform/spring-boot-starter/starter-rest/pom.xml`
- `/Users/bliu/code/camunda-bpm-platform/spring-boot-starter/starter-security/pom.xml`

建议命令：

```bash
cd /Users/bliu/code/camunda-bpm-platform
mvn versions:set -DnewVersion=7.24.0-tiny-sb4-01-SNAPSHOT \
  -DgenerateBackupPoms=false -DprocessAllModules=true
```

关键说明：

- 当前阶段不要只用 `-DskipTests`
- 应优先使用 `-Dmaven.test.skip=true`
- 原因是 `spring-boot-starter/starter` 的测试源码仍有 Boot 3 测试类引用，`testCompile` 会失败

第一组安装命令：

```bash
cd /Users/bliu/code/camunda-bpm-platform
mvn -pl spring-boot-starter/starter,spring-boot-starter/starter-rest -am \
  -Dmaven.repo.local=/usr/local/data/repo -Dmaven.test.skip=true install
```

第二组安装命令：

```bash
cd /Users/bliu/code/camunda-bpm-platform
mvn -pl bom/camunda-only-bom,spin/bom,commons/bom,connect/bom -am \
  -Dmaven.repo.local=/usr/local/data/repo -Dmaven.test.skip=true install
```

第三组安装命令：

```bash
cd /Users/bliu/code/camunda-bpm-platform
mvn -pl spin/dataformat-all -am \
  -Dmaven.repo.local=/usr/local/data/repo -Dmaven.test.skip=true install
```

### 6.2 项目仓库

重点文件：

- `/Users/bliu/code/tiny-platform/pom.xml`

建议命令：

```bash
cd /Users/bliu/code/tiny-platform
mvn -pl tiny-oauth-server -DskipTests compile -Dmaven.repo.local=/usr/local/data/repo
mvn -pl tiny-oauth-server test -Dmaven.repo.local=/usr/local/data/repo
```

最小 smoke 可继续复用：

```bash
cd /Users/bliu/code/tiny-platform
mvn -pl tiny-oauth-server -q -DincludeScope=runtime \
  -Dmdep.outputFile=target/classpath.txt dependency:build-classpath
java -cp "tiny-oauth-server/target/classes:$(cat tiny-oauth-server/target/classpath.txt)" \
  com.tiny.platform.application.oauth.workflow.smoke.CamundaSmokeVerifierApplication
```

## 6.3 本轮实际执行结果（2026-04-16）

已完成：

- `camunda-bpm-platform` 全模块版本已切到 `7.24.0-tiny-sb4-01-SNAPSHOT`
- `tiny-platform/pom.xml` 已切到 `org.camunda.bpm:camunda-bom:7.24.0-tiny-sb4-01-SNAPSHOT`
- `tiny-oauth-server` 依赖树已确认解析到：
  - `camunda-bpm-spring-boot-starter:7.24.0-tiny-sb4-01-SNAPSHOT`
  - `camunda-engine:7.24.0-tiny-sb4-01-SNAPSHOT`
  - `camunda-engine-rest-core-jakarta:7.24.0-tiny-sb4-01-SNAPSHOT`
  - `camunda-spin-dataformat-all:7.24.0-tiny-sb4-01-SNAPSHOT`
- `mvn -pl tiny-oauth-server -DskipTests compile -Dmaven.repo.local=/usr/local/data/repo` 已通过
- `mvn -pl tiny-oauth-server test -Dmaven.repo.local=/usr/local/data/repo` 已通过
  - 结果：`Tests run: 1166, Failures: 0, Errors: 0, Skipped: 2`
- `CamundaSmokeVerifierApplication` 已运行成功
  - 日志已确认 `Process Engine default created`
  - 日志已确认 `Camunda SB4 smoke cleanup completed`

## 7. 验收标准

以下条件同时满足，才视为本卡完成：

1. `camunda-bpm-platform` 已能产出 `7.24.0-tiny-sb4-01-SNAPSHOT`
2. 本地 Maven 仓库中 `camunda-bom`、`camunda-only-bom`、二级 BOM 与 `spin-dataformat-all` 已安装
3. `tiny-platform/pom.xml` 已切到显式内部版本
4. `tiny-oauth-server` 编译通过
5. 全量测试通过
6. 最小流程 smoke 通过

## 8. 回退策略

如果显式版本迁移过程中出现问题，可按以下顺序回退：

1. 先回退 `tiny-platform/pom.xml` 的 `camunda-bom` 版本
2. 保留 `camunda-bpm-platform` 中的 patch 分支，不立即删除
3. 如需继续验证，可暂时回到当前本地可工作的 `7.24.0-SNAPSHOT` 过渡态

需要强调：

`7.24.0-SNAPSHOT` 只能作为短期过渡回退点，不能作为最终长期状态。

## 9. 一句话结论

显式版本化已经落地完成，当前唯一推荐入口就是：

`7.24.0-tiny-sb4-01`

后续若继续打补丁，应在这个版本线上递进，而不是再回到含糊的 `7.24.0-SNAPSHOT`。
