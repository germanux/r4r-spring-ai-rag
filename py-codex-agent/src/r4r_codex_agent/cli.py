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
    os.environ.setdefault("R4R_PLAN_PATH", str(plan_path.relative_to(repo) if plan_path.is_relative_to(repo) else plan_path))
    os.environ.setdefault("R4R_PROGRESS_PATH", str(progress_path.relative_to(repo) if progress_path.is_relative_to(repo) else progress_path))
    runner: AutomaticRunner | None = None
    try:
        runner = AutomaticRunner(repo, load_task_plan(plan_path), progress_path)
        exit_code = runner.status() if args.status else runner.execute()
    except (OSError, ValueError, RuntimeError, json.JSONDecodeError) as exception:
        print(f"r4r-codex-agent: {exception}", file=sys.stderr)
        exit_code = runner.record_unhandled_failure(exception) if runner is not None else 2
    raise SystemExit(exit_code)


if __name__ == "__main__":
    main()
