#!/usr/bin/env bash
set -Eeuo pipefail

PROGRAM="$(basename "$0")"
REPO="$(pwd)"
BRANCH="${R4R_SURGICAL_BRANCH:-agent/opencode-dual-surgical}"
MODE="patch"                 # review | patch
PROMPT=""
PROMPT_FILE=""
OPENCODE_MODEL="${R4R_SURGICAL_OPENCODE_MODEL:-auto}"
OPENCODE_RETRIES=2
KEEP_WORKTREE=false
RUN_CODEX_REVIEW=false
WORK_ROOT="${R4R_DUAL_SURGICAL_WORK_ROOT:-/tmp/r4r-opencode-dual-surgical}"
OUTPUT_ROOT=""

log() { printf '[r4r-dual] %s\n' "$*" >&2; }
die() { log "ERROR: $*"; exit 2; }

usage() {
  cat <<USAGE
Usage:
  $PROGRAM [--branch REF] [options]

Required:
  --branch REF                  Branch, tag or commit to inspect.
                                Default: agent/opencode-dual-surgical.

Options:
  --repo PATH                   Git repository (default: current directory).
  --mode review|patch           Architect only, or architect then fixer (default: patch).
  --prompt TEXT                 Additional objective.
  --prompt-file PATH            Read the objective from a file.
  --opencode-model MODEL        provider/model, or auto from config/r4r-agents.json.
  --opencode-retries N          Attempts per OpenCode agent (default: 2).
  --output-root PATH            Default: REPO/runtime/opencode-dual-surgical.
  --codex-review                Run final Codex read-only assessment when available.
  --keep-worktree               Preserve the detached worktree.
  -h, --help                    Show help.

The patch mode launches, in order:
  1. r4r-surgical-architect (read-only)
  2. r4r-surgical-fixer (isolated-worktree edits)
  3. deterministic validations
  4. optional Codex read-only review
USAGE
}

while (($#)); do
  case "$1" in
    --repo) REPO="${2:?missing --repo value}"; shift 2 ;;
    --branch) BRANCH="${2:?missing --branch value}"; shift 2 ;;
    --mode) MODE="${2:?missing --mode value}"; shift 2 ;;
    --prompt) PROMPT="${2:?missing --prompt value}"; shift 2 ;;
    --prompt-file) PROMPT_FILE="${2:?missing --prompt-file value}"; shift 2 ;;
    --opencode-model) OPENCODE_MODEL="${2:?missing --opencode-model value}"; shift 2 ;;
    --opencode-retries) OPENCODE_RETRIES="${2:?missing --opencode-retries value}"; shift 2 ;;
    --output-root) OUTPUT_ROOT="${2:?missing --output-root value}"; shift 2 ;;
    --codex-review) RUN_CODEX_REVIEW=true; shift ;;
    --keep-worktree) KEEP_WORKTREE=true; shift ;;
    -h|--help) usage; exit 0 ;;
    *) die "unknown argument: $1" ;;
  esac
done

[[ -n "$BRANCH" ]] || { usage >&2; die '--branch must not be empty'; }
[[ "$MODE" == review || "$MODE" == patch ]] || die '--mode must be review or patch'
[[ "$OPENCODE_RETRIES" =~ ^[1-9][0-9]*$ ]] || die '--opencode-retries must be positive'

for command in git python3 opencode; do
  command -v "$command" >/dev/null 2>&1 || die "required command not found: $command"
done
GIT_BIN="$(command -v git)"
PYTHON_BIN="$(command -v python3)"
OPENCODE_BIN="$(command -v opencode)"
CODEX_BIN="$(command -v codex 2>/dev/null || true)"

REPO="$($GIT_BIN -C "$REPO" rev-parse --show-toplevel 2>/dev/null)" || die "not a Git repository: $REPO"
REF_COMMIT="$($GIT_BIN -C "$REPO" rev-parse --verify "${BRANCH}^{commit}" 2>/dev/null)" || die "ref not found: $BRANCH"

if [[ -n "$PROMPT_FILE" ]]; then
  [[ -f "$PROMPT_FILE" ]] || die "prompt file not found: $PROMPT_FILE"
  [[ -z "$PROMPT" ]] || PROMPT+=$'\n\n'
  PROMPT+="$(cat "$PROMPT_FILE")"
fi

resolve_model() {
  if [[ "$OPENCODE_MODEL" != auto ]]; then printf '%s\n' "$OPENCODE_MODEL"; return; fi
  local config
  config="$($GIT_BIN -C "$REPO" show "$REF_COMMIT:config/r4r-agents.json" 2>/dev/null || true)"
  [[ -n "$config" ]] || return 1
  printf '%s' "$config" | "$PYTHON_BIN" -c '
import json, sys
d=json.load(sys.stdin)
surgical=d.get("agents",{}).get("SURGICAL",{})
p=str(surgical.get("provider","")).strip(); m=str(surgical.get("model","")).strip()
if p and m: print(f"{p}/{m}")
'
}
MODEL="$(resolve_model)" || die 'unable to resolve agents.SURGICAL model; pass --opencode-model provider/model'
[[ -n "$MODEL" ]] || die 'resolved OpenCode model is empty'

RUN_ID="$(date -u +%Y%m%dT%H%M%SZ)-${REF_COMMIT:0:12}"
OUTPUT_ROOT="${OUTPUT_ROOT:-$REPO/runtime/opencode-dual-surgical}"
RUN_DIR="$OUTPUT_ROOT/$RUN_ID"
WORKTREE="$WORK_ROOT/$RUN_ID/worktree"
mkdir -p "$RUN_DIR" "$(dirname "$WORKTREE")"

cleanup() {
  local rc=$?
  if [[ "$KEEP_WORKTREE" == false && -d "$WORKTREE" ]]; then
    "$GIT_BIN" -C "$REPO" worktree remove --force "$WORKTREE" >/dev/null 2>&1 || true
  fi
  "$GIT_BIN" -C "$REPO" worktree prune >/dev/null 2>&1 || true
  exit "$rc"
}
trap cleanup EXIT INT TERM

log "repository:      $REPO"
log "selected ref:    $BRANCH"
log "selected commit: $REF_COMMIT"
log "model:           $MODEL"
log "mode:            $MODE"
log "results:         $RUN_DIR"
log "worktree:        $WORKTREE"

"$GIT_BIN" -C "$REPO" worktree add --detach "$WORKTREE" "$REF_COMMIT" >/dev/null

for required in \
  .opencode/agents/r4r-surgical-architect.md \
  .opencode/agents/r4r-surgical-fixer.md; do
  [[ -f "$WORKTREE/$required" ]] || die "selected commit lacks $required; install/commit phase 2.19 first"
done

# Mask likely credentials before model access. Examples and samples remain visible.
MASKED="$RUN_DIR/masked-sensitive-paths.txt"
: > "$MASKED"
while IFS= read -r -d '' path; do
  rel="${path#"$WORKTREE/"}"
  case "$rel" in .env.example|*.env.example|*.example|*.sample) continue ;; esac
  printf '%s\n' "$rel" >> "$MASKED"
  printf '%s\n' '# MASKED BY R4R DUAL SURGICAL REVIEW.' > "$path"
done < <(find "$WORKTREE" -type f \( -name '.env' -o -name '.env.*' -o -name '*credentials*.json' -o -name '*secret*.json' \) -print0 2>/dev/null)

extract_text() {
  "$PYTHON_BIN" - "$1" <<'PY'
import json, sys
p=sys.argv[1]
out=[]
with open(p, encoding='utf-8', errors='replace') as f:
    for raw in f:
        raw=raw.strip()
        if not raw: continue
        try: obj=json.loads(raw)
        except Exception: continue
        part=obj.get('part') or {}
        text=part.get('text') if isinstance(part, dict) else None
        if isinstance(text,str) and text.strip(): out.append(text)
        elif isinstance(obj.get('text'),str) and obj['text'].strip(): out.append(obj['text'])
print('\n'.join(out))
PY
}

run_agent() {
  local agent="$1" prompt="$2" raw="$3" err="$4" report="$5"
  local attempt rc=1
  for ((attempt=1; attempt<=OPENCODE_RETRIES; attempt++)); do
    log "$agent attempt $attempt/$OPENCODE_RETRIES"
    set +e
    "$OPENCODE_BIN" run \
      --dir "$WORKTREE" \
      --agent "$agent" \
      --model "$MODEL" \
      --format json \
      --auto \
      "$prompt" >"$raw.attempt-$attempt.jsonl" 2>"$err.attempt-$attempt.log"
    rc=$?
    set -e
    extract_text "$raw.attempt-$attempt.jsonl" > "$report.attempt-$attempt.md" || true
    if [[ $rc -eq 0 && -s "$report.attempt-$attempt.md" ]]; then
      cp "$raw.attempt-$attempt.jsonl" "$raw"
      cp "$err.attempt-$attempt.log" "$err"
      cp "$report.attempt-$attempt.md" "$report"
      return 0
    fi
  done
  printf '%s\n' "$rc" > "$report.exit-code"
  return 1
}

ARCH_PROMPT="Audit the complete selected R4R repository. User objective:\n${PROMPT:-Find and rank defects in agent processes, permissions, gates, evidence, worktrees and restarts.}"
log 'running r4r-surgical-architect'
if ! run_agent \
  r4r-surgical-architect "$ARCH_PROMPT" \
  "$RUN_DIR/architect.raw.jsonl" "$RUN_DIR/architect.stderr.log" "$RUN_DIR/architect-analysis.md"; then
  printf 'BLOCKED_OPENCODE_ARCHITECT\n' > "$RUN_DIR/status.txt"
  log "finished with BLOCKED_OPENCODE_ARCHITECT; inspect $RUN_DIR"
  exit 70
fi

mkdir -p "$WORKTREE/.opencode/current/surgical"
cp "$RUN_DIR/architect-analysis.md" "$WORKTREE/.opencode/current/surgical/architect-analysis.md"

if [[ "$MODE" == patch ]]; then
  FIX_PROMPT="Read .opencode/current/surgical/architect-analysis.md. Apply the minimal justified corrections for this objective:\n${PROMPT:-Correct the defects identified by the architect.}"
  log 'running r4r-surgical-fixer'
  if ! run_agent \
    r4r-surgical-fixer "$FIX_PROMPT" \
    "$RUN_DIR/fixer.raw.jsonl" "$RUN_DIR/fixer.stderr.log" "$RUN_DIR/fixer-summary.md"; then
    printf 'BLOCKED_OPENCODE_FIXER\n' > "$RUN_DIR/status.txt"
    log "finished with BLOCKED_OPENCODE_FIXER; inspect $RUN_DIR"
    exit 71
  fi
fi

rm -rf "$WORKTREE/.opencode/current/surgical"
while IFS= read -r rel; do
  [[ -n "$rel" ]] || continue
  "$GIT_BIN" -C "$WORKTREE" checkout -- "$rel" 2>/dev/null || "$GIT_BIN" -C "$WORKTREE" clean -f -- "$rel" >/dev/null 2>&1 || true
done < "$MASKED"

HEAD_AFTER="$($GIT_BIN -C "$WORKTREE" rev-parse HEAD)"
[[ "$HEAD_AFTER" == "$REF_COMMIT" ]] || { printf 'GIT_HISTORY_VIOLATION\n' > "$RUN_DIR/status.txt"; exit 74; }

"$GIT_BIN" -C "$WORKTREE" status --short > "$RUN_DIR/git-status.txt"
"$GIT_BIN" -C "$WORKTREE" add -N -- . >/dev/null 2>&1 || true
"$GIT_BIN" -C "$WORKTREE" diff --name-only > "$RUN_DIR/changed-paths.txt"
"$GIT_BIN" -C "$WORKTREE" diff --binary > "$RUN_DIR/changes.patch"

VALIDATION_FAILED=false
set +e
find "$WORKTREE/scripts" -type f -name '*.sh' -print0 2>/dev/null | xargs -0 -r bash -n >"$RUN_DIR/shell-syntax.log" 2>&1
SHELL_RC=$?
"$PYTHON_BIN" -m compileall -q "$WORKTREE/py-codex-agent" "$WORKTREE/py-ring-agent" "$WORKTREE/scripts" >"$RUN_DIR/python-compile.log" 2>&1
PYCOMPILE_RC=$?
if [[ -d "$WORKTREE/py-codex-agent/tests" ]]; then
  (cd "$WORKTREE" && "$PYTHON_BIN" -m unittest discover -s py-codex-agent/tests -p 'test*.py') >"$RUN_DIR/python-tests.log" 2>&1
  PYTEST_RC=$?
else
  printf 'No py-codex-agent/tests directory.\n' >"$RUN_DIR/python-tests.log"
  PYTEST_RC=0
fi
set -e
printf 'shell=%s\npython_compile=%s\npython_tests=%s\n' "$SHELL_RC" "$PYCOMPILE_RC" "$PYTEST_RC" > "$RUN_DIR/validation-exit-codes.txt"
(( SHELL_RC == 0 && PYCOMPILE_RC == 0 && PYTEST_RC == 0 )) || VALIDATION_FAILED=true

if [[ "$RUN_CODEX_REVIEW" == true ]]; then
  [[ -n "$CODEX_BIN" ]] || die '--codex-review requested but codex is missing'
  {
    printf 'Review this isolated R4R surgical change in read-only mode.\n\nARCHITECT:\n'
    cat "$RUN_DIR/architect-analysis.md"
    printf '\n\nFIXER:\n'
    cat "$RUN_DIR/fixer-summary.md" 2>/dev/null || true
    printf '\n\nVALIDATION:\n'
    cat "$RUN_DIR/validation-exit-codes.txt"
    printf '\n\nPATCH:\n'
    cat "$RUN_DIR/changes.patch"
  } | "$CODEX_BIN" exec --sandbox read-only --ephemeral -o "$RUN_DIR/codex-review.md" - >"$RUN_DIR/codex.stdout.log" 2>"$RUN_DIR/codex.stderr.log" || {
    printf 'CODEX_REVIEW_FAILED\n' > "$RUN_DIR/status.txt"
    exit 73
  }
fi

if [[ "$VALIDATION_FAILED" == true ]]; then
  STATUS=VALIDATION_FAILED
  RC=72
elif [[ -s "$RUN_DIR/changes.patch" ]]; then
  STATUS=SUCCESS
  RC=0
else
  STATUS=SUCCESS_NO_CHANGES
  RC=0
fi
printf '%s\n' "$STATUS" > "$RUN_DIR/status.txt"
cat > "$RUN_DIR/RESULT.txt" <<RESULT
R4R OpenCode dual surgical run

Status:          $STATUS
Repository:      $REPO
Selected ref:    $BRANCH
Selected commit: $REF_COMMIT
Mode:            $MODE
Model:           $MODEL
Worktree:        $WORKTREE
Worktree kept:   $KEEP_WORKTREE

Artifacts:
- architect-analysis.md
- fixer-summary.md
- changed-paths.txt
- changes.patch
- git-status.txt
- validation-exit-codes.txt
- codex-review.md (when requested)
RESULT
log "finished with $STATUS (exit $RC); inspect $RUN_DIR/RESULT.txt"
exit "$RC"
