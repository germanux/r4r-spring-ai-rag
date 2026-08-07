#!/usr/bin/env bash
set -Eeuo pipefail

# CODE_ROOT is the worktree that contains the Phase-3 supervisor implementation.
# RING_ROOT is the authoritative operational worktree whose runtime, heartbeats and
# worker launcher are supervised. They may intentionally be different worktrees.
CODE_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RING_ROOT="${R4R_RING_WORKTREE:-$CODE_ROOT}"
RUNTIME_ENV_HELPER="$CODE_ROOT/scripts/r4r-runtime-env.sh"
OPENAI_ENV_FILE="${R4R_OPENAI_ENV_FILE:-$HOME/.config/r4r/openai.env}"
PYTHON="$CODE_ROOT/py-ring-agent/run-ring-system.py"
GUARDIAN="$CODE_ROOT/scripts/ensure-r4r-workers.sh"
STOP_ALL="$CODE_ROOT/scripts/stop-all-r4r-agents.sh"
RUNTIME="$RING_ROOT/runtime/ring-system"
PID_FILE="$RUNTIME/supervisor.pid"
LOG_FILE="$RUNTIME/supervisor.log"
RING_AGENT_PID_FILE="$RUNTIME/ring-agent.pid"
RING_AGENT_LOG_FILE="$RUNTIME/ring-agent.console.log"
ARCHIVE_DIR="$RUNTIME/archive"
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

ring_agent_requested() {
  local option
  for option in "$@"; do
    [[ "$option" == "--no-ring-agent" ]] && return 1
  done
  return 0
}

prepare_opencode_models() {
  if [[ -z "${OPENAI_API_KEY:-}" && -r "$OPENAI_ENV_FILE" ]]; then
    set -a
    # shellcheck disable=SC1090
    source "$OPENAI_ENV_FILE"
    set +a
  fi

  local binary="${R4R_OPENCODE_BIN:-opencode}" available model
  command -v "$binary" >/dev/null 2>&1 || {
    echo "ERROR: OpenCode is not available: $binary" >&2
    exit 2
  }
  available="$($binary models 2>/dev/null)" || {
    echo "ERROR: OpenCode cannot read its authenticated model catalog" >&2
    exit 2
  }
  for model in \
    openai/gpt-5.6-luna \
    openai/gpt-5.6-terra \
    openai/gpt-5.6-sol; do
    grep -Fq -- "$model" <<<"$available" || {
      echo "ERROR: OpenCode model is unavailable: $model" >&2
      exit 2
    }
  done
}

archive_current_logs() {
  local stamp archive
  stamp="$(date '+%Y%m%dT%H%M%S%z')"
  mkdir -p "$ARCHIVE_DIR"

  if [[ -s "$LOG_FILE" ]]; then
    archive="$ARCHIVE_DIR/supervisor-$stamp.log"
    mv -- "$LOG_FILE" "$archive"
    echo "[r4r-system] archived previous supervisor log: $archive"
  else
    rm -f -- "$LOG_FILE"
  fi

  if [[ -s "$RING_AGENT_LOG_FILE" ]]; then
    archive="$ARCHIVE_DIR/ring-agent.console-$stamp.log"
    mv -- "$RING_AGENT_LOG_FILE" "$archive"
    echo "[r4r-system] archived previous Ring log: $archive"
  else
    rm -f -- "$RING_AGENT_LOG_FILE"
  fi

  : >"$LOG_FILE"
  : >"$RING_AGENT_LOG_FILE"
}

case "$ACTION" in
  start)
    if pid_alive; then
      echo "[r4r-system] already running pid=$(cat "$PID_FILE")"
      exit 0
    fi
    if ring_agent_alive; then
      echo "ERROR: The-Ring is still running without its supervisor; run '$0 stop' first" >&2
      exit 1
    fi
    if ring_agent_requested "$@"; then
      prepare_opencode_models
    fi
    rm -f "$PID_FILE" "$RING_AGENT_PID_FILE"
    archive_current_logs
    printf '[r4r-system] supervisor launch at %s code=%s ring=%s\n' \
      "$(date '+%Y-%m-%dT%H:%M:%S%z')" "$CODE_ROOT" "$RING_ROOT" >>"$LOG_FILE"
    launcher=(python3 -u "$PYTHON" --ring "$RING_ROOT" --guardian "$GUARDIAN" "$@")
    if command -v setsid >/dev/null 2>&1; then
      launcher=(setsid "${launcher[@]}")
    fi
    nohup "${launcher[@]}" >>"$LOG_FILE" 2>&1 &
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
    supervisor_pid=""
    if pid_alive; then
      supervisor_pid="$(cat "$PID_FILE")"
      kill -TERM "$supervisor_pid" 2>/dev/null || true
      for _ in $(seq 1 80); do
        kill -0 "$supervisor_pid" 2>/dev/null || break
        sleep 0.25
      done
      if kill -0 "$supervisor_pid" 2>/dev/null; then
        echo "[r4r-system] supervisor survived SIGTERM; sending SIGKILL" >&2
        kill -KILL "$supervisor_pid" 2>/dev/null || true
      fi
    fi

    # The workers and OpenCode sessions are intentionally detached from the
    # supervisor. A complete stop therefore delegates to the repository-wide,
    # process-tree-aware cleanup after the supervisor has stopped creating work.
    if [[ -x "$STOP_ALL" ]]; then
      if [[ "${R4R_RING_STOP_KEEP_OLLAMA:-false}" == "true" ]]; then
        "$STOP_ALL" --keep-models
      else
        "$STOP_ALL"
      fi
    else
      echo "[r4r-system] ERROR: missing executable $STOP_ALL" >&2
      exit 2
    fi

    rm -f "$PID_FILE" "$RING_AGENT_PID_FILE"
    echo "[r4r-system] stopped supervisor, Ring, PC and LP"
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
  logs)
    echo "===== CURRENT SUPERVISOR LOG ====="
    tail -n "${R4R_LOG_LINES:-120}" "$LOG_FILE" 2>/dev/null || true
    echo "===== CURRENT RING LOG ====="
    tail -n "${R4R_LOG_LINES:-120}" "$RING_AGENT_LOG_FILE" 2>/dev/null || true
    ;;
  follow)
    touch "$LOG_FILE" "$RING_AGENT_LOG_FILE"
    exec tail -n "${R4R_LOG_LINES:-40}" -F "$LOG_FILE" "$RING_AGENT_LOG_FILE"
    ;;
  foreground)
    if ring_agent_requested "$@"; then
      prepare_opencode_models
    fi
    exec python3 -u "$PYTHON" --ring "$RING_ROOT" --guardian "$GUARDIAN" "$@"
    ;;
  once)
    exec python3 -u "$PYTHON" --ring "$RING_ROOT" --guardian "$GUARDIAN" --once "$@"
    ;;
  *)
    echo "Usage: $0 {start|stop|status|logs|follow|foreground|once} [supervisor options]" >&2
    exit 2
    ;;
esac
