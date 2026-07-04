#!/usr/bin/env python3
"""
Aggregate Kover XML reports and write a module coverage table to GitHub Step Summary.

Usage:
    aggregate-kover-coverage.py <coverage-root>
"""

from __future__ import annotations

import glob
import os
import sys
import xml.etree.ElementTree as ET
from dataclasses import dataclass


@dataclass
class Counter:
    covered: int = 0
    missed: int = 0

    @property
    def total(self) -> int:
        return self.covered + self.missed

    @property
    def percent(self) -> float:
        return (self.covered * 100.0 / self.total) if self.total else 0.0

    def add(self, other: "Counter") -> None:
        self.covered += other.covered
        self.missed += other.missed


@dataclass
class ModuleCoverage:
    module: str
    reports: int
    instruction: Counter
    line: Counter


def read_counter(root: ET.Element, counter_type: str) -> Counter:
    counter = Counter()
    for node in root.findall("counter"):
        if node.get("type") == counter_type:
            counter.covered += int(node.get("covered", "0"))
            counter.missed += int(node.get("missed", "0"))
    return counter


def parse_report(path: str) -> tuple[Counter, Counter]:
    root = ET.parse(path).getroot()
    return read_counter(root, "INSTRUCTION"), read_counter(root, "LINE")


def module_from_path(root_dir: str, path: str) -> str:
    rel_path = os.path.relpath(path, root_dir)
    parts = rel_path.split(os.sep)
    for index in range(len(parts) - 1, -1, -1):
        if parts[index] == "build" and index >= 1:
            return parts[index - 1]

    artifact_dir = parts[0] if parts else ""
    if artifact_dir.startswith("coverage-"):
        return artifact_dir.removeprefix("coverage-")
    return os.path.basename(os.path.dirname(path))


def collect(root_dir: str) -> list[ModuleCoverage]:
    reports_by_module: dict[str, list[str]] = {}
    for pattern in (
        os.path.join(root_dir, "**", "report.xml"),
        os.path.join(root_dir, "**", "reportJvm.xml"),
    ):
        for xml_path in sorted(glob.glob(pattern, recursive=True)):
            module = module_from_path(root_dir, xml_path)
            reports_by_module.setdefault(module, []).append(xml_path)

    rows: list[ModuleCoverage] = []
    for module in sorted(reports_by_module):
        instruction = Counter()
        line = Counter()
        for xml_path in reports_by_module[module]:
            report_instruction, report_line = parse_report(xml_path)
            instruction.add(report_instruction)
            line.add(report_line)
        rows.append(
            ModuleCoverage(
                module=module,
                reports=len(reports_by_module[module]),
                instruction=instruction,
                line=line,
            ),
        )
    return rows


def render(rows: list[ModuleCoverage]) -> str:
    lines = ["## Kover Coverage Summary", ""]
    if not rows:
        lines.append("_No coverage reports found._")
        return "\n".join(lines) + "\n"

    total_instruction = Counter()
    total_line = Counter()
    lines.extend(
        [
            "| Module | Reports | Line Coverage | Instruction Coverage |",
            "|--------|--------:|--------------:|---------------------:|",
        ],
    )

    for row in rows:
        total_instruction.add(row.instruction)
        total_line.add(row.line)
        lines.append(
            f"| `{row.module}` | {row.reports} | "
            f"{row.line.percent:.2f}% ({row.line.covered}/{row.line.total}) | "
            f"{row.instruction.percent:.2f}% ({row.instruction.covered}/{row.instruction.total}) |",
        )

    lines.append(
        f"| **TOTAL** |  | "
        f"**{total_line.percent:.2f}% ({total_line.covered}/{total_line.total})** | "
        f"**{total_instruction.percent:.2f}% ({total_instruction.covered}/{total_instruction.total})** |",
    )
    return "\n".join(lines) + "\n"


def main() -> int:
    root_dir = sys.argv[1] if len(sys.argv) > 1 else "coverage-artifacts"
    rows = collect(root_dir)
    output = render(rows)

    summary_path = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary_path:
        with open(summary_path, "a", encoding="utf-8") as summary:
            summary.write(output)

    print(output)
    if not rows:
        print("No Kover XML reports found.", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
