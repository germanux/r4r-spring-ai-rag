#!/usr/bin/env bash
set -Eeuo pipefail

# R4R hub-and-spoke branch synchronization.
#
# Phase A (collect): merge every active agent branch, one by one, into the
# canonical hub branch agent/integration.
# Phase B (propagate): pin the resulting integration commit and fast-forward it
# into every active branch. PC and LP use the existing safe worker merger so
# dirty task state is backed up/restored and workers are restarted only when
# their branches actually need the new integration commit.
#
# A conflict is never hidden or auto-resolved. It is left open in the dedicated
# integration worktree, a report is written, and a desktop alert shows the exact
# worktree path and unresolved files. The next scheduled run detects the same
# merge and waits for the operator to resolve or abort it.

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DEVELOPMENT_ROOT="${R4R_DEVELOPMENT_ROOT:-$HOME/Desarrollo}"
REPOSITORY="${R4R_REPOSITORY:-$ROOT}"
INTEGRATION_WORKTREE_HINT="${R4R_INTEGRATION_WORKTREE:-$DEVELOPMENT_ROOT/r4r-integration.git}"
CANONICAL_RING_WORKTREE="${R4R_RING_WORKTREE:-$DEVELOPMENT_ROOT/r4r-ring-agent.git}"
SOURCE_BRANCH="${R4R_INTEGRATION_BRANCH:-agent/integration}"
REMOTE="${R4R_SYNC_REMOTE:-origin}"
PUSH_POLICY="strict"
FETCH=false
COLLECT=true
PROPAGATE=true
SYNC_WORKERS=true
START_GUARDIAN=true
DRY_RUN=false
NOTIFY=true
OPEN_CONFLICT_DIR=true
MODAL_ALERT=true
TARGETS=()
TARGETS_EXPLICIT=false
FAILED=()
UPDATED_HUB=()
UPDATED_TARGETS=()
SKIPPED=()
TMP_ROOT=""
WORKER_SYNC_OK=true
SOURCE_COMMIT=""
COMMON_DIR=""
INTEGRATION_WORKTREE=""
LOG_ROOT=""
ALERT_ROOT=""

usage() {
  cat <<'USAGE'
Usage: ./scripts/sync-agent-branches.sh [options]

Hub-and-spoke flow:
  1. collect every selected branch into agent/integration, sequentially;
  2. pin the resulting integration commit;
  3. propagate that exact commit to every selected branch;
  4. push integration first, then each updated branch.

Options:
  --source BRANCH          Hub branch (default: agent/integration).
  --target BRANCH          Select one branch; repeatable.
  --remote NAME            Remote for fetch/push (default: origin).
  --fetch                  Fetch/prune before synchronization.
  --no-fetch               Do not fetch (default).
  --push                   Push and report push failures (default).
  --push-if-available      Continue locally if credentials/network are absent.
  --no-push                Do not push.
  --collect-only           Merge selected branches into integration only.
  --propagate-only         Propagate current integration without collection.
  --no-workers             Do not use the safe PC/LP worker merger.
  --no-guardian            Do not start/check the Ring supervisor afterwards.
  --no-notify              Disable desktop notifications.
  --open-conflict-dir      Open the affected worktree on a new conflict (default).
  --no-open-conflict-dir   Do not open a file manager automatically.
  --no-modal-alert         Use notification only; no Zenity/KDialog popup.
  --dry-run                Print actions without modifying Git or processes.
  -h, --help               Show this help.

Default selected branches:
  local r4r-chatgpt and agent/* branches, excluding the hub, main/master,
  backup/*, agent/snapshots and obsolete *claude-surgical* branches.

Conflict policy:
  Collection conflicts remain open in the integration worktree. The script
  stops before propagation and displays the exact directory and unresolved
  paths. Resolve and commit there, or run `git merge --abort`.
USAGE
}

log()  { printf '[r4r-hub-sync] %s\n' "$*"; }
warn() { printf '\033[1;33m[r4r-hub-sync] WARNING: %s\033[0m\n' "$*" >&2; }
die()  { printf '\033[1;31m[r4r-hub-sync] ERROR: %s\033[0m\n' "$*" >&2; exit 2; }

while (($#)); do
  case "$1" in
    --source) (($# >= 2)) || die "--source requires a branch"; SOURCE_BRANCH="$2"; shift 2 ;;
    --target) (($# >= 2)) || die "--target requires a branch"; TARGETS+=("$2"); TARGETS_EXPLICIT=true; shift 2 ;;
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

for command in git flock mktemp realpath sha256sum; do
  command -v "$command" >/dev/null 2>&1 || die "required command unavailable: $command"
done

REPOSITORY="$(realpath -e "$REPOSITORY" 2>/dev/null)" || die "repository path does not exist"
git -C "$REPOSITORY" rev-parse --is-inside-work-tree >/dev/null 2>&1 \
  || die "not a Git worktree: $REPOSITORY"
COMMON_DIR="$(git -C "$REPOSITORY" rev-parse --git-common-dir)"
[[ "$COMMON_DIR" == /* ]] || COMMON_DIR="$REPOSITORY/$COMMON_DIR"
COMMON_DIR="$(realpath -e "$COMMON_DIR")"

runtime_root() {
  # Keep synchronization evidence outside every worktree. Otherwise a repository
  # without a runtime/ ignore rule becomes dirty merely because synchronization ran.
  printf '%s\n' "${R4R_BRANCH_SYNC_RUNTIME_ROOT:-$DEVELOPMENT_ROOT/.r4r-runtime/branch-sync}"
}

LOG_ROOT="$(runtime_root)"
ALERT_ROOT="$LOG_ROOT/alerts"
mkdir -p "$LOG_ROOT/conflicts" "$ALERT_ROOT"

# One canonical lock for cron/systemd/manual runs across all worktrees.
exec 9>/tmp/r4r-agent-branch-sync.lock
flock -n 9 || { log "another branch synchronization is already running"; exit 0; }
# Serialize with Google Drive import/autocommit jobs.
exec 8>/tmp/r4r-drive-import.lock
flock -w 30 8 || die "Google Drive import lock remained busy for 30 seconds"

sanitize() {
  printf '%s' "$1" | tr -cs 'A-Za-z0-9._-' '_'
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
  local key="$1" title="$2" body="$3" path="${4:-}" fingerprint_file fingerprint old=""
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
      "$title" "$body${path:+\n\nRuta: $path}" >/dev/null 2>&1 || true
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

clear_alert() {
  rm -f "$ALERT_ROOT/$(sanitize "$1").sha256"
}

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
      if [[ "$branch" == "$SOURCE_BRANCH" ]]; then
        realpath -e "$candidate"
        return 0
      fi
    fi
  done
  candidate="$(worktree_for_branch "$SOURCE_BRANCH" || true)"
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

branch_exists() {
  git -C "$REPOSITORY" show-ref --verify --quiet "refs/heads/$1"
}

remote_ref_exists() {
  git -C "$REPOSITORY" show-ref --verify --quiet "refs/remotes/$REMOTE/$1"
}

remote_exists() {
  git -C "$REPOSITORY" remote get-url "$REMOTE" >/dev/null 2>&1
}

merge_in_progress() {
  local worktree="$1" merge_head
  merge_head="$(git -C "$worktree" rev-parse --git-path MERGE_HEAD)"
  [[ "$merge_head" == /* ]] || merge_head="$worktree/$merge_head"
  [[ -f "$merge_head" ]]
}

unmerged_paths() {
  git -C "$1" diff --name-only --diff-filter=U | sed '/^$/d'
}

write_conflict_report() {
  local incoming="$1" merge_log="$2" report stamp
  stamp="$(date +%Y%m%d-%H%M%S)"
  report="$LOG_ROOT/conflicts/${stamp}-$(sanitize "$incoming").txt"
  {
    echo 'R4R BRANCH SYNC CONFLICT'
    echo "Generated: $(date --iso-8601=seconds)"
    echo "Integration worktree: $INTEGRATION_WORKTREE"
    echo "Hub branch: $SOURCE_BRANCH"
    echo "Incoming branch/ref: $incoming"
    echo
    echo 'Unmerged paths:'
    unmerged_paths "$INTEGRATION_WORKTREE" || true
    echo
    echo 'Git status:'
    git -C "$INTEGRATION_WORKTREE" status --short || true
    echo
    echo 'Merge output:'
    [[ -f "$merge_log" ]] && sed -n '1,240p' "$merge_log" || true
    echo
    echo 'Resolve:'
    echo "  cd '$INTEGRATION_WORKTREE'"
    echo '  git status'
    echo '  # edit each conflicted file'
    echo '  git add <resolved-files>'
    echo '  git commit'
    echo
    echo 'Abort instead:'
    echo "  git -C '$INTEGRATION_WORKTREE' merge --abort"
  } > "$report"
  printf '%s\n' "$report"
}

notify_existing_integration_conflict() {
  local paths report_body
  paths="$(unmerged_paths "$INTEGRATION_WORKTREE" || true)"
  report_body="$(printf 'Hay una fusión pendiente en %s.\nNo se propagará ninguna rama hasta resolverla o abortarla.\n\nFicheros:\n%s' \
    "$SOURCE_BRANCH" "${paths:-'(consulta git status)'}")"
  alert_once "integration-conflict" 'R4R: conflicto Git pendiente' "$report_body" "$INTEGRATION_WORKTREE"
}

integration_must_be_ready() {
  if merge_in_progress "$INTEGRATION_WORKTREE"; then
    notify_existing_integration_conflict
    return 4
  fi
  local dirty
  dirty="$(git -C "$INTEGRATION_WORKTREE" status --porcelain=v1 --untracked-files=all)"
  if [[ -n "$dirty" ]]; then
    alert_once "integration-dirty" 'R4R: integration tiene cambios sin commit' \
      "$(printf 'El sincronizador no puede centralizar ramas mientras el worktree de integration esté sucio.\n\n%s' \
        "$(printf '%s\n' "$dirty" | sed -n '1,30p')")" \
      "$INTEGRATION_WORKTREE"
    return 4
  fi
  clear_alert "integration-conflict"
  clear_alert "integration-dirty"
  return 0
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

select_default_targets() {
  local branch
  while IFS= read -r branch; do
    case "$branch" in
      "$SOURCE_BRANCH"|main|master|backup/*|agent/snapshots|*claude-surgical*) continue ;;
      r4r-chatgpt|agent/*) TARGETS+=("$branch") ;;
    esac
  done < <(git -C "$REPOSITORY" for-each-ref --format='%(refname:short)' refs/heads | sort)
}

collect_ref_into_hub() {
  local incoming="$1" label="$2" merge_log before report paths body
  if git -C "$INTEGRATION_WORKTREE" merge-base --is-ancestor "$incoming" HEAD; then
    log "$label: already centralized"
    return 0
  fi

  merge_log="$LOG_ROOT/collect-$(sanitize "$label").log"
  before="$(git -C "$INTEGRATION_WORKTREE" rev-parse HEAD)"
  log "$label: merging into $SOURCE_BRANCH"

  if "$DRY_RUN"; then
    log "DRY-RUN: git -C $INTEGRATION_WORKTREE merge --no-ff --no-edit $incoming"
    return 0
  fi

  if git -C "$INTEGRATION_WORKTREE" merge --no-ff --no-edit "$incoming" \
      >"$merge_log" 2>&1; then
    UPDATED_HUB+=("$label")
    clear_alert "collect-$label"
    return 0
  fi

  if merge_in_progress "$INTEGRATION_WORKTREE" || [[ -n "$(unmerged_paths "$INTEGRATION_WORKTREE" || true)" ]]; then
    report="$(write_conflict_report "$label" "$merge_log")"
    paths="$(unmerged_paths "$INTEGRATION_WORKTREE" || true)"
    body="$(printf 'Conflicto al integrar %s en %s.\nLa fusión queda abierta para resolución manual.\n\nFicheros:\n%s\n\nInforme: %s' \
      "$label" "$SOURCE_BRANCH" "${paths:-'(consulta git status)'}" "$report")"
    alert_once "collect-$label" 'R4R: conflicto al centralizar rama' "$body" "$INTEGRATION_WORKTREE"
    FAILED+=("$label:conflict")
    return 4
  fi

  git -C "$INTEGRATION_WORKTREE" merge --abort >/dev/null 2>&1 || true
  git -C "$INTEGRATION_WORKTREE" reset --hard "$before" >/dev/null 2>&1 || true
  body="$(printf 'Falló la integración de %s sin conflicto resoluble.\nSe restauró %s a %s.\n\n%s' \
    "$label" "$SOURCE_BRANCH" "${before:0:12}" "$(sed -n '1,40p' "$merge_log")")"
  alert_once "collect-$label" 'R4R: fallo al centralizar rama' "$body" "$INTEGRATION_WORKTREE"
  FAILED+=("$label:merge-failed")
  return 1
}

propagate_regular_branch() {
  local branch="$1" worktree temporary=false branch_tip merge_log dirty
  branch_exists "$branch" || { FAILED+=("$branch:missing"); return 1; }

  if git -C "$REPOSITORY" merge-base --is-ancestor "$SOURCE_COMMIT" "$branch"; then
    log "$branch: already contains ${SOURCE_COMMIT:0:12}"
    push_ref "$branch" || FAILED+=("$branch:push")
    return 0
  fi

  branch_tip="$(git -C "$REPOSITORY" rev-parse "$branch")"
  if ! git -C "$REPOSITORY" merge-base --is-ancestor "$branch_tip" "$SOURCE_COMMIT"; then
    alert_once "race-$branch" 'R4R: rama cambió durante la sincronización' \
      "$(printf '%s ya contiene commits que no estaban en el snapshot de integration.\nNo se forzará ni se hará merge inverso. Se recogerá en el siguiente ciclo.' "$branch")" \
      "$(worktree_for_branch "$branch" || printf '%s' "$REPOSITORY")"
    SKIPPED+=("$branch:changed-after-collection")
    return 1
  fi

  if worktree="$(worktree_for_branch "$branch")"; then
    worktree="$(realpath -e "$worktree")"
  else
    TMP_ROOT="${TMP_ROOT:-$(mktemp -d "${TMPDIR:-/tmp}/r4r-hub-sync.XXXXXX")}" 
    worktree="$TMP_ROOT/$(sanitize "$branch")"
    if "$DRY_RUN"; then
      log "DRY-RUN: temporary worktree for $branch at $worktree"
      worktree="$REPOSITORY"
    else
      git -C "$REPOSITORY" worktree add --quiet "$worktree" "$branch"
      temporary=true
    fi
  fi

  dirty="$(git -C "$worktree" status --porcelain=v1 --untracked-files=all)"
  if [[ -n "$dirty" ]]; then
    alert_once "dirty-$branch" 'R4R: rama no propagada por cambios locales' \
      "$(printf '%s tiene cambios sin commit. No se ha tocado.\n\n%s' "$branch" \
        "$(printf '%s\n' "$dirty" | sed -n '1,30p')")" "$worktree"
    SKIPPED+=("$branch:dirty")
    "$temporary" && git -C "$REPOSITORY" worktree remove --force "$worktree" >/dev/null 2>&1 || true
    return 1
  fi

  merge_log="$LOG_ROOT/propagate-$(sanitize "$branch").log"
  log "$branch: fast-forwarding to ${SOURCE_COMMIT:0:12}"
  if "$DRY_RUN"; then
    log "DRY-RUN: git -C $worktree merge --ff-only $SOURCE_COMMIT"
  elif git -C "$worktree" merge --ff-only "$SOURCE_COMMIT" >"$merge_log" 2>&1; then
    UPDATED_TARGETS+=("$branch")
    clear_alert "dirty-$branch"
    clear_alert "race-$branch"
  else
    alert_once "propagate-$branch" 'R4R: fallo al propagar integration' \
      "$(printf 'No se pudo hacer fast-forward de %s a %s.\n\n%s' "$branch" "${SOURCE_COMMIT:0:12}" \
        "$(sed -n '1,40p' "$merge_log")")" "$worktree"
    FAILED+=("$branch:propagate")
    "$temporary" && git -C "$REPOSITORY" worktree remove --force "$worktree" >/dev/null 2>&1 || true
    return 1
  fi

  push_ref "$branch" || FAILED+=("$branch:push")
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

if "$FETCH"; then
  if remote_exists; then
    log "fetching $REMOTE"
    if ! git -C "$REPOSITORY" fetch --prune "$REMOTE"; then
      alert_once 'fetch' 'R4R: fallo de fetch' \
        "No se pudo actualizar el remoto $REMOTE. La sincronización local continuará con las referencias disponibles." \
        "$REPOSITORY"
      FAILED+=("fetch:$REMOTE")
    else
      clear_alert 'fetch'
    fi
  else
    warn "remote $REMOTE unavailable; fetch skipped"
  fi
fi

git -C "$REPOSITORY" show-ref --verify --quiet "refs/heads/$SOURCE_BRANCH" \
  || die "local hub branch does not exist: $SOURCE_BRANCH"

if ! "$TARGETS_EXPLICIT"; then
  select_default_targets
fi
((${#TARGETS[@]} > 0)) || die "no synchronization targets selected"
mapfile -t TARGETS < <(printf '%s\n' "${TARGETS[@]}" | awk 'NF && !seen[$0]++')

INTEGRATION_WORKTREE="$(resolve_integration_worktree || true)"
if [[ -z "$INTEGRATION_WORKTREE" ]]; then
  alert_once 'missing-integration-worktree' 'R4R: falta el worktree de integration' \
    "$(printf 'No se encontró un worktree limpio que tenga checkout de %s.\nRuta esperada: %s' \
      "$SOURCE_BRANCH" "$INTEGRATION_WORKTREE_HINT")" \
    "$DEVELOPMENT_ROOT"
  exit 4
fi
clear_alert 'missing-integration-worktree'

log "repository:            $REPOSITORY"
log "integration worktree:  $INTEGRATION_WORKTREE"
log "hub branch:            $SOURCE_BRANCH"
log "targets:               ${TARGETS[*]}"

integration_must_be_ready || exit $?

if "$COLLECT"; then
  # First absorb the published integration tip when it is ahead locally.
  if "$FETCH" && remote_ref_exists "$SOURCE_BRANCH"; then
    collect_ref_into_hub "$REMOTE/$SOURCE_BRANCH" "$REMOTE/$SOURCE_BRANCH" || {
      code=$?; ((code == 4)) && exit 4
    }
  fi

  for branch in "${TARGETS[@]}"; do
    branch_exists "$branch" || { warn "$branch: local branch missing"; FAILED+=("$branch:missing"); continue; }
    collect_ref_into_hub "$branch" "$branch" || {
      code=$?; ((code == 4)) && exit 4
    }
    # Include a fetched remote-only advance as well. It will later fast-forward
    # the corresponding local branch during propagation.
    if "$FETCH" && remote_ref_exists "$branch"; then
      collect_ref_into_hub "$REMOTE/$branch" "$REMOTE/$branch" || {
        code=$?; ((code == 4)) && exit 4
      }
    fi
  done
fi

SOURCE_COMMIT="$(git -C "$INTEGRATION_WORKTREE" rev-parse HEAD)"
log "pinned integration commit: $SOURCE_COMMIT"
push_ref "$SOURCE_BRANCH" || FAILED+=("$SOURCE_BRANCH:push")

if "$PROPAGATE"; then
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
    propagate_regular_branch "$branch" || true
  done

  if "$SYNC_WORKERS" && { "$pc_selected" || "$lp_selected"; }; then
    PC_BRANCH='agent/pc-qwen3-worker'
    LP_BRANCH='agent/laptop-qwen3-worker'
    if ! branch_exists "$PC_BRANCH" || ! branch_exists "$LP_BRANCH"; then
      FAILED+=("workers:missing-branch")
      WORKER_SYNC_OK=false
    elif git -C "$REPOSITORY" merge-base --is-ancestor "$SOURCE_COMMIT" "$PC_BRANCH" \
        && git -C "$REPOSITORY" merge-base --is-ancestor "$SOURCE_COMMIT" "$LP_BRANCH"; then
      log "PC and LP already contain ${SOURCE_COMMIT:0:12}"
    else
      RING_WORKTREE="$(resolve_ring_runtime_worktree || true)"
      PC_WORKTREE="$(worktree_for_branch "$PC_BRANCH" || true)"
      LP_WORKTREE="$(worktree_for_branch "$LP_BRANCH" || true)"
      MERGER="$ROOT/scripts/merge-worker-branches-and-restart.sh"
      if [[ -z "$RING_WORKTREE" || -z "$PC_WORKTREE" || -z "$LP_WORKTREE" || ! -x "$MERGER" ]]; then
        alert_once 'worker-resolution' 'R4R: no se pueden propagar PC/LP' \
          "$(printf 'Falta un worktree o el merger seguro.\nRing=%s\nPC=%s\nLP=%s\nMerger=%s' \
            "${RING_WORKTREE:-MISSING}" "${PC_WORKTREE:-MISSING}" "${LP_WORKTREE:-MISSING}" "$MERGER")" \
          "$ROOT"
        FAILED+=("workers:resolution")
        WORKER_SYNC_OK=false
      else
        command=("$MERGER" --source "$SOURCE_COMMIT" --ring "$RING_WORKTREE" --pc "$PC_WORKTREE" --lp "$LP_WORKTREE")
        "$DRY_RUN" && command+=(--dry-run)
        if "${command[@]}"; then
          "$DRY_RUN" || UPDATED_TARGETS+=("$PC_BRANCH" "$LP_BRANCH")
          push_ref "$PC_BRANCH" || FAILED+=("$PC_BRANCH:push")
          push_ref "$LP_BRANCH" || FAILED+=("$LP_BRANCH:push")
          clear_alert 'worker-resolution'
          clear_alert 'worker-merge'
        else
          alert_once 'worker-merge' 'R4R: fallo al propagar PC/LP' \
            "El merger seguro de workers falló. Revisa los worktrees y su evidencia runtime." \
            "$RING_WORKTREE"
          FAILED+=("workers:merge-or-restart")
          WORKER_SYNC_OK=false
        fi
      fi
    fi
  fi
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

printf '\n[r4r-hub-sync] SUMMARY\n'
printf '  centralized into %s: %s\n' "$SOURCE_BRANCH" "${UPDATED_HUB[*]:-(none)}"
printf '  propagated:             %s\n' "${UPDATED_TARGETS[*]:-(none)}"
printf '  skipped:                %s\n' "${SKIPPED[*]:-(none)}"
printf '  failed:                 %s\n' "${FAILED[*]:-(none)}"
printf '  integration commit:     %s\n' "$SOURCE_COMMIT"

if ((${#FAILED[@]} > 0 || ${#SKIPPED[@]} > 0)); then
  exit 3
fi

log "hub collection and branch propagation completed"
