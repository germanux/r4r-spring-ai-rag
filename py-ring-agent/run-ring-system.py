#!/usr/bin/env python3
"""Deterministic Phase-3 worker guardian.

This supervisor does not make technical product decisions. It repeatedly invokes the
idempotent worker guardian so PC and LP wrappers are present in their authoritative
worktrees. Git synchronization remains the responsibility of sync-agent-branches.sh.
"""
from __future__ import annotations

import argparse
import fcntl
import os
from pathlib import Path
import signal
import subprocess
import sys
import time

DEFAULT_INTERVAL_SECONDS = 15


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--ring", type=Path, default=Path.home() / "Desarrollo" / "r4r-ring-agent.git")
    parser.add_argument("--interval", type=int, default=DEFAULT_INTERVAL_SECONDS)
    parser.add_argument("--once", action="store_true")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    if args.interval < 1:
        raise SystemExit("--interval must be positive")
    ring = args.ring.expanduser().resolve()
    guardian = ring / "scripts" / "ensure-r4r-workers.sh"
    if not guardian.is_file():
        raise SystemExit(f"worker guardian missing: {guardian}")

    runtime = ring / "runtime" / "ring-system"
    runtime.mkdir(parents=True, exist_ok=True)
    lock_handle = (runtime / "supervisor.lock").open("a+")
    try:
        fcntl.flock(lock_handle.fileno(), fcntl.LOCK_EX | fcntl.LOCK_NB)
    except BlockingIOError:
        print("[r4r-system] supervisor already running")
        return 0

    stopping = False

    def stop(_signum: int, _frame: object) -> None:
        nonlocal stopping
        stopping = True

    signal.signal(signal.SIGINT, stop)
    signal.signal(signal.SIGTERM, stop)
    (runtime / "supervisor.pid").write_text(f"{os.getpid()}\n", encoding="utf-8")

    def iteration() -> int:
        completed = subprocess.run(
            [str(guardian), "--once", "--ring", str(ring)],
            cwd=ring,
            text=True,
        )
        return completed.returncode

    try:
        if args.once:
            return iteration()
        print(f"[r4r-system] supervising PC and LP every {args.interval}s")
        while not stopping:
            iteration()
            deadline = time.monotonic() + args.interval
            while not stopping and time.monotonic() < deadline:
                time.sleep(0.25)
        return 0
    finally:
        try:
            (runtime / "supervisor.pid").unlink()
        except FileNotFoundError:
            pass


if __name__ == "__main__":
    raise SystemExit(main())
