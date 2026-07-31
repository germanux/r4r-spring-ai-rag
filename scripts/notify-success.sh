#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ERROR_SOUND="$SCRIPT_DIR/universfield-error-notification-03-125761.mp3"

play_error_sound() {
  if [[ ! -f "$ERROR_SOUND" ]]; then
    printf '\a'
    echo "Error sound not found: $ERROR_SOUND" >&2
    return 0
  fi

  if command -v ffplay >/dev/null 2>&1; then
    ffplay -nodisp -autoexit -loglevel error "$ERROR_SOUND" </dev/null
  elif command -v mpv >/dev/null 2>&1; then
    mpv --no-video --really-quiet "$ERROR_SOUND" </dev/null
  elif command -v cvlc >/dev/null 2>&1; then
    cvlc --intf dummy --play-and-exit --quiet "$ERROR_SOUND" </dev/null
  else
    printf '\a'
    echo "No MP3 player found; terminal bell used." >&2
  fi
}

if [[ "${1:-}" == "--error" ]]; then
  message="${2:-R4R error}"
  play_error_sound
  printf 'R4R error: %s at %s\n' "$message" "$(date --iso-8601=seconds)"
  exit 0
fi

count="${1:-1}"
message="${2:-R4R notification}"
delay="${R4R_NOTIFICATION_DELAY_SECONDS:-0.18}"

if [[ ! "$count" =~ ^[0-9]+$ ]] || (( count < 1 || count > 20 )); then
  echo "Usage: $0 [count:1-20] [message] | $0 --error [message]" >&2
  exit 2
fi

for ((index = 1; index <= count; index++)); do
  printf '\a'
  (( index < count )) && sleep "$delay"
done

printf 'R4R notification: %s — %s ting(s) at %s\n' \
  "$message" "$count" "$(date --iso-8601=seconds)"
