#!/usr/bin/env bash
set -euo pipefail

ROOT="${R4R_REPO:-$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)}"
ROOT="$(realpath "$ROOT")"
cd "$ROOT"

RUNS_DIR="$ROOT/runtime/runs"
mkdir -p "$RUNS_DIR"

usage() {
  cat <<'USAGE'
Usage:
  ./scripts/export-evaluation.sh [OFFSET]

OFFSET selects an execution directory ordered from newest to oldest:
  omitted or 0  latest execution
  -1            penultimate execution
  -2            antepenultimate execution
  -N            N executions before the latest

For backward compatibility, an explicit run directory or run-directory name is
also accepted.

The ZIP is written to runtime/runs/ using the selected execution timestamp:
  runtime/runs/2026-07-30T10-13-37Z.zip

After the ZIP has passed an integrity check, the selected original execution
directory is deleted. Only timestamp-named direct children of runtime/runs/ are
ever eligible for deletion.
USAGE
}

# Keep generated evaluation packages out of Git without modifying tracked files.
if [[ -f "$ROOT/.git/info/exclude" ]] \
    && ! grep -Fxq "/runtime/runs/*.zip" "$ROOT/.git/info/exclude"; then
  printf '\n/runtime/runs/*.zip\n' >> "$ROOT/.git/info/exclude"
fi

list_run_dirs() {
  # Select only real run directories. Auxiliary directories such as old-runs/
  # are deliberately ignored.
  find "$RUNS_DIR" \
    -mindepth 1 \
    -maxdepth 1 \
    -type d \
    -printf '%f\t%p\n' 2>/dev/null \
    | awk -F '\t' '$1 ~ /^[0-9]{8}T[0-9]{6}Z$/ { print }' \
    | sort -r \
    | cut -f2-
}

resolve_run_dir() {
  local requested="${1:-0}"

  case "$requested" in
    -h|--help)
      usage
      exit 0
      ;;
  esac

  # Preserve the previous explicit-directory behaviour.
  if [[ ! "$requested" =~ ^-?[0-9]+$ ]]; then
    if [[ -d "$requested" ]]; then
      realpath "$requested"
      return
    fi
    if [[ -d "$RUNS_DIR/$requested" ]]; then
      realpath "$RUNS_DIR/$requested"
      return
    fi
    echo "Run directory not found: $requested" >&2
    usage >&2
    exit 2
  fi

  if (( requested > 0 )); then
    echo "OFFSET must be 0 or negative; received: $requested" >&2
    usage >&2
    exit 2
  fi

  local index=$(( -requested ))
  local -a runs=()
  mapfile -t runs < <(list_run_dirs)

  if (( ${#runs[@]} == 0 )); then
    echo "No execution directories found under runtime/runs/" >&2
    exit 2
  fi

  if (( index >= ${#runs[@]} )); then
    echo "OFFSET $requested is out of range: only ${#runs[@]} execution directories exist." >&2
    echo "Valid offsets: 0 through -$(( ${#runs[@]} - 1 ))" >&2
    exit 2
  fi

  realpath "${runs[$index]}"
}

format_run_timestamp() {
  local run_name="$1"

  if [[ "$run_name" =~ ^([0-9]{4})([0-9]{2})([0-9]{2})T([0-9]{2})([0-9]{2})([0-9]{2})Z$ ]]; then
    printf '%s-%s-%sT%s-%s-%sZ\n' \
      "${BASH_REMATCH[1]}" \
      "${BASH_REMATCH[2]}" \
      "${BASH_REMATCH[3]}" \
      "${BASH_REMATCH[4]}" \
      "${BASH_REMATCH[5]}" \
      "${BASH_REMATCH[6]}"
    return
  fi

  echo "Selected run directory does not use the expected YYYYMMDDTHHMMSSZ format: $run_name" >&2
  exit 2
}

REQUESTED="${1:-0}"
RUN_DIR="$(resolve_run_dir "$REQUESTED")"
RUN_NAME="$(basename "$RUN_DIR")"
FORMATTED_RUN_NAME="$(format_run_timestamp "$RUN_NAME")"
PACKAGE_NAME="r4r-evaluation-$FORMATTED_RUN_NAME"
WORK_DIR="$(mktemp -d "${TMPDIR:-/tmp}/r4r-evaluation-staging.XXXXXX")"
STAGE_DIR="$WORK_DIR/$PACKAGE_NAME"
OUTPUT_ZIP="$RUNS_DIR/$FORMATTED_RUN_NAME.zip"
TEMP_ZIP="$WORK_DIR/$FORMATTED_RUN_NAME.zip"

cleanup() {
  rm -rf "$WORK_DIR"
}
trap cleanup EXIT INT TERM

mkdir -p "$STAGE_DIR"

copy_path() {
  local source="$1"
  local destination="${2:-$STAGE_DIR}"

  if [[ -e "$source" ]]; then
    cp -a "$source" "$destination/"
  fi
}

# Product source and repository contracts.
copy_path src
copy_path knowledge
copy_path scripts
copy_path docs

for file in \
  pom.xml \
  AGENTS.md \
  opencode.jsonc \
  README.md \
  LICENSE \
  .gitignore \
  .env.example
do
  copy_path "$file"
done

# OpenCode configuration, excluding generated node_modules.
mkdir -p "$STAGE_DIR/.opencode"
copy_path .opencode/agents "$STAGE_DIR/.opencode"
copy_path .opencode/commands "$STAGE_DIR/.opencode"

for file in \
  .opencode/memory.md \
  .opencode/progress.json \
  .opencode/task-plan.json \
  .opencode/package.json \
  .opencode/package-lock.json \
  .opencode/.gitignore
do
  if [[ -f "$file" ]]; then
    cp -a "$file" "$STAGE_DIR/.opencode/"
  fi
done

# Codex controller source and contracts, excluding .venv, caches and egg-info.
if [[ -d py-codex-agent ]]; then
  mkdir -p "$STAGE_DIR/py-codex-agent"
  copy_path py-codex-agent/prompts "$STAGE_DIR/py-codex-agent"
  copy_path py-codex-agent/schemas "$STAGE_DIR/py-codex-agent"
  copy_path py-codex-agent/tests "$STAGE_DIR/py-codex-agent"

  if [[ -d py-codex-agent/src/r4r_codex_agent ]]; then
    mkdir -p "$STAGE_DIR/py-codex-agent/src"
    cp -a py-codex-agent/src/r4r_codex_agent \
      "$STAGE_DIR/py-codex-agent/src/"
  fi

  for file in \
    py-codex-agent/pyproject.toml \
    py-codex-agent/README.md
  do
    if [[ -f "$file" ]]; then
      cp -a "$file" "$STAGE_DIR/py-codex-agent/"
    fi
  done
fi

# Remove generated Python caches from every copied subtree.
find "$STAGE_DIR" -type d -name '__pycache__' -prune -exec rm -rf {} +
find "$STAGE_DIR" -type f \( -name '*.pyc' -o -name '*.pyo' \) -delete
find "$STAGE_DIR" -type d -name '*.egg-info' -prune -exec rm -rf {} +

# Docker definition only. Never package PostgreSQL data or local backups.
if [[ -d docker-postgres ]]; then
  mkdir -p "$STAGE_DIR/docker-postgres"
  copy_path docker-postgres/compose.yml "$STAGE_DIR/docker-postgres"
  copy_path docker-postgres/init "$STAGE_DIR/docker-postgres"
  copy_path docker-postgres/README.md "$STAGE_DIR/docker-postgres"
fi

# Snapshot only the selected execution. ZIP archives do not retain empty
# directories, so add a generated marker to prove which runtime directory was
# selected even when an early controller exception left it otherwise empty.
mkdir -p "$STAGE_DIR/runtime"
cp -a "$RUN_DIR" "$STAGE_DIR/runtime/run"
python3 - "$STAGE_DIR/runtime/run/.evaluation-snapshot.json" "$RUN_NAME" "$RUN_DIR" <<'PY'
from datetime import datetime, timezone
import json
from pathlib import Path
import sys

marker = Path(sys.argv[1])
run_name = sys.argv[2]
source = Path(sys.argv[3])
file_count = sum(1 for path in source.rglob("*") if path.is_file())
marker.write_text(
    json.dumps(
        {
            "schema_version": 1,
            "run_name": run_name,
            "source_runtime_directory": str(source),
            "source_file_count": file_count,
            "created_at": datetime.now(timezone.utc).isoformat(),
        },
        indent=2,
        ensure_ascii=False,
    )
    + "\n",
    encoding="utf-8",
)
PY

# Git evidence. Untracked files are already copied through selected paths.
git status --short > "$STAGE_DIR/git-status.txt"
git log --oneline --decorate -20 > "$STAGE_DIR/git-log.txt"
git diff --no-ext-diff --binary > "$STAGE_DIR/git-diff.txt"
git diff --cached --no-ext-diff --binary > "$STAGE_DIR/git-diff-cached.txt"
git diff --stat > "$STAGE_DIR/git-diff-stat.txt"
git ls-files --others --exclude-standard > "$STAGE_DIR/git-untracked-files.txt"
git rev-parse HEAD > "$STAGE_DIR/git-head.txt"

cat > "$STAGE_DIR/export-info.txt" <<EOF_INFO
created_at=$(date --iso-8601=seconds)
repository=$ROOT
requested_offset=$REQUESTED
runtime_run=$RUN_DIR
runtime_run_name=$RUN_NAME
formatted_run_name=$FORMATTED_RUN_NAME
package=$PACKAGE_NAME
output_zip=$OUTPUT_ZIP
note=.env, node_modules, virtual environments, database data, backups, target and CodeGraph indexes are intentionally excluded.
EOF_INFO

# Produce a per-file SHA-256 manifest.
python3 - "$STAGE_DIR" <<'PY'
from hashlib import sha256
from pathlib import Path
import sys

root = Path(sys.argv[1])
lines = []
for path in sorted(p for p in root.rglob("*") if p.is_file()):
    if path.name == "SHA256SUMS.txt":
        continue
    digest = sha256(path.read_bytes()).hexdigest()
    lines.append(f"{digest}  {path.relative_to(root).as_posix()}")

(root / "SHA256SUMS.txt").write_text("\n".join(lines) + "\n", encoding="utf-8")
PY

# Build into the temporary staging directory first. The existing ZIP, if any,
# is replaced only after the new archive has passed integrity checks.
rm -f "$TEMP_ZIP"

# Create the ZIP with Python so no extra zip package is required.
python3 - "$WORK_DIR" "$PACKAGE_NAME" "$TEMP_ZIP" <<'PY'
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
PY

# Verify CRCs and confirm that the selected runtime snapshot is actually present
# before replacing the final ZIP or deleting the source directory.
python3 - "$TEMP_ZIP" "$PACKAGE_NAME" "$RUN_NAME" <<'PY'
import json
from pathlib import Path
from zipfile import BadZipFile, ZipFile
import sys

archive_path = Path(sys.argv[1])
package_name = sys.argv[2]
expected_run_name = sys.argv[3]
marker_name = f"{package_name}/runtime/run/.evaluation-snapshot.json"

try:
    with ZipFile(archive_path, "r") as archive:
        bad_member = archive.testzip()
        if bad_member is not None:
            raise SystemExit(f"ZIP integrity check failed at member: {bad_member}")
        names = set(archive.namelist())
        if marker_name not in names:
            raise SystemExit(
                "ZIP integrity check failed: selected runtime snapshot marker is missing"
            )
        marker = json.loads(archive.read(marker_name).decode("utf-8"))
        if marker.get("run_name") != expected_run_name:
            raise SystemExit(
                "ZIP integrity check failed: runtime snapshot marker does not match "
                f"{expected_run_name}"
            )
except (BadZipFile, json.JSONDecodeError, UnicodeDecodeError) as exception:
    raise SystemExit(f"ZIP integrity check failed: {exception}") from exception
PY

rm -f "$OUTPUT_ZIP"
mv "$TEMP_ZIP" "$OUTPUT_ZIP"
ZIP_SHA256="$(sha256sum "$OUTPUT_ZIP" | awk '{print $1}')"

# Destructive cleanup is deliberately restricted to a timestamp-named direct
# child of runtime/runs/. Explicit directories outside that location can still
# be exported, but they are never removed automatically.
if [[ "$(dirname "$RUN_DIR")" == "$RUNS_DIR" ]] \
    && [[ "$RUN_NAME" =~ ^[0-9]{8}T[0-9]{6}Z$ ]]; then
  rm -rf -- "$RUN_DIR"
  RUN_REMOVED=true
else
  RUN_REMOVED=false
  echo "Archive created, but source directory was retained because it is not a timestamp-named direct child of runtime/runs/:" >&2
  echo "$RUN_DIR" >&2
fi

echo "Evaluation package created and verified:"
echo "$OUTPUT_ZIP"
echo "Requested offset:"
echo "$REQUESTED"
echo "Runtime snapshot:"
echo "$RUN_DIR"
echo "Original runtime directory removed:"
echo "$RUN_REMOVED"
echo "SHA-256:"
echo "$ZIP_SHA256"
