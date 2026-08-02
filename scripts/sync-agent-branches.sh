#!/usr/bin/env bash
set -Eeuo pipefail

# Merge one pinned integration revision into every active agent branch. Conflicts are
# isolated per target: the target is reset to its original commit, the conflict is
# reported, and synchronization continues with the remaining branches.
#
# PC and LP are synchronized together through merge-worker-branches-and-restart.sh so
# their dirty state is backed up/stashed/restored and both authoritative wrappers are
# restarted safely. At the end, the Phase-3 worker guardian is started (or checked) so
# an inactive laptop worker is recovered even when no new merge was necessary.

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DEVELOPMENT_ROOT="${R4R_DEVELOPMENT_ROOT:-$HOME/Desarrollo}"
CANONICAL_RING_WORKTREE="${R4R_RING_WORKTREE:-$DEVELOPMENT_ROOT/r4r-ring-agent.git}"
REPOSITORY="${R4R_REPOSITORY:-$ROOT}"
SOURCE_BRANCH="${R4R_INTEGRATION_BRANCH:-agent/integration}"
REMOTE="${R4R_SYNC_REMOTE:-origin}"
PUSH=true
FETCH=false
DRY_RUN=false
SYNC_WORKERS=true
START_GUARDIAN=true
TARGETS=()
TARGETS_EXPLICIT=false
TMP_ROOT=""
FAILED=()
UPDATED=()
SKIPPED=()

usage() {
  cat <<'USAGE'
Usage: ./scripts/sync-agent-branches.sh [options]

  --source BRANCH       Integration source (default: agent/integration).
  --target BRANCH       Synchronize only this target; repeatable.
  --remote NAME         Push remote (default: origin).
  --fetch               Fetch/prune the remote before pinning the source.
  --push                Push updated branches (default; accepted explicitly).
  --no-push             Do not push updated branches.
  --no-workers          Do not merge/restart PC and LP.
  --no-guardian         Do not start/check the PC+LP guardian.
  --dry-run             Print the plan without modifying branches or processes.
  -h, --help            Show this help.

Default targets are local active branches matching agent/* plus r4r-chatgpt, excluding:
- the source branch itself;
- main/master;
- backup/*;
- obsolete *claude-surgical* branches.

The integration commit is pinned once. A clean target is merged with git merge --no-edit.
On conflict, the merge is aborted and the target is restored. Dirty non-worker targets
are skipped. PC/LP dirty state is preserved by merge-worker-branches-and-restart.sh.
Successful targets are pushed by default.
USAGE
}

log()  { printf '[r4r-branch-sync] %s\n' "$*"; }
warn() { printf '[r4r-branch-sync] WARNING: %s\n' "$*" >&2; }
die()  { printf '[r4r-branch-sync] ERROR: %s\n' "$*" >&2; exit 2; }

while (($#)); do
  case "$1" in
    --source) (($# >= 2)) || die "--source requires a branch"; SOURCE_BRANCH="$2"; shift 2 ;;
    --target) (($# >= 2)) || die "--target requires a branch"; TARGETS+=("$2"); TARGETS_EXPLICIT=true; shift 2 ;;
    --remote) (($# >= 2)) || die "--remote requires a name"; REMOTE="$2"; shift 2 ;;
    --fetch) FETCH=true; shift ;;
    --push) PUSH=true; shift ;;
    --no-push) PUSH=false; shift ;;
    --no-workers) SYNC_WORKERS=false; shift ;;
    --no-guardian) START_GUARDIAN=false; shift ;;
    --dry-run) DRY_RUN=true; shift ;;
    -h|--help) usage; exit 0 ;;
    *) die "unknown argument: $1" ;;
  esac
done

for command in git flock mktemp realpath; do
  command -v "$command" >/dev/null 2>&1 || die "required command unavailable: $command"
done
REPOSITORY="$(realpath -e "$REPOSITORY" 2>/dev/null)" || die "repository path does not exist"
git -C "$REPOSITORY" rev-parse --is-inside-work-tree >/dev/null 2>&1 \
  || die "not a Git worktree: $REPOSITORY"
COMMON_DIR="$(git -C "$REPOSITORY" rev-parse --git-common-dir)"
[[ "$COMMON_DIR" == /* ]] || COMMON_DIR="$REPOSITORY/$COMMON_DIR"
COMMON_DIR="$(realpath -e "$COMMON_DIR")"

mkdir -p "$REPOSITORY/runtime"
exec 9>"$REPOSITORY/runtime/.sync-agent-branches.lock"
flock -n 9 || { log "another branch synchronization is already running"; exit 0; }
# Serialize with the Google Drive import/autocommit jobs shown in the existing cron.
exec 8>/tmp/r4r-drive-import.lock
flock -w 30 8 || die "Google Drive import lock remained busy for 30 seconds"

if "$FETCH"; then
  git -C "$REPOSITORY" fetch --prune "$REMOTE"
fi

git -C "$REPOSITORY" show-ref --verify --quiet "refs/heads/$SOURCE_BRANCH" \
  || die "local source branch does not exist: $SOURCE_BRANCH"
SOURCE_COMMIT="$(git -C "$REPOSITORY" rev-parse --verify "$SOURCE_BRANCH^{commit}")"

if ! "$TARGETS_EXPLICIT"; then
  while IFS= read -r branch; do
    case "$branch" in
      "$SOURCE_BRANCH"|main|master|backup/*|*claude-surgical*) continue ;;
      r4r-chatgpt|agent/*) TARGETS+=("$branch") ;;
    esac
  done < <(git -C "$REPOSITORY" for-each-ref --format='%(refname:short)' refs/heads | sort)
fi

((${#TARGETS[@]} > 0)) || die "no synchronization targets were selected"

# Deduplicate while retaining the first occurrence.
mapfile -t TARGETS < <(printf '%s\n' "${TARGETS[@]}" | awk 'NF && !seen[$0]++')

worktree_for_branch() {
  local target="$1" current_path="" line
  while IFS= read -r line; do
    case "$line" in
      "worktree "*) current_path="${line#worktree }" ;;
      "branch refs/heads/"*)
        if [[ "${line#branch refs/heads/}" == "$target" ]]; then
          printf '%s\n' "$current_path"
          return 0
        fi
        ;;
    esac
  done < <(git -C "$REPOSITORY" worktree list --porcelain)
  return 1
}

valid_common_worktree() {
  local path="$1" common
  [[ -d "$path" ]] || return 1
  git -C "$path" rev-parse --is-inside-work-tree >/dev/null 2>&1 || return 1
  common="$(git -C "$path" rev-parse --git-common-dir 2>/dev/null)" || return 1
  [[ "$common" == /* ]] || common="$path/$common"
  common="$(realpath -e "$common" 2>/dev/null)" || return 1
  [[ "$common" == "$COMMON_DIR" ]]
}

resolve_ring_runtime_worktree() {
  local candidate
  candidate="$(worktree_for_branch agent/ring-agent-worker || true)"
  if [[ -n "$candidate" ]] && valid_common_worktree "$candidate" \
      && [[ -f "$candidate/py-ring-agent/run-worker-streamed.py" ]]; then
    realpath -e "$candidate"
    return 0
  fi
  for candidate in "$CANONICAL_RING_WORKTREE" "$ROOT"; do
    if valid_common_worktree "$candidate" \
        && [[ -f "$candidate/py-ring-agent/run-worker-streamed.py" ]]; then
      realpath -e "$candidate"
      return 0
    fi
  done
  return 1
}

branch_exists() {
  git -C "$REPOSITORY" show-ref --verify --quiet "refs/heads/$1"
}

branch_contains_source() {
  git -C "$REPOSITORY" merge-base --is-ancestor "$SOURCE_COMMIT" "$1"
}

remote_exists() {
  git -C "$REPOSITORY" remote get-url "$REMOTE" >/dev/null 2>&1
}

push_branch() {
  local branch="$1"
  "$PUSH" || return 0
  remote_exists || { warn "remote $REMOTE is unavailable; cannot push $branch"; return 1; }
  if "$DRY_RUN"; then
    log "DRY-RUN: git push $REMOTE refs/heads/$branch:refs/heads/$branch"
    return 0
  fi
  git -C "$REPOSITORY" push "$REMOTE" "refs/heads/$branch:refs/heads/$branch"
}

merge_regular_branch() {
  local branch="$1" worktree temporary=false before merge_log
  branch_exists "$branch" || { warn "$branch: local branch missing"; FAILED+=("$branch:missing"); return 1; }
  if branch_contains_source "$branch"; then
    log "$branch: already contains ${SOURCE_COMMIT:0:12}"
    push_branch "$branch" || FAILED+=("$branch:push")
    return 0
  fi

  if worktree="$(worktree_for_branch "$branch")"; then
    worktree="$(realpath -e "$worktree")"
  else
    TMP_ROOT="${TMP_ROOT:-$(mktemp -d "${TMPDIR:-/tmp}/r4r-branch-sync.XXXXXX")}"
    worktree="$TMP_ROOT/${branch//\//__}"
    if "$DRY_RUN"; then
      log "DRY-RUN: temporary worktree for $branch at $worktree"
      worktree="$REPOSITORY"
    else
      git -C "$REPOSITORY" worktree add --quiet "$worktree" "$branch"
      temporary=true
    fi
  fi

  if [[ "$worktree" != "$REPOSITORY" ]] && [[ -n "$(git -C "$worktree" status --porcelain=v1 --untracked-files=all)" ]]; then
    warn "$branch: worktree is dirty; skipping without touching it ($worktree)"
    SKIPPED+=("$branch:dirty")
    "$temporary" && git -C "$REPOSITORY" worktree remove --force "$worktree" >/dev/null 2>&1 || true
    return 1
  fi

  before="$(git -C "$worktree" rev-parse "$branch")"
  log "$branch: merging pinned ${SOURCE_COMMIT:0:12}"
  if "$DRY_RUN"; then
    log "DRY-RUN: git -C $worktree merge --no-edit $SOURCE_COMMIT"
  else
    merge_log="$REPOSITORY/runtime/branch-sync-${branch//\//__}.log"
    if ! git -C "$worktree" merge --no-edit "$SOURCE_COMMIT" >"$merge_log" 2>&1; then
      git -C "$worktree" merge --abort >/dev/null 2>&1 || true
      git -C "$worktree" reset --hard "$before" >/dev/null 2>&1 || true
      warn "$branch: merge conflict or merge failure; branch restored to ${before:0:12}"
      sed -n '1,160p' "$merge_log" >&2 || true
      FAILED+=("$branch:conflict")
      "$temporary" && git -C "$REPOSITORY" worktree remove --force "$worktree" >/dev/null 2>&1 || true
      return 1
    fi
    UPDATED+=("$branch")
  fi

  push_branch "$branch" || FAILED+=("$branch:push")
  "$temporary" && git -C "$REPOSITORY" worktree remove --force "$worktree" >/dev/null 2>&1 || true
  return 0
}

cleanup() {
  local code=$?
  if [[ -n "$TMP_ROOT" && -d "$TMP_ROOT" ]]; then
    git -C "$REPOSITORY" worktree prune >/dev/null 2>&1 || true
    rm -rf "$TMP_ROOT"
  fi
  exit "$code"
}
trap cleanup EXIT

log "repository:    $REPOSITORY"
log "source branch: $SOURCE_BRANCH"
log "pinned commit: $SOURCE_COMMIT"
log "targets:       ${TARGETS[*]}"

# Push source first so every pushed target can be traced to a published integration
# revision. A push failure does not rewrite local branches, but is included in status.
push_branch "$SOURCE_BRANCH" || FAILED+=("$SOURCE_BRANCH:push")

pc_selected=false
lp_selected=false
regular_targets=()
for branch in "${TARGETS[@]}"; do
  case "$branch" in
    agent/pc-qwen3-worker) pc_selected=true ;;
    agent/laptop-qwen3-worker) lp_selected=true ;;
    *) regular_targets+=("$branch") ;;
  esac
done

for branch in "${regular_targets[@]}"; do
  merge_regular_branch "$branch" || true
done

if "$SYNC_WORKERS" && { "$pc_selected" || "$lp_selected"; }; then
  PC_BRANCH="agent/pc-qwen3-worker"
  LP_BRANCH="agent/laptop-qwen3-worker"
  if ! branch_exists "$PC_BRANCH" || ! branch_exists "$LP_BRANCH"; then
    warn "PC/LP branches are incomplete; worker synchronization skipped"
    FAILED+=("workers:missing-branch")
  elif branch_contains_source "$PC_BRANCH" && branch_contains_source "$LP_BRANCH"; then
    log "PC and LP already contain ${SOURCE_COMMIT:0:12}; no merge/restart required"
  else
    RING_WORKTREE="$(resolve_ring_runtime_worktree || true)"
    PC_WORKTREE="$(worktree_for_branch "$PC_BRANCH" || true)"
    LP_WORKTREE="$(worktree_for_branch "$LP_BRANCH" || true)"
    if [[ -z "$RING_WORKTREE" || -z "$PC_WORKTREE" || -z "$LP_WORKTREE" ]]; then
      warn "authoritative worktree resolution failed: Ring=${RING_WORKTREE:-MISSING} PC=${PC_WORKTREE:-MISSING} LP=${LP_WORKTREE:-MISSING}"
      FAILED+=("workers:missing-worktree")
    fi
    if [[ -n "$RING_WORKTREE" && -n "$PC_WORKTREE" && -n "$LP_WORKTREE" ]]; then
      log "worker runtime roots: Ring=$RING_WORKTREE PC=$PC_WORKTREE LP=$LP_WORKTREE"
      MERGER="$ROOT/scripts/merge-worker-branches-and-restart.sh"
      [[ -x "$MERGER" ]] || { warn "worker merger is unavailable: $MERGER"; FAILED+=("workers:missing-merger"); }
      if [[ -x "$MERGER" ]]; then
        command=("$MERGER" --source "$SOURCE_COMMIT" --ring "$RING_WORKTREE" --pc "$PC_WORKTREE" --lp "$LP_WORKTREE")
        "$DRY_RUN" && command+=(--dry-run)
        if "${command[@]}"; then
          "$DRY_RUN" || UPDATED+=("$PC_BRANCH" "$LP_BRANCH")
          push_branch "$PC_BRANCH" || FAILED+=("$PC_BRANCH:push")
          push_branch "$LP_BRANCH" || FAILED+=("$LP_BRANCH:push")
        else
          FAILED+=("workers:merge-or-restart")
        fi
      fi
    fi
  fi
fi

if "$START_GUARDIAN" && ! "$DRY_RUN"; then
  RING_WORKTREE="$(resolve_ring_runtime_worktree || true)"
  if [[ -n "$RING_WORKTREE" && -x "$ROOT/scripts/run-ring-system.sh" ]]; then
    if ! R4R_RING_WORKTREE="$RING_WORKTREE" "$ROOT/scripts/run-ring-system.sh" start; then
      FAILED+=("guardian:start")
    fi
  elif [[ -n "$RING_WORKTREE" && -x "$ROOT/scripts/ensure-r4r-workers.sh" ]]; then
    warn "persistent supervisor unavailable; performing one immediate guardian check"
    R4R_RING_WORKTREE="$RING_WORKTREE" \
      "$ROOT/scripts/ensure-r4r-workers.sh" --once || FAILED+=("guardian:check")
  else
    FAILED+=("guardian:missing")
  fi
fi

printf '\n[r4r-branch-sync] SUMMARY\n'
printf '  updated: %s\n' "${UPDATED[*]:-(none)}"
printf '  skipped: %s\n' "${SKIPPED[*]:-(none)}"
printf '  failed:  %s\n' "${FAILED[*]:-(none)}"

((${#FAILED[@]} == 0 && ${#SKIPPED[@]} == 0)) || exit 3
log "all selected branches synchronized; PC and LP guardian checked"
