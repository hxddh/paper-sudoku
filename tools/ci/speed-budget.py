#!/usr/bin/env python3
"""Compares Macrobenchmark results with tools/ci/speed-budget.json; exits 1 when one is over.

Usage: speed-budget.py <benchmark outputs dir> [--against <main's outputs dir>] [budget.json]

A metric fails above `tolerance` x its budget. A budgeted metric missing from the results also
fails, so a renamed benchmark or metric cannot drop out of the budget unnoticed.

With --against (a PR, with main benchmarked on the same emulator in the same job), each budgeted
metric is held to main's own value instead, within `relative_tolerance`: runners differ from one
another by far more than a change does, so a fixed number mostly measures the runner.
"""
import json
import pathlib
import sys


def results(root):
    found = {}
    for path in sorted(pathlib.Path(root).rglob("*benchmarkData.json")):
        data = json.loads(path.read_text())
        for bench in data.get("benchmarks", []):
            name = f"{bench.get('className', '').rsplit('.', 1)[-1]}.{bench.get('name', '?')}"
            stats = {}
            for metric, values in bench.get("metrics", {}).items():
                stats[metric] = values
            for metric, values in bench.get("sampledMetrics", {}).items():
                stats[metric] = values
            found[name] = stats
    return found


def main(root, budget_path, against=None):
    budget = json.loads(pathlib.Path(budget_path).read_text())
    base = results(against) if against else {}
    tolerance = float(budget.get("relative_tolerance" if base else "tolerance", 1.25))
    measured = results(root)
    failures = 0
    for bench, metrics in budget["metrics"].items():
        for metric, stats in metrics.items():
            for stat, limit in stats.items():
                value = measured.get(bench, {}).get(metric, {}).get(stat)
                label = f"{bench} {metric} {stat}"
                if base:
                    limit = base.get(bench, {}).get(metric, {}).get(stat)
                    if limit is None:
                        print(f"::error::{label}: not measured on main")
                        failures += 1
                        continue
                if value is None:
                    print(f"::error::{label}: not measured (budget {limit})")
                    failures += 1
                    continue
                ratio = value / limit
                verdict = "OVER" if ratio > tolerance else "ok"
                against_what = "main" if base else "budget"
                print(f"{label:70} {value:9.1f}  {against_what} {limit:7.1f}  x{ratio:4.2f}  {verdict}")
                if ratio > tolerance:
                    print(f"::error::{label} is {value:.1f}, above {tolerance} x {against_what}'s {limit:.1f}")
                    failures += 1
    if failures:
        print(f"{failures} over budget (tools/ci/speed-budget.json)")
        return 1
    print(f"Every budgeted metric within {tolerance} x {'main on this emulator' if base else 'its budget'}.")
    return 0


if __name__ == "__main__":
    here = pathlib.Path(__file__).resolve().parent
    args = sys.argv[1:]
    against = None
    if "--against" in args:
        i = args.index("--against")
        against = args[i + 1]
        del args[i:i + 2]
    sys.exit(main(args[0], args[1] if len(args) > 1 else here / "speed-budget.json", against))
