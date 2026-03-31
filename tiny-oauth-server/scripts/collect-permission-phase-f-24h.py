#!/usr/bin/env python3
"""
Collect Phase F-2 runtime signals from application logs.

Signals:
  - DENY_DISABLED
  - DENY_UNKNOWN
  - ROLE_ASSIGNMENT_CHANGED
  - OLD_PERMISSION_INPUT_CHANGED
  - ROLE_PERMISSION_CHANGED
  - PERMISSION_MASTER_CHANGED
  - ROLE_HIERARCHY_CHANGED

Output:
  - JSON summary to stdout
  - Optional markdown table file (--markdown-out)
"""

from __future__ import annotations

import argparse
import json
import re
from collections import Counter, defaultdict
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable


SIGNALS = [
    "DENY_DISABLED",
    "DENY_UNKNOWN",
    "ROLE_ASSIGNMENT_CHANGED",
    "OLD_PERMISSION_INPUT_CHANGED",
    "ROLE_PERMISSION_CHANGED",
    "PERMISSION_MASTER_CHANGED",
    "ROLE_HIERARCHY_CHANGED",
]


PATTERNS = [
    ("DENY_DISABLED", re.compile(r"authority deny disabled permissions")),
    ("DENY_UNKNOWN", re.compile(r"authority deny unknown permissions")),
]

VERSION_REASON_RE = re.compile(r"versionChangeReason=\[([^\]]*)\]")
TENANT_RE = re.compile(r"tenantId=([^,\s]+)")
SCOPE_RE = re.compile(r"scopeType=([^,\s]+)")
SOURCE_RE = re.compile(r"signalSource=([^,\s]+)")
@dataclass
class Event:
    signal: str
    tenant_id: str
    scope_type: str
    signal_source: str


def list_log_files(log_dir: Path) -> list[Path]:
    if not log_dir.exists():
        return []
    return sorted([p for p in log_dir.rglob("*.log") if p.is_file()])


def parse_line(line: str) -> list[Event]:
    tenant = "UNKNOWN"
    scope = "UNKNOWN"
    tenant_match = TENANT_RE.search(line)
    if tenant_match:
        tenant = tenant_match.group(1)
    scope_match = SCOPE_RE.search(line)
    if scope_match:
        scope = scope_match.group(1)
    signal_source = "RUNTIME"
    source_match = SOURCE_RE.search(line)
    if source_match:
        raw_source = source_match.group(1).strip().upper()
        if raw_source in ("TEST", "RUNTIME"):
            signal_source = raw_source

    events: list[Event] = []
    for signal, pattern in PATTERNS:
        if pattern.search(line):
            events.append(Event(signal, tenant, scope, signal_source))

    if "permissionsVersion inputs summary" in line:
        reason_match = VERSION_REASON_RE.search(line)
        if reason_match:
            raw = reason_match.group(1).strip()
            if raw:
                for token in [t.strip() for t in raw.split(",") if t.strip()]:
                    if token in SIGNALS:
                        events.append(Event(token, tenant, scope, signal_source))
    return events


def collect_events(lines: Iterable[str]) -> list[Event]:
    events: list[Event] = []
    for line in lines:
        events.extend(parse_line(line))
    return events


def summarize(events: list[Event]) -> dict:
    by_signal = Counter()
    by_source_signal: dict[str, Counter] = defaultdict(Counter)
    by_tenant_signal: dict[str, Counter] = defaultdict(Counter)
    by_scope_signal: dict[str, Counter] = defaultdict(Counter)

    for event in events:
        by_signal[event.signal] += 1
        by_source_signal[event.signal_source][event.signal] += 1
        by_tenant_signal[event.tenant_id][event.signal] += 1
        by_scope_signal[event.scope_type][event.signal] += 1

    for signal in SIGNALS:
        by_signal.setdefault(signal, 0)

    return {
        "total_events": sum(by_signal.values()),
        "signals": dict(by_signal),
        "by_source": {source: dict(counter) for source, counter in sorted(by_source_signal.items())},
        "by_tenant": {tenant: dict(counter) for tenant, counter in sorted(by_tenant_signal.items())},
        "by_scope": {scope: dict(counter) for scope, counter in sorted(by_scope_signal.items())},
    }


def render_markdown(summary: dict) -> str:
    lines = []
    lines.append("## Phase F-2 Runtime Signal Summary\n")
    lines.append(f"- Total matched events: `{summary['total_events']}`\n")

    lines.append("### Signals\n")
    lines.append("| signal | count |")
    lines.append("| --- | ---: |")
    for signal in SIGNALS:
        lines.append(f"| {signal} | {summary['signals'].get(signal, 0)} |")
    lines.append("")

    lines.append("### Source Buckets\n")
    lines.append("| signal_source | signal | count |")
    lines.append("| --- | --- | ---: |")
    if summary["by_source"]:
        for source, signal_map in summary["by_source"].items():
            for signal in SIGNALS:
                if signal in signal_map:
                    lines.append(f"| {source} | {signal} | {signal_map[signal]} |")
    else:
        lines.append("| (none) | (none) | 0 |")
    lines.append("")

    lines.append("### Tenant Buckets\n")
    lines.append("| tenantId | signal | count |")
    lines.append("| --- | --- | ---: |")
    if summary["by_tenant"]:
        for tenant, signal_map in summary["by_tenant"].items():
            for signal in SIGNALS:
                if signal in signal_map:
                    lines.append(f"| {tenant} | {signal} | {signal_map[signal]} |")
    else:
        lines.append("| (none) | (none) | 0 |")
    lines.append("")

    lines.append("### Scope Buckets\n")
    lines.append("| scopeType | signal | count |")
    lines.append("| --- | --- | ---: |")
    if summary["by_scope"]:
        for scope, signal_map in summary["by_scope"].items():
            for signal in SIGNALS:
                if signal in signal_map:
                    lines.append(f"| {scope} | {signal} | {signal_map[signal]} |")
    else:
        lines.append("| (none) | (none) | 0 |")
    lines.append("")
    return "\n".join(lines)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--log-dir", required=True, help="Directory containing .log files")
    parser.add_argument("--markdown-out", help="Optional markdown output path")
    args = parser.parse_args()

    log_dir = Path(args.log_dir)
    files = list_log_files(log_dir)
    if not files:
        summary = {"total_events": 0, "signals": {signal: 0 for signal in SIGNALS}, "by_source": {}, "by_tenant": {}, "by_scope": {}}
    else:
        lines: list[str] = []
        for file in files:
            try:
                lines.extend(file.read_text(encoding="utf-8", errors="ignore").splitlines())
            except OSError:
                continue
        summary = summarize(collect_events(lines))

    print(json.dumps(summary, ensure_ascii=False, indent=2))

    if args.markdown_out:
        md_path = Path(args.markdown_out)
        md_path.parent.mkdir(parents=True, exist_ok=True)
        md_path.write_text(render_markdown(summary), encoding="utf-8")

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
