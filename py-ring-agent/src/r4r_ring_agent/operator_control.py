from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime, timezone
import fcntl
import json
import os
from pathlib import Path
import re
from typing import Any


WORKERS = ("RING", "PC", "LP", "MAINTAINER")
RING_TARGET_WORKERS = ("RING", "MAINTAINER")
COMMANDS = ("stop", "pause", "continue", "restart")
TARGETS = (*WORKERS, "ALL")
HEARTBEAT_MAX_AGE_SECONDS = 20


def _utc_now() -> str:
    return datetime.now(timezone.utc).isoformat()


def _strip_jsonc(text: str) -> str:
    output: list[str] = []
    index = 0
    in_string = False
    escaped = False
    while index < len(text):
        char = text[index]
        if in_string:
            output.append(char)
            if escaped:
                escaped = False
            elif char == "\\":
                escaped = True
            elif char == '"':
                in_string = False
            index += 1
            continue
        if char == '"':
            in_string = True
            output.append(char)
            index += 1
            continue
        if text[index : index + 2] == "//":
            index += 2
            while index < len(text) and text[index] not in "\r\n":
                index += 1
            continue
        if text[index : index + 2] == "/*":
            end = text.find("*/", index + 2)
            index = len(text) if end < 0 else end + 2
            continue
        output.append(char)
        index += 1
    return re.sub(r",\s*([}\]])", r"\1", "".join(output))


def _default_document() -> dict[str, Any]:
    return {
        "schema_version": 1,
        "commands": {
            "stop": "Stop the selected process after finalizing its logs.",
            "pause": "Stop the current LLM session and remain paused.",
            "continue": "Leave pause and start a completely new session.",
            "restart": "Stop the current session and immediately start a new one.",
        },
        "state": {worker: "stopped" for worker in WORKERS},
        "next_state": "",
        "target": "RING",
        "reason": "",
        "request": {
            "id": 0,
            "expected_targets": [],
            "executed_by": [],
            "created_at": "",
        },
        "last_command": "",
        "last_result": "",
        "last_transition_at": "",
        "revision": 0,
    }


def _render_jsonc(value: dict[str, Any]) -> str:
    encoded = json.dumps(value, indent=2, ensure_ascii=False)
    lines = encoded.splitlines()
    return "\n".join(
        [
            "{",
            "  // Edit only next_state, target and reason.",
            '  // next_state: "stop", "pause", "continue", "restart" or "".',
            '  // target: "RING", "PC", "LP", "MAINTAINER" or "ALL".',
            *lines[1:-1],
            "}",
            "",
        ]
    )


@dataclass(frozen=True)
class OperatorCommand:
    request_id: int
    command: str
    target: str
    reason: str


class RingCommandFile:
    """One human-editable JSONC file shared by Ring, PC, LP and maintainer wrappers."""

    def __init__(self, repo: Path, worker: str):
        worker = worker.upper()
        if worker not in WORKERS:
            raise ValueError(f"invalid worker: {worker}")
        self.repo = repo.resolve()
        self.worker = worker
        self.path = self.repo / "runtime" / "the-ring-command.jsonc"
        self.lock_path = self.repo / "runtime" / ".the-ring-command.lock"
        self.heartbeat_dir = self.repo / "runtime" / "the-ring-heartbeats"
        self.path.parent.mkdir(parents=True, exist_ok=True)
        self.heartbeat_dir.mkdir(parents=True, exist_ok=True)
        self._ensure()

    def _lock(self):
        handle = self.lock_path.open("a+", encoding="utf-8")
        fcntl.flock(handle.fileno(), fcntl.LOCK_EX)
        return handle

    def _read_unlocked(self) -> dict[str, Any]:
        try:
            value = json.loads(_strip_jsonc(self.path.read_text(encoding="utf-8")))
        except (OSError, json.JSONDecodeError, TypeError, ValueError):
            value = _default_document()
        if not isinstance(value, dict):
            value = _default_document()
        defaults = _default_document()
        for key, default in defaults.items():
            value.setdefault(key, default)
        if not isinstance(value.get("state"), dict):
            value["state"] = defaults["state"]
        for worker in WORKERS:
            value["state"].setdefault(worker, "stopped")
        if not isinstance(value.get("request"), dict):
            value["request"] = defaults["request"]
        return value

    def _write_unlocked(self, value: dict[str, Any]) -> None:
        temporary = self.path.with_suffix(".jsonc.tmp")
        temporary.write_text(_render_jsonc(value), encoding="utf-8")
        with temporary.open("r+", encoding="utf-8") as handle:
            handle.flush()
            os.fsync(handle.fileno())
        temporary.replace(self.path)

    def _ensure(self) -> None:
        if self.path.exists():
            return
        lock = self._lock()
        try:
            if not self.path.exists():
                self._write_unlocked(_default_document())
        finally:
            fcntl.flock(lock.fileno(), fcntl.LOCK_UN)
            lock.close()

    def heartbeat(self, state: str) -> None:
        path = self.heartbeat_dir / f"{self.worker}.json"
        temporary = path.with_suffix(".json.tmp")
        temporary.write_text(
            json.dumps(
                {
                    "worker": self.worker,
                    "pid": os.getpid(),
                    "state": state,
                    "updated_at": _utc_now(),
                    "updated_at_epoch": datetime.now(timezone.utc).timestamp(),
                },
                indent=2,
            )
            + "\n",
            encoding="utf-8",
        )
        temporary.replace(path)

    def _target_matches(self, target: str) -> bool:
        if target == "ALL":
            return True
        if target == "RING":
            return self.worker in RING_TARGET_WORKERS
        return target == self.worker

    def _expected_targets_unlocked(self, target: str) -> list[str]:
        active = self._active_workers_unlocked()
        if target == "ALL":
            return active
        if target == "RING":
            return [worker for worker in active if worker in RING_TARGET_WORKERS]
        return [target]

    def _active_workers_unlocked(self) -> list[str]:
        now = datetime.now(timezone.utc).timestamp()
        active: list[str] = []
        for worker in WORKERS:
            path = self.heartbeat_dir / f"{worker}.json"
            try:
                value = json.loads(path.read_text(encoding="utf-8"))
                age = now - float(value.get("updated_at_epoch", 0))
                pid = int(value.get("pid", 0))
                if age <= HEARTBEAT_MAX_AGE_SECONDS and pid > 0:
                    os.kill(pid, 0)
                    active.append(worker)
            except (OSError, ValueError, TypeError, json.JSONDecodeError):
                continue
        return active

    def set_state(self, state: str, result: str = "") -> None:
        lock = self._lock()
        try:
            value = self._read_unlocked()
            value["state"][self.worker] = state
            value["last_result"] = result
            value["last_transition_at"] = _utc_now()
            value["revision"] = int(value.get("revision", 0)) + 1
            self._write_unlocked(value)
        finally:
            fcntl.flock(lock.fileno(), fcntl.LOCK_UN)
            lock.close()
        self.heartbeat(state)

    def poll(self) -> OperatorCommand | None:
        self.heartbeat(self.current_state())
        lock = self._lock()
        try:
            value = self._read_unlocked()
            command = str(value.get("next_state", "")).strip().lower()
            target = str(value.get("target", "RING")).strip().upper()
            if not command:
                return None
            if command not in COMMANDS or target not in TARGETS:
                value["last_result"] = f"INVALID: {command!r} target={target!r}"
                value["next_state"] = ""
                self._write_unlocked(value)
                return None
            if not self._target_matches(target):
                return None

            request = value["request"]
            request_id = int(request.get("id", 0))
            created_at = str(request.get("created_at", ""))
            if not created_at:
                request_id += 1
                expected = self._expected_targets_unlocked(target)
                if not expected:
                    expected = [self.worker]
                request.update(
                    {
                        "id": request_id,
                        "expected_targets": expected,
                        "executed_by": [],
                        "created_at": _utc_now(),
                    }
                )
                value["last_result"] = f"PENDING: {command} for {target}"
                value["revision"] = int(value.get("revision", 0)) + 1
                self._write_unlocked(value)
            executed = {str(item).upper() for item in request.get("executed_by", [])}
            if self.worker in executed:
                return None
            return OperatorCommand(
                request_id=request_id,
                command=command,
                target=target,
                reason=str(value.get("reason", "")),
            )
        finally:
            fcntl.flock(lock.fileno(), fcntl.LOCK_UN)
            lock.close()

    def complete(self, command: OperatorCommand, new_state: str, result: str) -> None:
        lock = self._lock()
        try:
            value = self._read_unlocked()
            request = value["request"]
            if int(request.get("id", -1)) != command.request_id:
                return
            executed = [str(item).upper() for item in request.get("executed_by", [])]
            if self.worker not in executed:
                executed.append(self.worker)
            request["executed_by"] = executed
            value["state"][self.worker] = new_state
            expected = {str(item).upper() for item in request.get("expected_targets", [])}
            if expected.issubset(set(executed)):
                value["last_command"] = command.command
                value["last_result"] = result
                value["next_state"] = ""
                value["reason"] = ""
                request["expected_targets"] = []
                request["executed_by"] = []
                request["created_at"] = ""
            else:
                value["last_result"] = f"PARTIAL: {command.command}; done={executed}"
            value["last_transition_at"] = _utc_now()
            value["revision"] = int(value.get("revision", 0)) + 1
            self._write_unlocked(value)
        finally:
            fcntl.flock(lock.fileno(), fcntl.LOCK_UN)
            lock.close()
        self.heartbeat(new_state)

    def current_state(self) -> str:
        lock = self._lock()
        try:
            return str(self._read_unlocked()["state"].get(self.worker, "stopped"))
        finally:
            fcntl.flock(lock.fileno(), fcntl.LOCK_UN)
            lock.close()
