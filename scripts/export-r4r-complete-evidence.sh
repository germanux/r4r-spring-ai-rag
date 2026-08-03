#!/usr/bin/env bash
set -Eeuo pipefail

REPO="${1:-/home/german/Desarrollo/r4r-ring-agent.git}"
OUT_DIR="${2:-$REPO/runtime/exports/complete}"
STAMP="$(date -u +%Y%m%dT%H%M%SZ)"
NAME="r4r-complete-evidence-$STAMP"
ZIP="$OUT_DIR/$NAME.zip"
SHA="$ZIP.sha256"
MANIFEST="$OUT_DIR/$NAME.manifest.txt"

fail() {
  printf 'ERROR: %s\n' "$*" >&2
  exit 1
}

command -v git >/dev/null 2>&1 || fail "git no está instalado"
command -v zip >/dev/null 2>&1 || fail "zip no está instalado"
command -v sha256sum >/dev/null 2>&1 || fail "sha256sum no está instalado"
git -C "$REPO" rev-parse --is-inside-work-tree >/dev/null 2>&1 \
  || fail "no es un worktree Git válido: $REPO"

mkdir -p "$OUT_DIR"
rm -f "$ZIP" "$SHA" "$MANIFEST"

# Resolve only paths that actually exist.
CANDIDATES=(
  "runtime/ring-agent"
  "runtime/runs"
  "runtime/control"
  "runtime/worker-sync-backups"
  "runtime/exports/PC"
  "runtime/exports/LP"
  ".ring-agent"
  ".opencode/current/ring"
  ".opencode/task-plan.backend.json"
  ".opencode/task-plan.frontend.json"
  ".opencode/progress.backend.json"
  ".opencode/progress.frontend.json"
  ".opencode/memory.backend.md"
  ".opencode/memory.frontend.md"
  "scripts/export-evaluation.sh"
  "scripts/merge-worker-branches-and-restart.sh"
  "scripts/stop-all-r4r-agents.sh"
)

INCLUDE=()
for rel in "${CANDIDATES[@]}"; do
  [[ -e "$REPO/$rel" ]] && INCLUDE+=("$rel")
done

((${#INCLUDE[@]} > 0)) || fail "no se encontraron rutas de evidencia"

{
  echo "R4R COMPLETE EVIDENCE MANIFEST"
  echo "Generated UTC: $(date -u --iso-8601=seconds)"
  echo "Generated local: $(date --iso-8601=seconds)"
  echo "Repository: $REPO"
  echo "Branch: $(git -C "$REPO" branch --show-current)"
  echo "HEAD: $(git -C "$REPO" rev-parse HEAD)"
  echo
  echo "=== INCLUDED PATHS ==="
  printf '%s\n' "${INCLUDE[@]}"
  echo
  echo "=== GIT STATUS: RING ==="
  git -C "$REPO" status --short || true
  echo
  for worker in \
    /home/german/Desarrollo/r4r-pc-worker.git \
    /home/german/Desarrollo/r4r-lp-worker.git
  do
    if git -C "$worker" rev-parse --is-inside-work-tree >/dev/null 2>&1; then
      echo "=== GIT STATUS: $worker ==="
      echo "Branch: $(git -C "$worker" branch --show-current)"
      echo "HEAD: $(git -C "$worker" rev-parse HEAD)"
      git -C "$worker" status --short || true
      echo
    fi
  done
  echo "=== DISK SPACE ==="
  df -h "$OUT_DIR" || true
} > "$MANIFEST"

printf 'Repositorio: %s\n' "$REPO"
printf 'Destino:    %s\n' "$ZIP"
printf 'Estimación de evidencia seleccionada:\n'
(
  cd "$REPO"
  du -sh "${INCLUDE[@]}" 2>/dev/null || true
)

# Quiet mode avoids flooding the terminal. ZIP64 is automatic when needed.
(
  cd "$REPO"
  nice -n 10 zip -q -y -r "$ZIP" \
    "${INCLUDE[@]}" \
    -x \
      '*/.env' \
      '*/.env.*' \
      '*token*' \
      '*TOKEN*' \
      '*secret*' \
      '*SECRET*' \
      '*credential*' \
      '*CREDENTIAL*' \
      '*.pid' \
      '*.lock' \
      '*/node_modules/*' \
      '*/target/*' \
      '*/build/*' \
      '*/dist/*' \
      '*/.venv/*' \
      '*/__pycache__/*' \
      'runtime/exports/complete/*'
)

zip -q -j "$ZIP" "$MANIFEST"
zip -T "$ZIP" >/dev/null
sha256sum "$ZIP" | tee "$SHA"

[[ -s "$ZIP" ]] || fail "el ZIP no se creó o está vacío"

printf '\nCREADO CORRECTAMENTE\n'
ls -lh "$ZIP" "$SHA" "$MANIFEST"
printf '\nSube este fichero:\n%s\n' "$ZIP"
