#!/usr/bin/env python3
"""Deterministic Phase-3 supervisor for workers and The-Ring cognitive loop.

The guardian dispatches PC and LP only while each has a fresh Ring assignment. A
separately locked Ring process reads both workers' evidence and publishes assignments.
Git synchronization remains the responsibility of
sync-agent-branches.sh.
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

DEFAULT_INTERVAL_SECONDS = 763


def _invalidate_startup_assignments(ring: Path) -> tuple[Path, ...]:
    removed: list[Path] = []
    for worker in ("PC", "LP"):
        path = ring / "runtime" / "control" / worker / "assignment.json"
        if path.exists():
            path.unlink()
            removed.append(path)
    return tuple(removed)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--ring", type=Path, default=Path.home() / "Desarrollo" / "r4r-ring-agent.git")
    parser.add_argument("--interval", type=int, default=DEFAULT_INTERVAL_SECONDS)
    parser.add_argument("--guardian", type=Path, default=None)
    parser.add_argument("--ring-agent", type=Path, default=None)
    parser.add_argument("--no-ring-agent", action="store_true")
    parser.add_argument("--once", action="store_true")
    return parser.parse_args()


def _read_live_pid(path: Path) -> int | None:
    try:
        value = int(path.read_text(encoding="utf-8").strip())
        if value <= 0:
            return None
        os.kill(value, 0)
        return value
    except (OSError, ValueError):
        return None


def _proc_state_and_group(pid: int) -> tuple[str, int] | None:
    try:
        stat = (Path("/proc") / str(pid) / "stat").read_text(encoding="utf-8")
        right = stat.rfind(")")
        fields = stat[right + 2 :].split()
        return fields[0], int(fields[2])
    except (OSError, ValueError, IndexError):
        return None


def _is_alive(pid: int) -> bool:
    state_group = _proc_state_and_group(pid)
    if state_group is not None:
        state, _group = state_group
        return state != "Z"
    try:
        os.kill(pid, 0)
    except ProcessLookupError:
        return False
    except PermissionError:
        return True
    return True


def _reap_child(pid: int) -> bool:
    try:
        reaped, _status = os.waitpid(pid, os.WNOHANG)
    except ChildProcessError:
        return False
    return reaped == pid


def _descendants(root_pid: int) -> set[int]:
    children_by_parent: dict[int, list[int]] = {}
    proc = Path("/proc")
    if not proc.exists():
        return set()
    for entry in proc.iterdir():
        if not entry.name.isdigit():
            continue
        try:
            stat = (entry / "stat").read_text(encoding="utf-8")
            right = stat.rfind(")")
            fields = stat[right + 2 :].split()
            pid = int(entry.name)
            ppid = int(fields[1])
        except (OSError, ValueError, IndexError):
            continue
        children_by_parent.setdefault(ppid, []).append(pid)

    found: set[int] = set()
    stack = list(children_by_parent.get(root_pid, ()))
    while stack:
        candidate = stack.pop()
        if candidate in found:
            continue
        found.add(candidate)
        stack.extend(children_by_parent.get(candidate, ()))
    return found


def _process_groups(root_pid: int) -> set[int]:
    current_group = os.getpgrp()
    groups: set[int] = set()
    for candidate in _descendants(root_pid) | {root_pid}:
        try:
            pgid = os.getpgid(candidate)
        except ProcessLookupError:
            continue
        if pgid > 1 and pgid != current_group:
            groups.add(pgid)
    return groups


def _group_alive(pgid: int) -> bool:
    proc = Path("/proc")
    if proc.exists():
        for entry in proc.iterdir():
            if not entry.name.isdigit():
                continue
            state_group = _proc_state_and_group(int(entry.name))
            if state_group is None:
                continue
            state, candidate_group = state_group
            if candidate_group == pgid and state != "Z":
                return True
        return False
    try:
        os.killpg(pgid, 0)
    except ProcessLookupError:
        return False
    except PermissionError:
        return True
    return True


def _signal_groups(groups: set[int], sig: signal.Signals) -> None:
    current_group = os.getpgrp()
    for pgid in sorted(groups, reverse=True):
        if pgid <= 1 or pgid == current_group:
            continue
        try:
            os.killpg(pgid, sig)
        except ProcessLookupError:
            pass


def _terminate_managed_process(pid: int, timeout_seconds: float = 20.0) -> None:
    """Terminate the Ring launcher plus detached OpenCode process groups."""
    groups = _process_groups(pid)
    _reap_child(pid)
    if not _is_alive(pid) and not any(_group_alive(group) for group in groups):
        return

    _signal_groups(groups, signal.SIGTERM)
    if _is_alive(pid):
        try:
            os.kill(pid, signal.SIGTERM)
        except ProcessLookupError:
            pass

    deadline = time.monotonic() + timeout_seconds
    while time.monotonic() < deadline:
        _reap_child(pid)
        if _is_alive(pid):
            groups.update(_process_groups(pid))
        groups = {group for group in groups if _group_alive(group)}
        if not _is_alive(pid) and not groups:
            return
        time.sleep(0.25)

    if _is_alive(pid):
        groups.update(_process_groups(pid))
    _signal_groups(groups, signal.SIGKILL)
    if _is_alive(pid):
        try:
            os.kill(pid, signal.SIGKILL)
        except ProcessLookupError:
            pass

    deadline = time.monotonic() + 5
    while time.monotonic() < deadline:
        _reap_child(pid)
        groups = {group for group in groups if _group_alive(group)}
        if not _is_alive(pid) and not groups:
            return
        _signal_groups(groups, signal.SIGKILL)
        time.sleep(0.1)


def main() -> int:
    args = parse_args()
    if args.interval < 1:
        raise SystemExit("--interval must be positive")
    ring = args.ring.expanduser().resolve()
    guardian = (
        args.guardian.expanduser().resolve()
        if args.guardian
        else ring / "scripts" / "ensure-r4r-workers.sh"
    )
    ring_agent = (
        args.ring_agent.expanduser().resolve()
        if args.ring_agent
        else Path(__file__).resolve().with_name("run-ring-agent.py")
    )
    if not guardian.is_file():
        raise SystemExit(f"worker guardian missing: {guardian}")
    if not args.no_ring_agent and not ring_agent.is_file():
        raise SystemExit(f"The-Ring launcher missing: {ring_agent}")

    runtime = ring / "runtime" / "ring-system"
    runtime.mkdir(parents=True, exist_ok=True)
    lock_handle = (runtime / "supervisor.lock").open("a+")
    try:
        fcntl.flock(lock_handle.fileno(), fcntl.LOCK_EX | fcntl.LOCK_NB)
    except BlockingIOError:
        print("[r4r-system] supervisor already running")
        return 0

    stopping = False
    ring_agent_pid_file = runtime / "ring-agent.pid"
    ring_agent_log = runtime / "ring-agent.console.log"

    def stop(_signum: int, _frame: object) -> None:
        nonlocal stopping
        stopping = True

    signal.signal(signal.SIGINT, stop)
    signal.signal(signal.SIGTERM, stop)
    (runtime / "supervisor.pid").write_text(f"{os.getpid()}\n", encoding="utf-8")

    def runtime_env() -> dict[str, str]:
        env = dict(os.environ)
        env["R4R_RING_WORKTREE"] = str(ring)
        env.setdefault(
            "R4R_PC_WORKTREE",
            str(Path.home() / "Desarrollo" / "r4r-pc-worker.git"),
        )
        env.setdefault(
            "R4R_LP_WORKTREE",
            str(Path.home() / "Desarrollo" / "r4r-lp-worker.git"),
        )
        return env

    def guardian_iteration() -> int:
        completed = subprocess.run(
            [str(guardian), "--once", "--ring", str(ring)],
            cwd=ring,
            env=runtime_env(),
            text=True,
        )
        return completed.returncode

    def ensure_ring_agent() -> int | None:
        if args.no_ring_agent:
            return None
        live = _read_live_pid(ring_agent_pid_file)
        if live is not None:
            return live
        ring_agent_pid_file.unlink(missing_ok=True)
        env = runtime_env()
        env["R4R_RING_RUN_ONCE"] = "false"
        with ring_agent_log.open("a", encoding="utf-8") as output:
            output.write(
                f"\n[r4r-system] starting The-Ring at {time.strftime('%Y-%m-%dT%H:%M:%S%z')} "
                f"launcher={ring_agent}\n"
            )
            output.flush()
            process = subprocess.Popen(
                [sys.executable, str(ring_agent)],
                cwd=ring,
                env=env,
                stdin=subprocess.DEVNULL,
                stdout=output,
                stderr=subprocess.STDOUT,
                start_new_session=True,
                text=True,
            )
        ring_agent_pid_file.write_text(f"{process.pid}\n", encoding="utf-8")
        print(
            f"[r4r-system] The-Ring cognitive loop started pid={process.pid} "
            f"log={ring_agent_log}"
        )
        return process.pid

    try:
        if args.once:
            return guardian_iteration()
        # A supervisor restart is a new orchestration epoch. Remove only the
        # ephemeral worker assignments so the guardian cannot replay an
        # unexpired directive before Luna has reviewed current evidence.
        _invalidate_startup_assignments(ring)
        print(
            f"[r4r-system] checking Ring assignments for PC and LP every "
            f"{args.interval}s"
        )
        ensure_ring_agent()
        while not stopping:
            guardian_iteration()
            ensure_ring_agent()
            deadline = time.monotonic() + args.interval
            while not stopping and time.monotonic() < deadline:
                time.sleep(0.25)
        return 0
    finally:
        ring_pid = _read_live_pid(ring_agent_pid_file)
        if ring_pid is not None:
            _terminate_managed_process(ring_pid)
        ring_agent_pid_file.unlink(missing_ok=True)
        try:
            (runtime / "supervisor.pid").unlink()
        except FileNotFoundError:
            pass
        fcntl.flock(lock_handle.fileno(), fcntl.LOCK_UN)
        lock_handle.close()


if __name__ == "__main__":
    raise SystemExit(main())
