from __future__ import annotations

from contextlib import contextmanager
from dataclasses import dataclass
import fnmatch
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
from typing import Iterable, Iterator, Sequence


@dataclass(frozen=True)
class CandidateDiff:
    paths: tuple[str, ...]
    additions: int
    deletions: int
    binary_paths: tuple[str, ...]
    violations: tuple[str, ...]

    @property
    def changed_lines(self) -> int:
        return self.additions + self.deletions

    @property
    def valid(self) -> bool:
        return not self.violations


@dataclass(frozen=True)
class CheckResult:
    command: str
    exit_code: int
    output: str

    @property
    def passed(self) -> bool:
        return self.exit_code == 0


def _run(
    command: Sequence[str],
    cwd: Path,
    *,
    check: bool = False,
    env: dict[str, str] | None = None,
) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        list(command),
        cwd=cwd,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        check=check,
        env=env,
    )


def git_output(repo: Path, *args: str) -> str:
    return _run(("git", *args), repo).stdout


def dirty_paths(repo: Path) -> tuple[str, ...]:
    output = git_output(repo, "status", "--porcelain=v1", "--untracked-files=all")
    paths: list[str] = []
    for line in output.splitlines():
        if len(line) < 4:
            continue
        path = line[3:]
        if " -> " in path:
            path = path.split(" -> ", 1)[1]
        paths.append(path.strip('"'))
    return tuple(dict.fromkeys(paths))


def path_allowed(path: str, allowed_globs: Sequence[str]) -> bool:
    normalized = path.replace(os.sep, "/")
    return any(fnmatch.fnmatchcase(normalized, pattern) for pattern in allowed_globs)


def dirty_allowed_paths(repo: Path, allowed_globs: Sequence[str]) -> tuple[str, ...]:
    return tuple(path for path in dirty_paths(repo) if path_allowed(path, allowed_globs))


def _intent_to_add_untracked(repo: Path) -> None:
    output = git_output(repo, "ls-files", "--others", "--exclude-standard")
    paths = [line for line in output.splitlines() if line]
    if paths:
        _run(("git", "add", "-N", "--", *paths), repo, check=True)


def inspect_candidate(
    repo: Path,
    *,
    allowed_globs: Sequence[str],
    max_files: int,
    max_changed_lines: int,
) -> CandidateDiff:
    _intent_to_add_untracked(repo)
    output = git_output(repo, "diff", "--numstat", "--no-renames")
    additions = 0
    deletions = 0
    paths: list[str] = []
    binary: list[str] = []
    violations: list[str] = []

    for line in output.splitlines():
        parts = line.split("\t", 2)
        if len(parts) != 3:
            continue
        added, deleted, path = parts
        paths.append(path)
        if added == "-" or deleted == "-":
            binary.append(path)
            continue
        additions += int(added)
        deletions += int(deleted)

    paths = list(dict.fromkeys(paths))
    outside = [path for path in paths if not path_allowed(path, allowed_globs)]
    if outside:
        violations.append("paths outside scope: " + ", ".join(outside))
    if len(paths) > max_files:
        violations.append(f"changed files {len(paths)} exceed limit {max_files}")
    if additions + deletions > max_changed_lines:
        violations.append(
            f"changed lines {additions + deletions} exceed limit {max_changed_lines}"
        )
    if binary:
        violations.append("binary changes are forbidden: " + ", ".join(binary))
    if not paths:
        violations.append("no candidate code change was produced")

    return CandidateDiff(
        paths=tuple(paths),
        additions=additions,
        deletions=deletions,
        binary_paths=tuple(binary),
        violations=tuple(violations),
    )


def candidate_patch(repo: Path) -> str:
    _intent_to_add_untracked(repo)
    return git_output(repo, "diff", "--binary", "--no-ext-diff", "--no-renames")


def reset_isolated_worktree(repo: Path) -> None:
    _run(("git", "restore", "--staged", "--worktree", "."), repo, check=True)
    _run(("git", "clean", "-fd"), repo, check=True)


@contextmanager
def detached_worktree(repo: Path, run_id: str) -> Iterator[Path]:
    root = Path(tempfile.gettempdir()) / "r4r-ring-agent-worktrees"
    root.mkdir(parents=True, exist_ok=True)
    worktree = root / run_id
    if worktree.exists():
        shutil.rmtree(worktree)
    _run(("git", "worktree", "add", "--detach", str(worktree), "HEAD"), repo, check=True)
    try:
        yield worktree
    finally:
        _run(("git", "worktree", "remove", "--force", str(worktree)), repo)
        if worktree.exists():
            shutil.rmtree(worktree, ignore_errors=True)


def run_candidate_checks(
    repo: Path,
    paths: Iterable[str],
    test_commands: Sequence[str],
) -> tuple[CheckResult, ...]:
    results: list[CheckResult] = []
    for path in paths:
        if path.endswith(".py"):
            command = f"python3 -m py_compile {path}"
        elif path.endswith(".sh"):
            command = f"bash -n {path}"
        else:
            continue
        completed = _run(("bash", "-lc", command), repo)
        results.append(CheckResult(command, completed.returncode, completed.stdout))
        if completed.returncode != 0:
            return tuple(results)

    for command in test_commands:
        completed = _run(("bash", "-lc", command), repo)
        results.append(CheckResult(command, completed.returncode, completed.stdout))
        if completed.returncode != 0:
            break
    return tuple(results)
