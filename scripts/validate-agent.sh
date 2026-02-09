#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE'
Usage:
  scripts/validate-agent.sh [mdc|rulemd]

Examples:
  scripts/validate-agent.sh
  scripts/validate-agent.sh mdc
  scripts/validate-agent.sh rulemd
USAGE
}

FORMAT="${1:-mdc}"
if [[ "$FORMAT" == "-h" || "$FORMAT" == "--help" ]]; then
  usage
  exit 0
fi

if [[ "$FORMAT" != "mdc" && "$FORMAT" != "rulemd" ]]; then
  echo "❌ Invalid format: $FORMAT (expected mdc|rulemd)"
  usage
  exit 1
fi

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

"$ROOT/.agent/build/build.sh" --target cursor --cursor-format "$FORMAT"
"$ROOT/.agent/build/validate.sh" --target cursor --cursor-format "$FORMAT"
