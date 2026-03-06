# 导出生产值班 5 分钟排查清单

## 1. 适用目标

适用于线上先快速判断问题落点，不适用于替代完整分析。

使用原则：

1. 先判断问题在哪一层
2. 先判断是否需要止血或回滚
3. 不要一上来就抓大而全的 JFR

配套文档：

1. `docs/EXPORT_DOCS_INDEX.md`
2. `docs/EXPORT_ONCALL_COMMAND_RUNBOOK.md`
3. `docs/EXPORT_INVESTIGATION_BENCHMARK_TROUBLESHOOTING_GUIDE.md`

## 2. 5 分钟执行顺序

### 第 1 步：确认任务和实例

```bash
export APP_PORT=9000
export MYSQL_DB=tiny_web
export APP_LOG=/var/log/oauth-server/application.log

lsof -iTCP:${APP_PORT} -sTCP:LISTEN -n -P
```

拿到 `PID` 后：

```bash
export APP_PID=<pid>
jcmd ${APP_PID} VM.system_properties | rg 'spring.profiles.active|java.io.tmpdir'
```

确认：

1. 当前是不是导出专用节点
2. 当前是不是 `fesod` 还是 `poi`
3. `tmpdir` 是否落在预期磁盘

### 第 2 步：先看任务表，不要先猜

```bash
mysql -D ${MYSQL_DB} -e "
SELECT task_id, status, progress, total_rows, processed_rows, error_code, updated_at
FROM export_task
ORDER BY id DESC
LIMIT 20;
"
```

如果已有 `TASK_ID`：

```bash
export TASK_ID=<task_id>
mysql -D ${MYSQL_DB} -e "
SELECT task_id, status, progress, total_rows, processed_rows, error_code, error_msg,
       worker_id, last_heartbeat, updated_at
FROM export_task
WHERE task_id='${TASK_ID}';
"
```

快速判断：

1. `FAILED`：先看 `error_code/error_msg`
2. `processed_rows` 不涨：先怀疑 DB / provider / executor
3. `processed_rows=total_rows` 但还没 `SUCCESS`：先怀疑 writer 尾段

### 第 3 步：看导出指标

```bash
curl -s http://127.0.0.1:${APP_PORT}/actuator/metrics/tiny.export.async.failed.total
curl -s http://127.0.0.1:${APP_PORT}/actuator/metrics/tiny.export.async.rejected.total
curl -s http://127.0.0.1:${APP_PORT}/actuator/metrics/tiny.export.runtime.running
curl -s http://127.0.0.1:${APP_PORT}/actuator/metrics/tiny.export.executor.queue.size
curl -s http://127.0.0.1:${APP_PORT}/actuator/metrics/tiny.export.executor.active.count
```

快速判断：

1. `failed.total` 持续涨：先看失败堆栈和业务错误
2. `rejected.total` 持续涨：先看并发限制和队列容量
3. `queue.size` 长时间高位：先看线程池和大任务堆积

### 第 4 步：看日志 phase

```bash
rg -n '\\[EXPORT_TRACE\\]|runTask.failed|runTask.success|prefetch.summary|prefetch.progress' ${APP_LOG} | tail -n 200
```

如果有 `TASK_ID`：

```bash
rg -n "${TASK_ID}|\\[EXPORT_TRACE\\]" ${APP_LOG} | tail -n 200
```

快速判断：

1. `prefetch.progress` 慢：先查 DB / provider
2. `prefetch.summary` 早结束但 `runTask.success` 很晚：先查 writer 尾段
3. `runTask.failed`：先处理异常，不要先调参数

### 第 5 步：看 CPU / RSS / 堆

```bash
ps -p ${APP_PID} -o pid=,pcpu=,pmem=,rss=,etime=,command=
jcmd ${APP_PID} GC.heap_info
```

必要时：

```bash
jcmd ${APP_PID} GC.class_histogram | head -n 80
```

快速判断：

1. CPU 高、`processed_rows` 不涨：更偏压缩/写文件/锁
2. CPU 低、任务不动：更偏 DB/线程阻塞
3. 堆高位但没有证据：先不要直接判泄漏

## 3. 常见问题落点

### A. 提交直接失败

先看：

1. `tiny.export.async.rejected.total`
2. `queue.size`
3. 并发限制配置

### B. 任务长时间 RUNNING，但行数不动

先看：

1. `prefetch.progress`
2. SQL / provider / 连接池
3. 线程池是否占满

### C. 行数已满，但迟迟不 SUCCESS

先看：

1. writer 尾段
2. `SXSSFWorkbook.write/finish`
3. 临时目录磁盘和写出速度

### D. 导出后内存高位

先看：

1. `GC.heap_info`
2. 是否自然 GC 不触发
3. 必要时 `GC.run` 后再判断

## 4. 什么时候升级到深度分析

满足任一条件再进深度分析：

1. 连续失败或拒绝超过阈值
2. `RUNNING` 任务明显堆积
3. `processed_rows=total_rows` 后尾段异常拉长
4. 强制 GC 后内存仍不回落

升级动作：

1. 打开 `docs/EXPORT_ONCALL_COMMAND_RUNBOOK.md`
2. 按标准顺序抓任务表、日志、指标、`ps`
3. 最后再开短时 `JFR`

## 5. 需要立刻记录的现场信息

值班时至少记录这些：

1. `task_id`
2. `status/progress/total_rows/processed_rows`
3. `tiny.export.async.failed.total`
4. `tiny.export.async.rejected.total`
5. `tiny.export.runtime.running`
6. `tiny.export.executor.queue.size`
7. `APP_PID`、`spring.profiles.active`、`java.io.tmpdir`
8. 一条对应任务的日志片段

## 6. 立即止血和回滚

如果确认是 `fesod` 路径异常，优先动作：

1. 切回 `export.writer.type=poi`
2. 保留现有任务与日志现场
3. 不要先删任务表记录

如果确认是大导出压垮混部节点，优先动作：

1. 只保留导出专用节点承接大任务
2. 降低系统并发和线程池
3. 再决定是否摘掉 `export-worker` profile
