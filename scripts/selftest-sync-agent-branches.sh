#!/usr/bin/env bash
set -Eeuo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SYNC="$ROOT/scripts/sync-agent-branches.sh"
if [[ ! -e /dev/fd ]]; then
  printf 'SKIP: this environment does not provide /dev/fd for Bash process substitution\n'
  exit 0
fi
TEST_PARENT="${TMPDIR:-/tmp}"
if [[ ! -d "$TEST_PARENT" ]]; then
  TEST_PARENT="$ROOT/runtime"
  mkdir -p "$TEST_PARENT"
fi
TEST_ROOT="$(mktemp -d "$TEST_PARENT/r4r-sync-selftest.XXXXXX")"
cleanup() {
  local code=$?
  rm -rf -- "$TEST_ROOT"
  exit "$code"
}
trap cleanup EXIT

INTEGRATION="$TEST_ROOT/r4r-integration.git"
mkdir -p "$INTEGRATION"
git -C "$INTEGRATION" init -q
git -C "$INTEGRATION" config user.name "R4R Selftest"
git -C "$INTEGRATION" config user.email "selftest@example.invalid"
printf 'base\n' >"$INTEGRATION/state.txt"
git -C "$INTEGRATION" add state.txt
git -C "$INTEGRATION" commit -qm base
git -C "$INTEGRATION" branch -m agent/integration

declare -A WORKTREES=(
  [agent/pc-qwen3-worker]="$TEST_ROOT/r4r-pc-worker.git"
  [agent/laptop-qwen3-worker]="$TEST_ROOT/r4r-lp-worker.git"
  [agent/ring-agent-worker]="$TEST_ROOT/r4r-ring-agent.git"
  [agent/opencode-dual-surgical]="$TEST_ROOT/r4r-surgical-worker.git"
  [r4r-chatgpt]="$TEST_ROOT/r4r-chatgpt.git"
)

for branch in "${!WORKTREES[@]}"; do
  git -C "$INTEGRATION" branch "$branch"
  git -C "$INTEGRATION" worktree add -q "${WORKTREES[$branch]}" "$branch"
done

run_sync() {
  local output="$1"
  R4R_REPOSITORY="$INTEGRATION" \
  R4R_DEVELOPMENT_ROOT="$TEST_ROOT" \
  R4R_INTEGRATION_WORKTREE="$INTEGRATION" \
  R4R_RING_WORKTREE="${WORKTREES[agent/ring-agent-worker]}" \
  R4R_PC_WORKTREE="${WORKTREES[agent/pc-qwen3-worker]}" \
  R4R_LP_WORKTREE="${WORKTREES[agent/laptop-qwen3-worker]}" \
  R4R_BRANCH_SYNC_RUNTIME_ROOT="$TEST_ROOT/runtime" \
  R4R_BRANCH_SYNC_LOCK="$TEST_ROOT/sync.lock" \
  R4R_GIT_LOCK="$TEST_ROOT/git.lock" \
    "$SYNC" \
      --no-fetch \
      --no-push \
      --no-notify \
      --no-open-conflict-dir \
      --no-modal-alert \
      --dry-run >"$output" 2>&1 || {
        sed -n '1,240p' "$output" >&2
        return 1
      }
}

disabled_log="$TEST_ROOT/disabled.log"
run_sync "$disabled_log"
disabled_sources="$(grep -F 'source sequence:' "$disabled_log")"
disabled_targets="$(grep -F 'propagation targets:' "$disabled_log")"
for expected in \
  agent/pc-qwen3-worker \
  agent/laptop-qwen3-worker \
  agent/ring-agent-worker \
  r4r-chatgpt; do
  [[ "$disabled_sources" == *"$expected"* ]] || {
    printf 'Missing source %s\n' "$expected" >&2
    exit 1
  }
  [[ "$disabled_targets" == *"$expected"* ]] || {
    printf 'Missing target %s\n' "$expected" >&2
    exit 1
  }
done
[[ "$disabled_sources" != *agent/opencode-dual-surgical* ]]
[[ "$disabled_targets" != *agent/opencode-dual-surgical* ]]

printf 'OK: PC/LP hot-sync remains active and the retired SURGICAL branch is excluded\n'
