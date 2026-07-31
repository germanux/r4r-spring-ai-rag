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

CONTROLLER=("$PYTHON" -m r4r_codex_agent.cli --repo "$ROOT" "$@")
dirty_recovery_attempted="${R4R_DIRTY_RECOVERY_ATTEMPTED:-0}"
lock_repair_attempted="${R4R_LOCK_REPAIR_ATTEMPTED:-0}"

while true; do
  set +e
  "${CONTROLLER[@]}"
  controller_exit=$?
  set -e

  if (( controller_exit == 2 )) && [[ "$lock_repair_attempted" != "1" ]]; then
    printf '%s\n' \
      "[r4r] controller exception detected; checking whether a stale active-task lock can be repaired"
    if "$ROOT/scripts/repair-active-task-lock.sh"; then
      lock_repair_attempted=1
      export R4R_LOCK_REPAIR_ATTEMPTED=1
      printf '%s\n' \
        "[r4r] active-task lock repaired safely; retrying the controller once"
      continue
    fi
    echo "[r4r] active-task lock repair was not applicable or not safe" >&2
    exit "$controller_exit"
  fi

  if (( controller_exit == 64 )) && [[ "$dirty_recovery_attempted" != "1" ]]; then
    printf '%s\n' \
      "[r4r] DIRTY_WORKTREE_UNOWNED detected; attempting conservative ownership recovery"
    if "$ROOT/scripts/recover-dirty-worktree.sh"; then
      dirty_recovery_attempted=1
      export R4R_DIRTY_RECOVERY_ATTEMPTED=1
      printf '%s\n' \
        "[r4r] dirty worktree adopted safely; retrying the controller once"
      continue
    fi
    echo "[r4r] automatic recovery was not safe; preserving the original exit code 64" >&2
    exit "$controller_exit"
  fi

  exit "$controller_exit"
done
