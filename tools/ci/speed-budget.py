#!/usr/bin/env python3
"""Compares Macrobenchmark results with tools/ci/speed-budget.json; exits 1 when one is over.

Usage: speed-budget.py <benchmark outputs dir> [budget.json]

A metric fails above `tolerance` x its budget. A budgeted metric missing from the results also
fails, so a renamed benchmark or metric cannot drop out of the budget unnoticed.
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


def main(root, budget_path):
    budget = json.loads(pathlib.Path(budget_path).read_text())
    tolerance = float(budget.get("tolerance", 1.25))
    measured = results(root)
    failures = 0
    for bench, metrics in budget["metrics"].items():
        for metric, stats in metrics.items():
            for stat, limit in stats.items():
                value = measured.get(bench, {}).get(metric, {}).get(stat)
                label = f"{bench} {metric} {stat}"
                if value is None:
                    print(f"::error::{label}: not measured (budget {limit})")
                    failures += 1
                    continue
                ratio = value / limit
                verdict = "OVER" if ratio > tolerance else "ok"
                print(f"{label:70} {value:9.1f}  budget {limit:7.1f}  x{ratio:4.2f}  {verdict}")
                if ratio > tolerance:
                    print(f"::error::{label} is {value:.1f}, above {tolerance} x its budget of {limit}")
                    failures += 1
    if failures:
        print(f"{failures} over budget (tools/ci/speed-budget.json)")
        return 1
    print(f"Every budgeted metric within {tolerance} x its budget.")
    return 0


if __name__ == "__main__":
    here = pathlib.Path(__file__).resolve().parent
    sys.exit(main(sys.argv[1], sys.argv[2] if len(sys.argv) > 2 else here / "speed-budget.json"))
