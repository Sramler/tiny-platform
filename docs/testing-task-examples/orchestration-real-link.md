# Orchestration Real-Link Example

```text
请为 tiny-platform 的编排型改动补测试。

适用业务：
- 调度 / 工作流 / 统计 DAG / 任务编排

必须遵守：
- docs/TINY_PLATFORM_TESTING_PLAYBOOK.md
- .agent/src/rules/50-testing.rules.md
- .agent/src/rules/90-tiny-platform.rules.md

变更点：
- ...

风险点：
- 节点释放顺序
- 状态机
- 取消/暂停/恢复
- run 级与 node 级语义混淆
- 多租户运行上下文

测试层级：
- Service / integration test
- isolated real-link E2E（如有真实前后端联动）

最低覆盖：
- 一条并行分叉后归并
- 一条串行推进
- 一条失败后重试 / 取消 / 暂停恢复中的相关路径

如果有分层操作，必须区分：
- 全局级
- 单次运行级
- 节点级

断言：
- 节点释放顺序
- 中间状态
- 最终状态
- 历史 / 审计 / 统计一致性

禁止：
- 不要只测接口 200
- 不要把 DAG 级操作测成 run 级
- 不要把 run 级操作测成 node 级
- real-link E2E 不要 mock first-party API

交付：
- 测试文件清单
- 执行命令
- 剩余风险
```
