# Export Writer Fesod Rollout

## 目标

- 默认 writer 切换为 `fesod`
- 保留 `poi` 回退开关
- 先在导出专用节点灰度，再决定是否扩大到全部节点

关联排查与压测指南：

- `docs/EXPORT_INVESTIGATION_BENCHMARK_TROUBLESHOOTING_GUIDE.md`

## 当前默认值

- 默认 writer: `fesod`
- 默认配置入口: `src/main/resources/application.yaml`
- writer Bean 缺省值入口: `src/main/java/com/tiny/platform/infrastructure/export/config/ExportConfig.java`

## 推荐发布方式

### 1. 先上导出专用节点

使用叠加 profile：

```bash
SPRING_PROFILES_ACTIVE=prod,export-worker \
java -jar tiny-oauth-server/target/tiny-oauth-server-1.0.0.jar
```

专用 profile 文件：

- `src/main/resources/application-export-worker.yaml`

这份配置是保守基线：

- `export.writer.type=fesod`
- `export.concurrent.max-system=1`
- `export.concurrent.max-user=1`
- `export.executor.core-pool-size=1`
- `export.executor.max-pool-size=1`
- `export.executor.queue-capacity=5`
- `export.executor.queue-reject-threshold=3`
- `export.fesod.batch-size=1024`

适用前提：

- 该节点主要承接大导出
- 允许把 `1000w` 级任务按单并发运行

### 2. 灰度顺序

按这个顺序，不要直接全量切：

1. 单台导出节点开启 `prod,export-worker`
2. 观察 `1-2` 天
3. 再扩到全部导出节点
4. 最后再决定是否让混部节点也默认使用 `fesod`

### 3. 放量门槛

至少满足下面这些再扩容：

1. `tiny.export.async.failed.total` 没有异常抬升
2. `tiny.export.async.rejected.total` 符合预期，没有持续飙升
3. `tiny.export.runtime.running` 长期不超过配置上限
4. `tiny.export.executor.queue.size` 没有长期堆高
5. `tiny.export.executor.active.count` 与并发配置匹配，没有卡死
6. `100w` 和 `1000w` 样本任务都能稳定完成

## 运行中重点观察

### Metrics

导出服务当前真实埋点：

- `tiny.export.sync.total`
- `tiny.export.async.submit.total`
- `tiny.export.async.success.total`
- `tiny.export.async.failed.total`
- `tiny.export.async.rejected.total`
- `tiny.export.runtime.running`
- `tiny.export.executor.queue.size`
- `tiny.export.executor.active.count`
- `tiny.export.duration.ms`

### 日志

导出关键日志统一带：

- `"[EXPORT_TRACE] phase=..."`

重点看这些 phase：

- `submitAsync`
- `performExport`
- `runTask.success`
- `runTask.failed`
- `prefetch.progress`
- `prefetch.summary`

异常信号：

1. `prefetch.summary` 很快结束，但 `runTask.success` 很晚才出现
2. `runTask.failed` 持续增加
3. `queue.size` 长期高位，且 `async.rejected.total` 同步上升

## 回滚方案

### 方案 A：配置回滚到 POI

最直接：

```yaml
export:
  writer:
    type: poi
```

或启动时临时覆盖：

```bash
SPRING_APPLICATION_JSON='{"export":{"writer":{"type":"poi"}}}' \
java -jar tiny-oauth-server/target/tiny-oauth-server-1.0.0.jar
```

### 方案 B：摘掉 export-worker profile

如果当前只在导出专用节点启用了 `prod,export-worker`，直接移除 `export-worker` 即可回到普通生产配置。

## 重要边界

### 1. 不要在任务堆积时切 writer

`ExportService` 在启动时会恢复待处理/卡住的任务。  
这意味着：

1. 任务是按当前运行实例的 writer 继续执行
2. 如果切换 writer 后再重启，未完成任务可能会由新 writer 接手

所以更稳的做法是：

1. 先停止新提交
2. 等 `tiny.export.runtime.running=0`
3. 再切 writer 并重启

### 2. 大导出不要和在线节点混部硬扛

根据实测：

1. `100w` 场景 `fesod` 明显优于 `poi`
2. `1000w` 场景 `VUS=3` 下 `fesod` 也明显更快
3. 但 `1000w VUS=5` 时两者差距已经收敛，瓶颈回到共同的尾段封包链路

所以：

- `fesod` 适合作为默认候选
- 但 `1000w` 级任务仍建议放在独立导出节点

## 建议决策

1. 默认配置保留 `fesod`
2. 发布时先走 `prod,export-worker`
3. `poi` 保留至少一个发布周期作为回退实现
4. 等线上观测稳定后，再决定是否在全部节点统一默认 `fesod`
