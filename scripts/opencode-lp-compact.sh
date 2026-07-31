#!/usr/bin/env bash
set -Eeuo pipefail

ROOT="${R4R_REPO:-$(pwd)}"
BASE_URL="${R4R_OPENCODE_LP_BASE_URL:-http://192.168.1.9:11434/v1}"
MODEL="${R4R_LP_MODEL:-qwen3-30b-coder-28k-6k-t33:latest}"

if [[ "${1:-}" == "--smoke" ]]; then
  exec python3 "$ROOT/scripts/r4r-lp-compact-worker.py" \
    --repo "$ROOT" --base-url "$BASE_URL" --model "$MODEL" --smoke
fi

[[ "${1:-}" == "run" ]] || {
  echo "LP_COMPACT_WORKER_ERROR: only 'run' is supported by the OpenCode shim" >&2
  exit 2
}

prompt=""
while (($#)); do
  case "$1" in
    --dir|--agent|--format)
      [[ $# -ge 2 ]] || { echo "LP_COMPACT_WORKER_ERROR: missing value for $1" >&2; exit 2; }
      shift 2
      ;;
    --auto)
      [[ $# -ge 2 ]] || { echo "LP_COMPACT_WORKER_ERROR: missing prompt" >&2; exit 2; }
      prompt="$2"
      shift 2
      ;;
    run) shift ;;
    *)
      # OpenCode versions may add harmless flags. Preserve only the explicit prompt.
      shift
      ;;
  esac
done

[[ -n "$prompt" ]] || {
  echo "LP_COMPACT_WORKER_ERROR: no --auto prompt received" >&2
  exit 2
}

exec python3 "$ROOT/scripts/r4r-lp-compact-worker.py" \
  --repo "$ROOT" --base-url "$BASE_URL" --model "$MODEL" --prompt "$prompt"
