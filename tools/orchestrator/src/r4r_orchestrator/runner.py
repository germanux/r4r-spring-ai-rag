from __future__ import annotations

from dataclasses import asdict, dataclass
from datetime import datetime, timezone
import json
import os
from pathlib import Path
import shlex
import subprocess
from typing import Sequence

from .contracts import Task, validate_decision


@dataclass(frozen=True)
class CommandResult:
    command: tuple[str, ...]
    exit_code: int
    stdout: str
    stderr: str


def run_command(command: Sequence[str], cwd: Path) -> CommandResult:
    if not command:
        raise ValueError("Command cannot be empty")
    completed = subprocess.run(
        list(command),
        cwd=cwd,
        text=True,
        capture_output=True,
        check=False,
    )
    return CommandResult(tuple(command), completed.returncode, completed.stdout, completed.stderr)


def git_changed_paths(repo: Path) -> tuple[str, ...]:
    result = run_command(("git", "status", "--short"), repo)
    if result.exit_code != 0:
        raise RuntimeError(result.stderr.strip() or "git status failed")
    paths: list[str] = []
    for line in result.stdout.splitlines():
        if not line.strip():
            continue
        value = line[3:]
        if " -> " in value:
            value = value.split(" -> ", 1)[1]
        paths.append(value)
    return tuple(paths)


def path_is_allowed(path: str, allowed_patterns: Sequence[str]) -> bool:
    from fnmatch import fnmatch

    return any(fnmatch(path, pattern) for pattern in allowed_patterns)


class CycleRunner:
    def __init__(self, repo: Path, task: Task):
        self.repo = repo.resolve()
        self.task = task
        stamp = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
        self.evidence_dir = self.repo / "runtime" / "evidence" / stamp
        self.evidence_dir.mkdir(parents=True, exist_ok=False)

    def execute(self, opencode_command: Sequence[str], codex_command: Sequence[str] | None) -> int:
        summary: dict[str, object] = {
            "task": asdict(self.task),
            "started_at": datetime.now(timezone.utc).isoformat(),
        }

        pre = run_command(self.task.pre_gate, self.repo)
        self._write_result("pre-gate", pre)
        summary["pre_gate_exit"] = pre.exit_code
        if pre.exit_code != 0:
            summary["status"] = "PRE_GATE_FAILED"
            self._write_summary(summary)
            return pre.exit_code

        before = set(git_changed_paths(self.repo))
        if before:
            summary["status"] = "DIRTY_WORKTREE"
            summary["changed_paths"] = sorted(before)
            self._write_summary(summary)
            return 64

        prompt = (self.repo / self.task.opencode_prompt).read_text(encoding="utf-8")
        edit_command = tuple(opencode_command) + (prompt,)
        edit = run_command(edit_command, self.repo)
        self._write_result("opencode", edit)
        summary["opencode_exit"] = edit.exit_code
        if edit.exit_code != 0:
            summary["status"] = "OPENCODE_FAILED"
            self._write_summary(summary)
            return edit.exit_code

        after = set(git_changed_paths(self.repo))
        new_or_changed = sorted(after)
        disallowed = [path for path in new_or_changed if not path_is_allowed(path, self.task.allowed_paths)]
        summary["changed_paths"] = new_or_changed
        summary["disallowed_paths"] = disallowed
        if disallowed:
            summary["status"] = "SCOPE_VIOLATION"
            self._write_summary(summary)
            return 65

        post = run_command(self.task.post_gate, self.repo)
        self._write_result("post-gate", post)
        summary["post_gate_exit"] = post.exit_code
        if post.exit_code != 0:
            summary["status"] = "POST_GATE_FAILED"
            self._write_summary(summary)
            return post.exit_code

        if self.task.review_required:
            if not codex_command:
                summary["status"] = "REVIEW_PENDING"
                self._write_summary(summary)
                return 0
            review_input = json.dumps(summary, indent=2, ensure_ascii=False)
            review = run_command(tuple(codex_command) + (review_input,), self.repo)
            self._write_result("codex-review", review)
            if review.exit_code != 0:
                summary["status"] = "REVIEW_COMMAND_FAILED"
                self._write_summary(summary)
                return review.exit_code
            try:
                decision = validate_decision(json.loads(review.stdout), self.task.id)
            except (json.JSONDecodeError, ValueError) as exception:
                summary["status"] = "INVALID_REVIEW"
                summary["review_error"] = str(exception)
                self._write_summary(summary)
                return 66
            summary["review"] = decision
            summary["status"] = decision["decision"]
        else:
            summary["status"] = "GATES_GREEN"

        self._write_summary(summary)
        return 0

    def _write_result(self, name: str, result: CommandResult) -> None:
        payload = {
            "command": list(result.command),
            "display": shlex.join(result.command),
            "exit_code": result.exit_code,
            "stdout": result.stdout,
            "stderr": result.stderr,
        }
        (self.evidence_dir / f"{name}.json").write_text(
            json.dumps(payload, indent=2, ensure_ascii=False), encoding="utf-8"
        )

    def _write_summary(self, summary: dict[str, object]) -> None:
        summary["finished_at"] = datetime.now(timezone.utc).isoformat()
        (self.evidence_dir / "summary.json").write_text(
            json.dumps(summary, indent=2, ensure_ascii=False), encoding="utf-8"
        )


def command_from_env(name: str) -> tuple[str, ...] | None:
    raw = os.environ.get(name)
    if not raw:
        return None
    parsed = json.loads(raw)
    if not isinstance(parsed, list) or not parsed or not all(isinstance(value, str) for value in parsed):
        raise ValueError(f"{name} must be a non-empty JSON array of strings")
    return tuple(parsed)
