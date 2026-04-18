# Tiny Platform `main..sb4` Remaining Diff Classification

最后更新：2026-04-17

比较基线：

- `main`: `fc496b6`
- `sb4`: `e682890`

生成口径：

- 命令：`git diff --name-only main..sb4`
- 剩余路径总数：`93`

分类结果：

- `业务`：`0`
- `治理`：`11`
- `兼容`：`72`
- `待商榷`：`10`

说明：

- 本清单只覆盖当前 `main..sb4` 的剩余差异
- 已经通过集合校验，确保 `93` 个路径全部被归类且无重复
- `待商榷` 不表示错误，表示“已超出纯编译/纯适配范畴，是否继续只留在 `sb4` 需要单独决定”

## 1. 业务

当前无剩余路径归类为 `业务`。

结论：

- 截至本快照，未发现“产品业务改动仍留在 `sb4`、但未进入 `main`”的剩余差异

## 2. 治理

定义：

- 升级任务卡、实施报告、runbook、基线说明、策略说明等过程治理资产

路径清单：

- `docs/TINY_OAUTH_SERVER_ITEM05_PROPERTIES_MIGRATOR_CURSOR_TASK_CARD.md`
- `docs/TINY_OAUTH_SERVER_ITEM05_PROPERTIES_MIGRATOR_REPORT.md`
- `docs/TINY_OAUTH_SERVER_SPRING_BOOT4_OFFICIAL_UPGRADE_CHECKLIST.md`
- `docs/TINY_PLATFORM_CAMUNDA7_SB4_CARD_C7_05C_CURSOR_TASK_CARD.md`
- `docs/TINY_PLATFORM_CAMUNDA7_SB4_CARD_C7_05C_IMPLEMENTATION_REPORT.md`
- `docs/TINY_PLATFORM_CAMUNDA7_SB4_CARD_C7_05D_IMPLEMENTATION_REPORT.md`
- `docs/TINY_PLATFORM_CAMUNDA7_SB4_COORDINATE_EXPLICIT_MIGRATION_CARD.md`
- `docs/TINY_PLATFORM_CAMUNDA7_SB4_FORK_BASELINE.md`
- `docs/TINY_PLATFORM_CAMUNDA7_SB4_FORK_TASK_CARDS.md`
- `docs/TINY_PLATFORM_CAMUNDA7_SB4_GITHUB_PACKAGES_RUNBOOK.md`
- `docs/TINY_PLATFORM_CAMUNDA7_SPRING_BOOT4_ENGINE_ONLY_STRATEGY.md`

## 3. 兼容

定义：

- 为 Spring Boot 4 / Camunda 7 fork / Jackson 3 / Spring Security 7 / CI 解析路径适配而存在
- 当前判断不属于产品功能新增

路径清单：

- `.github/actions/install-camunda-fork-local/action.yml`
- `.github/actions/setup-camunda-fork-java/action.yml`
- `.github/workflows/verify-auth-backend.yml`
- `.github/workflows/verify-auth-db-residuals.yml`
- `.github/workflows/verify-migration-smoke-mysql.yml`
- `.github/workflows/verify-scheduling-demo.yml`
- `.github/workflows/verify-scheduling-real-e2e-cross-tenant.yml`
- `.github/workflows/verify-scheduling-real-e2e.yml`
- `.github/workflows/verify-scheduling-seed-reset.yml`
- `.github/workflows/verify-webapp-real-e2e.yml`
- `pom.xml`
- `tiny-oauth-server/docs/README.md`
- `tiny-oauth-server/pom.xml`
- `tiny-oauth-server/src/main/java/com/tiny/platform/OauthServerApplication.java`
- `tiny-oauth-server/src/main/java/com/tiny/platform/core/oauth/config/AuthorizationServerConfig.java`
- `tiny-oauth-server/src/main/java/com/tiny/platform/core/oauth/config/DefaultSecurityConfig.java`
- `tiny-oauth-server/src/main/java/com/tiny/platform/core/oauth/config/OAuth2DataConfig.java`
- `tiny-oauth-server/src/main/java/com/tiny/platform/core/oauth/config/jackson/CustomWebAuthenticationDetailsJackson3Deserializer.java`
- `tiny-oauth-server/src/main/java/com/tiny/platform/core/oauth/config/jackson/JacksonConfig.java`
- `tiny-oauth-server/src/main/java/com/tiny/platform/core/oauth/config/jackson/LongAllowlistModule.java`
- `tiny-oauth-server/src/main/java/com/tiny/platform/core/oauth/config/jackson/LongTypeIdResolver.java`
- `tiny-oauth-server/src/main/java/com/tiny/platform/core/oauth/config/jackson/MultiFactorAuthenticationTokenJackson3Deserializer.java`
- `tiny-oauth-server/src/main/java/com/tiny/platform/core/oauth/config/jackson/MultiFactorAuthenticationTokenJackson3Serializer.java`
- `tiny-oauth-server/src/main/java/com/tiny/platform/infrastructure/idempotent/starter/autoconfigure/RedisIdempotentRepositoryConfiguration.java`
- `tiny-oauth-server/src/main/java/com/tiny/platform/infrastructure/scheduling/config/QuartzCustomizationConfig.java`
- `tiny-oauth-server/src/main/java/org/springframework/security/oauth2/server/authorization/authentication/OAuth2PasswordAuthenticationProvider.java`
- `tiny-oauth-server/src/main/java/org/springframework/security/oauth2/server/authorization/authentication/OAuth2PasswordAuthenticationToken.java`
- `tiny-oauth-server/src/main/java/org/springframework/security/oauth2/server/authorization/authentication/OAuth2PasswordGrantAuthenticationToken.java`
- `tiny-oauth-server/src/main/java/org/springframework/security/oauth2/server/authorization/config/annotation/web/configurers/OAuth2Utils.java`
- `tiny-oauth-server/src/main/java/org/springframework/security/oauth2/server/authorization/web/authentication/OAuth2PasswordAuthenticationConverter.java`
- `tiny-oauth-server/src/test/java/com/tiny/platform/application/controller/audit/AuthenticationAuditControllerApiEndpointGuardRealControllerIntegrationTest.java`
- `tiny-oauth-server/src/test/java/com/tiny/platform/application/controller/audit/AuthenticationAuditControllerRbacIntegrationTest.java`
- `tiny-oauth-server/src/test/java/com/tiny/platform/application/controller/audit/AuthorizationAuditApiEndpointGuardFilterChainIntegrationTest.java`
- `tiny-oauth-server/src/test/java/com/tiny/platform/application/controller/audit/AuthorizationAuditControllerApiEndpointGuardRealControllerIntegrationTest.java`
- `tiny-oauth-server/src/test/java/com/tiny/platform/application/controller/dict/DictControllerMockMvcTest.java`
- `tiny-oauth-server/src/test/java/com/tiny/platform/application/controller/dict/PlatformDictControllerApiEndpointGuardRealControllerIntegrationTest.java`
- `tiny-oauth-server/src/test/java/com/tiny/platform/application/controller/idempotent/ControllerIdempotencyAnnotationTest.java`
- `tiny-oauth-server/src/test/java/com/tiny/platform/application/controller/menu/MenuControllerApiEndpointGuardRealControllerIntegrationTest.java`
- `tiny-oauth-server/src/test/java/com/tiny/platform/application/controller/menu/MenuControllerRbacIntegrationTest.java`
- `tiny-oauth-server/src/test/java/com/tiny/platform/application/controller/permission/PermissionLookupControllerRbacIntegrationTest.java`
- `tiny-oauth-server/src/test/java/com/tiny/platform/application/controller/resource/ResourceControllerApiEndpointTemplateUriIntegrationTest.java`
- `tiny-oauth-server/src/test/java/com/tiny/platform/application/controller/resource/ResourceControllerRbacIntegrationTest.java`
- `tiny-oauth-server/src/test/java/com/tiny/platform/application/controller/role/RoleApiEndpointGuardFilterChainIntegrationTest.java`
- `tiny-oauth-server/src/test/java/com/tiny/platform/application/controller/role/RoleConstraintEnforceViolationLogIntegrationTest.java`
- `tiny-oauth-server/src/test/java/com/tiny/platform/application/controller/role/RoleConstraintRuleControllerIntegrationTest.java`
- `tiny-oauth-server/src/test/java/com/tiny/platform/application/controller/role/RoleConstraintRuleControllerRbacIntegrationTest.java`
- `tiny-oauth-server/src/test/java/com/tiny/platform/application/controller/role/RoleConstraintViolationLogControllerIntegrationTest.java`
- `tiny-oauth-server/src/test/java/com/tiny/platform/application/controller/role/RoleConstraintViolationObservabilityIntegrationTest.java`
- `tiny-oauth-server/src/test/java/com/tiny/platform/application/controller/role/RoleControllerApiEndpointGuardRealControllerIntegrationTest.java`
- `tiny-oauth-server/src/test/java/com/tiny/platform/application/controller/role/RoleControllerRbacIntegrationTest.java`
- `tiny-oauth-server/src/test/java/com/tiny/platform/application/controller/tenant/TenantApiEndpointGuardFilterChainIntegrationTest.java`
- `tiny-oauth-server/src/test/java/com/tiny/platform/application/controller/tenant/TenantControllerApiEndpointGuardRealControllerIntegrationTest.java`
- `tiny-oauth-server/src/test/java/com/tiny/platform/application/controller/tenant/TenantControllerRbacIntegrationTest.java`
- `tiny-oauth-server/src/test/java/com/tiny/platform/application/controller/user/PlatformUserManagementApiEndpointGuardRealControllerIntegrationTest.java`
- `tiny-oauth-server/src/test/java/com/tiny/platform/application/controller/user/UserApiEndpointGuardFilterChainIntegrationTest.java`
- `tiny-oauth-server/src/test/java/com/tiny/platform/application/controller/user/UserControllerApiEndpointGuardRealControllerIntegrationTest.java`
- `tiny-oauth-server/src/test/java/com/tiny/platform/application/controller/user/UserControllerIdempotencyIntegrationTest.java`
- `tiny-oauth-server/src/test/java/com/tiny/platform/application/controller/user/UserControllerRbacIntegrationTest.java`
- `tiny-oauth-server/src/test/java/com/tiny/platform/core/dict/service/impl/DictServiceJpaIntegrationTest.java`
- `tiny-oauth-server/src/test/java/com/tiny/platform/core/oauth/config/AuthorizationServerUserInfoIntegrationTest.java`
- `tiny-oauth-server/src/test/java/com/tiny/platform/core/oauth/config/DefaultSecurityConfigUserEndpointIntegrationTest.java`
- `tiny-oauth-server/src/test/java/com/tiny/platform/core/oauth/config/JwtTokenCustomizerTest.java`
- `tiny-oauth-server/src/test/java/com/tiny/platform/core/oauth/config/OAuth2DataConfigTest.java`
- `tiny-oauth-server/src/test/java/com/tiny/platform/core/oauth/config/jackson/JacksonConfigCoverageTest.java`
- `tiny-oauth-server/src/test/java/com/tiny/platform/core/oauth/integration/AuthenticationFlowE2eProfileIntegrationTest.java`
- `tiny-oauth-server/src/test/java/com/tiny/platform/core/oauth/tenant/TenantContextFilterTest.java`
- `tiny-oauth-server/src/test/java/com/tiny/platform/infrastructure/export/service/ExportServiceAsyncJpaIntegrationTest.java`
- `tiny-oauth-server/src/test/java/com/tiny/platform/infrastructure/export/service/ExportTaskServiceReadableQueryIntegrationTest.java`
- `tiny-oauth-server/src/test/java/com/tiny/platform/infrastructure/export/web/ExportControllerMockMvcTest.java`
- `tiny-oauth-server/src/test/java/com/tiny/platform/infrastructure/scheduling/controller/SchedulingApiEndpointGuardFilterChainIntegrationTest.java`
- `tiny-oauth-server/src/test/java/com/tiny/platform/infrastructure/scheduling/controller/SchedulingControllerApiEndpointGuardRealControllerIntegrationTest.java`
- `tiny-oauth-server/src/test/java/com/tiny/platform/infrastructure/scheduling/controller/SchedulingControllerRbacIntegrationTest.java`

## 4. 待商榷

定义：

- 不属于产品业务新增
- 但已经超出“纯编译适配 / 纯依赖对齐”的范围
- 经本轮二次收敛，`10` 个路径已细分为：
  - `建议回补 main`：`1`
  - `建议继续留在 sb4`：`4`
  - `建议随 tiny-web 冻结退出`：`5`

### 4.1 建议回补 `main`

- `tiny-oauth-server/src/main/java/com/tiny/platform/application/oauth/workflow/CamundaProcessEngineServiceImpl.java`
  - 核心原因不是 Boot 4 编译适配，而是 `startProcessInstance(processKey, activeTenantId, variables)` 的运行语义修正。
  - `main` 旧实现把 `activeTenantId` 传进了 `RuntimeService.startProcessInstanceByKey(processKey, businessKey, variables)` 的 `businessKey` 槽位，参数名与实际行为不一致。
  - `sb4` 新实现改为显式 builder：按 `tenantId` 选择流程定义，再设置变量并执行；这属于 tenant 启动语义修正，对仍保留 Camunda 7 集成的 `main` 同样成立。
  - 建议动作：回补 `main` 时优先保证 tenant 启动语义修正进入主线；若希望把 `@ConditionalOnProperty` 与语义修正拆成两个提交，也可以单独拆开处理。
  - 已开并执行任务卡：
    - `docs/TINY_PLATFORM_CAMUNDA7_MAIN_BACKPORT_CARD_C7_08.md`

### 4.2 建议继续留在 `sb4`

- `tiny-oauth-server/src/main/java/com/tiny/platform/application/oauth/workflow/ProcessController.java`
  - 当前变化主要是端点装配条件从 `@ConditionalOnBean(ProcessEngineService.class)` 切到 `camunda.bpm.enabled` 属性开关。
  - 这更像 `sb4` 分支围绕 Camunda fork 做的显式 enable/disable 策略收口，而不是必须回补 `main` 的功能缺陷修复。
  - `main` 现有按 Bean 存在装配的策略仍可工作，因此建议先留在 `sb4`；若后续 `main` 也统一采用显式属性门控，再一起回补。
- `tiny-oauth-server/src/main/java/com/tiny/platform/application/oauth/workflow/smoke/CamundaSmokeDelegate.java`
  - 文件名、日志与职责都明确指向 `Camunda SB4 smoke`，本质上是 fork 可运行性验证资产，不是主线产品代码。
  - 当前更适合作为 `sb4` 的持续验证工具保留。
- `tiny-oauth-server/src/main/java/com/tiny/platform/application/oauth/workflow/smoke/CamundaSmokeVerifier.java`
  - 这是最小 BPMN 部署、`ServiceTask` 执行、历史变量校验与 cleanup 的验证编排器，直接服务于 `Camunda 7 + Boot 4` 兼容证据链。
  - 在 `main` 仍以 SB3 为主线时，没有必要强行回补。
- `tiny-oauth-server/src/main/java/com/tiny/platform/application/oauth/workflow/smoke/CamundaSmokeVerifierApplication.java`
  - 这是脚本化 smoke 启动入口，价值在于让 `sb4` 分支能重复验证 fork 产物，而不是提供生产运行时能力。
  - 建议继续保留在 `sb4`；待未来默认主线切到 SB4 后，再决定是否一并纳入主线验证工具集。

### 4.3 建议随 `tiny-web` 冻结退出

共同依据：

- 仓库文档已多次明确：`tiny-web` 是“冻结中的历史模块”，不再作为长期演进主线的一部分。
- 现阶段若 `tiny-web` 出现阻断，只接受最小生存修复；不应因为它的 compile-through 适配，反向拉动主线治理或长期维护承诺。

路径清单：

- `tiny-web/src/main/java/com/tiny/web/TinyWebAllInOneApplication.java`
  - 仅为 Boot 4 下 `ErrorMvcAutoConfiguration` 包路径调整的 compile-through 适配。
- `tiny-web/src/main/java/com/tiny/web/oauth2/config/OAuth2AuthorizationServerConfig.java`
  - 仅为 Spring Authorization Server 配置类包路径变化的 compile-through 适配。
- `tiny-web/src/main/java/com/tiny/web/oauth2/password/OAuth2PasswordAuthenticationConverter.java`
  - 仅为冻结模块里的 password grant 常量与 API 兼容做最小适配。
- `tiny-web/src/main/java/com/tiny/web/oauth2/password/OAuth2PasswordAuthenticationProvider.java`
  - 仅为冻结模块里的 password grant token context 兼容做最小适配。
- `tiny-web/src/main/java/com/tiny/web/sys/security/ResourceAuthorizationManager.java`
  - 仅为 Spring Security 新接口签名变化做最小适配。

结论：

- 以上 `5` 个 `tiny-web` 文件都不建议回补 `main`，也不建议继续把它们视为 `sb4` 长期演进资产。
- 更合适的策略是：在 `tiny-web` 仍需参与编译时把它们视为临时生存修复；一旦 `tiny-web` 彻底冻结/退出主线，这组差异应同步退出。

## 5. 当前建议

- `业务`
  - 当前无需额外回补 `main`
- `治理`
  - 可按需决定是否需要让 `main` 也持有同一套升级证据文档
- `兼容`
  - 当前可继续保留为 `sb4-only`
- `待商榷`
  - 已完成二次细分，不再作为开放项悬置：
  - `建议回补 main`：`1`
  - `建议继续留在 sb4`：`4`
  - `建议随 tiny-web 冻结退出`：`5`
# 历史说明

> 本文是 2026-04-17 的分支差异盘点快照。当前运行态分支治理已切换为 **`sb4` 主干 / `sb3` 维护**；若本文中的旧表述与当前口径冲突，以 [docs/TINY_PLATFORM_SB4_SB3_BRANCH_STRATEGY.md](docs/TINY_PLATFORM_SB4_SB3_BRANCH_STRATEGY.md) 为准。
