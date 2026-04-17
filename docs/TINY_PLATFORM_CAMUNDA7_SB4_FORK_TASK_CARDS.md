# Tiny Platform Camunda 7 / Spring Boot 4 Fork 改造任务卡

最后更新：2026-04-16

状态：可执行任务卡

适用仓库：

- 项目仓库：`/Users/bliu/code/tiny-platform`
- 兼容性源码仓库：`/Users/bliu/code/camunda-bpm-platform`

关联文档：

- `docs/TINY_PLATFORM_CAMUNDA7_SPRING_BOOT4_ENGINE_ONLY_STRATEGY.md`
- `docs/TINY_PLATFORM_CAMUNDA7_SB4_FORK_BASELINE.md`
- `docs/TINY_PLATFORM_CAMUNDA7_SB4_COORDINATE_EXPLICIT_MIGRATION_CARD.md`
- `docs/TINY_OAUTH_SERVER_SPRING_BOOT4_OFFICIAL_UPGRADE_CHECKLIST.md`

## 1. 现在是否应该开始拆卡

结论：

可以，现在就应该开始拆。

原因不是“已经准备开始大改 engine”，而是以下前提已经稳定：

- `tiny-platform` 已明确采用 `Engine Only + 自研 UI + 自研 IAM`
- 当前真实集成模块已明确为 `tiny-oauth-server`
- `camunda-bpm-platform` 已明确只是兼容性 fork 与 patch 来源
- 本阶段目标已明确为“最小可运行”，不是全量平台兼容

因此，当前最适合做的是：

先把 fork 改造拆成可执行的最小任务卡，再按证据推进

而不是：

直接并行大范围改 `engine` 和所有周边模块

## 2. 拆卡原则

本轮拆卡遵循以下原则：

1. 先拆 `spring-boot-starter`，后拆 `engine`
2. `engine` 只在 starter 路线被证明确实卡住时才开后备卡
3. 先保证编译和启动，再保证部署和 `ServiceTask`
4. 先在 fork 仓库完成最小 patch，再回到 `tiny-platform` 做集成验证
5. 每张卡必须有明确仓库归属、修改边界、验收标准

## 3. 仓库职责

### 3.1 `tiny-platform`

负责：

- 项目级决策
- 集成接入
- 业务验证
- 文档、回归与上线约束

### 3.2 `camunda-bpm-platform`

负责：

- fork 版本坐标与产物
- Spring Boot 4 兼容 patch
- starter / engine 源码改造
- 最小运行 smoke

## 4. 推荐执行顺序

推荐顺序如下：

1. `CARD-C7-01` fork 坐标与版本基线冻结
2. `CARD-C7-02` `spring-boot-starter` 依赖基线升级到 Spring Boot 4
3. `CARD-C7-03` `spring-boot-starter` AutoConfiguration 最小兼容 patch
4. `CARD-C7-04` Webapp / Security / REST 边界收口
5. `CARD-C7-05` fork 产物发布与 `tiny-platform` 接入验证
6. `CARD-C7-06` `tiny-platform` 核心流程 smoke
7. `CARD-C7-07` `engine` 后备卡，仅在 starter 路线不足时开启

## 5. 任务卡

### CARD-C7-01 fork 坐标与版本基线冻结（已冻结）

目标：

把本轮 fork 的坐标、版本命名、分支归属、最小成功标准先固定下来，避免后续一边改代码一边改口径。

主要仓库：

- `camunda-bpm-platform`
- `tiny-platform`

改动范围：

- 明确 fork 产物坐标命名规则
- 明确 Spring Boot 4 目标版本
- 明确是否保留原 groupId / artifactId 或改为内部命名
- 明确成功标准只包括：
  - 启动
  - BPMN 部署
  - `ServiceTask` 执行

验收标准：

- 有书面版本基线记录
- 有统一 fork 命名规则
- 团队对“本阶段不做 Webapps 全量适配”无歧义

禁止项：

- 不在本卡引入真实源码 patch
- 不在本卡讨论 Camunda 8 迁移细节

当前结果：

- 已完成，冻结文档见 `docs/TINY_PLATFORM_CAMUNDA7_SB4_FORK_BASELINE.md`
- 已冻结：
  - 首个兼容目标：`Camunda 7.24.0 + Spring Boot 4.0.0 + JDK 21`
  - 开发阶段 fork 版本：`7.24.0-tiny-sb4-01-SNAPSHOT`
  - 当前主线固定版：`7.24.0-tiny-sb4-01`
  - 命名策略：保留上游 `groupId/artifactId`，仅通过版本后缀区分
  - 主战场：`spring-boot-starter`

### CARD-C7-02 `spring-boot-starter` 依赖基线升级到 Spring Boot 4

目标：

先让 `spring-boot-starter` 相关模块在 Spring Boot 4 依赖面上具备可编译基础。

主要仓库：

- `camunda-bpm-platform`

主要模块：

- `spring-boot-starter/starter`
- 必要时包括：
  - `spring-boot-starter/starter-rest`
  - `spring-boot-starter/starter-webapp-core`
  - `spring-boot-starter/starter-security`

改动范围：

- `pom.xml` / BOM 对齐到 Spring Boot 4
- Jakarta 依赖面核对
- 明确哪些模块继续纳入编译，哪些先不作为本阶段目标

验收标准：

- `starter` 主路径可完成最小编译
- 编译错误集中在已知少量兼容点，而不是全局散乱失败
- 形成一份清晰的“编译阻塞点清单”

禁止项：

- 不在本卡直接大改 `engine`
- 不在本卡做业务仓库接入

当前进展（2026-04-16）：

- 已在 `camunda-bpm-platform/parent/pom.xml` 将 `version.spring-boot` 提升到 `4.0.0`
- 已补充 `rest-assured` 版本管理，解决 Spring Boot 4 BOM 不再覆盖该依赖导致的 POM 解析失败
- 真实首个编译阻塞并不在 starter，而是 `engine-dmn/feel-juel` 对 `org.camunda.bpm.impl.juel.jakarta.el.*` 的源码级引用
- 已通过对照构建确认：该阻塞在 `-Dversion.spring-boot=3.5.5` 下同样复现，因此它不是 Spring Boot 4 独有问题
- 已执行最小使能 patch：
  - 将源码层面对 relocated EL 包的引用统一回 `jakarta.el.*`
  - 将 `camunda-juel` 中的 `jakarta.el-api` 从 `optional` 调整为可传递编译依赖
  - 将 `camunda-engine-rest-jakarta` 的 transformer 规则来源从 `camunda-engine` 产物路径改为仓库内静态规则文件，修复 reactor `compile` 时把 `target/classes` 误当作 jar 的问题
- 当前结果：
  - `mvn -pl engine-dmn/feel-juel -am -DskipTests -DskipITs compile` 已通过
  - `mvn -pl engine-rest/engine-rest-jakarta -am -DskipTests -DskipITs compile` 已通过
  - `spring-boot-starter/starter` 已进入可编译状态
  - `spring-boot-starter/starter-rest` 已在完整生命周期下完成编译
  - 原始 starter 编译链已成功越过 `feel-juel` 与 `engine-rest-jakarta` 前置阻塞，正在继续向 `starter-security / webapp` 边界收口

执行规则补充：

- 对包含 `generate-sources` / Jakarta transformer 的模块，禁止再使用 `org.apache.maven.plugins:maven-compiler-plugin:...:compile` 直打编译目标做结论判断
- 这类模块必须使用 Maven 生命周期命令，例如：
  - `mvn -pl engine-rest/engine-rest-jakarta -am -DskipTests -DskipITs compile`
  - `mvn -pl spring-boot-starter/starter,spring-boot-starter/starter-rest -am -DskipTests -DskipITs compile`
- 否则会出现“源码生成未执行导致的假失败”，典型表现是 Jakarta 变体模块缺少生成后的父类或源文件

结论修正：

- `CARD-C7-02` 仍然是当前执行卡
- 但为进入 starter 兼容面，允许处理“阻挡 starter 路径的最小前置编译问题”
- `CARD-C7-07` 仍未正式开启，不应扩展成大范围 `engine` 重构
- `CARD-C7-02` 的阶段目标已基本达成：
  - starter 主路径与 starter-rest 路径都已具备可编译基础
  - 下一步重点不再是依赖基线本身，而是 `starter-security -> webapp` 的范围边界

### CARD-C7-03 `spring-boot-starter` AutoConfiguration 最小兼容 patch

目标：

把 Spring Boot 4 真正卡启动的自动配置问题收口在 starter 层解决。

主要仓库：

- `camunda-bpm-platform`

重点范围：

- `spring-boot-starter/starter`
- 条件注解
- JPA 相关配置
- `javax` 到 `jakarta` 的残留引用
- Bean 覆盖与最小启动行为

优先 patch 范围：

- `@ConditionalOnClass`
- `@ConditionalOnBean` / `@ConditionalOnClass` 的不兼容判断
- JPA 自动配置防御或开关
- Servlet 包迁移

验收标准：

- 最小 demo 应用可启动
- Camunda 引擎 bean 可创建
- 基础部署与启动日志进入可验证状态

禁止项：

- 不把本卡扩展成 Webapps 全量修复
- 不因个别问题过早回退到改 `engine`

当前进展（2026-04-16）：

- 已完成的最小兼容 patch：
  - `CamundaBpmAutoConfiguration` 去除对 `HibernateJpaAutoConfiguration` 的直接 class import，改为字符串类名延迟判断
  - `starter-rest` 中 Jersey 自动配置 import 已切换到 Spring Boot 4 包路径
  - `starter-security` 中已移除 Boot 3 已删除的 `SecurityProperties` / `ClientsConfiguredCondition` 直接依赖，改为本地条件类与常量兜底
  - `CamundaBpmActuatorConfiguration` 与两个 health indicator 已切换到 `org.springframework.boot.health.contributor.*`
  - `camunda-bpm-spring-boot-starter` 已补充 `org.springframework.boot:spring-boot-jdbc`
  - `camunda-juel` 已取消对 `jakarta.el` API 的 shade relocation，恢复标准 `jakarta.el.ExpressionFactory` SPI 暴露
- 当前状态：
  - `spring-boot-starter/starter` 编译通过
  - `starter-qa/integration-test-simple` 最小启动 smoke 已通过
  - Boot 4 真正卡点已从 starter 主模块启动链转移到后续是否继续兼容 `starter-security / webapp`

最小启动验证补充（2026-04-16）：

- 为避免 surefire 默认跳测掩盖启动问题，已在
  - `spring-boot-starter/starter-qa/integration-test-simple`
  上使用 `JUnitCore` 直接执行：
  - `mvn -DskipTests=false -Dexec.classpathScope=test -Dexec.mainClass=org.junit.runner.JUnitCore -Dexec.args='org.camunda.bpm.springboot.project.qa.simple.SimpleApplicationIT' org.codehaus.mojo:exec-maven-plugin:3.5.0:java`
- 本轮启动链路经历了两个真实 blocker：
  - 第一个 blocker：`No qualifying bean of type 'org.springframework.transaction.PlatformTransactionManager' available`
    - 根因：Spring Boot 4 的 JDBC 自动配置类已迁移到 `spring-boot-jdbc` 模块，starter 仅保留 `spring-jdbc`/`spring-tx` 还不足以触发 Boot 4 的 `DataSourceAutoConfiguration` 与 `DataSourceTransactionManagerAutoConfiguration`
    - 修复：在 `spring-boot-starter/starter/pom.xml` 增加 `org.springframework.boot:spring-boot-jdbc`
  - 第二个 blocker：`JuelExpressionManager` 初始化时触发 `VerifyError`
    - 根因：`camunda-juel` 产物仍将 `jakarta.el` shade 到内部包，导致 `ExpressionFactoryImpl` 运行时继承链变成 `org.camunda.bpm.impl.juel.jakarta.el.ExpressionFactory`，与 engine 侧期望的标准 `jakarta.el.ExpressionFactory` 不兼容
    - 修复：
      - 删除 `juel/pom.xml` 中对 `jakarta.el` 的 shade relocation
      - 将 SPI 资源切换为 `juel/src/main/resources/META-INF/services/jakarta.el.ExpressionFactory`
      - 对 `juel` 执行一次 `clean install`，清理旧的 relocated SPI 残留资源
- 最终结果：
  - H2 内存库成功创建
  - `ProcessEngine default` 成功创建
  - `SpringBootProcessApplication` 成功部署
  - `SimpleApplicationIT` 两个测试均通过：`OK (2 tests)`

执行规则补充：

- 对“打包形态发生变化”的模块，尤其是曾使用 shade / relocation 的模块，修改完 POM 或 SPI 资源后不能只跑 `install`
- 必须至少对该模块执行一次 `clean install`，避免 `target/classes` 中残留旧资源进入最终 jar，典型案例就是：
  - `camunda-juel` 旧的 `META-INF/services/org.camunda.bpm.impl.juel.jakarta.el.ExpressionFactory`

阶段结论更新：

- `CARD-C7-03` 的“Spring Boot 4 最小启动兼容”目标已基本达成
- 当前已满足：
  - Spring Boot 4 可启动
  - Camunda 引擎 bean 可创建
  - 最小 ProcessApplication smoke 可运行
- 下一步可按顺序继续收口 `CARD-C7-04` 的边界与后续 `tiny-platform` 接入验证

### CARD-C7-04 Webapp / Security / REST 边界收口

目标：

把本阶段要支持和不支持的边界在 fork 代码层明确下来，防止后续重复扩 scope。

主要仓库：

- `camunda-bpm-platform`
- `tiny-platform`

改动范围：

- 明确 `webapp` 在本阶段是否仅保持编译，不保证可用
- 明确 `starter-security` 不作为 `tiny-platform` 的生产方案
- 明确 `starter-rest` 是可选能力，不是必需能力

验收标准：

- 边界在文档和代码依赖上保持一致
- `tiny-platform` 不因默认引入错误模块而放大兼容面
- 有明确的“生产禁用 / 非生产可选”口径

禁止项：

- 不把调试环境保留 `cockpit` 误写成生产依赖

当前发现（2026-04-16）：

- `camunda-bpm-spring-boot-starter-security` 当前 POM 明确 `provided` 依赖：
  - `camunda-bpm-spring-boot-starter-webapp`
  - `camunda-bpm-spring-boot-starter-rest`
- 因此只要验证 `starter-security`，Maven 就会继续拉起：
  - `starter-webapp-core`
  - `starter-webapp`
  - `camunda-webapp-jakarta`
  - `camunda-webapp-webjar`
  - `webapps` 前端构建链（Node / npm）
- 这与 `tiny-platform` 当前确定的 `Engine Only + 自研 UI / IAM` 生产策略不一致
- `tiny-platform` 当前业务仓库侧已基本符合边界要求：
  - `tiny-oauth-server/pom.xml` 仅引入 `camunda-bpm-spring-boot-starter` 与 `camunda-bpm-spring-boot-starter-rest`
  - 未发现 `camunda-bpm-spring-boot-starter-webapp` / `camunda-bpm-spring-boot-starter-security` 直接依赖
  - `tiny-oauth-server/src/main/resources/application.yaml` 已显式设置 `camunda.bpm.webapp.enabled=false`
  - `tiny-oauth-server/src/main/resources/application-e2e.yaml` 已显式设置 `camunda.bpm.enabled=false`
  - `tiny-oauth-server/src/main/resources/application-e2e.yaml` 已排除 `CamundaBpmRestJerseyAutoConfiguration`
- 已新增构建级护栏：
  - `tiny-oauth-server/pom.xml` 通过 `maven-enforcer-plugin` 禁止引入：
    - `camunda-bpm-spring-boot-starter-webapp`
    - `camunda-bpm-spring-boot-starter-security`
    - `org.camunda.bpm.webapp:*`

边界结论补充：

- `starter-rest` 可以继续作为“可选能力”保留在本阶段编译验证范围内
- `starter-security` 不应再被视为 `tiny-platform` 生产接入前置条件
- `starter-security` 如需继续兼容，应明确标注为“非生产调试 / 兼容观察项”，不能反向扩大到 webapp 全量修复
- `tiny-platform` E2E 链路当前不应被视为 Camunda 可用性的验收口径：
  - 当前 E2E profile 已主动关闭 `camunda.bpm.enabled`
  - 当前 E2E profile 已主动排除 Camunda REST Jersey 自动配置
- 生产边界不再只依赖文档口径，已具备构建期失败保护

### CARD-C7-05 fork 产物发布与 `tiny-platform` 接入验证

目标：

把 fork 产物真正接到 `tiny-platform`，验证不是“demo 能跑、业务仓库不能接”。

主要仓库：

- `camunda-bpm-platform`
- `tiny-platform`

主要模块：

- `tiny-platform/pom.xml`
- `tiny-oauth-server/pom.xml`

改动范围：

- 将 `tiny-platform` 的 Camunda 依赖切到 fork 产物
- 保持现有 Engine Only 策略不变
- 验证 `tiny-oauth-server` 可编译、可启动

验收标准：

- `tiny-oauth-server` 使用 fork 产物后可启动
- 现有流程相关 bean 与接口不因 fork 坐标变化直接失效
- 有接入说明和回退方案

禁止项：

- 不在本卡同时推动业务逻辑重构

当前前置条件（2026-04-16）：

- `tiny-platform` 已建立独立验证分支：`codex/tiny-platform-sb4-camunda7-fork`
- 该分支中 `tiny-platform/pom.xml` 已提升到 Spring Boot `4.0.0`
- 本地 fork 后的 `camunda-bpm-spring-boot-starter` 产物已切到 Spring Boot `4.0.0` 依赖基线
- 因此本卡已不再是“接入前检查”，而是进入真实接入验证阶段
- 仍然不能在主线 Boot 3.x 环境中直接切换 fork 坐标，否则会引入混合 Spring Boot 基线风险

执行前提补充：

- 进入本卡前，需先明确以下两种路径之一：
  - 路径 A：`tiny-platform` 先升级到 Spring Boot 4，再切 fork 坐标
  - 路径 B：建立专门的 Boot 4 验证分支/沙箱，对 fork 产物做隔离接入验证
- 在这两个前提未满足前，本卡只做接入前检查，不直接改业务仓库依赖基线

当前进展（2026-04-16）：

- 已满足路径 B，并已在验证分支内完成真实接入：
  - `tiny-platform/pom.xml`
    - `spring-boot.version` -> `4.0.0`
    - `camunda-bom` -> `7.24.0-tiny-sb4-01-SNAPSHOT`
  - `tiny-oauth-server/pom.xml`
    - 保持 `Engine Only + starter-rest`
    - 已补充 `commons-logging` 显式依赖，用于修复 Boot 4 运行时 classpath 中缺少 `LogFactory` 的问题
- 本地 fork 产物已补齐并安装到 `/usr/local/data/repo`：
  - `camunda-bom`
  - `camunda-only-bom`
  - `camunda-bpm-spring-boot-starter`
  - `camunda-bpm-spring-boot-starter-rest`
  - `camunda-spin-dataformat-all`
  - `camunda-spin-bom`
  - `camunda-commons-bom`
  - `camunda-connect-bom`
- `tiny-oauth-server` 当前验证结果：
  - `mvn -pl tiny-oauth-server -DskipTests compile` 已通过
  - `mvn -pl tiny-oauth-server -DskipTests test-compile` 已通过
  - `mvn -pl tiny-oauth-server spring-boot:run -Dspring-boot.run.arguments=--server.port=19001` 已启动成功
  - `mvn -pl tiny-oauth-server test -Dmaven.repo.local=/usr/local/data/repo` 已全量通过
  - `CamundaSmokeVerifierApplication` 已运行通过，并完成自动 cleanup
  - 启动日志已确认：
    - `Camunda Platform Spring Boot Starter (v7.24.0-tiny-sb4-01-SNAPSHOT)`
    - `Process Engine default created`
    - JobExecutor 已启动
    - Camunda REST 已完成装配
    - `Tomcat started on port 19001 (http) with context path '/'`
    - `Started OauthServerApplication in 12.58 seconds`
  - 代表性测试运行已通过：
    - `mvn -pl tiny-oauth-server '-Dtest=MenuControllerRbacIntegrationTest' test`
    - 结果：`Tests run: 12, Failures: 0, Errors: 0, Skipped: 0`
  - 全量测试结果已确认：
    - `Tests run: 1166, Failures: 0, Errors: 0, Skipped: 2`
    - `BUILD SUCCESS`
- 当前本卡阶段结论：
  - `fork 产物发布与 tiny-platform 接入验证` 已越过“可编译 / 可启动 / 可回归”门槛
  - 剩余未完成项不再是 fork 接入本身，而是推进 `ITEM-04` / `ITEM-05` 并继续收口少量 warning 级技术债

版本显式化补充（2026-04-16）：

- 坐标显式化已完成：
  - `org.camunda.bpm:camunda-bom:7.24.0-tiny-sb4-01-SNAPSHOT`
- 本轮真实踩坑也已明确：
  - 仅安装 `camunda-bom` 不够
  - 还必须安装 `camunda-only-bom`、二级 import BOM 与 `camunda-spin-dataformat-all`
- 详细执行记录已收敛到：
  - `docs/TINY_PLATFORM_CAMUNDA7_SB4_COORDINATE_EXPLICIT_MIGRATION_CARD.md`

当前剩余问题：

- `ITEM-02` 已完成：
  - Boot 4 官方测试 starter 已引入
  - 业务测试源码已切到 `@MockitoBean` 与 Boot 4 新包路径
  - `src/test/java/org/springframework/...` shim 与 `AutoConfiguration.replacements` 已删除
- 全量测试矩阵现已跑完，结果为：
  - `Tests run: 1166, Failures: 0, Errors: 0, Skipped: 2`
- 当前仍需跟踪的观察项已经收敛为：
  - 2 个 `Skipped` 来自 `AuthenticationFlowE2eProfileIntegrationTest$FormLoginBehaviour`
  - 跳过原因均为环境/种子前提不满足时的 `Assumptions.abort(...)`，分别是：
    - E2E 账号/租户与库内 seed 数据不一致时，跳过“管理员表单登录成功路径”断言
    - `platform_admin` 在 e2e 数据中未具备 `/sys/tenants` 所需端点登记或权限条件时，跳过平台控制面透测
  - 测试侧已完成一轮 deprecated 收口：
    - `Jackson2AutoConfiguration` 直接引用已移除
    - `MappingJackson2HttpMessageConverter` 直接引用已移除
    - 相关回归验证结果：`Tests run: 35, Failures: 0, Errors: 0, Skipped: 0`
  - 当前残余 warning 主要来自：
    - `DatabaseIdempotentRepositoryTest` 的 2 处 varargs 精度 warning
    - 其他文件的聚合型 deprecated / unchecked 提示
  - Camunda snapshot metadata `401` 警告仍会出现，但当前已证实不阻塞构建、启动与测试
  - 这些问题属于“测试适配收口面 / 运行时噪音面”，不影响当前主应用最小启动、流程 smoke 与全量测试结论
  - 版本治理层面当前已完成显式收敛
  - 后续只需沿 `7.24.0-tiny-sb4-01-SNAPSHOT` 持续打补丁，不应再回退到模糊的 `7.24.0-SNAPSHOT`

新增收口子卡（2026-04-16）：

#### CARD-C7-05A ITEM-05 文档与证据收口

目标：

把 `ITEM-05` 已执行完成的事实在文档层完全对齐，避免评审阶段出现“报告写完成、清单还写待执行”的状态不一致。

主要仓库：

- `tiny-platform`

改动范围：

- `docs/TINY_OAUTH_SERVER_ITEM05_PROPERTIES_MIGRATOR_REPORT.md`
- `docs/TINY_OAUTH_SERVER_SPRING_BOOT4_OFFICIAL_UPGRADE_CHECKLIST.md`

验收标准：

- 报告中补齐每个 profile 的启动前提
- 报告中补齐至少一条“migrator 已真实进入运行类路径”的证据
- 升级清单不再把 `ITEM-05` 写成下一步待执行事项
- 升级清单明确链接到最终报告

禁止项：

- 不重复跑 `ITEM-05`
- 不借本卡扩展到新的 Boot 4 清理项

当前结果（2026-04-16）：

- 已完成：
  - `docs/TINY_OAUTH_SERVER_ITEM05_PROPERTIES_MIGRATOR_REPORT.md` 已补齐各 profile 启动前提与 migrator 运行证据
  - `docs/TINY_OAUTH_SERVER_SPRING_BOOT4_OFFICIAL_UPGRADE_CHECKLIST.md` 已改为“ITEM-05 已执行完成，不再列为下一步”

#### CARD-C7-05B fork 产物发布到 CI 可访问 Maven 仓库（推荐主路径）

目标：

把 Camunda fork 产物从“仅本地 `/usr/local/data/repo` 可见”提升到“GitHub Actions / 团队成员可直接解析”的状态，消除后端 workflow 对本地预装仓库的隐性依赖。

主要仓库：

- `camunda-bpm-platform`
- `tiny-platform`

建议目标仓库：

- GitHub Packages
- Nexus
- Artifactory

改动范围：

- `camunda-bpm-platform` 发布配置
- `tiny-platform/pom.xml` 或 CI `settings.xml` / workflow 中的仓库与凭证接入
- GitHub Actions secrets / variables

验收标准：

- CI 可解析以下显式坐标而不依赖开发机本地仓库：
  - `org.camunda.bpm:camunda-bom:7.24.0-tiny-sb4-01`
  - `org.camunda.bpm:camunda-only-bom:7.24.0-tiny-sb4-01`
  - `org.camunda.bpm.springboot:camunda-bpm-spring-boot-starter:7.24.0-tiny-sb4-01`
  - `org.camunda.bpm.springboot:camunda-bpm-spring-boot-starter-rest:7.24.0-tiny-sb4-01`
  - `org.camunda.spin:camunda-spin-dataformat-all:7.24.0-tiny-sb4-01`
- 至少一条后端 GitHub Actions workflow 在“无本地预装 fork 产物”的 runner 上通过依赖解析与启动/测试
- 凭证不写死在仓库源码中

禁止项：

- 不只发布 `camunda-bom` 而遗漏 `camunda-only-bom`、二级 BOM 或 `spin-dataformat-all`
- 不把 CI 成功建立在 runner 缓存偶然命中之上

当前结果（2026-04-17，已完成）：

- 发布侧已落地：
  - `camunda-bpm-platform/pom.xml` 已补充 GitHub Packages `distributionManagement`
  - 已新增发布 workflow：
    - `camunda-bpm-platform/.github/workflows/publish-camunda-fork-github-packages.yml`
  - 已新增发布说明：
    - `camunda-bpm-platform/docs/github-packages-publishing.md`
- 消费侧已落地：
  - `tiny-platform/pom.xml` 已增加受控 profile：
    - `camunda-github-packages`
  - 已新增 GitHub Actions 本地 action：
    - `.github/actions/setup-camunda-fork-java/action.yml`
  - 已将主要后端 workflow 切到统一认证入口，并补充 `packages: read`
- 运行说明已收敛到：
  - `docs/TINY_PLATFORM_CAMUNDA7_SB4_GITHUB_PACKAGES_RUNBOOK.md`
- 远端发布验证已完成：
  - `camunda-bpm-platform` 发布 workflow 成功：
    - <https://github.com/Sramler/camunda-bpm-platform/actions/runs/24518485540>
- 远端消费验证已完成：
  - `verify-auth-backend` 成功：
    - <https://github.com/Sramler/tiny-platform/actions/runs/24519082136>
  - `verify-migration-smoke-mysql` 成功：
    - <https://github.com/Sramler/tiny-platform/actions/runs/24519643624>
  - `verify-auth-db-residuals` 成功：
    - <https://github.com/Sramler/tiny-platform/actions/runs/24519644111>
- 异常收口说明：
  - 发布完成前曾出现一次瞬时失败：
    - <https://github.com/Sramler/tiny-platform/actions/runs/24518879704>
  - 失败原因为 `org.camunda.bpm:camunda-bom:7.24.0-tiny-sb4-01` 尚未完成首轮发布；发布成功后重跑已全部通过，因此不再构成 `CARD-C7-05B` blocker
- 凭证结论：
  - 本轮远端验证已证明 workflow 内的 `github.actor` / `github.token` fallback 可工作
  - `CAMUNDA_PACKAGES_USERNAME` / `CAMUNDA_PACKAGES_TOKEN` 继续保留为增强型配置，而非 `CARD-C7-05B` 完成前置

#### CARD-C7-05C workflow 内 checkout fork 并 `mvn install`（次优兜底）

目标：

在共享 Maven 仓库暂未就绪时，为 GitHub Actions 提供一个可工作的过渡路径：先在 runner 上构建并安装 Camunda fork，再回头构建 `tiny-platform`。

主要仓库：

- `tiny-platform`
- 必要时联动 `camunda-bpm-platform`

改动范围：

- 后端相关 GitHub Actions workflow
- 可能需要的 fork checkout / path / `maven.repo.local` 统一

验收标准：

- 受影响的后端 workflow 在 runner 上先完成 fork 安装，再完成 `tiny-oauth-server` 的 `test-compile` / `test` / `spring-boot:run`
- `tiny-platform` 与 `camunda-bpm-platform` 使用同一个 runner Maven 本地仓库路径
- 失败日志能区分“fork 构建失败”和“业务仓库构建失败”
- fallback 分支必须显式校验：
  - `CAMUNDA_FORK_REF`
  - `CAMUNDA_FORK_CHECKOUT_TOKEN`
- workflow 中对 repo variable / secret 的读取必须显式归一化，不能把“文档中的默认值”当作 GitHub 自动生效
- 至少完成一次本地共享 `MAVEN_REPO_LOCAL` smoke：
  - fork install
  - `tiny-oauth-server` 在同一 `MAVEN_REPO_LOCAL` 下 `test-compile`

禁止项：

- 不把本卡当作长期终态
- 不在纯前端 workflow 中无差别引入 Camunda fork 构建
- 不省略 `camunda-only-bom`、二级 BOM 与 `spin-dataformat-all` 的安装
- 不在 `CARD-C7-05B` 主路径上顺手改掉现有 Maven cache 语义，除非在实现报告中单列说明风险

Cursor 执行卡：

- `docs/TINY_PLATFORM_CAMUNDA7_SB4_CARD_C7_05C_CURSOR_TASK_CARD.md`

当前进展（2026-04-16）：

- 首批落地已完成，并已扩面到 3 条代表性后端 workflow：
  - `.github/workflows/verify-auth-backend.yml`
  - `.github/workflows/verify-migration-smoke-mysql.yml`
  - `.github/workflows/verify-auth-db-residuals.yml`
- 已新增本地复用 action：
  - `.github/actions/install-camunda-fork-local/action.yml`
- 实现约束已落地：
  - 默认仍走 `CARD-C7-05B` GitHub Packages 主路径
  - 仅当 `CAMUNDA_FORK_LOCAL_INSTALL_ENABLED=true` 时切入 `05C` fallback
  - fallback 分支显式归一化并校验：
    - `CAMUNDA_FORK_REF`
    - `CAMUNDA_FORK_CHECKOUT_TOKEN`
    - `MAVEN_REPO_LOCAL`
  - fallback 分支通过 `MAVEN_OPTS=-Dmaven.repo.local=...` 把同一 Maven 本地仓库透传给后续命令
- 实施报告：
  - `docs/TINY_PLATFORM_CAMUNDA7_SB4_CARD_C7_05C_IMPLEMENTATION_REPORT.md`

#### CARD-C7-05D `SNAPSHOT` 向固定版 `7.24.0-tiny-sb4-01` 迁移

目标：

在兼容补丁与 CI 分发路径稳定后，把主线消费从 `7.24.0-tiny-sb4-01-SNAPSHOT` 收敛到固定内部版本 `7.24.0-tiny-sb4-01`，提升构建可重复性。

主要仓库：

- `camunda-bpm-platform`
- `tiny-platform`

改动范围：

- fork 版本号
- `tiny-platform/pom.xml` 中的 `camunda-bom` 版本
- CI / 制品仓库的消费版本

验收标准：

- 已存在可被 CI 解析的固定版制品 `7.24.0-tiny-sb4-01`
- `tiny-platform` 主线与关键 workflow 不再依赖 `-SNAPSHOT`
- 固定版切换后，`tiny-oauth-server` 编译、全量测试与 Camunda smoke 均保持通过

禁止项：

- 在 CI 分发路径未稳定前过早切固定版
- 固定版切换后继续回退到模糊的 `7.24.0-SNAPSHOT`

当前进展（2026-04-16，已完成）：

- `camunda-bpm-platform` 全仓 POM 已从 `7.24.0-tiny-sb4-01-SNAPSHOT` 切换到 `7.24.0-tiny-sb4-01`
- `tiny-platform/pom.xml` 中的 `camunda-bom` 已切换到 `7.24.0-tiny-sb4-01`
- 消费侧依赖树已确认固定版解析结果：
  - `org.camunda.bpm.springboot:camunda-bpm-spring-boot-starter:7.24.0-tiny-sb4-01`
  - `org.camunda.bpm:camunda-engine:7.24.0-tiny-sb4-01`
  - `org.camunda.bpm.springboot:camunda-bpm-spring-boot-starter-rest:7.24.0-tiny-sb4-01`
  - `org.camunda.spin:camunda-spin-dataformat-all:7.24.0-tiny-sb4-01`
- `tiny-oauth-server` 全量测试已通过：
  - `Tests run: 1166, Failures: 0, Errors: 0, Skipped: 2`
- 最小 Camunda smoke verifier 已在固定版下执行成功
- 发布 workflow 与 `05C` fallback action 已补齐固定版所需的两段式安装约束：
  - 先安装 `spin/core`
  - 再安装 / 发布主最小制品集
- `CARD-C7-05D` 实施报告：
  - `docs/TINY_PLATFORM_CAMUNDA7_SB4_CARD_C7_05D_IMPLEMENTATION_REPORT.md`

### CARD-C7-06 `tiny-platform` 核心流程 smoke

目标：

验证 fork 产物在 `tiny-platform` 中满足本阶段真实目标，而不是只通过编译。

主要仓库：

- `tiny-platform`

重点验证：

- 启动
- BPMN 部署
- `ServiceTask` 执行
- 基础流程相关控制器或服务可用

建议验证对象：

- `ProcessController`
- 流程部署入口
- 多租户上下文与 Camunda bridge 基础链路

验收标准：

- 有一条最小真实流程 smoke 成功
- 至少有一组自动化或脚本化验证证据
- 问题能区分为 fork 问题还是项目集成问题

禁止项：

- 不把本卡扩大为完整工作流产品验收

当前进展（2026-04-16，已完成）：

- 本卡已完成最小真实流程 smoke，验证链路覆盖：
  - `tiny-oauth-server` Boot 4 启动
  - Camunda `Process Engine default` 创建
  - 最小 BPMN 部署
  - `ServiceTask` 执行
  - 流程实例结束
  - 部署级清理
- 本轮同时修复了一处与 smoke 直接相关的集成缺陷：
  - `ProcessEngineService.startProcessInstance(processKey, activeTenantId, variables)` 原实现误将 `activeTenantId` 传入 `RuntimeService.startProcessInstanceByKey(processKey, businessKey, variables)` 的 `businessKey` 槽位
  - 现已改为 `RuntimeService.createProcessInstanceByKey(processKey).processDefinitionTenantId(activeTenantId).setVariables(variables).execute()`
  - 这意味着 `/process/start` 的租户语义已从“参数名正确、运行语义错误”修正为“按租户选定义并启动实例”
- 已新增最小 smoke 验证入口：
  - `CamundaSmokeVerifierApplication`
  - `CamundaSmokeVerifier`
  - `CamundaSmokeDelegate`
  - 若未显式传入 `--server.port=...`，入口会自动注入 `--server.port=0`，避免与本机已有服务冲突
- 当前推荐的脚本化验证命令：
  - `mvn -pl tiny-oauth-server -q -DincludeScope=runtime -Dmdep.outputFile=target/classpath.txt dependency:build-classpath`
  - `java -cp "tiny-oauth-server/target/classes:$(cat tiny-oauth-server/target/classpath.txt)" com.tiny.platform.application.oauth.workflow.smoke.CamundaSmokeVerifierApplication`
- 关键日志证据：
  - `ENGINE-00001 Process Engine default created.`
  - `Camunda SB4 smoke ServiceTask executed, tenantId=camunda-sb4-smoke`
  - `Camunda SB4 smoke passed: deploymentId=..., instanceId=..., processKey=..., tenantId=camunda-sb4-smoke`
  - `Camunda SB4 smoke cleanup completed, deploymentId=...`
- 验证结论：
  - fork 产物在 `tiny-platform` 中已满足本阶段最小目标
  - 当前问题已不再停留在“只能编译/只能启动”，而是具备“可部署、可执行、可清理”的实证
  - `CARD-C7-07` 不应因本卡而自动开启，除非后续出现明确且可复现的 starter 无法收敛 blocker

### CARD-C7-07 `engine` 后备卡：仅在 starter 路线不足时开启

目标：

仅当证据表明 starter 层无法解决问题时，再有控制地打开 engine 改造面。

主要仓库：

- `camunda-bpm-platform`

开启条件：

- 已完成 `CARD-C7-02` 到 `CARD-C7-06`
- 仍存在明确 blocker
- blocker 已证明确实不在 starter 层

可能范围：

- `engine` 中与 Spring Boot 4 / Jakarta 运行时交互直接相关的最小 patch

验收标准：

- 每个 engine patch 都能说明“为什么 starter 层无解”
- 改动面可追踪、可回退、可解释

禁止项：

- 不把 `engine` 当作第一优先级主战场
- 不做与本阶段目标无关的清洁式重构

## 6. 当前建议

当前建议是：

继续按任务卡推进，但当前不应打开 `engine` 大范围改造。

也就是说：

`CARD-C7-01` 到 `CARD-C7-06` 已基本拿到最小成功证据；
接下来应聚焦 `tiny-oauth-server` 的 Boot 4 兼容层固化、技术债标注与后续退出策略，而不是反向扩张兼容范围。

补充收敛说明（2026-04-16）：

- 对 `tiny-oauth-server` 而言，Spring 官方升级指南中真正需要继续推进的事项，
  已单独收敛到：
  - `docs/TINY_OAUTH_SERVER_SPRING_BOOT4_OFFICIAL_UPGRADE_CHECKLIST.md`
- 当前推荐优先级为：
  1. Boot 4 正式 starter 坐标对齐：已完成
  2. Jackson 3 / Spring Security 7 序列化链路迁移：授权持久化路径已完成
  3. 测试基础设施从 Boot 3 兼容 shim 回归官方 Boot 4 测试 starter：已完成
- 这意味着后续工作重点应从“继续找能不能跑”切换到：
  - 清理仍停留在过渡态的官方升级项
  - 维持 Camunda fork 固定版发布与 CI 消费路径的稳定性

## 7. 下一步建议动作

如果继续推进，下一步应当是：

1. `CARD-C7-05A` 到 `CARD-C7-05D` 已完成当前轮收口：
   - `05B` 主路径已完成远端发布与 consumer workflow 闭环验证
   - `05C` 保留为仓库异常时的临时兜底
   - `05D` 固定版 `7.24.0-tiny-sb4-01` 已成为主线消费版本
2. 后续新增或调整后端 workflow 时，默认复用 `CARD-C7-05B` 主路径：
   - 直接消费 GitHub Packages 中的固定版 fork 产物
3. 仅当 package 访问或发布链路再次出现明确故障时，才临时启用 `CARD-C7-05C`：
   - 在受影响 workflow 中 checkout fork 并执行本地 `mvn install`
4. 继续记录当前收口状态：
   - `ITEM-01` 已完成
   - `ITEM-02` 已完成
   - `ITEM-03` 已完成授权持久化链路迁移
   - `ITEM-04` 已完成一轮审计与回归补强
   - `ITEM-05` 已完成一次性 migrator 扫描与回归验证
   - Web Jackson 2 路径仍保留，属于本轮接受的过渡态
5. 将当前 warning 级遗留项单独收口：
   - `DatabaseIdempotentRepositoryTest` varargs warning
   - 其余聚合型 deprecated / unchecked warning
6. 仅当出现新的、可复现且明确不在 starter/测试层的 blocker 时，才考虑开启 `CARD-C7-07`
