#!/usr/bin/env python3
"""Prints each Macrobenchmark metric of two runs side by side: base, head and head / base.

Usage: benchmark-compare.py <base dir> <head dir>

Meant for runs on the same emulator in the same job (CI benchmarks main, then the PR): numbers
from different runners differ by far more than a change does, so only these ratios compare.
"""
import json
import pathlib
import sys


def metrics(root):
    out = {}
    for path in sorted(pathlib.Path(root).rglob("*benchmarkData.json")):
        try:
            data = json.loads(path.read_text())
        except (OSError, ValueError):
            continue
        for bench in data.get("benchmarks", []):
            name = f"{bench.get('className', '').rsplit('.', 1)[-1]}.{bench.get('name', '?')}"
            for metric, values in bench.get("metrics", {}).items():
                out[f"{name} {metric} median"] = values.get("median")
            for metric, values in bench.get("sampledMetrics", {}).items():
                for p in ("P50", "P90"):
                    out[f"{name} {metric} {p}"] = values.get(p)
    return out


def main(base_root, head_root):
    base, head = metrics(base_root), metrics(head_root)
    if not base or not head:
        print(f"Nothing to compare ({len(base)} base, {len(head)} head metrics)")
        return
    print(f"{'':72} {'base':>9} {'head':>9} {'head/base':>9}")
    for key in sorted(set(base) & set(head)):
        b, h = base[key], head[key]
        if isinstance(b, (int, float)) and isinstance(h, (int, float)):
            ratio = f"x{h / b:.2f}" if b else "-"
            print(f"{key:72} {b:9.1f} {h:9.1f} {ratio:>9}")


if __name__ == "__main__":
    if len(sys.argv) != 3:
        sys.exit(__doc__)
    main(sys.argv[1], sys.argv[2])
