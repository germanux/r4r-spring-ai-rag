#!/usr/bin/env bash
set -Eeuo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RUN_DIR="$ROOT/.cgr-runtime"

mkdir -p "$RUN_DIR"
cd "$RUN_DIR"

CGR_PACKAGE='code-graph-rag[treesitter-full,semantic]'
CGR=(uvx --from "$CGR_PACKAGE" cgr)

command="${1:-}"
shift || true

case "$command" in
  doctor)
    exec "${CGR[@]}" doctor "$@"
    ;;

  up)
    exec "${CGR[@]}" daemon up "$@"
    ;;

  down)
    exec "${CGR[@]}" daemon down "$@"
    ;;

  index)
    exec "${CGR[@]}" start \
      --repo-path "$ROOT" \
      --update-graph \
      "$@"
    ;;

  index-clean)
    exec "${CGR[@]}" start \
      --repo-path "$ROOT" \
      --update-graph \
      --clean \
      "$@"
    ;;

  query)
    exec "${CGR[@]}" start \
      --repo-path "$ROOT" \
      "$@"
    ;;

  mcp)
    exec "${CGR[@]}" mcp-server "$@"
    ;;

  *)
    echo "Uso: $0 {doctor|up|down|index|index-clean|query|mcp}" >&2
    exit 2
    ;;
esac
