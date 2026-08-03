from __future__ import annotations

from datetime import datetime, timedelta, timezone
import fcntl
import json
import os
from pathlib import Path
import shutil
import subprocess
import time
from typing import Any, Callable, Sequence

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



STAGED_OUTPUT_NAMES = (
    "state.json",
    "code-pc-review.md",
    "code-lp-review.md",
    "backend-frontend-handoff.md",
    "worker-understanding.md",
    "global-summary.md",
)
VALID_RING_ACTIONS = {
    "START",
    "CONTINUE",
    "HOLD",
    "REVIEW",
    "STOP",
    "NO_ACTION",
}
VALID_OVERALL_STATUSES = {"READY", "BLOCKED", "NO_ACTION"}


def _atomic_write_text(path: Path, content: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_name(f".{path.name}.tmp-{os.getpid()}")
    temporary.write_text(content, encoding="utf-8")
    os.replace(temporary, path)


def _decision_ledger_entry(run_id: str, state: dict[str, Any]) -> str:
    lines = [
        f"## Cycle `{run_id}` — {state['overall_status']}",
        "",
    ]
    for worker in ("PC", "LP"):
        decision = state["decisions"][worker]
        lines.extend(
            [
                f"### {worker}",
                "",
                f"- Decision: `{decision['action']}`",
                f"- Task: `{decision['task_id'] or 'NO_ACTIVE_TASK'}`",
                f"- Reason: {decision['reason']}",
                f"- Next action: {decision['next_action']}",
                f"- Avoid repeating: {decision['avoid_repeating']}",
                "- Acceptance gates:",
                *[f"  - {gate}" for gate in decision["acceptance_gates"]],
                "- Evidence:",
                *[f"  - `{path}`" for path in decision["evidence_paths"]],
                "",
            ]
        )
    lines.append("### Integration risks")
    lines.append("")
    risks = state["integration_risks"] or ["None recorded."]
    lines.extend(f"- {risk}" for risk in risks)
    lines.extend(["", "### Evidence limitations", ""])
    limitations = state["evidence_limitations"] or ["None recorded."]
    lines.extend(f"- {limitation}" for limitation in limitations)
    return "\n".join(lines).rstrip() + "\n"


def _append_decision_ledger(
    destination: Path,
    run_id: str,
    state: dict[str, Any],
) -> None:
    marker = f"## Cycle `{run_id}`"
    if destination.is_file():
        existing = destination.read_text(encoding="utf-8")
    else:
        existing = (
            "# R4R agent coordination decisions\n\n"
            "Append-only ledger generated after each validated Ring cycle.\n"
        )
    if marker in existing:
        return
    content = existing.rstrip() + "\n\n" + _decision_ledger_entry(run_id, state)
    _atomic_write_text(destination, content)


def _commit_coordination_files(
    repo: Path,
    files: Sequence[Path],
    run_id: str,
) -> dict[str, Any]:
    result: dict[str, Any] = {"status": "not-run", "commit": None, "detail": ""}
    if _git(repo, ["rev-parse", "--is-inside-work-tree"]).strip() != "true":
        result.update(status="not-git-worktree", detail="coordination files remain written")
        return result

    relative_paths = [str(path.relative_to(repo)) for path in files]
    add = subprocess.run(
        ["git", "add", "--", *relative_paths],
        cwd=repo,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        check=False,
    )
    if add.returncode != 0:
        result.update(status="failed", detail=add.stdout.strip())
        return result

    changed = subprocess.run(
        ["git", "diff", "--cached", "--quiet", "--", *relative_paths],
        cwd=repo,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        check=False,
    )
    if changed.returncode == 0:
        result.update(status="no-changes", detail="coordination documents unchanged")
        return result
    if changed.returncode != 1:
        result.update(
            status="failed",
            detail=f"git diff exited {changed.returncode}",
        )
        return result

    commit = subprocess.run(
        [
            "git",
            "-c",
            "commit.gpgSign=false",
            "commit",
            "--only",
            "-m",
            f"docs(ring): record coordination cycle {run_id}",
            "--",
            *relative_paths,
        ],
        cwd=repo,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        check=False,
    )
    if commit.returncode != 0:
        result.update(status="failed", detail=commit.stdout.strip())
        return result
    result.update(
        status="committed",
        commit=_git(repo, ["rev-parse", "HEAD"]).strip(),
        detail=commit.stdout.strip(),
    )
    return result


def _path_within(root: Path, candidate: Path) -> bool:
    try:
        candidate.relative_to(root)
    except ValueError:
        return False
    return True


def _non_empty_strings(value: Any, field: str) -> list[str]:
    if not isinstance(value, list) or not value:
        raise ValueError(f"{field} must be a non-empty list")
    result: list[str] = []
    for item in value:
        if not isinstance(item, str) or not item.strip():
            raise ValueError(f"{field} must contain only non-empty strings")
        result.append(item.strip())
    return result


def _validate_staged_state(
    state_path: Path,
    run_dir: Path,
    run_id: str,
) -> dict[str, Any]:
    try:
        value = json.loads(state_path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exception:
        raise ValueError(f"invalid state.json: {exception}") from exception
    if not isinstance(value, dict):
        raise ValueError("state.json root must be an object")
    if value.get("schema_version") != 1:
        raise ValueError("state.json schema_version must be 1")
    if value.get("run_id") != run_id:
        raise ValueError(
            f"state.json run_id must be {run_id!r}, got {value.get('run_id')!r}"
        )
    overall_status = str(value.get("overall_status", "")).upper()
    if overall_status not in VALID_OVERALL_STATUSES:
        raise ValueError(
            "state.json overall_status must be READY, BLOCKED or NO_ACTION"
        )
    decisions = value.get("decisions")
    if not isinstance(decisions, dict):
        raise ValueError("state.json decisions must be an object")

    normalized_decisions: dict[str, dict[str, Any]] = {}
    resolved_run_dir = run_dir.resolve()
    for worker in ("PC", "LP"):
        decision = decisions.get(worker)
        if not isinstance(decision, dict):
            raise ValueError(f"state.json decisions.{worker} must be an object")
        action = str(decision.get("action", "")).upper()
        if action not in VALID_RING_ACTIONS:
            raise ValueError(
                f"state.json decisions.{worker}.action is invalid: {action!r}"
            )
        raw_task_id = decision.get("task_id")
        if raw_task_id is not None and (
            not isinstance(raw_task_id, str) or not raw_task_id.strip()
        ):
            raise ValueError(
                f"state.json decisions.{worker}.task_id must be null or non-empty"
            )
        reason = str(decision.get("reason", "")).strip()
        next_action = str(decision.get("next_action", "")).strip()
        avoid_repeating = str(decision.get("avoid_repeating", "")).strip()
        if not reason:
            raise ValueError(
                f"state.json decisions.{worker}.reason must be non-empty"
            )
        if not next_action:
            raise ValueError(
                f"state.json decisions.{worker}.next_action must be non-empty"
            )
        if not avoid_repeating:
            raise ValueError(
                f"state.json decisions.{worker}.avoid_repeating must be non-empty"
            )

        acceptance_gates = _non_empty_strings(
            decision.get("acceptance_gates"),
            f"state.json decisions.{worker}.acceptance_gates",
        )
        evidence_paths = _non_empty_strings(
            decision.get("evidence_paths"),
            f"state.json decisions.{worker}.evidence_paths",
        )
        normalized_evidence: list[str] = []
        for evidence in evidence_paths:
            candidate = Path(evidence)
            if not candidate.is_absolute():
                candidate = resolved_run_dir / candidate
            candidate = candidate.resolve()
            if not _path_within(resolved_run_dir, candidate):
                raise ValueError(
                    f"state.json decisions.{worker}.evidence_paths escapes RUN_DIR: "
                    f"{evidence!r}"
                )
            if not candidate.exists():
                raise ValueError(
                    f"state.json decisions.{worker}.evidence_paths does not exist: "
                    f"{evidence!r}"
                )
            normalized_evidence.append(str(candidate))

        normalized_decisions[worker] = {
            "action": action,
            "task_id": raw_task_id.strip() if isinstance(raw_task_id, str) else None,
            "reason": reason,
            "next_action": next_action,
            "avoid_repeating": avoid_repeating,
            "acceptance_gates": acceptance_gates,
            "evidence_paths": normalized_evidence,
        }

    return {
        "schema_version": 1,
        "run_id": run_id,
        "overall_status": overall_status,
        "decisions": normalized_decisions,
        "integration_risks": [
            str(item).strip()
            for item in value.get("integration_risks", [])
            if isinstance(item, str) and item.strip()
        ],
        "evidence_limitations": [
            str(item).strip()
            for item in value.get("evidence_limitations", [])
            if isinstance(item, str) and item.strip()
        ],
    }


def _publish_staged_outputs(
    paths: WorktreePaths,
    run_dir: Path,
    run_id: str,
) -> dict[str, Any]:
    output_dir = run_dir / "output"
    result: dict[str, Any] = {
        "schema_version": 1,
        "run_id": run_id,
        "output_dir": str(output_dir),
        "published": False,
        "reason": "not-run",
        "promoted_files": {},
        "versioned_files": {},
        "coordination_commit": {"status": "not-run"},
        "directive_files": {},
    }
    missing = [
        name
        for name in STAGED_OUTPUT_NAMES
        if not (output_dir / name).is_file()
        or not (output_dir / name).read_text(encoding="utf-8").strip()
    ]
    if missing:
        result["reason"] = f"missing-or-empty: {', '.join(missing)}"
        (run_dir / "ring-output-publication.json").write_text(
            json.dumps(result, indent=2, ensure_ascii=False) + "\n",
            encoding="utf-8",
        )
        return result

    try:
        normalized_state = _validate_staged_state(
            output_dir / "state.json",
            run_dir,
            run_id,
        )
    except ValueError as exception:
        result["reason"] = str(exception)
        (run_dir / "ring-output-publication.json").write_text(
            json.dumps(result, indent=2, ensure_ascii=False) + "\n",
            encoding="utf-8",
        )
        return result

    operational_destinations = {
        "state.json": paths.ring / ".ring-agent" / "state.json",
        "code-pc-review.md": paths.ring / ".ring-agent" / "code-pc-review.md",
        "code-lp-review.md": paths.ring / ".ring-agent" / "code-lp-review.md",
        "backend-frontend-handoff.md": (
            paths.ring / ".ring-agent" / "backend-frontend-handoff.md"
        ),
        "global-summary.md": paths.ring / ".ring-agent" / "global-summary.md",
        "worker-understanding.md": (
            paths.ring / ".opencode" / "current" / "ring" / "worker-understanding.md"
        ),
    }
    for name, destination in operational_destinations.items():
        if name == "state.json":
            content = json.dumps(
                normalized_state,
                indent=2,
                ensure_ascii=False,
            ) + "\n"
        else:
            content = (output_dir / name).read_text(encoding="utf-8")
        _atomic_write_text(destination, content)
        result["promoted_files"][name] = str(destination)

    coordination_dir = paths.ring / "docs" / "agent-coordination"
    versioned_destinations = {
        "code-pc-review.md": coordination_dir / "PC-WORKER.md",
        "code-lp-review.md": coordination_dir / "LAPTOP-WORKER.md",
        "backend-frontend-handoff.md": coordination_dir / "RING-HANDOFF.md",
        "worker-understanding.md": coordination_dir / "WORKER-UNDERSTANDING.md",
        "global-summary.md": coordination_dir / "CURRENT-STATE.md",
    }
    for name, destination in versioned_destinations.items():
        _atomic_write_text(
            destination,
            (output_dir / name).read_text(encoding="utf-8"),
        )
        result["versioned_files"][name] = str(destination)

    decisions_path = coordination_dir / "DECISIONS.md"
    _append_decision_ledger(decisions_path, run_id, normalized_state)
    result["versioned_files"]["decisions"] = str(decisions_path)
    commit_result = _commit_coordination_files(
        paths.ring,
        [*versioned_destinations.values(), decisions_path],
        run_id,
    )
    result["coordination_commit"] = commit_result
    if commit_result["status"] == "failed":
        result["reason"] = "coordination-commit-failed"
        (run_dir / "ring-output-publication.json").write_text(
            json.dumps(result, indent=2, ensure_ascii=False) + "\n",
            encoding="utf-8",
        )
        return result

    generated_at = datetime.now(timezone.utc)
    expires_at = generated_at + timedelta(seconds=DIRECTIVE_MAX_AGE_SECONDS)
    for worker in ("PC", "LP"):
        decision = normalized_state["decisions"][worker]
        directive = {
            "schema_version": 1,
            "target": worker,
            "task_id": decision["task_id"] or "NO_ACTIVE_TASK",
            "generated_at": generated_at.isoformat(),
            "expires_at": expires_at.isoformat(),
            "priority": "advisory",
            "summary": decision["reason"],
            "next_action": decision["next_action"],
            "evidence_paths": decision["evidence_paths"],
            "constraints": decision["acceptance_gates"],
            "avoid_repeating": decision["avoid_repeating"],
        }
        destination = _directive_path(paths.ring, worker)
        _atomic_write_text(
            destination,
            json.dumps(directive, indent=2, ensure_ascii=False) + "\n",
        )
        result["directive_files"][worker] = str(destination)

    result["published"] = True
    result["reason"] = "ok"
    (run_dir / "ring-output-publication.json").write_text(
        json.dumps(result, indent=2, ensure_ascii=False) + "\n",
        encoding="utf-8",
    )
    return result


def _prompt(paths: WorktreePaths, run_dir: Path, run_id: str) -> str:
    output_dir = run_dir / "output"
    return f"""You are The Ring, the cross-stack commander for R4R.
This is a fresh OpenCode session. Do not resume another transcript.

The deterministic supervisor has prepared the primary evidence and repository context:
- RUN_DIR: {run_dir}
- OUTPUT_DIR: {output_dir}
- RUN_ID: {run_id}
- RING_WORKTREE: {paths.ring}

Read the bounded evidence below RUN_DIR first. Do not read `opencode.console.log` and
do not perform unbounded searches. You may then read and modify any file inside the
current RING_WORKTREE when a current evidence-backed correction is necessary. Never
read or edit the live PC or LP worktrees directly.

Repository preservation rules:
- never delete, unlink, remove, rename or move an existing file or directory;
- never truncate an existing file to empty or replace useful content with a placeholder;
- read before editing and preserve unrelated content;
- create new files only in an appropriate existing directory;
- keep the repository root limited to canonical project entry files;
- never edit secrets, credentials, private keys, tokens, `.env` files or PID/lock files;
- never write Git history, install packages or run shell commands.

Review the Ring, PC and LP commit/status/diff evidence and both worker-runtime
subdirectories. Prefer the newest authoritative evidence inside RUN_DIR: progress,
worker memory, checkpoint, Codex plan/review, correction packet, local understanding,
CodeGraph report, gate summary and prior Ring directive.

Identify the first current defect for PC and LP. Prefer correction before new
implementation. Do not claim a test passed, a task completed, Codex accepted or a
worker started unless direct evidence proves it.

Write these six staged files below OUTPUT_DIR on every successful cycle:
- {output_dir}/state.json
- {output_dir}/code-pc-review.md
- {output_dir}/code-lp-review.md
- {output_dir}/backend-frontend-handoff.md
- {output_dir}/worker-understanding.md
- {output_dir}/global-summary.md

You may additionally make bounded, non-destructive edits inside RING_WORKTREE. Document
those edits and their evidence in the staged summaries. The Python supervisor validates
the staged files, publishes current snapshots plus an append-only decision ledger below
`docs/agent-coordination/`, commits only those coordination documents, and then creates
the PC/LP advisory directive JSON files. Do not write `runtime/control/**` yourself
during the staged review.

state.json must be valid JSON with this exact structure:
{{
  "schema_version": 1,
  "run_id": "{run_id}",
  "overall_status": "READY | BLOCKED | NO_ACTION",
  "decisions": {{
    "PC": {{
      "action": "START | CONTINUE | HOLD | REVIEW | STOP | NO_ACTION",
      "task_id": "exact active task id or null",
      "reason": "non-empty evidence-grounded diagnosis",
      "next_action": "one focused action for one worker pass",
      "evidence_paths": [
        "one or more existing paths inside RUN_DIR supporting the decision"
      ],
      "acceptance_gates": [
        "one or more exact task, gate or Codex constraints"
      ],
      "avoid_repeating": "the last failed or wasteful approach to avoid"
    }},
    "LP": {{
      "action": "START | CONTINUE | HOLD | REVIEW | STOP | NO_ACTION",
      "task_id": "exact active task id or null",
      "reason": "non-empty evidence-grounded diagnosis",
      "next_action": "one focused action for one worker pass",
      "evidence_paths": [
        "one or more existing paths inside RUN_DIR supporting the decision"
      ],
      "acceptance_gates": [
        "one or more exact task, gate or Codex constraints"
      ],
      "avoid_repeating": "the last failed or wasteful approach to avoid"
    }}
  }},
  "integration_risks": ["zero or more evidence-grounded risks"],
  "evidence_limitations": ["zero or more explicit limitations"]
}}

The exact task specification, deterministic gate and current Codex correction packet
remain authoritative for PC and LP. Do not ask workers to bypass a gate, change task
scope, write Git history or repeat an already failed approach.

Finish after the six staged files and any explicitly justified, non-destructive Ring
worktree edits have been written.
"""


def _command(
    paths: WorktreePaths,
    run_dir: Path,
    run_id: str,
) -> tuple[str, ...]:
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
        _prompt(paths, run_dir, run_id),
    )


def run_ring_loop(
    paths: WorktreePaths,
    *,
    once: bool = False,
    stop_requested: Callable[[], bool] | None = None,
) -> int:
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
            if stop_requested is not None and stop_requested():
                control.set_state("stopped", "The Ring stopped by system signal")
                return 0

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
                if stop_requested is not None and stop_requested():
                    control.set_state("stopped", "The Ring stopped by system signal")
                    return 0
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
                if stop_requested is not None and stop_requested():
                    return "system-stop"
                request = control.poll()
                if request is None or request.command == "continue":
                    if request is not None:
                        control.complete(request, "running", "Already running")
                    return ""
                active_command = request
                return request.command

            result = run_streamed(
                _command(paths, run_dir, run_id),
                paths.ring,
                run_dir / "opencode.console.log",
                timeout_seconds=SESSION_TIMEOUT_SECONDS,
                stop_poll=stop_poll,
            )
            publication = (
                _publish_staged_outputs(paths, run_dir, run_id)
                if result.exit_code == 0 and not result.stop_reason
                else {
                    "schema_version": 1,
                    "run_id": run_id,
                    "published": False,
                    "reason": (
                        f"session-not-publishable: exit={result.exit_code} "
                        f"stop_reason={result.stop_reason!r}"
                    ),
                }
            )
            if not (run_dir / "ring-output-publication.json").exists():
                (run_dir / "ring-output-publication.json").write_text(
                    json.dumps(publication, indent=2, ensure_ascii=False) + "\n",
                    encoding="utf-8",
                )
            _validate_directives(paths.ring, run_dir)
            effective_exit_code = result.exit_code
            if (
                effective_exit_code == 0
                and not result.stop_reason
                and not publication.get("published", False)
            ):
                effective_exit_code = 65

            (run_dir / "session-result.json").write_text(
                json.dumps(
                    {
                        "run_id": run_id,
                        "exit_code": result.exit_code,
                        "effective_exit_code": effective_exit_code,
                        "duration_seconds": result.duration_seconds,
                        "publication": publication,
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

            if result.stop_reason == "system-stop":
                control.set_state("stopped", "Ring session stopped by system signal")
                return 0

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
            elif not publication.get("published", False):
                control.set_state(
                    "running",
                    "Ring session output rejected: "
                    f"{publication.get('reason', 'unknown publication failure')}",
                )
            else:
                control.set_state(
                    "running",
                    f"Ring session finished with exit {effective_exit_code}",
                )

            if once:
                return effective_exit_code
            next_run = time.monotonic() + REVIEW_INTERVAL_SECONDS
    finally:
        fcntl.flock(lock_handle.fileno(), fcntl.LOCK_UN)
        lock_handle.close()
