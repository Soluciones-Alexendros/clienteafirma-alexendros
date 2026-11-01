#!/usr/bin/env python3
"""Fail if SARIF contains new Semgrep findings at error/high severity.

Used by CI (#89). Full Semgrep still uploads once; this gate only fails the
build on *new* high/error results vs a baseline SARIF (or empty baseline).
"""
from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path


def level_of(result: dict) -> str:
    lvl = (result.get("level") or "").lower()
    if lvl:
        return lvl
    props = result.get("properties") or {}
    sev = (props.get("security-severity") or props.get("priority") or "").lower()
    return sev


def is_blocking(result: dict) -> bool:
    lvl = level_of(result)
    return lvl in {"error", "high", "critical"}


def key(result: dict) -> tuple:
    locs = result.get("locations") or []
    path = line = None
    if locs:
        phys = (locs[0].get("physicalLocation") or {})
        art = phys.get("artifactLocation") or {}
        reg = phys.get("region") or {}
        path = art.get("uri")
        line = reg.get("startLine")
    rule = result.get("ruleId")
    return (rule, path, line)


def load_results(path: Path) -> list:
    if not path.exists():
        return []
    data = json.loads(path.read_text(encoding="utf-8"))
    out = []
    for run in data.get("runs") or []:
        out.extend(run.get("results") or [])
    return out


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--sarif", required=True, type=Path, help="Current SARIF")
    ap.add_argument(
        "--baseline",
        type=Path,
        default=None,
        help="Baseline SARIF (findings already known). Empty/missing = no baseline.",
    )
    args = ap.parse_args()

    current = load_results(args.sarif)
    baseline_keys = set()
    if args.baseline:
        baseline_keys = {key(r) for r in load_results(args.baseline)}

    blockers = []
    for r in current:
        if not is_blocking(r):
            continue
        k = key(r)
        if k in baseline_keys:
            continue
        blockers.append(k)

    if blockers:
        print("New high/error Semgrep findings (blocking):", file=sys.stderr)
        for b in blockers:
            print(f"  {b[0]} @ {b[1]}:{b[2]}", file=sys.stderr)
        return 1
    print("semgrep-new-high-gate: OK (no new high/error findings)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
