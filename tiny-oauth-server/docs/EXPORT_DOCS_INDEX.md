# 导出文档索引

统一首页：

- `docs/README.md`

## 1. 总入口

优先阅读顺序：

1. `docs/EXPORT_INVESTIGATION_BENCHMARK_TROUBLESHOOTING_GUIDE.md`
2. `docs/EXPORT_ONCALL_5MIN_CHECKLIST.md`
3. `docs/EXPORT_ONCALL_COMMAND_RUNBOOK.md`
4. `docs/EXPORT_CHANGESET_SUMMARY_20260306.md`
5. `docs/EXPORT_WRITER_FESOD_ROLLOUT.md`
6. `docs/EXPORT_WRITER_FESOD_RELEASE_CHECKLIST.md`

## 2. 指南类

### 导出链路排查、压测、选型与生产排障指南

文件：

- `docs/EXPORT_INVESTIGATION_BENCHMARK_TROUBLESHOOTING_GUIDE.md`

用途：

1. 了解这次完整做了哪些工作
2. 查看 `POI vs Fesod` 的对比方法与结论
3. 按标准 SOP 排查生产导出问题

### 导出生产值班 5 分钟排查清单

文件：

- `docs/EXPORT_ONCALL_5MIN_CHECKLIST.md`

用途：

1. 5 分钟内判断问题落点
2. 决定先止血、回滚还是继续深查
3. 适合值班第一响应

### 导出值班命令速查表

文件：

- `docs/EXPORT_ONCALL_COMMAND_RUNBOOK.md`

用途：

1. 值班时直接执行命令
2. 快速看任务表、指标、日志、GC、JFR
3. 避免线上排障时临时拼命令

### 导出专项变更摘要

文件：

- `docs/EXPORT_CHANGESET_SUMMARY_20260306.md`

用途：

1. 提交说明
2. PR 摘要
3. 评审入口
4. 发布说明引用

### Fesod 切换发布方案

文件：

- `docs/EXPORT_WRITER_FESOD_ROLLOUT.md`

用途：

1. 了解为什么默认 writer 切到 `fesod`
2. 了解为什么建议先上导出专用节点

### Fesod 发布检查清单

文件：

- `docs/EXPORT_WRITER_FESOD_RELEASE_CHECKLIST.md`

用途：

1. 发版前检查
2. 发版后观察
3. 回滚阈值与回滚动作

## 3. 配置类

### 默认配置

文件：

- `src/main/resources/application.yaml`

关键项：

1. `export.writer.type`
2. `export.concurrent.*`
3. `export.fesod.*`
4. `export.poi.*`

### 生产配置说明

文件：

- `src/main/resources/application-prod.yaml`

用途：

1. 明确 `prod`
2. 明确导出专用节点建议使用 `prod,export-worker`

### 导出专用节点 profile

文件：

- `src/main/resources/application-export-worker.yaml`

用途：

1. 导出专用节点保守基线
2. 大导出与在线节点隔离

## 4. 脚本与压测入口

### k6 脚本

文件：

- `perf/k6/export_async_flow.js`
- `perf/k6/README.md`

用途：

1. 真实 Web 链路导出压测
2. 支持 `session` / `bearer`
3. 支持 submit 拒绝率场景

## 5. 代码入口

### 主链路

1. `src/main/java/com/tiny/platform/infrastructure/export/service/ExportService.java`
2. `src/main/java/com/tiny/platform/infrastructure/export/service/ExportTaskService.java`
3. `src/main/java/com/tiny/platform/infrastructure/export/persistence/ExportTaskRepository.java`

### Provider

1. `src/main/java/com/tiny/platform/infrastructure/export/demo/DemoExportUsageDataProvider.java`

### Writer

1. `src/main/java/com/tiny/platform/infrastructure/export/writer/fesod/FesodWriterAdapter.java`
2. `src/main/java/com/tiny/platform/infrastructure/export/writer/poi/POIWriterAdapter.java`
3. `src/main/java/com/tiny/platform/infrastructure/export/config/ExportConfig.java`

## 6. 使用建议

后续排查时建议：

1. 先看本索引
2. 再看总指南
3. 如果是线上第一响应，先看 5 分钟排查清单
4. 如果要继续深挖，再看命令速查表
5. 如果要准备提交或评审，再看变更摘要
6. 如果是上线问题，再看发布方案和检查清单
7. 如果是代码问题，直接跳到本索引里的代码入口
