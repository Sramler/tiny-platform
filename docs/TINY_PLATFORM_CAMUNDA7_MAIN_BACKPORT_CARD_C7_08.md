# Tiny Platform CARD-C7-08 `main` 回补任务卡

最后更新：2026-04-17

状态：

- `已执行`
- `main` 回补分支：
  - `codex/c7-main-tenant-backport`

目标：

- 把 `CamundaProcessEngineServiceImpl.startProcessInstance(...)` 中已经在 `sb4` 识别出的 tenant 启动语义修正，最小范围回补到 `main`
- 防止 `main` 继续把 `activeTenantId` 错传到 Camunda `businessKey` 槽位
- 用最小回归测试把这条行为锁住，避免后续再回退

关联背景：

- `main..sb4` 剩余差异分类结论：
  - `docs/TINY_PLATFORM_MAIN_SB4_REMAINING_DIFF_CLASSIFICATION_20260417.md`
- 本卡对应“建议回补 `main`”的唯一代码文件：
  - `tiny-oauth-server/src/main/java/com/tiny/platform/application/oauth/workflow/CamundaProcessEngineServiceImpl.java`

问题定义：

- `main` 当前实现：
  - `runtimeService.startProcessInstanceByKey(processKey, activeTenantId, variables)`
- 该重载第二个参数在 Camunda 中表示 `businessKey`，而不是 `tenantId`
- 结果是：
  - 方法签名里的 `activeTenantId` 名称正确
  - 运行时语义却错误
  - 多租户流程启动会表现为“把 tenant 当 businessKey”，而不是“按 tenant 选择流程定义”

本卡范围：

- 仓库：
  - `tiny-platform`
- 目标分支：
  - `main`
- 实际执行分支：
  - `codex/c7-main-tenant-backport`
- 仅处理：
  - `CamundaProcessEngineServiceImpl.startProcessInstance(...)` 的 tenant 启动语义修正
  - 对应最小单元回归测试

非目标：

- 不把 `sb4` 的 `@ConditionalOnProperty(camunda.bpm.enabled)` 一并回补到 `main`
- 不把 `ProcessController` 的装配策略切换一并带回 `main`
- 不回补 `CamundaSmokeVerifier*` 这组三个 `sb4` smoke 资产
- 不扩大为 Spring Boot 4 / fork / smoke 总体回补

目标改动：

1. `activeTenantId` 非空时：
   - 改为 `runtimeService.createProcessInstanceByKey(processKey)`
   - 再调用 `processDefinitionTenantId(activeTenantId)`
   - 再设置变量并 `execute()`
2. `activeTenantId` 为空或空白时：
   - 显式走 `processDefinitionWithoutTenantId()`
3. `variables` 为空时：
   - 统一按空 `Map` 处理，避免把 `null` 直接透传

建议文件：

- `tiny-oauth-server/src/main/java/com/tiny/platform/application/oauth/workflow/CamundaProcessEngineServiceImpl.java`
- `tiny-oauth-server/src/test/java/com/tiny/platform/application/oauth/workflow/CamundaProcessEngineServiceImplTest.java`

验收标准：

- `main` 上 `startProcessInstance(...)` 不再把 `activeTenantId` 传给 `businessKey`
- 非空 tenant 能走 `processDefinitionTenantId(...)`
- 空 tenant 能走 `processDefinitionWithoutTenantId()`
- 空变量输入能被稳定处理
- 新增回归测试通过

执行建议：

1. 在独立 `main` worktree 中实施，避免污染当前 `sb4` 工作区
2. 改动尽量保持单文件主逻辑 + 单文件回归测试
3. 验证优先跑：
   - `CamundaProcessEngineServiceImplTest`
   - 必要时补模块级 `test-compile`

本轮实际执行结果：

- 已在独立 `main` worktree 分支 `codex/c7-main-tenant-backport` 完成实现
- 已把 `startProcessInstance(...)` 从错误的 `startProcessInstanceByKey(processKey, activeTenantId, variables)` 回补为 builder 路径
- 已新增最小回归测试：
  - `CamundaProcessEngineServiceImplTest`
- 已执行验证：
  - `mvn -pl tiny-oauth-server -Dtest=CamundaProcessEngineServiceImplTest test -Dmaven.repo.local=/usr/local/data/repo`
- 验证结果：
  - `BUILD SUCCESS`
  - `Tests run: 2, Failures: 0, Errors: 0, Skipped: 0`
  - 完成时间：`2026-04-17T10:23:52+08:00`

完成定义：

- 本卡文档已落地
- `main` backport 分支已完成实现与回归
- 验证结果可复述“tenant 语义已被锁定”
