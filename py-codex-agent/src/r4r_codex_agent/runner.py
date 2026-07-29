from __future__ import annotations

from dataclasses import asdict, dataclass
from datetime import datetime, timezone
from fnmatch import fnmatch
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


def run_command(command: Sequence[str], cwd: Path, input_text: str | None = None) -> CommandResult:
    if not command:
        raise ValueError("Command cannot be empty")
    completed = subprocess.run(
        list(command), cwd=cwd, input=input_text, text=True,
        capture_output=True, check=False,
    )
    return CommandResult(tuple(command), completed.returncode, completed.stdout, completed.stderr)


def git_changed_paths(repo: Path) -> tuple[str, ...]:
    result = run_command(("git", "status", "--short"), repo)
    if result.exit_code != 0:
        raise RuntimeError(result.stderr.strip() or "git status failed")
    paths: list[str] = []
    for line in result.stdout.splitlines():
        if line.strip():
            value = line[3:]
            paths.append(value.split(" -> ", 1)[-1])
    return tuple(paths)


def path_is_allowed(path: str, allowed_patterns: Sequence[str]) -> bool:
    return any(fnmatch(path, pattern) for pattern in allowed_patterns)


class CycleRunner:
    def __init__(self, repo: Path, task: Task):
        self.repo = repo.resolve()
        self.task = task
        stamp = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
        self.run_dir = self.repo / "runtime" / "runs" / stamp
        self.logs_dir = self.run_dir / "logs"
        self.evidence_dir = self.run_dir / "evidence"
        self.decisions_dir = self.run_dir / "decisions"
        for directory in (self.logs_dir, self.evidence_dir, self.decisions_dir):
            directory.mkdir(parents=True, exist_ok=False if directory == self.logs_dir else True)

    def execute(self, opencode_command: Sequence[str], codex_command: Sequence[str] | None) -> int:
        summary: dict[str, object] = {
            "task": asdict(self.task),
            "started_at": datetime.now(timezone.utc).isoformat(),
        }
        pre = run_command(self.task.pre_gate, self.repo)
        self._write_result("pre-gate", pre)
        if pre.exit_code != 0:
            return self._finish(summary, "PRE_GATE_FAILED", pre.exit_code)

        before = set(git_changed_paths(self.repo))
        if before:
            summary["changed_paths"] = sorted(before)
            return self._finish(summary, "DIRTY_WORKTREE", 64)

        prompt = (self.repo / self.task.opencode_prompt).read_text(encoding="utf-8")
        edit = run_command(tuple(opencode_command) + (prompt,), self.repo)
        self._write_result("opencode", edit)
        if edit.exit_code != 0:
            return self._finish(summary, "OPENCODE_FAILED", edit.exit_code)

        changed = sorted(set(git_changed_paths(self.repo)))
        disallowed = [path for path in changed if not path_is_allowed(path, self.task.allowed_paths)]
        summary["changed_paths"] = changed
        summary["disallowed_paths"] = disallowed
        if disallowed:
            return self._finish(summary, "SCOPE_VIOLATION", 65)

        patch = run_command(("git", "diff", "--binary", "--", *changed), self.repo) if changed else None
        if patch:
            (self.evidence_dir / "changes.patch").write_text(patch.stdout, encoding="utf-8")

        post = run_command(self.task.post_gate, self.repo)
        self._write_result("post-gate", post)
        if post.exit_code != 0:
            return self._finish(summary, "POST_GATE_FAILED", post.exit_code)

        if not self.task.review_required:
            return self._finish(summary, "GATES_GREEN", 0)
        if not codex_command:
            return self._finish(summary, "REVIEW_PENDING", 0)

        review_prompt = (self.repo / "py-codex-agent/prompts/review.md").read_text(encoding="utf-8")
        review_input = review_prompt + "\n\nEVIDENCE SUMMARY\n" + json.dumps(summary, indent=2, ensure_ascii=False)
        review = run_command(codex_command, self.repo, input_text=review_input)
        self._write_result("codex-review", review)
        if review.exit_code != 0:
            return self._finish(summary, "REVIEW_COMMAND_FAILED", review.exit_code)
        try:
            decision = validate_decision(json.loads(review.stdout), self.task.id)
        except (json.JSONDecodeError, ValueError) as exception:
            summary["review_error"] = str(exception)
            return self._finish(summary, "INVALID_REVIEW", 66)
        (self.decisions_dir / "codex-decision.json").write_text(
            json.dumps(decision, indent=2, ensure_ascii=False), encoding="utf-8"
        )
        summary["review"] = decision
        return self._finish(summary, str(decision["decision"]), 0)

    def _write_result(self, name: str, result: CommandResult) -> None:
        (self.logs_dir / f"{name}.stdout.log").write_text(result.stdout, encoding="utf-8")
        (self.logs_dir / f"{name}.stderr.log").write_text(result.stderr, encoding="utf-8")
        metadata = {
            "command": list(result.command),
            "display": shlex.join(result.command),
            "exit_code": result.exit_code,
        }
        (self.evidence_dir / f"{name}.json").write_text(
            json.dumps(metadata, indent=2, ensure_ascii=False), encoding="utf-8"
        )

    def _finish(self, summary: dict[str, object], status: str, exit_code: int) -> int:
        summary["status"] = status
        summary["finished_at"] = datetime.now(timezone.utc).isoformat()
        (self.run_dir / "state.json").write_text(
            json.dumps(summary, indent=2, ensure_ascii=False), encoding="utf-8"
        )
        return exit_code


def command_from_env(name: str) -> tuple[str, ...] | None:
    raw = os.environ.get(name, "").strip()
    if not raw:
        return None
    parsed = json.loads(raw)
    if not isinstance(parsed, list) or not parsed or not all(isinstance(value, str) for value in parsed):
        raise ValueError(f"{name} must be a non-empty JSON array of strings")
    return tuple(parsed)
