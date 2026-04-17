# Tiny OAuth Server Spring Boot 4 官方升级清单

最后更新：2026-04-17

状态：Working Baseline

适用仓库：

- 项目仓库：`/Users/bliu/code/tiny-platform`
- 目标模块：`tiny-oauth-server`

## 1. 文档目的

本文用于把 Spring 官方升级指南中，真正命中 `tiny-oauth-server` 的升级项收敛成一份可执行清单。

目标不是复述全部官方文档，而是回答以下问题：

1. 官方升级指南要求了什么
2. 这些变化在 `tiny-oauth-server` 中是否真实命中
3. 哪些应该列入正式升级范围
4. 哪些只是后续技术债，不应打断当前 Camunda 7 + Spring Boot 4 可运行基线

## 2. 当前项目事实

当前 `tiny-oauth-server` 已满足以下基线：

- 父工程 Spring Boot 版本：`4.1.0-SNAPSHOT`
- JDK 基线：`21`
- Servlet 基线：`6.1`
- `tiny-oauth-server` 已完成：
  - Boot 4 启动
  - Camunda 7 fork 集成
  - Camunda 最小 smoke
  - 多轮全量 / 定向测试收口
  - `2026-04-17` 补充完成 `tiny-oauth-server`、`tiny-web`、`tiny-oauth-client`、`tiny-oauth-resource` 在 `4.1.0-SNAPSHOT` 下的 `compile` 验证

因此，当前升级策略不应再是“大面积继续扫荡 Boot 3 组件”，而应是：

在不破坏现有可运行基线的前提下，只把 Spring 官方明确要求、且对本模块未来维护风险较高的项列入正式升级范围。

## 3. 官方来源

本清单依据以下 Spring 官方资料整理：

- Spring Boot 4.0 Migration Guide  
  [https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide)
- Spring Security 7 Web Migrations  
  [https://docs.spring.io/spring-security/reference/6.5/migration-7/web.html](https://docs.spring.io/spring-security/reference/6.5/migration-7/web.html)
- Spring 官方关于 Jackson 3 支持说明  
  [https://spring.io/blog/2025/10/07/introducing-jackson-3-support-in-spring](https://spring.io/blog/2025/10/07/introducing-jackson-3-support-in-spring)

补充判断依据：

- Spring Security 7.0.0 / Spring Authorization Server 7.0.0 本地源码与依赖产物
- 当前 `tiny-oauth-server` 已落地的 Boot 4 兼容实现

## 4. 正式升级范围

### 4.1 本轮必须纳入

#### ITEM-01 依赖坐标切换到 Boot 4 正式 starter 命名

官方依据：

- Boot 4 对 starter 体系进行了模块化重排，旧 starter 仍可用但已被视为 deprecated，未来会移除
- Boot 官方明确要求把旧命名 starter 替换为新的技术对齐命名

项目命中点（执行前）：

- 当前仍直接使用旧命名 starter 或第三方裸依赖：
  - `spring-boot-starter-web`
  - `spring-boot-starter-oauth2-resource-server`
  - `liquibase-core`
  - `spring-security-oauth2-authorization-server`
- 位置：
  - `tiny-oauth-server/pom.xml`

当前证据：

- `spring-boot-starter-web`：见 `tiny-oauth-server/pom.xml`
- `spring-boot-starter-oauth2-resource-server`：见 `tiny-oauth-server/pom.xml`
- `liquibase-core`：见 `tiny-oauth-server/pom.xml`
- `spring-security-oauth2-authorization-server`：见 `tiny-oauth-server/pom.xml`

推荐动作：

1. `spring-boot-starter-web` -> `spring-boot-starter-webmvc`
2. `spring-boot-starter-oauth2-resource-server` -> `spring-boot-starter-security-oauth2-resource-server`
3. `liquibase-core` -> `spring-boot-starter-liquibase`
4. 评估 `spring-security-oauth2-authorization-server` -> `spring-boot-starter-security-oauth2-authorization-server`

当前进展（2026-04-16）：

- 已在 `tiny-oauth-server/pom.xml` 完成 starter 坐标对齐：
  - `spring-boot-starter-web` -> `spring-boot-starter-webmvc`
  - `spring-boot-starter-oauth2-resource-server` -> `spring-boot-starter-security-oauth2-resource-server`
  - `spring-security-oauth2-authorization-server` -> `spring-boot-starter-security-oauth2-authorization-server`
  - `liquibase-core` -> `spring-boot-starter-liquibase`
- 过程中已确认一个实现细节：
  - `spring-boot-starter-liquibase` 的正确坐标应为
    `org.springframework.boot:spring-boot-starter-liquibase`
  - 不能误写成 `org.liquibase:spring-boot-starter-liquibase`
- 已完成验证：
  - `mvn -pl tiny-oauth-server -DskipTests compile -Dmaven.repo.local=/usr/local/data/repo`
  - `mvn -pl tiny-oauth-server -DskipTests test-compile -Dmaven.repo.local=/usr/local/data/repo`
  - `mvn -DskipTests compile -Dmaven.repo.local=/usr/local/data/repo`
  - 结果均为 `BUILD SUCCESS`

说明：

- 这项属于“正式升级范围”，因为它直接影响 Boot 4 后续小版本的官方对齐程度
- 这不是为了好看，而是为了减少后续被 deprecated starter 再次追着改

#### ITEM-02 测试基础设施从自制 Boot 3 兼容层回归 Boot 4 官方测试 starter

官方依据：

- Boot 4 的测试基础设施同样模块化
- 官方要求用 `spring-boot-starter-<technology>-test`
- 官方明确指出：`@WithMockUser` / `@WithUserDetails` 现在需要 `spring-boot-starter-security-test`
- 官方明确指出：`@MockBean` / `@SpyBean` 已移除，改为 `@MockitoBean` / `@MockitoSpyBean`
- 官方同时说明：当技术测试 starter 已齐备时，`spring-boot-starter-test` 不再是必须显式保留的唯一测试入口

项目命中点（执行前）：

- 执行前仍在维护一层 test-only 兼容 shim：
  - `src/test/java/org/springframework/...`
  - `AutoConfiguration.replacements`
  - `@MockBean` shim -> `@MockitoBean`
- 位置：
  - `tiny-oauth-server/src/test/java/org/springframework/...`
  - `tiny-oauth-server/src/test/resources/META-INF/spring/...`

推荐动作：

1. 将 `spring-security-test` 升级为 `spring-boot-starter-security-test`
2. 评估引入：
   - `spring-boot-starter-webmvc-test`
   - `spring-boot-starter-data-jpa-test`
   - `spring-boot-starter-liquibase-test`
   - 需要时加 `spring-boot-starter-security-oauth2-authorization-server-test`
3. 在技术测试 starter 覆盖稳定后，评估是否弱化或移除单独声明的 `spring-boot-starter-test`
4. 在兼容层稳定后，逐步淘汰：
   - `org.springframework.boot.test.mock.mockito.MockBean`
   - `org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc`
   - `AutoConfiguration.replacements`

当前进展（2026-04-16）：

- 已在 `tiny-oauth-server/pom.xml` 引入 Boot 4 官方测试 starter：
  - `spring-boot-starter-security-test`
  - `spring-boot-starter-webmvc-test`
  - `spring-boot-starter-data-jpa-test`
  - `spring-boot-starter-liquibase-test`
- 已将业务测试中的 Boot 3 测试 API 全部切换到 Boot 4 官方路径：
  - `@MockBean` -> `@MockitoBean`
  - `AutoConfigureMockMvc` -> `org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc`
  - JPA / WebMvc / HttpMessageConverters / Liquibase / Transaction 等测试自动配置类 -> Boot 4 新包路径
- 已删除全部 test-only 兼容层：
  - `tiny-oauth-server/src/test/java/org/springframework/...`
  - `tiny-oauth-server/src/test/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.replacements`
- 已完成验证：
  - `mvn -pl tiny-oauth-server -DskipTests test-compile -Dmaven.repo.local=/usr/local/data/repo`
  - `mvn -pl tiny-oauth-server '-Dtest=MenuControllerRbacIntegrationTest,AuthorizationServerUserInfoIntegrationTest,ExportServiceAsyncJpaIntegrationTest' test -Dmaven.repo.local=/usr/local/data/repo`
  - `mvn -pl tiny-oauth-server '-Dtest=DictControllerMockMvcTest,UserControllerIdempotencyIntegrationTest,ExportControllerMockMvcTest,AuthorizationServerUserInfoIntegrationTest,ExportServiceAsyncJpaIntegrationTest,ExportTaskServiceReadableQueryIntegrationTest' test -Dmaven.repo.local=/usr/local/data/repo`
  - `mvn -pl tiny-oauth-server test -Dmaven.repo.local=/usr/local/data/repo`
  - 结果：
    - `test-compile`：`BUILD SUCCESS`
    - 代表性测试：`Tests run: 14, Failures: 0, Errors: 0, Skipped: 0`
    - warning 收敛回归测试：`Tests run: 35, Failures: 0, Errors: 0, Skipped: 0`
    - 全量测试：`Tests run: 1166, Failures: 0, Errors: 0, Skipped: 2`

阶段结论：

- `ITEM-02` 已完成，从“自制 Boot 3 兼容层”回归到“Boot 4 官方测试 starter + 官方测试 API”
- 当前仍保留 `spring-boot-starter-test` 作为通用聚合测试依赖，但它已不再承担 Boot 3 兼容 shim 的角色
- 这轮已额外完成测试侧 deprecated 收口：
  - `Jackson2AutoConfiguration` 的直接测试引用已移除
  - `MappingJackson2HttpMessageConverter` 的直接测试引用已移除
- 当前剩余的是 warning 级技术债，而不是测试基础设施 blocker：
  - `DatabaseIdempotentRepositoryTest` 中仍有 2 处 varargs 精度 warning
  - 仍存在少量聚合型 deprecated / unchecked 提示，但已不再集中在 Boot 3 测试兼容层

#### ITEM-03 Jackson 3 迁移进入正式升级范围

官方依据：

- Spring 官方已明确把 Jackson 3 作为 Boot 4 / Framework 7 的默认前进方向
- Spring Security 7 中：
  - `org.springframework.security.jackson2.SecurityJackson2Modules` 已 `@Deprecated(forRemoval = true)`
  - 推荐迁移到 `org.springframework.security.jackson.SecurityJacksonModules`
- Spring Authorization Server 7 中：
  - `OAuth2AuthorizationServerJackson2Module` 已 `@Deprecated(forRemoval = true)`
  - 推荐迁移到 `org.springframework.security.oauth2.server.authorization.jackson.OAuth2AuthorizationServerJacksonModule`

项目命中点：

- 当前授权服务 ObjectMapper 仍停留在旧 `jackson2` 模块链路
- 当前还依赖自定义 allowlist hack 和 `sun.misc.Unsafe`

当前证据：

- `JacksonConfig` 仍显式注册：
  - `SecurityJackson2Modules`
  - `OAuth2AuthorizationServerJackson2Module`
- `LongAllowlistModule` 仍通过反射 + `Unsafe` 修改 allowlist

推荐动作：

1. `SecurityJackson2Modules` -> `SecurityJacksonModules`
2. `OAuth2AuthorizationServerJackson2Module` -> `OAuth2AuthorizationServerJacksonModule`
3. `Jackson2ObjectMapperBuilder` / `Jackson2ObjectMapperBuilderCustomizer`
   - 评估迁移为 Jackson 3 对应构建方式
4. 优先尝试移除：
   - `LongAllowlistModule`
   - `LongTypeIdResolver`
   - `sun.misc.Unsafe` 相关实现

说明：

- 这是当前最值得继续推进的运行时升级项
- 它不只是 warning 清理，而是未来 Spring Security 7.x / Boot 4.x 小版本升级的真实风险点
- 在 `ITEM-01` 完成后，当前编译告警最集中的区域已经进一步收敛到本项

当前进展（2026-04-16）：

- 已在 `tiny-oauth-server` 完成最小落地：
  - `OAuth2DataConfig` 已切换到 Spring Security 7 提供的
    `JsonMapperOAuth2AuthorizationRowMapper`
    与 `JsonMapperOAuth2AuthorizationParametersMapper`
  - `JacksonConfig` 已将授权持久化链路切换为 Jackson 3 `JsonMapper`
  - 运行时不再依赖：
    - `SecurityJackson2Modules`
    - `OAuth2AuthorizationServerJackson2Module`
    - `LongAllowlistModule` 中的 `Unsafe` allowlist hack
- 同时明确了一个实现边界：
  - `tiny-oauth-server` 当前仍是“双栈 Jackson”
  - Web / Camunda REST 仍保留 Jackson 2 路径
  - OAuth2 授权持久化已切到 Jackson 3 路径
  - 因为 Camunda REST 依赖仍会引入 `com.fasterxml.jackson.databind`
- 已完成验证：
  - `mvn -pl tiny-oauth-server -DskipTests compile -Dmaven.repo.local=/usr/local/data/repo`
  - `mvn -pl tiny-oauth-server -DskipTests test-compile -Dmaven.repo.local=/usr/local/data/repo`
  - `mvn -pl tiny-oauth-server -Dtest=JacksonConfigCoverageTest,OAuth2DataConfigTest,JwtTokenCustomizerTest test -Dmaven.repo.local=/usr/local/data/repo`
  - 结果均为 `BUILD SUCCESS`

阶段结论：

- `ITEM-03` 已完成“授权持久化链路”的正式迁移
- 但 `Jackson2ObjectMapperBuilder` / `Jackson2ObjectMapperBuilderCustomizer` 仍保留在 Web Jackson 2 路径中
- 因此本项更准确的状态应为：
  - `ITEM-03A` Jackson 3 授权持久化迁移：已完成
  - `ITEM-03B` Web Jackson 2 -> 3 全量统一：暂不纳入本轮

### 4.2 下轮建议纳入

#### ITEM-04 Spring Security 7 Web 行为差异补充验证

官方依据：

- Security 7 将登录跳转默认改为相对 URI
- Security 7 默认全面转向 `PathPatternRequestMatcher`
- 对直接构造 matcher、登录入口、过滤器处理 URL 的项目，需要主动验证

项目命中点：

- 当前模块存在自定义：
  - 登录页
  - OAuth2 授权服务器异常入口
  - MFA 跳转
  - TenantContextFilter
  - 多条基于 URI 的 matcher 规则

当前证据：

- `DefaultSecurityConfig` 已使用 `PathPatternRequestMatcher`
- `AuthorizationServerConfig` 仍手工配置 `LoginUrlAuthenticationEntryPoint("/login")`
- 存在大量 `/self/security/*`、`/oauth2/*`、前端 redirect 相关逻辑

推荐动作：

1. 增补显式回归：
   - `/login`
   - `/oauth2/authorize`
   - TOTP bind / verify
   - 前端 redirect 参数
   - 反向代理场景下 `Location` 头
2. 确认是否需要：
   - `LoginUrlAuthenticationEntryPoint#setFavorRelativeUris(false)`
3. 继续避免：
   - `AntPathRequestMatcher`
   - `MvcRequestMatcher`

说明：

- 这项更偏“行为回归验证”，不是当前最先动代码的一项
- 但它应该被纳入升级清单，否则后面代理层或前端回调容易出现隐性问题

当前审计结论（2026-04-16）：

- 已完成的代码面核对：
  - `DefaultSecurityConfig` 已切到 `PathPatternRequestMatcher`
  - 全仓未检出 `AntPathRequestMatcher`
  - 全仓未检出 `MvcRequestMatcher`
  - 全仓未检出 `PortResolver`
- 已具备的行为回归覆盖：
  - `AuthenticationFlowE2eProfileIntegrationTest`
    已覆盖 `prompt=none` 未登录时回 `redirect_uri?error=login_required`
  - `CustomLoginSuccessHandlerTest`
    已覆盖登录后 TOTP verify / bind 的前端跳转
  - `PartialMfaFormLoginIntegrationTest`
    已覆盖 partial MFA 会话对 `/self/security/totp/pre-bind` 等端点的真实访问
  - `TenantContextFilterTest`
    已补强为显式断言授权导航失败时使用相对 `/login?redirect=...`
    而不是绝对 `http(s)://...` 跳转
- 当前判断：
  - `AuthorizationServerConfig` 里直接构造 `LoginUrlAuthenticationEntryPoint("/login")`
    在现有行为和测试矩阵下是可接受的
  - 暂无证据表明当前必须显式调用
    `LoginUrlAuthenticationEntryPoint#setFavorRelativeUris(false)`
  - 若后续反向代理或外部网关明确要求绝对 `Location`，再将该项提升为代码改造任务更合适

#### ITEM-05 增加一次性配置属性迁移扫描

官方依据：

- Boot 4 官方建议在升级时临时加入 `spring-boot-properties-migrator`
- 用于分析并临时桥接属性迁移
- 迁移完成后移除

项目命中点：

- `tiny-oauth-server` profile 较多：
  - `application.yaml`
  - `application-dev.yaml`
  - `application-prod.yaml`
  - `application-e2e.yaml`
  - `application-ci.yaml`
  - `application-export-worker.yaml`
- 且涉及：
  - Quartz
  - Liquibase
  - Security
  - Camunda
  - JPA / DataSource

推荐动作：

1. 临时增加 `spring-boot-properties-migrator`
2. 在 `dev` / `ci` / `e2e` 启动路径下各跑一轮
3. 清理提示后移除该依赖

说明：

- 当前未发现明显命中的已废弃属性
- 但这项成本低、收益高，适合作为一次性核查项纳入

当前审计结论（2026-04-16）：

- 已完成一次静态核对：
  - `application.yaml`
  - `application-dev.yaml`
  - `application-prod.yaml`
  - `application-e2e.yaml`
- 对照 Boot 4 官方“deprecated application properties”后，当前未直接命中常见迁移项，例如：
  - `spring.sql.init.enabled`
  - `spring.jackson2.*`
  - `management.endpoints.enabled-by-default`
  - `logging.file.max-size`
  - `spring.main.web-environment`
- 已完成一次运行时属性迁移扫描（临时引入 `spring-boot-properties-migrator`，分别跑 `dev / ci / e2e` ）：
  - `dev`：日志中 `profile is active: "dev"` 且迁移器典型输出关键词（`PropertiesMigration` / `Properties migration` / `deprecated application properties` / `renamed.*application properties` / `replaced.*application properties` / `No properties migration`）无命中（见 `tiny-oauth-server/target/item05-properties-migrator/dev.log`）
  - `ci`：日志中 `profile is active: "ci"` 且迁移器典型输出关键词无命中（见 `tiny-oauth-server/target/item05-properties-migrator/ci.log`）
  - `e2e`：日志中 `profile is active: "e2e"` 且迁移器典型输出关键词无命中（见 `tiny-oauth-server/target/item05-properties-migrator/e2e.log`）
  - 结果报告已形成：
    - `docs/TINY_OAUTH_SERVER_ITEM05_PROPERTIES_MIGRATOR_REPORT.md`
- 因此当前判断是：
  - 未发现会阻塞 Spring Boot 4 运行的显性旧属性迁移项
  - 本卡为一次性核查项；除非相关配置发生变化，否则不需要重复扫描
- 已细化可执行任务卡：
  - `docs/TINY_OAUTH_SERVER_ITEM05_PROPERTIES_MIGRATOR_CURSOR_TASK_CARD.md`
- 已完成回归验证：
  - compile：`BUILD SUCCESS`
  - test：`Tests run: 1166, Failures: 0, Errors: 0, Skipped: 2`；`BUILD SUCCESS`

## 5. 不列入本轮正式升级范围

以下内容当前不建议列入“本轮必须完成”：

### 5.1 不单独以“清空所有 warning”为目标

原因：

- 当前 warning 很多是测试侧或兼容层的自然遗留
- 若直接以“全部清零”为目标，会把升级范围重新放大

应对策略：

- 把 warning 归并到所属专项：
  - Jackson 3 迁移
  - 测试 starter 迁移
  - 安全链路回归

### 5.2 不继续做大面积 Boot 3 组件扫荡式替换

原因：

- `tiny-oauth-server` 已满足当前业务里程碑：
  - 启动
  - Camunda smoke
  - 全量测试通过
- 继续全面扫荡式升级，会重新制造大范围不确定性

应对策略：

- 只处理官方明确要求且在本模块真实命中的高价值升级项

### 5.3 不把 `spring-boot-starter-classic` / `spring-boot-starter-test-classic` 当作目标态

原因：

- 它们更适合短期过渡，而不是长期收敛目标
- 当前我们已经通过自制兼容层把主线跑通，没有必要再额外引入一层 classic 兼容包来扩大变量

## 6. 当前建议顺序

推荐顺序如下：

1. ITEM-01 依赖坐标切换到 Boot 4 正式 starter 命名：已完成
2. ITEM-02 测试基础设施回归 Boot 4 官方测试 starter：已完成
3. ITEM-03 Jackson 3 迁移：授权持久化链路已完成，Web Jackson 2 路径暂保留
4. ITEM-04 Spring Security 7 Web 行为差异补充验证：已完成一轮审计与回归补强
5. ITEM-05 配置属性迁移扫描：已执行，`dev / ci / e2e` 无迁移命中

说明：

- 当前真正还值得继续投入的，主要是 `ITEM-03` 的运行时收敛，以及 Camunda fork 产物的 CI 分发与固定版本治理
- `ITEM-02` 已不再是阻塞项
- `ITEM-04` 与 `ITEM-05` 当前都已不再是下一步执行项
- Camunda fork 的后续任务卡见：
  - `docs/TINY_PLATFORM_CAMUNDA7_SB4_FORK_TASK_CARDS.md`

## 7. 与当前代码的直接对应关系

以下文件最值得作为升级入口：

- `tiny-oauth-server/pom.xml`
- `tiny-oauth-server/src/main/java/com/tiny/platform/core/oauth/config/jackson/JacksonConfig.java`
- `tiny-oauth-server/src/main/java/com/tiny/platform/core/oauth/config/jackson/LongAllowlistModule.java`
- `tiny-oauth-server/src/main/java/com/tiny/platform/core/oauth/config/jackson/LongTypeIdResolver.java`
- `tiny-oauth-server/src/main/java/com/tiny/platform/core/oauth/config/DefaultSecurityConfig.java`
- `tiny-oauth-server/src/main/java/com/tiny/platform/core/oauth/config/AuthorizationServerConfig.java`
- `tiny-oauth-server/src/test/java/com/tiny/platform/application/controller/menu/MenuControllerRbacIntegrationTest.java`
- `tiny-oauth-server/src/test/java/com/tiny/platform/core/dict/service/impl/DictServiceJpaIntegrationTest.java`

## 8. 结论

对于 `tiny-oauth-server`，Spring 官方升级指南中，真正应列入正式升级范围的重点不是“继续大面积 Boot 3 -> 4 扫荡”，而是：

1. 依赖坐标对齐到 Boot 4 正式 starter
2. Jackson 3 / Spring Security 7 序列化链路迁移
3. 测试基础设施从兼容 shim 回归官方 Boot 4 测试 starter

当前状态补充：

- 第 1 项已完成
- 第 3 项已完成
- 第 2 项已完成授权持久化路径迁移，但 Web Jackson 2 路径仍是本轮接受的过渡态

更准确地说：

当前项目已经跨过“能不能跑”的阶段；
下一阶段应进入“把仍然停留在过渡态的官方升级项收口”的阶段。
