# 导出专项变更摘要（2026-03-06）

## 1. 文档目的

这份文档用于把本次导出专项改动整理成一份可直接用于：

1. 提交说明
2. PR 描述
3. 评审入口
4. 发布说明引用

它不替代完整指南；完整证据链、压测方法和排障 SOP 仍以：

1. `docs/EXPORT_INVESTIGATION_BENCHMARK_TROUBLESHOOTING_GUIDE.md`
2. `docs/EXPORT_DOCS_INDEX.md`

为准。

## 2. 建议标题

如果按一次导出专项提交，建议标题：

`feat(export): harden async export pipeline and switch default writer to fesod`

如果拆成两次提交，建议：

1. `feat(export): harden async export pipeline and writer implementations`
2. `docs(export): add benchmark, rollout and oncall guides`

## 3. 这次改动解决了什么

本次改动主要解决这些问题：

1. 异步导出并发限制有竞态，超限时会先接收任务再失败
2. 导出主链路存在“写完当前批后等待下一批 DB 返回”的空转
3. `DemoExportUsage` 使用 JPA Entity 导出，Hibernate 上下文对象参与热路径，导致 GC 自然回落差
4. `POI` 与 `Fesod` 在超大导出下都缺少完整的生产级验证口径
5. `Fesod` 之前不具备大导出必需的拆 sheet 能力
6. 线上值班缺少标准化的导出排障入口

## 4. 代码改动范围

### 4.1 导出主链路

关键文件：

1. `src/main/java/com/tiny/platform/infrastructure/export/service/ExportService.java`
2. `src/main/java/com/tiny/platform/infrastructure/export/service/ExportTaskService.java`
3. `src/main/java/com/tiny/platform/infrastructure/export/persistence/ExportTaskRepository.java`
4. `src/main/java/com/tiny/platform/infrastructure/export/service/ExportTaskCleanupScheduler.java`

关键改动：

1. 增加导出预取流水线和有界反压
2. 并发限制前移到提交阶段，超限直接拒绝
3. 提交并发控制改为原子获取槽位，修复竞态
4. 增加任务进度估算、`processedRows`、`totalRows`、`progress` 持久化
5. 增加任务恢复、任务清理和运行期指标
6. 增加 `[EXPORT_TRACE]`、`prefetch.progress`、`prefetch.summary` 观测日志

### 4.2 Provider 与查询层

关键文件：

1. `src/main/java/com/tiny/platform/infrastructure/export/demo/DemoExportUsageDataProvider.java`
2. `src/main/java/com/tiny/platform/application/oauth/export/UserDataProvider.java`

关键改动：

1. `DemoExportUsageDataProvider` 从 JPA Entity 导出切换为 `Tuple -> Map`
2. 全量导出使用 keyset 分页
3. 新增 `estimateTotal()` 和 count 查询
4. `UserDataProvider` 也使用 `FilterAwareDataProvider<Map<String,Object>>` 与 keyset 分页

### 4.3 Writer 与文件生成

关键文件：

1. `src/main/java/com/tiny/platform/infrastructure/export/writer/poi/POIWriterAdapter.java`
2. `src/main/java/com/tiny/platform/infrastructure/export/writer/fesod/FesodWriterAdapter.java`
3. `src/main/java/com/tiny/platform/infrastructure/export/service/SheetWriteModel.java`
4. `src/main/java/com/tiny/platform/infrastructure/export/config/ExportConfig.java`

关键改动：

1. `POI` 支持 `compress-temp-files` 配置化
2. `POI` 和 `Fesod` 都支持超出单 sheet 行上限后的自动拆 sheet
3. `Fesod` 接入独立配置：`batch-size`、`max-rows-per-sheet`
4. 默认 writer 切到 `fesod`
5. 修复 `SheetWriteModel` 构造器漏设 `sheetName`

### 4.4 导出支撑运行时

关键文件：

1. `src/main/java/com/tiny/platform/core/oauth/config/AsyncExecutionConfig.java`
2. `src/main/java/com/tiny/platform/core/oauth/config/HttpRequestLoggingProperties.java`
3. `src/main/java/com/tiny/platform/core/oauth/filter/HttpRequestLoggingFilter.java`

关键改动：

1. 默认 `@Async` 执行器改为队列满时直接拒绝，避免请求线程回退执行耗时任务
2. 对 `/export` 路径禁用响应体缓存包装，避免大文件导出时 `ContentCachingResponseWrapper` 把整包响应留在堆内

## 5. 配置改动

关键文件：

1. `src/main/resources/application.yaml`
2. `src/main/resources/application-prod.yaml`
3. `src/main/resources/application-export-worker.yaml`

关键配置：

1. `export.writer.type=fesod`
2. `export.concurrent.max-system=10`
3. `export.concurrent.max-user=3`
4. `export.fesod.batch-size=1024`
5. `export.poi.compress-temp-files=true`
6. 导出专用节点 profile：`prod,export-worker`

## 6. 测试与压测资产

### 6.1 Web 压测脚本

文件：

1. `perf/k6/export_async_flow.js`
2. `perf/k6/README.md`

能力：

1. 覆盖 `session` / `bearer`
2. 覆盖 `async` / `sync`
3. 覆盖 submit 拒绝率场景
4. 覆盖下载链路

### 6.2 单元测试与 benchmark

文件：

1. `src/test/java/com/tiny/platform/infrastructure/export/writer/poi/POIWriterAdapterTest.java`
2. `src/test/java/com/tiny/platform/infrastructure/export/benchmark/DemoExportUsageExportBenchmark.java`

覆盖点：

1. `POI` 拆 sheet
2. 拆 sheet 后 header 复用
3. `DemoExportUsage` 的 `100w / 1000w` 基准运行入口

## 7. 已执行的验证

### 7.1 编译验证

命令：

```bash
mvn -pl tiny-oauth-server -DskipTests compile
```

结果：

1. 编译通过

### 7.2 实际采用的工具

本次用于证据采集和结论判断的工具：

1. `k6`
2. `curl + jq`
3. `ps`
4. `jcmd`
5. `JFR`
6. `GC log`
7. `python3 + zipfile/xml`
8. `mysql`

这些工具分别用于：

1. 真实 Web 链路压测
2. 任务进度曲线采集
3. CPU / RSS 采样
4. JVM 堆与热点定位
5. 文件结构校验
6. 任务表与运行状态核对

## 8. 关键实测结论

### 8.1 数据规模

样本数据：

1. `recordTenantId=1`：`10,000,000`
2. `recordTenantId=2`：`100,000`
3. `recordTenantId=3`：`1,000,000`
4. 总表：`11,100,000`

### 8.2 导出规模验证

已完成：

1. `10w`
2. `100w`
3. `1000w`
4. `VUS=3`
5. `VUS=5`

### 8.3 `POI vs Fesod` 同口径结果

| 场景 | Fesod | POI | 结论 |
|---|---:|---:|---|
| `100w VUS=3` | `24.42s` | `32.48s` | `fesod` 更快 |
| `100w VUS=5` | `26.35s` | `30.31s` | `fesod` 更快 |
| `1000w VUS=3` | `146.85s` | `182.21s` | `fesod` 更快 |
| `1000w VUS=5` | `223.59s` | `225.86s` | 基本打平 |

统一结论：

1. `fesod` 当前可作为默认 writer 候选
2. 但 `1000w VUS=5` 已接近共同瓶颈区
3. 两者在超大文件尾段最终都落到 `POI/SXSSF` 问题域

### 8.4 GC 与尾段结论

关键结论：

1. 旧实现的高位不回落，主因不是硬泄漏，而是 JPA Entity 导出链路和 GC 触发时机叠加
2. `DemoExportUsageDataProvider` 去实体化后，自然回落明显改善
3. `1000w` 的尾段主要不是 DB，而是 `SXSSFWorkbook.write/finish`
4. `compress-temp-files=false` 能带来约 `8%~10%` 的收益，但会增加临时文件/内存压力

## 9. 文档交付物

本次新增或更新的文档：

1. `docs/README.md`
2. `docs/EXPORT_DOCS_INDEX.md`
3. `docs/EXPORT_INVESTIGATION_BENCHMARK_TROUBLESHOOTING_GUIDE.md`
4. `docs/EXPORT_ONCALL_5MIN_CHECKLIST.md`
5. `docs/EXPORT_ONCALL_COMMAND_RUNBOOK.md`
6. `docs/EXPORT_CHANGESET_SUMMARY_20260306.md`
7. `docs/EXPORT_WRITER_FESOD_ROLLOUT.md`
8. `docs/EXPORT_WRITER_FESOD_RELEASE_CHECKLIST.md`
9. `docs/认证文档索引.md`

用途分层：

1. 首页导航
2. 完整指南
3. 值班第一响应
4. 深度排障
5. 提交与评审摘要
6. 发布与回滚

## 10. 建议评审重点

评审时建议优先看这些点：

1. `ExportService` 的并发控制和提交流程是否满足预期
2. `DemoExportUsageDataProvider` 的查询方式是否破坏原有语义
3. `FesodWriterAdapter` 的拆 sheet、header、sum 行行为是否正确
4. `POIWriterAdapter` 的 `compress-temp-files` 行为是否保留回退能力
5. `HttpRequestLoggingFilter` 对 `/export` 的 passthrough 是否只影响导出路径
6. 默认 writer 切到 `fesod` 是否符合当前发布策略
7. `ExportTaskCleanupScheduler` 的清理节奏是否符合生产预期

## 11. 建议提交分组与 `git add` 清单

当前工作区里，这批改动适合至少拆成 `3` 组，不建议一次性把所有变更压成一个提交。

### 11.1 提交 A：导出主链路与运行时支撑

建议标题：

`feat(export): harden async export pipeline and writer implementations`

建议纳入文件：

1. `src/main/java/com/tiny/platform/infrastructure/export/config/ExportConfig.java`
2. `src/main/java/com/tiny/platform/infrastructure/export/demo/DemoExportUsageDataProvider.java`
3. `src/main/java/com/tiny/platform/infrastructure/export/persistence/ExportTaskRepository.java`
4. `src/main/java/com/tiny/platform/infrastructure/export/service/ExportService.java`
5. `src/main/java/com/tiny/platform/infrastructure/export/service/ExportTaskService.java`
6. `src/main/java/com/tiny/platform/infrastructure/export/service/ExportTaskCleanupScheduler.java`
7. `src/main/java/com/tiny/platform/infrastructure/export/service/SheetWriteModel.java`
8. `src/main/java/com/tiny/platform/infrastructure/export/writer/fesod/FesodWriterAdapter.java`
9. `src/main/java/com/tiny/platform/infrastructure/export/writer/poi/POIWriterAdapter.java`
10. `src/main/java/com/tiny/platform/application/oauth/export/UserDataProvider.java`
11. `src/main/java/com/tiny/platform/core/oauth/config/AsyncExecutionConfig.java`
12. `src/main/java/com/tiny/platform/core/oauth/config/HttpRequestLoggingProperties.java`
13. `src/main/java/com/tiny/platform/core/oauth/filter/HttpRequestLoggingFilter.java`
14. `src/main/resources/application.yaml`
15. `src/main/resources/application-prod.yaml`
16. `src/main/resources/application-export-worker.yaml`

建议命令：

```bash
git add \
  tiny-oauth-server/src/main/java/com/tiny/platform/infrastructure/export/config/ExportConfig.java \
  tiny-oauth-server/src/main/java/com/tiny/platform/infrastructure/export/demo/DemoExportUsageDataProvider.java \
  tiny-oauth-server/src/main/java/com/tiny/platform/infrastructure/export/persistence/ExportTaskRepository.java \
  tiny-oauth-server/src/main/java/com/tiny/platform/infrastructure/export/service/ExportService.java \
  tiny-oauth-server/src/main/java/com/tiny/platform/infrastructure/export/service/ExportTaskService.java \
  tiny-oauth-server/src/main/java/com/tiny/platform/infrastructure/export/service/ExportTaskCleanupScheduler.java \
  tiny-oauth-server/src/main/java/com/tiny/platform/infrastructure/export/service/SheetWriteModel.java \
  tiny-oauth-server/src/main/java/com/tiny/platform/infrastructure/export/writer/fesod/FesodWriterAdapter.java \
  tiny-oauth-server/src/main/java/com/tiny/platform/infrastructure/export/writer/poi/POIWriterAdapter.java \
  tiny-oauth-server/src/main/java/com/tiny/platform/application/oauth/export/UserDataProvider.java \
  tiny-oauth-server/src/main/java/com/tiny/platform/core/oauth/config/AsyncExecutionConfig.java \
  tiny-oauth-server/src/main/java/com/tiny/platform/core/oauth/config/HttpRequestLoggingProperties.java \
  tiny-oauth-server/src/main/java/com/tiny/platform/core/oauth/filter/HttpRequestLoggingFilter.java \
  tiny-oauth-server/src/main/resources/application.yaml \
  tiny-oauth-server/src/main/resources/application-prod.yaml \
  tiny-oauth-server/src/main/resources/application-export-worker.yaml
```

### 11.2 提交 B：测试、benchmark 与压测脚本

建议标题：

`test(export): add writer tests, benchmark and k6 coverage`

建议纳入文件：

1. `perf/k6/export_async_flow.js`
2. `perf/k6/README.md`
3. `src/test/java/com/tiny/platform/infrastructure/export/writer/poi/POIWriterAdapterTest.java`
4. `src/test/java/com/tiny/platform/infrastructure/export/benchmark/DemoExportUsageExportBenchmark.java`

建议命令：

```bash
git add \
  tiny-oauth-server/perf/k6/export_async_flow.js \
  tiny-oauth-server/perf/k6/README.md \
  tiny-oauth-server/src/test/java/com/tiny/platform/infrastructure/export/writer/poi/POIWriterAdapterTest.java \
  tiny-oauth-server/src/test/java/com/tiny/platform/infrastructure/export/benchmark/DemoExportUsageExportBenchmark.java
```

### 11.3 提交 C：文档、发布与值班排障

建议标题：

`docs(export): add benchmark, rollout and oncall guides`

建议纳入文件：

1. `docs/README.md`
2. `docs/EXPORT_DOCS_INDEX.md`
3. `docs/EXPORT_INVESTIGATION_BENCHMARK_TROUBLESHOOTING_GUIDE.md`
4. `docs/EXPORT_ONCALL_5MIN_CHECKLIST.md`
5. `docs/EXPORT_ONCALL_COMMAND_RUNBOOK.md`
6. `docs/EXPORT_CHANGESET_SUMMARY_20260306.md`
7. `docs/EXPORT_WRITER_FESOD_ROLLOUT.md`
8. `docs/EXPORT_WRITER_FESOD_RELEASE_CHECKLIST.md`
9. `docs/认证文档索引.md`

建议命令：

```bash
git add \
  tiny-oauth-server/docs/README.md \
  tiny-oauth-server/docs/EXPORT_DOCS_INDEX.md \
  tiny-oauth-server/docs/EXPORT_INVESTIGATION_BENCHMARK_TROUBLESHOOTING_GUIDE.md \
  tiny-oauth-server/docs/EXPORT_ONCALL_5MIN_CHECKLIST.md \
  tiny-oauth-server/docs/EXPORT_ONCALL_COMMAND_RUNBOOK.md \
  tiny-oauth-server/docs/EXPORT_CHANGESET_SUMMARY_20260306.md \
  tiny-oauth-server/docs/EXPORT_WRITER_FESOD_ROLLOUT.md \
  tiny-oauth-server/docs/EXPORT_WRITER_FESOD_RELEASE_CHECKLIST.md \
  "tiny-oauth-server/docs/认证文档索引.md"
```

### 11.4 如果只想做一次提交

不建议，但如果必须一次提交，至少按下面顺序先自检：

```bash
git add \
  tiny-oauth-server/src/main/java/com/tiny/platform/infrastructure/export \
  tiny-oauth-server/src/main/java/com/tiny/platform/application/oauth/export/UserDataProvider.java \
  tiny-oauth-server/src/main/java/com/tiny/platform/core/oauth/config/AsyncExecutionConfig.java \
  tiny-oauth-server/src/main/java/com/tiny/platform/core/oauth/config/HttpRequestLoggingProperties.java \
  tiny-oauth-server/src/main/java/com/tiny/platform/core/oauth/filter/HttpRequestLoggingFilter.java \
  tiny-oauth-server/src/main/resources/application.yaml \
  tiny-oauth-server/src/main/resources/application-prod.yaml \
  tiny-oauth-server/src/main/resources/application-export-worker.yaml \
  tiny-oauth-server/perf/k6 \
  tiny-oauth-server/src/test/java/com/tiny/platform/infrastructure/export \
  tiny-oauth-server/docs

git diff --cached --stat
```

## 12. 提交前建议核对

提交前建议至少执行：

```bash
git status --short
mvn -pl tiny-oauth-server -DskipTests compile
```

如果要补测试：

```bash
mvn -pl tiny-oauth-server -Dtest=POIWriterAdapterTest test
```

如果要补 Web 压测：

```bash
BASE_URL=http://127.0.0.1:9000 \
AUTH_MODE=session \
LOGIN_USERNAME=k6bench \
LOGIN_PASSWORD=k6pass \
LOGIN_TENANT_ID=1 \
FLOW=async \
TENANT_ID=2 \
VUS=1 \
DURATION=10s \
k6 run tiny-oauth-server/perf/k6/export_async_flow.js
```

每次 `git add` 后建议补一条：

```bash
git diff --cached --name-only
```

避免把当前工作区里不属于导出专项的文件一起带进去。

## 13. 建议实际提交顺序

如果按推荐方式拆 `3` 次提交，建议顺序如下。

### 13.1 第一步：导出主链路与运行时支撑

```bash
git add \
  tiny-oauth-server/src/main/java/com/tiny/platform/infrastructure/export/config/ExportConfig.java \
  tiny-oauth-server/src/main/java/com/tiny/platform/infrastructure/export/demo/DemoExportUsageDataProvider.java \
  tiny-oauth-server/src/main/java/com/tiny/platform/infrastructure/export/persistence/ExportTaskRepository.java \
  tiny-oauth-server/src/main/java/com/tiny/platform/infrastructure/export/service/ExportService.java \
  tiny-oauth-server/src/main/java/com/tiny/platform/infrastructure/export/service/ExportTaskService.java \
  tiny-oauth-server/src/main/java/com/tiny/platform/infrastructure/export/service/ExportTaskCleanupScheduler.java \
  tiny-oauth-server/src/main/java/com/tiny/platform/infrastructure/export/service/SheetWriteModel.java \
  tiny-oauth-server/src/main/java/com/tiny/platform/infrastructure/export/writer/fesod/FesodWriterAdapter.java \
  tiny-oauth-server/src/main/java/com/tiny/platform/infrastructure/export/writer/poi/POIWriterAdapter.java \
  tiny-oauth-server/src/main/java/com/tiny/platform/application/oauth/export/UserDataProvider.java \
  tiny-oauth-server/src/main/java/com/tiny/platform/core/oauth/config/AsyncExecutionConfig.java \
  tiny-oauth-server/src/main/java/com/tiny/platform/core/oauth/config/HttpRequestLoggingProperties.java \
  tiny-oauth-server/src/main/java/com/tiny/platform/core/oauth/filter/HttpRequestLoggingFilter.java \
  tiny-oauth-server/src/main/resources/application.yaml \
  tiny-oauth-server/src/main/resources/application-prod.yaml \
  tiny-oauth-server/src/main/resources/application-export-worker.yaml

git diff --cached --name-only
mvn -pl tiny-oauth-server -DskipTests compile
git commit -m "feat(export): harden async export pipeline and writer implementations"
```

### 13.2 第二步：测试、benchmark 与压测脚本

```bash
git add \
  tiny-oauth-server/perf/k6/export_async_flow.js \
  tiny-oauth-server/perf/k6/README.md \
  tiny-oauth-server/src/test/java/com/tiny/platform/infrastructure/export/writer/poi/POIWriterAdapterTest.java \
  tiny-oauth-server/src/test/java/com/tiny/platform/infrastructure/export/benchmark/DemoExportUsageExportBenchmark.java

git diff --cached --name-only
mvn -pl tiny-oauth-server -Dtest=POIWriterAdapterTest test
git commit -m "test(export): add writer tests, benchmark and k6 coverage"
```

### 13.3 第三步：文档、发布与值班排障

```bash
git add \
  tiny-oauth-server/docs/README.md \
  tiny-oauth-server/docs/EXPORT_DOCS_INDEX.md \
  tiny-oauth-server/docs/EXPORT_INVESTIGATION_BENCHMARK_TROUBLESHOOTING_GUIDE.md \
  tiny-oauth-server/docs/EXPORT_ONCALL_5MIN_CHECKLIST.md \
  tiny-oauth-server/docs/EXPORT_ONCALL_COMMAND_RUNBOOK.md \
  tiny-oauth-server/docs/EXPORT_CHANGESET_SUMMARY_20260306.md \
  tiny-oauth-server/docs/EXPORT_WRITER_FESOD_ROLLOUT.md \
  tiny-oauth-server/docs/EXPORT_WRITER_FESOD_RELEASE_CHECKLIST.md \
  "tiny-oauth-server/docs/认证文档索引.md"

git diff --cached --name-only
git commit -m "docs(export): add benchmark, rollout and oncall guides"
```

### 13.4 如果你要压成一个提交

建议标题：

`feat(export): optimize export pipeline and add rollout assets`

建议先执行：

```bash
git diff --stat
git diff --cached --stat
```

避免把当前工作区里与导出无关的文件一起带进去。

## 14. PR 描述模板

下面这段可以直接作为 PR 描述初稿使用。

```md
## 背景

本 PR 聚焦导出链路的稳定性、可观测性、大数据量能力以及 writer 选型落地。

## 本次改动

1. 加固异步导出主链路
   - 预取流水线
   - 有界反压
   - 提交前并发拒绝
   - 任务进度持久化
   - 任务恢复与清理
   - 导出运行指标与 EXPORT_TRACE 日志

2. 优化 provider 与查询路径
   - DemoExportUsage 从 JPA Entity 导出改为 Tuple/Map
   - 使用 keyset 分页
   - 增加 totalRows 估算

3. 补齐 writer 能力
   - POI / Fesod 自动拆 sheet
   - POI compress-temp-files 配置化
   - 默认 writer 切为 fesod
   - 保留 export.writer.type=poi 回退能力

4. 增强运行时支撑
   - @Async 执行器拒绝策略改为 AbortPolicy
   - /export 路径跳过响应体缓存包装

5. 补充测试、压测与运维资产
   - k6 Web 链路脚本
   - POI writer 单测
   - DemoExportUsage benchmark 入口
   - 发布、值班、排障文档

## 验证

- `mvn -pl tiny-oauth-server -DskipTests compile`
- `mvn -pl tiny-oauth-server -Dtest=POIWriterAdapterTest test`
- k6 真实 Web 链路验证 `10w / 100w / 1000w`
- `POI vs Fesod` 同口径对比
- JFR / GC / ps / mysql / curl+jq 交叉验证

## 结果摘要

- `fesod` 在 `100w` 和 `1000w VUS=3` 下明显优于 `poi`
- `1000w VUS=5` 下两者基本打平
- 大文件尾段主要瓶颈在 `POI/SXSSF write/finish`
- 去实体化后，GC 自然回落明显改善

## 风险与回滚

- 风险点：
  - 默认 writer 从 `poi` 切换到 `fesod`
  - 导出并发拒绝行为从“提交后失败”改为“提交阶段直接拒绝”

- 回滚方式：
  - `export.writer.type=poi`
  - 导出节点摘掉 `export-worker` profile
```

## 15. 关联文档

1. `docs/README.md`
2. `docs/EXPORT_DOCS_INDEX.md`
3. `docs/EXPORT_INVESTIGATION_BENCHMARK_TROUBLESHOOTING_GUIDE.md`
4. `docs/EXPORT_ONCALL_5MIN_CHECKLIST.md`
5. `docs/EXPORT_ONCALL_COMMAND_RUNBOOK.md`
6. `docs/EXPORT_CHANGESET_SUMMARY_20260306.md`
7. `docs/EXPORT_WRITER_FESOD_ROLLOUT.md`
8. `docs/EXPORT_WRITER_FESOD_RELEASE_CHECKLIST.md`
