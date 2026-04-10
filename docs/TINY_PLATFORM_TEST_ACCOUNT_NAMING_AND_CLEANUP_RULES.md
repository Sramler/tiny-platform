# Tiny Platform 测试数据命名与清理规则（E2E / Integration / Seed）

## 1. 文档目的与适用范围

- 该规则适用于 `tiny-oauth-server` 在真实环境（real-link / 真实 MySQL）中使用的共享测试库清理。
- 主要解决两个问题：测试副产物残留导致共享库膨胀，以及清理脚本误删长期 seed/E2E 资产。
- 本文聚焦“共享真库测试资产治理”，不讨论普通开发环境中的一次性本地测试数据。
- 本文中的“测试数据”不是只指账号或角色，而是指**整个数据库模型中由测试写入的所有数据**：包括主体表、关联表、中间表、约束表、membership、权限绑定、运行态实例、历史表、审计日志、派生配置与其它模块实例数据。

统一治理入口：
- 基线脚本：`tiny-oauth-server/scripts/verify-test-db-residuals.sh`
- 作用：编排模块级 cleanup / verify 脚本，统一执行 dry-run、`--apply`、`--fail-on-stale`
- 要求：共享真库/real-link 的测试残留治理应优先从该基线入口触发，而不是要求使用者手工逐个执行专名脚本

## 2. 术语约定（约束面）

- 受控前缀白名单：`cleanup` / `verify` 脚本允许识别并进入候选集的“测试账号用户名受控前缀集合”；删除/统计以它为准。
- 结构锚点：`_user_`（用户名结构中必须出现的分类锚点，用于结构约束校验，不作为单独的删除依据）。
- 长期资产：Seed / Bootstrap 账号与 E2E 固定账号（通常长期存在，不走可回收残留清理逻辑）。
- 可回收资产：Integration 临时账号（测试期间创建，测试结束后应回收，清理脚本仅兜底）。

## 3. 测试数据资产分层

### 3.1 Seed / Bootstrap 账号（长期稳定资产）

特征：
- 通常在租户 bootstrap 或初始化数据中创建。
- `tenant_user.status='ACTIVE'` 可能长期存在。

示例：
`admin`、`user`、`k6bench`（以及可能的同类基础账号）。

规则：
- 默认不参与任何“残留清理”。
- 清理脚本仅允许按强约束的测试前缀匹配集成测试账号，不匹配 seed 账号命名。

### 3.2 E2E 账号（真实链路登录凭证）

定义来源：
- 环境变量注入（请参照 `tiny-oauth-server/docs/E2E_AUTOMATION_IDENTITIES.md`）。

示例：
`E2E_USERNAME` / `E2E_PASSWORD`，`E2E_USERNAME_B` / `E2E_PASSWORD_B`，`E2E_USERNAME_READONLY` / `E2E_PASSWORD_READONLY`，`E2E_USERNAME_BIND` / `E2E_PASSWORD_BIND`

规则：
- E2E 账号为固定登录凭证，通常不做“每次结束就删库”。
- `E2E_*` 是环境变量名（例如 `E2E_USERNAME`），并不要求数据库里的 `user.username` 一定以 `E2E_` 开头；真实账号名可能是 `e2e_admin`、`tenant_a_admin` 等。
- 清理/排除的依据仍应以“Integration 临时账号受控前缀白名单 + `_user_` 结构约束”为准，不应以环境变量名去推断数据库用户名。
- E2E 账号属于长期资产：统一通过 `.env.e2e.local`、CI secrets 或受控测试配置注入，不应在测试代码里硬编码用户名/密码/TOTP。
- 清理脚本的匹配范围必须永远收敛在“受控前缀白名单”的 Integration 临时账号命名空间，严禁把 E2E（`E2E_`）当作可回收资产处理。

### 3.3 Integration 测试资产（可回收临时资产）

- 用于 `@SpringBootTest` + 真实 MySQL 的集成测试（在测试中可能写入 `role_assignment`、`tenant_user`、`role_constraint_violation_log`、运行态实例、历史表、审计日志或其它模块子表）。
- 测试用例应在 `finally` 中清理，防止断言失败导致残留。
- 清理对象必须覆盖本次用例写入的**整条数据链**，不能只删账号/角色主体，留下子表或投影残留。

RBAC3 相关集成测试在 `User.username` 上会写入“用于清理/统计的标识”（时间戳后缀由测试代码追加）。

## 4. Integration 临时账号命名规则

### 4.1 RBAC3 当前兼容口径（现状）

当前 `cleanup-rbac3-test-residual-users.sh` 与 `verify-authorization-model-rollout.sh` 的识别逻辑是：以“受控前缀白名单”做匹配，不依赖对 `<module>_<scenario>_user_<uniqueSuffix>` 的解析。

因此，历史上出现过一些“未严格满足统一模板解析”的命名形式；这不会影响清理脚本的正确性。脚本是否能够识别并清理，仍只取决于是否命中受控前缀白名单。

本阶段仍然被接受的受控前缀白名单（只要命中其中任一项就会进入候选集；是否最终删除还取决于是否处于 ACTIVE membership）：

1. `rbac3_dryrun_user_`
2. `rbac3_enforce_user_`
3. `rbac3_violation_obs_user_`
4. `rbac3_enforce_obs_user_`
5. `rbac3_enforce_allowlist_user_`

历史示例（说明为什么“不能用模板解析”来推断清理范围）：

1. `rbac3_enforce_user_pre_<ts>`
2. `rbac3_dryrun_user_prereq_<ts>`

### 4.2 历史前缀兼容列表

以下前缀出现在现有 RBAC3 真库集成测试中，用于构造不同场景的 `User.username`（时间戳后缀由测试代码追加）：

1. `rbac3_dryrun_user_<ts>`
2. `rbac3_dryrun_user_prereq_<ts>`
3. `rbac3_dryrun_user_card_a_<ts>`
4. `rbac3_dryrun_user_card_b_<ts>`
5. `rbac3_enforce_user_<ts>`
6. `rbac3_enforce_user_pre_<ts>`
7. `rbac3_enforce_user_card_a_<ts>`
8. `rbac3_enforce_user_card_b_<ts>`
9. `rbac3_enforce_allowlist_user_<ts>`
10. `rbac3_violation_obs_user_<ts>`
11. `rbac3_enforce_obs_user_<ts>`

### 4.3 未来推荐模板（新增命名收敛）

从新增集成测试开始，推荐统一收敛到以下命名模板：

`<module>_<scenario>_user_<uniqueSuffix>`

字段含义与可推广性（未来可覆盖其它模块）：

1. `<module>`：表示模块域，可从 `rbac3` 推广到 `scheduling`、`tenant`、`dict` 等（与清理脚本的“受控前缀白名单”同步扩展）
2. `<scenario>`：表示测试场景/模式，如 `dryrun` / `enforce` / `obs` / `allowlist`，也可以细化到约束类别
3. `_user_`：分类锚点必须保留，用于结构约束（必须出现在模板结构中）
4. `<uniqueSuffix>`：必须以“可唯一且可检索”的后缀结尾，建议由时间戳 + 随机串或 commit hash 组成，避免并发运行导致重名

命名示例（未来统一收敛的推荐写法）：

1. `rbac3_enforce_user_20240320123000_abcdef12`
2. `rbac3_dryrun_user_prereq_20240320123000_abcdef12`

以下仍以 RBAC3 作为当前已落地示例：受控前缀白名单继续以兼容期逻辑为准；脚本不会依赖对 `<module>_<scenario>_user_<uniqueSuffix>` 的“模板解析”，而只依赖“受控前缀白名单”是否覆盖到对应前缀。

#### 4.3.1 uniqueSuffix 生成原则（建议）

1. 固定前缀：`<module>_<scenario>_user_` 固定不变（便于按前缀快速检索）
2. 随机/Commit 后缀：`<uniqueSuffix>` 建议用 `timestamp + random`（或 `timestamp + commitHash`）组合，避免并发写入重名；不建议仅使用 commit hash 作为唯一后缀，推荐始终包含 timestamp。随机串建议不少于 6 位字符（例如 8 位 hex）
3. 可回收：uniqueSuffix 必须足够短且唯一，便于删除时快速定位到本次 CI/本次用例集的记录

#### 4.3.2 环境变量前缀支持（建议）

为避免不同分支/不同 CI 并发写入共享库互相干扰，推荐在 `<uniqueSuffix>` 末尾加入二级标识（例如 `TEST_PREFIX`）：

建议口径（默认模式 / CI 推荐模式）：
1. 默认（未设置 `TEST_PREFIX`）：`<uniqueSuffix> := <timestamp>_<randomOrCommit>`
2. CI 推荐（已设置 `TEST_PREFIX`）：`<uniqueSuffix> := ${TEST_PREFIX}_${timestamp}_${randomOrCommit}`

清理脚本的安全匹配仍应优先依赖“受控前缀白名单”，`TEST_PREFIX` 只用于进一步降低误删风险与便于人工排查

本地与 CI 注入示例：
1. 本地：`export TEST_PREFIX='mypr-123'`
2. GitHub Actions：在 job 的 `env:` 中增加 `TEST_PREFIX: ${{ github.ref_name }}`

uniqueSuffix 示例（仅示意）：
1. 默认：`20240320123000Z_a1b2c3d4`
2. 带前缀：`mypr123_20240320123000Z_a1b2c3d4`

### 4.4 从历史格式向新格式的收敛策略

收敛分三步进行，期间必须保证清理脚本不会因为命名变化而丢失覆盖：

1. 兼容期：受控前缀白名单保持对历史前缀的覆盖不变；新增用例逐步改为推荐模板，但脚本不做“反向解析”。
2. 扩展期：当新增用例开始覆盖新模板命名后，才将新模板对应的前缀加入“受控前缀白名单”，保证统计与清理仍完整。
3. 完成期：等历史前缀在运行态不再产生时，再将白名单逐步收敛为只覆盖推荐模板的前缀集合。

## 5. 清理与回收规则

### 5.1 用例内清理（finally 必须执行）

每个 Integration 真实库集成测试都必须在 `finally` 中清理“本次用例写入的整条数据链”，并按外键依赖顺序执行。当前以 RBAC3 为例，但约束适用于整个数据库模型：

1. `role_assignment`（USER 主体在 tenant + scope 下的赋权）
2. `role_constraint_violation_log`（若 dry-run 产生违例日志，按 principalType/principalId/tenantId 删除）
3. `tenant_user`（测试账号在 tenant 下的 membership）
4. `user`（最后删除用户行）
5. 当前模块的规则/关联表数据（如果测试创建了约束/规则/版本/中间表，必须补删），包括：
   - 示例模块（当前以 RBAC3 为例）规则表：`role_hierarchy`、`role_mutex`、`role_prerequisite`、`role_cardinality`，以及测试创建的 `role` 行
   - 如果未来测试扩展到其它模块（如 scheduling 的 task_instance / dag_run / task_history，或 tenant 相关审计/日志表），同样要求在 `finally` 中按外键依赖顺序清理本次用例写入的子表和主表

清理顺序约束（避免外键错误）：
1. 必须遵循“子表先删、父表后删”的外键依赖顺序；
2. 优先复用该模块已有的清理 helper / SQL 的 delete 顺序；没有 helper 的新模块，先补清理 helper，再把用例纳入可回收资产治理；
3. 示例思路（仅作为方向提示）：先删通用关联（如 `role_assignment`、`tenant_user`、`user` 等），再删模块运行态/实例/历史（如 scheduling 的 run / task_instance / history），最后删 DAG 本体与模块配置。

建议：
复用测试包内已有的清理 helper，例如 `com.tiny.platform.infrastructure.auth.role.integration.Rbac3RoleConstraintIntegrationTestCleanup`，避免不同测试用例清理口径漂移。

说明：
- 删除测试账号不等于完成清理；凡测试写入过的模块运行态/实例/历史/日志/约束/中间表/派生配置，都必须在 `finally` 中同步清理，不能把“删 user/role”当作唯一回收动作。

### 5.2 共享库兜底清理（cleanup 脚本）

脚本：
- `tiny-oauth-server/scripts/cleanup-rbac3-test-residual-users.sh`
- `tiny-oauth-server/scripts/cleanup-rbac3-test-residual-roles.sh`

默认行为（安全模式，不含 ACTIVE membership）：
- 仅匹配用户名以“受控前缀白名单”之一开头的临时测试账号（脚本侧等价于 `username LIKE 'rbac3_*_user_%'` 强前缀匹配；前缀本身已包含 `_user_` 结构）。
- 默认只清理“没有 `tenant_user.status='ACTIVE'` 的账号”，以避免误删真正处于活跃 membership 的业务 / seed / E2E 资产。
- `_user_` 结构锚点的“显式校验”以 `verify-rbac3-test-user-prefix-whitelist.sh` 为准；cleanup 脚本侧通过强前缀白名单收敛候选集。
- 角色清理脚本只处理“孤儿 RBAC3 测试角色”：`ROLE_RBAC3_% / RBAC3 %` 且无 `role_assignment`、`role_permission`、`role_data_scope` 绑定；同时一并删除它们挂接的 `role_hierarchy` / `role_mutex` / `role_cardinality` / `role_prerequisite`。
- 兜底脚本的设计目标不是“只删 user/role 两张表”，而是覆盖脚本白名单范围内由测试写入的整条数据链；若某个模块的运行态/历史/审计/中间表尚未接入脚本，视为治理未完成，而不是“主体删掉就算清理完成”。

### 5.3 real-link 派生资产审计（非 RBAC3 临时账号）

以下资产不属于 RBAC3 Integration 临时账号清理范围：

- 通过 real-link `/sys/tenants` 动态创建的派生租户：`tenant.name = E2E租户(<tenantCode>)`
- 这些派生租户自动生成的初始管理员：`username = e2e_init_<tenantCode>`
- 当前 keep-set 之外的 `e2e_*` 长期账号与其 stale membership

它们的治理入口应与 RBAC3 cleanup 分开，避免误把固定 E2E 登录身份当作可回收资产：

- 审计脚本：`tiny-oauth-server/scripts/verify-real-e2e-derived-assets.sh`
- 自动清理：`bash tiny-oauth-server/scripts/verify-real-e2e-derived-assets.sh --apply`
- 当前 run 派生租户定向清理：`bash tiny-oauth-server/scripts/verify-real-e2e-derived-assets.sh --apply --target-generated-tenant-codes <csv>`

脚本口径：

- 优先读取 shell 中的 `E2E_*` keep-set；缺失时回退到 `src/main/webapp/.env.e2e.local`
- 当前仓库额外维护一组“长期自动化身份兼容 allowlist”，默认包括：`e2e_admin_b`、`e2e_platform_admin`、`e2e_scheduling_readonly`
- 如需继续扩展长期身份白名单，可通过 `E2E_KEEP_USERS_EXTRA=user1,user2` 追加，而不必把它们误报为 stale `e2e_*`
- 固定长期身份只做“是否超出 keep-set”的提示，不直接纳入自动删除
- `E2E租户(...)`、`e2e_init_*`、以及 keep 用户挂在生成型租户上的 stale membership，属于自动治理范围
- real-link `globalSetup` / `globalTeardown` 应在跑前执行 `--apply --fail-on-stale` 幂等清理，并在跑后对本轮派生租户再次执行定向清理

## 6. 风险控制与操作约束

### 6.1 危险但必要的操作选项
- `--include-active`
- 含义：即使测试账号在 `tenant_user.status='ACTIVE'`，也仍仅按“受控前缀白名单”强前缀匹配（前缀本身已包含 `_user_` 结构）来筛选并清理。
- 使用前必须确认：这些账号前缀来自“受控前缀白名单”且确为临时 Integration 测试产物；否则禁止使用该选项。
- 执行责任：`--include-active` 不应进入默认 CI；仅允许人工执行。执行前必须完成 dry-run 确认候选数，并保留 dry-run 输出作为操作证据，同时在工单/PR/值班记录中注明执行原因、目标环境与预计影响范围。

### 6.2 脚本命名迁移说明
迁移说明（脚本名仍是 RBAC3 专名，但规则可推广到整个数据库模型）：
- 当前仓库脚本以 RBAC3 为起点命名（`cleanup-rbac3...`、`verify-rbac3...`），是为了快速落地共享库残留治理。
- 当规则推广到其它模块时，现阶段可以沿用脚本命名策略：为新模块先复制脚本模板并替换受控前缀白名单/表范围，然后再评估是否需要统一重命名为更通用的治理脚本（例如 `cleanup-integration-test-residual-data.sh`）。

### 6.3 常用执行方式
常用执行方式：

1. 先跑统一基线 dry-run：
   `bash tiny-oauth-server/scripts/verify-test-db-residuals.sh`
2. 统一基线执行治理并在仍有残留时报错：
   `bash tiny-oauth-server/scripts/verify-test-db-residuals.sh --apply --fail-on-stale`
3. 审计 RBAC3 临时测试用户：
   `bash tiny-oauth-server/scripts/cleanup-rbac3-test-residual-users.sh`
4. 执行 RBAC3 临时测试用户清理（安全模式）：
   `bash tiny-oauth-server/scripts/cleanup-rbac3-test-residual-users.sh --apply`
5. 执行 RBAC3 临时测试用户清理（包含 ACTIVE）：
   `bash tiny-oauth-server/scripts/cleanup-rbac3-test-residual-users.sh --apply --include-active`
6. 审计 RBAC3 孤儿测试角色：
   `bash tiny-oauth-server/scripts/cleanup-rbac3-test-residual-roles.sh`
7. 执行 RBAC3 孤儿测试角色清理：
   `bash tiny-oauth-server/scripts/cleanup-rbac3-test-residual-roles.sh --apply`

### 6.4 日志与失败处理建议
日志与失败处理建议：
1. 建议把输出落盘便于定位失败原因：`bash tiny-oauth-server/scripts/cleanup-rbac3-test-residual-users.sh --apply > /tmp/rbac3-cleanup.log 2>&1`
2. 若删除失败或仍有残留，优先检查：是否命中“受控前缀白名单”（dry-run 候选数是否>0）、账号是否仍为 ACTIVE membership（是否需要谨慎使用 `--include-active`）、以及是否遗漏同步扩展白名单/清理口径。

### 6.5 数据库选择
数据库选择：
- 默认 `VERIFY_DB_NAME=tiny_web`
- 可通过环境变量覆盖：`VERIFY_DB_NAME=your_db bash ...`
- 连接参数可通过环境变量覆盖：`VERIFY_DB_HOST` / `VERIFY_DB_PORT` / `VERIFY_DB_USER`。
- 密码读取顺序（脚本侧）：`VERIFY_DB_PASSWORD`，否则回退到 `E2E_DB_PASSWORD` / `E2E_MYSQL_PASSWORD` / `MYSQL_ROOT_PASSWORD`。

## 7. 团队接入与变更流程

### 7.1 日常变更要求

- 只要集成测试新增了新的“可回收 Integration 临时账号”前缀，就必须同时更新以下三处：
  1. 测试用例生成前缀（`setUsername(...)` 中的硬编码前缀或模板前缀）
  2. 对应模块的残留清理脚本白名单（当前仓库中：RBAC3 使用 `cleanup-rbac3-test-residual-users.sh`；新增模块应新增或扩展对应的 cleanup 脚本）
  3. 对应模块的残留统计 / 一致性校验脚本（RBAC3 使用 `verify-authorization-model-rollout.sh`；其它模块应提供各自的 verify）
- 任何清理脚本都必须以“强前缀匹配”作为第一道保险，严禁用“用户名部分匹配”泛化到 seed/E2E 资产。
- 文档中的“用户名模板”和“受控前缀白名单”是两个层次的概念：模板用于新增命名收敛，白名单用于当前 cleanup/verify 的实际匹配口径；未进入白名单的模板前缀，不得假定脚本已经覆盖。
- 建议在 CI 中运行静态校验（例如 `verify-rbac3-test-user-prefix-whitelist.sh`），把“新增前缀未同步进白名单”尽可能前置到 PR 阶段发现。

### 7.2 新增模块接入清单（Checklist）

当引入新的 Integration 临时账号前缀（跨模块推广 `<module>_<scenario>_user_<uniqueSuffix>`）时，必须完成以下事项：

1. 定义该模块的 Integration 临时账号受控前缀（模块维度 `<module>`、场景 `<scenario>`）
2. 将前缀加入对应 cleanup 脚本的白名单（当前仓库脚本以 RBAC3 为例）
3. 将前缀纳入对应 verify/rollout 统计与残留一致性校验口径
4. 为该模块补清理 helper / SQL delete 顺序，并在 `finally` 中覆盖回收逻辑
5. 在 CI 中补静态校验或前缀白名单校验（例如新增/扩展 `verify-<module>-test-user-prefix-whitelist.sh`，或在现有校验脚本中纳入该模块前缀），避免新增前缀未同步
6. 补至少一个“残留可被统计/清理”的回归测试（确保 finally 回收在断言失败场景仍生效）
7. 同步更新本文档中的受控前缀示例、常见错误和执行说明，保证与脚本/用例口径保持一致

## 8. 正确示例与常见错误

正确示例：
1. `rbac3_enforce_user_20240320123000_abcdef12`（受控前缀 + `_user_` + 唯一后缀）
2. `rbac3_dryrun_user_prereq_20240320123000_abcdef12`（历史兼容前缀 + `_user_` 结构）

常见错误（禁止出现）：
1. 忘记 `_user_` 锚点（示例均缺失 `_user_` 结构）：  
   - `rbac3_enforce_20240320123000_abcdef12`  
   - `rbac3_enforceuser_20240320123000_abcdef12`  
   - `rbac3_enforce_tmp_20240320123000_abcdef12`
2. 不生成唯一后缀：如 `rbac3_enforce_user_` 或仅用短随机导致并发重名
3. 在 seed/E2E 固定账号上追加临时前缀：如直接把 seed 的 `admin` 改名为 `rbac3_enforce_user_admin`
4. 仅依赖清理脚本兜底而不在 `finally` 清理：导致共享库残留累积并增加排障成本
