"""Deterministic stabilization supervisor for the R4R Ring agent.

This module deliberately uses only the Python standard library. It provides:
- strict OpenCode agent/model preflight;
- bounded snapshot collection from the three worktrees;
- snapshot-only prompt construction;
- streamed OpenCode monitoring;
- repeated tool-error circuit breaking;
- timeout and operator-interrupt handling;
- strict staged-artifact validation;
- atomic promotion only on SUCCESS;
- a machine-readable supervisor-result.json.
"""

from __future__ import annotations

import argparse
import dataclasses
import hashlib
import json
import os
import queue
import re
import shutil
import signal
import subprocess
import sys
import threading
import time
from collections import Counter
from dataclasses import dataclass, field
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Iterable, Mapping, Sequence

AGENT_NAME = "r4r-ring"
DEFAULT_MODEL = "ollama-pc/qwen3-coder-next-80b-t033-128k-8k-pc-pc:latest"
DEFAULT_INTERVAL_SECONDS = 4 * 60 * 60
DEFAULT_SESSION_TIMEOUT_SECONDS = 90 * 60
DEFAULT_IDLE_TIMEOUT_SECONDS = 15 * 60
DEFAULT_EXTERNAL_DIRECTORY_THRESHOLD = 2
DEFAULT_TOOL_ERROR_THRESHOLD = 3

STATUS_SUCCESS = "SUCCESS"
STATUS_PARTIAL = "PARTIAL_ARTIFACTS"
STATUS_INVALID_AGENT = "INVALID_AGENT"
STATUS_REPEATED_ERROR = "REPEATED_TOOL_ERROR"
STATUS_INVALID_ARTIFACT = "INVALID_FINAL_ARTIFACT"
STATUS_TIMEOUT = "TIMEOUT"
STATUS_INTERRUPTED = "OPERATOR_INTERRUPTED"

EXIT_CODES = {
    STATUS_SUCCESS: 0,
    STATUS_PARTIAL: 20,
    STATUS_INVALID_AGENT: 21,
    STATUS_REPEATED_ERROR: 22,
    STATUS_INVALID_ARTIFACT: 23,
    STATUS_TIMEOUT: 24,
    STATUS_INTERRUPTED: 130,
}

REQUIRED_OUTPUTS = {
    "state.json": ".ring-agent/state.json",
    "code-pc-review.md": ".ring-agent/code-pc-review.md",
    "code-lp-review.md": ".ring-agent/code-lp-review.md",
    "backend-frontend-handoff.md": ".ring-agent/backend-frontend-handoff.md",
    "worker-understanding.md": ".opencode/current/ring/worker-understanding.md",
    "global-summary.md": ".ring-agent/global-summary.md",
}

EXPECTED_BRANCHES = {
    "RING": "agent/ring-agent-worker",
    "PC": "agent/pc-qwen3-worker",
    "LP": "agent/laptop-qwen3-worker",
}

ANSI_RE = re.compile(r"\x1b\[[0-?]*[ -/]*[@-~]")
VOLATILE_RE = re.compile(
    r"(?:call|session|message|part)ID[\"']?\s*[:=]\s*[\"']?[^\s,}\]]+|"
    r"\b(?:ses|msg|prt|call)_[A-Za-z0-9]+\b|"
    r"\b\d{10,16}\b"
)
ABS_PATH_RE = re.compile(r"/(?:home|tmp|var|opt|mnt)/[^\s\"'<>]+")


@dataclass(frozen=True)
class StabilizedPaths:
    ring: Path
    pc: Path
    lp: Path

    def as_dict(self) -> dict[str, Path]:
        return {"RING": self.ring, "PC": self.pc, "LP": self.lp}


@dataclass
class AgentPreflight:
    valid: bool
    code: str | None = None
    detail: str | None = None
    model: str | None = None
    agent_list_output: str = ""
    model_list_output: str = ""


@dataclass
class ArtifactValidation:
    valid: list[str] = field(default_factory=list)
    missing: list[str] = field(default_factory=list)
    invalid: dict[str, str] = field(default_factory=dict)

    @property
    def complete(self) -> bool:
        return not self.missing and not self.invalid and len(self.valid) == len(REQUIRED_OUTPUTS)

    @property
    def has_any_valid(self) -> bool:
        return bool(self.valid)


@dataclass
class MonitorOutcome:
    status: str | None
    process_exit: int | None
    error: dict[str, Any] | None
    repeated_signatures: dict[str, int]
    fallback_detected: bool = False


def _utc_run_id() -> str:
    return datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")


def _allocate_run_dir(ring_repo: Path) -> tuple[str, Path]:
    base = _utc_run_id()
    root = ring_repo / "runtime" / "ring-agent" / "ring"
    for index in range(1000):
        run_id = base if index == 0 else f"{base}-{index:02d}"
        candidate = root / run_id
        try:
            candidate.mkdir(parents=True, exist_ok=False)
            return run_id, candidate
        except FileExistsError:
            continue
    raise RuntimeError("Unable to allocate a unique Ring run directory")


def _utc_now() -> str:
    return datetime.now(timezone.utc).isoformat()


def _strip_ansi(value: str) -> str:
    return ANSI_RE.sub("", value)


def _run(
    args: Sequence[str],
    *,
    cwd: Path | None = None,
    timeout: int = 30,
    check: bool = False,
) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        list(args),
        cwd=str(cwd) if cwd else None,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        timeout=timeout,
        check=check,
    )


def _write_text(path: Path, content: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content, encoding="utf-8")


def _write_json(path: Path, payload: Any) -> None:
    _write_text(path, json.dumps(payload, ensure_ascii=False, indent=2, sort_keys=True) + "\n")


def _sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def _bounded_text(value: str, max_bytes: int = 512 * 1024) -> str:
    raw = value.encode("utf-8", errors="replace")
    if len(raw) <= max_bytes:
        return value
    prefix = raw[:max_bytes].decode("utf-8", errors="replace")
    return prefix + f"\n\n[TRUNCATED BY SUPERVISOR: original_bytes={len(raw)} limit={max_bytes}]\n"


def _safe_resolve_inside(path: Path, root: Path) -> bool:
    try:
        path.resolve(strict=False).relative_to(root.resolve(strict=False))
        return True
    except ValueError:
        return False


def _parse_frontmatter(agent_file: Path) -> dict[str, str]:
    text = agent_file.read_text(encoding="utf-8")
    lines = text.splitlines()
    if not lines or lines[0].strip() != "---":
        raise ValueError("missing opening YAML frontmatter delimiter")
    try:
        end = next(index for index, line in enumerate(lines[1:], start=1) if line.strip() == "---")
    except StopIteration as exc:
        raise ValueError("missing closing YAML frontmatter delimiter") from exc

    values: dict[str, str] = {}
    for line in lines[1:end]:
        if not line or line[:1].isspace() or ":" not in line:
            continue
        key, raw = line.split(":", 1)
        values[key.strip()] = raw.strip().strip('"').strip("'")
    return values


def validate_opencode_agent(
    ring_repo: Path,
    *,
    agent_name: str = AGENT_NAME,
    strict_model_check: bool = True,
) -> AgentPreflight:
    agent_file = ring_repo / ".opencode" / "agents" / f"{agent_name}.md"
    if not agent_file.is_file():
        return AgentPreflight(False, "AGENT_NOT_FOUND", str(agent_file))

    try:
        frontmatter = _parse_frontmatter(agent_file)
    except (OSError, UnicodeError, ValueError) as exc:
        return AgentPreflight(False, "AGENT_CONFIG_INVALID", str(exc))

    if frontmatter.get("mode") != "primary":
        return AgentPreflight(False, "AGENT_NOT_PRIMARY", f"mode={frontmatter.get('mode')!r}")

    model = frontmatter.get("model")
    if not model or "/" not in model:
        return AgentPreflight(False, "AGENT_CONFIG_INVALID", f"invalid model={model!r}")

    try:
        listed = _run(["opencode", "agent", "list"], cwd=ring_repo, timeout=30)
    except FileNotFoundError:
        return AgentPreflight(False, "OPENCODE_NOT_FOUND", "opencode executable not found", model=model)
    except subprocess.TimeoutExpired:
        return AgentPreflight(False, "AGENT_LIST_TIMEOUT", "opencode agent list exceeded 30s", model=model)

    clean_list = _strip_ansi(listed.stdout)
    pattern = re.compile(rf"(?m)^\s*{re.escape(agent_name)}\s+\(primary\)\b")
    if listed.returncode != 0 or not pattern.search(clean_list):
        return AgentPreflight(
            False,
            "AGENT_NOT_VISIBLE",
            f"exit={listed.returncode}",
            model=model,
            agent_list_output=clean_list,
        )

    model_output = ""
    if strict_model_check:
        provider, model_id = model.split("/", 1)
        attempts = (["opencode", "models", provider], ["opencode", "models"])
        success = False
        outputs: list[str] = []
        for command in attempts:
            try:
                result = _run(command, cwd=ring_repo, timeout=45)
            except subprocess.TimeoutExpired:
                outputs.append(f"$ {' '.join(command)}\nTIMEOUT")
                continue
            clean = _strip_ansi(result.stdout)
            outputs.append(f"$ {' '.join(command)}\n{clean}")
            if result.returncode == 0 and (model in clean or model_id in clean):
                success = True
                break
        model_output = "\n\n".join(outputs)
        if not success:
            return AgentPreflight(
                False,
                "MODEL_NOT_AVAILABLE",
                model,
                model=model,
                agent_list_output=clean_list,
                model_list_output=model_output,
            )

    return AgentPreflight(
        True,
        model=model,
        agent_list_output=clean_list,
        model_list_output=model_output,
    )


def _git_output(repo: Path, args: Sequence[str], timeout: int = 45) -> str:
    try:
        result = _run(["git", "-C", str(repo), *args], timeout=timeout)
    except (FileNotFoundError, subprocess.TimeoutExpired) as exc:
        return f"ERROR: {type(exc).__name__}: {exc}\n"
    return _bounded_text(result.stdout)


def validate_worktrees(paths: StabilizedPaths) -> list[str]:
    errors: list[str] = []
    resolved: dict[str, Path] = {}
    for label, repo in paths.as_dict().items():
        if not repo.is_dir():
            errors.append(f"{label}: directory does not exist: {repo}")
            continue
        result = _git_output(repo, ["rev-parse", "--is-inside-work-tree"]).strip()
        if result != "true":
            errors.append(f"{label}: not a Git worktree: {repo}: {result}")
            continue
        resolved[label] = repo.resolve()
        branch = _git_output(repo, ["branch", "--show-current"]).strip()
        expected = EXPECTED_BRANCHES[label]
        if branch != expected:
            errors.append(f"{label}: expected branch {expected!r}, found {branch!r}")

    if len(set(resolved.values())) != len(resolved):
        errors.append("RING, PC and LP must point to three distinct directories")
    return errors


def _copy_snapshot_file(
    source: Path,
    destination: Path,
    *,
    manifest: list[dict[str, Any]],
    label: str,
    relative: Path,
    max_file_bytes: int = 512 * 1024,
) -> int:
    try:
        if source.is_symlink() or not source.is_file():
            return 0
        size = source.stat().st_size
        destination.parent.mkdir(parents=True, exist_ok=True)
        if size <= max_file_bytes:
            shutil.copyfile(source, destination)
        else:
            with source.open("rb") as src, destination.open("wb") as dst:
                dst.write(src.read(max_file_bytes))
                dst.write(
                    f"\n\n[TRUNCATED BY SUPERVISOR: original_bytes={size} limit={max_file_bytes}]\n".encode()
                )
        manifest.append(
            {
                "label": label,
                "relative_path": relative.as_posix(),
                "snapshot_path": destination.as_posix(),
                "source_size": size,
                "snapshot_size": destination.stat().st_size,
                "sha256": _sha256(destination),
            }
        )
        return destination.stat().st_size
    except OSError as exc:
        manifest.append(
            {
                "label": label,
                "relative_path": relative.as_posix(),
                "error": f"{type(exc).__name__}: {exc}",
            }
        )
        return 0


def _selected_snapshot_files(repo: Path, label: str) -> list[Path]:
    candidates: set[Path] = set()
    exact = [
        "AGENTS.md",
        ".opencode/task-plan.json",
        ".opencode/progress.pc.json",
        ".opencode/progress.lp.json",
        ".ring-agent/state.json",
        ".ring-agent/code-pc-review.md",
        ".ring-agent/code-lp-review.md",
        ".ring-agent/backend-frontend-handoff.md",
        ".ring-agent/global-summary.md",
        ".opencode/current/ring/worker-understanding.md",
    ]
    for item in exact:
        path = repo / item
        if path.is_file():
            candidates.add(path)

    for base_rel in (".opencode/current", ".ring-agent"):
        base = repo / base_rel
        if base.is_dir():
            for path in base.rglob("*"):
                if path.is_file() and path.suffix.lower() in {".md", ".json", ".txt"}:
                    try:
                        rel = path.relative_to(repo)
                    except ValueError:
                        continue
                    if len(rel.parts) <= 6:
                        candidates.add(path)

    control = repo / "runtime" / "control" / label
    if control.is_dir():
        for path in control.glob("*"):
            if path.is_file() and path.suffix.lower() in {".md", ".json", ".txt", ".log"}:
                candidates.add(path)

    runs = repo / "runtime" / "runs" / label
    if runs.is_dir():
        selected_names = {
            "codex-review.json",
            "task-gate.json",
            "initial-gate.json",
            "pre-edit-gate.json",
            "gate-diagnostics.json",
            "local-understanding.md",
            "pre-edit-understanding.md",
            "gate-summary.md",
            "error-manifest.json",
        }
        recent: list[tuple[float, Path]] = []
        for path in runs.rglob("*"):
            if path.is_file() and path.name in selected_names:
                try:
                    recent.append((path.stat().st_mtime, path))
                except OSError:
                    continue
        for _, path in sorted(recent, reverse=True)[:60]:
            candidates.add(path)

    return sorted(candidates, key=lambda item: item.as_posix())


def collect_snapshot(paths: StabilizedPaths, run_dir: Path) -> dict[str, Any]:
    evidence_root = run_dir / "evidence"
    manifest: list[dict[str, Any]] = []
    repository_summary: dict[str, Any] = {}

    for label, repo in paths.as_dict().items():
        target = evidence_root / label
        target.mkdir(parents=True, exist_ok=True)

        branch = _git_output(repo, ["branch", "--show-current"]).strip()
        head = _git_output(repo, ["rev-parse", "HEAD"]).strip()
        common_dir = _git_output(repo, ["rev-parse", "--git-common-dir"]).strip()
        metadata = {
            "label": label,
            "repository_name": repo.name,
            "branch": branch,
            "expected_branch": EXPECTED_BRANCHES[label],
            "head": head,
            "git_common_dir_name": Path(common_dir).name if common_dir else None,
            "collected_at": _utc_now(),
        }
        _write_json(target / "repository-metadata.json", metadata)
        repository_summary[label] = metadata

        commands = {
            "git-status.txt": ["status", "--short", "--branch"],
            "git-diff-stat.txt": ["diff", "--stat", "--", "."],
            "git-diff.patch": ["diff", "--no-ext-diff", "--binary", "--", "."],
            "last-ten-commits.txt": [
                "log",
                "-n",
                "10",
                "--date=iso-strict",
                "--pretty=format:commit %H%nDate: %ad%nSubject: %s",
                "--name-status",
            ],
        }
        for filename, args in commands.items():
            max_bytes = 1024 * 1024 if filename == "git-diff.patch" else 512 * 1024
            _write_text(target / filename, _bounded_text(_git_output(repo, args), max_bytes))

        total_bytes = 0
        for source in _selected_snapshot_files(repo, label):
            try:
                relative = source.relative_to(repo)
            except ValueError:
                continue
            if any(part in {"node_modules", "dist", ".angular", ".r4r"} for part in relative.parts):
                continue
            if total_bytes >= 8 * 1024 * 1024:
                break
            destination = target / "files" / relative
            total_bytes += _copy_snapshot_file(
                source,
                destination,
                manifest=manifest,
                label=label,
                relative=relative,
            )

    public_worktrees = {
        label: {
            "repository_name": repo.name,
            "branch": repository_summary[label]["branch"],
            "expected_branch": EXPECTED_BRANCHES[label],
            "head": repository_summary[label]["head"],
        }
        for label, repo in paths.as_dict().items()
    }
    _write_json(run_dir / "worktrees.json", public_worktrees)
    _write_json(run_dir / "snapshot-manifest.json", manifest)
    return {
        "repositories": repository_summary,
        "files_copied": len([item for item in manifest if "sha256" in item]),
        "manifest_errors": [item for item in manifest if "error" in item],
    }


def build_ring_prompt(run_dir: Path, run_id: str) -> str:
    output_dir = run_dir / "output"
    return f"""Perform one snapshot-only R4R Ring review.

RUN_ID: {run_id}
RUN_DIR: {run_dir}
EVIDENCE_DIR: {run_dir / 'evidence'}
OUTPUT_DIR: {output_dir}

The complete evidence for this execution is already inside RUN_DIR. Read only RUN_DIR.
Do not open any repository or worktree directly. Do not read opencode.console.log.
A permission denial is final and must never be retried through another tool or child path.

First inspect:
- {run_dir / 'worktrees.json'}
- {run_dir / 'snapshot-manifest.json'}
- the RING, PC and LP directories below EVIDENCE_DIR

Then write exactly the six artifacts required by the r4r-ring agent definition into
OUTPUT_DIR. state.json must use run_id {run_id!r}. Use `content` as the write-tool
content field. Stop immediately after all six files are written.
"""


def _extract_input_path(tool_input: Mapping[str, Any]) -> str:
    for key in ("filePath", "path", "target", "filename"):
        value = tool_input.get(key)
        if isinstance(value, str) and value:
            return value.rstrip(".,;:)")
    command = tool_input.get("command")
    if isinstance(command, str):
        match = ABS_PATH_RE.search(command)
        if match:
            return match.group(0).rstrip(".,;:)")
    return "<unknown>"


def normalize_tool_error(
    tool: str,
    tool_input: Mapping[str, Any],
    error: str,
    *,
    forbidden_roots: Iterable[Path],
) -> tuple[str, int]:
    clean_error = _strip_ansi(error).strip()
    path_text = _extract_input_path(tool_input)
    candidate_paths = [path_text, *ABS_PATH_RE.findall(clean_error)]

    for root in forbidden_roots:
        root_text = str(root.resolve())
        for candidate in candidate_paths:
            if candidate == root_text or candidate.startswith(root_text + os.sep):
                return f"external_directory|{root_text}", DEFAULT_EXTERNAL_DIRECTORY_THRESHOLD

    lowered = clean_error.lower()
    if "external_directory" in lowered or (
        "prevents you from using this specific tool call" in lowered and any(p.startswith("/") for p in candidate_paths)
    ):
        normalized_path = path_text if path_text.startswith("/") else "<external>"
        return f"external_directory|{normalized_path}", DEFAULT_EXTERNAL_DIRECTORY_THRESHOLD

    missing_match = re.search(r"missing key.*?[\[\"']([A-Za-z0-9_.-]+)", clean_error, re.IGNORECASE)
    if "schemaerror" in lowered or missing_match:
        missing = missing_match.group(1).lower() if missing_match else "unknown"
        return f"tool_schema|{tool}|missing:{missing}|{path_text}", DEFAULT_TOOL_ERROR_THRESHOLD

    normalized = VOLATILE_RE.sub("<volatile>", clean_error)
    normalized = re.sub(r"\s+", " ", normalized).strip()
    normalized = normalized[:500]
    return f"tool_error|{tool}|{normalized}", DEFAULT_TOOL_ERROR_THRESHOLD


def _terminate_process_group(process: subprocess.Popen[str], grace_seconds: float = 5.0) -> None:
    if process.poll() is not None:
        return
    try:
        os.killpg(process.pid, signal.SIGTERM)
    except ProcessLookupError:
        return
    deadline = time.monotonic() + grace_seconds
    while process.poll() is None and time.monotonic() < deadline:
        time.sleep(0.1)
    if process.poll() is None:
        try:
            os.killpg(process.pid, signal.SIGKILL)
        except ProcessLookupError:
            pass


def _operator_stop_requested(command_file: Path, started_wall: float) -> bool:
    try:
        if not command_file.is_file() or command_file.stat().st_mtime < started_wall:
            return False
        text = command_file.read_text(encoding="utf-8", errors="replace")
    except OSError:
        return False
    stop = re.search(r'"next_state"\s*:\s*"stop"', text, re.IGNORECASE)
    target = re.search(r'"target"\s*:\s*"(RING|ALL)"', text, re.IGNORECASE)
    return bool(stop and target)


def monitor_opencode(
    process: subprocess.Popen[str],
    *,
    log_path: Path,
    forbidden_roots: Iterable[Path],
    timeout_seconds: int,
    idle_timeout_seconds: int,
    operator_command_file: Path,
) -> MonitorOutcome:
    line_queue: queue.Queue[str | None] = queue.Queue()

    def reader() -> None:
        assert process.stdout is not None
        try:
            for line in iter(process.stdout.readline, ""):
                line_queue.put(line)
        finally:
            line_queue.put(None)

    threading.Thread(target=reader, name="ring-opencode-reader", daemon=True).start()

    started = time.monotonic()
    started_wall = time.time()
    last_output = started
    counts: Counter[str] = Counter()
    terminal_status: str | None = None
    terminal_error: dict[str, Any] | None = None
    fallback_detected = False
    eof = False

    log_path.parent.mkdir(parents=True, exist_ok=True)
    with log_path.open("w", encoding="utf-8") as log:
        try:
            while True:
                now = time.monotonic()
                if terminal_status is None and now - started >= timeout_seconds:
                    terminal_status = STATUS_TIMEOUT
                    terminal_error = {"code": "SESSION_TIMEOUT", "limit_seconds": timeout_seconds}
                    _terminate_process_group(process)

                if terminal_status is None and now - last_output >= idle_timeout_seconds:
                    terminal_status = STATUS_TIMEOUT
                    terminal_error = {"code": "IDLE_TIMEOUT", "limit_seconds": idle_timeout_seconds}
                    _terminate_process_group(process)

                if terminal_status is None and _operator_stop_requested(operator_command_file, started_wall):
                    terminal_status = STATUS_INTERRUPTED
                    terminal_error = {"code": "OPERATOR_COMMAND_STOP"}
                    _terminate_process_group(process)

                try:
                    line = line_queue.get(timeout=0.25)
                except queue.Empty:
                    line = ""

                if line is None:
                    eof = True
                elif line:
                    last_output = time.monotonic()
                    sys.stdout.write(line)
                    sys.stdout.flush()
                    log.write(line)
                    log.flush()

                    clean = _strip_ansi(line)
                    lower = clean.lower()
                    if "falling back to default agent" in lower or (
                        f'agent "{AGENT_NAME}" not found' in lower
                    ):
                        fallback_detected = True
                        if terminal_status is None:
                            terminal_status = STATUS_INVALID_AGENT
                            terminal_error = {
                                "code": "AGENT_FALLBACK_DETECTED",
                                "requested_agent": AGENT_NAME,
                            }
                            _terminate_process_group(process)

                    try:
                        event = json.loads(clean)
                    except json.JSONDecodeError:
                        event = None

                    if isinstance(event, dict) and event.get("type") == "tool_use":
                        part = event.get("part")
                        if isinstance(part, dict):
                            tool = str(part.get("tool") or "<unknown>")
                            state = part.get("state")
                            if isinstance(state, dict) and state.get("status") == "error":
                                tool_input = state.get("input")
                                if not isinstance(tool_input, dict):
                                    tool_input = {}
                                error = str(state.get("error") or "unknown tool error")
                                attempted_path = _extract_input_path(tool_input)
                                if Path(attempted_path).name == "opencode.console.log":
                                    signature, threshold = (
                                        f"supervisor_owned|opencode.console.log|{attempted_path}",
                                        1,
                                    )
                                else:
                                    signature, threshold = normalize_tool_error(
                                        tool,
                                        tool_input,
                                        error,
                                        forbidden_roots=forbidden_roots,
                                    )
                                counts[signature] += 1
                                if terminal_status is None and counts[signature] >= threshold:
                                    terminal_status = STATUS_REPEATED_ERROR
                                    terminal_error = {
                                        "code": "REPEATED_TOOL_ERROR",
                                        "signature": signature,
                                        "occurrences": counts[signature],
                                        "threshold": threshold,
                                    }
                                    _terminate_process_group(process)

                if eof and process.poll() is not None and line_queue.empty():
                    break
                if terminal_status is not None and process.poll() is not None and eof:
                    break

        except KeyboardInterrupt:
            terminal_status = STATUS_INTERRUPTED
            terminal_error = {"code": "KEYBOARD_INTERRUPT"}
            _terminate_process_group(process)

    try:
        exit_code = process.wait(timeout=10)
    except subprocess.TimeoutExpired:
        _terminate_process_group(process, grace_seconds=1)
        exit_code = process.poll()

    return MonitorOutcome(
        status=terminal_status,
        process_exit=exit_code,
        error=terminal_error,
        repeated_signatures=dict(counts),
        fallback_detected=fallback_detected,
    )


def _validate_markdown(path: Path) -> str | None:
    try:
        text = path.read_text(encoding="utf-8")
    except (OSError, UnicodeError) as exc:
        return f"unreadable UTF-8 Markdown: {exc}"
    if len(text.strip()) < 120:
        return "Markdown is empty or too small (<120 non-whitespace characters)"
    if not re.search(r"(?m)^#", text):
        return "Markdown has no heading"
    if re.fullmatch(r"(?is)\s*(todo|tbd|placeholder)[\s.!-]*", text):
        return "placeholder-only Markdown"
    return None


def _validate_state_json(path: Path, run_id: str) -> str | None:
    try:
        payload = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, UnicodeError, json.JSONDecodeError) as exc:
        return f"invalid JSON: {exc}"
    if not isinstance(payload, dict):
        return "top-level state must be an object"
    if payload.get("schema_version") != 1:
        return "schema_version must equal 1"
    if payload.get("run_id") != run_id:
        return f"run_id must equal {run_id!r}"
    if payload.get("overall_status") not in {"READY", "BLOCKED", "NO_ACTION"}:
        return "overall_status must be READY, BLOCKED or NO_ACTION"
    decisions = payload.get("decisions")
    if not isinstance(decisions, dict):
        return "decisions must be an object"
    allowed_actions = {"START", "CONTINUE", "HOLD", "STOP", "NO_ACTION"}
    for worker in ("PC", "LP"):
        decision = decisions.get(worker)
        if not isinstance(decision, dict):
            return f"decisions.{worker} must be an object"
        if decision.get("action") not in allowed_actions:
            return f"decisions.{worker}.action is invalid"
        if not isinstance(decision.get("reason"), str) or not decision["reason"].strip():
            return f"decisions.{worker}.reason must be non-empty"
        gates = decision.get("acceptance_gates")
        if not isinstance(gates, list) or not gates or not all(isinstance(item, str) and item.strip() for item in gates):
            return f"decisions.{worker}.acceptance_gates must be a non-empty string array"
        task_id = decision.get("task_id")
        if task_id is not None and not isinstance(task_id, str):
            return f"decisions.{worker}.task_id must be a string or null"
    for key in ("integration_risks", "evidence_limitations"):
        value = payload.get(key)
        if not isinstance(value, list) or not all(isinstance(item, str) for item in value):
            return f"{key} must be a string array"
    return None


def validate_artifacts(output_dir: Path, run_id: str) -> ArtifactValidation:
    result = ArtifactValidation()
    resolved_root = output_dir.resolve(strict=False)
    for filename in REQUIRED_OUTPUTS:
        path = output_dir / filename
        if not path.exists():
            result.missing.append(filename)
            continue
        if path.is_symlink() or not path.is_file() or not _safe_resolve_inside(path, resolved_root):
            result.invalid[filename] = "not a regular in-tree file"
            continue
        try:
            size = path.stat().st_size
        except OSError as exc:
            result.invalid[filename] = f"cannot stat: {exc}"
            continue
        if size > 2 * 1024 * 1024:
            result.invalid[filename] = "artifact exceeds 2 MiB"
            continue
        error = _validate_state_json(path, run_id) if filename == "state.json" else _validate_markdown(path)
        if error:
            result.invalid[filename] = error
        else:
            result.valid.append(filename)
    return result


def _atomic_promote(ring_repo: Path, output_dir: Path) -> list[str]:
    promoted: list[str] = []
    for source_name, destination_name in REQUIRED_OUTPUTS.items():
        source = output_dir / source_name
        destination = ring_repo / destination_name
        destination.parent.mkdir(parents=True, exist_ok=True)
        temporary = destination.with_name(destination.name + ".ring-tmp")
        shutil.copyfile(source, temporary)
        os.replace(temporary, destination)
        promoted.append(destination_name)
    return promoted


def _classify_final_status(
    monitor: MonitorOutcome,
    artifacts: ArtifactValidation,
) -> tuple[str, list[str], dict[str, Any] | None]:
    flags: list[str] = []
    if artifacts.has_any_valid and not artifacts.complete:
        flags.append(STATUS_PARTIAL)

    if monitor.status is not None:
        return monitor.status, flags, monitor.error
    if artifacts.complete and monitor.process_exit == 0:
        return STATUS_SUCCESS, flags, None
    if artifacts.has_any_valid:
        if STATUS_PARTIAL not in flags:
            flags.append(STATUS_PARTIAL)
        return STATUS_PARTIAL, flags, {
            "code": "PROCESS_ENDED_WITH_PARTIAL_ARTIFACTS",
            "process_exit": monitor.process_exit,
        }
    return STATUS_INVALID_ARTIFACT, flags, {
        "code": "FINAL_ARTIFACT_VALIDATION_FAILED",
        "process_exit": monitor.process_exit,
    }


def _extract_paths(worktrees: Any) -> StabilizedPaths:
    if isinstance(worktrees, StabilizedPaths):
        return worktrees
    if isinstance(worktrees, (tuple, list)) and len(worktrees) == 3:
        return StabilizedPaths(*(Path(value) for value in worktrees))

    mappings: list[Mapping[str, Any]] = []
    if isinstance(worktrees, Mapping):
        mappings.append(worktrees)
    if dataclasses.is_dataclass(worktrees):
        mappings.append(dataclasses.asdict(worktrees))
    try:
        mappings.append(vars(worktrees))
    except TypeError:
        pass

    key_sets = (
        ("ring", "pc", "lp"),
        ("ring_worktree", "pc_worktree", "lp_worktree"),
        ("ring_path", "pc_path", "lp_path"),
        ("RING", "PC", "LP"),
    )
    for mapping in mappings:
        for keys in key_sets:
            if all(key in mapping for key in keys):
                return StabilizedPaths(*(Path(mapping[key]) for key in keys))
    raise TypeError(f"Cannot extract RING/PC/LP paths from {type(worktrees).__name__}")


class RingStabilizationSupervisor:
    def __init__(
        self,
        paths: StabilizedPaths,
        *,
        timeout_seconds: int = DEFAULT_SESSION_TIMEOUT_SECONDS,
        idle_timeout_seconds: int = DEFAULT_IDLE_TIMEOUT_SECONDS,
        strict_model_check: bool = True,
        agent_name: str = AGENT_NAME,
    ) -> None:
        self.paths = paths
        self.timeout_seconds = timeout_seconds
        self.idle_timeout_seconds = idle_timeout_seconds
        self.strict_model_check = strict_model_check
        self.agent_name = agent_name

    def _write_preflight_failure(
        self,
        run_dir: Path,
        *,
        code: str,
        detail: Any,
        preflight: AgentPreflight | None = None,
    ) -> int:
        result = {
            "schema_version": 1,
            "run_id": run_dir.name,
            "started_at": _utc_now(),
            "finished_at": _utc_now(),
            "status": STATUS_INVALID_AGENT,
            "flags": [],
            "error": {"code": code, "detail": detail},
            "agent": dataclasses.asdict(preflight) if preflight else None,
            "artifacts": {"valid": [], "missing": list(REQUIRED_OUTPUTS), "invalid": {}},
            "promoted": [],
        }
        _write_json(run_dir / "supervisor-result.json", result)
        print(json.dumps(result, ensure_ascii=False, indent=2), file=sys.stderr)
        return EXIT_CODES[STATUS_INVALID_AGENT]

    def run_once(self) -> int:
        run_id, run_dir = _allocate_run_dir(self.paths.ring)
        output_dir = run_dir / "output"
        output_dir.mkdir(parents=True, exist_ok=False)

        worktree_errors = validate_worktrees(self.paths)
        if worktree_errors:
            return self._write_preflight_failure(
                run_dir,
                code="INVALID_WORKTREE_CONFIGURATION",
                detail=worktree_errors,
            )

        preflight = validate_opencode_agent(
            self.paths.ring,
            agent_name=self.agent_name,
            strict_model_check=self.strict_model_check,
        )
        _write_text(run_dir / "agent-list.txt", preflight.agent_list_output)
        if preflight.model_list_output:
            _write_text(run_dir / "model-list.txt", preflight.model_list_output)
        if not preflight.valid:
            return self._write_preflight_failure(
                run_dir,
                code=preflight.code or "INVALID_AGENT",
                detail=preflight.detail,
                preflight=preflight,
            )

        snapshot = collect_snapshot(self.paths, run_dir)
        _write_json(run_dir / "snapshot-summary.json", snapshot)
        prompt = build_ring_prompt(run_dir, run_id)
        _write_text(run_dir / "ring-prompt.txt", prompt)

        command = [
            "opencode",
            "run",
            "--dir",
            str(self.paths.ring),
            "--agent",
            self.agent_name,
            "--format",
            "json",
            "--model",
            preflight.model or DEFAULT_MODEL,
            "--auto",
            prompt,
        ]
        _write_json(run_dir / "command.json", command)

        started_at = _utc_now()
        try:
            process = subprocess.Popen(
                command,
                cwd=str(self.paths.ring),
                text=True,
                stdout=subprocess.PIPE,
                stderr=subprocess.STDOUT,
                bufsize=1,
                start_new_session=True,
            )
        except FileNotFoundError:
            return self._write_preflight_failure(
                run_dir,
                code="OPENCODE_NOT_FOUND",
                detail="opencode executable not found at launch",
                preflight=preflight,
            )

        monitor = monitor_opencode(
            process,
            log_path=run_dir / "opencode.console.log",
            forbidden_roots=(self.paths.pc, self.paths.lp),
            timeout_seconds=self.timeout_seconds,
            idle_timeout_seconds=self.idle_timeout_seconds,
            operator_command_file=self.paths.ring / "runtime" / "the-ring-command.jsonc",
        )

        artifacts = validate_artifacts(output_dir, run_id)
        status, flags, error = _classify_final_status(monitor, artifacts)
        promoted: list[str] = []
        if status == STATUS_SUCCESS:
            promoted = _atomic_promote(self.paths.ring, output_dir)

        result = {
            "schema_version": 1,
            "run_id": run_id,
            "started_at": started_at,
            "finished_at": _utc_now(),
            "status": status,
            "flags": flags,
            "error": error,
            "agent": {
                "name": self.agent_name,
                "mode": "primary",
                "model": preflight.model,
                "fallback_detected": monitor.fallback_detected,
            },
            "process": {
                "exit_code": monitor.process_exit,
                "timeout_seconds": self.timeout_seconds,
                "idle_timeout_seconds": self.idle_timeout_seconds,
            },
            "repeated_error_signatures": monitor.repeated_signatures,
            "artifacts": {
                "valid": artifacts.valid,
                "missing": artifacts.missing,
                "invalid": artifacts.invalid,
            },
            "promoted": promoted,
            "snapshot": snapshot,
        }
        _write_json(run_dir / "supervisor-result.json", result)
        print("\n===== R4R RING SUPERVISOR RESULT =====")
        print(json.dumps(result, ensure_ascii=False, indent=2))
        return EXIT_CODES[status]


def run_stabilized_ring_loop(
    worktrees: Any,
    once: bool = False,
    *,
    interval_seconds: int = DEFAULT_INTERVAL_SECONDS,
    session_timeout_seconds: int = DEFAULT_SESSION_TIMEOUT_SECONDS,
    idle_timeout_seconds: int = DEFAULT_IDLE_TIMEOUT_SECONDS,
    strict_model_check: bool = True,
) -> int:
    paths = _extract_paths(worktrees)
    supervisor = RingStabilizationSupervisor(
        paths,
        timeout_seconds=session_timeout_seconds,
        idle_timeout_seconds=idle_timeout_seconds,
        strict_model_check=strict_model_check,
    )
    while True:
        code = supervisor.run_once()
        if once:
            return code
        if code == EXIT_CODES[STATUS_INTERRUPTED]:
            return code
        time.sleep(interval_seconds)


def _parser() -> argparse.ArgumentParser:
    home = Path.home() / "Desarrollo"
    parser = argparse.ArgumentParser(description="Run the stabilized snapshot-only R4R Ring supervisor")
    parser.add_argument("--ring-worktree", type=Path, default=home / "r4r-ring-agent.git")
    parser.add_argument("--pc-worktree", type=Path, default=home / "r4r-pc-worker.git")
    parser.add_argument("--lp-worktree", type=Path, default=home / "r4r-lp-worker.git")
    parser.add_argument("--once", action="store_true", help="Run one Ring session and exit")
    parser.add_argument("--interval-seconds", type=int, default=DEFAULT_INTERVAL_SECONDS)
    parser.add_argument("--timeout-seconds", type=int, default=DEFAULT_SESSION_TIMEOUT_SECONDS)
    parser.add_argument("--idle-timeout-seconds", type=int, default=DEFAULT_IDLE_TIMEOUT_SECONDS)
    parser.add_argument("--skip-model-check", action="store_true")
    return parser


def main(argv: Sequence[str] | None = None) -> int:
    args = _parser().parse_args(argv)
    paths = StabilizedPaths(args.ring_worktree, args.pc_worktree, args.lp_worktree)
    return run_stabilized_ring_loop(
        paths,
        once=args.once,
        interval_seconds=args.interval_seconds,
        session_timeout_seconds=args.timeout_seconds,
        idle_timeout_seconds=args.idle_timeout_seconds,
        strict_model_check=not args.skip_model_check,
    )


if __name__ == "__main__":
    raise SystemExit(main())
