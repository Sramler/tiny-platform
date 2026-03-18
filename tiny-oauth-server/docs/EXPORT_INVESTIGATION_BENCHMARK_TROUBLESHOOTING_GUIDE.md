# 导出链路排查、压测、选型与生产排障指南

## 1. 文档目的

这份文档沉淀的是本次围绕导出链路做过的完整工作，目标不是只给结论，而是提供一套后续可重复执行的流程。

索引入口：

- `docs/EXPORT_DOCS_INDEX.md`
- `docs/EXPORT_ONCALL_5MIN_CHECKLIST.md`
- `docs/EXPORT_ONCALL_COMMAND_RUNBOOK.md`

覆盖内容：

1. 做了哪些改动
2. 用了哪些工具
3. 收集了哪些信息
4. 这些信息分别证明了什么
5. 遇到过哪些问题
6. 后续线上出现导出问题时，应该按什么顺序排查
7. 线上值班时有哪些可直接执行的命令
8. 线上第一响应时 5 分钟内先做什么

适用范围：

1. Java Web 异步导出
2. `DemoExportUsage` 样本数据链路
3. `POI` / `Fesod` 两套 writer 对比
4. 大数据量 `10w / 100w / 1000w`

## 2. 这次排查的总体思路

本次排查不是只盯“导出慢”，而是按下面顺序做：

1. 先确认测试是不是基于真实 Web 链路
2. 再确认数据集规模和样本是否真实
3. 再看导出主链路的代码行为
4. 再看堆内存、GC、线程、DB、文件写出、下载
5. 最后再做 writer 选型和生产发布方案

核心原则：

1. 只看堆内存不够，必须联动 GC
2. 只看接口 RT 不够，必须拆 submit / poll / build / write / download
3. 只看单次样本不够，必须做 `100w / 1000w` 和 `VUS=3/5` 对照
4. 只看单一 writer 不够，必须做 `POI vs Fesod` 同口径对比

## 3. 代码入口与当前关键实现

关键代码入口：

1. 导出主服务：`src/main/java/com/tiny/platform/infrastructure/export/service/ExportService.java`
2. 样本 provider：`src/main/java/com/tiny/platform/infrastructure/export/demo/DemoExportUsageDataProvider.java`
3. POI writer：`src/main/java/com/tiny/platform/infrastructure/export/writer/poi/POIWriterAdapter.java`
4. Fesod writer：`src/main/java/com/tiny/platform/infrastructure/export/writer/fesod/FesodWriterAdapter.java`
5. writer 选择配置：`src/main/java/com/tiny/platform/infrastructure/export/config/ExportConfig.java`
6. sheet 模型：`src/main/java/com/tiny/platform/infrastructure/export/service/SheetWriteModel.java`
7. 默认配置：`src/main/resources/application.yaml`
8. 导出专用节点配置：`src/main/resources/application-export-worker.yaml`

当前关键实现点：

1. 导出支持 `poi|fesod` 配置切换
2. 默认 writer 已切到 `fesod`
3. `DemoExportUsageDataProvider` 已从 JPA Entity 导出改成 `Tuple -> Map`
4. `ExportService` 已支持预取流水线、有界反压、提交前并发拒绝、进度持久化
5. `FesodWriterAdapter` 和 `POIWriterAdapter` 都支持超出单 sheet 行上限后的自动拆 sheet

## 4. 本次实际做过的代码改动

### 4.1 导出主链路

在 `ExportService` 中完成了这些关键修正：

1. 增加运行时并发控制和指标埋点
2. 将异步并发限制前移到提交阶段
3. 增加导出线程池队列饱和拒绝
4. 增加任务恢复逻辑
5. 增加 `tiny.export.*` 指标
6. 增加 `[EXPORT_TRACE]` 观测日志

关键位置：

1. 指标与 Gauge：`ExportService.java:72-131`
2. 异步提交：`ExportService.java:163-190`
3. 并发控制：`ExportService.java:337-380`
4. 总行数估算与进度：`ExportService.java:510-533`
5. 进度上报：`ExportService.java:584-629`
6. 预取流水线：`ExportService.java:637-760`

### 4.2 Provider 去实体化

`DemoExportUsageDataProvider` 从“导出 JPA Entity”改为“导出 Tuple/Map”，目的是：

1. 避免 Hibernate 一阶缓存把大量实体带进导出热路径
2. 避免 `StatefulPersistenceContext` / `IdentityHashMap` / `ResultsConsumer` 等长活对象
3. 避免因为 Entity 查询导致 GC 自然回落差

关键位置：

1. provider 类型：`DemoExportUsageDataProvider.java:35`
2. `fetchIterator()`：`DemoExportUsageDataProvider.java:99-140`
3. `estimateTotal()`：`DemoExportUsageDataProvider.java:143-164`
4. keyset 查询：`DemoExportUsageDataProvider.java:265-289`
5. count 查询：`DemoExportUsageDataProvider.java:316-329`

### 4.3 Writer 能力补齐

`FesodWriterAdapter` 不是一开始就具备大导出验证条件，这次补了：

1. sheet 自动拆分
2. 合计行写入
3. 顶部信息和表头复用
4. 迭代器 finally close

关键位置：

1. 多 sheet 写入：`FesodWriterAdapter.java:69-89`
2. 按 part 拆 sheet：`FesodWriterAdapter.java:92-141`
3. 打开 sheet：`FesodWriterAdapter.java:159-169`

`POIWriterAdapter` 当前支持：

1. `rowAccessWindowSize`
2. `maxRowsPerSheet`
3. `compressTempFiles`

关键位置：

1. `compressTempFiles`：`POIWriterAdapter.java:42-45`
2. `writeMultiSheet()`：`POIWriterAdapter.java:48-113`

### 4.4 其它修正

1. `SheetWriteModel` 修复了 `sheetName` 构造器漏赋值
   - `SheetWriteModel.java:25-33`
2. `application.yaml` 默认 writer 已切 `fesod`
3. 增加 `application-export-worker.yaml`

## 5. 数据集与样本设计

本次本地样本表为 `demo_export_usage`，最终形成的数据分布：

1. `recordTenantId=1`：`10,000,000`
2. `recordTenantId=2`：`100,000`
3. `recordTenantId=3`：`1,000,000`
4. 全表合计：`11,100,000`

为什么这样做：

1. `recordTenantId=2` 用于 `10w`
2. `recordTenantId=3` 用于 `100w`
3. `recordTenantId=1` 用于 `1000w`

这样可以同时保证：

1. 底表足够大
2. 三个档位可控
3. 每次导出不需要额外复杂筛选条件

## 6. 工具清单与它们收集的信息

### 6.1 k6

脚本：

- `perf/k6/export_async_flow.js`

用途：

1. 真实 Web 链路压测
2. 走 `/csrf -> /login -> /export/async -> /export/task/{id} -> /download`
3. 记录：
   - `export_submit_ms`
   - `export_end_to_end_ms`
   - `export_poll_roundtrip_ms`
   - `export_download_ms`
   - `submit/task/download` 成功率

为什么必须用：

1. 这次排查的重点是 WebApp 访问后端的真实导出行为
2. 不是只测 service 方法
3. 可以覆盖 session、csrf、轮询、下载流、客户端耗时

### 6.2 curl + jq

用途：

1. 构建单任务精细观测脚本
2. 获取 `taskId`
3. 轮询任务状态
4. 生成 `progress curve csv`
5. 精确定位：
   - `processedRows` 到满值的时刻
   - `progress=99` 到 `SUCCESS=100` 的尾段时间

为什么必须用：

1. `k6` 适合并发矩阵
2. `curl + jq` 更适合单任务逐秒观察
3. 能直接把任务状态持久化成 CSV 供后续分析

### 6.3 ps

用途：

1. 每秒采集 Java 进程：
   - `%CPU`
   - `%MEM`
   - `RSS`

为什么必须用：

1. 快
2. 足够做导出期间 CPU/RSS 走势判断
3. 能和 `k6` 的 wall time 对齐

### 6.4 jcmd / JFR / GC log

用途：

1. `GC.heap_info`
2. `GC.run`
3. `JFR.start / JFR.stop`
4. 观察：
   - 自然 GC 是否触发
   - 强制 GC 后是否明显回落
   - 尾段热点是不是 `Deflater/Inflater/SXSSFWorkbook.write()`

为什么必须用：

1. 只看 RSS 不能判断泄漏
2. 只看 heap 曲线不能判断“为什么不回落”
3. JFR 是定位尾段热点的硬证据

### 6.5 Python / zipfile / XML 解析

用途：

1. 校验导出文件的 worksheet 数量
2. 校验 sheet name
3. 校验最终产物是否符合预期

为什么必须用：

1. 大导出不仅要“跑完”
2. 还要确认文件结构正确

### 6.6 mvn

用途：

1. `compile`
2. `package`
3. `spring-boot:repackage`

为什么必须用：

1. 临时独立 `9001` 实例需要 `java -jar`
2. 之前未重打包时，jar 不具备可执行 manifest

## 7. 本次保留的重要实测产物

### 7.1 单任务与 GC/JFR 产物

示例：

1. `/tmp/export_async_10w_20260306.json`
2. `/tmp/export_async_100w_20260306.json`
3. `/tmp/export_async_1000w_20260306.json`
4. `/tmp/export_1000w_progress_curve_20260306.csv`
5. `/tmp/export_1000w_poi_tail_20260306.jfr`
6. `/tmp/export_1000w_poi_tail_no_compress_20260306.jfr`
7. `/tmp/fesod_1000w_tail_retry_20260306.jfr`
8. `/tmp/export_100k_vus5_jfr_20260306.jfr`
9. `/tmp/export_100k_vus5_gc_20260306.log`

### 7.2 Fesod 参数与并发矩阵

1. `/tmp/fesod_batchsize_100w_20260306.csv`
2. `/tmp/fesod-k6-matrix/`

### 7.3 POI 并发矩阵

1. `/tmp/poi-k6-matrix/`

说明：

这些路径是本轮本机执行产物，不是项目长期资源。  
它们的价值在于给后续问题排查提供“上一轮真实样本”。

## 8. 本次做过的关键测试

### 8.1 单任务 Web 链路

验证目标：

1. 走真实登录和下载链路
2. 得到 `10w / 100w / 1000w` 单任务端到端耗时

结果：

1. `10w`：约 `4.15s`
2. `100w`：约 `36.3s`
3. `1000w`：约 `229s`

结论：

1. 当前链路能稳定完成 `1000w`
2. 大规模场景瓶颈不在 submit，不在 download 建链，而在文件生成尾段

### 8.2 GC 与自然回落分析

目的：

1. 判断是不是泄漏
2. 判断为什么“导出完内存看起来回不去”

方法：

1. 跑 `100w x VUS=5`
2. 记录 GC log
3. 记录 JFR
4. 空闲等待 `t+60 / t+120`
5. 再执行一次 `GC.run`

结论：

1. 不是硬泄漏
2. 大量对象在导出结束后没有新的 GC 触发条件
3. 强制 GC 后内存能明显回落
4. `DemoExportUsage` 旧的 Entity 查询链路确实放大了长活对象

### 8.3 POI 尾段分析

目的：

定位为什么 `1000w` 在 `processedRows` 满值后还要额外几十秒。

方法：

1. 跑 `1000w`
2. 记录 `progress=99 -> success=100` 的时间
3. 用 JFR 看尾段热点

结论：

1. 尾段主要耗在 `SXSSFWorkbook.write()`
2. 主要热点是：
   - `Deflater.deflateBytesBytes`
   - `Inflater.inflateBytesBytes`
   - `SXSSFWorkbook.copyStreamAndInjectWorksheet`
3. 根因不是 DB
4. 根因是 SXSSF 的最终注入、压缩、封包

### 8.4 `compressTempFiles` 对比

目的：

确认 `POI` 的 `compressTempFiles` 是否值得关闭。

结果：

1. `compress=true`
   - 端到端 `235823 ms`
2. `compress=false`
   - 端到端 `212925 ms`

结论：

1. `false` 能带来约 `8%~10%` 提升
2. 代价是更高的内存和临时文件压力
3. 适合导出专用节点，不适合无脑全局默认

### 8.5 Fesod 1000w 单任务验证

目的：

确认 `Fesod` 不是纸面实现，是真能跑通 `1000w` 的。

结果：

1. 能产出 `10` 个 worksheet
2. sheet 拆分真实生效
3. `1000w` 路径成功完成并可下载

### 8.6 Fesod 尾段 JFR

目的：

验证 `Fesod` 是否绕开了 `POI` 的尾段瓶颈。

结论：

1. 没有绕开
2. `Fesod` 最终 `finish()` 仍然落到 `POI/SXSSF`
3. 热点仍然是：
   - `Deflater`
   - `OpcOutputStream`
   - `SXSSFWorkbook.copyStreamAndInjectWorksheet`

这意味着：

1. `Fesod` 不是全新底层
2. 它和 `POI` 共享相同的尾段问题域

### 8.7 Fesod `batch-size` Sweep

目的：

找 `Fesod` 自己的更合适批大小。

结果：

`100w` sweep：

1. `512`：慢
2. `1024`：快
3. `2048`：和 `1024` 几乎持平
4. `4096`：开始退化

随后补了 `1000w` 对照：

1. `1024`：`128.86s`
2. `2048`：`135.21s`

最终结论：

1. 默认仍保留 `1024`
2. `2048` 没有形成稳定优势

### 8.8 POI vs Fesod 并发矩阵

统一口径：

1. `100w VUS=3`
2. `100w VUS=5`
3. `1000w VUS=3`
4. `1000w VUS=5`

`Fesod`：

1. `100w VUS=3`：`24.42s`
2. `100w VUS=5`：`26.35s`
3. `1000w VUS=3`：`146.85s`
4. `1000w VUS=5`：`223.59s`

`POI`：

1. `100w VUS=3`：`32.48s`
2. `100w VUS=5`：`30.31s`
3. `1000w VUS=3`：`182.21s`
4. `1000w VUS=5`：`225.86s`

结论：

1. `100w` 和 `1000w VUS=3` 下，`Fesod` 明显更快
2. `1000w VUS=5` 下，两者几乎打平
3. 说明极限场景最终仍然被共同的尾段瓶颈限制

## 9. 本次遇到过的问题与处理方式

### 9.1 测试最初不是完整 Web 环境

问题：

1. 只测 service 方法，不符合 WebApp 真实访问链路

处理：

1. 切到 `k6`
2. 强制使用 `/csrf -> /login -> async submit -> poll -> download`

### 9.2 TOTP 登录流程干扰压测

问题：

1. 会话登录可能跳转 `totp-bind`
2. 影响压测脚本稳定性

处理：

1. 使用专门测试账号
2. 固定 `User-Agent`
3. 让测试账号避免 TOTP 绑定提醒干扰

### 9.3 `/actuator/health` 不适合作为就绪探针

问题：

1. 该服务存在租户上下文要求
2. 健康检查不一定能作为无状态 readiness

处理：

1. 临时实例启动后改用 `/csrf` 检查就绪

### 9.4 `java -jar` 启不来

问题：

1. 之前 jar 不是 repackage 后的可执行 boot jar

处理：

1. 执行 `spring-boot:repackage`

### 9.5 curl 登录 403

问题：

1. `/csrf` 拿 token 时没有把 cookie jar 带到 `/login`

处理：

1. `curl -c/-b` 统一使用同一个 cookie 文件

### 9.6 k6 任务其实完成了，但脚本不退出

问题：

1. 为了防止同一 VU 重复发起任务，把 `THINK_TIME_SECONDS` 设得太大
2. 结果任务完成后还在 sleep

处理：

1. 对单轮矩阵改成 `DURATION=1s + THINK_TIME_SECONDS=0`
2. 让 `k6` 在导出真正完成后立即退出

### 9.7 Fesod 没有生产可用的大导出能力

问题：

1. 早期版本没有自动拆 sheet
2. `sheetName` 存在模型层 bug

处理：

1. 补了拆 sheet
2. 修复 `SheetWriteModel` 构造器漏赋值

### 9.8 进度不动

问题：

1. `totalRows` 常为 `null`
2. `progress` 长期 `0`

处理：

1. 补 `estimateTotal()`
2. 把 filters 正确下发给 `FilterAwareDataProvider`
3. 让尾段停在 `99%`，最终成功再到 `100%`

### 9.9 并发限制有竞态

问题：

1. 校验并发和注册运行中任务不是原子操作

处理：

1. 加锁
2. 变成原子“检查 + 占位”
3. 并把拒绝前移到提交阶段

## 10. 当前确定下来的工程结论

### 10.1 默认 writer 结论

当前更推荐：

1. 默认 writer 用 `fesod`
2. `poi` 保留为回退

原因：

1. 在多数场景下 `fesod` 更快
2. `100w` 和 `1000w VUS=3` 的优势比较明显
3. 但极限大并发场景不具备绝对碾压优势，所以不能直接删掉 `poi`

### 10.2 生产部署结论

推荐：

1. 先用导出专用节点
2. 使用 `prod,export-worker`
3. 保守单并发跑 `1000w`

对应文件：

1. `docs/EXPORT_WRITER_FESOD_ROLLOUT.md`
2. `docs/EXPORT_WRITER_FESOD_RELEASE_CHECKLIST.md`
3. `src/main/resources/application-export-worker.yaml`

## 11. 生产问题排查 SOP

下面这套流程优先级固定，不建议跳着查。

### 第一步：先确定问题类型

先分清楚是哪一类：

1. 提交失败
2. 提交成功但任务失败
3. 任务一直 `RUNNING`
4. `processedRows` 满了但长时间不 `SUCCESS`
5. 文件下载慢
6. 导出成功但文件错误
7. 导出后内存高位不回落

### 第二步：先看任务记录与指标

先看：

1. `tiny.export.runtime.running`
2. `tiny.export.executor.queue.size`
3. `tiny.export.async.failed.total`
4. `tiny.export.async.rejected.total`
5. `tiny.export.duration.ms`

如果：

1. `runtime.running` 很高
2. `queue.size` 很高
3. `rejected.total` 同步涨

说明优先排查：

1. 并发配置
2. 线程池队列
3. 导出节点负载

### 第三步：看 `[EXPORT_TRACE]`

按 phase 看：

1. `submitAsync`
2. `prefetch.progress`
3. `prefetch.summary`
4. `performExport`
5. `runTask.success`
6. `runTask.failed`

判断方法：

1. `prefetch.summary` 很早结束，`runTask.success` 很晚出现
   - 说明瓶颈更偏 writer 尾段
2. `prefetch.progress` 本身就很慢
   - 更偏 DB / provider / 映射阶段
3. `runTask.failed` 增多
   - 先看异常分类和堆栈，不要先猜性能

### 第四步：拆 DB、写文件、下载三段

如果是单任务：

1. 用 `curl + jq` 轮询任务
2. 记录：
   - 首次 `progress=99`
   - 首次 `processedRows=totalRows`
   - `SUCCESS=100`

判断：

1. `processedRows` 很早满，但 `SUCCESS` 很晚
   - 不是 DB，优先查 writer 尾段
2. `processedRows` 增长本身就慢
   - 优先查 DB / provider
3. `SUCCESS` 很快，但下载很慢
   - 优先查代理、网络、客户端

### 第五步：看 CPU / RSS / GC

最低成本先做：

1. `ps -p <pid> -o pcpu=,pmem=,rss=`

再做 JVM 级判断：

1. `jcmd <pid> GC.heap_info`
2. 空闲等待
3. 再 `jcmd <pid> GC.run`
4. 再看一次 `GC.heap_info`

判断：

1. 强制 GC 后明显回落
   - 更像 GC 时机问题，不像硬泄漏
2. 强制 GC 后也不回落
   - 再怀疑强引用链、缓存、线程残留、类加载、堆外

### 第六步：尾段问题才上 JFR

只有出现这类情况再上 JFR：

1. `99% -> 100%` 很慢
2. `processedRows` 早就满了但还卡很久
3. CPU 高但 DB 看不出问题

重点看栈：

1. `Deflater`
2. `Inflater`
3. `SXSSFWorkbook.write`
4. `SXSSFWorkbook.copyStreamAndInjectWorksheet`
5. `OpcOutputStream`
6. `FileInputStream.readBytes`

### 第七步：判断是不是 writer 选型问题

如果：

1. `100w` 场景普遍慢
2. `1000w VUS=3` 慢
3. CPU 明显高

可以对比：

1. `poi`
2. `fesod`

但要记住：

1. `Fesod` 不是完全绕开 `POI`
2. 极限场景仍然会回到共同的 SXSSF 尾段瓶颈

## 12. 后续排查时建议保留的最小工具集

建议后续每次线上排查都准备这几样：

1. `perf/k6/export_async_flow.js`
2. 一套 `curl + jq` 单任务脚本
3. `ps` 采样脚本
4. `jcmd` 命令清单
5. 一个无 TOTP 干扰的导出测试账号

## 13. 最终建议

对于当前项目，建议长期保持下面这套策略：

1. 默认 writer：`fesod`
2. 回退 writer：`poi`
3. `1000w` 级任务优先走导出专用节点
4. 问题排查按本文流程固定顺序执行
5. 每次重大改动后都至少补一轮：
   - `100w VUS=3`
   - `1000w VUS=3`
   - 单任务 `1000w` 尾段观察

这份文档不是一次性报告，而是后续导出问题的标准排查入口。
