#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_VERSION="4.1.0-recent-unified-opencode"
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
SCRIPT_ROOT="$(dirname -- "$SCRIPT_DIR")"
REPO="${1:-${R4R_RING_WORKTREE:-$SCRIPT_ROOT}}"
REPO="$(realpath -e -- "$REPO" 2>/dev/null)" || {
  printf 'ERROR: el repositorio no existe: %s\n' "$REPO" >&2
  exit 1
}
OUT_DIR="${2:-$REPO/runtime/exports/complete}"
STAMP="$(date -u +%Y%m%dT%H%M%SZ)"
NAME="r4r-complete-evidence-$STAMP"
WORK_DIR=""
RECENT_AGENT_SESSIONS="${R4R_EVIDENCE_RECENT_AGENT_SESSIONS:-3}"
RECENT_WORKER_RUNS="${R4R_EVIDENCE_RECENT_WORKER_RUNS:-2}"
RECENT_ARCHIVED_LOGS="${R4R_EVIDENCE_RECENT_ARCHIVED_LOGS:-4}"

fail() {
  printf 'ERROR: %s\n' "$*" >&2
  exit 1
}

cleanup() {
  if [[ -n "$WORK_DIR" && -d "$WORK_DIR" ]]; then
    rm -rf -- "$WORK_DIR"
  fi
}
on_error() {
  local exit_code="$?"
  printf 'ERROR: exportación interrumpida en la línea %s (código %s)\n' \
    "${BASH_LINENO[0]:-unknown}" "$exit_code" >&2
  exit "$exit_code"
}
trap cleanup EXIT INT TERM
trap on_error ERR

for command_name in git zip sha256sum python3; do
  command -v "$command_name" >/dev/null 2>&1 \
    || fail "$command_name no está instalado"
done
for numeric_value in \
  "$RECENT_AGENT_SESSIONS" \
  "$RECENT_WORKER_RUNS" \
  "$RECENT_ARCHIVED_LOGS"
do
  [[ "$numeric_value" =~ ^[1-9][0-9]*$ ]] \
    || fail "los límites de historial deben ser enteros positivos"
done
git -C "$REPO" rev-parse --is-inside-work-tree >/dev/null 2>&1 \
  || fail "no es un worktree Git válido: $REPO"

mkdir -p -- "$OUT_DIR"
OUT_DIR="$(realpath -e -- "$OUT_DIR")"
ZIP="$OUT_DIR/$NAME.zip"
[[ ! -e "$ZIP" ]] || fail "el destino ya existe: $ZIP"

WORK_DIR="$(mktemp -d "$OUT_DIR/.r4r-evidence-stage.XXXXXX")"
BUNDLE="$WORK_DIR/$NAME"
SNAPSHOT="$BUNDLE/diagnostics"
mkdir -p -- "$SNAPSHOT"

declare -a CANDIDATES=(
  "runtime/ring-system/supervisor.log"
  "runtime/ring-system/ring-agent.console.log"
  "runtime/control"
  "runtime/the-ring-heartbeats"
  ".ring-agent/state.json"
  ".ring-agent/code-pc-review.md"
  ".ring-agent/code-lp-review.md"
  ".ring-agent/fullstack-handoff.md"
  ".ring-agent/global-summary.md"
  ".ring-agent/worker-understanding.md"
  ".opencode/task-plan.json"
  ".opencode/progress.pc.json"
  ".opencode/progress.lp.json"
  ".opencode/memory.pc.md"
  ".opencode/memory.lp.md"
  "config/r4r-agents.json"
  "opencode.jsonc"
  "AGENTS.md"
)
declare -a INCLUDED=()

copy_source_path() {
  local source="$1" destination="$2" label="$3"
  [[ -e "$source" || -L "$source" ]] || return 0
  mkdir -p -- "$(dirname -- "$destination")"
  if ! cp -a -- "$source" "$destination" \
      2>>"$SNAPSHOT/copy-warnings.txt"; then
    printf 'AVISO: %s cambió durante la copia; se conserva la instantánea parcial.\n' \
      "$label" >>"$SNAPSHOT/copy-warnings.txt"
  fi
  INCLUDED+=("$label")
}

copy_evidence_path() {
  local relative="$1"
  copy_source_path "$REPO/$relative" "$BUNDLE/$relative" "$relative"
}

copy_recent_directories() {
  local source_root="$1" destination_root="$2" limit="$3" label_prefix="$4"
  [[ -d "$source_root" ]] || return 0
  local -a selected=()
  local list_file
  list_file="$(mktemp "$WORK_DIR/recent-directories.XXXXXX")"
  {
    find "$source_root" -mindepth 1 -maxdepth 1 -type d \
      -printf '%T@\t%f\n' 2>/dev/null \
      | sort -rn \
      | awk -F '\t' -v limit="$limit" 'NR <= limit {print $2}'
  } >"$list_file" || true
  mapfile -t selected <"$list_file"
  local name
  for name in "${selected[@]}"; do
    [[ -n "$name" ]] || continue
    copy_source_path \
      "$source_root/$name" \
      "$destination_root/$name" \
      "$label_prefix/$name"
  done
}

copy_recent_files() {
  local source_root="$1" destination_root="$2" limit="$3" label_prefix="$4"
  [[ -d "$source_root" ]] || return 0
  local -a selected=()
  local list_file
  list_file="$(mktemp "$WORK_DIR/recent-files.XXXXXX")"
  {
    find "$source_root" -mindepth 1 -maxdepth 1 -type f \
      -printf '%T@\t%f\n' 2>/dev/null \
      | sort -rn \
      | awk -F '\t' -v limit="$limit" 'NR <= limit {print $2}'
  } >"$list_file" || true
  mapfile -t selected <"$list_file"
  local name
  for name in "${selected[@]}"; do
    [[ -n "$name" ]] || continue
    copy_source_path \
      "$source_root/$name" \
      "$destination_root/$name" \
      "$label_prefix/$name"
  done
}

for relative in "${CANDIDATES[@]}"; do
  copy_evidence_path "$relative"
done

for agent in ring pc lp; do
  copy_recent_directories \
    "$REPO/runtime/ring-agent/$agent" \
    "$BUNDLE/runtime/ring-agent/$agent" \
    "$RECENT_AGENT_SESSIONS" \
    "runtime/ring-agent/$agent"
done
copy_recent_files \
  "$REPO/runtime/ring-agent/guardian" \
  "$BUNDLE/runtime/ring-agent/guardian" \
  "$((RECENT_AGENT_SESSIONS * 2))" \
  "runtime/ring-agent/guardian"
copy_recent_files \
  "$REPO/runtime/ring-system/archive" \
  "$BUNDLE/runtime/ring-system/archive" \
  "$RECENT_ARCHIVED_LOGS" \
  "runtime/ring-system/archive"

if [[ -d "$REPO/.ring-agent/evidence" ]]; then
  EVIDENCE_TASK_DIRS="$WORK_DIR/evidence-task-directories.list"
  find "$REPO/.ring-agent/evidence" -mindepth 1 -maxdepth 1 -type d \
    -print | sort >"$EVIDENCE_TASK_DIRS" || true
  while IFS= read -r task_dir; do
    task_name="$(basename -- "$task_dir")"
    copy_recent_files \
      "$task_dir" \
      "$BUNDLE/.ring-agent/evidence/$task_name" \
      2 \
      ".ring-agent/evidence/$task_name"
  done <"$EVIDENCE_TASK_DIRS"
fi

((${#INCLUDED[@]} > 0)) \
  || fail "no se encontraron rutas de evidencia en $REPO"

capture() {
  local output="$1"
  shift
  {
    printf '$'
    printf ' %q' "$@"
    printf '\n'
    "$@"
  } >"$output" 2>&1 || true
}

capture "$SNAPSHOT/system.txt" uname -a
{
  printf 'Generated UTC:   %s\n' "$(date -u --iso-8601=seconds)"
  printf 'Generated local: %s\n' "$(date --iso-8601=seconds)"
  printf 'Hostname:        %s\n' "$(hostname 2>/dev/null || printf unavailable)"
  printf '\n=== UPTIME ===\n'
  uptime 2>&1 || true
  printf '\n=== MEMORY ===\n'
  free -h 2>&1 || true
  printf '\n=== DISK ===\n'
  df -h -- "$REPO" "$OUT_DIR" 2>&1 || true
  printf '\n=== LOAD ===\n'
  cat /proc/loadavg 2>/dev/null || true
} >>"$SNAPSHOT/system.txt"

{
  printf '=== RELEVANT PROCESS TREE ===\n'
  ps -eo pid,ppid,pgid,sid,stat,etime,wchan:28,args --forest 2>&1 \
    | awk 'NR == 1 || tolower($0) ~ /(r4r|opencode|ollama|run-ring-agent|run-opencode-worker)/' \
    || true
  printf '\n=== RELEVANT PROCESS MATCHES ===\n'
  pgrep -af 'run-ring-agent.py|run-ring-system.py|run-opencode-worker|opencode|ollama' \
    2>&1 || true
} >"$SNAPSHOT/processes.txt"

{
  printf '=== GIT WORKTREES ===\n'
  git -C "$REPO" worktree list --porcelain 2>&1 || true
  printf '\n=== GIT COMMON DIRECTORY ===\n'
  git -C "$REPO" rev-parse --git-common-dir 2>&1 || true
  printf '\n=== REMOTE NAMES ===\n'
  git -C "$REPO" remote 2>&1 || true
} >"$SNAPSHOT/git-topology.txt"

WORKTREE_LIST="$WORK_DIR/worktrees.list"
git -C "$REPO" worktree list --porcelain 2>/dev/null \
  | sed -n 's/^worktree //p' >"$WORKTREE_LIST" || true
mapfile -t WORKTREES <"$WORKTREE_LIST"
WORKER_BRANCHES_FILE="$WORK_DIR/worker-branches.list"
python3 - "$REPO/config/r4r-agents.json" >"$WORKER_BRANCHES_FILE" <<'PY'
import json
import sys
from pathlib import Path

try:
    value = json.loads(Path(sys.argv[1]).read_text(encoding="utf-8"))
except (OSError, json.JSONDecodeError):
    value = {}
agents = value.get("agents", {})
for worker in ("PC", "LP"):
    agent = agents.get(worker, {})
    print(str(agent.get("branch", "")))
PY
mapfile -t WORKER_BRANCHES <"$WORKER_BRANCHES_FILE"
PC_BRANCH="${WORKER_BRANCHES[0]:-agent/pc-qwen3-worker}"
LP_BRANCH="${WORKER_BRANCHES[1]:-agent/laptop-qwen3-worker}"
worktree_number=0
for worktree in "${WORKTREES[@]}"; do
  [[ -n "$worktree" ]] || continue
  worktree_number=$((worktree_number + 1))
  report="$SNAPSHOT/git-worktree-$(printf '%02d' "$worktree_number").txt"
  branch="$(git -C "$worktree" branch --show-current 2>/dev/null || true)"
  {
    printf 'Worktree: %s\n' "$worktree"
    printf 'Branch:   %s\n' "$branch"
    printf 'HEAD:     %s\n' "$(git -C "$worktree" rev-parse HEAD 2>/dev/null || true)"
    printf '\n=== STATUS ===\n'
    git -C "$worktree" status --short --branch 2>&1 || true
    printf '\n=== LAST 30 COMMITS ===\n'
    git -C "$worktree" log -30 --date=iso-strict \
      --pretty=format:'%h%x09%ad%x09%an%x09%d %s' 2>&1 || true
    printf '\n\n=== DIFF STAT ===\n'
    git -C "$worktree" diff --stat 2>&1 || true
    printf '\n=== DIFF CHECK ===\n'
    git -C "$worktree" diff --check 2>&1 || true
    printf '\n=== STAGED DIFF STAT ===\n'
    git -C "$worktree" diff --cached --stat 2>&1 || true
    printf '\n=== STAGED DIFF CHECK ===\n'
    git -C "$worktree" diff --cached --check 2>&1 || true
  } >"$report"

  worker=""
  case "$branch" in
    "$PC_BRANCH") worker="PC" ;;
    "$LP_BRANCH") worker="LP" ;;
  esac
  if [[ -n "$worker" ]]; then
    copy_recent_directories \
      "$worktree/runtime/runs/$worker" \
      "$BUNDLE/worker-runtime/$worker/runs" \
      "$RECENT_WORKER_RUNS" \
      "worker-runtime/$worker/runs"
    worker_lower="${worker,,}"
    copy_source_path \
      "$worktree/.opencode/progress.$worker_lower.json" \
      "$BUNDLE/worker-runtime/$worker/progress.json" \
      "worker-runtime/$worker/progress.json"
    copy_source_path \
      "$worktree/.opencode/memory.$worker_lower.md" \
      "$BUNDLE/worker-runtime/$worker/memory.md" \
      "worker-runtime/$worker/memory.md"
  fi
done

{
  printf '=== R4R USER UNITS ===\n'
  if command -v systemctl >/dev/null 2>&1; then
    systemctl --user list-units --all --no-pager 'r4r-*' 2>&1 || true
    printf '\n=== R4R USER TIMERS ===\n'
    systemctl --user list-timers --all --no-pager 'r4r-*' 2>&1 || true
  else
    printf 'systemctl no está disponible\n'
  fi
} >"$SNAPSHOT/systemd.txt"

if command -v journalctl >/dev/null 2>&1; then
  UNIT_LIST="$WORK_DIR/systemd-units.list"
  systemctl --user list-units --all --no-legend 'r4r-*' 2>/dev/null \
    | awk '{print $1}' \
    | grep -E '^r4r-[A-Za-z0-9_.@:-]+\.(service|timer)$' \
    | sort -u >"$UNIT_LIST" || true
  mapfile -t USER_UNITS <"$UNIT_LIST"
  {
    if ((${#USER_UNITS[@]} == 0)); then
      printf 'No se encontraron unidades de usuario r4r-* cargadas.\n'
    fi
    for unit in "${USER_UNITS[@]}"; do
      printf '\n===== %s: STATUS =====\n' "$unit"
      systemctl --user status --no-pager --full "$unit" 2>&1 || true
      printf '\n===== %s: LAST 500 JOURNAL LINES =====\n' "$unit"
      journalctl --user --no-pager -n 500 -u "$unit" 2>&1 || true
    done
  } >"$SNAPSHOT/systemd-details.txt"
fi

{
  printf 'R4R COMPLETE EVIDENCE\n'
  printf 'Exporter version: %s\n' "$SCRIPT_VERSION"
  printf 'Generated UTC:    %s\n' "$(date -u --iso-8601=seconds)"
  printf 'Generated local:  %s\n' "$(date --iso-8601=seconds)"
  printf 'Repository:       %s\n' "$REPO"
  printf 'Branch:           %s\n' "$(git -C "$REPO" branch --show-current)"
  printf 'HEAD:             %s\n' "$(git -C "$REPO" rev-parse HEAD)"
  printf 'Recent Ring/PC/LP sessions each: %s\n' "$RECENT_AGENT_SESSIONS"
  printf 'Recent worker controller runs each: %s\n' "$RECENT_WORKER_RUNS"
  printf 'Recent archived supervisor logs: %s\n' "$RECENT_ARCHIVED_LOGS"
  printf '\n=== COPIED EVIDENCE PATHS ===\n'
  printf '%s\n' "${INCLUDED[@]}"
  printf '\n=== SECURITY ===\n'
  printf '%s\n' \
    'Credential files are excluded.' \
    'Text files are scrubbed for common API keys, bearer tokens and secret values.' \
    'Working-tree patch contents are not exported; only status, stat and diff-check results are included.'
} >"$BUNDLE/MANIFEST.txt"

# Remove credential containers before inspecting or compressing the staging tree.
find "$BUNDLE" -type f \
  \( -name '.env' -o -name '.env.*' -o -iname '*credentials*' \
     -o -iname '*secret*.json' -o -iname '*secret*.env' \
     -o -iname 'auth.json' -o -iname '*.pem' -o -iname '*.key' \) \
  -delete

python3 - "$BUNDLE" <<'PY'
from __future__ import annotations

from pathlib import Path
import os
import re
import shutil
import sys
import tempfile

root = Path(sys.argv[1])
patterns = [
    re.compile(r"(?i)(bearer)[ \\t]+[A-Za-z0-9._~+/=-]{12,}"),
    re.compile(r"(?<![A-Za-z0-9])(?:sk|sess)-[A-Za-z0-9_-]{12,}"),
    re.compile(r"(?<![A-Za-z0-9])gh[pousr]_[A-Za-z0-9]{20,}"),
    re.compile(r"(?i)(https?://[^/\s:@]+:)[^@\s/]+@"),
    re.compile(
        r'''(?ix)
        (\b[A-Za-z0-9_-]*(?:api[_-]?key|access[_-]?token|refresh[_-]?token|
        password|passwd|client[_-]?secret|authorization|credential)\b
        ["']?\s*(?:[:=]\s*|[ \t]+)["']?)
        ([^\s,"'}]+)
        '''
    ),
]

text_suffixes = {
    ".cfg", ".conf", ".err", ".ini", ".json", ".jsonc", ".log", ".md",
    ".out", ".properties", ".text", ".toml", ".tsv", ".txt", ".yaml", ".yml",
}

redacted_files: list[str] = []
skipped_binary: list[str] = []
for path in root.rglob("*"):
    if not path.is_file() or path.is_symlink():
        continue
    try:
        with path.open("rb") as source:
            sample = source.read(65536)
    except OSError:
        continue
    known_text = path.suffix.lower() in text_suffixes or path.name == "MANIFEST.txt"
    if b"\0" in sample:
        skipped_binary.append(str(path.relative_to(root)))
        continue
    if not known_text:
        try:
            sample.decode("utf-8")
        except UnicodeDecodeError:
            skipped_binary.append(str(path.relative_to(root)))
            continue

    changed = False
    temporary_name = ""
    try:
        with path.open("r", encoding="utf-8", errors="surrogateescape") as source:
            with tempfile.NamedTemporaryFile(
                "w",
                encoding="utf-8",
                errors="surrogateescape",
                dir=path.parent,
                delete=False,
            ) as destination:
                temporary_name = destination.name
                for line in source:
                    updated = line
                    for pattern in patterns:
                        if pattern.groups >= 2:
                            updated = pattern.sub(r"\1[REDACTED]", updated)
                        else:
                            updated = pattern.sub("[REDACTED]", updated)
                    changed = changed or updated != line
                    destination.write(updated)
        if changed:
            shutil.copystat(path, temporary_name)
            os.replace(temporary_name, path)
            temporary_name = ""
        else:
            Path(temporary_name).unlink(missing_ok=True)
            temporary_name = ""
    finally:
        if temporary_name:
            Path(temporary_name).unlink(missing_ok=True)

    if changed:
        redacted_files.append(str(path.relative_to(root)))

report = root / "diagnostics" / "redaction-report.txt"
report.write_text(
    "Redacted text files:\n"
    + ("\n".join(redacted_files) if redacted_files else "(none)")
    + "\n\nBinary files not text-scanned:\n"
    + ("\n".join(skipped_binary) if skipped_binary else "(none)")
    + "\n",
    encoding="utf-8",
)
PY

{
  printf 'path\tsize_bytes\tmodified_utc\n'
  find "$BUNDLE" -type f -printf '%P\t%s\t%TY-%Tm-%TdT%TH:%TM:%TSZ\n' \
    | sort
} >"$SNAPSHOT/file-inventory.tsv"

printf 'Repositorio: %s\n' "$REPO"
printf 'Destino:    %s\n' "$ZIP"
printf 'Rutas copiadas: %s\n' "${#INCLUDED[@]}"

(
  cd "$WORK_DIR"
  nice -n 10 zip -q -y -r "$ZIP" "$NAME"
)

zip -T "$ZIP" >/dev/null
[[ -s "$ZIP" ]] || fail "el ZIP no se creó o está vacío"

printf '\nCREADO CORRECTAMENTE\n'
ls -lh -- "$ZIP"
printf 'SHA-256: %s\n' "$(sha256sum "$ZIP" | awk '{print $1}')"
printf '\nAdjunta únicamente este fichero:\n%s\n' "$ZIP"
