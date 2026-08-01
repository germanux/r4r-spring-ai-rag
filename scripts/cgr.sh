#!/usr/bin/env bash
set -Eeuo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RUN_DIR="$ROOT/.cgr-runtime"
mkdir -p "$RUN_DIR"

if [[ -f "$ROOT/.env.r4r.local" ]]; then
  set -a
  # shellcheck disable=SC1091
  source "$ROOT/.env.r4r.local"
  set +a
fi

CGR_PACKAGE="${R4R_CGR_PACKAGE:-code-graph-rag[treesitter-full,semantic,ast-grep]==0.0.484}"
CGR=(uvx --from "$CGR_PACKAGE" cgr)
export ORCHESTRATOR_PROVIDER="${ORCHESTRATOR_PROVIDER:-ollama}"
export ORCHESTRATOR_MODEL="${ORCHESTRATOR_MODEL:-${R4R_CGR_MODEL:-qwen3-coder-next-80b-t033-128k-8k-pc-pc:latest}}"
export ORCHESTRATOR_ENDPOINT="${ORCHESTRATOR_ENDPOINT:-${R4R_CGR_BASE_URL:-http://127.0.0.1:11434/v1}}"
export CYPHER_PROVIDER="${CYPHER_PROVIDER:-ollama}"
export CYPHER_MODEL="${CYPHER_MODEL:-$ORCHESTRATOR_MODEL}"
export CYPHER_ENDPOINT="${CYPHER_ENDPOINT:-$ORCHESTRATOR_ENDPOINT}"
export TARGET_REPO_PATH="${TARGET_REPO_PATH:-$ROOT}"
# The local coding LLM already occupies most of the 8 GiB GPU. Keep UniXcoder
# embeddings on CPU by default and use small flush batches. Both values remain
# overridable from .env.r4r.local or the process environment.
export CGR_EMBEDDING_DEVICE="${CGR_EMBEDDING_DEVICE:-${R4R_CGR_EMBEDDING_DEVICE:-cpu}}"
export QDRANT_BATCH_SIZE="${QDRANT_BATCH_SIZE:-${R4R_CGR_EMBEDDING_BATCH_SIZE:-8}}"

cd "$RUN_DIR" # isolates CGR from the application's generic .env
command="${1:-}"
shift || true
case "$command" in
  doctor) exec "${CGR[@]}" doctor "$@" ;;
  up) exec "${CGR[@]}" daemon up "$@" ;;
  down) exec "${CGR[@]}" daemon down "$@" ;;
  index) exec "${CGR[@]}" start --repo-path "$ROOT" --update-graph "$@" ;;
  index-clean) exec "${CGR[@]}" start --repo-path "$ROOT" --update-graph --clean "$@" ;;
  index-path)
    repo="${1:?repository path required}"; project="${2:-}"; shift $(( $# >= 2 ? 2 : 1 ))
    args=(start --repo-path "$repo" --update-graph)
    [[ -n "$project" ]] && args+=(--project-name "$project")
    exec "${CGR[@]}" "${args[@]}" "$@"
    ;;
  query) exec "${CGR[@]}" start --repo-path "$ROOT" "$@" ;;
  query-workspace) workspace="${1:-r4r-code}"; shift || true; exec "${CGR[@]}" start --workspace "$workspace" "$@" ;;
  workspace-create) exec "${CGR[@]}" workspace create "${1:?workspace required}" ;;
  workspace-add) exec "${CGR[@]}" workspace add-repo "${1:?workspace required}" "${2:?repo required}" ;;
  delete-project) exec "${CGR[@]}" delete-project --name "${1:?project name required}" ;;
  mcp) exec "${CGR[@]}" mcp-server "$@" ;;
  *) echo "Uso: $0 {doctor|up|down|index|index-clean|index-path|query|query-workspace|workspace-create|workspace-add|delete-project|mcp}" >&2; exit 2 ;;
esac
