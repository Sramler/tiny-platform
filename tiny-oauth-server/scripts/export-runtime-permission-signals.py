#!/usr/bin/env python3
"""
Export runtime-only permission signal breakdown from log files.

Only lines that explicitly contain `signalSource=RUNTIME` are counted.
"""

from __future__ import annotations

import argparse
import json
import re
from collections import defaultdict
from dataclasses import dataclass
from datetime import datetime
from pathlib import Path


LINE_TS_RE = re.compile(r"^(\d{2}:\d{2}:\d{2}\.\d{3})")
SIGNAL_SOURCE_RE = re.compile(r"signalSource=([^,\s]+)")
TENANT_RE = re.compile(r"tenantId=([^,\s]+)")
SCOPE_TYPE_RE = re.compile(r"scopeType=([^,\s]+)")
SCOPE_ID_RE = re.compile(r"scopeId=([^,\s]+)")
USER_ID_RE = re.compile(r"userId=([^,\s]+)")

DENY_DISABLED_RE = re.compile(r"authority deny disabled permissions")
DENY_UNKNOWN_RE = re.compile(r"authority deny unknown permissions")

DENIED_CODES_RE = re.compile(r"deniedPermissionCodes=\[([^\]]*)\]")


@dataclass(frozen=True)
class Key:
    signal_source: str
    signal_type: str
    permission_code: str
    tenant_id: str
    scope_type: str
    scope_id: str
    user_id_or_user_key: str


def parse_codes(raw: str | None) -> list[str]:
    if not raw:
        return []
    codes = []
    for item in raw.split(","):
        code = item.strip()
        if code:
            codes.append(code)
    return codes


def extract_signal_and_codes(line: str) -> tuple[str | None, list[str]]:
    if DENY_DISABLED_RE.search(line):
        denied_match = DENIED_CODES_RE.search(line)
        return "DENY_DISABLED", parse_codes(denied_match.group(1) if denied_match else "")
    if DENY_UNKNOWN_RE.search(line):
        denied_match = DENIED_CODES_RE.search(line)
        return "DENY_UNKNOWN", parse_codes(denied_match.group(1) if denied_match else "")
    return None, []


def parse_time(day: str, line: str) -> str:
    ts_match = LINE_TS_RE.search(line)
    if not ts_match:
        return ""
    return f"{day} {ts_match.group(1)}"


def parse_log_file(path: Path, day: str, bucket: dict[Key, dict]) -> None:
    for line in path.read_text(encoding="utf-8", errors="ignore").splitlines():
        source_match = SIGNAL_SOURCE_RE.search(line)
        if not source_match:
            continue
        signal_source = source_match.group(1).strip().upper()
        if signal_source != "RUNTIME":
            continue

        signal_type, permission_codes = extract_signal_and_codes(line)
        if signal_type is None or not permission_codes:
            continue

        tenant_id = TENANT_RE.search(line).group(1) if TENANT_RE.search(line) else "UNKNOWN"
        scope_type = SCOPE_TYPE_RE.search(line).group(1) if SCOPE_TYPE_RE.search(line) else "UNKNOWN"
        scope_id = SCOPE_ID_RE.search(line).group(1) if SCOPE_ID_RE.search(line) else "UNKNOWN"
        user_key = USER_ID_RE.search(line).group(1) if USER_ID_RE.search(line) else "UNKNOWN"
        seen_at = parse_time(day, line)

        for permission_code in permission_codes:
            key = Key(
                signal_source="RUNTIME",
                signal_type=signal_type,
                permission_code=permission_code,
                tenant_id=tenant_id,
                scope_type=scope_type,
                scope_id=scope_id,
                user_id_or_user_key=user_key,
            )
            row = bucket.setdefault(
                key,
                {
                    "signal_source": "RUNTIME",
                    "signal_type": signal_type,
                    "permission_code": permission_code,
                    "tenantId": tenant_id,
                    "scopeType": scope_type,
                    "scopeId": scope_id,
                    "userId_or_userKey": user_key,
                    "hit_count": 0,
                    "first_seen": seen_at,
                    "last_seen": seen_at,
                },
            )
            row["hit_count"] += 1
            if seen_at and (not row["first_seen"] or seen_at < row["first_seen"]):
                row["first_seen"] = seen_at
            if seen_at and (not row["last_seen"] or seen_at > row["last_seen"]):
                row["last_seen"] = seen_at


def to_markdown(rows: list[dict]) -> str:
    lines = [
        "# Runtime Permission Signals Breakdown",
        "",
        "| signal_source | signal_type | permission_code | tenantId | scopeType | scopeId | userId_or_userKey | hit_count | first_seen | last_seen |",
        "| --- | --- | --- | ---: | --- | --- | --- | ---: | --- | --- |",
    ]
    if not rows:
        lines.append("| RUNTIME | (none) | (none) | - | - | - | - | 0 | - | - |")
        return "\n".join(lines) + "\n"
    for row in rows:
        lines.append(
            f"| {row['signal_source']} | {row['signal_type']} | {row['permission_code']} | "
            f"{row['tenantId']} | {row['scopeType']} | {row['scopeId']} | {row['userId_or_userKey']} | "
            f"{row['hit_count']} | {row['first_seen'] or '-'} | {row['last_seen'] or '-'} |"
        )
    return "\n".join(lines) + "\n"


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--log-dir", required=True)
    parser.add_argument("--json-out", required=True)
    parser.add_argument("--markdown-out", required=True)
    parser.add_argument("--day", default=datetime.now().strftime("%Y-%m-%d"))
    args = parser.parse_args()

    log_dir = Path(args.log_dir)
    bucket: dict[Key, dict] = {}
    for path in sorted(log_dir.glob("*.log")):
        parse_log_file(path, args.day, bucket)

    rows = sorted(
        bucket.values(),
        key=lambda row: (
            row["signal_type"],
            row["tenantId"],
            row["scopeType"],
            row["scopeId"],
            row["permission_code"],
            row["userId_or_userKey"],
        ),
    )
    summary = {
        "total_rows": len(rows),
        "total_hits": sum(row["hit_count"] for row in rows),
        "rows": rows,
    }

    json_out = Path(args.json_out)
    md_out = Path(args.markdown_out)
    json_out.parent.mkdir(parents=True, exist_ok=True)
    md_out.parent.mkdir(parents=True, exist_ok=True)
    json_out.write_text(json.dumps(summary, ensure_ascii=False, indent=2), encoding="utf-8")
    md_out.write_text(to_markdown(rows), encoding="utf-8")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
