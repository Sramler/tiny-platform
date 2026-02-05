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
