#!/usr/bin/env python3
"""Compares the Macrobenchmark Perfetto traces of two runs, per benchmark, by median over iterations.

Usage: trace-compare.py <base dir> <head dir>

For each benchmark (traces named <Class>_<test>_iter<NNN>_...perfetto-trace) it prints, base
against head, where the app's time went:
- main thread: total time in each slice name (all depths; digits in names folded to N);
- main thread: time in each thread state (Running, Runnable = waiting for a CPU, Sleeping, ...);
- app threads: CPU time per thread name;
- other processes: CPU time while the app runs (what competes with it on the device).
Rows are sorted by how much head differs from base. Needs `pip install perfetto` (which fetches
trace_processor on first use). Either directory may hold no traces: its column is then empty.
"""
import pathlib
import re
import statistics
import sys
from collections import defaultdict

APP = "com.titpea.papersudoku"
ITER = re.compile(r"^(.*?)_iter\d+")
DIGITS = re.compile(r"\d+")
TOP = 25


def traces(root):
    found = defaultdict(dict)
    for path in sorted(pathlib.Path(root).rglob("*.perfetto-trace")):
        match = ITER.match(path.name)
        if match:
            # The same trace can sit in two output folders: keep one per file name.
            found[match.group(1)].setdefault(path.name, path)
    return {bench: list(files.values()) for bench, files in found.items()}


def fold(name):
    return DIGITS.sub("N", name or "?")


def measure(tp):
    """Section -> {row: milliseconds} for one trace."""
    out = defaultdict(lambda: defaultdict(float))
    procs = list(tp.query(
        f"select upid, pid, start_ts from process where name = '{APP}' order by start_ts desc"
    ))
    if not procs:
        return out
    upid, pid = procs[0].upid, procs[0].pid
    main = list(tp.query(f"select utid from thread where upid = {upid} and tid = {pid}"))
    if not main:
        return out
    utid = main[0].utid
    for row in tp.query(f"""
        select s.name as name, s.dur as dur from slice s
        join thread_track t on s.track_id = t.id where t.utid = {utid}"""):
        out["main thread slices"][fold(row.name)] += row.dur / 1e6
    for row in tp.query(f"""
        select state, io_wait, sum(dur) as dur from thread_state where utid = {utid}
        group by state, io_wait"""):
        label = row.state + (" (io)" if row.io_wait == 1 else "")
        out["main thread states"][label] += row.dur / 1e6
    # What the main thread waits on while it opens the app's code (bindApplication).
    for row in tp.query(f"""
        select ts.state as state, ts.io_wait as io_wait, ts.blocked_function as fn,
        sum(min(ts.ts + ts.dur, s.ts + s.dur) - max(ts.ts, s.ts)) as dur
        from slice s join thread_track t on s.track_id = t.id
        join thread_state ts on ts.utid = t.utid and ts.ts < s.ts + s.dur and ts.ts + ts.dur > s.ts
        where t.utid = {utid} and s.name like 'OpenDexFilesFromOat%'
        group by 1, 2, 3"""):
        label = row.state + (" (io)" if row.io_wait == 1 else "") + (f" {row.fn}" if row.fn else "")
        out["main thread states in OpenDexFilesFromOat"][label] += row.dur / 1e6
    for row in tp.query(f"""
        select t.name as name, sum(s.dur) as dur from sched s join thread t using (utid)
        where t.upid = {upid} group by t.name"""):
        out["app threads CPU"][fold(row.name)] += row.dur / 1e6
    window = list(tp.query(f"""
        select min(s.ts) as start, max(s.ts + s.dur) as end from sched s join thread t using (utid)
        where t.upid = {upid}"""))
    if window and window[0].start is not None:
        start, end = window[0].start, window[0].end
        for row in tp.query(f"""
            select coalesce(p.name, t.name) as name, sum(s.dur) as dur from sched s
            join thread t using (utid) left join process p using (upid)
            where s.ts >= {start} and s.ts < {end} and (t.upid is null or t.upid != {upid})
            and t.tid != 0 group by 1"""):
            out["other processes CPU"][fold(row.name)] += row.dur / 1e6
    return out


def medians(files):
    from perfetto.trace_processor import TraceProcessor

    samples = defaultdict(lambda: defaultdict(list))
    for path in files:
        tp = TraceProcessor(trace=str(path))
        try:
            for section, rows in measure(tp).items():
                for name, ms in rows.items():
                    samples[section][name].append(ms)
        finally:
            tp.close()
    n = len(files)
    # A row missing from an iteration counts as 0 there.
    return {
        section: {name: statistics.median(values + [0.0] * (n - len(values))) for name, values in rows.items()}
        for section, rows in samples.items()
    }


def main(base_root, head_root):
    base, head = traces(base_root), traces(head_root)
    if not base and not head:
        print("No Perfetto traces found")
        return
    for bench in sorted(set(base) | set(head)):
        b = medians(base.get(bench, []))
        h = medians(head.get(bench, []))
        print(f"\n=== {bench}: {len(base.get(bench, []))} base / {len(head.get(bench, []))} head traces (median ms) ===")
        for section in ["main thread states", "main thread states in OpenDexFilesFromOat", "main thread slices", "app threads CPU", "other processes CPU"]:
            rows = set(b.get(section, {})) | set(h.get(section, {}))
            diff = sorted(
                rows,
                key=lambda r: -abs(h.get(section, {}).get(r, 0) - b.get(section, {}).get(r, 0)),
            )[:TOP]
            print(f"--- {section}")
            print(f"{'':60} {'base':>9} {'head':>9} {'delta':>9}")
            for r in diff:
                bv, hv = b.get(section, {}).get(r, 0.0), h.get(section, {}).get(r, 0.0)
                print(f"{r[:60]:60} {bv:9.1f} {hv:9.1f} {hv - bv:+9.1f}")


if __name__ == "__main__":
    if len(sys.argv) != 3:
        sys.exit(__doc__)
    main(sys.argv[1], sys.argv[2])
