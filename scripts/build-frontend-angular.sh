#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# Local source checkouts and an already mounted SMB share. No embedded credentials.
ATHAGON_WEB_ROOT="${ATHAGON_WEB_ROOT:-$(dirname "$(dirname "$ROOT")")/athagon-web.git}"
COMMERCIAL_WEB_ROOT="${COMMERCIAL_WEB_ROOT:-$(dirname "$ROOT")/R4R.git}"
NAS_FRONTEND_DIR="${NAS_FRONTEND_DIR:-${XDG_RUNTIME_DIR:-/run/user/$(id -u)}/gvfs/smb-share:server=synox-nas.local,share=web/r4r-rag}"
ATHAGON_STATIC="$ATHAGON_WEB_ROOT/html-static"
COMMERCIAL_STATIC="$COMMERCIAL_WEB_ROOT/dist"
for site_root in "$ATHAGON_STATIC" "$COMMERCIAL_STATIC"; do
  [[ -f "$site_root/index.html" ]] || {
    echo "ERROR: static site not found at $site_root. Set ATHAGON_WEB_ROOT / COMMERCIAL_WEB_ROOT." >&2
    exit 1
  }
done
ATHAGON_DEST="$ATHAGON_STATIC/r4r-rag"
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

# Overlay generated files, preserving shared documents, historical assets and v1/.
for destination in "$ATHAGON_DEST" "$COMMERCIAL_STATIC/asistente-ia"; do
  mkdir -p "$destination"
  cp -a "$DEST/." "$destination/"
  test -s "$destination/index.html"
  test -s "$destination/privacy-consent.js"
  test -s "$destination/docs/catalogue.json"
  printf 'Frontend copied to %s\n' "$destination"
done
cp "$ROOT/frontend/public/privacy-consent.js" "$ATHAGON_STATIC/privacy-consent.js"
cp "$ROOT/frontend/public/privacy-consent.js" "$COMMERCIAL_STATIC/privacy-consent.js"

# GVfs/CIFS may not support Unix metadata or symlinks: copy contents, not attributes.
# Copy the entry point last so existing pages can use their previous bundles meanwhile.
[[ -d "$(dirname "$NAS_FRONTEND_DIR")" ]] || {
  echo "ERROR: SMB share is not accessible: $NAS_FRONTEND_DIR" >&2
  echo 'Mount smb://synox-nas.local/web in Files, or run: gio mount smb://synox-nas.local/web' >&2
  echo 'For another mounted location, set NAS_FRONTEND_DIR to its local directory.' >&2
  exit 1
}
mkdir -p "$NAS_FRONTEND_DIR"
shopt -s dotglob nullglob
for entry in "$DEST"/*; do
  [[ "$(basename "$entry")" == index.html ]] && continue
  cp -RL -- "$entry" "$NAS_FRONTEND_DIR/"
done
cp -L -- "$DEST/index.html" "$NAS_FRONTEND_DIR/index.html"
test -s "$NAS_FRONTEND_DIR/index.html"
test -s "$NAS_FRONTEND_DIR/privacy-consent.js"
test -s "$NAS_FRONTEND_DIR/docs/catalogue.json"
printf 'Frontend copied to %s (SMB/Web Station)\n' "$NAS_FRONTEND_DIR"
printf 'No Git push performed. The SMB copy updates the NAS web files.\n'
