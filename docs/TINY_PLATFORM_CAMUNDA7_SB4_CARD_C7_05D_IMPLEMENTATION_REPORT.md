# Tiny Platform CARD-C7-05D 实施报告

最后更新：2026-04-16

## 1. 实施结论

`CARD-C7-05D` 已完成。

本轮已把 Camunda 7 / Spring Boot 4 fork 主线消费从开发快照
`7.24.0-tiny-sb4-01-SNAPSHOT` 收敛到固定内部版本 `7.24.0-tiny-sb4-01`，
并补齐了固定版下真实可执行的本地安装、发布和 fallback 口径。

当前结论：

- `camunda-bpm-platform` 根版本及全仓继承版本已切到 `7.24.0-tiny-sb4-01`
- `tiny-platform/pom.xml` 中的 `camunda-bom` 已切到 `7.24.0-tiny-sb4-01`
- 消费侧依赖树、`tiny-oauth-server` 全量测试、最小 Camunda smoke 均已通过
- `05C` fallback action 与发布 workflow 已同步修正为固定版所需的两段式构建口径

## 2. 修改范围

### 2.1 `camunda-bpm-platform`

- 根版本与全仓继承的 `pom.xml`：
  - 从 `7.24.0-tiny-sb4-01-SNAPSHOT` 收敛到 `7.24.0-tiny-sb4-01`
- 发布 workflow：
  - `.github/workflows/publish-camunda-fork-github-packages.yml`
- 发布说明：
  - `docs/github-packages-publishing.md`

### 2.2 `tiny-platform`

- 消费侧 BOM 入口：
  - `pom.xml`
- `05C` fallback action：
  - `.github/actions/install-camunda-fork-local/action.yml`
- 任务与运行手册：
  - `docs/TINY_PLATFORM_CAMUNDA7_SB4_CARD_C7_05C_CURSOR_TASK_CARD.md`
  - `docs/TINY_PLATFORM_CAMUNDA7_SB4_CARD_C7_05C_IMPLEMENTATION_REPORT.md`
  - `docs/TINY_PLATFORM_CAMUNDA7_SB4_FORK_TASK_CARDS.md`
  - `docs/TINY_PLATFORM_CAMUNDA7_SB4_GITHUB_PACKAGES_RUNBOOK.md`
- 本报告：
  - `docs/TINY_PLATFORM_CAMUNDA7_SB4_CARD_C7_05D_IMPLEMENTATION_REPORT.md`

## 3. 固定版下的真实安装口径

本轮验证确认，固定版 `7.24.0-tiny-sb4-01` 不能简单沿用一步式 install。

必须采用两段式：

1. 先本地安装 `spin/core`
2. 再安装或发布主最小制品集

原因：

- `spin/dataformat-json-jackson` 需要
  `org.camunda.spin:camunda-spin-core:jar:tests:7.24.0-tiny-sb4-01`
- 如果直接对主最小制品集使用 `-Dmaven.test.skip=true`，该 `tests.jar` 不会被产出
- 如果只用 `-DskipTests`，`spring-boot-starter/starter` 仍会在 `testCompile`
  阶段命中 Boot 4 已移除的测试类引用

因此本轮同步修正了：

- `05C` fallback action
- GitHub Packages 发布 workflow

## 4. 实际执行命令

### 4.1 Camunda fork 本地固定版 install

```bash
cd /Users/bliu/code/camunda-bpm-platform
mvn -B -o \
  -pl spin/core \
  -am \
  -Dmaven.repo.local=/usr/local/data/repo \
  -DskipTests \
  -DskipITs \
  install
```

```bash
cd /Users/bliu/code/camunda-bpm-platform
mvn -B -o \
  -pl bom/camunda-bom,bom/camunda-only-bom,spin/bom,commons/bom,connect/bom,spin/dataformat-all,spring-boot-starter/starter,spring-boot-starter/starter-rest \
  -am \
  -Dmaven.repo.local=/usr/local/data/repo \
  -Dmaven.test.skip=true \
  -DskipTests \
  -DskipITs \
  install
```

### 4.2 消费侧依赖解析验证

```bash
cd /Users/bliu/code/tiny-platform
mvn -o -pl tiny-oauth-server -am dependency:tree \
  -Dincludes=org.camunda.bpm:camunda-engine,org.camunda.bpm.springboot:camunda-bpm-spring-boot-starter,org.camunda.bpm.springboot:camunda-bpm-spring-boot-starter-rest,org.camunda.spin:camunda-spin-dataformat-all \
  -Dmaven.repo.local=/usr/local/data/repo \
  -DskipTests
```

### 4.3 消费侧全量测试

```bash
cd /Users/bliu/code/tiny-platform
mvn -o -pl tiny-oauth-server test -Dmaven.repo.local=/usr/local/data/repo
```

### 4.4 最小 Camunda smoke

```bash
cd /Users/bliu/code/tiny-platform
mvn -o -pl tiny-oauth-server -q \
  -DincludeScope=runtime \
  -Dmdep.outputFile=target/classpath.txt \
  -Dmaven.repo.local=/usr/local/data/repo \
  dependency:build-classpath
```

```bash
cd /Users/bliu/code/tiny-platform
java -cp "tiny-oauth-server/target/classes:$(cat tiny-oauth-server/target/classpath.txt)" \
  com.tiny.platform.application.oauth.workflow.smoke.CamundaSmokeVerifierApplication
```

## 5. 验证结果

### 5.1 固定版 install 成功

- `spin/core` 预安装成功
- 主最小制品集 install 成功
- 结论：
  - 本地 Maven 仓库 `/usr/local/data/repo` 已具备固定版 `7.24.0-tiny-sb4-01`
    的最小可消费产物集

### 5.2 依赖树解析已切到固定版

`tiny-oauth-server` 当前解析结果为：

- `org.camunda.bpm.springboot:camunda-bpm-spring-boot-starter:7.24.0-tiny-sb4-01`
- `org.camunda.bpm:camunda-engine:7.24.0-tiny-sb4-01`
- `org.camunda.bpm.springboot:camunda-bpm-spring-boot-starter-rest:7.24.0-tiny-sb4-01`
- `org.camunda.spin:camunda-spin-dataformat-all:7.24.0-tiny-sb4-01`

### 5.3 `tiny-oauth-server` 全量测试通过

结果：

- `Tests run: 1166, Failures: 0, Errors: 0, Skipped: 2`
- `BUILD SUCCESS`
- 总耗时：`01:59 min`
- 完成时间：`2026-04-16T22:59:33+08:00`

### 5.4 最小 Camunda smoke 成功

固定版 smoke verifier 实际观测到：

- Spring Boot 启动 banner：
  - `Spring-Boot: (v4.0.0)`
  - `Camunda Platform: (v7.24.0-tiny-sb4-01)`
  - `Camunda Platform Spring Boot Starter: (v7.24.0-tiny-sb4-01)`
- 运行日志包含：
  - `ENGINE-00001 Process Engine default created.`
- smoke verifier 进程退出码为 `0`

结论：

- 固定版下的 Boot 4 启动、Engine 创建、最小流程链路保持可用

## 6. 收口后的当前口径

当前默认消费版本：

- `7.24.0-tiny-sb4-01`

当前不再作为主线默认消费的版本：

- `7.24.0-tiny-sb4-01-SNAPSHOT`

当前固定版构建规则：

- 本地 / CI fallback / 发布 workflow 如需重新组装最小制品集，必须：
  - 先安装 `spin/core`
  - 再执行主最小制品集 install 或 deploy

## 7. 剩余风险

- `spring-boot-starter/starter` 的 Boot 4 测试源码兼容问题仍在，因此不能把
  `-Dmaven.test.skip=true` 直接移除
- 固定版一旦发布到共享仓库，不应轻易覆盖同版本制品；后续补丁应明确升级版本号
- 本轮完成的是最小可运行与固定版收口，不代表 Camunda 7 在 Spring Boot 4
  下已完成全量兼容
