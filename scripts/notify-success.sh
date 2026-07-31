#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ERROR_SOUND="$SCRIPT_DIR/universfield-error-notification-03-125761.mp3"

play_error_sound() {
  if [[ ! -f "$ERROR_SOUND" ]]; then
    echo "Error sound not found: $ERROR_SOUND" >&2
    printf '\a'
    return 0
  fi

  if command -v ffplay >/dev/null 2>&1; then
    ffplay \
      -nodisp \
      -autoexit \
      -loglevel error \
      "$ERROR_SOUND" </dev/null
    return
  fi

  if command -v mpv >/dev/null 2>&1; then
    mpv \
      --no-video \
      --really-quiet \
      "$ERROR_SOUND"
    return
  fi

  if command -v cvlc >/dev/null 2>&1; then
    cvlc \
      --intf dummy \
      --play-and-exit \
      --quiet \
      "$ERROR_SOUND"
    return
  fi

  echo "No MP3 player found; using terminal bell." >&2
  printf '\a'
}

if [[ "${1:-}" == "--error" ]]; then
  MESSAGE="${2:-R4R error}"

  play_error_sound

  printf 'R4R error: %s at %s\n' \
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
  echo "  $0 --error [message]" >&2
  exit 2
fi

for ((index = 1; index <= COUNT; index++)); do
  printf '\a'

  if (( index < COUNT )); then
    sleep "$DELAY"
  fi
done

printf 'R4R notification: %s — %s ting(s) at %s\n' \
  "$MESSAGE" \
  "$COUNT" \
  "$(date --iso-8601=seconds)"
  
