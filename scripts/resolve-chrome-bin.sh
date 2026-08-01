#!/usr/bin/env bash
set -Eeuo pipefail

# Resolve a Chrome/Chromium executable suitable for Karma's ChromeHeadless launcher.
# stdout: one absolute executable path
# stderr: diagnostics only

fail() {
  printf 'ERROR: %s\n' "$*" >&2
  exit 3
}

is_usable_browser() {
  local candidate="${1:-}"
  [[ -n "$candidate" && -x "$candidate" ]] || return 1
  timeout 10 "$candidate" --version >/dev/null 2>&1 || return 1
}

emit_if_usable() {
  local candidate="${1:-}"
  if is_usable_browser "$candidate"; then
    readlink -f "$candidate" 2>/dev/null || printf '%s\n' "$candidate"
    exit 0
  fi
}

# Explicit configuration has priority.
for variable in CHROME_BIN R4R_CHROME_BIN; do
  value="${!variable:-}"
  if [[ -n "$value" ]]; then
    if [[ "$value" != /* ]]; then
      value="$(command -v "$value" 2>/dev/null || true)"
    fi
    emit_if_usable "$value"
    printf 'WARNING: %s is set but is not a usable executable: %s\n' \
      "$variable" "${!variable}" >&2
  fi
done

# Conventional host installations.
for command_name in \
  chromium \
  chromium-browser \
  google-chrome \
  google-chrome-stable \
  chrome \
  chrome-browser; do
  emit_if_usable "$(command -v "$command_name" 2>/dev/null || true)"
done

for fixed_path in \
  /usr/bin/chromium \
  /usr/bin/chromium-browser \
  /usr/bin/google-chrome \
  /usr/bin/google-chrome-stable \
  /opt/google/chrome/google-chrome \
  /snap/bin/chromium; do
  emit_if_usable "$fixed_path"
done

# Browsers previously downloaded by Playwright. This also covers the old
# benchmark setup where Chromium was managed outside the current repository.
declare -a browser_roots=()
[[ -n "${PLAYWRIGHT_BROWSERS_PATH:-}" ]] && browser_roots+=("$PLAYWRIGHT_BROWSERS_PATH")
browser_roots+=("${XDG_CACHE_HOME:-$HOME/.cache}/ms-playwright")

for browser_root in "${browser_roots[@]}"; do
  [[ -d "$browser_root" ]] || continue
  while IFS= read -r candidate; do
    emit_if_usable "$candidate"
  done < <(
    find -L "$browser_root" -type f \
      \( -name chrome -o -name chromium -o -name chrome-headless-shell -o -name headless_shell \) \
      -perm -u+x -print 2>/dev/null \
      | sort -V -r
  )
done

fail "No compatible Chrome/Chromium executable was found. Checked CHROME_BIN, R4R_CHROME_BIN, PATH, fixed host paths and the Playwright browser cache."
