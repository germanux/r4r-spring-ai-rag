#!/usr/bin/env python3
from __future__ import annotations

from datetime import datetime, timezone
import os
from pathlib import Path
import sys
import time

# ---------------------------------------------------------------------------
# EDIT THESE VALUES HERE. The optional PC/LP argument remains only a shortcut.
# ---------------------------------------------------------------------------
DEVELOPMENT_ROOT = Path(
    os.environ.get("R4R_DEVELOPMENT_ROOT", str(Path.home() / "Desarrollo"))
).expanduser()
RING_WORKTREE = Path(
    os.environ.get("R4R_RING_WORKTREE", str(DEVELOPMENT_ROOT / "r4r-ring-agent.git"))
).expanduser()
PC_WORKTREE = Path(
    os.environ.get("R4R_PC_WORKTREE", str(DEVELOPMENT_ROOT / "r4r-pc-worker.git"))
).expanduser()
LP_WORKTREE = Path(
    os.environ.get("R4R_LP_WORKTREE", str(DEVELOPMENT_ROOT / "r4r-lp-worker.git"))
).expanduser()
DESTINATION = "PC"  # PC or LP
RUNNER_SCRIPT = Path("scripts/run-opencode-worker.sh")
RUNNER = ("bash", f"./{RUNNER_SCRIPT.as_posix()}", "--destination")
SESSION_TIMEOUT_SECONDS = None  # The inner harness owns its session TTL.

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE / "src"))

from r4r_ring_agent.operator_control import OperatorCommand, RingCommandFile  # noqa: E402
from r4r_ring_agent.ring_process import run_streamed  # noqa: E402
from r4r_ring_agent.worktrees import WorktreePaths, require_git_worktree  # noqa: E402


def main() -> int:
    destination = (sys.argv[1] if len(sys.argv) > 1 else DESTINATION).upper()
    paths = WorktreePaths(RING_WORKTREE, PC_WORKTREE, LP_WORKTREE)
    ring_repo = require_git_worktree(paths.ring, "RING")
    worker_repo = require_git_worktree(paths.worker(destination), destination)

    runner_path = worker_repo / RUNNER_SCRIPT
    if not runner_path.is_file():
        raise SystemExit(f"worker runner is missing: {runner_path}")

    control = RingCommandFile(ring_repo, destination)
    control.set_state("running", f"{destination} wrapper started in {worker_repo}")

    worker_env = dict(os.environ)
    worker_env.update(
        {
            "R4R_RING_WORKTREE": str(ring_repo),
            "R4R_PC_WORKTREE": str(paths.pc.expanduser().resolve()),
            "R4R_LP_WORKTREE": str(paths.lp.expanduser().resolve()),
        }
    )

    while True:
        run_id = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
        log = ring_repo / "runtime" / "ring-agent" / destination.lower() / run_id / "controller.console.log"
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
            (*RUNNER, destination),
            worker_repo,
            log,
            timeout_seconds=SESSION_TIMEOUT_SECONDS,
            stop_poll=stop_poll,
            env=worker_env,
        )

        if active_command is None:
            control.set_state("stopped", f"Controller exited {result.exit_code}; log={log}")
            return result.exit_code
        if active_command.command == "stop":
            control.complete(active_command, "stopped", f"Stopped cleanly; log={log}")
            return 0
        if active_command.command == "pause":
            control.complete(active_command, "paused", f"Paused cleanly; log={log}")
            while control.current_state() == "paused":
                request = control.poll()
                if request is not None:
                    if request.command == "stop":
                        control.complete(request, "stopped", "Stopped while paused")
                        return 0
                    if request.command in {"continue", "restart"}:
                        control.complete(request, "running", "Starting a fresh controller process")
                        break
                control.heartbeat("paused")
                time.sleep(1)
            continue

        control.complete(
            active_command,
            "running",
            f"Restarting from a fresh controller process; log={log}",
        )


if __name__ == "__main__":
    raise SystemExit(main())
