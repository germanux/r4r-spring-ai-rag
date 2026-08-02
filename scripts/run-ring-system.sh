#!/usr/bin/env bash
set -Eeuo pipefail

# CODE_ROOT is the worktree that contains the Phase-3 supervisor implementation.
# RING_ROOT is the authoritative operational worktree whose runtime, heartbeats and
# worker launcher are supervised. They may intentionally be different worktrees.
CODE_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RING_ROOT="${R4R_RING_WORKTREE:-$CODE_ROOT}"
RUNTIME_ENV_HELPER="$CODE_ROOT/scripts/r4r-runtime-env.sh"
PYTHON="$CODE_ROOT/py-ring-agent/run-ring-system.py"
GUARDIAN="$CODE_ROOT/scripts/ensure-r4r-workers.sh"
RUNTIME="$RING_ROOT/runtime/ring-system"
PID_FILE="$RUNTIME/supervisor.pid"
LOG_FILE="$RUNTIME/supervisor.log"
RING_AGENT_PID_FILE="$RUNTIME/ring-agent.pid"
RING_AGENT_LOG_FILE="$RUNTIME/ring-agent.console.log"
ACTION="${1:-start}"
shift || true

RING_ROOT="$(realpath -e "$RING_ROOT" 2>/dev/null)" || {
  echo "ERROR: Ring worktree does not exist: $RING_ROOT" >&2
  exit 2
}
export R4R_RING_WORKTREE="$RING_ROOT"
if [[ -r "$RUNTIME_ENV_HELPER" ]]; then
  # shellcheck disable=SC1090
  source "$RUNTIME_ENV_HELPER"
  r4r_runtime_bootstrap "$RING_ROOT"
fi

mkdir -p "$RUNTIME"
[[ -f "$PYTHON" ]] || { echo "ERROR: missing $PYTHON" >&2; exit 2; }
[[ -x "$GUARDIAN" ]] || { echo "ERROR: missing executable $GUARDIAN" >&2; exit 2; }

pid_alive_file() {
  local file="$1"
  [[ -s "$file" ]] || return 1
  local pid
  pid="$(cat "$file")"
  [[ "$pid" =~ ^[1-9][0-9]*$ ]] || return 1
  kill -0 "$pid" 2>/dev/null
}

pid_alive() {
  pid_alive_file "$PID_FILE"
}

ring_agent_alive() {
  pid_alive_file "$RING_AGENT_PID_FILE"
}

case "$ACTION" in
  start)
    if pid_alive; then
      echo "[r4r-system] already running pid=$(cat "$PID_FILE")"
      exit 0
    fi
    rm -f "$PID_FILE"
    nohup python3 "$PYTHON" --ring "$RING_ROOT" --guardian "$GUARDIAN" "$@" >>"$LOG_FILE" 2>&1 &
    launcher_pid=$!
    for _ in $(seq 1 40); do
      if pid_alive; then
        echo "[r4r-system] started pid=$(cat "$PID_FILE") log=$LOG_FILE code=$CODE_ROOT ring=$RING_ROOT"
        exit 0
      fi
      kill -0 "$launcher_pid" 2>/dev/null || break
      sleep 0.25
    done
    tail -n 120 "$LOG_FILE" >&2 || true
    echo "[r4r-system] ERROR: supervisor did not start" >&2
    exit 1
    ;;
  stop)
    if ! pid_alive; then
      rm -f "$PID_FILE"
      echo "[r4r-system] already stopped"
      exit 0
    fi
    pid="$(cat "$PID_FILE")"
    kill -TERM "$pid"
    for _ in $(seq 1 80); do
      kill -0 "$pid" 2>/dev/null || { rm -f "$PID_FILE"; echo "[r4r-system] stopped"; exit 0; }
      sleep 0.25
    done
    echo "[r4r-system] ERROR: supervisor did not stop" >&2
    exit 1
    ;;
  status)
    if pid_alive; then
      echo "[r4r-system] running pid=$(cat "$PID_FILE") code=$CODE_ROOT ring=$RING_ROOT"
      if ring_agent_alive; then
        echo "[r4r-system] The-Ring cognitive loop: running pid=$(cat "$RING_AGENT_PID_FILE") log=$RING_AGENT_LOG_FILE"
      else
        echo "[r4r-system] The-Ring cognitive loop: not running"
      fi
      "$GUARDIAN" --check-only --ring "$RING_ROOT" || true
      exit 0
    fi
    echo "[r4r-system] stopped code=$CODE_ROOT ring=$RING_ROOT"
    if ring_agent_alive; then
      echo "[r4r-system] WARNING: The-Ring cognitive loop still runs pid=$(cat "$RING_AGENT_PID_FILE")"
    fi
    "$GUARDIAN" --check-only --ring "$RING_ROOT" || true
    exit 1
    ;;
  foreground)
    exec python3 "$PYTHON" --ring "$RING_ROOT" --guardian "$GUARDIAN" "$@"
    ;;
  once)
    exec python3 "$PYTHON" --ring "$RING_ROOT" --guardian "$GUARDIAN" --once "$@"
    ;;
  *)
    echo "Usage: $0 {start|stop|status|foreground|once} [supervisor options]" >&2
    exit 2
    ;;
esac
