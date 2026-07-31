#!/usr/bin/env bash
set -Eeuo pipefail

REPO="${1:-/home/german/Desarrollo/r4r-spring-ai-rag.git}"
REPO="$(realpath "$REPO")"
SOURCE="${2:-./task-04-rag.md}"

[[ -f "$SOURCE" ]] || {
  echo "ERROR: no existe $SOURCE" >&2
  exit 2
}

TARGET="$REPO/.opencode/commands/task-04-rag.md"
CONTROL="$REPO/runtime/control/codex-qwen3-extra-instructions.md"
STAMP="$(date +%Y%m%d-%H%M%S)"
BACKUP="$REPO/patches-applied/task04-pc-agent-$STAMP"

mkdir -p "$BACKUP/.opencode/commands" "$BACKUP/runtime/control"

[[ -f "$TARGET" ]] && cp -a "$TARGET" "$BACKUP/.opencode/commands/" || true
[[ -f "$CONTROL" ]] && cp -a "$CONTROL" "$BACKUP/runtime/control/" || true

cp "$SOURCE" "$TARGET"
cp "$SOURCE" "$CONTROL"

echo "Actualizado: $TARGET"
echo "Actualizado: $CONTROL"
echo "Copia previa: $BACKUP"
echo
echo "El agente ya en curso lo leerá en su siguiente intento/ciclo."
