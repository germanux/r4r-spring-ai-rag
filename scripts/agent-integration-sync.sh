#!/usr/bin/env bash
set -Eeuo pipefail

# Synchronize the current agent branch with agent/integration at lifecycle
# boundaries. Dirty worktrees are attempted, not rejected pre-emptively.
# No stash, reset or force-push is used. A recovery snapshot is written before
# attempting an incoming merge over local uncommitted changes.
# Structured Codex/local-LLM artifacts are published before the branch is pushed.

PHASE="${1:-}"
case "$PHASE" in
  startup|checkpoint|shutdown) ;;
  *) echo "Usage: $0 {startup|checkpoint|shutdown}" >&2; exit 64 ;;
esac

ROOT="$(git rev-parse --show-toplevel 2>/dev/null)" || {
  echo "[r4r-agent-sync] not inside a Git worktree" >&2
  exit 66
}
DEVELOPMENT_ROOT="${R4R_DEVELOPMENT_ROOT:-$HOME/Desarrollo}"
HUB_BRANCH="${R4R_INTEGRATION_BRANCH:-agent/integration}"
REMOTE="${R4R_SYNC_REMOTE:-origin}"
HUB_WORKTREE="${R4R_INTEGRATION_WORKTREE:-$DEVELOPMENT_ROOT/r4r-integration.git}"
LOCK_PATH="${R4R_GIT_LOCK:-$DEVELOPMENT_ROOT/.r4r-runtime/git.lock}"
PUSH_POLICY="${R4R_AGENT_SYNC_PUSH_POLICY:-strict}"
BACKUP_ROOT="${R4R_AGENT_SYNC_BACKUP_ROOT:-$DEVELOPMENT_ROOT/.r4r-runtime/agent-sync-backups}"
ARTIFACT_COLLECTOR="${R4R_ARTIFACT_COLLECTOR:-$ROOT/scripts/collect-agent-artifacts.py}"

log() { printf '[r4r-agent-sync] %s\n' "$*"; }
die() { printf '[r4r-agent-sync] ERROR: %s\n' "$*" >&2; exit 2; }

for command in git flock realpath tar sha256sum awk stat python3; do
  command -v "$command" >/dev/null 2>&1 || die "required command unavailable: $command"
done

ROOT="$(realpath -e "$ROOT")"
[[ -d "$HUB_WORKTREE" ]] || die "integration worktree does not exist: $HUB_WORKTREE"
HUB_WORKTREE="$(realpath -e "$HUB_WORKTREE")"
git -C "$HUB_WORKTREE" rev-parse --is-inside-work-tree >/dev/null 2>&1 \
  || die "invalid integration worktree: $HUB_WORKTREE"

BRANCH="$(git -C "$ROOT" branch --show-current)"
[[ -n "$BRANCH" ]] || die "detached HEAD is not supported: $ROOT"
[[ "$BRANCH" != "$HUB_BRANCH" ]] || die "agent worktree is already on $HUB_BRANCH"
[[ "$(git -C "$HUB_WORKTREE" branch --show-current)" == "$HUB_BRANCH" ]] \
  || die "$HUB_WORKTREE is not on $HUB_BRANCH"

mkdir -p "$(dirname "$LOCK_PATH")"
exec 9>"$LOCK_PATH"
flock -w "${R4R_GIT_LOCK_TIMEOUT_SECONDS:-120}" 9 || die "shared Git lock remained busy"

is_clean() {
  [[ -z "$(git -C "$1" status --porcelain=v1 --untracked-files=all -- . ':(exclude)runtime/**')" ]]
}

push_ref() {
  local path="$1" branch="$2"
  case "$PUSH_POLICY" in
    off) return 0 ;;
    strict) git -C "$path" push "$REMOTE" "$branch" ;;
    best-effort)
      git -C "$path" push "$REMOTE" "$branch" || {
        log "WARNING: push failed for $branch; local synchronization is preserved"
        return 0
      }
      ;;
    *) die "invalid R4R_AGENT_SYNC_PUSH_POLICY: $PUSH_POLICY" ;;
  esac
}

abort_merge_if_needed() {
  local path="$1"
  if git -C "$path" rev-parse -q --verify MERGE_HEAD >/dev/null 2>&1; then
    git -C "$path" merge --abort >/dev/null 2>&1 || true
  fi
}

merge_or_abort() {
  local path="$1" ref="$2" label="$3"
  if git -C "$path" merge --no-edit "$ref"; then
    return 0
  fi
  abort_merge_if_needed "$path"
  die "merge conflict while $label; merge was aborted"
}

backup_dirty_state() {
  local path="$1" branch="$2" stamp safe_branch destination
  is_clean "$path" && return 0
  stamp="$(date -u +%Y%m%dT%H%M%SZ)-$$"
  safe_branch="${branch//\//-}"
  destination="$BACKUP_ROOT/$stamp-$safe_branch"
  mkdir -p "$destination"
  git -C "$path" status --porcelain=v1 --untracked-files=all -- . ':(exclude)runtime/**' \
    >"$destination/status.txt"
  git -C "$path" diff --binary -- . ':(exclude)runtime/**' >"$destination/worktree.patch"
  git -C "$path" diff --cached --binary -- . ':(exclude)runtime/**' >"$destination/index.patch"
  git -C "$path" ls-files --others --exclude-standard -z -- . ':(exclude)runtime/**' \
    >"$destination/untracked.list"
  if [[ -s "$destination/untracked.list" ]]; then
    tar -C "$path" --null -T "$destination/untracked.list" \
      -czf "$destination/untracked.tar.gz"
  fi
  printf '%s\n' \
    "worktree=$path" \
    "branch=$branch" \
    "head=$(git -C "$path" rev-parse HEAD)" \
    "phase=$PHASE" \
    >"$destination/manifest.txt"
  log "$branch: dirty-state recovery snapshot=$destination"
}

state_fingerprint() {
  local path="$1"
  {
    git -C "$path" status --porcelain=v1 -z --untracked-files=all -- . ':(exclude)runtime/**'
    git -C "$path" diff --binary -- . ':(exclude)runtime/**'
    git -C "$path" diff --cached --binary -- . ':(exclude)runtime/**'
    git -C "$path" ls-files --stage -z -- . ':(exclude)runtime/**'
    git -C "$path" ls-files --others --exclude-standard -z -- . ':(exclude)runtime/**' |
      while IFS= read -r -d '' file; do
        printf '%s\0' "$file"
        stat --printf='%f %s\0' -- "$path/$file"
        [[ -f "$path/$file" ]] && sha256sum -- "$path/$file" || true
      done
  } | sha256sum | awk '{print $1}'
}

publish_agent_artifacts() {
  local agent="" worker=""
  case "$BRANCH" in
    agent/ring-agent-worker) agent=ring; worker=RING ;;
    agent/pc-qwen3-worker) agent=PC; worker=PC ;;
    agent/laptop-qwen3-worker) agent=LP; worker=LP ;;
    *) return 0 ;;
  esac
  [[ -f "$ARTIFACT_COLLECTOR" ]] || die "artifact collector not found: $ARTIFACT_COLLECTOR"
  python3 "$ARTIFACT_COLLECTOR" \
    --repo "$ROOT" \
    --agent "$agent" \
    --worker-id "$worker" \
    --commit
}

merge_inbound_or_defer() {
  local path="$1" ref="$2" label="$3" before_state after_state
  backup_dirty_state "$path" "$BRANCH"
  before_state="$(state_fingerprint "$path")"
  if git -C "$path" merge --no-edit "$ref"; then
    return 0
  fi
  abort_merge_if_needed "$path"
  after_state="$(state_fingerprint "$path")"
  [[ "$after_state" == "$before_state" ]] \
    || die "Git rejected $label and the prior index/worktree state was not restored exactly"
  log "$BRANCH: inbound merge deferred after Git rejected $label"
  return 1
}

publish_agent_artifacts

if ! is_clean "$HUB_WORKTREE"; then
  die "integration worktree is dirty; refusing automatic synchronization"
fi

log "$BRANCH: phase=$PHASE"
git -C "$ROOT" fetch --prune "$REMOTE"

if git -C "$HUB_WORKTREE" show-ref --verify --quiet "refs/remotes/$REMOTE/$HUB_BRANCH"; then
  git -C "$HUB_WORKTREE" merge --ff-only "$REMOTE/$HUB_BRANCH" \
    || die "$HUB_BRANCH diverged from $REMOTE/$HUB_BRANCH; manual reconciliation required"
fi

# Every lifecycle boundary carries any already committed agent work outward.
# This makes startup repair a missed prior publication as well as import the hub.
push_ref "$ROOT" "$BRANCH"
git -C "$HUB_WORKTREE" fetch "$REMOTE" "$BRANCH"
merge_or_abort "$HUB_WORKTREE" "$REMOTE/$BRANCH" "centralizing $BRANCH"
push_ref "$HUB_WORKTREE" "$HUB_BRANCH"
git -C "$ROOT" fetch "$REMOTE" "$HUB_BRANCH"

# Always ask Git to perform the incoming merge, even when the worktree is dirty.
# Git may accept disjoint local changes. A real rejection is deferred safely.
if ! merge_inbound_or_defer "$ROOT" "$REMOTE/$HUB_BRANCH" "importing $HUB_BRANCH into $BRANCH"; then
  exit 0
fi
push_ref "$ROOT" "$BRANCH"

log "$BRANCH: phase=$PHASE complete; head=$(git -C "$ROOT" rev-parse --short=12 HEAD)"
