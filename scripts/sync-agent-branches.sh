#!/usr/bin/env bash
set -Eeuo pipefail

# R4R worktree-aware hub synchronization.
#
# Default invocation performs a complete pass:
#   1. fetch/prune origin;
#   2. discover every non-detached worktree attached to this Git common dir;
#   3. for each subscribed source branch, merge its local and remote tips into
#      agent/integration;
#   4. after every source round, push integration and propagate the current hub
#      commit to every subscribed branch;
#   5. push every successfully updated branch;
#   6. start/check the Ring supervisor once after final convergence.
#
# Conflicts are never auto-resolved. A collection conflict remains open in the
# integration worktree. A propagation conflict remains open in the exact target
# worktree. The script reports and opens that directory, copies it to the clipboard,
# and emits terminal, freedesktop, GNOME or KDE alerts when available.

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DEVELOPMENT_ROOT="${R4R_DEVELOPMENT_ROOT:-$HOME/Desarrollo}"
REPOSITORY="${R4R_REPOSITORY:-$ROOT}"
INTEGRATION_WORKTREE_HINT="${R4R_INTEGRATION_WORKTREE:-$DEVELOPMENT_ROOT/r4r-integration.git}"
CANONICAL_RING_WORKTREE="${R4R_RING_WORKTREE:-$DEVELOPMENT_ROOT/r4r-ring-agent.git}"
HUB_BRANCH="${R4R_INTEGRATION_BRANCH:-agent/integration}"
REMOTE="${R4R_SYNC_REMOTE:-origin}"
PUSH_POLICY="strict"
FETCH=true
COLLECT=true
PROPAGATE=true
SYNC_WORKERS=true
START_GUARDIAN=true
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
SKIPPED=()
ROUND_COMMITS=()
COMMON_DIR=""
INTEGRATION_WORKTREE=""
HUB_COMMIT=""
LOG_ROOT=""
ALERT_ROOT=""
WORKER_SYNC_OK=true

usage() {
  cat <<'USAGE'
Usage: ./scripts/sync-agent-branches.sh [options]

Default: complete automatic pass
  - fetch/prune origin;
  - discover every branch checked out by a worktree subscribed to this repository;
  - merge one source branch into agent/integration;
  - immediately propagate the resulting integration commit to all subscribed branches;
  - repeat with the next source branch;
  - push integration and every updated branch;
  - start/check Ring once after final convergence.

Options:
  --hub BRANCH             Hub branch (default: agent/integration).
  --source BRANCH          Process only this source branch; repeatable.
  --target BRANCH          Propagate only to this target branch; repeatable.
  --exclude PATTERN        Exclude matching branch glob; repeatable.
  --all-local-branches     Include local branches without a linked worktree.
  --remote NAME            Fetch/push remote (default: origin).
  --fetch                  Fetch/prune before synchronization (default).
  --no-fetch               Use current local/remote-tracking refs only.
  --push                   Push and fail/report on push errors (default).
  --push-if-available      Keep local synchronization if push is unavailable.
  --no-push                Do not push.
  --collect-only           Centralize selected sources; do not propagate.
  --propagate-only         Propagate current hub; do not collect sources.
  --no-workers             Do not use the safe PC/LP worker merger.
  --no-guardian            Do not start/check Ring afterwards.
  --no-notify              Disable desktop notifications.
  --open-conflict-dir      Open conflict directory automatically (default).
  --no-open-conflict-dir   Do not open a file manager.
  --no-modal-alert         Keep terminal/desktop notification; no modal dialog.
  --dry-run                Print the complete plan without changing Git/processes.
  -h, --help               Show help.

Subscribed worktree definition:
  Every non-detached branch returned by `git worktree list --porcelain` for the same
  Git common directory. The hub worktree is excluded from sources/targets. No branch
  name is silently excluded; use --exclude when a checked-out branch must not sync.

Worker safety:
  PC and LP use merge-worker-branches-and-restart.sh so dirty task state is backed up,
  stashed, restored and verified. To avoid repeated restarts, their final convergence
  is applied once after all source rounds; every other subscribed worktree is
  propagated after each source round.
USAGE
}

log()  { printf '[r4r-hub-sync] %s\n' "$*"; }
warn() { printf '\033[1;33m[r4r-hub-sync] WARNING: %s\033[0m\n' "$*" >&2; }
die()  { printf '\033[1;31m[r4r-hub-sync] ERROR: %s\033[0m\n' "$*" >&2; exit 2; }

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
    --no-workers) SYNC_WORKERS=false; shift ;;
    --no-guardian) START_GUARDIAN=false; shift ;;
    --no-notify) NOTIFY=false; shift ;;
    --open-conflict-dir) OPEN_CONFLICT_DIR=true; shift ;;
    --no-open-conflict-dir) OPEN_CONFLICT_DIR=false; shift ;;
    --no-modal-alert) MODAL_ALERT=false; shift ;;
    --dry-run) DRY_RUN=true; shift ;;
    -h|--help) usage; exit 0 ;;
    *) die "unknown argument: $1" ;;
  esac
done

for command in git flock realpath sha256sum awk sed sort mktemp; do
  command -v "$command" >/dev/null 2>&1 || die "required command unavailable: $command"
done

REPOSITORY="$(realpath -e "$REPOSITORY" 2>/dev/null)" || die "repository path does not exist"
git -C "$REPOSITORY" rev-parse --is-inside-work-tree >/dev/null 2>&1 \
  || die "not a Git worktree: $REPOSITORY"
COMMON_DIR="$(git -C "$REPOSITORY" rev-parse --git-common-dir)"
[[ "$COMMON_DIR" == /* ]] || COMMON_DIR="$REPOSITORY/$COMMON_DIR"
COMMON_DIR="$(realpath -e "$COMMON_DIR")"

runtime_root() {
  printf '%s\n' "${R4R_BRANCH_SYNC_RUNTIME_ROOT:-$DEVELOPMENT_ROOT/.r4r-runtime/branch-sync}"
}

LOG_ROOT="$(runtime_root)"
ALERT_ROOT="$LOG_ROOT/alerts"
mkdir -p "$LOG_ROOT/conflicts" "$LOG_ROOT/worktrees" "$ALERT_ROOT"

# One canonical lock across systemd, cron and manual runs in any worktree.
exec 9>/tmp/r4r-agent-branch-sync.lock
flock -n 9 || { log "another branch synchronization is already running"; exit 0; }
# Serialize with the Google Drive import/autocommit process.
exec 8>/tmp/r4r-drive-import.lock
flock -w 30 8 || die "Google Drive import lock remained busy for 30 seconds"

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
  printf '%s\n' "$fingerprint" > "$fingerprint_file"
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
        >/dev/null 2>&1 &
    elif command -v zenity >/dev/null 2>&1; then
      nohup zenity --warning --title='R4R Git Sync' --width=720 --timeout=120 \
        --text="$(printf '%s%s' "$body" "${path:+$'\n\n'Ruta copiada al portapapeles:$'\n'$path}")" \
        >/dev/null 2>&1 &
    fi
  fi
  if "$OPEN_CONFLICT_DIR" && [[ -n "$path" && -d "$path" ]] \
      && command -v xdg-open >/dev/null 2>&1; then
    nohup xdg-open "$path" >/dev/null 2>&1 &
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

resolve_integration_worktree() {
  local candidate branch
  for candidate in "$INTEGRATION_WORKTREE_HINT" "$REPOSITORY"; do
    if valid_common_worktree "$candidate"; then
      branch="$(git -C "$candidate" branch --show-current 2>/dev/null || true)"
      if [[ "$branch" == "$HUB_BRANCH" ]]; then
        realpath -e "$candidate"
        return 0
      fi
    fi
  done
  candidate="$(worktree_for_branch "$HUB_BRANCH" || true)"
  if [[ -n "$candidate" ]] && valid_common_worktree "$candidate"; then
    realpath -e "$candidate"
    return 0
  fi
  return 1
}

resolve_ring_runtime_worktree() {
  local candidate
  for candidate in "$CANONICAL_RING_WORKTREE" "$ROOT"; do
    if valid_common_worktree "$candidate" \
        && [[ -f "$candidate/py-ring-agent/run-worker-streamed.py" ]]; then
      realpath -e "$candidate"
      return 0
    fi
  done
  candidate="$(worktree_for_branch agent/ring-agent-worker || true)"
  if [[ -n "$candidate" ]] && valid_common_worktree "$candidate" \
      && [[ -f "$candidate/py-ring-agent/run-worker-streamed.py" ]]; then
    realpath -e "$candidate"
    return 0
  fi
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

write_conflict_report() {
  local mode="$1" incoming="$2" worktree="$3" merge_log="$4" report stamp
  stamp="$(date +%Y%m%d-%H%M%S)"
  report="$LOG_ROOT/conflicts/${stamp}-$(sanitize "$mode-$incoming").txt"
  {
    echo 'R4R BRANCH SYNC CONFLICT'
    echo "Generated: $(date --iso-8601=seconds)"
    echo "Mode: $mode"
    echo "Hub branch: $HUB_BRANCH"
    echo "Incoming/target: $incoming"
    echo "Worktree: $worktree"
    echo
    echo 'Unmerged paths:'
    unmerged_paths "$worktree" || true
    echo
    echo 'Git status:'
    git -C "$worktree" status --short || true
    echo
    echo 'Merge output:'
    [[ -f "$merge_log" ]] && sed -n '1,240p' "$merge_log" || true
    echo
    echo 'Resolve:'
    echo "  cd '$worktree'"
    echo '  git status'
    echo '  # edit each conflicted file'
    echo '  git add <resolved-files>'
    echo '  git commit'
    echo
    echo 'Abort instead:'
    echo "  git -C '$worktree' merge --abort"
  } > "$report"
  printf '%s\n' "$report"
}

push_ref() {
  local branch="$1" output
  [[ "$PUSH_POLICY" != off ]] || return 0
  if ! remote_exists; then
    if [[ "$PUSH_POLICY" == best-effort ]]; then
      warn "remote $REMOTE unavailable; local synchronization retained"
      return 0
    fi
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
    warn "$branch: push unavailable; local branch remains synchronized"
    [[ -z "$output" ]] || printf '%s\n' "$output" >&2
    return 0
  fi
  alert_once "push-$branch" 'R4R: fallo al publicar rama' \
    "git push falló para $branch.\n\n$(printf '%s\n' "$output" | sed -n '1,20p')" "$REPOSITORY"
  return 1
}

discover_subscribed_worktree_branches() {
  local current_path="" line branch
  while IFS= read -r line; do
    case "$line" in
      'worktree '*) current_path="${line#worktree }" ;;
      'branch refs/heads/'*)
        branch="${line#branch refs/heads/}"
        branch_is_excluded "$branch" && continue
        printf '%s\n' "$branch"
        ;;
    esac
  done < <(git -C "$REPOSITORY" worktree list --porcelain)
}

select_all_local_branches() {
  local branch
  while IFS= read -r branch; do
    branch_is_excluded "$branch" || printf '%s\n' "$branch"
  done < <(git -C "$REPOSITORY" for-each-ref --format='%(refname:short)' refs/heads)
}

ensure_no_pending_merges() {
  local branch path paths
  if merge_in_progress "$INTEGRATION_WORKTREE"; then
    paths="$(unmerged_paths "$INTEGRATION_WORKTREE" || true)"
    alert_once 'pending-integration-merge' 'R4R: conflicto Git pendiente' \
      "Hay una fusión pendiente en $HUB_BRANCH.\nNo se continuará hasta resolverla o abortarla.\n\n${paths:-Consulta git status.}" \
      "$INTEGRATION_WORKTREE"
    return 4
  fi
  clear_alert 'pending-integration-merge'

  for branch in "${TARGETS[@]}"; do
    path="$(worktree_for_branch "$branch" || true)"
    [[ -n "$path" ]] || continue
    if merge_in_progress "$path"; then
      paths="$(unmerged_paths "$path" || true)"
      alert_once "pending-$branch" 'R4R: conflicto Git pendiente en worktree' \
        "La rama $branch tiene una fusión pendiente.\nNo se continuará hasta resolverla o abortarla.\n\n${paths:-Consulta git status.}" \
        "$path"
      return 4
    fi
    clear_alert "pending-$branch"
  done
}

integration_must_be_clean() {
  local dirty
  dirty="$(git -C "$INTEGRATION_WORKTREE" status --porcelain=v1 --untracked-files=all)"
  if [[ -n "$dirty" ]]; then
    alert_once 'integration-dirty' 'R4R: integration tiene cambios sin commit' \
      "El sincronizador no puede centralizar ramas mientras integration esté sucio.\n\n$(printf '%s\n' "$dirty" | sed -n '1,30p')" \
      "$INTEGRATION_WORKTREE"
    return 4
  fi
  clear_alert 'integration-dirty'
}

collect_ref_into_hub() {
  local incoming="$1" label="$2" merge_log before report paths body
  if git -C "$INTEGRATION_WORKTREE" merge-base --is-ancestor "$incoming" HEAD; then
    log "$label: already centralized"
    return 0
  fi

  merge_log="$LOG_ROOT/collect-$(sanitize "$label").log"
  before="$(git -C "$INTEGRATION_WORKTREE" rev-parse HEAD)"
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

  if merge_in_progress "$INTEGRATION_WORKTREE" \
      || [[ -n "$(unmerged_paths "$INTEGRATION_WORKTREE" || true)" ]]; then
    report="$(write_conflict_report collection "$label" "$INTEGRATION_WORKTREE" "$merge_log")"
    paths="$(unmerged_paths "$INTEGRATION_WORKTREE" || true)"
    body="Conflicto al integrar $label en $HUB_BRANCH.\nLa fusión queda abierta para resolución manual.\n\nFicheros:\n${paths:-Consulta git status.}\n\nInforme: $report"
    alert_once "collect-$label" 'R4R: conflicto al centralizar rama' "$body" "$INTEGRATION_WORKTREE"
    FAILED+=("$label:conflict")
    return 4
  fi

  git -C "$INTEGRATION_WORKTREE" merge --abort >/dev/null 2>&1 || true
  git -C "$INTEGRATION_WORKTREE" reset --hard "$before" >/dev/null 2>&1 || true
  body="Falló la integración de $label sin conflicto resoluble.\nSe restauró $HUB_BRANCH a ${before:0:12}.\n\n$(sed -n '1,40p' "$merge_log")"
  alert_once "collect-$label" 'R4R: fallo al centralizar rama' "$body" "$INTEGRATION_WORKTREE"
  FAILED+=("$label:merge-failed")
  return 1
}

prepare_target_worktree() {
  local branch="$1" path
  if path="$(worktree_for_branch "$branch")"; then
    realpath -e "$path"
    return 0
  fi
  path="$LOG_ROOT/worktrees/$(sanitize "$branch")"
  if [[ -e "$path" ]]; then
    git -C "$REPOSITORY" worktree prune >/dev/null 2>&1 || true
    [[ -e "$path" ]] && rm -rf "$path"
  fi
  if "$DRY_RUN"; then
    printf '%s\n' "$REPOSITORY"
    return 0
  fi
  git -C "$REPOSITORY" worktree add --quiet "$path" "$branch"
  printf '%s\n' "$path"
}

remove_temporary_target_worktree() {
  local branch="$1" path="$2"
  [[ "$path" == "$LOG_ROOT/worktrees/"* ]] || return 0
  merge_in_progress "$path" && return 0
  "$DRY_RUN" || git -C "$REPOSITORY" worktree remove --force "$path" >/dev/null 2>&1 || true
}

propagate_regular_branch() {
  local branch="$1" worktree before merge_log dirty report paths body
  branch_exists "$branch" || { FAILED+=("$branch:missing"); return 1; }

  if git -C "$REPOSITORY" merge-base --is-ancestor "$HUB_COMMIT" "$branch"; then
    log "$branch: already contains ${HUB_COMMIT:0:12}"
    push_ref "$branch" || FAILED+=("$branch:push")
    return 0
  fi

  worktree="$(prepare_target_worktree "$branch")" || {
    FAILED+=("$branch:worktree")
    return 1
  }

  if merge_in_progress "$worktree"; then
    alert_once "pending-$branch" 'R4R: conflicto Git pendiente en worktree' \
      "La rama $branch ya tiene una fusión pendiente. Resuélvela o abórtala." "$worktree"
    FAILED+=("$branch:pending-merge")
    return 4
  fi

  dirty="$(git -C "$worktree" status --porcelain=v1 --untracked-files=all)"
  if [[ -n "$dirty" ]]; then
    alert_once "dirty-$branch" 'R4R: rama no propagada por cambios locales' \
      "$branch tiene cambios sin commit y no se ha tocado.\n\n$(printf '%s\n' "$dirty" | sed -n '1,30p')" \
      "$worktree"
    SKIPPED+=("$branch:dirty")
    remove_temporary_target_worktree "$branch" "$worktree"
    return 1
  fi

  before="$(git -C "$worktree" rev-parse HEAD)"
  merge_log="$LOG_ROOT/propagate-$(sanitize "$branch").log"
  log "$branch: merging integration ${HUB_COMMIT:0:12}"

  if "$DRY_RUN"; then
    log "DRY-RUN: git -C $worktree merge --no-edit $HUB_COMMIT"
  elif git -C "$worktree" merge --no-edit "$HUB_COMMIT" >"$merge_log" 2>&1; then
    PROPAGATED+=("$branch")
    clear_alert "dirty-$branch"
    clear_alert "propagate-$branch"
  elif merge_in_progress "$worktree" || [[ -n "$(unmerged_paths "$worktree" || true)" ]]; then
    report="$(write_conflict_report propagation "$branch" "$worktree" "$merge_log")"
    paths="$(unmerged_paths "$worktree" || true)"
    body="Conflicto al propagar $HUB_BRANCH a $branch.\nLa fusión queda abierta en el worktree exacto.\n\nFicheros:\n${paths:-Consulta git status.}\n\nInforme: $report"
    alert_once "propagate-$branch" 'R4R: conflicto al propagar integration' "$body" "$worktree"
    FAILED+=("$branch:conflict")
    return 4
  else
    git -C "$worktree" merge --abort >/dev/null 2>&1 || true
    git -C "$worktree" reset --hard "$before" >/dev/null 2>&1 || true
    body="Falló la propagación de $HUB_BRANCH a $branch.\nLa rama se restauró a ${before:0:12}.\n\n$(sed -n '1,40p' "$merge_log")"
    alert_once "propagate-$branch" 'R4R: fallo al propagar integration' "$body" "$worktree"
    FAILED+=("$branch:propagate")
    remove_temporary_target_worktree "$branch" "$worktree"
    return 1
  fi

  push_ref "$branch" || FAILED+=("$branch:push")
  remove_temporary_target_worktree "$branch" "$worktree"
}

worker_targets_selected() {
  local branch
  for branch in "${TARGETS[@]}"; do
    [[ "$branch" == agent/pc-qwen3-worker || "$branch" == agent/laptop-qwen3-worker ]] \
      && return 0
  done
  return 1
}

propagate_regular_targets_round() {
  local branch code
  for branch in "${TARGETS[@]}"; do
    case "$branch" in
      agent/pc-qwen3-worker|agent/laptop-qwen3-worker) continue ;;
    esac
    propagate_regular_branch "$branch" || {
      code=$?
      ((code == 4)) && return 4
    }
  done
}

propagate_workers_final() {
  local pc_selected=false lp_selected=false branch ring_worktree pc_worktree lp_worktree merger
  "$SYNC_WORKERS" || return 0
  for branch in "${TARGETS[@]}"; do
    [[ "$branch" == agent/pc-qwen3-worker ]] && pc_selected=true
    [[ "$branch" == agent/laptop-qwen3-worker ]] && lp_selected=true
  done
  { "$pc_selected" || "$lp_selected"; } || return 0

  # The safe merger is paired because it preserves and restarts both worker runtimes.
  # If only one worker is subscribed, fall back to normal branch propagation.
  if ! "$pc_selected" || ! "$lp_selected"; then
    "$pc_selected" && propagate_regular_branch agent/pc-qwen3-worker
    "$lp_selected" && propagate_regular_branch agent/laptop-qwen3-worker
    return
  fi

  if git -C "$REPOSITORY" merge-base --is-ancestor "$HUB_COMMIT" agent/pc-qwen3-worker \
      && git -C "$REPOSITORY" merge-base --is-ancestor "$HUB_COMMIT" agent/laptop-qwen3-worker; then
    log "PC and LP already contain ${HUB_COMMIT:0:12}"
    push_ref agent/pc-qwen3-worker || FAILED+=("agent/pc-qwen3-worker:push")
    push_ref agent/laptop-qwen3-worker || FAILED+=("agent/laptop-qwen3-worker:push")
    return 0
  fi

  ring_worktree="$(resolve_ring_runtime_worktree || true)"
  pc_worktree="$(worktree_for_branch agent/pc-qwen3-worker || true)"
  lp_worktree="$(worktree_for_branch agent/laptop-qwen3-worker || true)"
  merger="$ROOT/scripts/merge-worker-branches-and-restart.sh"
  if [[ -z "$ring_worktree" || -z "$pc_worktree" || -z "$lp_worktree" || ! -x "$merger" ]]; then
    alert_once 'worker-resolution' 'R4R: no se pueden propagar PC/LP' \
      "Falta un worktree o el merger seguro.\nRing=${ring_worktree:-MISSING}\nPC=${pc_worktree:-MISSING}\nLP=${lp_worktree:-MISSING}\nMerger=$merger" \
      "$ROOT"
    FAILED+=("workers:resolution")
    WORKER_SYNC_OK=false
    return 1
  fi

  local command=("$merger" --source "$HUB_COMMIT" --ring "$ring_worktree" --pc "$pc_worktree" --lp "$lp_worktree")
  "$DRY_RUN" && command+=(--dry-run)
  if "${command[@]}"; then
    "$DRY_RUN" || PROPAGATED+=(agent/pc-qwen3-worker agent/laptop-qwen3-worker)
    push_ref agent/pc-qwen3-worker || FAILED+=("agent/pc-qwen3-worker:push")
    push_ref agent/laptop-qwen3-worker || FAILED+=("agent/laptop-qwen3-worker:push")
    clear_alert 'worker-resolution'
    clear_alert 'worker-merge'
  else
    alert_once 'worker-merge' 'R4R: fallo al propagar PC/LP' \
      "El merger seguro de workers falló. Revisa sus worktrees y runtime/worker-sync-backups." \
      "$ring_worktree"
    FAILED+=("workers:merge-or-restart")
    WORKER_SYNC_OK=false
    return 1
  fi
}

propagate_current_hub_round() {
  local label="$1" code
  HUB_COMMIT="$(git -C "$INTEGRATION_WORKTREE" rev-parse HEAD)"
  ROUND_COMMITS+=("$label=${HUB_COMMIT:0:12}")
  log "round $label: hub commit $HUB_COMMIT"
  push_ref "$HUB_BRANCH" || FAILED+=("$HUB_BRANCH:push")

  "$PROPAGATE" || return 0
  propagate_regular_targets_round || {
    code=$?
    ((code == 4)) && return 4
  }
}

cleanup() {
  local code=$?
  git -C "$REPOSITORY" worktree prune >/dev/null 2>&1 || true
  exit "$code"
}
trap cleanup EXIT

if "$FETCH"; then
  if remote_exists; then
    log "fetching/pruning $REMOTE"
    if ! git -C "$REPOSITORY" fetch --prune "$REMOTE"; then
      alert_once fetch 'R4R: fallo de fetch' \
        "No se pudo actualizar $REMOTE. Se continuará con las referencias locales disponibles." \
        "$REPOSITORY"
      FAILED+=("fetch:$REMOTE")
    else
      clear_alert fetch
    fi
  else
    warn "remote $REMOTE unavailable; fetch skipped"
  fi
fi

git -C "$REPOSITORY" show-ref --verify --quiet "refs/heads/$HUB_BRANCH" \
  || die "local hub branch does not exist: $HUB_BRANCH"

mapfile -t SUBSCRIBED < <(discover_subscribed_worktree_branches | sort -u)
if "$ALL_LOCAL_BRANCHES"; then
  mapfile -t SUBSCRIBED < <(
    { printf '%s\n' "${SUBSCRIBED[@]}"; select_all_local_branches; } | awk 'NF && !seen[$0]++' | sort
  )
fi

if ! "$TARGETS_EXPLICIT"; then
  TARGETS=("${SUBSCRIBED[@]}")
fi
if ! "$SOURCES_EXPLICIT"; then
  SOURCES=("${SUBSCRIBED[@]}")
fi
mapfile -t TARGETS < <(printf '%s\n' "${TARGETS[@]}" | awk 'NF && !seen[$0]++')
mapfile -t SOURCES < <(printf '%s\n' "${SOURCES[@]}" | awk 'NF && !seen[$0]++')

if "$COLLECT" && ((${#SOURCES[@]} == 0)); then
  die "no source branches selected or subscribed"
fi
if "$PROPAGATE" && ((${#TARGETS[@]} == 0)); then
  die "no target branches selected or subscribed"
fi

INTEGRATION_WORKTREE="$(resolve_integration_worktree || true)"
if [[ -z "$INTEGRATION_WORKTREE" ]]; then
  alert_once 'missing-integration-worktree' 'R4R: falta el worktree de integration' \
    "No se encontró un worktree con checkout de $HUB_BRANCH.\nRuta esperada: $INTEGRATION_WORKTREE_HINT" \
    "$DEVELOPMENT_ROOT"
  exit 4
fi
clear_alert 'missing-integration-worktree'

log "repository:             $REPOSITORY"
log "Git common dir:         $COMMON_DIR"
log "integration worktree:   $INTEGRATION_WORKTREE"
log "hub branch:             $HUB_BRANCH"
log "subscribed worktrees:   ${SUBSCRIBED[*]:-(none)}"
log "source sequence:        ${SOURCES[*]:-(none)}"
log "propagation targets:    ${TARGETS[*]:-(none)}"
log "fetch:                  $FETCH"
log "push policy:            $PUSH_POLICY"

ensure_no_pending_merges || exit $?
integration_must_be_clean || exit $?

rounds=0
if "$COLLECT"; then
  # Reconcile a remote hub advance first, then expose it to subscribed worktrees.
  if "$FETCH" && remote_ref_exists "$HUB_BRANCH"; then
    before="$(git -C "$INTEGRATION_WORKTREE" rev-parse HEAD)"
    collect_ref_into_hub "$REMOTE/$HUB_BRANCH" "$REMOTE/$HUB_BRANCH" || {
      code=$?; ((code == 4)) && exit 4
    }
    after="$(git -C "$INTEGRATION_WORKTREE" rev-parse HEAD)"
    if [[ "$after" != "$before" ]]; then
      ((rounds += 1))
      propagate_current_hub_round "remote-hub" || { code=$?; ((code == 4)) && exit 4; }
    fi
  fi

  for source_branch in "${SOURCES[@]}"; do
    branch_is_excluded "$source_branch" && continue
    before="$(git -C "$INTEGRATION_WORKTREE" rev-parse HEAD)"
    found=false

    if branch_exists "$source_branch"; then
      found=true
      collect_ref_into_hub "$source_branch" "$source_branch" || {
        code=$?; ((code == 4)) && exit 4
      }
    fi
    if "$FETCH" && remote_ref_exists "$source_branch"; then
      found=true
      collect_ref_into_hub "$REMOTE/$source_branch" "$REMOTE/$source_branch" || {
        code=$?; ((code == 4)) && exit 4
      }
    fi
    if [[ "$found" != true ]]; then
      warn "$source_branch: neither local nor remote branch exists"
      FAILED+=("$source_branch:missing-source")
      continue
    fi

    after="$(git -C "$INTEGRATION_WORKTREE" rev-parse HEAD)"
    if [[ "$after" != "$before" ]]; then
      ((rounds += 1))
      propagate_current_hub_round "$source_branch" || {
        code=$?; ((code == 4)) && exit 4
      }
    else
      log "$source_branch: no new hub commit"
    fi
  done
fi

# Always perform one final convergence pass. It catches an unchanged hub that a target
# has not yet consumed, and gives PC/LP a single safe final merge/restart.
HUB_COMMIT="$(git -C "$INTEGRATION_WORKTREE" rev-parse HEAD)"
push_ref "$HUB_BRANCH" || FAILED+=("$HUB_BRANCH:push")
if "$PROPAGATE"; then
  propagate_regular_targets_round || { code=$?; ((code == 4)) && exit 4; }
  propagate_workers_final || true
fi

if "$START_GUARDIAN" && "$PROPAGATE" && ! "$DRY_RUN"; then
  if [[ "$WORKER_SYNC_OK" != true ]]; then
    warn "worker propagation failed; Ring start skipped"
  else
    RING_WORKTREE="$(resolve_ring_runtime_worktree || true)"
    if [[ -n "$RING_WORKTREE" && -x "$ROOT/scripts/run-ring-system.sh" ]]; then
      R4R_RING_WORKTREE="$RING_WORKTREE" "$ROOT/scripts/run-ring-system.sh" start \
        || FAILED+=("ring:start")
    fi
  fi
fi

mapfile -t CENTRALIZED < <(printf '%s\n' "${CENTRALIZED[@]}" | awk 'NF && !seen[$0]++')
mapfile -t PROPAGATED < <(printf '%s\n' "${PROPAGATED[@]}" | awk 'NF && !seen[$0]++')
mapfile -t SKIPPED < <(printf '%s\n' "${SKIPPED[@]}" | awk 'NF && !seen[$0]++')
mapfile -t FAILED < <(printf '%s\n' "${FAILED[@]}" | awk 'NF && !seen[$0]++')

printf '\n[r4r-hub-sync] SUMMARY\n'
printf '  hub:                    %s\n' "$HUB_BRANCH"
printf '  final hub commit:       %s\n' "$HUB_COMMIT"
printf '  source rounds:          %s\n' "$rounds"
printf '  round commits:          %s\n' "${ROUND_COMMITS[*]:-(none)}"
printf '  centralized refs:       %s\n' "${CENTRALIZED[*]:-(none)}"
printf '  propagated branches:    %s\n' "${PROPAGATED[*]:-(none)}"
printf '  skipped:                %s\n' "${SKIPPED[*]:-(none)}"
printf '  failed:                 %s\n' "${FAILED[*]:-(none)}"

if ((${#FAILED[@]} > 0 || ${#SKIPPED[@]} > 0)); then
  exit 3
fi
log "complete worktree-aware collection, propagation and push pass finished"
