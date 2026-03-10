# 调度模块真前后端联动 E2E 设计

## 1. 目标

本文设计一条可执行的“真前后端联动”调度 E2E 主链路，用于覆盖：

- 真实前端页面操作
- 真实后端调度 API
- 真实 MySQL / Quartz / Liquibase
- 真实 OIDC 登录态
- DAG 编排中的并行、串行、归并

本设计不再采用 `page.route()` 拦截调度接口的方式，而是让前端直接访问真实后端。

---

## 2. 当前约束

当前仓库中的 Playwright E2E 配置见 [playwright.config.ts](/Users/bliu/code/tiny-platform/tiny-oauth-server/src/main/webapp/playwright.config.ts)，默认只启动前端 Vite。

这意味着现有 E2E 更接近“前端集成测试”，不是真正的前后端联动。

要实现真联动，必须同时具备：

1. 前端 Vite 服务
2. 后端 `tiny-oauth-server`
3. MySQL 测试库
4. Liquibase 初始化
5. 真实 OIDC 登录态

另外，当前调度相关变更集大量使用 `dbms: mysql`，因此不建议用 H2 冒充生产链路。

---

## 3. 主场景

### 3.1 场景 A：并行统计 -> 归并汇总

这是第一条必须落地的真 E2E。

#### DAG 定义

- DAG 编码：`sales_report_pipeline`
- DAG 名称：`销售报表流水线`
- 节点：
  - `user_stat`
  - `order_stat`
  - `merge_report`
- 边：
  - `user_stat -> merge_report`
  - `order_stat -> merge_report`

#### 业务语义

- `user_stat` 与 `order_stat` 为两个并行根节点
- `merge_report` 为归并节点
- 只有两个统计节点都成功后，`merge_report` 才允许进入执行

#### 任务定义

- 任务类型 `REPORT_STAT`
  - 执行器：`delayTaskExecutor`
- 任务类型 `REPORT_SUMMARY`
  - 执行器：`loggingTaskExecutor`

- 任务 `report_user`
  - 参数：`{"delayMs":2000,"message":"用户统计"}`
- 任务 `report_order`
  - 参数：`{"delayMs":1500,"message":"订单统计"}`
- 任务 `report_summary`
  - 参数：`{"message":"日报汇总完成","step":"summary"}`

#### 必须验证的断言

1. DAG 列表中 `currentVersionId` 存在时才能触发。
2. 触发后会创建新的 run。
3. `user_stat` 和 `order_stat` 会先被创建成节点实例。
4. 在任一统计节点未完成前，`merge_report` 不得开始执行。
5. 两个统计节点都完成后，`merge_report` 才会被释放。
6. 最终 run 状态为 `SUCCESS`。
7. 历史页统计卡片 `total/success/avg/p95/p99` 与运行结果一致。

### 3.2 场景 B：串行统计 -> 最终归并

这是第二条补齐编排语义的 E2E。

#### DAG 定义

- DAG 编码：`serial_sales_pipeline`
- 节点：
  - `extract`
  - `normalize`
  - `aggregate`
  - `finalize`
- 边：
  - `extract -> normalize`
  - `normalize -> aggregate`
  - `aggregate -> finalize`

#### 必须验证的断言

1. 任一时刻只释放当前阶段之后的一个节点。
2. 不存在并行根节点。
3. 上游未完成时，下游节点不能提前执行。
4. 运行历史中节点执行顺序与链路顺序一致。

---

## 4. 认证方案

真联动 E2E 不应该继续使用 fake JWT。

原因见：

- [request.ts](/Users/bliu/code/tiny-platform/tiny-oauth-server/src/main/webapp/src/utils/request.ts)
- [auth.ts](/Users/bliu/code/tiny-platform/tiny-oauth-server/src/main/webapp/src/auth/auth.ts)
- [oidc.ts](/Users/bliu/code/tiny-platform/tiny-oauth-server/src/main/webapp/src/auth/oidc.ts)

前端请求会主动附带：

- `Authorization: Bearer <token>`
- `X-Tenant-Id`

因此推荐做法是：

### 4.1 Playwright setup 登录

新增一个专门的登录准备脚本，例如：

- `src/main/webapp/e2e/setup/auth.setup.ts`

职责：

1. 打开 `/login`
2. 输入真实租户编码，例如 `tiny`
3. 输入真实用户名/密码
4. 完成后端登录页与 OIDC 回调
5. 将登录后的浏览器状态保存为 `storageState`

后续调度 E2E 复用该 `storageState`，而不是每条用例重复登录。

### 4.2 为什么不用前端注入 localStorage

现有 `export-task.spec.ts` 中的 `seedAuthenticatedSession` 适合 mock E2E，不适合真联动。

因为浏览器里伪造出来的 token 无法通过后端真实校验。

---

## 5. 数据准备方案

建议使用“SQL 种子 + UI 验证”的混合方案。

### 5.1 不建议全部走 UI 建模

如果任务类型、任务、DAG、版本、节点、边全部通过页面逐步创建：

- 用例太长
- 对非关键 UI 波动过于敏感
- 难以快速稳定地搭建拓扑

### 5.2 推荐做法

测试前通过 SQL 直接种好 DAG，再用前端页面完成“触发 -> 查看历史 -> 查看节点 -> 操作”的验证。

建议新增：

- `scripts/e2e/seed-scheduling-orchestration.sql`

该脚本至少负责：

1. 插入任务类型
2. 插入任务
3. 插入 DAG
4. 插入 ACTIVE 版本
5. 插入节点与边
6. 清理上次 run/history/audit

如果想兼容当前示例体系，也可以在 [SCHEDULING_TASK_EXAMPLES.md](/Users/bliu/code/tiny-platform/tiny-oauth-server/docs/SCHEDULING_TASK_EXAMPLES.md) 现有示例基础上追加专用 E2E 数据。

---

## 6. Playwright 规格设计

### 6.1 配置拆分

建议新增一个真联动配置，而不是直接污染现有 mock E2E：

- `src/main/webapp/playwright.real.config.ts`

职责：

1. 启动前端 Vite
2. 启动后端 Spring Boot
3. 指向真实 API 基地址
4. 复用登录后的 `storageState`

### 6.2 后端启动建议

建议使用 CI profile：

- [application-ci.yaml](/Users/bliu/code/tiny-platform/tiny-oauth-server/src/main/resources/application-ci.yaml)

后端启动命令可设计为：

```bash
mvn -pl tiny-oauth-server spring-boot:run -Dspring-boot.run.profiles=ci
```

前提：

- 本地或 CI 已启动 MySQL
- `tiny_web` 库可用
- `MYSQL_ROOT_PASSWORD` 已注入

### 6.3 规格文件

建议新增：

- `src/main/webapp/e2e/scheduling-dag-orchestration.spec.ts`

结构拆为两个 `describe`：

1. `parallel fan-out and merge`
2. `serial pipeline`

### 6.4 主用例步骤

#### 用例 1：并行统计归并

1. 载入已登录 `storageState`
2. 打开 `/scheduling/dag`
3. 定位 `sales_report_pipeline`
4. 点击“触发”
5. 跳转到历史页
6. 断言最新 run 出现
7. 打开“节点记录”
8. 轮询直到两个统计节点都完成
9. 断言 `merge_report` 之后才进入运行
10. 断言整条 run 成功
11. 断言历史页统计卡片变化正确

#### 用例 2：串行流水线

1. 打开历史页
2. 触发 `serial_sales_pipeline`
3. 观察节点记录
4. 断言节点按 `extract -> normalize -> aggregate -> finalize` 顺序推进
5. 断言没有两个串行节点同时处于运行态

---

## 7. 页面断言建议

建议优先使用当前页面已有的稳定文案：

### DAG 列表页

文件：

- [Dag.vue](/Users/bliu/code/tiny-platform/tiny-oauth-server/src/main/webapp/src/views/scheduling/Dag.vue)

可断言：

- `DAG 列表`
- `当前版本ID`
- `运行中`
- `可重试`
- `触发`
- `停止 DAG`
- `重试最近失败运行`

### 历史页

文件：

- [DagHistory.vue](/Users/bliu/code/tiny-platform/tiny-oauth-server/src/main/webapp/src/views/scheduling/DagHistory.vue)

可断言：

- `运行历史列表`
- `运行统计`
- `节点记录`
- `停止本次`
- `重试本次`
- `暂停本节点`
- `恢复本节点`
- `重试本节点`

---

## 8. 扩展覆盖顺序

在主场景跑通后，按这个顺序扩展：

1. 并行归并成功链路
2. 串行链路成功链路
3. 并行链路中一个分支失败 -> run 失败
4. 失败 run 的“重试本次”
5. 运行中的“停止本次”
6. 节点级“暂停/恢复/重试”

不要一开始就把所有失败态都堆到第一条真 E2E 里，否则会让排障成本过高。

---

## 9. 执行命令建议

### 9.1 启动数据库

由本地或 CI 负责，保证 MySQL 中存在 `tiny_web`。

### 9.2 启动后端

```bash
cd /Users/bliu/code/tiny-platform
mvn -pl tiny-oauth-server spring-boot:run -Dspring-boot.run.profiles=ci
```

### 9.3 启动前端真联动 E2E

建议后续新增：

```bash
npx playwright test -c /Users/bliu/code/tiny-platform/tiny-oauth-server/src/main/webapp/playwright.real.config.ts
```

### 9.4 测试前置

执行种子脚本：

```bash
mysql -uroot -p"$MYSQL_ROOT_PASSWORD" tiny_web < /Users/bliu/code/tiny-platform/tiny-oauth-server/scripts/e2e/seed-scheduling-orchestration.sql
```

---

## 10. 验收标准

这条真联动 E2E 可以认为合格，至少要满足：

1. 不使用 `page.route()` mock 调度接口。
2. 前端通过真实登录获得可用 token。
3. DAG 触发、历史、节点记录全部走真实后端。
4. 能稳定验证“并行根节点 + 归并节点”的释放时机。
5. 能稳定验证“串行链”的顺序执行。

---

## 11. 风险

### 风险 1：登录链路较长

真实 OIDC 登录比 mock E2E 慢，建议用 `storageState` 降低重复成本。

### 风险 2：Quartz / Worker 有异步波动

断言不能只靠固定 sleep，必须用轮询等待状态收敛。

### 风险 3：环境依赖高

真联动 E2E 依赖：

- MySQL
- 后端
- 前端
- OIDC 登录配置

因此应单独作为一套 suite，不与纯前端 mock E2E 混跑。
