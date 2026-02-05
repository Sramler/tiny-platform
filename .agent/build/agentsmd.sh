#!/usr/bin/env bash
set -euo pipefail

: "${OUTPUT_ROOT:?OUTPUT_ROOT is required}"

OUT="$OUTPUT_ROOT/AGENTS.md"
BEGIN_MARK="<!-- BEGIN GENERATED:AGENTS -->"
END_MARK="<!-- END GENERATED:AGENTS -->"

# 你要写入生成块的内容：可以来自 build 过程中收集的内容
# 这里给一个示例：后续你可以改成从 rules-map.json 读取（那就需要 jq）
GENERATED_BLOCK_CONTENT="$(cat <<'EOF'
<!-- BEGIN GENERATED:AGENTS -->
## 生成区（自动生成）

- 规则系统版本：v2.3.1
- 构建命令：.agent/build/build.sh --target cursor
- 校验命令：.agent/build/validate.sh --target cursor --cursor-format mdc

> 注意：本区块由脚本生成，禁止手改。
<!-- END GENERATED:AGENTS -->
EOF
)"

# 如果 AGENTS.md 不存在，先写入一个“半手写 + 生成块”模板
if [[ ! -f "$OUT" ]]; then
  cat > "$OUT" <<EOF
# AGENTS.md（项目 AI 协作说明）

> 目标：让“人 + AI”在同一套规则体系下协作；并且跨工具（Cursor/Copilot/Continue/Windsurf）通用。

---

## 0. 快速入口（给人）

- 规则唯一真相：.agent/src/**
- Cursor 生效入口（生成物）：.cursor/rules/**
- 构建：.agent/build/build.sh --target cursor
- 校验：.agent/build/validate.sh --target cursor --cursor-format mdc

---

## 1. 项目概述（手写区）

（请手写：项目定位、关键模块、强约束、常用命令等）

---

## 2. 协作铁律（手写区）

1) 先假设后执行：不确定必须写出假设与风险  
2) 修改最小化：不做无关重构；必须可回滚  
3) 安全/权限/多租户不可弱化：削弱必须说明并请求确认  
4) 代码必须可运行：给出可执行命令/路径/文件清单  
5) 产物禁止手改：.cursor/rules/** 等生成物不手工编辑（只改 .agent/src/**）

---

$BEGIN_MARK
$END_MARK

---

## 4. 项目特有补充（手写区）

（请手写：表命名、JWT/Session 策略、前端栈、安全策略等）

EOF
fi

# 把生成块内容放入临时文件，避免复杂转义
TMP_BLOCK="$(mktemp)"
trap 'rm -f "$TMP_BLOCK"' EXIT
printf "%s\n" "$GENERATED_BLOCK_CONTENT" > "$TMP_BLOCK"

# 用 awk 替换区块：跨平台最稳
TMP_OUT="$(mktemp)"
awk -v BEGIN_MARK="$BEGIN_MARK" -v END_MARK="$END_MARK" -v BLOCK_FILE="$TMP_BLOCK" '
BEGIN {
  in_block = 0
  replaced = 0
}
{
  if ($0 == BEGIN_MARK) {
    # 写入整个生成块（包含 BEGIN/END）
    while ((getline line < BLOCK_FILE) > 0) {
      print line
    }
    close(BLOCK_FILE)
    in_block = 1
    replaced = 1
    next
  }

  if (in_block) {
    if ($0 == END_MARK) {
      in_block = 0
    }
    next
  }

  print
}
END {
  if (replaced == 0) {
    # 如果没找到标记，直接失败（避免默默写错）
    exit 2
  }
}
' "$OUT" > "$TMP_OUT" || {
  code=$?
  if [[ $code -eq 2 ]]; then
    echo "❌ AGENTS.md missing markers:"
    echo "   $BEGIN_MARK"
    echo "   $END_MARK"
    echo "Fix: add the markers once, then rerun agentsmd.sh"
  else
    echo "❌ Failed to update generated block (awk exit=$code)"
  fi
  rm -f "$TMP_OUT"
  exit 1
}

mv "$TMP_OUT" "$OUT"
echo "✅ Updated: $OUT"