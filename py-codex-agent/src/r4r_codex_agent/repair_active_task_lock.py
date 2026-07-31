from __future__ import annotations

import argparse
from datetime import datetime, timezone
import json
import os
from pathlib import Path
import re
import sys
from typing import Any

from .contracts import load_progress, load_task_plan, task_progress
from .runner import (
    git_changed_paths,
    git_head,
    git_is_ancestor,
    git_paths_between,
    is_controller_runtime_path,
    is_lock_auto_advance_path,
    path_is_allowed,
)


REPAIR_REFUSED_EXIT = 66
_RUN_DIRECTORY_PATTERN = re.compile(r"^[0-9]{8}T[0-9]{6}Z$")
_EXPECTED_ERROR_PREFIX = (
    "Active-task lock cannot advance across non-maintenance commits:"
)


class RepairRefused(RuntimeError):
    """Raised when advancing an active-task lock is not demonstrably safe."""


def _read_json_object(path: Path, label: str) -> dict[str, Any]:
    try:
        raw = json.loads(path.read_text(encoding="utf-8"))
    except FileNotFoundError as exception:
        raise RepairRefused(f"{label} does not exist: {path}") from exception
    except json.JSONDecodeError as exception:
        raise RepairRefused(f"{label} is not valid JSON: {path}: {exception}") from exception
    if not isinstance(raw, dict):
        raise RepairRefused(f"{label} must be a JSON object: {path}")
    return raw


def latest_controller_state(repo: Path) -> Path:
    runs_dir = repo / "runtime" / "runs"
    if not runs_dir.is_dir():
        raise RepairRefused(f"controller runs directory does not exist: {runs_dir}")
    for child in sorted(runs_dir.iterdir(), reverse=True):
        if child.is_dir() and _RUN_DIRECTORY_PATTERN.fullmatch(child.name):
            state = child / "state.json"
            if state.is_file():
                return state
    raise RepairRefused(f"no controller state.json found under: {runs_dir}")


def _first_unaccepted_task(plan: Any, progress: dict[str, Any]) -> Any:
    for task in plan.tasks:
        if task_progress(progress, task.id)["status"] != "ACCEPTED":
            return task
    raise RepairRefused("all tasks are ACCEPTED; active lock ownership is ambiguous")


def _write_json_atomically(path: Path, payload: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_name(
        f".{path.name}.{os.getpid()}.{datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%S%fZ')}.tmp"
    )
    data = (json.dumps(payload, indent=2, ensure_ascii=False) + "\n").encode("utf-8")
    descriptor = os.open(temporary, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    try:
        with os.fdopen(descriptor, "wb", closefd=True) as stream:
            stream.write(data)
            stream.flush()
            os.fsync(stream.fileno())
        os.replace(temporary, path)
        directory_descriptor = os.open(path.parent, os.O_RDONLY)
        try:
            os.fsync(directory_descriptor)
        finally:
            os.close(directory_descriptor)
    finally:
        temporary.unlink(missing_ok=True)


def repair_active_task_lock(
    repo: Path,
    plan_path: Path,
    progress_path: Path,
    state_path: Path | None = None,
) -> tuple[str, str, tuple[str, ...], Path]:
    repo = repo.resolve()
    plan_path = plan_path if plan_path.is_absolute() else repo / plan_path
    progress_path = progress_path if progress_path.is_absolute() else repo / progress_path
    state_path = state_path or latest_controller_state(repo)
    state_path = state_path if state_path.is_absolute() else repo / state_path

    state = _read_json_object(state_path, "controller state")
    if state.get("status") != "CONTROLLER_EXCEPTION" or state.get("exit_code") != 2:
        raise RepairRefused("latest controller state is not CONTROLLER_EXCEPTION with exit code 2")
    error = state.get("error")
    if not isinstance(error, str) or not error.startswith(_EXPECTED_ERROR_PREFIX):
        raise RepairRefused("controller exception is not the supported active-lock advance failure")

    plan = load_task_plan(plan_path)
    progress = load_progress(progress_path, (task.id for task in plan.tasks))
    task = _first_unaccepted_task(plan, progress)

    lock_path = repo / "runtime" / "locks" / "active-task.json"
    lock = _read_json_object(lock_path, "active-task lock")
    if lock.get("schema_version") != 1:
        raise RepairRefused("active-task lock has an unsupported schema_version")
    if lock.get("task_id") != task.id:
        raise RepairRefused(
            f"active lock belongs to {lock.get('task_id')!r}, not first unaccepted task {task.id!r}"
        )
    if tuple(lock.get("allowed_paths") or ()) != task.allowed_paths:
        raise RepairRefused("active lock allowed_paths no longer match the task plan")

    base_commit = str(lock.get("base_commit") or "")
    current_head = git_head(repo)
    if not base_commit or not current_head:
        raise RepairRefused("active lock or current Git HEAD is unavailable")
    if state.get("git_head") != current_head:
        raise RepairRefused("controller exception was recorded for a different Git HEAD")
    if not git_is_ancestor(repo, base_commit, current_head):
        raise RepairRefused("active lock base commit is not an ancestor of current HEAD")

    committed_paths = git_paths_between(repo, base_commit, current_head)
    unsafe_committed = tuple(
        path for path in committed_paths if not is_lock_auto_advance_path(path)
    )
    if unsafe_committed:
        raise RepairRefused(
            "commits since the lock contain real non-maintenance paths: "
            f"{list(unsafe_committed)}"
        )

    dirty = tuple(
        path for path in git_changed_paths(repo) if not is_controller_runtime_path(path)
    )
    if not dirty:
        raise RepairRefused("the worktree has no task-owned dirty files to resume")
    out_of_scope = tuple(
        path for path in dirty if not path_is_allowed(path, task.allowed_paths)
    )
    if out_of_scope:
        raise RepairRefused(
            f"current dirty worktree is not exclusively owned by {task.id}: {list(out_of_scope)}"
        )

    previous_base = base_commit
    lock["base_commit"] = current_head
    lock["run_id"] = "repaired-" + datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
    _write_json_atomically(lock_path, lock)
    return previous_base, current_head, dirty, state_path


def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Safely advance a stale R4R active-task lock across maintenance-only commits"
    )
    parser.add_argument("--repo", type=Path, default=Path.cwd())
    parser.add_argument("--plan", type=Path, default=Path(".opencode/task-plan.json"))
    parser.add_argument("--progress", type=Path, default=Path(".opencode/progress.json"))
    parser.add_argument("--state", type=Path, default=None)
    return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    args = parse_args(argv)
    try:
        old_base, new_base, dirty, state_path = repair_active_task_lock(
            args.repo,
            args.plan,
            args.progress,
            args.state,
        )
    except (OSError, ValueError, RepairRefused, json.JSONDecodeError) as exception:
        print(f"[r4r] active-task lock repair refused: {exception}", file=sys.stderr)
        return REPAIR_REFUSED_EXIT

    print(
        f"[r4r] active-task lock advanced safely: {old_base[:12]} -> {new_base[:12]}",
        flush=True,
    )
    print(f"[r4r] evidence: {state_path}", flush=True)
    for path in dirty:
        print(f"[r4r] task-owned dirty path: {path}", flush=True)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
