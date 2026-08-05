#!/usr/bin/env python3
"""Stateful, conflict-aware synchronization between Insync and a Git worktree.

The importer uses a three-state manifest:
  * last successfully observed source hash;
  * current source hash;
  * destination hash at the last successful import.

By default the historical one-way import behaviour is retained. With
``--bidirectional``, committed Git files are also exported back to Insync.
If both sides changed the same path since the last successful run, the run
stops and reports a conflict without modifying either side.
"""

from __future__ import annotations

import argparse
import datetime as dt
import fcntl
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import time
from typing import Any

VERSION = 2
DEFAULT_SOURCE = Path.home() / "Insync" / "riansares4r@gmail.com" / "Google Drive" / "Agentes R4R" / "r4r-ring-agent.git"
DEFAULT_DEST = Path.home() / "Desarrollo" / "r4r-google-drive.git"
DEFAULT_MANIFEST = Path.home() / "Desarrollo" / ".r4r-runtime" / "drive-import" / "state.json"
DEFAULT_CONFLICT_ROOT = Path.home() / "Desarrollo" / ".r4r-runtime" / "drive-import" / "conflicts"
DEFAULT_LOCK = Path.home() / "Desarrollo" / ".r4r-runtime" / "git.lock"
DEFAULT_BRANCH = "agent/r4r-google-drive"

EXCLUDED_DIRS = {
    ".git",
    "target",
    "node_modules",
    ".venv",
    "venv",
    "build",
    "dist",
    ".angular",
    "coverage",
    "__pycache__",
}
EXCLUDED_SUFFIXES = {".log", ".pid", ".lock", ".pyc", ".pyo"}


class ImportFailure(RuntimeError):
    pass


def log(message: str) -> None:
    print(f"[r4r-drive-import] {message}", flush=True)


def run(command: list[str], *, cwd: Path | None = None, check: bool = True) -> subprocess.CompletedProcess[str]:
    completed = subprocess.run(
        command,
        cwd=str(cwd) if cwd else None,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        check=False,
    )
    if check and completed.returncode != 0:
        detail = completed.stderr.strip() or completed.stdout.strip() or f"exit {completed.returncode}"
        raise ImportFailure(f"command failed: {' '.join(command)}\n{detail}")
    return completed


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for block in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def hash_or_none(path: Path) -> str | None:
    if not path.exists():
        return None
    if not path.is_file() or path.is_symlink():
        raise ImportFailure(f"unsupported destination path type: {path}")
    return sha256_file(path)


def secret_env_name(name: str) -> bool:
    if name == ".env":
        return True
    if name.startswith(".env.") and not name.endswith(".example"):
        return True
    return False


def excluded_relative(relative: Path) -> bool:
    if not relative.parts:
        return True
    # Raw runtime stays machine-local. The deterministic artifact collector
    # publishes the durable Markdown/JSON view under .opencode/current/.
    if relative.parts[0] == "runtime":
        return True
    if any(part in EXCLUDED_DIRS for part in relative.parts[:-1]):
        return True
    name = relative.name
    if name == ".git" or secret_env_name(name):
        return True
    if Path(name).suffix.lower() in EXCLUDED_SUFFIXES:
        return True
    return False


def git_path_ignored(destination: Path, relative: Path) -> bool:
    rel = relative.as_posix()
    tracked = run(["git", "ls-files", "--error-unmatch", "--", rel], cwd=destination, check=False)
    if tracked.returncode == 0:
        return False
    ignored = run(["git", "check-ignore", "-q", "--no-index", "--", rel], cwd=destination, check=False)
    return ignored.returncode == 0


def enumerate_source(source: Path, destination: Path) -> dict[str, str]:
    result: dict[str, str] = {}
    for root, dirs, files in os.walk(source, topdown=True, followlinks=False):
        root_path = Path(root)
        relative_root = root_path.relative_to(source)
        dirs[:] = [
            directory
            for directory in dirs
            if directory not in EXCLUDED_DIRS
            and not (root_path / directory).is_symlink()
        ]
        for filename in files:
            path = root_path / filename
            relative = relative_root / filename if relative_root.parts else Path(filename)
            if excluded_relative(relative):
                continue
            if path.is_symlink() or not path.is_file():
                raise ImportFailure(f"unsupported source path type: {path}")
            if git_path_ignored(destination, relative):
                continue
            result[relative.as_posix()] = sha256_file(path)
    return dict(sorted(result.items()))


def enumerate_destination(destination: Path) -> dict[str, str]:
    """Hash tracked files, excluding raw machine-local runtime state."""
    tracked = run(["git", "ls-files", "-z"], cwd=destination).stdout.split("\0")
    result: dict[str, str] = {}
    for value in tracked:
        if not value:
            continue
        relative = Path(value)
        if excluded_relative(relative):
            continue
        path = destination / relative
        if not path.exists():
            continue
        if path.is_symlink() or not path.is_file():
            raise ImportFailure(f"unsupported Git path type: {path}")
        result[relative.as_posix()] = sha256_file(path)
    return dict(sorted(result.items()))


def load_manifest(path: Path) -> dict[str, Any]:
    if not path.exists():
        return {"version": VERSION, "entries": {}}
    try:
        raw = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        raise ImportFailure(f"cannot read manifest {path}: {exc}") from exc
    if raw.get("version") not in {1, VERSION} or not isinstance(raw.get("entries"), dict):
        raise ImportFailure(f"unsupported or invalid manifest: {path}")
    # Version 1 already recorded both baselines, so it can be upgraded in place.
    raw["version"] = VERSION
    return raw


def save_manifest(path: Path, manifest: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    payload = json.dumps(manifest, indent=2, sort_keys=True, ensure_ascii=False) + "\n"
    with tempfile.NamedTemporaryFile("w", encoding="utf-8", dir=path.parent, delete=False) as handle:
        temporary = Path(handle.name)
        handle.write(payload)
        handle.flush()
        os.fsync(handle.fileno())
    temporary.replace(path)


def assert_clean_git(destination: Path, expected_branch: str) -> None:
    inside = run(["git", "rev-parse", "--is-inside-work-tree"], cwd=destination)
    if inside.stdout.strip() != "true":
        raise ImportFailure(f"not a Git worktree: {destination}")
    branch = run(["git", "branch", "--show-current"], cwd=destination).stdout.strip()
    if branch != expected_branch:
        raise ImportFailure(f"expected branch {expected_branch}, found {branch or '(detached)'}")
    status = run(["git", "status", "--porcelain=v1", "--untracked-files=all"], cwd=destination).stdout
    if status.strip():
        raise ImportFailure(
            "destination worktree is not clean; refusing to mix unrelated changes:\n" + status.rstrip()
        )
    for marker in ("MERGE_HEAD", "REBASE_HEAD", "CHERRY_PICK_HEAD", "REVERT_HEAD"):
        marker_path = run(["git", "rev-parse", "--git-path", marker], cwd=destination).stdout.strip()
        resolved = Path(marker_path)
        if not resolved.is_absolute():
            resolved = destination / resolved
        if resolved.exists():
            raise ImportFailure(f"Git operation in progress ({marker}) in {destination}")


def normalize_mode(path: Path, relative: str) -> None:
    mode = 0o755 if relative.startswith("scripts/") and relative.endswith(".sh") else 0o644
    path.chmod(mode)


def atomic_copy(source: Path, destination: Path, relative: str) -> None:
    destination.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.NamedTemporaryFile("wb", dir=destination.parent, delete=False) as handle:
        temporary = Path(handle.name)
        with source.open("rb") as origin:
            shutil.copyfileobj(origin, handle)
        handle.flush()
        os.fsync(handle.fileno())
    normalize_mode(temporary, relative)
    temporary.replace(destination)


def notify_conflict(report: Path, count: int) -> None:
    title = "R4R: conflicto en sincronización de Google Drive"
    body = f"{count} rutas cambiaron tanto en Insync como en Git. Informe: {report}"
    if shutil.which("notify-send"):
        subprocess.run(["notify-send", "--urgency=critical", title, body], check=False)
    print(f"\a{title}\n{body}", file=sys.stderr, flush=True)


def write_conflict_report(
    conflict_root: Path,
    source: Path,
    destination: Path,
    conflicts: list[dict[str, Any]],
) -> Path:
    stamp = dt.datetime.now(dt.timezone.utc).strftime("%Y%m%dT%H%M%SZ")
    root = conflict_root / stamp
    root.mkdir(parents=True, exist_ok=True)
    report = root / "report.txt"
    lines = [
        "R4R DRIVE SYNC CONFLICT",
        f"Generated UTC: {dt.datetime.now(dt.timezone.utc).isoformat()}",
        f"Source: {source}",
        f"Destination: {destination}",
        "",
    ]
    for item in conflicts:
        relative = item["path"]
        lines.extend(
            [
                f"Path: {relative}",
                f"  previous source: {item['previous_source']}",
                f"  current source:  {item['current_source']}",
                f"  previous dest:   {item['previous_destination']}",
                f"  current dest:    {item['current_destination']}",
                "",
            ]
        )
        safe_name = relative.replace("/", "__")
        src = source / relative
        dst = destination / relative
        if src.exists() and src.is_file():
            shutil.copy2(src, root / f"{safe_name}.insync")
        if dst.exists() and dst.is_file():
            shutil.copy2(dst, root / f"{safe_name}.git")
    report.write_text("\n".join(lines), encoding="utf-8")
    return report


def initialize_manifest(source: Path, destination: Path, manifest_path: Path) -> None:
    current_source = enumerate_source(source, destination)
    current_destination = enumerate_destination(destination)
    entries: dict[str, Any] = {}
    for relative in sorted(set(current_source) | set(current_destination)):
        entries[relative] = {
            "source_hash": current_source.get(relative),
            "destination_hash": current_destination.get(relative),
        }
    manifest = {
        "version": VERSION,
        "source": str(source),
        "destination": str(destination),
        "initialized_at": dt.datetime.now(dt.timezone.utc).isoformat(),
        "updated_at": dt.datetime.now(dt.timezone.utc).isoformat(),
        "entries": entries,
    }
    save_manifest(manifest_path, manifest)
    log(f"baseline initialized with {len(entries)} source files; no files copied")
    log(f"manifest: {manifest_path}")


def apply_import(args: argparse.Namespace) -> int:
    source = args.source.resolve()
    destination = args.destination.resolve()
    manifest_path = args.manifest.expanduser().resolve()
    conflict_root = args.conflict_root.expanduser().resolve()

    if not source.is_dir():
        raise ImportFailure(f"source mirror does not exist: {source}")
    if not destination.is_dir():
        raise ImportFailure(f"destination worktree does not exist: {destination}")

    assert_clean_git(destination, args.branch)

    if args.initialize:
        initialize_manifest(source, destination, manifest_path)
        return 0

    manifest = load_manifest(manifest_path)
    if not manifest_path.exists():
        raise ImportFailure(
            f"manifest is missing: {manifest_path}\n"
            "Run once with --initialize after cleaning the destination branch."
        )

    entries: dict[str, Any] = manifest["entries"]
    current_source = enumerate_source(source, destination)
    current_destination = enumerate_destination(destination)
    paths = sorted(set(entries) | set(current_source) | set(current_destination))
    import_plan: list[dict[str, Any]] = []
    export_plan: list[dict[str, Any]] = []
    record_plan: list[dict[str, Any]] = []
    conflicts: list[dict[str, Any]] = []

    for relative in paths:
        entry = entries.get(relative)
        current_source_hash = current_source.get(relative)
        current_destination_hash = current_destination.get(relative)

        if entry is None:
            previous_source_hash = None
            previous_destination_hash = None
        else:
            previous_source_hash = entry.get("source_hash")
            previous_destination_hash = entry.get("destination_hash")

        source_changed = current_source_hash != previous_source_hash
        destination_changed = current_destination_hash != previous_destination_hash

        if not source_changed and not destination_changed:
            continue

        if source_changed and destination_changed:
            if current_source_hash == current_destination_hash:
                record_plan.append(
                    {"path": relative, "source_hash": current_source_hash,
                     "destination_hash": current_destination_hash}
                )
                continue
            conflicts.append(
                {
                    "path": relative,
                    "previous_source": previous_source_hash,
                    "current_source": current_source_hash,
                    "previous_destination": previous_destination_hash,
                    "current_destination": current_destination_hash,
                }
            )
            continue

        if source_changed:
            import_plan.append(
                {"path": relative,
                 "action": "delete" if current_source_hash is None else "copy",
                 "source_hash": current_source_hash,
                 "destination_hash": current_source_hash}
            )
        elif args.bidirectional:
            export_plan.append(
                {"path": relative,
                 "action": "delete" if current_destination_hash is None else "copy",
                 "source_hash": current_destination_hash,
                 "destination_hash": current_destination_hash}
            )

    if conflicts:
        report = write_conflict_report(conflict_root, source, destination, conflicts)
        notify_conflict(report, len(conflicts))
        return 4

    if args.dry_run:
        for item in import_plan:
            log(f"DRY-RUN import-{item['action']}: {item['path']}")
        for item in export_plan:
            log(f"DRY-RUN export-{item['action']}: {item['path']}")
        log(
            "dry run complete; "
            f"imports={len(import_plan)} exports={len(export_plan)}"
        )
        return 0

    changed_paths: list[str] = []
    copied = deleted = exported = export_deleted = recorded = 0
    for item in import_plan:
        relative = item["path"]
        action = item["action"]
        destination_path = destination / relative
        if action == "copy":
            atomic_copy(source / relative, destination_path, relative)
            copied += 1
            changed_paths.append(relative)
        elif action == "delete":
            if destination_path.exists():
                if destination_path.is_dir() or destination_path.is_symlink():
                    raise ImportFailure(f"refusing to delete non-regular path: {destination_path}")
                destination_path.unlink()
                deleted += 1
                changed_paths.append(relative)
        entries[relative] = {
            "source_hash": item["source_hash"],
            "destination_hash": item["destination_hash"],
        }

    for item in export_plan:
        relative = item["path"]
        action = item["action"]
        source_path = source / relative
        if action == "copy":
            atomic_copy(destination / relative, source_path, relative)
            exported += 1
        else:
            if source_path.exists():
                if source_path.is_dir() or source_path.is_symlink():
                    raise ImportFailure(f"refusing to delete non-regular path: {source_path}")
                source_path.unlink()
            export_deleted += 1
        entries[relative] = {
            "source_hash": item["source_hash"],
            "destination_hash": item["destination_hash"],
        }

    for item in record_plan:
        entries[item["path"]] = {
            "source_hash": item["source_hash"],
            "destination_hash": item["destination_hash"],
        }
        recorded += 1

    commit_created = False
    if changed_paths:
        run(["git", "add", "-A", "--", *changed_paths], cwd=destination)
        diff_check = run(["git", "diff", "--cached", "--check"], cwd=destination, check=False)
        if diff_check.returncode != 0:
            raise ImportFailure("git diff --cached --check failed:\n" + (diff_check.stdout + diff_check.stderr).strip())

        if args.commit:
            staged = run(["git", "diff", "--cached", "--quiet"], cwd=destination, check=False)
            if staged.returncode == 1:
                message = f"chore(drive): import {copied} updates and {deleted} deletions"
                run(["git", "commit", "-m", message], cwd=destination)
                commit_created = True
                log(f"commit created: {run(['git', 'rev-parse', '--short=12', 'HEAD'], cwd=destination).stdout.strip()}")
            elif staged.returncode != 0:
                raise ImportFailure("cannot inspect staged Git changes")

    if args.push:
        if not args.commit:
            raise ImportFailure("--push requires --commit")
        run(["git", "push", args.remote, f"HEAD:refs/heads/{args.branch}"], cwd=destination)
        log(f"pushed {args.branch} to {args.remote}")

    manifest["source"] = str(source)
    manifest["destination"] = str(destination)
    manifest["updated_at"] = dt.datetime.now(dt.timezone.utc).isoformat()
    manifest["entries"] = entries
    save_manifest(manifest_path, manifest)

    log(
        f"complete: imported={copied} import_deleted={deleted} "
        f"exported={exported} export_deleted={export_deleted} recorded={recorded} "
        f"commit={'yes' if commit_created else 'no'}"
    )
    return 0


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", type=Path, default=DEFAULT_SOURCE)
    parser.add_argument("--destination", type=Path, default=DEFAULT_DEST)
    parser.add_argument("--manifest", type=Path, default=DEFAULT_MANIFEST)
    parser.add_argument("--conflict-root", type=Path, default=DEFAULT_CONFLICT_ROOT)
    parser.add_argument("--lock", type=Path, default=DEFAULT_LOCK)
    parser.add_argument("--branch", default=DEFAULT_BRANCH)
    parser.add_argument("--remote", default="origin")
    parser.add_argument("--initialize", action="store_true", help="record a baseline without copying files")
    parser.add_argument("--dry-run", action="store_true")
    parser.add_argument(
        "--bidirectional", action="store_true",
        help="also export committed Git changes back to the Insync mirror",
    )
    parser.add_argument("--lock-timeout", type=float, default=55.0)
    parser.add_argument("--commit", action="store_true")
    parser.add_argument("--push", action="store_true")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    args.lock.parent.mkdir(parents=True, exist_ok=True)
    with args.lock.open("a+") as lock_handle:
        deadline = time.monotonic() + max(0.0, args.lock_timeout)
        while True:
            try:
                fcntl.flock(lock_handle.fileno(), fcntl.LOCK_EX | fcntl.LOCK_NB)
                break
            except BlockingIOError:
                if time.monotonic() >= deadline:
                    log("shared Git lock remained busy; skipping this scheduled pass")
                    return 0
                time.sleep(0.2)
        try:
            return apply_import(args)
        except ImportFailure as exc:
            print(f"[r4r-drive-import] ERROR: {exc}", file=sys.stderr, flush=True)
            return 2
        finally:
            fcntl.flock(lock_handle.fileno(), fcntl.LOCK_UN)


if __name__ == "__main__":
    raise SystemExit(main())
