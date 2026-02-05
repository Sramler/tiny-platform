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
echo "TMP=$TMP"

# 重建到临时 output-root
"$ROOT/.agent/build/build.sh" --target "$TARGET" --cursor-format "$CURSOR_FORMAT" --output-root "$TMP" --no-agents

if [[ "$TARGET" == "cursor" ]]; then
  RULES_DIR="$ROOT/.agent/src/rules"
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