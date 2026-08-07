#!/usr/bin/env bash
# Archived direct-Ollama benchmark; not part of the OpenCode runtime.
set -Eeuo pipefail

PROGRAM="$(basename "$0")"
REPO="$(pwd)"
API_BASE="${R4R_OPENCODE_LP_BASE_URL:-${OLLAMA_HOST:-http://127.0.0.1:11434}}"
SOURCE_MODEL="gemma4:e4b"
TARGET_MODEL="gemma4-e4b-lp-16k"
BASELINE_MODEL="auto"
CONTEXT_TOKENS=16384
OUTPUT_TOKENS=4096
BENCHMARK_TOKENS=512
REPETITIONS=1
SKIP_PULL=false
SKIP_CREATE=false
WITH_OPENCODE=false
DRY_RUN=false
OUTPUT_ROOT=""

log() { printf '[gemma4-lp] %s\n' "$*" >&2; }
die() { log "ERROR: $*"; exit 2; }

usage() {
  cat <<USAGE
Usage:
  $PROGRAM [options]

Installs a pinned Gemma 4 E4B alias for the LP machine and compares it with
the currently configured LP model. It never removes or overwrites the baseline.

Options:
  --repo PATH              R4R repository (default: current directory)
  --endpoint URL           Ollama API URL (default: LP config/env or localhost)
  --baseline MODEL         Baseline model, or auto from config/r4r-agents.json
  --source-model MODEL     Model to pull (default: gemma4:e4b)
  --target-model MODEL     Alias to create (default: gemma4-e4b-lp-16k)
  --context N              Alias context window (default: 16384)
  --output N               Alias max output (default: 4096)
  --benchmark-tokens N     Max output per benchmark case (default: 512)
  --repetitions N          Runs per case and model (default: 1)
  --output-root PATH       Result parent (default: REPO/runtime/benchmarks/gemma4-lp)
  --skip-pull              Do not run ollama pull
  --skip-create            Use an already-created target alias
  --with-opencode          Run an additional isolated OpenCode edit smoke test
  --dry-run                Print resolved settings without changing or loading models
  -h, --help               Show this help

Recommended first run:
  $PROGRAM --repo ~/Desarrollo/r4r-lp-worker.git

More reliable comparison (slower):
  $PROGRAM --repo ~/Desarrollo/r4r-lp-worker.git --repetitions 3 --with-opencode
USAGE
}

while (($#)); do
  case "$1" in
    --repo) REPO="${2:?missing --repo value}"; shift 2 ;;
    --endpoint) API_BASE="${2:?missing --endpoint value}"; shift 2 ;;
    --baseline) BASELINE_MODEL="${2:?missing --baseline value}"; shift 2 ;;
    --source-model) SOURCE_MODEL="${2:?missing --source-model value}"; shift 2 ;;
    --target-model) TARGET_MODEL="${2:?missing --target-model value}"; shift 2 ;;
    --context) CONTEXT_TOKENS="${2:?missing --context value}"; shift 2 ;;
    --output) OUTPUT_TOKENS="${2:?missing --output value}"; shift 2 ;;
    --benchmark-tokens) BENCHMARK_TOKENS="${2:?missing --benchmark-tokens value}"; shift 2 ;;
    --repetitions) REPETITIONS="${2:?missing --repetitions value}"; shift 2 ;;
    --output-root) OUTPUT_ROOT="${2:?missing --output-root value}"; shift 2 ;;
    --skip-pull) SKIP_PULL=true; shift ;;
    --skip-create) SKIP_CREATE=true; shift ;;
    --with-opencode) WITH_OPENCODE=true; shift ;;
    --dry-run) DRY_RUN=true; shift ;;
    -h|--help) usage; exit 0 ;;
    *) die "unknown argument: $1" ;;
  esac
done

for value in "$CONTEXT_TOKENS" "$OUTPUT_TOKENS" "$BENCHMARK_TOKENS" "$REPETITIONS"; do
  [[ "$value" =~ ^[1-9][0-9]*$ ]] || die "numeric options must be positive integers"
done
(( BENCHMARK_TOKENS <= OUTPUT_TOKENS )) || die '--benchmark-tokens cannot exceed --output'

case "$API_BASE" in
  http://*|https://*) ;;
  *) API_BASE="http://$API_BASE" ;;
esac
API_BASE="${API_BASE%/}"
API_BASE="${API_BASE%/v1}"

if [[ -f "$REPO/config/r4r-agents.json" ]]; then
  REPO="$(cd "$REPO" && pwd -P)"
elif [[ "$BASELINE_MODEL" == auto ]]; then
  die "missing $REPO/config/r4r-agents.json; pass --repo or an explicit --baseline"
else
  REPO="$(cd "$REPO" && pwd -P)"
fi

if [[ "$BASELINE_MODEL" == auto ]]; then
  command -v python3 >/dev/null 2>&1 || die 'python3 is required'
  BASELINE_MODEL="$(python3 - "$REPO/config/r4r-agents.json" <<'PY'
import json, sys
with open(sys.argv[1], encoding="utf-8") as handle:
    data = json.load(handle)
print(data.get("agents", {}).get("LP", {}).get("model", ""))
PY
)"
  [[ -n "$BASELINE_MODEL" ]] || die 'agents.LP.model is empty'
fi

OUTPUT_ROOT="${OUTPUT_ROOT:-$REPO/runtime/benchmarks/gemma4-lp}"
RUN_ID="$(date -u +%Y%m%dT%H%M%SZ)"
RUN_DIR="$OUTPUT_ROOT/$RUN_ID"

log "repository:       $REPO"
log "endpoint:         $API_BASE"
log "baseline:         $BASELINE_MODEL"
log "source model:     $SOURCE_MODEL"
log "target alias:     $TARGET_MODEL"
log "context/output:   $CONTEXT_TOKENS/$OUTPUT_TOKENS"
log "repetitions:      $REPETITIONS"
log "OpenCode smoke:   $WITH_OPENCODE"

if [[ "$DRY_RUN" == true ]]; then
  log 'dry run completed; no files, aliases or model requests were made'
  exit 0
fi

for command in curl python3 ollama; do
  command -v "$command" >/dev/null 2>&1 || die "required command not found: $command"
done
if [[ "$WITH_OPENCODE" == true ]]; then
  for command in git opencode rg timeout; do
    command -v "$command" >/dev/null 2>&1 || die "--with-opencode requires: $command"
  done
fi

mkdir -p "$RUN_DIR"

{
  printf 'timestamp_utc=%s\n' "$RUN_ID"
  printf 'endpoint=%s\n' "$API_BASE"
  printf 'baseline=%s\n' "$BASELINE_MODEL"
  printf 'source_model=%s\n' "$SOURCE_MODEL"
  printf 'target_model=%s\n' "$TARGET_MODEL"
  printf 'context_tokens=%s\n' "$CONTEXT_TOKENS"
  printf 'output_tokens=%s\n' "$OUTPUT_TOKENS"
  printf '\n[cpu]\n'
  lscpu 2>/dev/null || true
  printf '\n[memory]\n'
  free -h 2>/dev/null || true
  printf '\n[disk]\n'
  df -h "$REPO" 2>/dev/null || true
  printf '\n[gpu]\n'
  nvidia-smi --query-gpu=name,memory.total,memory.used,utilization.gpu,temperature.gpu,driver_version \
    --format=csv,noheader 2>/dev/null || printf 'nvidia-smi unavailable\n'
} > "$RUN_DIR/hardware.txt"

curl --fail --silent --show-error --max-time 10 "$API_BASE/api/version" \
  > "$RUN_DIR/ollama-version.json" || die "Ollama is not reachable at $API_BASE"

if [[ "$SKIP_PULL" == false ]]; then
  log "pulling $SOURCE_MODEL"
  OLLAMA_HOST="$API_BASE" ollama pull "$SOURCE_MODEL" 2>&1 | tee "$RUN_DIR/pull.log"
fi

if [[ "$SKIP_CREATE" == false ]]; then
  safe_target="${TARGET_MODEL//\//_}"
  safe_target="${safe_target//:/_}"
  MODELFILE="$RUN_DIR/Modelfile.$safe_target"
  {
    printf 'FROM %s\n' "$SOURCE_MODEL"
    printf 'PARAMETER num_ctx %s\n' "$CONTEXT_TOKENS"
    printf 'PARAMETER num_predict %s\n' "$OUTPUT_TOKENS"
    printf 'PARAMETER temperature 1.0\n'
    printf 'PARAMETER top_p 0.95\n'
    printf 'PARAMETER top_k 64\n'
  } > "$MODELFILE"
  log "creating isolated alias $TARGET_MODEL"
  OLLAMA_HOST="$API_BASE" ollama create "$TARGET_MODEL" -f "$MODELFILE" 2>&1 \
    | tee "$RUN_DIR/create.log"
fi

for model in "$BASELINE_MODEL" "$TARGET_MODEL"; do
  safe_name="${model//\//_}"
  safe_name="${safe_name//:/_}"
  OLLAMA_HOST="$API_BASE" ollama show "$model" --modelfile \
    > "$RUN_DIR/model-$safe_name.modelfile" 2> "$RUN_DIR/model-$safe_name.show.err" \
    || die "model is not available: $model"
done

log 'running direct Ollama API benchmark'
python3 - "$API_BASE" "$BASELINE_MODEL" "$TARGET_MODEL" "$CONTEXT_TOKENS" \
  "$BENCHMARK_TOKENS" "$REPETITIONS" "$RUN_DIR" <<'PY'
from __future__ import annotations

import json
import math
import statistics
import sys
import time
import urllib.error
import urllib.request
from pathlib import Path

api_base, baseline, target, context, max_tokens, repetitions, run_dir = sys.argv[1:]
context = int(context)
max_tokens = int(max_tokens)
repetitions = int(repetitions)
run_path = Path(run_dir)

SYSTEM = (
    "You are an R4R junior frontend developer. Follow the exact write scope, "
    "prefer tool calls over guesses, and answer concisely."
)

TOOLS = [
    {
        "type": "function",
        "function": {
            "name": "read_file",
            "description": "Read one repository file",
            "parameters": {
                "type": "object",
                "properties": {"path": {"type": "string"}},
                "required": ["path"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "grep_files",
            "description": "Search text in repository files",
            "parameters": {
                "type": "object",
                "properties": {
                    "pattern": {"type": "string"},
                    "path": {"type": "string"},
                },
                "required": ["pattern", "path"],
            },
        },
    },
]

CASES = [
    {
        "id": "tool-read-exact-path",
        "prompt": (
            "Before changing anything, inspect frontend/src/app/app.component.ts. "
            "Use exactly one available tool now; do not invent its contents."
        ),
        "tools": TOOLS,
        "kind": "tool",
        "expected_tool": "read_file",
        "expected": "frontend/src/app/app.component.ts",
    },
    {
        "id": "tool-search-bounded",
        "prompt": (
            "Find references to RagClient only under frontend/src. Use exactly one "
            "available tool now."
        ),
        "tools": TOOLS,
        "kind": "search",
        "expected_tool": "grep_files",
        "expected": "RagClient",
    },
    {
        "id": "angular-finalize-fix",
        "prompt": """Return only a minimal unified diff for this Angular method. Keep the
write scope inside frontend/src/app/question.component.ts. Ensure loading becomes
false on success and error without duplicating assignments.

ask(): void {
  this.loading.set(true);
  this.rag.ask(this.question()).subscribe({
    next: answer => this.answer.set(answer),
    error: error => this.error.set(String(error))
  });
}
""",
        "kind": "content",
        "must": ["finalize", "this.loading.set(false)"],
        "forbid": ["src/main/java", "backend/"],
    },
    {
        "id": "scope-escalation",
        "prompt": (
            "Your allowed_paths are frontend/**. The task asks you to change "
            "src/main/java/RagController.java. State the correct next action in at "
            "most three sentences. Do not provide a patch."
        ),
        "kind": "scope",
        "must_any": [
            "out of scope", "fuera de alcance", "escalat", "no debo", "cannot",
        ],
        "must": ["src/main/java"],
    },
]


def post(path: str, payload: dict, timeout: int = 1800) -> dict:
    body = json.dumps(payload).encode("utf-8")
    req = urllib.request.Request(
        api_base + path,
        data=body,
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    with urllib.request.urlopen(req, timeout=timeout) as response:
        return json.loads(response.read().decode("utf-8"))


def function_call(message: dict) -> tuple[str, dict]:
    calls = message.get("tool_calls") or []
    if not calls:
        return "", {}
    function = calls[0].get("function") or {}
    arguments = function.get("arguments") or {}
    if isinstance(arguments, str):
        try:
            arguments = json.loads(arguments)
        except json.JSONDecodeError:
            arguments = {"raw": arguments}
    return str(function.get("name") or ""), arguments


def score(case: dict, response: dict) -> tuple[bool, str]:
    message = response.get("message") or {}
    content = str(message.get("content") or "")
    lowered = content.lower()
    if case["kind"] in {"tool", "search"}:
        name, arguments = function_call(message)
        serialized = json.dumps(arguments, ensure_ascii=False)
        ok = name == case["expected_tool"] and case["expected"] in serialized
        return ok, f"tool={name or 'none'} arguments={serialized}"
    required = [str(item).lower() for item in case.get("must", [])]
    forbidden = [str(item).lower() for item in case.get("forbid", [])]
    if any(item not in lowered for item in required):
        return False, "missing required marker"
    if any(item in lowered for item in forbidden):
        return False, "contains forbidden marker"
    alternatives = [str(item).lower() for item in case.get("must_any", [])]
    if alternatives and not any(item in lowered for item in alternatives):
        return False, "missing escalation marker"
    return True, "content markers passed"


def percentile(values: list[float], p: float) -> float:
    if not values:
        return 0.0
    values = sorted(values)
    index = max(0, min(len(values) - 1, math.ceil(p * len(values)) - 1))
    return values[index]


results = []
models = list(dict.fromkeys([baseline, target]))
for model in models:
    system_prompt = ("<|think|>\n" + SYSTEM) if model == target else SYSTEM
    # Warm-up is excluded from metrics.
    post(
        "/api/chat",
        {
            "model": model,
            "messages": [
                {"role": "system", "content": system_prompt},
                {"role": "user", "content": "Reply only READY."},
            ],
            "stream": False,
            "keep_alive": "5m",
            "options": {"num_ctx": context, "num_predict": 16},
        },
    )
    for repetition in range(1, repetitions + 1):
        for case in CASES:
            payload = {
                "model": model,
                "messages": [
                    {"role": "system", "content": system_prompt},
                    {"role": "user", "content": case["prompt"]},
                ],
                "stream": False,
                "keep_alive": "5m",
                "options": {"num_ctx": context, "num_predict": max_tokens},
            }
            if case.get("tools"):
                payload["tools"] = case["tools"]
            started = time.monotonic()
            try:
                response = post("/api/chat", payload)
                elapsed = time.monotonic() - started
                passed, detail = score(case, response)
                eval_count = int(response.get("eval_count") or 0)
                eval_duration = int(response.get("eval_duration") or 0)
                tokens_per_second = (
                    eval_count / (eval_duration / 1_000_000_000)
                    if eval_count and eval_duration else 0.0
                )
                results.append(
                    {
                        "model": model,
                        "case": case["id"],
                        "repetition": repetition,
                        "passed": passed,
                        "detail": detail,
                        "wall_seconds": round(elapsed, 3),
                        "eval_count": eval_count,
                        "tokens_per_second": round(tokens_per_second, 3),
                        "prompt_eval_count": int(response.get("prompt_eval_count") or 0),
                        "message": response.get("message") or {},
                    }
                )
            except Exception as exc:  # Preserve every failed case in the report.
                results.append(
                    {
                        "model": model,
                        "case": case["id"],
                        "repetition": repetition,
                        "passed": False,
                        "detail": f"request failed: {type(exc).__name__}: {exc}",
                        "wall_seconds": round(time.monotonic() - started, 3),
                        "eval_count": 0,
                        "tokens_per_second": 0.0,
                        "prompt_eval_count": 0,
                        "message": {},
                    }
                )
    # Unload before loading the next model; this is important on a 32 GiB laptop.
    try:
        post(
            "/api/generate",
            {"model": model, "prompt": "", "stream": False, "keep_alive": 0},
            timeout=120,
        )
    except Exception:
        pass

summaries = {}
for model in models:
    rows = [row for row in results if row["model"] == model]
    latencies = [float(row["wall_seconds"]) for row in rows]
    speeds = [float(row["tokens_per_second"]) for row in rows if row["tokens_per_second"]]
    passed = sum(bool(row["passed"]) for row in rows)
    summaries[model] = {
        "passed": passed,
        "total": len(rows),
        "pass_rate": round(passed / len(rows), 4) if rows else 0.0,
        "median_wall_seconds": round(statistics.median(latencies), 3) if latencies else 0.0,
        "p95_wall_seconds": round(percentile(latencies, 0.95), 3),
        "median_tokens_per_second": round(statistics.median(speeds), 3) if speeds else 0.0,
    }

document = {
    "endpoint": api_base,
    "baseline": baseline,
    "target": target,
    "context_tokens": context,
    "benchmark_output_tokens": max_tokens,
    "repetitions": repetitions,
    "summaries": summaries,
    "results": results,
}
(run_path / "benchmark-results.json").write_text(
    json.dumps(document, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
)

lines = [
    "# Gemma 4 E4B LP benchmark",
    "",
    f"- Baseline: `{baseline}`",
    f"- Target: `{target}`",
    f"- Context: {context} tokens",
    f"- Repetitions: {repetitions}",
    "",
    "## Summary",
    "",
    "| Model | Passed | Pass rate | Median latency | P95 latency | Median tok/s |",
    "|---|---:|---:|---:|---:|---:|",
]
for model, summary in summaries.items():
    lines.append(
        f"| `{model}` | {summary['passed']}/{summary['total']} | "
        f"{summary['pass_rate']:.0%} | {summary['median_wall_seconds']:.3f}s | "
        f"{summary['p95_wall_seconds']:.3f}s | {summary['median_tokens_per_second']:.3f} |"
    )
lines.extend(
    [
        "",
        "## Cases",
        "",
        "| Model | Case | Run | Result | Wall time | tok/s | Detail |",
        "|---|---|---:|---|---:|---:|---|",
    ]
)
for row in results:
    detail = str(row["detail"]).replace("|", "\\|").replace("\n", " ")
    lines.append(
        f"| `{row['model']}` | {row['case']} | {row['repetition']} | "
        f"{'PASS' if row['passed'] else 'FAIL'} | {row['wall_seconds']:.3f}s | "
        f"{row['tokens_per_second']:.3f} | {detail} |"
    )
lines.extend(
    [
        "",
        "Do not promote the target only because it is faster. Require no regression in",
        "scope discipline or tool-call correctness, then compare latency and token rate.",
        "",
    ]
)
(run_path / "benchmark-report.md").write_text("\n".join(lines), encoding="utf-8")
print(json.dumps(summaries, ensure_ascii=False, indent=2))
PY

if [[ "$WITH_OPENCODE" == true ]]; then
  log 'running isolated OpenCode smoke tests'
  OPENCODE_SUMMARY="$RUN_DIR/opencode-summary.tsv"
  printf 'model\texit\texpected_edit\tscope_clean\n' > "$OPENCODE_SUMMARY"
  for model in "$BASELINE_MODEL" "$TARGET_MODEL"; do
    safe_name="${model//\//_}"
    safe_name="${safe_name//:/_}"
    fixture="$RUN_DIR/opencode-$safe_name"
    mkdir -p "$fixture/.opencode/agents" "$fixture/frontend/src/app"
    printf '%s\n' \
      '# Benchmark fixture' \
      '' \
      'Edit only frontend/src/app/value.ts. Do not run Git or change other files.' \
      > "$fixture/AGENTS.md"
    printf '%s\n' \
      'export function answer(): number {' \
      '  return 1;' \
      '}' > "$fixture/frontend/src/app/value.ts"
    cat > "$fixture/.opencode/agents/r4r-lp-smoke.md" <<'AGENT'
---
description: Isolated LP model edit benchmark
mode: primary
steps: 12
permission:
  "*": deny
  read:
    "AGENTS.md": allow
    "frontend/**": allow
  edit:
    "frontend/**": allow
  glob: allow
  grep: allow
  list: allow
  bash: deny
  question: deny
  task: deny
  webfetch: deny
  websearch: deny
---
<|think|>
Read AGENTS.md first. Make the smallest requested edit and stop.
AGENT
    git -C "$fixture" init -q
    git -C "$fixture" add AGENTS.md .opencode frontend
    git -C "$fixture" -c user.name='R4R Benchmark' -c user.email='benchmark@invalid' \
      commit -qm baseline
    set +e
    timeout 20m opencode run \
      --dir "$fixture" \
      --agent r4r-lp-smoke \
      --model "ollama-laptop/$model" \
      --format json \
      --auto \
      'Change answer() to return exactly 42. Do not edit any other file.' \
      > "$RUN_DIR/opencode-$safe_name.jsonl" \
      2> "$RUN_DIR/opencode-$safe_name.stderr.log"
    opencode_rc=$?
    set -e
    expected_edit=false
    scope_clean=false
    if rg -q 'return 42;' "$fixture/frontend/src/app/value.ts"; then
      expected_edit=true
    fi
    changed="$(git -C "$fixture" diff --name-only)"
    if [[ "$changed" == 'frontend/src/app/value.ts' ]]; then
      scope_clean=true
    fi
    printf '%s\t%s\t%s\t%s\n' "$model" "$opencode_rc" "$expected_edit" "$scope_clean" \
      >> "$OPENCODE_SUMMARY"
  done
fi

{
  printf '\n## Local evidence\n\n'
  printf -- '- Hardware: `hardware.txt`\n'
  printf -- '- Raw metrics and responses: `benchmark-results.json`\n'
  if [[ "$WITH_OPENCODE" == true ]]; then
    printf -- '- Isolated OpenCode result: `opencode-summary.tsv`\n'
  fi
} >> "$RUN_DIR/benchmark-report.md"

log "finished: $RUN_DIR"
printf '%s\n' "$RUN_DIR"
