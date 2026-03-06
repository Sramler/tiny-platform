# tiny-oauth-server 文档首页

最后更新：2026-03-06

适用模块：`tiny-oauth-server`

## 1. 这个首页解决什么问题

`docs` 目录里的文档已经覆盖了认证、导出、调度、日志、数据库等多个专题，但之前缺少统一入口。

这个首页只做一件事：

1. 告诉你应该先看哪份文档
2. 按问题类型把专题索引挂出来
3. 区分“值班排障”“研发设计”“生产发布”三种使用场景

## 2. 推荐阅读顺序

如果你不确定从哪里开始，按这个顺序：

1. 导出问题：`EXPORT_DOCS_INDEX.md`
2. 认证/登录/TOTP：`认证文档索引.md`
3. 日志与 Trace：`LOGGING_AND_TRACE_ENTERPRISE_GUIDE.md`
4. 调度/Quartz：本页第 5 节的调度专题
5. 数据库变更：`LIQUIBASE.md`

## 3. 值班与生产排障入口

### 导出

1. 第一响应：`EXPORT_ONCALL_5MIN_CHECKLIST.md`
2. 深度排查：`EXPORT_ONCALL_COMMAND_RUNBOOK.md`
3. 完整背景与证据链：`EXPORT_INVESTIGATION_BENCHMARK_TROUBLESHOOTING_GUIDE.md`
4. 提交/评审摘要：`EXPORT_CHANGESET_SUMMARY_20260306.md`

### 认证

1. 统一入口：`认证文档索引.md`
2. 登录链路：`登录与租户认证流程.md`
3. TOTP：`totp-mfa-security-guide.md`

### 日志与 Trace

1. 统一规范：`LOGGING_AND_TRACE_ENTERPRISE_GUIDE.md`
2. `logback` 问题定位：`LOGBACK_APP_NAME_ISSUE.md`

### Quartz / 调度

1. 集群状态检查：`QUARTZ_CLUSTER_STATUS_CHECK.md`
2. 集成状态：`QUARTZ_INTEGRATION_STATUS.md`
3. 数据库初始化：`QUARTZ_DATABASE_SETUP.md`

## 4. 导出专题

入口：

1. `EXPORT_DOCS_INDEX.md`

适用问题：

1. `POI vs Fesod` 选型
2. `10w / 100w / 1000w` 压测口径
3. GC / JFR / 尾段分析
4. 导出专用节点发布与回滚
5. 提交、评审与发布摘要

推荐顺序：

1. `EXPORT_DOCS_INDEX.md`
2. `EXPORT_INVESTIGATION_BENCHMARK_TROUBLESHOOTING_GUIDE.md`
3. `EXPORT_ONCALL_5MIN_CHECKLIST.md`
4. `EXPORT_ONCALL_COMMAND_RUNBOOK.md`
5. `EXPORT_CHANGESET_SUMMARY_20260306.md`
6. `EXPORT_WRITER_FESOD_ROLLOUT.md`
7. `EXPORT_WRITER_FESOD_RELEASE_CHECKLIST.md`

## 5. 调度专题

### 推荐阅读顺序

1. `SCHEDULING_MODULE_SAAS_CAPABILITIES.md`
2. `SCHEDULING_MODULE_ISSUES.md`
3. `SCHEDULING_TRIGGER_DESIGN.md`
4. `SCHEDULING_TASK_EXECUTOR_GUIDE.md`
5. `SCHEDULING_TASK_EXAMPLES.md`
6. `SCHEDULING_DEMO_DATA.md`

### Quartz 相关

1. `QUARTZ_CLUSTER_STATUS_CHECK.md`
2. `QUARTZ_INTEGRATION_STATUS.md`
3. `QUARTZ_TABLE_MIGRATION_GUIDE.md`
4. `QUARTZ_DATABASE_SETUP.md`

### 设计与数据

1. `DAG_TASK_TABLE_DESIGN_REVIEW.md`
2. `docs/scripts/scheduling-task-type-demo.http`
3. `scheduling-task-examples-data.sql`

## 6. 认证与安全专题

入口：

1. `认证文档索引.md`

推荐顺序：

1. `登录与租户认证流程.md`
2. `TOKEN_CLAIMS_ENTERPRISE_STANDARD.md`
3. `totp-mfa-security-guide.md`
4. `USERID_IN_TOKEN_FIX.md`
5. `LONG_ALLOWLIST_FIX.md`

## 7. 日志与可观测性专题

推荐顺序：

1. `LOGGING_AND_TRACE_ENTERPRISE_GUIDE.md`
2. `LOGBACK_APP_NAME_ISSUE.md`

适用问题：

1. `traceId/requestId` 全链路追踪
2. HTTP 请求日志
3. `logback` 配置异常

## 8. 数据库与变更治理

推荐顺序：

1. `LIQUIBASE.md`
2. `COVERAGE_GOVERNANCE_PLAN.md`

适用问题：

1. 表结构变更流程
2. 变更集组织方式
3. 覆盖率治理计划

## 9. 使用建议

1. 值班时先看第 3 节，不要先翻设计文档
2. 研发排导出问题时，先看导出专题，不要只看代码
3. 认证问题优先以 `认证文档索引.md` 为准，不要混用旧文档
4. 新增专题时，优先补自己的专题索引，再把入口挂到本首页
