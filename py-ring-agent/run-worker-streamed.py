#!/usr/bin/env python3
from __future__ import annotations

from datetime import datetime, timezone
import os
from pathlib import Path
import sys
import time

HERE = Path(__file__).resolve().parent
REPO = HERE.parent
sys.path.insert(0, str(HERE / "src"))

from r4r_ring_agent.operator_control import OperatorCommand, RingCommandFile  # noqa: E402
from r4r_ring_agent.ring_process import run_streamed  # noqa: E402


# ---------------------------------------------------------------------------
# Editable defaults. The first optional argument may override DESTINATION.
# ---------------------------------------------------------------------------
DESTINATION = "PC"  # PC or LP
RUNNER = ("./scripts/run-codex-agent.sh", "--destination")
SESSION_TIMEOUT_SECONDS = None  # The inner harness owns its session TTL.


def main() -> int:
    destination = (sys.argv[1] if len(sys.argv) > 1 else DESTINATION).upper()
    if destination not in {"PC", "LP"}:
        raise SystemExit("destination must be PC or LP")

    control = RingCommandFile(REPO, destination)
    control.set_state("running", f"{destination} streamed wrapper started")

    while True:
        run_id = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
        log = REPO / "runtime" / "outer-console" / destination / run_id / "controller.console.log"
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
            REPO,
            log,
            timeout_seconds=SESSION_TIMEOUT_SECONDS,
            stop_poll=stop_poll,
            env=os.environ,
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
