from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path
import subprocess


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


def _common_git_dir(path: Path) -> Path:
    result = _git(path, "rev-parse", "--git-common-dir", check=True)
    value = Path(result.stdout.strip())
    if not value.is_absolute():
        value = path / value
    return value.resolve()


def _repair_after_move(destination: Path, common_git_dir: Path) -> None:
    subprocess.run(
        ["git", f"--git-dir={common_git_dir}", "worktree", "repair", str(destination)],
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        check=False,
    )


def move_or_create_worker(
    *,
    ring_repo: Path,
    source: Path,
    destination: Path,
    branch: str,
) -> str:
    """Move the current worker path to its short name, or create it if absent.

    Existing worktrees are preserved. Linked worktrees are moved through Git when
    possible. Independent clones/main worktrees are renamed on disk and repaired.
    """
    ring_repo = require_git_worktree(ring_repo, "RING")
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
        source_common = _common_git_dir(source)
        ring_common = _common_git_dir(ring_repo)
        linked = (source / ".git").is_file()

        if linked and source_common == ring_common:
            result = _git(ring_repo, "worktree", "move", str(source), str(destination))
            if result.returncode != 0:
                raise RuntimeError(f"git worktree move failed:\n{result.stdout}")
        else:
            source.rename(destination)
            _repair_after_move(destination, source_common)

        require_git_worktree(destination, destination.name)
        return f"moved: {source} -> {destination} ({current_branch(destination) or 'detached'})"

    branch_check = _git(ring_repo, "show-ref", "--verify", f"refs/heads/{branch}")
    if branch_check.returncode != 0:
        raise RuntimeError(f"branch does not exist in Ring repository: {branch}")
    result = _git(ring_repo, "worktree", "add", str(destination), branch)
    if result.returncode != 0:
        raise RuntimeError(f"git worktree add failed:\n{result.stdout}")
    require_git_worktree(destination, destination.name)
    return f"created: {destination} ({current_branch(destination) or 'detached'})"
