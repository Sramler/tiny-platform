# Tiny Platform Camunda 7 / Jackson 3 迁移任务卡

最后更新：2026-08-24

## 1. 裁决

- 不调整现有 password grant 兼容链。
- tiny-platform 默认保持 `Engine Only`，原生 Camunda REST 只能通过 Maven `camunda-rest` profile 进入运行时制品。
- REST 先迁移并验收；Spin 作为独立阶段验收，不能用“编译通过”替代变量序列化兼容验证。
- 不采用未发布的 Jersey PR 产物。REST 使用 Camunda 已注册的官方 `tools.jackson.jakarta.rs` Provider；Spring Boot starter 必须停止注册 Jackson 2 `JacksonFeature`。

## 2. 已确认的根因

旧制品 `7.24.0-tiny-sb4-01` 同时包含两条 JSON 链：

- Spring Boot 4.1.1 / Authorization Server：Jackson 3；
- Camunda REST starter -> Spring Boot Jersey -> `spring-boot-jackson2` / `jersey-media-json-jackson`：Jackson 2。

该问题已在新的不可变 fork 版本 `7.24.0-tiny-sb4-jackson3-01` 中收口；旧制品未被覆盖。

## 3. 执行顺序

### CARD-CJ3-01：生产依赖边界

状态：已实现，tiny-platform 主代码、测试编译和定向行为回归已通过。

- 默认依赖仅以 test scope 保留 Camunda REST，保证既有测试源码可编译；
- `-Pcamunda-rest` 才把 REST starter 提升到 compile/runtime；
- `verify-camunda-runtime-boundary.sh default` 阻止默认制品重新携带 REST/Jersey Jackson 2。

实施时发现并一并消除了一个此前被 starter 掩盖的依赖问题：tiny-platform 一方源码曾通过
Camunda REST 的传递依赖取得 Jackson 2 core/databind。现在一方主代码与测试已迁移到
`tools.jackson.*`；`com.fasterxml.jackson.annotation` 按 Jackson 3 官方命名空间保留。
门禁同时扫描源码和 runtime dependency tree，防止以后通过其他 starter 再次回流。

2026-08-24 tiny-platform 验证结果：

- 542 个主源码文件编译通过；
- 229 个测试源码文件编译通过；
- Jackson、SecurityUser、租户守卫、审计、调度、导出、工作流定向测试 55 条通过；
- 最终默认 Engine Only 全量回归 1362 条，0 失败、0 错误、2 条按条件跳过；
  其中原生 REST E2E 在默认 profile 下按设计跳过，并已在 `camunda-rest` profile 中单独实跑通过；
- `verify-camunda-runtime-boundary.sh default` 通过，默认 runtime 未发现 Jackson 2 实现依赖。

全量回归额外修复：

- MVC 测试不再断言旧 `MappingJackson2HttpMessageConverter`，改为验证 Boot 4
  `JacksonJsonHttpMessageConverter`；
- `webObjectMapper` Bean 显式声明为 `JsonMapper`，使 Boot 4
  `@ConditionalOnMissingBean(JsonMapper.class)` 正确退让，避免与自动创建的
  `jacksonJsonMapper` 同时成为 Primary。

### CARD-CJ3-02：fork REST starter 收口

状态：已完成。新不可变版本已安装到本地 Maven 仓库并接入 tiny-platform。

- 删除 `CamundaJerseyResourceConfig` 对 Jackson 2 `JacksonFeature` 的导入和注册；
- 从 `spring-boot-starter-jersey` 排除 `spring-boot-jackson2` 与 `jersey-media-json-jackson`；
- 保留 `CamundaRestResources` 注册的 Jackson 3 `JacksonJsonProvider` 与 `JacksonConfigurator`；
- 增加依赖树和 Spring context 回归，禁止 Jackson 2 桥接回流。

2026-08-24 验证结果：

- fork 对齐 Spring Boot `4.1.1`、Jackson `3.1.5`、Surefire `3.5.6`；
- REST starter 45 模块 reactor compile：通过；
- REST runtime 定向依赖树：仅出现
  `tools.jackson.jakarta.rs:jackson-jakarta-rs-json-provider`，REST starter 节点下未出现
  `spring-boot-jackson2`、`jersey-media-json-jackson` 或未 shaded Jackson 2；
- 随机端口真实 HTTP 测试：原有 5 条通过；
- 新增变量 JSON 往返和非法 JSON 场景后，`SampleCamundaRestApplicationIT` 6 条通过。

### CARD-CJ3-03：真实 Camunda REST 验收

状态：已完成。

必须使用 `-Pcamunda-rest` 启动真实应用并至少验证：

1. `GET /engine-rest/engine` 返回可反序列化 JSON；
2. 部署最小 BPMN；
3. 通过 REST 启动实例并传入字符串、数值、布尔和日期变量；
4. 查询实例/变量并验证 JSON 类型和值；
5. 非法 JSON 返回稳定 4xx，而不是 Provider 缺失或 5xx；
6. 原生 REST 不经外部入口直接暴露，生产访问仍受平台网关/IAM 边界约束。

完成标准：`verify-camunda-runtime-boundary.sh rest` 通过，且上述真实 HTTP 往返全部通过。

2026-08-24 tiny-platform 验证结果：

- 使用 `7.24.0-tiny-sb4-jackson3-01` 和 `-Pcamunda-rest` 启动完整应用；
- 真实连接本地 MySQL，Liquibase 确认 197 个 change set 均为当前态；
- 随机端口原生 Jersey REST 实际完成 engine 查询、BPMN multipart 部署、流程启动、
  String/Integer/Boolean/Date 变量读取和非法 JSON 4xx；
- Date 输入的 `+0800` 偏移被规范化为同一时刻的 UTC `Z` 输出；
- 测试 1 条通过，0 失败、0 错误、0 跳过；
- 级联删除后 deployment、process definition、runtime execution 三类残留均为 0。

### CARD-CJ3-04：Spin 独立迁移验收

状态：已完成 fork 模块级兼容验收；生产历史数据迁移仍须在发布环境按独立数据批次验收。

除模块测试外，必须覆盖历史 Spin JSON 读取、新值写入、Java object 映射、JSONPath、日期时间、未知字段和失败回滚。若历史持久化 payload 与 Jackson 3 不兼容，必须先提供兼容读取或迁移方案，不得直接替换生产制品。

2026-08-24 fork 验证结果：

- `spin/dataformat-json-jackson` reactor 共 671 条测试通过，0 失败、0 错误、5 条按运行时条件跳过；
- 恢复历史默认语义：未知字段 fail-fast、结构节点描述转为稳定 Spin 异常、Date 默认写为时间戳；
- 新增旧数字日期 payload 读取和日期表示往返测试；
- Java object 映射与 JSONPath 均由完整模块测试覆盖。

## 4. 命令

```bash
bash tiny-oauth-server/scripts/verify-camunda-runtime-boundary.sh default
bash tiny-oauth-server/scripts/verify-camunda-runtime-boundary.sh rest
set -a; source tiny-oauth-server/src/main/webapp/.env.e2e.local; set +a
mvn -Pcamunda-rest -pl tiny-oauth-server -Dtest=CamundaRestJackson3MySqlE2eTest test
```

前两条在 `7.24.0-tiny-sb4-jackson3-01` 上均已转绿；旧 `7.24.0-tiny-sb4-01` 不再作为 REST profile 的允许版本。

## 5. 发布约束

- 已发布的 `7.24.0-tiny-sb4-01` 不得原地覆盖；Jackson 3 完整制品必须使用新的不可变版本号。
- tiny-platform 当前 `camunda.version` 已升级到 `7.24.0-tiny-sb4-jackson3-01`。
- fork 工作区已有未提交修改，后续补丁必须先审计并保留这些修改，不得清理或覆盖用户工作。
