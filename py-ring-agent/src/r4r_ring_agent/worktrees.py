from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path
import subprocess
from typing import Iterable


@dataclass(frozen=True)
class WorktreePaths:
    ring: Path
    pc: Path
    lp: Path

    def worker(self, destination: str) -> Path:
        key = destination.upper()
        if key == "PC":
            return self.pc
        if key == "LP":
            return self.lp
        raise ValueError("destination must be PC or LP")


def _git(path: Path, *args: str, check: bool = False) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        ["git", "-C", str(path), *args],
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        check=check,
    )


def is_git_worktree(path: Path) -> bool:
    resolved = path.expanduser().resolve()
    if not resolved.is_dir():
        return False
    result = _git(resolved, "rev-parse", "--show-toplevel")
    if result.returncode != 0:
        return False
    try:
        return Path(result.stdout.strip()).resolve() == resolved
    except OSError:
        return False


def require_git_worktree(path: Path, label: str) -> Path:
    resolved = path.expanduser().resolve()
    if not resolved.is_dir():
        raise RuntimeError(f"{label} worktree does not exist: {resolved}")
    result = _git(resolved, "rev-parse", "--show-toplevel")
    if result.returncode != 0:
        raise RuntimeError(f"{label} is not a Git worktree: {resolved}\n{result.stdout}")
    top = Path(result.stdout.strip()).resolve()
    if top != resolved:
        raise RuntimeError(f"{label} path is not the worktree root: {resolved}; Git root={top}")
    return resolved


def current_branch(path: Path) -> str:
    result = _git(path, "branch", "--show-current")
    return result.stdout.strip() if result.returncode == 0 else ""


def find_repository_anchor(candidates: Iterable[Path]) -> Path:
    """Return the first currently usable worktree of the shared repository."""
    checked: list[str] = []
    for candidate in candidates:
        resolved = candidate.expanduser().resolve()
        checked.append(str(resolved))
        if is_git_worktree(resolved):
            return resolved
    raise RuntimeError(
        "no usable Git worktree was found; checked:\n- " + "\n- ".join(checked)
    )


def registered_worktrees(repository_anchor: Path) -> tuple[Path, ...]:
    anchor = require_git_worktree(repository_anchor, "REPOSITORY_ANCHOR")
    result = _git(anchor, "worktree", "list", "--porcelain")
    if result.returncode != 0:
        raise RuntimeError(f"git worktree list failed:\n{result.stdout}")

    paths: list[Path] = []
    for line in result.stdout.splitlines():
        if line.startswith("worktree "):
            paths.append(Path(line.removeprefix("worktree ")).expanduser().resolve())
    return tuple(paths)


def repair_registered_worktrees(repository_anchor: Path) -> tuple[Path, ...]:
    """Repair stale .git links after a primary worktree directory was renamed.

    The primary checkout owns the common .git directory. Moving that checkout by
    filesystem rename makes every linked worktree point at the old location.
    `git worktree repair` must therefore run from the new primary path and receive
    the still-existing linked worktree paths.
    """
    anchor = require_git_worktree(repository_anchor, "REPOSITORY_ANCHOR")
    paths = registered_worktrees(anchor)
    existing = tuple(path for path in paths if path.is_dir())
    if not existing:
        return paths

    result = _git(anchor, "worktree", "repair", *(str(path) for path in existing))
    if result.returncode != 0:
        raise RuntimeError(f"git worktree repair failed:\n{result.stdout}")

    broken = [path for path in existing if not is_git_worktree(path)]
    if broken:
        joined = "\n- ".join(str(path) for path in broken)
        raise RuntimeError(f"worktree repair left broken paths:\n- {joined}")
    return paths


def _is_primary_worktree(path: Path) -> bool:
    return (path / ".git").is_dir()


def move_or_create_worker(
    *,
    repository_anchor: Path,
    source: Path,
    destination: Path,
    branch: str,
) -> str:
    """Move the worker to its short path, or create it if absent.

    Linked worktrees are moved with `git worktree move`. A primary checkout is
    renamed on disk and immediately followed by `git worktree repair`, because
    all linked worktree .git files refer to the primary checkout's common Git
    directory.
    """
    anchor = require_git_worktree(repository_anchor, "REPOSITORY_ANCHOR")
    source = source.expanduser().resolve()
    destination = destination.expanduser().resolve()

    if destination.exists():
        if source.exists() and source != destination:
            raise RuntimeError(
                f"both old and new worker paths exist; resolve manually: {source} and {destination}"
            )
        require_git_worktree(destination, destination.name)
        actual_branch = current_branch(destination)
        if actual_branch and actual_branch != branch:
            raise RuntimeError(
                f"unexpected branch in {destination}: {actual_branch}; expected {branch}"
            )
        return f"already exists: {destination} ({actual_branch or 'detached'})"

    destination.parent.mkdir(parents=True, exist_ok=True)

    if source.exists():
        require_git_worktree(source, source.name)

        if _is_primary_worktree(source):
            source.rename(destination)
            repair_registered_worktrees(destination)
        else:
            result = _git(anchor, "worktree", "move", str(source), str(destination))
            if result.returncode != 0:
                raise RuntimeError(f"git worktree move failed:\n{result.stdout}")

        require_git_worktree(destination, destination.name)
        actual_branch = current_branch(destination)
        if actual_branch and actual_branch != branch:
            raise RuntimeError(
                f"unexpected branch in {destination}: {actual_branch}; expected {branch}"
            )
        return f"moved: {source} -> {destination} ({actual_branch or 'detached'})"

    branch_check = _git(anchor, "show-ref", "--verify", f"refs/heads/{branch}")
    if branch_check.returncode != 0:
        raise RuntimeError(f"branch does not exist in repository: {branch}")
    result = _git(anchor, "worktree", "add", str(destination), branch)
    if result.returncode != 0:
        raise RuntimeError(f"git worktree add failed:\n{result.stdout}")
    require_git_worktree(destination, destination.name)
    return f"created: {destination} ({current_branch(destination) or 'detached'})"
