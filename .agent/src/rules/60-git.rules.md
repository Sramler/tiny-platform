# 60 Git 规范

## 适用范围

- 适用于：全仓库协作、Git 提交、分支管理
- 不适用于：第三方库的 Git 历史（但贡献时应遵循其规范）

## 总体策略

1. **Conventional Commits**：遵循 Conventional Commits 规范，便于自动化版本管理和变更日志生成。
2. **原子提交**：每次提交只包含一个逻辑变更，便于审查和回滚。
3. **清晰沟通**：提交信息必须清晰表达"做了什么"、"为什么"、"怎么验证"。

---

## 禁止（Must Not）

### 1) 提交信息

- ❌ 提交无意义信息（如 "update"、"fix"、"WIP"）。
- ❌ 提交信息过长（标题超过 50 字符，应使用 body 详细说明）。
- ❌ 提交信息使用过去式（应使用祈使语气："Add feature" 而非 "Added feature"）。

### 2) 提交内容

- ❌ 提交大体积构建产物（除非项目明确要求）。
- ❌ 提交包含调试代码（如 `System.out.println`、`console.log`、未使用的 import）。
- ❌ 提交包含敏感信息（密码、密钥、Token、个人数据）。

### 3) 提交粒度

- ❌ 一次提交包含多个不相关的变更（应拆分为多个原子提交）。
- ❌ 提交包含格式化变更和功能变更（应分开提交）。

### 4) 推送与同步

- ❌ 在未核对将被推送的 commit 范围前直接执行 `git push`，尤其是当前分支已经 `ahead` 多个提交时。
- ❌ 本次只想同步单个修复，却把当前分支上尚未审阅或无关的历史提交一并推送到远端。

---

## 必须（Must）

### 1) 提交信息格式

- ✅ 遵循 Conventional Commits 规范：`<type>[optional scope]: <description>`
- ✅ 提交信息包含：做了什么 + 为什么 + 怎么验证。
- ✅ 标题使用祈使语气：`feat: add user authentication` 而非 `feat: added user authentication`。
- ✅ 标题长度 ≤ 50 字符，详细说明放在 body 中。

### 2) 提交类型

- ✅ 使用标准类型：`feat`（新功能）、`fix`（修复）、`docs`（文档）、`style`（格式）、`refactor`（重构）、`perf`（性能）、`test`（测试）、`chore`（构建/工具）。
- ✅ 破坏性变更使用 `BREAKING CHANGE:` 标记。

### 3) 分支管理

- ✅ 主分支受保护；重要变更走 PR 并通过 validate。
- ✅ 功能分支命名：`feat/功能名`、`fix/问题描述`、`refactor/重构内容`。
- ✅ tiny-platform 正式维护两条交付分支：`sb4`（当前业务主干）与 `sb3`（当前维护线）。
- ✅ `main`/`master` 视为历史基线或默认入口，不作为日常修复、升级改造、常规 PR 的默认目标分支，除非用户明确要求。
- ✅ 通用业务功能、业务修复、安全修复，以及需要双线存在的改动默认先进入 `sb4`。
- ✅ `sb3` 只接来自 `sb4` 的选择性稳定 backport；回补时必须使用 `cherry-pick -x`、专用 backport 分支或等价可追踪方式。
- ✅ Spring Boot 4、Jakarta、Jackson 3、框架兼容、依赖迁移类改造仅进入 `sb4`，不得误提交到 `sb3`。
- ✅ 推送前必须核对将进入远端的提交列表，至少执行一次 `git log --oneline origin/<当前分支>..<当前分支>` 或等价命令。
- ✅ 若当前分支已领先远端多个提交，而本次只想同步其中一部分，必须通过拆分分支、`cherry-pick`、`git worktree` 等方式隔离；不得直接全量 `push`。

---

## 应该（Should）

### 1) 版本管理

- ⚠️ 版本遵循 SemVer；破坏性变更写清迁移说明。
- ⚠️ 使用语义化版本：`MAJOR.MINOR.PATCH`（如 `1.2.3`）。

### 2) 提交粒度

- ⚠️ 原子提交：每次提交只包含一个逻辑变更。
- ⚠️ 相关变更分组：格式化、重构、功能变更分开提交。

### 3) 提交信息详细说明

- ⚠️ Body 说明"为什么"：解释变更原因和背景。
- ⚠️ Footer 引用 Issue：使用 `Fixes #123`、`Closes JIRA-789` 等格式。

### 4) 分支与工作区操作

- ⚠️ 提交前先执行 `git branch --show-current` 与 `git status --short --branch`，确认当前分支和工作区状态。
- ⚠️ 查看远端状态优先使用 `git fetch --all --prune`，不要在脏工作区直接执行 `git pull`。
- ⚠️ 当前工作区存在未提交改动且又需要处理另一条分支时，优先使用 `git worktree`，不要直接切换分支。
- ⚠️ PR 目标分支必须与变更性质一致：默认业务改动走 `sb4`，维护回补走 `sb3`，升级兼容改造留在 `sb4`，不要例行提交到 `main`/`master`。

---

## 可以（May）

- 💡 使用 commitlint 等工具自动检查提交信息格式。
- 💡 使用 semantic-release 自动生成版本号和变更日志。
- 💡 使用 Git hooks（如 Husky）在提交前自动检查。

---

## 例外与裁决

### 紧急修复

- 紧急安全漏洞修复可使用简化提交信息，但必须包含安全相关关键词（如 `[SECURITY]`）。
- 紧急修复后必须补全详细说明和测试。

### 实验性分支

- 实验性分支可使用临时提交信息，但合并前必须整理提交历史。
- 实验性功能提交信息可包含 `[EXPERIMENTAL]` 标记。

### 冲突裁决

- 平台特定规则（90+）优先于本规范。
- Git 规范与代码规范冲突时，优先保证提交信息清晰。
- 涉及 tiny-platform 的认证、多租户、平台升级、真实链路验证时，`90+` 平台规则高于本文件中的通用 Git 约定。

---

## tiny-platform 分支治理补充

### 1) 分支职责

- `sb4`：当前业务开发主干，承载默认业务迭代、线上问题修复、升级后主线演进。
- `sb3`：维护线，只承载从 `sb4` 选择性回补的稳定修复与必要维护补丁。
- `main`/`master`：历史或默认入口分支，不作为默认 PR 目标。

### 2) Agent / Cursor 必须遵守

- ✅ 修改前必须确认当前分支，不允许在不清楚目标分支的情况下直接编码、提交或推送。
- ✅ 工作区存在未提交改动时，不允许随意 `checkout`、`pull`、`merge`。
- ✅ 必须只暂存本次要提交的文件，不允许把无关修改顺带提交。
- ✅ `sb4` 专属升级项的提交说明应明确这是升级/兼容修复。
- ✅ 需要进入 `sb3` 的修复，应先在 `sb4` 收敛，再通过 backport 分支 / `cherry-pick -x` 选择性回补到 `sb3`。
- ✅ 创建 `sb3` PR 前，必须明确它对应哪些 `sb4` 来源提交以及为什么 `sb3` 也需要该修复。
- ✅ 创建 PR 或执行 `push` 前，必须明确说明“这次会进入远端的 commit 列表”以及目标分支，不能只看工作区 diff 就直接发布。

### 3) Agent / Cursor 禁止

- ❌ 在脏工作区直接执行会改写工作树的分支切换。
- ❌ 默认将 PR 或提交目标设为 `main`/`master`。
- ❌ 将 `sb4` 的框架升级改造混入 `sb3` 稳定修复。
- ❌ 将整条 `sb4` 周期性同步到 `sb3`，或用整线 merge / sync PR 代替选择性 backport。
- ❌ 用 `git reset --hard`、`git checkout --`、强推覆写等破坏性方式“清理现场”，除非用户明确授权。
- ❌ 在已知当前分支 `ahead` 多个提交的情况下，未经核对就把全部历史一起推到远端。

### 4) 推荐工作流

```bash
# 提交前先确认分支与状态
git branch --show-current
git status --short --branch

# 查看远端最新状态
git fetch --all --prune

# 当前工作区不干净但要处理另一条分支
git worktree add ../tiny-platform-sb3 sb3
```

---

## 示例

### ✅ 正例：Conventional Commits 格式

```bash
# 新功能
git commit -m "feat(user): add user avatar upload

- Implement avatar upload endpoint (POST /users/{id}/avatar)
- Support image formats: jpg, png, webp
- Image size limit: 5MB

Reason: Users need to customize avatars to improve UX

Verification:
- Unit test: UserControllerTest.testUploadAvatar
- Integration test: Manual upload verification
- Performance test: Concurrent upload 100 images

Closes #123"

# 修复 bug
git commit -m "fix(auth): fix token expiration calculation

Problem: Token expiration time calculation error, causing early expiration
Reason: Timezone conversion issue
Fix: Use UTC time for unified calculation

Verification:
- Unit test: TokenServiceTest.testTokenExpiration
- Manual verification: Token expires at expected time

Fixes #456"

# 破坏性变更
git commit -m "feat(api)!: change user endpoint response format

BREAKING CHANGE: User endpoint now returns UserDTO instead of User entity.
Migration: Update client code to use UserDTO fields.

Refs #789"
```

### ❌ 反例：无意义信息、缺少原因和验证方式

```bash
# 错误：无意义信息
git commit -m "update" # ❌ 无意义

# 错误：缺少详细信息
git commit -m "fix bug" # ❌ 缺少原因和验证方式

# 错误：缺少为什么和怎么验证
git commit -m "feat: add feature" # ❌ 缺少详细说明

# 错误：使用过去式
git commit -m "feat: added user authentication" # ❌ 应使用祈使语气
```

### ✅ 正例：原子提交

```bash
# 第一次提交：格式化代码
git commit -m "style: format code with google-java-format"

# 第二次提交：重构
git commit -m "refactor(user): extract user validation logic to separate method"

# 第三次提交：新功能
git commit -m "feat(user): add user email validation"
```

### ❌ 反例：一次提交包含多个不相关变更

```bash
# 错误：一次提交包含格式化和功能变更
git commit -m "feat: add user feature and format code" # ❌ 应分开提交
```

### ✅ 正例：使用 scope 明确模块

```bash
git commit -m "feat(auth): add OAuth2 support"
git commit -m "fix(api): handle null pointer exception"
git commit -m "docs(readme): update installation guide"
```

### ✅ 正例：Body 详细说明

```bash
git commit -m "refactor(database): migrate from JPA to MyBatis

Reason:
- Better control over SQL queries
- Improved performance for complex queries
- Easier to optimize database operations

Changes:
- Replace JPA repositories with MyBatis mappers
- Update service layer to use MyBatis
- Add MyBatis configuration

Migration:
- Update dependencies in pom.xml
- Run database migration scripts
- Update integration tests

Refs #456"
```

### ✅ 正例：破坏性变更标记

```bash
git commit -m "feat(api)!: change response format

BREAKING CHANGE: API response format changed from JSON object to array.
Migration guide: https://wiki.example.com/migration-guide

Refs #789"
```

### ✅ 正例：Issue 引用

```bash
git commit -m "fix(auth): resolve session timeout issue

Fixes #123
Closes #456
Refs #789"
```
