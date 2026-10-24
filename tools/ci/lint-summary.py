#!/usr/bin/env python3
"""Prints Android lint's findings in the CI log: a count per rule, then one line per finding.

Reads the SARIF report AGP writes for lintDebug (or, failing that, the XML one):

    Lint: 3 findings (0 errors, 3 warnings)
          2 UnusedResources
          1 ObsoleteSdkInt
    app/src/main/res/values/strings.xml:12: UnusedResources: The resource ... appears to be unused

Usage: lint-summary.py [report] (default app/build/reports/lint-results-debug.sarif). Never fails
the build: the lint task does that for errors.
"""
import json
import os
import sys
import xml.etree.ElementTree as ET
from collections import Counter

DEFAULT = "app/build/reports/lint-results-debug.sarif"


def from_sarif(path):
    with open(path, encoding="utf-8") as f:
        sarif = json.load(f)
    findings = []
    for run in sarif.get("runs", []):
        for result in run.get("results", []):
            rule = result.get("ruleId", "?")
            level = result.get("level", "warning")
            message = (result.get("message") or {}).get("text", "")
            where, line = "", 0
            for location in result.get("locations", [])[:1]:
                physical = location.get("physicalLocation", {})
                where = physical.get("artifactLocation", {}).get("uri", "")
                line = physical.get("region", {}).get("startLine", 0)
            findings.append((where, line, level, rule, message))
    return findings


def from_xml(path):
    findings = []
    for issue in ET.parse(path).getroot().iter("issue"):
        location = issue.find("location")
        where = location.get("file", "") if location is not None else ""
        line = int(location.get("line", "0")) if location is not None else 0
        severity = issue.get("severity", "Warning").lower()
        level = "error" if severity in ("error", "fatal") else "warning"
        findings.append((where, line, level, issue.get("id", "?"), issue.get("message", "")))
    return findings


def relative(path):
    path = path.removeprefix("file://")
    here = os.getcwd() + os.sep
    return path.removeprefix(here) if path.startswith(here) else path


def main():
    path = sys.argv[1] if len(sys.argv) > 1 else DEFAULT
    xml = path.removesuffix(".sarif") + ".xml"
    if os.path.exists(path):
        findings = from_sarif(path)
    elif os.path.exists(xml):
        findings = from_xml(xml)
    else:
        print(f"No lint report at {path} (did lint run?)")
        return
    errors = sum(1 for f in findings if f[2] == "error")
    print(f"Lint: {len(findings)} findings ({errors} errors, {len(findings) - errors} warnings)")
    for rule, count in sorted(Counter(f[3] for f in findings).items(), key=lambda kv: (-kv[1], kv[0])):
        print(f"{count:7d} {rule}")
    for where, line, level, rule, message in sorted(findings, key=lambda f: (f[0], f[1], f[3])):
        message = " ".join(message.split())
        print(f"{relative(where) or '(project)'}:{line}: {rule}: {message}")


if __name__ == "__main__":
    main()
