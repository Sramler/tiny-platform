# tiny-platform Testing Playbook

> 适用范围：tiny-platform 全仓库  
> 目标：把测试分层、E2E、认证、多租户、编排、CI 对接标准化，供人和 AI 工具共用。  
> 规则来源：以 [50-testing.rules.md](../.agent/src/rules/50-testing.rules.md)、[58-cicd.rules.md](../.agent/src/rules/58-cicd.rules.md)、[90-tiny-platform.rules.md](../.agent/src/rules/90-tiny-platform.rules.md)、[91-tiny-platform-auth.rules.md](../.agent/src/rules/91-tiny-platform-auth.rules.md) 为准。

配套模板：
- [TINY_PLATFORM_TESTING_PR_CHECKLIST.md](./TINY_PLATFORM_TESTING_PR_CHECKLIST.md)
- [TINY_PLATFORM_AI_TEST_TASK_TEMPLATE.md](./TINY_PLATFORM_AI_TEST_TASK_TEMPLATE.md)
- [testing-task-examples/README.md](./testing-task-examples/README.md)
- [TINY_PLATFORM_CI_WAIVER_POLICY.md](./TINY_PLATFORM_CI_WAIVER_POLICY.md)
- [TINY_PLATFORM_WAIVER_REVIEWER_TEMPLATE.md](./TINY_PLATFORM_WAIVER_REVIEWER_TEMPLATE.md)
- [GITHUB_ACTIONS_REAL_E2E_GUIDE.md](./GITHUB_ACTIONS_REAL_E2E_GUIDE.md)

## 1. 什么时候补什么测试

| 变更类型 | 最低要求 | 常见补充 |
| --- | --- | --- |
| 纯逻辑 / 工具类 / 校验函数 | 单元测试 | 参数化测试 |
| Vue 组件交互 / 表单 / 权限按钮 | 组件测试 | E2E |
| Controller -> Service -> Repository | 集成测试 | MySQL/Testcontainers |
| 鉴权 / 租户 / OIDC / MFA | 集成测试 + real-link E2E | 拒绝路径 |
| 编排 / 工作流 / 调度 / 统计 DAG | 拓扑语义测试 | real-link E2E |
| Liquibase / MySQL 方言 / 唯一约束 | 真实数据库验证 | migration smoke |
| 关键用户主路径 | smoke 或 E2E | shared-env smoke / nightly |

最低原则：
- 不允许只靠人工验证关闭 bug。
- 不允许只补 getter/setter 或只补“接口 200”型测试冒充回归。
- 风险越跨边界，测试层级越要往上提。

## 2. tiny-platform 测试分层

### 2.1 单元测试

适合：
- 纯业务规则
- DTO 校验注解
- exception factory / errorCode 映射
- composable / store / adapter

要求：
- 不连真实外部依赖
- 失败时能直接定位到一个逻辑点
- 命名说明场景和预期

### 2.2 组件测试

适合：
- 按钮禁用态
- 表单校验
- 权限可见性
- 确认弹层
- 列表筛选、分页、弹窗流程

要求：
- 通过真实 DOM 交互驱动
- 不得绕过 disabled 按钮
- stub 必须保留 `disabled`、`loading`、`v-model`、confirm 语义

### 2.3 集成测试

适合：
- Controller 到 DB
- 事务、状态机、Repository 查询
- Spring 配置装配
- 多租户过滤和拒绝路径

要求：
- 尽量少 mock
- 明确事务边界和数据构造方式
- 依赖 MySQL 语义时不能只用 H2

### 2.4 E2E / Smoke

适合：
- 关键用户闭环
- 真实认证链路
- 真实租户上下文
- 编排型状态收敛
- 跨模块主路径

要求：
- 先定义环境等级
- 先定义 seed/reset
- 先定义身份来源
- 断言用户可观察结果

## 3. E2E 分类标准

### 3.1 `mock-assisted UI`

特点：
- 页面是真实页面
- 允许 mock first-party API
- 目标是验证前端交互和请求契约

适用：
- 页面回归
- 表单交互
- 按钮门禁
- 列表/弹窗行为

不适用：
- 验证真实认证
- 验证真实租户隔离
- 验证真实数据库约束
- 验证真实编排收敛

### 3.2 `isolated real-link`

特点：
- 前端、后端、数据库、认证链路都是真实的
- 环境可重置
- 允许隔离第三方外部依赖，但必须写清 mock 边界

适用：
- OIDC / MFA
- Session/JWT 切换
- 租户隔离
- 调度/工作流/统计 DAG
- 插件装载

强约束：
- 不得用 `page.route()` 替代 first-party 业务 API
- `storageState` 必须来自真实登录 setup

### 3.3 `shared-env smoke`

特点：
- 使用共享测试环境
- 只跑少量高价值路径
- 要尽量幂等和低副作用

适用：
- 只读验证
- 轻量写操作 smoke
- 发布后核验

### 3.4 `nightly/full-chain`

特点：
- 可以更慢
- 跨模块
- 覆盖真实长链路
- 必须有 trace、日志和 seed 产物

适用：
- 发布前全链路
- 插件编排
- 真实异步状态收敛

## 4. E2E 必填元信息

每条 E2E 或每个 E2E 套件至少写清：

- 环境等级：`mock-assisted UI` / `isolated real-link` / `shared-env smoke` / `nightly/full-chain`
- 依赖服务：前端、后端、数据库、Redis、OIDC、MQ 等
- 身份来源：测试用户、测试租户、测试 client、MFA secret 从哪里来
- seed/reset：用什么 SQL、fixture、脚本初始化和清理
- 允许 mock 的边界：哪些是 third-party，哪些绝对不能 mock
- 关键断言：用户看见什么、状态怎么变、哪些拒绝路径必须出现

如果这些信息写不清，这条 E2E 不能算“可维护”。

## 5. 认证与租户 E2E 标准

### 5.1 必测身份矩阵

至少覆盖：
- `e2e-user`：普通用户
- `e2e-tenant-admin`：租户管理员
- `e2e-deny`：已登录但无权限
- `e2e-cross-tenant-deny`：已登录但访问其他租户应失败
- `e2e-mfa-user`：启用 TOTP

### 5.2 必测主题

至少保留一条 real-link E2E 覆盖：
- 登录成功
- 登录拒绝
- MFA 二次验证
- 租户拒绝或跨租户拒绝
- Session / JWT / OIDC 相关真实链路中的至少一种

如果 real-link 依赖 setup helper 组装多身份环境变量或 auth-state，还必须补对应的单元回归测试，锁住：
- 身份切换时的环境变量覆盖顺序
- 主身份与次身份之间的 secret / one-time code 不串用
- storageState 输出路径与身份绑定关系

### 5.3 禁止做法

- 手写 JWT 冒充真实登录
- 直接写 cookie 或 `localStorage` 冒充认证完成
- 用个人账号或共享管理员账号跑自动化
- 把权限超大的单一 client 用到所有测试

## 6. 编排型业务测试标准

适用模块：
- scheduling
- workflow
- 统计 DAG
- 任务编排
- 有依赖图、状态机、异步收敛的业务

### 6.1 最低拓扑覆盖

至少覆盖：
- 并行分叉后归并
- 串行推进
- 失败后重试
- 取消或停止
- 暂停/恢复中的至少一条

### 6.2 最低作用域覆盖

如果系统存在层级操作，必须分别验证：
- 全局级
- 单次运行级
- 节点级

禁止把：
- 全局 stop 测成单次 run stop
- node retry 测成 run retry
- run 级操作误测为 DAG 级操作

### 6.3 断言要求

不要只断言“任务成功了”，还要断言：
- 节点释放顺序
- 中间状态
- 最终状态
- 失败收敛
- 历史/审计/统计是否一致

## 7. Vue 前端测试标准

### 7.1 组件测试必须验证

- 按钮可见性与禁用原因
- 加载态
- 错误提示
- 提交参数
- 路由跳转
- confirm 后才触发的动作

### 7.2 组件测试禁止

- `wrapper.vm` 直接改内部状态去覆盖用户不可达分支
- 手工 `$emit('click')` 到真实 disabled 的按钮
- 过度简化 Ant Design Vue stub，导致失真

### 7.3 什么时候升级成 E2E

出现以下任一情况，就不应只停留在组件测试：
- 真实登录或路由守卫参与
- 真实租户上下文参与
- 页面依赖后端状态收敛
- 页面语义依赖真实编排结果

## 8. 后端测试标准

### 8.1 Service / Repository

必须覆盖：
- 正常路径
- 边界条件
- 异常路径
- 权限/租户拒绝
- 并发或幂等中的至少一类

### 8.2 DTO / model / exception

允许补测试，但必须验证至少一项：
- 校验注解
- 默认值
- 序列化格式
- 生命周期回调
- errorCode 映射

不允许只测：
- getter/setter
- builder 存取
- 无语义的 `equals/hashCode`

### 8.3 MySQL / migration

以下变更必须有真实数据库验证：
- 唯一约束
- 索引
- JSON 列
- native SQL
- Liquibase/Flyway 迁移
- 方言相关锁和并发语义

## 9. CI 对接标准

### 9.1 本地

只跑快速链路：
- format
- lint
- typecheck
- unit
- 改动范围内的快速组件/集成测试

### 9.2 PR

必须：
- build / compile
- unit
- frontend typecheck
- changed-scope integration
- 必要时一条 smoke 或模块级 real-link E2E
- PR 描述通过 [validate-pr-description.yml](../.github/workflows/validate-pr-description.yml) 校验

豁免机制：
- 仅特殊场景可申请 PR 描述门禁豁免
- 需要在 PR 描述中填写“门禁豁免”章节
- 需要关联对应 waiver issue
- 需要维护者添加标签 `ci-pr-description-waived`
- 需要 reviewer 在 PR 评论或 APPROVED review 中显式写出 `Waiver-Approved: #issue`
- 需要写清豁免原因、补跑计划和批准依据

不应：
- 把 full-chain 全量 E2E 全塞进 PR 必跑
- 把需要专用测试身份、专用数据库、MFA secret 的 real-link 套件直接混进默认快速链路

### 9.3 Nightly / main

必须：
- 真实数据库兼容
- 较慢的集成/契约/安全扫描
- real-link 或 full-chain E2E
- trace / screenshot / 视频 / seed 日志

建议：
- real-link E2E 使用独立 workflow，入口至少包含 `workflow_dispatch` 和 `schedule`
- workflow 开始时显式检查必需 secrets/自动化身份，缺失时快速失败并输出修复提示
- setup helper 的纯逻辑回归仍然留在普通 unit test 中，和 PR 一起跑

### 9.4 失败输出

必须让人看得出是：
- 代码失败
- 环境失败
- 身份/权限失败
- seed/reset 失败

## 10. AI 工具协作模板

适用于 Cursor / Continue / Copilot / Codex。

### 10.1 让 AI 补测试时，至少给出这 6 项

1. 改动目标
2. 风险边界
3. 需要补的测试层级
4. 是否需要真实链路
5. 身份与租户要求
6. 禁止偷懒的方式

### 10.2 可直接复用的提示词骨架

```text
请为 tiny-platform 的这次改动补测试，并遵守项目 testing playbook 与 .agent/src/rules/**：

变更点：
- ...

风险点：
- 权限 / 租户 / 事务 / 编排 / MySQL / OIDC / MFA / 插件

必须补的测试层级：
- 单元 / 组件 / 集成 / real-link E2E

额外要求：
- 不要为了 coverage 绕过真实 UI 可达路径
- 不要新增纯 getter/setter 测试
- 如果是编排型业务，必须覆盖并行归并、串行推进或失败重试中的相关场景
- 如果是 real-link E2E，不要 mock first-party API

交付物：
- 测试文件清单
- 执行命令
- 还未覆盖的剩余风险
```

## 11. 提交前检查单

- 测试层级是否和风险匹配
- 是否有真实拒绝路径
- 是否验证了租户边界
- 是否验证了用户可观察结果
- E2E 是否写清环境等级和 seed/reset
- 是否误用 mock 替代了真实链路
- 是否新增了低价值 coverage 测试
- CI 命令是否可执行

## 12. 推荐落地路径

1. 先补单元和组件测试，锁住纯逻辑和 UI 门禁。
2. 再补集成测试，锁住事务、Repository、权限、租户。
3. 最后补最少量但最高价值的 real-link E2E。
4. full-chain 场景下沉到 Nightly，不污染默认快速反馈链路。
