from __future__ import annotations

from datetime import datetime, timedelta, timezone
import fcntl
import json
import os
from pathlib import Path
import shutil
import subprocess
import time
from typing import Any, Sequence

from .operator_control import OperatorCommand, RingCommandFile
from .ring_process import run_streamed
from .worktrees import WorktreePaths, require_git_worktree


# ---------------------------------------------------------------------------
# Editable runtime defaults. Worktree paths live in run-ring-agent.py.
# Environment variables override these values for the supervised runtime.
# ---------------------------------------------------------------------------
RING_AGENT = os.environ.get("R4R_RING_AGENT", "r4r-ring")
RING_MODEL = os.environ.get(
    "R4R_RING_MODEL",
    "ollama-pc/qwen3-coder-next-80b-t033-128k-8k-pc-pc:latest",
)
REVIEW_INTERVAL_SECONDS = int(
    os.environ.get("R4R_RING_REVIEW_INTERVAL_SECONDS", "3600")
)
SESSION_TIMEOUT_SECONDS = int(
    os.environ.get("R4R_RING_SESSION_TIMEOUT_SECONDS", str(90 * 60))
)
RUN_IMMEDIATELY = os.environ.get("R4R_RING_RUN_IMMEDIATELY", "true").lower() == "true"
OPENCODE_BIN = os.environ.get("R4R_OPENCODE_BIN", "opencode")
DIRECTIVE_MAX_AGE_SECONDS = int(
    os.environ.get("R4R_RING_DIRECTIVE_MAX_AGE_SECONDS", "10800")
)
EVENT_MIN_INTERVAL_SECONDS = int(
    os.environ.get("R4R_RING_EVENT_MIN_INTERVAL_SECONDS", "300")
)


def _git(repo: Path, args: Sequence[str]) -> str:
    result = subprocess.run(
        ["git", *args],
        cwd=repo,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        check=False,
    )
    return result.stdout


def _write_repo_evidence(label: str, repo: Path, run_dir: Path) -> None:
    prefix = label.lower()
    (run_dir / f"{prefix}-last-ten-commits.txt").write_text(
        _git(
            repo,
            [
                "log",
                "-10",
                "--date=iso-strict",
                "--format=commit %H%nDate: %ad%nSubject: %s",
                "--name-status",
            ],
        ),
        encoding="utf-8",
    )
    (run_dir / f"{prefix}-git-status.txt").write_text(
        _git(repo, ["status", "--short", "--branch"]), encoding="utf-8"
    )
    (run_dir / f"{prefix}-git-diff-stat.txt").write_text(
        _git(repo, ["diff", "--stat"]), encoding="utf-8"
    )


def _latest_directory(root: Path) -> Path | None:
    if not root.is_dir():
        return None
    candidates = [path for path in root.iterdir() if path.is_dir()]
    if not candidates:
        return None
    return max(candidates, key=lambda path: path.stat().st_mtime_ns)


def _latest_match(root: Path, pattern: str) -> Path | None:
    if not root.is_dir():
        return None
    matches = [path for path in root.rglob(pattern) if path.is_file()]
    if not matches:
        return None
    return max(matches, key=lambda path: path.stat().st_mtime_ns)


def _copy_snapshot_file(source: Path | None, destination: Path) -> str | None:
    if source is None or not source.is_file():
        return None
    destination.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(source, destination)
    return str(source)


def _worker_progress_path(worker: str, repo: Path) -> Path:
    suffix = "backend" if worker == "PC" else "frontend"
    return repo / ".opencode" / f"progress.{suffix}.json"


def _worker_memory_path(worker: str, repo: Path) -> Path:
    suffix = "backend" if worker == "PC" else "frontend"
    return repo / ".opencode" / f"memory.{suffix}.md"


def _request_directory(ring: Path) -> Path:
    return ring / "runtime" / "control" / "RING" / "requests"


def _pending_worker_requests(ring: Path) -> tuple[Path, ...]:
    root = _request_directory(ring)
    return tuple(
        path for path in (root / "PC.json", root / "LP.json")
        if path.is_file()
    )


def _consume_worker_requests(ring: Path, run_dir: Path) -> list[dict[str, Any]]:
    collected: list[dict[str, Any]] = []
    target = run_dir / "worker-requests"
    for source in _pending_worker_requests(ring):
        target.mkdir(parents=True, exist_ok=True)
        destination = target / source.name
        try:
            raw = source.read_text(encoding="utf-8")
            value = json.loads(raw)
        except (OSError, json.JSONDecodeError) as exception:
            value = {
                "schema_version": 1,
                "worker": source.stem,
                "reason": "invalid-request",
                "error": str(exception),
            }
            raw = json.dumps(value, indent=2) + "\n"
        destination.write_text(raw, encoding="utf-8")
        collected.append(value if isinstance(value, dict) else {"worker": source.stem})
        source.unlink(missing_ok=True)
    (run_dir / "worker-request-manifest.json").write_text(
        json.dumps(
            {
                "schema_version": 1,
                "consumed_at": datetime.now(timezone.utc).isoformat(),
                "requests": collected,
            },
            indent=2,
            ensure_ascii=False,
        ) + "\n",
        encoding="utf-8",
    )
    return collected


def _write_worker_runtime_evidence(
    worker: str,
    worker_repo: Path,
    ring_repo: Path,
    run_dir: Path,
) -> None:
    prefix = worker.lower()
    snapshot_dir = run_dir / f"{prefix}-runtime"
    snapshot_dir.mkdir(parents=True, exist_ok=True)

    latest_run = _latest_directory(worker_repo / "runtime" / "runs" / worker)
    sources: dict[str, str | None] = {
        "progress": _copy_snapshot_file(
            _worker_progress_path(worker, worker_repo),
            snapshot_dir / "progress.json",
        ),
        "memory": _copy_snapshot_file(
            _worker_memory_path(worker, worker_repo),
            snapshot_dir / "memory.md",
        ),
        "codex_extra_instructions": _copy_snapshot_file(
            worker_repo
            / "runtime"
            / "control"
            / worker
            / "codex-qwen3-extra-instructions.md",
            snapshot_dir / "codex-qwen3-extra-instructions.md",
        ),
        "ring_directive": _copy_snapshot_file(
            ring_repo
            / "runtime"
            / "control"
            / worker
            / "ring-qwen3-directive.json",
            snapshot_dir / "previous-ring-qwen3-directive.json",
        ),
    }

    if latest_run is not None:
        sources["latest_run"] = str(latest_run)
        selected_patterns = {
            "controller_state": "state.json",
            "codex_review": "codex-review.json",
            "codex_plan": "codex-plan.json",
            "local_understanding": "local-understanding.md",
            "pre_edit_understanding": "pre-edit-understanding.md",
            "codegraph_reconnaissance": "codegraph-reconnaissance.md",
            "gate_summary": "gate-summary.md",
            "checkpoint": "checkpoint.json",
        }
        for label, pattern in selected_patterns.items():
            source = _latest_match(latest_run, pattern)
            suffix = source.suffix if source is not None else Path(pattern).suffix
            sources[label] = _copy_snapshot_file(
                source,
                snapshot_dir / f"{label}{suffix}",
            )
    else:
        sources["latest_run"] = None

    (snapshot_dir / "manifest.json").write_text(
        json.dumps(
            {
                "schema_version": 1,
                "worker": worker,
                "worker_repo": str(worker_repo),
                "generated_at": datetime.now(timezone.utc).isoformat(),
                "sources": sources,
            },
            indent=2,
            ensure_ascii=False,
        )
        + "\n",
        encoding="utf-8",
    )


def _write_evidence(paths: WorktreePaths, run_dir: Path) -> None:
    run_dir.mkdir(parents=True, exist_ok=True)
    _write_repo_evidence("RING", paths.ring, run_dir)
    _write_repo_evidence("PC", paths.pc, run_dir)
    _write_repo_evidence("LP", paths.lp, run_dir)
    _write_worker_runtime_evidence("PC", paths.pc, paths.ring, run_dir)
    _write_worker_runtime_evidence("LP", paths.lp, paths.ring, run_dir)
    (run_dir / "worktrees.json").write_text(
        json.dumps(
            {
                "RING": str(paths.ring),
                "PC": str(paths.pc),
                "LP": str(paths.lp),
            },
            indent=2,
        )
        + "\n",
        encoding="utf-8",
    )


def _directive_path(ring: Path, worker: str) -> Path:
    return ring / "runtime" / "control" / worker / "ring-qwen3-directive.json"


def _parse_timestamp(value: Any) -> datetime | None:
    if not isinstance(value, str) or not value.strip():
        return None
    try:
        parsed = datetime.fromisoformat(value.replace("Z", "+00:00"))
    except ValueError:
        return None
    if parsed.tzinfo is None:
        return None
    return parsed.astimezone(timezone.utc)


def _validate_directive(path: Path, worker: str) -> dict[str, Any]:
    result: dict[str, Any] = {
        "worker": worker,
        "path": str(path),
        "valid": False,
        "reason": "missing",
    }
    if not path.is_file():
        return result
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exception:
        result["reason"] = f"invalid-json: {exception}"
        return result
    if not isinstance(value, dict):
        result["reason"] = "root-not-object"
        return result
    if value.get("schema_version") != 1:
        result["reason"] = "schema-version"
        return result
    if str(value.get("target", "")).upper() != worker:
        result["reason"] = "target-mismatch"
        return result
    if not str(value.get("task_id", "")).strip():
        result["reason"] = "missing-task-id"
        return result
    if str(value.get("priority", "")).lower() != "advisory":
        result["reason"] = "priority-must-be-advisory"
        return result
    if not str(value.get("next_action", "")).strip():
        result["reason"] = "missing-next-action"
        return result
    generated_at = _parse_timestamp(value.get("generated_at"))
    if generated_at is None:
        result["reason"] = "invalid-generated-at"
        return result
    age = (datetime.now(timezone.utc) - generated_at).total_seconds()
    if age > DIRECTIVE_MAX_AGE_SECONDS:
        result["reason"] = f"stale:{int(age)}s"
        return result
    if age < -300:
        result["reason"] = "generated-in-future"
        return result
    result.update(
        {
            "valid": True,
            "reason": "ok",
            "task_id": value["task_id"],
            "generated_at": value["generated_at"],
        }
    )
    return result


def _validate_directives(ring: Path, run_dir: Path) -> None:
    results = [
        _validate_directive(_directive_path(ring, worker), worker)
        for worker in ("PC", "LP")
    ]
    (run_dir / "ring-directive-validation.json").write_text(
        json.dumps(
            {
                "schema_version": 1,
                "validated_at": datetime.now(timezone.utc).isoformat(),
                "directives": results,
            },
            indent=2,
            ensure_ascii=False,
        )
        + "\n",
        encoding="utf-8",
    )


def _prompt(paths: WorktreePaths, run_dir: Path) -> str:
    generated_at = datetime.now(timezone.utc)
    expires_at = generated_at + timedelta(seconds=DIRECTIVE_MAX_AGE_SECONDS)
    return f"""You are The Ring, the cross-stack commander for R4R.
This is a fresh OpenCode session. Do not resume another transcript.

Worktrees:
- RING: {paths.ring}
- PC backend worker: {paths.pc}
- LP frontend worker: {paths.lp}

Fresh deterministic evidence for all three worktrees and the latest worker runtime
artifacts is stored under:
- {run_dir}

Read the Ring, PC and LP commit/status/diff evidence. Then read both worker-runtime
subdirectories, including progress, worker memory, latest gate-green checkpoint,
latest Codex plan/review, local understanding, CodeGraph report, gate summary and the
previous Ring directive when present. Also read `worker-request-manifest.json` and
`worker-requests/*.json`: these are event-triggered requests from the deterministic
controllers and identify what changed since the previous Ring cycle.
Identify the first current defect for PC and LP. Prefer correction before new
implementation. Read Java, Angular, tests, Python and shell only when needed.
Do not perform unbounded searches.

Update only these versioned files in the RING worktree:
- .ring-agent/global-summary.md
- .ring-agent/code-pc-review.md
- .ring-agent/code-lp-review.md
- .ring-agent/backend-frontend-handoff.md
- .ring-agent/state.json
- .opencode/current/ring/**

Also write exactly one current advisory directive for each Qwen3 worker:
- {paths.ring}/runtime/control/PC/ring-qwen3-directive.json
- {paths.ring}/runtime/control/LP/ring-qwen3-directive.json

Each directive must be valid JSON with this exact shape:
{{
  "schema_version": 1,
  "target": "PC or LP",
  "task_id": "the exact active task id from progress/evidence",
  "generated_at": "timezone-aware ISO-8601 timestamp; use {generated_at.isoformat()}",
  "expires_at": "timezone-aware ISO-8601 timestamp; use {expires_at.isoformat()}",
  "priority": "advisory",
  "summary": "short diagnosis",
  "next_action": "one focused action",
  "evidence_paths": ["exact paths supporting the decision"],
  "constraints": ["task/gate/Codex constraints that must remain true"],
  "avoid_repeating": "the last failed or wasteful approach to avoid"
}}

The exact task specification, deterministic gate and current Codex correction packet
are authoritative and override a Ring suggestion. Do not ask Qwen3 to bypass a gate,
change task scope, edit controller files, write Git history or repeat an already failed
approach. Keep each directive focused enough for one local-model pass.

Do not edit product Java or Angular. Do not edit PC or LP worktrees. Do not write Git
history. Do not install packages. Do not run find, recursive grep, git add, commit,
reset, checkout, merge, rebase, push or clean.

The PC and LP directives must each contain one focused next action, exact evidence,
paths to inspect, the exact gate and a strategy that does not repeat the last failed
approach. Detect a newly compilable REST contract and update the frontend handoff.
"""


def _command(paths: WorktreePaths, run_dir: Path) -> tuple[str, ...]:
    return (
        OPENCODE_BIN,
        "run",
        "--dir",
        str(paths.ring),
        "--agent",
        RING_AGENT,
        "--model",
        RING_MODEL,
        "--format",
        "json",
        "--auto",
        _prompt(paths, run_dir),
    )


def run_ring_loop(paths: WorktreePaths, *, once: bool = False) -> int:
    if REVIEW_INTERVAL_SECONDS < 1:
        raise ValueError("R4R_RING_REVIEW_INTERVAL_SECONDS must be positive")
    if SESSION_TIMEOUT_SECONDS < 1:
        raise ValueError("R4R_RING_SESSION_TIMEOUT_SECONDS must be positive")
    if EVENT_MIN_INTERVAL_SECONDS < 1:
        raise ValueError("R4R_RING_EVENT_MIN_INTERVAL_SECONDS must be positive")

    paths = WorktreePaths(
        require_git_worktree(paths.ring, "RING"),
        require_git_worktree(paths.pc, "PC"),
        require_git_worktree(paths.lp, "LP"),
    )
    loop_runtime = paths.ring / "runtime" / "ring-agent" / "ring"
    loop_runtime.mkdir(parents=True, exist_ok=True)
    lock_handle = (loop_runtime / "loop.lock").open("a+")
    try:
        fcntl.flock(lock_handle.fileno(), fcntl.LOCK_EX | fcntl.LOCK_NB)
    except BlockingIOError:
        print("[the-ring] cognitive loop already running")
        lock_handle.close()
        return 0

    control = RingCommandFile(paths.ring, "RING")
    control.set_state("running", "The Ring cognitive loop started")
    next_run = (
        time.monotonic()
        if RUN_IMMEDIATELY
        else time.monotonic() + REVIEW_INTERVAL_SECONDS
    )
    last_session_started = 0.0

    try:
        while True:
            request = control.poll()
            if request is not None:
                if request.command == "stop":
                    control.complete(request, "stopped", request.reason or "Stopped by operator")
                    return 0
                if request.command == "pause":
                    control.complete(request, "paused", request.reason or "Paused by operator")
                elif request.command == "restart":
                    control.complete(request, "running", request.reason or "Fresh Ring session requested")
                    next_run = time.monotonic()
                elif request.command == "continue":
                    control.complete(request, "running", request.reason or "Ring loop continued")
                    next_run = time.monotonic()

            if control.current_state() == "paused":
                control.heartbeat("paused")
                time.sleep(1)
                continue

            now = time.monotonic()
            if (
                _pending_worker_requests(paths.ring)
                and now - last_session_started >= EVENT_MIN_INTERVAL_SECONDS
            ):
                next_run = min(next_run, now)
            if now < next_run:
                control.heartbeat("running")
                time.sleep(1)
                continue

            last_session_started = time.monotonic()
            run_id = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
            run_dir = paths.ring / "runtime" / "ring-agent" / "ring" / run_id
            _write_evidence(paths, run_dir)
            consumed_requests = _consume_worker_requests(paths.ring, run_dir)
            control.set_state(
                "running",
                f"The Ring session {run_id} started; event_requests={len(consumed_requests)}",
            )

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
                _command(paths, run_dir),
                paths.ring,
                run_dir / "opencode.console.log",
                timeout_seconds=SESSION_TIMEOUT_SECONDS,
                stop_poll=stop_poll,
            )
            _validate_directives(paths.ring, run_dir)
            (run_dir / "session-result.json").write_text(
                json.dumps(
                    {
                        "run_id": run_id,
                        "exit_code": result.exit_code,
                        "duration_seconds": result.duration_seconds,
                        "stop_reason": result.stop_reason,
                        "agent": RING_AGENT,
                        "model": RING_MODEL,
                        "review_interval_seconds": REVIEW_INTERVAL_SECONDS,
                        "event_min_interval_seconds": EVENT_MIN_INTERVAL_SECONDS,
                        "consumed_worker_requests": consumed_requests,
                        "worktrees": {
                            "RING": str(paths.ring),
                            "PC": str(paths.pc),
                            "LP": str(paths.lp),
                        },
                    },
                    indent=2,
                )
                + "\n",
                encoding="utf-8",
            )

            if active_command is not None:
                if active_command.command == "stop":
                    control.complete(active_command, "stopped", "Ring session stopped and log finalized")
                    return 0
                if active_command.command == "pause":
                    control.complete(active_command, "paused", "Ring session paused and log finalized")
                else:
                    control.complete(active_command, "running", "Ring session restarted with fresh context")
                    next_run = time.monotonic()
                    continue
            elif result.stop_reason == "timeout":
                control.set_state(
                    "running",
                    f"Ring session reached {SESSION_TIMEOUT_SECONDS}s; next session will be fresh",
                )
            else:
                control.set_state("running", f"Ring session finished with exit {result.exit_code}")

            if once:
                return result.exit_code
            next_run = time.monotonic() + REVIEW_INTERVAL_SECONDS
    finally:
        fcntl.flock(lock_handle.fileno(), fcntl.LOCK_UN)
        lock_handle.close()
