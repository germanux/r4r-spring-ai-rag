from __future__ import annotations

from dataclasses import dataclass
import json
from pathlib import Path
from typing import Any


@dataclass(frozen=True)
class Task:
    schema_version: int
    id: str
    benchmark: str
    objective: str
    allowed_paths: tuple[str, ...]
    pre_gate: tuple[str, ...]
    post_gate: tuple[str, ...]
    opencode_prompt: str
    review_required: bool


def load_task(path: Path) -> Task:
    raw: Any = json.loads(path.read_text(encoding="utf-8"))
    required = {
        "schema_version",
        "id",
        "benchmark",
        "objective",
        "allowed_paths",
        "pre_gate",
        "post_gate",
        "opencode_prompt",
        "review_required",
    }
    missing = required.difference(raw)
    if missing:
        raise ValueError(f"Task is missing fields: {sorted(missing)}")
    if raw["schema_version"] != 1:
        raise ValueError("Unsupported task schema_version")
    for field in ("allowed_paths", "pre_gate", "post_gate"):
        if not isinstance(raw[field], list) or not all(isinstance(value, str) for value in raw[field]):
            raise ValueError(f"{field} must be a list of strings")
    if not raw["pre_gate"] or not raw["post_gate"]:
        raise ValueError("Both deterministic gates are required")
    return Task(
        schema_version=1,
        id=str(raw["id"]),
        benchmark=str(raw["benchmark"]),
        objective=str(raw["objective"]),
        allowed_paths=tuple(raw["allowed_paths"]),
        pre_gate=tuple(raw["pre_gate"]),
        post_gate=tuple(raw["post_gate"]),
        opencode_prompt=str(raw["opencode_prompt"]),
        review_required=bool(raw["review_required"]),
    )


def validate_decision(raw: Any, task_id: str) -> dict[str, Any]:
    if not isinstance(raw, dict):
        raise ValueError("Decision must be a JSON object")
    expected = {"schema_version", "decision", "task_id", "summary", "paths", "next_action"}
    if set(raw) != expected:
        raise ValueError("Decision fields do not match the strict contract")
    if raw["schema_version"] != 1:
        raise ValueError("Unsupported decision schema_version")
    if raw["decision"] not in {"ACCEPT", "REVISE", "BLOCKED"}:
        raise ValueError("Invalid decision")
    if raw["task_id"] != task_id:
        raise ValueError("Decision task_id does not match the active task")
    if not isinstance(raw["paths"], list) or not all(isinstance(path, str) for path in raw["paths"]):
        raise ValueError("Decision paths must be a list of strings")
    for field in ("summary", "next_action"):
        if not isinstance(raw[field], str) or not raw[field].strip():
            raise ValueError(f"Decision {field} must be non-empty")
    return raw
