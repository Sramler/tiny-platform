# tiny-platform Waiver Reviewer Template

> 用途：reviewer 批准 CI / PR / 测试门禁豁免时，可直接复制使用。  
> 配套文档：[TINY_PLATFORM_CI_WAIVER_POLICY.md](./TINY_PLATFORM_CI_WAIVER_POLICY.md)

## 1. 最小批准语句

在 PR 评论或 `APPROVED review` 中直接写：

```text
Waiver-Approved: #123
```

或

```text
批准豁免：#123
```

其中 `#123` 必须替换成 PR 描述里填写的 `关联 waiver issue`。

## 2. 推荐批准评论

```text
Waiver-Approved: #123

已核对：
- 豁免范围最小化
- 当前已有替代验证
- 补跑计划明确
- 本次改动不属于不可豁免范围
```

## 3. reviewer 自检

在发批准评论前，至少确认：

- 这不是为了省事跳过本地可运行测试
- 这不是在掩盖安全 / 权限 / 多租户 / 破坏性 migration 风险
- issue、PR 描述、标签三者是一致的
- 补跑计划具体到时间和责任人
