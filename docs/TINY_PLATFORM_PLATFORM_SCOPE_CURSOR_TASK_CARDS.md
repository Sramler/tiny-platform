# Tiny Platform PLATFORM Scope 解耦 Cursor 任务卡

> 状态：可执行任务卡  
> 适用范围：`tiny-oauth-server` 认证主链 / 菜单资源控制面 / 文档与门禁同步  
> 目标：把“平台是默认租户别名”的剩余实现拆成小步任务卡交给 Cursor 执行，由 Codex 负责审计  
> 当前主线：第一阶段（`CARD-01 ~ CARD-05`）已完成认证桥接落地；第二阶段继续做 backfill、旧表 fallback 收口、real-link / Nightly 认证链补强、最终兼容逻辑下线

> 口径纠偏（2026-04）：
> - 认证域当前迁移路线**不是**在 `user_authentication_method` 上继续演进 `scope_type/scope_id/scope_key`
> - 当前目标模型是：`user_auth_credential + user_auth_scope_policy`
> - 旧表 `user_authentication_method` 在桥接期仅保留兼容 fallback 与迁移过渡职责，不再作为长期语义载体
> - 当前已完成“新模型双写 + 新模型优先读”，后续按 `CARD-06 -> CARD-07A/07B/07C -> CARD-08A/08B -> CARD-09A/09B -> CARD-09C` 推进 backfill、fallback 收口、旧表下线以及 authority 契约收缩

---

## 1. 使用方式

一次只给 Cursor 一张任务卡，不要并行发两张。

每张卡都要求 Cursor：

- 先阅读本文件列出的固定约束与设计裁决；
- 再按本卡给出的文件范围、验收标准和限制直接实施；
- 不要把本卡擅自扩大成“顺手把后面几张也做了”；
- 完成后必须返回：
  - 修改文件清单
  - 执行命令
  - 测试结果
  - 剩余风险

Codex 的职责不是代替 Cursor 实施，而是按本文件末尾的审计口径做结果审计。

---

## 2. 固定约束

以下约束对所有任务卡都成立。

### 2.1 必读规则与文档

- `AGENTS.md`
- `docs/TINY_PLATFORM_TESTING_PLAYBOOK.md`
- `docs/TINY_PLATFORM_SESSION_BEARER_AUTH_MATRIX.md`
- `docs/TINY_PLATFORM_TENANT_GOVERNANCE.md`
- `.agent/src/rules/50-testing.rules.md`
- `.agent/src/rules/58-cicd.rules.md`
- `.agent/src/rules/90-tiny-platform.rules.md`
- `.agent/src/rules/91-tiny-platform-auth.rules.md`
- `.agent/src/rules/94-tiny-platform-tenant-governance.rules.md`

### 2.2 当前已经完成的基线

下列内容已在主线工作区落地，本轮任务卡不得回退：

- `ActiveScope` 已是运行时真源，`/sys/users/current/active-scope` 已支持 `PLATFORM`
- 平台 / 租户切换的前后端基础契约已收口
- `/platform` issuer 与前端 OIDC authority 已基本解耦
- 菜单读侧与资源读侧已经按 `tenant_id IS NULL` 平台正式载体补了关键回归
- `UserAuthenticationMethodProfileService` 已引入桥接域模型：
  - `UserAuthenticationCredential`
  - `UserAuthenticationScopePolicy`
  - `UserAuthenticationMethodProfile`
- `UserServiceImpl` / `TenantServiceImpl` / `SecurityServiceImpl` 的主要写链已开始显式组装 credential + scope policy

### 2.3 本轮禁止事项

- 不要把任务卡扩大成根 issuer 物理移除
- 不要把任务卡扩大成 `MenuServiceImpl` / `ResourceServiceImpl` 全面重构
- 不要一次性删除 `user_authentication_method`
- 不要引入第二套临时平台租户兼容开关
- 不要在没有明确验收的情况下顺手改 E2E 或 Nightly workflow

---

## 3. 执行顺序

按以下顺序交给 Cursor。

1. `CARD-01` 认证读链去 legacy platform tenant fallback
2. `CARD-02` 认证域物理拆层建模预埋
3. `CARD-03` 认证写链双写到新模型
4. `CARD-04` 认证读链优先读新模型
5. `CARD-05` 文档、任务清单与门禁同步

当前状态：

- `CARD-01 ~ CARD-05` 已完成并通过 Codex 审计
- 接下来按 `CARD-06 -> CARD-07A/07B/07C -> CARD-08A/08B -> CARD-09A/09B -> CARD-09C1/09C2/09C3/09C4` 继续做桥接态收口与 authority 契约收缩

### 3.1 与“PLATFORM 正确模型 7 件事”的映射

为避免第二阶段只看到局部动作、看不到总体主线，`CARD-06 ~ CARD-09B3` 必须放在下面这 7 件事里理解：

1. **ActiveScope 成为运行时真源**
   - 已完成，属于第二阶段基线；后续任务卡不得回退到“靠 `activeTenantId` 推导平台态”。
2. **平台 / 租户双身份切换契约明确**
   - 已完成，属于第二阶段基线；后续任务卡不得破坏 `/sys/users/current/active-scope` 与相关 Session / Bearer 契约。
3. **认证域从旧 `tenant_id` 语义升级到真正的作用域模型**
   - 这是 `CARD-06 ~ CARD-09B3` 的主线：
     - 注意：这里指的是“迁到 `user_auth_credential + user_auth_scope_policy` 新模型”，不是继续给 `user_authentication_method` 加新 scope 字段
     - `CARD-06`：补 backfill / dry-run / 对账证据
     - `CARD-07A/07B/07C`：收口旧表 fallback
     - `CARD-09A/09B`：最终删除旧兼容逻辑
4. **issuer 从“tenantCode 路由”拆成平台固定路由 + 真实租户路由**
   - 已完成基线；`CARD-06 ~ CARD-09B3` 不得引入新的 `tenantCode=platform` 假设。
5. **菜单 / 资源 / 安全读侧去 platform tenant 化**
   - 已完成关键读侧基线；后续任务卡不得恢复 `platformTenantId` 运行时依赖。
6. **`PlatformTenantResolver` / `platform-tenant-code` 降级为纯迁移工具**
   - 第二阶段持续审计项；尤其在 `CARD-07A ~ CARD-09B3` 中，不得新增新的运行时兼容壳。
7. **文档 / 前端 / 门禁与真实链路同步**
   - `CARD-05` 已完成文档同步基线；
   - `CARD-08A/08B` 负责把这条主线补到 real-link / Nightly 真实认证链路；
   - `CARD-09A/09B` 负责在最终收口时同步去掉桥接期口径。

---

## 4. 任务卡

### CARD-01 认证读链去 legacy platform tenant fallback

**目标**

把认证读链中对 `PlatformTenantResolver` / `platformTenantId` 的剩余依赖从 `UserAuthenticationMethodProfileService` 清掉。

**为什么先做**

这是当前认证域里最直接的“平台仍像租户”的残留点。只要这个 fallback 还在，平台认证方式的运行时语义就仍然和历史 platform tenant 绑在一起。

**范围**

- `tiny-oauth-server/src/main/java/com/tiny/platform/infrastructure/auth/user/service/UserAuthenticationMethodProfileService.java`
- 与它直接相关的单测 / 集成测试
- 必要时同步调整直接构造它的测试工厂

**明确要求**

- 生产代码中，`UserAuthenticationMethodProfileService` 不再注入 `PlatformTenantResolver`
- 平台态认证读取只使用 `tenant_id IS NULL` 载体
- 不新增 feature flag
- 不引入新的“platform tenant code”兼容路径

**验收标准**

- `UserAuthenticationMethodProfileService` 不再 import / 持有 `PlatformTenantResolver`
- 平台态 `loadEnabledMethodProfiles` / `findEffectiveMethodProfile` 不再回退 legacy platform tenant 行
- 直接依赖该服务的测试全部通过
- 没有把租户态 `TENANT + GLOBAL` 回退语义做坏

**建议验证**

- `mvn -pl tiny-oauth-server -Dtest=UserAuthenticationMethodProfileServiceTest,MultiAuthenticationProviderTest,SecurityServiceImplMfaDecisionTest,SecurityServiceImplTotpThrottleTest,SecurityControllerTest,SecurityControllerRedirectTest,PartialMfaFormLoginIntegrationTest test`

**复制给 Cursor 的提示词**

```text
请为 tiny-platform 执行 CARD-01：认证读链去 legacy platform tenant fallback。

必须先阅读：
- AGENTS.md
- docs/TINY_PLATFORM_TESTING_PLAYBOOK.md
- docs/TINY_PLATFORM_SESSION_BEARER_AUTH_MATRIX.md
- docs/TINY_PLATFORM_TENANT_GOVERNANCE.md
- .agent/src/rules/50-testing.rules.md
- .agent/src/rules/58-cicd.rules.md
- .agent/src/rules/90-tiny-platform.rules.md
- .agent/src/rules/91-tiny-platform-auth.rules.md
- .agent/src/rules/94-tiny-platform-tenant-governance.rules.md

当前基线：
- ActiveScope、平台/租户切换、前端 OIDC 解耦、菜单/资源读侧关键回归、认证桥接域模型都已存在
- 本卡不要重复做这些

本卡只做：
- 删除 UserAuthenticationMethodProfileService 中的 PlatformTenantResolver / platformTenantId fallback
- 平台态认证读取只走 tenant_id IS NULL
- 修复与之直接相关的测试

本卡不要做：
- 不要改根 issuer
- 不要改菜单/资源主实现
- 不要新建 feature flag
- 不要一次性做后续新表双写/双读

交付要求：
- 直接改代码，不要只给计划
- 输出修改文件
- 输出执行命令
- 输出测试结果
- 输出剩余风险
```

**Codex 审计点**

- 生产代码里是否还存在 `UserAuthenticationMethodProfileService -> PlatformTenantResolver`
- 平台认证链是否仍有 platform tenant fallback
- 租户回退语义是否被误伤

---

### CARD-02 认证域物理拆层建模预埋

**目标**

为“凭证表 + 作用域策略表”建立物理模型，但这一张卡先不切换生产读写。

**为什么排第二**

`CARD-01` 清掉 legacy platform tenant fallback 之后，才值得引入长期模型的物理表结构，否则新旧语义会继续纠缠。

**目标模型**

- `user_auth_credential`
  - 存用户认证材料
  - 不表达平台 / 租户策略
- `user_auth_scope_policy`
  - 存 `GLOBAL / PLATFORM / TENANT` 下的启用、主方式、优先级
  - 不存密码 hash / TOTP secret 这类材料

**范围**

- Liquibase changeset
- `schema.sql`
- 新 domain / repository / 基础 support 类
- 只做建模与持久层，不切生产流量

**明确要求**

- 不删除旧表 `user_authentication_method`
- 不切换 `UserAuthenticationMethodProfileService`
- 不把旧表字段直接复制成第二套坏模型
- 要显式区分“credential 层字段”和“scope policy 层字段”

**建议字段方向**

`user_auth_credential`

- `id`
- `user_id`
- `authentication_provider`
- `authentication_type`
- `authentication_configuration`
- `last_verified_at`
- `last_verified_ip`
- `expires_at`
- 唯一键：`user_id + authentication_provider + authentication_type`

`user_auth_scope_policy`

- `id`
- `credential_id`
- `scope_type`
- `scope_id`
- `scope_key`
- `is_primary_method`
- `is_method_enabled`
- `authentication_priority`
- 唯一键：`credential_id + scope_key`

**验收标准**

- Liquibase 可创建两张新表
- `schema.sql` 同步
- 新实体、仓库、基础测试可编译
- 生产代码主链路仍未切换到新表

**建议验证**

- `mvn -pl tiny-oauth-server -Dtest=UserAuthenticationMethodProfilesTest test`
- 如新增仓库测试，补到同一命令

**复制给 Cursor 的提示词**

```text
请为 tiny-platform 执行 CARD-02：认证域物理拆层建模预埋。

必须先阅读：
- AGENTS.md
- docs/TINY_PLATFORM_TESTING_PLAYBOOK.md
- docs/TINY_PLATFORM_TENANT_GOVERNANCE.md
- .agent/src/rules/50-testing.rules.md
- .agent/src/rules/58-cicd.rules.md
- .agent/src/rules/90-tiny-platform.rules.md
- .agent/src/rules/91-tiny-platform-auth.rules.md

目标：
- 新增 user_auth_credential
- 新增 user_auth_scope_policy
- 只完成建模、Liquibase、schema.sql、repository/support
- 不切生产读写

强约束：
- 旧表 user_authentication_method 暂时保留
- 不要把旧表原样复制成第二张“tenant_id 语义表”
- 要明确区分 credential 字段和 policy 字段

交付要求：
- 直接改代码
- 输出 changeset、实体、仓库、测试文件
- 输出执行命令
- 输出测试结果
- 输出剩余风险
```

**Codex 审计点**

- 表结构是否真的完成“凭证 / 策略”分层
- 是否偷偷把 `tenant_id` 语义复制进新表
- 是否保持旧表兼容不切流

---

### CARD-03 认证写链双写到新模型

**目标**

在不切断旧表兼容的前提下，把认证写链同时写入新模型。

**范围**

- `UserServiceImpl`
- `TenantServiceImpl`
- `SecurityServiceImpl`
- 如有必要，新建 `UserAuthenticationBridgeWriter` 一类聚合写服务

**明确要求**

- 旧表 `user_authentication_method` 暂时继续写
- 新表也要同步写
- 以“先新模型正确，再保留旧表兼容”为准
- 不要在 Controller 层堆业务逻辑

**优先覆盖的写路径**

- 用户密码创建
- 用户密码更新
- 租户初始管理员密码初始化
- TOTP 预绑定
- TOTP 绑定激活
- MFA remind skip

**验收标准**

- 以上写路径双写成功
- 旧行为不回退
- 新增或改造的单测能断言新表被写入

**建议验证**

- `mvn -pl tiny-oauth-server -Dtest=UserServiceImplTest,TenantServiceImplTest,SecurityServiceImplMfaDecisionTest,SecurityServiceImplTotpThrottleTest test`

**复制给 Cursor 的提示词**

```text
请为 tiny-platform 执行 CARD-03：认证写链双写到新模型。

必须先阅读：
- AGENTS.md
- docs/TINY_PLATFORM_TESTING_PLAYBOOK.md
- docs/TINY_PLATFORM_TENANT_GOVERNANCE.md
- .agent/src/rules/50-testing.rules.md
- .agent/src/rules/58-cicd.rules.md
- .agent/src/rules/90-tiny-platform.rules.md
- .agent/src/rules/91-tiny-platform-auth.rules.md

前置假设：
- 新表 user_auth_credential / user_auth_scope_policy 已存在

本卡只做：
- 用户密码创建/更新
- 租户初始管理员密码初始化
- TOTP 预绑定/绑定
- MFA remind skip
这些写链的双写

本卡不要做：
- 不要切生产读链到新表
- 不要删旧表写入
- 不要顺手做全量数据迁移脚本

交付要求：
- 直接改代码
- 输出修改文件
- 输出执行命令
- 输出测试结果
- 输出剩余风险
```

**Codex 审计点**

- 双写是否覆盖了关键写路径
- 是否保持旧行为兼容
- 是否把业务逻辑过度塞进 controller / test-only helper

---

### CARD-04 认证读链优先读新模型

**目标**

让 `UserAuthenticationMethodProfileService` 或其替代服务优先从新模型装配 profile，旧表只作为兼容 fallback。

**范围**

- `UserAuthenticationMethodProfileService`
- 新表 repository / adapter
- 与认证链直接相关的测试

**明确要求**

- 读优先级：新模型 > 旧表
- 平台态不允许再回落到 legacy platform tenant 行
- 兼容期内，仅当新模型缺失时才允许读旧表
- 不要把“新表不存在记录”误写成平台登录失败

**验收标准**

- 平台态、租户态都能从新模型读到 profile
- 兼容期缺数据时，旧表 fallback 仍可工作
- 平台态不再读取 platform tenant fallback

**建议验证**

- `mvn -pl tiny-oauth-server -Dtest=UserAuthenticationMethodProfileServiceTest,MultiAuthenticationProviderTest,SecurityControllerTest,SecurityControllerRedirectTest,PartialMfaFormLoginIntegrationTest test`

**复制给 Cursor 的提示词**

```text
请为 tiny-platform 执行 CARD-04：认证读链优先读新模型。

必须先阅读：
- AGENTS.md
- docs/TINY_PLATFORM_TESTING_PLAYBOOK.md
- docs/TINY_PLATFORM_SESSION_BEARER_AUTH_MATRIX.md
- docs/TINY_PLATFORM_TENANT_GOVERNANCE.md
- .agent/src/rules/50-testing.rules.md
- .agent/src/rules/58-cicd.rules.md
- .agent/src/rules/90-tiny-platform.rules.md
- .agent/src/rules/91-tiny-platform-auth.rules.md

前置假设：
- 新表与双写已经完成

本卡只做：
- UserAuthenticationMethodProfileService 优先从新模型组装 profile
- 旧表仅作为兼容 fallback
- 平台态禁止回退 legacy platform tenant

本卡不要做：
- 不要删除旧表
- 不要切别的控制面模块
- 不要把根 issuer 收尾塞进这张卡

交付要求：
- 直接改代码
- 输出修改文件
- 输出执行命令
- 输出测试结果
- 输出剩余风险
```

**Codex 审计点**

- 读优先级是否清晰
- 平台态是否彻底摆脱 legacy platform tenant fallback
- 兼容 fallback 是否只在“新模型缺失”时触发

---

### CARD-05 文档、任务清单与门禁同步

**目标**

把已经落地的现状同步回文档和门禁，避免 Cursor 继续按旧口径工作。

**范围**

- `docs/TINY_PLATFORM_TENANT_GOVERNANCE.md`
- `docs/TINY_PLATFORM_AUTHORIZATION_TASK_LIST.md`
- 相关测试矩阵或门禁文档

**明确要求**

- 当前状态必须以“已经落地的代码”为准
- 要写清楚当前桥接状态，不得假装已经完全切到新模型
- 要写清楚哪些门禁仍是 targeted test，哪些是后续需要补到 real-link / Nightly

**验收标准**

- 文档口径与当前代码一致
- 任务顺序与剩余项清晰
- 不再出现“平台认证仍默认依赖 platformTenantId”这类过期口径

**复制给 Cursor 的提示词**

```text
请为 tiny-platform 执行 CARD-05：文档、任务清单与门禁同步。

必须先阅读：
- AGENTS.md
- docs/TINY_PLATFORM_TENANT_GOVERNANCE.md
- docs/TINY_PLATFORM_AUTHORIZATION_TASK_LIST.md
- docs/TINY_PLATFORM_TESTING_PLAYBOOK.md
- .agent/src/rules/90-tiny-platform.rules.md
- .agent/src/rules/91-tiny-platform-auth.rules.md
- .agent/src/rules/94-tiny-platform-tenant-governance.rules.md

要求：
- 只按当前代码现状更新文档
- 不要把目标态写成已完成
- 要明确当前桥接态、剩余项、门禁缺口

交付要求：
- 输出修改文件
- 输出修改摘要
- 输出剩余风险
```

**Codex 审计点**

- 文档是否与代码一致
- 是否出现“目标态冒充现状”
- 是否补清楚了剩余门禁缺口

---

## 5. Codex 审计统一口径

Cursor 完成每张卡后，Codex 审计时统一按下面 5 点看：

1. 是否严格只做本卡范围  
2. 是否回退了已经落地的 `PLATFORM` 语义  
3. 是否引入了新的 platform tenant 兼容壳  
4. 是否给出足够的测试与执行命令  
5. 是否把“桥接态”和“终局态”混写成一回事  

---

## 6. 推荐交付节奏

推荐你按下面节奏把活交给 Cursor：

1. 第一阶段按 `CARD-01 -> CARD-02 -> CARD-03 -> CARD-04 -> CARD-05`
2. 第二阶段按 `CARD-06 -> CARD-07A -> CARD-07B -> CARD-07C -> CARD-08A -> CARD-08B -> CARD-09A -> CARD-09B1 -> CARD-09B2 -> CARD-09B3`
3. 每张卡都先过 Codex 审计，再发下一张

不要跳卡，也不要让 Cursor 同时做相邻依赖卡，例如：

- 不要并行做 `CARD-06` 和 `CARD-07A`
- 不要并行做 `CARD-07B` 和 `CARD-07C`
- 不要在 `CARD-08A/08B` 未补真实链验证前直接做 `CARD-09A/09B`

---

## 7. 第二阶段剩余任务卡（CARD-06 ~ CARD-09B3）

### CARD-06 新模型 backfill / dry-run / 对账脚本

**目标**

把历史 `user_authentication_method` 数据安全回填到 `user_auth_credential` / `user_auth_scope_policy`，但本卡先聚焦“脚本 + dry-run + 对账”，不直接删除旧表或切断 fallback。

**关联总主线**

- 直接承接第 3 项：把认证域从 `user_authentication_method` 的 `tenant_id` 旧模型迁到 `user_auth_credential + user_auth_scope_policy` 新模型
- 为第 6 项提供数据基础：后续要去掉迁移期兼容壳，必须先有可审计的回填证据
- 不得回退第 1 / 2 / 4 / 5 项已完成基线

**为什么先做**

只有先把存量数据迁到新模型并确认一致性，后面的 `CARD-07A/07B/07C` 才有事实基础。否则 fallback 收口会把“数据没回填”和“真实缺失”混在一起。

**范围**

- Liquibase backfill changeset 或显式 SQL / Java migration helper
- dry-run / 对账脚本
- 必要的仓库/验证辅助类
- 文档化回填口径（若需要，写到现有任务文档或测试手册）

**明确要求**

- 必须复用统一 `buildScopeKey(...)` 规则，禁止脚本手写第二套 key 格式
- backfill 口径必须明确区分：
  - `tenant_id IS NULL` 且平台语义 -> `PLATFORM` 或 `GLOBAL`
  - `tenant_id = tenant.id` -> `TENANT:{id}`
- 至少提供 dry-run 模式或不落库对账模式
- 必须能回答：
  - 准备写入多少 credential
  - 准备写入多少 scope policy
  - 与旧表逐 scope/provider/type 的差异有哪些
- 不要在这张卡里删旧表
- 不要在这张卡里关闭 fallback

**验收标准**

- 能生成 backfill 结果或 dry-run 报告
- 能输出回填前后计数和差异摘要
- `scope_key` 生成规则与生产代码一致
- 至少有一组测试或 smoke 验证锁住核心映射逻辑

**建议验证**

- 与回填脚本直接相关的定向测试
- 如是 Java helper，可补：
  - `mvn -pl tiny-oauth-server -Dtest=UserAuthenticationMethodProfileServiceTest,UserAuthenticationBridgeWriterTest test`

**复制给 Cursor 的提示词**

```text
请为 tiny-platform 执行 CARD-06：新模型 backfill / dry-run / 对账脚本。

必须先阅读：
- AGENTS.md
- docs/TINY_PLATFORM_TENANT_GOVERNANCE.md
- docs/TINY_PLATFORM_AUTHORIZATION_TASK_LIST.md
- docs/TINY_PLATFORM_TESTING_PLAYBOOK.md
- .agent/src/rules/50-testing.rules.md
- .agent/src/rules/58-cicd.rules.md
- .agent/src/rules/90-tiny-platform.rules.md
- .agent/src/rules/91-tiny-platform-auth.rules.md

当前基线：
- CARD-01 ~ CARD-05 已完成
- 新模型已落库、写链已双写、读链已新模型优先
- 旧表仍保留 fallback

本卡只做：
- 补 backfill / dry-run / 对账脚本
- 锁住 scope_key 映射与统计口径
- 给出“什么时候可以进入去 fallback”的数据证据

本卡不要做：
- 不要删除 user_authentication_method
- 不要关闭生产 fallback
- 不要顺手改 real-link / Nightly workflow

强约束：
- 必须复用统一 buildScopeKey(...) 规则
- 输出回填计数、差异摘要、执行命令、测试结果、剩余风险
```

**Codex 审计点**

- 是否真的复用了统一 `buildScopeKey(...)`
- dry-run / 对账是否能看出 PLATFORM / GLOBAL / TENANT 三类差异
- 是否误把“回填”和“切流 / 删旧表”混成一张卡

---

**CARD-06 当前已完成证据（2026-04-09）**

- `projected_credential_upserts = 15`
- `projected_scope_policy_upserts = 19`
- `conflict_groups = 0`
- `user_auth_credential = 15`
- `user_auth_scope_policy = 19`
- `legacy vs new-model diff = empty`

---

### CARD-07A fallback 命中观测与清单固化

**目标**

在不改变 fallback 默认行为的前提下，先把旧表命中路径观测清楚，形成“哪些请求还在读旧表”的可审计清单。

**前置条件**

- `CARD-06` 已完成，并具备上面的 6 条数据证据

**范围**

- `UserAuthenticationMethodProfileService`
- 与旧表 fallback 直接相关的日志 / 统计 / 计数器 / 诊断输出
- 针对 fallback 命中观测的 targeted regression

**明确要求**

- 本卡先加观测，不改 fallback 默认开关
- 必须能回答：
  - 哪个 scope 在命中旧表
  - 哪个 provider/type 在命中旧表
  - 是单条查找还是批量读取在命中旧表
- 平台态仍必须保持 `PLATFORM > GLOBAL`
- 不得恢复任何 legacy platform tenant fallback

**验收标准**

- 运行时可明确观测旧表命中
- 有测试锁住 fallback hit 的观测输出或计数行为
- 文档里能看到当前仍保留的旧表命中边界

**复制给 Cursor 的提示词**

```text
请为 tiny-platform 执行 CARD-07A：fallback 命中观测与清单固化。

必须先阅读：
- AGENTS.md
- docs/TINY_PLATFORM_TENANT_GOVERNANCE.md
- docs/TINY_PLATFORM_AUTHORIZATION_TASK_LIST.md
- docs/TINY_PLATFORM_TESTING_PLAYBOOK.md
- .agent/src/rules/50-testing.rules.md
- .agent/src/rules/58-cicd.rules.md
- .agent/src/rules/90-tiny-platform.rules.md
- .agent/src/rules/91-tiny-platform-auth.rules.md

前置证据：
- CARD-06 已完成
- projected_credential_upserts = 15
- projected_scope_policy_upserts = 19
- conflict_groups = 0
- user_auth_credential = 15
- user_auth_scope_policy = 19
- legacy vs new-model diff = empty

本卡只做：
- 给旧表 fallback 增加命中观测
- 固化 fallback hit 清单或诊断输出
- 保持现有默认行为不变

本卡不要做：
- 不要收紧 fallback 行为
- 不要删旧表
- 不要改 real-link / Nightly

交付要求：
- 直接改代码
- 输出修改文件
- 输出如何查看 fallback hit
- 输出执行命令
- 输出测试结果
- 输出剩余风险
```

**Codex 审计点**

- 是否真的新增了可用的 fallback 命中观测
- 是否偷带了行为切换而不是纯观测
- 是否保持 `PLATFORM > GLOBAL` 和统一 `scope_key` 契约

---

### CARD-07B fallback 收窄到显式异常场景

**目标**

在已有命中观测的前提下，把 fallback 收窄到“仅异常缺失/显式允许的历史脏数据场景”才触发。

**前置条件**

- `CARD-07A` 已完成，且能明确列出当前命中旧表的入口

**范围**

- `UserAuthenticationMethodProfileService`
- 与 fallback 判断直接相关的 adapter / helper
- 相关 targeted regression

**明确要求**

- 收窄后，新模型完整场景不得再读旧表
- 只允许“显式异常场景”触发 fallback，不允许宽泛兜底
- 必须保留命中观测
- 不得恢复任何 legacy platform tenant fallback
- 不删表，不做真实链路改造

**验收标准**

- fallback 命中路径明显减少
- 正常平台态/租户态认证链默认走新模型
- 旧表命中只剩可解释的异常边界

**复制给 Cursor 的提示词**

```text
请为 tiny-platform 执行 CARD-07B：fallback 收窄到显式异常场景。

必须先阅读：
- AGENTS.md
- docs/TINY_PLATFORM_TENANT_GOVERNANCE.md
- docs/TINY_PLATFORM_AUTHORIZATION_TASK_LIST.md
- docs/TINY_PLATFORM_TESTING_PLAYBOOK.md
- .agent/src/rules/50-testing.rules.md
- .agent/src/rules/58-cicd.rules.md
- .agent/src/rules/90-tiny-platform.rules.md
- .agent/src/rules/91-tiny-platform-auth.rules.md

前置条件：
- CARD-07A 已完成
- 已能明确列出当前 fallback hit 入口

本卡只做：
- 收窄 user_authentication_method fallback 触发条件
- 保留并复用 CARD-07A 的命中观测
- 保持新模型优先读与 PLATFORM > GLOBAL 不变

本卡不要做：
- 不要默认关闭 fallback
- 不要删旧表
- 不要改 real-link / Nightly

交付要求：
- 输出修改文件
- 输出 fallback 收窄前后差异
- 输出执行命令
- 输出测试结果
- 输出剩余风险
```

**Codex 审计点**

- fallback 是否真的只剩显式异常场景
- 是否仍能观察到命中原因
- 是否误把正常缺失和真实异常混在一起

---

### CARD-07C 受控环境默认关闭 fallback

**目标**

在 `CARD-07B` 收窄完成后，于受控环境中把认证主链切到“默认不依赖旧表 fallback”。

**前置条件**

- `CARD-07B` 已完成
- fallback hit 已降到可解释且可接受的剩余范围

**范围**

- 旧表 fallback 默认行为的最小开关或 fail-fast 保护
- 与认证主链直接相关的 targeted regression
- 最小必要文档同步

**明确要求**

- 只允许极小范围、短生命周期的受控环境切换手段
- 默认关闭后，如仍命中旧表，必须 fail-fast 或打出明确阻断信息
- 不得演化成长期双轨配置体系
- 不删表

**验收标准**

- 受控环境下可实现“默认不依赖旧表”
- 关闭 fallback 后 targeted regression 仍通过
- 命中旧表时能被明确识别，不会静默吞掉

**复制给 Cursor 的提示词**

```text
请为 tiny-platform 执行 CARD-07C：受控环境默认关闭 fallback。

必须先阅读：
- AGENTS.md
- docs/TINY_PLATFORM_TENANT_GOVERNANCE.md
- docs/TINY_PLATFORM_AUTHORIZATION_TASK_LIST.md
- docs/TINY_PLATFORM_TESTING_PLAYBOOK.md
- .agent/src/rules/50-testing.rules.md
- .agent/src/rules/58-cicd.rules.md
- .agent/src/rules/90-tiny-platform.rules.md
- .agent/src/rules/91-tiny-platform-auth.rules.md

前置条件：
- CARD-07B 已完成
- fallback hit 已收窄到可解释范围

本卡只做：
- 在受控环境中让认证主链默认不依赖旧表 fallback
- 为剩余命中提供 fail-fast 或明确阻断

本卡不要做：
- 不要删旧表
- 不要改 real-link / Nightly
- 不要做长期双轨开关体系

交付要求：
- 输出修改文件
- 输出如何开启/关闭受控环境行为
- 输出执行命令
- 输出测试结果
- 输出剩余风险
```

**Codex 审计点**

- 是否真的实现了“默认不依赖旧表”
- 是否引入了长期双轨开关债务
- 是否对剩余旧表命中给出了明确阻断

---

### CARD-08A 平台真实认证链路补强

**目标**

固定补 1 条平台真实认证链路，验证平台态 `PLATFORM > GLOBAL` 与新模型优先读在真实链路下成立。

**前置条件**

- `CARD-07C` 已完成

**范围**

- 1 条平台 real-link E2E 或 Nightly 认证链路
- 必要的 auth-state / setup helper / 环境变量归一化
- 与该链路直接相关的最小回归测试

**明确要求**

- 链路必须至少覆盖：
  - 平台登录
  - 认证方法读取
  - MFA 决策或安全状态读取
  - 成功进入平台控制面或得到明确业务结果
- 不能只断言接口 200
- 必须验证 `activeScopeType = PLATFORM`

**验收标准**

- 至少 1 条平台真实认证链路通过
- 平台边界和最终业务结果有断言
- helper 有配套回归

**复制给 Cursor 的提示词**

```text
请为 tiny-platform 执行 CARD-08A：平台真实认证链路补强。

必须先阅读：
- AGENTS.md
- docs/TINY_PLATFORM_TESTING_PLAYBOOK.md
- docs/TINY_PLATFORM_TENANT_GOVERNANCE.md
- docs/TINY_PLATFORM_AUTHORIZATION_TASK_LIST.md
- .agent/src/rules/50-testing.rules.md
- .agent/src/rules/58-cicd.rules.md
- .agent/src/rules/90-tiny-platform.rules.md
- .agent/src/rules/91-tiny-platform-auth.rules.md

前置条件：
- CARD-07C 已完成

本卡只做：
- 固定 1 条平台真实认证链路
- 必要的 auth-state / helper 回归

本卡不要做：
- 不要顺手删旧表
- 不要扩成整套 Nightly 大重构

强约束：
- 必须断言 activeScopeType = PLATFORM
- 必须断言最终业务结果，不只断言 200
```

**Codex 审计点**

- 是否真的是平台真实认证链
- 是否验证了 `PLATFORM > GLOBAL`
- helper / auth-state 是否有足够回归覆盖

---

### CARD-08B 租户真实认证链路补强

**目标**

固定补 1 条租户真实认证链路，验证租户态读取不会串成平台语义，且新模型优先读在真实链路下稳定。

**前置条件**

- `CARD-08A` 已完成

**范围**

- 1 条租户 real-link E2E 或 Nightly 认证链路
- 必要的 auth-state / setup helper / 环境变量归一化
- 与该链路直接相关的最小回归测试

**明确要求**

- 链路必须至少覆盖：
  - 租户登录
  - 认证方法读取
  - MFA 决策、成功路径或拒绝路径
  - 最终业务结果
- 必须断言租户边界不误落成平台态
- 不只断言接口 200

**验收标准**

- 至少 1 条租户真实认证链路通过
- 租户边界和最终业务结果有断言
- helper 有配套回归

**复制给 Cursor 的提示词**

```text
请为 tiny-platform 执行 CARD-08B：租户真实认证链路补强。

必须先阅读：
- AGENTS.md
- docs/TINY_PLATFORM_TESTING_PLAYBOOK.md
- docs/TINY_PLATFORM_TENANT_GOVERNANCE.md
- docs/TINY_PLATFORM_AUTHORIZATION_TASK_LIST.md
- .agent/src/rules/50-testing.rules.md
- .agent/src/rules/58-cicd.rules.md
- .agent/src/rules/90-tiny-platform.rules.md
- .agent/src/rules/91-tiny-platform-auth.rules.md

前置条件：
- CARD-08A 已完成

本卡只做：
- 固定 1 条租户真实认证链路
- 必要的 auth-state / helper 回归

本卡不要做：
- 不要顺手删旧表
- 不要扩成整套 Nightly 大重构

强约束：
- 必须断言租户边界不误落成平台态
- 必须断言最终业务结果，不只断言 200
```

**Codex 审计点**

- 是否真的是租户真实认证链
- 是否验证了平台/租户边界不串用
- helper / auth-state 是否有足够回归覆盖

---

### CARD-09A 主链零 runtime 依赖旧表

**目标**

在不急于物理删表的前提下，先让 production runtime 主链零依赖 `user_authentication_method`。

**前置条件**

- `CARD-08A`、`CARD-08B` 已完成

**范围**

- `UserAuthenticationMethodProfileService` 及其兼容 fallback
- 旧表 runtime 读依赖相关的 repository / service / 测试
- 最小必要文档同步

**明确要求**

- 删除 production runtime 主链对旧表的读取依赖
- 如仍保留旧表，只允许用于迁移、审计、历史对账，不允许参与主链鉴权
- 不得遗留新的 temporary fallback

**验收标准**

- 运行时主链零依赖旧表
- 真实认证链路证据仍成立
- 文档能明确说明“桥接读兼容已结束”

**复制给 Cursor 的提示词**

```text
请为 tiny-platform 执行 CARD-09A：主链零 runtime 依赖旧表。

必须先阅读：
- AGENTS.md
- docs/TINY_PLATFORM_TENANT_GOVERNANCE.md
- docs/TINY_PLATFORM_AUTHORIZATION_TASK_LIST.md
- docs/TINY_PLATFORM_TESTING_PLAYBOOK.md
- .agent/src/rules/50-testing.rules.md
- .agent/src/rules/58-cicd.rules.md
- .agent/src/rules/90-tiny-platform.rules.md
- .agent/src/rules/91-tiny-platform-auth.rules.md

前置条件：
- CARD-08A、CARD-08B 已完成

本卡只做：
- 删除旧表 runtime fallback 与主链读取依赖
- 同步最小必要文档与测试

本卡不要做：
- 不要物理删表
- 不要跳过真实链路证据

交付要求：
- 输出修改文件
- 输出执行命令
- 输出测试结果
- 输出是否已实现“零 runtime 依赖旧表”
- 输出剩余风险
```

**Codex 审计点**

- 是否真的完成零 runtime 依赖旧表
- 是否遗留隐蔽兼容壳
- 是否仍保留迁移/审计所需的最低信息面

---

### CARD-09B1 旧表下线 inventory 与 drop 前置确认

**目标**

在 `CARD-09A` 已完成后，先把旧表 `user_authentication_method` 的残留依赖、drop 前置和回滚面盘清楚，再进入物理删除。

**前置条件**

- `CARD-09A` 已完成

**范围**

- Liquibase / schema / scripts / docs / tests 的旧表 inventory
- `user_authentication_method` drop 前置确认
- 最小必要文档同步（只写现状与下一步，不做最终删除口径）

**明确要求**

- 必须给出“旧表还剩哪些引用、哪些要在 09B2 清、哪些要在 09B3 才能清”的清单
- 必须明确 drop 前置是否满足：运行时零依赖、real-link 证据、backfill 证据、迁移脚本证据
- 不允许在本卡直接删表
- 不允许在本卡顺手改业务逻辑

**验收标准**

- 产出可执行 inventory
- 产出 `09B2` / `09B3` 的明确分工边界
- 明确是否具备进入最终 drop 的前置条件

**复制给 Cursor 的提示词**

```text
请为 tiny-platform 执行 CARD-09B1：旧表下线 inventory 与 drop 前置确认。

必须先阅读：
- AGENTS.md
- docs/TINY_PLATFORM_TENANT_GOVERNANCE.md
- docs/TINY_PLATFORM_AUTHORIZATION_TASK_LIST.md
- docs/TINY_PLATFORM_TESTING_PLAYBOOK.md
- .agent/src/rules/50-testing.rules.md
- .agent/src/rules/58-cicd.rules.md
- .agent/src/rules/90-tiny-platform.rules.md
- .agent/src/rules/91-tiny-platform-auth.rules.md

前置条件：
- CARD-09A 已完成

本卡只做：
- 清点旧表 `user_authentication_method` 的代码 / 脚本 / 文档 / 测试 / Liquibase 残留
- 明确哪些残留属于 09B2，哪些属于 09B3
- 同步最小必要文档，写清楚“09A 已完成，09B 进入物理下线准备”

本卡不要做：
- 不要直接删表
- 不要修改主链业务逻辑
- 不要顺手完成 09B2/09B3

交付要求：
- 输出修改文件
- 输出执行命令
- 输出测试结果
- 输出 inventory 摘要
- 输出是否具备进入 09B3 的前置条件
- 输出剩余风险
```

**Codex 审计点**

- inventory 是否真实覆盖旧表残留
- 是否把 09B2/09B3 的边界切清楚
- 是否把“09A 已完成”和“旧表可 drop”清楚区分

---

### CARD-09B2 旧表代码/脚本/测试残留清理（不删表）

**目标**

在不立即 drop 表的前提下，先删掉仓库里对 `user_authentication_method` 物理存在的代码、脚本、测试、文档残留依赖。

**前置条件**

- `CARD-09B1` 已完成

**范围**

- 旧表 entity / repository / service helper 的下线
- scripts / tests / docs 中仍假设旧表存在的残留
- Liquibase / schema 中除最终 drop 之外的兼容残留

**明确要求**

- production runtime 已在 `09A` 下线，本卡只处理“仓库残留依赖”
- 如某些迁移/审计脚本仍必须保留旧表引用，必须明确标注“仅历史审计输入”
- 不允许在本卡直接 drop 表
- 不允许把“删不掉”的残留留成模糊 TODO

**验收标准**

- 仓库中除 `09B3` 最终 drop 所需迁移文件外，不再有旧表主路径依赖
- 旧表相关 entity/repository/helper 若仍保留，必须仅用于 `09B3` 之前的迁移过渡，并有明确理由
- 文档与测试不再把旧表视为运行时存在前提

**复制给 Cursor 的提示词**

```text
请为 tiny-platform 执行 CARD-09B2：旧表代码/脚本/测试残留清理（不删表）。

必须先阅读：
- AGENTS.md
- docs/TINY_PLATFORM_TENANT_GOVERNANCE.md
- docs/TINY_PLATFORM_AUTHORIZATION_TASK_LIST.md
- docs/TINY_PLATFORM_TESTING_PLAYBOOK.md
- docs/TINY_PLATFORM_PLATFORM_SCOPE_CURSOR_TASK_CARDS.md
- .agent/src/rules/50-testing.rules.md
- .agent/src/rules/58-cicd.rules.md
- .agent/src/rules/90-tiny-platform.rules.md
- .agent/src/rules/91-tiny-platform-auth.rules.md

前置条件：
- CARD-09A 已完成
- CARD-09B1 已完成

本卡只做：
- 清理旧表 `user_authentication_method` 的代码 / 脚本 / 测试 / 文档残留依赖
- 保留最终 drop 前仍必须存在的最小迁移面

本卡不要做：
- 不要直接 drop 表
- 不要恢复兼容 fallback
- 不要越过 inventory 直接删不确定残留

交付要求：
- 输出修改文件
- 输出执行命令
- 输出测试结果
- 输出还剩哪些内容只应由 09B3 完成
- 输出剩余风险
```

**Codex 审计点**

- 是否真的清掉仓库残留依赖，而不是只改文案
- 是否仍有隐蔽测试/脚本假设旧表存在
- 是否把最终 drop 所需最小残留控制到足够小

---

### CARD-09B3 最终 drop changeset 与物理下线收口

**目标**

在 `09B1/09B2` 已完成后，落最终 drop changeset，完成 `user_authentication_method` 的物理下线和最终口径收口。

**前置条件**

- `CARD-09A` 已完成
- `CARD-09B1` 已完成
- `CARD-09B2` 已完成
- 平台 / 租户 real-link 认证链证据已保留

**范围**

- 最终 Liquibase drop changeset / schema 同步
- 物理删表后的最小验证
- 最终文档口径：bridge 迁移期正式结束

**明确要求**

- 必须通过正式迁移路径执行最终 drop，不要手工库操作替代
- 删除后必须验证代码、测试、文档、脚本均不再依赖旧表存在
- 不允许留“逻辑已下线，物理删除以后再说”的尾巴

**验收标准**

- 已补最终 drop changeset
- 旧表物理下线后的验证通过
- bridge 迁移期口径正式结束

**复制给 Cursor 的提示词**

```text
请为 tiny-platform 执行 CARD-09B3：最终 drop changeset 与物理下线收口。

必须先阅读：
- AGENTS.md
- docs/TINY_PLATFORM_TENANT_GOVERNANCE.md
- docs/TINY_PLATFORM_AUTHORIZATION_TASK_LIST.md
- docs/TINY_PLATFORM_TESTING_PLAYBOOK.md
- docs/TINY_PLATFORM_PLATFORM_SCOPE_CURSOR_TASK_CARDS.md
- .agent/src/rules/50-testing.rules.md
- .agent/src/rules/58-cicd.rules.md
- .agent/src/rules/90-tiny-platform.rules.md
- .agent/src/rules/91-tiny-platform-auth.rules.md

前置条件：
- CARD-09A 已完成
- CARD-09B1 已完成
- CARD-09B2 已完成

本卡只做：
- 补最终 drop changeset
- 同步 schema / docs / tests 的最终物理下线口径
- 运行 drop 后最小验证

本卡不要做：
- 不要恢复旧表
- 不要引入新的迁移兼容壳

交付要求：
- 输出修改文件
- 输出执行命令
- 输出测试结果
- 输出旧表是否已物理下线
- 输出剩余风险
```

**Codex 审计点**

- 是否真的通过正式迁移路径完成最终 drop
- 是否还残留任何旧表存在假设
- 是否把“bridge 迁移期结束”明确写进最终文档口径

---

## 8. 第三阶段收尾任务卡（CARD-09C1 ~ CARD-09C4）

> 目标：在 `user_authentication_method` 与桥接兼容壳已经下线后，继续把 JWT / Session / runtime authority 从“`permission + role.code` 混合态”收敛到“permission 为主、显式 `roleCodes` 只供少量合法消费者使用”的最终契约。

### CARD-09C1 角色码 authority 消费点 inventory 与 keep-list 固化

**目标**

先把 runtime / JWT / Session / downstream 对 `ROLE_*` authority 的真实消费点盘清楚，形成 keep-list / migrate-list / test-only list，再做行为收缩。

**前置条件**

- `CARD-09B3` 已完成

**范围**

- `SecurityUserAuthorityService`
- `JwtTokenCustomizer`
- `TinyPlatformJwtGrantedAuthoritiesConverter`
- runtime 下游消费者（例如 workflow / bridge / filter）
- 最小必要文档清单

**明确要求**

- 必须区分：
  - 仍然合法、但应迁到显式 `roleCodes` 的消费者
  - 应改为纯 permission 判定的消费者
  - 仅测试辅助 / 历史脚本消费者
- 本卡只做 inventory / 口径固化 / 最小观测，不做行为切换

**验收标准**

- 已产出可执行 inventory
- 已明确 keep-list 与 migrate-list
- 未提前改动 authority 默认行为

**复制给 Cursor 的提示词**

```text
请为 tiny-platform 执行 CARD-09C1：角色码 authority 消费点 inventory 与 keep-list 固化。

必须先阅读：
- AGENTS.md
- docs/TINY_PLATFORM_AUTHORIZATION_MODEL.md
- docs/TINY_PLATFORM_AUTHORIZATION_TASK_LIST.md
- docs/TINY_PLATFORM_AUTHORIZATION_NEXT_PHASE_AND_IMPROVEMENTS.md
- docs/TINY_PLATFORM_PLATFORM_SCOPE_CURSOR_TASK_CARDS.md
- .agent/src/rules/50-testing.rules.md
- .agent/src/rules/58-cicd.rules.md
- .agent/src/rules/90-tiny-platform.rules.md
- .agent/src/rules/91-tiny-platform-auth.rules.md

前置条件：
- CARD-09B3 已完成

本卡只做：
- 盘点 runtime / JWT / Session / downstream 对 ROLE_* authority 的消费点
- 形成 keep-list / migrate-list / test-only list
- 同步最小必要文档

本卡不要做：
- 不要提前删除 role.code authority
- 不要顺手改业务权限判断
- 不要扩成大规模测试重写

交付要求：
- 输出 inventory 涉及文件
- 输出 keep-list / migrate-list / test-only list
- 输出执行命令
- 输出测试结果
- 输出剩余风险
```

**Codex 审计点**

- 是否真的盘到了 runtime 级消费者，而不只是搜文档
- 是否明确区分了“合法保留”与“应迁走”
- 是否保持了本卡不改默认行为的边界

---

### CARD-09C2 合法消费者迁到显式 `roleCodes`

**目标**

把少量仍然合法依赖角色码的消费者，从通用 `authorities` 中解耦出来，迁到显式 `roleCodes` 契约。

**前置条件**

- `CARD-09C1` 已完成

**范围**

- `SecurityUser` / JWT / Session 中与 `roleCodes` 相关的最小契约扩展
- 合法 keep-list 消费者（例如 workflow / bridge）
- 与该迁移直接相关的最小测试与文档

**明确要求**

- 新增 `roleCodes` 时，不得恢复 `role.name`
- 合法消费者应优先读显式 `roleCodes`，不再依赖通用 `ROLE_* authorities`
- 本卡期间允许 authority 继续兼容保留 `role.code`，为 `09C3` 做平滑切换准备

**验收标准**

- keep-list 消费者已不再依赖通用 `ROLE_* authority`
- `roleCodes` 契约已写清楚
- 不影响当前 permission 主链

**复制给 Cursor 的提示词**

```text
请为 tiny-platform 执行 CARD-09C2：合法消费者迁到显式 roleCodes。

必须先阅读：
- AGENTS.md
- docs/TINY_PLATFORM_AUTHORIZATION_MODEL.md
- docs/TINY_PLATFORM_AUTHORIZATION_TASK_LIST.md
- docs/TINY_PLATFORM_AUTHORIZATION_NEXT_PHASE_AND_IMPROVEMENTS.md
- docs/TINY_PLATFORM_PLATFORM_SCOPE_CURSOR_TASK_CARDS.md
- .agent/src/rules/50-testing.rules.md
- .agent/src/rules/58-cicd.rules.md
- .agent/src/rules/90-tiny-platform.rules.md
- .agent/src/rules/91-tiny-platform-auth.rules.md

前置条件：
- CARD-09C1 已完成

本卡只做：
- 引入显式 roleCodes 契约
- 迁移 keep-list 中仍合法的角色码消费者
- 同步最小必要测试与文档

本卡不要做：
- 不要删除 authorities 中现有 role.code 兼容
- 不要把 role.name 带回 JWT / Session
- 不要扩成权限体系重构

交付要求：
- 输出修改文件
- 输出哪些消费者已切到 roleCodes
- 输出执行命令
- 输出测试结果
- 输出剩余风险
```

**Codex 审计点**

- 是否真的把 keep-list 消费者迁离了通用 authorities
- `roleCodes` 是否为显式契约，而不是另一层隐式兼容
- 是否保持了 permission 主链不回退

---

### CARD-09C3 JWT / Session authority 收缩到 permission 为主

**目标**

在 keep-list 已迁到显式 `roleCodes` 后，把 JWT / Session / runtime authority 收缩到 permission 为主，移除 `role.code` 的主链兼容职责。

**前置条件**

- `CARD-09C2` 已完成

**范围**

- `SecurityUserAuthorityService`
- `JwtTokenCustomizer`
- `TinyPlatformJwtGrantedAuthoritiesConverter`
- 与 authority 契约直接相关的认证 / 授权回归

**明确要求**

- 新签发 JWT / Session / runtime authority 应以 permission-style authority 为主
- 不得破坏 factor authority、scope authority、`permissionsVersion`
- 如需保留旧 JWT 读兼容，只允许保留解码兼容，不得继续把新 token 生成为 `ROLE_* + permission` 混合态

**验收标准**

- 新 authority 契约已完成收缩
- 主链鉴权与真实认证链不回归
- 合法角色码消费者继续通过 `roleCodes` 工作

**复制给 Cursor 的提示词**

```text
请为 tiny-platform 执行 CARD-09C3：JWT / Session authority 收缩到 permission 为主。

必须先阅读：
- AGENTS.md
- docs/TINY_PLATFORM_AUTHORIZATION_MODEL.md
- docs/TINY_PLATFORM_AUTHORIZATION_TASK_LIST.md
- docs/TINY_PLATFORM_AUTHORIZATION_NEXT_PHASE_AND_IMPROVEMENTS.md
- docs/TINY_PLATFORM_PLATFORM_SCOPE_CURSOR_TASK_CARDS.md
- docs/TINY_PLATFORM_TESTING_PLAYBOOK.md
- .agent/src/rules/50-testing.rules.md
- .agent/src/rules/58-cicd.rules.md
- .agent/src/rules/90-tiny-platform.rules.md
- .agent/src/rules/91-tiny-platform-auth.rules.md

前置条件：
- CARD-09C2 已完成

本卡只做：
- 收缩新签发 JWT / Session / runtime authority
- 保持 permissions / roleCodes / factor / scope 契约一致
- 补最小必要回归

本卡不要做：
- 不要顺手改角色 CRUD
- 不要扩成 Camunda 或前端整套重构
- 不要删除旧 token 的最小读取兼容（若仍需要）

交付要求：
- 输出修改文件
- 输出 authority 新旧契约差异
- 输出执行命令
- 输出测试结果
- 输出剩余风险
```

**Codex 审计点**

- authority 是否真的收缩为 permission 为主
- 是否保住了 factor / scope / permissionsVersion
- 是否仍有新的 `ROLE_*` 主链输出残留

---

### CARD-09C4 文档、测试与辅助资产收尾

**目标**

在 authority 收缩完成后，清理文档、测试、token helper 与审计口径中的旧 `ROLE_*` authority 假设。

**前置条件**

- `CARD-09C3` 已完成

**范围**

- 核心授权文档
- token helper / auth fixture / unit test 中的旧 authority 假设
- 最小必要 CI / playbook 同步

**明确要求**

- 只清理“JWT / Session authority 契约”相关旧口径
- 不要误删角色码作为业务数据（如角色表单、角色 seed、角色展示）
- 必须把“roleCodes 仅供少量合法消费者”写清楚

**验收标准**

- 文档口径与当前实现一致
- 测试 helper 不再默认用 `ROLE_ADMIN` 模拟一般权限
- 没有把角色业务数据误当成 authority 兼容残留清掉

**复制给 Cursor 的提示词**

```text
请为 tiny-platform 执行 CARD-09C4：文档、测试与辅助资产收尾。

必须先阅读：
- AGENTS.md
- docs/TINY_PLATFORM_AUTHORIZATION_MODEL.md
- docs/TINY_PLATFORM_AUTHORIZATION_TASK_LIST.md
- docs/TINY_PLATFORM_AUTHORIZATION_NEXT_PHASE_AND_IMPROVEMENTS.md
- docs/TINY_PLATFORM_TESTING_PLAYBOOK.md
- docs/TINY_PLATFORM_PLATFORM_SCOPE_CURSOR_TASK_CARDS.md
- .agent/src/rules/50-testing.rules.md
- .agent/src/rules/58-cicd.rules.md
- .agent/src/rules/90-tiny-platform.rules.md
- .agent/src/rules/91-tiny-platform-auth.rules.md

前置条件：
- CARD-09C3 已完成

本卡只做：
- 清理 authority 契约相关旧文档与测试 helper
- 同步 roleCodes 的最终口径
- 保持角色业务数据不受影响

本卡不要做：
- 不要继续改 JWT / Session 主链行为
- 不要清理角色表、角色 seed、角色展示字段

交付要求：
- 输出修改文件
- 输出哪些 helper / 文档已同步
- 输出执行命令
- 输出测试结果
- 输出剩余风险
```

**Codex 审计点**

- 文档是否与 authority 新契约一致
- 测试 helper 是否仍默认依赖 ROLE_* authority
- 是否误伤了“角色是业务数据”这层语义

---

## 9. 第四阶段兼容壳收口任务卡（CARD-10A ~ CARD-10D）

> 目标：在 `CARD-09C1 ~ CARD-09C4` 完成后，继续把仍然保留的运行时兼容壳收口到“明确边界、可观测、可退出”的状态。  
> 范围只包含仍会影响当前主线判断的兼容层：
> - JWT / Session 解码兼容
> - `roleCodes` 合法消费者的最后一层 `ROLE_*` fallback
> - 平台 `default/platformTenantCode` 兼容壳
> - carrier requirement 缺失时的 fallback 兼容
>
> 冻结边界：
> - `tiny-web` 已视为冻结中的历史模块，不再作为权限/认证模型继续演进的承载体
> - 本阶段卡片默认 **不**为 `tiny-web` 设计长期收敛任务；若出现编译/启动/阻断性问题，仅做最小生存修复
> - 新的兼容清理与退出标准，以 `tiny-oauth-server` 为唯一主线

**执行状态（2026-04）**

- `CARD-10A`：已完成（解码兼容窗口边界 + 最小回归）
- `CARD-10B`：已完成（`roleCodes` 优先、`ROLE_*` fallback 收窄到 legacy carrier）
- `CARD-10C`：已完成（`default/platformTenantCode` 兼容壳降级为历史/bootstrap 边界）
- `CARD-10D`：已完成（carrier fallback 兼容路径观测化 + 审计 reason 固化）

### CARD-10A JWT / Session 解码兼容窗口固化

**目标**

把“旧 token / 旧 session 快照为何仍可读”这件事正式写成可执行边界，并固化退出前置条件，避免后续有人过早删掉解码兼容。

**前置条件**

- `CARD-09C4` 已完成

**范围**

- `SecurityUser` 的反序列化兼容
- JWT `authorities / permissions / roleCodes` 解码兼容
- 与旧 token / 旧 session 兼容窗口直接相关的最小文档与测试

**明确要求**

- 不改变新签发 token 契约
- 不恢复 `ROLE_* + permission` 混合生成
- 只清点/固化“解码兼容为什么还留着、何时能删”

**验收标准**

- 已明确列出仍保留的解码兼容点
- 已写清退出条件（例如：旧 token/session 窗口结束、灰度观察完成）
- 已有最小回归锁住“旧快照仍可读”

**复制给 Cursor 的提示词**

```text
请为 tiny-platform 执行 CARD-10A：JWT / Session 解码兼容窗口固化。

必须先阅读：
- AGENTS.md
- docs/TINY_PLATFORM_AUTHORIZATION_MODEL.md
- docs/TINY_PLATFORM_AUTHORIZATION_TASK_LIST.md
- docs/TINY_PLATFORM_LEGACY_COMPATIBILITY_INVENTORY.md
- docs/TINY_PLATFORM_PLATFORM_SCOPE_CURSOR_TASK_CARDS.md
- .agent/src/rules/50-testing.rules.md
- .agent/src/rules/58-cicd.rules.md
- .agent/src/rules/90-tiny-platform.rules.md
- .agent/src/rules/91-tiny-platform-auth.rules.md

前置条件：
- CARD-09C4 已完成

本卡只做：
- 盘点并固化 SecurityUser / JWT 解码兼容点
- 补最小必要文档和回归
- 写明退出前置条件

本卡不要做：
- 不要删除现有解码兼容
- 不要修改新签发 JWT 契约
- 不要扩成 Session 大重构

交付要求：
- 输出兼容点清单
- 输出退出条件
- 输出修改文件
- 输出执行命令
- 输出测试结果
- 输出剩余风险
```

**Codex 审计点**

- 是否明确区分了“新签发契约”和“旧快照解码兼容”
- 是否补了旧 payload / 旧 JWT 的最小回归
- 是否把退出条件写清楚，而不是无限期保留

---

### CARD-10B `roleCodes` 消费者最后一层 fallback 收窄

**目标**

在显式 `roleCodes` 已成为合法消费者主入口后，继续收窄对 `ROLE_* authorities` 的最后一层 fallback，使其只剩明确可解释的兼容兜底。

**前置条件**

- `CARD-09C4` 已完成

**范围**

- `CamundaIdentityBridgeFilter`
- 其他仍保留 `roleCodes -> ROLE_* fallback` 的主线消费者
- 与该 fallback 直接相关的最小测试与文档

**明确要求**

- 优先使用显式 `roleCodes`
- fallback 若保留，必须有明确触发条件与退出标准
- 不得重新把 `ROLE_*` 恢复成主链 authority 依赖

**验收标准**

- 主线消费者默认不再依赖 `ROLE_* authorities`
- fallback 保留范围已被明确收窄并可审计
- MFA session / JWT / 普通 session 三类路径均有回归

**复制给 Cursor 的提示词**

```text
请为 tiny-platform 执行 CARD-10B：roleCodes 消费者最后一层 fallback 收窄。

必须先阅读：
- AGENTS.md
- docs/TINY_PLATFORM_ROLE_CODE_AUTHORITY_CONSUMER_INVENTORY.md
- docs/TINY_PLATFORM_AUTHORIZATION_MODEL.md
- docs/TINY_PLATFORM_AUTHORIZATION_TASK_LIST.md
- docs/TINY_PLATFORM_PLATFORM_SCOPE_CURSOR_TASK_CARDS.md
- .agent/src/rules/50-testing.rules.md
- .agent/src/rules/58-cicd.rules.md
- .agent/src/rules/90-tiny-platform.rules.md
- .agent/src/rules/91-tiny-platform-auth.rules.md

前置条件：
- CARD-09C4 已完成

本卡只做：
- 收窄 roleCodes 合法消费者的 ROLE_* fallback
- 补最小必要测试与文档

本卡不要做：
- 不要改 JWT 新签发契约
- 不要恢复 ROLE_* 主链 authority
- 不要把 tiny-web 拉回长期演进范围

交付要求：
- 输出修改文件
- 输出 fallback 收窄前后差异
- 输出执行命令
- 输出测试结果
- 输出剩余风险
```

**Codex 审计点**

- 是否真的保持了 `roleCodes` 为第一入口
- fallback 是否被收窄成明确兼容兜底
- 是否覆盖了 MFA session 场景

---

### CARD-10C 平台 `default/platformTenantCode` 兼容壳降级

**目标**

把仍然残留的 `PlatformTenantResolver` / `platformTenantCode` / `tenantCode` 兼容壳继续降级成“仅 bootstrap / 历史入口兼容”，不允许它们再影响新业务主线。

**前置条件**

- `CARD-09C4` 已完成

**范围**

- `PlatformTenantResolver`
- `TenantContextFilter` 中的旧入口兼容路径
- 与平台租户兼容壳直接相关的配置、文档、测试

**明确要求**

- 不删除仍有 bootstrap/历史入口价值的最小兼容代码
- 但必须把“新业务禁止依赖”写成硬边界
- 尽量把运行时主链与 bootstrap / 历史入口兼容拆开

**验收标准**

- 已明确哪些调用点仍合法，哪些已禁止新增
- 平台主语义继续只认 `PLATFORM` scope
- 文档与代码注释已能区分“主链”与“历史兼容”

**复制给 Cursor 的提示词**

```text
请为 tiny-platform 执行 CARD-10C：平台 default/platformTenantCode 兼容壳降级。

必须先阅读：
- AGENTS.md
- docs/TINY_PLATFORM_AUTHORIZATION_MODEL.md
- docs/TINY_PLATFORM_TENANT_GOVERNANCE.md
- docs/TINY_PLATFORM_AUTHORIZATION_TASK_LIST.md
- docs/TINY_PLATFORM_PLATFORM_SCOPE_CURSOR_TASK_CARDS.md
- .agent/src/rules/50-testing.rules.md
- .agent/src/rules/58-cicd.rules.md
- .agent/src/rules/90-tiny-platform.rules.md
- .agent/src/rules/91-tiny-platform-auth.rules.md

前置条件：
- CARD-09C4 已完成

本卡只做：
- 盘点并降级 PlatformTenantResolver / platformTenantCode 兼容壳
- 固化“主链禁止新增依赖”的边界
- 补最小必要文档和测试

本卡不要做：
- 不要把平台主语义改回 default tenant
- 不要扩大成平台模板体系重构
- 不要顺手改 unrelated bootstrap

交付要求：
- 输出兼容调用点清单
- 输出允许保留 / 禁止新增的边界
- 输出修改文件
- 输出执行命令
- 输出测试结果
- 输出剩余风险
```

**Codex 审计点**

- 是否把 `PLATFORM` scope 主语义和 default 租户兼容壳分开了
- 是否避免让新业务继续依赖 `PlatformTenantResolver`
- 是否只做降级，不误删 bootstrap 所需最小兼容

---

### CARD-10D carrier requirement fallback 收口

**目标**

把 requirement 缺失时按 `fallbackPermission` 判定的兼容层，进一步收口成“清晰、可观测、可退出”的模式，避免它长期伪装成正常主链能力。

**前置条件**

- `CARD-09C4` 已完成

**范围**

- `CarrierPermissionRequirementEvaluator`
- requirement 缺失时的兼容判定、审计与观测
- 与 carrier fallback 直接相关的最小测试与文档

**明确要求**

- 不得恢复 `resource` 表或 `resource.permission` 主链语义
- requirement 行缺失时的 fallback 若继续保留，必须有明确观测与退出标准
- 新业务不得依赖 fallback 缺失判定作为正常接入方式

**验收标准**

- 已明确列出 fallback 仍保留的场景
- 已有 requirement 缺失 / compatibility fallback 的最小观测
- 已写清后续 fail-closed 或彻底下线的前置条件

**复制给 Cursor 的提示词**

```text
请为 tiny-platform 执行 CARD-10D：carrier requirement fallback 收口。

必须先阅读：
- AGENTS.md
- docs/TINY_PLATFORM_AUTHORIZATION_MODEL.md
- docs/TINY_PLATFORM_AUTHORIZATION_LAYERED_MODEL.md
- docs/TINY_PLATFORM_AUTHORIZATION_TASK_LIST.md
- docs/TINY_PLATFORM_PLATFORM_SCOPE_CURSOR_TASK_CARDS.md
- .agent/src/rules/50-testing.rules.md
- .agent/src/rules/58-cicd.rules.md
- .agent/src/rules/90-tiny-platform.rules.md
- .agent/src/rules/91-tiny-platform-auth.rules.md

前置条件：
- CARD-09C4 已完成

本卡只做：
- 盘点并收口 carrier requirement fallback
- 明确 requirement 缺失时的兼容边界
- 补最小必要测试、观测与文档

本卡不要做：
- 不要恢复 resource 主链
- 不要扩成 carrier 全量重构
- 不要把 fallback 当作新业务默认接入方式

交付要求：
- 输出 fallback 场景清单
- 输出观测/审计变化
- 输出修改文件
- 输出执行命令
- 输出测试结果
- 输出剩余风险
```

**Codex 审计点**

- 是否把 fallback 和正常 requirement 主链区分清楚
- 是否补了 requirement 缺失场景的最小回归/观测
- 是否阻止了新业务继续依赖 compatibility fallback

---

## 10. 第五阶段兼容代码移除任务卡（CARD-11A ~ CARD-11D）

> 目标：在 `CARD-10A ~ CARD-10D` 已把兼容边界固定下来之后，继续把仍残留在 `tiny-oauth-server` 主线里的兼容代码真正移除。  
> 这一阶段不再以“固化兼容”为目标，而是以“验证依赖已消失、随后删除兼容实现”为目标。  
> 删除顺序按风险从低到高推进：
> - `CARD-11A`：移除 carrier requirement fallback
> - `CARD-11B`：移除 `ROLE_*` 最后一层 fallback
> - `CARD-11C`：退役 `default/platformTenantCode` 兼容壳
> - `CARD-11D`：移除 JWT / Session 旧快照解码兼容
>
> 冻结边界：
> - `tiny-web` 继续视为冻结中的历史模块，不作为这一阶段兼容代码移除的主线目标
> - 若 `tiny-web` 因主线收口产生阻断，只接受最小生存修复，不反向决定 `tiny-oauth-server` 主线契约

**执行状态（2026-04）**

- `CARD-11A`：已完成
- `CARD-11B`：已完成
- `CARD-11C`：已完成
- `CARD-11D`：已完成

### CARD-11A 移除 carrier requirement fallback

**目标**

把 `CarrierPermissionRequirementEvaluator` 中“requirement 缺失时按 `fallbackPermission` 兜底”的兼容路径从主线删除，推进到 requirement 缺失即 fail-closed 或显式阻断的状态。

**前置条件**

- `CARD-10D` 已完成
- 已有 compatibility fallback 的观测与审计 reason

**范围**

- `CarrierPermissionRequirementEvaluator`
- requirement 缺失时的兼容判定与异常/审计处理
- 与 requirement 缺失兼容移除直接相关的最小测试与文档

**明确要求**

- 不得恢复 `resource` 表或 `resource.permission` 主链语义
- 新业务继续依赖 `fallbackPermission` 的路径必须先被识别并清理
- 删除 fallback 后，requirement 缺失必须是显式可诊断的失败，而不是静默放行

**验收标准**

- 主线不再因 requirement 缺失走 `fallbackPermission` 兜底
- requirement 缺失场景有清晰异常/审计口径
- 最小回归覆盖“存在 requirement 正常通过”和“缺失 requirement fail-closed”

**复制给 Cursor 的提示词**

```text
请为 tiny-platform 执行 CARD-11A：移除 carrier requirement fallback。

必须先阅读：
- AGENTS.md
- docs/TINY_PLATFORM_AUTHORIZATION_MODEL.md
- docs/TINY_PLATFORM_AUTHORIZATION_LAYERED_MODEL.md
- docs/TINY_PLATFORM_AUTHORIZATION_TASK_LIST.md
- docs/TINY_PLATFORM_PLATFORM_SCOPE_CURSOR_TASK_CARDS.md
- docs/TINY_PLATFORM_LEGACY_COMPATIBILITY_INVENTORY.md
- .agent/src/rules/50-testing.rules.md
- .agent/src/rules/58-cicd.rules.md
- .agent/src/rules/90-tiny-platform.rules.md
- .agent/src/rules/91-tiny-platform-auth.rules.md

前置条件：
- CARD-10D 已完成

本卡只做：
- 删除 requirement 缺失时的 fallbackPermission 兼容判定
- 把缺失 requirement 改成 fail-closed / 明确阻断
- 补最小必要测试、文档和审计口径

本卡不要做：
- 不要恢复 resource 主链
- 不要扩成 carrier 全量重构
- 不要顺手改 unrelated permission model

交付要求：
- 输出删除前后行为差异
- 输出修改文件
- 输出执行命令
- 输出测试结果
- 输出剩余风险
```

**Codex 审计点**

- fallbackPermission 是否已退出主链判定
- requirement 缺失是否变成显式失败而不是静默兼容
- 是否补了缺失 requirement 的最小回归

---

### CARD-11B 移除 `ROLE_*` 最后一层 fallback

**目标**

在 `roleCodes` 已成为合法消费者正式入口后，删除剩余的 `ROLE_* authorities` fallback，让角色码消费者只依赖显式 `roleCodes`。

**前置条件**

- `CARD-10B` 已完成
- `CARD-09C2 ~ CARD-09C4` 已完成

**范围**

- `CamundaIdentityBridgeFilter`
- 其他仍依赖 `ROLE_*` fallback 的主线合法消费者
- 与该 fallback 删除直接相关的最小测试与文档

**明确要求**

- 合法消费者必须显式消费 `roleCodes`
- 不得重新把 `ROLE_*` 恢复成新签发 authority
- legacy fallback 删除前，需确认 keep-list 已全部迁完

**验收标准**

- 主线不再依赖 `ROLE_* authorities` 计算角色码
- MFA session / 普通 session / JWT 三类路径都以 `roleCodes` 正常工作
- 文档已明确 `ROLE_*` 只剩旧快照解码兼容，不再参与角色码消费主链

**复制给 Cursor 的提示词**

```text
请为 tiny-platform 执行 CARD-11B：移除 ROLE_* 最后一层 fallback。

必须先阅读：
- AGENTS.md
- docs/TINY_PLATFORM_ROLE_CODE_AUTHORITY_CONSUMER_INVENTORY.md
- docs/TINY_PLATFORM_AUTHORIZATION_MODEL.md
- docs/TINY_PLATFORM_AUTHORIZATION_TASK_LIST.md
- docs/TINY_PLATFORM_PLATFORM_SCOPE_CURSOR_TASK_CARDS.md
- .agent/src/rules/50-testing.rules.md
- .agent/src/rules/58-cicd.rules.md
- .agent/src/rules/90-tiny-platform.rules.md
- .agent/src/rules/91-tiny-platform-auth.rules.md

前置条件：
- CARD-10B 已完成
- CARD-09C2 ~ CARD-09C4 已完成

本卡只做：
- 删除合法消费者中的 ROLE_* fallback
- 让角色码消费只认显式 roleCodes
- 补最小必要回归和文档

本卡不要做：
- 不要改 JWT 解码兼容窗口
- 不要恢复 ROLE_* 主链 authority
- 不要把 tiny-web 纳入长期演进

交付要求：
- 输出删除前后差异
- 输出修改文件
- 输出执行命令
- 输出测试结果
- 输出剩余风险
```

**Codex 审计点**

- 合法消费者是否已完全改成显式 `roleCodes`
- 是否删除了最后一层 `ROLE_*` fallback
- MFA session / JWT / 普通 session 回归是否齐全

---

### CARD-11C 退役 `default/platformTenantCode` 兼容壳

**目标**

把 `PlatformTenantResolver` / `platformTenantCode` / `default` 租户兼容壳从主链和 bootstrap 之外的路径彻底退役，使平台主语义完全只认 `PLATFORM` scope。

**前置条件**

- `CARD-10C` 已完成
- bootstrap / 历史入口 / 运维脚本仍需依赖的最小保留面已明确

**范围**

- `PlatformTenantResolver`
- `TenantContextFilter` 中相关兼容入口
- 主线代码中仍引用 `default/platformTenantCode` 的调用点、文档与测试

**明确要求**

- 平台主语义必须只认 `PLATFORM` scope
- 只能在确认 bootstrap / 历史入口已完成迁移后删除兼容壳
- 不得把平台语义重新解释成 default tenant

**验收标准**

- 主线运行时零依赖 `PlatformTenantResolver`
- `default/platformTenantCode` 不再作为平台主链语义入口
- 文档、测试与脚本已同步退出或改为历史说明

**复制给 Cursor 的提示词**

```text
请为 tiny-platform 执行 CARD-11C：退役 default/platformTenantCode 兼容壳。

必须先阅读：
- AGENTS.md
- docs/TINY_PLATFORM_TENANT_GOVERNANCE.md
- docs/TINY_PLATFORM_AUTHORIZATION_TASK_LIST.md
- docs/TINY_PLATFORM_PLATFORM_SCOPE_CURSOR_TASK_CARDS.md
- docs/TINY_PLATFORM_LEGACY_COMPATIBILITY_INVENTORY.md
- .agent/src/rules/50-testing.rules.md
- .agent/src/rules/58-cicd.rules.md
- .agent/src/rules/90-tiny-platform.rules.md
- .agent/src/rules/91-tiny-platform-auth.rules.md

前置条件：
- CARD-10C 已完成

本卡只做：
- 删除 default/platformTenantCode 兼容壳的主线依赖
- 收口或退役 PlatformTenantResolver
- 补最小必要测试、脚本与文档

本卡不要做：
- 不要把平台主语义改回 default tenant
- 不要扩大成租户模板体系重构
- 不要顺手改 unrelated bootstrap 逻辑

交付要求：
- 输出兼容壳残留点清单
- 输出删除前后差异
- 输出修改文件
- 输出执行命令
- 输出测试结果
- 输出剩余风险
```

**Codex 审计点**

- 主线运行时是否已零依赖 `PlatformTenantResolver`
- 是否把 bootstrap / 历史入口兼容和主链彻底分开
- 是否真的让平台只认 `PLATFORM` scope

---

### CARD-11D 移除 JWT / Session 旧快照解码兼容

**目标**

在兼容窗口结束且旧 token / 旧 session 已不再需要支持后，删除 `SecurityUser` / JWT 对旧混合态 payload 的解码兼容，使新契约成为唯一运行时输入。

**前置条件**

- `CARD-10A` 已完成
- 已确认旧 token / 旧 session 兼容窗口结束
- 已有证据说明旧快照不再需要继续读取

**范围**

- `SecurityUser` 旧 payload 兼容解析
- `TinyPlatformJwtGrantedAuthoritiesConverter` 的旧混合态解码兼容
- 与旧快照解码兼容删除直接相关的最小测试、文档和 inventory

**明确要求**

- 不得影响当前新签发 JWT / Session 契约
- 删除前必须明确兼容窗口结束证据
- 删除后，旧混合态 payload 应该是显式不支持，而不是半兼容半失败

**验收标准**

- 主线只认当前 JWT / Session 契约
- 旧混合态解码兼容代码已删除
- 文档、inventory、测试已同步到“无旧快照兼容”状态

**复制给 Cursor 的提示词**

```text
请为 tiny-platform 执行 CARD-11D：移除 JWT / Session 旧快照解码兼容。

必须先阅读：
- AGENTS.md
- docs/TINY_PLATFORM_AUTHORIZATION_MODEL.md
- docs/TINY_PLATFORM_AUTHORIZATION_TASK_LIST.md
- docs/TINY_PLATFORM_LEGACY_COMPATIBILITY_INVENTORY.md
- docs/TINY_PLATFORM_PLATFORM_SCOPE_CURSOR_TASK_CARDS.md
- .agent/src/rules/50-testing.rules.md
- .agent/src/rules/58-cicd.rules.md
- .agent/src/rules/90-tiny-platform.rules.md
- .agent/src/rules/91-tiny-platform-auth.rules.md

前置条件：
- CARD-10A 已完成
- 已确认旧 token / session 兼容窗口结束

本卡只做：
- 删除旧混合态 JWT / Session 解码兼容
- 让新契约成为唯一运行时输入
- 补最小必要测试、inventory 和文档

本卡不要做：
- 不要改当前新签发 token 契约
- 不要扩大成认证框架重写
- 不要在未确认兼容窗口结束前提前删除

交付要求：
- 输出兼容窗口结束证据
- 输出修改文件
- 输出执行命令
- 输出测试结果
- 输出剩余风险
```

**Codex 审计点**

- 是否真的拿到了“兼容窗口结束”的证据
- 是否删除了解码兼容而没有误改新签发契约
- 文档和 inventory 是否同步到新状态
