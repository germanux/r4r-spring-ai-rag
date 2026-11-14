#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT/frontend"
if [[ ! -x node_modules/.bin/ng || ! -d node_modules/playwright ]]; then npm ci --no-audit --no-fund; fi
# Chromium is a build-time tool only. CHROMIUM_PATH can select an existing browser.
if [[ -z "${CHROMIUM_PATH:-}" ]]; then
  CHROMIUM_PATH="$(bash "$ROOT/scripts/resolve-chrome-bin.sh" 2>/dev/null || true)"
  if [[ -n "$CHROMIUM_PATH" && -x "$CHROMIUM_PATH" ]]; then export CHROMIUM_PATH; else unset CHROMIUM_PATH; npx playwright install chromium; fi
fi
npx ng build --configuration production --base-href /v2-ng/
node prerender.mjs dist/r4r-frontend/browser
DEST="$ROOT/front-athagon-r4r-static/v2-ng"
mkdir -p "$DEST"
# Delete only previous generated v2 output. The archive is never an input or write target.
find "$DEST" -mindepth 1 -maxdepth 1 -exec rm -rf -- {} +
cp -a dist/r4r-frontend/browser/. "$DEST/"
test -s "$DEST/index.html"
