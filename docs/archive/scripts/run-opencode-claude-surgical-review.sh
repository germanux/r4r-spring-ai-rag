#!/usr/bin/env bash
set -Eeuo pipefail

# OpenCode -> Claude Code whole-branch surgical reviewer.
#
# The selected Git revision is materialized in an isolated detached worktree. OpenCode
# performs a read-only architecture pass, then Claude Code either reviews or edits that
# isolated worktree. The source branch and its worktree are never modified.

PROGRAM="$(basename "$0")"
REPO="$(pwd)"
BRANCH=""
MODE="review"                    # review | patch
PROMPT=""
PROMPT_FILE=""
MODEL=""
MAX_TURNS=36
OPENCODE_RETRIES=2
OPENCODE_MODEL="${R4R_SURGICAL_OPENCODE_MODEL:-auto}"
CLAUDE_BIN_OPTION="${R4R_CLAUDE_BIN:-}"
ALLOW_OPENCODE_FALLBACK=false
KEEP_WORKTREE=false
RUN_CODEX_REVIEW=false
WORK_ROOT="${R4R_CLAUDE_WORK_ROOT:-/tmp/r4r-claude-surgical}"
OUTPUT_ROOT=""

log() { printf '[r4r-claude] %s\n' "$*" >&2; }
die() { log "ERROR: $*"; exit 2; }

usage() {
  cat <<EOF
Usage:
  $PROGRAM --branch REF [options]

Required:
  --branch REF            Branch, tag or commit to inspect.

Options:
  --repo PATH             Git repository (default: current directory).
  --mode review|patch     Read-only review or edits in an isolated worktree.
  --prompt TEXT           Additional objective or defect description.
  --prompt-file PATH      Read the additional objective from a file.
  --model MODEL           Claude model alias or full model ID.
  --max-turns N           Claude Code agentic turn limit (default: $MAX_TURNS).
  --opencode-retries N     OpenCode architecture attempts (default: $OPENCODE_RETRIES).
  --opencode-model MODEL    OpenCode provider/model, or auto from config/r4r-agents.json.
  --claude-bin PATH         Exact Claude Code executable to use.
  --allow-opencode-fallback Continue with Claude after recorded OpenCode provider failure.
  --output-root PATH      Result directory root (default: REPO/runtime/claude-surgical).
  --codex-review          Run a final read-only Codex assessment when codex is installed.
  --keep-worktree         Preserve the temporary worktree for manual inspection.
  -h, --help              Show this help.

Examples:
  $PROGRAM --repo ~/Desarrollo/r4r-ring-agent.git --branch r4r-chatgpt

  $PROGRAM --repo ~/Desarrollo/r4r-ring-agent.git --branch agent/ring-agent-worker \\
    --mode patch --prompt 'Correct evidence capture, lifecycle and permission defects.'

Safety:
  * Never changes or checks out the source branch.
  * Masks likely credential files before either model can read the worktree.
  * Never commits, merges, pushes, resets or cleans the source repository.
  * Patch mode edits only the detached temporary worktree and emits changes.patch.
EOF
}

while (($#)); do
  case "$1" in
    --repo) REPO="${2:?missing --repo value}"; shift 2 ;;
    --branch) BRANCH="${2:?missing --branch value}"; shift 2 ;;
    --mode) MODE="${2:?missing --mode value}"; shift 2 ;;
    --prompt) PROMPT="${2:?missing --prompt value}"; shift 2 ;;
    --prompt-file) PROMPT_FILE="${2:?missing --prompt-file value}"; shift 2 ;;
    --model) MODEL="${2:?missing --model value}"; shift 2 ;;
    --max-turns) MAX_TURNS="${2:?missing --max-turns value}"; shift 2 ;;
    --opencode-retries) OPENCODE_RETRIES="${2:?missing --opencode-retries value}"; shift 2 ;;
    --opencode-model) OPENCODE_MODEL="${2:?missing --opencode-model value}"; shift 2 ;;
    --claude-bin) CLAUDE_BIN_OPTION="${2:?missing --claude-bin value}"; shift 2 ;;
    --allow-opencode-fallback) ALLOW_OPENCODE_FALLBACK=true; shift ;;
    --output-root) OUTPUT_ROOT="${2:?missing --output-root value}"; shift 2 ;;
    --codex-review) RUN_CODEX_REVIEW=true; shift ;;
    --keep-worktree) KEEP_WORKTREE=true; shift ;;
    -h|--help) usage; exit 0 ;;
    *) die "unknown argument: $1" ;;
  esac
done

[[ -n "$BRANCH" ]] || { usage >&2; die '--branch is required'; }
[[ "$MODE" == review || "$MODE" == patch ]] || die '--mode must be review or patch'
[[ "$MAX_TURNS" =~ ^[1-9][0-9]*$ ]] || die '--max-turns must be a positive integer'
[[ "$OPENCODE_RETRIES" =~ ^[1-9][0-9]*$ ]] || die '--opencode-retries must be a positive integer'

for command in git python3 opencode; do
  command -v "$command" >/dev/null 2>&1 || die "required command not found: $command"
done
GIT_BIN="$(command -v git)"
PYTHON_BIN="$(command -v python3)"
OPENCODE_BIN="$(command -v opencode)"
CODEX_BIN="$(command -v codex 2>/dev/null || true)"

select_claude_bin() {
  local candidate resolved
  local -a candidates=()
  if [[ -n "$CLAUDE_BIN_OPTION" ]]; then
    [[ -x "$CLAUDE_BIN_OPTION" ]] || die "Claude executable is not executable: $CLAUDE_BIN_OPTION"
    printf '%s\n' "$CLAUDE_BIN_OPTION"
    return
  fi

  while IFS= read -r candidate; do
    [[ -n "$candidate" && -x "$candidate" ]] || continue
    resolved="$(readlink -f "$candidate" 2>/dev/null || printf '%s' "$candidate")"
    if [[ " ${candidates[*]-} " != *" $resolved "* ]]; then
      candidates+=("$resolved")
    fi
  done < <(type -a -p claude 2>/dev/null || true)

  ((${#candidates[@]})) || die "required command not found: claude"

  for candidate in "${candidates[@]}"; do
    if "$candidate" auth status >/dev/null 2>&1; then
      printf '%s\n' "$candidate"
      return
    fi
  done
  printf '%s\n' "${candidates[0]}"
}

CLAUDE_BIN="$(select_claude_bin)"
CLAUDE_AUTH_OK=false
if "$CLAUDE_BIN" auth status >/dev/null 2>&1; then
  CLAUDE_AUTH_OK=true
elif [[ -n "${ANTHROPIC_API_KEY:-}${ANTHROPIC_AUTH_TOKEN:-}${CLAUDE_CODE_OAUTH_TOKEN:-}"      || -n "${CLAUDE_CODE_USE_BEDROCK:-}${CLAUDE_CODE_USE_VERTEX:-}${CLAUDE_CODE_USE_FOUNDRY:-}" ]]; then
  CLAUDE_AUTH_OK=true
fi
[[ "$CLAUDE_AUTH_OK" == true ]] || die "Claude Code is not authenticated for $CLAUDE_BIN. Run: $CLAUDE_BIN auth login, or pass --claude-bin PATH to an authenticated installation."

REPO="$(git -C "$REPO" rev-parse --show-toplevel 2>/dev/null)" || die "not a Git repository: $REPO"
REF_COMMIT="$(git -C "$REPO" rev-parse --verify "${BRANCH}^{commit}" 2>/dev/null)" \
  || die "branch/ref not found locally: $BRANCH"
SOURCE_BRANCH="$(git -C "$REPO" branch --show-current 2>/dev/null || true)"
SOURCE_HEAD="$(git -C "$REPO" rev-parse HEAD)"
SOURCE_STATUS="$(git -C "$REPO" status --short)"

resolve_opencode_model() {
  if [[ -n "$OPENCODE_MODEL" && "$OPENCODE_MODEL" != auto ]]; then
    printf '%s\n' "$OPENCODE_MODEL"
    return
  fi

  local config_json ring_agent resolved
  config_json="$(git -C "$REPO" show "$REF_COMMIT:config/r4r-agents.json" 2>/dev/null || true)"
  if [[ -n "$config_json" ]]; then
    resolved="$(
      printf '%s' "$config_json" | "$PYTHON_BIN" -c '
import json, sys
data = json.load(sys.stdin)
pc = data.get("agents", {}).get("PC", {})
provider = str(pc.get("provider", "")).strip()
model = str(pc.get("model", "")).strip()
if provider and model:
    print(f"{provider}/{model}")
'
    )"
    if [[ -n "$resolved" ]]; then
      printf '%s\n' "$resolved"
      return
    fi
  fi

  ring_agent="$(git -C "$REPO" show "$REF_COMMIT:.opencode/agents/r4r-ring.md" 2>/dev/null || true)"
  if [[ -n "$ring_agent" ]]; then
    resolved="$(
      printf '%s' "$ring_agent" | "$PYTHON_BIN" -c '
import re, sys
text = sys.stdin.read()
m = re.search(r"(?m)^model:\s*[\"'\"']?([^\"'\"'\n]+)", text)
if m:
    print(m.group(1).strip())
'
    )"
    if [[ -n "$resolved" ]]; then
      printf '%s\n' "$resolved"
      return
    fi
  fi

  return 1
}

OPENCODE_MODEL_RESOLVED="$(resolve_opencode_model)" \
  || die "unable to resolve OpenCode model from selected commit; pass --opencode-model provider/model"

if [[ -n "$PROMPT_FILE" ]]; then
  [[ -f "$PROMPT_FILE" ]] || die "prompt file not found: $PROMPT_FILE"
  FILE_PROMPT="$(cat "$PROMPT_FILE")"
  if [[ -n "$PROMPT" ]]; then
    PROMPT+=$'\n\n'
  fi
  PROMPT+="$FILE_PROMPT"
fi

RUN_ID="$(date -u +%Y%m%dT%H%M%SZ)-${REF_COMMIT:0:12}"
OUTPUT_ROOT="${OUTPUT_ROOT:-$REPO/runtime/claude-surgical}"
RUN_DIR="$OUTPUT_ROOT/$RUN_ID"
WORKTREE="$WORK_ROOT/$RUN_ID/worktree"
mkdir -p "$RUN_DIR" "$(dirname "$WORKTREE")"

cleanup() {
  local rc=$?
  if [[ "$KEEP_WORKTREE" == false && -d "$WORKTREE" ]]; then
    git -C "$REPO" worktree remove --force "$WORKTREE" >/dev/null 2>&1 || true
  fi
  git -C "$REPO" worktree prune >/dev/null 2>&1 || true
  exit "$rc"
}
trap cleanup EXIT INT TERM

log "repository:     $REPO"
log "source branch:  ${SOURCE_BRANCH:-<detached>}"
log "selected ref:   $BRANCH"
log "selected commit:$REF_COMMIT"
log "mode:           $MODE"
log "results:        $RUN_DIR"
log "worktree:       $WORKTREE"

cat > "$RUN_DIR/source-state.txt" <<EOF
repository=$REPO
source_branch=${SOURCE_BRANCH:-<detached>}
source_head=$SOURCE_HEAD
selected_ref=$BRANCH
selected_commit=$REF_COMMIT
mode=$MODE
git_bin=$GIT_BIN
python_bin=$PYTHON_BIN
opencode_bin=$OPENCODE_BIN
claude_bin=$CLAUDE_BIN
codex_bin=${CODEX_BIN:-<missing>}
opencode_model=$OPENCODE_MODEL_RESOLVED
allow_opencode_fallback=$ALLOW_OPENCODE_FALLBACK

SOURCE WORKTREE STATUS
${SOURCE_STATUS:-<clean>}
EOF

git -C "$REPO" worktree add --detach "$WORKTREE" "$REF_COMMIT" >/dev/null

# Mask common credentials before any external model can inspect the branch. The files
# are restored from HEAD before producing the final patch.
SENSITIVE_LIST="$RUN_DIR/masked-sensitive-paths.txt"
: > "$SENSITIVE_LIST"
while IFS= read -r -d '' path; do
  rel="${path#"$WORKTREE/"}"
  case "$rel" in
    .env.example|*.env.example|*.example|*.sample) continue ;;
  esac
  printf '%s\n' "$rel" >> "$SENSITIVE_LIST"
  printf '%s\n' '# MASKED BY R4R SURGICAL REVIEW: credential-bearing file omitted.' > "$path"
done < <(
  find "$WORKTREE" -type f \
    \( -name '.env' -o -name '.env.*' -o -name 'credentials*.json' \
       -o -name 'client_secret*.json' -o -name 'token*.json' \
       -o -name '*.pem' -o -name '*.p12' -o -name '*.key' \
       -o -name 'id_rsa' -o -name 'id_ed25519' -o -name '.npmrc' \) \
    -not -path '*/.git/*' -print0
)

mkdir -p "$WORKTREE/.opencode/agents"
TEMP_AGENT="$WORKTREE/.opencode/agents/r4r-surgical-architect.md"
TEMP_AGENT_EXISTED=false
if [[ -e "$TEMP_AGENT" ]]; then
  TEMP_AGENT_EXISTED=true
  cp -a "$TEMP_AGENT" "$RUN_DIR/original-r4r-surgical-architect.md"
fi
{
  cat <<EOF
---
description: Whole-repository R4R architecture and agent-process auditor
mode: primary
model: "$OPENCODE_MODEL_RESOLVED"
temperature: 0.15
permission:
  read: allow
  glob: allow
  grep: allow
  list: allow
  edit: deny
  bash: deny
  task: deny
  external_directory: deny
  webfetch: deny
  websearch: deny
---
EOF
  cat <<'AGENT'

Inspect the complete current repository, not only product code. Reconstruct the actual
agentic system from source and configuration. Concentrate on `.opencode/**`,
`py-codex-agent/**`, `py-ring-agent/**`, `scripts/**`, `config/**`, AGENTS.md, task
plans, gates and their interaction with `src/**` and `frontend/**`.

Identify concrete defects in process ownership, worktree selection, Git safety,
permissions, evidence capture, retry/circuit-breaker behavior, subprocess lifecycle,
timeouts, status classification, stale paths, task completion, frontend browser
resolution, backend environment handling and Codex/OpenCode handoff.

Every finding must cite exact repository paths and observable behavior. Distinguish
implementation defects from instruction defects and infrastructure prerequisites.
Return a bounded surgical plan ordered by risk and dependency. Do not edit anything and
do not claim that a gate passed unless direct repository evidence proves it.
AGENT
} > "$TEMP_AGENT"

DEFAULT_OBJECTIVE='Audit the entire selected branch and surgically correct the agentic process architecture. Preserve product behavior unless a product change is necessary to make an exact gate meaningful. Eliminate false success, stale-worktree reads, repeated permission loops, incomplete evidence bundles, unsafe Git attribution and non-deterministic restart behavior.'
OBJECTIVE="${PROMPT:-$DEFAULT_OBJECTIVE}"

ARCH_PROMPT=$(cat <<EOF
Selected Git ref: $BRANCH
Selected immutable commit: $REF_COMMIT
Execution mode requested after your analysis: $MODE

Operator objective:
$OBJECTIVE

Produce a complete architecture analysis and a bounded correction plan for Claude Code.
EOF
)

log 'running OpenCode whole-repository architecture pass'
OPENCODE_EXIT=125
OPENCODE_PARSE_EXIT=125
OPENCODE_ATTEMPT=0
OPENCODE_OK=false
: > "$RUN_DIR/opencode-attempts.tsv"

for ((attempt = 1; attempt <= OPENCODE_RETRIES; attempt++)); do
  OPENCODE_ATTEMPT=$attempt
  attempt_raw="$RUN_DIR/opencode.attempt-${attempt}.raw.jsonl"
  attempt_err="$RUN_DIR/opencode.attempt-${attempt}.stderr.log"
  attempt_analysis="$RUN_DIR/opencode.attempt-${attempt}.analysis.md"

  log "OpenCode architecture attempt $attempt/$OPENCODE_RETRIES"
  set +e
  (
    cd "$WORKTREE"
    "$OPENCODE_BIN" run --dir "$WORKTREE" --agent r4r-surgical-architect --model "$OPENCODE_MODEL_RESOLVED" --format json --auto "$ARCH_PROMPT"
  ) > "$attempt_raw" 2> "$attempt_err"
  OPENCODE_EXIT=$?

  "$PYTHON_BIN" - "$attempt_raw" "$attempt_analysis" <<'PY'
import json
import pathlib
import sys

source = pathlib.Path(sys.argv[1])
target = pathlib.Path(sys.argv[2])
parts: list[str] = []
for raw in source.read_text(errors="replace").splitlines():
    try:
        obj = json.loads(raw)
    except json.JSONDecodeError:
        continue
    if obj.get("type") == "text":
        part = obj.get("part") or {}
        text = part.get("text") if isinstance(part, dict) else None
        if isinstance(text, str) and text.strip():
            parts.append(text.strip())
if not parts:
    target.write_text(
        "OpenCode did not emit a parseable text report. "
        "Inspect the raw JSONL and stderr for this attempt.\n"
    )
    raise SystemExit(3)
target.write_text("\n\n".join(parts) + "\n")
PY
  OPENCODE_PARSE_EXIT=$?
  set -e

  printf '%s\t%s\t%s\n' "$attempt" "$OPENCODE_EXIT" "$OPENCODE_PARSE_EXIT" >> "$RUN_DIR/opencode-attempts.tsv"
  cp -f "$attempt_raw" "$RUN_DIR/opencode.raw.jsonl"
  cp -f "$attempt_err" "$RUN_DIR/opencode.stderr.log"
  cp -f "$attempt_analysis" "$RUN_DIR/opencode-analysis.md"

  if (( OPENCODE_EXIT == 0 && OPENCODE_PARSE_EXIT == 0 )); then
    OPENCODE_OK=true
    break
  fi

  if (( attempt < OPENCODE_RETRIES )); then
    log "OpenCode attempt $attempt failed (exit=$OPENCODE_EXIT parse=$OPENCODE_PARSE_EXIT); retrying"
    sleep "$attempt"
  fi
done

printf '%s\n' "$OPENCODE_EXIT" > "$RUN_DIR/opencode.exit-code"
printf '%s\n' "$OPENCODE_PARSE_EXIT" > "$RUN_DIR/opencode.parse-exit-code"
printf '%s\n' "$OPENCODE_ATTEMPT" > "$RUN_DIR/opencode.final-attempt"
OPENCODE_FALLBACK_USED=false
if [[ "$OPENCODE_OK" != true && "$ALLOW_OPENCODE_FALLBACK" == true ]]; then
  OPENCODE_FALLBACK_USED=true
  cat > "$RUN_DIR/opencode-analysis.md" <<EOF
# OpenCode provider stage unavailable

OpenCode failed after $OPENCODE_ATTEMPT attempt(s).

- Selected model: $OPENCODE_MODEL_RESOLVED
- Final process exit: $OPENCODE_EXIT
- Final parse exit: $OPENCODE_PARSE_EXIT
- Raw events: opencode.raw.jsonl
- Standard error: opencode.stderr.log

Claude Code must independently inspect the repository and treat the missing OpenCode
analysis as an explicit evidence limitation. Do not infer that a no-op is correct.
EOF
fi
printf '%s\n' "$OPENCODE_MODEL_RESOLVED" > "$RUN_DIR/opencode.model"
printf '%s\n' "$OPENCODE_FALLBACK_USED" > "$RUN_DIR/opencode.fallback-used"
if [[ "$TEMP_AGENT_EXISTED" == true ]]; then
  cp -a "$RUN_DIR/original-r4r-surgical-architect.md" "$TEMP_AGENT"
else
  rm -f "$TEMP_AGENT"
fi

CLAUDE_SYSTEM="$RUN_DIR/claude-system-prompt.md"
cat > "$CLAUDE_SYSTEM" <<'EOF'
You are the surgical implementation stage of an R4R agent-system audit.

Analyze the entire repository and reconcile the OpenCode architecture report against the
actual files before trusting it. Concentrate on deterministic behavior, exact evidence,
minimal scope and tests. Never modify Git history. Never run git commit, merge, rebase,
reset, checkout, clean, stash or push. Never read or recreate credential files marked as
masked. Never edit runtime execution evidence, generated caches, node_modules, .git,
virtual environments, binary media, or downloaded reference repositories.

In review mode, make no edits. In patch mode, make the smallest coherent corrections in
the isolated worktree. Prefer correcting controllers, scripts, gates, agent definitions,
configuration and focused tests over broad rewrites. Preserve backend/frontend ownership.
At the end, report exact changed paths, rationale, remaining uncertainty and commands the
outer deterministic script should run.
EOF

CLAUDE_PROMPT="$RUN_DIR/claude-user-prompt.md"
{
  cat <<EOF
Repository ref: $BRANCH
Immutable starting commit: $REF_COMMIT
Mode: $MODE

Operator objective:
$OBJECTIVE

OpenCode architecture report:

EOF
  cat "$RUN_DIR/opencode-analysis.md"
  cat <<'EOF'

Required procedure:
1. Independently inspect the relevant repository files and verify or reject each finding.
2. Trace actual control flow across Ring, PC, LP, OpenCode and Codex.
3. In patch mode, implement only evidence-backed corrections in this isolated worktree.
4. Do not create commits or manipulate branches.
5. Return a precise final report with changed paths and validation recommendations.
EOF
} > "$CLAUDE_PROMPT"

CLAUDE_ARGS=(
  -p
  --output-format json
  --max-turns "$MAX_TURNS"
  --append-system-prompt-file "$CLAUDE_SYSTEM"
  --disable-slash-commands
)
if [[ -n "$MODEL" ]]; then
  CLAUDE_ARGS+=(--model "$MODEL")
fi

if [[ "$MODE" == review ]]; then
  CLAUDE_ARGS+=(
    --permission-mode plan
    --tools 'Read,Glob,Grep'
    --allowedTools 'Read' 'Glob' 'Grep'
    --disallowedTools 'Edit' 'Write' 'Bash' 'Agent' 'WebFetch' 'WebSearch'
  )
else
  CLAUDE_ARGS+=(
    --permission-mode acceptEdits
    --tools 'Read,Glob,Grep,Edit,Write'
    --allowedTools 'Read' 'Glob' 'Grep' 'Edit' 'Write'
    --disallowedTools 'Bash' 'Agent' 'WebFetch' 'WebSearch'
  )
fi

CLAUDE_EXIT=125
CLAUDE_PARSE_EXIT=125
CLAUDE_OK=false

if [[ "$OPENCODE_OK" == true || "$OPENCODE_FALLBACK_USED" == true ]]; then
  log "running Claude Code in $MODE mode"
  set +e
  (
    cd "$WORKTREE"
    "$CLAUDE_BIN" "${CLAUDE_ARGS[@]}" --input-format text       "Execute the complete surgical task specification provided through standard input."       < "$CLAUDE_PROMPT"
  ) > "$RUN_DIR/claude-result.json" 2> "$RUN_DIR/claude.stderr.log"
  CLAUDE_EXIT=$?

  "$PYTHON_BIN" - "$RUN_DIR/claude-result.json" "$RUN_DIR/claude-summary.md" <<'PY'
import json
import pathlib
import sys

source = pathlib.Path(sys.argv[1])
target = pathlib.Path(sys.argv[2])
raw = source.read_text(errors="replace")
if not raw.strip():
    target.write_text("Claude Code produced no result. Inspect claude.stderr.log.\n")
    raise SystemExit(3)
try:
    obj = json.loads(raw)
except json.JSONDecodeError:
    target.write_text(raw if raw.endswith("\n") else raw + "\n")
    raise SystemExit(4)
if isinstance(obj, dict) and (obj.get("is_error") is True or obj.get("type") == "error"):
    target.write_text(json.dumps(obj, indent=2, ensure_ascii=False) + "\n")
    raise SystemExit(5)
for key in ("result", "text", "content", "message"):
    value = obj.get(key) if isinstance(obj, dict) else None
    if isinstance(value, str) and value.strip():
        target.write_text(value.strip() + "\n")
        raise SystemExit(0)
target.write_text(json.dumps(obj, indent=2, ensure_ascii=False) + "\n")
raise SystemExit(6)
PY
  CLAUDE_PARSE_EXIT=$?
  set -e
  if (( CLAUDE_EXIT == 0 && CLAUDE_PARSE_EXIT == 0 )); then
    CLAUDE_OK=true
  fi
else
  printf '%s\n' 'Claude Code was not started because the OpenCode architecture pass failed and fallback was not enabled.' > "$RUN_DIR/claude-summary.md"
  : > "$RUN_DIR/claude-result.json"
  : > "$RUN_DIR/claude.stderr.log"
fi
printf '%s\n' "$CLAUDE_EXIT" > "$RUN_DIR/claude.exit-code"
printf '%s\n' "$CLAUDE_PARSE_EXIT" > "$RUN_DIR/claude.parse-exit-code"

# Restore masked tracked credentials and remove untracked masked credentials so they
# cannot contaminate the generated patch.
if [[ -s "$SENSITIVE_LIST" ]]; then
  while IFS= read -r rel; do
    [[ -n "$rel" ]] || continue
    if git -C "$WORKTREE" ls-files --error-unmatch -- "$rel" >/dev/null 2>&1; then
      git -C "$WORKTREE" restore --source=HEAD --staged --worktree -- "$rel"
    else
      rm -f "$WORKTREE/$rel"
    fi
  done < "$SENSITIVE_LIST"
fi

# Remove common generated files from the patch while retaining their logs outside the
# worktree. These paths are never accepted as surgical source changes.
rm -rf "$WORKTREE/runtime/claude-surgical" >/dev/null 2>&1 || true

(
  cd "$WORKTREE"
  git status --short
) > "$RUN_DIR/git-status.txt"
(
  cd "$WORKTREE"
  git diff --binary --no-ext-diff "$REF_COMMIT" -- . \
    ':(exclude)runtime/**' ':(exclude)**/node_modules/**' \
    ':(exclude)**/.venv/**' ':(exclude)**/dist/**' \
    ':(exclude)**/.angular/**' ':(exclude).r4r/**'
) > "$RUN_DIR/changes.patch"
(
  cd "$WORKTREE"
  git diff --name-status "$REF_COMMIT" -- . \
    ':(exclude)runtime/**' ':(exclude)**/node_modules/**' \
    ':(exclude)**/.venv/**' ':(exclude)**/dist/**' \
    ':(exclude)**/.angular/**' ':(exclude).r4r/**'
) > "$RUN_DIR/changed-paths.txt"

SHELL_SYNTAX_EXIT=125
PYTHON_COMPILE_EXIT=125
PYTHON_TESTS_EXIT=125
VALIDATION_OK=true
VALIDATION_PYTHON="$PYTHON_BIN"
if [[ -x "$REPO/py-codex-agent/.venv/bin/python" ]]; then
  VALIDATION_PYTHON="$REPO/py-codex-agent/.venv/bin/python"
fi
printf '%s\n' "$VALIDATION_PYTHON" > "$RUN_DIR/validation-python.txt"

if [[ "$MODE" == patch && "$CLAUDE_OK" == true ]]; then
  log 'running deterministic syntax and focused controller checks'

  set +e
  {
    printf 'COMMAND: bash -n scripts/*.sh scripts/lib/*.sh (existing files only)\n'
    mapfile -t shell_files < <(find "$WORKTREE/scripts" -maxdepth 2 -type f -name '*.sh' -print 2>/dev/null | sort)
    if ((${#shell_files[@]})); then
      bash -n "${shell_files[@]}"
    else
      printf 'No shell scripts found.\n'
    fi
  } > "$RUN_DIR/shell-syntax.log" 2>&1
  SHELL_SYNTAX_EXIT=$?

  {
    printf 'COMMAND: Python compile checks\n'
    printf 'PYTHON: %s\n' "$VALIDATION_PYTHON"
    mapfile -t py_files < <(find "$WORKTREE/py-ring-agent" "$WORKTREE/py-codex-agent" "$WORKTREE/scripts" \
      -type f -name '*.py' -not -path '*/.venv/*' -not -path '*/__pycache__/*' 2>/dev/null | sort)
    if ((${#py_files[@]})); then
      "$VALIDATION_PYTHON" -m py_compile "${py_files[@]}"
    else
      printf 'No Python files found.\n'
    fi
  } > "$RUN_DIR/python-compile.log" 2>&1
  PYTHON_COMPILE_EXIT=$?

  {
    printf 'COMMAND: unittest discovery for both controllers\n'
    printf 'PYTHON: %s\n' "$VALIDATION_PYTHON"
    cd "$WORKTREE"
    export PYTHONPATH="$WORKTREE/py-codex-agent/src:$WORKTREE/py-ring-agent/src${PYTHONPATH:+:$PYTHONPATH}"
    "$VALIDATION_PYTHON" -m unittest discover -s py-codex-agent/tests -p 'test*.py'
    "$VALIDATION_PYTHON" -m unittest discover -s py-ring-agent/tests -p 'test*.py'
  } > "$RUN_DIR/python-tests.log" 2>&1
  PYTHON_TESTS_EXIT=$?
  set -e

  printf '%s\n' "$SHELL_SYNTAX_EXIT" > "$RUN_DIR/shell-syntax.exit-code"
  printf '%s\n' "$PYTHON_COMPILE_EXIT" > "$RUN_DIR/python-compile.exit-code"
  printf '%s\n' "$PYTHON_TESTS_EXIT" > "$RUN_DIR/python-tests.exit-code"

  if (( SHELL_SYNTAX_EXIT != 0 || PYTHON_COMPILE_EXIT != 0 || PYTHON_TESTS_EXIT != 0 )); then
    VALIDATION_OK=false
  fi
else
  printf '%s\n' 'Validation skipped because patch mode was not active or Claude Code failed.' > "$RUN_DIR/validation-skipped.txt"
fi

CODEX_EXIT=125
CODEX_OK=true
if [[ "$RUN_CODEX_REVIEW" == true ]]; then
  if [[ "$CLAUDE_OK" != true || ( "$OPENCODE_OK" != true && "$OPENCODE_FALLBACK_USED" != true ) ]]; then
    printf '%s\n' 'Codex review skipped because a prerequisite model stage failed.' > "$RUN_DIR/codex-review.md"
    CODEX_OK=false
  elif [[ -n "$CODEX_BIN" ]]; then
    log 'running final Codex read-only assessment'
    set +e
    {
      cat <<EOF
Review the isolated whole-repository surgical pass at commit $REF_COMMIT.
Do not edit files or Git history. Inspect:
- $RUN_DIR/opencode-analysis.md
- $RUN_DIR/claude-summary.md
- $RUN_DIR/changed-paths.txt
- $RUN_DIR/changes.patch
- $RUN_DIR/shell-syntax.log
- $RUN_DIR/python-compile.log
- $RUN_DIR/python-tests.log
- the corresponding *.exit-code files

Return a strict ACCEPT, REVISE or BLOCKED assessment with exact paths and one bounded
next action. Reject broad rewrites, unsupported claims, unsafe Git operations and any
change outside the stated operator objective.
EOF
    } | (
      cd "$WORKTREE"
      "$CODEX_BIN" exec --sandbox read-only --ephemeral -o "$RUN_DIR/codex-review.md" -
    ) > "$RUN_DIR/codex.stdout.log" 2> "$RUN_DIR/codex.stderr.log"
    CODEX_EXIT=$?
    set -e
    (( CODEX_EXIT == 0 )) || CODEX_OK=false
  else
    printf '%s\n' 'codex command not installed; skipped' > "$RUN_DIR/codex-review.md"
    CODEX_OK=false
  fi
fi
printf '%s\n' "$CODEX_EXIT" > "$RUN_DIR/codex.exit-code"

PATCH_BYTES="$(wc -c < "$RUN_DIR/changes.patch" | tr -d ' ')"
CHANGED_PATH_COUNT="$(awk 'NF {count++} END {print count+0}' "$RUN_DIR/changed-paths.txt")"
STATUS=SUCCESS
EXIT_CODE=0
if [[ "$OPENCODE_OK" != true && "$OPENCODE_FALLBACK_USED" != true ]]; then
  STATUS=BLOCKED_OPENCODE
  EXIT_CODE=70
elif [[ "$CLAUDE_OK" != true ]]; then
  STATUS=BLOCKED_CLAUDE
  EXIT_CODE=71
elif [[ "$MODE" == patch && "$VALIDATION_OK" != true ]]; then
  STATUS=VALIDATION_FAILED
  EXIT_CODE=72
elif [[ "$RUN_CODEX_REVIEW" == true && "$CODEX_OK" != true ]]; then
  STATUS=CODEX_REVIEW_FAILED
  EXIT_CODE=73
elif [[ "$MODE" == patch && "$PATCH_BYTES" == 0 ]]; then
  STATUS=SUCCESS_NO_CHANGES
fi
if [[ "$OPENCODE_FALLBACK_USED" == true && "$EXIT_CODE" == 0 ]]; then
  if [[ "$STATUS" == SUCCESS_NO_CHANGES ]]; then
    STATUS=SUCCESS_NO_CHANGES_WITH_OPENCODE_FALLBACK
  else
    STATUS=SUCCESS_WITH_OPENCODE_FALLBACK
  fi
fi

cat > "$RUN_DIR/RESULT.txt" <<EOF
R4R OpenCode -> Claude Code surgical run

Status:           $STATUS
Repository:       $REPO
Selected ref:     $BRANCH
Selected commit:  $REF_COMMIT
Mode:             $MODE
OpenCode model:   $OPENCODE_MODEL_RESOLVED
OpenCode attempts:$OPENCODE_ATTEMPT/$OPENCODE_RETRIES
OpenCode fallback:$OPENCODE_FALLBACK_USED
OpenCode exit:    $OPENCODE_EXIT
OpenCode parse:   $OPENCODE_PARSE_EXIT
Claude Code exit: $CLAUDE_EXIT
Claude parse:     $CLAUDE_PARSE_EXIT
Shell syntax:     $SHELL_SYNTAX_EXIT
Python compile:   $PYTHON_COMPILE_EXIT
Python tests:     $PYTHON_TESTS_EXIT
Codex exit:       $CODEX_EXIT
Changed paths:    $CHANGED_PATH_COUNT
Patch bytes:      $PATCH_BYTES
Temporary tree:   $WORKTREE
Worktree kept:    $KEEP_WORKTREE

Primary artifacts:
- opencode-attempts.tsv
- opencode.model and opencode.fallback-used
- opencode-analysis.md
- opencode.raw.jsonl
- opencode.stderr.log
- claude-summary.md
- claude-result.json
- claude.stderr.log
- changed-paths.txt
- changes.patch
- git-status.txt
- shell-syntax.log and .exit-code
- python-compile.log and .exit-code
- python-tests.log and .exit-code
- codex-review.md and codex.exit-code
- masked-sensitive-paths.txt
EOF

if [[ "$KEEP_WORKTREE" == true ]]; then
  log "temporary worktree preserved: $WORKTREE"
fi
if (( EXIT_CODE == 0 )); then
  log "completed successfully ($STATUS); inspect $RUN_DIR/RESULT.txt"
else
  log "finished with $STATUS (exit $EXIT_CODE); inspect $RUN_DIR/RESULT.txt"
fi
exit "$EXIT_CODE"
