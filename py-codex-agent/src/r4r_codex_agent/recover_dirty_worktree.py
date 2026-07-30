from __future__ import annotations

import argparse
from datetime import datetime, timezone
import json
import os
from pathlib import Path
import re
import sys
from typing import Any

from .contracts import Task, TaskPlan, load_progress, load_task_plan, task_progress
from .runner import (
    git_changed_paths,
    git_head,
    is_controller_runtime_path,
    path_is_allowed,
)


RECOVERY_REFUSED_EXIT = 65
_RUN_DIRECTORY_PATTERN = re.compile(r"^[0-9]{8}T[0-9]{6}Z$")


class RecoveryRefused(RuntimeError):
    """Raised when dirty-worktree ownership cannot be inferred safely."""


def _read_json_object(path: Path, label: str) -> dict[str, Any]:
    try:
        raw: Any = json.loads(path.read_text(encoding="utf-8"))
    except FileNotFoundError as exception:
        raise RecoveryRefused(f"{label} does not exist: {path}") from exception
    except json.JSONDecodeError as exception:
        raise RecoveryRefused(f"{label} is not valid JSON: {path}: {exception}") from exception

    if not isinstance(raw, dict):
        raise RecoveryRefused(f"{label} must be a JSON object: {path}")
    return raw


def latest_controller_state(repo: Path) -> Path:
    runs_dir = repo / "runtime" / "runs"
    if not runs_dir.is_dir():
        raise RecoveryRefused(f"controller runs directory does not exist: {runs_dir}")

    candidates = sorted(
        (
            child / "state.json"
            for child in runs_dir.iterdir()
            if child.is_dir() and _RUN_DIRECTORY_PATTERN.fullmatch(child.name)
        ),
        reverse=True,
    )
    for state_path in candidates:
        if state_path.is_file():
            return state_path

    raise RecoveryRefused(f"no controller state.json found under: {runs_dir}")


def first_unaccepted_task(plan: TaskPlan, progress: dict[str, Any]) -> Task:
    for task in plan.tasks:
        if task_progress(progress, task.id)["status"] != "ACCEPTED":
            return task
    raise RecoveryRefused("all tasks are already ACCEPTED; dirty ownership is ambiguous")


def _normalise_changed_paths(raw: Any, label: str) -> tuple[str, ...]:
    if not isinstance(raw, list) or not all(isinstance(path, str) and path for path in raw):
        raise RecoveryRefused(f"{label} must be a list of non-empty paths")
    if len(raw) != len(set(raw)):
        raise RecoveryRefused(f"{label} contains duplicate paths")
    return tuple(sorted(raw))


def _validate_scope(task: Task, changed_paths: tuple[str, ...]) -> tuple[str, ...]:
    product_paths = tuple(
        path for path in changed_paths
        if not is_controller_runtime_path(path)
    )
    if not product_paths:
        raise RecoveryRefused("the dirty worktree contains no product paths to adopt")

    disallowed = tuple(
        path for path in product_paths
        if not path_is_allowed(path, task.allowed_paths)
    )
    if disallowed:
        raise RecoveryRefused(
            f"dirty paths do not belong exclusively to {task.id}: {list(disallowed)}"
        )
    return product_paths


def _validate_existing_lock(
    lock_path: Path,
    task: Task,
    current_head: str,
    changed_paths: tuple[str, ...],
) -> None:
    lock = _read_json_object(lock_path, "active-task lock")
    if lock.get("schema_version") != 1:
        raise RecoveryRefused("existing active-task lock has an unsupported schema_version")
    if lock.get("task_id") != task.id:
        raise RecoveryRefused(
            f"existing lock belongs to {lock.get('task_id')!r}, not {task.id!r}"
        )
    if lock.get("base_commit") != current_head:
        raise RecoveryRefused("existing lock base_commit does not match current Git HEAD")
    if tuple(lock.get("allowed_paths") or ()) != task.allowed_paths:
        raise RecoveryRefused("existing lock allowed_paths do not match the task plan")
    _validate_scope(task, changed_paths)


def _write_lock_atomically(lock_path: Path, payload: dict[str, Any]) -> None:
    lock_path.parent.mkdir(parents=True, exist_ok=True)
    temporary = lock_path.with_name(
        f".{lock_path.name}.{os.getpid()}.{datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%S%fZ')}.tmp"
    )
    data = (json.dumps(payload, indent=2, ensure_ascii=False) + "\n").encode("utf-8")

    descriptor = os.open(temporary, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    try:
        with os.fdopen(descriptor, "wb", closefd=True) as stream:
            stream.write(data)
            stream.flush()
            os.fsync(stream.fileno())
        try:
            os.link(temporary, lock_path)
        except FileExistsError as exception:
            raise RecoveryRefused(
                f"active-task lock appeared concurrently: {lock_path}"
            ) from exception
        directory_descriptor = os.open(lock_path.parent, os.O_RDONLY)
        try:
            os.fsync(directory_descriptor)
        finally:
            os.close(directory_descriptor)
    finally:
        temporary.unlink(missing_ok=True)


def recover_dirty_worktree(
    repo: Path,
    plan_path: Path,
    progress_path: Path,
    state_path: Path | None = None,
) -> tuple[Task, tuple[str, ...], Path, bool]:
    repo = repo.resolve()
    plan_path = plan_path if plan_path.is_absolute() else repo / plan_path
    progress_path = progress_path if progress_path.is_absolute() else repo / progress_path
    state_path = state_path or latest_controller_state(repo)
    state_path = state_path if state_path.is_absolute() else repo / state_path

    plan = load_task_plan(plan_path)
    progress = load_progress(progress_path, (task.id for task in plan.tasks))
    task = first_unaccepted_task(plan, progress)

    active_task = progress.get("active_task")
    if active_task not in (None, task.id):
        raise RecoveryRefused(
            f"progress.active_task is {active_task!r}, but the first unaccepted task is {task.id!r}"
        )

    current_head = git_head(repo)
    if not current_head:
        raise RecoveryRefused("Git HEAD is unavailable")

    current_changed = git_changed_paths(repo)
    if not current_changed:
        raise RecoveryRefused("the worktree is clean; there is nothing to recover")

    lock_path = repo / "runtime" / "locks" / "active-task.json"
    if lock_path.exists():
        _validate_existing_lock(lock_path, task, current_head, current_changed)
        return task, _validate_scope(task, current_changed), state_path, False

    state = _read_json_object(state_path, "controller state")
    if state.get("schema_version") != 1:
        raise RecoveryRefused("controller state has an unsupported schema_version")
    if state.get("status") != "DIRTY_WORKTREE_UNOWNED" or state.get("exit_code") != 64:
        raise RecoveryRefused(
            "latest controller state is not DIRTY_WORKTREE_UNOWNED with exit code 64"
        )
    if state.get("git_head") != current_head:
        raise RecoveryRefused("controller state Git HEAD is stale")

    state_changed = _normalise_changed_paths(
        state.get("changed_paths"),
        "controller state changed_paths",
    )
    state_product_paths = tuple(
        path for path in state_changed
        if not is_controller_runtime_path(path)
    )
    current_product_paths = tuple(
        path for path in current_changed
        if not is_controller_runtime_path(path)
    )
    if state_product_paths != current_product_paths:
        raise RecoveryRefused(
            "product worktree changed after the controller recorded DIRTY_WORKTREE_UNOWNED"
        )

    product_paths = _validate_scope(task, current_changed)
    payload = {
        "schema_version": 1,
        "task_id": task.id,
        "base_commit": current_head,
        "run_id": "recovered-" + datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ"),
        "allowed_paths": list(task.allowed_paths),
    }
    _write_lock_atomically(lock_path, payload)
    return task, product_paths, state_path, True


def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Safely adopt an unowned dirty worktree for the first unaccepted R4R task"
    )
    parser.add_argument("--repo", type=Path, default=Path.cwd())
    parser.add_argument("--plan", type=Path, default=Path(".opencode/task-plan.json"))
    parser.add_argument("--progress", type=Path, default=Path(".opencode/progress.json"))
    parser.add_argument("--state", type=Path, default=None)
    return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    args = parse_args(argv)
    try:
        task, product_paths, state_path, created = recover_dirty_worktree(
            args.repo,
            args.plan,
            args.progress,
            args.state,
        )
    except (OSError, ValueError, RecoveryRefused, json.JSONDecodeError) as exception:
        print(f"[r4r] dirty-worktree recovery refused: {exception}", file=sys.stderr)
        return RECOVERY_REFUSED_EXIT

    action = "created" if created else "already valid"
    print(f"[r4r] active-task lock {action} for {task.id}", flush=True)
    print(f"[r4r] evidence: {state_path}", flush=True)
    for path in product_paths:
        print(f"[r4r] adopted: {path}", flush=True)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
