# Export Writer Fesod Release Checklist

## 范围

适用于：

1. 默认 writer 已切到 `fesod`
2. 生产先以 `prod,export-worker` 方式灰度
3. `poi` 仍保留回退

关联文档：

- `docs/EXPORT_WRITER_FESOD_ROLLOUT.md`
- `docs/EXPORT_INVESTIGATION_BENCHMARK_TROUBLESHOOTING_GUIDE.md`
- `src/main/resources/application-export-worker.yaml`

## 发布前

### 配置检查

1. 确认默认 writer 已为 `fesod`
   - `src/main/resources/application.yaml`
2. 确认导出专用节点 profile 存在
   - `src/main/resources/application-export-worker.yaml`
3. 确认回退开关仍可用

```yaml
export:
  writer:
    type: poi
```

### 数据与任务检查

1. 检查当前是否有运行中导出任务
2. 若有运行中任务，不要切 writer 后直接重启
3. 先让运行中任务排空，再做发布

建议检查项：

1. `tiny.export.runtime.running == 0`
2. `tiny.export.executor.queue.size == 0`

### 节点资源检查

导出专用节点最低要求建议：

1. 本地临时目录，不要用网络盘
2. 可用磁盘空间足够容纳导出临时文件和结果文件
3. 对 `1000w` 级任务，至少预留 `20GB` 临时空间预算
4. JVM 堆和系统内存不要与在线业务节点混用到极限

## 发布动作

### 灰度启动

```bash
SPRING_PROFILES_ACTIVE=prod,export-worker \
java -jar tiny-oauth-server/target/tiny-oauth-server-1.0.0.jar
```

### 首轮验证

发布后至少做两轮人工验证：

1. `100w` 异步导出一轮
2. `1000w` 异步导出一轮

验证项：

1. 提交成功
2. 任务能到 `SUCCESS`
3. 文件可下载
4. `sheet` 数量、命名、行数正确

## 发布后观察

### Metrics

重点观察：

1. `tiny.export.async.submit.total`
2. `tiny.export.async.success.total`
3. `tiny.export.async.failed.total`
4. `tiny.export.async.rejected.total`
5. `tiny.export.runtime.running`
6. `tiny.export.executor.queue.size`
7. `tiny.export.executor.active.count`
8. `tiny.export.duration.ms`

建议直接查：

```bash
curl -s http://127.0.0.1:9000/actuator/metrics/tiny.export.async.success.total
curl -s http://127.0.0.1:9000/actuator/metrics/tiny.export.async.failed.total
curl -s http://127.0.0.1:9000/actuator/metrics/tiny.export.executor.queue.size
curl -s http://127.0.0.1:9000/actuator/metrics/tiny.export.runtime.running
```

### 日志

关键日志前缀：

```text
[EXPORT_TRACE]
```

重点 phase：

1. `submitAsync`
2. `performExport`
3. `runTask.success`
4. `runTask.failed`
5. `prefetch.progress`
6. `prefetch.summary`

## 通过标准

灰度节点满足以下条件才继续放量：

1. `tiny.export.async.failed.total` 没有连续增长
2. `tiny.export.async.rejected.total` 没有异常抬升
3. `tiny.export.executor.queue.size` 不长期堆高
4. `100w` 样本导出持续成功
5. `1000w` 样本导出持续成功
6. 没有新的用户侧下载投诉或超时投诉

## 立即回滚条件

满足任一项即可回滚：

1. `tiny.export.async.failed.total` 明显高于切换前基线
2. 导出结果错误、缺 sheet、缺行、命名错误
3. 导出任务长时间卡在 `RUNNING/99%` 且无法恢复
4. 导出节点把在线业务节点资源拖垮
5. 业务侧出现集中下载失败

## 回滚动作

### 方式 A：切回 POI

```bash
SPRING_APPLICATION_JSON='{"export":{"writer":{"type":"poi"}}}' \
java -jar tiny-oauth-server/target/tiny-oauth-server-1.0.0.jar
```

### 方式 B：移除 export-worker profile

```bash
SPRING_PROFILES_ACTIVE=prod \
java -jar tiny-oauth-server/target/tiny-oauth-server-1.0.0.jar
```

## 回滚后确认

1. 再做一轮 `100w` 导出
2. 再做一轮 `1000w` 导出
3. 确认 `tiny.export.async.failed.total` 不再继续异常增长
4. 确认 `runTask.failed` 日志恢复到正常水平
