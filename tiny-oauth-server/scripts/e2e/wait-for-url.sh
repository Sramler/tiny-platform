#!/usr/bin/env bash
set -euo pipefail

TARGET_URL="${1:-}"
TIMEOUT_SECONDS="${2:-120}"
INTERVAL_SECONDS="${3:-2}"

if [[ -z "${TARGET_URL}" ]]; then
  echo "usage: $0 <url> [timeout_seconds] [interval_seconds]" >&2
  exit 2
fi

if [[ "${TIMEOUT_SECONDS}" -le 0 || "${INTERVAL_SECONDS}" -le 0 ]]; then
  echo "timeout and interval must be positive integers" >&2
  exit 2
fi

echo "wait-for-url target=${TARGET_URL} timeout=${TIMEOUT_SECONDS}s interval=${INTERVAL_SECONDS}s"

elapsed=0
while [[ "${elapsed}" -lt "${TIMEOUT_SECONDS}" ]]; do
  if curl -fsS "${TARGET_URL}" >/dev/null 2>&1; then
    echo "ready: ${TARGET_URL}"
    exit 0
  fi
  sleep "${INTERVAL_SECONDS}"
  elapsed=$((elapsed + INTERVAL_SECONDS))
done

echo "timeout waiting for ${TARGET_URL}" >&2
exit 1
