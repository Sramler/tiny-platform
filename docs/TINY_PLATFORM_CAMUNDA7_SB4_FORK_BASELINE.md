# Tiny Platform Camunda 7 / Spring Boot 4 Fork 基线冻结

最后更新：2026-04-16

状态：Frozen for CARD-C7-01

适用仓库：

- 项目仓库：`/Users/bliu/code/tiny-platform`
- 兼容性源码仓库：`/Users/bliu/code/camunda-bpm-platform`

关联任务卡：

- `docs/TINY_PLATFORM_CAMUNDA7_SB4_FORK_TASK_CARDS.md`

## 1. 文档目的

本文用于执行 `CARD-C7-01`：

冻结本轮 Camunda 7 / Spring Boot 4 fork 改造的版本基线、坐标命名、分支口径和阶段成功标准。

本文一旦冻结，后续卡片默认以本文为准，不再在每张卡里重复改口径。

## 2. 当前项目事实

当前 `tiny-platform` 代码中的真实依赖现状是：

- 父工程当前 Spring Boot 基线：`3.5.10`
- 父工程当前 JDK 基线：`21`
- 父工程当前 Camunda BOM：`org.camunda.bpm:camunda-bom:7.24.0`
- `tiny-oauth-server` 当前直接使用：
  - `org.camunda.bpm.springboot:camunda-bpm-spring-boot-starter`
  - `org.camunda.bpm.springboot:camunda-bpm-spring-boot-starter-rest`

因此，本轮 fork 的首要目标不是“重新设计依赖体系”，而是：

在尽量少改 `tiny-platform` 接入面的前提下，提供可被项目消费的 Spring Boot 4 兼容版本。

## 3. 冻结结论

### 3.1 上游基线

本轮 fork 以上游以下版本为起点：

- Camunda：`7.24.0`
- Spring Boot 目标首版：`4.0.0`
- JDK 基线：`21`

说明：

这里冻结的是“首个兼容目标版本”，不是承诺后续永远停留在 `4.0.0`。

后续如需升级到更高的 Spring Boot 4.x，另开任务卡处理，不在本卡内滚动变更。

### 3.2 坐标命名策略

本轮 fork 采用：

保留上游 `groupId` / `artifactId`，仅通过内部版本后缀区分 fork 产物

冻结原因：

1. `tiny-platform` 当前已经按上游坐标接入 Camunda
2. 保留原坐标可以最大限度减少项目侧 POM 改动
3. 版本后缀足以与 Maven Central 的正式版本区分
4. 更适合先完成最小兼容，再决定是否需要长期内部重命名

因此，本轮不采用：

- 改 `groupId`
- 改 `artifactId`
- 额外引入一套新的内部模块命名体系

### 3.3 版本命名策略

本轮冻结以下版本命名规则：

- 开发快照版本：`7.24.0-tiny-sb4-01-SNAPSHOT`
- 第一版可消费内部版本：`7.24.0-tiny-sb4-01`

当前主线状态补充（2026-04-16）：

- 固定版收口已开始落地
- 当前默认消费版本应视为：`7.24.0-tiny-sb4-01`
- `7.24.0-tiny-sb4-01-SNAPSHOT` 保留为开发阶段命名规则，不再作为默认消费版本

命名含义：

- `7.24.0`：对应上游 Camunda 基线
- `tiny`：表示 Tiny Platform 内部 fork
- `sb4`：表示 Spring Boot 4 兼容线
- `01`：表示第一条正式 fork 兼容线

后续如果需要第二轮兼容线，命名规则按以下方式递增：

- `7.24.0-tiny-sb4-02-SNAPSHOT`
- `7.24.0-tiny-sb4-02`

### 3.4 本轮默认消费坐标

本轮默认项目消费入口冻结为：

- `org.camunda.bpm:camunda-bom:7.24.0-tiny-sb4-01`
- `org.camunda.bpm.springboot:camunda-bpm-spring-boot-starter`
- `org.camunda.bpm.springboot:camunda-bpm-spring-boot-starter-rest`

说明：

项目侧优先通过 fork 后的 BOM 控制版本，不在 `tiny-oauth-server/pom.xml` 中零散写死多个 Camunda 子模块版本。

### 3.5 本轮 patch 主战场

本轮默认主战场冻结为：

- `spring-boot-starter`

具体优先级为：

1. `spring-boot-starter/starter`
2. 必要时 `spring-boot-starter/starter-rest`
3. 必要时 `spring-boot-starter/starter-webapp-core`
4. `engine` 仅作为后备面

因此：

`engine` 不是本轮第一优先级改造面。

只有在 starter 路线被证明确实无法闭环时，才开启 engine 后备卡。

### 3.6 分支命名策略

本轮冻结以下分支命名口径：

- 长期 fork 主线：`fork/camunda7-sb4-main`
- 单卡执行分支：`fork/camunda7-sb4/card-c7-xx`

示例：

- `fork/camunda7-sb4/card-c7-02`
- `fork/camunda7-sb4/card-c7-03`

说明：

这里冻结的是推荐命名，不要求当前立即创建这些分支。

## 4. 本阶段成功标准

本轮 fork 兼容工作的成功标准冻结为：

1. Spring Boot 4 应用可以成功启动
2. BPMN 可以部署
3. `ServiceTask` 可以执行

本阶段不要求：

1. Webapps 全量可用
2. `tasklist` / `cockpit` / `admin` 成为正式产品能力
3. 全量功能在 Spring Boot 4 下无差别兼容
4. 完成 Camunda 8 迁移

## 5. 本阶段非目标

以下事项明确不属于 `CARD-C7-01` 或本轮最小兼容主目标：

- 重做 Camunda 模块坐标体系
- 将 `starter-security` 作为 `tiny-platform` 正式生产方案
- 将 Webapps 改造成 SaaS 产品 UI
- 一开始就展开大范围 `engine` 重构
- 在本卡内确定长期制品仓库发布方案

## 6. 制品分发策略

本轮先冻结以下分发策略：

### 6.1 开发阶段

优先使用本地 Maven 安装产物验证：

- `mvn install`

### 6.2 团队共享阶段

团队共享制品仓库接入放到：

- `CARD-C7-05`

也就是说，本卡只冻结版本与命名规则，不冻结最终共享仓库实现方式。

## 7. 对 tiny-platform 的直接影响

一旦后续进入接入卡，`tiny-platform` 的项目侧改动原则冻结为：

1. 尽量只调整 BOM 版本入口
2. 尽量不改 starter 的 `artifactId`
3. 尽量不引入额外的内部别名依赖
4. 维持 `Engine Only` 策略不变

这意味着：

后续项目接入应优先追求“最小 POM 变更”，而不是“最彻底重命名”。

## 8. 一句话结论

`CARD-C7-01` 的冻结结论是：

本轮以 `Camunda 7.24.0 + Spring Boot 4.0.0 + JDK 21` 为首个兼容基线，
保留上游坐标，仅使用 `7.24.0-tiny-sb4-01(-SNAPSHOT)` 版本后缀区分 fork，
并把 `spring-boot-starter` 作为第一优先级改造面。
