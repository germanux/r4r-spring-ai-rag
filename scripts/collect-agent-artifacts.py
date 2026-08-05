#!/usr/bin/env python3
"""Publish permanent Markdown/JSON evidence for one R4R agent.

Raw executions remain under ``runtime/``.  This collector copies their structured
artifacts into ``.opencode/current/{ring,PC,LP}``, preserving source-relative paths
so evidence from different runs and attempts cannot overwrite each other.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
from typing import Iterable


ARTIFACT_SUFFIXES = {".md", ".json"}
AGENTS = {"ring": "RING", "PC": "PC", "LP": "LP"}


class CollectionError(RuntimeError):
    pass


def run(
    command: list[str],
    *,
    cwd: Path,
    check: bool = True,
    env: dict[str, str] | None = None,
) -> subprocess.CompletedProcess[str]:
    result = subprocess.run(
        command,
        cwd=cwd,
        env=env,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        check=False,
    )
    if check and result.returncode != 0:
        detail = result.stderr.strip() or result.stdout.strip() or f"exit {result.returncode}"
        raise CollectionError(f"command failed: {' '.join(command)}\n{detail}")
    return result


def sha256_bytes(content: bytes) -> str:
    return hashlib.sha256(content).hexdigest()


def stable_read(path: Path) -> bytes:
    """Read a live artifact without accepting a write that overlaps the read."""
    for _ in range(3):
        before = path.stat()
        content = path.read_bytes()
        after = path.stat()
        if (before.st_size, before.st_mtime_ns) == (after.st_size, after.st_mtime_ns):
            return content
    raise CollectionError(f"artifact kept changing while being collected: {path}")


def atomic_write(path: Path, content: bytes) -> bool:
    if path.is_file() and not path.is_symlink() and path.read_bytes() == content:
        return False
    if path.exists() and (path.is_symlink() or not path.is_file()):
        raise CollectionError(f"refusing to replace non-regular artifact: {path}")
    path.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.NamedTemporaryFile("wb", dir=path.parent, delete=False) as handle:
        temporary = Path(handle.name)
        handle.write(content)
        handle.flush()
        os.fsync(handle.fileno())
    temporary.chmod(0o644)
    temporary.replace(path)
    return True


def artifact_files(root: Path) -> Iterable[Path]:
    if not root.is_dir():
        return ()
    return (
        path
        for path in sorted(root.rglob("*"))
        if path.is_file()
        and not path.is_symlink()
        and path.suffix.lower() in ARTIFACT_SUFFIXES
    )


def single_files(repo: Path, agent: str) -> tuple[Path, ...]:
    if agent == "PC":
        names = ("memory.backend.md", "progress.backend.json")
    elif agent == "LP":
        names = ("memory.frontend.md", "progress.frontend.json")
    else:
        names = ("memory.md", "progress.json")
    return tuple(repo / ".opencode" / name for name in names)


def source_artifacts(repo: Path, agent: str, worker_id: str) -> list[Path]:
    sources: set[Path] = set()
    sources.update(path for path in single_files(repo, agent) if path.is_file())
    for root in (
        repo / "runtime" / "runs" / worker_id,
        repo / "runtime" / "control" / worker_id,
    ):
        sources.update(artifact_files(root))
    if agent == "ring":
        sources.update(artifact_files(repo / ".ring-agent"))
    return sorted(sources)


def destination_for(repo: Path, target: Path, source: Path) -> Path:
    relative = source.relative_to(repo)
    if relative.parts[0] == ".opencode":
        relative = Path("opencode", *relative.parts[1:])
    elif relative.parts[0] == ".ring-agent":
        relative = Path("ring-agent", *relative.parts[1:])
    destination = target / relative
    try:
        destination.relative_to(target)
    except ValueError as exc:
        raise CollectionError(f"artifact escaped permanent target: {source}") from exc
    return destination


def producer_for(relative: str) -> str:
    lowered = relative.lower()
    if "codex" in lowered:
        return "codex"
    if any(value in lowered for value in ("qwen", "opencode", "local-understanding", "pre-edit", "worker-understanding")):
        return "local-llm"
    if "ring-agent/" in lowered:
        return "ring"
    return "controller"


def publish(repo: Path, agent: str, worker_id: str) -> tuple[int, Path]:
    target = repo / ".opencode" / "current" / agent
    target.mkdir(parents=True, exist_ok=True)
    copied = 0
    source_by_target: dict[str, str] = {}
    for source in source_artifacts(repo, agent, worker_id):
        destination = destination_for(repo, target, source)
        content = stable_read(source)
        if atomic_write(destination, content):
            copied += 1
        source_by_target[destination.relative_to(target).as_posix()] = source.relative_to(repo).as_posix()

    records: list[dict[str, object]] = []
    for path in artifact_files(target):
        if path.name == "manifest.json" and path.parent == target:
            continue
        content = path.read_bytes()
        relative = path.relative_to(target).as_posix()
        records.append(
            {
                "bytes": len(content),
                "producer": producer_for(relative),
                "sha256": sha256_bytes(content),
                "source": source_by_target.get(relative, f".opencode/current/{agent}/{relative}"),
                "target": relative,
            }
        )
    manifest = {
        "agent": agent,
        "artifacts": records,
        "schema_version": 1,
        "worker_id": worker_id,
    }
    payload = json.dumps(manifest, indent=2, ensure_ascii=False, sort_keys=True).encode("utf-8") + b"\n"
    if atomic_write(target / "manifest.json", payload):
        copied += 1
    return copied, target


def commit_target(repo: Path, target: Path, agent: str) -> bool:
    relative = target.relative_to(repo).as_posix()
    branch = run(["git", "branch", "--show-current"], cwd=repo).stdout.strip()
    if not branch:
        raise CollectionError(f"detached HEAD is not supported: {repo}")
    run(["git", "add", "-A", "--", relative], cwd=repo)
    pending = run(
        ["git", "diff", "--cached", "--quiet", "--", relative],
        cwd=repo,
        check=False,
    )
    if pending.returncode == 0:
        return False
    if pending.returncode != 1:
        raise CollectionError("cannot inspect staged permanent artifacts")
    run(["git", "diff", "--cached", "--check", "--", relative], cwd=repo)
    name = os.environ.get("R4R_ARTIFACT_GIT_NAME", "R4R Artifact Propagator")
    email = os.environ.get("R4R_ARTIFACT_GIT_EMAIL", "r4r-artifacts@localhost")
    commit_env = os.environ.copy()
    commit_env.update(
        {
            "GIT_AUTHOR_NAME": name,
            "GIT_AUTHOR_EMAIL": email,
            "GIT_COMMITTER_NAME": name,
            "GIT_COMMITTER_EMAIL": email,
        }
    )
    run(
        [
            "git",
            "-c",
            f"user.name={name}",
            "-c",
            f"user.email={email}",
            "commit",
            "--only",
            "-m",
            f"docs({agent}): preserve current agent evidence",
            "--",
            relative,
        ],
        cwd=repo,
        env=commit_env,
    )
    return True


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repo", type=Path, required=True)
    parser.add_argument("--agent", choices=tuple(AGENTS), required=True)
    parser.add_argument("--worker-id", choices=tuple(AGENTS.values()))
    parser.add_argument("--commit", action="store_true")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    repo = args.repo.expanduser().resolve()
    worker_id = args.worker_id or AGENTS[args.agent]
    try:
        inside = run(["git", "rev-parse", "--is-inside-work-tree"], cwd=repo).stdout.strip()
        if inside != "true":
            raise CollectionError(f"not a Git worktree: {repo}")
        copied, target = publish(repo, args.agent, worker_id)
        committed = commit_target(repo, target, args.agent) if args.commit else False
        print(
            f"[r4r-artifacts] agent={args.agent} copied={copied} "
            f"committed={'yes' if committed else 'no'} target={target}",
            flush=True,
        )
        return 0
    except (CollectionError, OSError) as exc:
        print(f"[r4r-artifacts] ERROR: {exc}", file=sys.stderr, flush=True)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
