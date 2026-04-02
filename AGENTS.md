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
- **权限标识规范**：`docs/TINY_PLATFORM_PERMISSION_IDENTIFIER_SPEC.md`
- **测试账号命名与清理规范**：`docs/TINY_PLATFORM_TEST_ACCOUNT_NAMING_AND_CLEANUP_RULES.md`
- **授权模型与重构方案**：`docs/TINY_PLATFORM_AUTHORIZATION_MODEL.md`
- **Session / Bearer 认证来源矩阵**：`docs/TINY_PLATFORM_SESSION_BEARER_AUTH_MATRIX.md`（含 **`/sys/users/current` 读 vs `/active-scope` 写的 M4 分口径**，§8）
- **功能权限 + 数据权限分层总图**：`docs/TINY_PLATFORM_AUTHORIZATION_LAYERED_MODEL.md`
- **下一阶段变更与改进清单**：`docs/TINY_PLATFORM_AUTHORIZATION_NEXT_PHASE_AND_IMPROVEMENTS.md`
- **权限/授权可执行任务清单**：`docs/TINY_PLATFORM_AUTHORIZATION_TASK_LIST.md`
- **`@DataScope` 扩面指南**：`docs/TINY_PLATFORM_DATASCOPE_EXPANSION_GUIDE.md`
- **构建与技术债台账**：`docs/TINY_PLATFORM_BUILD_TECH_DEBT_LEDGER.md`
- **租户治理专题**：`docs/TINY_PLATFORM_TENANT_GOVERNANCE.md`
- **租户治理修复 Prompt**：`docs/TINY_PLATFORM_TENANT_GOVERNANCE_CURSOR_FIX_PROMPT.md`
- **租户命名拆分规范**：`docs/TINY_PLATFORM_TENANT_NAMING_GUIDELINES.md`
- **RBAC3 enforce 灰度 SOP**：`docs/TINY_PLATFORM_RBAC3_ENFORCE_ROLLOUT_SOP.md`
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
6. **可自动化先验证再下结论**：凡仓库内已有命令/测试能影响结论（如平台登录、租户解析、密码校验链），助手应先执行对应验证并写明结果；无法自动化部分（个人库数据、未注入密钥的 Playwright real-link）须明确标注缺口。平台登录相关快速门禁：`bash tiny-oauth-server/scripts/verify-platform-login-auth-chain.sh`（可选 `VERIFY_PLATFORM_LOGIN_E2E=1` 且设置 `E2E_DB_PASSWORD` 跑 Tier2 MockMvc 全链路）。平台模板行数（需本机 MySQL 与 `DB_PASSWORD`）：`DB_PASSWORD='…' bash tiny-oauth-server/scripts/verify-platform-template-row-counts.sh`（可选 `VERIFY_PLATFORM_TEMPLATE_MIN_ROWS=1` 要求 `role` 与 split carrier 模板总量均 > 0）。**tiny-platform 本地 AI 验证默认入口（先跑这个）**：`bash tiny-oauth-server/scripts/verify-platform-local-dev-stack.sh`。仅在**明确不需要前端联动**时，才降级到 **后端/数据库自举入口**：`DB_PASSWORD='…' bash tiny-oauth-server/scripts/verify-platform-dev-bootstrap.sh`。仅在**纯 Maven 编译/定向测试门禁**时，才使用顺序门禁：`bash tiny-oauth-server/scripts/mvn-tiny-oauth-server-gate-sequential.sh`。`SKIP_MVN=1`、`SKIP_OAUTH_SERVER_START=1` / `FORCE_START_OAUTH_SERVER=1`、`SKIP_FRONTEND_START=1` / `FORCE_START_FRONTEND=1` 见脚本头注释。**退出码**：`0` 通过；`1` 验证失败；`2` **环境前置未满足**（无 `DB_PASSWORD`/无 `mysql`/无 `npm`/连不上库）— **非代码失败**，详见 `docs/TINY_PLATFORM_TESTING_PLAYBOOK.md` §1.2、§1.4。**本地环境读取**：只允许从 login shell 白名单环境变量读取 `DB_*` / `E2E_DB_*` / `MYSQL_*` / `FRONTEND_*`；其中 `DB_*` 为 dev/bootstrap 主变量，`E2E_DB_*` 可作为兼容别名回填，禁止打印 `~/.zprofile` / `~/.zshrc` / `~/.bashrc` 全文。**Maven**：勿对 `tiny-oauth-server` 同模块并发 `compile`/`test`；顺序门禁见 `tiny-oauth-server/scripts/mvn-tiny-oauth-server-gate-sequential.sh`。

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
- 规则扩展记录：2026-02-05 增补 logging/performance/dependency/config/docs/code-review 规则并加强构建清理策略
