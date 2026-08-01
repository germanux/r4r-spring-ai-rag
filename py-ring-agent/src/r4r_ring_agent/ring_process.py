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


def _is_alive(pid: int) -> bool:
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


def _signal_tree(root_pid: int, sig: signal.Signals) -> None:
    pids = _descendants(root_pid) | {root_pid}
    pgids: set[int] = set()
    for pid in pids:
        try:
            pgids.add(os.getpgid(pid))
        except ProcessLookupError:
            continue
    for pgid in sorted(pgids, reverse=True):
        try:
            os.killpg(pgid, sig)
        except ProcessLookupError:
            pass


def terminate_process_tree(process: subprocess.Popen[bytes]) -> None:
    """Stop the controller plus detached OpenCode descendants, then let pipes drain."""
    if process.poll() is not None:
        return
    for sig, grace in (
        (signal.SIGINT, SIGINT_GRACE_SECONDS),
        (signal.SIGTERM, SIGTERM_GRACE_SECONDS),
    ):
        _signal_tree(process.pid, sig)
        deadline = time.monotonic() + grace
        while time.monotonic() < deadline:
            if process.poll() is not None:
                return
            time.sleep(0.1)
    _signal_tree(process.pid, signal.SIGKILL)
    try:
        process.wait(timeout=5)
    except subprocess.TimeoutExpired:
        pass


def run_streamed(
    command: Sequence[str],
    cwd: Path,
    log_path: Path,
    *,
    timeout_seconds: int | None = None,
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
    with log_path.open("ab", buffering=0) as log_handle:
        last_fsync = time.monotonic()

        def copy_output() -> None:
            nonlocal last_fsync
            try:
                while True:
                    chunk = os.read(process.stdout.fileno(), 65536)
                    if not chunk:
                        break
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
                time.sleep(POLL_INTERVAL_SECONDS)
        except KeyboardInterrupt:
            stop_reason = "keyboard_interrupt"
            terminate_process_tree(process)

        reader.join(timeout=30)
        os.fsync(log_handle.fileno())

    if reader_error:
        raise RuntimeError("console streaming failed") from reader_error[0]
    finished = time.monotonic()
    if stop_reason == "timeout":
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
