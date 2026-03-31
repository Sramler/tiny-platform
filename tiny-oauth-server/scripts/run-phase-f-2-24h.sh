#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/../.." && pwd)"
LOG_DIR="${1:-$ROOT_DIR/logs}"
OUT_DIR="${2:-$ROOT_DIR/test-results}"

mkdir -p "$OUT_DIR"

START_TS="$(date '+%Y-%m-%d %H:%M:%S')"
echo "[Phase F-2] start at $START_TS"
echo "[Phase F-2] log dir: $LOG_DIR"
echo "[Phase F-2] output dir: $OUT_DIR"

for i in $(seq 1 24); do
  NOW_TS="$(date '+%Y-%m-%d %H:%M:%S')"
  echo "[Phase F-2] hour $i/24 checkpoint at $NOW_TS"
  sleep 3600
done

python3 "$ROOT_DIR/tiny-oauth-server/scripts/collect-permission-phase-f-24h.py" \
  --log-dir "$LOG_DIR" \
  --markdown-out "$OUT_DIR/phase-f-2-runtime-signals.md" \
  > "$OUT_DIR/phase-f-2-runtime-signals.json"

END_TS="$(date '+%Y-%m-%d %H:%M:%S')"
echo "[Phase F-2] finished at $END_TS"
echo "[Phase F-2] summary json: $OUT_DIR/phase-f-2-runtime-signals.json"
echo "[Phase F-2] summary markdown: $OUT_DIR/phase-f-2-runtime-signals.md"
