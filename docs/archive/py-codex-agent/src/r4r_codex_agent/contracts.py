from __future__ import annotations

from dataclasses import dataclass
import json
from pathlib import Path
from typing import Any, Iterable


@dataclass(frozen=True)
class Task:
    id: str
    command: str
    objective: str
    allowed_paths: tuple[str, ...]
    gate: tuple[str, ...]
    commit_message: str


@dataclass(frozen=True)
class TaskPlan:
    schema_version: int
    tasks: tuple[Task, ...]
    final_gate: tuple[str, ...]


def _string_list(raw: Any, name: str, *, non_empty: bool = True) -> tuple[str, ...]:
    if not isinstance(raw, list) or not all(isinstance(value, str) and value for value in raw):
        raise ValueError(f"{name} must be a list of non-empty strings")
    if non_empty and not raw:
        raise ValueError(f"{name} cannot be empty")
    return tuple(raw)


def load_task_plan(path: Path) -> TaskPlan:
    raw: Any = json.loads(path.read_text(encoding="utf-8"))
    if raw.get("schema_version") != 1:
        raise ValueError("Unsupported task-plan schema_version")
    task_values = raw.get("tasks")
    if not isinstance(task_values, list) or not task_values:
        raise ValueError("Task plan requires at least one task")
    tasks: list[Task] = []
    ids: set[str] = set()
    for index, value in enumerate(task_values):
        if not isinstance(value, dict):
            raise ValueError(f"tasks[{index}] must be an object")
        required = {"id", "command", "objective", "allowed_paths", "gate", "commit_message"}
        missing = required.difference(value)
        if missing:
            raise ValueError(f"tasks[{index}] is missing fields: {sorted(missing)}")
        task_id = str(value["id"])
        if task_id in ids:
            raise ValueError(f"Duplicate task id: {task_id}")
        ids.add(task_id)
        tasks.append(Task(
            id=task_id,
            command=str(value["command"]),
            objective=str(value["objective"]),
            allowed_paths=_string_list(value["allowed_paths"], "allowed_paths"),
            gate=_string_list(value["gate"], "gate"),
            commit_message=str(value["commit_message"]),
        ))
    return TaskPlan(1, tuple(tasks), _string_list(raw.get("final_gate"), "final_gate"))


def default_progress(task_ids: Iterable[str]) -> dict[str, Any]:
    return {
        "schema_version": 1,
        "active_task": None,
        "last_run": None,
        "tasks": [
            {"id": task_id, "status": "PENDING", "accepted_at": None}
            for task_id in task_ids
        ],
    }


def load_progress(path: Path, task_ids: Iterable[str]) -> dict[str, Any]:
    expected = tuple(task_ids)
    if not path.exists():
        return default_progress(expected)

    raw: Any = json.loads(path.read_text(encoding="utf-8"))
    if raw.get("schema_version") != 1 or not isinstance(raw.get("tasks"), list):
        raise ValueError("Invalid progress file")

    valid = {"PENDING", "ACTIVE", "ACCEPTED", "REGRESSION", "BLOCKED"}
    by_id: dict[str, dict[str, Any]] = {}
    for index, item in enumerate(raw["tasks"]):
        if not isinstance(item, dict):
            raise ValueError(f"progress.tasks[{index}] must be an object")
        task_id = str(item.get("id") or "").strip()
        if not task_id:
            raise ValueError(f"progress.tasks[{index}] requires a non-empty id")
        if task_id in by_id:
            raise ValueError(f"Duplicate progress task id: {task_id}")
        if item.get("status") not in valid:
            raise ValueError(f"Invalid task status: {item.get('status')}")
        by_id[task_id] = item

    unknown = sorted(set(by_id).difference(expected))
    if unknown:
        raise ValueError(
            "Progress contains tasks that are absent from the task plan: "
            + ", ".join(unknown)
        )

    # Task plans may evolve by inserting new commit-sized subtasks. Preserve every
    # existing task record by id, reorder it to the current plan and initialize only
    # the newly inserted tasks as PENDING. This keeps accepted history and an active
    # legacy task intact while allowing additive plan refinement.
    raw["tasks"] = [
        by_id.get(task_id, {
            "id": task_id,
            "status": "PENDING",
            "accepted_at": None,
        })
        for task_id in expected
    ]

    active_task = raw.get("active_task")
    if active_task is not None and str(active_task) not in expected:
        raise ValueError(
            f"Active progress task is absent from the task plan: {active_task}"
        )
    return raw


def task_progress(progress: dict[str, Any], task_id: str) -> dict[str, Any]:
    for item in progress["tasks"]:
        if item["id"] == task_id:
            return item
    raise ValueError(f"Unknown task in progress: {task_id}")


def validate_structured_result(raw: Any, expected_task_id: str, allowed_decisions: set[str]) -> dict[str, Any]:
    if not isinstance(raw, dict):
        raise ValueError("Structured result must be an object")
    if raw.get("schema_version") != 1:
        raise ValueError("Unsupported structured-result schema_version")
    if raw.get("task_id") != expected_task_id:
        raise ValueError("Structured result references another task")
    if raw.get("decision") not in allowed_decisions:
        raise ValueError(f"Unexpected decision: {raw.get('decision')}")
    return raw
