# Tiny Platform Main / SB4 Branch Strategy

最后更新：2026-04-17

适用仓库：

- `/Users/bliu/code/tiny-platform`

## 1. 当前分支定位

- `main`
  - 当前生产主线
  - 当前承载 Spring Boot 3 业务迭代与线上修复
- `sb4`
  - 长期升级集成分支
  - 当前用于承接 Spring Boot 4 / Camunda 7 fork 兼容改造
  - 未来验证稳定后，将作为新的主线候选

补充说明：

- 当前仓库里正在使用的验证分支是 `codex/tiny-platform-sb4-camunda7-fork`
- 在正式创建长期分支 `sb4` 之前，该分支可继续承载验证工作
- 本文档中的自动同步 workflow 默认目标是 `sb4`
- 如果正式长期分支名不同，可通过仓库变量 `SB4_BRANCH_NAME` 覆盖

## 2. 改动分类规则

### 2.1 业务功能 / 业务修复

定义：

- 面向产品功能、权限、菜单、租户、审批、页面、接口、数据库演进等正常业务改动

规则：

- 必须先从 `main` 开发并合入 `main`
- 合入 `main` 后，必须进入 `sb4`
- 默认通过 `main -> sb4` 同步 PR 进入 `sb4`
- 不允许把业务需求首发在 `sb4`，再指望人工补回 `main`

### 2.2 `sb4-only` 兼容改动

定义：

- 仅为 Spring Boot 4 / Camunda 7 fork / Jakarta / 测试基础设施 / CI 兼容而存在的改动
- 在当前 `main` 的 Spring Boot 3 语义下不需要、也不应落地的改动

规则：

- 只进入 `sb4`
- 不回补 `main`
- PR 中应明确标注为 `sb4-only`

典型例子：

- Camunda fork 版本切换
- Boot 4 starter / 自动配置兼容修复
- 仅服务于 `sb4` 的 CI、runbook、收口文档

### 2.3 在 `sb4` 上发现的共享修复

定义：

- 问题是在 `sb4` 调试过程中发现，但本质上同样影响 `main`

规则：

- 优先在 `main` 重做或补做正式修复
- 如因排障节奏已先在 `sb4` 修复，必须尽快回补 `main`
- 回补时使用 `cherry-pick -x` 或重新提交等可追踪方式
- 该类 PR 应明确标注为 `backport-to-main`

## 3. 日常工作流

### 3.1 业务改动标准路径

1. 从 `main` 拉业务分支开发
2. PR 合入 `main`
3. 自动创建 `main -> sb4` 同步 PR
4. `sb4` 跑自己的 CI / 集成验证
5. 若同步冲突，由最近一次 `main` 改动负责人处理并完成合并

### 3.2 `sb4-only` 改动路径

1. 从 `sb4` 拉兼容分支开发
2. PR 合入 `sb4`
3. 在 PR 中明确写明“不回补 `main`”

### 3.3 共享修复回补路径

1. 若修复已先进入 `sb4`
2. 立即创建一个回补 `main` 的 PR
3. 在 PR 中记录来源提交 SHA 与回补原因

## 4. 硬性约束

- `main` 与 `sb4` 均应开启分支保护，禁止直接 push
- 业务改动不能长期只存在于一个分支
- `sb4` 不作为业务需求首发主线
- 同步冲突不能无限期挂起；建议由引入冲突的最近一次主线改动负责人在当天收口
- 只有明确的兼容性工作，才能标记为 `sb4-only`

## 5. 推荐标签口径

建议在仓库中维护以下标签：

- `sync-to-sb4`
  - 业务改动已进 `main`，需要同步到 `sb4`
- `backport-to-main`
  - 修复已先进入 `sb4`，需要回补 `main`
- `sb4-only`
  - 仅适用于 Spring Boot 4 / Camunda fork 兼容线

## 6. 提交前缀建议

目标：

- 让提交标题本身就能表达“这是业务改动”还是“这是 `sb4-only` 兼容改动”
- 降低把兼容提交误读为产品功能提交的概率

推荐口径：

- 业务功能 / 业务修复：
  - `feat: ...`
  - `fix: ...`
  - `refactor: ...`
  - `docs: ...`
- `sb4-only` 兼容改动：
  - `feat(sb4): ...`
  - `fix(sb4): ...`
  - `chore(sb4): ...`
  - `ci(sb4): ...`
  - `docs(sb4): ...`
- 分支治理 / 共享规则更新：
  - `docs(governance): ...`
  - `chore(governance): ...`
- `main -> sb4` 对齐 merge：
  - `merge(main): ...`
- `sb4 -> main` 回补修复：
  - 可使用 `fix: ...` 或 `chore(main): backport ...`
  - 同时在 PR 中记录来源提交 SHA

避免项：

- 不要把 `sb4-only` 兼容提交写成：
  - `feat(platform): ...`
  - `fix(platform): ...`
  - `feat(auth): ...`
  - `fix(menu): ...`
- 原因：
  - 这些前缀容易被误解为产品业务功能，后续审阅历史时会误判“这是不是应该在 `main` 也有”

一句话判断：

- 如果这次改动在 Spring Boot 3 主线下也应该存在，就用普通业务前缀
- 如果这次改动只为 Spring Boot 4 / Camunda fork 兼容而存在，就显式加 `(sb4)`

## 7. PR 描述要求

所有涉及 `main` / `sb4` 分支关系的 PR，建议至少写清：

- 本次改动属于“业务双落地”还是“`sb4-only`”
- 来源分支与目标分支
- 是否需要同步到另一条长期分支
- 如暂未同步，谁负责补齐，预计何时补齐

## 8. 自动同步机制

仓库已补充：

- `.github/workflows/sync-main-to-sb4-pr.yml`
- `docs/TINY_PLATFORM_SB4_BRANCH_ENABLEMENT_TASK_CARD.md`

默认行为：

- 当 `main` 有新的 push 时，自动检查是否存在目标分支 `sb4`
- 若 `sb4` 存在，且 `main` 相对 `sb4` 有新增提交，同时当前不存在打开中的 `main -> sb4` PR
- 自动创建一条同步 PR

设计原则：

- 自动创建 PR，但不自动合并
- 冲突必须显式处理
- `sb4` CI 仍然保留独立把关责任

## 9. 未来切主方案

当 `sb4` 稳定并准备转正时：

1. 将当前 `main` 的 Spring Boot 3 主线冻结为 `release/sb3`
2. 将 `sb4` 转为新的 `main`
3. 后续新业务仅进入新的 `main`
4. 旧 `release/sb3` 只接有限维护性补丁

一句话收敛：

- 现在：`main` 是业务主线，`sb4` 是持续同步的升级分支
- 未来：`sb4` 转正为新主线，原 `main` 退为 `release/sb3`
