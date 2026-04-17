# Tiny Platform SB4 Branch Enablement Task Card

最后更新：2026-04-17

适用仓库：

- `/Users/bliu/code/tiny-platform`

## 1. 目标

把当前用于 Spring Boot 4 / Camunda 7 fork 验证的临时工作分支，
平滑切换为正式长期分支 `sb4`，并让后续业务改动具备稳定的 `main -> sb4` 双落地路径。

## 2. 当前状态

- 当前生产主线：
  - `main`
- 当前 SB4 验证分支：
  - `codex/tiny-platform-sb4-camunda7-fork`
- 当前仓库尚未存在正式长期分支：
  - `sb4`
- 当前已落地的治理能力：
  - `docs/TINY_PLATFORM_MAIN_SB4_BRANCH_STRATEGY.md`
  - `.github/PULL_REQUEST_TEMPLATE.md`
  - `.github/workflows/sync-main-to-sb4-pr.yml`

## 3. 非目标

- 本卡不要求在本轮同时完成 Spring Boot 4 全量功能验收
- 本卡不替代 `sb4` 分支上的兼容开发
- 本卡不自动合并 `main -> sb4` 的同步 PR

## 4. 前置条件

执行本卡前应满足：

- 当前 `sb4` 治理改动已独立提交，避免与后续业务改动混杂
- 当前 `codex/tiny-platform-sb4-camunda7-fork` 已包含最新的 Camunda SB4 兼容收口内容
- 团队已确认正式长期分支名采用：
  - `sb4`

若最终决定不用 `sb4` 作为长期分支名，则：

- 需要在仓库 variable 中设置：
  - `SB4_BRANCH_NAME=<实际分支名>`

## 5. 执行步骤

### STEP-01 固化当前治理提交

目标：

- 先把本轮分支治理、PR 模板、自动同步 workflow 与 SB4 收口文档提交到当前验证分支

要求：

- 本次提交应只包含：
  - SB4 分支治理文档
  - PR 模板增强
  - `main -> sb4` 自动同步 workflow
  - `CARD-C7-05B` 文档收口

### STEP-02 从当前验证分支创建正式 `sb4`

建议命令：

```bash
git fetch origin
git checkout codex/tiny-platform-sb4-camunda7-fork
git pull --ff-only origin codex/tiny-platform-sb4-camunda7-fork
git checkout -b sb4
git push -u origin sb4
```

要求：

- `sb4` 必须从当前验证分支最新提交创建
- 不要从旧的 `main` 重新拉出一个空白 `sb4`

### STEP-03 配置分支保护

至少对以下分支开启保护：

- `main`
- `sb4`

建议保护项：

- 禁止直接 push
- 要求 PR 合并
- 要求关键 CI 通过后再合并
- 至少一位 reviewer

### STEP-04 创建推荐标签

建议创建以下 labels：

- `sync-to-sb4`
- `backport-to-main`
- `sb4-only`

用途：

- 明确区分“业务双落地”“回补主线”“仅 SB4 兼容改动”

### STEP-05 校验自动同步 workflow

方式一：

- 在 `main` 合入一条很小的文档或治理变更，观察是否自动创建 `main -> sb4` PR

方式二：

- 手动触发：

```bash
gh workflow run sync-main-to-sb4-pr.yml \
  -f source_branch=main \
  -f target_branch=sb4
```

验证点：

- 若 `main` 相对 `sb4` 有新增提交，应自动创建同步 PR
- 若已存在打开中的同步 PR，应复用而不是重复创建
- 若无差异，应输出 no-op summary

### STEP-06 团队切换执行口径

正式启用后，团队统一按以下规则执行：

- 业务功能 / 业务修复：
  - 先进入 `main`
  - 再同步到 `sb4`
- `sb4-only` 兼容改动：
  - 只进入 `sb4`
- 在 `sb4` 上发现但同样影响 `main` 的共享修复：
  - 必须回补 `main`

## 6. 验收标准

- 远端存在正式长期分支：
  - `sb4`
- `main` 与 `sb4` 已开启基本分支保护
- PR 模板已要求填写分支归属 / 同步要求
- 至少一次 `main -> sb4` 同步 PR 创建验证成功
- 团队后续新增业务改动不再直接首发到 `sb4`

## 7. 回滚 / 兜底

如果正式启用 `sb4` 后发现策略需要调整：

- 可先暂停使用自动同步 workflow
- 保留 `sb4` 分支，但临时改为人工同步
- 不要删除已有 `sb4` 兼容历史

## 8. 备注

- 当前自动同步 workflow 默认目标分支为 `sb4`
- 如果长期分支名保持为 `sb4`，无需额外设置 `SB4_BRANCH_NAME`
- 当前 `codex/tiny-platform-sb4-camunda7-fork` 仍可继续作为个人/任务执行分支存在，但不应替代正式长期分支 `sb4`
