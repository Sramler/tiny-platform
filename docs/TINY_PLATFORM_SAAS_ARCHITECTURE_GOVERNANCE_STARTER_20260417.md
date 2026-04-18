# Tiny Platform SaaS 软件架构治理收口启动清单

最后更新：2026-04-17

状态：

- `已立项`
- 当前仅做治理收口与后续任务卡准备
- 当前不直接改业务代码
- 当前不直接 cherry-pick `fae4cb7`

适用仓库：

- `/Users/bliu/code/tiny-platform`

适用线程：

- `saas 软件架构推进`

## 1. 目标

围绕两个非阻断议题，先把“是否需要继续做”与“下一步应该怎么做”收口成明确决议：

1. `fae4cb7` 是否需要回补 `sb3`
2. `sb4` 上 snapshot 仓库 / metadata `401` 是否只是可接受噪音，还是已经构成真实依赖风险

## 2. 非目标

- 本清单不直接改动 `tiny-oauth-server` 业务实现
- 不在本轮直接做 `fae4cb7 -> sb3` cherry-pick
- 不在本轮直接修改 Maven mirror / credentials / CI workflow
- 不把 `401` 日志通过静默 suppress 直接“消音”

## 3. 当前基线

- `eed3b04`：根 POM 已推进到 `Spring Boot 4.1.0-SNAPSHOT`
- `fae4cb7`：`sb4` MFA 重构下已保住 `prompt=none` 语义
- 当前 `sb4` 上 `tiny-oauth-server` 编译通过
- 当前 `sb4` 上 MFA / `prompt=none` 定向测试已通过
- 当前构建中的 `401 metadata` 主要来自 `camunda-nexus` 对 Spring snapshot metadata 的请求

补充基线证据：

- 根 POM 当前声明的主仓库链只包含：
  - `apache-releases`
  - `spring-snapshots`
  - `aliyun`
  - `central`
  - 受控 profile `camunda-github-packages` 下的 `github-camunda-fork`
- 仓库内未声明 `camunda-nexus`
- Camunda SB4 fork 当前推荐消费路径已收口到 GitHub Packages runbook，而不是开发机私有本地仓库

参考：

- [pom.xml](/Users/bliu/code/tiny-platform/pom.xml:24)
- [pom.xml](/Users/bliu/code/tiny-platform/pom.xml:257)
- [pom.xml](/Users/bliu/code/tiny-platform/pom.xml:309)
- [pom.xml](/Users/bliu/code/tiny-platform/pom.xml:348)
- [TINY_PLATFORM_CAMUNDA7_SB4_GITHUB_PACKAGES_RUNBOOK.md](/Users/bliu/code/tiny-platform/docs/TINY_PLATFORM_CAMUNDA7_SB4_GITHUB_PACKAGES_RUNBOOK.md:1)
- [TINY_PLATFORM_SB4_MAVEN_REPOSITORY_CHAIN_AUDIT_20260417.md](/Users/bliu/code/tiny-platform/docs/TINY_PLATFORM_SB4_MAVEN_REPOSITORY_CHAIN_AUDIT_20260417.md:1)

## 4. 决议总表

| 卡号 | 议题 | 收口结论 | 当前动作 |
| --- | --- | --- | --- |
| `CARD-SAAS-01` | `fae4cb7` 的 `sb3` 回补评估 | `明确不回补 sb3` | 记录决议；若未来 `sb3` 再触碰同一认证边界，另建新卡 |
| `CARD-SAAS-02` | `sb4` snapshot / metadata `401` 噪音治理 | `可接受噪音，但属于应治理的仓库链路风险` | 不先改代码；后续做仓库链路盘点与文档/脚本约束 |

## 5. CARD-SAAS-01 `fae4cb7` 的 `sb3` 回补评估

### 5.1 问题定义

需要判断 `fae4cb7` 修复的是：

- `sb4` 专属的 MFA 编排回归

还是：

- `sb3` / `sb4` 共享的认证语义约束

本卡要求的输出只能是二选一：

- `建卡回补 sb3`
- `明确不回补 sb3`

### 5.2 已核对证据

1. `main` 当前仍处于 Boot `3.5.10` 基线，而不是 Boot 4 兼容链。
2. `main` 的授权端点 MFA 主链仍是旧的 `MfaAuthorizationEndpointFilter` 挂载方式。
3. `sb4` 当前已经切到授权端点 `AuthorizationManager` 编排。
4. `fae4cb7` 的关键修复点，是在新的 `AuthorizationEndpointMfaAuthorizationManager` 中对未登录/匿名请求返回 `abstain`，让 Spring Authorization Server 自己处理 `prompt=none`、`login_required` 与常规登录重定向语义。
5. 已做过 clean worktree 语义核对：
   - `main/sb3` 对应 `prompt=none` 语义测试通过
   - clean `sb4@eed3b04` 同类语义测试也通过
   - 回归出现在后续 `sb4` MFA 新编排落地过程中，而不是 `sb3` 原有稳定行为中

参考：

- [AuthorizationServerConfig.java](/Users/bliu/code/tiny-platform/tiny-oauth-server/src/main/java/com/tiny/platform/core/oauth/config/AuthorizationServerConfig.java:92)
- [AuthorizationEndpointMfaAuthorizationManager.java](/Users/bliu/code/tiny-platform/tiny-oauth-server/src/main/java/com/tiny/platform/core/oauth/security/AuthorizationEndpointMfaAuthorizationManager.java:17)

### 5.3 决议

`明确不回补 sb3`

### 5.4 决议理由

1. `fae4cb7` 修复点绑定在 `sb4` 新的 MFA 编排边界上，不是 `sb3` 现有链路中的共享缺陷。
2. 现有证据不能支持“`sb3` 已存在同一问题”的判断。
3. 如果直接把 `fae4cb7` 视为通用 backport 卡，会把“共享认证语义约束”和“`sb4` 新架构回归修正”混成一件事，后续容易误导分支治理。

### 5.5 后续动作约束

- 当前不直接 cherry-pick `fae4cb7` 到 `sb3`
- 当前不为 `sb3` 建“代码回补卡”
- 若未来 `sb3` 也改到授权端点 MFA 边界，或再次出现 `prompt=none` 语义漂移：
  - 必须基于当时的 `sb3` 代码面新建一张独立卡
  - 不复用本卡作为“直接回补批准”

### 5.6 完成定义

满足以下条件即可视为本卡本轮完成：

- 分支治理层面已明确记录：
  - `fae4cb7` 不作为 `sb3` 直接回补候选
- 后续团队在讨论 `main/sb3` 回补时，不再把该提交默认归为“共享修复”

## 6. CARD-SAAS-02 `sb4` snapshot / metadata `401` 噪音治理

### 6.1 问题定义

需要判断当前 `sb4` 构建中出现的 snapshot metadata `401`：

- 只是可接受噪音

还是：

- 已经构成实际依赖解析风险

并对以下四件事给出收口口径：

1. 仓库顺序
2. mirror / repository 配置
3. 是否需要凭证
4. 是否需要降噪脚本或文档约束

### 6.2 已核对证据

1. 根 POM 已显式声明 `spring-snapshots`，用于 Boot `4.1.0-SNAPSHOT`。
2. Camunda fork 的受控消费路径是：
   - profile `camunda-github-packages`
   - 仓库 `github-camunda-fork`
3. 仓库内未声明 `camunda-nexus`，说明这条来源不是项目主 POM 的基线依赖源。
4. 当前 `sb4` 已能完成编译，且 MFA / `prompt=none` 定向测试通过，说明这批 `401` 目前没有演化成“主链依赖无法解析”。
5. runbook 已明确 GitHub Packages 是当前 Camunda SB4 fork 的目标消费路径，并说明了认证与 CI 使用方式。

参考：

- [pom.xml](/Users/bliu/code/tiny-platform/pom.xml:257)
- [pom.xml](/Users/bliu/code/tiny-platform/pom.xml:309)
- [pom.xml](/Users/bliu/code/tiny-platform/pom.xml:348)
- [TINY_PLATFORM_CAMUNDA7_SB4_GITHUB_PACKAGES_RUNBOOK.md](/Users/bliu/code/tiny-platform/docs/TINY_PLATFORM_CAMUNDA7_SB4_GITHUB_PACKAGES_RUNBOOK.md:40)
- [TINY_PLATFORM_CAMUNDA7_SB4_GITHUB_PACKAGES_RUNBOOK.md](/Users/bliu/code/tiny-platform/docs/TINY_PLATFORM_CAMUNDA7_SB4_GITHUB_PACKAGES_RUNBOOK.md:65)

### 6.3 决议

`可接受噪音，但属于应治理的仓库链路风险`

这里的“可接受”仅表示：

- 当前它还不是实际构建阻断

不表示：

- 可以长期忽略不记

### 6.4 决议理由

1. 现状上，项目已经能编译并完成关键定向测试，因此它不是“当前依赖缺失”的直接证据。
2. 但 `camunda-nexus` 并不在仓库声明中，却能出现在解析日志里，说明本机或 CI 的 Maven `settings.xml` / mirror / profile 仍可能注入额外解析路径。
3. 如果不治理这类“仓库外注入”，后续真实依赖故障会被噪音淹没，团队也难以判断“到底是项目 POM 问题，还是环境链路问题”。

### 6.5 收口口径

#### 6.5.1 仓库顺序

- 维持当前 POM 顺序，不新增 `camunda-nexus` 到项目仓库声明
- 当前主顺序保持：
  1. `apache-releases`
  2. `spring-snapshots`
  3. `aliyun`
  4. `central`
- Camunda fork 继续只通过受控 profile：
  - `camunda-github-packages`

#### 6.5.2 mirror / repository 配置

- 任何本机或 CI 的额外 mirror，如果会拦截或覆盖：
  - `spring-snapshots`
  - `github-camunda-fork`
  都必须视为“仓库外治理配置”，不能当成项目默认基线
- `camunda-nexus` 若继续出现，只能被视为环境注入来源，不能反向要求项目 POM 适配它

#### 6.5.3 凭证

- `spring-snapshots`：
  - 不需要额外凭证
- `github-camunda-fork`：
  - 在启用 `camunda-github-packages` profile 时需要可读取 GitHub Packages 的认证
  - 当前 runbook 口径是 `read:packages` 路径
- `camunda-nexus`：
  - 当前不追加凭证治理
  - 它不是主线路径，不应倒逼项目为噪音源补凭证

#### 6.5.4 降噪脚本或文档约束

- 需要
- 但优先级是：
  - `先显式盘点 effective repositories / mirrors`
  - `再做定向降噪`
- 不建议先做“静默 suppress 401 文本”这类处理，因为会掩盖真实来源

### 6.6 推荐后续动作

本卡后续应拆成一张独立治理任务，最小动作建议如下：

1. 盘点一份脱敏后的 effective Maven 仓库链路
   - 明确哪些仓库来自项目 POM
   - 哪些来自 `settings.xml` / mirror / CI action 注入
   - 本轮审计记录见：
     [TINY_PLATFORM_SB4_MAVEN_REPOSITORY_CHAIN_AUDIT_20260417.md](/Users/bliu/code/tiny-platform/docs/TINY_PLATFORM_SB4_MAVEN_REPOSITORY_CHAIN_AUDIT_20260417.md:1)
2. 在 runbook 或技术债台账中追加“`camunda-nexus` 非项目基线仓库”说明
3. 如仍有必要，再补一个只做诊断、不改业务代码的仓库链路脚本
   - 输出 effective repositories / mirrors
   - 对凭证字段做脱敏
   - 当前已落地首版：
     `bash tiny-oauth-server/scripts/diagnose-sb4-maven-repository-chain.sh`

### 6.7 完成定义

满足以下条件即可视为本卡完成：

- 团队已统一认知：
  - 当前 `401 metadata` 不是实际依赖阻断
  - 但也不是可以长期无视的“正常现象”
- 项目主线路径与环境注入路径被明确区分
- 后续若继续出现 `camunda-nexus` 相关日志，团队可先按“环境链路排查”而不是“改项目 POM”处理

## 7. 下一步建议

如果继续沿 `saas 软件架构推进` 线程推进，建议按以下顺序执行：

1. 把本清单作为该线程的启动基线
2. 先关闭 `CARD-SAAS-01` 的“是否回补 `sb3`”讨论，避免重复消耗
3. 再为 `CARD-SAAS-02` 单独开一个 Maven 仓库链路治理小任务
   - 目标是“看清楚 effective repo chain”
   - 不是先去改业务代码或盲目清日志
