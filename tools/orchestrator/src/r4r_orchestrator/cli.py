from __future__ import annotations

import argparse
import json
import os
from pathlib import Path
import sys

from .contracts import load_task
from .runner import CycleRunner, command_from_env


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Run one bounded R4R agentic cycle")
    parser.add_argument("--repo", type=Path, default=Path.cwd())
    parser.add_argument("--task", type=Path, default=Path("agent/shared/CURRENT_TASK.json"))
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    repo = args.repo.resolve()
    task_path = args.task if args.task.is_absolute() else repo / args.task
    task = load_task(task_path)

    opencode_bin = os.environ.get("R4R_OPENCODE_BIN", "opencode")
    agent = os.environ.get("R4R_OPENCODE_AGENT", "r4r-local")
    opencode_command = (
        opencode_bin,
        "--print-logs",
        "--log-level",
        "INFO",
        "run",
        "--dir",
        str(repo),
        "--agent",
        agent,
        "--format",
        "json",
    )

    try:
        codex_command = command_from_env("R4R_CODEX_CMD_JSON")
        exit_code = CycleRunner(repo, task).execute(opencode_command, codex_command)
    except (OSError, ValueError, RuntimeError, json.JSONDecodeError) as exception:
        print(f"r4r-cycle: {exception}", file=sys.stderr)
        raise SystemExit(2) from exception
    raise SystemExit(exit_code)


if __name__ == "__main__":
    main()
