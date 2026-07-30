#!/usr/bin/env bash
set -euo pipefail

export GIT_AUTHOR_NAME="Codex QWEN3 Agent"
export GIT_AUTHOR_EMAIL="conrado.perez@gmail.com"
export GIT_COMMITTER_NAME="Codex QWEN3 Agent"
export GIT_COMMITTER_EMAIL="conrado.perez@gmail.com"

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

[[ -f .env ]] || cp .env.example .env
set -a
# shellcheck disable=SC1091
source .env
set +a

# The autonomous controller cannot rely on an interactive sudo prompt.
# If setup.sh has already added the user to the docker group but the current
# login session has not refreshed its groups, re-execute once through `sg`.
if ! docker info >/dev/null 2>&1; then
  if [[ "${R4R_DOCKER_GROUP_REEXEC:-0}" != "1" ]] \
      && getent group docker >/dev/null 2>&1 \
      && getent group docker | grep -Eq "(^|,)$USER(,|$)"; then
    printf -v reexec_command '%q ' "$0" "$@"
    exec sg docker -c "R4R_DOCKER_GROUP_REEXEC=1 ${reexec_command}"
  fi

  echo "Docker is unavailable without sudo." >&2
  echo "Start the Docker service and log out/in once after setup.sh adds you to the docker group." >&2
  echo "Check with: docker info" >&2
  exit 2
fi

PYTHON="$ROOT/py-codex-agent/.venv/bin/python"
[[ -x "$PYTHON" ]] || {
  echo "Run ./scripts/setup.sh first" >&2
  exit 2
}

command -v "${R4R_OPENCODE_BIN:-opencode}" >/dev/null 2>&1 || {
  echo "OpenCode is not installed. Run ./scripts/setup.sh" >&2
  exit 2
}

command -v "${R4R_CODEX_BIN:-codex}" >/dev/null 2>&1 || {
  echo "Codex CLI is not installed. Run ./scripts/setup.sh" >&2
  exit 2
}

exec "$PYTHON" -m r4r_codex_agent.cli --repo "$ROOT" "$@"
