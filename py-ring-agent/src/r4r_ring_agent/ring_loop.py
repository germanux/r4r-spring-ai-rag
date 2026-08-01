from __future__ import annotations

from argparse import ArgumentParser
from datetime import datetime, timezone
import json
import os
from pathlib import Path
import subprocess
import time
from typing import Sequence

from .operator_control import OperatorCommand, RingCommandFile
from .ring_process import run_streamed


# ---------------------------------------------------------------------------
# Editable defaults. CLI options only override these values.
# ---------------------------------------------------------------------------
RING_AGENT = "r4r-ring"
RING_MODEL = "ollama-pc/qwen3-coder-next-80b-t033-128k-8k-pc-pc:latest"
REVIEW_INTERVAL_SECONDS = 4 * 60 * 60
SESSION_TIMEOUT_SECONDS = 90 * 60
RUN_IMMEDIATELY = True
OPENCODE_BIN = os.environ.get("R4R_OPENCODE_BIN", "opencode")


PROMPT = """You are The Ring, the cross-stack commander for R4R.
This is a fresh OpenCode session. Do not resume or rely on another transcript.

Analyze the current repository, the durable Ring files, the two queues, the latest
worker evidence and the last ten commits. Identify the first current defect for PC
and LP. Prefer correction before new implementation. Read Java, Angular, tests,
Python and shell only as needed. Do not perform unbounded searches.

Update only these versioned files:
- .ring-agent/global-summary.md
- .ring-agent/code-pc-review.md
- .ring-agent/code-lp-review.md
- .ring-agent/backend-frontend-handoff.md
- .ring-agent/state.json
- .opencode/current/ring/**

Do not edit product Java or Angular. Do not write Git history. Do not install
packages. Do not run find, recursive grep, git add, commit, reset, checkout, merge,
rebase, push or clean.

The PC and LP directives must each contain one focused next action, exact evidence,
paths to inspect, the exact gate and a strategy that does not repeat the last failed
approach. Detect a newly compilable REST contract and update the frontend handoff.
"""


def _repo_root(explicit: str | None) -> Path:
    if explicit:
        return Path(explicit).expanduser().resolve()
    return Path.cwd().resolve()


def _git(repo: Path, args: Sequence[str]) -> str:
    result = subprocess.run(
        ["git", *args], cwd=repo, text=True, stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT, check=False,
    )
    return result.stdout


def _write_evidence(repo: Path, run_dir: Path) -> None:
    run_dir.mkdir(parents=True, exist_ok=True)
    (run_dir / "last-ten-commits.txt").write_text(
        _git(repo, ["log", "-10", "--date=iso-strict", "--format=commit %H%nDate: %ad%nSubject: %s", "--name-status"]),
        encoding="utf-8",
    )
    (run_dir / "git-status.txt").write_text(
        _git(repo, ["status", "--short", "--branch"]), encoding="utf-8",
    )
    (run_dir / "git-diff-stat.txt").write_text(
        _git(repo, ["diff", "--stat"]), encoding="utf-8",
    )


def _command(repo: Path) -> tuple[str, ...]:
    return (
        OPENCODE_BIN,
        "run",
        "--dir",
        str(repo),
        "--agent",
        RING_AGENT,
        "--model",
        RING_MODEL,
        "--format",
        "json",
        "--auto",
        PROMPT,
    )


def run_ring_loop(repo: Path, *, once: bool = False) -> int:
    control = RingCommandFile(repo, "RING")
    control.set_state("running", "The Ring loop started")
    next_run = time.monotonic() if RUN_IMMEDIATELY else time.monotonic() + REVIEW_INTERVAL_SECONDS

    while True:
        request = control.poll()
        if request is not None:
            if request.command == "stop":
                control.complete(request, "stopped", request.reason or "Stopped by operator")
                return 0
            if request.command == "pause":
                control.complete(request, "paused", request.reason or "Paused by operator")
            elif request.command == "restart":
                control.complete(request, "running", request.reason or "Fresh Ring session requested")
                next_run = time.monotonic()
            elif request.command == "continue":
                control.complete(request, "running", request.reason or "Ring loop continued")
                next_run = time.monotonic()

        if control.current_state() == "paused":
            control.heartbeat("paused")
            time.sleep(1)
            continue
        if time.monotonic() < next_run:
            control.heartbeat("running")
            time.sleep(1)
            continue

        run_id = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
        run_dir = repo / "runtime" / "ring" / run_id
        _write_evidence(repo, run_dir)
        control.set_state("running", f"The Ring session {run_id} started")

        active_command: OperatorCommand | None = None

        def stop_poll() -> str:
            nonlocal active_command
            control.heartbeat("running")
            request = control.poll()
            if request is None or request.command == "continue":
                if request is not None:
                    control.complete(request, "running", "Already running")
                return ""
            active_command = request
            return request.command

        result = run_streamed(
            _command(repo),
            repo,
            run_dir / "opencode.console.log",
            timeout_seconds=SESSION_TIMEOUT_SECONDS,
            stop_poll=stop_poll,
        )
        (run_dir / "session-result.json").write_text(
            json.dumps(
                {
                    "run_id": run_id,
                    "exit_code": result.exit_code,
                    "duration_seconds": result.duration_seconds,
                    "stop_reason": result.stop_reason,
                    "agent": RING_AGENT,
                    "model": RING_MODEL,
                },
                indent=2,
            )
            + "\n",
            encoding="utf-8",
        )

        if active_command is not None:
            if active_command.command == "stop":
                control.complete(active_command, "stopped", "Ring session stopped and log finalized")
                return 0
            if active_command.command == "pause":
                control.complete(active_command, "paused", "Ring session paused and log finalized")
            else:
                control.complete(active_command, "running", "Ring session restarted with fresh context")
                next_run = time.monotonic()
                continue
        elif result.stop_reason == "timeout":
            control.set_state("running", "Ring session reached 90 minutes; next session will be fresh")
        else:
            control.set_state("running", f"Ring session finished with exit {result.exit_code}")

        if once:
            return result.exit_code
        next_run = time.monotonic() + REVIEW_INTERVAL_SECONDS
