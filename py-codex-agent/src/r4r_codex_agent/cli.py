from __future__ import annotations

import argparse
import json
import os
from pathlib import Path
import sys

from .contracts import load_task_plan
from .runner import AutomaticRunner


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Run the automatic R4R Codex/OpenCode task sequence")
    parser.add_argument("--repo", type=Path, default=Path.cwd())
    parser.add_argument("--plan", type=Path, default=Path(".opencode/task-plan.json"))
    parser.add_argument("--progress", type=Path, default=Path(".opencode/progress.json"))
    parser.add_argument("--status", action="store_true", help="Show task progress and current gate status")
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    repo = args.repo.resolve()
    plan_path = args.plan if args.plan.is_absolute() else repo / args.plan
    progress_path = args.progress if args.progress.is_absolute() else repo / args.progress
    try:
        runner = AutomaticRunner(repo, load_task_plan(plan_path), progress_path)
        exit_code = runner.status() if args.status else runner.execute()
    except (OSError, ValueError, RuntimeError, json.JSONDecodeError) as exception:
        print(f"r4r-codex-agent: {exception}", file=sys.stderr)
        raise SystemExit(2) from exception
    raise SystemExit(exit_code)


if __name__ == "__main__":
    main()
