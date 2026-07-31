#!/usr/bin/env bash
set -Eeuo pipefail

export GIT_AUTHOR_NAME="Codex QWEN3 Agent"
export GIT_AUTHOR_EMAIL="conrado.perez@gmail.com"
export GIT_COMMITTER_NAME="$GIT_AUTHOR_NAME"
export GIT_COMMITTER_EMAIL="$GIT_AUTHOR_EMAIL"

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

DEST=""
CONTROLLER_ARGS=()
while (($#)); do
  case "$1" in
    --destination) DEST="${2:-}"; shift 2 ;;
    *) CONTROLLER_ARGS+=("$1"); shift ;;
  esac
done

if [[ -n "$DEST" ]]; then
  "$ROOT/scripts/select-r4r-destination.sh" --destination "$DEST" --quiet
fi

[[ -f .env ]] || cp .env.example .env
set -a
# shellcheck disable=SC1091
source .env
set +a

# Legacy task locks are deliberately disabled.
rm -f runtime/locks/active-task.json

if ! docker info >/dev/null 2>&1; then
  if [[ "${R4R_DOCKER_GROUP_REEXEC:-0}" != "1" ]] \
      && getent group docker >/dev/null 2>&1 \
      && getent group docker | grep -Eq "(^|,)$USER(,|$)"; then
    printf -v reexec '%q ' "$0" "${CONTROLLER_ARGS[@]}"
    exec sg docker -c "R4R_DOCKER_GROUP_REEXEC=1 ${reexec}"
  fi
  echo "Docker no está disponible sin sudo. Comprueba: docker info" >&2
  exit 2
fi

PYTHON="$ROOT/py-codex-agent/.venv/bin/python"
[[ -x "$PYTHON" ]] || { echo "Ejecuta ./scripts/setup.sh primero" >&2; exit 2; }
command -v "${R4R_OPENCODE_BIN:-opencode}" >/dev/null 2>&1 || {
  echo "OpenCode no está en PATH" >&2; exit 2; }
command -v "${R4R_CODEX_BIN:-codex}" >/dev/null 2>&1 || {
  echo "Codex CLI no está en PATH" >&2; exit 2; }

printf '[r4r] agent=%s locks=disabled\n' "${R4R_OPENCODE_AGENT:-r4r-pc}"
exec "$PYTHON" -m r4r_codex_agent.cli --repo "$ROOT" "${CONTROLLER_ARGS[@]}"
