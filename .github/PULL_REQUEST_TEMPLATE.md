> 参考：
> - docs/TINY_PLATFORM_TESTING_PLAYBOOK.md
> - docs/TINY_PLATFORM_TESTING_PR_CHECKLIST.md
> - docs/TINY_PLATFORM_MAIN_SB4_BRANCH_STRATEGY.md

## 变更内容

- 

## 变更原因

- 

## 分支归属 / 同步要求

- [ ] 业务改动，先合 `main`，再同步到 `sb4`
- [ ] `sb4-only` 兼容改动，仅进入 `sb4`
- [ ] 在 `sb4` 发现的共享修复，需要回补 `main`
- 来源分支：
- 目标分支：
- 同步 / 回补计划：
- 建议标签：`sync-to-sb4` / `backport-to-main` / `sb4-only`

## 影响范围

- [ ] 后端逻辑
- [ ] 前端页面 / 交互
- [ ] 数据库 / Migration
- [ ] 配置 / 环境变量
- [ ] 安全 / 权限 / 多租户
- [ ] 认证 / OIDC / MFA
- [ ] 编排 / 工作流 / 调度 / 统计 DAG
- [ ] 发布 / 部署行为

## 风险与回滚

- 风险：
- 回滚：

## 测试层级

- [ ] 单元测试
- [ ] 组件测试
- [ ] 集成测试
- [ ] E2E / smoke

### E2E 等级（如适用）

- [ ] mock-assisted UI
- [ ] isolated real-link
- [ ] shared-env smoke
- [ ] nightly/full-chain

## E2E / 真实链路说明（如适用）

- 身份来源：
- 测试租户 / client：
- seed/reset：
- 允许 mock 的边界：
- 未覆盖的真实链路缺口：

## 门禁豁免（如适用）

> 仅在特殊场景使用。需要维护者给 PR 添加标签 `ci-pr-description-waived`。  
> 如需豁免某项，请在对应字段填写：`已豁免，见门禁豁免`  
> reviewer 还需在 PR 评论或 APPROVED review 中显式写：`Waiver-Approved: #123`

- 关联 waiver issue：
- 申请豁免项：
- 豁免原因：
- 补跑计划：
- 批准依据：

## 验证方式

### 自动化验证

```bash
# 填写本地执行过的命令或对应 CI job
```

### 手动验证

1. 

## 前端验证（如适用）

- 截图 / 录屏 / 预览链接：
- 关键交互验证：

## 测试与审查检查清单

- [ ] PR 描述已覆盖变更、原因、影响、风险、回滚、验证
- [ ] 已补充与风险匹配的自动化测试，或明确说明无需新增测试的原因
- [ ] 没有为了 coverage 绕过真实 UI 可达路径
- [ ] 没有新增纯 getter/setter、纯访问器覆盖测试
- [ ] 必需 CI 检查通过后再合并
- [ ] 若涉及数据库变更，已提供 migration 与回滚或兼容方案
- [ ] 若涉及安全 / 权限 / 多租户，已验证允许路径、拒绝路径、跨租户拒绝中的相关项
- [ ] 若涉及认证 / OIDC / MFA / Session-JWT 切换，已说明真实链路验证情况
- [ ] 若涉及编排型业务，已说明并行归并、串行推进、失败重试/取消/暂停恢复中的相关覆盖
- [ ] 若涉及前端页面或交互，已提供截图、录屏、预览链接或清晰手动验证步骤
- [ ] 若存在高价值测试因环境限制未跑，已明确写出缺口与补跑计划
