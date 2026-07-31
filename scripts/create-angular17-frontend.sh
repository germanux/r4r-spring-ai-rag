#!/usr/bin/env bash
set -Eeuo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
FRONTEND="$ROOT/frontend"

cd "$ROOT"

if [[ -d "$FRONTEND" ]]; then
  mapfile -t existing < <(find "$FRONTEND" -mindepth 1 -maxdepth 2 -print)
  if (( ${#existing[@]} > 0 )); then
    if (( ${#existing[@]} == 1 )) && [[ "${existing[0]}" == "$FRONTEND/README.md" ]]; then
      rm -f "$FRONTEND/README.md"
      rmdir "$FRONTEND"
    else
      echo "Refusing to overwrite non-empty frontend/:" >&2
      printf '  %s\n' "${existing[@]}" >&2
      exit 2
    fi
  else
    rmdir "$FRONTEND"
  fi
fi

exec npx --yes @angular/cli@17.3.17 new r4r-frontend \
  --directory frontend \
  --standalone \
  --routing \
  --strict \
  --style scss \
  --skip-git \
  --package-manager npm \
  --ssr=false \
  --interactive=false
