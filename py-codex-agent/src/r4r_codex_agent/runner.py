from __future__ import annotations

from dataclasses import asdict, dataclass
from datetime import datetime, timezone
from fnmatch import fnmatch
import hashlib
import json
import os
from pathlib import Path
import shlex
import shutil
import signal
import subprocess
import sys
import threading
from typing import Any, Sequence

from .contracts import Task, TaskPlan, load_progress, task_progress, validate_structured_result


@dataclass(frozen=True)
class CommandResult:
    command: tuple[str, ...]
    exit_code: int
    stdout: str
    stderr: str
    timed_out: bool = False


def run_command(
    command: Sequence[str], cwd: Path, input_text: str | None = None,
    timeout_seconds: int | None = None, stream: bool = False,
) -> CommandResult:
    if not command:
        raise ValueError("Command cannot be empty")
    process = subprocess.Popen(
        list(command), cwd=cwd,
        stdin=subprocess.PIPE if input_text is not None else None,
        stdout=subprocess.PIPE, stderr=subprocess.PIPE,
        text=True, bufsize=1, start_new_session=True,
    )
    stdout_parts: list[str] = []
    stderr_parts: list[str] = []

    def consume(pipe: Any, target: list[str], display: Any) -> None:
        try:
            for line in iter(pipe.readline, ""):
                target.append(line)
                if stream:
                    print(line, end="", file=display, flush=True)
        finally:
            pipe.close()

    threads = [
        threading.Thread(target=consume, args=(process.stdout, stdout_parts, sys.stdout), daemon=True),
        threading.Thread(target=consume, args=(process.stderr, stderr_parts, sys.stderr), daemon=True),
    ]
    for thread in threads:
        thread.start()
    if input_text is not None and process.stdin is not None:
        process.stdin.write(input_text)
        process.stdin.close()
    timed_out = False
    try:
        exit_code = process.wait(timeout=timeout_seconds)
    except subprocess.TimeoutExpired:
        timed_out = True
        os.killpg(process.pid, signal.SIGTERM)
        try:
            exit_code = process.wait(timeout=10)
        except subprocess.TimeoutExpired:
            os.killpg(process.pid, signal.SIGKILL)
            exit_code = process.wait()
        exit_code = 124
    for thread in threads:
        thread.join(timeout=5)
    return CommandResult(tuple(command), exit_code, "".join(stdout_parts), "".join(stderr_parts), timed_out)


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


def git_worktree_fingerprint(repo: Path) -> str:
    """Return a content fingerprint for tracked changes and untracked files."""
    digest = hashlib.sha256()

    head = git_head(repo)
    diff_command = ("git", "diff", "--binary", "--no-ext-diff", head or "--root")
    tracked = run_command(diff_command, repo)
    if tracked.exit_code != 0:
        raise RuntimeError(tracked.stderr.strip() or "Unable to fingerprint tracked changes")
    digest.update(tracked.stdout.encode("utf-8", errors="surrogateescape"))

    untracked = _nul_paths(run_command(("git", "ls-files", "--others", "--exclude-standard", "-z"), repo))
    for relative in sorted(untracked):
        digest.update(relative.encode("utf-8", errors="surrogateescape"))
        path = repo / relative
        if path.is_file():
            digest.update(path.read_bytes())
        elif path.is_symlink():
            digest.update(os.readlink(path).encode("utf-8", errors="surrogateescape"))

    return digest.hexdigest()


def path_is_allowed(path: str, allowed_patterns: Sequence[str]) -> bool:
    return any(fnmatch(path, pattern) for pattern in allowed_patterns)


LOCK_AUTO_ADVANCE_PATHS = (
    "scripts/run-codex-agent.sh",
    "scripts/export-evaluation.sh",
    "scripts/notify-success.sh",
    "py-codex-agent/**",
    ".opencode/commands/*",
)


def git_paths_between(repo: Path, base: str, head: str) -> tuple[str, ...]:
    result = run_command(("git", "diff", "--name-only", "-z", f"{base}..{head}"), repo)
    return tuple(sorted(_nul_paths(result)))


def git_is_ancestor(repo: Path, ancestor: str, descendant: str) -> bool:
    result = run_command(("git", "merge-base", "--is-ancestor", ancestor, descendant), repo)
    return result.exit_code == 0


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
    """Extract the final structured local-understanding report from JSONL."""
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


class AutomaticRunner:
    def __init__(self, repo: Path, plan: TaskPlan, progress_path: Path):
        self.repo = repo.resolve()
        self.plan = plan
        self.progress_path = progress_path
        self.progress = load_progress(progress_path, (task.id for task in plan.tasks))
        self.timeout = int(os.environ.get("R4R_COMMAND_TIMEOUT_SECONDS", "14400"))
        self.max_revisions = int(os.environ.get("R4R_MAX_REVISIONS_PER_TASK", "2"))
        self.max_tasks = int(os.environ.get("R4R_MAX_TASKS_PER_RUN", "0"))
        self.auto_commit = os.environ.get("R4R_AUTO_COMMIT", "true").lower() == "true"
        self.bootstrap_commit = os.environ.get("R4R_BOOTSTRAP_COMMIT", "true").lower() == "true"
        self.opencode_bin = os.environ.get("R4R_OPENCODE_BIN", "opencode")
        self.opencode_agent = os.environ.get("R4R_OPENCODE_AGENT", "r4r-pc")
        self.codex_bin = os.environ.get("R4R_CODEX_BIN", "codex")
        self.codex_model = os.environ.get("R4R_CODEX_MODEL", "").strip() or None
        stamp = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
        self.run_id = stamp
        self.run_dir = self.repo / "runtime" / "runs" / stamp
        self.run_dir.mkdir(parents=True, exist_ok=False)
        self.lock_path = self.repo / "runtime" / "locks" / "active-task.json"
        self.memory_path = self.repo / ".opencode" / "memory.md"
        self.notify_script = self.repo / "scripts" / "notify-success.sh"
        self.control_dir = self.repo / "runtime" / "control"
        self.control_dir.mkdir(parents=True, exist_ok=True)
        self.codex_extra_instructions_path = (
            self.control_dir / "codex-qwen3-extra-instructions.md"
        )
        self.verified_green: set[str] = set()

    def execute(self) -> int:
        self._require_binary(self.opencode_bin)
        self._require_binary(self.codex_bin)
        dirty = git_changed_paths(self.repo)
        if self.lock_path.exists():
            # A valid lock owns the current task, even when the worktree happens
            # to be clean. Validate it before selecting any task so a later-task
            # failure can never reopen an already accepted task.
            self._validate_resume_lock(dirty)
        elif dirty:
            if not self._can_bootstrap(dirty):
                return self._finish("DIRTY_WORKTREE_UNOWNED", 64, {"changed_paths": dirty})
            result = self._bootstrap()
            if result != 0:
                return result

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
                    self._commit_if_needed("chore: record completed R4R task plan")
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

    def _require_binary(self, binary: str) -> None:
        if shutil.which(binary) is None:
            raise RuntimeError(f"Required executable not found: {binary}. Run ./scripts/setup.sh")

    def _can_bootstrap(self, dirty: Sequence[str]) -> bool:
        if not self.bootstrap_commit or self.lock_path.exists():
            return False
        return all(item["status"] == "PENDING" for item in self.progress["tasks"])

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
            return self._finish("BOOTSTRAP_READY_COMMIT_REQUIRED", 0, {"task": task.id})
        if self._commit_if_needed(task.commit_message) is None:
            return self._finish("BOOTSTRAP_COMMIT_FAILED", 67)
        return 0

    def _validate_resume_lock(self, dirty: Sequence[str]) -> None:
        lock = json.loads(self.lock_path.read_text(encoding="utf-8"))
        task = self._task_by_id(str(lock.get("task_id")))
        current_head = git_head(self.repo)
        base_commit = str(lock.get("base_commit") or "")
        if base_commit != current_head:
            self._auto_advance_resume_lock(lock, base_commit, current_head)
        disallowed = [path for path in dirty if not path_is_allowed(path, task.allowed_paths)]
        if disallowed:
            raise RuntimeError(f"Dirty resume contains out-of-scope paths: {disallowed}")

    def _auto_advance_resume_lock(
        self, lock: dict[str, Any], base_commit: str, current_head: str | None,
    ) -> None:
        if not base_commit or not current_head:
            raise RuntimeError("Active-task lock has no usable Git base commit")
        if not git_is_ancestor(self.repo, base_commit, current_head):
            raise RuntimeError(
                "Active-task lock diverged from current Git HEAD; manual review is required"
            )

        committed_paths = git_paths_between(self.repo, base_commit, current_head)
        unsafe_paths = [
            path for path in committed_paths
            if not path_is_allowed(path, LOCK_AUTO_ADVANCE_PATHS)
        ]
        if unsafe_paths:
            raise RuntimeError(
                "Active-task lock cannot advance across non-maintenance commits: "
                f"{unsafe_paths}"
            )

        lock["base_commit"] = current_head
        lock["run_id"] = (
            "resume-" + datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
        )
        temporary = self.lock_path.with_suffix(".json.tmp")
        temporary.write_text(
            json.dumps(lock, indent=2, ensure_ascii=False) + "\n",
            encoding="utf-8",
        )
        temporary.replace(self.lock_path)
        print(
            f"[r4r] active-task lock advanced automatically to {current_head[:12]}",
            flush=True,
        )

    def _select_task(self) -> Task | None:
        # The active lock is authoritative. It represents unfinished work that
        # belongs to exactly one task and must be resumed before considering the
        # progress file. In particular, tests introduced by a pending task must
        # not cause an earlier accepted task to be reopened as a regression.
        if self.lock_path.exists():
            lock = json.loads(self.lock_path.read_text(encoding="utf-8"))
            return self._task_by_id(str(lock.get("task_id")))

        # Without a lock, advance monotonically to the first task that has not
        # been accepted. Cross-task regression detection is delegated to each
        # current task gate and to the final gate, both of which run the complete
        # deterministic verification suite.
        for task in self.plan.tasks:
            if task_progress(self.progress, task.id)["status"] != "ACCEPTED":
                return task
        return None

    def _execute_task(self, task: Task) -> int:
        task_root = self.run_dir / task.id
        task_root.mkdir(parents=True, exist_ok=True)
        self._write_lock(task)
        initial_gate = self._run_gate("initial-gate", task.gate, task_root, stream=True)
        next_action: str | None = None
        if initial_gate.exit_code != 0:
            plan = self._codex_plan(task, initial_gate, task_root)
            if plan["decision"] == "BLOCKED":
                self._mark_blocked(task)
                return self._finish("CODEX_PLAN_BLOCKED", 68, {"task": task.id, "plan": plan})
            next_action = "\n".join(f"{index + 1}. {value}" for index, value in enumerate(plan["instructions"]))

        for attempt in range(1, self.max_revisions + 2):
            attempt_dir = task_root / f"attempt-{attempt:02d}"
            attempt_dir.mkdir(parents=True, exist_ok=True)
            current_gate = initial_gate if attempt == 1 else self._run_gate("pre-edit-gate", task.gate, attempt_dir)
            if current_gate.exit_code != 0 or next_action:
                prompt = self._opencode_prompt(task, current_gate, next_action)
                before_head = git_head(self.repo)
                before_fingerprint = git_worktree_fingerprint(self.repo)
                edit = self._run_logged(
                    "opencode",
                    (self.opencode_bin, "run", "--dir", str(self.repo), "--agent", self.opencode_agent,
                     "--format", "json", "--auto", prompt),
                    attempt_dir, stream=True,
                )
                if edit.exit_code != 0:
                    return self._finish("OPENCODE_FAILED", edit.exit_code, {"task": task.id, "attempt": attempt})
                after_fingerprint = git_worktree_fingerprint(self.repo)
                if after_fingerprint != before_fingerprint:
                    self._notify(
                        attempt_dir, f"files-changed-{attempt:02d}", 1,
                        f"{task.id}: repository files changed",
                    )
                if git_head(self.repo) != before_head:
                    return self._finish("OPENCODE_GIT_WRITE_VIOLATION", 69, {"task": task.id})
                changed = git_changed_paths(self.repo)
                disallowed = [path for path in changed if not path_is_allowed(path, task.allowed_paths)]
                (attempt_dir / "evidence").mkdir(exist_ok=True)
                (attempt_dir / "evidence" / "changed-paths.json").write_text(
                    json.dumps({"changed_paths": changed, "disallowed_paths": disallowed}, indent=2), encoding="utf-8"
                )
                if disallowed:
                    return self._finish("SCOPE_VIOLATION", 65, {"task": task.id, "paths": disallowed})
                self._write_patch(attempt_dir, changed)

            gate = self._run_gate("task-gate", task.gate, attempt_dir, stream=True)
            if gate.exit_code == 0:
                self._notify(
                    attempt_dir, f"handoff-to-codex-{attempt:02d}", 4,
                    f"{task.id}: green gate, handing control to Codex",
                )

            # Force the local worker to re-read the active instruction bundle and
            # explain its understanding before Codex reviews the implementation.
            # This creates an explicit local-model -> Codex communication channel.
            assimilation_before_head = git_head(self.repo)
            assimilation_before_fingerprint = git_worktree_fingerprint(self.repo)
            assimilation = self._run_logged(
                "opencode-assimilation",
                (
                    self.opencode_bin, "run", "--dir", str(self.repo),
                    "--agent", self.opencode_agent, "--format", "json", "--auto",
                    self._opencode_assimilation_prompt(task, gate),
                ),
                attempt_dir,
                stream=True,
            )
            if assimilation.exit_code != 0:
                return self._finish(
                    "OPENCODE_ASSIMILATION_FAILED",
                    assimilation.exit_code,
                    {"task": task.id, "attempt": attempt},
                )
            if git_head(self.repo) != assimilation_before_head:
                return self._finish(
                    "OPENCODE_ASSIMILATION_GIT_WRITE_VIOLATION",
                    69,
                    {"task": task.id, "attempt": attempt},
                )
            if git_worktree_fingerprint(self.repo) != assimilation_before_fingerprint:
                return self._finish(
                    "OPENCODE_ASSIMILATION_FILE_WRITE_VIOLATION",
                    65,
                    {"task": task.id, "attempt": attempt},
                )
            self._write_local_understanding(attempt_dir, assimilation.stdout)

            review = self._codex_review(task, gate, attempt_dir)
            if review["decision"] == "ACCEPT":
                if gate.exit_code != 0:
                    next_action = (
                        "The task gate is still red. Fix the first current gate "
                        "failure before requesting ACCEPT."
                    )
                    invalid_accept = dict(review)
                    invalid_accept["decision"] = "REVISE"
                    invalid_accept["local_understanding_assessment"] = (
                        str(review.get("local_understanding_assessment") or "").strip()
                        + " Codex attempted ACCEPT while the deterministic gate was red."
                    ).strip()
                    invalid_accept["instruction_corrections"] = [
                        *list(review.get("instruction_corrections") or []),
                        "A red deterministic task gate always overrides an ACCEPT decision.",
                    ]
                    invalid_accept["corrected_extra_instructions"] = next_action
                    self._write_codex_extra_instructions(task, invalid_accept)
                    continue
                self._write_codex_extra_instructions(task, review)
                self._accept_progress(task)
                self.verified_green.add(task.id)
                self._write_progress(None)
                self._write_memory()
                if not self.auto_commit:
                    return self._finish("TASK_ACCEPTED_COMMIT_REQUIRED", 0, {"task": task.id})
                if self._commit_if_needed(task.commit_message) is None:
                    return self._finish("AUTO_COMMIT_FAILED", 67, {"task": task.id})
                self.lock_path.unlink(missing_ok=True)
                return 0

            self._write_codex_extra_instructions(task, review)
            if review["decision"] == "BLOCKED":
                self._mark_blocked(task)
                return self._finish("CODEX_REVIEW_BLOCKED", 68, {"task": task.id, "review": review})
            next_action = review["next_action"]
        return self._finish("REVISION_LIMIT_REACHED", 70, {"task": task.id})

    def _codex_plan(self, task: Task, gate: CommandResult, task_dir: Path) -> dict[str, Any]:
        output = task_dir / "decisions" / "codex-plan.json"
        output.parent.mkdir(parents=True, exist_ok=True)
        prompt = self._structured_prompt("plan", task, gate, task_dir)
        command = codex_exec_command(
            self.codex_bin, self.repo / "py-codex-agent/schemas/plan.schema.json", output, self.codex_model
        )
        result = self._run_logged("codex-plan", command, task_dir, input_text=prompt, stream=True)
        if result.exit_code != 0 or not output.exists():
            raise RuntimeError("Codex planning command failed or produced no structured output")
        value = validate_structured_result(json.loads(output.read_text(encoding="utf-8")), task.id, {"READY", "BLOCKED"})
        return value

    def _codex_review(self, task: Task, gate: CommandResult, attempt_dir: Path) -> dict[str, Any]:
        output = attempt_dir / "decisions" / "codex-review.json"
        output.parent.mkdir(parents=True, exist_ok=True)
        prompt = self._structured_prompt("review", task, gate, attempt_dir)
        command = codex_exec_command(
            self.codex_bin, self.repo / "py-codex-agent/schemas/review.schema.json", output, self.codex_model
        )
        result = self._run_logged("codex-review", command, attempt_dir, input_text=prompt, stream=True)
        if result.exit_code != 0 or not output.exists():
            raise RuntimeError("Codex review command failed or produced no structured output")
        return validate_structured_result(
            json.loads(output.read_text(encoding="utf-8")), task.id, {"ACCEPT", "REVISE", "BLOCKED"}
        )

    def _instruction_files(self, task: Task) -> tuple[Path, ...]:
        """Return the deterministic instruction bundle for the active task."""
        candidates: list[Path] = [
            self.repo / "AGENTS.md",
            self.repo / ".opencode" / "commands" / "task.md",
            self.repo / task.command,
        ]

        task_path = self.repo / task.command
        if task_path.parent == self.repo / ".opencode" / "commands":
            # Include task-specific companion guides such as
            # task-02-ingestion-implementation-guide.md, but never unrelated tasks.
            candidates.extend(sorted(task_path.parent.glob(f"{task_path.stem}*.md")))

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

    def _instruction_bundle(self, task: Task) -> str:
        sections: list[str] = []
        for path in self._instruction_files(task):
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
        local_understanding = (
            local_understanding_path.read_text(encoding="utf-8")
            if local_understanding_path.exists()
            else "No local understanding report was produced."
        )
        extra_instructions = (
            self.codex_extra_instructions_path.read_text(encoding="utf-8")
            if self.codex_extra_instructions_path.exists()
            else "No active Codex-to-local extra instructions."
        )
        tail_stdout = gate.stdout[-12000:]
        tail_stderr = gate.stderr[-12000:]
        return (
            contract
            + "\n\nACTIVE INSTRUCTION BUNDLE\n"
            + self._instruction_bundle(task)
            + "\n\nCURRENT MEMORY\n"
            + memory
            + "\n\nLOCAL MODEL UNDERSTANDING REPORT\n"
            + local_understanding
            + "\n\nCURRENT CODEX-TO-LOCAL EXTRA INSTRUCTIONS\n"
            + extra_instructions
            + f"\n\nTASK ID\n{task.id}"
            + f"\nEVIDENCE DIRECTORY\n{evidence_dir.relative_to(self.repo)}"
            + f"\n\nCURRENT GATE EXIT\n{gate.exit_code}"
            + f"\nGATE STDOUT TAIL\n{tail_stdout}"
            + f"\nGATE STDERR TAIL\n{tail_stderr}\n"
        )

    def _opencode_prompt(
        self,
        task: Task,
        gate: CommandResult,
        next_action: str | None,
    ) -> str:
        action = next_action or "Implement the selected task completely."
        instruction_list = "\n".join(
            f"- {path.relative_to(self.repo) if path.is_relative_to(self.repo) else path}"
            for path in self._instruction_files(task)
        )
        return f"""Read every file in this active instruction bundle before editing:
{instruction_list}

You are implementing only {task.id}: {task.objective}

CODEX PLAN OR REVISION ACTION:
{action}

CURRENT TASK GATE EXIT: {gate.exit_code}
CURRENT GATE STDOUT TAIL:
{gate.stdout[-8000:]}
CURRENT GATE STDERR TAIL:
{gate.stderr[-8000:]}

Do not edit task, controller, progress, memory or gate files. Do not run Git write commands.
Use CodeGraph only when useful. Implement, run the exact task gate, and stop.
"""

    def _opencode_assimilation_prompt(
        self,
        task: Task,
        gate: CommandResult,
    ) -> str:
        instruction_list = "\n".join(
            f"- {path.relative_to(self.repo) if path.is_relative_to(self.repo) else path}"
            for path in self._instruction_files(task)
        )
        changed = (
            "\n".join(f"- {path}" for path in git_changed_paths(self.repo))
            or "- none"
        )
        return f"""This is a read-only assimilation pass for {task.id}. Do not edit any file,
do not run Git write commands and do not run the task gate.

Read every instruction file in full:
{instruction_list}

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

Be specific. Do not claim success merely because the gate is green. This report is
sent directly to Codex so it can identify your misunderstandings and correct the
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
        content = f"""# Codex ↔ Qwen3 extra instructions

- Generated at: {datetime.now(timezone.utc).isoformat()}
- Active task: `{task.id}`
- Codex decision: `{review['decision']}`

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
        result = run_command(command, self.repo, input_text, self.timeout, stream)
        (logs / f"{name}.stdout.log").write_text(result.stdout, encoding="utf-8")
        (logs / f"{name}.stderr.log").write_text(result.stderr, encoding="utf-8")
        (evidence / f"{name}.json").write_text(json.dumps({
            "command": list(result.command), "exit_code": result.exit_code,
            "timed_out": result.timed_out,
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
        self.lock_path.parent.mkdir(parents=True, exist_ok=True)
        self.lock_path.write_text(json.dumps({
            "schema_version": 1,
            "task_id": task.id,
            "base_commit": git_head(self.repo),
            "run_id": self.run_id,
            "allowed_paths": list(task.allowed_paths),
        }, indent=2), encoding="utf-8")

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

    def _write_memory(self) -> None:
        accepted = [item for item in self.progress["tasks"] if item["status"] == "ACCEPTED"]
        pending = [item for item in self.progress["tasks"] if item["status"] != "ACCEPTED"]
        last = accepted[-1]["id"] if accepted else "None"
        lines = [
            "# Agent memory", "", "## Current state", "",
            f"- Last accepted task: {last}.",
            f"- Active task: {self.progress.get('active_task') or 'None'}.",
            f"- Accepted: {', '.join(item['id'] for item in accepted) or 'none'}.",
            f"- Remaining: {', '.join(item['id'] for item in pending) or 'none'}.",
            "- Exact plan: `.opencode/task-plan.json`.", "",
            "## Fixed decisions", "",
            "- Non-web application until an explicit later task changes scope.",
            "- PostgreSQL only in Docker; Flyway owns application schema.",
            "- Spring AI abstractions; no handwritten Ollama HTTP client.",
            "- Codex plans/reviews read-only; OpenCode edits; Python validates and commits.",
            "- CodeGraph is optional impact analysis, not a success gate.",
            "- Runtime evidence stays under `runtime/runs/`; no automatic push.", "",
            "## Task commits", "",
        ]
        for item in self.progress["tasks"]:
            lines.append(f"- {item['id']}: {item['status']} — accepted at {item.get('accepted_at') or 'not accepted'}")
        self.memory_path.write_text("\n".join(lines) + "\n", encoding="utf-8")

    def _commit_if_needed(self, message: str) -> str | None:
        changed = git_changed_paths(self.repo)
        if not changed:
            return git_head(self.repo)
        add = run_command(("git", "add", "-A"), self.repo)
        if add.exit_code != 0:
            return None
        check = run_command(("git", "diff", "--cached", "--check"), self.repo)
        if check.exit_code != 0:
            return None
        commit = run_command(("git", "commit", "-m", message), self.repo, timeout_seconds=self.timeout, stream=True)
        return git_head(self.repo) if commit.exit_code == 0 else None

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
            "changed_paths": git_changed_paths(self.repo),
        }
        if extra:
            state.update(extra)
        (self.run_dir / "state.json").write_text(json.dumps(state, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
        print(f"\n[r4r] {status} (exit {exit_code})", flush=True)
        return exit_code
