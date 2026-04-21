# AGENTS.md（项目 AI 协作说明）

> 目标：让“人 + AI”在同一套规则体系下协作；并且跨工具（Cursor/Copilot/Continue/Windsurf）通用。

---

## 0. 快速入口（给人）

- **规则唯一真相**：`.agent/src/**`
- **Cursor 生效入口（生成物）**：`.cursor/rules/**`
- **Codex 通用入口**：`AGENTS.md` + `docs/**` + `.agent/src/**`
- **测试实施手册**：`docs/TINY_PLATFORM_TESTING_PLAYBOOK.md`
- **AI 测试任务模板**：`docs/TINY_PLATFORM_AI_TEST_TASK_TEMPLATE.md`
- **门禁豁免政策**：`docs/TINY_PLATFORM_CI_WAIVER_POLICY.md`
- **测试数据库残留治理基线**：`tiny-oauth-server/scripts/verify-test-db-residuals.sh`
- **权限标识规范**：`docs/TINY_PLATFORM_PERMISSION_IDENTIFIER_SPEC.md`
- **测试数据命名与清理规范**：`docs/TINY_PLATFORM_TEST_ACCOUNT_NAMING_AND_CLEANUP_RULES.md`
- **授权模型与重构方案**：`docs/TINY_PLATFORM_AUTHORIZATION_MODEL.md`
- **Session / Bearer 认证来源矩阵**：`docs/TINY_PLATFORM_SESSION_BEARER_AUTH_MATRIX.md`（含 **`/sys/users/current` 读 vs `/active-scope` 写的 M4 分口径**，§8）
- **功能权限 + 数据权限分层总图**：`docs/TINY_PLATFORM_AUTHORIZATION_LAYERED_MODEL.md`
- **下一阶段变更与改进清单**：`docs/TINY_PLATFORM_AUTHORIZATION_NEXT_PHASE_AND_IMPROVEMENTS.md`
- **权限/授权可执行任务清单**：`docs/TINY_PLATFORM_AUTHORIZATION_TASK_LIST.md`
- **文档当前态漂移守卫（启发式）**：`tiny-oauth-server/scripts/verify-authorization-doc-current-state-drift.sh`
- **`@DataScope` 扩面指南**：`docs/TINY_PLATFORM_DATASCOPE_EXPANSION_GUIDE.md`
- **构建与技术债台账**：`docs/TINY_PLATFORM_BUILD_TECH_DEBT_LEDGER.md`
- **SaaS 架构治理收口启动清单**：`docs/TINY_PLATFORM_SAAS_ARCHITECTURE_GOVERNANCE_STARTER_20260417.md`
- **分支治理策略**：`docs/TINY_PLATFORM_SB4_SB3_BRANCH_STRATEGY.md`
- **租户治理专题**：`docs/TINY_PLATFORM_TENANT_GOVERNANCE.md`
- **租户治理修复 Prompt**：`docs/TINY_PLATFORM_TENANT_GOVERNANCE_CURSOR_FIX_PROMPT.md`
- **租户初始化向导 Cursor 任务卡**：`docs/TINY_PLATFORM_TENANT_INITIALIZATION_WIZARD_CURSOR_TASK_CARDS.md`
- **平台域解耦 Cursor 任务卡**：`docs/TINY_PLATFORM_PLATFORM_SCOPE_CURSOR_TASK_CARDS.md`（未标「当前态」的约束多为历史/阶段性口径；运行态以 `docs/TINY_PLATFORM_AUTHORIZATION_TASK_LIST.md`、`docs/TINY_PLATFORM_TESTING_PLAYBOOK.md`、`docs/TINY_PLATFORM_TENANT_GOVERNANCE.md` 为准，**CARD-14E**）
- **租户命名拆分规范**：`docs/TINY_PLATFORM_TENANT_NAMING_GUIDELINES.md`
- **RBAC3 enforce 灰度 SOP**：`docs/TINY_PLATFORM_RBAC3_ENFORCE_ROLLOUT_SOP.md`
- **平台角色 / RBAC3 / 审批设计**：`docs/TINY_PLATFORM_PLATFORM_RUNTIME_RBAC3_GOVERNANCE_DESIGN.md`
- **平台角色治理 Cursor 任务卡**：`docs/TINY_PLATFORM_PLATFORM_ROLE_GOVERNANCE_CURSOR_TASK_CARDS.md`
- **测试规则源**：`.agent/src/rules/50-testing.rules.md`
- **CI/CD 规则源**：`.agent/src/rules/58-cicd.rules.md`
- **平台规则源**：`.agent/src/rules/90-tiny-platform.rules.md`
- **认证规则源**：`.agent/src/rules/91-tiny-platform-auth.rules.md`
- **权限标识规则源**：`.agent/src/rules/92-tiny-platform-permission.rules.md`
- **授权模型规则源**：`.agent/src/rules/93-tiny-platform-authorization-model.rules.md`
- **租户治理规则源**：`.agent/src/rules/94-tiny-platform-tenant-governance.rules.md`
- **构建**：`.agent/build/build.sh --target cursor`
- **校验**：`.agent/build/validate.sh --target cursor --cursor-format mdc`

> 说明：
> - `AGENTS.md` 是统一入口与顶层契约，不是所有规则的全文展开版。
> - Cursor 运行时直接消费 `.cursor/rules/**`。
> - Codex 当前没有仓库内专用生成产物，默认以 `AGENTS.md`、`docs/**` 和 `.agent/src/**` 为规则来源。

---

## 1. 项目概述（手写区）

> ✅ 这里只写“稳定信息”（长期不变 / 少变）

- **tiny-platform 定位**：插件化单体 + All-in-One + 多租户
- **关键模块**：auth / dict / workflow / plugin / tenant
- **强约束**：安全 / 权限 / 租户隔离不可破坏

---

## 2. 协作铁律（手写区｜短而硬）

1. **先假设后执行**：信息不确定必须写出假设与风险，再给方案
2. **修改最小化**：不做无关重构；每次改动必须可回滚
3. **安全/权限/多租户不可弱化**：任何削弱必须明确说明并请求确认
4. **输出必须可执行**：给出可执行命令/路径/文件清单
5. **产物禁止手改**：`.cursor/rules/**` 等生成物不手工编辑（只改 `.agent/src/**`）
6. **可自动化先验证再下结论**：凡仓库内已有命令/测试能影响结论（如平台登录、租户解析、密码校验链），助手应先执行对应验证并写明结果；无法自动化部分（个人库数据、未注入密钥的 Playwright real-link）须明确标注缺口。平台登录相关快速门禁：`bash tiny-oauth-server/scripts/verify-platform-login-auth-chain.sh`（可选 `VERIFY_PLATFORM_LOGIN_E2E=1` 跑 Tier2 MockMvc 全链路；未导出 `E2E_DB_PASSWORD` 时会自动 `source` `tiny-oauth-server/src/main/webapp/.env.e2e.local`；Tier2 前在满足主身份变量齐全时自动执行 `scripts/e2e/ensure-scheduling-e2e-auth.sh` 对齐库内口令）。平台模板行数（需本机 MySQL 与 `DB_PASSWORD`）：`DB_PASSWORD='…' bash tiny-oauth-server/scripts/verify-platform-template-row-counts.sh`（可选 `VERIFY_PLATFORM_TEMPLATE_MIN_ROWS=1` 要求 `role` 与 split carrier 模板总量均 > 0）。**tiny-platform 本地 AI 验证默认入口（先跑这个）**：`bash tiny-oauth-server/scripts/verify-platform-local-dev-stack.sh`。仅在**明确不需要前端联动**时，才降级到 **后端/数据库自举入口**：`DB_PASSWORD='…' bash tiny-oauth-server/scripts/verify-platform-dev-bootstrap.sh`（**CARD-13E**：`verify-platform-dev-bootstrap.sh` 在调用 `ensure-platform-admin.sh` 前须能从 **`PLATFORM_TENANT_CODE` 或 `E2E_PLATFORM_TENANT_CODE`** 得到平台租户 code；两者皆缺则 exit 1，不再隐式 `default`）。仅在**纯 Maven 编译/定向测试门禁**时，才使用顺序门禁：`bash tiny-oauth-server/scripts/mvn-tiny-oauth-server-gate-sequential.sh`。若 `sb4` / Spring snapshot / Camunda fork 相关构建日志出现 `camunda-nexus` / `camunda-public-repository` / `JBoss public` 或 metadata `401`，默认先运行 `bash tiny-oauth-server/scripts/diagnose-sb4-maven-repository-chain.sh` 做脱敏盘点，**不要**直接把它判定成项目 POM 缺失仓库声明。`SKIP_MVN=1`、`SKIP_OAUTH_SERVER_START=1` / `FORCE_START_OAUTH_SERVER=1`、`SKIP_FRONTEND_START=1` / `FORCE_START_FRONTEND=1` 见脚本头注释。**退出码**：`0` 通过；`1` 验证失败；`2` **环境前置未满足**（无 `DB_PASSWORD`/无 `mysql`/无 `npm`/连不上库）— **非代码失败**，详见 `docs/TINY_PLATFORM_TESTING_PLAYBOOK.md` §1.2、§1.4。**本地环境读取**：只允许从 login shell 白名单环境变量读取 `DB_*` / `E2E_DB_*` / `MYSQL_*` / `FRONTEND_*`；其中 `DB_*` 为 dev/bootstrap 主变量，`E2E_DB_*` 可作为兼容别名回填，禁止打印 `~/.zprofile` / `~/.zshrc` / `~/.bashrc` 全文。**Maven**：勿对 `tiny-oauth-server` 同模块并发 `compile`/`test`；顺序门禁见 `tiny-oauth-server/scripts/mvn-tiny-oauth-server-gate-sequential.sh`。
7. **助手默认直接执行，不默认转嫁给用户**：凡仓库内已有脚本、编译命令、数据库查询或自动化测试能直接给出结果，助手应直接执行并反馈；只有在环境前置缺失、权限不足或存在明确破坏性风险时，才允许请求用户手工介入。
8. **分支治理不可口头漂移**：当前默认开发主干为 `sb4`，`sb3` 仅接选择性 backport；除非用户明确要求，不默认把 PR / 提交 / workflow 目标指向 `main`，也不默认做整线 `sb4 -> sb3` 同步。

---

## 2.1 冲突裁决顺序（手写区｜建议保留）

当不同规则冲突时，按以下顺序裁决：

1. **禁止** 优先于一切（Must Not > Must > Should > May）
2. **平台特定覆盖通用**：`90+` > `30+` > `10/20` > `00`
3. **更严格覆盖更宽松**
4. **仍不确定时**：必须说明假设并请求确认（或给默认策略）

---

## 2.2 工具适配说明（手写区）

为避免不同 AI 工具读取规则时产生理解偏差，统一约定如下：

1. **唯一真相源始终是**：`.agent/src/**`
2. **Cursor 产物只是生成物**：`.cursor/rules/**` 仅供 Cursor 直接消费，禁止手改
3. **Codex 依赖统一入口**：Codex 默认应先读 `AGENTS.md`，再按其中链接读取 `docs/**` 与 `.agent/src/**`
4. **新增规则时必须双同步**：
   - 更新对应 `.agent/src/rules/*.rules.md`
   - 如影响通用入口或高频决策，更新 `AGENTS.md`
5. **不要假设 AGENTS 全量展开了规则正文**：
   - 详细可执行约束以 `.agent/src/rules/**` 为准
   - 长文说明与设计基线以 `docs/**` 为准

当前仓库只实现了 `cursor` 构建/校验目标；若未来引入 `.codex` 产物，仍必须以 `.agent/src/**` 为唯一真相源，而不是在 `.codex/**` 中手工维护第二套规则。

---

## 3. 生成区（由脚本更新｜禁止手改）

> 本区块由 `.agent/build/agentsmd.sh` 或 build 流程更新  
> 允许脚本“只替换生成块”，不得覆盖手写区。

<!-- BEGIN GENERATED:AGENTS -->
## 生成区（自动生成）

- 规则系统版本：v2.3.1
- 构建命令：.agent/build/build.sh --target cursor
- 校验命令：.agent/build/validate.sh --target cursor --cursor-format mdc

> 注意：本区块由脚本生成，禁止手改。
<!-- END GENERATED:AGENTS -->

---

## 4. 项目特有补充（手写区）

> 写 tiny-platform 的“高频决策”，避免 AI 自作主张。

- 表命名：单数（user/resource）
- 认证：JWT / Session 混合策略（按客户端来源切换）
- 前端：Vue3 + Ant Design Vue
- 安全：RS256 JWT + JWK Set + MFA(TOTP)
- 新签发 access token 的 `authorities` / `permissions` / `roleCodes` 与 `permissionsVersion` 必须来自同一份当前运行时授权快照；若可按当前 active scope 重载权威链，不得只刷新 `permissionsVersion` 而继续复用登录期 `SecurityUser` 快照，尤其 `PLATFORM` 作用域
- 授权/控制面 DTO 或兼容写链一旦变更，必须同步后端 DTO、前端 TS 接口、表单透传与定向测试；不要只改服务层后宣称“任务已完成”
- 涉及 `db/changelog/**`、`db.changelog-master.yaml`、`api_endpoint` / `menu_permission_requirement` / `role_permission` 回填、权限/菜单 seed 或 DDL 的任务，完成条件必须包含一次真实 `SpringLiquibase` / 应用启动验证；只跑单测/集测不算完成。若环境前置不足，只能标“阻塞/未验证”，不得写“已完成，待用户启动验证”
- 平台角色治理 `CARD-PR-01 ~ CARD-PR-08` 必须显式写清本卡负责与不负责的边界，尤其统一守卫回填、菜单回填、Liquibase include、real-controller 测试、启动验证不得默认外推到下一张卡或留给用户首次启动时发现
- `POST /sys/roles/{id}/resources` 当前控制面契约只接受 `permissionIds`；前端组件 emit、TS payload、测试命名不得继续传播 `resourceIds` 运行态语义
- 菜单控制面主入口是 `/sys/menus`；不要新增/恢复 `/sys/resources/menus*`，也不要让菜单前端 API 再借用 `/sys/resources/check-*`
- 收口 `permission` 历史写链时，不能只改 `ResourceForm`；`MenuForm`、`MenuServiceImpl` 与菜单 DTO 也必须同步切到 `requiredPermissionId` 主入口
- 租户初始化向导当前态（CARD-TW-01~TW-04）必须保持：create 走 `TenantCreateWizard`、edit 走 `TenantForm`、确认步骤走 `POST /sys/tenants/precheck` dry-run、最终由 wizard 一次性 `POST /sys/tenants`、结果页成功后不自动关闭且详情跳转携带 `query.from`
- 规则扩展记录：2026-02-05 增补 logging/performance/dependency/config/docs/code-review 规则并加强构建清理策略
