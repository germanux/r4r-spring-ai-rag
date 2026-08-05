#!/usr/bin/env bash
set -Eeuo pipefail

# R4R worktree-aware hub synchronization with non-disruptive hot merges.
#
# Default invocation:
#   * fetch/prune origin;
#   * discover every non-detached worktree attached to the same Git common dir;
#   * centralize each source branch in agent/integration;
#   * after each source round, attempt to propagate the pinned hub commit to
#     every worktree, including active and dirty ones;
#   * before collection, publish only explicit Ring/PC/LP current-state files under
#     .opencode/current/{ring,PC,LP}; runtime remains ignored and task evidence stays
#     under .ring-agent/evidence with one writer per attempt;
#   * push the hub and every updated branch;
#   * preserve staged, unstaged and untracked work exactly as Git found it.
#
# A pending Git operation is skipped. A new merge is otherwise attempted and
# deferred only when Git rejects it. No unstage, stash, reset, force-push or
# automatic conflict resolution is performed.

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DEVELOPMENT_ROOT="${R4R_DEVELOPMENT_ROOT:-$HOME/Desarrollo}"
REPOSITORY="${R4R_REPOSITORY:-$ROOT}"
INTEGRATION_WORKTREE_HINT="${R4R_INTEGRATION_WORKTREE:-$DEVELOPMENT_ROOT/r4r-integration.git}"
RING_WORKTREE_HINT="${R4R_RING_WORKTREE:-$DEVELOPMENT_ROOT/r4r-ring-agent.git}"
PC_WORKTREE_HINT="${R4R_PC_WORKTREE:-$DEVELOPMENT_ROOT/r4r-pc-worker.git}"
LP_WORKTREE_HINT="${R4R_LP_WORKTREE:-$DEVELOPMENT_ROOT/r4r-lp-worker.git}"
HUB_BRANCH="${R4R_INTEGRATION_BRANCH:-agent/integration}"
REMOTE="${R4R_SYNC_REMOTE:-origin}"
PUSH_POLICY="strict"
FETCH=true
COLLECT=true
PROPAGATE=true
RUNTIME_POLICY="preserve"  # preserve | leave-stopped | require-idle
DRY_RUN=false
NOTIFY=true
OPEN_CONFLICT_DIR=true
MODAL_ALERT=true
ALL_LOCAL_BRANCHES=false
SOURCES=()
TARGETS=()
EXCLUDES=()
SOURCES_EXPLICIT=false
TARGETS_EXPLICIT=false
FAILED=()
SKIPPED=()
CENTRALIZED=()
PROPAGATED=()
ROUND_COMMITS=()
COMMON_DIR=""
INTEGRATION_WORKTREE=""
RING_WORKTREE=""
PC_WORKTREE=""
LP_WORKTREE=""
HUB_COMMIT=""
LOG_ROOT=""
ALERT_ROOT=""
BACKUP_RUN_ROOT=""
SYNC_STAMP="$(date -u +%Y%m%dT%H%M%SZ)"
LOCKS_RELEASED=false
TRANSACTION_STARTED=false
RESTORE_ATTEMPTED=false
RUNTIME_STOPPED=false
RING_WAS_ACTIVE=false
PC_WAS_ACTIVE=false
LP_WAS_ACTIVE=false

# branch -> recovery backup directory
declare -A WT_PATH=()
declare -A WT_BACKUP=()

usage() {
  cat <<'USAGE'
Usage: ./scripts/sync-agent-branches.sh [options]

Default: complete automatic hot-sync pass
  - discovers every linked non-detached worktree;
  - fetches and centralizes committed source refs in agent/integration;
  - attempts propagation to active and dirty worktrees;
  - preserves staged, unstaged and untracked work without stashing or unstaging;
  - preserves only explicit Ring/PC/LP current-state files in .opencode/current;
  - never copies runtime; durable task evidence remains in .ring-agent/evidence;
  - defers only when Git rejects a merge or another Git operation is pending.

Options:
  --hub BRANCH             Hub branch (default: agent/integration).
  --source BRANCH          Process only this source; repeatable.
  --target BRANCH          Propagate only to this target; repeatable.
  --exclude PATTERN        Exclude matching branch glob; repeatable.
  --all-local-branches     Include local branches without a linked worktree.
  --remote NAME            Fetch/push remote (default: origin).
  --fetch / --no-fetch     Enable/disable fetch (default: enabled).
  --push                   Strict push failures (default).
  --push-if-available      Keep local synchronization when push is unavailable.
  --no-push                Disable pushes.
  --collect-only           Centralize selected sources only.
  --propagate-only         Propagate current hub only.
  --leave-stopped          Deprecated compatibility option; agents are never stopped.
  --no-guardian            Deprecated compatibility option.
  --require-idle           Deprecated compatibility option.
  --no-notify              Disable desktop notifications.
  --open-conflict-dir      Open affected worktree automatically (default).
  --no-open-conflict-dir   Do not open a file manager.
  --no-modal-alert         Do not open Zenity/KDialog; keep terminal/notify-send.
  --dry-run                Print the plan without changing Git.
  -h, --help               Show this help.

Conflict policy:
  A collection conflict remains open in the integration worktree. A target merge
  is aborted and deferred after a real conflict, without changing its prior local
  index/worktree state. Other target branches continue.
USAGE
}

log()  { printf '[r4r-hot-sync] %s\n' "$*"; }
warn() { printf '\033[1;33m[r4r-hot-sync] WARNING: %s\033[0m\n' "$*" >&2; }
die()  { printf '\033[1;31m[r4r-hot-sync] ERROR: %s\033[0m\n' "$*" >&2; exit 2; }

while (($#)); do
  case "$1" in
    --hub) (($# >= 2)) || die "--hub requires a branch"; HUB_BRANCH="$2"; shift 2 ;;
    --source) (($# >= 2)) || die "--source requires a branch"; SOURCES+=("$2"); SOURCES_EXPLICIT=true; shift 2 ;;
    --target) (($# >= 2)) || die "--target requires a branch"; TARGETS+=("$2"); TARGETS_EXPLICIT=true; shift 2 ;;
    --exclude) (($# >= 2)) || die "--exclude requires a pattern"; EXCLUDES+=("$2"); shift 2 ;;
    --all-local-branches) ALL_LOCAL_BRANCHES=true; shift ;;
    --remote) (($# >= 2)) || die "--remote requires a name"; REMOTE="$2"; shift 2 ;;
    --fetch) FETCH=true; shift ;;
    --no-fetch) FETCH=false; shift ;;
    --push) PUSH_POLICY="strict"; shift ;;
    --push-if-available) PUSH_POLICY="best-effort"; shift ;;
    --no-push) PUSH_POLICY="off"; shift ;;
    --collect-only) COLLECT=true; PROPAGATE=false; shift ;;
    --propagate-only) COLLECT=false; PROPAGATE=true; shift ;;
    --leave-stopped|--no-guardian) RUNTIME_POLICY="leave-stopped"; shift ;;
    --require-idle) RUNTIME_POLICY="require-idle"; shift ;;
    --no-notify) NOTIFY=false; shift ;;
    --open-conflict-dir) OPEN_CONFLICT_DIR=true; shift ;;
    --no-open-conflict-dir) OPEN_CONFLICT_DIR=false; shift ;;
    --no-modal-alert) MODAL_ALERT=false; shift ;;
    --dry-run) DRY_RUN=true; shift ;;
    -h|--help) usage; exit 0 ;;
    *) die "unknown argument: $1" ;;
  esac
done

for command in git flock realpath sha256sum awk sed sort mktemp tar python3 stat; do
  command -v "$command" >/dev/null 2>&1 || die "required command unavailable: $command"
done

REPOSITORY="$(realpath -e "$REPOSITORY" 2>/dev/null)" || die "repository path does not exist"
git -C "$REPOSITORY" rev-parse --is-inside-work-tree >/dev/null 2>&1 \
  || die "not a Git worktree: $REPOSITORY"
COMMON_DIR="$(git -C "$REPOSITORY" rev-parse --git-common-dir)"
[[ "$COMMON_DIR" == /* ]] || COMMON_DIR="$REPOSITORY/$COMMON_DIR"
COMMON_DIR="$(realpath -e "$COMMON_DIR")"
LOG_ROOT="${R4R_BRANCH_SYNC_RUNTIME_ROOT:-$DEVELOPMENT_ROOT/.r4r-runtime/branch-sync}"
ALERT_ROOT="$LOG_ROOT/alerts"
BACKUP_RUN_ROOT="$LOG_ROOT/backups/$SYNC_STAMP"
ARTIFACT_COLLECTOR="${R4R_ARTIFACT_COLLECTOR:-$REPOSITORY/scripts/collect-agent-artifacts.py}"
mkdir -p "$LOG_ROOT/conflicts" "$ALERT_ROOT" "$BACKUP_RUN_ROOT"

# One scheduler lock plus one shared Git transaction lock used by workers and Drive.
SYNC_LOCK="${R4R_BRANCH_SYNC_LOCK:-$DEVELOPMENT_ROOT/.r4r-runtime/branch-sync.lock}"
GIT_LOCK="${R4R_GIT_LOCK:-$DEVELOPMENT_ROOT/.r4r-runtime/git.lock}"
mkdir -p "$(dirname "$SYNC_LOCK")" "$(dirname "$GIT_LOCK")"
exec 9>"$SYNC_LOCK"
flock -n 9 || { log "another branch synchronization is already running"; exit 0; }
exec 8>"$GIT_LOCK"
flock -w 60 8 || { warn "shared Git lock remained busy; retrying next pass"; exit 0; }

release_locks() {
  "$LOCKS_RELEASED" && return 0
  flock -u 8 2>/dev/null || true
  flock -u 9 2>/dev/null || true
  exec 8>&- 9>&-
  LOCKS_RELEASED=true
}

sanitize() { printf '%s' "$1" | tr -cs 'A-Za-z0-9._-' '_'; }

branch_is_excluded() {
  local branch="$1" pattern
  [[ "$branch" == "$HUB_BRANCH" ]] && return 0
  for pattern in "${EXCLUDES[@]}"; do
    # shellcheck disable=SC2053
    [[ "$branch" == $pattern ]] && return 0
  done
  return 1
}

copy_path_to_clipboard() {
  local path="$1"
  if command -v wl-copy >/dev/null 2>&1; then
    printf '%s' "$path" | wl-copy >/dev/null 2>&1 || true
  elif command -v xclip >/dev/null 2>&1; then
    printf '%s' "$path" | xclip -selection clipboard >/dev/null 2>&1 || true
  elif command -v xsel >/dev/null 2>&1; then
    printf '%s' "$path" | xsel --clipboard --input >/dev/null 2>&1 || true
  fi
}

alert_once() {
  local key="$1" title="$2" body="$3" path="${4:-}" fingerprint_file fingerprint old="" notification_body
  printf -v body '%b' "$body"
  notification_body="$body"
  [[ -z "$path" ]] || notification_body+=$'\n\nRuta: '"$path"
  fingerprint_file="$ALERT_ROOT/$(sanitize "$key").sha256"
  fingerprint="$(printf '%s\n%s\n%s\n' "$title" "$body" "$path" | sha256sum | awk '{print $1}')"
  [[ -f "$fingerprint_file" ]] && old="$(cat "$fingerprint_file" 2>/dev/null || true)"

  printf '\a\033[1;31m%s\033[0m\n%s\n' "$title" "$body" >&2
  [[ -z "$path" ]] || printf 'Ruta: %s\n' "$path" >&2
  [[ "$fingerprint" != "$old" ]] || return 0
  printf '%s\n' "$fingerprint" >"$fingerprint_file"
  [[ -z "$path" ]] || copy_path_to_clipboard "$path"
  "$NOTIFY" || return 0

  if command -v notify-send >/dev/null 2>&1; then
    notify-send --urgency=critical --expire-time=0 --app-name='R4R Git Sync' \
      "$title" "$notification_body" >/dev/null 2>&1 || true
  fi
  if "$MODAL_ALERT"; then
    if command -v kdialog >/dev/null 2>&1; then
      nohup kdialog --title 'R4R Git Sync' --error \
        "$(printf '%s%s' "$body" "${path:+$'\n\n'Ruta copiada al portapapeles:$'\n'$path}")" \
        >/dev/null 2>&1 8>&- 9>&- &
    elif command -v zenity >/dev/null 2>&1; then
      nohup zenity --warning --title='R4R Git Sync' --width=760 --timeout=180 \
        --text="$(printf '%s%s' "$body" "${path:+$'\n\n'Ruta copiada al portapapeles:$'\n'$path}")" \
        >/dev/null 2>&1 8>&- 9>&- &
    fi
  fi
  if "$OPEN_CONFLICT_DIR" && [[ -n "$path" && -d "$path" ]] \
      && command -v xdg-open >/dev/null 2>&1; then
    nohup xdg-open "$path" >/dev/null 2>&1 8>&- 9>&- &
  fi
}

clear_alert() { rm -f "$ALERT_ROOT/$(sanitize "$1").sha256"; }

worktree_for_branch() {
  local target="$1" current_path="" line
  while IFS= read -r line; do
    case "$line" in
      'worktree '*) current_path="${line#worktree }" ;;
      'branch refs/heads/'*)
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

resolve_branch_worktree() {
  local branch="$1" hint="$2" candidate current
  for candidate in "$hint" "$(worktree_for_branch "$branch" || true)"; do
    [[ -n "$candidate" ]] || continue
    if valid_common_worktree "$candidate"; then
      current="$(git -C "$candidate" branch --show-current 2>/dev/null || true)"
      [[ "$current" == "$branch" ]] && { realpath -e "$candidate"; return 0; }
    fi
  done
  return 1
}

branch_exists() { git -C "$REPOSITORY" show-ref --verify --quiet "refs/heads/$1"; }
remote_ref_exists() { git -C "$REPOSITORY" show-ref --verify --quiet "refs/remotes/$REMOTE/$1"; }
remote_exists() { git -C "$REPOSITORY" remote get-url "$REMOTE" >/dev/null 2>&1; }

merge_in_progress() {
  local worktree="$1" merge_head
  merge_head="$(git -C "$worktree" rev-parse --git-path MERGE_HEAD)"
  [[ "$merge_head" == /* ]] || merge_head="$worktree/$merge_head"
  [[ -f "$merge_head" ]]
}
unmerged_paths() { git -C "$1" diff --name-only --diff-filter=U | sed '/^$/d'; }

all_worktree_records() {
  local current_path="" line branch
  while IFS= read -r line; do
    case "$line" in
      'worktree '*) current_path="${line#worktree }" ;;
      'branch refs/heads/'*)
        branch="${line#branch refs/heads/}"
        printf '%s\t%s\n' "$branch" "$current_path"
        ;;
    esac
  done < <(git -C "$REPOSITORY" worktree list --porcelain)
}

discover_subscribed_worktree_branches() {
  local branch path
  while IFS=$'\t' read -r branch path; do
    branch_is_excluded "$branch" || printf '%s\n' "$branch"
  done < <(all_worktree_records)
}

select_all_local_branches() {
  local branch
  while IFS= read -r branch; do
    branch_is_excluded "$branch" || printf '%s\n' "$branch"
  done < <(git -C "$REPOSITORY" for-each-ref --format='%(refname:short)' refs/heads)
}

write_conflict_report() {
  local mode="$1" branch="$2" worktree="$3" log_file="$4" report
  report="$LOG_ROOT/conflicts/${SYNC_STAMP}-$(sanitize "$mode-$branch").txt"
  {
    echo 'R4R HOT SYNC CONFLICT'
    echo "Generated: $(date --iso-8601=seconds)"
    echo "Mode: $mode"
    echo "Hub: $HUB_BRANCH"
    echo "Branch: $branch"
    echo "Worktree: $worktree"
    echo "Backup: ${WT_BACKUP[$branch]:-(none)}"
    echo
    echo 'Unmerged paths:'
    unmerged_paths "$worktree" || true
    echo
    echo 'Git status:'
    visible_status "$worktree" || true
    echo
    echo 'Operation output:'
    [[ -f "$log_file" ]] && sed -n '1,260p' "$log_file" || true
    echo
    echo 'The target merge was aborted. Retry after the overlapping local change is committed.'
  } >"$report"
  printf '%s\n' "$report"
}

push_ref() {
  local branch="$1" output
  [[ "$PUSH_POLICY" != off ]] || return 0
  if ! remote_exists; then
    [[ "$PUSH_POLICY" == best-effort ]] && { warn "remote $REMOTE unavailable"; return 0; }
    alert_once "push-$branch" 'R4R: fallo al publicar rama' \
      "No existe o no es accesible el remoto '$REMOTE' para $branch." "$REPOSITORY"
    return 1
  fi
  if "$DRY_RUN"; then
    log "DRY-RUN: git push $REMOTE refs/heads/$branch:refs/heads/$branch"
    return 0
  fi
  if output="$(GIT_TERMINAL_PROMPT=0 git -C "$REPOSITORY" push "$REMOTE" \
      "refs/heads/$branch:refs/heads/$branch" 2>&1)"; then
    [[ -z "$output" ]] || printf '%s\n' "$output"
    clear_alert "push-$branch"
    return 0
  fi
  if [[ "$PUSH_POLICY" == best-effort ]]; then
    warn "$branch: push unavailable; local synchronization retained"
    [[ -z "$output" ]] || printf '%s\n' "$output" >&2
    return 0
  fi
  alert_once "push-$branch" 'R4R: fallo al publicar rama' \
    "git push falló para $branch.\n\n$(printf '%s\n' "$output" | sed -n '1,30p')" "$REPOSITORY"
  return 1
}

active_process_snapshot() {
  python3 - "$RING_WORKTREE" "$PC_WORKTREE" "$LP_WORKTREE" <<'PY_PROCESSES'
from pathlib import Path
import os
import sys

ring, pc, lp = sys.argv[1:4]
roots = {"RING": ring, "PC": pc, "LP": lp}

def value_after(args, flag):
    try:
        return args[args.index(flag) + 1]
    except (ValueError, IndexError):
        return ""

for entry in Path('/proc').iterdir():
    if not entry.name.isdigit() or int(entry.name) == os.getpid():
        continue
    try:
        raw = (entry / 'cmdline').read_bytes()
    except OSError:
        continue
    args = [part.decode('utf-8', 'replace') for part in raw.split(b'\0') if part]
    if not args:
        continue
    names = {Path(arg).name for arg in args if '/' in arg or arg}
    roles = set()
    if 'run-ring-system.py' in names or 'run-ring-agent.py' in names:
        roles.add('RING')
    if 'run-worker-streamed.py' in names:
        if 'PC' in args:
            roles.add('PC')
        if 'LP' in args:
            roles.add('LP')
    if '-m' in args:
        try:
            module = args[args.index('-m') + 1]
        except IndexError:
            module = ''
        if module == 'r4r_codex_agent.cli':
            repo = value_after(args, '--repo')
            if repo == pc:
                roles.add('PC')
            if repo == lp:
                roles.add('LP')
    executable = Path(args[0]).name
    if executable in {'opencode', 'opencode.exe'} and 'run' in args:
        directory = value_after(args, '--dir')
        if directory == ring:
            roles.add('RING')
        if directory == pc:
            roles.add('PC')
        if directory == lp:
            roles.add('LP')
    for role in sorted(roles):
        print(f"{role}\t{entry.name}\t{' '.join(args)}")
PY_PROCESSES
}

worktree_is_active() {
  local path="$1"
  active_process_snapshot | grep -Fq -- "$path"
}

snapshot_worktree_state() {
  local branch="$1" path="$2" destination index_path
  [[ -n "$(visible_status "$path")" ]] || return 0
  destination="$BACKUP_RUN_ROOT/state-$(sanitize "$branch")"
  mkdir -p "$destination"
  visible_status "$path" >"$destination/status.txt"
  git -C "$path" diff --binary -- . ':(exclude)runtime/**' >"$destination/worktree.patch"
  git -C "$path" diff --cached --binary -- . ':(exclude)runtime/**' >"$destination/index.patch"
  git -C "$path" ls-files --stage -z -- . ':(exclude)runtime/**' >"$destination/index.entries"
  git -C "$path" ls-files --others --exclude-standard -z -- . ':(exclude)runtime/**' >"$destination/untracked.list"
  if [[ -s "$destination/untracked.list" ]]; then
    tar -C "$path" --null -T "$destination/untracked.list" -czf "$destination/untracked.tar.gz"
  fi
  index_path="$(git -C "$path" rev-parse --git-path index)"
  [[ "$index_path" == /* ]] || index_path="$path/$index_path"
  [[ -f "$index_path" ]] && sha256sum "$index_path" >"$destination/index.sha256"
  printf '%s\n' \
    "branch=$branch" \
    "worktree=$path" \
    "head=$(git -C "$path" rev-parse HEAD)" \
    "active=$(worktree_is_active "$path" && echo true || echo false)" \
    >"$destination/manifest.txt"
  WT_BACKUP[$branch]="$destination"
  log "$branch: preserved dirty-state evidence=$destination"
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

visible_status() {
  git -C "$1" status --porcelain=v1 --untracked-files=all -- . ':(exclude)runtime/**'
}

collect_agent_artifacts() {
  local path="$1" agent="$2" worker="$3" branch
  [[ -n "$path" && -d "$path" ]] || { warn "$agent: worktree unavailable; artifact collection skipped"; return 0; }
  [[ -f "$ARTIFACT_COLLECTOR" ]] || die "artifact collector not found: $ARTIFACT_COLLECTOR"
  if merge_in_progress "$path" || [[ -n "$(unmerged_paths "$path" || true)" ]]; then
    warn "$agent: Git operation pending; artifact collection deferred"
    return 0
  fi
  branch="$(git -C "$path" branch --show-current)"
  if "$DRY_RUN"; then
    log "DRY-RUN: curate explicit $agent current-state files from $branch"
    return 0
  fi
  python3 "$ARTIFACT_COLLECTOR" \
    --repo "$path" \
    --agent "$agent" \
    --worker-id "$worker" \
    --commit
}

collect_ref_into_hub() {
  local incoming="$1" label="$2" merge_log report paths body
  if git -C "$INTEGRATION_WORKTREE" merge-base --is-ancestor "$incoming" HEAD; then
    log "$label: already centralized"
    return 0
  fi
  merge_log="$BACKUP_RUN_ROOT/collect-$(sanitize "$label").log"
  log "$label: merging into $HUB_BRANCH"
  if "$DRY_RUN"; then
    log "DRY-RUN: git -C $INTEGRATION_WORKTREE merge --no-edit $incoming"
    return 0
  fi
  if git -C "$INTEGRATION_WORKTREE" merge --no-edit "$incoming" >"$merge_log" 2>&1; then
    CENTRALIZED+=("$label")
    clear_alert "collect-$label"
    return 0
  fi
  if merge_in_progress "$INTEGRATION_WORKTREE" || [[ -n "$(unmerged_paths "$INTEGRATION_WORKTREE" || true)" ]]; then
    report="$(write_conflict_report collection "$label" "$INTEGRATION_WORKTREE" "$merge_log")"
    paths="$(unmerged_paths "$INTEGRATION_WORKTREE" || true)"
    body="Conflicto al integrar $label en $HUB_BRANCH.\nLa fusión queda abierta.\n\nFicheros:\n${paths:-Consulta git status.}\nInforme: $report"
    alert_once "collect-$label" 'R4R: conflicto al centralizar rama' "$body" "$INTEGRATION_WORKTREE"
    FAILED+=("$label:collection-conflict")
    return 4
  fi
  alert_once "collect-$label" 'R4R: fallo al centralizar rama' \
    "Falló la integración de $label.\n\n$(sed -n '1,50p' "$merge_log")" "$INTEGRATION_WORKTREE"
  FAILED+=("$label:collection-failed")
  return 1
}

prepare_target_worktree() {
  local branch="$1" path
  if path="$(worktree_for_branch "$branch" || true)"; then
    printf '%s\n' "$path"
    return 0
  fi
  path="$LOG_ROOT/temporary-worktrees/$(sanitize "$branch")"
  mkdir -p "$(dirname "$path")"
  [[ ! -e "$path" ]] || rm -rf "$path"
  if "$DRY_RUN"; then
    printf '%s\n' "$REPOSITORY"
  else
    git -C "$REPOSITORY" worktree add --quiet "$path" "$branch"
    printf '%s\n' "$path"
  fi
}

remove_temporary_worktree() {
  local path="$1"
  [[ "$path" == "$LOG_ROOT/temporary-worktrees/"* ]] || return 0
  merge_in_progress "$path" && return 0
  "$DRY_RUN" || git -C "$REPOSITORY" worktree remove --force "$path" >/dev/null 2>&1 || true
}

propagate_branch() {
  local branch="$1" path merge_log report body dirty active before_state after_state
  branch_exists "$branch" || { FAILED+=("$branch:missing"); return 1; }
  if git -C "$REPOSITORY" merge-base --is-ancestor "$HUB_COMMIT" "$branch"; then
    log "$branch: already contains ${HUB_COMMIT:0:12}"
    push_ref "$branch" || FAILED+=("$branch:push")
    return 0
  fi
  path="$(prepare_target_worktree "$branch")" || return 1
  if merge_in_progress "$path" || [[ -n "$(unmerged_paths "$path" || true)" ]]; then
    warn "$branch: skipped because a Git operation or conflict is pending"
    SKIPPED+=("$branch:conflict-pending")
    remove_temporary_worktree "$path"
    return 0
  fi
  dirty="$(visible_status "$path")"
  active=false
  worktree_is_active "$path" && active=true
  [[ -z "$dirty" ]] || snapshot_worktree_state "$branch" "$path"
  before_state="$(state_fingerprint "$path")"
  merge_log="$BACKUP_RUN_ROOT/propagate-$(sanitize "$branch").log"
  log "$branch: attempting hub ${HUB_COMMIT:0:12} (active=$active dirty=$([[ -n "$dirty" ]] && echo true || echo false))"
  if "$DRY_RUN"; then
    log "DRY-RUN: git -C $path merge --no-edit $HUB_COMMIT"
  elif git -C "$path" merge --no-edit "$HUB_COMMIT" >"$merge_log" 2>&1; then
    PROPAGATED+=("$branch")
    clear_alert "propagate-$branch"
  else
    if merge_in_progress "$path" || [[ -n "$(unmerged_paths "$path" || true)" ]]; then
      git -C "$path" merge --abort >>"$merge_log" 2>&1 || true
    fi
    after_state="$(state_fingerprint "$path")"
    report="$(write_conflict_report propagation "$branch" "$path" "$merge_log")"
    if [[ "$after_state" != "$before_state" ]]; then
      body="Git rechazó la propagación a $branch y el estado posterior no coincide con la huella previa.\nNo se aplicará ninguna reparación destructiva.\nInforme: $report\nCopia: ${WT_BACKUP[$branch]:-(worktree limpio)}"
      alert_once "propagate-$branch" 'R4R: revisar estado tras merge rechazado' "$body" "$path"
      FAILED+=("$branch:state-mismatch-after-abort")
      return 1
    fi
    log "$branch: merge deferred; Git rejected it and prior local state is unchanged"
    SKIPPED+=("$branch:merge-rejected")
    clear_alert "propagate-$branch"
    remove_temporary_worktree "$path"
    return 0
  fi
  push_ref "$branch" || FAILED+=("$branch:push")
  remove_temporary_worktree "$path"
}

propagate_hub_round() {
  local label="$1" branch code
  HUB_COMMIT="$(git -C "$INTEGRATION_WORKTREE" rev-parse HEAD)"
  ROUND_COMMITS+=("$label=${HUB_COMMIT:0:12}")
  push_ref "$HUB_BRANCH" || FAILED+=("$HUB_BRANCH:push")
  "$PROPAGATE" || return 0
  for branch in "${TARGETS[@]}"; do
    branch_is_excluded "$branch" && continue
    propagate_branch "$branch" || {
      code=$?
      ((code == 4)) && return 4
    }
  done
}

sync_is_needed() {
  local ref branch
  if "$COLLECT"; then
    if "$FETCH" && remote_ref_exists "$HUB_BRANCH" \
        && ! git -C "$REPOSITORY" merge-base --is-ancestor "$REMOTE/$HUB_BRANCH" "$HUB_BRANCH"; then
      return 0
    fi
    for branch in "${SOURCES[@]}"; do
      branch_is_excluded "$branch" && continue
      if branch_exists "$branch" \
          && ! git -C "$REPOSITORY" merge-base --is-ancestor "$branch" "$HUB_BRANCH"; then
        return 0
      fi
      if "$FETCH" && remote_ref_exists "$branch" \
          && ! git -C "$REPOSITORY" merge-base --is-ancestor "$REMOTE/$branch" "$HUB_BRANCH"; then
        return 0
      fi
    done
  fi
  if "$PROPAGATE"; then
    for branch in "${TARGETS[@]}"; do
      branch_is_excluded "$branch" && continue
      branch_exists "$branch" || continue
      if ! git -C "$REPOSITORY" merge-base --is-ancestor "$HUB_BRANCH" "$branch"; then
        return 0
      fi
    done
  fi
  return 1
}

on_exit() {
  local code=$?
  release_locks
  git -C "$REPOSITORY" worktree prune >/dev/null 2>&1 || true
  exit "$code"
}
trap on_exit EXIT

if "$FETCH"; then
  if remote_exists; then
    log "fetching/pruning $REMOTE"
    git -C "$REPOSITORY" fetch --prune "$REMOTE" || {
      [[ "$PUSH_POLICY" == best-effort ]] || FAILED+=("fetch:$REMOTE")
      warn "fetch failed; continuing with available refs"
    }
  else
    warn "remote $REMOTE unavailable; fetch skipped"
  fi
fi

git -C "$REPOSITORY" show-ref --verify --quiet "refs/heads/$HUB_BRANCH" \
  || die "local hub branch does not exist: $HUB_BRANCH"
INTEGRATION_WORKTREE="$(resolve_branch_worktree "$HUB_BRANCH" "$INTEGRATION_WORKTREE_HINT" || true)"
[[ -n "$INTEGRATION_WORKTREE" ]] || die "hub worktree not found for $HUB_BRANCH"
RING_WORKTREE="$(resolve_branch_worktree agent/ring-agent-worker "$RING_WORKTREE_HINT" || true)"
PC_WORKTREE="$(resolve_branch_worktree agent/pc-qwen3-worker "$PC_WORKTREE_HINT" || true)"
LP_WORKTREE="$(resolve_branch_worktree agent/laptop-qwen3-worker "$LP_WORKTREE_HINT" || true)"

mapfile -t SUBSCRIBED < <(discover_subscribed_worktree_branches | sort -u)
if "$ALL_LOCAL_BRANCHES"; then
  mapfile -t SUBSCRIBED < <(
    { printf '%s\n' "${SUBSCRIBED[@]}"; select_all_local_branches; } | awk 'NF && !seen[$0]++' | sort
  )
fi
! "$TARGETS_EXPLICIT" && TARGETS=("${SUBSCRIBED[@]}")
! "$SOURCES_EXPLICIT" && SOURCES=("${SUBSCRIBED[@]}")
mapfile -t TARGETS < <(printf '%s\n' "${TARGETS[@]}" | awk 'NF && !seen[$0]++')
mapfile -t SOURCES < <(printf '%s\n' "${SOURCES[@]}" | awk 'NF && !seen[$0]++')

log "repository:             $REPOSITORY"
log "Git common dir:         $COMMON_DIR"
log "integration worktree:   $INTEGRATION_WORKTREE"
log "hub branch:             $HUB_BRANCH"
log "subscribed worktrees:   ${SUBSCRIBED[*]:-(none)}"
log "source sequence:        ${SOURCES[*]:-(none)}"
log "propagation targets:    ${TARGETS[*]:-(none)}"
log "runtime policy:         $RUNTIME_POLICY"
log "push policy:            $PUSH_POLICY"

# A conflict in the hub blocks collection. Conflicts in worker worktrees are
# isolated and retried later instead of blocking every other branch.
while IFS=$'\t' read -r branch path; do
  if merge_in_progress "$path" || [[ -n "$(unmerged_paths "$path" || true)" ]]; then
    if [[ "$branch" == "$HUB_BRANCH" ]]; then
      alert_once "pending-$branch" 'R4R: conflicto Git pendiente' \
        "$branch tiene una operación Git pendiente. Resuélvela o abórtala antes del siguiente ciclo." "$path"
      exit 4
    fi
    SKIPPED+=("$branch:conflict-pending")
  fi
done < <(all_worktree_records)

# Every propagation pass first publishes only the explicit current-state files.
# runtime is never copied; task evidence is already durable in .ring-agent/evidence.
collect_agent_artifacts "$RING_WORKTREE" ring RING
collect_agent_artifacts "$PC_WORKTREE" PC PC
collect_agent_artifacts "$LP_WORKTREE" LP LP

[[ -z "$(visible_status "$INTEGRATION_WORKTREE")" ]] \
  || die "integration worktree is dirty; refusing to mix synchronization changes"

# Avoid interrupting agents every three minutes when every branch already converged.
if ! sync_is_needed; then
  HUB_COMMIT="$(git -C "$INTEGRATION_WORKTREE" rev-parse HEAD)"
  log "all subscribed branches already converge on hub ${HUB_COMMIT:0:12}"
  push_ref "$HUB_BRANCH" || FAILED+=("$HUB_BRANCH:push")
  for branch in "${TARGETS[@]}"; do
    branch_exists "$branch" && push_ref "$branch" || true
  done
else
  rounds=0
  if "$COLLECT"; then
    if "$FETCH" && remote_ref_exists "$HUB_BRANCH"; then
      before="$(git -C "$INTEGRATION_WORKTREE" rev-parse HEAD)"
      collect_ref_into_hub "$REMOTE/$HUB_BRANCH" "$REMOTE/$HUB_BRANCH" || exit $?
      after="$(git -C "$INTEGRATION_WORKTREE" rev-parse HEAD)"
      if [[ "$after" != "$before" ]]; then
        ((rounds += 1))
        propagate_hub_round remote-hub || exit $?
      fi
    fi

    for source_branch in "${SOURCES[@]}"; do
      branch_is_excluded "$source_branch" && continue
      before="$(git -C "$INTEGRATION_WORKTREE" rev-parse HEAD)"
      found=false
      if branch_exists "$source_branch"; then
        found=true
        collect_ref_into_hub "$source_branch" "$source_branch" || exit $?
      fi
      if "$FETCH" && remote_ref_exists "$source_branch"; then
        found=true
        collect_ref_into_hub "$REMOTE/$source_branch" "$REMOTE/$source_branch" || exit $?
      fi
      [[ "$found" == true ]] || { FAILED+=("$source_branch:missing-source"); continue; }
      after="$(git -C "$INTEGRATION_WORKTREE" rev-parse HEAD)"
      if [[ "$after" != "$before" ]]; then
        ((rounds += 1))
        propagate_hub_round "$source_branch" || exit $?
      else
        log "$source_branch: no new hub commit"
      fi
    done
  fi

  HUB_COMMIT="$(git -C "$INTEGRATION_WORKTREE" rev-parse HEAD)"
  push_ref "$HUB_BRANCH" || FAILED+=("$HUB_BRANCH:push")
  if "$PROPAGATE"; then
    for branch in "${TARGETS[@]}"; do
      branch_is_excluded "$branch" && continue
      propagate_branch "$branch" || exit $?
    done
  fi
fi

mapfile -t CENTRALIZED < <(printf '%s\n' "${CENTRALIZED[@]}" | awk 'NF && !seen[$0]++')
mapfile -t PROPAGATED < <(printf '%s\n' "${PROPAGATED[@]}" | awk 'NF && !seen[$0]++')
mapfile -t FAILED < <(printf '%s\n' "${FAILED[@]}" | awk 'NF && !seen[$0]++')
mapfile -t SKIPPED < <(printf '%s\n' "${SKIPPED[@]}" | awk 'NF && !seen[$0]++')

printf '\n[r4r-hot-sync] SUMMARY\n'
printf '  hub:                    %s\n' "$HUB_BRANCH"
printf '  final hub commit:       %s\n' "${HUB_COMMIT:-$(git -C "$INTEGRATION_WORKTREE" rev-parse HEAD)}"
printf '  centralized refs:       %s\n' "${CENTRALIZED[*]:-(none)}"
printf '  propagated branches:    %s\n' "${PROPAGATED[*]:-(none)}"
printf '  skipped worktrees:      %s\n' "${SKIPPED[*]:-(none)}"
printf '  backup root:            %s\n' "$BACKUP_RUN_ROOT"
printf '  failed:                 %s\n' "${FAILED[*]:-(none)}"

((${#FAILED[@]} == 0)) || exit 3
log 'complete hot-sync pass finished'
