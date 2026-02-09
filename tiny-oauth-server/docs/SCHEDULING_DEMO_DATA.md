# 调度中心示例数据与验证

## 说明

`data.sql` 中已包含一套调度示例数据，用于在**任务类型 → 任务管理 → DAG 管理 → 运行历史**之间完整流转并保证前端可正常查看。

依赖顺序：**任务类型** → **任务** → **DAG** → **DAG 版本** → **DAG 节点** → **DAG 边** → **DAG 运行** → **任务实例** → **任务历史**。

## 一、加载示例数据

### 方式 1：首次启动或重建库时自动加载

- 若使用 Spring Boot 默认 `schema.sql` + `data.sql` 初始化（如 H2/部分配置），启动应用时会自动执行 `data.sql`，示例数据会一并插入。
- 若使用外部 MySQL 且通过 Flyway/Liquibase 等管理数据，请将 `data.sql` 中「调度中心示例数据」整段在目标库中执行一次。

### 方式 2：在已有库中单独执行

在 MySQL 客户端或工具中执行 `tiny-oauth-server/src/main/resources/data.sql` 中从：

```sql
-- ========================================================================
-- 调度中心示例数据（DAG 管理 → 任务类型 → 任务 → 计划/版本/节点 → 运行历史）
```

到该段结束（含 `scheduling_task_history` 的 INSERT）的所有语句。  
使用 `INSERT IGNORE`，重复执行不会报错，仅会跳过已存在记录。

## 二、自动化验证

### 2.1 CI 自动执行（无需人工介入）

推送到 `main` 或发起针对 `main` 的 Pull Request 时，GitHub Actions 会自动执行调度示例数据验证：

- 工作流：`.github/workflows/verify-scheduling-demo.yml`
- 流程：启动 MySQL 8 → 构建应用 → 启动应用（执行 Liquibase 与 data.sql）→ 运行验证脚本
- 通过即表示示例数据与链路正常；失败会在 PR/Commit 状态中体现。

也可在 GitHub 仓库的 Actions 页手动触发 “Verify scheduling demo data”。

### 2.2 本地一键验证（推荐，不依赖 GitHub Actions）

在**本机已启动 MySQL**（默认 localhost:3306、用户 root、库 tiny_web）且已安装 JDK、Maven、mysql 客户端时，在**仓库根目录**执行一条命令即可完成：构建 → 启动应用（执行 Liquibase + data.sql）→ 运行验证 → 停止应用。

```bash
# 默认连接（localhost:3306, root, 无密码, tiny_web）
./scripts/verify-scheduling-demo-local.sh

# 有密码时
export SCHEDULING_VERIFY_DB_PASSWORD=你的密码
./scripts/verify-scheduling-demo-local.sh
```

无需 GitHub Actions，全程在本地完成。

### 2.3 仅运行验证脚本（数据库已有数据时）

在**已执行过 Liquibase（含 data.sql）且 MySQL 可连**的前提下，只跑验证检查时可在仓库根目录执行：

```bash
# 使用默认连接（localhost:3306, 用户 root, 库 tiny_web；无密码）
./tiny-oauth-server/scripts/verify-scheduling-demo.sh

# 或指定连接与 mysql 客户端路径（不把密码写进命令行时可 export）
export SCHEDULING_VERIFY_MYSQL_BIN=/path/to/mysql/bin/mysql   # 可选，未设置时使用 PATH 中的 mysql
export SCHEDULING_VERIFY_DB_HOST=localhost
export SCHEDULING_VERIFY_DB_PORT=3306
export SCHEDULING_VERIFY_DB_USER=root
export SCHEDULING_VERIFY_DB_PASSWORD=你的密码
export SCHEDULING_VERIFY_DB_NAME=tiny_web
./tiny-oauth-server/scripts/verify-scheduling-demo.sh
```

脚本会先检测能否连接数据库、表 `scheduling_task_type` 是否存在，再依次检查：任务类型 DEMO_SHELL、示例任务 2 条、DAG demo_dag、ACTIVE 版本、节点数 ≥2、边 ≥1、运行 RUN-DEMO-001、任务实例与历史 ≥2。全部通过即说明示例数据完整，可进行前端验证。  
本地或自建 CI 中执行时，请通过环境变量传入数据库连接信息，并确保在验证前已完成建表与 `data.sql` 执行（如 Liquibase 已跑完）。

## 三、验证步骤（前端）

1. **登录**  
   使用具备「调度中心」菜单权限的账号（如 admin）登录。

2. **任务类型**  
   - 进入 **调度中心 → 任务类型**。  
   - 应能看到一条：**示例Shell任务**（编码 `DEMO_SHELL`）。

3. **任务管理**  
   - 进入 **调度中心 → 任务管理**。  
   - 应能看到两条：**示例任务A-准备**（`demo_task_a`）、**示例任务B-执行**（`demo_task_b`），类型均为上述任务类型。

4. **DAG 管理**  
   - 进入 **调度中心 → DAG 管理**。  
   - 应能看到一条：**示例DAG计划**（编码 `demo_dag`），启用状态。  
   - 点击该行的 **详情**：应能看到版本 v1（ACTIVE）、两个节点（准备节点、执行节点）及边（node_a → node_b）。

5. **运行历史**  
   - 进入 **调度中心 → 运行历史**。  
   - 在「选择 DAG」中选中 **示例DAG计划**（或从 DAG 列表点击某 DAG 的「历史」带 `?dagId=1` 进入）。  
   - 应能看到一条运行记录：**RUN-DEMO-001**，状态 **SUCCESS**，触发类型 **MANUAL**，触发人 **admin**。  
   - 点击该行的 **查看详情** / **节点记录**：应能看到两个节点（node_a、node_b）的执行记录与状态。

按以上步骤可确认：任务类型、任务、DAG 计划（含版本与节点）与运行历史在前端均能正常流转与展示。

## 四、示例数据 ID 一览（便于排查）

| 表 | 示例 ID / 编码 |
|----|----------------|
| scheduling_task_type | id=1, code=DEMO_SHELL |
| scheduling_task | id=1 demo_task_a, id=2 demo_task_b |
| scheduling_dag | id=1, code=demo_dag |
| scheduling_dag_version | id=1, dag_id=1, version_no=1, status=ACTIVE |
| scheduling_dag_task | id=1 node_a, id=2 node_b |
| scheduling_dag_edge | id=1, node_a→node_b |
| scheduling_dag_run | id=1, run_no=RUN-DEMO-001, status=SUCCESS |
| scheduling_task_instance | id=1 node_a, id=2 node_b |
| scheduling_task_history | id=1,2 对应上述两节点 |

所有示例数据使用 `tenant_id = 1`。若当前登录租户与后端过滤逻辑按租户过滤，请确保请求带上的租户 ID 为 1，或在不传租户时后端会返回该数据（视当前接口实现而定）。
