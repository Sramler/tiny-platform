#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'EOF'
Usage:
  .agent/build/build.sh --target <cursor|all> [--cursor-format mdc|rulemd] [--output-root <dir>] [--no-agents]

Examples:
  .agent/build/build.sh --target cursor
  .agent/build/build.sh --target cursor --cursor-format rulemd
  .agent/build/build.sh --target all
  .agent/build/build.sh --target cursor --output-root /tmp/out
EOF
}

TARGET=""
CURSOR_FORMAT="mdc"
OUTPUT_ROOT=""
NO_AGENTS="false"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --target) TARGET="${2:-}"; shift 2 ;;
    --cursor-format) CURSOR_FORMAT="${2:-}"; shift 2 ;;
    --output-root) OUTPUT_ROOT="${2:-}"; shift 2 ;;
    --no-agents) NO_AGENTS="true"; shift 1 ;;
    -h|--help) usage; exit 0 ;;
    *) echo "❌ Unknown arg: $1"; usage; exit 1 ;;
  esac
done

if [[ -z "$TARGET" ]]; then
  echo "❌ --target is required"
  usage
  exit 1
fi

if [[ "$CURSOR_FORMAT" != "mdc" && "$CURSOR_FORMAT" != "rulemd" ]]; then
  echo "❌ --cursor-format must be mdc|rulemd (got: $CURSOR_FORMAT)"
  exit 1
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

if [[ -z "$OUTPUT_ROOT" ]]; then
  OUTPUT_ROOT="$ROOT"
fi

export OUTPUT_ROOT
export PROJECT_ROOT="$ROOT"
export AGENT_ROOT="$ROOT/.agent"
export SRC_ROOT="$AGENT_ROOT/src"
export MAP_JSON="$SRC_ROOT/map/rules-map.json"
export CURSOR_FORMAT

# ---- sanity checks ----
if [[ ! -d "$AGENT_ROOT" ]]; then
  echo "❌ .agent not found under project root: $AGENT_ROOT"
  exit 1
fi

if [[ ! -d "$SRC_ROOT" ]]; then
  echo "❌ src not found: $SRC_ROOT"
  exit 1
fi

if [[ ! -f "$MAP_JSON" ]]; then
  echo "❌ rules-map.json not found: $MAP_JSON"
  echo "Hint: create it at .agent/src/map/rules-map.json"
  exit 1
fi

if ! command -v jq >/dev/null 2>&1; then
  echo "❌ jq is required but not found in PATH"
  echo "macOS:  brew install jq"
  echo "Ubuntu: sudo apt-get update && sudo apt-get install -y jq"
  exit 1
fi

# ---- dispatch ----
case "$TARGET" in
  cursor)
    if [[ "$CURSOR_FORMAT" == "rulemd" ]]; then
      "$SCRIPT_DIR/cursor.rulemd.sh"
    else
      "$SCRIPT_DIR/cursor.mdc.sh"
    fi

    if [[ "$NO_AGENTS" != "true" ]]; then
      "$SCRIPT_DIR/agentsmd.sh"
    fi
    ;;
  all)
    # 当前只实现 cursor + agents（其它 target 后续再加）
    "$SCRIPT_DIR/cursor.mdc.sh"
    if [[ "$NO_AGENTS" != "true" ]]; then
      "$SCRIPT_DIR/agentsmd.sh"
    fi
    ;;
  *)
    echo "❌ Unknown target: $TARGET"
    usage
    exit 1
    ;;
esac

echo "✅ Build done: target=$TARGET, cursor-format=$CURSOR_FORMAT, output-root=$OUTPUT_ROOT"