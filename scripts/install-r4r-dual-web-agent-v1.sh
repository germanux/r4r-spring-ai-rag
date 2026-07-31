#!/usr/bin/env bash
set -Eeuo pipefail

DESTINATION=""
REPO="$(pwd)"
STOP_RUNNING=0
RUN_GALLERY=0
SOURCE_ROOT=""

usage() {
  cat <<'EOF'
Uso:
  bash install-r4r-dual-web-agent-v1.sh --destination LP|PC [opciones]

Opciones:
  --destination LP|PC   Modelo de ejecución. Si falta, se pregunta.
  --repo RUTA           Repositorio donde instalar la configuración.
  --stop-running        Detiene agentes OpenCode/controlador de ese repositorio.
  --run-gallery         Ejecuta la tarea web después de instalar.
  --source-root RUTA    Repositorio que contiene la web a modificar.
  -h, --help            Muestra esta ayuda.
EOF
}

while (($#)); do
  case "$1" in
    --destination)
      DESTINATION="${2:-}"
      shift 2
      ;;
    --repo)
      REPO="${2:-}"
      shift 2
      ;;
    --stop-running)
      STOP_RUNNING=1
      shift
      ;;
    --run-gallery)
      RUN_GALLERY=1
      shift
      ;;
    --source-root)
      SOURCE_ROOT="${2:-}"
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "ERROR: opción desconocida: $1" >&2
      usage >&2
      exit 2
      ;;
  esac
done

if [[ -z "$DESTINATION" ]]; then
  read -r -p "Destino del modelo [LP/PC]: " DESTINATION
fi
DESTINATION="${DESTINATION^^}"
case "$DESTINATION" in
  LP|PC) ;;
  *) echo "ERROR: --destination debe ser LP o PC" >&2; exit 2 ;;
esac

REPO="$(cd "$REPO" && pwd)"
[[ -d "$REPO/.git" || -f "$REPO/.git" ]] || {
  echo "ERROR: no es un worktree Git: $REPO" >&2
  exit 2
}

cd "$REPO"

running_for_repo() {
  ps -eo pid=,args= \
    | grep -E '[r]un-codex-agent|[r]4r_codex_agent|[o]pencode run' \
    | grep -F "$REPO" \
    || true
}

if [[ -n "$(running_for_repo)" ]]; then
  if (( STOP_RUNNING )); then
    if [[ -x scripts/find-and-stop-r4r-orphans.sh ]]; then
      R4R_REPO="$REPO" scripts/find-and-stop-r4r-orphans.sh --kill
    else
      mapfile -t pids < <(running_for_repo | awk '{print $1}')
      ((${#pids[@]} == 0)) || kill -TERM "${pids[@]}" 2>/dev/null || true
      sleep 3
    fi
  else
    echo "ERROR: hay un controlador/OpenCode activo para este worktree." >&2
    echo "Repite con --stop-running o detenlo antes de instalar." >&2
    running_for_repo >&2
    exit 3
  fi
fi

for command in git python3 node npm npx curl; do
  command -v "$command" >/dev/null 2>&1 || {
    echo "ERROR: falta el comando requerido: $command" >&2
    exit 2
  }
done

node_major="$(node -p 'Number(process.versions.node.split(".")[0])')"
(( node_major >= 18 )) || {
  echo "ERROR: Playwright MCP requiere Node.js 18 o superior." >&2
  exit 2
}

stamp="$(date +%Y%m%d-%H%M%S)"
backup="$REPO/patches-applied/dual-web-agent-backup-$stamp"
mkdir -p "$backup"
for path in opencode.jsonc AGENTS.md .env .env.example .opencode/agents .opencode/commands; do
  [[ -e "$path" ]] || continue
  mkdir -p "$backup/$(dirname "$path")"
  cp -a "$path" "$backup/$path"
done

mkdir -p .opencode/agents .opencode/commands scripts runtime/playwright

cat > opencode.jsonc <<'JSON'
{
  "$schema": "https://opencode.ai/config.json",
  "default_agent": "__DEFAULT_AGENT__",
  "share": "disabled",
  "snapshot": true,
  "provider": {
    "ollama-pc": {
      "npm": "@ai-sdk/openai-compatible",
      "name": "Ollama PC - R4R",
      "options": {
        "baseURL": "{env:R4R_OPENCODE_PC_BASE_URL}",
        "timeout": 14400000,
        "chunkTimeout": 600000
      },
      "models": {
        "qwen3-coder-next-80b-t025-168k-8k-pc-pc": {
          "name": "Qwen3-Coder-Next 80B PC 168K/8K",
          "temperature": true,
          "limit": { "context": 172032, "output": 8192 }
        }
      }
    },
    "ollama-laptop": {
      "npm": "@ai-sdk/openai-compatible",
      "name": "Ollama Laptop - R4R",
      "options": {
        "baseURL": "{env:R4R_OPENCODE_LP_BASE_URL}",
        "timeout": 14400000,
        "chunkTimeout": 600000
      },
      "models": {
        "qwen3-30b-coder-28k-6k-t33:latest": {
          "name": "Qwen3 Coder 30B Laptop 28K/6K t33",
          "temperature": true,
          "limit": { "context": 28672, "output": 6144 }
        }
      }
    }
  },
  "permission": {
    "codegraph_*": "deny",
    "playwright_*": "deny"
  },
  "mcp": {
    "codegraph": {
      "type": "local",
      "command": ["codegraph", "serve", "--mcp"],
      "enabled": true,
      "timeout": 15000
    },
    "playwright": {
      "type": "local",
      "command": [
        "npx", "-y", "@playwright/mcp@latest",
        "--headless", "--isolated", "--block-service-workers",
        "--image-responses", "omit",
        "--viewport-size", "1440x1000",
        "--timeout-navigation", "90000",
        "--output-dir", "runtime/playwright"
      ],
      "enabled": true,
      "timeout": 30000
    }
  },
  "watcher": {
    "ignore": [
      ".git/**", ".codegraph/**", "runtime/**", "target/**", "dist/**",
      "node_modules/**", "docker-postgres/data/**", "patches-applied/**",
      "**/*.zip", "r4r-agent-update-v*/**", "r4r-agent-hotfix-v*/**"
    ]
  }
}
JSON

if [[ "$DESTINATION" == "LP" ]]; then
  default_agent="r4r-laptop"
else
  default_agent="r4r-pc"
fi
sed -i "s/__DEFAULT_AGENT__/$default_agent/" opencode.jsonc

# Prefer the already installed Chrome on the launcher machine. Without Chrome,
# Playwright MCP keeps its documented default browser behavior.
if command -v google-chrome >/dev/null 2>&1 \
    || command -v google-chrome-stable >/dev/null 2>&1; then
  python3 - <<'PY_BROWSER'
import json
from pathlib import Path
path = Path("opencode.jsonc")
data = json.loads(path.read_text())
command = data["mcp"]["playwright"]["command"]
command[3:3] = ["--browser", "chrome"]
path.write_text(json.dumps(data, indent=2) + "\n")
PY_BROWSER
fi

cat > .opencode/agents/r4r-pc.md <<'EOF'
---
description: Implement one bounded R4R task with the PC 80B worker
mode: primary
model: ollama-pc/qwen3-coder-next-80b-t025-168k-8k-pc-pc
steps: 72
temperature: 0.25
permission:
  "*": deny
  read:
    "AGENTS.md": allow
    ".opencode/**": allow
    "pom.xml": allow
    "src/**": allow
    "knowledge/**": allow
    "docker-postgres/**": allow
    "scripts/**": allow
    "docs/**": allow
    ".env.example": allow
    ".gitignore": allow
    "codegraph.json": allow
    "runtime/**": allow
  edit:
    "pom.xml": allow
    "src/**": allow
    "knowledge/**": allow
    "docker-postgres/**": allow
    "docs/**": allow
    ".env.example": allow
    ".gitignore": allow
    "codegraph.json": allow
  glob: allow
  grep: allow
  list: allow
  bash: allow
  codegraph_*: allow
  playwright_*: deny
  question: deny
  task: deny
  webfetch: deny
  websearch: deny
  external_directory: deny
  doom_loop: deny
---
Follow the controller packet exactly. Edit only selected-task paths. After two
identical tool failures, stop and report the blocker instead of repeating the call.
Never write Git history. Run the exact gate once after bounded corrections.
EOF

cat > .opencode/agents/r4r-laptop.md <<'EOF'
---
description: Implement one compact R4R task with the remote laptop 30B worker
mode: primary
model: ollama-laptop/qwen3-30b-coder-28k-6k-t33:latest
steps: 44
temperature: 0.33
permission:
  "*": deny
  read:
    "AGENTS.md": allow
    ".opencode/**": allow
    "pom.xml": allow
    "src/**": allow
    "knowledge/**": allow
    "docker-postgres/**": allow
    "scripts/**": allow
    "docs/**": allow
    ".env.example": allow
    ".gitignore": allow
    "codegraph.json": allow
    "runtime/**": allow
  edit:
    "pom.xml": allow
    "src/**": allow
    "knowledge/**": allow
    "docker-postgres/**": allow
    "docs/**": allow
    ".env.example": allow
    ".gitignore": allow
    "codegraph.json": allow
  glob: allow
  grep: allow
  list: allow
  bash: allow
  codegraph_*: allow
  playwright_*: deny
  question: deny
  task: deny
  webfetch: deny
  websearch: deny
  external_directory: deny
  doom_loop: deny
---
The total window is 28K with up to 6K output, so keep working input below about 22K.
Read only the active packet and implicated files. Make one bounded repair batch. After
two identical failures, stop and report the blocker. Never write Git history.
EOF

cat > .opencode/agents/r4r-gallery-pc.md <<'EOF'
---
description: Rebuild the Riansares gallery section with Playwright and the PC worker
mode: primary
model: ollama-pc/qwen3-coder-next-80b-t025-168k-8k-pc-pc
steps: 64
temperature: 0.25
permission:
  "*": deny
  read:
    "*": allow
    ".git/**": deny
    ".env": deny
    ".env.*": deny
    ".env.example": allow
  edit:
    "**/*.html": allow
    "**/*.css": allow
    "**/*.scss": allow
    "**/*.sass": allow
    "**/*.less": allow
    "**/*.js": allow
    "**/*.mjs": allow
    "**/*.cjs": allow
    "**/*.ts": allow
    "**/*.tsx": allow
    "**/*.jsx": allow
    "**/*.vue": allow
    "**/*.astro": allow
    "**/*.svelte": allow
    "**/*.json": allow
    "**/*.md": allow
    "public/**": allow
    "assets/**": allow
    "src/**": allow
    "tests/**": allow
    "e2e/**": allow
  glob: allow
  grep: allow
  list: allow
  bash: allow
  playwright_*: allow
  codegraph_*: deny
  webfetch: deny
  websearch: deny
  question: deny
  task: deny
  external_directory: deny
  doom_loop: deny
---
Use Playwright only against the canonical public page and local preview. The public
site is read-only: do not submit forms, authenticate, upload, deploy or mutate remote
state. Edit only the local implementation of the target gallery section.
EOF

cat > .opencode/agents/r4r-gallery-laptop.md <<'EOF'
---
description: Rebuild the Riansares gallery section with Playwright and laptop 30B
mode: primary
model: ollama-laptop/qwen3-30b-coder-28k-6k-t33:latest
steps: 40
temperature: 0.33
permission:
  "*": deny
  read:
    "*": allow
    ".git/**": deny
    ".env": deny
    ".env.*": deny
    ".env.example": allow
  edit:
    "**/*.html": allow
    "**/*.css": allow
    "**/*.scss": allow
    "**/*.sass": allow
    "**/*.less": allow
    "**/*.js": allow
    "**/*.mjs": allow
    "**/*.cjs": allow
    "**/*.ts": allow
    "**/*.tsx": allow
    "**/*.jsx": allow
    "**/*.vue": allow
    "**/*.astro": allow
    "**/*.svelte": allow
    "**/*.json": allow
    "**/*.md": allow
    "public/**": allow
    "assets/**": allow
    "src/**": allow
    "tests/**": allow
    "e2e/**": allow
  glob: allow
  grep: allow
  list: allow
  bash: allow
  playwright_*: allow
  codegraph_*: deny
  webfetch: deny
  websearch: deny
  question: deny
  task: deny
  external_directory: deny
  doom_loop: deny
---
The 28K window leaves about 22K for input when reserving 6K output. Inspect one page,
one target section and only directly relevant local files. The public site is read-only.
After two identical failures, stop and report the exact blocker.
EOF

cat > AGENTS.md <<'EOF'
# R4R agent rules

## Scope

Use one selected agent and one bounded task. PC and laptop names select inference only;
OpenCode, Playwright, Git, builds and files run on the machine launching OpenCode.
Never run two agents against the same worktree.

## General discipline

- Read the active task file before editing.
- Read only files needed for that task; do not crawl the repository.
- Make the smallest coherent patch. Preserve public contracts and unrelated sections.
- After two identical tool errors, stop and report the blocker; never repeat blindly.
- No `sudo`, package-manager mutation, Git writes, push, deployment or secret reads.
- Run the repository's existing deterministic checks. Do not weaken tests or gates.

## RAG controller tasks

For Tasks 01-04 follow `.opencode/task-plan.json`, the selected command and the Codex
packet. Use CodeGraph only for implicated Java paths. Browser tools are forbidden.
A task advances only on its exact green gate and Codex `ACCEPT`.

## Gallery task

Canonical URL: `https://riansares4r.com/galeria-antes-despues`.
The XPath `/html/body/main/section[2]` is only a hint. Confirm the target semantically by
the heading `Trabajos realizados`; preserve the preceding hero and all later sections.

Use Playwright to inspect the target section, DOM, computed styles, responsive layout,
console and directly loaded assets. Do not copy the whole site's CSS or JavaScript.
Reuse local tokens/components first; transfer only rules or behavior required by the
section. Do not interact with forms, WhatsApp, cookies, authentication or remote state.

Edit only a local source implementation of `/galeria-antes-despues`. If that route or
its source cannot be found in the selected source worktree, stop without creating a
parallel website. Validate the local build and existing browser/e2e checks. Do not
deploy; report changed files, checks, visual differences and the first unproven item.
EOF

cat > .opencode/commands/task-web-gallery.md <<'EOF'
# Task — Rebuild the before/after gallery section

Reference: `https://riansares4r.com/galeria-antes-despues`.
Target hint: `/html/body/main/section[2]`. Confirm it is the section headed
`Trabajos realizados`; XPath position alone is not authoritative.

1. Open the reference page once with Playwright. Inspect only the target section,
   its parent layout, computed styles, responsive behavior, console and same-page
   CSS/JS requests directly needed to explain the section.
2. Locate the local source for route `/galeria-antes-despues` by route or heading.
   If absent, stop: do not invent a second site inside an unrelated repository.
3. Preserve the hero/first section and every section after the gallery. Rebuild only
   the target gallery in the local framework and conventions.
4. Reuse local design tokens and components. Do not paste whole remote stylesheets,
   minified bundles, analytics, trackers or unrelated scripts. Copy no credentials,
   cookies or user data.
5. Keep semantic headings, image alt text, keyboard behavior and responsive layout.
   Avoid fragile absolute XPath selectors in production code.
6. Run the existing formatter/build/tests. Run existing Playwright/e2e checks when
   available; otherwise report that browser acceptance remains unproven.
7. Stop after one coherent implementation and validation pass. After two identical
   tool failures, stop and report the exact blocker.

Remote site is read-only. Never submit forms, contact links, upload, deploy, push or
change DNS/hosting. Final report: changed paths, checks, visual equivalence and gaps.
EOF

# Replace the one known oversized guide with the compact V4 version.
cat > .opencode/commands/task-03-pgvector-implementation-guide.md <<'EOF'
# Task 03 implementation guide

## Schema/config

- Enable `vector`; use plain Flyway `CREATE` statements so drift is visible.
- Match Spring AI 1.0.0, cosine search and `vector(768)`.
- Tests use disposable PostgreSQL and deterministic 768-D embeddings, never Ollama.

## Store contract

Validate public inputs and prepare every source replacement before mutation. Detect
all duplicate logical identities before any `DELETE`. Stable IDs derive unambiguously
from source, heading path and ordinal, independent of content.

Replace one source transactionally: delete only that source, then add prepared
Documents preserving `source`, `headingPath` and `ordinal`. Reject malformed metadata.
Validate query, `topK` and finite threshold before delegating through `SearchRequest`.

## Evidence

Prove schema/index/dimension, idempotent reindex, stable IDs, stale-row deletion,
other-source preservation, pre-mutation duplicate failure, citation metadata, `topK`,
threshold behavior, deterministic embeddings and real PostgreSQL rollback via a
`BEFORE INSERT` trigger. Compare exact ordered snapshots for rollback assertions.

No mocks, spies, AOP, reflection, subclass failure hooks or weakened gates.
Gate: `./scripts/task-gate.sh task-03-pgvector`
EOF

cat > scripts/select-r4r-destination.sh <<'EOF'
#!/usr/bin/env bash
set -Eeuo pipefail
DEST=""
QUIET=0
while (($#)); do
  case "$1" in
    --destination) DEST="${2:-}"; shift 2 ;;
    --quiet) QUIET=1; shift ;;
    -h|--help) echo "Uso: $0 --destination LP|PC"; exit 0 ;;
    *) echo "ERROR: opción desconocida: $1" >&2; exit 2 ;;
  esac
done
[[ -n "$DEST" ]] || read -r -p "Destino [LP/PC]: " DEST
DEST="${DEST^^}"
case "$DEST" in LP|PC) ;; *) echo "ERROR: destino LP o PC" >&2; exit 2 ;; esac
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
[[ -f .env ]] || cp .env.example .env
upsert() {
  local file="$1" key="$2" value="$3" tmp
  tmp="$(mktemp)"
  awk -v k="$key" -v v="$value" '
    BEGIN { found=0 }
    $0 ~ "^" k "=" { print k "=" v; found=1; next }
    { print }
    END { if (!found) print k "=" v }
  ' "$file" > "$tmp"
  mv "$tmp" "$file"
}
if [[ "$DEST" == LP ]]; then
  agent="r4r-laptop"; gallery="r4r-gallery-laptop"
else
  agent="r4r-pc"; gallery="r4r-gallery-pc"
fi
upsert .env R4R_OPENCODE_AGENT "$agent"
upsert .env R4R_GALLERY_AGENT "$gallery"
python3 - "$agent" <<'PY'
import json, pathlib, sys
path = pathlib.Path("opencode.jsonc")
data = json.loads(path.read_text())
data["default_agent"] = sys.argv[1]
path.write_text(json.dumps(data, indent=2) + "\n")
PY
(( QUIET )) || printf 'Destino=%s\nAgente=%s\nGalería=%s\n' "$DEST" "$agent" "$gallery"
EOF

cat > scripts/run-gallery-agent.sh <<'EOF'
#!/usr/bin/env bash
set -Eeuo pipefail
DEST=""
SOURCE_ROOT=""
ALLOW_DIRTY=0
while (($#)); do
  case "$1" in
    --destination) DEST="${2:-}"; shift 2 ;;
    --source-root) SOURCE_ROOT="${2:-}"; shift 2 ;;
    --allow-dirty) ALLOW_DIRTY=1; shift ;;
    -h|--help)
      echo "Uso: $0 --destination LP|PC --source-root /ruta/web [--allow-dirty]"
      exit 0
      ;;
    *) echo "ERROR: opción desconocida: $1" >&2; exit 2 ;;
  esac
done
[[ -n "$DEST" ]] || read -r -p "Destino [LP/PC]: " DEST
DEST="${DEST^^}"
case "$DEST" in LP|PC) ;; *) echo "ERROR: destino LP o PC" >&2; exit 2 ;; esac
CONFIG_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SOURCE_ROOT="${SOURCE_ROOT:-$CONFIG_ROOT}"
SOURCE_ROOT="$(cd "$SOURCE_ROOT" && pwd)"
[[ -d "$SOURCE_ROOT/.git" || -f "$SOURCE_ROOT/.git" ]] || {
  echo "ERROR: --source-root no es un worktree Git" >&2; exit 2; }
if ps -eo args= | grep -E '[r]4r_codex_agent|[o]pencode run' | grep -F "$SOURCE_ROOT" >/dev/null; then
  echo "ERROR: ya hay un agente activo en el source worktree" >&2; exit 3
fi
command -v opencode >/dev/null 2>&1 || {
  echo "ERROR: OpenCode no está instalado o no está en PATH." >&2; exit 2; }
if (( ! ALLOW_DIRTY )) && [[ -n "$(git -C "$SOURCE_ROOT" status --porcelain)" ]]; then
  echo "ERROR: el source worktree tiene cambios; usa uno limpio." >&2
  git -C "$SOURCE_ROOT" status --short | head -20 >&2
  echo "Solo continúa conscientemente con --allow-dirty." >&2
  exit 5
fi
source_match="$(
  while IFS= read -r -d '' candidate; do
    if grep -Iq -e 'galeria-antes-despues' -e 'Trabajos realizados' "$candidate"; then
      printf '%s\n' "$candidate"
      break
    fi
  done < <(
    find "$SOURCE_ROOT" \
      -type d \
        \( -name .git -o -name .opencode -o -name node_modules -o -name target \
           -o -name runtime -o -name patches-applied -o -name dist -o -name build \) \
        -prune -o \
      -type f \
        \( -name '*.html' -o -name '*.htm' -o -name '*.css' -o -name '*.scss' \
           -o -name '*.js' -o -name '*.mjs' -o -name '*.cjs' -o -name '*.ts' \
           -o -name '*.tsx' -o -name '*.jsx' -o -name '*.vue' -o -name '*.astro' \
           -o -name '*.svelte' -o -name '*.md' -o -name '*.mdx' -o -name '*.json' \) \
        ! -name 'AGENTS.md' -print0
  )
)"
if [[ -z "$source_match" ]]; then
  echo "ERROR: no encuentro el código local de /galeria-antes-despues." >&2
  echo "Playwright puede leer la web pública, pero no modificar su servidor." >&2
  echo "Indica el repositorio real mediante --source-root." >&2
  exit 4
fi
"$CONFIG_ROOT/scripts/select-r4r-destination.sh" --destination "$DEST" --quiet
set -a
source "$CONFIG_ROOT/.env"
set +a
if [[ "$DEST" == LP ]]; then agent="r4r-gallery-laptop"; else agent="r4r-gallery-pc"; fi
export OPENCODE_CONFIG="$CONFIG_ROOT/opencode.jsonc"
export OPENCODE_CONFIG_DIR="$CONFIG_ROOT/.opencode"
export OPENCODE_CONFIG_CONTENT="$(cat "$CONFIG_ROOT/opencode.jsonc")"
mkdir -p "$SOURCE_ROOT/runtime/gallery-agent"
log="$SOURCE_ROOT/runtime/gallery-agent/$(date +%Y%m%d-%H%M%S).log"
prompt="$(cat "$CONFIG_ROOT/.opencode/commands/task-web-gallery.md")"
cd "$SOURCE_ROOT"
opencode run --dir "$SOURCE_ROOT" --agent "$agent" --auto "$prompt" 2>&1 | tee "$log"
printf '\nLog: %s\n' "$log"
EOF

chmod +x scripts/select-r4r-destination.sh scripts/run-gallery-agent.sh

upsert_env() {
  local file="$1" key="$2" value="$3" tmp
  [[ -f "$file" ]] || : > "$file"
  tmp="$(mktemp)"
  awk -v k="$key" -v v="$value" '
    BEGIN { found=0 }
    $0 ~ "^" k "=" { print k "=" v; found=1; next }
    { print }
    END { if (!found) print k "=" v }
  ' "$file" > "$tmp"
  mv "$tmp" "$file"
}

[[ -f .env.example ]] || : > .env.example
[[ -f .env ]] || cp .env.example .env
for file in .env.example .env; do
  upsert_env "$file" R4R_OPENCODE_PC_BASE_URL "http://127.0.0.1:11434/v1"
  upsert_env "$file" R4R_OPENCODE_LP_BASE_URL "http://192.168.1.9:11434/v1"
  upsert_env "$file" R4R_GALLERY_URL "https://riansares4r.com/galeria-antes-despues"
done
scripts/select-r4r-destination.sh --destination "$DESTINATION" --quiet

# Prefetch the MCP package. Chrome stays on the launcher machine; the laptop only
# serves the model when LP is selected.
npx -y @playwright/mcp@latest --help >/dev/null

python3 -m json.tool opencode.jsonc >/dev/null
for file in .opencode/commands/*.md; do
  bytes="$(wc -c < "$file")"
  if (( bytes > 10000 )); then
    echo "ERROR: command demasiado grande ($bytes bytes): $file" >&2
    exit 5
  fi
done

if [[ "$DESTINATION" == LP ]]; then
  curl -fsS --connect-timeout 5 \
    http://192.168.1.9:11434/api/tags \
    | grep -Fq 'qwen3-30b-coder-28k-6k-t33:latest' \
    || { echo "ERROR: el modelo LP no responde en 192.168.1.9:11434" >&2; exit 6; }
fi

echo "Instalación terminada."
echo "Destino seleccionado: $DESTINATION"
echo "Copia previa: $backup"
echo "Todos los command MD quedan por debajo de 10 KB."
echo "Playwright MCP se ejecutará en este PC; LP solo sirve inferencia."

if (( RUN_GALLERY )); then
  args=(--destination "$DESTINATION")
  [[ -z "$SOURCE_ROOT" ]] || args+=(--source-root "$SOURCE_ROOT")
  exec scripts/run-gallery-agent.sh "${args[@]}"
fi
