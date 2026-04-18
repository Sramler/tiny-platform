# Tiny Platform SB4 Trunk Governance Task Card

最后更新：2026-04-18

适用仓库：

- `/Users/bliu/code/tiny-platform`

## 1. 目标

把仓库分支治理正式收敛为：

- `sb4` 作为唯一业务开发主干
- `sb3` 作为维护分支
- `main` 仅保留历史入口或默认分支过渡角色

并同步更新：

- 分支治理文档
- AI 规则真相源（`.agent/src/**`）
- `AGENTS.md`
- PR 模板
- GitHub workflow

避免 Cursor / Codex / CI 继续沿用“`main -> sb4` 自动双落地”的旧口径。

## 2. 当前状态

- 当前业务主干：
  - `sb4`
- 当前维护分支：
  - `sb3`
- 当前历史入口：
  - `main`
- 当前治理主文档：
  - `docs/TINY_PLATFORM_SB4_SB3_BRANCH_STRATEGY.md`
- 当前回补辅助 workflow：
  - `.github/workflows/open-backport-to-sb3-pr.yml`

## 3. 非目标

- 本卡不要求把 `sb4` 整线周期性同步到 `sb3`
- 本卡不把 `main` 重新拉回业务主线
- 本卡不替代具体业务修复或功能开发

## 4. 当前执行口径

正式启用后，团队统一按以下规则执行：

- 业务功能 / 业务修复：
  - 默认进入 `sb4`
- `sb4-only` 兼容改动：
  - 只进入 `sb4`
- `sb3` 维护修复：
  - 从 `sb4` 挑选稳定提交，按需 `cherry-pick -x` / backport 到 `sb3`

明确禁止：

- 自动创建整线 `sb4 -> sb3` 同步 PR
- 把 `main` 当作日常默认 PR 目标
- 用“定期 merge 整条 `sb4`”代替选择性回补

## 5. 执行步骤

### STEP-01 统一文档和 AI 规则

要求：

- 更新 `AGENTS.md`
- 更新 `.agent/src/rules/**`
- 重新生成 `.cursor/rules/**`
- 更新分支治理文档与任务卡

### STEP-02 调整 PR 模板

要求：

- PR 模板必须显式区分：
  - `sb4` 主干业务改动
  - `sb4-only` 兼容改动
  - `sb3` 维护回补

### STEP-03 替换旧 workflow

要求：

- 移除旧的自动 `main -> sb4` 同步口径
- 只保留“为**已准备好的 sb3 backport 分支**开 PR”的手工辅助 workflow

### STEP-04 分支保护与标签

至少对以下分支开启保护：

- `sb4`
- `sb3`

建议标签：

- `backport-to-sb3`
- `sb4-only`

### STEP-05 可选的默认分支切换

如果团队准备把仓库默认分支从 `main` 切到 `sb4`：

- 在仓库设置中切换默认分支
- 更新相关说明文档和 onboarding 指引

如果暂不切换：

- 也必须在规则、文档、PR 模板中明确写清：
  - `main` 不是默认开发目标

## 6. 验收标准

- 分支治理文档统一写成：
  - `sb4` 主干
  - `sb3` 维护
- AI 规则与 `AGENTS.md` 不再把 `main` 视为默认业务主线
- PR 模板不再引导 `main -> sb4`
- 不存在自动整线 `sb4 -> sb3` 同步 workflow
- 如需辅助开 `sb3` PR，只能从准备好的 backport 分支发起

## 7. 备注

- 本卡是当前态治理任务卡，不再沿用早期“`main` 主线、`sb4` 升级线”的旧口径
- 如历史文档与本卡冲突，以 `docs/TINY_PLATFORM_SB4_SB3_BRANCH_STRATEGY.md` 为准
