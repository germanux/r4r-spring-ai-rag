#!/usr/bin/env bash
# Shared runtime environment bootstrap for interactive shells, cron and nohup.
# This file is meant to be sourced. It deliberately does not enable set -e/-u.

r4r_path_prepend_once() {
  local directory="${1:-}"
  [[ -n "$directory" && -d "$directory" ]] || return 0
  case ":${PATH:-}:" in
    *":$directory:"*) ;;
    *) PATH="$directory${PATH:+:$PATH}" ;;
  esac
}

r4r_source_runtime_env() {
  local root="${1:-}"
  local env_file
  [[ -n "$root" ]] || return 0
  for env_file in "$root/.env" "$root/.env.r4r.local"; do
    if [[ -f "$env_file" ]]; then
      set -a
      # shellcheck disable=SC1090
      source "$env_file"
      set +a
    fi
  done
}

r4r_resolve_cli_variable() {
  local variable="$1" fallback="$2" current resolved
  current="${!variable:-$fallback}"
  if ! resolved="$(command -v "$current" 2>/dev/null)"; then
    resolved="$(command -v "$fallback" 2>/dev/null)" || return 1
  fi
  resolved="$(readlink -f "$resolved" 2>/dev/null || printf '%s' "$resolved")"
  printf -v "$variable" '%s' "$resolved"
  export "$variable"
  return 0
}

r4r_runtime_bootstrap() {
  local root="${1:-}"
  local directory

  r4r_source_runtime_env "$root"

  # Non-interactive cron and nohup processes usually do not load NVM or user profile
  # initialization. Add the common per-user CLI locations deterministically.
  r4r_path_prepend_once /usr/local/bin
  if [[ -n "${HOME:-}" ]]; then
    r4r_path_prepend_once "$HOME/.local/bin"
    r4r_path_prepend_once "$HOME/.npm-global/bin"
    r4r_path_prepend_once "$HOME/.opencode/bin"
    r4r_path_prepend_once "$HOME/.volta/bin"
    r4r_path_prepend_once "$HOME/.bun/bin"
    for directory in "$HOME"/.nvm/versions/node/*/bin; do
      [[ -d "$directory" ]] && r4r_path_prepend_once "$directory"
    done
  fi
  export PATH
  hash -r 2>/dev/null || true

  r4r_resolve_cli_variable R4R_NODE_BIN node || true
  r4r_resolve_cli_variable R4R_NPM_BIN npm || true
  r4r_resolve_cli_variable R4R_OPENCODE_BIN opencode || true
}
