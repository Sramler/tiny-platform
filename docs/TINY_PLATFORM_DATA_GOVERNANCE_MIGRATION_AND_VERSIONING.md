# Tiny Platform 数据治理、数据对比、数据迁移与版本管理方案

> 状态：设计初稿
> 适用范围：`tenant / auth / menu / resource / user / workflow / dict / audit / db/changelog / runtime version`
> 适用分支：`sb4`
> 关联文档：
>
> - `docs/TINY_PLATFORM_TENANT_GOVERNANCE.md`
> - `docs/TINY_PLATFORM_AUTHORIZATION_MODEL.md`
> - `docs/TINY_PLATFORM_SESSION_BEARER_AUTH_MATRIX.md`
> - `docs/TINY_PLATFORM_TESTING_PLAYBOOK.md`
> - `AGENTS.md`

---

## 1. 目的与边界

### 1.1 目的

本方案用于统一 tiny-platform 在以下场景下的数据治理口径：

- 日常数据管理与数据质量治理
- 结构升级与历史数据回填
- 多租户数据对比、迁移与修复
- 平台模板与租户副本的版本控制
- 升级、迁移、回滚过程中的审计与证据留存

本方案重点回答四个问题：

1. tiny-platform 当前哪些数据是运行时真相源，哪些只是兼容层或派生物
2. 数据升级与迁移时，如何保证多租户隔离、安全边界和审计链不被破坏
3. 不同类型的数据应该采用什么版本管理策略，而不是一刀切地“全部快照”
4. 如何形成“设计 -> 执行 -> 比对 -> 审计 -> 回滚”的闭环

### 1.2 本方案负责什么

本方案负责：

- 定义 tiny-platform 的数据分类与治理边界
- 定义结构版本、运行时配置版本、迁移任务版本的分层口径
- 定义表级、行级、业务级、租户级的数据对比方法
- 定义迁移实施、切换、回滚和验证要求
- 定义多租户场景下的数据版本与审计要求

### 1.3 本方案不负责什么

本方案不负责：

- 替代各专项设计文档中的领域细节，例如授权模型、租户生命周期、RBAC3 规则本身
- 规定所有业务表都做全量历史版本
- 把缓存、临时中间表、可重建派生数据提升为长期版本化对象
- 直接定义某一张任务卡的实施完成度；真实完成度仍以专项任务清单和当前代码状态为准

---

## 2. 当前态

### 2.1 平台运行背景

tiny-platform 是插件化单体 + All-in-One + 多租户平台，当前已有明确的安全与数据边界：

- 安全、权限、租户隔离不可弱化
- 业务数据默认需要 `tenant_id` 维度
- 平台域与租户域必须显式分流
- 文档、规则、迁移脚本、运行时行为需要保持同一口径

### 2.2 当前已确认的运行态口径

根据现有文档和规则，当前至少有以下稳定口径：

- 业务多租户主链以 `tenant_id` 作为隔离基础维度
- membership 真相源以 `tenant_user` 为主，而不是简单依赖 `user.tenant_id`
- 授权关系主链以 `role_assignment -> role_permission -> permission` 为准
- 平台模板语义采用 `tenant_id IS NULL` 显式建模，不能继续把某个默认租户 code 当作平台语义本身
- 菜单结构、路由、显隐和菜单权限 requirement 的运行时变化，已要求通过 `runtime_version_signal` 的 `MENU_CONFIG` 版本域与 `/sys/menus/tree` ETag 做失效控制
- 新签发 token / session 权限快照已区分：
  - `permissionsVersion`：表达授权快照漂移
  - `tokenSecurityVersion` / `tokenNotBefore`：表达用户禁用、删除、密码或 MFA 安全状态变化后的强制失效
- 平台模板与租户副本当前口径为“显式派生 + 差异观测 + 不自动跟随同步”
- 租户生命周期已采用“tenant scope fail-closed，platform scope 治理白名单继续可用”的策略

### 2.3 当前态的直接结论

这些约束决定了 tiny-platform 的数据治理不能只看“数据库结构版本”，而必须同时覆盖：

1. 结构版本
2. 运行时配置版本
3. 迁移任务版本
4. 安全失效版本

换句话说，tiny-platform 的数据升级不是单纯的 DDL 迁移问题，而是“结构 + 运行时 + 治理证据”的组合问题。

---

## 3. 目标态

### 3.1 目标态总原则

目标态希望把 tiny-platform 的数据治理收口为三层闭环：

1. 结构层：用 `Liquibase` 管住 schema、约束、seed 和历史收口
2. 运行层：用版本信号、权限快照、安全失效机制管住运行时一致性
3. 治理层：用对比、审计、差异报告和回滚方案管住升级与迁移风险

### 3.2 目标态要求

目标态至少满足以下条件：

- 真相源与派生物边界清晰
- 多租户数据迁移和对比支持按租户维度执行
- 平台模板、租户副本、菜单配置、权限 requirement 具备可治理的版本证据
- 高风险迁移具备结构级、数据级、业务级、拒绝路径级四层验证
- 结构迁移、运行时失效、审计留痕和回滚路径形成完整闭环

### 3.3 目标态不追求什么

目标态不追求：

- 为所有表引入逐行全量历史版本
- 用缓存或前端本地状态代替服务端版本真相源
- 用一次性人工脚本替代长期可重复执行的治理链路
- 让平台模板自动强推到所有租户副本

---

## 4. 数据分类与真相源分层

### 4.1 数据分类

tiny-platform 的数据建议分为五类：

| 数据类型 | 典型对象 | 真相源特征 | 版本建议 |
| --- | --- | --- | --- |
| 结构数据 | 表、字段、索引、约束、DDL | 由 Liquibase changeset 驱动 | 结构版本 |
| 平台治理数据 | tenant、platform template、permission、menu requirement、api endpoint requirement | 平台控制面与运行时治理依赖 | 配置版本 + 审计 |
| 租户业务数据 | user、dict、workflow、carrier 副本数据 | 必须带租户语义 | 业务版本 / 审计 / 归档 |
| 安全与审计数据 | auth audit、authentication audit、token security state、治理审计 | 需要可追溯、不可静默覆盖 | 日志版本 / 保留策略 |
| 缓存与派生数据 | 菜单树缓存、运行时快照、本地缓存、中间结果 | 不是主真相源，可重建 | 失效机制，不做长期版本化 |

### 4.2 真相源裁决原则

所有数据治理与迁移任务都必须先回答：该数据到底是“真相源”还是“派生物”。

裁决原则如下：

1. 结构真相源由 `db/changelog/**` 和数据库当前 schema 决定
2. 授权真相源以当前运行态已确认的正式模型为准，不得继续把历史兼容字段当主链
3. 平台模板真相源由 `tenant_id IS NULL` 的平台模板数据表达
4. 缓存、前端本地菜单缓存、运行时拼装结果不是权限或路由的最终真相源
5. 对比、迁移、恢复时优先围绕真相源执行，派生物通过失效或重建恢复

---

## 5. 三类版本模型

### 5.1 结构版本

结构版本指表结构、字段、索引、约束、DDL 和初始化种子链路的版本。

实施要求：

- 统一通过 `Liquibase` 管理
- 已在共享环境或 fresh DB 执行过的 changeset 不得直接改写正文修问题，必须追加新的尾部 changeset 收口
- 任何涉及 `db/changelog/**`、`db.changelog-master.yaml`、权限/menu seed、DDL/nullability/index 的任务，都必须至少做一次真实 `SpringLiquibase` / 应用启动验证
- 不能只用 unit test 或 controller test 就宣称“迁移已闭合”

### 5.2 运行时配置版本

运行时配置版本指会影响当前业务可见性、权限判定、菜单注入、路由收敛、token 权限快照的版本。

在 tiny-platform 中，至少应包括：

- 菜单配置版本：`runtime_version_signal.MENU_CONFIG`
- 授权快照版本：`permissionsVersion`
- 安全失效版本：`tokenSecurityVersion`
- 安全失效时间下限：`tokenNotBefore`

该层版本的目标不是做历史归档，而是：

- 驱动缓存失效
- 阻止旧 token / 旧 session 继续误用
- 避免菜单、权限、路由和运行时快照漂移

### 5.3 迁移任务版本

迁移任务版本指某一次数据升级、回填、修复、导入导出、平台模板治理操作本身的版本。

至少应记录：

- 任务 ID / 批次号
- 关联需求或工单
- 涉及模块和表
- 迁移脚本版本
- 映射规则版本
- 执行人 / 执行时间
- 目标环境
- 对比报告
- 回滚方案
- 最终结果

---

## 6. 多租户数据治理要求

### 6.1 基本要求

对于租户业务数据，必须满足：

- 所有业务表都评估 `tenant_id` 语义
- 所有业务查询都评估租户过滤
- 所有唯一约束优先评估 `tenant_id + business_key`
- 所有迁移、比对、归档、恢复都支持租户维度
- 跨租户误读、误写、误恢复的容忍度为 `0`

### 6.2 平台域与租户域分流

tiny-platform 的平台治理数据与租户业务数据不能混为一谈。

明确口径：

- 平台模板、平台治理控制面、平台审计等数据允许显式 `tenant_id IS NULL`
- 租户业务数据不能把 `NULL tenant_id` 当作“特殊租户”直接复用
- 平台数据迁移与租户数据迁移必须在文档、脚本、校验、回滚上显式区分

### 6.3 租户副本治理

对于从平台模板派生出的租户副本：

- 必须明确模板真相源与租户副本不是同一层对象
- 默认不允许自动跟随平台模板同步
- 差异比较应作为治理观测证据，而不是运行时授权真相源
- 若执行修复或重建，必须以单租户、显式审批、审计留痕和备份前提为约束

---

## 7. 数据质量管理

### 7.1 质量规则分层

tiny-platform 的数据质量规则建议分三层：

#### 7.1.1 结构层

- 字段类型正确
- 非空约束符合预期
- 唯一约束符合租户语义
- 外键或逻辑关系不破坏主链

#### 7.1.2 运行态层

- 授权关系主链数据完整
- 平台模板记录满足层级不变量
- 菜单 requirement、endpoint requirement 与 seed 数据一致
- token 安全状态与用户状态不漂移

#### 7.1.3 治理层

- 不允许跨租户脏关联
- 不允许核心业务表遗漏 `tenant_id`
- 不允许兼容字段重新回流成新主链
- 不允许迁移后只更新版本指纹而不更新权威快照

### 7.2 质量输出物

每次重要迁移或升级建议至少产出：

- 迁移前体检报告
- 迁移后差异报告
- 多租户隔离验证报告
- 安全与权限链路验证报告
- 回滚演练记录或可执行回滚说明

---

## 8. 数据对比设计

### 8.1 对比分层

tiny-platform 的数据对比必须至少覆盖四层：

#### 8.1.1 结构级对比

用于确认：

- 表结构是否已按预期升级
- changeset 是否全部执行
- 索引、唯一约束、非空约束是否到位
- 初始化数据链路是否闭合

#### 8.1.2 表级对比

用于确认：

- 总行数是否一致
- 按租户分组的行数是否一致
- 关键统计值是否一致，如 `COUNT/SUM/MAX/MIN`
- 平台模板与租户副本数量是否符合预期

#### 8.1.3 行级与字段级对比

用于确认：

- 按主键或稳定业务键逐行比对
- 核心字段逐字段比对
- 大表分批或分租户比对
- 对整行生成摘要进行快速校验

说明：

- 如果仅用于一致性校验，摘要可以使用 `MD5`
- 如果涉及安全语义或对外证据链，优先使用更稳妥的摘要算法如 `SHA-256`

#### 8.1.4 业务级对比

这是 tiny-platform 必须保留的一层，至少包括：

- 关键控制面查询结果一致
- 关键租户侧页面或 API 查询结果一致
- 菜单树、按钮权限、endpoint requirement 的行为结果一致
- 权限拒绝路径、跨租户拒绝路径仍然正确

### 8.2 通过标准

必须预先定义“什么算通过”。

建议最低标准：

- 主数据、权限数据、菜单配置、平台模板数据必须 `100%` 一致
- 跨租户误读误写容忍度为 `0`
- 权限拒绝路径必须与升级前后一致或按新设计更收紧
- 历史日志、审计日志若允许不回填，必须在方案里明确写出非目标范围

### 8.3 推荐对比清单

每次高风险迁移建议至少比对以下对象：

- `tenant`
- `tenant_user`
- `role_assignment`
- `role_permission`
- `permission`
- `menu` 与 `menu_permission_requirement`
- `api_endpoint` 与 `api_endpoint_permission_requirement`
- 平台模板记录与目标租户副本
- 关键业务表的 `tenant_id`、主键、业务编码、状态字段、审计字段

---

## 9. 数据迁移设计

### 9.1 迁移类型

tiny-platform 支持的迁移类型建议分为：

1. 结构迁移  
   由 Liquibase 驱动，负责 schema、索引、约束、种子、回填 SQL。

2. 业务数据回填  
   负责补齐历史字段、修复兼容数据、迁移旧模型到新模型。

3. 配置与模板迁移  
   负责平台模板、菜单、权限 requirement、endpoint seed 等治理数据迁移。

4. 运行时失效迁移  
   负责通过版本信号、token 安全状态等方式使旧快照失效，而不是直接依赖人工清缓存。

### 9.2 迁移流程

#### 9.2.1 分析阶段

必须明确：

- 影响哪些表、哪些租户、哪些控制面
- 哪些数据是真相源，哪些可重建
- 是否影响平台模板与租户副本
- 是否影响 token / session 权限快照
- 是否涉及旧模型到新模型的兼容收口

#### 9.2.2 设计阶段

必须输出：

- 字段映射或关系映射
- 租户维度映射规则
- 差异校验规则
- 回滚路径
- 非目标范围
- 风险与默认策略

#### 9.2.3 执行阶段

执行原则：

- 结构迁移优先通过 Liquibase
- 同一 Maven 模块的编译/测试/打包避免并发污染
- 大表按租户、按时间窗口或按主键范围分批
- 所有异常记录必须可追踪到批次、对象、原因
- 禁止用静默过滤非法数据的方式制造“部分成功”

#### 9.2.4 验证阶段

至少验证：

- schema 升级成功
- 关键表对比通过
- 关键业务结果通过
- 跨租户拒绝路径通过
- 权限链与菜单链行为不漂移
- 审计链仍然可查询、可导出

### 9.3 切换与回滚

迁移方案必须写清：

- 是否需要停机窗口
- 是否需要双写或双跑
- 增量追平的终止点
- 回滚触发条件
- 回滚是回结构、回数据、回流量，还是组合执行
- 回滚后如何处理增量补偿和版本恢复

---

## 10. 历史版本管理策略

### 10.1 不是所有数据都要做全版本

tiny-platform 不建议把所有表都做逐行全历史版本。应按类型区分：

| 数据类型 | 建议策略 |
| --- | --- |
| 平台模板、菜单配置、权限 requirement | 快照 + 变更审计 |
| 主数据 | 有效期模型或审计日志 |
| 交易/运行类大表 | 审计 + 归档，不强制逐行快照 |
| 日志与审计表 | 保留周期管理，不做重复版本化 |
| 缓存与派生物 | 不做长期版本化，只保留失效与重建机制 |

### 10.2 推荐版本模式

#### 10.2.1 快照模式

适用于：

- 平台模板
- 菜单与权限配置
- 重要平台治理配置

记录内容建议包括：

- snapshot id
- version id
- tenant scope
- snapshot source
- snapshot time
- operator
- summary / diff sample

#### 10.2.2 有效期模式

适用于：

- 主数据历史状态
- 某些可追溯配置

字段建议：

- `version_id`
- `valid_from`
- `valid_to`
- `is_current`

#### 10.2.3 审计事件模式

适用于：

- 租户治理动作
- 权限分配
- 认证与安全状态变化
- 平台模板差异观测
- 数据修复动作

### 10.3 版本与审计关系

版本不是审计的替代物，审计也不是版本的替代物。

建议区分：

- 版本：用于恢复、回溯、重建某个状态
- 审计：用于回答“谁在何时因为什么做了什么”
- 差异报告：用于回答“这次迁移或升级到底改变了什么”

---

## 11. 分阶段实施计划

### 11.1 第一阶段：结构与真相源收口

目标：

- 统一确认结构真相源、授权真相源、平台模板真相源
- 清理“历史兼容字段被继续当主链”的漂移
- 补齐与 `tenant_id`、唯一约束、索引、seed 数据有关的缺口

完成标准：

- 相关 changeset 通过真实 `SpringLiquibase` / 应用启动验证
- 关键表结构、约束、索引可在 fresh DB 与 existing DB 路径解释清楚
- 真相源与派生物边界有对应文档证据

### 11.2 第二阶段：运行时版本与失效机制收口

目标：

- 菜单、权限、token 安全状态的版本信号与运行时快照保持一致
- 减少“数据库已变更，但旧 token / 旧菜单 / 旧 session 仍继续误用”的风险

完成标准：

- 菜单配置变更能触发 `MENU_CONFIG` 失效链
- 权限快照变化能触发 `permissionsVersion` 更新并与权威快照一致
- 安全状态变更能通过 `tokenSecurityVersion` / `tokenNotBefore` 强制失效旧凭证

### 11.3 第三阶段：迁移、比对与差异报告标准化

目标：

- 建立结构级、表级、行级、业务级四层对比标准
- 输出固定格式的差异报告和迁移记录
- 支持按租户维度执行与验收

完成标准：

- 高风险迁移至少有一套可重复执行的对比脚本或 SQL
- 差异报告能区分“可接受差异”和“阻断差异”
- 跨租户误用路径有明确反向断言

### 11.4 第四阶段：平台模板与租户副本治理增强

目标：

- 平台模板、租户副本、差异观测、快照留存形成治理闭环
- 继续坚持“无自动跟随、无静默覆盖、无无审计重建”

完成标准：

- 模板与副本差异可观测、可审计
- 单租户修复具备前置备份、审批和证据链
- 平台域与租户域边界在迁移和回滚中不漂移

---

## 12. 验收标准

### 12.1 必须满足

以下条件全部满足，才可认为迁移或升级闭合：

- 结构版本可追踪
- 迁移任务可追踪
- 关键真相源数据对比通过
- 关键业务结果对比通过
- 多租户隔离未破坏
- 平台域与租户域边界未漂移
- token / session / 菜单运行时失效链路未失效
- 回滚路径明确且可执行
- 审计与证据链完整

### 12.2 不得用以下方式冒充“完成”

以下情况都不能视为完成：

- 只跑了 unit test，没有做真实 Liquibase / 启动验证
- 只验证正向 happy path，没有验证拒绝路径和跨租户边界
- 只看表结构通过，没有做业务级结果校验
- 只刷新版本字段，但权威快照没有同步更新
- 只在本地已有脏库上通过，没有在 fresh DB 路径验证

---

## 13. 验证命令

### 13.1 默认本地验证入口

tiny-platform 本地 AI 验证默认入口应优先使用：

```bash
bash tiny-oauth-server/scripts/verify-platform-local-dev-stack.sh
```

适用场景：

- 涉及后端 + 前端联动验证
- 需要确认本地 dev stack 是否完整可用
- 需要跑通默认自愈式验证链

结果解释：

- `exit 0`：前后端 dev stack 就绪
- `exit 1`：前置满足，但某个服务启动或校验失败
- `exit 2`：本机环境前置未满足，例如缺少 `DB_PASSWORD`、`npm`、`mysql`

### 13.2 后端 / 数据库专用降级入口

若明确不需要前端联动，可降级使用：

```bash
DB_PASSWORD='…' bash tiny-oauth-server/scripts/verify-platform-dev-bootstrap.sh
```

适用场景：

- 只验证后端、数据库、模板、登录链、Liquibase 启动路径
- 不需要 Vite 前端联动

注意：

- `exit 2` 表示环境前置未满足，不得记为代码回归失败
- 平台租户 code 需遵循当前显式配置口径，不能依赖隐式 `default`

### 13.3 纯 Maven 顺序门禁

若只是纯编译或定向测试门禁，可使用：

```bash
bash tiny-oauth-server/scripts/mvn-tiny-oauth-server-gate-sequential.sh
```

如需先清理再执行：

```bash
GATE_CLEAN_FIRST=1 bash tiny-oauth-server/scripts/mvn-tiny-oauth-server-gate-sequential.sh
```

注意：

- 同一 `tiny-oauth-server` 模块禁止并发 `compile` / `test`
- 若需要可信的 JaCoCo 或避免 target 污染，可改用 `mvn clean test`

### 13.4 真实 Liquibase / 应用启动验证

如果任务触及以下任一内容：

- `db/changelog/**`
- `db.changelog-master.yaml`
- `api_endpoint` / `menu_permission_requirement` / `role_permission` 回填
- 权限 seed、菜单 seed、DDL、唯一键、nullable、索引

则完成条件必须包含至少一次真实 `SpringLiquibase` 执行路径验证。推荐顺序：

1. `bash tiny-oauth-server/scripts/verify-platform-local-dev-stack.sh`
2. 若明确不需要前端联动：`DB_PASSWORD='…' bash tiny-oauth-server/scripts/verify-platform-dev-bootstrap.sh`
3. 若脚本不适用，可直接启动 `OauthServerApplication` 或等价入口，让 `SpringLiquibase` 真正跑过

交付时应明确写清：

- 执行命令
- existing DB 还是 fresh DB 路径
- 是“changeset 已执行并成功越过 Liquibase”还是“未执行 / 被环境阻塞”

---

## 14. 风险与默认策略

### 14.1 主要风险

- 历史兼容字段被误当成新主链继续写入
- 只做结构迁移，不做运行时版本失效，导致旧 token / 旧菜单继续误用
- 平台模板与租户副本边界不清，造成跨租户批量漂移
- 大表回填或比对缺少租户维度，隐藏串租户风险
- 对已执行 changeset 直接改正文，导致 checksum 漂移和环境不一致

### 14.2 默认策略

若任务未特别说明，默认采用：

- 结构变更走 Liquibase
- 业务数据按租户维度迁移和验证
- 高风险配置变更保留快照与差异证据
- 权限与安全状态变更通过运行时版本信号失效旧快照
- 无法自动验证时明确标注“阻塞 / 未验证”，不写“已完成”

---

## 15. 数据管理功能清单

### 15.1 功能定位

本方案中的“数据管理”不应被实现为可任意编辑数据库表的通用后台，而应定位为：

> 数据治理与迁移工作台。

该工作台面向两类场景：

1. 本库接入治理  
   对 tiny-platform 当前库或接入库进行资产识别、结构扫描、质量体检、租户边界识别和治理基线建立。

2. 迁移治理  
   在已有治理基线之上执行映射、对比、dry-run、迁移、修复、回滚和审计留痕。

两类场景不冲突。接入治理解决“先摸清家底”，迁移治理解决“如何安全搬迁、修复或升级”。二者应共用同一套数据资产、规则、快照、对比、任务和审计模型。

### 15.2 推荐功能模块

数据治理与迁移工作台建议至少拆为以下模块：

| 模块 | 主要能力 | 默认边界 |
| --- | --- | --- |
| 数据资产登记 | 数据源登记、schema 扫描、表分类、真相源 / 派生物标记、`tenant_id` 语义标记 | 初期以只读识别为主 |
| 接入体检 | 表结构检查、行数统计、按租户统计、空值 / 重复 / 唯一性检查、跨租户脏数据检查 | 不直接修复数据 |
| 数据质量规则 | 内置租户隔离、授权主链、菜单 requirement、平台模板、副本差异规则；支持阻断 / 警告 / 提示级别 | 豁免必须有原因和期限 |
| 数据对比 | 本库当前态 vs 快照、源库 vs 目标库、平台模板 vs 租户副本、租户间结构性差异 | 对比结果是治理证据，不直接代表可自动覆盖 |
| 迁移任务管理 | 批次号、字段映射、租户映射、dry-run、分批执行、失败记录、幂等重试、回滚方案 | 写入型任务默认必须先 dry-run，再审批，再执行；仅明确的紧急例外才允许跳过 dry-run，且必须额外记录原因、风险接受和回滚说明 |
| 版本与快照 | schema / changeset 状态、平台模板快照、菜单配置快照、权限 requirement 快照、迁移前后快照 | 快照不替代审计 |
| 运行时失效联动 | `MENU_CONFIG`、`permissionsVersion`、`tokenSecurityVersion`、`tokenNotBefore` 联动检查 | 不把版本字段当业务数据修复工具 |
| 审计与审批 | 创建、审批、执行、回滚、豁免、差异确认全链路记录 | 高风险修复不得无审计执行 |

### 15.3 建议优先级

实施优先级建议如下：

1. P0：本库治理基线  
   支持当前 tiny-platform 数据库的只读资产登记、质量扫描、租户维度统计、权限 / 菜单 / 模板主链体检和报告导出。

2. P1：同类型库只读对比  
   支持 MySQL 到 MySQL 的源库 / 目标库 / 备份库对比，先只输出差异报告，不执行写入。

3. P2：同类型库受控迁移  
   支持 MySQL 到 MySQL 的 dry-run、分批迁移、断点续跑、回滚证据、迁移后业务级验证。

4. P3：跨类型数据库接入  
   先支持 PostgreSQL / Oracle / SQL Server 等源库的只读扫描和类型映射评估，再逐步开放迁移执行。

### 15.4 权限与安全边界

数据管理能力必须遵循以下边界：

- 默认只读，写入能力必须按任务、租户、数据类型和审批状态显式授权
- 不提供绕过业务服务、权限模型和租户边界的任意表编辑入口
- 平台域数据与租户域数据在权限、脚本、审批和审计中显式分流
- 高风险治理写操作默认要求 `platform scope`，并记录明确的 `reason` / `ticketId` / 操作人 / 审批人与审计事件
- 修复、回填、重建类任务必须保留变更前摘要、变更后摘要、批次号和回滚说明
- 涉及权限、菜单、token/session 运行态的变更，必须同步检查运行时失效链路

---

## 16. 本库接入治理与迁移治理的关系

### 16.1 本库接入治理

本库接入治理用于建立当前数据状态的可信基线。典型输入包括：

- 当前 tiny-platform dev / staging / production 数据库
- 历史库或备份库
- 新接入租户的初始化数据
- 从旧系统导入前的源库快照

接入治理至少输出：

- 数据资产清单
- 表分类和真相源标记
- 租户字段和租户过滤风险清单
- 授权、菜单、平台模板、审计链路体检结果
- 阻断问题、警告问题和可接受差异

### 16.2 迁移治理

迁移治理必须建立在接入治理输出之上。典型输入包括：

- 已确认的数据资产清单
- 字段映射和租户映射
- 迁移前快照
- dry-run 差异报告
- 审批记录和回滚方案

迁移治理至少输出：

- 迁移批次记录
- 执行日志和失败样本
- 迁移后差异报告
- 业务级验证结果
- 运行时失效检查结果
- 回滚或补偿记录

### 16.3 不矛盾的原因

本库接入治理与迁移治理不是两个互斥方向，而是同一闭环中的前后阶段：

1. 接入治理确认“当前有什么、风险在哪里、哪些是真相源”
2. 迁移治理确认“如何改、改了什么、如何证明没破坏边界”
3. 版本与审计贯穿两者，保证每次接入、比对、迁移、修复都可回溯

因此，产品实现时不应拆成两套孤立系统，而应共用数据资产、规则、快照、对比、任务、审计和权限模型。

---

## 17. 数据库类型适配成本

### 17.1 成本判断

数据库类型管理成本客观存在，但它不是数据治理方向的反证，而是适配层成本。

成本随场景递增：

| 场景 | 数据库类型成本 | 建议策略 |
| --- | --- | --- |
| 只管理 tiny-platform 当前本库 | 低 | 直接围绕当前 MySQL / Liquibase 口径实现 |
| 本库接入体检 | 中低 | 重点做 schema 扫描、规则识别、租户字段识别 |
| 同类型库迁移，例如 MySQL -> MySQL | 中 | 处理字符集、时区、自增、约束、批量导入和对比误差 |
| 跨类型迁移，例如 Oracle / PostgreSQL / SQL Server -> MySQL | 高 | 需要方言、类型映射、SQL 改写和等价性校验策略 |

### 17.2 主要适配点

数据库适配至少涉及：

- 元数据读取：表、列、索引、唯一约束、外键、注释、默认值
- 字段类型映射：字符串、文本、JSON、日期时间、数值、布尔、二进制
- 主键与自增策略：`AUTO_INCREMENT`、sequence、identity、雪花 ID 或业务主键
- SQL 方言：分页、upsert、hash、字符串拼接、日期函数、保留字转义
- 一致性对比：空值、时间精度、字符集、collation、大小写敏感、排序稳定性
- 执行策略：锁表风险、事务大小、批量提交、断点续跑、幂等写入、失败重试

### 17.3 推荐技术抽象

为了避免后续跨库能力反复重写，建议从一开始预留以下抽象：

- `DatabaseDialect`：描述数据库方言能力、保留字、分页、upsert、hash 等差异
- `MetadataAdapter`：读取 schema、表、列、索引、约束、注释和默认值
- `TypeMapping`：描述源库类型到目标库类型的映射和风险
- `DataComparator`：统一结构级、表级、行级、业务级对比入口
- `MigrationExecutor`：封装 dry-run、分批执行、断点续跑、失败记录和幂等重试
- `GovernanceRule`：封装租户隔离、权限链、菜单链、平台模板等业务规则

这些抽象不要求 P0 全量实现，但接口边界应提前保留，避免本库治理能力以后无法扩展到迁移治理。它们属于 P2 / P3 阶段的预留扩展点，不代表当前仓库需要立即实现一套通用跨库框架。

### 17.4 默认落地顺序

默认不做“全数据库通吃”的大而全目标。推荐顺序是：

1. 先把 tiny-platform 本库治理做好
2. 再支持 MySQL 到 MySQL 的只读对比
3. 再支持 MySQL 到 MySQL 的受控迁移
4. 最后按真实项目需要接入 PostgreSQL / Oracle / SQL Server 等源库

跨类型数据库在进入迁移执行前，必须先经过只读扫描、类型映射评估、样本对比和风险确认。

---

## 18. 能力状态矩阵

为避免把目标态能力误读为当前已实现承诺，数据治理能力必须显式标注状态。

状态定义：

- 当前已落地：仓库已有对应运行时能力或脚本入口，但具体任务仍需按变更范围验证
- 部分落地：已有基础能力或专项链路，但尚未形成统一工作台能力
- 目标态：设计方向明确，当前不代表已经实现
- 非本期范围：本方案明确不在 P0 / P1 承诺内

| 能力 | 状态 | 当前依据 | 备注 |
| --- | --- | --- | --- |
| Liquibase 结构版本管理 | 当前已落地 | `db/changelog/**`、`db.changelog-master.yaml` | 涉及 migration 的任务仍需真实 `SpringLiquibase` / 启动验证 |
| 本地默认验证入口 | 当前已落地 | `verify-platform-local-dev-stack.sh`、`verify-platform-dev-bootstrap.sh`、`mvn-tiny-oauth-server-gate-sequential.sh` | 需按 exit code 区分代码失败与环境前置缺失 |
| 菜单运行时配置版本 | 部分落地 | `runtime_version_signal.MENU_CONFIG`、`/sys/menus/tree` ETag 口径 | 需要随菜单控制面变更持续补齐验证 |
| 授权快照版本 | 部分落地 | `permissionsVersion` 与授权主链文档口径 | 必须避免只刷新版本而不刷新权威快照 |
| token/session 安全失效 | 部分落地 | `tokenSecurityVersion` / `tokenNotBefore` 口径 | 用户禁用、删除、密码和 MFA 变化不得复用 `permissionsVersion` 表达 |
| 数据资产登记 | 目标态 | 本文第 15 节 | P0 建议先只读扫描当前本库 |
| 接入体检与质量报告 | 目标态 | 本文第 15、16 节 | P0 优先实现只读体检与报告导出 |
| 同类型库只读对比 | 目标态 | 本文 P1 | 先支持 MySQL -> MySQL，不执行写入 |
| 同类型库受控迁移 | 目标态 | 本文 P2 | 必须经过 dry-run、审批、批次记录和回滚说明 |
| 跨类型数据库接入 | 目标态 | 本文 P3 | 先只读扫描与类型映射评估 |
| 任意 SQL 编辑器 | 非本期范围 | 安全边界约束 | 不提供绕过业务服务、权限模型和租户边界的任意写入口 |
| 跨库自动修复平台 | 非本期范围 | 本文第 17 节 | 不承诺全数据库通吃或自动跨库修复 |

---

## 19. 角色与职责矩阵

数据治理任务必须区分发起、审批、执行、回滚、豁免和查看职责，避免同一角色无约束地完成高风险闭环。

| 角色 | 可发起 | 可审批 | 可执行写操作 | 可回滚 | 可豁免 | 可查看报告 | 约束 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| platform scope 操作者 | 是 | 视权限而定 | 仅限已审批任务 | 仅限已审批任务 | 否 | 是 | 高风险写操作需记录 `reason` / `ticketId` / 审计事件 |
| 租户治理只读查看者 | 可发起只读体检 | 否 | 否 | 否 | 否 | 仅限授权租户范围 | 不得查看其他租户明细或敏感原值 |
| DBA / 运维执行者 | 可发起执行申请 | 否 | 可按审批执行 | 可按审批执行 | 否 | 是 | 执行动作必须绑定批次、脚本版本和窗口 |
| reviewer / 审批人 | 否 | 是 | 否 | 可审批回滚 | 可审批豁免 | 是 | 不应直接替代执行人完成写操作 |
| 安全 / 审计查看者 | 否 | 可参与审批 | 否 | 否 | 可参与豁免评审 | 是 | 重点查看敏感数据、跨租户、权限和审计链路 |
| 自动化任务账号 | 否 | 否 | 仅限预授权脚本 | 仅限预授权脚本 | 否 | 仅限任务输出 | 必须最小权限、可追踪、不可共享人工账号 |

高风险治理写操作至少应满足：

- 运行于明确的 `platform scope`
- 有 `reason` / `ticketId`
- 有审批人与执行人分离
- 有执行前快照或摘要
- 有执行后差异报告
- 有回滚或补偿说明

---

## 20. 标准产物清单

每次重要接入、对比、迁移或修复任务，都应产出可审计材料。产物可由系统生成，也可由任务卡引用，但不得只停留在口头说明。

| 产物 | 适用场景 | 最小字段 |
| --- | --- | --- |
| 数据资产扫描报告 | 接入治理、P0 体检 | 数据源、环境、schema、表清单、表分类、`tenant_id` 识别结果、扫描时间、扫描人或任务账号 |
| 基线体检报告 | 接入治理、迁移前检查 | 规则版本、检查项、阻断项、警告项、豁免项、影响租户、样本摘要 |
| dry-run 差异报告 | 迁移、修复、模板重建 | 批次号、源对象、目标对象、差异类型、差异数量、样本、是否阻断 |
| 执行批次记录 | 写入型迁移或修复 | 批次号、脚本版本、执行人、审批人、开始 / 结束时间、影响表、影响租户、成功数、失败数 |
| 失败样本清单 | 迁移或修复失败 | 批次号、对象主键或业务键、租户、失败原因、重试状态、最终处理方式 |
| 回滚或补偿记录 | 回滚、部分失败补偿 | 触发条件、回滚范围、回滚脚本版本、执行结果、剩余风险 |
| 豁免记录 | 阻断项暂缓处理 | 豁免原因、`ticketId`、影响范围、有效期、审批人、复核时间 |
| 运行时失效检查记录 | 权限、菜单、安全状态相关变更 | `MENU_CONFIG`、`permissionsVersion`、`tokenSecurityVersion` / `tokenNotBefore` 检查结果 |
| 审计导出记录 | 审计、合规、复盘 | 导出人、导出范围、脱敏策略、文件摘要、保留期限 |

产物命名建议包含：

- 环境
- 任务类型
- 批次号
- 日期时间
- 目标租户或 `platform`

---

## 21. 验证矩阵

验证不能只写命令，还必须说明验证目标、数据路径和失败语义。

| 验证维度 | 本地 | CI | Nightly / 专项 | 通过标准 |
| --- | --- | --- | --- | --- |
| fresh DB 启动路径 | 涉及 schema / seed 时必须验证 | migration smoke 可覆盖 | 建议定期覆盖 | `SpringLiquibase` 全量执行成功，初始化数据闭合 |
| existing DB 升级路径 | 涉及历史回填时必须验证 | 可按任务选择 | 建议覆盖关键迁移 | 已执行 changeset 不改正文，尾部 changeset 可重复解释 |
| 正向业务路径 | 变更涉及控制面时验证 | 定向测试覆盖 | 关键流程巡检 | 控制面查询、菜单、权限结果符合预期 |
| 拒绝路径 | 权限 / 租户 / 安全相关必须验证 | 应纳入定向测试 | 建议定期巡检 | 未授权、旧 token、错误 scope、禁用用户按预期拒绝 |
| 跨租户误用路径 | 迁移、修复、数据对比必须验证 | 关键链路纳入测试 | 建议定期巡检 | 跨租户误读、误写、误恢复容忍度为 `0` |
| 平台域 / 租户域分流 | 平台模板或治理数据变更必须验证 | 可用定向测试覆盖 | 建议专项巡检 | `tenant_id IS NULL` 平台语义不泄漏成租户业务数据 |
| 运行时版本失效 | 菜单、权限、安全状态相关必须验证 | 定向测试覆盖 | 建议专项巡检 | 旧菜单、旧权限快照、旧 token 不继续误用 |
| 差异报告生成 | 对比、迁移、修复必须验证 | 可静态校验格式 | 建议抽样复核 | 报告能区分阻断差异、可接受差异和豁免差异 |

推荐命令仍以第 13 节为准。交付说明必须写清：

- 使用 fresh DB 还是 existing DB
- 是否经过真实 `SpringLiquibase`
- 是否覆盖拒绝路径
- 是否覆盖跨租户误用路径
- 若未执行，原因是环境前置缺失、非本轮范围还是脚本不可用

---

## 22. 敏感数据与快照处理策略

数据治理工作台一旦支持扫描、快照、对比和导出，就必须显式处理敏感数据。

### 22.1 默认脱敏规则

以下数据默认不得在报告、快照导出或差异样本中保留明文：

- 密码、密码摘要、密码重置 token
- TOTP secret、恢复码、MFA 绑定密钥
- access token、refresh token、authorization code、session id
- 私钥、客户端密钥、JWK 私有材料
- 邮箱、手机号等个人身份信息，除非任务明确需要且审批通过
- 第三方系统凭证、数据库连接串、云资源密钥

### 22.2 快照与导出要求

- 只读体检报告优先保留摘要、计数、hash 和样本脱敏值
- 需要保留原值的快照必须记录审批、用途、保留周期和下载人
- 导出文件应记录文件摘要、生成时间、生成任务和保留期限
- 跨环境传输快照必须经过脱敏或加密
- 过期快照、导出文件和临时中间表必须有清理策略

### 22.2.1 高敏感访问附加要求

- 对含敏感原值的快照查看、原值导出、高敏感差异样本查看，默认要求 `platform scope + 对应权限 + reason / ticketId`
- 对 `DECOMMISSIONED` 状态下的高敏感材料访问，默认还应要求 MFA 已完成
- 不得因为治理任务需要而放宽现有租户生命周期、权限或审计边界

### 22.3 保留周期

保留周期由具体环境和合规要求决定，但默认原则是：

- 临时中间数据短期保留，任务结束后清理
- 审计记录按平台审计策略保留
- 差异报告保留到任务复盘和回滚窗口结束后再归档
- 含敏感原值的材料必须最短可用周期保留

---

## 23. 租户生命周期联动边界

数据治理能力必须尊重租户生命周期状态，不能因为治理任务绕过租户状态约束。

以下“允许动作”仅表示在现有租户生命周期规则下、由 `activeScopeType=PLATFORM` 的显式治理白名单可执行的动作；`tenant scope` 在 `FROZEN` / `DECOMMISSIONED` 下不保留治理例外。

| 租户状态 | 允许动作 | 禁止或限制动作 | 说明 |
| --- | --- | --- | --- |
| ACTIVE | 体检、对比、dry-run、经审批修复、迁移、审计导出 | 未审批批量写入、跨租户覆盖 | 正常治理状态 |
| FROZEN | 平台作用域下的体检、差异查看、审计导出、必要的只读对比 | `tenant scope` 全部治理例外；默认禁止写入型修复和迁移 | 仅允许显式白名单内的治理只读动作，如需进一步处置，必须走 platform scope 审批和专项说明 |
| DECOMMISSIONED | 平台作用域下的归档核验、只读扫描、审计导出、销毁前复核 | `tenant scope` 全部治理例外；默认禁止业务写入、模板重建、租户副本修复 | 重点保证归档、留痕、销毁边界，不得恢复成普通治理写链 |
| 平台模板域 | 模板体检、模板快照、模板差异观测、经审批修复 | 自动强推覆盖所有租户副本 | `tenant_id IS NULL` 平台语义必须显式处理 |

租户状态变化本身也应纳入审计，并与数据治理任务记录互相引用。

---

## 24. 性能、执行窗口与非目标清单

### 24.1 性能与执行窗口

大表扫描、对比、迁移和修复必须在方案中说明执行策略：

- 批大小和分页方式
- 单批事务大小
- 是否可能锁表
- 是否需要夜间窗口或低峰窗口
- 是否支持断点续跑
- 是否具备幂等重试
- 失败后是跳过、暂停、回滚还是补偿
- 是否会触发运行时缓存失效或用户重新登录

高风险任务默认先 dry-run，再小租户 / 小批次试跑，最后扩大范围。

### 24.2 非目标清单

P0 / P1 默认不做以下能力：

- 不做任意 SQL 编辑器
- 不做绕过业务服务的任意表写入
- 不做跨类型数据库自动修复
- 不做全数据库通吃的迁移平台
- 不做平台模板到所有租户副本的自动强推覆盖
- 不做所有表逐行全量历史版本
- 不把浏览器缓存、运行时缓存或中间表提升为长期真相源
- 不把目标态抽象当成当前必须立即实现的通用框架

---

## 25. P0 / P1 最小落地任务拆解

本节用于把目标态能力拆成可评审、可排期、可验收的最小任务。任务名称为建议口径，不代表当前仓库已经实现。

### 25.1 P0：本库治理基线

P0 只面向 tiny-platform 当前本库，默认只读，不执行迁移写入。

| 任务 | 目标 | 产物 | 完成标准 |
| --- | --- | --- | --- |
| P0-01 数据资产只读扫描 | 识别 schema、表、字段、索引、约束和 `tenant_id` 语义 | 数据资产扫描报告 | 能区分平台治理表、租户业务表、安全审计表、缓存派生表 |
| P0-02 内置治理规则集 | 固化租户隔离、授权主链、菜单 requirement、平台模板基础检查 | 基线体检报告 | 阻断 / 警告 / 提示级别可解释 |
| P0-03 标准报告格式 | 统一体检、差异、豁免、审计导出格式 | 报告模板或 JSON schema | 每个报告包含环境、批次、时间、执行主体、规则版本 |
| P0-04 权限与审计防线 | 给只读扫描、报告查看、豁免、导出建立权限边界 | 权限标识与审计事件清单 | 高风险动作必须 platform scope + reason / ticketId |
| P0-05 本地验证与文档闭环 | 将 P0 能力纳入现有验证说明 | 验证记录 | 不触及 DB 写入时无需真实迁移验证，但需说明未执行原因 |

### 25.2 P1：MySQL 同类型库只读对比

P1 支持 MySQL -> MySQL 的只读对比，不执行写入、不做自动修复。

| 任务 | 目标 | 产物 | 完成标准 |
| --- | --- | --- | --- |
| P1-01 MySQL 元数据适配 | 读取源库 / 目标库表、列、索引、约束、注释 | 元数据对比报告 | 能解释 schema 差异和阻断项 |
| P1-02 表级与租户级统计 | 输出总行数、按租户行数、关键字段统计 | 表级对比报告 | 能定位租户维度数量漂移 |
| P1-03 行级摘要对比 | 支持按业务键或主键生成摘要对比 | 行级差异报告 | 能输出差异样本且默认脱敏 |
| P1-04 业务级只读校验 | 对菜单、权限、平台模板等关键对象做业务口径对比 | 业务级差异报告 | 能区分可接受差异、阻断差异、需豁免差异 |
| P1-05 对比任务审计 | 对每次对比记录发起人、数据源、范围和报告摘要 | 对比任务审计记录 | 报告可追踪到任务和数据源 |

### 25.3 P2 / P3 暂缓边界

P2 / P3 暂不作为 P0 / P1 的完成条件：

- P2 同类型库受控迁移：需要 dry-run、审批、批次写入、断点续跑、回滚和真实业务验证
- P3 跨类型数据库接入：需要数据库方言、类型映射、SQL 改写、时间精度和字符集等专项评估

---

## 26. 逻辑数据模型建议

以下是治理工作台的逻辑模型建议，用于统一后续任务卡和设计讨论。它不是当前立即新增 DDL 的要求；若进入实现，仍需按 `db/changelog/**` 规则补 Liquibase changeset 和真实启动验证。

| 逻辑对象 | 目的 | 关键字段建议 |
| --- | --- | --- |
| `data_source_registry` | 登记本库、源库、目标库、备份库 | source_id、source_type、env、db_type、jdbc_alias、owner、enabled、created_at |
| `data_asset_snapshot` | 保存某次资产扫描结果摘要 | snapshot_id、source_id、schema_name、rule_version、table_count、tenant_table_count、created_at |
| `data_asset_table` | 表级资产描述 | snapshot_id、table_name、category、has_tenant_id、truth_source_type、row_count_digest |
| `governance_task` | 统一体检、对比、迁移、修复任务 | task_id、task_type、status、scope_type、tenant_id、reason、ticket_id、created_by、approved_by |
| `governance_task_artifact` | 记录报告、快照、导出物 | artifact_id、task_id、artifact_type、storage_ref、sha256、masked、retention_until |
| `governance_rule_result` | 记录规则执行结果 | task_id、rule_id、severity、status、affected_table、affected_tenant_id、sample_digest |
| `governance_diff_report` | 记录差异摘要 | report_id、task_id、source_ref、target_ref、diff_type、blocking_count、warning_count |
| `governance_waiver` | 记录豁免 | waiver_id、task_id、rule_id、reason、ticket_id、expires_at、approved_by |
| `migration_batch` | 记录迁移 / 修复批次 | batch_id、task_id、script_version、status、started_at、finished_at、success_count、failure_count |
| `migration_failure_sample` | 记录失败样本 | batch_id、table_name、business_key、tenant_id、failure_reason、retry_status |
| `governance_audit_event` | 记录治理审计事件 | event_id、event_type、actor_id、scope_type、tenant_id、task_id、reason、ticket_id、created_at |

逻辑模型原则：

- 报告与快照默认保存摘要和脱敏样本，不默认保存敏感原值
- 任务、报告、豁免、批次、审计事件必须能互相追踪
- 写入型任务必须能追踪到审批人、执行人、脚本版本和回滚记录
- 租户维度字段必须显式建模，不能依赖描述文本表达

---

## 27. 治理任务状态机

数据治理任务建议采用显式状态机，避免任务在审批、执行、回滚、豁免之间口径漂移。

### 27.1 通用状态

| 状态 | 含义 | 可进入条件 | 可退出到 |
| --- | --- | --- | --- |
| `DRAFT` | 草稿 | 创建任务但未提交 | `SUBMITTED`、`CANCELLED` |
| `SUBMITTED` | 已提交 | 发起人提交，填写范围、原因、ticketId | `APPROVED`、`REJECTED`、`CANCELLED` |
| `APPROVED` | 已审批 | 审批人确认范围、风险、窗口和回滚说明 | `DRY_RUN_RUNNING`、`CANCELLED` |
| `DRY_RUN_RUNNING` | dry-run 中 | 只读预执行或差异生成 | `DRY_RUN_PASSED`、`DRY_RUN_FAILED` |
| `DRY_RUN_PASSED` | dry-run 通过 | 阻断项为 0 或已有有效豁免 | `EXECUTION_RUNNING`、`CANCELLED` |
| `DRY_RUN_FAILED` | dry-run 失败 | 存在阻断差异或执行异常 | `SUBMITTED`、`CANCELLED` |
| `EXECUTION_RUNNING` | 执行中 | 已审批且满足执行窗口 | `EXECUTION_SUCCEEDED`、`EXECUTION_FAILED`、`ROLLBACK_RUNNING` |
| `EXECUTION_SUCCEEDED` | 执行成功 | 执行完成且验证通过 | `CLOSED`、`ROLLBACK_RUNNING` |
| `EXECUTION_FAILED` | 执行失败 | 失败样本或验证失败 | `ROLLBACK_RUNNING`、`SUBMITTED`、`CLOSED_WITH_RISK` |
| `ROLLBACK_RUNNING` | 回滚中 | 触发回滚条件 | `ROLLED_BACK`、`CLOSED_WITH_RISK` |
| `ROLLED_BACK` | 已回滚 | 回滚或补偿完成 | `CLOSED` |
| `REJECTED` | 已拒绝 | 审批不通过 | `CLOSED` |
| `CANCELLED` | 已取消 | 发起人或审批人取消 | `CLOSED` |
| `CLOSED` | 已关闭 | 产物完整、验证和审计完成 | 终态 |
| `CLOSED_WITH_RISK` | 带风险关闭 | 无法完全修复但有明确风险接受 | 终态，必须有关联豁免 |

### 27.2 状态机约束

- 写入型任务不得从 `SUBMITTED` 直接进入 `EXECUTION_RUNNING`
- 写入型任务默认必须先经过 `DRY_RUN_PASSED`，方可进入 `EXECUTION_RUNNING`
- 若因紧急处置需要跳过 dry-run，必须显式标记为例外任务，并补充审批意见、`reason` / `ticketId`、风险接受说明和回滚方案
- 存在阻断差异时不得进入 `EXECUTION_RUNNING`，除非存在有效豁免且审批通过
- `CLOSED_WITH_RISK` 必须关联豁免记录、剩余风险和复核时间
- `ROLLBACK_RUNNING` 后必须产出回滚或补偿记录
- 只读体检任务可跳过 `APPROVED`，但报告导出和敏感样本查看仍需权限控制

---

## 28. 权限标识建议

权限标识必须遵循现有权限标识规范，以下仅为数据治理工作台的建议命名空间。实际落地前需与 `docs/TINY_PLATFORM_PERMISSION_IDENTIFIER_SPEC.md` 对齐。

| 能力 | 建议权限标识 | 说明 |
| --- | --- | --- |
| 查看数据治理概览 | `platform:data-governance:view` | 查看工作台概览与任务列表 |
| 发起只读资产扫描 | `platform:data-governance:scan-asset` | 不读取敏感原值 |
| 查看资产扫描报告 | `platform:data-governance:view-asset` | 按 scope / tenant 限制 |
| 发起基线体检 | `platform:data-governance:check-baseline` | 只读规则检查 |
| 查看体检报告 | `platform:data-governance:view-baseline` | 默认脱敏 |
| 发起只读对比 | `platform:data-governance:create-diff` | P1 能力 |
| 查看差异报告 | `platform:data-governance:view-diff` | 敏感样本需额外权限 |
| 查看敏感样本 | `platform:data-governance:view-sensitive-sample` | 高风险权限，默认不授予 |
| 发起迁移 dry-run | `platform:data-governance:dry-run-migration` | P2 前置能力 |
| 执行迁移 / 修复 | `platform:data-governance:execute-migration` | 必须审批、审计、reason / ticketId |
| 执行回滚 / 补偿 | `platform:data-governance:rollback-migration` | 必须审批或应急授权 |
| 审批治理任务 | `platform:data-governance:approve-task` | 审批人与执行人应分离 |
| 创建豁免 | `platform:data-governance:create-waiver` | 需 reason / ticketId / 有效期 |
| 审计导出 | `platform:data-governance:export-audit` | 需要导出范围和保留周期 |

权限默认策略：

- 租户治理只读查看者只授予授权租户范围内的 view 类能力
- 写入、回滚、敏感样本查看、审计导出默认只在 platform scope 下授权
- 自动化任务账号只授予具体脚本所需的最小权限，不继承人工管理员全集

---

## 29. 审计事件字典

数据治理工作台必须为关键动作提供稳定审计事件类型，便于后续查询、告警和复盘。

| 事件类型 | 触发场景 | 必要字段 |
| --- | --- | --- |
| `DATA_GOVERNANCE_TASK_CREATED` | 创建治理任务 | task_id、task_type、scope_type、tenant_id、reason、ticket_id |
| `DATA_GOVERNANCE_TASK_SUBMITTED` | 提交审批 | task_id、actor_id、risk_level |
| `DATA_GOVERNANCE_TASK_APPROVED` | 审批通过 | task_id、approved_by、approval_comment |
| `DATA_GOVERNANCE_TASK_REJECTED` | 审批拒绝 | task_id、approved_by、reject_reason |
| `DATA_ASSET_SCAN_STARTED` | 开始资产扫描 | task_id、source_id、schema_name |
| `DATA_ASSET_SCAN_COMPLETED` | 完成资产扫描 | task_id、snapshot_id、table_count、warning_count |
| `DATA_BASELINE_CHECK_COMPLETED` | 完成基线体检 | task_id、blocking_count、warning_count、waiver_count |
| `DATA_DIFF_REPORT_CREATED` | 生成差异报告 | task_id、report_id、blocking_count、warning_count |
| `DATA_GOVERNANCE_WAIVER_CREATED` | 创建豁免 | waiver_id、task_id、rule_id、reason、ticket_id、expires_at |
| `DATA_GOVERNANCE_ARTIFACT_EXPORTED` | 导出报告或快照 | artifact_id、task_id、exported_by、masked、sha256、retention_until |
| `DATA_MIGRATION_DRY_RUN_COMPLETED` | dry-run 完成 | task_id、batch_id、status、failure_count |
| `DATA_MIGRATION_EXECUTION_STARTED` | 写入型执行开始 | task_id、batch_id、executor、script_version |
| `DATA_MIGRATION_EXECUTION_COMPLETED` | 写入型执行完成 | task_id、batch_id、success_count、failure_count |
| `DATA_MIGRATION_ROLLBACK_STARTED` | 回滚开始 | task_id、batch_id、rollback_reason |
| `DATA_MIGRATION_ROLLBACK_COMPLETED` | 回滚完成 | task_id、batch_id、status、remaining_risk |
| `DATA_GOVERNANCE_TASK_CLOSED` | 关闭任务 | task_id、status、closed_by、closed_reason |

审计事件必须满足：

- 不记录密码、token、密钥、TOTP secret 等敏感原值
- 事件能关联任务、批次、报告、豁免和执行主体
- 高风险写操作事件必须包含 `reason` / `ticketId`
- 事件查询应支持按租户、任务、操作人、事件类型和时间范围过滤

---

## 30. P0 可落地 MVP 纵切

若要进入实现，建议 P0 先做一条最小闭环，而不是一次铺开完整工作台。

### 30.1 P0 MVP 范围

P0 MVP 只实现：

1. 平台 scope 下的数据治理入口
2. 当前本库只读资产扫描
3. 内置基线体检规则执行
4. 报告摘要落库与脱敏样本展示
5. 治理审计事件记录
6. 前端只读工作台页面

P0 MVP 明确不实现：

- 外部数据源连接
- MySQL -> MySQL 对比
- 迁移写入
- 回滚执行
- 跨类型数据库适配
- 任意 SQL 编辑器

### 30.2 P0 用户路径

P0 用户路径建议为：

1. 平台用户进入 `/platform/data-governance`
2. 查看最近一次资产扫描与基线体检摘要
3. 点击“发起本库扫描”
4. 系统创建 `ASSET_SCAN` 任务并执行只读扫描
5. 系统生成资产扫描报告与规则结果
6. 用户查看阻断项、警告项、脱敏样本与审计事件
7. 用户导出脱敏报告，系统记录导出审计事件

### 30.3 P0 完成标准

P0 只有同时满足以下条件，才算可交付：

- 后端接口具备 platform scope guard 和规范权限码
- 资产扫描、基线体检、报告查看、报告导出均有审计事件
- 报告样本默认脱敏，不展示密码、token、密钥、TOTP secret 等敏感原值
- 前端页面能展示任务列表、资产摘要、规则结果和报告详情
- 单元测试覆盖权限拒绝路径、非 platform scope 拒绝、敏感字段脱敏
- 若新增表和 seed，必须通过真实 `SpringLiquibase` / 应用启动验证

---

## 31. 后端落地蓝图

### 31.1 建议包结构

P0 / P1 建议按现有分层放置：

| 层 | 建议路径 | 职责 |
| --- | --- | --- |
| Controller | `tiny-oauth-server/src/main/java/com/tiny/platform/application/controller/datagovernance` | REST API、请求校验、响应 DTO |
| Access Guard | `tiny-oauth-server/src/main/java/com/tiny/platform/application/controller/datagovernance/security` | platform scope + 权限码守卫 |
| Service | `tiny-oauth-server/src/main/java/com/tiny/platform/infrastructure/datagovernance/service` | 任务编排、扫描、规则执行、报告生成 |
| Repository | `tiny-oauth-server/src/main/java/com/tiny/platform/infrastructure/datagovernance/repository` | 治理任务、报告、规则结果、审计事件读写 |
| Domain | `tiny-oauth-server/src/main/java/com/tiny/platform/infrastructure/datagovernance/domain` | 任务、报告、规则结果、状态枚举 |
| DTO | `tiny-oauth-server/src/main/java/com/tiny/platform/infrastructure/datagovernance/dto` | 内部服务 DTO 与查询对象 |
| Rule | `tiny-oauth-server/src/main/java/com/tiny/platform/infrastructure/datagovernance/rule` | 内置规则接口与规则实现 |
| Metadata | `tiny-oauth-server/src/main/java/com/tiny/platform/infrastructure/datagovernance/metadata` | P0 本库元数据读取；P1 再抽象 MySQL adapter |

命名说明：

- Java package 建议使用 `datagovernance`，避免包名中出现短横线
- API path 和前端路由可使用 `data-governance`

### 31.2 P0 API 草案

P0 API 建议全部放在平台控制面下：

| 方法 | 路径 | 权限 | 说明 |
| --- | --- | --- | --- |
| `GET` | `/platform/data-governance/summary` | `platform:data-governance:view` | 工作台摘要 |
| `GET` | `/platform/data-governance/tasks` | `platform:data-governance:view` | 任务分页 |
| `POST` | `/platform/data-governance/asset-scans` | `platform:data-governance:scan-asset` | 发起本库只读资产扫描 |
| `GET` | `/platform/data-governance/asset-scans/{taskId}` | `platform:data-governance:view-asset` | 查看资产扫描详情 |
| `POST` | `/platform/data-governance/baseline-checks` | `platform:data-governance:check-baseline` | 发起基线体检 |
| `GET` | `/platform/data-governance/baseline-checks/{taskId}` | `platform:data-governance:view-baseline` | 查看体检详情 |
| `GET` | `/platform/data-governance/tasks/{taskId}/artifacts` | `platform:data-governance:view` | 查看任务产物 |
| `GET` | `/platform/data-governance/tasks/{taskId}/audit-events` | `platform:data-governance:view` | 查看任务审计事件 |
| `GET` | `/platform/data-governance/tasks/{taskId}/export` | `platform:data-governance:export-audit` | 导出脱敏报告 |

P1 再追加：

| 方法 | 路径 | 权限 | 说明 |
| --- | --- | --- | --- |
| `POST` | `/platform/data-governance/diff-tasks` | `platform:data-governance:create-diff` | 创建只读对比任务 |
| `GET` | `/platform/data-governance/diff-tasks/{taskId}` | `platform:data-governance:view-diff` | 查看差异报告 |

### 31.3 请求与响应 DTO 最小字段

P0 请求 DTO：

| DTO | 字段 |
| --- | --- |
| `CreateAssetScanRequest` | `reason`、`ticketId`、`scopeType=PLATFORM`、`includeTables`、`excludeTables` |
| `CreateBaselineCheckRequest` | `reason`、`ticketId`、`ruleIds`、`severityThreshold` |
| `TaskQueryRequest` | `taskType`、`status`、`createdBy`、`startTime`、`endTime`、`page`、`size` |

P0 响应 DTO：

| DTO | 字段 |
| --- | --- |
| `GovernanceTaskSummaryResponse` | `taskId`、`taskType`、`status`、`scopeType`、`tenantId`、`reason`、`ticketId`、`createdBy`、`createdAt`、`finishedAt` |
| `AssetScanSummaryResponse` | `taskId`、`schemaName`、`tableCount`、`tenantTableCount`、`platformTableCount`、`riskCountBySeverity` |
| `BaselineCheckSummaryResponse` | `taskId`、`ruleVersion`、`blockingCount`、`warningCount`、`infoCount`、`waiverCount` |
| `RuleResultResponse` | `ruleId`、`severity`、`status`、`affectedTable`、`affectedTenantId`、`message`、`sampleDigest` |
| `GovernanceAuditEventResponse` | `eventType`、`actorId`、`scopeType`、`tenantId`、`taskId`、`reason`、`ticketId`、`createdAt` |

### 31.4 后端实现顺序

P0 后端建议按以下顺序实现：

1. 新增 domain enum：`GovernanceTaskType`、`GovernanceTaskStatus`、`GovernanceRuleSeverity`、`GovernanceRuleStatus`
2. 新增只读元数据读取服务，先基于当前应用 DataSource 和 `information_schema`
3. 新增内置规则接口 `GovernanceRule`
4. 新增 P0 规则实现与脱敏工具
5. 新增任务、报告、规则结果、审计事件 repository
6. 新增 service 编排：创建任务 -> 执行扫描 / 体检 -> 写报告 -> 写审计
7. 新增 controller 与 access guard
8. 新增权限 seed、菜单 seed、api_endpoint requirement
9. 补 controller / service / repository / rule 单测
10. 跑真实 `SpringLiquibase` / 启动验证

---

## 32. 前端落地蓝图

### 32.1 建议文件结构

| 类型 | 建议路径 | 职责 |
| --- | --- | --- |
| API | `tiny-oauth-server/src/main/webapp/src/api/data-governance.ts` | 封装 P0 / P1 REST API |
| 页面 | `tiny-oauth-server/src/main/webapp/src/views/platform/data-governance/DataGovernance.vue` | 工作台容器 |
| 组件 | `tiny-oauth-server/src/main/webapp/src/views/platform/data-governance/components/*` | 摘要卡、任务表、规则结果、报告详情 |
| 测试 | `tiny-oauth-server/src/main/webapp/src/views/platform/data-governance/DataGovernance.test.ts` | 页面权限、加载、错误态 |
| 权限常量 | `tiny-oauth-server/src/main/webapp/src/constants/permission.ts` | 新增数据治理权限码常量 |

### 32.2 P0 页面结构

P0 页面建议使用工作台式布局，不做营销式页面：

- 顶部：摘要指标，展示最近扫描时间、阻断项、警告项、资产表数量
- 左侧或 Tabs：`任务`、`资产扫描`、`基线体检`、`审计事件`
- 主区：任务列表与报告详情
- 操作区：发起本库扫描、发起基线体检、导出脱敏报告

P0 页面必须具备：

- 加载态
- 空状态
- 无权限状态
- 接口失败状态
- 脱敏提示，但不展示敏感原值
- 任务状态 badge
- 阻断 / 警告 / 提示分级展示

### 32.3 前端实现顺序

1. 新增 `src/api/data-governance.ts`
2. 新增权限常量
3. 新增静态路由 `/platform/data-governance`
4. 新增 `DataGovernance.vue` 页面骨架
5. 接入任务列表、摘要、详情接口
6. 接入发起扫描 / 体检动作，使用既有 idempotency 机制
7. 补页面测试和 API mock 测试
8. 补菜单 seed 后做前端菜单可见性验证

---

## 33. P0 内置规则清单

P0 内置规则必须先覆盖 tiny-platform 当前最容易影响安全和租户隔离的对象。

| 规则 ID | 严重级别 | 检查对象 | 检查内容 |
| --- | --- | --- | --- |
| `DG-TENANT-001` | BLOCKING | 租户业务表 | 核心租户业务表必须评估 `tenant_id` 语义 |
| `DG-TENANT-002` | BLOCKING | 租户业务表 | 按租户维度统计时不得出现无法归属的业务数据 |
| `DG-TENANT-003` | BLOCKING | 唯一约束 | 租户业务唯一键必须评估 `tenant_id + business_key` |
| `DG-AUTH-001` | BLOCKING | 授权主链 | `tenant_user`、`role_assignment`、`role_permission`、`permission` 主链完整 |
| `DG-AUTH-002` | WARNING | 兼容字段 | 历史兼容字段不得重新成为主读或主写链路 |
| `DG-MENU-001` | BLOCKING | 菜单 requirement | `menu_permission_requirement` 指向的权限必须存在且 enabled |
| `DG-MENU-002` | WARNING | 菜单运行时版本 | 菜单相关变更需具备 `MENU_CONFIG` 失效证据 |
| `DG-ENDPOINT-001` | BLOCKING | API requirement | `api_endpoint_permission_requirement` 指向的权限必须存在且 enabled |
| `DG-PLATFORM-001` | BLOCKING | 平台模板 | 平台模板必须显式使用 `tenant_id IS NULL` 语义 |
| `DG-SECURITY-001` | BLOCKING | token 安全状态 | 用户禁用、删除、密码或 MFA 状态变化不得复用 `permissionsVersion` 表达强制失效 |
| `DG-SENSITIVE-001` | BLOCKING | 报告 / 快照 | 报告和样本不得输出密码、token、密钥、TOTP secret 明文 |

规则结果必须至少包含：

- `ruleId`
- `severity`
- `status`
- `message`
- `affectedTable`
- `affectedTenantId`
- `sampleDigest`
- `recommendation`

---

## 34. Liquibase、seed 与菜单落地要求

只要进入 P0 实现并新增表、权限、菜单或 endpoint requirement，就必须走正式迁移链路。

### 34.1 迁移内容

P0 可能需要的迁移包括：

- 治理任务表
- 治理任务产物表
- 治理规则结果表
- 治理审计事件表
- 数据治理权限 seed
- `/platform/data-governance` 菜单 seed
- `/platform/data-governance/**` API endpoint requirement seed

### 34.2 落地约束

- 新 changeset 必须追加到 `db/changelog/**` 尾部，不改已执行 changeset 正文
- 必须同步 include 到 `db.changelog-master.yaml`
- 权限 seed 的 `tenant_id` 必须是平台语义，即 `NULL`
- 菜单 requirement 必须使用 `requiredPermissionId` / `menu_permission_requirement` 主链
- API endpoint requirement 必须登记到 unified `api_endpoint` 主链
- 菜单、权限、endpoint seed 变更后必须评估 `MENU_CONFIG` 与运行时权限快照影响

### 34.3 必跑验证

若 P0 实现触及上述迁移内容，完成时必须至少跑一条真实 `SpringLiquibase` 执行路径：

```bash
bash tiny-oauth-server/scripts/verify-platform-local-dev-stack.sh
```

若明确不需要前端联动，可降级：

```bash
DB_PASSWORD='…' bash tiny-oauth-server/scripts/verify-platform-dev-bootstrap.sh
```

若只做 Maven 定向门禁，可补充：

```bash
bash tiny-oauth-server/scripts/mvn-tiny-oauth-server-gate-sequential.sh
```

交付必须写清 fresh DB / existing DB 路径、exit code、是否被环境前置阻塞。

---

## 35. P0 表与索引最小设计

若 P0 需要落库，建议只新增支撑“本库只读扫描 + 基线体检 + 报告摘要 + 审计事件”的最小表集。表命名继续使用单数。

### 35.1 `data_governance_task`

用途：记录资产扫描、基线体检、对比、迁移、修复等治理任务。

最小字段：

| 字段 | 建议类型 | 说明 |
| --- | --- | --- |
| `id` | `BIGINT` | 主键 |
| `task_type` | `VARCHAR(64)` | `ASSET_SCAN`、`BASELINE_CHECK`、`DIFF`、`MIGRATION` |
| `status` | `VARCHAR(64)` | 采用第 27 节状态机 |
| `scope_type` | `VARCHAR(32)` | P0 固定为 `PLATFORM` |
| `tenant_id` | `BIGINT NULL` | P0 平台治理任务为 `NULL`，租户维度报告通过结果表表达 |
| `reason` | `VARCHAR(512)` | 发起原因 |
| `ticket_id` | `VARCHAR(128)` | 工单或任务卡编号 |
| `created_by` | `BIGINT` | 发起人 |
| `approved_by` | `BIGINT NULL` | 审批人，P0 只读任务可为空 |
| `started_at` | `DATETIME NULL` | 开始时间 |
| `finished_at` | `DATETIME NULL` | 完成时间 |
| `created_at` / `updated_at` | `DATETIME` | 审计时间 |

建议索引：

- `idx_data_governance_task_type_status`
- `idx_data_governance_task_created_by`
- `idx_data_governance_task_ticket_id`
- `idx_data_governance_task_created_at`

### 35.2 `data_governance_artifact`

用途：记录报告、快照、导出物等产物摘要。

最小字段：

| 字段 | 建议类型 | 说明 |
| --- | --- | --- |
| `id` | `BIGINT` | 主键 |
| `task_id` | `BIGINT` | 关联 `data_governance_task.id` |
| `artifact_type` | `VARCHAR(64)` | `ASSET_REPORT`、`BASELINE_REPORT`、`EXPORT_FILE` |
| `storage_ref` | `VARCHAR(512)` | 文件引用或对象存储 key；P0 可为空，仅落摘要 |
| `sha256` | `VARCHAR(128)` | 产物摘要 |
| `masked` | `TINYINT(1)` | 是否已脱敏 |
| `retention_until` | `DATETIME NULL` | 保留到期时间 |
| `summary_json` | `JSON` | 报告摘要，不保存敏感原值 |
| `created_at` / `updated_at` | `DATETIME` | 审计时间 |

建议索引：

- `idx_data_governance_artifact_task_id`
- `idx_data_governance_artifact_type`
- `idx_data_governance_artifact_retention`

### 35.3 `data_governance_rule_result`

用途：记录规则执行结果和脱敏样本摘要。

最小字段：

| 字段 | 建议类型 | 说明 |
| --- | --- | --- |
| `id` | `BIGINT` | 主键 |
| `task_id` | `BIGINT` | 关联任务 |
| `rule_id` | `VARCHAR(64)` | 例如 `DG-TENANT-001` |
| `severity` | `VARCHAR(32)` | `BLOCKING`、`WARNING`、`INFO` |
| `status` | `VARCHAR(32)` | `PASSED`、`FAILED`、`WAIVED`、`SKIPPED` |
| `affected_table` | `VARCHAR(128)` | 影响表 |
| `affected_tenant_id` | `BIGINT NULL` | 影响租户 |
| `message` | `VARCHAR(1024)` | 结果说明 |
| `sample_digest` | `VARCHAR(256)` | 样本摘要，不保存敏感原值 |
| `recommendation` | `VARCHAR(1024)` | 修复建议 |
| `created_at` / `updated_at` | `DATETIME` | 审计时间 |

建议索引：

- `idx_data_governance_rule_result_task_id`
- `idx_data_governance_rule_result_rule_id`
- `idx_data_governance_rule_result_severity_status`
- `idx_data_governance_rule_result_tenant`

### 35.4 `data_governance_audit_event`

用途：记录治理任务关键操作审计事件。

最小字段：

| 字段 | 建议类型 | 说明 |
| --- | --- | --- |
| `id` | `BIGINT` | 主键 |
| `event_type` | `VARCHAR(128)` | 第 29 节事件类型 |
| `actor_id` | `BIGINT NULL` | 操作人或任务账号 |
| `scope_type` | `VARCHAR(32)` | `PLATFORM` / `TENANT` |
| `tenant_id` | `BIGINT NULL` | 关联租户，平台事件可为空 |
| `task_id` | `BIGINT NULL` | 关联任务 |
| `reason` | `VARCHAR(512)` | 原因 |
| `ticket_id` | `VARCHAR(128)` | 工单或任务卡 |
| `event_detail_json` | `JSON` | 脱敏后的事件详情 |
| `created_at` | `DATETIME` | 事件时间 |

建议索引：

- `idx_data_governance_audit_event_type`
- `idx_data_governance_audit_event_actor`
- `idx_data_governance_audit_event_task`
- `idx_data_governance_audit_event_tenant_time`

### 35.5 表设计约束

- P0 表只保存摘要、状态、计数、脱敏样本和审计信息
- 不保存密码、token、密钥、TOTP secret、连接串明文
- 若使用 `JSON` 字段，必须保证 MySQL 版本兼容，并在测试中覆盖序列化 / 反序列化
- 若后续 P1 / P2 引入大报告文件，报告正文应进入文件或对象存储，表中只保存摘要与引用
- 新表、权限、菜单和 endpoint seed 必须同卡补 Liquibase、测试和真实启动验证

---

## 36. 幂等、异步执行与并发边界

P0 扫描和体检虽然是只读任务，也必须定义幂等与并发策略，避免重复点击或多任务并发污染结果。

### 36.1 幂等建议

写入型创建接口必须接入现有 idempotency 机制：

| 接口 | 建议 idempotency scope |
| --- | --- |
| `POST /platform/data-governance/asset-scans` | `platform-data-governance:asset-scan:{actorId}` |
| `POST /platform/data-governance/baseline-checks` | `platform-data-governance:baseline-check:{actorId}` |
| `POST /platform/data-governance/diff-tasks` | `platform-data-governance:diff:{actorId}` |

幂等 payload 至少包含：

- `reason`
- `ticketId`
- `includeTables`
- `excludeTables`
- `ruleIds`

### 36.2 异步执行建议

P0 可以先同步执行小规模扫描，但接口与状态机应按异步任务设计：

- 创建任务后返回 `taskId`
- 前端通过任务详情或任务列表轮询状态
- 执行失败必须落 `FAILED` 状态和失败原因
- 重试必须创建新批次或明确复用原任务，不能静默覆盖旧结果

### 36.3 并发边界

默认策略：

- 同一环境同一时间只允许一个 `ASSET_SCAN` 正在执行
- 同一环境同一时间只允许一个 `BASELINE_CHECK` 正在执行
- P1 对比任务按数据源对加锁，避免同一源 / 目标对重复执行
- 并发冲突返回明确错误，不排队制造隐式延迟

---

## 37. P0 / P1 测试清单

### 37.1 后端测试

P0 后端至少补：

- Controller 权限测试：无权限、非 platform scope、缺少 reason / ticketId 的高风险请求
- Service 测试：任务创建、状态流转、扫描成功、扫描失败、报告生成
- Rule 测试：每个内置规则至少覆盖 pass / fail
- Masking 测试：敏感字段不出现在报告样本和审计事件中
- Repository 测试：任务、产物、规则结果、审计事件能按 taskId 追踪
- Liquibase / 启动验证：新增 migration 真实执行

P1 后端至少补：

- MySQL metadata adapter 测试
- 表级统计对比测试
- 行级摘要对比测试
- 字符集 / 空值 / 时间精度差异样本测试
- 只读保证测试：对比任务不得执行写 SQL

### 37.2 前端测试

P0 前端至少补：

- API 封装测试
- 页面加载成功态
- 空状态
- 无权限状态
- 接口失败态
- 发起扫描 / 体检按钮权限态
- 报告脱敏展示
- 任务状态 badge 展示

### 37.3 验收命令

实现阶段交付建议至少包含：

```bash
bash tiny-oauth-server/scripts/mvn-tiny-oauth-server-gate-sequential.sh
```

前端变更需补：

```bash
cd tiny-oauth-server/src/main/webapp && npm run test:unit
```

若触及 Liquibase / seed / 菜单 / endpoint requirement，必须再补真实启动验证，见第 34.3 节。

---

## 38. Definition of Done

进入实现后，P0 任务只有满足以下条件才可标记完成：

- 文档：本方案对应章节、任务卡、权限标识和验证结果同步更新
- 后端：Controller、Guard、Service、Repository、Rule、DTO 最小闭环完成
- 前端：API、路由、页面、权限常量、核心状态测试完成
- 数据：Liquibase、权限 seed、菜单 seed、endpoint requirement seed 完成并启动验证通过
- 安全：platform scope、权限码、reason / ticketId、敏感数据脱敏全部有测试覆盖
- 审计：关键动作有审计事件，且能按 taskId 追踪
- 验证：命令、结果、exit code、环境缺口写入交付说明
- 边界：未实现 P1 / P2 / P3 能力时明确标注非本轮范围

若任一项因环境前置缺失无法验证，只能标记为“阻塞 / 未验证”，不得写“已完成”。

---

## 39. 结论

tiny-platform 的数据治理、数据对比、数据迁移与版本管理，不应被理解为单一“数据库迁移问题”，而应理解为三层闭环：

1. 结构层：用 Liquibase 管住 schema、约束、seed 和历史收口
2. 运行层：用版本信号、权限快照、安全失效机制管住运行时一致性
3. 治理层：用对比、审计、差异报告和回滚方案管住升级与迁移风险

在多租户 SaaS 语境下，只有当结构版本、运行时配置版本、迁移任务版本三者同时被建模，并且每次升级都保留租户维度的对比与证据链，才能认为 tiny-platform 的数据升级和历史版本管理是安全、可控、可回溯的。
