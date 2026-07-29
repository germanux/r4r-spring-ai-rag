from __future__ import annotations

from dataclasses import asdict, dataclass
from datetime import datetime, timezone
from fnmatch import fnmatch
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


def path_is_allowed(path: str, allowed_patterns: Sequence[str]) -> bool:
    return any(fnmatch(path, pattern) for pattern in allowed_patterns)


def codex_exec_command(binary: str, schema: Path, output: Path, model: str | None = None) -> tuple[str, ...]:
    command = [
        binary, "exec", "--sandbox", "read-only", "--ephemeral",
        "--output-schema", str(schema), "-o", str(output),
    ]
    if model:
        command.extend(("--model", model))
    command.append("-")
    return tuple(command)


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
        self.verified_green: set[str] = set()

    def execute(self) -> int:
        self._require_binary(self.opencode_bin)
        self._require_binary(self.codex_bin)
        dirty = git_changed_paths(self.repo)
        if dirty and not self.lock_path.exists():
            if not self._can_bootstrap(dirty):
                return self._finish("DIRTY_WORKTREE_UNOWNED", 64, {"changed_paths": dirty})
            result = self._bootstrap()
            if result != 0:
                return result
        elif dirty and self.lock_path.exists():
            self._validate_resume_lock(dirty)

        completed = 0
        while True:
            task = self._select_task()
            if task is None:
                final = self._run_logged("final-gate", self.plan.final_gate, self.run_dir, stream=True)
                if final.exit_code != 0:
                    return self._finish("FINAL_GATE_FAILED", final.exit_code)
                self._write_progress(None)
                self._write_memory()
                if self.auto_commit:
                    self._commit_if_needed("chore: record completed R4R task plan")
                notify = self.repo / "scripts" / "notify-success.sh"
                if notify.exists() and os.access(notify, os.X_OK):
                    self._run_logged("notify-success", (str(notify),), self.run_dir, stream=True)
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
        gate = self._run_logged("task-gate", task.gate, task_dir, stream=True)
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
        if lock.get("base_commit") != git_head(self.repo):
            raise RuntimeError("Active-task lock does not match current Git HEAD")
        disallowed = [path for path in dirty if not path_is_allowed(path, task.allowed_paths)]
        if disallowed:
            raise RuntimeError(f"Dirty resume contains out-of-scope paths: {disallowed}")

    def _select_task(self) -> Task | None:
        accepted_tasks = [
            task for task in self.plan.tasks
            if task_progress(self.progress, task.id)["status"] == "ACCEPTED"
        ]
        if accepted_tasks:
            latest = accepted_tasks[-1]
            if latest.id not in self.verified_green:
                gate = run_command(latest.gate, self.repo, timeout_seconds=self.timeout)
                if gate.exit_code != 0:
                    item = task_progress(self.progress, latest.id)
                    item["status"] = "REGRESSION"
                    item["accepted_at"] = None
                    return latest
                self.verified_green.add(latest.id)
        for task in self.plan.tasks:
            if task_progress(self.progress, task.id)["status"] != "ACCEPTED":
                return task
        return None

    def _execute_task(self, task: Task) -> int:
        task_root = self.run_dir / task.id
        task_root.mkdir(parents=True, exist_ok=True)
        self._write_lock(task)
        initial_gate = self._run_logged("initial-gate", task.gate, task_root, stream=True)
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
            current_gate = initial_gate if attempt == 1 else self._run_logged("pre-edit-gate", task.gate, attempt_dir)
            if current_gate.exit_code != 0 or next_action:
                prompt = self._opencode_prompt(task, current_gate, next_action)
                before_head = git_head(self.repo)
                edit = self._run_logged(
                    "opencode",
                    (self.opencode_bin, "run", "--dir", str(self.repo), "--agent", self.opencode_agent,
                     "--format", "json", "--auto", prompt),
                    attempt_dir, stream=True,
                )
                if edit.exit_code != 0:
                    return self._finish("OPENCODE_FAILED", edit.exit_code, {"task": task.id, "attempt": attempt})
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

            gate = self._run_logged("task-gate", task.gate, attempt_dir, stream=True)
            review = self._codex_review(task, gate, attempt_dir)
            if review["decision"] == "ACCEPT":
                if gate.exit_code != 0:
                    next_action = "The task gate is still red. Fix the first current gate failure before requesting ACCEPT."
                    continue
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

    def _structured_prompt(self, stage: str, task: Task, gate: CommandResult, evidence_dir: Path) -> str:
        contract = (self.repo / f"py-codex-agent/prompts/{stage}.md").read_text(encoding="utf-8")
        task_text = (self.repo / task.command).read_text(encoding="utf-8")
        memory = self.memory_path.read_text(encoding="utf-8")
        tail_stdout = gate.stdout[-12000:]
        tail_stderr = gate.stderr[-12000:]
        return (
            contract + "\n\nSELECTED TASK\n" + task_text +
            "\n\nCURRENT MEMORY\n" + memory +
            f"\n\nTASK ID\n{task.id}\nEVIDENCE DIRECTORY\n{evidence_dir.relative_to(self.repo)}" +
            f"\n\nCURRENT GATE EXIT\n{gate.exit_code}\nGATE STDOUT TAIL\n{tail_stdout}" +
            f"\nGATE STDERR TAIL\n{tail_stderr}\n"
        )

    def _opencode_prompt(self, task: Task, gate: CommandResult, next_action: str | None) -> str:
        action = next_action or "Implement the selected task completely."
        return f"""Read AGENTS.md, .opencode/commands/task.md, .opencode/memory.md and {task.command}.
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

    def _write_patch(self, attempt_dir: Path, changed: Sequence[str]) -> None:
        evidence = attempt_dir / "evidence"
        tracked = run_command(("git", "diff", "--binary", "--", *changed), self.repo) if changed else None
        if tracked:
            (evidence / "changes.patch").write_text(tracked.stdout, encoding="utf-8")

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
