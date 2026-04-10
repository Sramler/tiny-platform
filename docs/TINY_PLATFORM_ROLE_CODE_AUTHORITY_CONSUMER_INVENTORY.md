# CARD-09C1 `ROLE_*` authority 消费点 inventory（keep/migrate/test-only）

> 目的：在 `CARD-09B3` 后，先盘清 runtime / JWT / Session / downstream 对 `ROLE_*` authority 的真实消费点，固化 `keep-list`、`migrate-list`、`test-only list`，为 `CARD-09C2/09C3` 行为收缩做输入。
> 边界：本卡不改默认行为，不删除 `role.code` authority。

## 1. 盘点方式（最小观测）

本轮使用以下仓库内命令盘点“真实代码消费点”（不是只看文档）：

- `rg "class SecurityUserAuthorityService|class JwtTokenCustomizer|class TinyPlatformJwtGrantedAuthoritiesConverter" tiny-oauth-server/src/main/java`
- `rg "startsWith\\(\"ROLE_|\\^ROLE_|replaceFirst\\(\"\\^ROLE_\" tiny-oauth-server/src/main/java`
- `rg "@PreAuthorize\\(\"[^\"]*ROLE_|hasAuthority\\('ROLE_'" tiny-oauth-server/src/main/java`
- `rg "ROLE_|GrantedAuthority|hasAuthority|authorities" tiny-web/src/main/java`
- `rg "ROLE_" tiny-oauth-server/src/test/java --files-with-matches`

结论：主链运行时没有 `@PreAuthorize("hasRole...")` / `hasAuthority("ROLE_*")` 的业务判定残留；`ROLE_*` 主要仍在 authority 生产与 JWT 读写兼容链路，以及 workflow bridge 的下游 group 映射。

## 2. Runtime/JWT/Session 消费点清单（含 tiny-oauth-server + tiny-web）

### 2.1 Keep-list（当前合法保留，09C1 不切行为）

1) `tiny-oauth-server/src/main/java/com/tiny/platform/core/oauth/security/SecurityUserAuthorityService.java`
- 现状：`buildRoleCodeAuthorities()` 将 `role.code`（如 `ROLE_TENANT_ADMIN`）并入运行态 `authorities`。
- 保留原因：当前主链仍处于 permission + role.code 混合兼容阶段；09C1 只做盘点，不改默认行为。

2) `tiny-oauth-server/src/main/java/com/tiny/platform/core/oauth/config/JwtTokenCustomizer.java`
- 现状：`authorities` claim 直接写入运行态 authority 集合（含 `ROLE_*`）；`permissions` claim 为 `:` 风格权限子集。
- 保留原因：现网 JWT 兼容窗口仍依赖 `authorities` 全量语义；09C3 再做“新签发 token permission 为主”收缩。

3) `tiny-oauth-server/src/main/java/com/tiny/platform/core/oauth/config/TinyPlatformJwtGrantedAuthoritiesConverter.java`
- 现状：Bearer 侧读取 JWT `authorities` + `permissions` 并集，`ROLE_*` 会被还原为 `GrantedAuthority`。
- 保留原因：与 `JwtTokenCustomizer` 成对兼容，09C1 不变更。

### 2.2 Migrate-list（应迁到显式 `roleCodes`，09C2 执行）

1) `tiny-oauth-server/src/main/java/com/tiny/platform/application/oauth/workflow/CamundaIdentityBridgeFilter.java`
- 现状：把 `auth.getAuthorities()` 中的 `ROLE_*` 通过 `replaceFirst("^ROLE_", "")` 映射成 Camunda groups。
- 判定：这是“合法但应迁移”的典型角色码消费者，不应长期从通用 authority 推导角色组。
- 09C2 目标：改为消费显式 `roleCodes` 契约（或其等价显式字段），不再依赖 `authorities` 中 `ROLE_*`。

2) `tiny-web/src/main/java/com/tiny/web/sys/model/User.java`
- 现状：`getAuthorities()` 直接把 `role.getName()`（示例为 `ROLE_ADMIN`）映射为 `GrantedAuthority`。
- 判定：这是 runtime authority 生产点，当前仍是角色码直出模式。
- 09C2/09C3 目标：迁到显式 `roleCodes` 或 permission-style authority 的明确契约，避免继续把角色码作为通用权限判断输入。

3) `tiny-web/src/main/java/com/tiny/web/sys/security/ResourceAuthorizationManager.java`
- 现状：对 `auth.getAuthorities()` 逐项传给 `resourceService.hasAccess(role, path, method)`，当前入参语义实质依赖 `ROLE_*`。
- 判定：这是 runtime 授权消费点，不应在 authority 收缩阶段遗漏。
- 09C2/09C3 目标：改成“显式角色集合（roleCodes）”或“permission-style authority”判定，避免耦合 `ROLE_*` 通用 authority。

## 3. Test-only list（仅测试断言/辅助，不作为运行时行为依据）

以下文件包含 `ROLE_*` 断言或测试数据，归类为 test-only，不代表运行时必须长期保留 `ROLE_*`：

- `tiny-oauth-server/src/test/java/com/tiny/platform/core/oauth/security/SecurityUserAuthorityServiceTest.java`
- `tiny-oauth-server/src/test/java/com/tiny/platform/core/oauth/config/JwtTokenCustomizerTest.java`
- `tiny-oauth-server/src/test/java/com/tiny/platform/core/oauth/config/TinyPlatformJwtGrantedAuthoritiesConverterTest.java`
- `tiny-oauth-server/src/test/java/com/tiny/platform/core/oauth/security/UserDetailsServiceImplTest.java`
- `tiny-oauth-server/src/test/java/com/tiny/platform/core/oauth/security/MultiAuthenticationProviderTest.java`
- `tiny-oauth-server/src/test/java/com/tiny/platform/core/oauth/integration/PartialMfaFormLoginIntegrationTest.java`

说明：`src/test/java` 下还有更多 `ROLE_*` 相关集成测试样例（RBAC / controller coverage），本卡按“与 authority 契约最直接相关”优先列出；其余用例在 09C3/09C4 收缩时按改动范围同步更新。

## 4. 09C1 结论（供 09C2/09C3 使用）

- `keep-list`：`SecurityUserAuthorityService`、`JwtTokenCustomizer`、`TinyPlatformJwtGrantedAuthoritiesConverter`
- `migrate-list`：`CamundaIdentityBridgeFilter`、`tiny-web User#getAuthorities`、`tiny-web ResourceAuthorizationManager`
- `test-only list`：见第 3 节

本卡未改默认 authority 行为，满足 `CARD-09C1` 边界。

