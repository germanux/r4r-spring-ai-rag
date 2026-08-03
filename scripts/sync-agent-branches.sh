#!/usr/bin/env bash
set -Eeuo pipefail

# R4R worktree-aware hub synchronization with coordinated hot merges.
#
# Default invocation:
#   * fetch/prune origin;
#   * discover every non-detached worktree attached to the same Git common dir;
#   * centralize each source branch in agent/integration;
#   * after each source round, propagate the pinned hub commit to every worktree;
#   * push the hub and every updated branch;
#   * when a Git update is actually required, stop the active R4R stack once,
#     preserve every dirty worktree once, perform all merges, restore the dirty
#     states once, then restart only the runtime that was active before the pass.
#
# Dirty worktrees are never silently skipped. A merge or stash-reapply conflict is
# left open in the exact worktree, backed up, and reported through terminal plus
# desktop notification. No force-push, reset of user work, or automatic conflict
# resolution is performed.

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

# branch -> path / stash OID / backup directory
declare -A WT_PATH=()
declare -A WT_STASH=()
declare -A WT_BACKUP=()
declare -A WT_WAS_DIRTY=()
PRESERVED_BRANCHES=()

usage() {
  cat <<'USAGE'
Usage: ./scripts/sync-agent-branches.sh [options]

Default: complete automatic hot-sync pass
  - discovers every linked non-detached worktree;
  - fetches, centralizes each source in agent/integration, propagates after each
    source round, pushes all updated refs;
  - when an update is needed, temporarily stops active R4R processes, backs up and
    stashes dirty states, merges, restores those states, and resumes the prior stack.

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
  --leave-stopped          Stop active agents for the merge and do not restart them.
  --no-guardian            Compatibility alias for --leave-stopped.
  --require-idle           Refuse to run when an R4R agent is active.
  --no-notify              Disable desktop notifications.
  --open-conflict-dir      Open affected worktree automatically (default).
  --no-open-conflict-dir   Do not open a file manager.
  --no-modal-alert         Do not open Zenity/KDialog; keep terminal/notify-send.
  --dry-run                Print the plan without changing Git or processes.
  -h, --help               Show this help.

Conflict policy:
  A collection conflict remains open in the integration worktree. A propagation
  conflict remains open in the exact target worktree. A stash-reapply conflict also
  remains open there, while its original stash and external backup are retained.
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

for command in git flock realpath sha256sum awk sed sort mktemp tar python3; do
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
mkdir -p "$LOG_ROOT/conflicts" "$ALERT_ROOT" "$BACKUP_RUN_ROOT"

# One canonical lock across systemd, cron and manual runs in any worktree.
exec 9>/tmp/r4r-agent-branch-sync.lock
flock -n 9 || { log "another branch synchronization is already running"; exit 0; }
# Serialize with the Google Drive import/autocommit process.
exec 8>/tmp/r4r-drive-import.lock
flock -w 30 8 || die "Google Drive import lock remained busy for 30 seconds"

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
    echo "Preserved stash: ${WT_STASH[$branch]:-(none)}"
    echo
    echo 'Unmerged paths:'
    unmerged_paths "$worktree" || true
    echo
    echo 'Git status:'
    git -C "$worktree" status --short --untracked-files=all || true
    echo
    echo 'Operation output:'
    [[ -f "$log_file" ]] && sed -n '1,260p' "$log_file" || true
    echo
    echo 'Resolve in place:'
    echo "  cd '$worktree'"
    echo '  git status'
    echo '  # edit files, git add <resolved>, git commit when the MERGE conflict is resolved'
    echo
    echo 'Abort a MERGE conflict:'
    echo "  git -C '$worktree' merge --abort"
    echo
    echo 'The preserved stash is intentionally retained until restoration succeeds.'
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

capture_runtime_state() {
  local rows
  rows="$(active_process_snapshot)"
  [[ -z "$rows" ]] && return 0
  grep -q $'^RING\t' <<<"$rows" && RING_WAS_ACTIVE=true || true
  grep -q $'^PC\t' <<<"$rows" && PC_WAS_ACTIVE=true || true
  grep -q $'^LP\t' <<<"$rows" && LP_WAS_ACTIVE=true || true
  printf '%s\n' "$rows" >"$BACKUP_RUN_ROOT/processes-before.txt"
}

any_runtime_active() { "$RING_WAS_ACTIVE" || "$PC_WAS_ACTIVE" || "$LP_WAS_ACTIVE"; }

stop_active_runtime() {
  any_runtime_active || return 0
  [[ "$RUNTIME_POLICY" != require-idle ]] || {
    alert_once runtime-active 'R4R: sincronización requiere agentes parados' \
      "Hay agentes activos y se pidió --require-idle.\n\n$(active_process_snapshot | sed -n '1,30p')" \
      "$DEVELOPMENT_ROOT"
    return 4
  }
  "$DRY_RUN" && { log 'DRY-RUN: stop active R4R runtime with --keep-models'; return 0; }
  local stopper=""
  for stopper in \
    "$RING_WORKTREE/scripts/stop-all-r4r-agents.sh" \
    "$INTEGRATION_WORKTREE/scripts/stop-all-r4r-agents.sh" \
    "$ROOT/scripts/stop-all-r4r-agents.sh"; do
    [[ -x "$stopper" ]] && break
    stopper=""
  done
  [[ -n "$stopper" ]] || die 'stop-all-r4r-agents.sh is required for an active hot sync'
  log "stopping active R4R runtime before the Git critical section"
  "$stopper" --keep-models
  sleep 1
  if [[ -n "$(active_process_snapshot)" ]]; then
    alert_once runtime-stop-failed 'R4R: no se pudo detener el runtime' \
      "Quedan procesos activos; no se tocará ningún worktree.\n\n$(active_process_snapshot | sed -n '1,30p')" \
      "$RING_WORKTREE"
    return 4
  fi
  RUNTIME_STOPPED=true
  clear_alert runtime-active
  clear_alert runtime-stop-failed
}

backup_worktree() {
  local branch="$1" path="$2" backup="$BACKUP_RUN_ROOT/$(sanitize "$branch")"
  mkdir -p "$backup"
  WT_BACKUP["$branch"]="$backup"
  git -C "$path" rev-parse HEAD >"$backup/head.txt"
  git -C "$path" branch --show-current >"$backup/branch.txt"
  git -C "$path" status --short --untracked-files=all >"$backup/status-before.txt"
  git -C "$path" diff --binary --full-index --no-ext-diff >"$backup/worktree.patch"
  git -C "$path" diff --cached --binary --full-index --no-ext-diff >"$backup/index.patch"
  git -C "$path" ls-files --others --exclude-standard >"$backup/untracked.txt"
  git -C "$path" ls-files -m -d -o --exclude-standard -z \
    | tar -C "$path" --null --no-recursion --ignore-failed-read -czf "$backup/files.tgz" -T - \
      2>"$backup/tar-warnings.txt" || true
}

preserve_dirty_worktrees() {
  local branch path dirty before_oid after_oid
  TRANSACTION_STARTED=true
  while IFS=$'\t' read -r branch path; do
    [[ -n "$branch" && -n "$path" ]] || continue
    WT_PATH["$branch"]="$path"
    if merge_in_progress "$path" || [[ -n "$(unmerged_paths "$path" || true)" ]]; then
      alert_once "pending-$branch" 'R4R: conflicto Git pendiente' \
        "$branch ya tiene una operación Git pendiente. Resuélvela o abórtala antes de sincronizar." "$path"
      return 4
    fi
    dirty="$(git -C "$path" status --porcelain=v1 --untracked-files=all)"
    [[ -n "$dirty" ]] || continue
    WT_WAS_DIRTY["$branch"]=true
    PRESERVED_BRANCHES+=("$branch")
    backup_worktree "$branch" "$path"
    log "$branch: preserving dirty state in ${WT_BACKUP[$branch]}"
    if "$DRY_RUN"; then
      WT_STASH["$branch"]="DRY-RUN"
      continue
    fi
    before_oid="$(git -C "$path" rev-parse -q --verify refs/stash 2>/dev/null || true)"
    git -C "$path" stash push --include-untracked \
      --message "r4r-hot-sync:$SYNC_STAMP:$branch" >/dev/null
    after_oid="$(git -C "$path" rev-parse -q --verify refs/stash 2>/dev/null || true)"
    [[ -n "$after_oid" && "$after_oid" != "$before_oid" ]] \
      || die "$branch: git stash did not create a preservation commit"
    WT_STASH["$branch"]="$after_oid"
    if [[ -n "$(git -C "$path" status --porcelain=v1 --untracked-files=all)" ]]; then
      alert_once "stash-$branch" 'R4R: no se pudo apartar el estado local' \
        "$branch sigue sucia después de git stash. Backup: ${WT_BACKUP[$branch]}" "$path"
      return 4
    fi
  done < <(all_worktree_records)
}

stash_ref_for_oid() {
  local oid="$1" ref current
  while IFS=' ' read -r ref current; do
    [[ "$current" == "$oid" ]] && { printf '%s\n' "$ref"; return 0; }
  done < <(git -C "$REPOSITORY" stash list --format='%gd %H')
  return 1
}

restore_preserved_worktrees() {
  local branch path oid ref report log_file body code=0
  "$RESTORE_ATTEMPTED" && return 0
  RESTORE_ATTEMPTED=true
  for branch in "${PRESERVED_BRANCHES[@]}"; do
    path="${WT_PATH[$branch]}"
    oid="${WT_STASH[$branch]}"
    [[ "$oid" != DRY-RUN ]] || { log "DRY-RUN: restore preserved state for $branch"; continue; }
    if merge_in_progress "$path" || [[ -n "$(unmerged_paths "$path" || true)" ]]; then
      warn "$branch: preserved stash remains unapplied because the worktree has a conflict"
      code=4
      continue
    fi
    log_file="${WT_BACKUP[$branch]}/stash-apply.log"
    log "$branch: restoring preserved dirty state"
    if git -C "$path" stash apply --index "$oid" >"$log_file" 2>&1; then
      git -C "$path" status --short --untracked-files=all >"${WT_BACKUP[$branch]}/status-after.txt"
      if ref="$(stash_ref_for_oid "$oid" || true)"; then
        git -C "$REPOSITORY" stash drop "$ref" >/dev/null
      fi
      clear_alert "stash-apply-$branch"
    else
      report="$(write_conflict_report stash-reapply "$branch" "$path" "$log_file")"
      body="Conflicto al reaplicar el trabajo local de $branch.\nEl conflicto queda abierto y el stash original se conserva.\n\nStash: $oid\nBackup: ${WT_BACKUP[$branch]}\nInforme: $report"
      alert_once "stash-apply-$branch" 'R4R: conflicto al restaurar trabajo local' "$body" "$path"
      FAILED+=("$branch:stash-reapply-conflict")
      code=4
    fi
  done
  return "$code"
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
  local branch="$1" path merge_log report paths body
  branch_exists "$branch" || { FAILED+=("$branch:missing"); return 1; }
  if git -C "$REPOSITORY" merge-base --is-ancestor "$HUB_COMMIT" "$branch"; then
    log "$branch: already contains ${HUB_COMMIT:0:12}"
    push_ref "$branch" || FAILED+=("$branch:push")
    return 0
  fi
  path="$(prepare_target_worktree "$branch")" || return 1
  merge_log="$BACKUP_RUN_ROOT/propagate-$(sanitize "$branch").log"
  log "$branch: merging hub ${HUB_COMMIT:0:12}"
  if "$DRY_RUN"; then
    log "DRY-RUN: git -C $path merge --no-edit $HUB_COMMIT"
  elif git -C "$path" merge --no-edit "$HUB_COMMIT" >"$merge_log" 2>&1; then
    PROPAGATED+=("$branch")
    clear_alert "propagate-$branch"
  elif merge_in_progress "$path" || [[ -n "$(unmerged_paths "$path" || true)" ]]; then
    report="$(write_conflict_report propagation "$branch" "$path" "$merge_log")"
    paths="$(unmerged_paths "$path" || true)"
    body="Conflicto al propagar $HUB_BRANCH a $branch.\nQueda abierto en el worktree exacto.\n\nFicheros:\n${paths:-Consulta git status.}\nInforme: $report"
    alert_once "propagate-$branch" 'R4R: conflicto al propagar integration' "$body" "$path"
    FAILED+=("$branch:propagation-conflict")
    return 4
  else
    alert_once "propagate-$branch" 'R4R: fallo al propagar integration' \
      "Falló la propagación a $branch.\n\n$(sed -n '1,50p' "$merge_log")" "$path"
    FAILED+=("$branch:propagation-failed")
    return 1
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

restart_preserved_runtime() {
  any_runtime_active || return 0
  [[ "$RUNTIME_POLICY" == preserve ]] || return 0
  "$DRY_RUN" && { log 'DRY-RUN: restore previously active R4R runtime'; return 0; }
  local starter wrapper python worker log_path pid heartbeat deadline
  if "$RING_WAS_ACTIVE"; then
    starter="$INTEGRATION_WORKTREE/scripts/run-ring-system.sh"
    [[ -x "$starter" ]] || starter="$RING_WORKTREE/scripts/run-ring-system.sh"
    [[ -x "$starter" ]] || { warn 'run-ring-system.sh missing; runtime remains stopped'; return 1; }
    log 'restarting the previously active managed R4R stack'
    R4R_RING_WORKTREE="$RING_WORKTREE" "$starter" start
    return $?
  fi

  # Individual workers may occasionally run without the Ring supervisor. Restore only
  # those that were active, using the same authoritative streamed wrapper.
  wrapper="$RING_WORKTREE/py-ring-agent/run-worker-streamed.py"
  [[ -f "$wrapper" ]] || { warn "worker wrapper missing: $wrapper"; return 1; }
  for worker in PC LP; do
    if [[ "$worker" == PC ]]; then
      "$PC_WAS_ACTIVE" || continue
    else
      "$LP_WAS_ACTIVE" || continue
    fi
    log_path="$RING_WORKTREE/runtime/ring-agent/bootstrap/${SYNC_STAMP}-${worker}-hot-sync.log"
    mkdir -p "$(dirname "$log_path")"
    pid="$(python3 - "$wrapper" "$worker" "$log_path" "$RING_WORKTREE" "$PC_WORKTREE" "$LP_WORKTREE" <<'PY'
from pathlib import Path
import os, subprocess, sys
wrapper, worker, log_path, ring, pc, lp = sys.argv[1:7]
Path(log_path).parent.mkdir(parents=True, exist_ok=True)
log = open(log_path, 'ab', buffering=0)
env = {**os.environ, 'PYTHONUNBUFFERED': '1', 'R4R_RING_WORKTREE': ring,
       'R4R_PC_WORKTREE': pc, 'R4R_LP_WORKTREE': lp}
p = subprocess.Popen([sys.executable, wrapper, worker], cwd=ring,
                     stdin=subprocess.DEVNULL, stdout=log, stderr=subprocess.STDOUT,
                     start_new_session=True, env=env, close_fds=True)
print(p.pid)
PY
)"
    sleep 2
    kill -0 "$pid" 2>/dev/null || { warn "$worker wrapper failed; see $log_path"; return 1; }
    log "$worker wrapper restarted pid=$pid log=$log_path"
  done
}

runtime_restart_safe() {
  local branch path oid
  while IFS=$'\t' read -r branch path; do
    merge_in_progress "$path" && return 1
    [[ -z "$(unmerged_paths "$path" || true)" ]] || return 1
  done < <(all_worktree_records)
  for branch in "${PRESERVED_BRANCHES[@]}"; do
    oid="${WT_STASH[$branch]:-}"
    [[ -z "$oid" || "$oid" == DRY-RUN ]] && continue
    # A remaining stash means restoration did not complete and must stay available.
    stash_ref_for_oid "$oid" >/dev/null 2>&1 && return 1
  done
  return 0
}

on_exit() {
  local code=$?
  if "$TRANSACTION_STARTED" && ! "$RESTORE_ATTEMPTED"; then
    restore_preserved_worktrees || code=$?
  fi
  release_locks
  if "$RUNTIME_STOPPED"; then
    if [[ "$RUNTIME_POLICY" == preserve ]] && runtime_restart_safe; then
      restart_preserved_runtime || code=5
    elif [[ "$RUNTIME_POLICY" == preserve ]]; then
      warn 'runtime remains stopped because a merge or preserved-state conflict is still open'
    else
      log 'runtime intentionally left stopped by policy'
    fi
  fi
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

# Existing conflicts are never hidden by a new synchronization pass.
while IFS=$'\t' read -r branch path; do
  if merge_in_progress "$path" || [[ -n "$(unmerged_paths "$path" || true)" ]]; then
    alert_once "pending-$branch" 'R4R: conflicto Git pendiente' \
      "$branch tiene una operación Git pendiente. Resuélvela o abórtala antes del siguiente ciclo." "$path"
    exit 4
  fi
done < <(all_worktree_records)

# Avoid interrupting agents every three minutes when every branch already converged.
if ! sync_is_needed; then
  HUB_COMMIT="$(git -C "$INTEGRATION_WORKTREE" rev-parse HEAD)"
  log "all subscribed branches already converge on hub ${HUB_COMMIT:0:12}"
  push_ref "$HUB_BRANCH" || FAILED+=("$HUB_BRANCH:push")
  for branch in "${TARGETS[@]}"; do
    branch_exists "$branch" && push_ref "$branch" || true
  done
else
  capture_runtime_state
  stop_active_runtime || exit $?
  preserve_dirty_worktrees || exit $?

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

  restore_preserved_worktrees || exit $?
fi

mapfile -t CENTRALIZED < <(printf '%s\n' "${CENTRALIZED[@]}" | awk 'NF && !seen[$0]++')
mapfile -t PROPAGATED < <(printf '%s\n' "${PROPAGATED[@]}" | awk 'NF && !seen[$0]++')
mapfile -t FAILED < <(printf '%s\n' "${FAILED[@]}" | awk 'NF && !seen[$0]++')

printf '\n[r4r-hot-sync] SUMMARY\n'
printf '  hub:                    %s\n' "$HUB_BRANCH"
printf '  final hub commit:       %s\n' "${HUB_COMMIT:-$(git -C "$INTEGRATION_WORKTREE" rev-parse HEAD)}"
printf '  centralized refs:       %s\n' "${CENTRALIZED[*]:-(none)}"
printf '  propagated branches:    %s\n' "${PROPAGATED[*]:-(none)}"
printf '  preserved worktrees:    %s\n' "${PRESERVED_BRANCHES[*]:-(none)}"
printf '  backup root:            %s\n' "$BACKUP_RUN_ROOT"
printf '  failed:                 %s\n' "${FAILED[*]:-(none)}"

((${#FAILED[@]} == 0)) || exit 3
log 'complete hot-sync pass finished'
