# .agent 工具链（v2.3.1）

> 规则与知识资产的工具无关构建系统  
> 目标：将「人维护的规则与知识源码」稳定、可验证、可复现地编译为各 AI 工具可直接生效的官方规则入口。

---

## 一句话定位

`.agent/src` 是源码，`.cursor/rules` 是编译产物。  
禁止手改产物，永远从源码构建。

---

## 设计目标

本工具链用于解决以下工程问题：

1. 规则如何长期维护而不腐化
2. 多人 + 多 AI 工具协作时如何避免规则入口混乱
3. 如何确保仓库中的规则与实际生效规则一致
4. 如何支持 Cursor / Copilot / 未来工具而不反复迁移规则

---

## 核心原则

### 1. 唯一真相（Source of Truth）

唯一真相路径为：

```text
.agent/src/**
```

所有规则正文、技能知识、映射关系只允许存在于此目录中。  
禁止在任何工具入口（例如 `.cursor/rules`）直接维护规则。

### 2. 真实产物（Real Artifacts）

构建脚本会将源码写入工具的官方入口路径。

例如：

```text
Cursor -> .cursor/rules/**
```

`.agent/targets` 仅作为中间工作区，不是任何工具的生效入口。

### 3. 闭环校验（Closed Loop）

validate.sh 用于校验：

仓库中的产物 == 源码重新构建的结果

若不一致，视为构建不可信，禁止提交。

---

## 目录结构说明

```text
.agent/
├── src/
│   ├── rules/              规则源码（按主题拆分，不含工具配置）
│   ├── skills/             技能 / 方法论 / 架构知识
│   ├── rules.local/        本地扩展（.gitignore，不提交）
│   └── map/
│       └── rules-map.json   声明式映射配置
├── build/
│   ├── build.sh            构建总入口
│   ├── cursor.mdc.sh       Cursor Project Rules（.mdc）
│   ├── cursor.rulemd.sh    Cursor Project Rules（RULE.md folder）
│   ├── agentsmd.sh         生成 / 更新 AGENTS.md
│   └── validate.sh         闭环校验脚本
└── targets/                中间产物工作区（.gitignore）
```

关键约定：

- `.rules.md` 只写规则正文，不包含工具配置
- `globs` / `alwaysApply` 等工具配置只写在 `rules-map.json`
- 默认策略：一源文件 = 一产物文件（模块化）

---

## 依赖

- bash（已兼容 macOS bash 3.x）
- jq（JSON 解析）
- diff（校验使用）

若 jq 不存在，构建脚本会直接失败并提示安装方式。

---

## 快速开始

### 初始化

```bash
chmod +x .agent/build/*.sh
jq --version
```

### 构建规则

生成 Cursor Project Rules（.mdc）：

```bash
.agent/build/build.sh --target cursor --cursor-format mdc
```

生成 Cursor Project Rules（RULE.md folder）：

```bash
.agent/build/build.sh --target cursor --cursor-format rulemd
```

### 校验一致性

```bash
.agent/build/validate.sh --target cursor --cursor-format mdc
```

---

## 5 分钟完整验证流程

以下流程确保系统正常工作，建议首次使用或修改后执行：

### Step 1: 检查依赖（30 秒）

```bash
# 检查 jq
jq --version || echo "❌ 请先安装 jq: brew install jq"

# 确保脚本有执行权限（幂等操作，已有权限不会报错）
chmod +x .agent/build/*.sh
```

### Step 2: 清理旧产物（10 秒）

根据构建格式选择清理策略：

**如果构建 `.mdc` 格式**：

```bash
# 清理所有 .mdc 文件 + 清理所有目录（避免之前 rulemd 格式的残留）
rm -rf .cursor/rules/*.mdc .cursor/rules/*/
```

**如果构建 `RULE.md folder` 格式**：

```bash
# 清理整个目录（更彻底，避免格式混用）
rm -rf .cursor/rules/*
```

### Step 3: 构建产物（30 秒）

```bash
# 构建 Cursor Project Rules（.mdc 格式）
.agent/build/build.sh --target cursor --cursor-format mdc

# 或构建 RULE.md folder 格式
# .agent/build/build.sh --target cursor --cursor-format rulemd
```

**预期输出**：

```text
📝 Building Cursor Project Rules (.mdc) -> .cursor/rules
✅ .cursor/rules/00-core.mdc
✅ .cursor/rules/10-java.mdc
...
✅ Build done: target=cursor
```

### Step 4: 运行完整验证（1 分钟）

```bash
# 运行 validate.sh，它会自动检查：
# 1. 产物数量是否与映射配置匹配
# 2. frontmatter 格式是否正确（.mdc 格式）
# 3. 产物是否与源码构建结果一致（diff）
.agent/build/validate.sh --target cursor --cursor-format mdc
```

**预期输出**：

```text
🔍 Validate: rebuild to temp and diff
📊 检查产物数量...
  ✅ 数量匹配: 8 个规则
📝 检查 frontmatter 格式...
  ✅ 所有产物 frontmatter 格式正确
🔍 检查产物一致性（diff）...
  ✅ 产物与源码构建结果一致

✅ Validate passed: .cursor/rules matches source build output
```

**验证内容说明**：

- **数量匹配**：确保所有规则文件都已生成，没有遗漏
- **frontmatter 格式**：确保 `.mdc` 文件有正确的成对 frontmatter（仅 `.mdc` 格式检查）
- **一致性校验**：确保仓库中的产物与源码重新构建的结果完全一致

如果验证通过，说明系统正常工作。

---

## 强约束（不是建议）

1. 禁止直接编辑 `.cursor/rules`
2. 禁止在 `.rules.md` 中写工具专属配置
3. 禁止跳过 validate 提交代码
4. 所有规则修改必须遵循流程：

```text
修改源码 → build → validate → commit
```

---

## 提交前验证清单

根据使用的格式选择清理策略：

**如果使用 `.mdc` 格式**：

```bash
# 清理所有产物（.mdc 文件 + 目录）
rm -rf .cursor/rules/*.mdc .cursor/rules/*/
.agent/build/build.sh --target cursor --cursor-format mdc
.agent/build/validate.sh --target cursor --cursor-format mdc
```

**如果使用 `RULE.md folder` 格式**：

```bash
# 清理整个目录
rm -rf .cursor/rules/*
.agent/build/build.sh --target cursor --cursor-format rulemd
.agent/build/validate.sh --target cursor --cursor-format rulemd
```

---

## 心智模型（请牢记）

```text
.agent/src        = 源码
.cursor/rules     = 编译产物
build.sh          = 编译器
validate.sh       = 单元测试
```

---

## 常见问题

### 设计决策类

**Q: 为什么不用 Cursor UI 直接写规则？**

- 不可审计：无法追踪变更历史
- 不可复现：无法保证多人环境一致
- 不可校验：无法验证规则是否正确
- 不适合团队协作：无法代码审查和版本控制

**Q: 为什么不用 Python / Node？**

- 工具链复杂度会反噬规则系统
- bash + jq 足以表达构建逻辑
- AI 可以写脚本，但人必须能看懂并维护
- 减少依赖，降低环境配置成本

**Q: 为什么产物要提交到 Git？**

- 开箱即用：clone 后即可使用，无需构建
- 可审计：可以看到规则的历史变更
- 可回滚：可以回退到任意历史版本
- CI 校验：确保产物与源码一致

### 使用操作类

**Q: 如何添加新规则？**

1. 在 `.agent/src/rules/` 创建新的 `.rules.md` 文件（例如 `91-new-feature.rules.md`）
2. 在 `.agent/src/map/rules-map.json` 中添加映射配置：
   ```json
   {
     "rules": {
       "91-new-feature.rules.md": {
         "cursor": {
           "id": "91-new-feature",
           "frontmatter": {
             "description": "新功能规范",
             "globs": ["**/*.new"]
           }
         }
       }
     },
     "cursor": {
       "order": ["...", "91-new-feature.rules.md"]
     }
   }
   ```
3. 运行构建：

```bash
.agent/build/build.sh --target cursor
```

4. 运行校验：

```bash
.agent/build/validate.sh --target cursor --cursor-format mdc
```

5. 提交源码和产物：

```bash
git add .agent/src .cursor/rules/
```

**Q: 如何修改现有规则？**

1. 编辑 `.agent/src/rules/<规则文件>.rules.md`
2. 如需修改 frontmatter（如 `globs`、`alwaysApply`），编辑 `rules-map.json`
3. 运行构建：

```bash
.agent/build/build.sh --target cursor
```

4. 运行校验：

```bash
.agent/build/validate.sh --target cursor --cursor-format mdc
```

5. 提交变更：

```bash
git add .agent/src .cursor/rules/
```

**Q: 如何删除规则？**

1. 删除 `.agent/src/rules/<规则文件>.rules.md`
2. 从 `rules-map.json` 的 `rules` 和 `cursor.order` 中移除对应条目
3. 运行构建（构建脚本会自动清理对应格式的旧产物）：

```bash
# .mdc 格式会自动清理旧的 .mdc 文件
# rulemd 格式会自动清理并重建目录
.agent/build/build.sh --target cursor --cursor-format mdc
```

**注意**：如果切换格式（从 mdc 切换到 rulemd 或反之），建议手动清理：

```bash
# 切换格式时，彻底清理所有产物
rm -rf .cursor/rules/*
```

4. 运行校验：

```bash
.agent/build/validate.sh --target cursor --cursor-format mdc
```

5. 提交变更：

```bash
git add .agent/src .cursor/rules/
```

**Q: 如何临时禁用某个规则？**

在 `rules-map.json` 的 `cursor.order` 中移除对应条目，但保留 `rules` 中的配置。这样规则文件还在，但不会被构建。

### 故障处理类

**Q: 验证失败怎么办？**

重新构建并提交产物：

```bash
.agent/build/build.sh --target cursor --cursor-format mdc
git add .cursor/rules/
```

**Q: 构建失败，提示 "Missing cursor.id"？**

检查 `rules-map.json`：

- 确保 `cursor.order` 中的每个文件都在 `rules` 中有对应配置
- 确保每个规则都有 `cursor.id` 字段

**Q: 构建失败，提示 "Source rule file not found"？**

检查：

- 文件路径是否正确（`.agent/src/rules/<文件名>`）
- 文件名是否与 `rules-map.json` 中的键名完全一致（包括大小写）

**Q: 产物数量不匹配？**

运行验证脚本，它会自动检查并报告：

```bash
.agent/build/validate.sh --target cursor --cursor-format mdc
```

如果数量不匹配，验证脚本会报告具体差异。检查：

- `rules-map.json` 中的 `cursor.order` 是否包含所有规则
- 是否有规则文件被删除但映射配置未更新
- 是否有规则文件存在但映射配置中缺少对应条目

**Q: jq 命令不存在？**

安装 jq：

```bash
# macOS
brew install jq

# Ubuntu/Debian
sudo apt-get update && sudo apt-get install -y jq

# 验证
jq --version
```

**Q: 产物格式不正确（缺少 frontmatter）？**

检查构建脚本是否正常执行，重新构建：

**如果使用 `.mdc` 格式**：

```bash
rm -rf .cursor/rules/*.mdc .cursor/rules/*/
.agent/build/build.sh --target cursor --cursor-format mdc
```

**如果使用 `RULE.md folder` 格式**：

```bash
rm -rf .cursor/rules/*
.agent/build/build.sh --target cursor --cursor-format rulemd
```

如果问题持续，检查 `rules-map.json` 中的 `frontmatter` 配置是否正确。

---

## 故障排查指南

### 问题诊断流程

遇到问题时，按以下顺序排查：

```text
1. 检查依赖 → 2. 检查文件结构 → 3. 检查配置 → 4. 检查产物 → 5. 检查日志
```

### 常见错误及解决方案

#### 错误 1: `jq: command not found`

**症状**：

```text
❌ jq is required but not found in PATH
```

**原因**：未安装 jq

**解决**：

```bash
# macOS
brew install jq

# Ubuntu/Debian
sudo apt-get update && sudo apt-get install -y jq

# 验证
jq --version
```

---

#### 错误 2: `rules-map.json not found`

**症状**：

```text
❌ rules-map.json not found: .agent/src/map/rules-map.json
```

**原因**：映射配置文件不存在或路径错误

**解决**：

```bash
# 检查文件是否存在
ls -la .agent/src/map/rules-map.json

# 如果不存在，创建最小配置
mkdir -p .agent/src/map
cat > .agent/src/map/rules-map.json <<'EOF'
{
  "rules": {
    "00-base.rules.md": {
      "cursor": {
        "id": "00-core",
        "frontmatter": {
          "description": "基础规范",
          "alwaysApply": true
        }
      }
    }
  },
  "cursor": {
    "order": ["00-base.rules.md"]
  }
}
EOF
```

---

#### 错误 3: `Missing cursor.id for: xxx.rules.md`

**症状**：

```text
❌ Missing cursor.id for: 10-java.rules.md
```

**原因**：`rules-map.json` 中缺少对应规则的 `cursor.id` 配置

**解决**：

1. 检查 `rules-map.json` 中是否有该规则的配置
2. 确保配置中有 `cursor.id` 字段：
   ```json
   {
     "rules": {
       "10-java.rules.md": {
         "cursor": {
           "id": "10-java",  // ← 确保有这个字段
           "frontmatter": {...}
         }
       }
     }
   }
   ```

---

#### 错误 4: `Source rule file not found: .agent/src/rules/xxx.rules.md`

**症状**：

```text
❌ Source rule file not found: .agent/src/rules/10-java.rules.md
```

**原因**：规则文件不存在或文件名不匹配

**解决**：

```bash
# 检查文件是否存在
ls -la .agent/src/rules/10-java.rules.md

# 检查文件名是否完全匹配（包括大小写、扩展名）
# rules-map.json 中的键名必须与文件名完全一致
```

---

#### 错误 5: `Validate failed: .cursor/rules differs from source build output`

**症状**：

```text
❌ Validate failed: .cursor/rules differs from source build output
--- Diff (summary) ---
Files .cursor/rules/00-core.mdc and /tmp/agent-validate-xxx/.cursor/rules/00-core.mdc differ
```

**原因**：仓库中的产物与源码重新构建的结果不一致

**解决**：

```bash
# 方法1：重新构建并提交（推荐）
.agent/build/build.sh --target cursor --cursor-format mdc
git add .cursor/rules/
git commit -m "chore: update agent rules"

# 方法2：查看具体差异
.agent/build/validate.sh --target cursor --cursor-format mdc
# 查看输出的 diff 信息，手动修复或重新构建
```

---

#### 错误 6: `cursor.order is empty in rules-map.json`

**症状**：

```text
❌ cursor.order is empty in rules-map.json
```

**原因**：`rules-map.json` 中的 `cursor.order` 数组为空

**解决**：
检查并修复 `rules-map.json`：

```json
{
  "rules": {...},
  "cursor": {
    "order": [  // ← 确保这个数组不为空
      "00-base.rules.md",
      "10-java.rules.md"
    ]
  }
}
```

---

#### 错误 7: 产物数量不匹配

**症状**：

```text
产物数量: 7
映射数量: 8
❌ 数量不匹配
```

**原因**：`cursor.order` 中的规则数量与生成的产物数量不一致

**排查**：

运行验证脚本，它会自动检查并报告详细问题：

```bash
.agent/build/validate.sh --target cursor --cursor-format mdc
```

验证脚本会自动检查：

- 产物数量与映射配置的差异
- 缺失的规则文件
- 缺失的规则配置（`cursor.id`）

**解决**：根据验证脚本的错误提示修复 `rules-map.json` 或补充缺失的文件

---

#### 错误 8: 产物格式不正确（缺少 frontmatter）

**症状**：

```text
❌ 00-core.mdc 缺少 frontmatter 分隔符
```

**原因**：构建脚本执行异常或 `rules-map.json` 配置错误

**排查**：

运行验证脚本，它会自动检查 frontmatter 格式：

```bash
.agent/build/validate.sh --target cursor --cursor-format mdc
```

如果 frontmatter 格式错误，验证脚本会报告具体问题。

**解决**：

1. 检查 `rules-map.json` 中的 `frontmatter` 配置是否正确
2. 重新构建（根据格式选择清理策略）：

```bash
# 如果使用 .mdc 格式：
rm -rf .cursor/rules/*.mdc .cursor/rules/*/
.agent/build/build.sh --target cursor --cursor-format mdc

# 如果使用 RULE.md folder 格式：
# rm -rf .cursor/rules/*
# .agent/build/build.sh --target cursor --cursor-format rulemd
```

3. 再次运行验证确认修复

---

#### 错误 9: 脚本没有执行权限

**症状**：

```text
bash: .agent/build/build.sh: Permission denied
```

**原因**：脚本文件没有执行权限

**解决**：

```bash
chmod +x .agent/build/*.sh

# 验证权限（可选，用于确认）
# 注意：ls 输出格式可能因系统/locale 而异，直接执行脚本更可靠
.agent/build/build.sh --help 2>&1 | head -1 || echo "脚本可能无执行权限"
```

---

#### 错误 10: JSON 格式错误

**症状**：

```text
parse error: Invalid numeric literal at line X, column Y
```

**原因**：`rules-map.json` 格式不正确

**排查**：

```bash
# 验证 JSON 格式
jq empty .agent/src/map/rules-map.json && echo "✅ JSON 格式正确" || echo "❌ JSON 格式错误"

# 查看具体错误位置
jq . .agent/src/map/rules-map.json
```

**解决**：修复 JSON 格式错误（常见问题：缺少逗号、引号不匹配、多余的逗号）

---

### 调试技巧

#### 1. 启用详细输出

修改构建脚本，添加 `set -x` 查看详细执行过程：

```bash
# 临时启用
bash -x .agent/build/build.sh --target cursor --cursor-format mdc
```

#### 2. 检查中间状态

```bash
# 检查临时构建目录（validate 时创建）
ls -la /tmp/agent-validate-*/

# 检查构建产物
ls -la .cursor/rules/

# 检查映射配置
jq . .agent/src/map/rules-map.json
```

#### 3. 使用验证脚本诊断

验证脚本会自动检查所有配置和文件，比手动检查更可靠：

```bash
.agent/build/validate.sh --target cursor --cursor-format mdc
```

验证脚本会报告：

- 产物数量是否匹配
- 规则文件是否存在
- 规则配置是否完整
- frontmatter 格式是否正确
- 产物是否与源码一致

---

### 获取帮助

如果以上方法都无法解决问题：

1. **检查日志**：查看构建脚本的完整输出
2. **验证环境**：确保 `bash`、`jq`、`diff` 都正常工作
3. **对比示例**：参考 `docs/agent-rules-structure-analysis.md` 中的示例配置
4. **重新初始化**：如果问题严重，可以重新创建最小配置并逐步添加规则

---

## 当前配置概览（示例）

- 规则文件：8 个  
  00-base / 10-java / 20-vue / 30-antdv / 40-security /  
  50-testing / 60-git / 90-tiny-platform
- alwaysApply = true：4 个（全局铁律）
- 使用 globs：4 个（领域规则）

---

最后说明：

这套 .agent 工具链的价值不在“现在能用”，  
而在“三年后仍然不需要推翻”。

# .agent 工具链（v2.3.1）

> 规则与知识资产的工具无关构建系统

## TL;DR

- **源码**：`.agent/src/**`
- **产物**：`.cursor/rules/**`（Cursor Project Rules 生效入口）
- **规则**：不手改产物；只改源码；提交前必须 `validate`。

```bash
chmod +x .agent/build/*.sh
.agent/build/build.sh --target cursor --cursor-format mdc
.agent/build/validate.sh --target cursor --cursor-format mdc
```

---

## 1. 为什么要这样设计

多人 + 多 AI 工具协作时，规则最容易出现三类问题：

1. **入口混乱**：有人在 UI 里改、有人在文件里改，规则到底以谁为准？
2. **不可复现**：同一仓库，不同机器/不同时间生成的产物不一致。
3. **不可审计**：无法 code review、无法回滚、无法确认“实际生效规则”。

本工具链用一个简单的工程约束解决这些问题：

- `.agent/src/**` 作为**唯一真相**（Source of Truth）
- `build.sh` 把源码编译到**官方入口**（Real Artifacts）
- `validate.sh` 做闭环校验：**仓库产物 = 源码重建结果**

---

## 2. 目录结构与约定

```text
.agent/
├── src/
│   ├── rules/               # 规则正文（工具无关，不含 frontmatter）
│   ├── skills/              # 知识/方法论（可被 AI 引用）
│   ├── rules.local/         # 个人扩展（.gitignore）
│   └── map/rules-map.json   # 映射：id / frontmatter / globs / alwaysApply / order
├── build/
│   ├── build.sh             # 总入口
│   ├── cursor.mdc.sh        # 生成 .cursor/rules/*.mdc
│   ├── cursor.rulemd.sh     # 生成 .cursor/rules/<id>/RULE.md
│   ├── agentsmd.sh          # 生成/更新仓库根目录 AGENTS.md（可选）
│   └── validate.sh          # 闭环校验（diff 为主）
└── targets/                 # 中间工作区（.gitignore，不是生效入口）
```

**硬约束**：

- `.agent/src/rules/*.rules.md`：只写规则正文，**不写**工具专属 frontmatter
- `globs/alwaysApply/description` 等工具配置：只写在 `rules-map.json`
- 默认：**一源文件 = 一产物文件**（模块化）；不在 Cursor 内合并

---

## 3. 依赖

- bash
- jq
- diff

安装 jq：

```bash
# macOS
brew install jq

# Ubuntu/Debian
sudo apt-get update && sudo apt-get install -y jq
```

---

## 4. 常用命令

### 4.1 构建（build）

```bash
# Cursor（默认：.mdc）
.agent/build/build.sh --target cursor --cursor-format mdc

# Cursor（RULE.md folder）
.agent/build/build.sh --target cursor --cursor-format rulemd

# 指定输出根目录（用于 CI/临时构建）
.agent/build/build.sh --target cursor --cursor-format mdc --output-root /tmp/out
```

### 4.2 校验（validate）

```bash
# 校验 Cursor（.mdc）
.agent/build/validate.sh --target cursor --cursor-format mdc

# 校验 Cursor（RULE.md folder）
.agent/build/validate.sh --target cursor --cursor-format rulemd
```

> 约定：`validate.sh` 至少保证“diff 闭环”。
> 如果你在脚本中实现了数量检查/frontmatter 校验，这些属于增强项，但 README 不把它当作默认承诺。

---

## 5. 5 分钟快速验证流程（推荐）

> 目标：验证“依赖正确 + build 可生成 + validate 可闭环”。

```bash
# 0) 脚本可执行 + 依赖可用
chmod +x .agent/build/*.sh
jq --version

# 1) 构建（生成到 .cursor/rules）
.agent/build/build.sh --target cursor --cursor-format mdc

# 2) 核对数量（可选但强烈建议）
ls -1 .cursor/rules/*.mdc 2>/dev/null | wc -l
jq '.cursor.order | length' .agent/src/map/rules-map.json

# 3) 闭环校验（必须）
.agent/build/validate.sh --target cursor --cursor-format mdc
```

**通过标准**：`validate.sh` 输出 diff 为 0（或输出“passed”）。

---

## 6. 提交前检查（必须）

每次改规则/映射后，按顺序执行：

```text
改源码/映射 → build → validate → commit
```

推荐的提交前命令：

```bash
.agent/build/build.sh --target cursor --cursor-format mdc
.agent/build/validate.sh --target cursor --cursor-format mdc

git add .agent/src .cursor/rules
```

---

## 7. 常见问题（精选）

### 7.1 jq 找不到

```text
jq: command not found
```

安装 jq（见第 3 节）。

### 7.2 validate 失败（产物不一致）

含义：仓库里的 `.cursor/rules` 与源码重建结果不同。

处理：

```bash
.agent/build/build.sh --target cursor --cursor-format mdc
.agent/build/validate.sh --target cursor --cursor-format mdc

git add .cursor/rules
```

### 7.3 Missing cursor.id / order 与 rules 不一致

含义：`cursor.order[]` 引用了一个规则文件，但 `rules[<file>]` 没有对应配置，或缺少 `cursor.id`。

检查：

```bash
jq -r '.cursor.order[]' .agent/src/map/rules-map.json | while read -r f; do
  jq -e --arg f "$f" '.rules[$f].cursor.id' .agent/src/map/rules-map.json >/dev/null \
    || echo "Missing cursor.id for: $f"
done
```

### 7.4 切换 mdc 与 rulemd 后出现残留

推荐做法：**由 build 脚本实现“按格式清理输出目录”**。

如果你当前脚本还没实现清理，临时手动清理（谨慎使用）：

```bash
rm -rf .cursor/rules/*
```

---

## 8. 故障排查（快速版）

按顺序排查：

1. **依赖**：`jq --version` 是否可用
2. **配置**：`jq '.cursor.order | length' .agent/src/map/rules-map.json` 是否正常
3. **源文件是否存在**：`.agent/src/rules/<file>` 与 `cursor.order[]` 是否一致
4. **构建日志**：`bash -x .agent/build/build.sh ...` 查看真实执行路径
5. **diff 细节**：`validate.sh` 输出的差异文件逐个查看

---

## 9. 约定与边界

- README 只描述**稳定承诺**：源码/产物边界、构建入口、validate 闭环。
- “数量检查 / frontmatter 校验 / 更强 lint”属于可选增强，应以脚本实现为准。
