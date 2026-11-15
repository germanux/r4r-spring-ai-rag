#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT/frontend"
if [[ ! -x node_modules/.bin/ng || ! -d node_modules/playwright ]]; then npm ci --no-audit --no-fund; fi
if [[ -z "${CHROMIUM_PATH:-}" ]]; then
  CHROMIUM_PATH="$(bash "$ROOT/scripts/resolve-chrome-bin.sh" 2>/dev/null || true)"
  if [[ -n "$CHROMIUM_PATH" && -x "$CHROMIUM_PATH" ]]; then export CHROMIUM_PATH; else unset CHROMIUM_PATH; npx playwright install chromium; fi
fi
npx ng build --configuration production --base-href ./
node prerender.mjs dist/r4r-frontend/browser
DEST="$ROOT/front-athagon-r4r-static"
mkdir -p "$DEST"
# Publish generated files only; shared documents and legacy assets are never deleted.
find "$DEST" -maxdepth 1 -type f \( -name 'index.html' -o -name '*.js' -o -name '*.css' -o -name '3rdpartylicenses.txt' \) -delete
cp -a dist/r4r-frontend/browser/. "$DEST/"
test -s "$DEST/index.html"
test -s "$DEST/docs/catalogue.json"
