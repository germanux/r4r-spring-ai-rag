#!/usr/bin/env bash
set -euo pipefail

ROOT="${R4R_REPO:-$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)}"
ROOT="$(realpath "$ROOT")"
cd "$ROOT"

EXPORT_DIR="${R4R_EXPORT_DIR:-$ROOT/runtime/exports}"
RUNS_DIR="$ROOT/runtime/runs"
mkdir -p "$EXPORT_DIR"

# Keep generated evaluation packages out of Git without modifying tracked files.
# A later repository commit may add /runtime/exports/ to .gitignore permanently.
if [[ -f "$ROOT/.git/info/exclude" ]]     && ! grep -Fxq "/runtime/exports/" "$ROOT/.git/info/exclude"; then
  printf '\n/runtime/exports/\n' >> "$ROOT/.git/info/exclude"
fi

resolve_run_dir() {
  local requested="${1:-}"

  if [[ -n "$requested" ]]; then
    if [[ -d "$requested" ]]; then
      realpath "$requested"
      return
    fi
    if [[ -d "$RUNS_DIR/$requested" ]]; then
      realpath "$RUNS_DIR/$requested"
      return
    fi
    echo "Run directory not found: $requested" >&2
    exit 2
  fi

  local latest
  latest="$(
    find "$RUNS_DIR" -mindepth 1 -maxdepth 1 -type d -printf '%T@ %p\n' 2>/dev/null \
      | sort -nr \
      | head -n 1 \
      | cut -d' ' -f2-
  )"

  [[ -n "$latest" ]] || {
    echo "No execution directories found under runtime/runs/" >&2
    exit 2
  }

  realpath "$latest"
}

RUN_DIR="$(resolve_run_dir "${1:-}")"
STAMP="$(date '+%Y%m%d-%H%M%S')"
PACKAGE_NAME="r4r-evaluation-$STAMP"
WORK_DIR="$(mktemp -d "${TMPDIR:-/tmp}/r4r-evaluation-staging.XXXXXX")"
STAGE_DIR="$WORK_DIR/$PACKAGE_NAME"
OUTPUT_ZIP="$EXPORT_DIR/$PACKAGE_NAME.zip"

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

# Snapshot only the selected/latest execution.
mkdir -p "$STAGE_DIR/runtime"
cp -a "$RUN_DIR" "$STAGE_DIR/runtime/run"

# Git evidence. Untracked files are already copied through src/ and other selected paths.
git status --short > "$STAGE_DIR/git-status.txt"
git log --oneline --decorate -20 > "$STAGE_DIR/git-log.txt"
git diff --no-ext-diff --binary > "$STAGE_DIR/git-diff.txt"
git diff --cached --no-ext-diff --binary > "$STAGE_DIR/git-diff-cached.txt"
git diff --stat > "$STAGE_DIR/git-diff-stat.txt"
git ls-files --others --exclude-standard > "$STAGE_DIR/git-untracked-files.txt"
git rev-parse HEAD > "$STAGE_DIR/git-head.txt"

cat > "$STAGE_DIR/export-info.txt" <<EOF
created_at=$(date --iso-8601=seconds)
repository=$ROOT
runtime_run=$RUN_DIR
package=$PACKAGE_NAME
note=.env, node_modules, virtual environments, database data, backups, target and CodeGraph indexes are intentionally excluded.
EOF

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

# Create the ZIP with Python so no extra zip package is required.
python3 - "$WORK_DIR" "$PACKAGE_NAME" "$OUTPUT_ZIP" <<'PY'
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

echo "Evaluation package created:"
echo "$OUTPUT_ZIP"
echo "Runtime snapshot:"
echo "$RUN_DIR"
echo "SHA-256:"
sha256sum "$OUTPUT_ZIP" | awk '{print $1}'
