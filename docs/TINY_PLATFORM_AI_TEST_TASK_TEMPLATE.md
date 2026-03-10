# tiny-platform AI Test Task Template

> 用途：给 Cursor / Continue / Copilot / Codex 下“补测试”任务时直接复用。  
> 配套文档：[TINY_PLATFORM_TESTING_PLAYBOOK.md](./TINY_PLATFORM_TESTING_PLAYBOOK.md)

## 1. 最小模板

```text
请为 tiny-platform 的这次改动补测试，并严格遵守：
- docs/TINY_PLATFORM_TESTING_PLAYBOOK.md
- .agent/src/rules/50-testing.rules.md
- .agent/src/rules/58-cicd.rules.md
- .agent/src/rules/90-tiny-platform.rules.md
- .agent/src/rules/91-tiny-platform-auth.rules.md

变更点：
- ...

风险点：
- 权限 / 多租户 / 认证 / 编排 / 并发 / MySQL / Liquibase / 插件 / UI 状态

必须补的测试层级：
- 单元 / 组件 / 集成 / E2E

限制：
- 不要为了 coverage 使用用户不可达路径
- 不要新增纯 getter/setter 测试
- 如果是 real-link E2E，不要 mock first-party 业务 API

输出要求：
- 给出新增/修改的测试文件清单
- 给出执行命令
- 给出尚未覆盖的剩余风险
```

## 2. 认证与租户改动模板

```text
请为 tiny-platform 的认证/租户改动补测试。

必须覆盖：
- 允许路径
- 权限拒绝
- 跨租户拒绝
- 如涉及 MFA/OIDC/Session-JWT 切换，至少一条 real-link E2E

额外限制：
- 不允许伪造 JWT、手写 cookie、手写 storageState 冒充真实登录
- 测试身份必须说明：普通用户、租户管理员、拒绝身份、跨租户拒绝身份、MFA 身份（如适用）

交付：
- 测试文件
- 执行命令
- 身份与 seed/reset 说明
```

## 3. 编排型业务模板

```text
请为 tiny-platform 的编排型改动补测试。

业务类型：
- 调度 / 工作流 / 统计 DAG / 任务编排

必须覆盖：
- 一条并行分叉后归并
- 一条串行推进或顺序依赖
- 一条失败后重试 / 取消 / 暂停恢复中的相关路径

如果存在分层操作，还必须区分：
- 全局级
- 单次运行级
- 节点级

断言要求：
- 节点释放顺序
- 中间状态
- 最终状态
- 历史/审计/统计一致性
```

## 4. 前端页面改动模板

```text
请为 tiny-platform 的前端页面改动补测试。

页面范围：
- ...

至少覆盖：
- 按钮禁用态与禁用原因
- 表单校验
- 错误提示
- 请求参数
- 路由跳转
- confirm 之后的真实动作

禁止：
- wrapper.vm 改内部状态
- 手工 $emit 到 disabled 按钮
- 过度简化 Ant Design Vue stub

如果页面依赖真实登录、真实租户或后端状态收敛，请补 E2E。
```

## 5. E2E 任务模板

```text
请为 tiny-platform 补一条 E2E，并先明确以下信息：

E2E 等级：
- mock-assisted UI / isolated real-link / shared-env smoke / nightly-full-chain

依赖环境：
- 前端 / 后端 / 数据库 / Redis / OIDC / MQ

身份来源：
- 测试用户 / 测试租户 / 测试 client / MFA secret

seed/reset：
- SQL / fixture / setup script / teardown 方案

允许 mock 的边界：
- 只能 mock third-party，不能 mock 的 first-party API 列出来

断言：
- 用户可观察结果
- 权限或租户拒绝
- 最终状态收敛

输出：
- 测试文件
- 配置文件
- 执行命令
- 剩余风险
```

## 6. 让 AI 自检的固定问题

让 AI 在提交结果前回答：

1. 这次新增的测试里，哪些是用户真实可达路径？
2. 哪些拒绝路径被覆盖了？
3. 有没有为了 coverage 加纯访问器测试？
4. 有没有把 real-link E2E 做成 API mock？
5. 如果还没到位，剩余风险是什么？
