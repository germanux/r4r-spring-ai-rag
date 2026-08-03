from __future__ import annotations

from contextlib import contextmanager
from dataclasses import asdict, dataclass
from datetime import datetime, timezone
from fnmatch import fnmatch
import fcntl
import hashlib
import json
import os
from pathlib import Path
import re
import shlex
import shutil
import signal
import subprocess
import sys
import threading
import time
from typing import Any, Sequence
from urllib.parse import urlparse

from .contracts import Task, TaskPlan, load_progress, task_progress, validate_structured_result
from .diagnostics import GateDiagnostics, build_gate_diagnostics


@dataclass(frozen=True)
class CommandResult:
    command: tuple[str, ...]
    exit_code: int
    stdout: str
    stderr: str
    timed_out: bool = False
    stop_reason: str | None = None
    observed_steps: int = 0
    meaningful_events: int = 0


@dataclass(frozen=True)
class CommandWatchdog:
    """Bound one streamed OpenCode session by wall time and useful activity.

    `step_start` events are deliberately not treated as progress. This prevents a
    stalled JSONL stream from keeping a worker alive forever merely by emitting
    periodic start markers.
    """

    max_seconds: int = 0
    idle_seconds: int = 0
    max_steps: int = 0
    repeat_event_budget: int = 0


def _opencode_event_observation(line: str) -> tuple[bool, bool, str]:
    """Return (is_step_start, is_meaningful, normalized_signature)."""
    stripped = line.strip()
    if not stripped:
        return False, False, "empty"
    try:
        value = json.loads(stripped)
    except json.JSONDecodeError:
        return False, True, "plain:" + hashlib.sha256(
            stripped.encode("utf-8", errors="replace")
        ).hexdigest()[:16]

    if not isinstance(value, dict):
        return False, True, "json-non-object"
    part = value.get("part") if isinstance(value.get("part"), dict) else {}
    event_type = str(value.get("type") or part.get("type") or "unknown")
    normalized_type = event_type.replace("-", "_").lower()
    if normalized_type in {"step_start", "stepstart"}:
        return True, False, "step_start"

    tool_name = str(value.get("tool") or part.get("tool") or "").strip()
    state = part.get("state") if isinstance(part.get("state"), dict) else {}
    if not state and isinstance(value.get("state"), dict):
        state = value["state"]
    if tool_name or normalized_type in {"tool", "tool_use", "tooluse"}:
        payload = state.get("input", {})
        try:
            encoded = json.dumps(payload, sort_keys=True, ensure_ascii=False)
        except TypeError:
            encoded = repr(payload)
        signature = hashlib.sha256(encoded.encode("utf-8", errors="replace")).hexdigest()[:12]
        status = str(state.get("status") or "unknown")
        return False, True, f"tool:{tool_name or 'unknown'}:{status}:{signature}"

    text_value = part.get("text") if isinstance(part.get("text"), str) else value.get("text")
    if isinstance(text_value, str) and text_value.strip():
        signature = hashlib.sha256(
            text_value.strip().encode("utf-8", errors="replace")
        ).hexdigest()[:16]
        return False, True, f"text:{signature}"

    if normalized_type in {"step_finish", "stepfinish", "message", "error"}:
        reason = str(part.get("reason") or value.get("reason") or "")
        return False, True, f"{normalized_type}:{reason}"
    return False, True, f"event:{normalized_type}"


def run_command(
    command: Sequence[str], cwd: Path, input_text: str | None = None,
    timeout_seconds: int | None = None, stream: bool = False,
    env: dict[str, str] | None = None,
    watchdog: CommandWatchdog | None = None,
) -> CommandResult:
    if not command:
        raise ValueError("Command cannot be empty")
    process = subprocess.Popen(
        list(command), cwd=cwd,
        stdin=subprocess.PIPE if input_text is not None else None,
        stdout=subprocess.PIPE, stderr=subprocess.PIPE,
        text=True, bufsize=1, start_new_session=True, env=env,
    )
    stdout_parts: list[str] = []
    stderr_parts: list[str] = []
    state_lock = threading.Lock()
    started = time.monotonic()
    watch_state: dict[str, Any] = {
        "last_meaningful": started,
        "steps": 0,
        "meaningful": 0,
        "last_signature": None,
        "repeat_count": 0,
        "stop_reason": None,
    }

    def observe(line: str) -> None:
        if watchdog is None:
            return
        is_step, meaningful, signature = _opencode_event_observation(line)
        now = time.monotonic()
        with state_lock:
            if is_step:
                watch_state["steps"] += 1
            if meaningful:
                watch_state["meaningful"] += 1
                watch_state["last_meaningful"] = now
            if signature == watch_state["last_signature"]:
                watch_state["repeat_count"] += 1
            else:
                watch_state["last_signature"] = signature
                watch_state["repeat_count"] = 1
            if (
                watchdog.repeat_event_budget > 0
                and watch_state["repeat_count"] > watchdog.repeat_event_budget
            ):
                watch_state["stop_reason"] = "repeat-event-budget"

    def consume(pipe: Any, target: list[str], display: Any, *, inspect: bool) -> None:
        try:
            for line in iter(pipe.readline, ""):
                target.append(line)
                if inspect:
                    observe(line)
                if stream:
                    print(line, end="", file=display, flush=True)
        finally:
            pipe.close()

    threads = [
        threading.Thread(
            target=consume,
            args=(process.stdout, stdout_parts, sys.stdout),
            kwargs={"inspect": True},
            daemon=True,
        ),
        threading.Thread(
            target=consume,
            args=(process.stderr, stderr_parts, sys.stderr),
            kwargs={"inspect": False},
            daemon=True,
        ),
    ]
    for thread in threads:
        thread.start()
    if input_text is not None and process.stdin is not None:
        process.stdin.write(input_text)
        process.stdin.close()

    stop_reason: str | None = None
    while process.poll() is None:
        now = time.monotonic()
        elapsed = now - started
        with state_lock:
            observed_steps = int(watch_state["steps"])
            last_meaningful = float(watch_state["last_meaningful"])
            requested_stop = watch_state["stop_reason"]
        if timeout_seconds is not None and timeout_seconds > 0 and elapsed >= timeout_seconds:
            stop_reason = "command-timeout"
        elif watchdog is not None and watchdog.max_seconds > 0 and elapsed >= watchdog.max_seconds:
            stop_reason = "session-timeout"
        elif (
            watchdog is not None
            and watchdog.idle_seconds > 0
            and now - last_meaningful >= watchdog.idle_seconds
        ):
            stop_reason = "idle-timeout"
        elif (
            watchdog is not None
            and watchdog.max_steps > 0
            and observed_steps >= watchdog.max_steps
        ):
            stop_reason = "step-limit"
        elif requested_stop:
            stop_reason = str(requested_stop)
        if stop_reason is not None:
            try:
                os.killpg(process.pid, signal.SIGTERM)
            except ProcessLookupError:
                pass
            try:
                process.wait(timeout=10)
            except subprocess.TimeoutExpired:
                try:
                    os.killpg(process.pid, signal.SIGKILL)
                except ProcessLookupError:
                    pass
                process.wait()
            break
        time.sleep(0.2)

    exit_code = process.wait()
    timed_out = stop_reason is not None
    if timed_out:
        exit_code = 124
        stderr_parts.append(
            "\n[r4r-watchdog] stopped OpenCode session: "
            f"reason={stop_reason} elapsed={time.monotonic() - started:.1f}s\n"
        )
    for thread in threads:
        thread.join(timeout=5)
    with state_lock:
        steps = int(watch_state["steps"])
        meaningful_events = int(watch_state["meaningful"])
    return CommandResult(
        tuple(command),
        exit_code,
        "".join(stdout_parts),
        "".join(stderr_parts),
        timed_out,
        stop_reason,
        steps,
        meaningful_events,
    )


def _nul_paths(result: CommandResult) -> set[str]:
    if result.exit_code != 0:
        raise RuntimeError(result.stderr.strip() or "Git path query failed")
    return {value for value in result.stdout.split("\0") if value}


def git_changed_paths(repo: Path) -> tuple[str, ...]:
    changed: set[str] = set()
    changed |= _nul_paths(run_command(("git", "diff", "--name-only", "-z"), repo))
    changed |= _nul_paths(run_command(("git", "diff", "--cached", "--name-only", "-z"), repo))
    changed |= _nul_paths(run_command(("git", "ls-files", "--others", "--exclude-standard", "-z"), repo))
    return tuple(sorted(changed))


def git_head(repo: Path) -> str | None:
    result = run_command(("git", "rev-parse", "HEAD"), repo)
    return result.stdout.strip() if result.exit_code == 0 else None


def git_worktree_fingerprint(
    repo: Path,
    ignored_patterns: Sequence[str] = (),
) -> str:
    """Fingerprint current non-maintenance changes while ignoring peer-owned paths."""
    # Git history is validated independently. Keeping HEAD out of this digest
    # prevents a safe peer commit from looking like a local file write.
    digest = hashlib.sha256()
    for relative in git_changed_paths(repo):
        if is_controller_runtime_path(relative) or is_lock_auto_advance_path(relative):
            continue
        if path_is_allowed(relative, ignored_patterns):
            continue
        digest.update(relative.encode("utf-8", errors="surrogateescape"))
        path = repo / relative
        tracked = run_command(
            ("git", "diff", "--binary", "HEAD", "--", relative), repo
        )
        if tracked.exit_code not in (0, 1):
            raise RuntimeError(
                tracked.stderr.strip() or f"Unable to fingerprint {relative}"
            )
        digest.update(tracked.stdout.encode("utf-8", errors="surrogateescape"))
        if path.is_file() and not tracked.stdout:
            digest.update(path.read_bytes())
        elif path.is_symlink():
            digest.update(os.readlink(path).encode("utf-8", errors="surrogateescape"))
    return digest.hexdigest()


def path_is_allowed(path: str, allowed_patterns: Sequence[str]) -> bool:
    return any(fnmatch(path, pattern) for pattern in allowed_patterns)


# Files generated by the controller are operational state, not product changes.
# They must never be attributed to OpenCode during task-scope validation.
CONTROLLER_RUNTIME_PATHS = (
    "runtime/runs/**",
    "runtime/locks/**",
    "runtime/control/**",
)


def is_controller_runtime_path(path: str) -> bool:
    return path_is_allowed(path, CONTROLLER_RUNTIME_PATHS)


def git_product_changed_paths(repo: Path) -> tuple[str, ...]:
    return tuple(
        path for path in git_changed_paths(repo)
        if not is_controller_runtime_path(path)
    )


MAINTENANCE_PATHS = (
    "AGENTS.md",
    "scripts/**",
    "py-codex-agent/**",
    ".opencode/**",
    ".env",
    ".env.example",
    ".env.r4r.local",
    ".env.r4r.local.example",
    "config/**",
    "package.json",
    "opencode.jsonc",
    "codegraph.json",
    ".gitignore",
    "patches-applied/**",
    "payload/**",
    "install-r4r-*.sh",
    "apply-r4r-*.sh",
    "fix-r4r-*.sh",
    "r4r-agent-*.zip",
    "README.txt",
    "README-DUAL-AGENTS.md",
    "docs/dual-agent-code-intelligence.md",
    "SHA256SUMS.txt",
)

# Backward-compatible alias used by existing diagnostics/tests. There is no active
# task lock anymore; this function now classifies controller/config maintenance.
LOCK_AUTO_ADVANCE_PATHS = MAINTENANCE_PATHS

# Browser downloads and manual extraction can accidentally commit a mirror of a
# maintenance bundle under a top-level directory. Such a mirror is maintenance
# only when its path, after stripping the bundle directory, is itself one of the
# canonical maintenance paths above. Product files under the bundle directory do
# not qualify.
_MAINTENANCE_BUNDLE_PREFIX = re.compile(
    r"^(?:r4r-(?:agent|dual-agent)-[^/]+|r4r-self-recovery)/(.+)$"
)
_MAINTENANCE_ARTIFACT_PATH = re.compile(
    r"^(?:(?:install|apply|fix)-r4r-[^/]+\.sh"
    r"|r4r-[^/]+\.zip(?:\.sha256)?"
    r"|r4r-[^/]+\.sha256"
    r"|r4r-[^/]+\.patch"
    r"|SHA256SUMS\.txt|README\.txt)$"
)


def is_lock_auto_advance_path(path: str) -> bool:
    """Return True for controller/configuration maintenance paths.

    The historical name is retained to avoid breaking callers, but active-task
    lock advancement has been removed.
    """
    if path_is_allowed(path, MAINTENANCE_PATHS):
        return True
    if _MAINTENANCE_ARTIFACT_PATH.fullmatch(path):
        return True
    match = _MAINTENANCE_BUNDLE_PREFIX.fullmatch(path)
    return bool(match and path_is_allowed(match.group(1), MAINTENANCE_PATHS))


def git_paths_between(repo: Path, base: str, head: str) -> tuple[str, ...]:
    # Inspect every commit in the fast-forward range, not only the net tree diff.
    # This prevents an unsafe path changed and later reverted from disappearing.
    result = run_command(
        ("git", "log", "--format=", "--name-only", "-z", f"{base}..{head}"),
        repo,
    )
    if result.exit_code != 0:
        raise RuntimeError(result.stderr.strip() or "Git history path query failed")
    paths = {
        value.strip("\n")
        for value in result.stdout.split("\0")
        if value.strip("\n")
    }
    return tuple(sorted(paths))


def git_is_ancestor(repo: Path, ancestor: str, descendant: str) -> bool:
    result = run_command(("git", "merge-base", "--is-ancestor", ancestor, descendant), repo)
    return result.exit_code == 0


@contextmanager
def exclusive_file_lock(path: Path):
    """Serialize cooperating controller Git index/history writes."""
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("a+", encoding="utf-8") as handle:
        fcntl.flock(handle.fileno(), fcntl.LOCK_EX)
        try:
            yield
        finally:
            fcntl.flock(handle.fileno(), fcntl.LOCK_UN)


def codex_exec_command(binary: str, schema: Path, output: Path, model: str | None = None) -> tuple[str, ...]:
    command = [
        binary, "exec", "--sandbox", "read-only", "--ephemeral",
        "--output-schema", str(schema), "-o", str(output),
    ]
    if model:
        command.extend(("--model", model))
    command.append("-")
    return tuple(command)


def extract_opencode_text(stdout: str) -> str:
    """Extract the model-authored text from OpenCode JSONL output."""
    parts: list[str] = []
    for raw_line in stdout.splitlines():
        try:
            event = json.loads(raw_line)
        except json.JSONDecodeError:
            continue
        part = event.get("part")
        if not isinstance(part, dict) or part.get("type") != "text":
            continue
        text = part.get("text")
        if isinstance(text, str) and text.strip():
            value = text.strip()
            if "# Local understanding report" in value:
                value = value[value.index("# Local understanding report"):].strip()
            parts.append(value)
    for index, value in enumerate(parts):
        if "# Local understanding report" in value:
            return "\n\n".join(parts[index:]).strip()
    return "\n\n".join(parts).strip()


def extract_codegraph_tool_calls(stdout: str) -> tuple[str, ...]:
    """Return actual CodeGraph tool identifiers found in OpenCode JSONL events.

    The traversal deliberately accepts different OpenCode event shapes, but only
    records exact string values beginning with ``codegraph_``. Merely mentioning
    CodeGraph in model prose or in the prompt does not satisfy the requirement.
    """
    calls: set[str] = set()

    def visit(value: Any) -> None:
        if isinstance(value, dict):
            for nested in value.values():
                visit(nested)
            return
        if isinstance(value, list):
            for nested in value:
                visit(nested)
            return
        if (
            isinstance(value, str)
            and value.startswith("codegraph_")
            and value.replace("_", "").replace("-", "").replace(".", "").isalnum()
        ):
            calls.add(value)

    for raw_line in stdout.splitlines():
        try:
            visit(json.loads(raw_line))
        except json.JSONDecodeError:
            continue
    return tuple(sorted(calls))


class AutomaticRunner:
    def __init__(self, repo: Path, plan: TaskPlan, progress_path: Path):
        self.repo = repo.resolve()
        self.worker_id = os.environ.get("R4R_WORKER_ID", "PC").strip().upper() or "PC"
        self.git_author_name = os.environ.get(
            "R4R_GIT_AUTHOR_NAME", ""
        ).strip()
        self.git_author_email = os.environ.get(
            "R4R_GIT_AUTHOR_EMAIL", ""
        ).strip()
        if (
            not self.git_author_name
            or not self.git_author_email
            or "\n" in self.git_author_name
            or "\r" in self.git_author_name
            or "\n" in self.git_author_email
            or "\r" in self.git_author_email
            or "@" not in self.git_author_email
        ):
            raise ValueError(
                "R4R_GIT_AUTHOR_NAME and R4R_GIT_AUTHOR_EMAIL must define "
                f"a valid single-line Git identity for worker {self.worker_id}"
            )
        try:
            peer_value = json.loads(os.environ.get("R4R_PEER_PATHS_JSON", "[]"))
        except json.JSONDecodeError as exception:
            raise ValueError("R4R_PEER_PATHS_JSON must be a JSON array") from exception
        if not isinstance(peer_value, list) or not all(isinstance(v, str) for v in peer_value):
            raise ValueError("R4R_PEER_PATHS_JSON must be a JSON string array")
        self.peer_paths = tuple(peer_value)
        self.plan_display = os.environ.get("R4R_PLAN_DISPLAY", ".opencode/task-plan.json")
        self.plan = plan
        self.progress_path = progress_path
        self.progress = load_progress(progress_path, (task.id for task in plan.tasks))
        self.timeout = int(os.environ.get("R4R_COMMAND_TIMEOUT_SECONDS", "14400"))
        # Every OpenCode invocation is already a fresh local-model session. Keep
        # revising the same active task across fresh sessions instead of stopping
        # after an arbitrary two-pass limit. Set to 0 for no controller cap.
        self.max_attempts = int(os.environ.get("R4R_MAX_ATTEMPTS_PER_TASK", "12"))
        self.max_transient_failures = int(
            os.environ.get("R4R_MAX_TRANSIENT_FAILURES", "3")
        )
        self.max_no_progress_cycles = int(
            os.environ.get("R4R_MAX_NO_PROGRESS_CYCLES", "3")
        )
        self.compact_revision_context = (
            os.environ.get("R4R_COMPACT_REVISION_CONTEXT", "true").lower()
            == "true"
        )
        self.max_tasks = int(os.environ.get("R4R_MAX_TASKS_PER_RUN", "0"))
        self.auto_commit = os.environ.get("R4R_AUTO_COMMIT", "true").lower() == "true"
        self.bootstrap_commit = os.environ.get("R4R_BOOTSTRAP_COMMIT", "true").lower() == "true"
        self.checkpoint_on_green = (
            os.environ.get("R4R_CHECKPOINT_ON_GREEN", "true").lower() == "true"
        )

        worker_prefix = f"R4R_{self.worker_id}_"
        def worker_int(name: str, default: int) -> int:
            worker_value = os.environ.get(worker_prefix + name)
            generic_value = os.environ.get("R4R_OPENCODE_" + name)
            raw = worker_value if worker_value not in (None, "") else generic_value
            return int(raw) if raw not in (None, "") else default

        self.opencode_session_seconds = worker_int("MAX_SESSION_SECONDS", 5400)
        self.opencode_idle_seconds = worker_int("IDLE_SECONDS", 900)
        self.opencode_max_steps = worker_int("MAX_SESSION_STEPS", 120)
        self.opencode_repeat_event_budget = worker_int("REPEAT_EVENT_BUDGET", 12)
        self.memory_context: dict[str, Any] = {
            "task_id": None,
            "attempt": None,
            "gate_exit": None,
            "gate_name": None,
            "codex_decision": None,
            "changed_paths": [],
            "demonstrated": [],
            "outstanding": [],
            "avoid_repeating": [],
            "next_action": None,
            "checkpoint_head": None,
            "checkpoint_status": "none",
        }
        self.opencode_bin = os.environ.get("R4R_OPENCODE_BIN", "opencode")
        self.opencode_agent = os.environ.get("R4R_OPENCODE_AGENT", "r4r-pc")
        self.compact_local_worker = (
            os.environ.get(
                "R4R_COMPACT_LOCAL_WORKER",
                "true" if self.opencode_agent == "r4r-laptop" else "false",
            ).lower()
            == "true"
        )
        self.codex_bin = os.environ.get("R4R_CODEX_BIN", "codex")
        self.codex_model = os.environ.get("R4R_CODEX_MODEL", "").strip() or None
        self.codegraph_bin = os.environ.get("R4R_CODEGRAPH_BIN", "codegraph")
        self.require_codegraph = (
            os.environ.get("R4R_REQUIRE_CODEGRAPH", "true").lower() == "true"
        )
        self.codegraph_policy = os.environ.get(
            "R4R_CODEGRAPH_POLICY", "advisory"
        ).strip().lower()
        if self.codegraph_policy not in {"off", "advisory", "required"}:
            raise ValueError("R4R_CODEGRAPH_POLICY must be off, advisory or required")
        self.codegraph_retries = int(
            os.environ.get("R4R_CODEGRAPH_RETRIES", "1")
        )
        self.codex_min_interval_seconds = int(
            os.environ.get("R4R_CODEX_MIN_INTERVAL_SECONDS", "3600")
        )
        stamp = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
        self.run_id = stamp
        self.run_dir = self.repo / "runtime" / "runs" / self.worker_id / stamp
        self.run_dir.mkdir(parents=True, exist_ok=False)
        # Legacy path is retained only so old locks can be deleted safely.
        # Task continuation is recorded in .opencode/progress.json.
        self.lock_path = self.repo / "runtime" / "locks" / "active-task.json"
        self.git_commit_lock_path = (
            self.repo / "runtime" / "locks" / "git-commit.lock"
        )
        self.active_task_lock_enabled = False
        memory_value = Path(os.environ.get("R4R_MEMORY_PATH", ".opencode/memory.md"))
        self.memory_path = memory_value if memory_value.is_absolute() else self.repo / memory_value
        self.notify_script = self.repo / "scripts" / "notify-success.sh"
        self.control_dir = self.repo / "runtime" / "control" / self.worker_id
        self.control_dir.mkdir(parents=True, exist_ok=True)
        self.codex_extra_instructions_path = (
            self.control_dir / "codex-qwen3-extra-instructions.md"
        )
        self.codex_plan_cache_path = self.control_dir / "codex-plan-cache.json"
        ring_value = os.environ.get("R4R_RING_WORKTREE", "").strip()
        self.ring_worktree = (
            Path(ring_value).expanduser().resolve() if ring_value else None
        )
        self.ring_directive_path = (
            self.ring_worktree
            / "runtime"
            / "control"
            / self.worker_id
            / "ring-qwen3-directive.json"
            if self.ring_worktree is not None
            else None
        )
        self.ring_directive_max_age_seconds = int(
            os.environ.get("R4R_RING_DIRECTIVE_MAX_AGE_SECONDS", "10800")
        )
        self.ring_request_path = (
            self.ring_worktree
            / "runtime"
            / "control"
            / "RING"
            / "requests"
            / f"{self.worker_id}.json"
            if self.ring_worktree is not None
            else None
        )
        self.verified_green: set[str] = set()

    def _worktree_fingerprint(self) -> str:
        return git_worktree_fingerprint(self.repo, getattr(self, "peer_paths", ()))

    def _accept_safe_external_head_change(
        self,
        before_head: str | None,
        phase: str,
    ) -> bool:
        """Accept only fast-forward commits confined to peer/maintenance paths.

        This avoids falsely blaming the current OpenCode session when the other
        worker or a human maintenance commit advances the shared worktree HEAD.
        Worker-owned product commits, rewrites and branch switches remain fatal.
        """
        after_head = git_head(self.repo)
        if after_head == before_head:
            return False
        if before_head is None or after_head is None:
            raise RuntimeError(
                f"{phase}: Git HEAD became unavailable or was newly created"
            )
        if not git_is_ancestor(self.repo, before_head, after_head):
            raise RuntimeError(
                f"{phase}: non-fast-forward Git history change detected "
                f"({before_head} -> {after_head})"
            )

        committed_paths = git_paths_between(self.repo, before_head, after_head)
        safe_patterns = (
            *getattr(self, "peer_paths", ()),
            *MAINTENANCE_PATHS,
        )
        unsafe_paths = tuple(
            path
            for path in committed_paths
            if not is_controller_runtime_path(path)
            and not path_is_allowed(path, safe_patterns)
        )
        if unsafe_paths:
            raise RuntimeError(
                f"{phase}: Git HEAD advanced through worker-owned or mixed paths: "
                f"{list(unsafe_paths)}"
            )

        print(
            f"[r4r] safe external fast-forward during {phase}: "
            f"{before_head[:12]} -> {after_head[:12]}; "
            f"paths={list(committed_paths)}",
            flush=True,
        )
        return True

    def execute(self) -> int:
        self._require_binary(self.opencode_bin)
        self._require_binary(self.codex_bin)
        if self.require_codegraph and self.codegraph_policy != "off":
            self._require_binary(self.codegraph_bin)
        self._validate_opencode_runtime_config()
        # Active-task lock control is intentionally disabled. A stale legacy lock
        # must never prevent a run or bind progress to an obsolete Git commit.
        self.lock_path.unlink(missing_ok=True)
        dirty = git_changed_paths(self.repo)
        selected = self._select_task()
        if dirty:
            self._validate_unlocked_resume(dirty, selected)

        completed = 0
        while True:
            task = self._select_task()
            if task is None:
                final = self._run_gate("final-gate", self.plan.final_gate, self.run_dir, stream=True)
                if final.exit_code != 0:
                    return self._finish("FINAL_GATE_FAILED", final.exit_code)
                self._write_progress(None)
                self._write_memory()
                if self.auto_commit:
                    self._commit_if_needed(
                        "chore: record completed R4R task plan",
                        (".opencode/progress.json", ".opencode/memory.md"),
                    )
                return self._finish("ALL_TASKS_ACCEPTED", 0)
            if self.max_tasks and completed >= self.max_tasks:
                return self._finish("TASK_LIMIT_REACHED", 0, {"next_task": task.id})
            result = self._execute_task(task)
            if result != 0:
                return result
            completed += 1

    def status(self) -> int:
        print("R4R task status")
        for task in self.plan.tasks:
            item = task_progress(self.progress, task.id)
            gate = run_command(task.gate, self.repo, timeout_seconds=self.timeout)
            print(f"- {task.id}: progress={item['status']} gate={'GREEN' if gate.exit_code == 0 else 'RED'}")
        return 0

    def record_unhandled_failure(self, exception: BaseException) -> int:
        """Persist a state.json even when validation raises outside normal flow."""
        return self._finish(
            "CONTROLLER_EXCEPTION",
            2,
            {
                "error_type": type(exception).__name__,
                "error": str(exception),
            },
        )

    def _require_binary(self, binary: str) -> None:
        if shutil.which(binary) is None:
            raise RuntimeError(f"Required executable not found: {binary}. Run ./scripts/setup.sh")

    def _validate_opencode_runtime_config(self) -> None:
        configured = os.environ.get("OPENCODE_CONFIG", "").strip()
        path = Path(configured) if configured else self.repo / "opencode.jsonc"
        if not path.is_absolute():
            path = (self.repo / path).resolve()
        if not path.is_file():
            raise RuntimeError(f"OpenCode config not found: {path}")
        try:
            data = json.loads(path.read_text(encoding="utf-8"))
        except (OSError, json.JSONDecodeError) as exception:
            raise RuntimeError(f"Invalid OpenCode config {path}: {exception}") from exception

        providers = data.get("provider")
        if not isinstance(providers, dict) or not providers:
            raise RuntimeError(f"OpenCode config has no providers: {path}")
        for provider_id, provider in providers.items():
            options = provider.get("options") if isinstance(provider, dict) else None
            base_url = options.get("baseURL") if isinstance(options, dict) else None
            if not isinstance(base_url, str) or "{env:" in base_url:
                raise RuntimeError(
                    f"OpenCode provider {provider_id} has unresolved baseURL: {base_url!r}"
                )
            parsed = urlparse(base_url)
            if parsed.scheme not in {"http", "https"} or not parsed.netloc:
                raise RuntimeError(
                    f"OpenCode provider {provider_id} baseURL is not absolute: {base_url!r}"
                )

        agent_path = self.repo / ".opencode" / "agents" / f"{self.opencode_agent}.md"
        if not agent_path.is_file():
            raise RuntimeError(f"OpenCode agent not found: {agent_path}")
        match = re.search(
            r"^model:\s*([^/\s]+)/([^\s]+)\s*$",
            agent_path.read_text(encoding="utf-8"),
            flags=re.MULTILINE,
        )
        if match is None:
            raise RuntimeError(f"OpenCode agent has no valid model declaration: {agent_path}")
        provider_id, model_id = match.groups()
        provider = providers.get(provider_id)
        models = provider.get("models") if isinstance(provider, dict) else None
        if not isinstance(models, dict) or model_id not in models:
            raise RuntimeError(
                f"OpenCode agent {self.opencode_agent} references unknown model "
                f"{provider_id}/{model_id}"
            )

    @staticmethod
    def _non_transient_opencode_failure(result: CommandResult) -> bool:
        text = (result.stdout + "\n" + result.stderr).lower()
        markers = (
            "cannot be parsed as a url",
            "baseurl is not absolute",
            "unresolved baseurl",
            "invalid opencode config",
            "references unknown model",
            "unknown provider",
            "no such model",
        )
        return any(marker in text for marker in markers)

    def _manual_commit_paths(self, task: Task) -> tuple[str, ...]:
        # Return only dirty product paths owned by the selected task.
        return tuple(
            path for path in git_changed_paths(self.repo)
            if not is_controller_runtime_path(path)
            and not is_lock_auto_advance_path(path)
            and not path_is_allowed(path, getattr(self, "peer_paths", ()))
            and path_is_allowed(path, task.allowed_paths)
        )

    @staticmethod
    def _manual_commit_guidance(
        task: Task, paths: Sequence[str],
    ) -> str:
        # Build safe, copyable manual Git commands without executing them.
        unique_paths = tuple(dict.fromkeys(paths))
        lines = [
            f"Suggested commit message: {task.commit_message}",
            "Changed product paths:",
        ]
        lines.extend(f"  - {path}" for path in unique_paths)
        lines.append("Suggested commands:")
        if unique_paths:
            quoted_paths = " ".join(shlex.quote(path) for path in unique_paths)
            lines.append(f"  git add -- {quoted_paths}")
        else:
            lines.append("  # No task-owned product path is currently dirty.")
        lines.append(
            f"  git commit -m {shlex.quote(task.commit_message)}"
        )
        return "\n".join(lines)

    def _can_bootstrap(self, dirty: Sequence[str]) -> bool:
        return bool(self.bootstrap_commit) and all(
            item["status"] == "PENDING" for item in self.progress["tasks"]
        )

    def _bootstrap(self) -> int:
        task = self.plan.tasks[0]
        task_dir = self.run_dir / task.id / "bootstrap"
        gate = self._run_gate("task-gate", task.gate, task_dir, stream=True)
        if gate.exit_code != 0:
            return self._finish("BOOTSTRAP_GATE_FAILED", gate.exit_code)
        self._accept_progress(task)
        self.verified_green.add(task.id)
        self._write_progress(None)
        self._write_memory()
        if not self.auto_commit:
            manual_paths = self._manual_commit_paths(task)
            guidance = self._manual_commit_guidance(task, manual_paths)
            print(f"\n[r4r] manual commit required\n{guidance}", flush=True)
            return self._finish(
                "BOOTSTRAP_READY_COMMIT_REQUIRED",
                0,
                {
                    "task": task.id,
                    "suggested_commit_message": task.commit_message,
                    "commit_paths": manual_paths,
                },
            )
        if self._commit_if_needed(
                task.commit_message,
                (*task.allowed_paths, str(self.progress_path.relative_to(self.repo)), str(self.memory_path.relative_to(self.repo))),
            ) is None:
            return self._finish("BOOTSTRAP_COMMIT_FAILED", 67)
        return 0

    def _validate_unlocked_resume(
        self, dirty: Sequence[str], task: Task | None,
    ) -> None:
        """Allow resumable task work plus controller/config maintenance.

        No Git commit ancestry or active-task lock is consulted. Product paths
        must still fit the currently active/pending task, preserving task scope
        without the brittle lock lifecycle.
        """
        product_dirty = tuple(
            path for path in dirty
            if not is_controller_runtime_path(path)
            and not is_lock_auto_advance_path(path)
            and not path_is_allowed(path, getattr(self, "peer_paths", ()))
        )
        if not product_dirty:
            return
        if task is None:
            # AUTO_COMMIT_COMPLETED_PLAN_RECOVERY_V1
            if not self.auto_commit:
                raise RuntimeError(
                    "Completed plan has uncommitted product paths: "
                    f"{list(product_dirty)}"
                )

            commit_task = next(
                (
                    candidate
                    for candidate in reversed(self.plan.tasks)
                    if all(
                        path_is_allowed(path, candidate.allowed_paths)
                        for path in product_dirty
                    )
                ),
                None,
            )
            if commit_task is None:
                raise RuntimeError(
                    "Completed plan has dirty paths that do not belong entirely "
                    f"to one accepted task: {list(product_dirty)}"
                )

            committed_head = self._commit_if_needed(
                commit_task.commit_message,
                (
                    *commit_task.allowed_paths,
                    str(self.progress_path.relative_to(self.repo)),
                    str(self.memory_path.relative_to(self.repo)),
                ),
            )
            if committed_head is None:
                raise RuntimeError(
                    "Automatic recovery commit failed for accepted task "
                    f"{commit_task.id}: {list(product_dirty)}"
                )

            print(
                "[r4r] recovered accepted task with automatic commit: "
                f"{commit_task.id} -> {committed_head}",
                flush=True,
            )
            return

        disallowed = [
            path for path in product_dirty
            if not path_is_allowed(path, task.allowed_paths)
        ]
        if disallowed:
            raise RuntimeError(
                f"Dirty resume contains out-of-scope paths for {task.id}: "
                f"{disallowed}"
            )

    def _select_task(self) -> Task | None:
        active_task = str(self.progress.get("active_task") or "").strip()
        if active_task:
            task = self._task_by_id(active_task)
            if task_progress(self.progress, task.id)["status"] != "ACCEPTED":
                return task

        for task in self.plan.tasks:
            if task_progress(self.progress, task.id)["status"] != "ACCEPTED":
                return task
        return None

    def _execute_task(self, task: Task) -> int:
        task_root = self.run_dir / task.id
        task_root.mkdir(parents=True, exist_ok=True)
        self.memory_context.update(
            {
                "task_id": task.id,
                "attempt": 0,
                "gate_exit": None,
                "gate_name": None,
                "codex_decision": None,
                "changed_paths": list(self._manual_commit_paths(task)),
                "demonstrated": [],
                "outstanding": [task.objective],
                "avoid_repeating": [],
                "next_action": "Run the initial exact gate and classify the first current failure.",
                "checkpoint_head": None,
                "checkpoint_status": "none",
            }
        )
        self._write_progress(task.id)
        self._write_memory()
        initial_gate = self._run_gate("initial-gate", task.gate, task_root, stream=True)
        next_action = self._resume_action_from_codex_extra(task)

        attempt = 1
        transient_failures = 0
        no_progress_cycles = 0
        last_review_action = ""

        while self.max_attempts <= 0 or attempt <= self.max_attempts:
            edit_result: CommandResult | None = None
            attempt_dir = task_root / f"attempt-{attempt:02d}"
            attempt_dir.mkdir(parents=True, exist_ok=True)
            self.memory_context["attempt"] = attempt
            self.memory_context["next_action"] = next_action or (
                "Review the exact gate evidence and make one coherent, task-scoped repair."
            )
            self._write_memory()
            current_gate = (
                initial_gate
                if attempt == 1
                else self._run_gate("pre-edit-gate", task.gate, attempt_dir)
            )
            changed_this_attempt = False
            diagnostics = self._record_gate_diagnostics(current_gate, attempt_dir)
            codegraph_report = "CodeGraph reconnaissance skipped for a green gate." if current_gate.exit_code == 0 else "CodeGraph reconnaissance is disabled."
            if (
                current_gate.exit_code != 0
                and self.require_codegraph
                and self.codegraph_policy != "off"
                and not getattr(self, "compact_local_worker", False)
            ):
                try:
                    codegraph_report = self._run_codegraph_reconnaissance(
                        task,
                        current_gate,
                        attempt_dir,
                        diagnostics.source_paths,
                    )
                except RuntimeError as exception:
                    if self.codegraph_policy == "required":
                        return self._finish(
                            "CODEGRAPH_RECONNAISSANCE_FAILED",
                            76,
                            {
                                "task": task.id,
                                "attempt": attempt,
                                "error": str(exception),
                            },
                        )
                    codegraph_report = self._write_advisory_codegraph_failure(
                        attempt_dir, str(exception)
                    )

            if current_gate.exit_code != 0:
                self._run_pre_edit_understanding(
                    task, current_gate, attempt_dir, diagnostics, codegraph_report
                )
            else:
                evidence = attempt_dir / "evidence"
                evidence.mkdir(parents=True, exist_ok=True)
                (evidence / "pre-edit-understanding.md").write_text(
                    "# Pre-edit understanding report\n\nSkipped because the exact task gate is already green; proceed directly to evidence review.\n",
                    encoding="utf-8",
                )

            if current_gate.exit_code != 0:
                try:
                    plan = self._codex_plan(task, current_gate, attempt_dir)
                except RuntimeError as exception:
                    return self._finish(
                        "CODEX_PLAN_RETRY_EXHAUSTED",
                        75,
                        {"task": task.id, "attempt": attempt, "error": str(exception)},
                    )
                if plan["decision"] == "BLOCKED":
                    self._mark_blocked(task)
                    return self._finish(
                        "CODEX_PLAN_BLOCKED", 68, {"task": task.id, "plan": plan}
                    )
                next_action = "\n".join(
                    f"{index + 1}. {value}"
                    for index, value in enumerate(plan["instructions"])
                )

            if current_gate.exit_code != 0 or next_action:
                prompt = self._opencode_prompt(
                    task,
                    current_gate,
                    next_action,
                    codegraph_report,
                    diagnostics,
                )
                before_head = git_head(self.repo)
                before_fingerprint = self._worktree_fingerprint()
                edit_result = self._run_logged(
                    "opencode",
                    (
                        self.opencode_bin,
                        "run",
                        "--dir",
                        str(self.repo),
                        "--agent",
                        self.opencode_agent,
                        "--format",
                        "json",
                        "--auto",
                        prompt,
                    ),
                    attempt_dir,
                    stream=True,
                )
                if edit_result.exit_code != 0:
                    self.memory_context["avoid_repeating"] = [
                        f"Do not repeat the stopped OpenCode session without changing the plan; stop_reason={edit_result.stop_reason or 'process-exit'}.",
                    ]
                    self.memory_context["outstanding"] = [
                        "The local edit session did not complete; no acceptance claim can be made from it."
                    ]
                    self.memory_context["next_action"] = (
                        "Start a fresh bounded session using the preserved worktree and the first current gate failure."
                    )
                    self._write_memory()
                    if self._non_transient_opencode_failure(edit_result):
                        return self._finish(
                            "OPENCODE_CONFIGURATION_ERROR",
                            edit_result.exit_code or 78,
                            {
                                "task": task.id,
                                "attempt": attempt,
                                "diagnostic": (edit_result.stderr or edit_result.stdout)[-4000:],
                            },
                        )
                    transient_failures += 1
                    if transient_failures > self.max_transient_failures:
                        return self._finish(
                            "OPENCODE_RETRY_EXHAUSTED",
                            edit_result.exit_code,
                            {"task": task.id, "attempt": attempt},
                        )
                    next_action = self._transient_retry_action(
                        "OpenCode edit",
                        edit_result,
                        next_action,
                    )
                    attempt += 1
                    continue

                transient_failures = 0
                after_fingerprint = self._worktree_fingerprint()
                changed_this_attempt = after_fingerprint != before_fingerprint
                if changed_this_attempt:
                    no_progress_cycles = 0
                    self._notify_file_changed(
                        attempt_dir,
                        f"files-changed-{attempt:02d}",
                        f"{task.id}: local LLM changed repository files",
                    )
                else:
                    no_progress_cycles += 1

                try:
                    self._accept_safe_external_head_change(
                        before_head, "OpenCode edit"
                    )
                except RuntimeError as exception:
                    return self._finish(
                        "OPENCODE_GIT_WRITE_VIOLATION",
                        69,
                        {"task": task.id, "error": str(exception)},
                    )

                all_changed = git_changed_paths(self.repo)
                controller_runtime = tuple(
                    path for path in all_changed
                    if is_controller_runtime_path(path)
                    or is_lock_auto_advance_path(path)
                )
                peer_changes = tuple(
                    path for path in all_changed
                    if path_is_allowed(path, getattr(self, "peer_paths", ()))
                )
                changed = tuple(
                    path for path in all_changed
                    if not is_controller_runtime_path(path)
                    and not is_lock_auto_advance_path(path)
                    and not path_is_allowed(path, getattr(self, "peer_paths", ()))
                )
                disallowed = [
                    path for path in changed
                    if not path_is_allowed(path, task.allowed_paths)
                ]
                (attempt_dir / "evidence").mkdir(exist_ok=True)
                (attempt_dir / "evidence" / "changed-paths.json").write_text(
                    json.dumps(
                        {
                            "changed_paths": changed,
                            "ignored_controller_runtime_paths": controller_runtime,
                            "peer_owned_background_paths": peer_changes,
                            "disallowed_paths": disallowed,
                        },
                        indent=2,
                    ),
                    encoding="utf-8",
                )
                if disallowed:
                    return self._finish(
                        "SCOPE_VIOLATION",
                        65,
                        {"task": task.id, "paths": disallowed},
                    )
                self._write_patch(attempt_dir, changed)
                self.memory_context["changed_paths"] = list(changed)
                self.memory_context["next_action"] = (
                    "Run the exact task gate against the coherent edited state."
                )
                self._write_memory()

                if (
                    not changed_this_attempt
                    and no_progress_cycles >= self.max_no_progress_cycles
                ):
                    next_action = self._no_progress_action(next_action)

            gate = self._run_gate("task-gate", task.gate, attempt_dir, stream=True)
            self.memory_context["gate_name"] = "task-gate"
            self.memory_context["gate_exit"] = gate.exit_code
            if gate.exit_code == 0:
                self.memory_context["demonstrated"] = [
                    "The exact deterministic task gate completed successfully.",
                ]
                self.memory_context["outstanding"] = [
                    "Codex has not yet accepted the current checkpoint.",
                ]
                self.memory_context["next_action"] = (
                    "Preserve a deterministic gate-green checkpoint, generate final evidence and request Codex review."
                )
                try:
                    self._checkpoint_green(task, gate, attempt, attempt_dir)
                except RuntimeError as exception:
                    return self._finish(
                        "CHECKPOINT_COMMIT_FAILED",
                        67,
                        {"task": task.id, "attempt": attempt, "error": str(exception)},
                    )
                self._notify(
                    attempt_dir,
                    f"handoff-to-codex-{attempt:02d}",
                    4,
                    f"{task.id}: green gate, handing control to Codex",
                )
            else:
                self.memory_context["demonstrated"] = []
                self.memory_context["outstanding"] = [
                    f"The exact task gate is red with exit code {gate.exit_code}.",
                ]
                self.memory_context["next_action"] = (
                    "Fix the first current deterministic gate failure before requesting acceptance."
                )
                self._write_memory()

            # The compact laptop worker avoids a second model call. Codex reviews
            # the exact diff and gate evidence directly. The PC worker keeps the
            # assimilation pass as additional evidence.
            if getattr(self, "compact_local_worker", False):
                self._write_compact_local_understanding(
                    attempt_dir,
                    edit_result.stdout if edit_result is not None else "",
                    task,
                    gate,
                )
            else:
                assimilation_before_head = git_head(self.repo)
                assimilation_before_fingerprint = self._worktree_fingerprint()
                assimilation = self._run_logged(
                    "opencode-assimilation",
                    (
                        self.opencode_bin,
                        "run",
                        "--dir",
                        str(self.repo),
                        "--agent",
                        self.opencode_agent,
                        "--format",
                        "json",
                        "--auto",
                        self._opencode_assimilation_prompt(
                            task,
                            gate,
                            codegraph_report,
                        ),
                    ),
                    attempt_dir,
                    stream=True,
                )
                if assimilation.exit_code != 0:
                    self._write_failed_local_understanding(attempt_dir, assimilation)
                else:
                    try:
                        self._accept_safe_external_head_change(
                            assimilation_before_head,
                            "OpenCode assimilation",
                        )
                    except RuntimeError as exception:
                        return self._finish(
                            "OPENCODE_ASSIMILATION_GIT_WRITE_VIOLATION",
                            69,
                            {
                                "task": task.id,
                                "attempt": attempt,
                                "error": str(exception),
                            },
                        )
                    if (
                        self._worktree_fingerprint()
                        != assimilation_before_fingerprint
                    ):
                        return self._finish(
                            "OPENCODE_ASSIMILATION_FILE_WRITE_VIOLATION",
                            65,
                            {"task": task.id, "attempt": attempt},
                        )
                    self._write_local_understanding(attempt_dir, assimilation.stdout)

            try:
                review = self._codex_review(task, gate, attempt_dir)
            except RuntimeError as exception:
                transient_failures += 1
                if transient_failures > self.max_transient_failures:
                    return self._finish(
                        "CODEX_REVIEW_RETRY_EXHAUSTED",
                        75,
                        {
                            "task": task.id,
                            "attempt": attempt,
                            "error": str(exception),
                        },
                    )
                next_action = (
                    "Codex review failed transiently. Preserve the current product "
                    "changes, rerun the deterministic gate, regenerate the local "
                    "understanding report and hand the same evidence to Codex again."
                )
                attempt += 1
                continue

            transient_failures = 0
            self.memory_context["codex_decision"] = review["decision"]
            review_corrections = [
                str(value).strip()
                for value in (review.get("instruction_corrections") or [])
                if str(value).strip()
            ]
            review_next = str(review.get("next_action") or "").strip()
            if review["decision"] == "ACCEPT":
                self.memory_context["outstanding"] = []
                self.memory_context["next_action"] = (
                    "Record ACCEPT, create the final controller commit and advance the queue."
                )
            else:
                self.memory_context["outstanding"] = review_corrections or [
                    review_next or "Codex requires another bounded revision."
                ]
                self.memory_context["avoid_repeating"] = review_corrections
                self.memory_context["next_action"] = review_next or (
                    "Apply the resolved Codex correction packet in one coherent batch."
                )
            self._write_memory()
            self._request_ring_review(
                task,
                reason=f"codex-{str(review['decision']).lower()}",
                attempt=attempt,
                gate=gate,
                review=review,
                checkpoint_head=self.memory_context.get("checkpoint_head"),
            )
            if review["decision"] == "ACCEPT":
                if gate.exit_code != 0:
                    next_action = (
                        "The task gate is still red. Fix the first current gate "
                        "failure before requesting ACCEPT."
                    )
                    invalid_accept = dict(review)
                    invalid_accept["decision"] = "REVISE"
                    invalid_accept["local_understanding_assessment"] = (
                        str(
                            review.get("local_understanding_assessment") or ""
                        ).strip()
                        + " Codex attempted ACCEPT while the deterministic gate was red."
                    ).strip()
                    invalid_accept["instruction_corrections"] = [
                        *list(review.get("instruction_corrections") or []),
                        "A red deterministic task gate always overrides an ACCEPT decision.",
                    ]
                    invalid_accept["corrected_extra_instructions"] = next_action
                    self._write_codex_extra_instructions(task, invalid_accept)
                    attempt += 1
                    continue

                self._write_codex_extra_instructions(task, review)
                self._accept_progress(task)
                self.verified_green.add(task.id)
                self._write_progress(None)
                self._write_memory()
                if not self.auto_commit:
                    manual_paths = self._manual_commit_paths(task)
                    guidance = self._manual_commit_guidance(
                        task, manual_paths,
                    )
                    print(
                        f"\n[r4r] manual commit required\n{guidance}",
                        flush=True,
                    )
                    return self._finish(
                        "TASK_ACCEPTED_COMMIT_REQUIRED",
                        0,
                        {
                            "task": task.id,
                            "suggested_commit_message": task.commit_message,
                            "commit_paths": manual_paths,
                        },
                    )
                if self._commit_if_needed(
                    task.commit_message,
                    (*task.allowed_paths, str(self.progress_path.relative_to(self.repo)), str(self.memory_path.relative_to(self.repo))),
                ) is None:
                    return self._finish(
                        "AUTO_COMMIT_FAILED",
                        67,
                        {"task": task.id},
                    )
                return 0

            self._write_codex_extra_instructions(task, review)
            if review["decision"] == "BLOCKED":
                self._mark_blocked(task)
                return self._finish(
                    "CODEX_REVIEW_BLOCKED",
                    68,
                    {"task": task.id, "review": review},
                )

            next_action = str(review["next_action"]).strip()
            normalized_action = " ".join(next_action.split()).lower()
            if normalized_action and normalized_action == last_review_action:
                no_progress_cycles += 1
            else:
                last_review_action = normalized_action
                if changed_this_attempt:
                    no_progress_cycles = 0
            if no_progress_cycles >= self.max_no_progress_cycles:
                next_action = self._no_progress_action(next_action)

            attempt += 1

        return self._finish(
            "GLOBAL_ATTEMPT_LIMIT_REACHED",
            70,
            {
                "task": task.id,
                "attempts": self.max_attempts,
                "recovery_packet": str(
                    self.codex_extra_instructions_path.relative_to(self.repo)
                )
                if self.codex_extra_instructions_path.exists()
                else None,
            },
        )

    def _transient_retry_action(
        self,
        component: str,
        result: CommandResult,
        previous_action: str | None,
    ) -> str:
        details = (result.stderr or result.stdout)[-4000:].strip()
        return (
            f"{component} failed transiently with exit {result.exit_code}. "
            "Start a fresh local session, preserve the current worktree, diagnose "
            "the concrete error below and continue the same active task. Do not "
            "revert already validated changes.\n\n"
            f"Previous action:\n{previous_action or 'Implement the active task.'}"
            f"\n\nFailure tail:\n{details or 'No diagnostic text was produced.'}"
        )

    def _no_progress_action(self, previous_action: str | None) -> str:
        return (
            "The previous local pass made no effective product change or repeated "
            "the same unresolved action. Treat the CURRENT CODEX-TO-LOCAL EXTRA "
            "INSTRUCTIONS as a checklist: map every numbered requirement to an "
            "exact code or test assertion, edit the named path, and verify each "
            "item before running the exact gate. Do not merely restate the task."
            f"\n\nPrevious unresolved action:\n{previous_action or 'none'}"
        )

    def _record_gate_diagnostics(
        self, gate: CommandResult, directory: Path,
    ) -> GateDiagnostics:
        evidence = directory / "evidence"
        evidence.mkdir(parents=True, exist_ok=True)
        diagnostics = build_gate_diagnostics(
            self.repo,
            evidence,
            gate.command,
            gate.exit_code,
            gate.stdout,
            gate.stderr,
        )
        (evidence / "gate-diagnostics.json").write_text(
            json.dumps(diagnostics.to_dict(), indent=2, ensure_ascii=False) + "\n",
            encoding="utf-8",
        )
        print(
            f"[r4r] gate diagnostics: {diagnostics.classification}; "
            f"sources={len(diagnostics.source_paths)}; bundle={diagnostics.bundle_path}",
            flush=True,
        )
        return diagnostics

    def _diagnostic_fingerprint(self, directory: Path) -> str:
        manifest = directory / "evidence" / "diagnostics" / "error-manifest.json"
        if not manifest.exists():
            return "missing-diagnostics"
        value = json.loads(manifest.read_text(encoding="utf-8"))
        return str(value.get("fingerprint") or "missing-fingerprint")

    def _load_cached_codex_plan(
        self, task_id: str, fingerprint: str,
    ) -> dict[str, Any] | None:
        if self.codex_min_interval_seconds <= 0 or not self.codex_plan_cache_path.exists():
            return None
        try:
            value = json.loads(self.codex_plan_cache_path.read_text(encoding="utf-8"))
            created = float(value.get("created_at_epoch", 0))
            plan = value.get("plan")
            if (
                value.get("task_id") == task_id
                and value.get("diagnostic_fingerprint") == fingerprint
                and time.time() - created < self.codex_min_interval_seconds
                and isinstance(plan, dict)
            ):
                return validate_structured_result(
                    plan, task_id, {"READY", "BLOCKED"}
                )
        except (OSError, ValueError, TypeError, json.JSONDecodeError):
            return None
        return None

    def _store_cached_codex_plan(
        self, task_id: str, fingerprint: str, plan: dict[str, Any],
    ) -> None:
        self.control_dir.mkdir(parents=True, exist_ok=True)
        temporary = self.codex_plan_cache_path.with_suffix(".json.tmp")
        temporary.write_text(
            json.dumps(
                {
                    "schema_version": 1,
                    "task_id": task_id,
                    "diagnostic_fingerprint": fingerprint,
                    "created_at_epoch": time.time(),
                    "minimum_interval_seconds": self.codex_min_interval_seconds,
                    "plan": plan,
                },
                indent=2,
                ensure_ascii=False,
            ) + "\n",
            encoding="utf-8",
        )
        temporary.replace(self.codex_plan_cache_path)

    def _write_codegraph_skipped(self, attempt_dir: Path, reason: str) -> str:
        report = (
            "# CodeGraph reconnaissance report\n\n"
            "## Status\n\n"
            f"Skipped: {reason}\n\n"
            "## Scope\n\n"
            "No repository-wide exploration was performed.\n"
        )
        evidence = attempt_dir / "evidence"
        evidence.mkdir(parents=True, exist_ok=True)
        (evidence / "codegraph-reconnaissance.md").write_text(
            report, encoding="utf-8"
        )
        (evidence / "codegraph-tool-calls.json").write_text(
            json.dumps({"required": False, "calls": [], "status": "skipped"}, indent=2)
            + "\n",
            encoding="utf-8",
        )
        return report

    def _write_advisory_codegraph_failure(
        self, attempt_dir: Path, error: str,
    ) -> str:
        report = (
            "# CodeGraph reconnaissance report\n\n"
            "## Status\n\n"
            "Advisory CodeGraph mapping was unavailable. The workflow continues; "
            "Codex and exact source inspection remain authoritative.\n\n"
            "## Diagnostic\n\n"
            f"{error}\n"
        )
        evidence = attempt_dir / "evidence"
        evidence.mkdir(parents=True, exist_ok=True)
        (evidence / "codegraph-reconnaissance.md").write_text(
            report, encoding="utf-8"
        )
        (evidence / "codegraph-tool-calls.json").write_text(
            json.dumps(
                {"required": False, "calls": [], "status": "unavailable", "error": error},
                indent=2,
                ensure_ascii=False,
            ) + "\n",
            encoding="utf-8",
        )
        print(f"[r4r] CodeGraph advisory unavailable: {error}", file=sys.stderr)
        return report

    def _pre_edit_understanding_prompt(
        self,
        task: Task,
        gate: CommandResult,
        diagnostics: GateDiagnostics,
        codegraph_report: str,
    ) -> str:
        source_list = "\n".join(
            f"- {path}" for path in diagnostics.source_paths
        ) or "- none"
        ring_directive = self._current_ring_directive(task)
        return f"""This is a read-only pre-edit understanding pass for {task.id}.
Do not edit files, do not run Git write commands and do not run Maven.

CURRENT THE-RING ADVISORY DIRECTIVE:
{ring_directive}

Read only AGENTS.md, .opencode/commands/task.md, the selected task file, the
current diagnostic summary and the focused CodeGraph report. Do not read the full
Maven log; Codex will process that complete evidence.

Task objective: {task.objective}
Gate exit: {gate.exit_code}
Diagnostic classification: {diagnostics.classification}
Diagnostic summary: {diagnostics.summary}
Implicated source paths:
{source_list}

Focused CodeGraph report:
{codegraph_report}

Return only concise Markdown with exactly these headings:
# Pre-edit understanding report
## Objective
## Current blocker
## Files I expect Codex to inspect
## Minimal repair boundary
## What I must not change
## Question for Codex

Do not propose code yet. Do not claim that an infrastructure outage is a Java bug.
"""

    def _run_pre_edit_understanding(
        self,
        task: Task,
        gate: CommandResult,
        attempt_dir: Path,
        diagnostics: GateDiagnostics,
        codegraph_report: str,
    ) -> None:
        if getattr(self, "compact_local_worker", False):
            evidence = attempt_dir / "evidence"
            evidence.mkdir(parents=True, exist_ok=True)
            (evidence / "pre-edit-understanding.md").write_text(
                "# Pre-edit understanding report\n\n"
                "Skipped for the compact laptop worker. Codex planning and the "
                "deterministic gate remain authoritative.\n",
                encoding="utf-8",
            )
            return
        before_head = git_head(self.repo)
        before_fingerprint = self._worktree_fingerprint()
        result = self._run_logged(
            "opencode-pre-edit-understanding",
            (
                self.opencode_bin,
                "run",
                "--dir",
                str(self.repo),
                "--agent",
                self.opencode_agent,
                "--format",
                "json",
                "--auto",
                self._pre_edit_understanding_prompt(
                    task, gate, diagnostics, codegraph_report
                ),
            ),
            attempt_dir,
            stream=True,
        )
        evidence = attempt_dir / "evidence"
        evidence.mkdir(parents=True, exist_ok=True)
        if result.exit_code != 0:
            report = (
                "# Pre-edit understanding report\n\n"
                "The local worker failed to produce a read-only summary. Codex must "
                "use the deterministic diagnostic bundle directly.\n"
            )
        else:
            self._accept_safe_external_head_change(
                before_head, "pre-edit understanding"
            )
            if self._worktree_fingerprint() != before_fingerprint:
                raise RuntimeError("Pre-edit understanding modified repository files")
            report = extract_opencode_text(result.stdout).strip() or (
                "# Pre-edit understanding report\n\n"
                "No model-authored summary was produced.\n"
            )
        (evidence / "pre-edit-understanding.md").write_text(
            report.rstrip() + "\n", encoding="utf-8"
        )

    def _codex_plan(
        self,
        task: Task,
        gate: CommandResult,
        task_dir: Path,
    ) -> dict[str, Any]:
        fingerprint = self._diagnostic_fingerprint(task_dir)
        cached = self._load_cached_codex_plan(task.id, fingerprint)
        if cached is not None:
            decisions = task_dir / "decisions"
            decisions.mkdir(parents=True, exist_ok=True)
            (decisions / "codex-plan.json").write_text(
                json.dumps(cached, indent=2, ensure_ascii=False) + "\n",
                encoding="utf-8",
            )
            (task_dir / "evidence" / "codex-plan-cache-reused.json").write_text(
                json.dumps(
                    {
                        "task_id": task.id,
                        "diagnostic_fingerprint": fingerprint,
                        "minimum_interval_seconds": self.codex_min_interval_seconds,
                    },
                    indent=2,
                ) + "\n",
                encoding="utf-8",
            )
            print(
                "[r4r] reusing Codex plan for unchanged diagnostics within the hourly cooldown",
                flush=True,
            )
            return cached

        plan = self._run_codex_structured(
            "plan",
            task,
            gate,
            task_dir,
            self.repo / "py-codex-agent/schemas/plan.schema.json",
            {"READY", "BLOCKED"},
        )
        self._store_cached_codex_plan(task.id, fingerprint, plan)
        return plan

    def _codex_review(
        self,
        task: Task,
        gate: CommandResult,
        attempt_dir: Path,
    ) -> dict[str, Any]:
        return self._run_codex_structured(
            "review",
            task,
            gate,
            attempt_dir,
            self.repo / "py-codex-agent/schemas/review.schema.json",
            {"ACCEPT", "REVISE", "BLOCKED"},
        )

    def _run_codex_structured(
        self,
        stage: str,
        task: Task,
        gate: CommandResult,
        directory: Path,
        schema: Path,
        allowed_decisions: set[str],
    ) -> dict[str, Any]:
        output = directory / "decisions" / f"codex-{stage}.json"
        output.parent.mkdir(parents=True, exist_ok=True)
        prompt = self._structured_prompt(stage, task, gate, directory)
        command = codex_exec_command(
            self.codex_bin,
            schema,
            output,
            self.codex_model,
        )
        errors: list[str] = []
        for retry in range(1, self.max_transient_failures + 2):
            output.unlink(missing_ok=True)
            name = (
                f"codex-{stage}"
                if retry == 1
                else f"codex-{stage}-retry-{retry:02d}"
            )
            result = self._run_logged(
                name,
                command,
                directory,
                input_text=prompt,
                stream=True,
            )
            if result.exit_code != 0 or not output.exists():
                errors.append(
                    f"try {retry}: exit={result.exit_code}; "
                    f"{(result.stderr or result.stdout)[-1000:].strip()}"
                )
                continue
            try:
                value = json.loads(output.read_text(encoding="utf-8"))
                return validate_structured_result(
                    value,
                    task.id,
                    allowed_decisions,
                )
            except (json.JSONDecodeError, KeyError, TypeError, ValueError) as exception:
                errors.append(f"try {retry}: invalid structured result: {exception}")
        raise RuntimeError(
            f"Codex {stage} failed after retries: " + " | ".join(errors)
        )

    def _companion_instruction_files(self, task: Task) -> tuple[Path, ...]:
        task_path = self.repo / task.command
        if task_path.parent != self.repo / ".opencode" / "commands":
            return ()
        return tuple(
            path
            for path in sorted(task_path.parent.glob(f"{task_path.stem}*.md"))
            if path.resolve() != task_path.resolve()
        )

    def _instruction_files(
        self,
        task: Task,
        include_companion: bool = True,
    ) -> tuple[Path, ...]:
        """Return the deterministic instruction bundle for the active task."""
        candidates: list[Path] = [
            self.repo / "AGENTS.md",
            self.repo / ".opencode" / "commands" / "task.md",
            self.repo / task.command,
        ]
        if include_companion:
            candidates.extend(self._companion_instruction_files(task))
        if self.codex_extra_instructions_path.exists():
            candidates.append(self.codex_extra_instructions_path)

        unique: list[Path] = []
        seen: set[Path] = set()
        for candidate in candidates:
            resolved = candidate.resolve()
            if resolved in seen or not candidate.is_file():
                continue
            seen.add(resolved)
            unique.append(candidate)
        return tuple(unique)

    def _use_full_instruction_bundle(self, stage: str) -> bool:
        return (
            stage == "plan"
            or not getattr(self, "compact_revision_context", True)
            or not self.codex_extra_instructions_path.exists()
        )

    def _instruction_manifest(self, task: Task) -> str:
        lines: list[str] = []
        for path in self._companion_instruction_files(task):
            content = path.read_bytes()
            relative = path.relative_to(self.repo)
            line_count = path.read_text(encoding="utf-8").count("\n") + 1
            lines.append(
                f"- {relative}: sha256={hashlib.sha256(content).hexdigest()} "
                f"lines={line_count}"
            )
        return "\n".join(lines) or "- none"

    def _instruction_bundle(
        self,
        task: Task,
        include_companion: bool = True,
    ) -> str:
        sections: list[str] = []
        for path in self._instruction_files(task, include_companion):
            try:
                label = str(path.relative_to(self.repo))
            except ValueError:
                label = str(path)
            sections.append(
                f"### {label}\n\n{path.read_text(encoding='utf-8').strip()}"
            )
        return "\n\n".join(sections)

    def _structured_prompt(
        self,
        stage: str,
        task: Task,
        gate: CommandResult,
        evidence_dir: Path,
    ) -> str:
        contract = (
            self.repo / f"py-codex-agent/prompts/{stage}.md"
        ).read_text(encoding="utf-8")
        memory = self.memory_path.read_text(encoding="utf-8")
        local_understanding_path = (
            evidence_dir / "evidence" / "local-understanding.md"
        )
        pre_edit_understanding_path = (
            evidence_dir / "evidence" / "pre-edit-understanding.md"
        )
        local_understanding = (
            local_understanding_path.read_text(encoding="utf-8")
            if local_understanding_path.exists()
            else "No post-edit local understanding report was produced."
        )
        pre_edit_understanding = (
            pre_edit_understanding_path.read_text(encoding="utf-8")
            if pre_edit_understanding_path.exists()
            else "No pre-edit local understanding report was produced."
        )
        checkpoint_path = evidence_dir / "evidence" / "checkpoint.json"
        checkpoint_evidence = (
            checkpoint_path.read_text(encoding="utf-8")
            if checkpoint_path.exists()
            else "No gate-green checkpoint was created for this attempt."
        )
        codegraph_path = (
            evidence_dir / "evidence" / "codegraph-reconnaissance.md"
        )
        codegraph_reconnaissance = (
            codegraph_path.read_text(encoding="utf-8")
            if codegraph_path.exists()
            else "No verified CodeGraph reconnaissance was produced."
        )
        extra_instructions = self._current_codex_extra_instructions()
        ring_directive = self._current_ring_directive(task)
        include_companion = self._use_full_instruction_bundle(stage)
        context_mode = (
            "full canonical bundle"
            if include_companion
            else "focused revision bundle; unchanged companion guides are hashed below"
        )
        diagnostics_summary_path = evidence_dir / "evidence" / "diagnostics" / "gate-summary.md"
        diagnostics_manifest_path = evidence_dir / "evidence" / "diagnostics" / "error-manifest.json"
        diagnostics_bundle_path = evidence_dir / "evidence" / "diagnostics" / "codex-error-bundle.zip"
        diagnostics_summary = (
            diagnostics_summary_path.read_text(encoding="utf-8")
            if diagnostics_summary_path.exists()
            else "No gate diagnostic summary was produced."
        )
        return (
            contract
            + f"\n\nCONTEXT MODE\n{context_mode}"
            + "\n\nACTIVE INSTRUCTION BUNDLE\n"
            + self._instruction_bundle(task, include_companion)
            + "\n\nUNCHANGED COMPANION GUIDE MANIFEST\n"
            + self._instruction_manifest(task)
            + "\n\nCURRENT MEMORY\n"
            + memory
            + "\n\nGATE-GREEN CHECKPOINT EVIDENCE\n"
            + checkpoint_evidence
            + "\n\nVERIFIED CODEGRAPH RECONNAISSANCE\n"
            + codegraph_reconnaissance
            + "\n\nPRE-EDIT LOCAL UNDERSTANDING REPORT\n"
            + pre_edit_understanding
            + "\n\nPOST-EDIT LOCAL UNDERSTANDING REPORT\n"
            + local_understanding
            + "\n\nGATE DIAGNOSTIC SUMMARY\n"
            + diagnostics_summary
            + "\n\nDIAGNOSTIC ARTIFACTS\n"
            + f"Manifest: {diagnostics_manifest_path.relative_to(self.repo) if diagnostics_manifest_path.exists() else 'missing'}\n"
            + f"Compressed error bundle: {diagnostics_bundle_path.relative_to(self.repo) if diagnostics_bundle_path.exists() else 'missing'}\n"
            + "Codex must inspect the complete log and every packaged source file before returning a plan or review.\n"
            + "\n\nCURRENT CODEX-TO-LOCAL EXTRA INSTRUCTIONS\n"
            + extra_instructions
            + "\n\nCURRENT THE-RING ADVISORY DIRECTIVE\n"
            + ring_directive
            + "\n\nDIRECTIVE PRECEDENCE\n"
            + "The exact task, deterministic gate and current Codex correction packet override The-Ring. "
            + "Treat The-Ring as cross-stack advisory evidence only.\n"
            + f"\nTASK ID\n{task.id}"
            + f"\nEVIDENCE DIRECTORY\n{evidence_dir.relative_to(self.repo)}"
            + f"\n\nCURRENT GATE EXIT\n{gate.exit_code}"
            + f"\nFULL GATE STDOUT (UNTRUNCATED)\n{gate.stdout}"
            + f"\nFULL GATE STDERR (UNTRUNCATED)\n{gate.stderr}\n"
        )

    def _current_codex_extra_instructions(self) -> str:
        if not self.codex_extra_instructions_path.exists():
            return "No active Codex-to-local extra instructions."
        return self.codex_extra_instructions_path.read_text(encoding="utf-8").strip()

    @staticmethod
    def _ring_timestamp(value: Any) -> datetime | None:
        if not isinstance(value, str) or not value.strip():
            return None
        try:
            parsed = datetime.fromisoformat(value.replace("Z", "+00:00"))
        except ValueError:
            return None
        if parsed.tzinfo is None:
            return None
        return parsed.astimezone(timezone.utc)

    def _current_ring_directive(self, task: Task) -> str:
        path = getattr(self, "ring_directive_path", None)
        if path is None or not Path(path).is_file():
            return "No active The-Ring directive for this worker."
        try:
            value = json.loads(Path(path).read_text(encoding="utf-8"))
        except (OSError, json.JSONDecodeError) as exception:
            return f"Ignored malformed The-Ring directive: {exception}."
        if not isinstance(value, dict) or value.get("schema_version") != 1:
            return "Ignored The-Ring directive with unsupported schema."
        target = str(value.get("target", "")).upper()
        if target != getattr(self, "worker_id", "").upper():
            return f"Ignored The-Ring directive for target {target or 'unknown'}."
        directive_task = str(value.get("task_id", "")).strip()
        if directive_task != task.id:
            return (
                f"Ignored The-Ring directive for task {directive_task or 'unknown'}; "
                f"active task is {task.id}."
            )
        if str(value.get("priority", "")).lower() != "advisory":
            return "Ignored The-Ring directive because priority is not advisory."
        generated_at = self._ring_timestamp(value.get("generated_at"))
        if generated_at is None:
            return "Ignored The-Ring directive with invalid generated_at."
        age = (datetime.now(timezone.utc) - generated_at).total_seconds()
        max_age = int(getattr(self, "ring_directive_max_age_seconds", 10800))
        if age > max_age:
            return f"Ignored stale The-Ring directive ({int(age)} seconds old)."
        if age < -300:
            return "Ignored The-Ring directive generated in the future."
        next_action = str(value.get("next_action", "")).strip()
        if not next_action:
            return "Ignored The-Ring directive without a next_action."
        evidence = value.get("evidence_paths") or []
        constraints = value.get("constraints") or []
        if not isinstance(evidence, list):
            evidence = []
        if not isinstance(constraints, list):
            constraints = []
        evidence_lines = "\n".join(f"- {item}" for item in evidence) or "- none supplied"
        constraint_lines = "\n".join(f"- {item}" for item in constraints) or "- none supplied"
        return (
            f"Directive source: {path}\n"
            f"Generated: {value.get('generated_at')}\n"
            f"Summary: {str(value.get('summary', '')).strip() or 'none'}\n"
            f"Focused next action: {next_action}\n"
            f"Avoid repeating: {str(value.get('avoid_repeating', '')).strip() or 'none'}\n"
            f"Evidence paths:\n{evidence_lines}\n"
            f"Constraints:\n{constraint_lines}\n"
            "Authority: advisory only; the exact task, gate and current Codex corrections override it."
        )

    def _resume_action_from_codex_extra(self, task: Task) -> str | None:
        if not self.codex_extra_instructions_path.exists():
            return None
        content = self.codex_extra_instructions_path.read_text(encoding="utf-8")
        if f"- Active task: `{task.id}`" not in content:
            return None
        return (
            "Resume the unfinished revision by applying the complete CURRENT "
            "CODEX-TO-LOCAL EXTRA INSTRUCTIONS included below. Do not wait for "
            "another Codex review before making the requested correction."
        )

    def _codegraph_reconnaissance_prompt(
        self,
        task: Task,
        gate: CommandResult,
        retry: int,
        focus_paths: Sequence[str],
    ) -> str:
        changed = (
            "\n".join(f"- {path}" for path in focus_paths)
            or "- none"
        )
        retry_instruction = ""
        if retry > 1:
            retry_instruction = (
                "\nThe previous reconnaissance produced no verified CodeGraph MCP "
                "tool event. You must call an available codegraph_* tool now; prose "
                "about CodeGraph does not count.\n"
            )
        return f"""This is a focused read-only CodeGraph map for {task.id}.
Do not edit files, do not run Git write commands and do not run the task gate.
{retry_instruction}
Call an available CodeGraph MCP tool whose identifier begins with `codegraph_`.
Map only the listed failing source files, their directly connected symbols, callers
and focused tests. Do not perform repository-wide exploration. Prefer exact symbols.

Task objective:
{task.objective}

Current failing or implicated source paths:
{changed}

Current gate exit: {gate.exit_code}
Gate stderr tail:
{gate.stderr[-4000:]}

Return only a concise Markdown report with exactly these headings:

# CodeGraph reconnaissance report
## CodeGraph tools called
## Relevant symbols and relationships
## Changed paths mapped to symbols
## Tests and callers affected
## Structural risks or stale assumptions
## Recommended bounded file reads

Name every CodeGraph tool you actually called. Do not claim a tool call that was not
executed. The controller verifies OpenCode JSONL events and rejects prose-only claims.
"""

    def _run_codegraph_reconnaissance(
        self,
        task: Task,
        gate: CommandResult,
        attempt_dir: Path,
        focus_paths: Sequence[str],
    ) -> str:
        if not focus_paths:
            return self._write_codegraph_skipped(
                attempt_dir,
                "No Java source path was implicated by the current gate evidence.",
            )
        sync_before_head = git_head(self.repo)
        sync_before_fingerprint = self._worktree_fingerprint()
        sync = self._run_logged(
            "codegraph-sync",
            (self.codegraph_bin, "sync", ".", "--quiet"),
            attempt_dir,
            stream=True,
        )
        if sync.exit_code != 0:
            diagnostic = (sync.stderr or sync.stdout)[-4000:].strip()
            raise RuntimeError(
                "CodeGraph index synchronization failed: "
                + (diagnostic or f"exit {sync.exit_code}")
            )
        self._accept_safe_external_head_change(
            sync_before_head, "CodeGraph synchronization"
        )
        if self._worktree_fingerprint() != sync_before_fingerprint:
            raise RuntimeError(
                "CodeGraph synchronization modified tracked or untracked product files"
            )

        errors: list[str] = []
        for retry in range(1, self.codegraph_retries + 2):
            before_head = git_head(self.repo)
            before_fingerprint = self._worktree_fingerprint()
            result = self._run_logged(
                "opencode-codegraph"
                if retry == 1
                else f"opencode-codegraph-retry-{retry:02d}",
                (
                    self.opencode_bin,
                    "run",
                    "--dir",
                    str(self.repo),
                    "--agent",
                    self.opencode_agent,
                    "--format",
                    "json",
                    "--auto",
                    self._codegraph_reconnaissance_prompt(
                        task, gate, retry, focus_paths
                    ),
                ),
                attempt_dir,
                stream=True,
            )
            if result.exit_code != 0:
                errors.append(
                    f"try {retry}: exit={result.exit_code}; "
                    f"{(result.stderr or result.stdout)[-1000:].strip()}"
                )
                continue
            self._accept_safe_external_head_change(
                before_head, "CodeGraph reconnaissance"
            )
            if self._worktree_fingerprint() != before_fingerprint:
                raise RuntimeError(
                    "CodeGraph reconnaissance modified repository files"
                )

            calls = extract_codegraph_tool_calls(result.stdout)
            if not calls:
                errors.append(
                    f"try {retry}: OpenCode emitted no actual codegraph_* tool event"
                )
                continue

            report = extract_opencode_text(result.stdout).strip()
            if not report:
                report = (
                    "# CodeGraph reconnaissance report\n\n"
                    "The worker called CodeGraph but produced no model-authored report."
                )
            evidence = attempt_dir / "evidence"
            evidence.mkdir(parents=True, exist_ok=True)
            (evidence / "codegraph-tool-calls.json").write_text(
                json.dumps(
                    {
                        "required": True,
                        "calls": list(calls),
                    },
                    indent=2,
                    ensure_ascii=False,
                )
                + "\n",
                encoding="utf-8",
            )
            (evidence / "codegraph-reconnaissance.md").write_text(
                report.rstrip() + "\n",
                encoding="utf-8",
            )
            return report

        raise RuntimeError(
            "Mandatory CodeGraph reconnaissance was not proven after retries: "
            + " | ".join(errors)
        )

    def _opencode_prompt(
        self,
        task: Task,
        gate: CommandResult,
        next_action: str | None,
        codegraph_report: str,
        diagnostics: GateDiagnostics,
    ) -> str:
        action = next_action or "Implement the selected task completely."
        extra_instructions = self._current_codex_extra_instructions()
        ring_directive = self._current_ring_directive(task)
        exact_gate = shlex.join(task.gate)
        include_companion = self._use_full_instruction_bundle("edit")
        instruction_list = "\n".join(
            f"- {path.relative_to(self.repo) if path.is_relative_to(self.repo) else path}"
            for path in self._instruction_files(task, include_companion)
        )
        if getattr(self, "compact_local_worker", False):
            return f"""Implement only {task.id}: {task.objective}

Read these files before editing:
{instruction_list}

Codex plan:
{action}

The-Ring advisory directive:
{ring_directive}

Precedence: the exact task, exact gate and Codex plan override The-Ring.

Current gate exit: {gate.exit_code}
Current blocker: {diagnostics.summary}
Gate stderr tail:
{gate.stderr[-1800:]}

Edit only task-allowed product paths. Do not write Git history. Run exactly:
{exact_gate}
Stop after that exact gate result.
"""
        return f"""Read every file in this active instruction bundle before editing:
{instruction_list}

You are implementing only {task.id}: {task.objective}

CODEX PLAN OR REVISION ACTION:
{action}

CURRENT CODEX-TO-LOCAL EXTRA INSTRUCTIONS:
{extra_instructions}

CURRENT THE-RING CROSS-STACK DIRECTIVE (ADVISORY):
{ring_directive}

DIRECTIVE PRECEDENCE:
The exact task, deterministic gate and current Codex correction packet override The-Ring.
Use The-Ring only to preserve cross-stack coordination and avoid repeated failed approaches.

FOCUSED CODEGRAPH EVIDENCE (ADVISORY):
{codegraph_report}

CURRENT TASK GATE EXIT: {gate.exit_code}
CURRENT DIAGNOSTIC CLASSIFICATION: {diagnostics.classification}
CURRENT DIAGNOSTIC SUMMARY: {diagnostics.summary}
CODEX ERROR BUNDLE: {diagnostics.bundle_path}
IMPLICATED SOURCE PATHS:
{chr(10).join(f"- {path}" for path in diagnostics.source_paths) or "- none"}
CURRENT GATE STDOUT TAIL:
{gate.stdout[-4000:]}
CURRENT GATE STDERR TAIL:
{gate.stderr[-4000:]}

Codex has received the complete untruncated Maven output and packaged source files.
Do not reinterpret an infrastructure outage as a Java defect.
Do not edit task, controller, progress, memory or gate files. Do not run Git write commands.
Before editing, translate every numbered Codex instruction into an explicit code/test
checklist. Apply all items; do not stop after satisfying only the first one. Before the
gate, re-open the edited path and verify the checklist against exact assertions.
Use a verified focused CodeGraph map when available. If CodeGraph was unavailable,
follow Codex and exact source/compiler evidence instead of stopping the workflow.
Implement, then run exactly:
{exact_gate}
Do not add a pipeline, redirect, tee, tail, grep, echo suffix or log-file write.
The Python controller captures the gate output. Stop after the exact gate result.
"""

    def _opencode_assimilation_prompt(
        self,
        task: Task,
        gate: CommandResult,
        codegraph_report: str,
    ) -> str:
        include_companion = self._use_full_instruction_bundle("review")
        instruction_list = "\n".join(
            f"- {path.relative_to(self.repo) if path.is_relative_to(self.repo) else path}"
            for path in self._instruction_files(task, include_companion)
        )
        changed = (
            "\n".join(f"- {path}" for path in git_product_changed_paths(self.repo))
            or "- none"
        )
        extra_instructions = self._current_codex_extra_instructions()
        ring_directive = self._current_ring_directive(task)
        return f"""This is a read-only assimilation pass for {task.id}. Do not edit any file,
do not run Git write commands and do not run the task gate.

Read every instruction file in full:
{instruction_list}

CURRENT CODEX-TO-LOCAL EXTRA INSTRUCTIONS:
{extra_instructions}

CURRENT THE-RING ADVISORY DIRECTIVE:
{ring_directive}

VERIFIED CODEGRAPH RECONNAISSANCE:
{codegraph_report}

Inspect the currently changed implementation paths:
{changed}

Current gate exit: {gate.exit_code}

Return only a concise Markdown report with exactly these headings:

# Local understanding report
## Task objective in my own words
## Instructions I reconciled
## Mapping from requirements to changed code and tests
## Claims supported by current gate evidence
## Uncertainties, contradictions or possible instruction defects
## Questions or corrections requested from Codex

Be specific. For every numbered Codex instruction, state the exact file, method and
assertion or implementation line that satisfies it. Explicitly mark any item that is
not yet proven. Do not claim success merely because the gate is green. This report
is sent directly to Codex so it can identify your misunderstandings and correct the
next instruction packet.
"""

    def _write_local_understanding(
        self,
        attempt_dir: Path,
        stdout: str,
    ) -> None:
        evidence = attempt_dir / "evidence"
        evidence.mkdir(parents=True, exist_ok=True)
        report = extract_opencode_text(stdout)
        if not report:
            report = (
                "# Local understanding report\n\n"
                "The local worker produced no model-authored text. Codex must treat "
                "this as missing assimilation evidence.\n"
            )
        (evidence / "local-understanding.md").write_text(
            report.rstrip() + "\n",
            encoding="utf-8",
        )

    def _write_compact_local_understanding(
        self,
        attempt_dir: Path,
        stdout: str,
        task: Task,
        gate: CommandResult,
    ) -> None:
        evidence = attempt_dir / "evidence"
        evidence.mkdir(parents=True, exist_ok=True)
        report = extract_opencode_text(stdout).strip()
        if "# Local understanding report" not in report:
            changed = git_product_changed_paths(self.repo)
            changed_text = "\n".join(f"- {path}" for path in changed) or "- none"
            report = (
                "# Local understanding report\n\n"
                "## Task objective in my own words\n"
                f"{task.objective}\n\n"
                "## Instructions I reconciled\n"
                "The compact LP worker did not return its requested model-authored "
                "summary. Codex must inspect the exact diff.\n\n"
                "## Mapping from requirements to changed code and tests\n"
                f"{changed_text}\n\n"
                "## Claims supported by current gate evidence\n"
                "See the controller-verified post-edit evidence below.\n\n"
                "## Uncertainties, contradictions or possible instruction defects\n"
                "Model-authored compact summary missing.\n\n"
                "## Questions or corrections requested from Codex\n"
                "Review the exact patch and deterministic gate evidence."
            )
        report = report.rstrip()
        report += (
            "\n\n## Controller-verified post-edit evidence\n"
            f"- Exact task gate exit code: `{gate.exit_code}`.\n"
            f"- Gate timed out: `{'yes' if gate.timed_out else 'no'}`.\n"
            "- A green exit code proves only the assertions implemented by the exact gate; "
            "Codex must still review the diff and acceptance contract.\n"
        )
        (evidence / "local-understanding.md").write_text(
            report.rstrip() + "\n",
            encoding="utf-8",
        )

    def _write_failed_local_understanding(
        self,
        attempt_dir: Path,
        result: CommandResult,
    ) -> None:
        evidence = attempt_dir / "evidence"
        evidence.mkdir(parents=True, exist_ok=True)
        diagnostic = (result.stderr or result.stdout)[-4000:].strip()
        (evidence / "local-understanding.md").write_text(
            "# Local understanding report\n\n"
            "## Assimilation status\n"
            f"The local assimilation command failed with exit {result.exit_code}. "
            "Codex must review the implementation and gate evidence directly.\n\n"
            "## Diagnostic tail\n"
            f"{diagnostic or 'No diagnostic text was produced.'}\n",
            encoding="utf-8",
        )

    def _write_codex_extra_instructions(
        self,
        task: Task,
        review: dict[str, Any],
    ) -> None:
        """Persist Codex's correction packet outside the Git worktree evidence."""
        self.control_dir.mkdir(parents=True, exist_ok=True)
        if review["decision"] == "ACCEPT":
            self.codex_extra_instructions_path.unlink(missing_ok=True)
            return

        corrections = review.get("instruction_corrections") or []
        correction_lines = (
            "\n".join(f"- {value}" for value in corrections) or "- none"
        )
        body = str(review.get("corrected_extra_instructions") or "").strip()
        if not body:
            body = str(review.get("next_action") or "").strip()
        reviewed_paths = review.get("paths") or []
        path_lines = "\n".join(f"- `{value}`" for value in reviewed_paths) or "- none"
        content = f"""# Codex ↔ Qwen3 extra instructions

- Generated at: {datetime.now(timezone.utc).isoformat()}
- Active task: `{task.id}`
- Codex decision: `{review['decision']}`

## Reviewed or target paths

{path_lines}

## Immediate next action

{str(review.get('next_action') or '').strip() or 'Apply the resolved instructions below.'}

## Codex assessment of the local understanding

{review.get('local_understanding_assessment', '').strip() or 'No assessment supplied.'}

## Corrections to ambiguous, inaccurate or misunderstood instructions

{correction_lines}

## Mandatory resolved instructions for the next local pass

{body or 'Re-read the active task and fix the first unproven acceptance condition.'}
"""
        temporary = self.codex_extra_instructions_path.with_suffix(".md.tmp")
        temporary.write_text(content.rstrip() + "\n", encoding="utf-8")
        temporary.replace(self.codex_extra_instructions_path)

    def _run_logged(
        self, name: str, command: Sequence[str], directory: Path,
        input_text: str | None = None, stream: bool = False,
    ) -> CommandResult:
        logs = directory / "logs"
        evidence = directory / "evidence"
        logs.mkdir(parents=True, exist_ok=True)
        evidence.mkdir(parents=True, exist_ok=True)
        print(f"\n[r4r] {name}: {shlex.join(command)}", flush=True)
        watchdog = None
        if name == "opencode" or name.startswith("opencode-"):
            watchdog = CommandWatchdog(
                max_seconds=self.opencode_session_seconds,
                idle_seconds=self.opencode_idle_seconds,
                max_steps=self.opencode_max_steps,
                repeat_event_budget=self.opencode_repeat_event_budget,
            )
        result = run_command(
            command,
            self.repo,
            input_text,
            self.timeout,
            stream,
            watchdog=watchdog,
        )
        (logs / f"{name}.stdout.log").write_text(result.stdout, encoding="utf-8")
        (logs / f"{name}.stderr.log").write_text(result.stderr, encoding="utf-8")
        (evidence / f"{name}.json").write_text(json.dumps({
            "command": list(result.command),
            "exit_code": result.exit_code,
            "timed_out": result.timed_out,
            "stop_reason": result.stop_reason,
            "observed_steps": result.observed_steps,
            "meaningful_events": result.meaningful_events,
            "watchdog": asdict(watchdog) if watchdog is not None else None,
        }, indent=2), encoding="utf-8")
        return result

    def _run_gate(
        self, name: str, command: Sequence[str], directory: Path,
        input_text: str | None = None, stream: bool = False,
    ) -> CommandResult:
        result = self._run_logged(name, command, directory, input_text=input_text, stream=stream)
        if result.exit_code == 0:
            self._notify(directory, f"{name}-green", 2, f"{name}: tests are green")
        return result

    def _notify_file_changed(self, directory: Path, event: str, message: str) -> None:
        if not self.notify_script.exists() or not os.access(self.notify_script, os.X_OK):
            print(
                f"[r4r] notification skipped; executable not found: {self.notify_script}",
                file=sys.stderr,
            )
            return
        result = self._run_logged(
            f"notify-{event}",
            (str(self.notify_script), "--file-changed", message),
            directory,
            stream=True,
        )
        if result.exit_code != 0:
            print(
                f"[r4r] file-change notification failed for {event}; workflow continues",
                file=sys.stderr,
            )

    def _notify(self, directory: Path, event: str, count: int, message: str) -> None:
        if not self.notify_script.exists() or not os.access(self.notify_script, os.X_OK):
            print(f"[r4r] notification skipped; executable not found: {self.notify_script}", file=sys.stderr)
            return
        result = self._run_logged(
            f"notify-{event}",
            (str(self.notify_script), str(count), message),
            directory,
            stream=True,
        )
        if result.exit_code != 0:
            print(f"[r4r] notification failed for {event}; workflow continues", file=sys.stderr)

    def _write_patch(self, attempt_dir: Path, changed: Sequence[str]) -> None:
        evidence = attempt_dir / "evidence"
        evidence.mkdir(parents=True, exist_ok=True)
        parts: list[str] = []

        if changed:
            unstaged = run_command(
                ("git", "diff", "--binary", "--no-ext-diff", "--", *changed),
                self.repo,
            )
            if unstaged.exit_code != 0:
                raise RuntimeError(unstaged.stderr.strip() or "Unable to capture unstaged changes")
            if unstaged.stdout:
                parts.append(unstaged.stdout)

            staged = run_command(
                ("git", "diff", "--cached", "--binary", "--no-ext-diff", "--", *changed),
                self.repo,
            )
            if staged.exit_code != 0:
                raise RuntimeError(staged.stderr.strip() or "Unable to capture staged changes")
            if staged.stdout:
                parts.append(staged.stdout)

            untracked = _nul_paths(
                run_command(("git", "ls-files", "--others", "--exclude-standard", "-z"), self.repo)
            )
            for relative in sorted(set(changed).intersection(untracked)):
                path = self.repo / relative
                if not path.is_file():
                    continue
                addition = run_command(
                    ("git", "diff", "--no-index", "--binary", "--", "/dev/null", relative),
                    self.repo,
                )
                # `git diff --no-index` returns 1 when differences are present.
                if addition.exit_code not in (0, 1):
                    raise RuntimeError(addition.stderr.strip() or f"Unable to capture untracked file: {relative}")
                if addition.stdout:
                    parts.append(addition.stdout)

        (evidence / "changes.patch").write_text("".join(parts), encoding="utf-8")

    def _write_lock(self, task: Task) -> None:
        """Compatibility no-op: active-task locks are disabled."""
        self.lock_path.unlink(missing_ok=True)

    def _accept_progress(self, task: Task) -> None:
        item = task_progress(self.progress, task.id)
        item["status"] = "ACCEPTED"
        item["accepted_at"] = datetime.now(timezone.utc).isoformat()
        self.progress["active_task"] = None
        self.progress["last_run"] = self.run_id

    def _mark_blocked(self, task: Task) -> None:
        item = task_progress(self.progress, task.id)
        item["status"] = "BLOCKED"
        self.progress["active_task"] = task.id
        self.progress["last_run"] = self.run_id
        self._write_progress(task.id)
        self._write_memory()

    def _write_progress(self, active_task: str | None) -> None:
        self.progress["active_task"] = active_task
        self.progress["last_run"] = self.run_id
        self.progress_path.write_text(json.dumps(self.progress, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")

    def _request_ring_review(
        self,
        task: Task,
        *,
        reason: str,
        attempt: int | None = None,
        gate: CommandResult | None = None,
        review: dict[str, Any] | None = None,
        checkpoint_head: str | None = None,
    ) -> None:
        path = getattr(self, "ring_request_path", None)
        if path is None:
            return
        path = Path(path)
        path.parent.mkdir(parents=True, exist_ok=True)
        payload = {
            "schema_version": 1,
            "worker": self.worker_id,
            "task_id": task.id,
            "reason": reason,
            "requested_at": datetime.now(timezone.utc).isoformat(),
            "run_id": self.run_id,
            "attempt": attempt,
            "gate_exit": gate.exit_code if gate is not None else None,
            "gate_stop_reason": gate.stop_reason if gate is not None else None,
            "codex_decision": review.get("decision") if review else None,
            "next_action": str(review.get("next_action") or "").strip() if review else None,
            "checkpoint_head": checkpoint_head,
            "memory_path": str(self.memory_path),
            "changed_paths": list(self.memory_context.get("changed_paths") or []),
        }
        temporary = path.with_suffix(".json.tmp")
        temporary.write_text(
            json.dumps(payload, indent=2, ensure_ascii=False) + "\n",
            encoding="utf-8",
        )
        temporary.replace(path)

    def _checkpoint_green(
        self,
        task: Task,
        gate: CommandResult,
        attempt: int,
        attempt_dir: Path,
    ) -> str | None:
        product_paths = self._manual_commit_paths(task)
        now = datetime.now(timezone.utc).isoformat()
        item = task_progress(self.progress, task.id)
        item["last_gate_green_at"] = now
        item["last_gate_green_attempt"] = attempt
        item["last_gate_green_run"] = self.run_id
        self.memory_context.update(
            {
                "task_id": task.id,
                "attempt": attempt,
                "gate_exit": gate.exit_code,
                "gate_name": "task-gate",
                "changed_paths": list(product_paths),
                "demonstrated": [
                    "The exact deterministic task gate completed with exit code 0.",
                    "The checkpoint contains only task-owned product paths plus controller progress/memory.",
                ],
                "checkpoint_status": (
                    "disabled" if not self.checkpoint_on_green
                    else "no-product-diff" if not product_paths
                    else "pending"
                ),
            }
        )
        self._write_progress(task.id)
        self._write_memory()

        checkpoint_payload: dict[str, Any] = {
            "schema_version": 1,
            "worker": self.worker_id,
            "task_id": task.id,
            "run_id": self.run_id,
            "attempt": attempt,
            "created_at": now,
            "gate_exit": gate.exit_code,
            "product_paths": list(product_paths),
            "checkpoint_enabled": self.checkpoint_on_green,
            "auto_commit": self.auto_commit,
            "status": self.memory_context["checkpoint_status"],
            "head_before": git_head(self.repo),
            "head_after": None,
        }
        evidence = attempt_dir / "evidence"
        evidence.mkdir(parents=True, exist_ok=True)
        checkpoint_path = evidence / "checkpoint.json"

        if not self.checkpoint_on_green or not self.auto_commit or not product_paths:
            checkpoint_path.write_text(
                json.dumps(checkpoint_payload, indent=2, ensure_ascii=False) + "\n",
                encoding="utf-8",
            )
            self._request_ring_review(
                task,
                reason="gate-green-no-checkpoint",
                attempt=attempt,
                gate=gate,
            )
            return None

        message = (
            f"wip({self.worker_id.lower()}/{task.id}): "
            f"gate-green checkpoint attempt-{attempt:02d}"
        )
        head = self._commit_if_needed(
            message,
            (
                *task.allowed_paths,
                str(self.progress_path.relative_to(self.repo)),
                str(self.memory_path.relative_to(self.repo)),
            ),
        )
        checkpoint_payload["head_after"] = head
        checkpoint_payload["status"] = "created" if head else "failed"
        checkpoint_path.write_text(
            json.dumps(checkpoint_payload, indent=2, ensure_ascii=False) + "\n",
            encoding="utf-8",
        )
        self.memory_context["checkpoint_head"] = head
        self.memory_context["checkpoint_status"] = checkpoint_payload["status"]
        self._request_ring_review(
            task,
            reason="gate-green-checkpoint",
            attempt=attempt,
            gate=gate,
            checkpoint_head=head,
        )
        if head is None:
            raise RuntimeError("Automatic gate-green checkpoint commit failed")
        return head

    def _write_memory(self) -> None:
        accepted = [item for item in self.progress["tasks"] if item["status"] == "ACCEPTED"]
        pending = [item for item in self.progress["tasks"] if item["status"] != "ACCEPTED"]
        last = accepted[-1]["id"] if accepted else "None"
        context = getattr(self, "memory_context", {})

        changed = list(context.get("changed_paths") or [])
        demonstrated = list(context.get("demonstrated") or [])
        outstanding = list(context.get("outstanding") or [])
        avoid = list(context.get("avoid_repeating") or [])
        next_action = str(context.get("next_action") or "").strip()

        lines = [
            "# Agent memory",
            "",
            "## Current state",
            "",
            f"- Worker: {self.worker_id}.",
            f"- Run: {self.run_id}.",
            f"- Last accepted task: {last}.",
            f"- Active task: {self.progress.get('active_task') or 'None'}.",
            f"- Current attempt: {context.get('attempt') or 'not started'}.",
            f"- Latest exact gate: {context.get('gate_name') or 'not run'}; exit={context.get('gate_exit') if context.get('gate_exit') is not None else 'unknown'}.",
            f"- Latest Codex decision: {context.get('codex_decision') or 'pending'}.",
            f"- Checkpoint: {context.get('checkpoint_status') or 'none'}; head={context.get('checkpoint_head') or 'not recorded'}.",
            f"- Accepted: {', '.join(item['id'] for item in accepted) or 'none'}.",
            f"- Remaining: {', '.join(item['id'] for item in pending) or 'none'}.",
            f"- Exact plan: `{self.plan_display}`.",
            "",
            "## Files currently owned or edited",
            "",
        ]
        lines.extend(f"- `{path}`" for path in changed)
        if not changed:
            lines.append("- No task-owned dirty product path at the latest snapshot.")

        lines.extend(["", "## Demonstrated by current evidence", ""])
        lines.extend(f"- {value}" for value in demonstrated)
        if not demonstrated:
            lines.append("- No new acceptance claim has been demonstrated in this run yet.")

        lines.extend(["", "## Still unproven or below expectations", ""])
        lines.extend(f"- {value}" for value in outstanding)
        if not outstanding:
            lines.append("- Awaiting the next Codex decision or exact gate evidence.")

        lines.extend(["", "## Approaches not to repeat", ""])
        lines.extend(f"- {value}" for value in avoid)
        if not avoid:
            lines.append("- Do not repeat an unchanged failing action without new evidence.")

        lines.extend(["", "## Next exact action", ""])
        lines.append(next_action or "Run the active task's exact deterministic gate and act on its first current failure.")

        lines.extend([
            "",
            "## Fixed decisions",
            "",
            "- OpenCode/Qwen3 and Codex never write Git history.",
            "- The deterministic Python controller may create a gate-green checkpoint and a final ACCEPT commit.",
            "- A gate-green checkpoint preserves useful work but does not mark the task ACCEPTED.",
            "- A task completes only after its exact gate is green and Codex returns `ACCEPT`.",
            "- PostgreSQL only in Docker; Flyway owns application schema.",
            "- Spring AI abstractions; no handwritten Ollama HTTP client.",
            "- Every red gate retains complete diagnostics for Codex.",
            "- CodeGraph is focused retrieval evidence, not authority to expand task scope.",
            "- Runtime evidence stays under `runtime/runs/`; no automatic push.",
            "",
            "## Task ledger",
            "",
        ])
        for item in self.progress["tasks"]:
            lines.append(
                f"- {item['id']}: {item['status']} — accepted at "
                f"{item.get('accepted_at') or 'not accepted'}; "
                f"last green attempt={item.get('last_gate_green_attempt') or 'none'}"
            )
        self.memory_path.parent.mkdir(parents=True, exist_ok=True)
        self.memory_path.write_text("\n".join(lines) + "\n", encoding="utf-8")

    def _commit_if_needed(
        self,
        message: str,
        allowed_patterns: Sequence[str] | None = None,
    ) -> str | None:
        # PC and LP share the same Git index and HEAD in the current topology.
        # Serialize only the short add/check/commit section; model work and gates
        # remain concurrent. Manual Git commands do not honor this cooperative lock.
        with exclusive_file_lock(self.git_commit_lock_path):
            changed = git_changed_paths(self.repo)
            if allowed_patterns is None:
                selected = changed
            else:
                selected = tuple(
                    path for path in changed
                    if path_is_allowed(path, allowed_patterns)
                )
            if not selected:
                return git_head(self.repo)

            add = run_command(("git", "add", "-A", "--", *selected), self.repo)
            if add.exit_code != 0:
                print(
                    "[r4r] git add failed\n"
                    + add.stdout + add.stderr,
                    file=sys.stderr,
                    flush=True,
                )
                return None
            check = run_command(
                ("git", "diff", "--cached", "--check", "--", *selected),
                self.repo,
            )
            if check.exit_code != 0:
                print(
                    "[r4r] git diff --cached --check failed\n"
                    + check.stdout + check.stderr,
                    file=sys.stderr,
                    flush=True,
                )
                return None

            # --only commits the selected task/progress paths and deliberately
            # leaves peer work, downloads and unrelated staged paths untouched.
            # Per-command identity avoids races through shared .git/config.
            commit_env = os.environ.copy()
            commit_env.update(
                {
                    "GIT_AUTHOR_NAME": self.git_author_name,
                    "GIT_AUTHOR_EMAIL": self.git_author_email,
                    "GIT_COMMITTER_NAME": self.git_author_name,
                    "GIT_COMMITTER_EMAIL": self.git_author_email,
                }
            )
            commit = run_command(
                (
                    "git",
                    "-c",
                    f"user.name={self.git_author_name}",
                    "-c",
                    f"user.email={self.git_author_email}",
                    "commit",
                    "--only",
                    "-m",
                    message,
                    "--",
                    *selected,
                ),
                self.repo,
                timeout_seconds=self.timeout,
                stream=True,
                env=commit_env,
            )
            if commit.exit_code != 0:
                print(
                    "[r4r] git commit failed\n"
                    + commit.stdout + commit.stderr,
                    file=sys.stderr,
                    flush=True,
                )
                return None

            identity = run_command(
                (
                    "git",
                    "show",
                    "-s",
                    "--format=%an%x00%ae%x00%cn%x00%ce",
                    "HEAD",
                ),
                self.repo,
            )
            observed = identity.stdout.rstrip("\n").split("\0")
            expected = [
                self.git_author_name,
                self.git_author_email,
                self.git_author_name,
                self.git_author_email,
            ]
            if identity.exit_code != 0 or observed != expected:
                print(
                    "[r4r] committed Git identity mismatch\n"
                    f"expected={expected!r}\n"
                    f"observed={observed!r}\n"
                    + identity.stderr,
                    file=sys.stderr,
                    flush=True,
                )
                return None

            head = git_head(self.repo)
            print(
                f"[r4r] committed {head} as "
                f"{self.git_author_name} <{self.git_author_email}>",
                flush=True,
            )
            return head

    def _task_by_id(self, task_id: str) -> Task:
        for task in self.plan.tasks:
            if task.id == task_id:
                return task
        raise ValueError(f"Unknown task id: {task_id}")

    def _finish(self, status: str, exit_code: int, extra: dict[str, Any] | None = None) -> int:
        state: dict[str, Any] = {
            "schema_version": 1,
            "run_id": self.run_id,
            "status": status,
            "exit_code": exit_code,
            "finished_at": datetime.now(timezone.utc).isoformat(),
            "git_head": git_head(self.repo),
            "git_author_name": self.git_author_name,
            "git_author_email": self.git_author_email,
            "changed_paths": git_changed_paths(self.repo),
        }
        if extra:
            state.update(extra)
        (self.run_dir / "state.json").write_text(json.dumps(state, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
        print(f"\n[r4r] {status} (exit {exit_code})", flush=True)
        notify_script = getattr(self, "notify_script", None)
        if (
            exit_code != 0
            and isinstance(notify_script, Path)
            and notify_script.exists()
            and os.access(notify_script, os.X_OK)
        ):
            notification = run_command(
                (str(notify_script), "--error", f"{status} (exit {exit_code})"),
                self.repo,
                timeout_seconds=30,
            )
            if notification.exit_code != 0:
                print("[r4r] error sound notification failed; workflow state is preserved", file=sys.stderr)
        return exit_code
