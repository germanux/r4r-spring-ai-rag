#!/usr/bin/env bash
set -Eeuo pipefail

# Safely synchronize the committed Ring revision into both worker branches.
#
# Safety order:
#   1. Reject PC/LP controllers accidentally running in the Ring worktree.
#   2. Stop managed PC and LP wrappers and wait for their complete process trees.
#   3. Require clean, correctly-bound worker worktrees.
#   4. Merge one pinned source commit into both worker branches.
#   5. Start fresh wrappers, each bound to its authoritative worker worktree.
#
# A stopped wrapper cannot consume a later JSONC "restart" request. Starting a new
# wrapper after the merge is therefore the race-free equivalent of restart.

DEVELOPMENT_ROOT="${R4R_DEVELOPMENT_ROOT:-$HOME/Desarrollo}"
RING_WORKTREE="${R4R_RING_WORKTREE:-$DEVELOPMENT_ROOT/r4r-ring-agent.git}"
PC_WORKTREE="${R4R_PC_WORKTREE:-$DEVELOPMENT_ROOT/r4r-pc-worker.git}"
LP_WORKTREE="${R4R_LP_WORKTREE:-$DEVELOPMENT_ROOT/r4r-lp-worker.git}"
PC_BRANCH="${R4R_PC_BRANCH:-agent/pc-qwen3-worker}"
LP_BRANCH="${R4R_LP_BRANCH:-agent/laptop-qwen3-worker}"
SOURCE_REF="${R4R_MERGE_SOURCE_REF:-}"
WAIT_SECONDS="${R4R_RESTART_WAIT_SECONDS:-180}"
DRY_RUN=false
MERGE_ONLY=false
PC_STOPPED_BY_SCRIPT=false
LP_STOPPED_BY_SCRIPT=false
PC_STARTED=false
LP_STARTED=false

usage() {
  cat <<'USAGE'
Usage: ./scripts/merge-worker-branches-and-restart.sh [options]

  --source REF      Ref whose current commit is merged into both workers.
                    Default: current Ring branch. The commit is pinned once.
  --ring PATH       Ring worktree.
  --pc PATH         PC worker worktree.
  --lp PATH         LP worker worktree.
  --wait SECONDS    Stop/start timeout. Default: 180.
  --merge-only      Merge without stopping or starting worker processes.
  --dry-run         Validate and print the planned actions without modifying state.
  -h, --help        Show this help.

Environment overrides: R4R_DEVELOPMENT_ROOT, R4R_RING_WORKTREE,
R4R_PC_WORKTREE, R4R_LP_WORKTREE, R4R_PC_BRANCH, R4R_LP_BRANCH,
R4R_MERGE_SOURCE_REF and R4R_RESTART_WAIT_SECONDS.
USAGE
}

log()  { printf '[r4r-sync] %s\n' "$*"; }
warn() { printf '[r4r-sync] WARNING: %s\n' "$*" >&2; }
die()  { printf '[r4r-sync] ERROR: %s\n' "$*" >&2; exit 2; }

while (($#)); do
  case "$1" in
    --source) (($# >= 2)) || die "--source requires a value"; SOURCE_REF="$2"; shift 2 ;;
    --ring)   (($# >= 2)) || die "--ring requires a value"; RING_WORKTREE="$2"; shift 2 ;;
    --pc)     (($# >= 2)) || die "--pc requires a value"; PC_WORKTREE="$2"; shift 2 ;;
    --lp)     (($# >= 2)) || die "--lp requires a value"; LP_WORKTREE="$2"; shift 2 ;;
    --wait)   (($# >= 2)) || die "--wait requires a value"; WAIT_SECONDS="$2"; shift 2 ;;
    --merge-only) MERGE_ONLY=true; shift ;;
    --dry-run) DRY_RUN=true; shift ;;
    -h|--help) usage; exit 0 ;;
    *) die "unknown argument: $1" ;;
  esac
done

[[ "$WAIT_SECONDS" =~ ^[1-9][0-9]*$ ]] || die "--wait must be a positive integer"
for command in git python3 flock nohup realpath; do
  command -v "$command" >/dev/null 2>&1 || die "required command unavailable: $command"
done

RING_WORKTREE="$(realpath -e "$RING_WORKTREE" 2>/dev/null)" || die "Ring worktree does not exist"
PC_WORKTREE="$(realpath -e "$PC_WORKTREE" 2>/dev/null)" || die "PC worktree does not exist"
LP_WORKTREE="$(realpath -e "$LP_WORKTREE" 2>/dev/null)" || die "LP worktree does not exist"
PY_RING_SRC="$RING_WORKTREE/py-ring-agent/src"
WRAPPER="$RING_WORKTREE/py-ring-agent/run-worker-streamed.py"
CONTROL_FILE="$RING_WORKTREE/runtime/the-ring-command.jsonc"

[[ -f "$PY_RING_SRC/r4r_ring_agent/operator_control.py" ]] \
  || die "operator-control module missing under $PY_RING_SRC"
[[ "$MERGE_ONLY" == true || -f "$WRAPPER" ]] \
  || die "worker wrapper missing: $WRAPPER"

require_worktree() {
  local path="$1" label="$2" expected="$3" top branch
  git -C "$path" rev-parse --is-inside-work-tree >/dev/null 2>&1 \
    || die "$label is not a Git worktree: $path"
  top="$(realpath -e "$(git -C "$path" rev-parse --show-toplevel)")"
  [[ "$top" == "$path" ]] || die "$label path is not its worktree root: $path; Git root=$top"
  branch="$(git -C "$path" branch --show-current)"
  [[ "$branch" == "$expected" ]] || die "$label uses ${branch:-DETACHED}; expected $expected"
}

common_dir() {
  local path="$1" value
  value="$(git -C "$path" rev-parse --git-common-dir)"
  [[ "$value" == /* ]] || value="$path/$value"
  realpath -e "$value"
}

ring_branch="$(git -C "$RING_WORKTREE" branch --show-current)"
[[ -n "$ring_branch" ]] || die "Ring worktree is detached"
[[ -n "$SOURCE_REF" ]] || SOURCE_REF="$ring_branch"
require_worktree "$PC_WORKTREE" PC "$PC_BRANCH"
require_worktree "$LP_WORKTREE" LP "$LP_BRANCH"
SOURCE_COMMIT="$(git -C "$RING_WORKTREE" rev-parse --verify "${SOURCE_REF}^{commit}" 2>/dev/null)" \
  || die "source ref is not a commit: $SOURCE_REF"
ring_common="$(common_dir "$RING_WORKTREE")"
[[ "$(common_dir "$PC_WORKTREE")" == "$ring_common" ]] || die "PC is from a different Git repository"
[[ "$(common_dir "$LP_WORKTREE")" == "$ring_common" ]] || die "LP is from a different Git repository"

mkdir -p "$RING_WORKTREE/runtime"
exec 9>"$RING_WORKTREE/runtime/.merge-workers-and-restart.lock"
flock -n 9 || die "another merge/restart operation is already running"

# Actions:
#   queue COMMAND TARGET REASON
#   snapshot WORKER
#   settle-stopped WORKER REASON
control_py() {
  PYTHONPATH="$PY_RING_SRC" python3 - "$RING_WORKTREE" "$@" <<'PY'
from __future__ import annotations

from datetime import datetime, timezone
import fcntl
import json
import os
from pathlib import Path
import sys

from r4r_ring_agent.operator_control import RingCommandFile

repo = Path(sys.argv[1])
action = sys.argv[2]


def unlock(lock) -> None:
    fcntl.flock(lock.fileno(), fcntl.LOCK_UN)
    lock.close()


if action == "queue":
    command, target, reason = sys.argv[3:6]
    control = RingCommandFile(repo, "RING")
    lock = control._lock()
    try:
        value = control._read_unlocked()
        request = value["request"]
        request["expected_targets"] = []
        request["executed_by"] = []
        request["created_at"] = ""
        value["next_state"] = command
        value["target"] = target
        value["reason"] = reason
        value["last_result"] = (
            "QUEUED BY merge-worker-branches-and-restart.sh: "
            f"{command} for {target}"
        )
        value["last_transition_at"] = datetime.now(timezone.utc).isoformat()
        value["revision"] = int(value.get("revision", 0)) + 1
        control._write_unlocked(value)
    finally:
        unlock(lock)
    raise SystemExit(0)

if action == "snapshot":
    worker = sys.argv[3].upper()
    control = RingCommandFile(repo, worker)
    lock = control._lock()
    try:
        value = control._read_unlocked()
    finally:
        unlock(lock)

    heartbeat_path = control.heartbeat_dir / f"{worker}.json"
    heartbeat_active = False
    pid_alive = False
    pid = 0
    age = 10**9
    command_line = ""
    try:
        heartbeat = json.loads(heartbeat_path.read_text(encoding="utf-8"))
        pid = int(heartbeat.get("pid", 0))
        age = datetime.now(timezone.utc).timestamp() - float(
            heartbeat.get("updated_at_epoch", 0)
        )
        if pid > 0:
            os.kill(pid, 0)
            pid_alive = True
            try:
                command_line = (
                    Path(f"/proc/{pid}/cmdline")
                    .read_bytes()
                    .replace(b"\0", b" ")
                    .decode("utf-8", errors="replace")
                    .strip()
                )
            except OSError:
                command_line = ""
        heartbeat_active = pid_alive and age <= 20
    except (OSError, ValueError, TypeError, json.JSONDecodeError):
        pass

    print(json.dumps({
        "active": heartbeat_active,
        "pid_alive": pid_alive,
        "pid": pid,
        "pid_command": command_line,
        "age": age,
        "state": str(value.get("state", {}).get(worker, "unknown")),
        "next_state": str(value.get("next_state", "")),
        "target": str(value.get("target", "")),
        "last_command": str(value.get("last_command", "")),
        "last_result": str(value.get("last_result", "")),
    }))
    raise SystemExit(0)

if action == "settle-stopped":
    worker, reason = sys.argv[3:5]
    worker = worker.upper()
    control = RingCommandFile(repo, worker)
    lock = control._lock()
    try:
        value = control._read_unlocked()
        pending = str(value.get("next_state", "")).strip().lower()
        target = str(value.get("target", "")).strip().upper()
        if pending == "stop" and target == worker:
            request = value["request"]
            value["next_state"] = ""
            value["reason"] = ""
            request["expected_targets"] = []
            request["executed_by"] = []
            request["created_at"] = ""
        value["state"][worker] = "stopped"
        value["last_command"] = "stop"
        value["last_result"] = reason
        value["last_transition_at"] = datetime.now(timezone.utc).isoformat()
        value["revision"] = int(value.get("revision", 0)) + 1
        control._write_unlocked(value)
    finally:
        unlock(lock)
    raise SystemExit(0)

raise SystemExit(f"unsupported action: {action}")
PY
}

# Scan /proc without matching the scanner itself. Modes:
#   worker WORKER WORKTREE  -> managed wrapper plus worker-bound controller processes
#   misbound RING_WORKTREE  -> PC/LP controller processes incorrectly bound to Ring
process_py() {
  python3 - "$@" <<'PY'
from __future__ import annotations

import json
import os
from pathlib import Path
import shlex
import sys

mode = sys.argv[1]
self_pid = os.getpid()
parent_pid = os.getppid()


def read_argv(pid: int) -> list[str]:
    try:
        raw = Path(f"/proc/{pid}/cmdline").read_bytes()
    except OSError:
        return []
    return [
        part.decode("utf-8", errors="replace")
        for part in raw.split(b"\0")
        if part
    ]


def read_cwd(pid: int) -> str:
    try:
        return str(Path(f"/proc/{pid}/cwd").resolve())
    except OSError:
        return ""


def under(cwd: str, root: str) -> bool:
    if not cwd:
        return False
    try:
        Path(cwd).relative_to(Path(root))
        return True
    except ValueError:
        return False


def has_pair(argv: list[str], option: str, values: set[str]) -> bool:
    normalized = {value.upper() for value in values}
    for index, arg in enumerate(argv):
        if arg == option and index + 1 < len(argv):
            if argv[index + 1].upper() in normalized:
                return True
        if arg.startswith(option + "="):
            if arg.split("=", 1)[1].upper() in normalized:
                return True
    return False


def is_wrapper(argv: list[str], worker: str) -> bool:
    for index, arg in enumerate(argv):
        if Path(arg).name == "run-worker-streamed.py":
            return index + 1 < len(argv) and argv[index + 1].upper() == worker
    return False


def is_controller(argv: list[str]) -> bool:
    names = [Path(arg).name for arg in argv]
    if "run-codex-agent.sh" in names:
        return True
    if any("r4r_codex_agent" in arg for arg in argv):
        return True
    if "opencode" in names and "run" in argv:
        return True
    if "codex" in names and "exec" in argv:
        return True
    return False


def argv_bound_to(argv: list[str], root: str) -> bool:
    root_path = str(Path(root).resolve())
    for arg in argv:
        if arg == root_path or arg.startswith(root_path + os.sep):
            return True
    return False


rows = []
for entry in Path("/proc").iterdir():
    if not entry.name.isdigit():
        continue
    pid = int(entry.name)
    if pid in {self_pid, parent_pid}:
        continue
    argv = read_argv(pid)
    if not argv:
        continue
    cwd = read_cwd(pid)

    if mode == "worker":
        worker = sys.argv[2].upper()
        worktree = str(Path(sys.argv[3]).resolve())
        wrapper = is_wrapper(argv, worker)
        controller = is_controller(argv)
        bound = under(cwd, worktree) or argv_bound_to(argv, worktree)
        relevant = wrapper or (controller and bound)
        kind = "wrapper" if wrapper else "controller"
    elif mode == "misbound":
        ring = str(Path(sys.argv[2]).resolve())
        controller = is_controller(argv)
        local_worker_identity = (
            has_pair(argv, "--destination", {"PC", "LP"})
            or has_pair(argv, "--agent", {"r4r-pc", "r4r-laptop"})
        )
        relevant = controller and local_worker_identity and (
            under(cwd, ring) or argv_bound_to(argv, ring)
        )
        kind = "misbound-controller"
    else:
        raise SystemExit(f"unsupported process scan mode: {mode}")

    if relevant:
        rows.append({
            "pid": pid,
            "kind": kind,
            "cwd": cwd,
            "command": shlex.join(argv),
        })

print(json.dumps(sorted(rows, key=lambda row: row["pid"])))
PY
}

json_field() {
  local field="$1"
  python3 -c 'import json,sys; print(json.load(sys.stdin)[sys.argv[1]])' "$field"
}

json_count() {
  python3 -c 'import json,sys; print(len(json.load(sys.stdin)))'
}

pretty_processes() {
  python3 -c '
import json, sys
for row in json.load(sys.stdin):
    print("pid={} kind={} cwd={}\n  {}".format(
        row["pid"], row.get("kind", "unknown"), row["cwd"], row["command"]
    ))
'
}

queue_command() {
  local command="$1" worker="$2" reason="$3"
  if "$DRY_RUN"; then
    log "DRY-RUN JSONC: next_state=$command target=$worker"
  else
    control_py queue "$command" "$worker" "$reason"
  fi
}

worker_processes() {
  local worker="$1" worktree="$2"
  process_py worker "$worker" "$worktree"
}

worker_process_count() {
  worker_processes "$1" "$2" | json_count
}

reject_misbound_workers() {
  local rows count
  rows="$(process_py misbound "$RING_WORKTREE")"
  count="$(json_count <<<"$rows")"
  if ((count > 0)); then
    pretty_processes <<<"$rows" >&2
    die "a PC/LP controller is running in the Ring worktree; stop it before synchronization"
  fi
}

wrapper_is_live() {
  local worker="$1" worktree="$2" rows wrappers
  rows="$(worker_processes "$worker" "$worktree")"
  wrappers="$(python3 -c 'import json,sys; print(sum(1 for row in json.load(sys.stdin) if row.get("kind") == "wrapper"))' <<<"$rows")"
  ((wrappers > 0))
}

wait_fully_stopped() {
  local worker="$1" worktree="$2" deadline=$((SECONDS + WAIT_SECONDS))
  local quiet_since=-1 snapshot state pending target rows count
  while ((SECONDS < deadline)); do
    snapshot="$(control_py snapshot "$worker")"
    state="$(json_field state <<<"$snapshot")"
    pending="$(json_field next_state <<<"$snapshot")"
    target="$(json_field target <<<"$snapshot")"
    rows="$(worker_processes "$worker" "$worktree")"
    count="$(json_count <<<"$rows")"

    if ((count == 0)); then
      if [[ "$state" == stopped && -z "$pending" ]]; then
        return 0
      fi
      if ((quiet_since < 0)); then
        quiet_since=$SECONDS
      elif ((SECONDS - quiet_since >= 3)); then
        warn "$worker process tree is gone but control state was not finalized; settling stale stop request"
        control_py settle-stopped "$worker" \
          "STOPPED: process tree exited before final acknowledgement"
        return 0
      fi
    else
      quiet_since=-1
    fi
    sleep 1
  done

  warn "$worker did not stop completely within ${WAIT_SECONDS}s"
  warn "control snapshot: $(control_py snapshot "$worker")"
  rows="$(worker_processes "$worker" "$worktree")"
  pretty_processes <<<"$rows" >&2 || true
  return 1
}

stop_worker_or_require_idle() {
  local worker="$1" worktree="$2" rows count
  rows="$(worker_processes "$worker" "$worktree")"
  count="$(json_count <<<"$rows")"

  if wrapper_is_live "$worker" "$worktree"; then
    log "$worker: stopping managed wrapper and complete controller tree"
    queue_command stop "$worker" "Stop before merging pinned commit $SOURCE_COMMIT"
    "$DRY_RUN" && return 0
    wait_fully_stopped "$worker" "$worktree" \
      || die "$worker could not be stopped safely"
    if [[ "$worker" == PC ]]; then
      PC_STOPPED_BY_SCRIPT=true
    else
      LP_STOPPED_BY_SCRIPT=true
    fi
    return 0
  fi

  # A controller without the managed wrapper has no safe JSONC lifecycle. Refuse to
  # kill an arbitrary interactive process automatically.
  if ((count > 0)); then
    pretty_processes <<<"$rows" >&2
    die "$worker has an unmanaged controller; stop it explicitly before synchronization"
  fi
  log "$worker: already idle"
}

require_quiescent() {
  local worker="$1" worktree="$2" rows count
  rows="$(worker_processes "$worker" "$worktree")"
  count="$(json_count <<<"$rows")"
  if ((count > 0)); then
    pretty_processes <<<"$rows" >&2
    die "$worker still has live processes; refusing to touch its Git worktree"
  fi
}

require_clean() {
  local worker="$1" path="$2" status
  status="$(git -C "$path" status --porcelain=v1 --untracked-files=normal)"
  if [[ -n "$status" ]]; then
    printf '%s\n' "$status" >&2
    die "$worker worktree is dirty; commit or preserve its changes before merging"
  fi
}

merge_worker() {
  local worker="$1" path="$2"
  log "$worker: merging pinned ${SOURCE_COMMIT:0:12} from $SOURCE_REF into $(git -C "$path" branch --show-current)"
  if "$DRY_RUN"; then
    git -C "$path" merge-base --is-ancestor "$SOURCE_COMMIT" HEAD \
      && log "$worker: already contains the pinned source commit" \
      || log "$worker: merge would advance or create a merge commit"
  else
    git -C "$path" merge --no-edit "$SOURCE_COMMIT"
  fi
}

launch_wrapper() {
  local worker="$1" worktree="$2" stamp log_path pid_file pid deadline
  local heartbeat_path heartbeat_pid heartbeat_age rows controllers wrappers

  require_quiescent "$worker" "$worktree"
  stamp="$(date -u +%Y%m%dT%H%M%SZ)"
  log_path="$RING_WORKTREE/runtime/ring-agent/bootstrap/${stamp}-${worker}.log"
  pid_file="$RING_WORKTREE/runtime/ring-agent/bootstrap/${worker}.pid"
  heartbeat_path="$RING_WORKTREE/runtime/the-ring-heartbeats/${worker}.json"
  mkdir -p "$(dirname "$log_path")"
  log "$worker: starting fresh wrapper after merge; log=$log_path"

  nohup env PYTHONUNBUFFERED=1 python3 "$WRAPPER" "$worker" >"$log_path" 2>&1 &
  pid=$!
  printf '%s\n' "$pid" >"$pid_file"
  deadline=$((SECONDS + WAIT_SECONDS))

  while ((SECONDS < deadline)); do
    read -r heartbeat_pid heartbeat_age < <(
      python3 - "$heartbeat_path" <<'PY'
from datetime import datetime, timezone
import json
from pathlib import Path
import sys

path = Path(sys.argv[1])
try:
    value = json.loads(path.read_text(encoding="utf-8"))
    pid = int(value.get("pid", 0))
    age = datetime.now(timezone.utc).timestamp() - float(value.get("updated_at_epoch", 0))
except (OSError, ValueError, TypeError, json.JSONDecodeError):
    pid, age = 0, 10**9
print(pid, age)
PY
    )
    rows="$(worker_processes "$worker" "$worktree")"
    controllers="$(python3 -c 'import json,sys; print(sum(1 for row in json.load(sys.stdin) if row.get("kind") == "controller"))' <<<"$rows")"
    wrappers="$(python3 -c 'import json,sys; print(sum(1 for row in json.load(sys.stdin) if row.get("kind") == "wrapper"))' <<<"$rows")"
    if [[ "$heartbeat_pid" == "$pid" ]] \
      && python3 -c 'import sys; raise SystemExit(0 if float(sys.argv[1]) <= 20 else 1)' "$heartbeat_age" \
      && ((controllers > 0 && wrappers > 0)); then
      reject_misbound_workers
      return 0
    fi
    if ! kill -0 "$pid" 2>/dev/null; then
      tail -n 120 "$log_path" >&2 || true
      return 1
    fi
    sleep 1
  done

  warn "$worker wrapper did not become healthy within ${WAIT_SECONDS}s"
  tail -n 120 "$log_path" >&2 || true
  return 1
}

best_effort_relaunch() {
  local worker="$1" worktree="$2"
  warn "$worker: attempting recovery launch after synchronization failure"
  launch_wrapper "$worker" "$worktree" || warn "$worker recovery launch failed"
}

on_exit() {
  local code=$?
  trap - EXIT
  if ((code != 0)) && ! "$DRY_RUN" && ! "$MERGE_ONLY"; then
    if "$PC_STOPPED_BY_SCRIPT" && ! "$PC_STARTED"; then
      best_effort_relaunch PC "$PC_WORKTREE"
    fi
    if "$LP_STOPPED_BY_SCRIPT" && ! "$LP_STARTED"; then
      best_effort_relaunch LP "$LP_WORKTREE"
    fi
  fi
  exit "$code"
}
trap on_exit EXIT

log "Ring:          $RING_WORKTREE [$ring_branch]"
log "Source ref:    $SOURCE_REF"
log "Pinned commit: $SOURCE_COMMIT"
log "PC:            $PC_WORKTREE [$PC_BRANCH]"
log "LP:            $LP_WORKTREE [$LP_BRANCH]"
log "Control:       $CONTROL_FILE"

[[ -z "$(git -C "$RING_WORKTREE" status --porcelain=v1 --untracked-files=normal)" ]] \
  || warn "Ring has uncommitted files; only pinned commit $SOURCE_COMMIT is merged"

reject_misbound_workers

if ! "$MERGE_ONLY"; then
  stop_worker_or_require_idle PC "$PC_WORKTREE"
  stop_worker_or_require_idle LP "$LP_WORKTREE"
fi
if ! "$DRY_RUN"; then
  require_quiescent PC "$PC_WORKTREE"
  require_quiescent LP "$LP_WORKTREE"
  reject_misbound_workers
fi

# Revalidate branch bindings after process shutdown so no concurrent controller can
# have switched or replaced a worker worktree between preflight and merge.
require_worktree "$PC_WORKTREE" PC "$PC_BRANCH"
require_worktree "$LP_WORKTREE" LP "$LP_BRANCH"
require_clean PC "$PC_WORKTREE"
require_clean LP "$LP_WORKTREE"

pc_before="$(git -C "$PC_WORKTREE" rev-parse HEAD)"
lp_before="$(git -C "$LP_WORKTREE" rev-parse HEAD)"

rollback_workers() {
  warn "merge failed; restoring both initially clean worker branches"
  git -C "$PC_WORKTREE" merge --abort >/dev/null 2>&1 || true
  git -C "$LP_WORKTREE" merge --abort >/dev/null 2>&1 || true
  git -C "$PC_WORKTREE" reset --hard "$pc_before" >/dev/null 2>&1 || true
  git -C "$LP_WORKTREE" reset --hard "$lp_before" >/dev/null 2>&1 || true
}

if ! merge_worker PC "$PC_WORKTREE"; then
  rollback_workers
  die "PC merge failed"
fi
if ! merge_worker LP "$LP_WORKTREE"; then
  rollback_workers
  die "LP merge failed"
fi

log "PC HEAD: ${pc_before:0:12} -> $(git -C "$PC_WORKTREE" rev-parse --short=12 HEAD)"
log "LP HEAD: ${lp_before:0:12} -> $(git -C "$LP_WORKTREE" rev-parse --short=12 HEAD)"

"$MERGE_ONLY" && { log "merge-only completed"; exit 0; }
"$DRY_RUN" && { log "DRY-RUN completed"; exit 0; }

launch_wrapper PC "$PC_WORKTREE" || die "PC wrapper could not be started"
PC_STARTED=true
launch_wrapper LP "$LP_WORKTREE" || die "LP wrapper could not be started"
LP_STARTED=true

reject_misbound_workers
log "completed: pinned source merged; PC and LP restarted on their authoritative worktrees"
