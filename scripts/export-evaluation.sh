#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_VERSION="3.0.0-dual"
ROOT="${R4R_REPO:-$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)}"
ROOT="$(realpath "$ROOT")"
cd "$ROOT"

RUNS_DIR="$ROOT/runtime/runs"
EXPORTS_DIR="$ROOT/runtime/exports"

ACTIVE_WORK_DIR=""
cleanup_active_work_dir() {
  if [[ -n "$ACTIVE_WORK_DIR" && -d "$ACTIVE_WORK_DIR" ]]; then
    rm -rf "$ACTIVE_WORK_DIR"
  fi
}
trap cleanup_active_work_dir EXIT INT TERM

WORKER=""
ALL_WORKERS=false
OFFSET=0
EXPLICIT_RUN=""
DELETE_SOURCE=false
INCLUDE_CONTROL=true
POSITIONAL_SEEN=false

usage() {
  cat <<'USAGE'
Usage:
  ./scripts/export-evaluation.sh
  ./scripts/export-evaluation.sh [OFFSET]
  ./scripts/export-evaluation.sh RUN_ID_OR_DIRECTORY
  ./scripts/export-evaluation.sh --worker PC|LP [--offset 0|-N]
  ./scripts/export-evaluation.sh --worker PC|LP --run RUN_ID_OR_DIRECTORY
  ./scripts/export-evaluation.sh --all-workers [--offset 0|-N]

Options:
  --worker PC|LP       Export one worker.
  --all-workers        Export the selected offset for PC and LP independently.
  --offset 0|-N        0 = latest; -1 = previous; -2 = two runs before latest.
  --run VALUE          Explicit run ID or directory.
  --delete-source      Delete the source run only after ZIP verification and
                       only when no agent process is active and state.json says
                       the run finished.
  --keep-source        Keep the source run. This is the default.
  --no-control         Do not snapshot runtime/control/<WORKER>.
  -h, --help           Show this help.

Backward compatibility:
  A single positional OFFSET or explicit run path/name is accepted.

Current dual layout:
  runtime/runs/PC/<RUN_ID>/
  runtime/runs/LP/<RUN_ID>/

Legacy direct timestamp directories under runtime/runs/ are readable, but are
never selected by --worker PC or --worker LP.

Output:
  runtime/exports/<WORKER>/r4r-evaluation-<WORKER>-<TIMESTAMP>.zip

Safety:
  Source runs are retained by default.
  The script does not modify tracked files or .git/info/exclude.
  It never packages .env, database data, node_modules, build output or venvs.
USAGE
}

die() {
  echo "ERROR: $*" >&2
  exit 2
}

while (($#)); do
  case "$1" in
    --worker)
      [[ $# -ge 2 ]] || die "falta valor para --worker"
      WORKER="${2^^}"
      shift 2
      ;;
    --all-workers)
      ALL_WORKERS=true
      shift
      ;;
    --offset)
      [[ $# -ge 2 ]] || die "falta valor para --offset"
      OFFSET="$2"
      shift 2
      ;;
    --run)
      [[ $# -ge 2 ]] || die "falta valor para --run"
      EXPLICIT_RUN="$2"
      shift 2
      ;;
    --delete-source)
      DELETE_SOURCE=true
      shift
      ;;
    --keep-source)
      DELETE_SOURCE=false
      shift
      ;;
    --no-control)
      INCLUDE_CONTROL=false
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    --)
      shift
      break
      ;;
    -*)
      # A negative integer is accepted as the legacy positional OFFSET.
      if [[ "$1" =~ ^-[0-9]+$ ]] && [[ "$POSITIONAL_SEEN" == false ]]; then
        OFFSET="$1"
        POSITIONAL_SEEN=true
        shift
      else
        die "opción desconocida: $1"
      fi
      ;;
    *)
      [[ "$POSITIONAL_SEEN" == false ]] || die "solo se admite un argumento posicional"
      POSITIONAL_SEEN=true
      if [[ "$1" =~ ^-?[0-9]+$ ]]; then
        OFFSET="$1"
      else
        EXPLICIT_RUN="$1"
      fi
      shift
      ;;
  esac
done

[[ "$WORKER" == "" || "$WORKER" == "PC" || "$WORKER" == "LP" ]] \
  || die "--worker debe ser PC o LP"

[[ "$OFFSET" =~ ^-?[0-9]+$ ]] || die "OFFSET debe ser un entero"
(( OFFSET <= 0 )) || die "OFFSET debe ser 0 o negativo"

if [[ "$ALL_WORKERS" == true && -n "$WORKER" ]]; then
  die "--all-workers y --worker son incompatibles"
fi
if [[ "$ALL_WORKERS" == true && -n "$EXPLICIT_RUN" ]]; then
  die "--all-workers y --run son incompatibles"
fi

command -v git >/dev/null 2>&1 || die "git no está disponible"
command -v python3 >/dev/null 2>&1 || die "python3 no está disponible"
git -C "$ROOT" rev-parse --is-inside-work-tree >/dev/null 2>&1 \
  || die "no es un worktree Git: $ROOT"

mkdir -p "$RUNS_DIR" "$EXPORTS_DIR"

is_run_id() {
  [[ "$1" =~ ^[0-9]{8}T[0-9]{6}Z$ ]]
}

formatted_run_id() {
  local run_id="$1"
  if [[ "$run_id" =~ ^([0-9]{4})([0-9]{2})([0-9]{2})T([0-9]{2})([0-9]{2})([0-9]{2})Z$ ]]; then
    printf '%s-%s-%sT%s-%s-%sZ\n' \
      "${BASH_REMATCH[1]}" "${BASH_REMATCH[2]}" "${BASH_REMATCH[3]}" \
      "${BASH_REMATCH[4]}" "${BASH_REMATCH[5]}" "${BASH_REMATCH[6]}"
    return 0
  fi
  return 1
}

list_worker_runs() {
  local worker="$1"
  local parent="$RUNS_DIR/$worker"
  [[ -d "$parent" ]] || return 0
  find "$parent" -mindepth 1 -maxdepth 1 -type d -printf '%f\t%p\n' 2>/dev/null \
    | awk -F '\t' '$1 ~ /^[0-9]{8}T[0-9]{6}Z$/ { print }' \
    | sort -r \
    | cut -f2-
}

list_legacy_runs() {
  find "$RUNS_DIR" -mindepth 1 -maxdepth 1 -type d -printf '%f\t%p\n' 2>/dev/null \
    | awk -F '\t' '$1 ~ /^[0-9]{8}T[0-9]{6}Z$/ { print }' \
    | sort -r \
    | cut -f2-
}

infer_worker_from_run() {
  local run_dir="$1"
  local parent
  parent="$(basename "$(dirname "$run_dir")")"
  case "${parent^^}" in
    PC|LP) printf '%s\n' "${parent^^}" ;;
    *) printf 'LEGACY\n' ;;
  esac
}

resolve_explicit_run() {
  local requested="$1"
  local candidate=""
  local -a matches=()

  if [[ -d "$requested" ]]; then
    realpath "$requested"
    return 0
  fi

  if [[ -n "$WORKER" && -d "$RUNS_DIR/$WORKER/$requested" ]]; then
    realpath "$RUNS_DIR/$WORKER/$requested"
    return 0
  fi

  for candidate in \
    "$RUNS_DIR/PC/$requested" \
    "$RUNS_DIR/LP/$requested" \
    "$RUNS_DIR/$requested"
  do
    [[ -d "$candidate" ]] && matches+=("$(realpath "$candidate")")
  done

  (( ${#matches[@]} > 0 )) || die "no se encontró la ejecución: $requested"
  (( ${#matches[@]} == 1 )) \
    || die "el RUN_ID es ambiguo; usa --worker PC|LP o una ruta explícita: $requested"
  printf '%s\n' "${matches[0]}"
}

resolve_by_offset() {
  local requested_worker="$1"
  local offset="$2"
  local index=$(( -offset ))
  local -a runs=()

  if [[ -n "$requested_worker" ]]; then
    mapfile -t runs < <(list_worker_runs "$requested_worker")
  else
    # Auto mode: select newest globally across PC, LP and legacy runs.
    mapfile -t runs < <(
      {
        while IFS= read -r path; do
          [[ -n "$path" ]] && printf '%s\t%s\n' "$(basename "$path")" "$path"
        done < <(list_worker_runs PC)
        while IFS= read -r path; do
          [[ -n "$path" ]] && printf '%s\t%s\n' "$(basename "$path")" "$path"
        done < <(list_worker_runs LP)
        while IFS= read -r path; do
          [[ -n "$path" ]] && printf '%s\t%s\n' "$(basename "$path")" "$path"
        done < <(list_legacy_runs)
      } | sort -r | cut -f2-
    )
  fi

  (( ${#runs[@]} > 0 )) || {
    if [[ -n "$requested_worker" ]]; then
      die "no hay ejecuciones para $requested_worker bajo runtime/runs/$requested_worker/"
    fi
    die "no hay ejecuciones PC, LP ni legacy bajo runtime/runs/"
  }

  (( index < ${#runs[@]} )) || die \
    "OFFSET $offset fuera de rango: hay ${#runs[@]} ejecuciones seleccionables"

  realpath "${runs[$index]}"
}

resolve_worker_metadata() {
  local worker="$1"
  local output_file="$2"
  : > "$output_file"

  if [[ "$worker" != "PC" && "$worker" != "LP" ]]; then
    return 0
  fi

  if command -v node >/dev/null 2>&1 \
      && [[ -f "$ROOT/scripts/resolve-r4r-config.mjs" ]]; then
    local metadata_path=""
    if metadata_path="$(
      node "$ROOT/scripts/resolve-r4r-config.mjs" --destination "$worker" 2>/dev/null
    )" && [[ -n "$metadata_path" ]]; then
      [[ "$metadata_path" = /* ]] || metadata_path="$ROOT/$metadata_path"
      if [[ -f "$metadata_path" ]]; then
        python3 - "$metadata_path" > "$output_file" <<'PYMETA'
import json
import sys
from pathlib import Path

path = Path(sys.argv[1])
data = json.loads(path.read_text(encoding="utf-8"))
for key in ("plan", "progress", "memory", "controlDir", "opencodeConfig"):
    value = data.get(key)
    if isinstance(value, str) and value.strip():
        print(f"{key}\t{value.strip()}")
PYMETA
        return 0
      fi
    fi
  fi

  # Safe fallback for repositories that predate resolve-r4r-config.mjs.
  if [[ "$worker" == "PC" ]]; then
    printf '%s\n' \
      $'plan\t.opencode/task-plan.json' \
      $'progress\t.opencode/progress.pc.json' \
      $'memory\t.opencode/memory.pc.md' \
      $'controlDir\truntime/control/PC' \
      >> "$output_file"
  else
    printf '%s\n' \
      $'plan\t.opencode/task-plan.json' \
      $'progress\t.opencode/progress.lp.json' \
      $'memory\t.opencode/memory.lp.md' \
      $'controlDir\truntime/control/LP' \
      >> "$output_file"
  fi
}

copy_filtered() {
  local source="$1"
  local destination="$2"
  [[ -e "$source" ]] || return 0

  python3 - "$source" "$destination" <<'PYCOPY'
from pathlib import Path
import shutil
import sys

src = Path(sys.argv[1])
dst = Path(sys.argv[2])

EXCLUDED_DIRS = {
    ".git", ".venv", "node_modules", "target", "dist", ".angular",
    "__pycache__", ".codegraph", ".idea", ".gradle",
}
EXCLUDED_SUFFIXES = {".pyc", ".pyo"}
EXCLUDED_NAMES = {
    ".env", ".env.r4r.local",
}

def should_skip(path: Path) -> bool:
    if path.name in EXCLUDED_NAMES:
        return True
    if path.is_dir() and path.name in EXCLUDED_DIRS:
        return True
    if path.is_file() and path.suffix in EXCLUDED_SUFFIXES:
        return True
    if path.is_dir() and path.name.endswith(".egg-info"):
        return True
    return False

if src.is_symlink():
    print(f"Warning: symlink skipped: {src}", file=sys.stderr)
    raise SystemExit(0)

if src.is_file():
    if should_skip(src):
        raise SystemExit(0)
    dst.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(src, dst)
    raise SystemExit(0)

dst.mkdir(parents=True, exist_ok=True)
for child in src.iterdir():
    if child.is_symlink():
        print(f"Warning: symlink skipped: {child}", file=sys.stderr)
        continue
    if should_skip(child):
        continue
    target = dst / child.name
    if child.is_dir():
        shutil.copytree(
            child,
            target,
            dirs_exist_ok=True,
            symlinks=False,
            ignore=lambda directory, names: {
                name
                for name in names
                if should_skip(Path(directory) / name)
                or (Path(directory) / name).is_symlink()
            },
        )
    else:
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(child, target)
PYCOPY
}

copy_top_file() {
  local file="$1"
  local destination_dir="$2"
  [[ -f "$file" ]] || return 0
  mkdir -p "$destination_dir"
  cp -a "$file" "$destination_dir/"
}

run_is_finished() {
  local run_dir="$1"
  local state="$run_dir/state.json"
  [[ -f "$state" ]] || return 1
  python3 - "$state" <<'PYSTATE'
import json
import sys
from pathlib import Path

try:
    data = json.loads(Path(sys.argv[1]).read_text(encoding="utf-8"))
except Exception:
    raise SystemExit(1)

status = str(data.get("status", "")).strip().upper()
finished_at = data.get("finished_at")
running = {"", "RUNNING", "STARTING", "EDITING", "REVIEWING", "GATING"}
if status in running or not finished_at:
    raise SystemExit(1)
raise SystemExit(0)
PYSTATE
}

agent_processes_active() {
  pgrep -af 'r4r_codex_agent|opencode[[:space:]]+run|codex[[:space:]]+exec' \
    >/dev/null 2>&1
}

safe_delete_run() {
  local run_dir="$1"
  local worker="$2"
  local expected_parent=""

  case "$worker" in
    PC|LP) expected_parent="$(realpath "$RUNS_DIR/$worker")" ;;
    LEGACY) expected_parent="$(realpath "$RUNS_DIR")" ;;
    *) return 1 ;;
  esac

  [[ "$(realpath "$(dirname "$run_dir")")" == "$expected_parent" ]] || return 1
  is_run_id "$(basename "$run_dir")" || return 1
  run_is_finished "$run_dir" || return 1
  agent_processes_active && return 1

  rm -rf -- "$run_dir"
}

export_one() {
  local run_dir="$1"
  local worker="$2"
  local run_id
  local formatted
  local package_name
  local output_dir
  local output_zip
  local output_sha
  local work_dir
  local stage_dir
  local temp_zip
  local metadata_file
  local control_dir=""

  run_dir="$(realpath "$run_dir")"
  run_id="$(basename "$run_dir")"
  is_run_id "$run_id" || die \
    "el directorio seleccionado no usa RUN_ID YYYYMMDDTHHMMSSZ: $run_dir"

  formatted="$(formatted_run_id "$run_id")" \
    || die "RUN_ID no válido: $run_id"
  package_name="r4r-evaluation-${worker}-${formatted}"
  output_dir="$EXPORTS_DIR/$worker"
  output_zip="$output_dir/$package_name.zip"
  output_sha="$output_dir/$package_name.sha256"

  mkdir -p "$output_dir"
  work_dir="$(mktemp -d "${TMPDIR:-/tmp}/r4r-evaluation-dual.XXXXXX")"
  stage_dir="$work_dir/$package_name"
  temp_zip="$work_dir/$package_name.zip"
  metadata_file="$work_dir/worker-metadata.tsv"

  ACTIVE_WORK_DIR="$work_dir"

  mkdir -p \
    "$stage_dir/repository" \
    "$stage_dir/runtime/$worker" \
    "$stage_dir/git"

  resolve_worker_metadata "$worker" "$metadata_file"

  # Common repository contracts and source. Generated/build/local state is
  # filtered by copy_filtered.
  local top
  for top in \
    AGENTS.md README.md LICENSE pom.xml package.json package-lock.json \
    opencode.jsonc .gitignore .env.example
  do
    copy_top_file "$ROOT/$top" "$stage_dir/repository"
  done

  local tree
  for tree in \
    config scripts py-ring-agent .opencode src knowledge docs frontend e2e
  do
    [[ -e "$ROOT/$tree" ]] || continue
    copy_filtered "$ROOT/$tree" "$stage_dir/repository/$tree"
  done

  # Docker definitions only. PostgreSQL data and local backups are never copied.
  if [[ -d "$ROOT/docker-postgres" ]]; then
    mkdir -p "$stage_dir/repository/docker-postgres"
    copy_top_file "$ROOT/docker-postgres/compose.yml" \
      "$stage_dir/repository/docker-postgres"
    copy_top_file "$ROOT/docker-postgres/docker-compose.yml" \
      "$stage_dir/repository/docker-postgres"
    copy_top_file "$ROOT/docker-postgres/README.md" \
      "$stage_dir/repository/docker-postgres"
    if [[ -d "$ROOT/docker-postgres/init" ]]; then
      copy_filtered \
        "$ROOT/docker-postgres/init" \
        "$stage_dir/repository/docker-postgres/init"
    fi
  fi

  # Ensure the exact worker-resolved plan/progress/memory/config are present,
  # even if a future repository stops copying all of .opencode/config.
  while IFS=$'\t' read -r key relative; do
    [[ -n "${relative:-}" ]] || continue
    case "$key" in
      plan|progress|memory|opencodeConfig)
        if [[ -f "$ROOT/$relative" ]]; then
          copy_filtered \
            "$ROOT/$relative" \
            "$stage_dir/repository/$relative"
        fi
        ;;
      controlDir)
        control_dir="$relative"
        ;;
    esac
  done < "$metadata_file"

  # Exact selected runtime snapshot.
  copy_filtered "$run_dir" "$stage_dir/runtime/$worker/run"

  if [[ "$INCLUDE_CONTROL" == true ]]; then
    if [[ -z "$control_dir" ]]; then
      control_dir="runtime/control/$worker"
    fi
    if [[ -d "$ROOT/$control_dir" ]]; then
      copy_filtered \
        "$ROOT/$control_dir" \
        "$stage_dir/runtime/$worker/control"
    fi
  fi

  # Git evidence.
  git status --short > "$stage_dir/git/status.txt"
  git log --oneline --decorate -30 > "$stage_dir/git/log.txt"
  git diff --no-ext-diff --binary > "$stage_dir/git/diff.patch"
  git diff --cached --no-ext-diff --binary > "$stage_dir/git/diff-cached.patch"
  git diff --stat > "$stage_dir/git/diff-stat.txt"
  git ls-files --others --exclude-standard > "$stage_dir/git/untracked-files.txt"
  git rev-parse HEAD > "$stage_dir/git/head.txt"
  git branch --show-current > "$stage_dir/git/branch.txt"
  git worktree list --porcelain > "$stage_dir/git/worktrees.txt"
  git show -s --format=fuller HEAD > "$stage_dir/git/head-fuller.txt"

  cat > "$stage_dir/export-info.txt" <<EOF_INFO
script_version=$SCRIPT_VERSION
created_at=$(date --iso-8601=seconds)
repository=$ROOT
worker=$worker
run_id=$run_id
runtime_run=$run_dir
offset=$OFFSET
source_delete_requested=$DELETE_SOURCE
source_retained_by_default=true
control_snapshot=$INCLUDE_CONTROL
package=$package_name
output_zip=$output_zip
note=.env, .env.r4r.local, database data, node_modules, build outputs, CodeGraph indexes, IDE metadata and virtual environments are excluded.
EOF_INFO

  python3 - "$stage_dir" "$worker" "$run_id" "$SCRIPT_VERSION" <<'PYMANIFEST'
from hashlib import sha256
from pathlib import Path
import json
import sys

root = Path(sys.argv[1])
worker = sys.argv[2]
run_id = sys.argv[3]
version = sys.argv[4]

files = []
for path in sorted(p for p in root.rglob("*") if p.is_file()):
    relative = path.relative_to(root).as_posix()
    if relative in {"SHA256SUMS.txt", "manifest.json"}:
        continue
    payload = path.read_bytes()
    files.append({
        "path": relative,
        "bytes": len(payload),
        "sha256": sha256(payload).hexdigest(),
    })

manifest = {
    "schema_version": 1,
    "exporter_version": version,
    "worker": worker,
    "run_id": run_id,
    "file_count": len(files),
    "files": files,
}
(root / "manifest.json").write_text(
    json.dumps(manifest, indent=2, ensure_ascii=False) + "\n",
    encoding="utf-8",
)

checksum_lines = [
    f"{entry['sha256']}  {entry['path']}"
    for entry in files
]
manifest_payload = (root / "manifest.json").read_bytes()
checksum_lines.append(
    f"{sha256(manifest_payload).hexdigest()}  manifest.json"
)
(root / "SHA256SUMS.txt").write_text(
    "\n".join(checksum_lines) + "\n",
    encoding="utf-8",
)
PYMANIFEST

  python3 - "$work_dir" "$package_name" "$temp_zip" <<'PYZIP'
from pathlib import Path
from zipfile import ZIP_DEFLATED, ZipFile
import sys

work = Path(sys.argv[1])
package_name = sys.argv[2]
output = Path(sys.argv[3])
package = work / package_name

with ZipFile(output, "w", compression=ZIP_DEFLATED, compresslevel=6) as archive:
    for path in sorted(package.rglob("*")):
        if path.is_file():
            archive.write(path, path.relative_to(work))
PYZIP

  python3 - "$temp_zip" "$package_name" "$worker" <<'PYVERIFY'
from zipfile import BadZipFile, ZipFile
import sys

archive_path, package_name, worker = sys.argv[1:4]
run_prefix = f"{package_name}/runtime/{worker}/run/"

try:
    with ZipFile(archive_path, "r") as archive:
        bad = archive.testzip()
        if bad is not None:
            raise SystemExit(f"CRC failure at: {bad}")
        names = archive.namelist()
        required = {
            f"{package_name}/manifest.json",
            f"{package_name}/SHA256SUMS.txt",
            f"{package_name}/export-info.txt",
        }
        missing = sorted(required.difference(names))
        if missing:
            raise SystemExit(f"missing required members: {missing}")
        if not any(name.startswith(run_prefix) for name in names):
            raise SystemExit("selected runtime snapshot is missing")
except BadZipFile as exc:
    raise SystemExit(f"invalid ZIP: {exc}") from exc
PYVERIFY

  mv -f "$temp_zip" "$output_zip"
  local zip_sha
  zip_sha="$(sha256sum "$output_zip" | awk '{print $1}')"
  printf '%s  %s\n' "$zip_sha" "$(basename "$output_zip")" > "$output_sha"

  local removed=false
  if [[ "$DELETE_SOURCE" == true ]]; then
    if safe_delete_run "$run_dir" "$worker"; then
      removed=true
    else
      echo "AVISO: ZIP creado, pero la ejecución original NO se eliminó." >&2
      echo "Motivo posible: run activo, state.json incompleto, agentes activos o ruta no segura." >&2
    fi
  fi

  echo
  echo "Evaluation package created and verified:"
  echo "$output_zip"
  echo "SHA-256:"
  echo "$zip_sha"
  echo "Worker:"
  echo "$worker"
  echo "Runtime snapshot:"
  echo "$run_dir"
  echo "Original runtime directory removed:"
  echo "$removed"

  rm -rf "$work_dir"
  ACTIVE_WORK_DIR=""
}

if [[ "$ALL_WORKERS" == true ]]; then
  exported=0
  for requested_worker in PC LP; do
    if mapfile -t available < <(list_worker_runs "$requested_worker") \
        && (( ${#available[@]} > 0 )); then
      run_dir="$(resolve_by_offset "$requested_worker" "$OFFSET")"
      export_one "$run_dir" "$requested_worker"
      exported=$(( exported + 1 ))
    else
      echo "AVISO: no hay ejecuciones para $requested_worker; se omite." >&2
    fi
  done
  (( exported > 0 )) || die "no se exportó ninguna ejecución"
  exit 0
fi

if [[ -n "$EXPLICIT_RUN" ]]; then
  RUN_DIR="$(resolve_explicit_run "$EXPLICIT_RUN")"
else
  RUN_DIR="$(resolve_by_offset "$WORKER" "$OFFSET")"
fi

SELECTED_WORKER="$(infer_worker_from_run "$RUN_DIR")"

if [[ -n "$WORKER" && "$SELECTED_WORKER" != "$WORKER" ]]; then
  die "la ejecución seleccionada pertenece a $SELECTED_WORKER, no a $WORKER"
fi

export_one "$RUN_DIR" "$SELECTED_WORKER"
