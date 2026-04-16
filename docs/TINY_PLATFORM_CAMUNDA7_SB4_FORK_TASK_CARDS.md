# Tiny Platform Camunda 7 / Spring Boot 4 Fork 改造任务卡

最后更新：2026-04-16

状态：可执行任务卡

适用仓库：

- 项目仓库：`/Users/bliu/code/tiny-platform`
- 兼容性源码仓库：`/Users/bliu/code/camunda-bpm-platform`

关联文档：

- `docs/TINY_PLATFORM_CAMUNDA7_SPRING_BOOT4_ENGINE_ONLY_STRATEGY.md`

## 1. 现在是否应该开始拆卡

结论：

可以，现在就应该开始拆。

原因不是“已经准备开始大改 engine”，而是以下前提已经稳定：

- `tiny-platform` 已明确采用 `Engine Only + 自研 UI + 自研 IAM`
- 当前真实集成模块已明确为 `tiny-oauth-server`
- `camunda-bpm-platform` 已明确只是兼容性 fork 与 patch 来源
- 本阶段目标已明确为“最小可运行”，不是全量平台兼容

因此，当前最适合做的是：

先把 fork 改造拆成可执行的最小任务卡，再按证据推进

而不是：

直接并行大范围改 `engine` 和所有周边模块

## 2. 拆卡原则

本轮拆卡遵循以下原则：

1. 先拆 `spring-boot-starter`，后拆 `engine`
2. `engine` 只在 starter 路线被证明确实卡住时才开后备卡
3. 先保证编译和启动，再保证部署和 `ServiceTask`
4. 先在 fork 仓库完成最小 patch，再回到 `tiny-platform` 做集成验证
5. 每张卡必须有明确仓库归属、修改边界、验收标准

## 3. 仓库职责

### 3.1 `tiny-platform`

负责：

- 项目级决策
- 集成接入
- 业务验证
- 文档、回归与上线约束

### 3.2 `camunda-bpm-platform`

负责：

- fork 版本坐标与产物
- Spring Boot 4 兼容 patch
- starter / engine 源码改造
- 最小运行 smoke

## 4. 推荐执行顺序

推荐顺序如下：

1. `CARD-C7-01` fork 坐标与版本基线冻结
2. `CARD-C7-02` `spring-boot-starter` 依赖基线升级到 Spring Boot 4
3. `CARD-C7-03` `spring-boot-starter` AutoConfiguration 最小兼容 patch
4. `CARD-C7-04` Webapp / Security / REST 边界收口
5. `CARD-C7-05` fork 产物发布与 `tiny-platform` 接入验证
6. `CARD-C7-06` `tiny-platform` 核心流程 smoke
7. `CARD-C7-07` `engine` 后备卡，仅在 starter 路线不足时开启

## 5. 任务卡

### CARD-C7-01 fork 坐标与版本基线冻结

目标：

把本轮 fork 的坐标、版本命名、分支归属、最小成功标准先固定下来，避免后续一边改代码一边改口径。

主要仓库：

- `camunda-bpm-platform`
- `tiny-platform`

改动范围：

- 明确 fork 产物坐标命名规则
- 明确 Spring Boot 4 目标版本
- 明确是否保留原 groupId / artifactId 或改为内部命名
- 明确成功标准只包括：
  - 启动
  - BPMN 部署
  - `ServiceTask` 执行

验收标准：

- 有书面版本基线记录
- 有统一 fork 命名规则
- 团队对“本阶段不做 Webapps 全量适配”无歧义

禁止项：

- 不在本卡引入真实源码 patch
- 不在本卡讨论 Camunda 8 迁移细节

### CARD-C7-02 `spring-boot-starter` 依赖基线升级到 Spring Boot 4

目标：

先让 `spring-boot-starter` 相关模块在 Spring Boot 4 依赖面上具备可编译基础。

主要仓库：

- `camunda-bpm-platform`

主要模块：

- `spring-boot-starter/starter`
- 必要时包括：
  - `spring-boot-starter/starter-rest`
  - `spring-boot-starter/starter-webapp-core`
  - `spring-boot-starter/starter-security`

改动范围：

- `pom.xml` / BOM 对齐到 Spring Boot 4
- Jakarta 依赖面核对
- 明确哪些模块继续纳入编译，哪些先不作为本阶段目标

验收标准：

- `starter` 主路径可完成最小编译
- 编译错误集中在已知少量兼容点，而不是全局散乱失败
- 形成一份清晰的“编译阻塞点清单”

禁止项：

- 不在本卡直接大改 `engine`
- 不在本卡做业务仓库接入

### CARD-C7-03 `spring-boot-starter` AutoConfiguration 最小兼容 patch

目标：

把 Spring Boot 4 真正卡启动的自动配置问题收口在 starter 层解决。

主要仓库：

- `camunda-bpm-platform`

重点范围：

- `spring-boot-starter/starter`
- 条件注解
- JPA 相关配置
- `javax` 到 `jakarta` 的残留引用
- Bean 覆盖与最小启动行为

优先 patch 范围：

- `@ConditionalOnClass`
- `@ConditionalOnBean` / `@ConditionalOnClass` 的不兼容判断
- JPA 自动配置防御或开关
- Servlet 包迁移

验收标准：

- 最小 demo 应用可启动
- Camunda 引擎 bean 可创建
- 基础部署与启动日志进入可验证状态

禁止项：

- 不把本卡扩展成 Webapps 全量修复
- 不因个别问题过早回退到改 `engine`

### CARD-C7-04 Webapp / Security / REST 边界收口

目标：

把本阶段要支持和不支持的边界在 fork 代码层明确下来，防止后续重复扩 scope。

主要仓库：

- `camunda-bpm-platform`
- `tiny-platform`

改动范围：

- 明确 `webapp` 在本阶段是否仅保持编译，不保证可用
- 明确 `starter-security` 不作为 `tiny-platform` 的生产方案
- 明确 `starter-rest` 是可选能力，不是必需能力

验收标准：

- 边界在文档和代码依赖上保持一致
- `tiny-platform` 不因默认引入错误模块而放大兼容面
- 有明确的“生产禁用 / 非生产可选”口径

禁止项：

- 不把调试环境保留 `cockpit` 误写成生产依赖

### CARD-C7-05 fork 产物发布与 `tiny-platform` 接入验证

目标：

把 fork 产物真正接到 `tiny-platform`，验证不是“demo 能跑、业务仓库不能接”。

主要仓库：

- `camunda-bpm-platform`
- `tiny-platform`

主要模块：

- `tiny-platform/pom.xml`
- `tiny-oauth-server/pom.xml`

改动范围：

- 将 `tiny-platform` 的 Camunda 依赖切到 fork 产物
- 保持现有 Engine Only 策略不变
- 验证 `tiny-oauth-server` 可编译、可启动

验收标准：

- `tiny-oauth-server` 使用 fork 产物后可启动
- 现有流程相关 bean 与接口不因 fork 坐标变化直接失效
- 有接入说明和回退方案

禁止项：

- 不在本卡同时推动业务逻辑重构

### CARD-C7-06 `tiny-platform` 核心流程 smoke

目标：

验证 fork 产物在 `tiny-platform` 中满足本阶段真实目标，而不是只通过编译。

主要仓库：

- `tiny-platform`

重点验证：

- 启动
- BPMN 部署
- `ServiceTask` 执行
- 基础流程相关控制器或服务可用

建议验证对象：

- `ProcessController`
- 流程部署入口
- 多租户上下文与 Camunda bridge 基础链路

验收标准：

- 有一条最小真实流程 smoke 成功
- 至少有一组自动化或脚本化验证证据
- 问题能区分为 fork 问题还是项目集成问题

禁止项：

- 不把本卡扩大为完整工作流产品验收

### CARD-C7-07 `engine` 后备卡：仅在 starter 路线不足时开启

目标：

仅当证据表明 starter 层无法解决问题时，再有控制地打开 engine 改造面。

主要仓库：

- `camunda-bpm-platform`

开启条件：

- 已完成 `CARD-C7-02` 到 `CARD-C7-06`
- 仍存在明确 blocker
- blocker 已证明确实不在 starter 层

可能范围：

- `engine` 中与 Spring Boot 4 / Jakarta 运行时交互直接相关的最小 patch

验收标准：

- 每个 engine patch 都能说明“为什么 starter 层无解”
- 改动面可追踪、可回退、可解释

禁止项：

- 不把 `engine` 当作第一优先级主战场
- 不做与本阶段目标无关的清洁式重构

## 6. 当前建议

当前建议是：

立即开始拆卡，但只正式开启前 3 张卡。

也就是说：

现在就可以开始 fork 版本改造任务卡拆分；
但执行上应先聚焦 `spring-boot-starter`，不要一开始就把 `engine` 开成大工程。

## 7. 下一步建议动作

如果继续推进，下一步应当是：

1. 先执行 `CARD-C7-01`
2. 然后在 `camunda-bpm-platform` 中正式开始 `CARD-C7-02`
3. 待 starter 层真实阻塞点收敛后，再决定是否开启 `CARD-C7-07`
