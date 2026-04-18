# Tiny Platform 构建与技术债台账

> 状态：正式登记（非口头约定）  
> 适用范围：`tiny-oauth-server`（Maven/Java）、嵌入式 `webapp`（Vite）  
> 关联：`TINY_PLATFORM_DATASCOPE_EXPANSION_GUIDE.md` §11（DataScope/前端控制面相关卫生项）、平台模板治理见 `TINY_PLATFORM_TENANT_GOVERNANCE.md` §3.2。

本文把 **已消除的告警**、**执行方式噪声** 与 **刻意保留项** 分开写清；禁止用「以后再优化」替代登记。

---

## 0. 问题分级（环境缺口 / 执行噪声 / 真实技术债）

| 类别 | 典型表现 | 判定 | 本轮动作 |
| --- | --- | --- | --- |
| **环境前置缺口** | 未设 `DB_PASSWORD`、无 `mysql` 客户端、无法连接本机 MySQL | **非代码失败**；`verify-platform-dev-bootstrap.sh` **exit 2** | 脚本 preflight + 文档 §1.2（Testing Playbook） |
| **Maven 同模块并发噪声** | 并行对 `tiny-oauth-server` 跑 `compile`/`test`/`package` → `target/classes` 下 `NoSuchFileException`、半写入 | **非代码回归**；执行方式问题 | 文档禁止并发；提供 `mvn-tiny-oauth-server-gate-sequential.sh` 顺序门禁 |
| **真实技术债** | javac deprecation、JaCoCo exec 漂移；Vite mixed import **已收口**（见 §1） | 需改代码或架构升级才能消 | §1 已修 / §2 保留项 |

---

## 1. 本轮已处理（可验证）

| 工具 | 项 | 处理 |
| --- | --- | --- |
| javac | `Specification.where(null)` 过时（`DataScopeSpecification`） | 改为 `(root, q, cb) -> cb.conjunction()` |
| javac | `AntPathRequestMatcher` 过时（`DefaultSecurityConfig` CSRF 路径） | 改为 `PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, …)` |
| Vite | 主 chunk > 500kB 默认告警 | `vite.config.ts` 设置 `build.chunkSizeWarningLimit: 3072`（消除噪声；与 **入口业务 chunk** 体积分开看待，见下行） |
| Vite | 入口 `index-*.js` 过大（历史 ~2.8MB 单文件） | **已推进（2026-03-28）**：① `router/index.ts` 将原静态引入的 **Login、Callback、BasicLayout、HomeView、异常页、TotpBind/TotpVerify、OIDCDebug、DefaultView fallback** 全部改为 `() => import(...)`，路由级异步 chunk；② `build.rollupOptions.output.manualChunks` 将 `node_modules` 拆为 `vendor-antd`、`vendor-antd-icons`、`vendor-vue`、`vendor-vue-router`、`vendor-pinia`、`vendor-oidc`、`vendor-axios`、`vendor-bpmn`、`vendor-other`。实测主入口 **约 35KB**（gzip ~12.6KB）。可选：`npm run build:analyze` 生成 `dist/bundle-stats.html`。**mixed import**：仍须 `build-only` 无 `vite:reporter` 混用告警。 |
| Vite | Ant Design Vue **整包** `app.use(Antd)` + 图标 `import *` | **已收口（2026-03-28）策略 B**：`unplugin-vue-components` + `AntDesignVueResolver`（`importStyle: false`，`resolveIcons: true`）；`main.ts` **不再** `app.use(整包 Antd)`；全局仅保留 **`ant-design-vue/dist/reset.css`** + **`App.vue`** 显式 `ConfigProvider` + `zh_CN` locale。`message` / `Modal` / 类型仍从 `ant-design-vue` **按文件显式 import**（统一 API 路径）。`Icon.vue` / `IconSelect.vue` 去掉 `@ant-design/icons-vue` 的 `import *`，改为 `import.meta.glob` 按文件懒加载 + `Icon.vue` 内 **动态** `import('@/utils/antdIconLoaders')` 将 glob 映射移出 `BasicLayout` chunk。`MenuForm.vue` 删除未使用 `import * as allIcons`。构建对比（`build-only`）：`vendor-antd` **约 1232KB → 835KB**（gzip **约 372KB → 248KB**，**约 -33%**）；`vendor-antd-icons` 原 **约 501KB / gzip 39KB** → **约 593KB / gzip 46KB**（gzip 略升：Resolver + 各页 `XxxOutlined` 命名导入聚合；**已消除**整包 namespace 拉全量图标）。另增 **独立 chunk** `antdIconLoaders-*.js`（**约 130KB raw / gzip 7KB**，首屏侧栏图标时加载，含 glob 路径表）。 |
| Vite | `plugin vite:reporter`：同模块既静态又动态 `import`（mixed import） | **已收口（2026-03-28）**：统一为静态导入或去掉无效动态边界。`traceId.ts` 顶部静态引入 `logger` / `tenant`；`request.ts` 静态 `persistentLogger`；`auth.ts` 静态 `addTraceIdToFetchOptions`；`router/index.ts` 静态 `getCurrentTraceId`；`TotpBind.vue` / `Setting.vue` / `Profile.vue` 静态 `fetchWithTraceId`；`401.vue` 静态 `userManager` / `useAuth`（构建日志已证实动态 import **不会**把 `auth`/`oidc` 拆出独立 chunk，故改为静态无懒加载收益损失）。`traceId` 内原 `import('@/router')` 已移除，改用 `window.location.assign` 兜底，**避免** `router → auth → traceId → router` 同步环。验证：`npm --prefix tiny-oauth-server/src/main/webapp run build-only` 无 `plugin vite:reporter` mixed import 行。 |

---

## 2. 保留项（延期，需架构/上游协同）

| 工具 | Warning / 现象 | 影响范围 | 不在这轮改的原因 | 唯一建议后续动作 |
| --- | --- | --- | --- | --- |
| javac | `OAuth2AuthorizationServerConfiguration.applyDefaultSecurity` 过时 | `AuthorizationServerConfig` | Spring Authorization Server 大版本迁移与全链路回归 | 跟随 Boot/AS BOM 升级专项；单独「消一行告警」价值低、风险高 |
| javac | `AuthorizationGrantType.PASSWORD` 过时 | `OAuth2Password*` 等扩展类 | OAuth2 授权模式策略与 AS 版本绑定 | 与「是否继续支持 password grant」产品决策一并处理 |
| javac | `sun.misc.Unsafe`（Jackson `LongAllowlistModule`） | 序列化安全路径 | 替换需验证数值边界与性能 | 跟踪 Jackson/JDK 官方替代或模块封装 |
| javac | 其他 deprecation 摘要行（如 `IdempotentConsoleRepository`） | 局部 API | 未逐条打开 `-Xlint:deprecation` 全量清零 | 模块触碰时逐文件收敛 |
| Spring Test | `@MockBean` 过时（Boot 3.4+ 倾向 `@MockitoBean` 等） | 多份 `*RbacIntegrationTest` | 批量改注解与上下文契约，易波及 CI | **专项 PR**：按模块迁移并跑全量集成测试；本轮未改半套 |
| JaCoCo | `execution data does not match` / 报告行号漂移 | `target/jacoco.exec` 与 class 不同步时 | 多次增量 `test` 复用旧 exec、或与并发编译交错、或**先后**运行不同 surefire 子集复用同一 exec | **唯一建议动作**：需要可信报告或消除 mismatch 告警时 **`mvn clean test`**（或顺序门禁前设 **`GATE_CLEAN_FIRST=1`** 跑 `mvn-tiny-oauth-server-gate-sequential.sh`）；**不要**与同模块并发的 `compile`/`test` 交错 |
| Maven 仓库链路 | `camunda-nexus` / `camunda-public-repository` / `JBoss public` 的 metadata `401` 或候选仓库痕迹 | `sb4` snapshot / 冷仓解析排查、`/usr/local/data/repo` 历史 `resolver-status.properties` / `.lastUpdated` | 当前 effective settings 与项目 effective POM 均未把这些来源作为 tiny-platform 主线路径；现阶段更像环境注入或历史解析候选仓库噪音，而不是项目 POM 缺失仓库声明 | **唯一建议动作**：优先运行 `bash tiny-oauth-server/scripts/diagnose-sb4-maven-repository-chain.sh` 做脱敏盘点；不要把 `camunda-nexus` 反写进项目 POM，也不要先做静默 suppress |
| Vite | **`vendor-antd` 仍为最大传输块**（gzip **~248KB** 量级，2026-03-28 实测，随版本波动） | 业务广谱使用 `a-*` 与 `message`/`Modal` 等 API，对应 **es 子模块**仍聚合到 `vendor-antd` | 与 mixed import **不同类**；整包 `app.use(Antd)` 已移除 | **唯一主阻断点（前端）**：按业务域继续收紧 AntD 组件面、或评估 **组件级样式** 与表单密度（需产品/UI 取舍，非纯构建开关） |

---

## 2.1 Maven 同模块并发（执行噪声，正式界定）

- **现象**：对同一 `artifactId`（如 `tiny-oauth-server`）**同时**执行多个 Maven 生命周期（例如两个终端分别 `mvn compile` 与 `mvn test`，或 IDE 后台编译与 CLI `test`），共享同一 `target/`，可能出现 `NoSuchFileException`、`.class` 不完整。
- **性质**：**执行方式噪声**，不是源代码缺陷。
- **规避**：同一模块 **顺序执行** `compile` → `test`（或 `mvn clean test`）；或使用仓库脚本 `tiny-oauth-server/scripts/mvn-tiny-oauth-server-gate-sequential.sh`。
- **CI/助手**：不要对同一 `target` 并发跑多个 Maven 进程。

---

## 3. 与 DataScope 指南的关系

- **§10–§11**（`TINY_PLATFORM_DATASCOPE_EXPANSION_GUIDE.md`）保留 **前端 active scope / 租户边界** 与 **首段构建卫生** 叙述。
- **本文**是全仓库 **构建链技术债** 的单一扩展台账；新增条目优先追加到 §2，并在 §1 记录已关闭项。
