#!/usr/bin/env python3
"""Lists the rules of a generated Baseline Profile that the committed one does not cover.

Usage: profile-coverage.py <generated-baseline-prof.txt> <app/src/main/baseline-prof.txt>

The release ships the committed (hand-written) profile; CI records the real journey on its
emulator (BaselineProfileGenerator) and prints what that journey ran outside it, per package,
so the committed file can be extended from it. Libraries' own profiles (Compose and the other
AndroidX libraries) are merged at build time, so androidx rules are counted, not listed.
Informational: always exits 0.
"""
import collections
import re
import sys


def rules(path):
    out = []
    for line in open(path, encoding="utf-8"):
        line = line.strip()
        if line and not line.startswith("#"):
            out.append(line)
    return out


def pattern(rule):
    # HSPL flags, then the class (and method) descriptor with ** and * wildcards.
    body = re.sub(r"^[HSP]+", "", rule)
    out = ""
    i = 0
    while i < len(body):
        if body.startswith("**", i):
            out += ".*"
            i += 2
        elif body[i] == "*":
            out += "[^/;]*"
            i += 1
        else:
            out += re.escape(body[i])
            i += 1
    return re.compile(out + "$")


def main(generated, committed):
    covers = [pattern(r) for r in rules(committed)]
    missing = collections.Counter()
    library = 0
    for rule in rules(generated):
        body = re.sub(r"^[HSP]+", "", rule)
        if any(p.match(body) for p in covers):
            continue
        if body.startswith("Landroidx/"):
            library += 1
            continue
        package = "/".join(body.split(";")[0].lstrip("L").split("/")[:-1][:3])
        missing[package] += 1
    print(f"Generated rules outside the committed profile: {sum(missing.values())} (plus {library} androidx)")
    for package, count in missing.most_common(30):
        print(f"{count:6}  {package}")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1], sys.argv[2]))
