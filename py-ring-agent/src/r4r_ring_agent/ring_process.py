from __future__ import annotations

from dataclasses import dataclass
import os
from pathlib import Path
import signal
import subprocess
import sys
import threading
import time
from typing import Callable, Mapping, Sequence


# These defaults are deliberately simple and editable.
FSYNC_INTERVAL_SECONDS = 5.0
POLL_INTERVAL_SECONDS = 0.25
SIGINT_GRACE_SECONDS = 20.0
SIGTERM_GRACE_SECONDS = 10.0


@dataclass(frozen=True)
class StreamedResult:
    command: tuple[str, ...]
    exit_code: int
    log_path: Path
    started_at_monotonic: float
    finished_at_monotonic: float
    stop_reason: str = ""

    @property
    def duration_seconds(self) -> float:
        return self.finished_at_monotonic - self.started_at_monotonic


def _proc_state_and_group(pid: int) -> tuple[str, int] | None:
    try:
        stat = (Path("/proc") / str(pid) / "stat").read_text(encoding="utf-8")
        right = stat.rfind(")")
        fields = stat[right + 2 :].split()
        return fields[0], int(fields[2])
    except (OSError, ValueError, IndexError):
        return None


def _is_alive(pid: int) -> bool:
    state_group = _proc_state_and_group(pid)
    if state_group is not None:
        state, _group = state_group
        return state != "Z"
    try:
        os.kill(pid, 0)
    except ProcessLookupError:
        return False
    except PermissionError:
        return True
    return True


def _descendants(root_pid: int) -> set[int]:
    """Return the Linux process descendants visible in /proc."""
    children_by_parent: dict[int, list[int]] = {}
    proc = Path("/proc")
    if not proc.exists():
        return set()
    for entry in proc.iterdir():
        if not entry.name.isdigit():
            continue
        try:
            stat = (entry / "stat").read_text(encoding="utf-8")
            right = stat.rfind(")")
            fields = stat[right + 2 :].split()
            pid = int(entry.name)
            ppid = int(fields[1])
        except (OSError, ValueError, IndexError):
            continue
        children_by_parent.setdefault(ppid, []).append(pid)

    found: set[int] = set()
    stack = list(children_by_parent.get(root_pid, ()))
    while stack:
        pid = stack.pop()
        if pid in found:
            continue
        found.add(pid)
        stack.extend(children_by_parent.get(pid, ()))
    return found


def _process_groups(root_pid: int) -> set[int]:
    """Return every process group currently visible below ``root_pid``."""
    pids = _descendants(root_pid) | {root_pid}
    current_group = os.getpgrp()
    groups: set[int] = set()
    for pid in pids:
        try:
            pgid = os.getpgid(pid)
        except ProcessLookupError:
            continue
        if pgid > 1 and pgid != current_group:
            groups.add(pgid)
    return groups


def _process_group_alive(pgid: int) -> bool:
    proc = Path("/proc")
    if proc.exists():
        for entry in proc.iterdir():
            if not entry.name.isdigit():
                continue
            state_group = _proc_state_and_group(int(entry.name))
            if state_group is None:
                continue
            state, candidate_group = state_group
            if candidate_group == pgid and state != "Z":
                return True
        return False
    try:
        os.killpg(pgid, 0)
    except ProcessLookupError:
        return False
    except PermissionError:
        return True
    return True


def _signal_groups(pgids: set[int], sig: signal.Signals) -> None:
    current_group = os.getpgrp()
    for pgid in sorted(pgids, reverse=True):
        if pgid <= 1 or pgid == current_group:
            continue
        try:
            os.killpg(pgid, sig)
        except ProcessLookupError:
            pass


def terminate_process_tree(process: subprocess.Popen[bytes]) -> None:
    """Stop the controller and all detached process groups observed below it.

    OpenCode starts in its own session. If the Python parent exits first, waiting only
    for the root PID leaves OpenCode alive. Capture every descendant process group
    before signalling and continue until both the root and those groups are gone.
    """
    known_groups = _process_groups(process.pid)
    if process.poll() is not None and not any(
        _process_group_alive(pgid) for pgid in known_groups
    ):
        return

    for sig, grace in (
        (signal.SIGINT, SIGINT_GRACE_SECONDS),
        (signal.SIGTERM, SIGTERM_GRACE_SECONDS),
    ):
        if _is_alive(process.pid):
            known_groups.update(_process_groups(process.pid))
        _signal_groups(known_groups, sig)
        deadline = time.monotonic() + grace
        while time.monotonic() < deadline:
            if _is_alive(process.pid):
                known_groups.update(_process_groups(process.pid))
            known_groups = {
                pgid for pgid in known_groups if _process_group_alive(pgid)
            }
            if process.poll() is not None and not known_groups:
                return
            time.sleep(0.1)

    if _is_alive(process.pid):
        known_groups.update(_process_groups(process.pid))
    _signal_groups(known_groups, signal.SIGKILL)
    try:
        process.wait(timeout=5)
    except subprocess.TimeoutExpired:
        pass

    deadline = time.monotonic() + 5
    while time.monotonic() < deadline:
        known_groups = {
            pgid for pgid in known_groups if _process_group_alive(pgid)
        }
        if not known_groups:
            return
        _signal_groups(known_groups, signal.SIGKILL)
        time.sleep(0.1)


def run_streamed(
    command: Sequence[str],
    cwd: Path,
    log_path: Path,
    *,
    timeout_seconds: int | None = None,
    first_output_timeout_seconds: int | None = None,
    stop_poll: Callable[[], str] | None = None,
    env: Mapping[str, str] | None = None,
) -> StreamedResult:
    """Run a command and mirror the exact merged console byte stream to disk.

    stderr is redirected to stdout before the child starts. The same bytes are written
    to the terminal and to ``log_path`` while the process is still running. The file is
    periodically fsynced and always fsynced at shutdown.
    """
    if not command:
        raise ValueError("command cannot be empty")
    if first_output_timeout_seconds is not None and first_output_timeout_seconds < 1:
        raise ValueError("first_output_timeout_seconds must be positive")
    cwd = cwd.resolve()
    log_path = log_path.resolve()
    log_path.parent.mkdir(parents=True, exist_ok=True)
    log_path.touch(exist_ok=True)

    started = time.monotonic()
    process = subprocess.Popen(
        list(command),
        cwd=cwd,
        stdin=subprocess.DEVNULL,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=False,
        bufsize=0,
        start_new_session=True,
        env=dict(env) if env is not None else None,
    )
    assert process.stdout is not None

    reader_error: list[BaseException] = []
    output_seen = threading.Event()
    with log_path.open("ab", buffering=0) as log_handle:
        last_fsync = time.monotonic()

        def copy_output() -> None:
            nonlocal last_fsync
            try:
                while True:
                    chunk = os.read(process.stdout.fileno(), 65536)
                    if not chunk:
                        break
                    output_seen.set()
                    log_handle.write(chunk)
                    console = getattr(sys.stdout, "buffer", None)
                    if console is not None:
                        console.write(chunk)
                        console.flush()
                    else:
                        sys.stdout.write(chunk.decode("utf-8", errors="replace"))
                        sys.stdout.flush()
                    now = time.monotonic()
                    if now - last_fsync >= FSYNC_INTERVAL_SECONDS:
                        os.fsync(log_handle.fileno())
                        last_fsync = now
            except BaseException as exc:  # surfaced after process cleanup
                reader_error.append(exc)
            finally:
                process.stdout.close()

        reader = threading.Thread(target=copy_output, name="r4r-console-tee", daemon=True)
        reader.start()

        deadline = None if timeout_seconds is None else started + timeout_seconds
        stop_reason = ""
        try:
            while process.poll() is None:
                if stop_poll is not None:
                    requested = stop_poll().strip().lower()
                    if requested:
                        stop_reason = requested
                        terminate_process_tree(process)
                        break
                if deadline is not None and time.monotonic() >= deadline:
                    stop_reason = "timeout"
                    terminate_process_tree(process)
                    break
                if (
                    first_output_timeout_seconds is not None
                    and not output_seen.is_set()
                    and time.monotonic() - started >= first_output_timeout_seconds
                ):
                    stop_reason = "first_output_timeout"
                    message = (
                        "[r4r-process] child produced no output for "
                        f"{first_output_timeout_seconds}s; terminating it\n"
                    ).encode("utf-8")
                    log_handle.write(message)
                    console = getattr(sys.stdout, "buffer", None)
                    if console is not None:
                        console.write(message)
                        console.flush()
                    else:
                        sys.stdout.write(message.decode("utf-8"))
                        sys.stdout.flush()
                    terminate_process_tree(process)
                    break
                time.sleep(POLL_INTERVAL_SECONDS)
        except KeyboardInterrupt:
            stop_reason = "keyboard_interrupt"
            terminate_process_tree(process)

        reader.join(timeout=30)
        os.fsync(log_handle.fileno())

    if reader_error:
        raise RuntimeError("console streaming failed") from reader_error[0]
    finished = time.monotonic()
    if stop_reason in {"timeout", "first_output_timeout"}:
        exit_code = 124
    elif stop_reason:
        exit_code = 130
    else:
        exit_code = int(process.returncode or 0)
    return StreamedResult(
        command=tuple(command),
        exit_code=exit_code,
        log_path=log_path,
        started_at_monotonic=started,
        finished_at_monotonic=finished,
        stop_reason=stop_reason,
    )
