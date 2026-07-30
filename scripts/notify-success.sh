#!/usr/bin/env bash
set -euo pipefail

COUNT="${1:-1}"
MESSAGE="${2:-R4R notification}"
DELAY="${R4R_NOTIFICATION_DELAY_SECONDS:-0.18}"

if [[ ! "$COUNT" =~ ^[0-9]+$ ]] || (( COUNT < 1 || COUNT > 20 )); then
  echo "Usage: $0 [count:1-20] [message]" >&2
  exit 2
fi

for ((index = 1; index <= COUNT; index++)); do
  printf '\a'
  if (( index < COUNT )); then
    sleep "$DELAY"
  fi
done

printf 'R4R notification: %s — %s ting(s) at %s\n' \
  "$MESSAGE" "$COUNT" "$(date --iso-8601=seconds)"
