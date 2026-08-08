from __future__ import annotations

from datetime import datetime, timedelta, timezone
import fcntl
from fnmatch import fnmatchcase
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import time
from types import SimpleNamespace
from typing import Any, Callable, Sequence

from .assignment import (
    global_progress_path,
    load_global_progress,
    migrate_legacy_acceptances,
    parse_utc_timestamp,
    validate_assignment,
)
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
    "openai/gpt-5.6-luna",
)
RING_FALLBACK_MODEL = os.environ.get(
    "R4R_RING_FALLBACK_MODEL",
    "openai/gpt-5.3-codex",
)
RING_VARIANT = os.environ.get("R4R_RING_VARIANT", "low")
RING_FALLBACK_VARIANT = os.environ.get("R4R_RING_FALLBACK_VARIANT", "low")
ESCALATION_AGENT = os.environ.get("R4R_ESCALATION_AGENT", "r4r-escalation")
ESCALATION_MODEL = os.environ.get(
    "R4R_ESCALATION_MODEL",
    "openai/gpt-5.3-codex",
)
ESCALATION_VARIANT = os.environ.get("R4R_ESCALATION_VARIANT", "high")
REVIEW_INTERVAL_SECONDS = int(
    os.environ.get("R4R_RING_REVIEW_INTERVAL_SECONDS", "763")
)
SESSION_TIMEOUT_SECONDS = int(
    os.environ.get("R4R_RING_SESSION_TIMEOUT_SECONDS", str(90 * 60))
)
FIRST_OUTPUT_TIMEOUT_SECONDS = int(
    os.environ.get("R4R_RING_FIRST_OUTPUT_TIMEOUT_SECONDS", "120")
)
RUN_IMMEDIATELY = os.environ.get("R4R_RING_RUN_IMMEDIATELY", "true").lower() == "true"
OPENCODE_BIN = os.environ.get("R4R_OPENCODE_BIN", "opencode")
DIRECTIVE_MAX_AGE_SECONDS = int(
    os.environ.get("R4R_RING_DIRECTIVE_MAX_AGE_SECONDS", "10800")
)
EVENT_MIN_INTERVAL_SECONDS = int(
    os.environ.get("R4R_RING_EVENT_MIN_INTERVAL_SECONDS", "763")
)
FAILURE_RETRY_BASE_SECONDS = int(
    os.environ.get("R4R_RING_FAILURE_RETRY_BASE_SECONDS", "30")
)
FAILURE_RETRY_MAX_SECONDS = int(
    os.environ.get("R4R_RING_FAILURE_RETRY_MAX_SECONDS", "300")
)
WHITESPACE_REPAIR_POLICY_VERSION = 2
INTEGRATION_SYNC_ENABLED = (
    os.environ.get("R4R_AGENT_INTEGRATION_SYNC", "true").lower() == "true"
)
GIT_LOCK_PATH = Path(
    os.environ.get(
        "R4R_GIT_LOCK",
        str(Path.home() / "Desarrollo" / ".r4r-runtime" / "git.lock"),
    )
).expanduser()


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


def _git_diff_check(repo: Path) -> tuple[str, int]:
    """Return combined unstaged and staged whitespace diagnostics."""
    outputs: list[str] = []
    failed = False
    for args in (("diff", "--check"), ("diff", "--cached", "--check")):
        result = subprocess.run(
            ["git", *args],
            cwd=repo,
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            check=False,
        )
        outputs.append(result.stdout)
        failed = failed or result.returncode != 0
    return "".join(outputs), 1 if failed else 0


def _failure_retry_delay(consecutive_failures: int) -> int:
    """Return bounded exponential retry delay for unsuccessful Ring sessions."""
    if consecutive_failures < 1:
        raise ValueError("consecutive_failures must be positive")
    exponent = min(consecutive_failures - 1, 20)
    return min(
        FAILURE_RETRY_MAX_SECONDS,
        FAILURE_RETRY_BASE_SECONDS * (2**exponent),
    )


def _integration_sync(repo: Path, phase: str) -> dict[str, Any]:
    result: dict[str, Any] = {"status": "disabled", "phase": phase}
    if not INTEGRATION_SYNC_ENABLED:
        return result
    script = Path(
        os.environ.get(
            "R4R_AGENT_INTEGRATION_SYNC_SCRIPT",
            str(repo / "scripts" / "agent-integration-sync.sh"),
        )
    ).expanduser()
    if not script.is_file():
        return {
            "status": "failed",
            "phase": phase,
            "detail": f"integration sync script not found: {script}",
        }
    completed = subprocess.run(
        [str(script), phase],
        cwd=repo,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        check=False,
    )
    return {
        "status": "ok" if completed.returncode == 0 else "failed",
        "phase": phase,
        "exit_code": completed.returncode,
        "detail": completed.stdout[-4000:].strip(),
    }


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
    diff_check, _ = _git_diff_check(repo)
    (run_dir / f"{prefix}-git-diff-check.txt").write_text(
        diff_check, encoding="utf-8"
    )
    (run_dir / f"{prefix}-git-diff-cached-check.txt").write_text(
        _git(repo, ["diff", "--cached", "--check"]), encoding="utf-8"
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
    current = repo / ".opencode" / f"progress.{worker.lower()}.json"
    if current.is_file():
        return current
    legacy = "progress.backend.json" if worker == "PC" else "progress.frontend.json"
    return repo / ".opencode" / legacy


def _worker_memory_path(worker: str, repo: Path) -> Path:
    current = repo / ".opencode" / f"memory.{worker.lower()}.md"
    if current.is_file():
        return current
    legacy = "memory.backend.md" if worker == "PC" else "memory.frontend.md"
    return repo / ".opencode" / legacy


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
        "escalation_extra_instructions": _copy_snapshot_file(
            worker_repo
            / "runtime"
            / "control"
            / worker
            / "ring-extra-instructions.md",
            snapshot_dir / "ring-extra-instructions.md",
        ),
        "ring_assignment": _copy_snapshot_file(
            ring_repo
            / "runtime"
            / "control"
            / worker
            / "assignment.json",
            snapshot_dir / "previous-assignment.json",
        ),
    }

    if latest_run is not None:
        sources["latest_run"] = str(latest_run)
        selected_patterns = {
            "controller_state": "state.json",
            "escalation_review": "escalation-review.json",
            "escalation_plan": "escalation-plan.json",
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
    _copy_snapshot_file(
        global_progress_path(paths.ring),
        run_dir / "global-progress.json",
    )
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
    return ring / "runtime" / "control" / worker / "assignment.json"


def _parse_timestamp(value: Any) -> datetime | None:
    return parse_utc_timestamp(value)


def _validate_directive(
    path: Path,
    worker: str,
    ring: Path | None = None,
) -> dict[str, Any]:
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
    if ring is not None:
        try:
            plan = json.loads(
                (ring / ".opencode" / "task-plan.json").read_text(encoding="utf-8")
            )
            tasks = {
                item["id"]: SimpleNamespace(
                    allowed_paths=tuple(item["allowed_paths"]),
                    depends_on=tuple(item.get("depends_on", [])),
                )
                for item in plan["tasks"]
            }
            accepted = tuple(
                load_global_progress(global_progress_path(ring))["accepted"]
            )
            value = validate_assignment(
                value,
                worker=worker,
                tasks=tasks,
                accepted_task_ids=accepted,
                max_age_seconds=DIRECTIVE_MAX_AGE_SECONDS,
                require_active=False,
            )
        except (KeyError, OSError, TypeError, ValueError) as exception:
            result["reason"] = str(exception)
            return result
    else:
        if value.get("schema_version") != 1:
            result["reason"] = "schema-version"
            return result
        if str(value.get("target", "")).upper() != worker:
            result["reason"] = "target-mismatch"
            return result
        if str(value.get("action", "")).upper() not in VALID_RING_ACTIONS:
            result["reason"] = "invalid-action"
            return result
        if not str(value.get("assignment_id", "")).strip():
            result["reason"] = "missing-assignment-id"
            return result
        if _parse_timestamp(value.get("generated_at")) is None:
            result["reason"] = "invalid-generated-at"
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
        _validate_directive(_directive_path(ring, worker), worker, ring)
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
    "fullstack-handoff.md",
    "worker-understanding.md",
    "global-summary.md",
)


def _ring_fallback_trigger(result: Any, output_dir: Path) -> str | None:
    """Explain why a fresh fallback session is needed, if at all."""
    if result.stop_reason:
        return (
            str(result.stop_reason)
            if result.stop_reason in {"timeout", "first_output_timeout"}
            else None
        )
    if result.exit_code != 0:
        return f"exit-code-{result.exit_code}"
    missing = [
        name
        for name in STAGED_OUTPUT_NAMES
        if not (output_dir / name).is_file()
        or not (output_dir / name).read_text(encoding="utf-8").strip()
    ]
    if missing:
        return f"missing-or-empty: {', '.join(missing)}"
    return None


VALID_RING_ACTIONS = {
    "START",
    "CONTINUE",
    "RETRY_AUTHORIZED",
    "HOLD",
    "STOP",
    "NO_ACTION",
}
VALID_RING_DRAFT_ACTIONS = VALID_RING_ACTIONS | {"ESCALATE"}
VALID_OVERALL_STATUSES = {"READY", "BLOCKED", "NO_ACTION"}
ACTIVE_DISPATCH_ACTIONS = {"START", "CONTINUE", "RETRY_AUTHORIZED"}


def _atomic_write_text(path: Path, content: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_name(f".{path.name}.tmp-{os.getpid()}")
    temporary.write_text(content, encoding="utf-8")
    os.replace(temporary, path)


def _normalized_semantic_value(value: Any) -> Any:
    """Normalize ordering and whitespace before deciding whether state changed."""
    if isinstance(value, dict):
        return {
            key: _normalized_semantic_value(item)
            for key, item in sorted(value.items())
        }
    if isinstance(value, list):
        normalized = [_normalized_semantic_value(item) for item in value]
        return sorted(
            normalized,
            key=lambda item: json.dumps(
                item,
                ensure_ascii=False,
                sort_keys=True,
                separators=(",", ":"),
            ),
        )
    if isinstance(value, str):
        return " ".join(value.split())
    return value


def _semantic_fingerprint(value: Any) -> str:
    payload = json.dumps(
        _normalized_semantic_value(value),
        ensure_ascii=False,
        sort_keys=True,
        separators=(",", ":"),
    ).encode("utf-8")
    return hashlib.sha256(payload).hexdigest()


def _decision_payload(decision: dict[str, Any]) -> dict[str, Any]:
    return {
        key: value
        for key, value in decision.items()
        if key not in {"evidence_path", "evidence_paths"}
    }


def _coordination_fingerprint(state: dict[str, Any]) -> str:
    return _semantic_fingerprint(
        {
            "overall_status": state["overall_status"],
            "decisions": {
                worker: _decision_payload(state["decisions"][worker])
                for worker in ("PC", "LP")
            },
            "integration_risks": state["integration_risks"],
            "evidence_limitations": state["evidence_limitations"],
        }
    )


def _last_coordination_fingerprint(path: Path) -> str | None:
    if not path.is_file():
        return None
    try:
        content = path.read_text(encoding="utf-8")
    except OSError:
        return None
    matches = re.findall(
        r"^- Decision fingerprint: `([0-9a-f]{64})`$",
        content,
        flags=re.MULTILINE,
    )
    return matches[-1] if matches else None


def _decision_ledger_entry(
    run_id: str,
    state: dict[str, Any],
    fingerprint: str,
) -> str:
    lines = [
        f"## Cycle `{run_id}` — {state['overall_status']}",
        "",
        f"- Decision fingerprint: `{fingerprint}`",
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
) -> bool:
    fingerprint = _coordination_fingerprint(state)
    if destination.is_file():
        existing = destination.read_text(encoding="utf-8")
    else:
        existing = (
            "# R4R agent coordination decisions\n\n"
            "Append-only ledger generated after each validated Ring cycle.\n"
        )
    if _last_coordination_fingerprint(destination) == fingerprint:
        return False
    content = existing.rstrip() + "\n\n" + _decision_ledger_entry(
        run_id,
        state,
        fingerprint,
    )
    _atomic_write_text(destination, content)
    return True


def _commit_coordination_files_locked(
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
            "-c",
            f"user.name={os.environ.get('R4R_RING_GIT_AUTHOR_NAME', 'GermanGPT Ring Agent')}",
            "-c",
            f"user.email={os.environ.get('R4R_RING_GIT_AUTHOR_EMAIL', 'germanux@gmail.com')}",
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


def _commit_coordination_files(
    repo: Path,
    files: Sequence[Path],
    run_id: str,
) -> dict[str, Any]:
    GIT_LOCK_PATH.parent.mkdir(parents=True, exist_ok=True)
    with GIT_LOCK_PATH.open("a+") as lock_handle:
        fcntl.flock(lock_handle.fileno(), fcntl.LOCK_EX)
        try:
            return _commit_coordination_files_locked(repo, files, run_id)
        finally:
            fcntl.flock(lock_handle.fileno(), fcntl.LOCK_UN)


def _path_within(root: Path, candidate: Path) -> bool:
    try:
        candidate.relative_to(root)
    except ValueError:
        return False
    return True


def _non_empty_strings(
    value: Any,
    field: str,
    *,
    non_empty: bool = True,
) -> list[str]:
    if not isinstance(value, list) or (non_empty and not value):
        raise ValueError(f"{field} must be a non-empty list")
    result: list[str] = []
    for item in value:
        if not isinstance(item, str) or not item.strip():
            raise ValueError(f"{field} must contain only non-empty strings")
        result.append(item.strip())
    return result


def _safe_slug(value: str, *, fallback: str) -> str:
    slug = re.sub(r"[^a-z0-9]+", "-", value.lower()).strip("-")
    return slug[:80] or fallback


def _scope_anchor(pattern: str) -> tuple[str, ...]:
    raw = pattern.strip().replace("\\", "/")
    if raw.startswith("/"):
        raise ValueError(f"write_scope must be repository-relative: {pattern!r}")
    normalized = raw.strip("/")
    parts = tuple(part for part in normalized.split("/") if part)
    if not parts or any(part == ".." for part in parts):
        raise ValueError(f"invalid write_scope pattern: {pattern!r}")
    anchor: list[str] = []
    for part in parts:
        if any(marker in part for marker in ("*", "?", "[")):
            break
        anchor.append(part)
    return tuple(anchor)


def _scopes_overlap(left: str, right: str) -> bool:
    left_normalized = left.strip().replace("\\", "/").strip("/")
    right_normalized = right.strip().replace("\\", "/").strip("/")
    left_glob = any(marker in left_normalized for marker in ("*", "?", "["))
    right_glob = any(marker in right_normalized for marker in ("*", "?", "["))
    if not left_glob and not right_glob:
        return left_normalized == right_normalized
    left_anchor = _scope_anchor(left_normalized)
    right_anchor = _scope_anchor(right_normalized)
    shortest = min(len(left_anchor), len(right_anchor))
    return left_anchor[:shortest] == right_anchor[:shortest]


def _load_task_assignments(
    ring: Path,
    state: dict[str, Any],
) -> dict[str, dict[str, Any]]:
    config_path = ring / "config" / "r4r-agents.json"
    try:
        config = json.loads(config_path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exception:
        raise ValueError(f"invalid agent configuration: {exception}") from exception
    agents = config.get("agents")
    if not isinstance(agents, dict):
        raise ValueError("config/r4r-agents.json agents must be an object")

    global_progress = load_global_progress(global_progress_path(ring))
    globally_accepted = set(global_progress["accepted"])
    assignments: dict[str, dict[str, Any]] = {}
    for worker in ("PC", "LP"):
        agent = agents.get(worker)
        if not isinstance(agent, dict):
            raise ValueError(f"config/r4r-agents.json agents.{worker} is missing")
        plan_name = agent.get("plan")
        if not isinstance(plan_name, str) or not plan_name.strip():
            raise ValueError(
                f"config/r4r-agents.json agents.{worker}.plan must be non-empty"
            )
        branch = agent.get("branch")
        if (
            not isinstance(branch, str)
            or not branch.startswith("agent/")
            or not branch.removeprefix("agent/").strip()
        ):
            raise ValueError(
                f"config/r4r-agents.json agents.{worker}.branch must start with agent/"
            )
        plan_path = (ring / plan_name).resolve()
        if not _path_within(ring.resolve(), plan_path):
            raise ValueError(f"agents.{worker}.plan escapes the Ring worktree")
        try:
            plan = json.loads(plan_path.read_text(encoding="utf-8"))
        except (OSError, json.JSONDecodeError) as exception:
            raise ValueError(f"invalid {plan_name}: {exception}") from exception
        tasks = plan.get("tasks")
        if not isinstance(tasks, list):
            raise ValueError(f"{plan_name} tasks must be a list")
        tasks_by_id: dict[str, dict[str, Any]] = {}
        for task in tasks:
            if not isinstance(task, dict) or not isinstance(task.get("id"), str):
                raise ValueError(f"{plan_name} contains a task without a valid id")
            task_id_in_plan = task["id"].strip()
            if not task_id_in_plan or task_id_in_plan in tasks_by_id:
                raise ValueError(
                    f"{plan_name} contains an empty or duplicate task id: "
                    f"{task.get('id')!r}"
                )
            tasks_by_id[task_id_in_plan] = task

        decision = state["decisions"][worker]
        task_id = decision["task_id"]
        if task_id is None:
            if decision["action"] in ACTIVE_DISPATCH_ACTIONS:
                raise ValueError(
                    f"state.json decisions.{worker} action {decision['action']} "
                    "requires a task_id"
                )
            write_scope: list[str] = []
        else:
            task = tasks_by_id.get(task_id)
            if not isinstance(task, dict):
                raise ValueError(
                    f"state.json decisions.{worker}.task_id {task_id!r} "
                    f"is not declared in {plan_name}"
                )
            write_scope = _non_empty_strings(
                task.get("allowed_paths"),
                f"{plan_name} task {task_id}.allowed_paths",
            )
            for pattern in write_scope:
                _scope_anchor(pattern)
            if decision["action"] in ACTIVE_DISPATCH_ACTIONS:
                if task_id in globally_accepted:
                    raise ValueError(
                        f"state.json assigns globally accepted task {task_id} to {worker}"
                    )
                dependencies = _non_empty_strings(
                    task.get("depends_on", []),
                    f"{plan_name} task {task_id}.depends_on",
                    non_empty=False,
                )
                missing_dependencies = sorted(
                    set(dependencies).difference(globally_accepted)
                )
                if missing_dependencies:
                    raise ValueError(
                        f"state.json assigns task {task_id} with unmet dependencies: "
                        f"{missing_dependencies}"
                    )

        assignments[worker] = {
            "agent_id": str(agent.get("agentId", worker.lower())).strip(),
            "assigned_agent": str(agent.get("agentId", worker.lower())).strip(),
            "branch": branch,
            "model": str(agent.get("model", "unknown-model")).strip(),
            "task_id": task_id,
            "write_scope": write_scope,
            "active": decision["action"] in ACTIVE_DISPATCH_ACTIONS,
        }

    active_workers = [
        worker for worker in ("PC", "LP") if assignments[worker]["active"]
    ]
    if len(active_workers) == 2:
        left_task = assignments[active_workers[0]]["task_id"]
        right_task = assignments[active_workers[1]]["task_id"]
        if left_task == right_task:
            raise ValueError(
                f"duplicate active assignment for canonical task {left_task}"
            )
    for index, left_worker in enumerate(active_workers):
        for right_worker in active_workers[index + 1 :]:
            left = assignments[left_worker]
            right = assignments[right_worker]
            for left_pattern in left["write_scope"]:
                for right_pattern in right["write_scope"]:
                    if _scopes_overlap(left_pattern, right_pattern):
                        raise ValueError(
                            "overlapping write_scope: "
                            f"{left_worker}/{left['task_id']}:{left_pattern} conflicts "
                            f"with {right_worker}/{right['task_id']}:{right_pattern}"
                        )
    return assignments


def _attempt_evidence_content(
    run_id: str,
    worker: str,
    decision: dict[str, Any],
) -> str:
    fingerprint = _semantic_fingerprint(
        {"worker": worker, "decision": _decision_payload(decision)}
    )
    lines = [
        f"# Ring evidence: {decision['task_id']}",
        "",
        f"- Run: `{run_id}`",
        f"- Decision fingerprint: `{fingerprint}`",
        f"- Worker: `{worker}`",
        f"- Assigned agent: `{decision['assigned_agent']}`",
        f"- Model: `{decision['model']}`",
        f"- Branch: `{decision['branch']}`",
        f"- Action: `{decision['action']}`",
        f"- Task: `{decision['task_id']}`",
        f"- Evidence path: `{decision['evidence_path']}`",
        "- Write scope:",
        *[f"  - `{pattern}`" for pattern in decision["write_scope"]],
        "",
        "## Decision",
        "",
        decision["reason"],
        "",
        "## Next action",
        "",
        decision["next_action"],
        "",
        "## Acceptance gates",
        "",
        *[f"- {gate}" for gate in decision["acceptance_gates"]],
        "",
        "## Runtime sources",
        "",
        *[f"- `{path}`" for path in decision["evidence_paths"]],
        "",
    ]
    return "\n".join(lines)


def _publish_task_evidence(
    ring: Path,
    run_id: str,
    worker: str,
    decision: dict[str, Any],
) -> Path | None:
    task_id = decision["task_id"]
    if task_id is None:
        return None
    task_dir = ring / ".ring-agent" / "evidence" / _safe_slug(
        task_id,
        fallback="unknown-task",
    )
    author = _safe_slug(decision["assigned_agent"], fallback=worker.lower())
    existing = sorted(task_dir.glob(f"{author}-attempt-*.md"))
    fingerprint = _semantic_fingerprint(
        {"worker": worker, "decision": _decision_payload(decision)}
    )
    marker = f"- Decision fingerprint: `{fingerprint}`"
    highest_attempt = 0
    latest_path: Path | None = None
    for path in existing:
        match = re.fullmatch(
            rf"{re.escape(author)}-attempt-(\d+)\.md",
            path.name,
        )
        if match:
            attempt = int(match.group(1))
            if attempt > highest_attempt:
                highest_attempt = attempt
                latest_path = path
    if latest_path is not None:
        try:
            if marker in latest_path.read_text(encoding="utf-8"):
                decision["evidence_path"] = latest_path.relative_to(ring).as_posix()
                return latest_path
        except OSError:
            pass
    destination = task_dir / f"{author}-attempt-{highest_attempt + 1:02d}.md"
    decision["evidence_path"] = destination.relative_to(ring).as_posix()
    _atomic_write_text(
        destination,
        _attempt_evidence_content(run_id, worker, decision),
    )
    return destination


def _validate_staged_state(
    state_path: Path,
    run_dir: Path,
    run_id: str,
    ring: Path,
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

    normalized_state = {
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
    assignments = _load_task_assignments(ring, normalized_state)
    for worker in ("PC", "LP"):
        normalized_state["decisions"][worker].update(assignments[worker])
    return normalized_state


def _recovery_grant_available(item: dict[str, Any]) -> bool:
    grants_total = int(item.get("recovery_grants_total") or 0)
    consumed_policy_version = int(item.get("recovery_repair_policy_version") or 0)
    legacy_v1_grant = (
        grants_total == 1
        and bool(item.get("recovery_authorization_consumed"))
        and consumed_policy_version < WHITESPACE_REPAIR_POLICY_VERSION
    )
    return grants_total < 1 or legacy_v1_grant


def _inside_scope(candidate: str, scopes: Sequence[str]) -> bool:
    for raw_scope in scopes:
        scope = str(raw_scope).strip().rstrip("/")
        if candidate == scope or candidate.startswith(scope + "/"):
            return True
        if fnmatchcase(candidate, scope):
            return True
    return False


def _whitespace_recovery_candidate(
    *,
    worker: str,
    repository: Path,
    run_dir: Path,
    task_id: str,
    write_scope: Sequence[str],
    progress_item: dict[str, Any],
) -> dict[str, Any] | None:
    if progress_item.get("status") != "BLOCKED":
        return None
    if not _recovery_grant_available(progress_item):
        return None

    evidence_path = run_dir / f"{worker.lower()}-git-diff-check.txt"
    diagnostics, diff_check_exit = _git_diff_check(repository)
    evidence_path.write_text(diagnostics, encoding="utf-8")
    findings = re.findall(r"^(.+?):\d+: (.+)$", diagnostics, re.MULTILINE)
    if (
        diff_check_exit == 0
        or not findings
        or any(message != "trailing whitespace." for _, message in findings)
    ):
        return None
    candidates = sorted({candidate for candidate, _ in findings})
    if not all(_inside_scope(candidate, write_scope) for candidate in candidates):
        return None
    return {
        "worker": worker,
        "task_id": task_id,
        "write_scope": list(write_scope),
        "evidence_path": str(evidence_path),
        "action": "RETRY_AUTHORIZED",
        "reason": (
            "Deterministic recovery policy found trailing whitespace only inside "
            "the authorized write scope of the blocked task."
        ),
        "next_action": (
            "Consume one recovery grant, remove scoped trailing whitespace, and "
            "rerun the exact task gate once."
        ),
        "avoid_repeating": (
            "Do not reset the durable attempt counter or issue another grant at "
            "the same or a newer recovery policy version."
        ),
        "recovery_policy_version": WHITESPACE_REPAIR_POLICY_VERSION,
    }


def _authorize_bounded_whitespace_recovery(
    paths: WorktreePaths,
    run_dir: Path,
    state: dict[str, Any],
) -> list[str]:
    """Upgrade model decisions when a blocked task has one safe repair."""
    authorized: list[str] = []
    repositories = {"PC": paths.pc, "LP": paths.lp}
    for worker in ("PC", "LP"):
        decision = state["decisions"][worker]
        task_id = str(decision.get("task_id") or "")
        if not task_id:
            continue
        try:
            progress = json.loads(
                _worker_progress_path(worker, repositories[worker]).read_text(
                    encoding="utf-8"
                )
            )
        except (OSError, json.JSONDecodeError):
            continue
        item = next(
            (
                value
                for value in progress.get("tasks", [])
                if isinstance(value, dict) and value.get("id") == task_id
            ),
            None,
        )
        if not isinstance(item, dict):
            continue
        candidate = _whitespace_recovery_candidate(
            worker=worker,
            repository=repositories[worker],
            run_dir=run_dir,
            task_id=task_id,
            write_scope=decision["write_scope"],
            progress_item=item,
        )
        if candidate is None:
            continue
        for key in (
            "action",
            "reason",
            "next_action",
            "avoid_repeating",
            "recovery_policy_version",
        ):
            decision[key] = candidate[key]
        authorized.append(worker)
    return authorized


def _dispatch_recovery_assignments(
    paths: WorktreePaths,
    run_dir: Path,
    run_id: str,
) -> dict[str, Any]:
    """Restore only work already selected by Ring or one safe blocked repair.

    This preflight never scans the plan for new work. It can refresh an expired
    active assignment for the same unfinished task, or create one one-shot
    whitespace repair for the task recorded as blocked in worker progress.
    """
    result: dict[str, Any] = {
        "schema_version": 1,
        "run_id": run_id,
        "published": [],
        "workers": {},
    }
    report_path = run_dir / "deterministic-dispatch-recovery.json"
    repositories = {"PC": paths.pc, "LP": paths.lp}
    try:
        plan_value = json.loads(
            (paths.ring / ".opencode" / "task-plan.json").read_text(
                encoding="utf-8"
            )
        )
        tasks = {
            str(item["id"]): item
            for item in plan_value["tasks"]
            if isinstance(item, dict) and isinstance(item.get("id"), str)
        }
        config = json.loads(
            (paths.ring / "config" / "r4r-agents.json").read_text(
                encoding="utf-8"
            )
        )
        agents = config["agents"]
        accepted = set(
            load_global_progress(global_progress_path(paths.ring))["accepted"]
        )
    except (KeyError, OSError, TypeError, json.JSONDecodeError, ValueError) as exception:
        result["error"] = f"recovery context unavailable: {exception}"
        report_path.write_text(
            json.dumps(result, indent=2, ensure_ascii=False) + "\n",
            encoding="utf-8",
        )
        return result

    reservations: list[tuple[str, str, Sequence[str]]] = []
    candidates: list[dict[str, Any]] = []
    now = datetime.now(timezone.utc)

    for worker in ("PC", "LP"):
        worker_result: dict[str, Any] = {"status": "not-recoverable"}
        result["workers"][worker] = worker_result
        repository = repositories[worker]
        progress_path = _worker_progress_path(worker, repository)
        try:
            progress = json.loads(progress_path.read_text(encoding="utf-8"))
        except (OSError, json.JSONDecodeError) as exception:
            worker_result["reason"] = f"progress unavailable: {exception}"
            continue
        progress_items = {
            str(item.get("id")): item
            for item in progress.get("tasks", [])
            if isinstance(item, dict) and item.get("id")
        }
        directive_path = _directive_path(paths.ring, worker)
        raw_directive: dict[str, Any] | None = None
        if directive_path.is_file():
            try:
                loaded = json.loads(directive_path.read_text(encoding="utf-8"))
                raw_directive = loaded if isinstance(loaded, dict) else None
            except (OSError, json.JSONDecodeError):
                raw_directive = None

        task_objects = {
            task_id: SimpleNamespace(
                allowed_paths=tuple(task.get("allowed_paths", ())),
                depends_on=tuple(task.get("depends_on", ())),
            )
            for task_id, task in tasks.items()
        }
        if raw_directive is not None:
            try:
                current = validate_assignment(
                    raw_directive,
                    worker=worker,
                    tasks=task_objects,
                    accepted_task_ids=tuple(accepted),
                    max_age_seconds=DIRECTIVE_MAX_AGE_SECONDS,
                    now=now,
                )
            except ValueError as exception:
                worker_result["previous_assignment"] = f"invalid: {exception}"
            else:
                current_item = progress_items.get(current["task_id"], {})
                current_action = current["action"]
                dispatch_ready = current_item.get("status") != "ACCEPTED"
                if current_item.get("status") == "BLOCKED":
                    dispatch_ready = (
                        current_action == "RETRY_AUTHORIZED"
                        and _recovery_grant_available(current_item)
                        and current_item.get("recovery_authorization_consumed")
                        != current.get("authorization_id")
                    )
                if dispatch_ready:
                    worker_result.update(
                        status="already-valid",
                        task_id=current["task_id"],
                    )
                    reservations.append(
                        (worker, current["task_id"], current["write_scope"])
                    )
                    continue
                worker_result["previous_assignment"] = (
                    "valid schema but not dispatchable for current progress"
                )

        active_task = str(progress.get("active_task") or "").strip()
        active_item = progress_items.get(active_task)
        if not isinstance(active_item, dict):
            blocked = [
                item
                for item in progress_items.values()
                if item.get("status") == "BLOCKED"
            ]
            if len(blocked) == 1:
                active_item = blocked[0]
                active_task = str(active_item["id"])

        task_id = active_task
        item = active_item
        if not task_id and raw_directive is not None:
            task_id = str(raw_directive.get("task_id") or "").strip()
            item = progress_items.get(task_id)
        task = tasks.get(task_id)
        if not isinstance(item, dict) or not isinstance(task, dict):
            worker_result["reason"] = "no unfinished task already selected by Ring"
            continue
        if task_id in accepted or item.get("status") == "ACCEPTED":
            worker_result["reason"] = "task already accepted"
            continue
        missing_dependencies = sorted(
            set(task.get("depends_on", ())).difference(accepted)
        )
        if missing_dependencies:
            worker_result["reason"] = (
                f"unmet dependencies: {missing_dependencies}"
            )
            continue
        write_scope = task.get("allowed_paths")
        if not isinstance(write_scope, list) or not write_scope or not all(
            isinstance(value, str) and value for value in write_scope
        ):
            worker_result["reason"] = "canonical task scope is invalid"
            continue

        candidate: dict[str, Any] | None = None
        if item.get("status") == "BLOCKED":
            candidate = _whitespace_recovery_candidate(
                worker=worker,
                repository=repository,
                run_dir=run_dir,
                task_id=task_id,
                write_scope=write_scope,
                progress_item=item,
            )
            if candidate is None:
                worker_result["reason"] = (
                    "blocked task is not eligible for one-shot whitespace recovery"
                )
                continue
        elif item.get("status") in {"PENDING", "IN_PROGRESS", "REGRESSION"}:
            if raw_directive is None:
                worker_result["reason"] = "no previous Ring assignment to refresh"
                continue
            raw_action = str(raw_directive.get("action") or "").upper()
            raw_authorization_id = str(
                raw_directive.get("authorization_id") or ""
            ).strip()
            consumed_authorization_id = str(
                item.get("recovery_authorization_consumed") or ""
            ).strip()
            interrupted_recovery = (
                item.get("status") == "IN_PROGRESS"
                and raw_action == "RETRY_AUTHORIZED"
                and bool(raw_authorization_id)
                and raw_authorization_id == consumed_authorization_id
                and int(item.get("recovery_resume_count") or 0) < 1
            )
            if (
                str(raw_directive.get("target") or "").upper() != worker
                or str(raw_directive.get("task_id") or "") != task_id
                or (
                    raw_action not in {"START", "CONTINUE"}
                    and not interrupted_recovery
                )
                or not isinstance(raw_directive.get("write_scope"), list)
                or len(raw_directive["write_scope"]) != len(write_scope)
                or set(raw_directive["write_scope"]) != set(write_scope)
            ):
                worker_result["reason"] = (
                    "previous assignment does not exactly match active task and scope"
                )
                continue
            action = (
                "RETRY_AUTHORIZED"
                if interrupted_recovery
                else ("START" if item.get("status") == "PENDING" else "CONTINUE")
            )
            candidate = {
                "worker": worker,
                "task_id": task_id,
                "write_scope": list(write_scope),
                "evidence_path": str(
                    run_dir / f"{worker.lower()}-runtime" / "progress.json"
                ),
                "action": action,
                "authorization_id": (
                    raw_authorization_id if interrupted_recovery else None
                ),
                "reason": (
                    "The previous Ring recovery assignment expired after its "
                    "authorization was consumed, while the same interrupted attempt "
                    "remains eligible for its single resume."
                    if interrupted_recovery
                    else "The previous Ring assignment expired while the same task "
                    "remained unfinished with an unchanged canonical scope."
                ),
                "next_action": (
                    "Resume the same interrupted recovery attempt and run its exact gate."
                    if interrupted_recovery
                    else "Resume the same assigned task from current evidence and run "
                    "its exact gate."
                ),
                "avoid_repeating": (
                    "Do not issue a new recovery authorization or increment the attempt."
                    if interrupted_recovery
                    else "Do not select a different task or expand the canonical scope."
                ),
                "recovery_policy_version": (
                    int(item.get("recovery_repair_policy_version") or 1)
                    if interrupted_recovery
                    else None
                ),
            }
        else:
            worker_result["reason"] = f"task status is {item.get('status')!r}"
            continue
        candidates.append(candidate)

    for candidate in candidates:
        worker = candidate["worker"]
        conflict = next(
            (
                (reserved_worker, reserved_task)
                for reserved_worker, reserved_task, reserved_scope in reservations
                if reserved_worker != worker
                and any(
                    _scopes_overlap(left, right)
                    for left in candidate["write_scope"]
                    for right in reserved_scope
                )
            ),
            None,
        )
        if conflict is not None:
            result["workers"][worker].update(
                status="deferred",
                reason=(
                    f"scope overlaps {conflict[0]} task {conflict[1]}"
                ),
            )
            continue

        agent = agents.get(worker)
        if not isinstance(agent, dict):
            result["workers"][worker]["reason"] = "worker config unavailable"
            continue
        generated_at = datetime.now(timezone.utc)
        authorization_id = candidate.get("authorization_id")
        if candidate["action"] == "RETRY_AUTHORIZED" and not authorization_id:
            authorization_id = (
                f"{run_id}:{worker}:{candidate['task_id']}:deterministic-recovery"
            )
        directive = {
            "schema_version": 1,
            "assignment_id": (
                f"{run_id}:{worker}:{candidate['task_id']}:dispatch-recovery"
            ),
            "target": worker,
            "task_id": candidate["task_id"],
            "assigned_agent": str(agent.get("agentId") or worker.lower()),
            "model": str(agent.get("model") or "unknown-model"),
            "branch": str(agent.get("branch") or ""),
            "write_scope": candidate["write_scope"],
            "evidence_path": candidate["evidence_path"],
            "generated_at": generated_at.isoformat(),
            "expires_at": (
                generated_at + timedelta(seconds=DIRECTIVE_MAX_AGE_SECONDS)
            ).isoformat(),
            "priority": "advisory",
            "action": candidate["action"],
            "authorization_id": authorization_id,
            "recovery_policy_version": candidate["recovery_policy_version"],
            "summary": candidate["reason"],
            "next_action": candidate["next_action"],
            "evidence_paths": [candidate["evidence_path"]],
            "constraints": ["Run the exact canonical task gate."],
            "avoid_repeating": candidate["avoid_repeating"],
        }
        _atomic_write_text(
            _directive_path(paths.ring, worker),
            json.dumps(directive, indent=2, ensure_ascii=False) + "\n",
        )
        reservations.append(
            (worker, candidate["task_id"], candidate["write_scope"])
        )
        result["published"].append(worker)
        result["workers"][worker].update(
            status="published",
            action=candidate["action"],
            task_id=candidate["task_id"],
            assignment_id=directive["assignment_id"],
        )

    report_path.write_text(
        json.dumps(result, indent=2, ensure_ascii=False) + "\n",
        encoding="utf-8",
    )
    return result


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
        "integration_sync": {"status": "not-run"},
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
            paths.ring,
        )
    except ValueError as exception:
        result["reason"] = str(exception)
        (run_dir / "ring-output-publication.json").write_text(
            json.dumps(result, indent=2, ensure_ascii=False) + "\n",
            encoding="utf-8",
        )
        return result

    result["deterministic_recovery_authorizations"] = (
        _authorize_bounded_whitespace_recovery(paths, run_dir, normalized_state)
    )

    coordination_dir = paths.ring / "docs" / "agent-coordination"
    decisions_path = coordination_dir / "DECISIONS.md"
    coordination_fingerprint = _coordination_fingerprint(normalized_state)
    semantic_change = (
        _last_coordination_fingerprint(decisions_path)
        != coordination_fingerprint
    )
    result["coordination_fingerprint"] = coordination_fingerprint
    result["semantic_change"] = semantic_change

    task_evidence_files = []
    if semantic_change:
        for worker in ("PC", "LP"):
            evidence_path = _publish_task_evidence(
                paths.ring,
                run_id,
                worker,
                normalized_state["decisions"][worker],
            )
            if evidence_path is not None:
                task_evidence_files.append(evidence_path)
                result["versioned_files"][f"{worker.lower()}-task-evidence"] = str(
                    evidence_path
                )
            else:
                normalized_state["decisions"][worker]["evidence_path"] = None

    operational_destinations = {
        "state.json": paths.ring / ".ring-agent" / "state.json",
        "code-pc-review.md": paths.ring / ".ring-agent" / "code-pc-review.md",
        "code-lp-review.md": paths.ring / ".ring-agent" / "code-lp-review.md",
        "fullstack-handoff.md": (
            paths.ring / ".ring-agent" / "fullstack-handoff.md"
        ),
        "global-summary.md": paths.ring / ".ring-agent" / "global-summary.md",
        "worker-understanding.md": (
            paths.ring / ".ring-agent" / "worker-understanding.md"
        ),
    }
    if semantic_change:
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

    versioned_destinations = {
        "code-pc-review.md": coordination_dir / "PC-WORKER.md",
        "code-lp-review.md": coordination_dir / "LAPTOP-WORKER.md",
        "fullstack-handoff.md": coordination_dir / "RING-HANDOFF.md",
        "worker-understanding.md": coordination_dir / "WORKER-UNDERSTANDING.md",
        "global-summary.md": coordination_dir / "CURRENT-STATE.md",
    }
    if semantic_change:
        for name, destination in versioned_destinations.items():
            _atomic_write_text(
                destination,
                (output_dir / name).read_text(encoding="utf-8"),
            )
            result["versioned_files"][name] = str(destination)
        _append_decision_ledger(decisions_path, run_id, normalized_state)
        result["versioned_files"]["decisions"] = str(decisions_path)
        commit_result = _commit_coordination_files(
            paths.ring,
            [*versioned_destinations.values(), decisions_path, *task_evidence_files],
            run_id,
        )
    else:
        commit_result = {
            "status": "no-semantic-change",
            "commit": None,
            "detail": "task, action, constraints and diagnosis are unchanged",
        }
    result["coordination_commit"] = commit_result
    if commit_result["status"] == "failed":
        result["reason"] = "coordination-commit-failed"
        (run_dir / "ring-output-publication.json").write_text(
            json.dumps(result, indent=2, ensure_ascii=False) + "\n",
            encoding="utf-8",
        )
        return result

    if commit_result["status"] == "committed":
        result["integration_sync"] = _integration_sync(paths.ring, "checkpoint")

    deterministic_authorizations = tuple(
        result.get("deterministic_recovery_authorizations") or ()
    )
    workers_to_publish = (
        ("PC", "LP") if semantic_change else deterministic_authorizations
    )
    if workers_to_publish:
        generated_at = datetime.now(timezone.utc)
        expires_at = generated_at + timedelta(seconds=DIRECTIVE_MAX_AGE_SECONDS)
        for worker in workers_to_publish:
            decision = normalized_state["decisions"][worker]
            directive = {
                "schema_version": 1,
                "assignment_id": f"{run_id}:{worker}:{decision['task_id']}",
                "target": worker,
                "task_id": decision["task_id"] or "NO_ACTIVE_TASK",
                "assigned_agent": decision["assigned_agent"],
                "model": decision["model"],
                "branch": decision["branch"],
                "write_scope": decision["write_scope"],
                "evidence_path": decision["evidence_path"],
                "generated_at": generated_at.isoformat(),
                "expires_at": expires_at.isoformat(),
                "priority": "advisory",
                "action": decision["action"],
                "authorization_id": (
                    f"{run_id}:{worker}:{decision['task_id']}"
                    if decision["action"] == "RETRY_AUTHORIZED"
                    else None
                ),
                "recovery_policy_version": (
                    decision.get("recovery_policy_version")
                    if decision["action"] == "RETRY_AUTHORIZED"
                    else None
                ),
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
    result["reason"] = (
        "ok"
        if semantic_change
        else "recovery-authorization-refreshed"
        if deterministic_authorizations
        else "no-semantic-change"
    )
    (run_dir / "ring-output-publication.json").write_text(
        json.dumps(result, indent=2, ensure_ascii=False) + "\n",
        encoding="utf-8",
    )
    return result


def _prompt(paths: WorktreePaths, run_dir: Path, run_id: str) -> str:
    output_dir = run_dir / "output"
    return f"""You are The Ring, the fullstack task coordinator for R4R.
This is a fresh OpenCode session. Do not resume another transcript.

The deterministic supervisor has prepared the primary evidence and repository context:
- RUN_DIR: {run_dir}
- OUTPUT_DIR: {output_dir}
- RUN_ID: {run_id}
- RING_WORKTREE: {paths.ring}

Read the bounded evidence below RUN_DIR first. Do not read `opencode.console.log` and
do not perform unbounded searches. Read `.opencode/task-plan.json` as the only task
authority and `global-progress.json` as the only accepted-task/dependency ledger.
Never read or edit the live PC or LP worktrees directly.

Repository preservation rules:
- write only the six required staged files below OUTPUT_DIR;
- never edit product, tests, controller, configuration, policy or task-plan files;
- never edit secrets, credentials, private keys, tokens, `.env` files or PID/lock files;
- never write Git history, install packages, launch workers or run shell commands.

Review the Ring, PC and LP commit/status/diff evidence and both worker-runtime
subdirectories. Prefer the newest authoritative evidence inside RUN_DIR: progress,
worker memory, checkpoint, escalation plan/review, correction packet, local
understanding, CodeGraph report, gate summary and prior Ring assignment.

PC and LP are equivalent fullstack workers. Generate at most one current assignment
for each worker. Respect dependencies, current durable progress and exact task gates.
Never assign overlapping `allowed_paths`. Prefer correction before new implementation.
Do not claim a test passed, a task completed or a worker started unless direct evidence
proves it. If a decision is ambiguous, cross-cutting or high risk, use `ESCALATE` for
that worker; the deterministic supervisor will ask Sol for a complete replacement set.

`CONTINUE` never unlocks a BLOCKED task. Use `RETRY_AUTHORIZED` only when current
evidence proves a bounded deterministic repair exists and the task has not already
consumed a recovery grant. It authorizes exactly one additional attempt. If that
attempt fails, use `HOLD` and request operator diagnosis; never issue another
recovery authorization for the same blocked task.

Write these six staged files below OUTPUT_DIR on every successful cycle:
- {output_dir}/state.json
- {output_dir}/code-pc-review.md
- {output_dir}/code-lp-review.md
- {output_dir}/fullstack-handoff.md
- {output_dir}/worker-understanding.md
- {output_dir}/global-summary.md

The Python supervisor validates the staged files, publishes current snapshots plus an
append-only decision ledger below `docs/agent-coordination/`, commits only those
coordination documents, and creates the PC/LP assignment JSON files. Do not write
`runtime/control/**` yourself during the staged review.

state.json must be valid JSON with this exact structure:
{{
  "schema_version": 1,
  "run_id": "{run_id}",
  "overall_status": "READY | BLOCKED | NO_ACTION",
  "decisions": {{
    "PC": {{
      "action": "START | CONTINUE | RETRY_AUTHORIZED | HOLD | STOP | NO_ACTION | ESCALATE",
      "task_id": "exact active task id or null",
      "reason": "non-empty evidence-grounded diagnosis",
      "next_action": "one focused action for one worker pass",
      "evidence_paths": [
        "one or more existing paths inside RUN_DIR supporting the decision"
      ],
      "acceptance_gates": [
        "one or more exact task or deterministic gate constraints"
      ],
      "avoid_repeating": "the last failed or wasteful approach to avoid"
    }},
    "LP": {{
      "action": "START | CONTINUE | RETRY_AUTHORIZED | HOLD | STOP | NO_ACTION | ESCALATE",
      "task_id": "exact active task id or null",
      "reason": "non-empty evidence-grounded diagnosis",
      "next_action": "one focused action for one worker pass",
      "evidence_paths": [
        "one or more existing paths inside RUN_DIR supporting the decision"
      ],
      "acceptance_gates": [
        "one or more exact task or deterministic gate constraints"
      ],
      "avoid_repeating": "the last failed or wasteful approach to avoid"
    }}
  }},
  "integration_risks": ["zero or more evidence-grounded risks"],
  "evidence_limitations": ["zero or more explicit limitations"]
}}

The exact task specification, deterministic gate and current Ring/Sol correction packet
remain authoritative for PC and LP. Do not ask workers to bypass a gate, change task
scope, write Git history or repeat an already failed approach. Use only task IDs from
`.opencode/task-plan.json`. Each task's `allowed_paths` list is its canonical write
scope; the supervisor rejects active scopes that overlap.

Finish after the six staged files have been written.
"""


def _command(
    paths: WorktreePaths,
    run_dir: Path,
    run_id: str,
    *,
    model: str = RING_MODEL,
    variant: str = RING_VARIANT,
) -> tuple[str, ...]:
    return (
        OPENCODE_BIN,
        "run",
        "--dir",
        str(paths.ring),
        "--agent",
        RING_AGENT,
        "--model",
        model,
        "--variant",
        variant,
        "--format",
        "json",
        "--auto",
        _prompt(paths, run_dir, run_id),
    )


def _draft_escalations(output_dir: Path) -> tuple[str, ...]:
    """Return workers for which Luna explicitly requested Sol review."""
    state_path = output_dir / "state.json"
    try:
        value = json.loads(state_path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError):
        return ()
    decisions = value.get("decisions") if isinstance(value, dict) else None
    if not isinstance(decisions, dict):
        return ()
    return tuple(
        worker
        for worker in ("PC", "LP")
        if isinstance(decisions.get(worker), dict)
        and str(decisions[worker].get("action", "")).upper() == "ESCALATE"
    )


def _stage_luna_draft(run_dir: Path) -> Path:
    output_dir = run_dir / "output"
    draft_dir = run_dir / "luna-draft"
    if draft_dir.exists():
        raise ValueError(f"Luna draft destination already exists: {draft_dir}")
    output_dir.rename(draft_dir)
    output_dir.mkdir(parents=True)
    return draft_dir


def _escalation_prompt(
    paths: WorktreePaths,
    run_dir: Path,
    run_id: str,
    workers: Sequence[str],
    draft_dir: Path,
) -> str:
    output_dir = run_dir / "output"
    worker_list = ", ".join(workers)
    return f"""You are the on-demand R4R high-reasoning escalation reviewer.
This is a fresh OpenCode session. Do not resume another transcript.

The Ring coordinator requested escalation for: {worker_list}.
- RUN_ID: {run_id}
- RUN_DIR: {run_dir}
- LUNA_DRAFT: {draft_dir}
- OUTPUT_DIR: {output_dir}
- RING_WORKTREE: {paths.ring}

Read the bounded evidence in RUN_DIR, the complete coordinator draft and
`.opencode/task-plan.json`. PC and LP are equivalent fullstack workers. Resolve the
explicitly escalated decision, verify dependencies and disjoint `allowed_paths`, then
write a complete replacement set of these six files:
- {output_dir}/state.json
- {output_dir}/code-pc-review.md
- {output_dir}/code-lp-review.md
- {output_dir}/fullstack-handoff.md
- {output_dir}/worker-understanding.md
- {output_dir}/global-summary.md

Use the exact state.json structure from the coordinator draft, but every final action must be
one of START, CONTINUE, RETRY_AUTHORIZED, HOLD, STOP or NO_ACTION. `ESCALATE` is not a
valid final action. Use only task IDs in the canonical plan and existing evidence paths
inside RUN_DIR. Never edit product, tests, controller, configuration, policy, task-plan,
Git history or `runtime/control/**`; write only the six files below OUTPUT_DIR.
"""


def _escalation_command(
    paths: WorktreePaths,
    run_dir: Path,
    run_id: str,
    workers: Sequence[str],
    draft_dir: Path,
) -> tuple[str, ...]:
    return (
        OPENCODE_BIN,
        "run",
        "--dir",
        str(paths.ring),
        "--agent",
        ESCALATION_AGENT,
        "--model",
        ESCALATION_MODEL,
        "--variant",
        ESCALATION_VARIANT,
        "--format",
        "json",
        "--auto",
        _escalation_prompt(paths, run_dir, run_id, workers, draft_dir),
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
    if FIRST_OUTPUT_TIMEOUT_SECONDS < 1:
        raise ValueError("R4R_RING_FIRST_OUTPUT_TIMEOUT_SECONDS must be positive")
    if EVENT_MIN_INTERVAL_SECONDS < 1:
        raise ValueError("R4R_RING_EVENT_MIN_INTERVAL_SECONDS must be positive")
    if FAILURE_RETRY_BASE_SECONDS < 1:
        raise ValueError("R4R_RING_FAILURE_RETRY_BASE_SECONDS must be positive")
    if FAILURE_RETRY_MAX_SECONDS < FAILURE_RETRY_BASE_SECONDS:
        raise ValueError(
            "R4R_RING_FAILURE_RETRY_MAX_SECONDS must be greater than or equal "
            "to R4R_RING_FAILURE_RETRY_BASE_SECONDS"
        )

    paths = WorktreePaths(
        require_git_worktree(paths.ring, "RING"),
        require_git_worktree(paths.pc, "PC"),
        require_git_worktree(paths.lp, "LP"),
    )
    canonical_plan = json.loads(
        (paths.ring / ".opencode" / "task-plan.json").read_text(encoding="utf-8")
    )
    migrate_legacy_acceptances(
        global_progress_path(paths.ring),
        plan_task_ids=tuple(item["id"] for item in canonical_plan["tasks"]),
        worker_progress={
            "PC": _worker_progress_path("PC", paths.pc),
            "LP": _worker_progress_path("LP", paths.lp),
        },
        worker_heads={
            "PC": _git(paths.pc, ["rev-parse", "HEAD"]).strip(),
            "LP": _git(paths.lp, ["rev-parse", "HEAD"]).strip(),
        },
    )
    startup_sync = _integration_sync(paths.ring, "startup")
    if startup_sync["status"] == "failed":
        print(
            "[the-ring] startup integration synchronization failed: "
            + startup_sync.get("detail", "unknown error"),
            flush=True,
        )
        return 74
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
    consecutive_failures = 0

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
            dispatch_recovery = _dispatch_recovery_assignments(
                paths, run_dir, run_id
            )
            control.set_state(
                "running",
                (
                    f"The Ring session {run_id} started; "
                    f"event_requests={len(consumed_requests)}; "
                    "dispatch_recovery="
                    f"{len(dispatch_recovery.get('published', []))}"
                ),
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

            luna_result = run_streamed(
                _command(paths, run_dir, run_id),
                paths.ring,
                run_dir / "opencode.console.log",
                timeout_seconds=SESSION_TIMEOUT_SECONDS,
                first_output_timeout_seconds=FIRST_OUTPUT_TIMEOUT_SECONDS,
                stop_poll=stop_poll,
            )
            result = luna_result
            effective_model = RING_MODEL
            effective_variant = RING_VARIANT
            fallback: dict[str, Any] = {
                "requested": False,
                "model": RING_FALLBACK_MODEL,
                "variant": RING_FALLBACK_VARIANT,
            }
            fallback_trigger = _ring_fallback_trigger(
                luna_result,
                run_dir / "output",
            )
            if (
                fallback_trigger is not None
                and active_command is None
                and RING_FALLBACK_MODEL
                and RING_FALLBACK_MODEL != RING_MODEL
            ):
                fallback["requested"] = True
                fallback["trigger"] = fallback_trigger
                result = run_streamed(
                    _command(
                        paths,
                        run_dir,
                        run_id,
                        model=RING_FALLBACK_MODEL,
                        variant=RING_FALLBACK_VARIANT,
                    ),
                    paths.ring,
                    run_dir / "opencode.fallback.console.log",
                    timeout_seconds=SESSION_TIMEOUT_SECONDS,
                    first_output_timeout_seconds=FIRST_OUTPUT_TIMEOUT_SECONDS,
                    stop_poll=stop_poll,
                )
                effective_model = RING_FALLBACK_MODEL
                effective_variant = RING_FALLBACK_VARIANT
                fallback.update(
                    {
                        "exit_code": result.exit_code,
                        "duration_seconds": result.duration_seconds,
                        "stop_reason": result.stop_reason,
                    }
                )
            escalation: dict[str, Any] = {
                "requested": False,
                "workers": [],
                "agent": ESCALATION_AGENT,
                "model": ESCALATION_MODEL,
                "variant": ESCALATION_VARIANT,
            }
            if result.exit_code == 0 and not result.stop_reason:
                escalated_workers = _draft_escalations(run_dir / "output")
                if escalated_workers:
                    escalation["requested"] = True
                    escalation["workers"] = list(escalated_workers)
                    try:
                        draft_dir = _stage_luna_draft(run_dir)
                    except (OSError, ValueError) as exception:
                        escalation["error"] = str(exception)
                    else:
                        result = run_streamed(
                            _escalation_command(
                                paths,
                                run_dir,
                                run_id,
                                escalated_workers,
                                draft_dir,
                            ),
                            paths.ring,
                            run_dir / "escalation.console.log",
                            timeout_seconds=SESSION_TIMEOUT_SECONDS,
                            first_output_timeout_seconds=FIRST_OUTPUT_TIMEOUT_SECONDS,
                            stop_poll=stop_poll,
                        )
                        escalation.update(
                            {
                                "exit_code": result.exit_code,
                                "duration_seconds": result.duration_seconds,
                                "stop_reason": result.stop_reason,
                                "draft_dir": str(draft_dir),
                            }
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
            session_succeeded = (
                effective_exit_code == 0
                and not result.stop_reason
                and publication.get("published", False)
            )
            if session_succeeded:
                consecutive_failures = 0
                next_delay_seconds = REVIEW_INTERVAL_SECONDS
            else:
                consecutive_failures += 1
                next_delay_seconds = _failure_retry_delay(consecutive_failures)

            (run_dir / "session-result.json").write_text(
                json.dumps(
                    {
                        "run_id": run_id,
                        "exit_code": result.exit_code,
                        "effective_exit_code": effective_exit_code,
                        "duration_seconds": result.duration_seconds,
                        "primary_duration_seconds": luna_result.duration_seconds,
                        "luna_duration_seconds": luna_result.duration_seconds,
                        "publication": publication,
                        "stop_reason": result.stop_reason,
                        "agent": RING_AGENT,
                        "model": effective_model,
                        "variant": effective_variant,
                        "fallback": fallback,
                        "escalation": escalation,
                        "review_interval_seconds": REVIEW_INTERVAL_SECONDS,
                        "event_min_interval_seconds": EVENT_MIN_INTERVAL_SECONDS,
                        "consecutive_failures": consecutive_failures,
                        "next_delay_seconds": next_delay_seconds,
                        "dispatch_recovery": dispatch_recovery,
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
            elif result.stop_reason in {"timeout", "first_output_timeout"}:
                control.set_state(
                    "running",
                    (
                        f"Ring session stopped by {result.stop_reason}; "
                        f"fresh retry in {next_delay_seconds}s"
                    ),
                )
            elif not publication.get("published", False):
                control.set_state(
                    "running",
                    "Ring session output rejected: "
                    f"{publication.get('reason', 'unknown publication failure')}; "
                    f"fresh retry in {next_delay_seconds}s",
                )
            else:
                control.set_state(
                    "running",
                    f"Ring session finished with exit {effective_exit_code}",
                )

            if once:
                return effective_exit_code
            next_run = time.monotonic() + next_delay_seconds
    finally:
        fcntl.flock(lock_handle.fileno(), fcntl.LOCK_UN)
        lock_handle.close()
