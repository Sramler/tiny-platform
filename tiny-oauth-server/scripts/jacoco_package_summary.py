#!/usr/bin/env python3
"""
Summarize JaCoCo XML coverage by package/class for tiny-oauth-server.

Usage:
  python3 tiny-oauth-server/scripts/jacoco_package_summary.py \
    --xml tiny-oauth-server/target/site/jacoco/jacoco.xml
"""

from __future__ import annotations

import argparse
import sys
import xml.etree.ElementTree as ET
from dataclasses import dataclass


@dataclass
class Ratio:
    covered: int
    missed: int

    @property
    def total(self) -> int:
        return self.covered + self.missed

    @property
    def pct(self) -> float:
        return 100.0 if self.total == 0 else (self.covered * 100.0 / self.total)


def read_counter(node: ET.Element, counter_type: str) -> Ratio:
    for counter in node.findall("counter"):
        if counter.get("type") == counter_type:
            return Ratio(
                covered=int(counter.get("covered", "0")),
                missed=int(counter.get("missed", "0")),
            )
    return Ratio(covered=0, missed=0)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--xml", required=True, help="Path to jacoco.xml")
    parser.add_argument("--top", type=int, default=20, help="Top N rows to print")
    parser.add_argument(
        "--class-line-threshold",
        type=float,
        default=30.0,
        help="Line coverage threshold for listing low-coverage classes (percent)",
    )
    parser.add_argument(
        "--package-prefix",
        default="",
        help="Only include package/class names starting with this prefix (slash format)",
    )
    args = parser.parse_args()

    root = ET.parse(args.xml).getroot()

    overall_line = read_counter(root, "LINE")
    overall_branch = read_counter(root, "BRANCH")
    overall_method = read_counter(root, "METHOD")

    print("Overall Coverage")
    print(f"LINE   {overall_line.covered}/{overall_line.total}  {overall_line.pct:.2f}%")
    print(f"BRANCH {overall_branch.covered}/{overall_branch.total}  {overall_branch.pct:.2f}%")
    print(f"METHOD {overall_method.covered}/{overall_method.total}  {overall_method.pct:.2f}%")
    print()

    package_rows = []
    low_classes = []

    for pkg in root.findall("package"):
        package_name = pkg.get("name", "")
        if args.package_prefix and not package_name.startswith(args.package_prefix):
            continue

        pkg_line = read_counter(pkg, "LINE")
        if pkg_line.total > 0:
            package_rows.append((pkg_line.pct, pkg_line.total, package_name))

        for cls in pkg.findall("class"):
            class_name = cls.get("name", "")
            if args.package_prefix and not class_name.startswith(args.package_prefix):
                continue
            line = read_counter(cls, "LINE")
            if line.total == 0:
                continue
            if line.pct < args.class_line_threshold:
                low_classes.append((line.pct, line.total, class_name))

    package_rows.sort(key=lambda x: (x[0], x[1], x[2]))
    low_classes.sort(key=lambda x: (x[0], x[1], x[2]))

    print(f"Worst Packages (top {args.top})")
    for pct, total, name in package_rows[: args.top]:
        print(f"{pct:6.2f}%  lines={total:4d}  {name}")
    print()

    print(
        f"Classes Below {args.class_line_threshold:.1f}% LINE (showing {min(args.top, len(low_classes))}/{len(low_classes)})"
    )
    for pct, total, name in low_classes[: args.top]:
        print(f"{pct:6.2f}%  lines={total:4d}  {name}")

    return 0


if __name__ == "__main__":
    sys.exit(main())
