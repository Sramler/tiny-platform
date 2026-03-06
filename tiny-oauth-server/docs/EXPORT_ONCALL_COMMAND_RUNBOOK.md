# 导出值班命令速查表

## 1. 适用场景

适用于下面这些情况：

1. 导出提交失败
2. 导出任务长时间 `RUNNING`
3. `processedRows` 很早满了，但任务很晚才 `SUCCESS`
4. 导出后内存高位不回落
5. 文件下载慢或集中失败

优先配套阅读：

1. `docs/EXPORT_DOCS_INDEX.md`
2. `docs/EXPORT_INVESTIGATION_BENCHMARK_TROUBLESHOOTING_GUIDE.md`

## 2. 先准备变量

```bash
export APP_PORT=9000
export APP_LOG=/var/log/oauth-server/application.log
export MYSQL_DB=tiny_web
```

如果是本机开发环境：

```bash
export APP_PORT=9000
export APP_LOG=/tmp/oauth-server.log
export MYSQL_DB=tiny_web
```

## 3. 找进程与当前配置

### 找 Java 进程

```bash
lsof -iTCP:${APP_PORT} -sTCP:LISTEN -n -P
```

记下 `PID` 后：

```bash
export APP_PID=<pid>
```

### 看关键系统属性

```bash
jcmd ${APP_PID} VM.system_properties | rg 'spring.profiles.active|java.io.tmpdir|user.dir'
```

重点看：

1. 是否启用了 `prod,export-worker`
2. `java.io.tmpdir` 指到哪里

## 4. 先看导出指标

如果 `/actuator/metrics` 对内可直接访问：

```bash
curl -s http://127.0.0.1:${APP_PORT}/actuator/metrics/tiny.export.async.submit.total
curl -s http://127.0.0.1:${APP_PORT}/actuator/metrics/tiny.export.async.success.total
curl -s http://127.0.0.1:${APP_PORT}/actuator/metrics/tiny.export.async.failed.total
curl -s http://127.0.0.1:${APP_PORT}/actuator/metrics/tiny.export.async.rejected.total
curl -s http://127.0.0.1:${APP_PORT}/actuator/metrics/tiny.export.runtime.running
curl -s http://127.0.0.1:${APP_PORT}/actuator/metrics/tiny.export.executor.queue.size
curl -s http://127.0.0.1:${APP_PORT}/actuator/metrics/tiny.export.executor.active.count
curl -s http://127.0.0.1:${APP_PORT}/actuator/metrics/tiny.export.duration.ms
```

重点判断：

1. `failed.total` 是否持续涨
2. `rejected.total` 是否异常涨
3. `runtime.running` 是否长期不降
4. `queue.size` 是否长期堆高

## 5. 查任务表

### 最近任务

```bash
mysql -D ${MYSQL_DB} -e \"\n+SELECT task_id, user_id, status, progress, total_rows, processed_rows, error_code, created_at, updated_at\n+FROM export_task\n+ORDER BY id DESC\n+LIMIT 20;\n+\"\n+```

### 查某个任务

```bash
export TASK_ID=<task_id>
mysql -D ${MYSQL_DB} -e \"\n+SELECT task_id, user_id, username, status, progress, total_rows, processed_rows,\n+       sheet_count, file_path, error_code, error_msg, worker_id, attempt,\n+       last_heartbeat, created_at, updated_at\n+FROM export_task\n+WHERE task_id='${TASK_ID}';\n+\"\n+```

### 查长时间运行任务

```bash
mysql -D ${MYSQL_DB} -e \"\n+SELECT task_id, status, progress, total_rows, processed_rows, TIMESTAMPDIFF(SECOND, updated_at, NOW()) AS idle_seconds,\n+       last_heartbeat, worker_id\n+FROM export_task\n+WHERE status IN ('PENDING','RUNNING')\n+ORDER BY updated_at ASC;\n+\"\n+```

判断方法：

1. `processed_rows` 长时间不动：更偏 DB / provider / 线程池
2. `processed_rows=total_rows` 但还没 `SUCCESS`：更偏 writer 尾段
3. `error_code` 稳定重复：先看业务/代码错误，不要先猜性能

## 6. 看日志

### 快速看导出日志

```bash
rg -n '\\[EXPORT_TRACE\\]|runTask.failed|runTask.success|prefetch.summary|prefetch.progress' ${APP_LOG} | tail -n 200
```

### 看某个任务

```bash
rg -n \"${TASK_ID}|\\[EXPORT_TRACE\\]\" ${APP_LOG} | tail -n 200
```

重点看 phase：

1. `submitAsync`
2. `prefetch.progress`
3. `prefetch.summary`
4. `performExport`
5. `runTask.success`
6. `runTask.failed`

常见判断：

1. `prefetch.summary` 很早结束，但 `runTask.success` 很晚出现
   - 更偏 writer 尾段
2. `prefetch.progress` 本身就很慢
   - 更偏 DB / provider
3. `runTask.failed`
   - 先看异常堆栈和 `error_code`

## 7. 看系统资源

### 单点查看

```bash
ps -p ${APP_PID} -o pid=,pcpu=,pmem=,rss=,etime=,command=
```

### 连续采样

```bash
while true; do\n+  ts=$(date '+%F %T')\n+  ps -p ${APP_PID} -o pcpu=,pmem=,rss= | awk -v ts=\"$ts\" '{gsub(/^ +| +$/, \"\", $0); print ts \",\" $1 \",\" $2 \",\" $3}'\n+  sleep 2\n+done\n+```

重点判断：

1. CPU 高但 `processed_rows` 不涨：优先查锁、压缩、写文件
2. CPU 低但任务不动：优先查 DB、线程池、阻塞
3. RSS 长期高位：再联动 GC 判断，不要直接判泄漏

## 8. 看 JVM 堆与 GC

### 堆信息

```bash
jcmd ${APP_PID} GC.heap_info
```

### class 直方图

```bash
jcmd ${APP_PID} GC.class_histogram | head -n 80
```

### 强制 GC 后再看一次

```bash
jcmd ${APP_PID} GC.run
sleep 3
jcmd ${APP_PID} GC.heap_info
```

判断方法：

1. 强制 GC 后明显回落
   - 更像 GC 时机问题，不像硬泄漏
2. 强制 GC 后仍几乎不回落
   - 再怀疑缓存、强引用链、堆外、线程残留

## 9. 任务尾段专项排查

适用于：

1. `processed_rows` 已经满
2. 但 `SUCCESS` 很久不来

先用任务表确认：

```bash
mysql -D ${MYSQL_DB} -e \"\n+SELECT task_id, status, progress, total_rows, processed_rows, updated_at\n+FROM export_task\n+WHERE task_id='${TASK_ID}';\n+\"\n+```

如果已经 `processed_rows=total_rows`：

1. 优先怀疑 writer 尾段
2. 不要再先查 DB

### 开短时 JFR

```bash
jcmd ${APP_PID} JFR.start name=export-tail settings=profile filename=/tmp/export-tail.jfr duration=120s
```

结束后：

```bash
jcmd ${APP_PID} JFR.check
```

重点看：

1. `Deflater`
2. `Inflater`
3. `SXSSFWorkbook.write`
4. `SXSSFWorkbook.copyStreamAndInjectWorksheet`
5. `OpcOutputStream`

## 10. 文件正确性快速校验

### 看 worksheet 数

```bash
python3 - <<'PY' /path/to/export.xlsx
import sys, zipfile
zf = zipfile.ZipFile(sys.argv[1])
print(len([n for n in zf.namelist() if n.startswith('xl/worksheets/sheet') and n.endswith('.xml')]))
PY
```

### 看 sheet name

```bash
python3 - <<'PY' /path/to/export.xlsx
import sys, zipfile, xml.etree.ElementTree as ET
zf = zipfile.ZipFile(sys.argv[1])
root = ET.fromstring(zf.read('xl/workbook.xml'))
ns = {'a':'http://schemas.openxmlformats.org/spreadsheetml/2006/main'}
print('|'.join(s.attrib['name'] for s in root.find('a:sheets', ns)))
PY
```

## 11. Web 链路快速复现

### k6 单轮验证

```bash
BASE_URL=http://127.0.0.1:${APP_PORT} \
AUTH_MODE=session \
LOGIN_USERNAME=k6bench \
LOGIN_PASSWORD=k6pass \
LOGIN_TENANT_ID=1 \
USER_AGENT=TinyPerfK6/1.0 \
FLOW=async \
TENANT_ID=3 \
PAGE_SIZE=5000 \
VUS=1 \
DURATION=1s \
GRACEFUL_STOP=1800s \
MAX_POLL_SECONDS=900 \
POLL_INTERVAL_SECONDS=2 \
THINK_TIME_SECONDS=0 \
DOWNLOAD_ON_SUCCESS=true \
k6 run /Users/bliu/code/tiny-platform/tiny-oauth-server/perf/k6/export_async_flow.js
```

### 并发复现

`100w VUS=3`

```bash
BASE_URL=http://127.0.0.1:${APP_PORT} \
AUTH_MODE=session \
LOGIN_USERNAME=k6bench \
LOGIN_PASSWORD=k6pass \
LOGIN_TENANT_ID=1 \
USER_AGENT=TinyPerfK6/1.0 \
FLOW=async \
TENANT_ID=3 \
PAGE_SIZE=5000 \
VUS=3 \
DURATION=1s \
GRACEFUL_STOP=7200s \
MAX_POLL_SECONDS=900 \
POLL_INTERVAL_SECONDS=2 \
THINK_TIME_SECONDS=0 \
DOWNLOAD_ON_SUCCESS=true \
k6 run /Users/bliu/code/tiny-platform/tiny-oauth-server/perf/k6/export_async_flow.js
```

## 12. 导出 writer 快速确认与回退

### 看当前 writer

优先看启动参数或配置文件：

```bash
jcmd ${APP_PID} VM.system_properties | rg 'spring.profiles.active'
```

然后确认配置：

1. `src/main/resources/application.yaml`
2. `src/main/resources/application-export-worker.yaml`

### 临时回退到 POI

```bash
SPRING_APPLICATION_JSON='{\"export\":{\"writer\":{\"type\":\"poi\"}}}' \
java -jar tiny-oauth-server/target/tiny-oauth-server-1.0.0.jar
```

### 使用导出专用节点 profile

```bash
SPRING_PROFILES_ACTIVE=prod,export-worker \
java -jar tiny-oauth-server/target/tiny-oauth-server-1.0.0.jar
```

## 13. 值班判断顺序

严格按这个顺序：

1. 先看任务表
2. 再看 `tiny.export.*` 指标
3. 再看 `[EXPORT_TRACE]`
4. 再看 `ps`
5. 再看 `jcmd GC.heap_info`
6. 只有确认是尾段问题，才开 JFR

不要反过来先上 JFR 或先改参数。
