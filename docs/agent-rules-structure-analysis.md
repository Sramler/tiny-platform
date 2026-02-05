# Agent Rules 结构设计分析与建议（合并版）

> **文档版本**：v2.3.1（多目标适配器版｜自洽修订｜合并版）  
> **修正日期**：2026-01-11  
> **适用范围**：Cursor 最新版本（Project Rules 主推）+ 其他 AI 编程工具（Copilot / Continue / Windsurf 等）  
> **核心目标**：
>
> 1. 规则与知识资产“工具无关”
> 2. 通过 Target/Adapter 生成各工具所需格式
> 3. 支持 Cursor Project Rules 的“载体演进”（.mdc 与 RULE.md folder 双轨可切换）
>
> **v2.3.1 关键修订（相对 v2.3）**：
>
> - 明确：`.agent/targets/` = 适配器工作区/中间产物（默认不提交），真实产物必须写入各工具官方入口路径
> - 统一：build/validate 参数与输出规则（引入 `--output-root`，validate 执行三项检查：数量 + frontmatter + diff）
> - 明确：`.agent/src/rules/*.rules.md` 禁止工具专属 frontmatter；frontmatter/触发条件只写在 `rules-map.json`
> - 明确：默认“一源文件 = 一产物文件”（模块化），不做合并；合并仅用于 legacy（如需要）

---

## 📋 一句话结论（团队口号）

- **唯一真相**：`.agent/src/`（规则源码 + skills 知识库，工具无关）
- **Cursor 主入口**：`.cursor/rules/`（Project Rules，载体可演进：.mdc 或 RULE.md folder）
- **跨工具入口**：`AGENTS.md`（通用协作说明，可生成/可手工维护）
- **一键构建**：`.agent/build/build.sh --target <tool> [--cursor-format mdc|rulemd] [--output-root <dir>]`
- **一键校验**：`.agent/build/validate.sh --target cursor [--cursor-format mdc|rulemd]`

---

## 0. 基本定义（必须统一口径）

### 0.1 唯一真相（Source of Truth）

- **唯一真相是 `.agent/src/`**
- 所有规则正文、技能知识、映射配置都在 `.agent/src/` 维护
- **禁止直接编辑任何产物目录**（例如 `.cursor/rules/`、`.github/copilot-instructions.md` 等）

### 0.2 真实产物（Real Artifacts）与适配器工作区（Adapter Workspace）

- **真实产物**：必须写入目标工具官方入口路径：
  - Cursor：`.cursor/rules/**`
  - Copilot：`.github/copilot-instructions.md`
  - Continue/Windsurf：按各工具要求写入其入口目录/配置（示意）
- **适配器工作区**：`.agent/targets/`
  - 用于模板、临时渲染、生成中间文件
  - **默认不提交**（`.gitignore` 忽略）
  - 不作为任何工具的最终生效入口

### 0.3 默认模块化策略（强制）

- **默认策略：一源文件 = 一个 Project Rule 文件**（模块化）
- 不在 Cursor Project Rules 内做“多文件合并”
- “合并为单文件”仅用于 legacy 或某些工具限制（由 target 负责降级/裁剪）

---

## 1. 当前结构 vs 提议结构（v2.3.1）

### 1.1 当前结构（简单/历史遗留）

```text
tiny-platform/
└── .cursor/
    └── rules/
        └── my.mdc               # 未系统拆分/未统一构建
```

### 1.2 提议结构（通用源码 + 多目标适配，真实产物写回官方入口）

```text
tiny-platform/
├── AGENTS.md                    # 通用协作入口（建议提交；可由 build 生成/更新）
├── .cursor/
│   └── rules/                   # Cursor Project Rules（建议提交；由 build 生成）
│       ├── 00-core.mdc          # 或 00-core/RULE.md（取决于 cursor-format）
│       ├── 10-java.mdc
│       ├── 20-vue.mdc
│       ├── 30-antdv.mdc
│       ├── 40-security.mdc
│       ├── 50-testing.mdc
│       ├── 60-git.mdc
│       └── 90-tiny-platform.mdc
└── .agent/
    ├── README.md                # 工具链说明（必须）
    ├── VERSION                  # 规则系统版本（推荐）
    ├── CHANGELOG.md             # 变更记录（推荐）
    ├── .gitignore               # 忽略 rules.local / targets / cache 等
    ├── src/                     # ✅ 唯一真相（工具无关）
    │   ├── rules/               # 规则源码（主题拆分）
    │   │   ├── 00-base.rules.md
    │   │   ├── 10-java.rules.md
    │   │   ├── 20-vue.rules.md
    │   │   ├── 30-antdv.rules.md
    │   │   ├── 40-security.rules.md
    │   │   ├── 50-testing.rules.md
    │   │   ├── 60-git.rules.md
    │   │   ├── 90-tiny-platform.rules.md
    │   │   ├── 91-tiny-platform-auth.rules.md
    │   │   └── 92-tiny-platform-frontend.rules.md
    │   ├── rules.local/         # 本地扩展（.gitignore；个人定制）
    │   │   └── README.md
    │   ├── skills/              # 技能/知识库（工具无关）
    │   │   ├── README.md
    │   │   ├── java-vue-standards/SKILL.md
    │   │   └── tiny-platform-architecture/SKILL.md
    │   └── map/                 # ✅ 声明式映射（强烈建议）
    │       ├── rules-map.json
    │       └── templates/
    │           ├── cursor-frontmatter.mdc.hbs
    │           ├── cursor-frontmatter.rulemd.hbs
    │           └── copilot.hbs
    ├── targets/                 # ⚠️ 适配器工作区/中间产物（默认不提交）
    │   ├── cursor/
    │   │   ├── mdc/
    │   │   └── rulemd/
    │   ├── copilot/
    │   ├── continue/
    │   └── windsurf/
    └── build/
        ├── build.sh             # 总入口（必须）
        ├── cursor.mdc.sh        # Cursor：输出 .cursor/rules/*.mdc
        ├── cursor.rulemd.sh     # Cursor：输出 .cursor/rules/*/RULE.md
        ├── agentsmd.sh          # 生成/更新 AGENTS.md（推荐）
        ├── copilot.sh           # 生成 .github/copilot-instructions.md（可选）
        └── validate.sh          # 校验：src 与产物一致（推荐）
```

---

## 2. v2.3.1 关键改进点（摘要）

### 2.1 “targets”的定位被统一

- `.agent/targets/` 不再承载“真实产物”
- 真实产物总是写到工具官方入口（例如 `.cursor/rules`）

### 2.2 build 与 validate 闭环

- `build.sh` 支持 `--output-root`，默认项目根目录
- `validate.sh` 在临时 output-root 重新构建，执行三项检查：
  1. 产物数量匹配（与 `rules-map.json` 中的 `cursor.order` 长度一致）
  2. frontmatter 格式检查（仅 `.mdc` 格式，检查成对的 `---` 分隔符）
  3. 产物一致性（diff 对比真实产物目录与临时构建结果）

### 2.3 src 彻底工具无关（硬规则）

- `.agent/src/rules/*.rules.md` 禁止工具专属 frontmatter（alwaysApply/globs/description 等）
- 触发条件/frontmatter/文件名映射必须在 `.agent/src/map/rules-map.json` 声明

### 2.4 默认“一源文件=一产物文件”

- Cursor Project Rules 走模块化，不做合并
- 合并逻辑（如 legacy 或目标工具限制）由 target 负责

---

## 3. 规则入口矩阵（v2.3.1）

### 3.1 Cursor 内部生效入口（只谈 Cursor）

优先级建议（高 -> 低）：

| 层级                 | 入口                                               | 优先级     | 说明                                                                   |
| -------------------- | -------------------------------------------------- | ---------- | ---------------------------------------------------------------------- |
| **A. Project Rules** | `.cursor/rules/*.mdc` 或 `.cursor/rules/*/RULE.md` | ⭐⭐⭐⭐⭐ | Cursor 当前主推荐入口，载体可能是 .mdc 或 RULE.md folder（随版本演进） |
| **B. UI Rules**      | Cursor UI 配置                                     | ⭐⭐⭐     | 临时/个人/实验用，避免承载项目规范                                     |

注意：

- v2.3.1 不宣称某一种载体“唯一稳定”
- 我们的稳定性来自：`.agent/src`（唯一真相）+ build 适配 + validate 校验

### 3.2 项目通用协作入口（跨工具）

优先级建议（高 -> 低）：

| 层级             | 入口                | 优先级     | 说明                             |
| ---------------- | ------------------- | ---------- | -------------------------------- |
| **A. AGENTS.md** | `AGENTS.md`         | ⭐⭐⭐⭐⭐ | 人与 Agent 都能读，跨工具通用    |
| **B. 源码层**    | `.agent/src/rules`  | ⭐⭐⭐⭐   | 机器可编译的规范源码（唯一真相） |
| **C. Skills**    | `.agent/src/skills` | ⭐⭐⭐     | 方法论/知识库（辅助引用）        |

---

## 4. 规则裁决与优先级（通用、工具无关）

### 4.1 规则强度（从强到弱）

| 类型     | 关键词                | 优先级     | 说明                     |
| -------- | --------------------- | ---------- | ------------------------ |
| **禁止** | ❌ 禁止、不允许、不得 | ⭐⭐⭐⭐⭐ | 最高优先级，违反必须修复 |
| **必须** | ✅ 必须、一定要、强制 | ⭐⭐⭐⭐   | 高优先级，强烈建议遵守   |
| **应该** | ⚠️ 应该、推荐、建议   | ⭐⭐⭐     | 中等优先级，建议遵守     |
| **可以** | 💡 可以、可选、允许   | ⭐⭐       | 低优先级，视情况选择     |

### 4.2 覆盖范围（从强到弱）

| 范围         | 规则文件                  | 优先级     | 说明                     |
| ------------ | ------------------------- | ---------- | ------------------------ |
| **平台特定** | `90-tiny-platform*.md`    | ⭐⭐⭐⭐⭐ | 最高优先级，覆盖通用规则 |
| **框架特定** | `30-antdv.md`             | ⭐⭐⭐⭐   | 框架特定规则             |
| **语言特定** | `10-java.md`, `20-vue.md` | ⭐⭐⭐     | 语言特定规则             |
| **通用规范** | `00-base.md`              | ⭐⭐       | 基础规范，最低优先级     |

### 4.3 冲突裁决原则

1. 禁止优先于一切
2. 平台特定覆盖通用规则（90+ > 30+ > 10/20 > 00）
3. 更严格覆盖更宽松（必须 > 应该 > 可以）
4. 不确定时：必须显式声明假设并请求确认（或给出默认策略）

---

## 5. Cursor Project Rules 的 Frontmatter 工程守则（由 map 注入）

目标：避免上下文稀释，同时保证关键红线始终生效。

### 5.1 全局铁律：alwaysApply=true（只给少量核心规则）

- 仅把“红线/安全/架构底线/代码风格硬约束”放入 alwaysApply
- 示例（由 build 注入到产物）：

```yaml
---
description: 全局基础规范（始终应用）
alwaysApply: true
---
```

### 5.2 领域规则：globs 精准触发

```yaml
---
description: Java 编码规范
globs:
  - "**/*.java"
---
```

### 5.3 description 只解释适用范围，不重复规则正文

```yaml
---
description: Ant Design Vue 4.x 组件使用规范
globs:
  - "**/*.vue"
  - "src/components/**"
---
```

### 5.4 禁止滥用 alwaysApply=true（硬约束）

- ❌ 所有规则都 alwaysApply 会稀释上下文，降低命中率与一致性
- ✅ 只有少量“全局铁律”才 alwaysApply=true，其余用 globs

### 5.5 载体兼容（mdc / rulemd）

- 输出 `.mdc`：frontmatter 原样写入
- 输出 `RULE.md folder`：RULE.md 文件头同样写 frontmatter（若目标 Cursor 版本支持）
- 若目标工具不支持 frontmatter：target 负责降级（例如将 globs 转成说明文字）

---

## 6. Git 管理决策（v2.3.1）

### 6.1 推荐提交（开箱即用）

- `AGENTS.md`
- `.cursor/rules/**`
- `.agent/src/**`
- `.agent/build/**`
- `.agent/src/map/**`

### 6.2 推荐忽略（默认不提交）

- `.agent/src/rules.local/**`
- `.agent/targets/**`
- 构建缓存与临时目录（如 `.agent/cache/**`，如有）

### 6.3 一致性保障

- pre-commit（可选）：自动 build + 校验 + 仅在产物变化时加入暂存
- CI（推荐）：运行 validate.sh，确保“仓库里产物 = 源码构建结果”

---

## 7. 声明式映射（rules-map.json）规范（强烈推荐）

### 7.1 核心原则

- 规则正文只在 `.agent/src/rules/*.rules.md`
- Cursor 的 frontmatter / globs / alwaysApply / description 全在 `rules-map.json`
- 产物文件名（id）由 map 决定，避免脚本硬编码

### 7.2 示例（完整结构）

```json
{
  "rules": {
    "00-base.rules.md": {
      "cursor": {
        "id": "00-core",
        "frontmatter": {
          "description": "全局基础规范（始终应用）",
          "alwaysApply": true
        }
      },
      "copilot": {
        "section": "基础规范"
      }
    },
    "10-java.rules.md": {
      "cursor": {
        "id": "10-java",
        "frontmatter": {
          "description": "Java 编码规范",
          "globs": ["**/*.java"]
        }
      }
    },
    "20-vue.rules.md": {
      "cursor": {
        "id": "20-vue",
        "frontmatter": {
          "description": "Vue 3 编码规范",
          "globs": ["**/*.vue"]
        }
      }
    },
    "40-security.rules.md": {
      "cursor": {
        "id": "40-security",
        "frontmatter": {
          "description": "安全规范（始终应用）",
          "alwaysApply": true
        }
      }
    }
  },
  "cursor": {
    "order": [
      "00-base.rules.md",
      "40-security.rules.md",
      "60-git.rules.md",
      "10-java.rules.md",
      "20-vue.rules.md",
      "30-antdv.rules.md",
      "50-testing.rules.md",
      "90-tiny-platform.rules.md",
      "91-tiny-platform-auth.rules.md",
      "92-tiny-platform-frontend.rules.md"
    ]
  }
}
```

说明：

- `rules.*.cursor.id`：决定输出文件名（`<id>.mdc` 或 `<id>/RULE.md`）
- `cursor.order`：决定生成顺序/排序（不表示合并）

---

## 8. 规则正文格式规范（建议强制统一）

为便于长期维护与跨工具降级，建议每个 `.rules.md` 采用统一骨架：

```markdown
# <规则主题标题>

## 适用范围

- 适用于：xxx
- 不适用于：yyy（如有）

## 禁止（Must Not）

- ❌ ...

## 必须（Must）

- ✅ ...

## 应该（Should）

- ⚠️ ...

## 可以（May）

- 💡 ...

## 例外与裁决

- 允许例外的条件
- 冲突时如何裁决（引用第 4 章裁决原则）

## 示例

- ✅ 正例
- ❌ 反例
```

硬规则：

- `.rules.md` 里不写 Cursor frontmatter
- `.rules.md` 不出现工具入口路径说明（入口说明写在 AGENTS.md / 本规范）

---

## 9. Build 命令约定（v2.3.1）

### 9.1 命令格式

`.agent/build/build.sh --target <tool> [--cursor-format mdc|rulemd] [--output-root <dir>]`

参数说明：

- `--target`：cursor|copilot|continue|windsurf|all
- `--cursor-format`：mdc（默认）|rulemd
- `--output-root`：输出根目录（默认项目根目录）
  - Cursor -> `<output-root>/.cursor/rules`
  - Copilot -> `<output-root>/.github/copilot-instructions.md`
  - AGENTS -> `<output-root>/AGENTS.md`

### 9.2 常用命令

- Cursor（默认 .mdc）：

  ```bash
  .agent/build/build.sh --target cursor
  ```

- Cursor（RULE.md folder）：

  ```bash
  .agent/build/build.sh --target cursor --cursor-format rulemd
  ```

- Copilot：

  ```bash
  .agent/build/build.sh --target copilot
  ```

- 全部：
  ```bash
  .agent/build/build.sh --target all
  ```

---

## 9.3 依赖与环境约束（强烈建议明确）

- 构建与校验脚本依赖于以下工具：
  - `bash`（建议 4.x 及以上）
  - `jq`（用于解析 JSON 和数量检查）
  - `diff`（用于 validate 的产物一致性检查）
  - `grep`、`head`、`find`、`wc`（用于 validate 的数量和 frontmatter 检查）
- `jq` 安装方式：
  - macOS: `brew install jq`
  - Ubuntu: `sudo apt-get update && sudo apt-get install -y jq`
- **注意**：如果 `jq` 不可用，相关脚本必须 fail fast 并提示安装方法。

---

## 10. build.sh（总入口）脚本骨架（自洽版）

```bash
#!/usr/bin/env bash
set -euo pipefail

TARGET=""
CURSOR_FORMAT="mdc"
OUTPUT_ROOT=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    --target) TARGET="$2"; shift 2 ;;
    --cursor-format) CURSOR_FORMAT="$2"; shift 2 ;;
    --output-root) OUTPUT_ROOT="$2"; shift 2 ;;
    *) echo "Unknown arg: $1"; exit 1 ;;
  esac
done

if [[ -z "$TARGET" ]]; then
  echo "Usage: .agent/build/build.sh --target <cursor|copilot|continue|windsurf|all> [--cursor-format mdc|rulemd] [--output-root <dir>]"
  exit 1
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

if [[ -z "$OUTPUT_ROOT" ]]; then
  OUTPUT_ROOT="$ROOT"
fi

export AGENT_ROOT="$ROOT/.agent"
export SRC_ROOT="$AGENT_ROOT/src"
export MAP_JSON="$SRC_ROOT/map/rules-map.json"
export OUTPUT_ROOT
export CURSOR_FORMAT

if [[ ! -f "$MAP_JSON" ]]; then
  echo "❌ rules-map.json not found: $MAP_JSON"
  echo "Hint: create it under .agent/src/map/rules-map.json"
  exit 1
fi

if ! command -v jq >/dev/null 2>&1; then
  echo "❌ jq is required but not found in PATH"
  echo "macOS:  brew install jq"
  echo "Ubuntu: sudo apt-get update && sudo apt-get install -y jq"
  exit 1
fi

case "$TARGET" in
  cursor)
    if [[ "$CURSOR_FORMAT" == "rulemd" ]]; then
      "$SCRIPT_DIR/cursor.rulemd.sh"
    else
      "$SCRIPT_DIR/cursor.mdc.sh"
    fi
    "$SCRIPT_DIR/agentsmd.sh" || true
    ;;
  copilot)
    "$SCRIPT_DIR/copilot.sh"
    "$SCRIPT_DIR/agentsmd.sh" || true
    ;;
  all)
    "$SCRIPT_DIR/cursor.mdc.sh"
    "$SCRIPT_DIR/copilot.sh" || true
    "$SCRIPT_DIR/agentsmd.sh" || true
    ;;
  continue|windsurf)
    echo "TODO: implement target adapter: $TARGET"
    "$SCRIPT_DIR/agentsmd.sh" || true
    ;;
  *)
    echo "Unknown target: $TARGET"
    exit 1
    ;;
esac

echo "✅ Build done: $TARGET (cursor-format: $CURSOR_FORMAT, output-root: $OUTPUT_ROOT)"
```

---

## 11. Cursor 产物生成（模块化：一源文件=一产物文件）

### 11.1 cursor.mdc.sh（输出到 <output-root>/.cursor/rules/\*.mdc）

```bash
#!/usr/bin/env bash
set -euo pipefail

: "${OUTPUT_ROOT:?OUTPUT_ROOT is required}"
: "${SRC_ROOT:?SRC_ROOT is required}"
: "${MAP_JSON:?MAP_JSON is required}"

OUT="$OUTPUT_ROOT/.cursor/rules"
SRC_RULES_DIR="$SRC_ROOT/rules"

mkdir -p "$OUT"

# 清理旧产物（避免 map 移除后残留）
find "$OUT" -maxdepth 1 -type f -name "*.mdc" -print0 | xargs -0r rm -f

echo "📝 Building Cursor Project Rules (.mdc) -> $OUT"

order=$(jq -r '.cursor.order[]' "$MAP_JSON")
for src_file in $order; do
  id=$(jq -r --arg f "$src_file" '.rules[$f].cursor.id // empty' "$MAP_JSON")
  if [[ -z "$id" || "$id" == "null" ]]; then
    echo "❌ Missing cursor.id for: $src_file"
    exit 1
  fi

  src_path="$SRC_RULES_DIR/$src_file"
  if [[ ! -f "$src_path" ]]; then
    echo "❌ Source rule file not found: $src_path"
    exit 1
  fi

  out_path="$OUT/${id}.mdc"

  # 渲染 frontmatter（由 map 注入）
  fm_json=$(jq -c --arg f "$src_file" '.rules[$f].cursor.frontmatter // {}' "$MAP_JSON")

  {
    echo "---"
    # description
    desc=$(echo "$fm_json" | jq -r '.description // empty')
    if [[ -n "$desc" ]]; then
      echo "description: $desc"
    fi

    # alwaysApply
    aa=$(echo "$fm_json" | jq -r '.alwaysApply // empty')
    if [[ "$aa" == "true" || "$aa" == "false" ]]; then
      echo "alwaysApply: $aa"
    fi

    # globs
    has_globs=$(echo "$fm_json" | jq -r 'has("globs")')
    if [[ "$has_globs" == "true" ]]; then
      echo "globs:"
      echo "$fm_json" | jq -r '.globs[]? | "  - \"" + . + "\""'
    fi

    echo "---"
    echo
    cat "$src_path"
    echo
  } > "$out_path"

  echo "✅ $out_path"
done
```

### 11.2 cursor.rulemd.sh（输出到 <output-root>/.cursor/rules/<id>/RULE.md）

```bash
#!/usr/bin/env bash
set -euo pipefail

: "${OUTPUT_ROOT:?OUTPUT_ROOT is required}"
: "${SRC_ROOT:?SRC_ROOT is required}"
: "${MAP_JSON:?MAP_JSON is required}"

OUT="$OUTPUT_ROOT/.cursor/rules"
SRC_RULES_DIR="$SRC_ROOT/rules"

mkdir -p "$OUT"

echo "📝 Building Cursor Project Rules (RULE.md folder) -> $OUT"

order=$(jq -r '.cursor.order[]' "$MAP_JSON")
for src_file in $order; do
  id=$(jq -r --arg f "$src_file" '.rules[$f].cursor.id // empty' "$MAP_JSON")
  if [[ -z "$id" || "$id" == "null" ]]; then
    echo "❌ Missing cursor.id for: $src_file"
    exit 1
  fi

  src_path="$SRC_RULES_DIR/$src_file"
  if [[ ! -f "$src_path" ]]; then
    echo "❌ Source rule file not found: $src_path"
    exit 1
  fi

  out_dir="$OUT/$id"
  rm -rf "$out_dir"
  mkdir -p "$out_dir"

  out_path="$out_dir/RULE.md"

  fm_json=$(jq -c --arg f "$src_file" '.rules[$f].cursor.frontmatter // {}' "$MAP_JSON")

  {
    echo "---"
    desc=$(echo "$fm_json" | jq -r '.description // empty')
    if [[ -n "$desc" ]]; then
      echo "description: $desc"
    fi

    aa=$(echo "$fm_json" | jq -r '.alwaysApply // empty')
    if [[ "$aa" == "true" || "$aa" == "false" ]]; then
      echo "alwaysApply: $aa"
    fi

    has_globs=$(echo "$fm_json" | jq -r 'has("globs")')
    if [[ "$has_globs" == "true" ]]; then
      echo "globs:"
      echo "$fm_json" | jq -r '.globs[]? | "  - \"" + . + "\""'
    fi

    echo "---"
    echo
    cat "$src_path"
    echo
  } > "$out_path"

  echo "✅ $out_path"
done
```

---

## 12. validate.sh（三项检查：数量 + frontmatter + diff）

```bash
#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'EOF'
Usage:
  .agent/build/validate.sh --target cursor [--cursor-format mdc|rulemd]

Examples:
  .agent/build/validate.sh --target cursor --cursor-format mdc
  .agent/build/validate.sh --target cursor --cursor-format rulemd
EOF
}

TARGET=""
CURSOR_FORMAT="mdc"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --target) TARGET="${2:-}"; shift 2 ;;
    --cursor-format) CURSOR_FORMAT="${2:-}"; shift 2 ;;
    -h|--help) usage; exit 0 ;;
    *) echo "❌ Unknown arg: $1"; usage; exit 1 ;;
  esac
done

if [[ -z "$TARGET" ]]; then
  echo "❌ --target is required"
  usage
  exit 1
fi

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
TMP="/tmp/agent-validate-$$"
trap 'rm -rf "$TMP"' EXIT
mkdir -p "$TMP"

echo "🔍 Validate: rebuild to temp and diff"
echo "TARGET=$TARGET CURSOR_FORMAT=$CURSOR_FORMAT"

# 重建到临时 output-root（使用 --no-agents 跳过 AGENTS.md 生成）
"$ROOT/.agent/build/build.sh" --target "$TARGET" --cursor-format "$CURSOR_FORMAT" --output-root "$TMP" --no-agents

if [[ "$TARGET" == "cursor" ]]; then
  MAP_JSON="$ROOT/.agent/src/map/rules-map.json"
  OUT_DIR="$ROOT/.cursor/rules"
  TMP_OUT_DIR="$TMP/.cursor/rules"

  # 检查 1: 产物数量匹配
  echo "📊 检查产物数量..."
  if [[ "$CURSOR_FORMAT" == "rulemd" ]]; then
    PRODUCT_COUNT=$(find "$OUT_DIR" -type d -mindepth 1 -maxdepth 1 2>/dev/null | wc -l | tr -d ' ')
  else
    PRODUCT_COUNT=$(find "$OUT_DIR" -maxdepth 1 -type f -name "*.mdc" 2>/dev/null | wc -l | tr -d ' ')
  fi
  MAP_COUNT=$(jq '.cursor.order | length' "$MAP_JSON")

  if [[ "$PRODUCT_COUNT" -ne "$MAP_COUNT" ]]; then
    echo "❌ 产物数量不匹配: 产物=$PRODUCT_COUNT, 映射=$MAP_COUNT"
    exit 1
  fi
  echo "  ✅ 数量匹配: $PRODUCT_COUNT 个规则"

  # 检查 2: frontmatter 格式（仅 .mdc 格式）
  if [[ "$CURSOR_FORMAT" == "mdc" ]]; then
    echo "📝 检查 frontmatter 格式..."
    for f in "$OUT_DIR"/*.mdc; do
      [[ ! -f "$f" ]] && continue

      first_line=$(head -1 "$f")
      dash_count=$(head -20 "$f" | grep -c "^---$" || echo "0")

      if [[ "$first_line" != "---" ]]; then
        echo "❌ $(basename "$f") 第一行不是 frontmatter 开始标记（---）"
        exit 1
      fi

      if [[ "$dash_count" -lt 2 ]]; then
        echo "❌ $(basename "$f") 缺少成对的 frontmatter 分隔符（前 20 行应至少有两个 ---）"
        exit 1
      fi
    done
    echo "  ✅ 所有产物 frontmatter 格式正确"
  fi

  # 检查 3: 产物一致性（diff）
  echo "🔍 检查产物一致性（diff）..."
  if diff -qr "$OUT_DIR" "$TMP_OUT_DIR" >/dev/null 2>&1; then
    echo "  ✅ 产物与源码构建结果一致"
    echo ""
    echo "✅ Validate passed: .cursor/rules matches source build output"
  else
    echo "  ❌ 产物与源码构建结果不一致"
    echo ""
    echo "--- Diff (summary) ---"
    diff -qr "$OUT_DIR" "$TMP_OUT_DIR" || true
    echo ""
    echo "Fix:"
    echo "  .agent/build/build.sh --target cursor --cursor-format $CURSOR_FORMAT"
    exit 1
  fi
else
  echo "❌ validate: unsupported target=$TARGET (currently only cursor)"
  exit 2
fi
```

## 14.5 Quick Start（5 分钟跑通）

**前置要求**：已安装 `jq`（macOS: `brew install jq`，Ubuntu: `sudo apt-get install -y jq`）

**Step 1：创建目录骨架**

```bash
mkdir -p .agent/src/{rules,skills,map/templates,rules.local}
mkdir -p .agent/build .agent/targets
mkdir -p .cursor/rules .github
```

**Step 2：添加一个 sample 规则文件**

```bash
echo -e "# 示例规则\n\n## 禁止\n- ❌ 不允许泄漏密码\n" > .agent/src/rules/00-base.rules.md
```

**Step 3：编写最小 rules-map.json**

```json
{
  "rules": {
    "00-base.rules.md": {
      "cursor": {
        "id": "00-core",
        "frontmatter": {
          "description": "基础规范（始终应用）",
          "alwaysApply": true
        }
      }
    }
  },
  "cursor": {
    "order": ["00-base.rules.md"]
  }
}
```

保存为 `.agent/src/map/rules-map.json`

**Step 4：运行 build**

```bash
.agent/build/build.sh --target cursor
```

**Step 5：校验闭环**

```bash
.agent/build/validate.sh --target cursor --cursor-format mdc
```

**预期输出**：

```text
🔍 Validate: rebuild to temp and diff
📊 检查产物数量...
  ✅ 数量匹配: 1 个规则
📝 检查 frontmatter 格式...
  ✅ 所有产物 frontmatter 格式正确
🔍 检查产物一致性（diff）...
  ✅ 产物与源码构建结果一致

✅ Validate passed: .cursor/rules matches source build output
```

如无报错，即已跑通！

---

## 13. 最小落地步骤（v2.3.1 推荐）

Step 1：创建目录骨架

```bash
mkdir -p .agent/src/{rules,skills,map/templates,rules.local}
mkdir -p .agent/build .agent/targets
mkdir -p .cursor/rules .github
```

Step 2：拆分规则到 `.agent/src/rules/*.rules.md`

建议：

- 00-base / 10-java / 20-vue / 30-antdv / 40-security / 50-testing / 60-git / 90-platform
- 可选：91-auth / 92-frontend

Step 3：编写 `.agent/src/map/rules-map.json`

至少包含 cursor.id、cursor.frontmatter、cursor.order

Step 4：实现 cursor.mdc.sh / cursor.rulemd.sh（建议 jq）

做到：模块化生成、一源一产物

Step 5：构建并提交

```bash
.agent/build/build.sh --target cursor
git add .cursor/rules AGENTS.md .agent/src .agent/build .agent/src/map
git commit -m "chore: adopt agent rules v2.3.1"
```

Step 6：CI 校验

```bash
.agent/build/validate.sh --target cursor --cursor-format mdc
```

---

## 14. AGENTS.md（推荐模板）

```markdown
# AGENTS.md（项目 AI 协作说明）

## 项目概述

- tiny-platform：插件化单体 + 多租户 + 权限治理 + 可插拔模块体系

## 唯一真相与产物

- 规则唯一真相：.agent/src/rules
- 技能/知识库：.agent/src/skills
- Cursor 产物：.cursor/rules/（Project Rules）

## 开发协作准则（适用于所有 AI 工具）

1. 不确定时：先说明假设与风险，再给出方案
2. 修改必须最小化，避免无关重构
3. 安全/权限/多租户规则不可弱化
4. 代码需可读、可测试、可回滚
5. 与既有结构冲突时：优先遵循平台特定规则（90+）

## 可用 Skills（示例）

- java-vue-standards
- tiny-platform-architecture
- tiny-platform-auth（可选）
- tiny-platform-frontend（可选）
```

---

## 15. 总结（v2.3.1）

- `.agent/src`：工具无关、可长期复用（唯一真相）
- `.cursor/rules`：对齐 Cursor Project Rules 主入口（真实产物）
- `.mdc` 与 `RULE.md folder` 双轨可切换，避免版本对赌
- build/validate 闭环，具备工程化落地条件
- 对 tiny-platform 这种长期演进/跨团队/可能多工具并行的项目，v2.3.1 是更稳的选择

---

## 16. 仓库可直接落地的“完整套件”（Copy-Paste Kit）

本章提供**可直接复制到仓库**的最小可用套件：目录骨架、脚本、映射文件样例、CI 校验、pre-commit（可选）。

> 约定：
>
> - **源码唯一真相**：`.agent/src/**`
> - **真实产物**：写入工具官方入口（Cursor -> `.cursor/rules/**`）
> - **中间产物/工作区**：`.agent/targets/**`（默认忽略，不提交）

### 16.1 一次性创建目录骨架

```bash
mkdir -p .agent/src/{rules,skills,map/templates,rules.local}
mkdir -p .agent/build .agent/targets
mkdir -p .cursor/rules .github
```

### 16.2 必须新增/提交的文件清单

> 下列文件建议**全部提交**（除 `.agent/targets/**`）。

```text
.agent/
  build/
    build.sh
    cursor.mdc.sh
    cursor.rulemd.sh
    agentsmd.sh
    validate.sh
  src/
    map/
      rules-map.json
    rules/
      00-base.rules.md   (示例；后续按模块拆分)
  .gitignore
AGENTS.md
.cursor/rules/           (构建产物，建议提交)
.github/                 (可选：copilot 指令等)
```

### 16.3 .agent/.gitignore（建议内容）

创建/更新：`.agent/.gitignore`

```gitignore
# local extensions
src/rules.local/

# adapter workspace / intermediates
targets/

# caches (optional)
cache/

# OS
.DS_Store
```

### 16.4 rules-map.json（最小可运行样例）

创建：`.agent/src/map/rules-map.json`

```json
{
  "rules": {
    "00-base.rules.md": {
      "cursor": {
        "id": "00-core",
        "frontmatter": {
          "description": "基础规范（始终应用）",
          "alwaysApply": true
        }
      }
    }
  },
  "cursor": {
    "order": ["00-base.rules.md"]
  }
}
```

### 16.5 示例规则文件（最小可运行样例）

创建：`.agent/src/rules/00-base.rules.md`

```markdown
# 基础规范（示例）

## 适用范围

- 适用于：全仓库

## 禁止（Must Not）

- ❌ 不允许在日志、注释、提交记录中泄漏密码/密钥/JWT/Token。

## 必须（Must）

- ✅ 任何不确定的信息必须显式写出假设与风险。

## 应该（Should）

- ⚠️ 修改必须最小化，避免无关重构。

## 示例

- ✅ 正例：先说明假设，再给出可执行步骤。
- ❌ 反例：直接大改架构、删除大量文件。
```

### 16.6 build.sh（可直接运行版）

创建：`.agent/build/build.sh`

```bash
#!/usr/bin/env bash
set -euo pipefail

TARGET=""
CURSOR_FORMAT="mdc"
OUTPUT_ROOT=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    --target) TARGET="$2"; shift 2 ;;
    --cursor-format) CURSOR_FORMAT="$2"; shift 2 ;;
    --output-root) OUTPUT_ROOT="$2"; shift 2 ;;
    *) echo "Unknown arg: $1"; exit 1 ;;
  esac
done

if [[ -z "$TARGET" ]]; then
  echo "Usage: .agent/build/build.sh --target <cursor|copilot|continue|windsurf|all> [--cursor-format mdc|rulemd] [--output-root <dir>]"
  exit 1
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

if [[ -z "$OUTPUT_ROOT" ]]; then
  OUTPUT_ROOT="$ROOT"
fi

export AGENT_ROOT="$ROOT/.agent"
export SRC_ROOT="$AGENT_ROOT/src"
export MAP_JSON="$SRC_ROOT/map/rules-map.json"
export OUTPUT_ROOT
export CURSOR_FORMAT

if [[ ! -f "$MAP_JSON" ]]; then
  echo "❌ rules-map.json not found: $MAP_JSON"
  echo "Hint: create it under .agent/src/map/rules-map.json"
  exit 1
fi

if ! command -v jq >/dev/null 2>&1; then
  echo "❌ jq is required but not found in PATH"
  echo "macOS:  brew install jq"
  echo "Ubuntu: sudo apt-get update && sudo apt-get install -y jq"
  exit 1
fi

case "$TARGET" in
  cursor)
    if [[ "$CURSOR_FORMAT" == "rulemd" ]]; then
      "$SCRIPT_DIR/cursor.rulemd.sh"
    else
      "$SCRIPT_DIR/cursor.mdc.sh"
    fi
    "$SCRIPT_DIR/agentsmd.sh" || true
    ;;
  all)
    "$SCRIPT_DIR/cursor.mdc.sh"
    "$SCRIPT_DIR/agentsmd.sh" || true
    ;;
  copilot|continue|windsurf)
    echo "TODO: implement target adapter: $TARGET"
    "$SCRIPT_DIR/agentsmd.sh" || true
    ;;
  *)
    echo "Unknown target: $TARGET"
    exit 1
    ;;
esac

echo "✅ Build done: $TARGET (cursor-format: $CURSOR_FORMAT, output-root: $OUTPUT_ROOT)"
```

> 记得：`chmod +x .agent/build/*.sh`

### 16.7 cursor.mdc.sh（模块化生成 .mdc，可直接运行）

创建：`.agent/build/cursor.mdc.sh`

```bash
#!/usr/bin/env bash
set -euo pipefail

: "${OUTPUT_ROOT:?OUTPUT_ROOT is required}"
: "${SRC_ROOT:?SRC_ROOT is required}"
: "${MAP_JSON:?MAP_JSON is required}"

OUT="$OUTPUT_ROOT/.cursor/rules"
SRC_RULES_DIR="$SRC_ROOT/rules"

mkdir -p "$OUT"

# 清理旧 .mdc（避免 map 删除后残留）
find "$OUT" -maxdepth 1 -type f -name "*.mdc" -print0 | xargs -0r rm -f

echo "📝 Building Cursor Project Rules (.mdc) -> $OUT"
echo "MAP: $MAP_JSON"

order=$(jq -r '.cursor.order[]' "$MAP_JSON")
for src_file in $order; do
  id=$(jq -r --arg f "$src_file" '.rules[$f].cursor.id // empty' "$MAP_JSON")
  if [[ -z "$id" || "$id" == "null" ]]; then
    echo "❌ Missing cursor.id for: $src_file"
    exit 1
  fi

  src_path="$SRC_RULES_DIR/$src_file"
  if [[ ! -f "$src_path" ]]; then
    echo "❌ Source rule file not found: $src_path"
    exit 1
  fi

  out_path="$OUT/${id}.mdc"

  fm_json=$(jq -c --arg f "$src_file" '.rules[$f].cursor.frontmatter // {}' "$MAP_JSON")

  {
    echo "---"

    desc=$(echo "$fm_json" | jq -r '.description // empty')
    if [[ -n "$desc" ]]; then
      echo "description: $desc"
    fi

    aa=$(echo "$fm_json" | jq -r '.alwaysApply // empty')
    if [[ "$aa" == "true" || "$aa" == "false" ]]; then
      echo "alwaysApply: $aa"
    fi

    has_globs=$(echo "$fm_json" | jq -r 'has("globs")')
    if [[ "$has_globs" == "true" ]]; then
      echo "globs:"
      echo "$fm_json" | jq -r '.globs[]? | "  - \"" + . + "\""'
    fi

    echo "---"
    echo
    cat "$src_path"
    echo
  } > "$out_path"

  echo "✅ $out_path"
done
```

### 16.8 cursor.rulemd.sh（模块化生成 RULE.md folder，可直接运行）

创建：`.agent/build/cursor.rulemd.sh`

```bash
#!/usr/bin/env bash
set -euo pipefail

: "${OUTPUT_ROOT:?OUTPUT_ROOT is required}"
: "${SRC_ROOT:?SRC_ROOT is required}"
: "${MAP_JSON:?MAP_JSON is required}"

OUT="$OUTPUT_ROOT/.cursor/rules"
SRC_RULES_DIR="$SRC_ROOT/rules"

mkdir -p "$OUT"

echo "📝 Building Cursor Project Rules (RULE.md folder) -> $OUT"
echo "MAP: $MAP_JSON"

order=$(jq -r '.cursor.order[]' "$MAP_JSON")
for src_file in $order; do
  id=$(jq -r --arg f "$src_file" '.rules[$f].cursor.id // empty' "$MAP_JSON")
  if [[ -z "$id" || "$id" == "null" ]]; then
    echo "❌ Missing cursor.id for: $src_file"
    exit 1
  fi

  src_path="$SRC_RULES_DIR/$src_file"
  if [[ ! -f "$src_path" ]]; then
    echo "❌ Source rule file not found: $src_path"
    exit 1
  fi

  out_dir="$OUT/$id"
  rm -rf "$out_dir"
  mkdir -p "$out_dir"

  out_path="$out_dir/RULE.md"

  fm_json=$(jq -c --arg f "$src_file" '.rules[$f].cursor.frontmatter // {}' "$MAP_JSON")

  {
    echo "---"

    desc=$(echo "$fm_json" | jq -r '.description // empty')
    if [[ -n "$desc" ]]; then
      echo "description: $desc"
    fi

    aa=$(echo "$fm_json" | jq -r '.alwaysApply // empty')
    if [[ "$aa" == "true" || "$aa" == "false" ]]; then
      echo "alwaysApply: $aa"
    fi

    has_globs=$(echo "$fm_json" | jq -r 'has("globs")')
    if [[ "$has_globs" == "true" ]]; then
      echo "globs:"
      echo "$fm_json" | jq -r '.globs[]? | "  - \"" + . + "\""'
    fi

    echo "---"
    echo
    cat "$src_path"
    echo
  } > "$out_path"

  echo "✅ $out_path"
done
```

### 16.9 agentsmd.sh（最小实现：生成/更新 AGENTS.md）

创建：`.agent/build/agentsmd.sh`

```bash
#!/usr/bin/env bash
set -euo pipefail

: "${OUTPUT_ROOT:?OUTPUT_ROOT is required}"

OUT="$OUTPUT_ROOT/AGENTS.md"

cat > "$OUT" <<'EOF'
# AGENTS.md（项目 AI 协作说明）

## 项目概述
- tiny-platform：插件化单体 + 多租户 + 权限治理 + 可插拔模块体系

## 唯一真相与产物
- 规则唯一真相：.agent/src/rules
- 技能/知识库：.agent/src/skills
- Cursor 产物：.cursor/rules/（Project Rules）

## 开发协作准则（适用于所有 AI 工具）
1) 不确定时：先说明假设与风险，再给出方案
2) 修改必须最小化，避免无关重构
3) 安全/权限/多租户规则不可弱化
4) 代码需可读、可测试、可回滚
5) 与既有结构冲突时：优先遵循平台特定规则（90+）

## 可用 Skills（示例）
- java-vue-standards
- tiny-platform-architecture
- tiny-platform-auth（可选）
- tiny-platform-frontend（可选）
EOF

echo "✅ Generated: $OUT"
```

> 如果你希望“部分可手写”，建议改成：仅覆盖 `<!-- BEGIN GENERATED -->` 区块。

### 16.10 validate.sh（三项检查：数量 + frontmatter + diff，可直接用于 CI）

创建：`.agent/build/validate.sh`

```bash
#!/usr/bin/env bash
set -euo pipefail

TARGET="cursor"
CURSOR_FORMAT="mdc"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --target) TARGET="$2"; shift 2 ;;
    --cursor-format) CURSOR_FORMAT="$2"; shift 2 ;;
    *) echo "Unknown arg: $1"; exit 1 ;;
  esac
done

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
TMP="/tmp/agent-validate-$$"
trap 'rm -rf "$TMP"' EXIT
mkdir -p "$TMP"

echo "🔍 Validate: rebuild to temp and diff"
echo "TARGET=$TARGET CURSOR_FORMAT=$CURSOR_FORMAT"

"$ROOT/.agent/build/build.sh" --target "$TARGET" --cursor-format "$CURSOR_FORMAT" --output-root "$TMP"

if [[ "$TARGET" == "cursor" ]]; then
  if diff -qr "$ROOT/.cursor/rules" "$TMP/.cursor/rules" >/dev/null 2>&1; then
    echo "✅ Validate passed: .cursor/rules matches source"
  else
    echo "❌ Validate failed: .cursor/rules differs from source build output"
    echo "--- Diff (summary) ---"
    diff -qr "$ROOT/.cursor/rules" "$TMP/.cursor/rules" || true
    echo
    echo "Fix: run .agent/build/build.sh --target cursor --cursor-format $CURSOR_FORMAT"
    exit 1
  fi
else
  echo "TODO: implement validate for target=$TARGET"
  exit 2
fi
```

### 16.11 一次跑通（本地验证流程）

```bash
# 1) 确保 jq 可用
jq --version

# 2) 赋予脚本可执行权限
chmod +x .agent/build/*.sh

# 3) 生成 Cursor 产物（.mdc）
.agent/build/build.sh --target cursor --cursor-format mdc

# 4) 校验（数量 + frontmatter + diff）
.agent/build/validate.sh --target cursor --cursor-format mdc
```

### 16.12 CI（GitHub Actions）建议（可选）

创建：`.github/workflows/agent-rules-validate.yml`

```yaml
name: agent-rules-validate

on:
  pull_request:
  push:
    branches: [main, master]

jobs:
  validate:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - name: Install jq
        run: sudo apt-get update && sudo apt-get install -y jq
      - name: Validate
        run: |
          chmod +x .agent/build/*.sh
          .agent/build/validate.sh --target cursor --cursor-format mdc
```

### 16.13 pre-commit（可选，团队一致性更强）

建议策略：**只在提交时校验，不强制自动写产物**（避免“提交钩子改动文件”带来的困扰）。

如果要做 pre-commit，可在项目根目录新增 `.git/hooks/pre-commit`：

```bash
#!/usr/bin/env bash
set -euo pipefail

if [[ -d ".agent" ]]; then
  chmod +x .agent/build/*.sh || true
  .agent/build/validate.sh --target cursor --cursor-format mdc
fi
```

并执行：

```bash
chmod +x .git/hooks/pre-commit
```

---

> ✅ 到这里，你已经拥有一套“源码唯一真相 + 模块化产物 + build/validate 闭环 + CI 校验”的**可直接落地套件**。
>
> 下一步的工作就是：把你现有的 428 行规则按模块拆到 `.agent/src/rules/*.rules.md`，并在 `rules-map.json` 中逐个配置 `id/frontmatter`。
