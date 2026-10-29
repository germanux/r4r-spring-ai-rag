#!/usr/bin/env bash
set -Eeuo pipefail

REPO="${1:-/home/german/Desarrollo/r4r-ring-agent.git}"
DOCS_DIR="$REPO/docs"
PATCH_DIR="$REPO/patches-applied/root-import-20260803"

fail() {
  printf 'ERROR: %s\n' "$*" >&2
  exit 1
}

git -C "$REPO" rev-parse --is-inside-work-tree >/dev/null 2>&1 \
  || fail "No es un worktree Git válido: $REPO"

mkdir -p "$DOCS_DIR" "$PATCH_DIR"

# Historical documentation that must not remain in the repository root.
DOC_FILES=(
  "local-understanding-report.md"
  "R4R-PHASE3-OPERATIONAL-CONTROL-V1.md"
  "R4R-CLAUDE-SURGICAL-PHASE2.17-README.md"
  "R4R-CLAUDE-SURGICAL-PHASE2.18-README.md"
  "R4R-OPENCODE-DUAL-SURGICAL-PHASE2.19-README.md"
  "R4R-PHASE3-BRANCH-SYNC-WORKER-GUARDIAN.md"
  "R4R-PHASE3-NOTIFICATION-POLICY-PHASE2.24.md"
  "R4R-PHASE3-RUNTIME-CONTROL-PHASE2.23.md"
  "R4R-PHASE3-RUNTIME-PATH-PHASE2.22.md"
  "R4R-PHASE3-WORKTREE-RESOLUTION-PHASE2.21.md"
  "R4R-PHASE3-RING-QWEN-EXCHANGE-PHASE2.25.md"
  "R4R-SETUP-CLAUDE-PHASE2.16-README.md"
  "R4R-SETUP-CLAUDE-PHASE2.15-README.md"
  "R4R-OPENCODE-CLAUDE-SURGICAL-README.md"
  "R4R-PHASE2.9-README.md"
  "RING-STABILIZATION-README.md"
  "README-DUAL-AGENTS.md"
  "R4R-MERGE-WORKERS-PHASE2.13-README.txt"
  "README.txt"
)

# Installation/export artifacts that must not remain in the root either.
PATCH_FILES=(
  "r4r.zip"
  "r4r-phase3-operational-control-v1.patch"
  "r4r-phase3-operational-control-v1-validation.txt"
  "r4r-phase3-operational-control-v1-SHA256SUMS.txt"
  "SHA256SUMS.txt"
)

move_or_remove_duplicate() {
  local src="$1"
  local dst="$2"

  [[ -e "$src" ]] || {
    printf 'OMITIDO: no existe %s\n' "${src#$REPO/}"
    return 0
  }

  if [[ -e "$dst" ]]; then
    if cmp -s -- "$src" "$dst"; then
      printf 'DUPLICADO: eliminando raíz %s\n' "${src#$REPO/}"
      git -C "$REPO" rm -f -- "${src#$REPO/}"
    else
      fail "Hay dos versiones distintas: ${src#$REPO/} y ${dst#$REPO/}. No se ha borrado ninguna."
    fi
  else
    printf 'MOVIENDO: %s -> %s\n' "${src#$REPO/}" "${dst#$REPO/}"
    git -C "$REPO" mv -- "${src#$REPO/}" "${dst#$REPO/}"
  fi
}

printf 'Repositorio: %s\n\n' "$REPO"

for name in "${DOC_FILES[@]}"; do
  move_or_remove_duplicate "$REPO/$name" "$DOCS_DIR/$name"
done

for name in "${PATCH_FILES[@]}"; do
  move_or_remove_duplicate "$REPO/$name" "$PATCH_DIR/$name"
done

printf '\nComprobando whitespace y estado Git...\n'
git -C "$REPO" diff --cached --check
git -C "$REPO" status --short

printf '\nFicheros Markdown/TXT históricos que todavía quedan en raíz:\n'
find "$REPO" -maxdepth 1 -type f \
  \( -name 'R4R-*.md' -o -name 'RING-*.md' -o -name 'README-DUAL-AGENTS.md' \
     -o -name 'local-understanding-report.md' -o -name 'R4R-*.txt' \
     -o -name 'README.txt' -o -name 'SHA256SUMS.txt' \) \
  -printf '%f\n' | sort || true

printf '\nConservados expresamente en raíz:\n'
printf '  AGENTS.md\n  README.md\n'
