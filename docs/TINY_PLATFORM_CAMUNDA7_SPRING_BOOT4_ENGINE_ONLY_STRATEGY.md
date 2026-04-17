# Tiny Platform: Camunda 7 on Spring Boot 4 Engine Only 策略

最后更新：2026-04-17

适用仓库：`/Users/bliu/code/tiny-platform`

远端仓库：[Sramler/tiny-platform](https://github.com/Sramler/tiny-platform)

当前集成模块：`tiny-oauth-server`

参考兼容性源码仓库：`/Users/bliu/code/camunda-bpm-platform`

## 1. 这份文档解决什么问题

这份文档明确一件事：

在 `tiny-platform` 中，Camunda 7 在 Spring Boot 4 过渡阶段到底如何使用、哪些组件保留、哪些组件不作为正式产品能力交付。

它不是 Camunda 官方说明，也不是上游 `camunda-bpm-platform` 的通用文档。

它是 `tiny-platform` 自己的项目决策文档。

## 2. 项目上下文

当前 `tiny-platform` 是多模块仓库，根模块定义见：

- `tiny-platform/pom.xml`

当前 Camunda 集成实际落在：

- `tiny-oauth-server/pom.xml`

从当前代码可确认：

- 父工程当前 Spring Boot 基线为 `4.1.0-SNAPSHOT`
- 父工程当前 JDK 基线为 `21`
- 父工程通过 BOM 使用 Camunda `7.24.0`
- `tiny-oauth-server` 当前已引入：
  - `camunda-bpm-spring-boot-starter`
  - `camunda-bpm-spring-boot-starter-rest`

因此：

Camunda 7 + Spring Boot 4 的兼容问题，首先是 `tiny-platform` 的业务集成问题；
`camunda-bpm-platform` 只是兼容性参考和必要 patch 来源，不是最终产品归属仓库。

## 3. 最终决策

在 `tiny-platform` 中，生产环境采用：

`Engine Only + 自研 UI + 自研 IAM`

正式采用的 Camunda 组件为：

- `engine`
- `spring-boot-starter`
- `rest`，仅在明确需要时启用

不作为正式产品能力交付的组件为：

- `webapps`
- `tasklist`
- `cockpit`
- `admin`

非生产环境中，可按需保留 `cockpit` 作为排障工具，但不得对业务用户开放，也不得成为正式产品依赖。

## 4. 为什么这份决策应归属 tiny-platform

这份文档应优先放在 `tiny-platform`，原因很明确：

1. 真实运行和交付对象是 `tiny-platform`，不是 `camunda-bpm-platform`
2. Camunda 的业务接入、认证集成、租户集成、前端流程入口都在 `tiny-platform`
3. 最终是否启用 `rest`、是否保留 `cockpit`、如何接 IAM，都是项目级决策，不是上游源码仓库决策
4. 团队后续查找、评审、发布、排障，都是先进入 `tiny-platform`

因此，正确关系应当是：

- `tiny-platform`：项目正式策略与落地约束
- `camunda-bpm-platform`：兼容性分析、源码 patch、参考实现

## 5. 职责边界

在 `tiny-platform` 中，Camunda 的定位是：

嵌入式流程执行引擎

平台负责：

- 流程设计器
- 流程部署入口
- 任务中心
- 审批 UI
- 身份认证
- 租户模型
- 权限模型
- 流程监控视图
- incident / retry / 恢复等运维能力

原则是：

Camunda 负责执行。
Tiny Platform 负责产品化。

## 6. 组件使用规则

### 6.1 生产环境

生产环境只保留最小可行集合：

- `camunda-bpm-spring-boot-starter`
- 需要时使用 `camunda-bpm-spring-boot-starter-rest`

不将以下组件作为正式产品交付能力：

- `camunda-bpm-spring-boot-starter-webapp`
- `tasklist`
- `cockpit`
- `admin`

当前仓库中，主配置已经显式关闭 Camunda Webapp：

- `tiny-oauth-server/src/main/resources/application.yaml`
  - `camunda.bpm.webapp.enabled=false`

### 6.2 REST

`rest` 是可选的，不是必须的。

仅在以下场景启用：

- 外部系统需要通过 HTTP 与流程能力交互
- 平台需要受控的流程引擎接口面
- 部分运维查询或处置能力需要 API 化

推荐规则：

不要把原生 Camunda REST 直接当作产品前端 API 暴露；
优先由 `tiny-platform` 自己的服务层做收口和封装。

### 6.3 starter-security

在 `Engine Only` 策略下，不把 Camunda `starter-security` 作为 `tiny-platform` 的默认生产方案。

认证、授权、租户隔离应统一由平台自身安全体系负责。

为避免后续误引入，当前模块已增加构建级护栏：

- `tiny-oauth-server/pom.xml`
  - 通过 `maven-enforcer-plugin` 禁止引入：
    - `camunda-bpm-spring-boot-starter-webapp`
    - `camunda-bpm-spring-boot-starter-security`
    - `org.camunda.bpm.webapp:*`

额外说明：

- 当前 `application-e2e.yaml` 已显式设置：
  - `camunda.bpm.enabled=false`
  - 排除 `CamundaBpmRestJerseyAutoConfiguration`
- 因此现阶段 E2E 自动化链路不构成 Camunda 可用性的验收依据

## 7. Identity / Tenant / Admin 的准确表述

Camunda 7 本身具备：

- user
- group
- tenant
- authorization

但在 `tiny-platform` 中：

- 不采用 Camunda 内置 identity 作为主 IAM
- 不采用 Camunda tenant 作为平台主租户模型
- 不采用 Camunda authorization 作为平台主权限模型

平台自身才是以下能力的唯一主来源：

- 用户身份
- 会话与令牌
- 租户隔离
- RBAC / ABAC 权限模型

因此，准确说法应为：

Camunda 有这些能力，但 `tiny-platform` 生产不采用它们作为正式平台模型。

## 8. 平台必须自行补齐的能力

如果不采用 Webapps，`tiny-platform` 必须自行提供：

- 流程设计与发布
- 待办/已办任务中心
- 审批表单与审批动作
- 流程实例查询
- 历史轨迹查询
- 当前节点定位
- incident 管理
- 失败 job retry 处理
- 异常流程恢复
- 审计与运维留痕

重点不是“能查到数据”就够，而是要具备基本运维处理能力。

## 9. 数据与运维前提

如果 `tiny-platform` 要替代 Cockpit，则必须同步明确：

- history 级别策略
- 历史数据保留周期
- cleanup / 归档策略
- incident 处理流程
- retry / dead job 处理流程
- 运维权限边界

不用 Cockpit，不等于不需要运维能力；
只是这些责任改由平台自己承担。

## 10. 与兼容性源码仓库的关系

当前建议将信息分层管理：

### 10.1 `tiny-platform`

存放：

- 最终项目决策
- 生产/非生产使用边界
- 平台侧能力补齐要求
- 运维与权限约束
- 发布和演进策略

### 10.2 `camunda-bpm-platform`

存放：

- Spring Boot 4 兼容 patch
- starter 层源码适配
- vendor fork 说明
- 对上游 Camunda 源码的局部修改记录

也就是说：

项目决策归 `tiny-platform`
源码兼容细节归 `camunda-bpm-platform`

## 11. 后续约束

为了保留未来迁移空间，`tiny-platform` 新增业务代码应尽量避免在大量业务模块中直接扩散对原始 Camunda API 的依赖。

推荐通过平台自有流程服务抽象访问工作流能力，以便未来迁移到：

- Camunda 8
- 替代引擎
- 平台内部解耦后的流程实现

## 12. 一句话结论

如果你的目标是指导 `tiny-platform` 如何在未来承接 Camunda 7 + Spring Boot 4，
那么这份文档就应该首先归属在 `tiny-platform` 仓库中，而不是只存在于 `camunda-bpm-platform` 兼容性 fork 中。
