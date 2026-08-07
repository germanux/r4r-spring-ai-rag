from __future__ import annotations

from contextlib import contextmanager
from datetime import datetime, timezone
import fcntl
import json
import os
from pathlib import Path
from typing import Any, Iterator, Mapping, Sequence


ACTIVE_ACTIONS = {"START", "CONTINUE", "RETRY_AUTHORIZED"}
FINAL_ACTIONS = ACTIVE_ACTIONS | {"HOLD", "STOP", "NO_ACTION"}


def parse_utc_timestamp(value: Any) -> datetime | None:
    if not isinstance(value, str) or not value.strip():
        return None
    try:
        parsed = datetime.fromisoformat(value.replace("Z", "+00:00"))
    except ValueError:
        return None
    if parsed.tzinfo is None:
        return None
    return parsed.astimezone(timezone.utc)


def global_progress_path(ring_worktree: Path) -> Path:
    return ring_worktree / "runtime" / "control" / "RING" / "global-progress.json"


def default_global_progress() -> dict[str, Any]:
    return {
        "schema_version": 1,
        "accepted": {},
        "final_gate": {"status": "PENDING", "checked_at": None, "exit_code": None},
        "updated_at": None,
    }


def load_global_progress(path: Path) -> dict[str, Any]:
    if not path.is_file():
        return default_global_progress()
    value = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(value, dict) or value.get("schema_version") != 1:
        raise ValueError(f"Invalid global progress ledger: {path}")
    accepted = value.get("accepted")
    if not isinstance(accepted, dict):
        raise ValueError(f"Global progress accepted must be an object: {path}")
    for task_id, record in accepted.items():
        if not isinstance(task_id, str) or not task_id or not isinstance(record, dict):
            raise ValueError(f"Invalid global progress acceptance record: {task_id!r}")
        if not isinstance(record.get("worker"), str) or not record["worker"]:
            raise ValueError(f"Global progress acceptance lacks worker: {task_id}")
        if not isinstance(record.get("commit"), str) or not record["commit"]:
            raise ValueError(f"Global progress acceptance lacks commit: {task_id}")
    final_gate = value.get("final_gate")
    if not isinstance(final_gate, dict):
        value["final_gate"] = default_global_progress()["final_gate"]
    return value


@contextmanager
def _ledger_lock(path: Path) -> Iterator[None]:
    lock = path.with_suffix(path.suffix + ".lock")
    lock.parent.mkdir(parents=True, exist_ok=True)
    with lock.open("a+") as handle:
        fcntl.flock(handle.fileno(), fcntl.LOCK_EX)
        try:
            yield
        finally:
            fcntl.flock(handle.fileno(), fcntl.LOCK_UN)


def _atomic_write_json(path: Path, value: Mapping[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_name(f".{path.name}.tmp-{os.getpid()}")
    temporary.write_text(
        json.dumps(value, indent=2, ensure_ascii=False) + "\n",
        encoding="utf-8",
    )
    os.replace(temporary, path)


def record_global_acceptance(
    path: Path,
    *,
    task_id: str,
    worker: str,
    commit: str,
) -> dict[str, Any]:
    with _ledger_lock(path):
        value = load_global_progress(path)
        current = value["accepted"].get(task_id)
        if current is not None and (
            current.get("commit") != commit or current.get("worker") != worker
        ):
            raise RuntimeError(
                f"Task {task_id} is already globally accepted by "
                f"{current.get('worker')} at {current.get('commit')}"
            )
        value["accepted"][task_id] = {
            "worker": worker,
            "commit": commit,
            "accepted_at": datetime.now(timezone.utc).isoformat(),
        }
        value["updated_at"] = datetime.now(timezone.utc).isoformat()
        _atomic_write_json(path, value)
        return value


def migrate_legacy_acceptances(
    path: Path,
    *,
    plan_task_ids: Sequence[str],
    worker_progress: Mapping[str, Path],
    worker_heads: Mapping[str, str],
) -> dict[str, Any]:
    """Seed the global ledger once from accepted per-worker progress records."""
    allowed = set(plan_task_ids)
    with _ledger_lock(path):
        value = load_global_progress(path)
        changed = False
        for worker, progress_path in worker_progress.items():
            if not progress_path.is_file():
                continue
            try:
                progress = json.loads(progress_path.read_text(encoding="utf-8"))
            except (OSError, json.JSONDecodeError):
                continue
            head = str(worker_heads.get(worker) or "").strip()
            if not head:
                continue
            for item in progress.get("tasks", []):
                if (
                    not isinstance(item, dict)
                    or item.get("status") != "ACCEPTED"
                    or item.get("id") not in allowed
                    or item["id"] in value["accepted"]
                ):
                    continue
                value["accepted"][item["id"]] = {
                    "worker": worker,
                    "commit": head,
                    "accepted_at": item.get("accepted_at")
                    or datetime.now(timezone.utc).isoformat(),
                    "migrated": True,
                }
                changed = True
        if changed or not path.exists():
            value["updated_at"] = datetime.now(timezone.utc).isoformat()
            _atomic_write_json(path, value)
        return value


def record_final_gate(
    path: Path,
    *,
    exit_code: int,
    worker: str,
) -> dict[str, Any]:
    with _ledger_lock(path):
        value = load_global_progress(path)
        value["final_gate"] = {
            "status": "GREEN" if exit_code == 0 else "RED",
            "checked_at": datetime.now(timezone.utc).isoformat(),
            "exit_code": exit_code,
            "worker": worker,
        }
        value["updated_at"] = datetime.now(timezone.utc).isoformat()
        _atomic_write_json(path, value)
        return value


def validate_assignment(
    value: Any,
    *,
    worker: str,
    tasks: Mapping[str, Any],
    accepted_task_ids: Sequence[str] = (),
    max_age_seconds: int,
    now: datetime | None = None,
    require_active: bool = True,
) -> dict[str, Any]:
    if not isinstance(value, dict) or value.get("schema_version") != 1:
        raise ValueError("assignment schema_version must be 1")
    if str(value.get("target") or "").upper() != worker.upper():
        raise ValueError("assignment target mismatch")
    if str(value.get("priority") or "").lower() != "advisory":
        raise ValueError("assignment priority must be advisory")
    action = str(value.get("action") or "").upper()
    if action not in FINAL_ACTIONS:
        raise ValueError(f"assignment action is invalid: {action!r}")
    if require_active and action not in ACTIVE_ACTIONS:
        raise ValueError(f"assignment is not active: {action}")
    generated_at = parse_utc_timestamp(value.get("generated_at"))
    expires_at = parse_utc_timestamp(value.get("expires_at"))
    current = now or datetime.now(timezone.utc)
    if generated_at is None or expires_at is None or expires_at <= current:
        raise ValueError("assignment timestamps are invalid or expired")
    age = (current - generated_at).total_seconds()
    if age < -300 or age > max_age_seconds:
        raise ValueError(f"assignment is stale or generated in the future: {int(age)}s")
    assignment_id = str(value.get("assignment_id") or "").strip()
    if not assignment_id:
        raise ValueError("assignment_id is required")
    if action not in ACTIVE_ACTIONS:
        inactive_task_id = str(value.get("task_id") or "").strip()
        write_scope = value.get("write_scope")
        if not inactive_task_id:
            raise ValueError("inactive assignment requires a task_id")
        if not isinstance(write_scope, list) or not all(
            isinstance(item, str) and item for item in write_scope
        ):
            raise ValueError("inactive assignment write_scope must be a string array")
        normalized = dict(value)
        normalized.update(
            action=action,
            target=worker.upper(),
            task_id=inactive_task_id,
            assignment_id=assignment_id,
            write_scope=list(write_scope),
        )
        return normalized
    task_id = str(value.get("task_id") or "").strip()
    if not task_id or task_id == "NO_ACTIVE_TASK":
        raise ValueError("assignment requires a task_id")
    task = tasks.get(task_id)
    if task is None:
        raise ValueError(f"assignment task is absent from the canonical plan: {task_id}")
    accepted = set(accepted_task_ids)
    if task_id in accepted:
        raise ValueError(f"assignment task is already globally accepted: {task_id}")
    dependencies = tuple(getattr(task, "depends_on", ()) or ())
    missing_dependencies = sorted(set(dependencies).difference(accepted))
    if missing_dependencies:
        raise ValueError(
            f"assignment task {task_id} has unmet dependencies: {missing_dependencies}"
        )
    write_scope = value.get("write_scope")
    canonical_scope = tuple(getattr(task, "allowed_paths", ()) or ())
    if (
        not isinstance(write_scope, list)
        or not all(isinstance(item, str) and item for item in write_scope)
        or set(write_scope) != set(canonical_scope)
        or len(write_scope) != len(canonical_scope)
    ):
        raise ValueError(f"assignment scope does not match canonical task {task_id}")
    normalized = dict(value)
    normalized.update(
        action=action,
        target=worker.upper(),
        task_id=task_id,
        assignment_id=assignment_id,
        write_scope=list(canonical_scope),
    )
    return normalized
