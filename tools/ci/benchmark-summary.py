#!/usr/bin/env python3
"""Prints one line per Macrobenchmark metric from the benchmarkData.json files under a directory.

Usage: benchmark-summary.py <dir>
"""
import json
import pathlib
import sys


def fmt(value):
    return f"{value:.1f}" if isinstance(value, (int, float)) else "-"


def main(root):
    files = sorted(pathlib.Path(root).rglob("*benchmarkData.json"))
    if not files:
        print("No benchmark results")
        return
    for path in files:
        try:
            data = json.loads(path.read_text())
        except (OSError, ValueError) as error:
            print(f"{path}: unreadable ({error})")
            continue
        for bench in data.get("benchmarks", []):
            name = f"{bench.get('className', '').rsplit('.', 1)[-1]}.{bench.get('name', '?')}"
            for metric, values in sorted(bench.get("metrics", {}).items()):
                print(
                    f"{name:50} {metric:32} median {fmt(values.get('median'))}  "
                    f"min {fmt(values.get('minimum'))}  max {fmt(values.get('maximum'))}"
                )
            for metric, values in sorted(bench.get("sampledMetrics", {}).items()):
                print(
                    f"{name:50} {metric:32} P50 {fmt(values.get('P50'))}  "
                    f"P90 {fmt(values.get('P90'))}  P99 {fmt(values.get('P99'))}"
                )


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else ".")
