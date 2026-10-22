#!/usr/bin/env python3
"""Compares the screenshot matrix against committed goldens.

Usage: compare_screenshots.py <captured dir> <golden dir>

Both sides are reduced to half size before comparing, so antialiasing noise does not count. A shot
fails when more than 0.5% of its pixels differ by more than 40 levels in any channel. Shots without
a golden are reported (and printed as base64 for review) but do not fail the run; update goldens by
committing the reduced images this script writes to <captured dir>/reduced/.
"""
import base64
import io
import sys
from pathlib import Path

from PIL import Image, ImageChops

THRESHOLD = 40
MAX_FRACTION = 0.005


def reduce(path: Path) -> Image.Image:
    image = Image.open(path).convert("RGB")
    return image.resize((image.width // 2, image.height // 2), Image.LANCZOS)


def main() -> int:
    captured, goldens = Path(sys.argv[1]), Path(sys.argv[2])
    if not captured.is_dir():
        print(f"No captures at {captured} (the device tests did not get that far)")
        return 1
    reduced_dir = captured / "reduced"
    reduced_dir.mkdir(exist_ok=True)
    shots = sorted(p for p in captured.glob("*.png"))
    if not shots:
        print("No screenshots captured")
        return 1
    failures = []
    for shot in shots:
        reduced = reduce(shot)
        reduced.save(reduced_dir / shot.name, optimize=True)
        golden_path = goldens / shot.name
        if not golden_path.exists():
            buffer = io.BytesIO()
            reduced.save(buffer, format="PNG", optimize=True)
            print(f"NEW {shot.name} (no golden yet)")
            print(f"GOLDEN-BEGIN {shot.name}")
            print(base64.b64encode(buffer.getvalue()).decode())
            print(f"GOLDEN-END {shot.name}")
            continue
        golden = Image.open(golden_path).convert("RGB")
        if golden.size != reduced.size:
            failures.append(f"{shot.name}: size {reduced.size} != golden {golden.size}")
            continue
        r, g, b = ImageChops.difference(reduced, golden).split()
        peak = ImageChops.lighter(ImageChops.lighter(r, g), b)
        changed = peak.point(lambda v: 255 if v > THRESHOLD else 0).histogram()[255]
        fraction = changed / (reduced.width * reduced.height)
        status = "FAIL" if fraction > MAX_FRACTION else "ok"
        print(f"{status} {shot.name}: {fraction:.4%} of pixels changed")
        if fraction > MAX_FRACTION:
            failures.append(f"{shot.name}: {fraction:.4%} changed")
    for golden_path in sorted(goldens.glob("*.png")):
        if not (captured / golden_path.name).exists():
            failures.append(f"{golden_path.name}: golden has no capture")
    if failures:
        print("Screenshot changes:\n  " + "\n  ".join(failures))
        print("If the change is intended, replace the goldens with the reduced captures.")
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
