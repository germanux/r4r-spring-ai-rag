#!/usr/bin/env bash
set -Eeuo pipefail

# Ensure that the authoritative PC and LP wrappers are alive. This script is safe to
# execute repeatedly (for example from cron). It never starts a second wrapper for a
# worker that already owns a recent heartbeat.

DEVELOPMENT_ROOT="${R4R_DEVELOPMENT_ROOT:-$HOME/Desarrollo}"
RING_WORKTREE="${R4R_RING_WORKTREE:-$DEVELOPMENT_ROOT/r4r-ring-agent.git}"
PC_WORKTREE="${R4R_PC_WORKTREE:-$DEVELOPMENT_ROOT/r4r-pc-worker.git}"
LP_WORKTREE="${R4R_LP_WORKTREE:-$DEVELOPMENT_ROOT/r4r-lp-worker.git}"
RUNTIME_ENV_HELPER="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/r4r-runtime-env.sh"
WAIT_SECONDS="${R4R_WORKER_START_WAIT_SECONDS:-120}"
HEARTBEAT_MAX_AGE="${R4R_WORKER_HEARTBEAT_MAX_AGE:-25}"
WATCH=false
INTERVAL_SECONDS="${R4R_WORKER_GUARD_INTERVAL_SECONDS:-15}"
CHECK_ONLY=false
TARGETS=(PC LP)

usage() {
  cat <<'USAGE'
Usage: ./scripts/ensure-r4r-workers.sh [options]

  --once                 Check once (default).
  --watch                Keep checking and recover missing workers.
  --interval SECONDS     Watch interval (default: 15).
  --worker PC|LP         Check only one worker; repeatable.
  --check-only           Report state without starting anything.
  --ring PATH            Authoritative Ring worktree.
  --pc PATH              Authoritative PC worker worktree.
  --lp PATH              Authoritative LP worker worktree.
  --wait SECONDS         Start-health timeout (default: 120).
  -h, --help             Show this help.

A live wrapper must have a recent heartbeat whose PID still belongs to
run-worker-streamed.py for the same worker. Missing wrappers are started with their
official worker worktrees. Existing live but stale/mismatched processes are never
silently duplicated.
USAGE
}

log()  { printf '[r4r-guardian] %s\n' "$*"; }
warn() { printf '[r4r-guardian] WARNING: %s\n' "$*" >&2; }
die()  { printf '[r4r-guardian] ERROR: %s\n' "$*" >&2; exit 2; }

explicit_targets=()
while (($#)); do
  case "$1" in
    --once) WATCH=false; shift ;;
    --watch) WATCH=true; shift ;;
    --interval) (($# >= 2)) || die "--interval requires a value"; INTERVAL_SECONDS="$2"; shift 2 ;;
    --worker)
      (($# >= 2)) || die "--worker requires PC or LP"
      worker="${2^^}"
      [[ "$worker" == PC || "$worker" == LP ]] || die "--worker requires PC or LP"
      explicit_targets+=("$worker")
      shift 2
      ;;
    --check-only) CHECK_ONLY=true; shift ;;
    --ring) (($# >= 2)) || die "--ring requires a path"; RING_WORKTREE="$2"; shift 2 ;;
    --pc) (($# >= 2)) || die "--pc requires a path"; PC_WORKTREE="$2"; shift 2 ;;
    --lp) (($# >= 2)) || die "--lp requires a path"; LP_WORKTREE="$2"; shift 2 ;;
    --wait) (($# >= 2)) || die "--wait requires a value"; WAIT_SECONDS="$2"; shift 2 ;;
    -h|--help) usage; exit 0 ;;
    *) die "unknown argument: $1" ;;
  esac
done

((${#explicit_targets[@]} == 0)) || TARGETS=("${explicit_targets[@]}")
[[ "$WAIT_SECONDS" =~ ^[1-9][0-9]*$ ]] || die "--wait must be a positive integer"
[[ "$INTERVAL_SECONDS" =~ ^[1-9][0-9]*$ ]] || die "--interval must be a positive integer"
[[ "$HEARTBEAT_MAX_AGE" =~ ^[1-9][0-9]*$ ]] || die "R4R_WORKER_HEARTBEAT_MAX_AGE must be positive"
for command in git python3 flock realpath; do
  command -v "$command" >/dev/null 2>&1 || die "required command unavailable: $command"
done

RING_WORKTREE="$(realpath -e "$RING_WORKTREE" 2>/dev/null)" || die "Ring worktree does not exist"
PC_WORKTREE="$(realpath -e "$PC_WORKTREE" 2>/dev/null)" || die "PC worktree does not exist"
LP_WORKTREE="$(realpath -e "$LP_WORKTREE" 2>/dev/null)" || die "LP worktree does not exist"

if [[ -r "$RUNTIME_ENV_HELPER" ]]; then
  # shellcheck disable=SC1090
  source "$RUNTIME_ENV_HELPER"
  r4r_runtime_bootstrap "$RING_WORKTREE"
fi
WRAPPER="$RING_WORKTREE/py-ring-agent/run-worker-streamed.py"
[[ -f "$WRAPPER" ]] || die "worker wrapper missing: $WRAPPER"

mkdir -p "$RING_WORKTREE/runtime/ring-agent/guardian"
exec 9>"$RING_WORKTREE/runtime/.ensure-r4r-workers.lock"
flock -n 9 || { log "another guardian check is already running"; exit 0; }

worker_worktree() {
  case "$1" in
    PC) printf '%s\n' "$PC_WORKTREE" ;;
    LP) printf '%s\n' "$LP_WORKTREE" ;;
    *) return 2 ;;
  esac
}

require_worktree() {
  local path="$1" label="$2" expected="$3" top branch
  top="$(git -C "$path" rev-parse --show-toplevel 2>/dev/null)" || die "$label is not a Git worktree: $path"
  [[ "$(realpath -e "$top")" == "$path" ]] || die "$label path is not its Git root: $path"
  branch="$(git -C "$path" branch --show-current)"
  [[ "$branch" == "$expected" ]] || die "$label branch is ${branch:-DETACHED}; expected $expected"
}

require_worktree "$PC_WORKTREE" PC "${R4R_PC_BRANCH:-agent/pc-qwen3-worker}"
require_worktree "$LP_WORKTREE" LP "${R4R_LP_BRANCH:-agent/laptop-qwen3-worker}"

worker_state_json() {
  local worker="$1" heartbeat="$RING_WORKTREE/runtime/the-ring-heartbeats/$worker.json"
  python3 - "$worker" "$heartbeat" "$HEARTBEAT_MAX_AGE" <<'PY'
from __future__ import annotations

from datetime import datetime, timezone
import json
import os
from pathlib import Path
import sys

worker = sys.argv[1].upper()
heartbeat = Path(sys.argv[2])
max_age = float(sys.argv[3])


def argv_for(pid: int) -> list[str]:
    try:
        raw = Path(f"/proc/{pid}/cmdline").read_bytes()
    except OSError:
        return []
    return [p.decode("utf-8", errors="replace") for p in raw.split(b"\0") if p]


def wrapper_matches(argv: list[str]) -> bool:
    for index, value in enumerate(argv):
        if Path(value).name == "run-worker-streamed.py":
            return index + 1 < len(argv) and argv[index + 1].upper() == worker
    return False

heartbeat_pid = 0
heartbeat_age = 10**9
heartbeat_state = "missing"
try:
    value = json.loads(heartbeat.read_text(encoding="utf-8"))
    heartbeat_pid = int(value.get("pid", 0))
    heartbeat_age = datetime.now(timezone.utc).timestamp() - float(value.get("updated_at_epoch", 0))
    heartbeat_state = str(value.get("state", "unknown"))
except (OSError, ValueError, TypeError, json.JSONDecodeError):
    pass

live_wrappers: list[dict[str, object]] = []
for entry in Path("/proc").iterdir():
    if not entry.name.isdigit():
        continue
    pid = int(entry.name)
    argv = argv_for(pid)
    if wrapper_matches(argv):
        live_wrappers.append({"pid": pid, "argv": argv})

heartbeat_argv = argv_for(heartbeat_pid) if heartbeat_pid > 0 else []
heartbeat_matches = wrapper_matches(heartbeat_argv)
healthy = (
    heartbeat_pid > 0
    and heartbeat_age <= max_age
    and heartbeat_matches
    and any(int(row["pid"]) == heartbeat_pid for row in live_wrappers)
)
print(json.dumps({
    "worker": worker,
    "healthy": healthy,
    "heartbeat_pid": heartbeat_pid,
    "heartbeat_age": heartbeat_age,
    "heartbeat_state": heartbeat_state,
    "heartbeat_matches": heartbeat_matches,
    "live_wrappers": live_wrappers,
}, separators=(",", ":")))
PY
}

json_field() {
  python3 -c 'import json,sys; value=json.load(sys.stdin); print(value.get(sys.argv[1], ""))' "$1"
}

json_float_one_decimal() {
  python3 -c 'import json,sys; value=json.load(sys.stdin); print(format(float(value.get(sys.argv[1], 0.0)), ".1f"))' "$1"
}

prepare_python_runtime() {
  local worker="$1" worktree="$2" src_root
  src_root="$worktree/py-ring-agent/src"
  [[ -f "$src_root/r4r_worker/cli.py" ]] \
    || die "$worker controller source missing: $src_root/r4r_worker"
  PYTHONPATH="$src_root${PYTHONPATH:+:$PYTHONPATH}" \
    python3 -c 'import r4r_worker.cli' >/dev/null 2>&1 \
    || die "$worker OpenCode controller is not importable"
}

spawn_wrapper() {
  local worker="$1" worktree="$2" stamp log_path pid required resolved
  for required in node opencode; do
    case "$required" in
      node) resolved="${R4R_NODE_BIN:-node}" ;;
      opencode) resolved="${R4R_OPENCODE_BIN:-opencode}" ;;
    esac
    command -v "$resolved" >/dev/null 2>&1 || {
      warn "$worker: required CLI unavailable in non-interactive PATH: $required ($resolved)"
      warn "$worker: PATH=$PATH"
      return 1
    }
  done
  stamp="$(date -u +%Y%m%dT%H%M%SZ)"
  log_path="$RING_WORKTREE/runtime/ring-agent/guardian/${stamp}-${worker}.log"
  prepare_python_runtime "$worker" "$worktree"
  pid="$(python3 - "$WRAPPER" "$worker" "$log_path" \
      "$RING_WORKTREE" "$PC_WORKTREE" "$LP_WORKTREE" <<'PY'
from pathlib import Path
import os
import subprocess
import sys

wrapper, worker, log_path, ring_root, pc_root, lp_root = sys.argv[1:7]
Path(log_path).parent.mkdir(parents=True, exist_ok=True)
log = open(log_path, "ab", buffering=0)
env = {
    **os.environ,
    "PYTHONUNBUFFERED": "1",
    "R4R_RING_WORKTREE": ring_root,
    "R4R_PC_WORKTREE": pc_root,
    "R4R_LP_WORKTREE": lp_root,
}
process = subprocess.Popen(
    [sys.executable, wrapper, worker],
    cwd=ring_root,
    stdin=subprocess.DEVNULL,
    stdout=log,
    stderr=subprocess.STDOUT,
    start_new_session=True,
    env=env,
)
print(process.pid)
PY
  )"
  printf '%s\n' "$pid" >"$RING_WORKTREE/runtime/ring-agent/guardian/${worker}.pid"
  log "$worker: started wrapper pid=$pid log=$log_path"
}

wait_healthy() {
  local worker="$1" deadline=$((SECONDS + WAIT_SECONDS)) state healthy
  while ((SECONDS < deadline)); do
    state="$(worker_state_json "$worker")"
    healthy="$(json_field healthy <<<"$state")"
    [[ "$healthy" == True ]] && return 0
    # A launched process may fail before its first heartbeat. Avoid waiting the full
    # timeout when no wrapper process remains.
    if [[ "$(python3 -c 'import json,sys; print(len(json.load(sys.stdin).get("live_wrappers", [])))' <<<"$state")" == 0 ]] \
      && ((SECONDS + 3 < deadline)); then
      sleep 3
      state="$(worker_state_json "$worker")"
      [[ "$(python3 -c 'import json,sys; print(len(json.load(sys.stdin).get("live_wrappers", [])))' <<<"$state")" == 0 ]] && return 1
    fi
    sleep 1
  done
  return 1
}

worker_dispatch_ready() {
  local worker="$1" worktree="$2" progress directive
  case "$worker" in
    PC) progress="$worktree/.opencode/progress.pc.json" ;;
    LP) progress="$worktree/.opencode/progress.lp.json" ;;
    *) return 1 ;;
  esac
  directive="$RING_WORKTREE/runtime/control/$worker/assignment.json"
  [[ -f "$directive" ]] || return 1
  PYTHONPATH="$RING_WORKTREE/py-ring-agent/src${PYTHONPATH:+:$PYTHONPATH}" \
  python3 - "$worker" "$progress" "$directive" "$RING_WORKTREE" <<'PY'
import json
import sys
from pathlib import Path

from r4r_ring_agent.assignment import (
    global_progress_path,
    load_global_progress,
    validate_assignment,
)
from r4r_worker.contracts import load_task_plan

worker, progress_path, directive_path, ring_root = sys.argv[1:]
ring = Path(ring_root)
directive = json.loads(Path(directive_path).read_text(encoding="utf-8"))
plan = load_task_plan(ring / ".opencode" / "task-plan.json")
ledger = load_global_progress(global_progress_path(ring))
validated = validate_assignment(
    directive,
    worker=worker,
    tasks={task.id: task for task in plan.tasks},
    accepted_task_ids=tuple(ledger["accepted"]),
    max_age_seconds=int(
        __import__("os").environ.get("R4R_RING_DIRECTIVE_MAX_AGE_SECONDS", "10800")
    ),
)
task_id = validated["task_id"]
action = validated["action"]
if not Path(progress_path).is_file():
    raise SystemExit(0)
progress = json.loads(Path(progress_path).read_text(encoding="utf-8"))
item = next(
    (value for value in progress.get("tasks", []) if value.get("id") == task_id),
    {},
)
if item.get("status") == "ACCEPTED":
    raise SystemExit(1)
if item.get("status") == "BLOCKED" and action != "RETRY_AUTHORIZED":
    raise SystemExit(1)
valid = True
if action == "RETRY_AUTHORIZED":
    authorization_id = str(directive.get("authorization_id") or "")
    policy_version = int(directive.get("recovery_policy_version") or 1)
    grants_total = int(item.get("recovery_grants_total") or 0)
    consumed_version = int(item.get("recovery_repair_policy_version") or 0)
    legacy_v1_upgrade = (
        grants_total == 1
        and bool(item.get("recovery_authorization_consumed"))
        and policy_version == 2
        and consumed_version < policy_version
    )
    valid = (
        bool(authorization_id)
        and item.get("recovery_authorization_consumed") != authorization_id
        and (grants_total < 1 or legacy_v1_upgrade)
    )
raise SystemExit(0 if valid else 1)
PY
}

ensure_one() {
  local worker="$1" worktree state healthy count log_file age
  worktree="$(worker_worktree "$worker")"
  state="$(worker_state_json "$worker")"
  healthy="$(json_field healthy <<<"$state")"
  count="$(python3 -c 'import json,sys; print(len(json.load(sys.stdin).get("live_wrappers", [])))' <<<"$state")"

  if [[ "$healthy" == True ]]; then
    age="$(json_float_one_decimal heartbeat_age <<<"$state")"
    log "$worker: healthy (pid=$(json_field heartbeat_pid <<<"$state"), age=${age}s)"
    return 0
  fi
  if ((count > 0)); then
    warn "$worker: a wrapper process exists but its heartbeat is stale or mismatched; refusing a duplicate"
    warn "$worker state: $state"
    return 1
  fi
  if ! worker_dispatch_ready "$worker" "$worktree"; then
    log "$worker: deliberately quiescent; no fresh Ring-generated assignment"
    return 0
  fi
  if "$CHECK_ONLY"; then
    warn "$worker: inactive"
    return 1
  fi

  log "$worker: inactive; starting authoritative wrapper in $worktree"
  spawn_wrapper "$worker" "$worktree"
  if wait_healthy "$worker"; then
    state="$(worker_state_json "$worker")"
    log "$worker: healthy after start (pid=$(json_field heartbeat_pid <<<"$state"))"
    return 0
  fi
  log_file="$(ls -1t "$RING_WORKTREE"/runtime/ring-agent/guardian/*-"$worker".log 2>/dev/null | head -1 || true)"
  warn "$worker: wrapper failed to become healthy"
  [[ -z "$log_file" ]] || { warn "$worker log: $log_file"; tail -n 120 "$log_file" >&2 || true; }
  return 1
}

check_all() {
  local failed=0 worker
  for worker in "${TARGETS[@]}"; do
    ensure_one "$worker" || failed=1
  done
  return "$failed"
}

if ! "$WATCH"; then
  check_all
  exit $?
fi

log "watch mode active; interval=${INTERVAL_SECONDS}s"
trap 'log "watch mode stopped"; exit 0' INT TERM
while true; do
  check_all || true
  sleep "$INTERVAL_SECONDS"
done
