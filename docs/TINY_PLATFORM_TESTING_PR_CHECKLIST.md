# tiny-platform Testing PR Checklist

> 用途：提交 PR、审查 PR、补测试时统一核对。  
> 配套文档：[TINY_PLATFORM_TESTING_PLAYBOOK.md](./TINY_PLATFORM_TESTING_PLAYBOOK.md)

## 1. 变更识别

- [ ] 这次改动涉及哪些风险：逻辑 / UI / 事务 / 权限 / 多租户 / 认证 / 编排 / MySQL / 迁移 / 插件
- [ ] 已说明这次改动对应的测试层级：单元 / 组件 / 集成 / E2E
- [ ] 已说明为什么这些层级足够，为什么没有上更高层测试

## 2. 自动化覆盖

- [ ] bug 修复已补自动化回归
- [ ] 新逻辑已补自动化测试
- [ ] 没有新增纯 getter/setter、纯访问器覆盖测试
- [ ] 没有为了 coverage 绕过真实 UI 可达路径

## 3. 前端检查

- [ ] 关键按钮的可见性、禁用态、确认动作已验证
- [ ] 表单校验、错误提示、请求参数已验证
- [ ] 组件测试没有用 `wrapper.vm` 或手工 `$emit` 覆盖真实不可达路径
- [ ] 如果涉及真实登录、真实租户上下文、真实状态收敛，已补 E2E 或说明为何不需要

## 4. 后端检查

- [ ] Service / Controller / Repository 的正常路径已验证
- [ ] 拒绝路径已验证：权限拒绝、跨租户拒绝、非法状态、重复提交中的相关项
- [ ] 并发、幂等、事务、状态机中的关键一项已验证
- [ ] 涉及 MySQL 方言、Liquibase、唯一约束、native SQL 的改动已走真实数据库验证

## 5. 认证与租户

- [ ] 认证改动已覆盖允许路径和拒绝路径
- [ ] 涉及 MFA/OIDC/Session-JWT 切换的改动已补真实链路验证
- [ ] 没有使用个人账号、共享管理员账号或生产身份跑自动化
- [ ] 测试身份、测试租户、测试 client 来源明确且可重置

## 6. E2E 专项

- [ ] 已标明 E2E 等级：`mock-assisted UI` / `isolated real-link` / `shared-env smoke` / `nightly/full-chain`
- [ ] 已说明 seed/reset 方案
- [ ] 已说明身份来源
- [ ] 已说明允许 mock 的边界
- [ ] real-link E2E 没有 mock first-party 业务 API
- [ ] `storageState` 如有使用，来源于真实登录 setup
- [ ] 断言的是用户可观察结果，而不是只看网络 200

## 7. 编排型业务专项

- [ ] 已覆盖并行归并、串行推进、失败重试/取消/暂停恢复中的相关场景
- [ ] 已区分全局级、单次运行级、节点级操作语义
- [ ] 已验证中间状态和最终状态，而不是只看最终成功
- [ ] 历史、审计、统计或最终状态一致性已验证

## 8. PR 交付物

- [ ] PR 描述已写清验证命令
- [ ] PR 描述已写清风险点
- [ ] PR 描述已写清回滚思路
- [ ] 如某些高价值测试因环境限制未跑，PR 描述中已明确写出缺口与补跑计划
- [ ] 如申请门禁豁免，已填写“门禁豁免”章节、关联 waiver issue，并由维护者添加 `ci-pr-description-waived` 标签
- [ ] 如申请门禁豁免，reviewer 已在 PR 评论或 APPROVED review 中显式写出 `Waiver-Approved: #issue`
