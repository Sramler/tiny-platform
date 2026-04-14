# Tiny Platform PLATFORM Scope 解耦 Cursor 任务卡

> 状态：可执行任务卡  
> 适用范围：`tiny-oauth-server` 认证主链 / 菜单资源控制面 / 文档与门禁同步  
> 目标：把“平台是默认租户别名”的剩余实现拆成小步任务卡交给 Cursor 执行，由 Codex 负责审计  
>
> **历史任务卡阅读说明（CARD-14D）**：本文件按阶段保留任务卡正文；卡内“验收 / 必须 / 禁止”多数对应**该卡编写年代的桥接期或阶段性约束**，**不等于**未加说明时的现行运行时代码规范。**当前态**以 `docs/TINY_PLATFORM_AUTHORIZATION_TASK_LIST.md`、`docs/TINY_PLATFORM_TESTING_PLAYBOOK.md`（§1.7）、`docs/TINY_PLATFORM_TENANT_GOVERNANCE.md` §3.1 为准：**CARD-13A** 后认证读链只读取与当前激活作用域一致的**单个** `scope_key`；平台主语义认 **`PLATFORM` scope**；active 工具链对平台租户 **不隐式 `default`**（**CARD-13E**）。下文中带 **（历史）** / **（本卡执行期）** 的句子，均须在上述三份文档语境下理解。
>
> **（阶段性叙事，多阶段收口前）** 第一阶段（`CARD-01 ~ CARD-05`）已完成认证桥接落地；曾计划第二阶段继续 backfill、旧表 fallback 收口、real-link / Nightly 认证链补强、最终兼容逻辑下线。**（现状）** 各阶段完成度与开放项以实现与 `TINY_PLATFORM_AUTHORIZATION_TASK_LIST.md` 为准，勿仅凭本段推断仍有运行时旧表 fallback 或未收口桥接主路径。

> 口径纠偏（2026-04）：
> - 认证域迁移路线**不是**在 `user_authentication_method` 上继续演进 `scope_type/scope_id/scope_key`
> - 目标模型是：`user_auth_credential + user_auth_scope_policy`
> - **（桥接期，旧表尚未物理下线前）** 旧表 `user_authentication_method` 曾保留兼容 fallback 与迁移过渡职责；**（现状）** 旧表已按任务清单 `CARD-09B3` 等下线，production 主链不再读旧表，见 `TINY_PLATFORM_AUTHORIZATION_TASK_LIST.md`
> - **（阶段成果，已实现）** “新模型双写 + 新模型优先读”及后续 `CARD-06 -> … -> CARD-09C` 所覆盖的 backfill、fallback 收口、旧表下线、authority 契约收缩已在主线按任务清单推进；**后续延续项**亦以任务清单为唯一完成度真相源
>
> **CARD-14D 当前态摘要（与上文任务卡年代区分）**：运行时读链单 `scope_key`；平台语义 `PLATFORM`；工具链显式平台租户配置；细节见三份真相文档。
>
> **CARD-14E 新增任务卡模板约束（可复用）**  
> - **写作规则**：新增或修订任务卡时，凡涉及认证读链、`scope_key`、`PLATFORM` 与 `GLOBAL` 的关系、旧表或「新模型优先 + fallback」、`default` / `platformTenantCode`，须在卡内显式标注 **（历史）**、**（桥接期）**、**（阶段性）** 或 **（当前态）** 之一。  
> - **当前态真相源（固定三份）**：`docs/TINY_PLATFORM_AUTHORIZATION_TASK_LIST.md`、`docs/TINY_PLATFORM_TESTING_PLAYBOOK.md` §1.7、`docs/TINY_PLATFORM_TENANT_GOVERNANCE.md` §3.1；**不得**仅凭本文件未标注的旧卡正文推断现行门禁。  
> - **禁止**：把桥接期「跨 `scope_key` 合并读序」或历史 **`PLATFORM > GLOBAL`** 简写写成未加标注的现行规范；保留历史卡原文时可加一句指向 **CARD-13A** / **CARD-13E**。

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

> **（CARD-14D）** 本节为任务卡编写年代的基线快照；读链、旧表角色与后续收口以 `TINY_PLATFORM_AUTHORIZATION_TASK_LIST.md`（含 CARD-04、CARD-09B3、CARD-13A 等）为准。

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

当前状态（**执行顺序说明；完成度见任务清单**）：

- `CARD-01 ~ CARD-05` 已完成并通过 Codex 审计
- **（本文件编写时计划）** 接下来按 `CARD-06 -> CARD-07A/07B/07C -> CARD-08A/08B -> CARD-09A/09B -> CARD-09C1/09C2/09C3/09C4` 继续做桥接态收口与 authority 契约收缩。**（现状）** 后续多阶段已按 `TINY_PLATFORM_AUTHORIZATION_TASK_LIST.md` 落地；勿将“桥接态收口”误解为仍有开放运行时桥接主链（以任务清单为准）。

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
- **（历史验收）** 没有把租户态在桥接期涉及的 `TENANT` 与 `GLOBAL` 并存/回退语义做坏（**当前态**读链见 CARD-13A / `TINY_PLATFORM_TENANT_GOVERNANCE.md` §3.1）

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

> **（CARD-14D）** 本卡对应桥接期「新模型优先、旧表兼容 fallback」的阶段性目标；**当前 production 主链**已只读新模型且无运行时旧表 fallback，见 `TINY_PLATFORM_AUTHORIZATION_TASK_LIST.md`（CARD-09A、CARD-13A）。

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
- backfill 口径必须明确区分（**数据迁移归类**：把源行映射到目标 `scope_key`，**不是**运行时一次查询合并多个 `scope_key`）：
  - `tenant_id IS NULL` 且平台语义 -> `PLATFORM` 或 `GLOBAL`（按源材料归入对应策略行；**CARD-13A 后**会话内仍只读单 active `scope_key`）
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
- **（本卡执行期，CARD-13A 前桥接读侧）** 观测仍须能在平台上下文中区分 `PLATFORM` 与 `GLOBAL` 相关命中（当时文档常简写为「`PLATFORM` 相对 `GLOBAL` 的读侧顺序」）。**（当前态）** 运行时不再做跨 `scope_key` 合并；平台登录只读 `scope_key=PLATFORM`；以 `TINY_PLATFORM_TENANT_GOVERNANCE.md` §3.1、`TINY_PLATFORM_AUTHORIZATION_TASK_LIST.md`（CARD-13A）为准——勿将历史简写 **`PLATFORM > GLOBAL`** 理解为现行合并读序。
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
- **（历史审计）** 在当时桥接约束下，是否仍满足平台上下文对 `PLATFORM`/`GLOBAL` 维度的可观测区分与统一 `scope_key` 契约。**（当前态审计替代）** 新代码是否遵守单 `scope_key` 读链与 §3.1（勿用历史 **`PLATFORM > GLOBAL`** 表述验收现行实现）

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
- **（本卡执行期）** 保持新模型优先读与当时桥接读侧对 `PLATFORM`/`GLOBAL` 的约定不变。**（当前态）** **CARD-13A** 后已无运行时「`PLATFORM` 再合并 `GLOBAL`」读序；本句仅描述该卡年代约束

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

**（本卡执行期）** 固定补 1 条平台真实认证链路，验证（当时）新模型优先读与桥接期平台上下文中对 `PLATFORM`/`GLOBAL` 读侧语义的可观测性（历史文案常写作 **`PLATFORM > GLOBAL`**）。**（CARD-13A 后读此卡）** 真实链路验收应覆盖 `activeScopeType = PLATFORM` 与认证读链在**单 `scope_key=PLATFORM`** 下成立；勿将历史 **`PLATFORM > GLOBAL`** 简写当作仍需验证的运行时合并顺序。

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
- **（历史）** 是否在当时的桥接门禁口径下验证了平台上下文中 `PLATFORM` 与 `GLOBAL` 相关语义（历史汇总表述 **`PLATFORM > GLOBAL`**）。**（当前若复用本卡思路）** 是否验证平台登录路径与单 `scope_key` 读链、任务清单 CARD-13A 一致
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
> **（CARD-14D）** 上列为该阶段**当时**计划收口的兼容层；`default/platformTenantCode` 类入口已随 `CARD-10C` / `CARD-11C` / `CARD-13C` 等降级为 **bootstrap / 历史配置边界**（见任务清单）。**当前平台主语义**只认 `PLATFORM` scope；bootstrap-only `platformTenantCode` 不替代平台定义。
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

---

## 11. 第六阶段剩余兼容壳清理任务卡（CARD-12A ~ CARD-12D）

> 目标：在 `CARD-11A ~ CARD-11D` 已删除主线兼容路径后，继续清掉仍会误导后续开发、但已不再承担主链职责的残留兼容壳。  
> 这一阶段主要针对：
> - 只为旧测试/旧装配保留的 deprecated 构造器与 legacy bridge
> - 已退出主链、但仍以运行时 Bean 形式存在的 `PlatformTenantResolver`
> - `SecurityUser` 中仍通过 `authorities` 隐式恢复 `roleCodes` 的构造器兼容
> - 运行时代码与核心文档中仍残留的旧表/旧模型措辞
>
> 删除顺序按风险从低到高推进：
> - `CARD-12A`：删除 deprecated 构造器与 legacy bridge 接口
> - `CARD-12B`：把 `PlatformTenantResolver` 降到纯 bootstrap / 运维工具
> - `CARD-12C`：删除 `SecurityUser` 构造器层的隐式 `roleCodes` 恢复
> - `CARD-12D`：清理运行时代码与核心文档中的旧模型措辞
>
> 冻结边界：
> - `tiny-web` 继续视为冻结中的历史模块，不作为这一阶段长期演进目标
> - backfill / reconcile / inventory 类迁移脚本不属于本阶段的主线删除目标

**执行状态（2026-04）**

- `CARD-12A`：已完成
- `CARD-12B`：已完成
- `CARD-12C`：已完成
- `CARD-12D`：已完成

### CARD-12A 删除 deprecated 构造器与 legacy bridge 接口

**目标**

删除仅为旧测试/旧装配保留、但已不再参与运行时主链的 deprecated 构造器和 legacy bridge 接口，避免后续开发继续沿着旧模型接线。

**前置条件**

- `CARD-11A ~ CARD-11D` 已完成
- 已确认相关 deprecated 路径不再被生产运行时依赖

**范围**

- `DefaultSecurityConfig`、`TenantContextFilter`、`MenuServiceImpl`、`ResourceServiceImpl` 中仅为历史装配保留的 deprecated 构造器
- `ResourceRepository` 等只用于 legacy compatibility 的桥接接口
- 与这些删除直接相关的最小测试与文档

**明确要求**

- 删除前先确认主 Bean 装配与当前测试构造都已有非 deprecated 入口
- 不要恢复 `resource` 表或旧 runtime bridge
- 不要把测试失败简单改成“继续保留 deprecated 构造器”

**验收标准**

- 主线运行与测试不再依赖这些 deprecated 构造器
- legacy bridge 接口不再作为运行时扩展点保留
- 文档与注释同步到“已删除”状态

**复制给 Cursor 的提示词**

```text
请为 tiny-platform 执行 CARD-12A：删除 deprecated 构造器与 legacy bridge 接口。

必须先阅读：
- AGENTS.md
- docs/TINY_PLATFORM_AUTHORIZATION_TASK_LIST.md
- docs/TINY_PLATFORM_PLATFORM_SCOPE_CURSOR_TASK_CARDS.md
- docs/TINY_PLATFORM_LEGACY_COMPATIBILITY_INVENTORY.md
- .agent/src/rules/50-testing.rules.md
- .agent/src/rules/58-cicd.rules.md
- .agent/src/rules/90-tiny-platform.rules.md
- .agent/src/rules/91-tiny-platform-auth.rules.md

前置条件：
- CARD-11A ~ CARD-11D 已完成

本卡只做：
- 删除已退出主链的 deprecated 构造器
- 删除 legacy bridge 接口或把其从运行时主路径中移除
- 补最小必要测试和文档

本卡不要做：
- 不要恢复 resource/runtime 旧模型
- 不要扩大成大规模测试重构
- 不要把 tiny-web 纳入长期收敛主线

交付要求：
- 输出删除清单
- 输出修改文件
- 输出执行命令
- 输出测试结果
- 输出剩余风险
```

**Codex 审计点**

- 是否真的删掉了运行时不再需要的 deprecated 构造器
- 是否避免继续保留 legacy bridge 误导新代码
- 测试是否改成走当前主链而不是继续依赖旧入口

---

### CARD-12B 把 `PlatformTenantResolver` 降到纯 bootstrap / 运维工具

**目标**

继续削弱 `PlatformTenantResolver` 的运行时存在感，使其不再作为应用主线 Bean 的默认依赖，而只保留在 bootstrap / 运维脚本 / 历史入口的明确边界内。

**前置条件**

- `CARD-11C` 已完成
- 主链已确认只认 `PLATFORM` scope

**范围**

- `PlatformTenantResolver`
- 仍然直接注入或引用它的配置/服务/测试入口
- 与其边界说明直接相关的最小文档与测试

**明确要求**

- 不要重新引入“平台 = default tenant”的语义
- 允许保留在 bootstrap / 历史运维工具中的最小使用面，但必须和主链完全隔离
- 若仍需保留 Bean，也要明确降级为非主链工具组件

**验收标准**

- 主线配置与运行时服务零依赖 `PlatformTenantResolver`
- 仅 bootstrap / 运维路径保留最小可解释依赖
- 文档明确其已不属于运行时语义组件

**复制给 Cursor 的提示词**

```text
请为 tiny-platform 执行 CARD-12B：把 PlatformTenantResolver 降到纯 bootstrap / 运维工具。

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
- CARD-11C 已完成

本卡只做：
- 清理主链对 PlatformTenantResolver 的残留依赖
- 只保留 bootstrap / 运维工具需要的最小边界
- 补最小必要测试和文档

本卡不要做：
- 不要改回 default tenant 平台语义
- 不要扩大成 tenant bootstrap 全量重构
- 不要顺手改 unrelated tenantCode 路由能力

交付要求：
- 输出残留依赖清单
- 输出修改文件
- 输出执行命令
- 输出测试结果
- 输出剩余风险
```

**Codex 审计点**

- `PlatformTenantResolver` 是否真正退出主线配置/服务依赖
- bootstrap / 运维边界是否和运行时语义分开
- 是否避免把 `tenantCode` 正常租户路由误删

---

### CARD-12C 删除 `SecurityUser` 构造器层的隐式 `roleCodes` 恢复

**目标**

在 `11D` 已删除 JSON/JWT 解码兼容后，进一步删除 `SecurityUser` 构造器层仍然通过 `authorities` 隐式恢复 `roleCodes` 的兼容逻辑，让 `roleCodes` 成为真正显式的输入契约。

**前置条件**

- `CARD-11B`、`CARD-11D` 已完成
- keep-list 消费者已只依赖显式 `roleCodes`

**范围**

- `SecurityUser` 构造器与辅助方法
- 仍通过 `authorities -> roleCodes` 隐式恢复的调用点
- 与该契约变化直接相关的最小测试与文档

**明确要求**

- 不得影响当前显式传入 `roleCodes` 的主链
- 删除后，调用方若需要角色码必须显式传入
- 不要重新恢复 `ROLE_*` 主链 authority

**验收标准**

- `SecurityUser` 不再通过构造器隐式恢复 `roleCodes`
- 主链调用方显式传入 `roleCodes`
- 相关回归覆盖“显式 roleCodes 正常、缺失 roleCodes 不再隐式恢复”

**复制给 Cursor 的提示词**

```text
请为 tiny-platform 执行 CARD-12C：删除 SecurityUser 构造器层的隐式 roleCodes 恢复。

必须先阅读：
- AGENTS.md
- docs/TINY_PLATFORM_AUTHORIZATION_MODEL.md
- docs/TINY_PLATFORM_AUTHORIZATION_TASK_LIST.md
- docs/TINY_PLATFORM_PLATFORM_SCOPE_CURSOR_TASK_CARDS.md
- docs/TINY_PLATFORM_ROLE_CODE_AUTHORITY_CONSUMER_INVENTORY.md
- .agent/src/rules/50-testing.rules.md
- .agent/src/rules/58-cicd.rules.md
- .agent/src/rules/90-tiny-platform.rules.md
- .agent/src/rules/91-tiny-platform-auth.rules.md

前置条件：
- CARD-11B 已完成
- CARD-11D 已完成

本卡只做：
- 删除 SecurityUser 构造器中的 authorities -> roleCodes 隐式恢复
- 让主链调用方显式传入 roleCodes
- 补最小必要测试和文档

本卡不要做：
- 不要改 JWT claim 契约
- 不要恢复 ROLE_* authority 主链
- 不要扩大成认证模型重写

交付要求：
- 输出契约变化说明
- 输出修改文件
- 输出执行命令
- 输出测试结果
- 输出剩余风险
```

**Codex 审计点**

- 是否真正删除了构造器层的隐式恢复
- 主链调用点是否全部显式传入 `roleCodes`
- 是否补了“无 roleCodes 不再自动恢复”的回归

---

### CARD-12D 清理旧模型措辞与运行时代码注释残留

**目标**

把运行时代码、核心文档和高频注释中仍残留的 `user_authentication_method` / `resource` / 旧 authority 混合态等历史措辞清理掉，避免后续开发继续被旧模型误导。

**前置条件**

- `CARD-12A ~ CARD-12C` 已完成或至少已确认主链契约稳定

**范围**

- 运行时代码中的高频注释、Javadoc、类说明
- `AGENTS.md` / 核心授权文档 / compatibility inventory 中仍可能误导主线的旧措辞
- 与口径清理直接相关的最小测试/检查（如有）

**明确要求**

- 优先清理“会误导当前开发”的入口文档与运行时代码注释
- 不要求一次性重写全部历史 runbook
- 历史迁移/归档文档可保留，但必须明确标成历史阶段说明

**验收标准**

- 主线代码注释与核心文档不再把旧表/旧兼容语义写成当前态
- 历史文档与当前文档边界清楚
- 不再出现“当前库/主链仍依赖旧模型”的误导描述

**复制给 Cursor 的提示词**

```text
请为 tiny-platform 执行 CARD-12D：清理旧模型措辞与运行时代码注释残留。

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
- CARD-12A ~ CARD-12C 已完成或主链契约已稳定

本卡只做：
- 清理会误导当前开发的旧模型措辞
- 同步核心文档与运行时代码注释
- 明确历史文档与当前真相源边界

本卡不要做：
- 不要扩大成全仓库文案大修
- 不要重写全部历史 runbook
- 不要顺手改 unrelated 行为代码

交付要求：
- 输出清理范围清单
- 输出修改文件
- 输出执行命令
- 输出测试结果（如有）
- 输出剩余风险
```

**Codex 审计点**

- 是否优先清掉了高频入口与运行时代码中的误导措辞
- 是否把历史文档和当前真相源边界写清楚
- 是否避免扩大成低收益的大规模文案重写

---

## 12. 第七阶段剩余主线兼容代码移除任务卡（CARD-13A ~ CARD-13E）

> 目标：在 `CARD-12A ~ CARD-12D` 已清理大部分兼容壳和误导入口后，继续删除仍留在 `tiny-oauth-server` 主链中的少量兼容行为。  
> 这一阶段主要针对：
> - 认证桥接里“新模型优先 + 旧模型 fallback”的残留主路径
> - carrier compatibility group / unregistered endpoint legacy behavior
> - `platformTenantCode/default` 作为 bootstrap 历史配置的残余主线影响
> - API/角色层的历史别名与 compat flags
>
> 删除顺序按“先主链 fallback，再配置残留，再 API 别名”推进：
> - `CARD-13A`：删除认证桥接 fallback 主路径
> - `CARD-13B`：删除 carrier compatibility 运行时兜底
> - `CARD-13C`：退役 `platformTenantCode/default` bootstrap 兼容配置
> - `CARD-13D`：删除 API/角色层历史别名与 compat flags
> - `CARD-13E`：清理 active tooling 的 `default` 隐式平台入口与当前态文档残留旧口径
>
> 冻结边界：
> - `tiny-web` 继续视为冻结中的历史模块，不作为这一阶段主线收口目标
> - backfill / reconcile / inventory / audit 类迁移材料不属于本阶段的删除目标

**执行状态（2026-04）**

- `CARD-13A`：已完成
- `CARD-13B`：已完成
- `CARD-13C`：已完成
- `CARD-13D`：已完成
- `CARD-13E`：已完成

### CARD-13A 删除认证桥接 fallback 主路径

**目标**

把认证主链中仍然存在的“新模型优先 + 旧模型 fallback”桥接残留真正移除，让运行时只认 `user_auth_credential + user_auth_scope_policy`。

**前置条件**

- `CARD-09B3` 已完成
- `CARD-07A ~ CARD-07C` 的 fallback 观测、收窄、fail-fast 经验已沉淀
- 已确认旧表与 bridge fallback 不再承担运行时兜底职责
- 存量库若存在 CARD-06 回填的 `scope_key=GLOBAL` 策略行：必须先执行 Liquibase `135-duplicate-global-auth-scope-policy-card-13a`，或运行 `tiny-oauth-server/scripts/verify-card-13a-global-auth-scope-policy-rollout.sh` 证明缺口为 0（避免读侧单 `scope_key` 后租户/平台登录断链）

**范围**

- `UserAuthenticationMethodProfileService`
- `UserAuthenticationMethodMerge`
- 与认证桥接 fallback 删除直接相关的最小测试与文档

**明确要求**

- 删除后不得重新引入旧表 runtime 读取依赖
- 不要恢复任何 platform tenant / legacy scope fallback
- 若保留迁移工具类，必须明确与运行时主链隔离

**验收标准**

- 认证主链不再存在旧模型 fallback
- `UserAuthenticationMethodMerge` 等桥接残留要么删除，要么降为迁移材料
- 相关回归覆盖“新模型唯一真相源”契约
- 数据侧：`verify-card-13a-global-auth-scope-policy-rollout.sh`（或等价 SQL）对账通过，或已执行 `135` 迁移补齐 `TENANT:{id}` / `PLATFORM` 策略行

**复制给 Cursor 的提示词**

```text
请为 tiny-platform 执行 CARD-13A：删除认证桥接 fallback 主路径。

必须先阅读：
- AGENTS.md
- docs/TINY_PLATFORM_AUTHORIZATION_TASK_LIST.md
- docs/TINY_PLATFORM_PLATFORM_SCOPE_CURSOR_TASK_CARDS.md
- docs/TINY_PLATFORM_LEGACY_COMPATIBILITY_INVENTORY.md
- docs/TINY_PLATFORM_TENANT_GOVERNANCE.md
- .agent/src/rules/50-testing.rules.md
- .agent/src/rules/58-cicd.rules.md
- .agent/src/rules/90-tiny-platform.rules.md
- .agent/src/rules/91-tiny-platform-auth.rules.md

前置条件：
- CARD-09B3 已完成
- CARD-07A ~ CARD-07C 已完成

本卡只做：
- 删除认证主链里的旧模型 fallback
- 清理认证桥接 merge / helper 残留
- 补最小必要测试和文档

本卡不要做：
- 不要改 backfill / reconcile 脚本
- 不要恢复 legacy platform tenant fallback
- 不要扩大成认证框架重写

交付要求：
- 输出删除/降级清单
- 输出修改文件
- 输出执行命令
- 输出测试结果
- 输出剩余风险
```

**Codex 审计点**

- 是否真正删掉了认证主链 fallback，而不是只改注释
- 是否避免把迁移工具误删成运行时缺口
- 测试是否已经证明新模型成为唯一运行时输入

---

### CARD-13B 删除 carrier compatibility 运行时兜底

**目标**

继续移除 carrier requirement 体系里残留的 compatibility group / unregistered endpoint legacy behavior，让 requirement 成为唯一正式判定入口。

**前置条件**

- `CARD-11A` 已完成
- 已有 requirement 缺失 fail-closed 经验与回归

**范围**

- `CarrierPermissionReferenceSafetyService`（原 `CarrierCompatibilitySafetyService`）
- `ApiEndpointRequirementDecision`
- `ResourceService`
- 与上述 compatibility 行为删除直接相关的最小测试与文档

**明确要求**

- 删除后，新 carrier / endpoint 未注册时必须显式 fail-closed 或走明确审计拒绝
- 不要重新引入 `resource` 表或 compatibility resource 投影
- 不要把 requirement 缺失与 endpoint 未注册混成同一种 silent fallback

**验收标准**

- compatibility group 不再作为运行时兜底手段
- unregistered endpoint 不再“保持 legacy behavior”
- 测试和文档同步到 requirement 唯一入口口径

**复制给 Cursor 的提示词**

```text
请为 tiny-platform 执行 CARD-13B：删除 carrier compatibility 运行时兜底。

必须先阅读：
- AGENTS.md
- docs/TINY_PLATFORM_AUTHORIZATION_TASK_LIST.md
- docs/TINY_PLATFORM_PLATFORM_SCOPE_CURSOR_TASK_CARDS.md
- docs/TINY_PLATFORM_LEGACY_COMPATIBILITY_INVENTORY.md
- docs/TINY_PLATFORM_AUTHORIZATION_LAYERED_MODEL.md
- .agent/src/rules/50-testing.rules.md
- .agent/src/rules/58-cicd.rules.md
- .agent/src/rules/90-tiny-platform.rules.md
- .agent/src/rules/93-tiny-platform-authorization-model.rules.md

前置条件：
- CARD-11A 已完成

本卡只做：
- 删除 carrier compatibility group / unregistered endpoint legacy behavior
- 让 requirement 成为唯一正式判定入口
- 补最小必要测试和文档

本卡不要做：
- 不要恢复 resource 表或 compatibility projection
- 不要顺手做 carrier 全量重构
- 不要把 tiny-web 纳入范围

交付要求：
- 输出删除点清单
- 输出修改文件
- 输出执行命令
- 输出测试结果
- 输出剩余风险
```

**Codex 审计点**

- 是否真正删除了 compatibility runtime 兜底
- 未注册 endpoint 是否改成显式拒绝/可审计行为
- 是否避免 requirement 主链再次出现 silent legacy behavior

---

### CARD-13C 退役 `platformTenantCode/default` bootstrap 兼容配置

**目标**

把 `platformTenantCode/default` 从“仍可能被新环境或新脚本引用的 bootstrap 兼容配置”继续降级到明确历史配置，避免它再成为平台语义的隐性入口。

**前置条件**

- `CARD-11C`、`CARD-12B` 已完成
- 已确认主链平台语义只认 `PLATFORM` scope

**范围**

- `TenantBootstrapServiceImpl`
- `PlatformTenantProperties`
- `IdempotentProperties`
- 与该配置退役直接相关的最小脚本、测试和文档

**明确要求**

- 不要误删正常租户 `tenantCode` 路由能力
- 若仍需保留历史配置项，必须降为明确 deprecated / bootstrap-only 口径
- 不要让新环境初始化继续默认依赖 `default`

**验收标准**

- `platformTenantCode/default` 不再是新环境/新脚本的默认入口
- 正常平台语义与 bootstrap 历史配置完全分层
- 文档明确该配置的剩余用途或正式退场

**复制给 Cursor 的提示词**

```text
请为 tiny-platform 执行 CARD-13C：退役 platformTenantCode/default bootstrap 兼容配置。

必须先阅读：
- AGENTS.md
- docs/TINY_PLATFORM_TENANT_GOVERNANCE.md
- docs/TINY_PLATFORM_AUTHORIZATION_TASK_LIST.md
- docs/TINY_PLATFORM_PLATFORM_SCOPE_CURSOR_TASK_CARDS.md
- docs/TINY_PLATFORM_LEGACY_COMPATIBILITY_INVENTORY.md
- .agent/src/rules/50-testing.rules.md
- .agent/src/rules/58-cicd.rules.md
- .agent/src/rules/90-tiny-platform.rules.md
- .agent/src/rules/94-tiny-platform-tenant-governance.rules.md

前置条件：
- CARD-11C 已完成
- CARD-12B 已完成

本卡只做：
- 退役 platformTenantCode/default 的 bootstrap 兼容配置
- 明确剩余历史用途或删除默认值依赖
- 补最小必要测试和文档

本卡不要做：
- 不要破坏正常 tenantCode 路由
- 不要扩大成 tenant bootstrap 大重构
- 不要恢复 default tenant 平台语义

交付要求：
- 输出配置退役清单
- 输出修改文件
- 输出执行命令
- 输出测试结果
- 输出剩余风险
```

**Codex 审计点**

- 是否真的把 `platformTenantCode/default` 从新环境默认入口里退下来了
- 是否把 bootstrap 历史配置与平台运行时语义分开
- 是否避免误伤正常租户路由与现有运维脚本

---

### CARD-13D 删除 API/角色层历史别名与 compat flags

**目标**

继续清理 API/角色层里仍保留的历史别名和 compat flags，避免新调用继续沿用旧契约。

**前置条件**

- `CARD-12D` 已完成
- 已有文档明确当前正式 API / 权限契约

**范围**

- `RoleManagementAccessGuard`
- `RoleController`
- `RoleConstraintServiceImpl`
- 与这些别名/flags 删除直接相关的最小测试与文档

**明确要求**

- `resourceIds` 这类 alias 删除前要确认正式字段已有足够调用覆盖
- compat permission alias / compat role flags 删除后必须有清晰错误或迁移提示
- 不要扩大成整套 role management API 重写

**验收标准**

- API/角色层不再保留历史 alias 和 compat flags
- 新调用方只能走当前正式契约
- 回归测试与任务清单/文档口径一致

**复制给 Cursor 的提示词**

```text
请为 tiny-platform 执行 CARD-13D：删除 API/角色层历史别名与 compat flags。

必须先阅读：
- AGENTS.md
- docs/TINY_PLATFORM_AUTHORIZATION_MODEL.md
- docs/TINY_PLATFORM_AUTHORIZATION_TASK_LIST.md
- docs/TINY_PLATFORM_PLATFORM_SCOPE_CURSOR_TASK_CARDS.md
- docs/TINY_PLATFORM_PERMISSION_IDENTIFIER_SPEC.md
- .agent/src/rules/50-testing.rules.md
- .agent/src/rules/58-cicd.rules.md
- .agent/src/rules/90-tiny-platform.rules.md
- .agent/src/rules/92-tiny-platform-permission.rules.md
- .agent/src/rules/93-tiny-platform-authorization-model.rules.md

前置条件：
- CARD-12D 已完成

本卡只做：
- 删除 API/角色层历史 alias 与 compat flags
- 让正式字段/正式权限码成为唯一入口
- 补最小必要测试与文档

本卡不要做：
- 不要扩大成 role management 全链重写
- 不要重新引入 resourceIds / compat role flag 语义
- 不要越界到 unrelated controller

交付要求：
- 输出删除清单
- 输出修改文件
- 输出执行命令
- 输出测试结果
- 输出剩余风险
```

**Codex 审计点**

- 是否真正删掉了 API/角色层的历史 alias / compat flags
- 是否保住了当前正式契约和测试覆盖
- 是否避免误删无关 role management 功能

---

### CARD-13E 清理 active tooling 的 `default` 隐式平台入口与当前态文档残留旧口径

**目标**

收掉仍活跃在 real-link / 运维脚本里的“未配置则回退到 `default` 平台租户”隐式入口，并把当前态文档里仍残留的旧口径同步到实际实现，避免 Cursor 或运维继续把历史兼容壳当成现状。

**前置条件**

- `CARD-13A ~ CARD-13D` 已完成
- 主链平台语义已只认 `PLATFORM` scope
- `platformTenantCode/default` 已从运行时主链与 bootstrap 默认值中退场

**范围**

- `tiny-oauth-server/src/main/webapp/e2e/setup/real.global.setup.ts`
- `tiny-oauth-server/scripts/ensure-platform-admin.sh`
- `docs/TINY_PLATFORM_AUTHORIZATION_TASK_LIST.md`
- 与上述两条 active tooling / 当前态文档口径同步直接相关的最小测试与文档

**明确要求**

- `real.global.setup.ts` 中所有 `E2E_PLATFORM_TENANT_CODE ?? 'default'` 或等价静默回退必须删除
- 缺少 `E2E_PLATFORM_TENANT_CODE` 时，要么 fail-fast，要么明确仅在单测 helper 中保留“历史说明”但不得参与实际 real-link 执行路径
- `ensure-platform-admin.sh` 不允许再把 `PLATFORM_TENANT_CODE` 默认为 `default`；必须显式传入或直接失败
- 当前态文档必须明确：
  - 新租户初始化管理员角色是 `ROLE_TENANT_ADMIN`
  - `CARD-13A` 后认证读链只读当前激活作用域对应的单个 `scope_key`
- 不要扩大成 Playwright 全链重构
- 不要顺手修改 tenant bootstrap 行为本身
- 不要去动 `tiny-web`

**验收标准**

- active real-link / 运维脚本不再通过缺省值把平台语义偷偷绑定到 `default`
- 缺少平台租户配置时，脚本行为为显式报错或显式受控分支，而不是静默回退
- `TINY_PLATFORM_AUTHORIZATION_TASK_LIST.md` 与相关专题文档不再把 `ROLE_ADMIN`、`PLATFORM > GLOBAL` 桥接期口径写成当前态
- 至少补一组覆盖：
  - real-link env 解析在缺失 `E2E_PLATFORM_TENANT_CODE` 时的 fail-fast / 明确行为
  - `ensure-platform-admin.sh` 缺失 `PLATFORM_TENANT_CODE` 时的受控失败

**复制给 Cursor 的提示词**

```text
请为 tiny-platform 执行 CARD-13E：清理 active tooling 的 default 隐式平台入口与当前态文档残留旧口径。

必须先阅读：
- AGENTS.md
- docs/TINY_PLATFORM_AUTHORIZATION_TASK_LIST.md
- docs/TINY_PLATFORM_PLATFORM_SCOPE_CURSOR_TASK_CARDS.md
- docs/TINY_PLATFORM_TENANT_GOVERNANCE.md
- docs/TINY_PLATFORM_LEGACY_COMPATIBILITY_INVENTORY.md
- docs/TINY_PLATFORM_TESTING_PLAYBOOK.md
- .agent/src/rules/50-testing.rules.md
- .agent/src/rules/58-cicd.rules.md
- .agent/src/rules/90-tiny-platform.rules.md
- .agent/src/rules/91-tiny-platform-auth.rules.md
- .agent/src/rules/94-tiny-platform-tenant-governance.rules.md

前置条件：
- CARD-13A ~ CARD-13D 已完成

本卡只做：
1. 删除 active real-link tooling 中对 default 平台租户的静默回退
   - 重点检查 `tiny-oauth-server/src/main/webapp/e2e/setup/real.global.setup.ts`
   - 不允许实际执行路径继续出现 `E2E_PLATFORM_TENANT_CODE ?? 'default'`
2. 删除 active 运维脚本中对 default 平台租户的静默回退
   - 重点检查 `tiny-oauth-server/scripts/ensure-platform-admin.sh`
   - 缺少 `PLATFORM_TENANT_CODE` 时必须显式失败或受控退出
3. 同步当前态文档口径
   - 新租户初始化管理员角色必须写成 `ROLE_TENANT_ADMIN`
   - `CARD-13A` 后认证读链必须写成“单 active scope_key 真相源”，不能把桥接期 `PLATFORM > GLOBAL` 写成当前态
4. 补最小必要测试
   - helper / env 解析测试
   - 脚本受控失败测试（如仓库已有对应测试方式；若无，则至少给出可执行 smoke 验证）

本卡不要做：
- 不要扩大成 Playwright global setup 重构
- 不要改 tenant bootstrap 核心业务逻辑
- 不要改 tiny-web
- 不要顺手继续做下一阶段兼容清理

交付要求：
- 输出删除/收紧点清单
- 输出修改文件
- 输出执行命令
- 输出测试结果
- 输出剩余风险
- 额外说明：哪些分支只是“历史说明/单测语义”，哪些仍是 active runtime/tooling 路径
```

**Codex 审计点**

- active tooling 是否真的不再静默回退到 `default`
- 缺少 `E2E_PLATFORM_TENANT_CODE` / `PLATFORM_TENANT_CODE` 时是否变成显式可诊断行为
- 当前态文档是否已经和 `ROLE_TENANT_ADMIN`、单 `scope_key` 读链现状一致
- 是否严格控制范围，没有把这张卡扩成 e2e 或 bootstrap 大重构

---

## 13. 第八阶段剩余 bootstrap / 安全承接 / API 兼容边界收口任务卡（CARD-14A ~ CARD-14C）

> 适用背景：
> - `CARD-13A ~ CARD-13F` 已完成，主授权链与 active tooling 的大块历史兼容已经收口
> - 当前剩余的兼容边界主要集中在：
>   - bootstrap-only 的 `platformTenantCode` 历史入口
>   - 仅剩安全检查职责、但仍带 `compatibility` 命名的 carrier 服务
>   - API 层仍允许的裸数组用户分配请求体兼容
>
> 这一阶段的目标不是“继续大改主链”，而是把这些边界变成更清晰的当前态：
> - bootstrap-only 就只保留 bootstrap-only
> - safety service 就按 safety service 命名与使用
> - API 只保留正式契约，不再留裸数组兼容口

### 执行状态（2026-04）

- `CARD-14A`：已完成
- `CARD-14B`：已完成
- `CARD-14C`：已完成

### CARD-14A 收紧 bootstrap-only 平台租户兼容入口

**目标**

继续收口 `platformTenantCode` 相关历史入口，让它只在“平台模板缺失、且显式允许历史回填”的 bootstrap / dev 自愈路径生效；正常启动、正常模板存在场景不得再隐式依赖该配置。

**前置条件**

- `CARD-13C`、`CARD-13E` 已完成
- 主链平台语义已只认 `PLATFORM` scope
- active tooling 已不再静默回退 `default`

**范围**

- `PlatformTenantProperties`
- `TenantBootstrapServiceImpl`
- `PlatformTemplateDevAutoBootstrapRunner`
- 与上述 bootstrap-only 兼容口径直接相关的最小测试、脚本说明、文档

**明确要求**

- 不要把 `platformTenantCode` 重新带回运行时主链
- 仅在“平台模板缺失且明确允许历史回填”的路径读取该配置
- 模板已存在时，缺少 `platformTenantCode` 应该是 no-op / 不触发，而不是要求所有环境都配置
- 模板缺失且确实需要历史回填时，缺配置必须 fail-fast，错误信息要能直接说明“这是 bootstrap 历史入口，不是平台运行时语义”
- 不要扩大成 tenant bootstrap 大重构
- 不要碰 `tiny-web`

**验收标准**

- 正常模板已存在场景，不再因为没配 `platformTenantCode` 而产生误报或隐式分支
- 模板缺失 + 需要历史回填时，行为为“显式配置才执行，否则显式失败”
- `PlatformTenantProperties` / `TenantBootstrapServiceImpl` / `PlatformTemplateDevAutoBootstrapRunner` 的注释、日志、异常都不再把该配置描述成平台运行时事实来源
- 文档能明确区分：
  - `PLATFORM` scope 是运行时真相源
  - `platformTenantCode` 只是 bootstrap-only 历史入口

**复制给 Cursor 的提示词**

```text
请为 tiny-platform 执行 CARD-14A：收紧 bootstrap-only 平台租户兼容入口。

必须先阅读：
- AGENTS.md
- docs/TINY_PLATFORM_TENANT_GOVERNANCE.md
- docs/TINY_PLATFORM_AUTHORIZATION_TASK_LIST.md
- docs/TINY_PLATFORM_PLATFORM_SCOPE_CURSOR_TASK_CARDS.md
- docs/TINY_PLATFORM_LEGACY_COMPATIBILITY_INVENTORY.md
- .agent/src/rules/50-testing.rules.md
- .agent/src/rules/58-cicd.rules.md
- .agent/src/rules/90-tiny-platform.rules.md
- .agent/src/rules/94-tiny-platform-tenant-governance.rules.md

前置条件：
- CARD-13C 已完成
- CARD-13E 已完成

本卡只做：
1. 收紧 `PlatformTenantProperties`、`TenantBootstrapServiceImpl`、`PlatformTemplateDevAutoBootstrapRunner` 这条 bootstrap-only 兼容入口
2. 让 `platformTenantCode` 只在“平台模板缺失、且显式允许历史回填”的路径生效
3. 补最小必要测试、日志/异常文案、文档口径

本卡不要做：
- 不要改平台运行时 scope 判定
- 不要恢复任何 `default` 隐式平台入口
- 不要做 tenant bootstrap 大重构
- 不要改 tiny-web

交付要求：
- 输出哪些路径仍允许读取 `platformTenantCode`
- 输出哪些路径已改成 no-op 或 fail-fast
- 输出修改文件
- 输出执行命令
- 输出测试结果
- 输出剩余风险
```

**Codex 审计点**

- `platformTenantCode` 是否只剩 bootstrap-only 历史入口
- 模板已存在 / 模板缺失两类场景是否区分清楚
- 是否避免把 bootstrap 配置重新写成运行时平台真相源

---

### CARD-14B 去掉 carrier 安全承接中的 compatibility 语义

**目标**

把 `CarrierCompatibilitySafetyService` 收口成当前真实职责的正式命名与接口：它已经不再维护 runtime compatibility fallback，而只是做权限引用安全检查。需要把“compatibility”从主命名和文档口径中拿掉，避免后续开发误以为 carrier requirement 仍有兼容主链。

**前置条件**

- `CARD-13B` 已完成
- requirement fallback 与未注册 endpoint legacy behavior 已 fail-closed

**范围**

- `CarrierPermissionReferenceSafetyService`（原 `CarrierCompatibilitySafetyService`）
- 直接调用它的 service / repository / test
- 与其命名/职责变化直接相关的最小文档

**明确要求**

- 可以重命名为更符合现状的名字，例如 `CarrierPermissionReferenceSafetyService` 或等价名称
- 只能保留当前真实职责：
  - 判断删除/解绑权限时是否仍被 menu / ui_action / api_endpoint / requirement 表引用
- 不要重新引入 requirement compatibility group 或 fallback 逻辑
- 不要扩大成 carrier 体系重构

**验收标准**

- 运行时代码主线不再把该服务描述成 compatibility service
- 调用方名称、注释、测试都能反映“权限引用安全检查”而不是“兼容兜底”
- `docs/TINY_PLATFORM_LEGACY_COMPATIBILITY_INVENTORY.md` / 任务清单等不再把它误描述为仍保留 compatibility 主链

**复制给 Cursor 的提示词**

```text
请为 tiny-platform 执行 CARD-14B：去掉 carrier 安全承接中的 compatibility 语义。

必须先阅读：
- AGENTS.md
- docs/TINY_PLATFORM_AUTHORIZATION_MODEL.md
- docs/TINY_PLATFORM_AUTHORIZATION_TASK_LIST.md
- docs/TINY_PLATFORM_PLATFORM_SCOPE_CURSOR_TASK_CARDS.md
- docs/TINY_PLATFORM_LEGACY_COMPATIBILITY_INVENTORY.md
- .agent/src/rules/50-testing.rules.md
- .agent/src/rules/58-cicd.rules.md
- .agent/src/rules/90-tiny-platform.rules.md
- .agent/src/rules/93-tiny-platform-authorization-model.rules.md

前置条件：
- CARD-13B 已完成

本卡只做：
1. 收口 `CarrierCompatibilitySafetyService` 的命名与接口
2. 让它只表达“权限引用安全检查”当前职责
3. 同步最小必要测试与文档

本卡不要做：
- 不要恢复任何 requirement compatibility fallback
- 不要扩大成 carrier 大重构
- 不要改无关的 Resource/Menu 主链行为

交付要求：
- 输出重命名/收口前后职责对照
- 输出修改文件
- 输出执行命令
- 输出测试结果
- 输出剩余风险
```

**Codex 审计点**

- 是否真正去掉了 compatibility 语义，而不是只改类名不改文档/调用
- 是否保住了“最后一个 carrier 删除时的权限引用安全检查”职责
- 是否没有借机把已删除的 requirement fallback 带回来

---

### CARD-14C 删除 RoleController 用户分配裸数组请求体兼容

**目标**

把 `POST /sys/roles/{id}/users` 收紧为唯一正式契约：对象体 `{ userIds, scopeType, scopeId }`。删除裸数组 `List<Long>` 请求体兼容，避免新调用继续沿用旧 API 形态。

**前置条件**

- `CARD-13D` 已完成
- 当前角色写入正式契约已明确为对象体/显式字段

**范围**

- `RoleController`
- `RoleService` / 相关 DTO / 前端 API helper / 页面调用 / 测试
- 与该请求体兼容删除直接相关的最小文档

**明确要求**

- 删除 `parseUserAssignmentRequest(...)` 中对裸数组 `List<?>` 的兼容分支
- 保留并明确对象体契约：
  - `userIds`
  - `scopeType`
  - `scopeId`
- 裸数组请求体必须得到清晰的 4xx 错误，而不是静默接受
- 前端 helper / 页面 / 测试必须全部切到对象体
- 不要扩大成 role management API 全链重写

**验收标准**

- `RoleController` 不再接受裸数组用户分配请求体
- 前端/测试没有继续把裸数组当正式写入契约
- 文档与任务清单能明确“用户分配只认对象体”

**复制给 Cursor 的提示词**

```text
请为 tiny-platform 执行 CARD-14C：删除 RoleController 用户分配裸数组请求体兼容。

必须先阅读：
- AGENTS.md
- docs/TINY_PLATFORM_AUTHORIZATION_MODEL.md
- docs/TINY_PLATFORM_AUTHORIZATION_TASK_LIST.md
- docs/TINY_PLATFORM_PLATFORM_SCOPE_CURSOR_TASK_CARDS.md
- .agent/src/rules/50-testing.rules.md
- .agent/src/rules/58-cicd.rules.md
- .agent/src/rules/90-tiny-platform.rules.md
- .agent/src/rules/93-tiny-platform-authorization-model.rules.md

前置条件：
- CARD-13D 已完成

本卡只做：
1. 删除 `POST /sys/roles/{id}/users` 的裸数组请求体兼容
2. 统一到对象体 `{ userIds, scopeType, scopeId }`
3. 补最小必要后端/前端/测试与文档

本卡不要做：
- 不要扩大成整个 role management API 重写
- 不要修改无关权限模型
- 不要顺手改 tiny-web

交付要求：
- 输出请求体契约变化
- 输出修改文件
- 输出执行命令
- 输出测试结果
- 输出剩余风险
```

**Codex 审计点**

- `RoleController` 是否真的不再接受裸数组
- 前端 helper / 页面 / 测试是否已经统一到对象体
- 错误响应是否清晰可诊断，而不是静默兼容

---

## 14. 第九阶段当前态漂移与活跃兼容尾巴收口任务卡（第一批：CARD-15C / CARD-15B / CARD-15A）

> 目标：在 `CARD-14E` 已明确“当前态真相源”之后，继续把**仍在活跃代码里**的兼容尾巴往“显式契约 + fail-closed + 小范围可验证”推进。  
> 本节先细化 3 张最高优先级卡：
> - `CARD-15C`：active tooling / bootstrap compat 尾巴
> - `CARD-15B`：`roleCodes <- ROLE_*` runtime compat 尾巴
> - `CARD-15A`：`/sys/resources` compatibility facade 主链
>
> 推荐顺序：
> 1. `CARD-15C`
> 2. `CARD-15B`
> 3. `CARD-15A`
>
> 冻结边界：
> - `tiny-web` 继续视为冻结历史模块，不纳入这一阶段长期演进主线
> - 不在同一张卡里同时推进“大规模历史文档治理 + 主链契约重写 + 前端全量重构”
> - 若扫描到超出本卡范围的新消费者或新阻断，优先记录到剩余风险，不顺手扩大范围

**执行状态（2026-04，更新到 2026-04-13）**

- `CARD-15C`：已完成
- `CARD-15B`：已完成
- `CARD-15A`：已完成（`15A-1 ~ 15A-8` 已收口：permission lookup 拆到 `/sys/permissions/options`、资源页 no-op 查询壳移除、后端 `permission` 查询契约删除）

### CARD-15C 清理 active tooling / bootstrap compat 尾巴

**目标**

把 real-link / E2E 工具链中仍然活跃的 `default` 模板来源与 `ROLE_ADMIN` 回退尾巴删除掉，统一到“显式平台租户配置 + `ROLE_TENANT_ADMIN` 当前态”。

**前置条件**

- `CARD-13E` 已完成
- `real.global.setup.ts` / `requireRealLinkPlatformTenantCode(...)` 已要求显式 `E2E_PLATFORM_TENANT_CODE`
- 当前 active tooling 禁止静默回退 `default` 的前端口径已经稳定

**范围**

- `tiny-oauth-server/scripts/e2e/ensure-scheduling-e2e-auth.sh`
- 如需同步：`tiny-oauth-server/scripts/verify-platform-login-auth-chain.sh`
- 与该脚本口径直接相关的文档：
  - `tiny-oauth-server/src/main/webapp/e2e/README.md`
  - `tiny-oauth-server/docs/E2E_AUTOMATION_IDENTITIES.md`
- 与该口径直接相关的测试：
  - `tiny-oauth-server/src/main/webapp/src/e2e/realGlobalSetup.test.ts`

**明确要求**

- `ensure-scheduling-e2e-auth.sh` 不再硬编码 `default` 作为调度 bootstrap 模板来源
- 平台模板来源必须由**已存在的显式变量**驱动：
  - 优先复用 `E2E_PLATFORM_TENANT_CODE`
  - 不要再发明第二套平台租户环境变量
- 删除 `ROLE_TENANT_ADMIN -> ROLE_ADMIN` 的历史回退
- 若平台租户 code 或模板角色缺失，应 **fail-fast** 并给出可诊断错误；不要静默补成旧口径
- 不要改变以下既有边界：
  - bind / readonly / primary 三类自动化身份的职责划分
  - `real.global.setup.ts` 已固化的派生租户规则
  - 非本卡范围内的登录态生成与 Playwright orchestration

**验收标准**

- 脚本中不再存在 `findTenantIdByCode(..., "default")` 作为调度模板主来源
- 脚本中不再存在 `ROLE_ADMIN` 的模板角色回退
- 缺少平台租户配置或模板角色时，失败信息明确指向显式配置或 seed 缺口
- `realGlobalSetup.test.ts` 与相关 README 已同步到“显式平台租户 + `ROLE_TENANT_ADMIN`”口径

**建议验证**

- `npm --prefix tiny-oauth-server/src/main/webapp run test:unit -- src/e2e/realGlobalSetup.test.ts`
- 若本机具备 real-link 前置变量，再跑：
  - `bash tiny-oauth-server/scripts/verify-platform-login-auth-chain.sh`

**复制给 Cursor 的提示词**

```text
请为 tiny-platform 执行 CARD-15C：清理 active tooling / bootstrap compat 尾巴。

必须先阅读：
- AGENTS.md
- docs/TINY_PLATFORM_TESTING_PLAYBOOK.md
- docs/TINY_PLATFORM_AUTHORIZATION_TASK_LIST.md
- docs/TINY_PLATFORM_PLATFORM_SCOPE_CURSOR_TASK_CARDS.md
- docs/TINY_PLATFORM_TENANT_GOVERNANCE.md
- tiny-oauth-server/src/main/webapp/e2e/README.md
- .agent/src/rules/50-testing.rules.md
- .agent/src/rules/58-cicd.rules.md
- .agent/src/rules/90-tiny-platform.rules.md
- .agent/src/rules/91-tiny-platform-auth.rules.md

前置条件：
- CARD-13E 已完成
- real.global.setup 已要求显式 E2E_PLATFORM_TENANT_CODE

本卡只做：
1. 清理 ensure-scheduling-e2e-auth.sh 里仍活跃的 default 模板来源
2. 删除 ROLE_TENANT_ADMIN -> ROLE_ADMIN 的历史回退
3. 保持现有 real-link 工具链显式平台租户契约不变
4. 补最小必要测试与文档

本卡不要做：
- 不要新增第二套平台租户环境变量
- 不要扩大成 globalSetup / Playwright 全量重写
- 不要顺手改 unrelated E2E 身份派生逻辑
- 不要把 tiny-web 纳入长期演进范围

建议验证：
- npm --prefix tiny-oauth-server/src/main/webapp run test:unit -- src/e2e/realGlobalSetup.test.ts
- 若本机具备 real-link 前置变量，再跑 bash tiny-oauth-server/scripts/verify-platform-login-auth-chain.sh

交付要求：
- 输出脚本兼容尾巴删除前后差异
- 输出修改文件
- 输出执行命令
- 输出测试结果
- 输出环境前置不足时的处理方式
- 输出剩余风险
```

**Codex 审计点**

- 是否真的移除了 `default` 模板来源，而不是只改文案
- 是否真的删掉了 `ROLE_ADMIN` 回退
- 是否保住了 `CARD-13E` 已固化的显式平台租户契约
- 缺少环境或模板时是否 fail-fast 且错误可诊断

---

### CARD-15B 切断 `roleCodes <- ROLE_*` runtime compat 尾巴

**目标**

把 `SecurityUser` / `JwtTokenCustomizer` 中仍存活的 `ROLE_*` 兼容尾巴继续收紧到“显式 `roleCodes` only”契约，避免 Session carrier 和 JWT helper 再把角色码从 authority 里恢复出来。

**前置条件**

- `CARD-09C2 ~ CARD-09C4` 已完成
- `CARD-11B` / `CARD-11D` 已完成
- `CamundaIdentityBridgeFilter` 等合法消费者已经以显式 `roleCodes` 为第一入口

**范围**

- `tiny-oauth-server/src/main/java/com/tiny/platform/core/oauth/model/SecurityUser.java`
- `tiny-oauth-server/src/main/java/com/tiny/platform/core/oauth/config/JwtTokenCustomizer.java`
- 如确有必要，允许同步最小调用点或测试装配，但不得扩成全仓重构
- 与该契约变化直接相关的测试：
  - `tiny-oauth-server/src/test/java/com/tiny/platform/core/oauth/model/SecurityUserTest.java`
  - `tiny-oauth-server/src/test/java/com/tiny/platform/core/oauth/config/JwtTokenCustomizerTest.java`
  - `tiny-oauth-server/src/test/java/com/tiny/platform/application/oauth/workflow/CamundaIdentityBridgeFilterTest.java`
  - `tiny-oauth-server/src/test/java/com/tiny/platform/core/oauth/integration/PartialMfaFormLoginIntegrationTest.java`

**明确要求**

- `SecurityUser.buildAuthorities(...)` 不再把 `role.code` 当一般 Session authority 输出
- `JwtTokenCustomizer.resolveRoleCodes(...)` 不再从 `ROLE_* authorities` 补回 `roleCodes`
- 显式 `roleCodes` 字段必须保留，且仍供合法消费者使用
- 不得恢复以下旧契约：
  - `role.name`
  - 新签发 `ROLE_* + permission` 混合态 token
  - 旧 JWT / Session 解码兼容窗口
- 若发现阻断消费者超出本卡范围：
  - `tiny-oauth-server` 主线内最小适配可以做
  - `tiny-web` 或冻结历史模块只记录风险，不顺手展开

**验收标准**

- `SecurityUser` 从 `Role` 集合构造时，不再额外生成 `ROLE_*` authorities
- `JwtTokenCustomizer.resolveRoleCodes(...)` 在缺显式 `roleCodes` 时返回空集合，而不是从 `ROLE_* authorities` 反推
- MFA session / 普通 session / JWT 三类路径仍能通过显式 `roleCodes` 工作
- 文档与测试不再把 `ROLE_* authorities` 当成角色码消费主链

**建议验证**

- `mvn -pl tiny-oauth-server -Dtest=SecurityUserTest,JwtTokenCustomizerTest,CamundaIdentityBridgeFilterTest,PartialMfaFormLoginIntegrationTest test`

**复制给 Cursor 的提示词**

```text
请为 tiny-platform 执行 CARD-15B：切断 roleCodes <- ROLE_* runtime compat 尾巴。

必须先阅读：
- AGENTS.md
- docs/TINY_PLATFORM_AUTHORIZATION_MODEL.md
- docs/TINY_PLATFORM_AUTHORIZATION_TASK_LIST.md
- docs/TINY_PLATFORM_PLATFORM_SCOPE_CURSOR_TASK_CARDS.md
- docs/TINY_PLATFORM_LEGACY_COMPATIBILITY_INVENTORY.md
- docs/TINY_PLATFORM_ROLE_CODE_AUTHORITY_CONSUMER_INVENTORY.md
- .agent/src/rules/50-testing.rules.md
- .agent/src/rules/58-cicd.rules.md
- .agent/src/rules/90-tiny-platform.rules.md
- .agent/src/rules/91-tiny-platform-auth.rules.md

前置条件：
- CARD-09C2 ~ CARD-09C4 已完成
- CARD-11B / CARD-11D 已完成

本卡只做：
1. 收紧 SecurityUser / JwtTokenCustomizer 中仍活跃的 ROLE_* compat 尾巴
2. 让显式 roleCodes 成为唯一角色码输入契约
3. 补最小必要测试与文档

本卡不要做：
- 不要恢复 ROLE_* 主链 authority
- 不要恢复旧 JWT / Session 解码兼容窗口
- 不要扩大成全仓角色消费者改造
- 不要把 tiny-web 纳入长期演进主线

建议验证：
- mvn -pl tiny-oauth-server -Dtest=SecurityUserTest,JwtTokenCustomizerTest,CamundaIdentityBridgeFilterTest,PartialMfaFormLoginIntegrationTest test

交付要求：
- 输出契约变化说明
- 输出修改文件
- 输出执行命令
- 输出测试结果
- 输出仍未覆盖的消费者或剩余风险
```

**Codex 审计点**

- `SecurityUser.buildAuthorities(...)` 是否真的不再输出 `role.code`
- `JwtTokenCustomizer.resolveRoleCodes(...)` 是否真的去掉了 `ROLE_*` 恢复
- 显式 `roleCodes` 是否仍然稳定可用
- MFA session / 普通 session / JWT 三类路径是否都补了回归

---

### CARD-15A 退役 `/sys/resources` compatibility facade 主链（持续收口）

**当前状态补充（2026-04-13）**

- `15A-1 / 15A-2` 已完成：资源写链切到显式 `requiredPermissionId`，`permission` 字符串不再隐式 INSERT / 推断权限主数据，资源表单已切到 lookup + 派生只读 `permission`
- `15A-3` 已完成：删除 `/sys/resources/permission/{permission}` 读侧入口
- `15A-4` 已完成：删除重复 `/sys/resources/menus*` 路由，并把 `/sys/menus/check-*` 的内部实现下沉到 menu carrier
- `15A-5` 已完成：菜单写链切到显式 `requiredPermissionId`，`MenuForm.vue` 不再允许手填 `permission`，`MenuServiceImpl` 对 legacy `permission`-only payload fail-closed
- `15A-6` 已完成：permission lookup 从 `/sys/resources/permission-options` 拆到 `/sys/permissions/options`，资源/菜单表单改用独立 permission API
- `15A-7` 已完成：资源管理页 no-op 查询壳删除，前端未使用 `resourceList()/ResourceQuery` 清零
- `15A-8` 已完成：后端 `ResourceRequestDto` 与 `ResourceServiceImpl` 删除 permission 查询参数/分支，保留 `permission` 派生展示

**目标**

把 `/sys/resources` compatibility facade 再往前收紧一层：保留当前控制面路由与运行时自省端点，但让资源管理写链不再通过任意 `permission` 字符串隐式扩写权限主数据，改为以显式 `requiredPermissionId` 为主。

**前置条件**

- `resource` 表已物理删除
- `menu / ui_action / api_endpoint + required_permission_id + *_permission_requirement` 已是当前 carrier 主链
- 当前前端和控制面仍通过 `/sys/resources` 做兼容聚合管理

**范围**

- 后端：
  - `tiny-oauth-server/src/main/java/com/tiny/platform/application/controller/resource/ResourceController.java`
  - `tiny-oauth-server/src/main/java/com/tiny/platform/infrastructure/auth/resource/service/ResourceService.java`
  - `tiny-oauth-server/src/main/java/com/tiny/platform/infrastructure/auth/resource/service/ResourceServiceImpl.java`
  - `tiny-oauth-server/src/main/java/com/tiny/platform/infrastructure/auth/resource/service/ResourcePermissionBindingService.java`
  - `tiny-oauth-server/src/main/java/com/tiny/platform/infrastructure/auth/resource/dto/ResourceCreateUpdateDto.java`
- 前端（仅当写入契约变化需要同步）：
  - `tiny-oauth-server/src/main/webapp/src/api/resource.ts`
  - `tiny-oauth-server/src/main/webapp/src/views/resource/ResourceForm.vue`
  - `tiny-oauth-server/src/main/webapp/src/views/resource/resource.vue`
- 与本卡直接相关的测试：
  - `tiny-oauth-server/src/test/java/com/tiny/platform/application/controller/resource/ResourceControllerTest.java`
  - `tiny-oauth-server/src/test/java/com/tiny/platform/application/controller/resource/ResourceControllerRbacIntegrationTest.java`
  - `tiny-oauth-server/src/test/java/com/tiny/platform/application/controller/resource/ResourceControllerApiEndpointTemplateUriIntegrationTest.java`
  - `tiny-oauth-server/src/test/java/com/tiny/platform/infrastructure/auth/resource/service/ResourceServiceImplTest.java`
  - `tiny-oauth-server/src/test/java/com/tiny/platform/infrastructure/menu/service/MenuServiceImplTest.java`
  - 如改前端：`tiny-oauth-server/src/main/webapp/src/views/resource/resource.test.ts`

**明确要求**

- 本卡只做“第一刀”，**不**要求一张卡里彻底删除 `/sys/resources` 路由
- `ResourceController` 的运行时自省端点暂时保留：
  - `/sys/resources/runtime/ui-actions`
  - `/sys/resources/runtime/api-access`
- 写链必须改为“显式 `requiredPermissionId` 为主”：
  - 可以保留 `permission` 字符串作为展示或兼容输入
  - 但不得再因为任意新字符串而隐式 `INSERT permission`
- 若请求只给了 `permission` 字符串：
  - 仅允许在**能无歧义映射到现有 permission 主数据**时继续兼容
  - 无法映射时必须 fail-closed，返回清晰 4xx / `BusinessException`
- 不得恢复 `resource` 表、shared-id、compatibility group 或新的 permission alias
- 不得顺手把资源管理页、菜单管理页、carrier requirement 做大范围重写

**验收标准**

- `ResourcePermissionBindingService` 不再从任意 `permission` 字符串隐式创建权限主数据
- `ResourceCreateUpdateDto` / 前端写入契约已具备显式 `requiredPermissionId` 主入口
- `/sys/resources/runtime/ui-actions` 与 `/runtime/api-access` 行为保持不变
- 最小回归覆盖：
  - 显式 `requiredPermissionId` 正常写入
  - 仅给 `permission` 且能映射现有主数据时兼容通过
  - 仅给 `permission` 且无法映射时 fail-closed

**建议验证**

- `mvn -pl tiny-oauth-server -Dtest=ResourceControllerTest,ResourceControllerRbacIntegrationTest,ResourceControllerApiEndpointTemplateUriIntegrationTest,ResourceServiceImplTest,MenuServiceImplTest test`
- 若改前端：
  - `npm --prefix tiny-oauth-server/src/main/webapp run test:unit -- src/views/resource/resource.test.ts`

**复制给 Cursor 的提示词**

```text
请为 tiny-platform 执行 CARD-15A：退役 /sys/resources compatibility facade 主链（第一刀）。

必须先阅读：
- AGENTS.md
- docs/TINY_PLATFORM_AUTHORIZATION_MODEL.md
- docs/TINY_PLATFORM_AUTHORIZATION_TASK_LIST.md
- docs/TINY_PLATFORM_PLATFORM_SCOPE_CURSOR_TASK_CARDS.md
- docs/TINY_PLATFORM_LEGACY_COMPATIBILITY_INVENTORY.md
- .agent/src/rules/50-testing.rules.md
- .agent/src/rules/58-cicd.rules.md
- .agent/src/rules/90-tiny-platform.rules.md
- .agent/src/rules/92-tiny-platform-permission.rules.md
- .agent/src/rules/93-tiny-platform-authorization-model.rules.md

前置条件：
- resource 表已删除
- carrier split 与 required_permission_id 主链已稳定

本卡只做：
1. 把 /sys/resources 写链改成显式 requiredPermissionId 为主
2. 禁止通过任意 permission 字符串隐式扩写 permission 主数据
3. 保持 runtime/ui-actions 与 runtime/api-access 端点行为不变
4. 补最小必要后端/前端/测试与文档

本卡不要做：
- 不要一张卡里彻底删除 /sys/resources 路由
- 不要删除 runtime/ui-actions 或 runtime/api-access
- 不要恢复 resource 表或 shared-id 语义
- 不要扩大成资源/菜单全量重构
- 不要把 tiny-web 纳入长期演进主线

建议验证：
- mvn -pl tiny-oauth-server -Dtest=ResourceControllerTest,ResourceControllerRbacIntegrationTest,ResourceControllerApiEndpointTemplateUriIntegrationTest,ResourceServiceImplTest,MenuServiceImplTest test
- 若改前端：npm --prefix tiny-oauth-server/src/main/webapp run test:unit -- src/views/resource/resource.test.ts

交付要求：
- 输出写链契约变化
- 输出兼容保留面与删除面
- 输出修改文件
- 输出执行命令
- 输出测试结果
- 输出剩余风险
```

**Codex 审计点**

- 是否真的把 `requiredPermissionId` 提升为写链主入口
- 是否真的禁止了“任意 permission 字符串 -> 新 permission 主数据”隐式扩写
- 是否保住了 runtime 自省端点与现有前端主流程
- 是否把本卡控制在“第一刀”范围，没有扩成大重构

---

## 15. 第九阶段历史材料硬化与减噪任务卡（第二批：CARD-14G / CARD-14H / CARD-14I / CARD-15D）

> 目标：继续把“历史文档容易被误读成当前态”和“历史 schema / 示例容易被误读成活动真相源”的风险收紧。  
> 这组卡不追求大规模内容重写，而是强调：
> - **历史材料保留真实性**
> - **当前态入口要更醒目**
> - **启发式守卫而不是重 CI**
> - **减噪优先于重组仓库**
>
> 推荐顺序：
> 1. `CARD-14G`
> 2. `CARD-14H`
> 3. `CARD-14I`
> 4. `CARD-15D`
>
> 冻结边界：
> - 不把历史计划、历史设计文档整篇重写成“全是 DONE”
> - 不为了减噪而大规模迁目录、改链接或破坏历史引用
> - 若某历史文档正文与当前态冲突，优先通过页眉 / 指针 / 表前说明解决；除非本卡明确允许，不顺手改正文细节

**执行状态（2026-04）**

- `CARD-14G`：待做
- `CARD-14H`：待做
- `CARD-14I`：待做
- `CARD-15D`：待做

### CARD-14G 历史计划 / 兼容评估归档硬化

**目标**

给历史计划文档、兼容评估文档补统一的“历史快照 / 非当前运行态真相源”页眉和表前说明，降低它们被误当成当前主链结论的概率，同时保留历史裁决与证据痕迹。

**前置条件**

- `CARD-14F` 已完成或至少已明确当前态真相源文档
- `TINY_PLATFORM_AUTHORIZATION_TASK_LIST.md`、`TINY_PLATFORM_AUTHORIZATION_MODEL.md`、`TINY_PLATFORM_AUTHORIZATION_DOC_MAP.md` 已能承担当前态入口职责

**范围**

- `docs/TINY_PLATFORM_AUTHORIZATION_LEGACY_REMOVAL_PLAN.md`
- `docs/PERMISSION_REFACTOR_COMPATIBILITY_LAYER_ASSESSMENT.md`
- 如有必要，允许最小同步：
  - `docs/TINY_PLATFORM_AUTHORIZATION_DOC_MAP.md`

**明确要求**

- 每份文档首页必须出现：
  - “历史快照 / 归档评估 / 非当前运行态真相源”之类的明确页眉
  - 指向当前真相源文档的指针
- 对包含大量表格结论的文档，表格前必须补一句总说明，明确：
  - 表内状态代表**当时评估窗口**
  - 不直接代表当前运行态 still active / still pending
- 不要整表刷成 `DONE` / `REMOVED`
- 不要大改历史结论正文；若个别条目明显会被误解，优先用“注释型说明”而不是重写历史记录
- 若 `AUTHORIZATION_DOC_MAP` 对这些文档的描述仍可能让人误以为“当前态”，允许做一两行入口文字对齐，但不要扩大成文档地图大重构

**验收标准**

- 两份历史文档首页都显式声明“非当前运行态真相源”
- 表格密集区域前都有“这是历史快照/评估窗口”的总说明
- `TINY_PLATFORM_AUTHORIZATION_DOC_MAP.md` 如有更新，应能明确把它们归类到“历史/归档参考”
- 全文不出现把历史计划整体刷成当前完成度的行为

**建议验证**

- `rg -n "历史快照|归档|非当前运行态真相源|当前真相源|当前运行态" docs/TINY_PLATFORM_AUTHORIZATION_LEGACY_REMOVAL_PLAN.md docs/PERMISSION_REFACTOR_COMPATIBILITY_LAYER_ASSESSMENT.md docs/TINY_PLATFORM_AUTHORIZATION_DOC_MAP.md`

**复制给 Cursor 的提示词**

```text
请为 tiny-platform 执行 CARD-14G：历史计划 / 兼容评估归档硬化。

必须先阅读：
- AGENTS.md
- docs/TINY_PLATFORM_AUTHORIZATION_DOC_MAP.md
- docs/TINY_PLATFORM_AUTHORIZATION_MODEL.md
- docs/TINY_PLATFORM_AUTHORIZATION_TASK_LIST.md
- docs/TINY_PLATFORM_PLATFORM_SCOPE_CURSOR_TASK_CARDS.md
- docs/TINY_PLATFORM_AUTHORIZATION_LEGACY_REMOVAL_PLAN.md
- docs/PERMISSION_REFACTOR_COMPATIBILITY_LAYER_ASSESSMENT.md

前置条件：
- 当前态真相源已经由 TASK_LIST / AUTHORIZATION_MODEL / DOC_MAP 承接

本卡只做：
1. 给历史计划 / 兼容评估文档补统一页眉
2. 给表格密集区域补“历史评估窗口”总说明
3. 如有必要，最小同步 DOC_MAP 的入口描述

本卡不要做：
- 不要整篇重写历史计划
- 不要整表刷成 DONE / REMOVED
- 不要扩大成全仓文档语言统一工程

建议验证：
- rg -n "历史快照|归档|非当前运行态真相源|当前真相源|当前运行态" docs/TINY_PLATFORM_AUTHORIZATION_LEGACY_REMOVAL_PLAN.md docs/PERMISSION_REFACTOR_COMPATIBILITY_LAYER_ASSESSMENT.md docs/TINY_PLATFORM_AUTHORIZATION_DOC_MAP.md

交付要求：
- 输出新增的页眉/说明位置
- 输出修改文件
- 输出执行命令
- 输出验证结果
- 输出仍保留的历史正文范围与原因
```

**Codex 审计点**

- 是否真的把“历史快照”信号放到了最显眼位置
- 是否避免把历史表格刷成当前态
- 是否只做了归档硬化，没有顺手重写历史证据

---

### CARD-14H 历史设计长文页眉 + 当前态指针

**目标**

给历史设计长文补统一页眉与“当前态请看哪里”的指针，降低它们被误读为当前运行态规范的概率，同时保留大篇幅设计讨论的考古价值。

**前置条件**

- `CARD-14E` / `CARD-14F` 已把当前态真相源固定下来
- 历史长文仍有被频繁打开、但容易误读的风险

**范围**

- `docs/TINY_PLATFORM_AUTHORIZATION_PHASE1_TECHNICAL_DESIGN.md`
- `docs/tiny-platform-saas-overall-design.md`
- `docs/TINY_PLATFORM_MODULE_GAP_ANALYSIS.md`

**明确要求**

- 每份文档首页必须补：
  - “历史设计基线 / 历史讨论稿 / 附录型盘点 / 非当前运行态真相源”页眉
  - 指向当前真相源的 1-2 个链接
- 对 `TINY_PLATFORM_MODULE_GAP_ANALYSIS.md`：
  - 保持其“附录/盘点”定位
  - 不要把粗略百分比或缺口表整体改写成当前任务状态
- 对 `tiny-platform-saas-overall-design.md`：
  - 只加页眉和前置说明，不做大篇幅正文刷新
  - 若文中仍有明显“下一步将...”且无时间/历史说明的句子，优先在文首说明其为目标模型讨论，不顺手全篇改句式
- 对 `AUTHORIZATION_PHASE1_TECHNICAL_DESIGN.md`：
  - 保留 Phase1 历史裁决
  - 明确其不是当前执行真相，当前完成度以 Task List 为准

**验收标准**

- 三份文档首页都能一眼看出“历史设计 / 附录 / 非当前真相源”
- 每份文档都至少有一处明确指向 `TINY_PLATFORM_AUTHORIZATION_TASK_LIST.md` 或 `TINY_PLATFORM_TENANT_GOVERNANCE.md`
- 不发生全篇状态刷新或大规模正文改写

**建议验证**

- `rg -n "非当前运行态真相源|当前完成度|Task List|附录|历史设计|目标模型对齐分析" docs/TINY_PLATFORM_AUTHORIZATION_PHASE1_TECHNICAL_DESIGN.md docs/tiny-platform-saas-overall-design.md docs/TINY_PLATFORM_MODULE_GAP_ANALYSIS.md`

**复制给 Cursor 的提示词**

```text
请为 tiny-platform 执行 CARD-14H：历史设计长文页眉 + 当前态指针。

必须先阅读：
- AGENTS.md
- docs/TINY_PLATFORM_AUTHORIZATION_DOC_MAP.md
- docs/TINY_PLATFORM_AUTHORIZATION_TASK_LIST.md
- docs/TINY_PLATFORM_TENANT_GOVERNANCE.md
- docs/TINY_PLATFORM_PLATFORM_SCOPE_CURSOR_TASK_CARDS.md
- docs/TINY_PLATFORM_AUTHORIZATION_PHASE1_TECHNICAL_DESIGN.md
- docs/tiny-platform-saas-overall-design.md
- docs/TINY_PLATFORM_MODULE_GAP_ANALYSIS.md

前置条件：
- 当前态真相源已由 TASK_LIST / TENANT_GOVERNANCE / DOC_MAP 固化

本卡只做：
1. 给三份历史设计长文补统一页眉
2. 补“当前态请看哪里”的指针
3. 保留历史设计与模块盘点正文，不做大篇幅刷新

本卡不要做：
- 不要把长文整篇重写成当前态
- 不要大规模改章节结构
- 不要改动不在范围内的其它文档

建议验证：
- rg -n "非当前运行态真相源|当前完成度|Task List|附录|历史设计|目标模型对齐分析" docs/TINY_PLATFORM_AUTHORIZATION_PHASE1_TECHNICAL_DESIGN.md docs/tiny-platform-saas-overall-design.md docs/TINY_PLATFORM_MODULE_GAP_ANALYSIS.md

交付要求：
- 输出每份文档新增的页眉/指针位置
- 输出修改文件
- 输出执行命令
- 输出验证结果
- 输出保留未改正文的原因
```

**Codex 审计点**

- 是否真的把“当前态看哪里”写清楚了
- 是否保住了历史设计正文，没有把它们刷成现状报告
- `MODULE_GAP_ANALYSIS` 是否仍然只是附录，不再被当完成度真相源

---

### CARD-14I 漂移守卫（启发式脚本 + checklist）

**目标**

新增一套轻量的文档漂移守卫，用启发式扫描高风险关键词，提醒“这句话可能把历史/桥接期写成当前态”，但不把它做成高噪音重 CI。

**前置条件**

- `CARD-14F ~ 14H` 已把高频文档口径先收紧
- 团队接受“grep 级启发式 + 人工复核”的 guard 方式

**范围**

- 新增脚本，建议放在：
  - `tiny-oauth-server/scripts/verify-authorization-doc-current-state-drift.sh`
- 如需最小配套：
  - `docs/TINY_PLATFORM_AUTHORIZATION_DOC_MAP.md`
  - `AGENTS.md`

**明确要求**

- 守卫必须是**启发式**，不是 AST / parser 工程
- 默认扫描高风险关键词，例如：
  - `resource.permission`
  - `PLATFORM > GLOBAL`
  - `新模型优先 + fallback`
  - `ROLE_ADMIN` / `ADMIN` 控制面兜底
  - `default` 平台语义
  - `roleCodes <- ROLE_*`
- 必须支持“人工确认后允许保留”的白名单方式，建议二选一：
  - 行内注释标记，例如 `CARD-14I: allow-historical`
  - 独立 ignore pattern 文件
- 不接重 CI，不要求零误报
- 脚本输出应区分：
  - 命中但可能合理的历史材料
  - 更值得人工复核的当前态文档
- 若同步 `DOC_MAP` 或 `AGENTS.md`，只补一行“有轻量漂移守卫”的入口说明，不做大改

**验收标准**

- 仓库内新增可执行脚本，能在本地直接运行
- 脚本能扫描至少一批固定关键词并输出文件/行号
- 脚本支持白名单或注释忽略
- 文档或入口说明能告诉后续维护者：这是启发式提醒，不是硬性失败门禁

**建议验证**

- `bash tiny-oauth-server/scripts/verify-authorization-doc-current-state-drift.sh`

**复制给 Cursor 的提示词**

```text
请为 tiny-platform 执行 CARD-14I：漂移守卫（启发式脚本 + checklist）。

必须先阅读：
- AGENTS.md
- docs/TINY_PLATFORM_AUTHORIZATION_DOC_MAP.md
- docs/TINY_PLATFORM_AUTHORIZATION_TASK_LIST.md
- docs/TINY_PLATFORM_PLATFORM_SCOPE_CURSOR_TASK_CARDS.md
- .agent/src/rules/50-testing.rules.md
- .agent/src/rules/90-tiny-platform.rules.md
- .agent/src/rules/93-tiny-platform-authorization-model.rules.md

前置条件：
- 14F ~ 14H 已先把高频真相源文档收紧

本卡只做：
1. 新增一个启发式文档漂移扫描脚本
2. 扫描高风险关键词并输出文件/行号
3. 支持人工白名单或注释忽略
4. 视情况在 DOC_MAP 或 AGENTS 里补一句入口说明

本卡不要做：
- 不要做复杂 parser / AST 工程
- 不要接重 CI
- 不要要求零误报
- 不要扩大成全仓 docs lint 框架

建议验证：
- bash tiny-oauth-server/scripts/verify-authorization-doc-current-state-drift.sh

交付要求：
- 输出脚本路径与使用方法
- 输出默认扫描关键词
- 输出白名单机制
- 输出执行命令
- 输出脚本运行结果
- 输出剩余误报风险
```

**Codex 审计点**

- 是否真的是轻量启发式，而不是过度工程化
- 是否提供了白名单/注释忽略机制
- 输出是否能帮助人工复核，而不是只有噪音

---

### CARD-15D 历史 schema / 示例归档减噪

**目标**

降低 `schema.sql`、参考 SQL、示例 seed 等历史材料被误读成“当前活动 schema / 当前运行态 seed 真相源”的风险；优先通过归档标识、前置说明、最小迁移来减噪，而不是大规模搬迁仓库。

**前置条件**

- `CARD-14G / 14H` 已把历史文档的入口信号收紧
- 团队接受“先减噪、再视情况归档迁移”的分步策略

**范围**

- `tiny-oauth-server/src/main/resources/schema.sql`
- `tiny-oauth-server/src/main/resources/menu_resource_data.sql`
- 如有必要，允许最小同步：
  - `docs/TINY_PLATFORM_AUTHORIZATION_DOC_MAP.md`
  - `docs/TINY_PLATFORM_LEGACY_COMPATIBILITY_INVENTORY.md`

**明确要求**

- 至少要让这两类文件一开头就能看出：
  - 是历史 schema / 参考样例 / 非当前活动真相源
  - 当前真相源应看哪里
- 允许的“第一步”包括：
  - 文件头注释硬化
  - 文档入口说明
  - 移到更不易误读的位置并保留链接
- 但**不要**在一张卡里做以下事情：
  - 重写大段 SQL 内容
  - 删除仓库仍被工具或人工引用的历史文件
  - 重构整个资源初始化体系
- 若决定移动文件位置，必须同步所有仓库内引用；若风险偏高，优先只加头注释，不强行迁文件

**验收标准**

- `schema.sql`、`menu_resource_data.sql` 首页都有明确的“历史/参考/非当前真相源”提示
- 至少一处能明确指向当前真相源文档或当前 seed / migration 入口
- 不破坏现有仓库内引用或脚本使用
- 若选择迁移文件位置，引用已同步更新

**建议验证**

- `rg -n "schema.sql|menu_resource_data.sql" -g '!node_modules' .`
- `rg -n "历史|参考|非当前|真相源" tiny-oauth-server/src/main/resources/schema.sql tiny-oauth-server/src/main/resources/menu_resource_data.sql`

**复制给 Cursor 的提示词**

```text
请为 tiny-platform 执行 CARD-15D：历史 schema / 示例归档减噪。

必须先阅读：
- AGENTS.md
- docs/TINY_PLATFORM_AUTHORIZATION_DOC_MAP.md
- docs/TINY_PLATFORM_AUTHORIZATION_TASK_LIST.md
- docs/TINY_PLATFORM_LEGACY_COMPATIBILITY_INVENTORY.md
- docs/TINY_PLATFORM_PLATFORM_SCOPE_CURSOR_TASK_CARDS.md

前置条件：
- 历史文档页眉与当前态入口已先收紧

本卡只做：
1. 给 schema.sql / menu_resource_data.sql 补明显的历史/参考标识
2. 视情况做最小归档或入口说明
3. 保证仓库内引用不被破坏

本卡不要做：
- 不要重写大量 SQL 内容
- 不要删除仍可能被人工或脚本引用的历史文件
- 不要扩大成初始化体系重构

建议验证：
- rg -n "schema.sql|menu_resource_data.sql" -g '!node_modules' .
- rg -n "历史|参考|非当前|真相源" tiny-oauth-server/src/main/resources/schema.sql tiny-oauth-server/src/main/resources/menu_resource_data.sql

交付要求：
- 输出采取的是“加头注释”还是“最小迁移”策略
- 输出修改文件
- 输出执行命令
- 输出验证结果
- 输出仍保留这些历史文件的原因
```

**Codex 审计点**

- 是否优先做了低风险减噪，而不是直接大迁移
- 历史 schema / 示例是否一眼可见“非当前真相源”
- 是否保护了仓库内引用和人工排障入口
