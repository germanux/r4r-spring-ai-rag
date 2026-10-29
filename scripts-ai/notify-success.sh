#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ERROR_SOUND="${R4R_ERROR_SOUND:-$SCRIPT_DIR/universfield-error-notification-03-125761.mp3}"
SUCCESS_SOUND="${R4R_SUCCESS_SOUND:-$SCRIPT_DIR/u-freesound_community-success-1-6297.mp3}"
NOTIFICATION_MODE="${R4R_NOTIFICATION_MODE:-changes}"
ERROR_COOLDOWN_SECONDS="${R4R_ERROR_SOUND_COOLDOWN_SECONDS:-900}"
STATE_DIR="${R4R_NOTIFICATION_STATE_DIR:-${XDG_RUNTIME_DIR:-/tmp}/r4r-notifications-${UID}}"

case "${NOTIFICATION_MODE,,}" in
  0|false|none|off|silent)
    NOTIFICATION_MODE="off"
    ;;
  change|changes|file|files|file-change|file-changes)
    NOTIFICATION_MODE="changes"
    ;;
  error|errors)
    NOTIFICATION_MODE="errors"
    ;;
  important|changes-and-errors|errors-and-changes)
    NOTIFICATION_MODE="important"
    ;;
  all|full)
    NOTIFICATION_MODE="all"
    ;;
  *)
    echo "Invalid R4R_NOTIFICATION_MODE: $NOTIFICATION_MODE" >&2
    echo "Allowed values: off, changes, errors, important, all" >&2
    exit 2
    ;;
esac

if [[ ! "$ERROR_COOLDOWN_SECONDS" =~ ^[0-9]+$ ]]; then
  echo "R4R_ERROR_SOUND_COOLDOWN_SECONDS must be a non-negative integer" >&2
  exit 2
fi

notification_enabled() {
  local event="$1"

  case "$NOTIFICATION_MODE:$event" in
    all:*)
      return 0
      ;;
    changes:file-change)
      return 0
      ;;
    errors:error)
      return 0
      ;;
    important:file-change|important:error)
      return 0
      ;;
    *)
      return 1
      ;;
  esac
}

play_sound() {
  local sound_file="$1"
  local label="$2"

  if [[ ! -f "$sound_file" ]]; then
    echo "$label not found: $sound_file" >&2
    printf '\a'
    return 0
  fi

  if command -v ffplay >/dev/null 2>&1; then
    ffplay \
      -nodisp \
      -autoexit \
      -loglevel error \
      "$sound_file" </dev/null
    return
  fi

  if command -v mpv >/dev/null 2>&1; then
    mpv \
      --no-video \
      --really-quiet \
      "$sound_file"
    return
  fi

  if command -v cvlc >/dev/null 2>&1; then
    cvlc \
      --intf dummy \
      --play-and-exit \
      --quiet \
      "$sound_file"
    return
  fi

  echo "No MP3 player found; using terminal bell." >&2
  printf '\a'
}

error_sound_allowed_by_cooldown() {
  local now last elapsed lock_file state_file temp_file

  (( ERROR_COOLDOWN_SECONDS == 0 )) && return 0

  mkdir -p "$STATE_DIR"
  chmod 700 "$STATE_DIR" 2>/dev/null || true

  lock_file="$STATE_DIR/error-sound.lock"
  state_file="$STATE_DIR/error-sound.last-epoch"

  exec 9>"$lock_file"
  if command -v flock >/dev/null 2>&1; then
    flock -w 2 9 || return 1
  fi

  now="$(date +%s)"
  last=0
  if [[ -r "$state_file" ]]; then
    read -r last < "$state_file" || last=0
  fi
  [[ "$last" =~ ^[0-9]+$ ]] || last=0

  elapsed=$((now - last))
  if (( elapsed < ERROR_COOLDOWN_SECONDS )); then
    printf 'R4R error sound suppressed by cooldown (%ss remaining)\n' \
      "$((ERROR_COOLDOWN_SECONDS - elapsed))"
    return 1
  fi

  temp_file="$state_file.$$"
  printf '%s\n' "$now" > "$temp_file"
  mv -f "$temp_file" "$state_file"
  return 0
}

if [[ "${1:-}" == "--status" ]]; then
  printf 'R4R_NOTIFICATION_MODE=%s\n' "$NOTIFICATION_MODE"
  printf 'R4R_ERROR_SOUND_COOLDOWN_SECONDS=%s\n' "$ERROR_COOLDOWN_SECONDS"
  printf 'R4R_SUCCESS_SOUND=%s\n' "$SUCCESS_SOUND"
  printf 'R4R_ERROR_SOUND=%s\n' "$ERROR_SOUND"
  exit 0
fi

if [[ "${1:-}" == "--error" ]]; then
  MESSAGE="${2:-R4R error}"

  if notification_enabled error; then
    if error_sound_allowed_by_cooldown; then
      play_sound "$ERROR_SOUND" "Error sound"
    fi
  else
    printf 'R4R error sound suppressed (mode=%s)\n' "$NOTIFICATION_MODE"
  fi

  printf 'R4R error: %s at %s\n' \
    "$MESSAGE" \
    "$(date --iso-8601=seconds)"

  exit 0
fi

if [[ "${1:-}" == "--file-changed" ]]; then
  MESSAGE="${2:-Local LLM changed repository files}"

  if notification_enabled file-change; then
    play_sound "$SUCCESS_SOUND" "Success sound"
  else
    printf 'R4R file-change sound suppressed (mode=%s)\n' "$NOTIFICATION_MODE"
  fi

  printf 'R4R file-change notification: %s at %s\n' \
    "$MESSAGE" \
    "$(date --iso-8601=seconds)"

  exit 0
fi

COUNT="${1:-1}"
MESSAGE="${2:-R4R notification}"
DELAY="${R4R_NOTIFICATION_DELAY_SECONDS:-0.18}"

if [[ ! "$COUNT" =~ ^[0-9]+$ ]] \
    || (( COUNT < 1 || COUNT > 20 )); then

  echo "Usage:" >&2
  echo "  $0 [count:1-20] [message]" >&2
  echo "  $0 --file-changed [message]" >&2
  echo "  $0 --error [message]" >&2
  echo "  $0 --status" >&2
  exit 2
fi

if notification_enabled gate; then
  for ((index = 1; index <= COUNT; index++)); do
    printf '\a'

    if (( index < COUNT )); then
      sleep "$DELAY"
    fi
  done
else
  printf 'R4R gate notification suppressed (mode=%s)\n' "$NOTIFICATION_MODE"
fi

printf 'R4R notification: %s — %s ting(s) at %s\n' \
  "$MESSAGE" \
  "$COUNT" \
  "$(date --iso-8601=seconds)"
