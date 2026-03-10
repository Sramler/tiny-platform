# tiny-platform CI Gate Waiver Policy

> 适用范围：tiny-platform 全仓库  
> 目标：定义什么时候允许豁免 CI / PR 描述 / 测试门禁，谁可以批准，批准后必须补什么。  
> 配套文档：
> - [TINY_PLATFORM_TESTING_PLAYBOOK.md](./TINY_PLATFORM_TESTING_PLAYBOOK.md)
> - [TINY_PLATFORM_TESTING_PR_CHECKLIST.md](./TINY_PLATFORM_TESTING_PR_CHECKLIST.md)
> - [TINY_PLATFORM_WAIVER_REVIEWER_TEMPLATE.md](./TINY_PLATFORM_WAIVER_REVIEWER_TEMPLATE.md)
> - [PULL_REQUEST_TEMPLATE.md](../.github/PULL_REQUEST_TEMPLATE.md)

## 1. 核心原则

- 豁免是临时手段，不是常规流程。
- 豁免必须留痕，不能口头约定。
- 豁免必须有补偿措施，不能只写“先过再说”。
- 涉及安全、权限、多租户、数据库破坏性风险的门禁，默认不豁免。

## 2. 允许申请豁免的场景

仅限以下场景之一：

- 紧急线上修复，需要先合并止血，但部分高成本验证暂时无法立即完成
- CI 测试身份、测试租户、OIDC/MFA 基础设施正在维护或轮换，导致真实链路验证暂时不可用
- 共享测试环境短时故障，且本次改动已经有足够的低层自动化和明确补跑计划
- 非功能性轻微变更误触发了与改动范围不匹配的高成本验证，但需经 reviewer 确认

## 3. 不允许豁免的场景

以下情况不得申请豁免：

- 用豁免替代本地可运行的单元测试、组件测试、类型检查、lint、编译
- 用豁免掩盖明确失败的安全、权限、多租户隔离问题
- 用豁免绕过数据库 migration 破坏性风险而没有 expand-contract / 回滚方案
- 用豁免长期替代 flaky test 修复
- 仅因为“时间不够”“懒得补测试”“ reviewer 催得急”申请豁免

## 4. 谁可以批准

最低要求：

- PR 作者不能单独批准自己的豁免
- 至少一名熟悉对应域的 reviewer 明确同意
- 高风险改动建议由模块 owner 或维护者确认

当前门禁标签：

- `ci-pr-description-waived`

说明：

- 该标签只能由维护者或具备相应仓库权限的人添加
- 没有标签，仅在 PR 描述里写“已豁免”无效

## 5. 申请步骤

1. 在 PR 描述中填写 `门禁豁免（如适用）`
2. 写清：
   - 关联 waiver issue
   - 申请豁免项
   - 豁免原因
   - 补跑计划
   - 批准依据
3. reviewer 评估是否接受
4. 维护者添加对应豁免标签
5. reviewer 在 PR 评论或 APPROVED review 中显式写出批准语句
6. 合并后按补跑计划完成补偿验证

说明：

- reviewer 评论、review 提交、标签变更后，`validate-pr-description.yml` 会自动重新校验

## 6. 必须写清的补偿信息

每个豁免都必须回答：

- 对应哪一个 waiver issue
- 哪个门禁被豁免了
- 为什么现在跑不了，而不是为什么不想跑
- 当前已经有哪些替代验证
- 什么时候补跑
- 谁负责补跑
- 补跑失败时怎么处理

## 7. 推荐格式

```text
关联 waiver issue：#123
申请豁免项：real-link OIDC E2E
豁免原因：CI 测试 client 正在轮换，当前回调链路不可用
补跑计划：24 小时内在身份恢复后补跑 verify-webapp-auth 与 OIDC real-link E2E
批准依据：auth 模块 reviewer 同意，属于紧急线上修复窗口
```

reviewer 批准语句格式：

```text
Waiver-Approved: #123
```

或

```text
批准豁免：#123
```

## 8. Reviewer 检查点

reviewer 在同意豁免前至少要看：

- 这是不是确实临时不可运行，而不是纯粹偷懒
- 当前是否已经有足够的替代自动化验证
- 豁免范围是否最小化
- 补跑计划是否具体到时间和责任人
- 本次改动是否触及不可豁免范围
- reviewer 的批准评论是否显式引用了对应 waiver issue

## 9. 合并后要求

- 必须按补跑计划补齐验证
- 如果补跑失败，必须开后续修复或回滚处理
- 不允许同一问题连续多次申请相同豁免而不修环境/修测试

## 10. 配套入口

- PR 门禁： [validate-pr-description.yml](../.github/workflows/validate-pr-description.yml)
- PR 模板： [PULL_REQUEST_TEMPLATE.md](../.github/PULL_REQUEST_TEMPLATE.md)
- 申请表单： [ci-gate-waiver-request.yml](../.github/ISSUE_TEMPLATE/ci-gate-waiver-request.yml)
